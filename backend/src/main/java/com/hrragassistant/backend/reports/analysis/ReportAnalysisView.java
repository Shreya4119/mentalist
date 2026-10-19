package com.hrragassistant.backend.reports.analysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.hrragassistant.backend.reports.analysis.BalanceSheetExtraction.FigurePair;
import com.hrragassistant.backend.reports.analysis.HealthScorer.HealthScore;

/**
 * The complete, display-ready analysis of one report: what the model
 * extracted, plus everything computed from it. Built by {@link #assemble}
 * and stored as JSON, so re-reading a report never calls the model again.
 */
public record ReportAnalysisView(
        BalanceSheetExtraction extraction,
        List<KeyMetric> metrics,
        List<Ratio> ratios,
        HealthScore health,
        String provider,
        String model,
        String generatedAt,
        String disclaimer) {

    public static final String DISCLAIMER = "Educational analysis of the figures in this report, generated with AI "
            + "and computed with standard formulas. It is not investment advice. Figures can be misread, so check "
            + "anything important against the original report, and consult a licensed financial adviser before "
            + "investing.";

    /** One headline line item, with numbers for charts and text for tables. */
    public record KeyMetric(String key, String label, Double current, Double prior, String currentDisplay,
                            String priorDisplay, Double changePct, String source) {
    }

    public static ReportAnalysisView assemble(BalanceSheetExtraction extraction, String provider, String model) {
        String unit = extraction.unit();
        List<KeyMetric> metrics = new ArrayList<>();
        for (String[] kl : BalanceSheetExtraction.FIGURE_LABELS) {
            FigurePair p = BalanceSheetExtraction.figure(extraction.figures(), kl[0]);
            if (p.current() == null && p.prior() == null) {
                continue;
            }
            Double change = p.current() != null && p.prior() != null && p.prior() != 0
                    ? (p.current() - p.prior()) / Math.abs(p.prior())
                    : null;
            metrics.add(new KeyMetric(kl[0], kl[1], p.current(), p.prior(),
                    RatioCalculator.amount(p.current(), unit), RatioCalculator.amount(p.prior(), unit),
                    change, p.source() == null ? "" : p.source()));
        }
        List<Ratio> ratios = RatioCalculator.calculate(extraction);
        HealthScore health = HealthScorer.score(ratios);
        return new ReportAnalysisView(extraction, metrics, ratios, health, provider, model, Instant.now().toString(), DISCLAIMER);
    }

    /** Plain-text digest of the analysis, indexed alongside the report so chat can cite the computed ratios. */
    public String toSearchableText(String filename) {
        StringBuilder sb = new StringBuilder();
        BalanceSheetExtraction x = extraction;
        sb.append("Mentalist AI analysis of ").append(filename).append(" (")
                .append(nullToDash(x.companyName())).append(", ").append(nullToDash(x.reportingPeriod())).append(").\n");
        if (health.score() != null) {
            sb.append("Financial health score: ").append(health.score()).append("/100 (").append(health.rating()).append("). ");
        }
        sb.append(health.summary()).append('\n');
        if (x.summary() != null) {
            sb.append("Summary: ").append(x.summary()).append('\n');
        }
        sb.append("Computed ratios:\n");
        for (Ratio r : ratios) {
            if (r.value() != null || r.assessment() != Ratio.Assessment.NOT_APPLICABLE) {
                sb.append("- ").append(r.name()).append(": ").append(r.display())
                        .append(" (").append(r.assessment()).append("; ").append(r.formula()).append(")\n");
            }
        }
        sb.append("Key figures (").append(nullToDash(x.unit())).append("):\n");
        for (KeyMetric m : metrics) {
            sb.append("- ").append(m.label()).append(": ").append(m.currentDisplay())
                    .append(" (prior ").append(m.priorDisplay()).append(")\n");
        }
        return sb.toString();
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
