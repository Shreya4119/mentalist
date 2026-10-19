package com.hrragassistant.backend.reports.analysis;

import static com.hrragassistant.backend.reports.analysis.RatioCalculatorTest.DISTRESSED;
import static com.hrragassistant.backend.reports.analysis.RatioCalculatorTest.HEALTHY;
import static com.hrragassistant.backend.reports.analysis.RatioCalculatorTest.extraction;
import static org.assertj.core.api.Assertions.assertThat;

import com.hrragassistant.backend.reports.analysis.ReportComparator.Comparison;
import com.hrragassistant.backend.reports.analysis.ReportComparator.Direction;
import org.junit.jupiter.api.Test;

class ReportComparatorTest {

    @Test
    void sameCompanyAcrossYearsListsWhatMoved() {
        ReportAnalysisView a = ReportAnalysisView.assemble(extraction(HEALTHY, false, "INR crore", "Acme Ltd"), "gemini", "m");
        ReportAnalysisView b = ReportAnalysisView.assemble(extraction(DISTRESSED, false, "INR crore", "ACME Limited"), "gemini", "m");
        Comparison c = ReportComparator.compare("1", "fy25.pdf", a, "2", "fy26.pdf", b);

        assertThat(c.sameCompany()).isTrue();
        assertThat(c.warnings()).isEmpty();
        assertThat(c.deteriorations()).anyMatch(s -> s.startsWith("Net profit margin"));
        assertThat(c.ratios()).anyMatch(r -> r.key().equals("currentRatio") && r.direction() == Direction.WORSENED);
        assertThat(c.metrics()).hasSize(15);
    }

    @Test
    void warnsWhenUnitsDiffer() {
        ReportAnalysisView a = ReportAnalysisView.assemble(extraction(HEALTHY, false, "INR crore", "Acme"), "gemini", "m");
        ReportAnalysisView b = ReportAnalysisView.assemble(extraction(HEALTHY, false, "USD millions", "Other Co"), "claude", "m");

        assertThat(ReportComparator.compare("1", "a", a, "2", "b", b).warnings()).anyMatch(w -> w.contains("units"));
    }
}
