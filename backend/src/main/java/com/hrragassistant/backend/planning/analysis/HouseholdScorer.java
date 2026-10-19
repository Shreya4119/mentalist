package com.hrragassistant.backend.planning.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.hrragassistant.backend.planning.analysis.HouseholdRatio.Area;
import com.hrragassistant.backend.planning.analysis.HouseholdRatio.Assessment;

/**
 * Same published method as the company health score: Healthy 100 /
 * Caution 60 / Concern 20 per ratio, averaged per area, weighted (Safety net
 * 25, Cash flow 20, Debt 15, Future 25, Investing quality 15) over the areas
 * that have data. Strong >= 75, Stable 55-74, Watch 35-54, Weak < 35; fewer
 * than 4 ratios gives "Insufficient data".
 */
public final class HouseholdScorer {

    public static final int MIN_RATIOS_FOR_SCORE = 4;

    public record AreaScore(Area area, String label, Integer score, String rating, int ratiosUsed, int weight) {
    }

    public record HouseholdScore(Integer score, String rating, String summary, List<AreaScore> areas, int ratiosUsed) {
    }

    private HouseholdScorer() {
    }

    public static HouseholdScore score(List<HouseholdRatio> ratios) {
        List<AreaScore> areas = new ArrayList<>();
        double weighted = 0;
        int weightTotal = 0, used = 0;
        for (Area area : Area.values()) {
            List<Integer> pts = ratios.stream().filter(r -> r.area() == area && r.computed()).map(r -> points(r.assessment())).toList();
            if (pts.isEmpty()) {
                areas.add(new AreaScore(area, area.label(), null, "Insufficient data", 0, area.weight()));
                continue;
            }
            int s = (int) Math.round(pts.stream().mapToInt(Integer::intValue).average().orElse(0));
            areas.add(new AreaScore(area, area.label(), s, rating(s), pts.size(), area.weight()));
            weighted += s * (double) area.weight();
            weightTotal += area.weight();
            used += pts.size();
        }
        if (used < MIN_RATIOS_FOR_SCORE || weightTotal == 0) {
            return new HouseholdScore(null, "Insufficient data",
                    "Add a few more numbers to get a score (" + used + " of the " + MIN_RATIOS_FOR_SCORE + " ratios needed).", areas, used);
        }
        int overall = (int) Math.round(weighted / weightTotal);
        return new HouseholdScore(overall, rating(overall), summary(areas), areas, used);
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
        if (score >= 75) return "Strong";
        if (score >= 55) return "Stable";
        if (score >= 35) return "Watch";
        return "Weak";
    }

    private static String summary(List<AreaScore> areas) {
        List<AreaScore> scored = areas.stream().filter(a -> a.score() != null).toList();
        AreaScore best = scored.stream().max(Comparator.comparingInt(AreaScore::score)).orElseThrow();
        AreaScore worst = scored.stream().min(Comparator.comparingInt(AreaScore::score)).orElseThrow();
        if (worst.score() >= 75) {
            return "Every area is in good shape. Keep the habits that got you here and review once a year.";
        }
        if (best == worst || best.score().equals(worst.score())) {
            return "All areas score about the same; the next steps below show where to start.";
        }
        return "Strongest area: " + best.label() + ". Needs the most attention: " + worst.label() + "; your next steps below show where to start.";
    }
}
