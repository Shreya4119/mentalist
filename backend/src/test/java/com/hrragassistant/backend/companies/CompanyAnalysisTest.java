package com.hrragassistant.backend.companies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.hrragassistant.backend.companies.analysis.CompanyAnalyzer;
import com.hrragassistant.backend.companies.analysis.CompanyHistory;
import com.hrragassistant.backend.companies.xbrl.FigureMapper;
import com.hrragassistant.backend.companies.xbrl.FilingMerger;
import com.hrragassistant.backend.companies.xbrl.Metric;
import com.hrragassistant.backend.companies.xbrl.XbrlFact;
import com.hrragassistant.backend.companies.xbrl.XbrlJsonReader;
import com.hrragassistant.backend.companies.xbrl.YearFigures;

class CompanyAnalysisTest {

    private static List<YearFigures> sampleYears() throws Exception {
        try (InputStream in = CompanyAnalysisTest.class.getResourceAsStream("/esef/sample-fy2024.json")) {
            return FigureMapper.years(XbrlJsonReader.read(new ObjectMapper().readTree(in)));
        }
    }

    @Test
    void readsBothYearsOfAFilingAndIgnoresSegmentsHalfYearsAndOtherCurrencies() throws Exception {
        List<YearFigures> ys = sampleYears();
        assertThat(ys).extracting(YearFigures::periodEnd)
                .containsExactly(LocalDate.of(2023, 12, 31), LocalDate.of(2024, 12, 31));
        YearFigures fy24 = ys.get(1);
        assertThat(fy24.currency()).isEqualTo("EUR");
        assertThat(fy24.get(Metric.REVENUE)).isEqualTo(1_200_000_000d); // not the segment, half-year or USD figure
        assertThat(fy24.values().get(Metric.REVENUE).concept()).isEqualTo("ifrs-full:Revenue");
        assertThat(fy24.get(Metric.TOTAL_ASSETS)).isEqualTo(2_000_000_000d); // instant at 2025-01-01T00:00 = 31 Dec 2024
        assertThat(fy24.get(Metric.CAPEX)).isEqualTo(90_000_000d);
        assertThat(fy24.get(Metric.COST_OF_SALES)).isEqualTo(700_000_000d); // reported negative, stored as a cost
        assertThat(fy24.get(Metric.SHARES_OUTSTANDING)).isEqualTo(50_000_000d);
    }

    @Test
    void scoresMatchTheirFormulas() throws Exception {
        CompanyHistory h = CompanyAnalyzer.analyze(sampleYears());
        CompanyHistory.Altman z = h.altman().get(1);
        // 6.56*0.2 + 3.26*0.3 + 6.72*0.12 + 1.05*(900/1100)
        assertThat(z.z()).isCloseTo(3.9555, within(0.001));
        assertThat(z.zone()).isEqualTo("SAFE");
        assertThat(h.piotroski()).hasSize(1);
        assertThat(h.piotroski().get(0).score()).isEqualTo(9);
        assertThat(h.piotroski().get(0).known()).isEqualTo(9);
        assertThat(h.beneish().get(0).m()).isCloseTo(-2.4932, within(0.001));
        assertThat(h.beneish().get(0).flagged()).isFalse();
        assertThat(h.years().get(1).ratios().get("revenueGrowth")).isCloseTo(0.2, within(1e-9));
        assertThat(h.trends().revenueCagr()).isCloseTo(0.2, within(0.002));
    }

    @Test
    void periodEndsFollowTheXbrlJsonConvention() {
        assertThat(XbrlJsonReader.read(new ObjectMapper().createObjectNode()).isEmpty()).isTrue();
        XbrlFact year = new XbrlFact("c", 1, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), "iso4217:EUR", false);
        assertThat(year.fullYear()).isTrue();
        assertThat(year.currency()).isEqualTo("EUR");
        XbrlFact half = new XbrlFact("c", 1, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30), "iso4217:EUR", false);
        assertThat(half.fullYear()).isFalse();
    }

    private static YearFigures year(int y, Map<Metric, Double> values) {
        Map<Metric, YearFigures.Value> v = new EnumMap<>(Metric.class);
        values.forEach((m, a) -> v.put(m, new YearFigures.Value(a, "test:" + m)));
        return new YearFigures(LocalDate.of(y, 12, 31), "EUR", v);
    }

    @Test
    void warningPatternsFireWithEvidence() {
        List<YearFigures> ys = List.of(
                year(2021, Map.of(Metric.REVENUE, 100d, Metric.NET_INCOME, 10d, Metric.OPERATING_CASH_FLOW, 12d,
                        Metric.RECEIVABLES, 20d, Metric.SHARES_OUTSTANDING, 100d)),
                year(2022, Map.of(Metric.REVENUE, 105d, Metric.NET_INCOME, 12d, Metric.OPERATING_CASH_FLOW, 8d,
                        Metric.RECEIVABLES, 30d, Metric.SHARES_OUTSTANDING, 110d)),
                year(2023, Map.of(Metric.REVENUE, 95d, Metric.NET_INCOME, 11d, Metric.OPERATING_CASH_FLOW, 7d,
                        Metric.RECEIVABLES, 31d, Metric.SHARES_OUTSTANDING, 110d)));
        CompanyHistory h = CompanyAnalyzer.analyze(ys);
        assertThat(h.warnings()).extracting(CompanyHistory.Finding::key)
                .contains("receivablesOutpaceSales", "profitUpCashDown", "dilution", "cashBelowProfit");
        assertThat(h.warnings()).filteredOn(f -> f.key().equals("receivablesOutpaceSales")).first()
                .satisfies(f -> assertThat(f.evidence()).contains("50.0%").contains("5.0%"));
        // not enough figures: scores say so instead of guessing
        assertThat(h.altman().get(0).z()).isNull();
        assertThat(h.altman().get(0).missing()).contains("Total assets");
        assertThat(h.beneish().get(0).m()).isNull();
    }

    @Test
    void mergingPrefersEachYearsOwnFilingAndFillsGapsFromComparatives() {
        YearFigures own2023 = year(2023, Map.of(Metric.REVENUE, 100d));
        YearFigures restated2023 = year(2023, Map.of(Metric.REVENUE, 99d, Metric.NET_INCOME, 9d));
        YearFigures own2024 = year(2024, Map.of(Metric.REVENUE, 120d));
        YearFigures comp2022 = year(2022, Map.of(Metric.REVENUE, 90d));
        List<FilingMerger.MergedYear> merged = FilingMerger.merge(List.of(
                new FilingMerger.FilingYears(LocalDate.of(2024, 12, 31), "v24", List.of(restated2023, own2024)),
                new FilingMerger.FilingYears(LocalDate.of(2023, 12, 31), "v23", List.of(comp2022, own2023))));
        assertThat(merged).extracting(m -> m.figures().periodEnd().getYear()).containsExactly(2022, 2023, 2024);
        FilingMerger.MergedYear y23 = merged.get(1);
        assertThat(y23.figures().get(Metric.REVENUE)).isEqualTo(100d); // as originally reported
        assertThat(y23.figures().get(Metric.NET_INCOME)).isEqualTo(9d); // filled from the later filing
        assertThat(y23.viewerUrl()).isEqualTo("v23");
        assertThat(merged.get(0).viewerUrl()).isEqualTo("v23");
    }

    @Test
    void oneFilingPerYearWithTheFewestErrors() {
        FilingsClient.Filing a = new FilingsClient.Filing("1", LocalDate.of(2024, 12, 31), "NL", "/a.json", "va", 3, "2025-03-01");
        FilingsClient.Filing b = new FilingsClient.Filing("2", LocalDate.of(2024, 12, 31), "NL", "/b.json", "vb", 0, "2025-02-01");
        FilingsClient.Filing noJson = new FilingsClient.Filing("3", LocalDate.of(2023, 12, 31), "NL", null, "vc", 0, "2024-03-01");
        assertThat(CompanyService.chooseFilings(List.of(a, b, noJson))).extracting(FilingsClient.Filing::id).containsExactly("2");
    }

    @Test
    void storedFiguresRoundTrip() {
        YearFigures y = year(2024, Map.of(Metric.REVENUE, 1.5e9, Metric.EQUITY, -2e6));
        Map<Metric, YearFigures.Value> back = CompanyService.fromJson(CompanyService.toJson(y));
        assertThat(back.get(Metric.REVENUE).amount()).isEqualTo(1.5e9);
        assertThat(back.get(Metric.EQUITY).concept()).isEqualTo("test:EQUITY");
    }

    @Test
    void countryListKeepsEachCompanysLatestFilingYear() throws Exception {
        String page = """
            {"data":[
              {"type":"filing","id":"1","attributes":{"period_end":"2023-12-31"},"relationships":{"entity":{"data":{"type":"entity","id":"7"}}}},
              {"type":"filing","id":"2","attributes":{"period_end":"2024-12-31"},"relationships":{"entity":{"data":{"type":"entity","id":"7"}}}},
              {"type":"filing","id":"3","attributes":{"period_end":"bad"},"relationships":{"entity":{"data":{"type":"entity","id":"8"}}}},
              {"type":"filing","id":"4","attributes":{"period_end":"2024-06-30"},"relationships":{"entity":{"data":{"type":"entity","id":"99"}}}}],
             "included":[
              {"type":"entity","id":"7","attributes":{"identifier":"724500ABCDEFGHIJ0001","name":" Adyen N.V. "}},
              {"type":"entity","id":"8","attributes":{"identifier":"724500ABCDEFGHIJ0002","name":"ASML Holding N.V."}}]}
            """;
        Map<String, DirectoryEntry> byLei = new java.util.LinkedHashMap<>();
        int count = FilingsDirectory.addPage(new ObjectMapper().readTree(page), "NL", byLei);
        assertThat(count).isEqualTo(4);
        assertThat(byLei).hasSize(2);
        assertThat(byLei.get("724500ABCDEFGHIJ0001").name()).isEqualTo("Adyen N.V.");
        assertThat(byLei.get("724500ABCDEFGHIJ0001").lastPeriodEnd()).isEqualTo(LocalDate.of(2024, 12, 31));
        assertThat(byLei.get("724500ABCDEFGHIJ0002").lastPeriodEnd()).isNull();
    }
}
