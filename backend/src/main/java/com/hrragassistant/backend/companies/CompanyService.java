package com.hrragassistant.backend.companies;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import com.hrragassistant.backend.companies.analysis.CompanyAnalyzer;
import com.hrragassistant.backend.companies.analysis.CompanyHistory;
import com.hrragassistant.backend.companies.xbrl.FigureMapper;
import com.hrragassistant.backend.companies.xbrl.FilingMerger;
import com.hrragassistant.backend.companies.xbrl.Metric;
import com.hrragassistant.backend.companies.xbrl.XbrlJsonReader;
import com.hrragassistant.backend.companies.xbrl.YearFigures;
import com.hrragassistant.backend.hosting.UsageLimits;

/**
 * Finds listed EU companies, imports their official annual filings and
 * builds the multi-year analysis. Company data is public, so imported
 * companies are shared by everyone; importing again is throttled.
 */
@Service
public class CompanyService {

    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern LEI = Pattern.compile("^[A-Z0-9]{20}$");
    /** Most recent annual filings read per import (each also carries the year before). */
    static final int MAX_FILINGS = 8;
    private static final java.util.concurrent.Semaphore READERS = new java.util.concurrent.Semaphore(1, true);
    private static final Duration REFRESH_AFTER = Duration.ofHours(12);
    private static final Duration SEARCH_CACHE = Duration.ofHours(1);

    private final FilingsClient filings;
    private final CompanyRepository companies;
    private final CompanyYearRepository years;
    private final UsageLimits limits;
    private final TransactionTemplate tx;
    private final Map<String, CachedSearch> searchCache = new ConcurrentHashMap<>();

    public CompanyService(FilingsClient filings, CompanyRepository companies, CompanyYearRepository years,
                          UsageLimits limits, PlatformTransactionManager transactions) {
        this.filings = filings;
        this.companies = companies;
        this.years = years;
        this.limits = limits;
        this.tx = new TransactionTemplate(transactions);
    }

    // ------------------------------------------------------------------ DTOs

    public record SearchHit(String lei, String name, boolean imported) {
    }

    public record CompanySummary(String lei, String name, String country, Instant importedAt,
                                 LocalDate firstYear, LocalDate lastYear, int yearsOfData) {
    }

    /** Where each year's figures came from: the filing, and the IFRS concept behind each figure. */
    public record YearSource(LocalDate periodEnd, LocalDate filingPeriodEnd, String viewerUrl, Map<String, String> concepts) {
    }

    public record CompanyView(CompanySummary company, CompanyHistory history, List<YearSource> sources, String importNote) {
    }

    private record CachedSearch(Instant at, List<FilingsClient.EntityHit> hits) {
    }

    // ------------------------------------------------------------------ queries

    public List<CompanySummary> list() {
        return companies.findAllByOrderByNameAsc().stream().map(this::summary).toList();
    }

    public List<SearchHit> search(String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type at least 2 letters of the company name.");
        }
        String key = q.toLowerCase();
        CachedSearch cached = searchCache.get(key);
        List<FilingsClient.EntityHit> hits;
        if (cached != null && cached.at().plus(SEARCH_CACHE).isAfter(Instant.now())) {
            hits = cached.hits();
        } else {
            try {
                hits = filings.searchEntities(q);
            } catch (RestClientException e) {
                log.warn("Company search failed: {}", e.getMessage());
                throw unavailable();
            }
            if (searchCache.size() > 500) {
                searchCache.clear();
            }
            searchCache.put(key, new CachedSearch(Instant.now(), hits));
        }
        Set<String> imported = companies.findAll().stream().map(Company::getLei).collect(Collectors.toSet());
        return hits.stream().map(h -> new SearchHit(h.lei(), h.name(), imported.contains(h.lei()))).toList();
    }

    /** Countries with listed companies that publish ESEF (or UK) annual reports, in display order. */
    public static final Map<String, String> COUNTRIES = countries();
    private static final Duration DIRECTORY_CACHE = Duration.ofHours(24);
    private final Map<String, CachedDirectory> directoryCache = new ConcurrentHashMap<>();

    private record CachedDirectory(Instant at, List<DirectoryEntry> entries) {
    }

    public record Country(String code, String name) {
    }

    public record DirectoryItem(String lei, String name, String country, LocalDate lastPeriodEnd, boolean imported) {
    }

    private static Map<String, String> countries() {
        Map<String, String> m = new LinkedHashMap<>();
        String[][] all = {{"AT", "Austria"}, {"BE", "Belgium"}, {"BG", "Bulgaria"}, {"HR", "Croatia"}, {"CY", "Cyprus"},
            {"CZ", "Czechia"}, {"DK", "Denmark"}, {"EE", "Estonia"}, {"FI", "Finland"}, {"FR", "France"}, {"DE", "Germany"},
            {"GR", "Greece"}, {"HU", "Hungary"}, {"IS", "Iceland"}, {"IE", "Ireland"}, {"IT", "Italy"}, {"LV", "Latvia"},
            {"LT", "Lithuania"}, {"LU", "Luxembourg"}, {"MT", "Malta"}, {"NL", "Netherlands"}, {"NO", "Norway"},
            {"PL", "Poland"}, {"PT", "Portugal"}, {"RO", "Romania"}, {"SK", "Slovakia"}, {"SI", "Slovenia"},
            {"ES", "Spain"}, {"SE", "Sweden"}, {"GB", "United Kingdom"}};
        for (String[] c : all) {
            m.put(c[0], c[1]);
        }
        return java.util.Collections.unmodifiableMap(m);
    }

    public List<Country> countryList() {
        return COUNTRIES.entrySet().stream().map(e -> new Country(e.getKey(), e.getValue())).toList();
    }

    /**
     * Every company from one country that has an annual filing in the index,
     * A–Z. Cached for a day: the list only changes when new reports come in.
     */
    public List<DirectoryItem> directory(String country) {
        String code = country == null ? "" : country.trim().toUpperCase();
        if (!COUNTRIES.containsKey(code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a country from the list.");
        }
        CachedDirectory cached = directoryCache.get(code);
        List<DirectoryEntry> entries;
        if (cached != null && cached.at().plus(DIRECTORY_CACHE).isAfter(Instant.now())) {
            entries = cached.entries();
        } else {
            synchronized (directoryCache) {
                cached = directoryCache.get(code);
                if (cached != null && cached.at().plus(DIRECTORY_CACHE).isAfter(Instant.now())) {
                    entries = cached.entries();
                } else {
                    try {
                        entries = filings.directory(code);
                    } catch (RestClientException e) {
                        log.warn("Company list for {} failed: {}", code, e.getMessage());
                        throw unavailable();
                    }
                    directoryCache.put(code, new CachedDirectory(Instant.now(), entries));
                }
            }
        }
        Set<String> imported = companies.findAll().stream().map(Company::getLei).collect(Collectors.toSet());
        return entries.stream()
                .sorted(Comparator.comparing(e -> e.name().toLowerCase()))
                .map(e -> new DirectoryItem(e.lei(), e.name(), e.country(), e.lastPeriodEnd(), imported.contains(e.lei())))
                .toList();
    }

    public CompanyView get(String lei) {
        Company company = companies.findById(checkLei(lei))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This company hasn't been imported yet."));
        return view(company);
    }

    // ------------------------------------------------------------------ import

    /**
     * Imports (or refreshes) a company's filings. A company imported in the
     * last 12 hours is returned as it is, unless an admin forces a refresh.
     */
    public CompanyView importCompany(String lei, String username, boolean admin, boolean force) {
        lei = checkLei(lei);
        Company company = companies.findById(lei).orElse(null);
        if (company != null && company.getImportedAt() != null && !(admin && force)
                && company.getImportedAt().plus(REFRESH_AFTER).isAfter(Instant.now())) {
            return view(company);
        }
        limits.acquireImport(username, admin);

        List<FilingsClient.Filing> all;
        String name;
        try {
            all = filings.filings(lei);
            name = company != null ? company.getName() : filings.entityName(lei);
        } catch (RestClientException e) {
            limits.releaseImport(username, admin);
            log.warn("Listing filings for {} failed: {}", lei, e.getMessage());
            throw unavailable();
        }
        List<FilingsClient.Filing> chosen = chooseFilings(all);
        if (chosen.isEmpty()) {
            limits.releaseImport(username, admin);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No machine-readable annual reports were found for this company. Only companies listed on an EU-regulated market publish them (since 2020/2021).");
        }

        List<FilingMerger.FilingYears> read = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        // A filing can be tens of MB in memory while it's read: one import at a time,
        // so a burst of imports queues up instead of running the server out of memory.
        boolean permit;
        try {
            permit = READERS.tryAcquire(3, java.util.concurrent.TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            permit = false;
        }
        if (!permit) {
            limits.releaseImport(username, admin);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Mentalist is busy reading other companies' filings. Please try again in a few minutes.");
        }
        try {
            for (FilingsClient.Filing f : chosen) {
                try {
                    List<YearFigures> ys = FigureMapper.years(XbrlJsonReader.read(filings.report(f.jsonUrl())));
                    if (!ys.isEmpty()) {
                        read.add(new FilingMerger.FilingYears(f.periodEnd(), f.viewerUrl(), ys));
                    } else {
                        problems.add(f.periodEnd().getYear() + ": no IFRS figures found");
                    }
                } catch (Exception e) {
                    log.warn("Reading filing {} of {} failed: {}", f.id(), lei, e.getMessage());
                    problems.add(f.periodEnd().getYear() + ": couldn't be read");
                }
            }
        } finally {
            READERS.release();
        }
        List<FilingMerger.MergedYear> merged = FilingMerger.merge(read);
        if (merged.isEmpty()) {
            limits.releaseImport(username, admin);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The filings were found, but their figures couldn't be read. Some companies (e.g. banks and insurers) use labels Mentalist doesn't map yet.");
        }

        // Downloads are done; only the save runs in a (short) transaction.
        Company toSave = company != null ? company : new Company(lei, name != null ? name : lei, chosen.get(0).country());
        if (name != null) {
            toSave.setName(name);
        }
        toSave.setCountry(chosen.get(0).country());
        toSave.setImportedAt(Instant.now());
        toSave.setImportNote(problems.isEmpty() ? null : "Some filings were skipped: " + String.join("; ", problems) + ".");
        String id = lei;
        tx.executeWithoutResult(status -> {
            companies.save(toSave);
            years.deleteByLei(id);
            years.flush();
            for (FilingMerger.MergedYear m : merged) {
                years.save(new CompanyYear(id, m.figures().periodEnd(), m.figures().currency(), toJson(m.figures()),
                        m.filingPeriodEnd(), m.viewerUrl()));
            }
        });
        log.info("Imported {} years for {} from {} filings", merged.size(), lei, read.size());
        return view(toSave);
    }

    /** One filing per year: the one with the fewest validation errors, then the most recently processed. */
    static List<FilingsClient.Filing> chooseFilings(List<FilingsClient.Filing> all) {
        Map<LocalDate, FilingsClient.Filing> best = new LinkedHashMap<>();
        for (FilingsClient.Filing f : all) {
            if (f.jsonUrl() == null || f.jsonUrl().isBlank()) {
                continue;
            }
            best.merge(f.periodEnd(), f, (a, b) -> {
                if (a.errorCount() != b.errorCount()) {
                    return a.errorCount() < b.errorCount() ? a : b;
                }
                return a.processed().compareTo(b.processed()) >= 0 ? a : b;
            });
        }
        return best.values().stream()
                .sorted(Comparator.comparing(FilingsClient.Filing::periodEnd).reversed())
                .limit(MAX_FILINGS)
                .toList();
    }

    // ------------------------------------------------------------------ helpers

    private CompanyView view(Company company) {
        List<CompanyYear> rows = years.findByLeiOrderByPeriodEndAsc(company.getLei());
        List<YearFigures> figures = new ArrayList<>();
        List<YearSource> sources = new ArrayList<>();
        for (CompanyYear row : rows) {
            Map<Metric, YearFigures.Value> values = fromJson(row.getValuesJson());
            figures.add(new YearFigures(row.getPeriodEnd(), row.getCurrency(), values));
            Map<String, String> concepts = new LinkedHashMap<>();
            values.forEach((m, v) -> concepts.put(m.name(), v.concept()));
            sources.add(new YearSource(row.getPeriodEnd(), row.getFilingPeriodEnd(), row.getViewerUrl(), concepts));
        }
        CompanyHistory history = CompanyAnalyzer.analyze(figures);
        return new CompanyView(summary(company, rows), history, sources, company.getImportNote());
    }

    private CompanySummary summary(Company c) {
        return summary(c, years.findByLeiOrderByPeriodEndAsc(c.getLei()));
    }

    private static CompanySummary summary(Company c, List<CompanyYear> rows) {
        return new CompanySummary(c.getLei(), c.getName(), c.getCountry(), c.getImportedAt(),
                rows.isEmpty() ? null : rows.get(0).getPeriodEnd(),
                rows.isEmpty() ? null : rows.get(rows.size() - 1).getPeriodEnd(), rows.size());
    }

    static String toJson(YearFigures y) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        y.values().forEach((m, v) -> out.put(m.name(), Map.of("amount", v.amount(), "concept", v.concept())));
        try {
            return JSON.writeValueAsString(out);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static Map<Metric, YearFigures.Value> fromJson(String json) {
        Map<Metric, YearFigures.Value> values = new EnumMap<>(Metric.class);
        try {
            Map<String, Map<String, Object>> in = JSON.readValue(json, new TypeReference<>() {
            });
            in.forEach((k, v) -> {
                try {
                    values.put(Metric.valueOf(k), new YearFigures.Value(((Number) v.get("amount")).doubleValue(), (String) v.get("concept")));
                } catch (RuntimeException ignored) {
                    // a metric from an older version: ignore it
                }
            });
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Stored company figures could not be read", e);
        }
        return values;
    }

    private static String checkLei(String lei) {
        String l = lei == null ? "" : lei.trim().toUpperCase();
        if (!LEI.matcher(l).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That isn't a valid company identifier (LEI).");
        }
        return l;
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "The filings service (filings.xbrl.org) couldn't be reached. Please try again in a few minutes.");
    }
}
