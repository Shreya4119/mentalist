package com.hrragassistant.backend.companies.xbrl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Combines several annual filings into one history. Each year's figures come
 * from that year's own filing first; anything missing is filled from the next
 * year's comparatives, and years older than the oldest filing come from its
 * comparatives. Every year remembers which filing it mostly came from.
 */
public final class FilingMerger {

    private FilingMerger() {
    }

    /** The years read from one filing, plus where that filing can be viewed. */
    public record FilingYears(LocalDate filingPeriodEnd, String viewerUrl, List<YearFigures> years) {
    }

    public record MergedYear(YearFigures figures, LocalDate filingPeriodEnd, String viewerUrl) {
    }

    public static List<MergedYear> merge(List<FilingYears> filings) {
        // Newest filing first, so its comparatives fill gaps before older ones.
        List<FilingYears> ordered = new ArrayList<>(filings);
        ordered.sort(Comparator.comparing(FilingYears::filingPeriodEnd).reversed());

        Map<LocalDate, MergedYear> byYear = new TreeMap<>();
        // 1) each filing's own year
        for (FilingYears f : ordered) {
            for (YearFigures y : f.years()) {
                if (sameYear(y.periodEnd(), f.filingPeriodEnd()) && !byYear.containsKey(y.periodEnd())) {
                    byYear.put(y.periodEnd(), new MergedYear(y, f.filingPeriodEnd(), f.viewerUrl()));
                }
            }
        }
        // 2) comparatives fill missing metrics or missing years
        for (FilingYears f : ordered) {
            for (YearFigures y : f.years()) {
                if (sameYear(y.periodEnd(), f.filingPeriodEnd())) {
                    continue;
                }
                MergedYear existing = byYear.get(y.periodEnd());
                if (existing == null) {
                    byYear.put(y.periodEnd(), new MergedYear(y, f.filingPeriodEnd(), f.viewerUrl()));
                } else if (sameCurrency(existing.figures(), y)) {
                    byYear.put(y.periodEnd(), new MergedYear(existing.figures().fillFrom(y),
                            existing.filingPeriodEnd(), existing.viewerUrl()));
                }
            }
        }
        return byYear.values().stream()
                .filter(m -> m.figures().has(Metric.REVENUE) || m.figures().has(Metric.NET_INCOME))
                .toList();
    }

    /** Filing period ends and fact dates can differ by a day or so (e.g. 52/53-week years). */
    private static boolean sameYear(LocalDate a, LocalDate b) {
        return Math.abs(a.toEpochDay() - b.toEpochDay()) <= 7;
    }

    private static boolean sameCurrency(YearFigures a, YearFigures b) {
        return a.currency() == null || b.currency() == null || a.currency().equals(b.currency());
    }
}
