package com.hrragassistant.backend.reports.analysis;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.hrragassistant.backend.reports.analysis.Ratio.Assessment;
import com.hrragassistant.backend.reports.analysis.Ratio.Category;

/**
 * Turns computed ratios into a 0-100 financial health score with a fixed,
 * published method (shown in the UI under "How this score works"):
 *
 *   1. Each computed ratio scores Healthy = 100, Caution = 60, Concern = 20.
 *   2. A dimension's score is the average of its computed ratios.
 *   3. The overall score is a weighted average of the dimensions that have
 *      data: Profitability 25, Liquidity 20, Leverage 20, Cash flow 20,
 *      Growth 10, Efficiency 5 -- reweighted over the dimensions present.
 *   4. Rating: Strong ≥ 75, Stable 55-74, Watch 35-54, Weak < 35.
 *      Fewer than 4 computed ratios gives "Insufficient data" instead.
 */
public final class HealthScorer {

    public static final int MIN_RATIOS_FOR_SCORE = 4;

    private static final Map<Category, Integer> WEIGHTS = new EnumMap<>(Map.of(
            Category.PROFITABILITY, 25,
            Category.LIQUIDITY, 20,
            Category.LEVERAGE, 20,
            Category.CASH_FLOW, 20,
            Category.GROWTH, 10,
            Category.EFFICIENCY, 5));

    private HealthScorer() {
    }

    public record Dimension(Category category, String label, Integer score, String rating, int ratiosUsed, int weight) {
    }

    public record HealthScore(Integer score, String rating, String summary, List<Dimension> dimensions, int ratiosUsed) {
    }

    public static HealthScore score(List<Ratio> ratios) {
        List<Dimension> dims = new ArrayList<>();
        double weighted = 0;
        int weightTotal = 0;
        int used = 0;

        for (Category c : Category.values()) {
            List<Integer> points = ratios.stream()
                    .filter(r -> r.category() == c && r.assessment() != Assessment.NOT_APPLICABLE)
                    .map(r -> points(r.assessment()))
                    .toList();
            int weight = WEIGHTS.get(c);
            if (points.isEmpty()) {
                dims.add(new Dimension(c, c.label(), null, "Insufficient data", 0, weight));
                continue;
            }
            int s = (int) Math.round(points.stream().mapToInt(Integer::intValue).average().orElse(0));
            dims.add(new Dimension(c, c.label(), s, rating(s), points.size(), weight));
            weighted += s * (double) weight;
            weightTotal += weight;
            used += points.size();
        }

        if (used < MIN_RATIOS_FOR_SCORE || weightTotal == 0) {
            return new HealthScore(null, "Insufficient data",
                    "Not enough figures could be read from this report to score it reliably ("
                            + used + " of the " + MIN_RATIOS_FOR_SCORE + " ratios needed).",
                    dims, used);
        }

        int overall = (int) Math.round(weighted / weightTotal);
        String rating = rating(overall);
        return new HealthScore(overall, rating, summary(rating, dims), dims, used);
    }

    static int points(Assessment a) {
        return switch (a) {
            case HEALTHY -> 100;
            case CAUTION -> 60;
            case CONCERN -> 20;
            case NOT_APPLICABLE -> 0;
        };
    }

    static String rating(int score) {
        if (score >= 75) {
            return "Strong";
        }
        if (score >= 55) {
            return "Stable";
        }
        if (score >= 35) {
            return "Watch";
        }
        return "Weak";
    }

    private static String summary(String rating, List<Dimension> dims) {
        List<String> strong = dims.stream().filter(d -> d.score() != null && d.score() >= 75).map(d -> d.label().toLowerCase()).toList();
        List<String> weak = dims.stream().filter(d -> d.score() != null && d.score() < 55).map(d -> d.label().toLowerCase()).toList();
        StringBuilder sb = new StringBuilder(switch (rating) {
            case "Strong" -> "Financially strong on the figures reported.";
            case "Stable" -> "Financially stable, with some areas to watch.";
            case "Watch" -> "Mixed financial health: several areas need attention.";
            default -> "Weak financial health on the figures reported.";
        });
        if (!strong.isEmpty()) {
            sb.append(" Strongest: ").append(String.join(", ", strong)).append('.');
        }
        if (!weak.isEmpty()) {
            sb.append(" Weakest: ").append(String.join(", ", weak)).append('.');
        }
        return sb.toString();
    }
}
