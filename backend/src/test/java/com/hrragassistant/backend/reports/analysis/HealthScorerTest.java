package com.hrragassistant.backend.reports.analysis;

import static com.hrragassistant.backend.reports.analysis.RatioCalculatorTest.DISTRESSED;
import static com.hrragassistant.backend.reports.analysis.RatioCalculatorTest.HEALTHY;
import static com.hrragassistant.backend.reports.analysis.RatioCalculatorTest.extraction;
import static com.hrragassistant.backend.reports.analysis.RatioCalculatorTest.p;
import static org.assertj.core.api.Assertions.assertThat;

import com.hrragassistant.backend.reports.analysis.BalanceSheetExtraction.Figures;
import com.hrragassistant.backend.reports.analysis.HealthScorer.HealthScore;
import org.junit.jupiter.api.Test;

class HealthScorerTest {

    @Test
    void healthyCompanyScoresStrong() {
        HealthScore h = HealthScorer.score(RatioCalculator.calculate(extraction(HEALTHY, false, "INR crore", "Acme")));

        assertThat(h.score()).isGreaterThanOrEqualTo(75);
        assertThat(h.rating()).isEqualTo("Strong");
        assertThat(h.dimensions()).hasSize(6);
    }

    @Test
    void distressedCompanyScoresWeak() {
        HealthScore h = HealthScorer.score(RatioCalculator.calculate(extraction(DISTRESSED, false, "USD m", "Shaky")));

        assertThat(h.rating()).isEqualTo("Weak");
        assertThat(h.score()).isLessThan(35);
    }

    @Test
    void tooFewRatiosGivesInsufficientDataInsteadOfAScore() {
        Figures sparse = new Figures(p(100.0, null), null, p(5.0, null), null, null, null, null, null, null, null,
                null, null, null, null, null);
        HealthScore h = HealthScorer.score(RatioCalculator.calculate(extraction(sparse, false, "", "Tiny")));

        assertThat(h.score()).isNull();
        assertThat(h.rating()).isEqualTo("Insufficient data");
    }

    @Test
    void ratingBands() {
        assertThat(HealthScorer.rating(75)).isEqualTo("Strong");
        assertThat(HealthScorer.rating(74)).isEqualTo("Stable");
        assertThat(HealthScorer.rating(55)).isEqualTo("Stable");
        assertThat(HealthScorer.rating(54)).isEqualTo("Watch");
        assertThat(HealthScorer.rating(35)).isEqualTo("Watch");
        assertThat(HealthScorer.rating(34)).isEqualTo("Weak");
    }
}
