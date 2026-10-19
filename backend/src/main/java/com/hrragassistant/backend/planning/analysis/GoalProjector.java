package com.hrragassistant.backend.planning.analysis;

import java.util.List;

import com.hrragassistant.backend.planning.analysis.HouseholdInput.Goal;

/**
 * Projects each savings goal to its target year with fixed, published
 * assumptions (shown in the UI). The horizon decides how the money is
 * assumed to be invested: money needed soon stays safe, long-term money can
 * take more risk. These are illustrative assumptions, not forecasts.
 */
public final class GoalProjector {

    public static final double INFLATION = 0.02;

    public enum Bucket {
        SAFE("Safe (savings account, money-market fund)", 0.02, "up to 3 years"),
        BALANCED("Balanced (mix of bonds and shares)", 0.04, "3 to 7 years"),
        GROWTH("Growth (mostly global shares)", 0.06, "more than 7 years");

        private final String label;
        private final double assumedReturn;
        private final String horizon;

        Bucket(String label, double assumedReturn, String horizon) {
            this.label = label;
            this.assumedReturn = assumedReturn;
            this.horizon = horizon;
        }

        public String label() {
            return label;
        }

        public double assumedReturn() {
            return assumedReturn;
        }

        public String horizon() {
            return horizon;
        }

        static Bucket forYears(int years) {
            if (years <= 3) {
                return SAFE;
            }
            return years <= 7 ? BALANCED : GROWTH;
        }
    }

    public record GoalResult(
            String name,
            double targetToday,
            int targetYear,
            int years,
            Bucket bucket,
            String bucketLabel,
            double assumedReturnPct,
            double targetInFuture,
            double projected,
            double currentSaved,
            double monthlyContribution,
            double monthlyNeeded,
            boolean onTrack,
            String summary) {
    }

    private GoalProjector() {
    }

    public static List<GoalResult> project(List<Goal> goals, int currentYear) {
        return goals.stream().map(g -> project(g, currentYear)).toList();
    }

    public static GoalResult project(Goal g, int currentYear) {
        String name = g.name() == null || g.name().isBlank() ? "Goal" : g.name().trim();
        double target = HouseholdInput.orZero(g.targetAmount());
        int year = g.targetYear() == null ? currentYear : g.targetYear();
        int years = Math.max(0, year - currentYear);
        double saved = HouseholdInput.orZero(g.currentSaved());
        double monthly = HouseholdInput.orZero(g.monthlyContribution());
        Bucket bucket = Bucket.forYears(years);
        double r = bucket.assumedReturn();

        double targetFuture = target * Math.pow(1 + INFLATION, years);
        double growthOfSaved = saved * Math.pow(1 + r, years);
        double annuity = annuityFactor(r, years);
        double projected = growthOfSaved + monthly * annuity;
        double needed = annuity == 0 ? Math.max(0, targetFuture - saved) : Math.max(0, (targetFuture - growthOfSaved) / annuity);
        boolean onTrack = projected >= targetFuture - 0.5;

        String summary;
        if (onTrack) {
            summary = "On track: about " + Money.eur(projected) + " by " + year + " against a target of " + Money.eur(targetFuture)
                    + " (" + Money.eur(target) + " in today's money).";
        } else if (years == 0) {
            summary = "Due this year: " + Money.eur(Math.max(0, targetFuture - saved)) + " still missing.";
        } else {
            summary = "Behind: about " + Money.eur(projected) + " by " + year + " against " + Money.eur(targetFuture)
                    + ". Saving about " + Money.eur(Math.ceil(needed)) + " a month (now " + Money.eur(monthly) + ") would close the gap.";
        }
        return new GoalResult(name, target, year, years, bucket, bucket.label(), r * 100, round(targetFuture), round(projected),
                saved, monthly, Math.ceil(needed), onTrack, summary);
    }

    /** Future value of 1 per month for {@code years} at annual return {@code r}, compounded monthly. */
    static double annuityFactor(double r, int years) {
        int n = years * 12;
        if (n == 0) {
            return 0;
        }
        double m = r / 12;
        return m == 0 ? n : (Math.pow(1 + m, n) - 1) / m;
    }

    private static double round(double v) {
        return Math.round(v);
    }
}
