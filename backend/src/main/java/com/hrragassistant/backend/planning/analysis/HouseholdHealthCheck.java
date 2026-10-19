package com.hrragassistant.backend.planning.analysis;

import java.util.List;

import com.hrragassistant.backend.planning.analysis.CountryPack.CountryNote;
import com.hrragassistant.backend.planning.analysis.CountryPack.Source;
import com.hrragassistant.backend.planning.analysis.GoalProjector.GoalResult;
import com.hrragassistant.backend.planning.analysis.HouseholdScorer.HouseholdScore;
import com.hrragassistant.backend.planning.analysis.PriorityLadder.Step;

/**
 * The full family financial health check: ratios, score, goals map,
 * priority ladder and country notes, computed deterministically from the
 * household's own numbers. No AI is involved.
 */
public record HouseholdHealthCheck(
        boolean individual,
        String country,
        String countryName,
        String currency,
        HouseholdScore health,
        List<HouseholdRatio> ratios,
        List<GoalResult> goals,
        List<Step> priorities,
        List<CountryNote> notes,
        Assumptions assumptions,
        String rulesVerifiedOn,
        List<Source> sources,
        String disclaimer) {

    public static final String DISCLAIMER = "Education and self-assessment only, not financial, tax or legal advice. "
            + "Thresholds are common rules of thumb and projections use simple assumptions; your situation may differ. "
            + "For important decisions, speak to an independent licensed advisor.";

    public record Assumptions(double inflationPct, double safeReturnPct, double balancedReturnPct, double growthReturnPct, String note) {
    }

    public static HouseholdHealthCheck run(HouseholdInput in, int currentYear) {
        CountryPack pack = CountryPack.of(in.country());
        List<GoalResult> goals = GoalProjector.project(in.goalsOrEmpty(), currentYear);
        List<HouseholdRatio> ratios = HouseholdRatioCalculator.compute(in, pack, goals);
        HouseholdScore score = HouseholdScorer.score(ratios);
        List<Step> steps = PriorityLadder.build(in, pack, goals);
        Assumptions assumptions = new Assumptions(
                GoalProjector.INFLATION * 100,
                GoalProjector.Bucket.SAFE.assumedReturn() * 100,
                GoalProjector.Bucket.BALANCED.assumedReturn() * 100,
                GoalProjector.Bucket.GROWTH.assumedReturn() * 100,
                "Illustrative yearly returns after costs, not forecasts. Real returns vary and can be negative for years.");
        return new HouseholdHealthCheck(in.isIndividual(), pack.country().name(), pack.countryName(), pack.currency(), score, ratios, goals, steps,
                pack.notes(in), assumptions, pack.verifiedOn(), pack.sources(), DISCLAIMER);
    }
}
