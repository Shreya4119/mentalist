package com.hrragassistant.backend.reports.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import com.hrragassistant.backend.reports.analysis.BalanceSheetExtraction.FigurePair;
import com.hrragassistant.backend.reports.analysis.BalanceSheetExtraction.Figures;
import com.hrragassistant.backend.reports.analysis.Ratio.Assessment;
import org.junit.jupiter.api.Test;

class RatioCalculatorTest {

    static FigurePair p(Double current, Double prior) {
        return new FigurePair(current, prior, "p. 1");
    }

    static BalanceSheetExtraction extraction(Figures f, boolean financial, String unit, String company) {
        return new BalanceSheetExtraction(company, "FY2026", "FY2025", "INR", unit, "IT services", financial,
                "Consolidated", f, "summary", List.of(), List.of(), List.of(), List.of(), "", List.of());
    }

    static final Figures HEALTHY = new Figures(p(1000.0, 880.0), p(200.0, 160.0), p(150.0, 120.0), p(10.0, 12.0),
            p(2000.0, 1800.0), p(900.0, 800.0), p(300.0, 250.0), p(100.0, 90.0), p(200.0, 180.0), p(800.0, 760.0),
            p(400.0, 420.0), p(300.0, 350.0), p(1200.0, 1040.0), p(180.0, 130.0), p(-60.0, -50.0));

    static final Figures DISTRESSED = new Figures(p(500.0, 600.0), p(-40.0, 10.0), p(-80.0, -5.0), p(60.0, 50.0),
            p(900.0, 950.0), p(200.0, 260.0), p(20.0, 40.0), p(80.0, 70.0), p(90.0, 100.0), p(950.0, 900.0),
            p(400.0, 300.0), p(700.0, 600.0), p(-50.0, 50.0), p(-30.0, 20.0), p(-25.0, -40.0));

    static Ratio ratio(List<Ratio> rs, String key) {
        return rs.stream().filter(r -> r.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void computesStandardRatiosWithFormulasShowingTheFigures() {
        List<Ratio> rs = RatioCalculator.calculate(extraction(HEALTHY, false, "INR crore", "Acme Ltd"));

        assertThat(ratio(rs, "currentRatio").value()).isCloseTo(2.25, within(1e-9));
        assertThat(ratio(rs, "currentRatio").display()).isEqualTo("2.25x");
        assertThat(ratio(rs, "quickRatio").value()).isCloseTo(2.0, within(1e-9));
        assertThat(ratio(rs, "netMargin").display()).isEqualTo("15.0%");
        assertThat(ratio(rs, "debtToEquity").value()).isCloseTo(0.25, within(1e-9));
        assertThat(ratio(rs, "interestCoverage").value()).isCloseTo(20.0, within(1e-9));
        assertThat(ratio(rs, "cashConversion").value()).isCloseTo(1.2, within(1e-9));
        assertThat(ratio(rs, "freeCashFlow").display()).isEqualTo("120 INR crore");
        assertThat(ratio(rs, "revenueGrowth").value()).isCloseTo(120.0 / 880.0, within(1e-9));
        assertThat(ratio(rs, "roe").formula()).contains("Net income (150 INR crore)", "Shareholders' equity (1,200 INR crore)");
        assertThat(rs).filteredOn(r -> r.assessment() != Assessment.NOT_APPLICABLE).hasSize(16);
    }

    @Test
    void flagsNegativeEquityLossesAndWeakLiquidity() {
        List<Ratio> rs = RatioCalculator.calculate(extraction(DISTRESSED, false, "USD millions", "Shaky Inc"));

        assertThat(ratio(rs, "roe").display()).isEqualTo("Negative equity");
        assertThat(ratio(rs, "roe").assessment()).isEqualTo(Assessment.CONCERN);
        assertThat(ratio(rs, "currentRatio").assessment()).isEqualTo(Assessment.CONCERN);
        assertThat(ratio(rs, "cashConversion").assessment()).isEqualTo(Assessment.NOT_APPLICABLE);
        assertThat(ratio(rs, "netIncomeGrowth").value()).isNull();
        assertThat(ratio(rs, "netIncomeGrowth").formula()).startsWith("Not meaningful");
    }

    @Test
    void skipsOperatingCompanyRatiosForBanks() {
        List<Ratio> rs = RatioCalculator.calculate(extraction(HEALTHY, true, "INR crore", "Big Bank"));

        assertThat(ratio(rs, "currentRatio").assessment()).isEqualTo(Assessment.NOT_APPLICABLE);
        assertThat(ratio(rs, "debtToEquity").display()).isEqualTo("Not applicable");
        assertThat(ratio(rs, "roe").assessment()).isNotEqualTo(Assessment.NOT_APPLICABLE);
    }

    @Test
    void zeroInterestMeansNoInterestBurden() {
        Figures noDebt = new Figures(p(100.0, 90.0), p(20.0, 15.0), p(15.0, 12.0), p(0.0, 0.0), p(200.0, 180.0),
                null, null, null, null, null, null, p(0.0, 0.0), p(150.0, 130.0), null, null);
        Ratio coverage = ratio(RatioCalculator.calculate(extraction(noDebt, false, "", "Z")), "interestCoverage");

        assertThat(coverage.display()).isEqualTo("No interest");
        assertThat(coverage.assessment()).isEqualTo(Assessment.HEALTHY);
    }

    @Test
    void missingFiguresGiveNotApplicableWithAReason() {
        Figures sparse = new Figures(p(100.0, null), null, p(5.0, null), null, null, null, null, null, null, null,
                null, null, null, null, null);
        Ratio roa = ratio(RatioCalculator.calculate(extraction(sparse, false, "", "Tiny")), "roa");

        assertThat(roa.assessment()).isEqualTo(Assessment.NOT_APPLICABLE);
        assertThat(roa.formula()).contains("missing: total assets");
    }
}
