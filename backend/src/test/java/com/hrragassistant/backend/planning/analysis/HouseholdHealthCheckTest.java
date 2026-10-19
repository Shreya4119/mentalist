package com.hrragassistant.backend.planning.analysis;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.hrragassistant.backend.planning.analysis.HouseholdInput.Country;
import com.hrragassistant.backend.planning.analysis.HouseholdInput.Goal;
import com.hrragassistant.backend.planning.analysis.HouseholdInput.Insurance;
import com.hrragassistant.backend.planning.analysis.HouseholdRatio.Assessment;
import com.hrragassistant.backend.planning.analysis.PriorityLadder.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HouseholdHealthCheckTest {

    private static final int YEAR = 2026;

    static HouseholdInput familyNL() {
        return new HouseholdInput(Country.NL, 2, 1, 5200.0, 7600.0, 2600.0, 4300.0, 1500.0, 11000.0, 150.0, 0.0, 600.0,
                true, false, 42000.0, 9000.0, 0.45, new Insurance(true, true, false, false),
                List.of(new Goal("Home deposit", 40000.0, 2030, 15000.0, 300.0),
                        new Goal("Child's education", 30000.0, 2042, 2000.0, 100.0)), false);
    }

    static HouseholdInput singleDE() {
        return new HouseholdInput(Country.DE, 1, 0, 3100.0, 4800.0, 1500.0, 2300.0, 900.0, 10500.0, 0.0, 0.0, 750.0,
                false, null, 18000.0, 0.0, 0.2, new Insurance(true, true, true, false),
                List.of(new Goal("Emergency car", 8000.0, 2028, 6000.0, 150.0)), true);
    }

    static Map<String, HouseholdRatio> byKey(HouseholdHealthCheck h) {
        return h.ratios().stream().collect(Collectors.toMap(HouseholdRatio::key, Function.identity()));
    }

    @Test
    void dutchFamilyRatiosUseTheirOwnNumbers() {
        HouseholdHealthCheck h = HouseholdHealthCheck.run(familyNL(), YEAR);
        Map<String, HouseholdRatio> r = byKey(h);

        assertThat(r.get("emergencyFund").value()).isCloseTo(4.23, within(0.01));
        assertThat(r.get("emergencyFund").assessment()).isEqualTo(Assessment.CAUTION);
        assertThat(r.get("emergencyFund").nextStep()).contains("€16,370").contains("own risk");
        assertThat(r.get("insuranceCover").assessment()).isEqualTo(Assessment.CONCERN);
        assertThat(r.get("savingsRate").value()).isCloseTo(0.1731, within(0.0001));
        assertThat(r.get("housingCost").assessment()).isEqualTo(Assessment.HEALTHY);
        assertThat(r.get("consumerDebt").assessment()).isEqualTo(Assessment.HEALTHY);
        assertThat(r.get("retirementSaving").assessment()).isEqualTo(Assessment.CONCERN);
        assertThat(r.get("goalsOnTrack").display()).isEqualTo("0 of 2");
        assertThat(r.get("feeDrag").assessment()).isEqualTo(Assessment.CAUTION);
        assertThat(r.get("concentration").assessment()).isEqualTo(Assessment.CAUTION);

        assertThat(h.health().score()).isEqualTo(55);
        assertThat(h.health().rating()).isEqualTo("Stable");
        assertThat(h.notes()).extracting(CountryPack.CountryNote::key).containsExactly("box3", "own-risk");
        assertThat(h.notes().get(0).text()).contains("€118,714").contains("below");
    }

    @Test
    void priorityLadderMarksExactlyOneNextStep() {
        List<PriorityLadder.Step> steps = HouseholdHealthCheck.run(familyNL(), YEAR).priorities();
        assertThat(steps).hasSize(9);
        assertThat(steps.stream().filter(s -> s.status() == Status.NEXT)).hasSize(1);
        assertThat(steps.get(3).key()).isEqualTo("employer-match");
        assertThat(steps.get(3).status()).isEqualTo(Status.NEXT);
    }

    @Test
    void healthyGermanSingleScoresStrong() {
        HouseholdHealthCheck h = HouseholdHealthCheck.run(singleDE(), YEAR);
        assertThat(h.health().score()).isEqualTo(100);
        assertThat(h.priorities().get(3).status()).isEqualTo(Status.NOT_APPLICABLE);
        assertThat(h.notes().get(0).text()).contains("Freistellungsauftrag");
    }

    @Test
    void tooFewNumbersGiveNoScore() {
        HouseholdInput sparse = new HouseholdInput(Country.DE, 1, 0, 2500.0, null, 1800.0, null, null, 1000.0, null, 2000.0,
                null, null, null, null, null, null, null, List.of(), null);
        HouseholdHealthCheck h = HouseholdHealthCheck.run(sparse, YEAR);
        assertThat(h.health().score()).isNull();
        assertThat(h.health().rating()).isEqualTo("Insufficient data");
        assertThat(byKey(h).get("consumerDebt").assessment()).isEqualTo(Assessment.NOT_APPLICABLE);
    }

    @Test
    void expensiveDebtIsNeverHealthy() {
        HouseholdInput in = new HouseholdInput(Country.DE, 1, 0, 3000.0, null, 1500.0, 2000.0, null, 5000.0, 50.0, 1200.0,
                null, null, null, null, null, null, null, List.of(), null);
        assertThat(byKey(HouseholdHealthCheck.run(in, YEAR)).get("consumerDebt").assessment()).isEqualTo(Assessment.CAUTION);
    }

    @Test
    void goalProjectionAdjustsForInflationAndHorizon() {
        GoalProjector.GoalResult g = GoalProjector.project(new Goal("Home deposit", 40000.0, 2030, 15000.0, 300.0), YEAR);
        assertThat(g.bucket()).isEqualTo(GoalProjector.Bucket.BALANCED);
        assertThat(g.targetInFuture()).isCloseTo(40000 * Math.pow(1.02, 4), within(1.0));
        assertThat(g.onTrack()).isFalse();
        assertThat(g.monthlyNeeded()).isGreaterThan(300);

        GoalProjector.GoalResult due = GoalProjector.project(new Goal("Now", 1000.0, YEAR, 400.0, 50.0), YEAR);
        assertThat(due.monthlyNeeded()).isEqualTo(600);
    }

    @Test
    void individualCheckIsForOnePersonWhateverWasSent() {
        HouseholdInput in = new HouseholdInput(Country.NL, 2, 3, 3000.0, 4200.0, 1500.0, 2200.0, 900.0, 70000.0, 0.0, 0.0, 400.0,
                null, null, null, null, null, new Insurance(true, true, true, false), List.of(), true);
        HouseholdHealthCheck h = HouseholdHealthCheck.run(in, YEAR);
        assertThat(h.individual()).isTrue();
        assertThat(byKey(h).get("insuranceCover").display()).isEqualTo("3 of 3 in place");
        assertThat(byKey(h).get("insuranceCover").meaning()).contains("protect you");
        assertThat(h.notes().get(0).text()).contains("for one person").contains("€59,357").contains("above");
        // Own risk counted once (one adult): 6 x 1,500 + 385.
        assertThat(h.priorities().get(4).detail()).contains("target €9,385");
    }

    @Test
    void validatorExplainsEveryProblem() {
        assertThat(HouseholdInputValidator.validate(familyNL(), YEAR)).isEmpty();
        HouseholdInput bad = new HouseholdInput(null, 3, -1, 0.0, 10.0, 0.0, null, null, -5.0, null, null, null, null, null,
                10.0, 20.0, 9.0, null, List.of(new Goal("", 0.0, 2000, null, null)), null);
        assertThat(HouseholdInputValidator.validate(bad, YEAR))
                .contains("Choose your country.", "Largest holding can't be more than your total investments.")
                .hasSizeGreaterThanOrEqualTo(10);
    }
}
