package com.hrragassistant.backend.planning.analysis;

import java.util.List;

/**
 * What a person or household tells Mentalist about its finances. With
 * {@code individual = true} the check is for one person: adults is 1 and
 * dependants 0 whatever was sent, and the wording says "you", not
 * "your household". All amounts are in the
 * country's currency (EUR for DE and NL). Monthly figures are per month.
 * Optional fields may be null; ratios that need them are then shown as
 * "not enough information" rather than guessed.
 */
public record HouseholdInput(
        Country country,
        Integer adults,
        Integer dependants,
        Double netMonthlyIncome,
        Double grossMonthlyIncome,
        Double essentialMonthlySpending,
        Double totalMonthlySpending,
        Double monthlyHousingCost,
        Double cashSavings,
        Double monthlyDebtPayments,
        Double expensiveDebtBalance,
        Double monthlyRetirementSaving,
        Boolean employerMatchAvailable,
        Boolean employerMatchFullyUsed,
        Double investmentsTotal,
        Double largestHoldingValue,
        Double weightedFundFeePct,
        Insurance insurance,
        List<Goal> goals,
        Boolean individual) {

    public enum Country {
        DE, NL
    }

    /** Which covers are in place. Health insurance is mandatory in DE and NL but asked anyway. */
    public record Insurance(Boolean liability, Boolean health, Boolean disability, Boolean termLife) {
    }

    /** A savings goal in today's money, e.g. a home deposit of 40,000 by 2031. */
    public record Goal(String name, Double targetAmount, Integer targetYear, Double currentSaved, Double monthlyContribution) {
    }

    public boolean isIndividual() {
        return Boolean.TRUE.equals(individual);
    }

    public int adultsOrOne() {
        if (isIndividual()) {
            return 1;
        }
        return adults == null || adults < 1 ? 1 : Math.min(adults, 2);
    }

    public int dependantsOrZero() {
        if (isIndividual()) {
            return 0;
        }
        return dependants == null || dependants < 0 ? 0 : dependants;
    }

    public List<Goal> goalsOrEmpty() {
        return goals == null ? List.of() : goals;
    }

    static double orZero(Double d) {
        return d == null ? 0 : d;
    }
}
