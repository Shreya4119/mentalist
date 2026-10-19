package com.hrragassistant.backend.reports.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.hrragassistant.backend.reports.analysis.Ratio.Assessment;
import com.hrragassistant.backend.reports.analysis.ReportAnalysisView.KeyMetric;

/**
 * Side-by-side comparison of two analysed reports -- two years of one
 * company, or two companies. Fully deterministic: it lines up the same
 * standard figures and ratios from both analyses and ranks what moved.
 * "A" is the baseline (older report / first company), "B" the comparison.
 */
public final class ReportComparator {

    private ReportComparator() {
    }

    public enum Direction { IMPROVED, WORSENED, UNCHANGED, NOT_COMPARABLE }

    public record Side(String reportId, String filename, String companyName, String period, String currency,
                       String unit, Integer score, String rating) {
    }

    public record MetricRow(String key, String label, Double a, Double b, String aDisplay, String bDisplay,
                            Double changePct) {
    }

    public record RatioRow(String key, String name, String category, String aDisplay, String bDisplay,
                           Assessment aAssessment, Assessment bAssessment, Direction direction) {
    }

    public record Comparison(Side a, Side b, boolean sameCompany, List<String> warnings, List<MetricRow> metrics,
                             List<RatioRow> ratios, List<String> improvements, List<String> deteriorations,
                             String disclaimer) {
    }

    public static Comparison compare(String idA, String fileA, ReportAnalysisView a,
                                     String idB, String fileB, ReportAnalysisView b) {
        BalanceSheetExtraction xa = a.extraction();
        BalanceSheetExtraction xb = b.extraction();
        Side sideA = new Side(idA, fileA, xa.companyName(), xa.reportingPeriod(), xa.currency(), xa.unit(),
                a.health().score(), a.health().rating());
        Side sideB = new Side(idB, fileB, xb.companyName(), xb.reportingPeriod(), xb.currency(), xb.unit(),
                b.health().score(), b.health().rating());

        List<String> warnings = new ArrayList<>();
        boolean sameCompany = normalize(xa.companyName()).equals(normalize(xb.companyName()))
                && !normalize(xa.companyName()).isEmpty();
        if (!Objects.equals(normalize(xa.currency()), normalize(xb.currency()))) {
            warnings.add("Different currencies (" + dash(xa.currency()) + " vs " + dash(xb.currency())
                    + "): compare ratios and percentages, not absolute amounts.");
        } else if (!Objects.equals(normalize(xa.unit()), normalize(xb.unit()))) {
            warnings.add("Different units (" + dash(xa.unit()) + " vs " + dash(xb.unit())
                    + "): absolute amounts are not directly comparable.");
        }
        if (!Objects.equals(xa.financialInstitution(), xb.financialInstitution())) {
            warnings.add("One report is a financial institution and the other is not; several ratios only apply to one of them.");
        }
        if (!sameCompany && xa.industry() != null && xb.industry() != null
                && !normalize(xa.industry()).equals(normalize(xb.industry()))) {
            warnings.add("Different industries (" + xa.industry() + " vs " + xb.industry()
                    + "): healthy ranges differ by industry, so read ratio gaps with care.");
        }

        Map<String, KeyMetric> mb = b.metrics().stream().collect(Collectors.toMap(KeyMetric::key, Function.identity()));
        List<MetricRow> metrics = new ArrayList<>();
        for (KeyMetric ma : a.metrics()) {
            KeyMetric other = mb.get(ma.key());
            Double bv = other == null ? null : other.current();
            Double change = ma.current() != null && bv != null && ma.current() != 0
                    ? (bv - ma.current()) / Math.abs(ma.current())
                    : null;
            metrics.add(new MetricRow(ma.key(), ma.label(), ma.current(), bv, ma.currentDisplay(),
                    other == null ? "—" : other.currentDisplay(), change));
        }
        for (KeyMetric other : b.metrics()) {
            if (metrics.stream().noneMatch(m -> m.key().equals(other.key()))) {
                metrics.add(new MetricRow(other.key(), other.label(), null, other.current(), "—",
                        other.currentDisplay(), null));
            }
        }

        Map<String, Ratio> rb = b.ratios().stream().collect(Collectors.toMap(Ratio::key, Function.identity()));
        List<RatioRow> ratios = new ArrayList<>();
        List<String> improvements = new ArrayList<>();
        List<String> deteriorations = new ArrayList<>();
        for (Ratio ra : a.ratios()) {
            Ratio other = rb.get(ra.key());
            if (other == null) {
                continue;
            }
            Direction d = direction(ra.assessment(), other.assessment());
            ratios.add(new RatioRow(ra.key(), ra.name(), ra.category().label(), ra.display(), other.display(),
                    ra.assessment(), other.assessment(), d));
            String line = ra.name() + ": " + ra.display() + " → " + other.display()
                    + " (" + label(ra.assessment()) + " → " + label(other.assessment()) + ")";
            if (d == Direction.IMPROVED) {
                improvements.add(line);
            } else if (d == Direction.WORSENED) {
                deteriorations.add(line);
            }
        }

        // Largest relative moves in headline figures also matter, even within the same band.
        metrics.stream()
                .filter(m -> m.changePct() != null && Math.abs(m.changePct()) >= 0.20 && warnings.isEmpty())
                .sorted(Comparator.comparingDouble((MetricRow m) -> Math.abs(m.changePct())).reversed())
                .limit(3)
                .forEach(m -> {
                    String line = m.label() + " " + (m.changePct() > 0 ? "up " : "down ")
                            + String.format(Locale.ENGLISH, "%.0f%%", Math.abs(m.changePct()) * 100)
                            + " (" + m.aDisplay() + " → " + m.bDisplay() + ")";
                    boolean good = isGoodWhenUp(m.key()) == (m.changePct() > 0);
                    (good ? improvements : deteriorations).add(line);
                });

        return new Comparison(sideA, sideB, sameCompany, warnings, metrics, ratios, improvements, deteriorations,
                ReportAnalysisView.DISCLAIMER);
    }

    static Direction direction(Assessment from, Assessment to) {
        if (from == Assessment.NOT_APPLICABLE || to == Assessment.NOT_APPLICABLE) {
            return Direction.NOT_COMPARABLE;
        }
        int diff = HealthScorer.points(to) - HealthScorer.points(from);
        return diff > 0 ? Direction.IMPROVED : diff < 0 ? Direction.WORSENED : Direction.UNCHANGED;
    }

    /** Debt, liabilities and interest going up is bad; most other line items going up is good. */
    static boolean isGoodWhenUp(String key) {
        return !List.of("totalDebt", "totalLiabilities", "currentLiabilities", "interestExpense").contains(key);
    }

    private static String label(Assessment a) {
        return switch (a) {
            case HEALTHY -> "healthy";
            case CAUTION -> "caution";
            case CONCERN -> "concern";
            case NOT_APPLICABLE -> "n/a";
        };
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT)
                .replaceAll("\\b(limited|ltd|inc|plc|corp|corporation|co|the)\\b", "")
                .replaceAll("[^a-z0-9]", "");
    }

    private static String dash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
