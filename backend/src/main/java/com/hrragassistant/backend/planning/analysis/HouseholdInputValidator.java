package com.hrragassistant.backend.planning.analysis;

import java.util.ArrayList;
import java.util.List;

/** Plain-language validation errors for a household form; empty list = valid. */
public final class HouseholdInputValidator {

    private HouseholdInputValidator() {
    }

    public static List<String> validate(HouseholdInput in, int currentYear) {
        List<String> e = new ArrayList<>();
        if (in == null) {
            return List.of("The form is empty.");
        }
        if (in.country() == null) e.add("Choose your country.");
        // For an individual check, adults and dependants are ignored (fixed at 1 and 0).
        if (!in.isIndividual() && in.adults() != null && (in.adults() < 1 || in.adults() > 2)) e.add("Adults must be 1 or 2.");
        if (!in.isIndividual() && in.dependants() != null && (in.dependants() < 0 || in.dependants() > 20)) e.add("Dependants must be between 0 and 20.");
        if (in.netMonthlyIncome() == null || in.netMonthlyIncome() <= 0) e.add("Net monthly income must be more than 0.");
        if (in.essentialMonthlySpending() == null || in.essentialMonthlySpending() <= 0) e.add("Essential monthly spending must be more than 0.");
        nonNegative(e, in.grossMonthlyIncome(), "Gross monthly income");
        nonNegative(e, in.totalMonthlySpending(), "Total monthly spending");
        nonNegative(e, in.monthlyHousingCost(), "Housing cost");
        nonNegative(e, in.cashSavings(), "Cash savings");
        nonNegative(e, in.monthlyDebtPayments(), "Debt repayments");
        nonNegative(e, in.expensiveDebtBalance(), "Expensive debt");
        nonNegative(e, in.monthlyRetirementSaving(), "Retirement saving");
        nonNegative(e, in.investmentsTotal(), "Investments");
        nonNegative(e, in.largestHoldingValue(), "Largest holding");
        if (in.totalMonthlySpending() != null && in.essentialMonthlySpending() != null
                && in.totalMonthlySpending() < in.essentialMonthlySpending()) {
            e.add("Total spending can't be lower than essential spending.");
        }
        if (in.grossMonthlyIncome() != null && in.netMonthlyIncome() != null && in.grossMonthlyIncome() > 0
                && in.grossMonthlyIncome() < in.netMonthlyIncome()) {
            e.add("Gross income can't be lower than net income.");
        }
        if (in.largestHoldingValue() != null && in.investmentsTotal() != null && in.largestHoldingValue() > in.investmentsTotal()) {
            e.add("Largest holding can't be more than your total investments.");
        }
        if (in.weightedFundFeePct() != null && (in.weightedFundFeePct() < 0 || in.weightedFundFeePct() > 5)) {
            e.add("Average fund fee must be between 0% and 5%.");
        }
        List<HouseholdInput.Goal> goals = in.goalsOrEmpty();
        if (goals.size() > 10) e.add("Add at most 10 goals.");
        for (int i = 0; i < goals.size(); i++) {
            HouseholdInput.Goal g = goals.get(i);
            String label = "Goal " + (i + 1);
            if (g.name() == null || g.name().isBlank()) e.add(label + ": give it a name.");
            if (g.targetAmount() == null || g.targetAmount() <= 0) e.add(label + ": the target amount must be more than 0.");
            if (g.targetYear() == null || g.targetYear() < currentYear || g.targetYear() > currentYear + 60) {
                e.add(label + ": the target year must be between " + currentYear + " and " + (currentYear + 60) + ".");
            }
            nonNegative(e, g.currentSaved(), label + " amount saved");
            nonNegative(e, g.monthlyContribution(), label + " monthly saving");
        }
        return e;
    }

    private static void nonNegative(List<String> e, Double v, String field) {
        if (v != null && v < 0) {
            e.add(field + " can't be negative.");
        }
    }
}
