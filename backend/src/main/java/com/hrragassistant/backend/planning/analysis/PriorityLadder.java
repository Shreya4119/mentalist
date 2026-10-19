package com.hrragassistant.backend.planning.analysis;

import java.util.ArrayList;
import java.util.List;

import com.hrragassistant.backend.planning.analysis.GoalProjector.GoalResult;

/**
 * The order in which a household usually builds financial security: each
 * step protects the ones after it. The first unmet step is marked NEXT.
 */
public final class PriorityLadder {

    public enum Status {
        DONE, NEXT, TODO, NOT_APPLICABLE
    }

    public record Step(int number, String key, String title, String why, Status status, String detail) {
    }

    private PriorityLadder() {
    }

    public static List<Step> build(HouseholdInput in, CountryPack pack, List<GoalResult> goals) {
        double essential = HouseholdInput.orZero(in.essentialMonthlySpending());
        double cash = HouseholdInput.orZero(in.cashSavings());
        double extra = pack.emergencyExtra(in.adultsOrOne());
        HouseholdInput.Insurance ins = in.insurance();
        boolean liability = ins != null && Boolean.TRUE.equals(ins.liability());
        boolean health = ins != null && Boolean.TRUE.equals(ins.health());
        boolean disability = ins != null && Boolean.TRUE.equals(ins.disability());
        boolean life = ins != null && Boolean.TRUE.equals(ins.termLife());
        boolean dependants = in.dependantsOrZero() > 0;
        double expensive = HouseholdInput.orZero(in.expensiveDebtBalance());
        double gross = HouseholdInput.orZero(in.grossMonthlyIncome());
        double retirement = HouseholdInput.orZero(in.monthlyRetirementSaving());
        double inv = HouseholdInput.orZero(in.investmentsTotal());

        List<Object[]> raw = new ArrayList<>();
        raw.add(new Object[] {"starter-buffer", "Starter buffer: one month of essential costs",
                "Stops a broken boiler or car repair from turning into debt.",
                essential > 0 && cash >= essential, essential > 0 ? Money.eur(cash) + " saved \u00b7 target " + Money.eur(essential) : "Add your essential spending"});
        raw.add(new Object[] {"basic-insurance", "Basic insurance: liability and health",
                in.isIndividual() ? "Cheap protection against the costs that could ruin your finances." : "Cheap protection against the costs that can ruin a household.",
                liability && health, ins == null ? "Not answered yet" : (liability ? "Liability ✓" : "Liability missing") + " · " + (health ? "Health ✓" : "Health missing")});
        raw.add(new Object[] {"expensive-debt", "Pay off expensive debt",
                "Credit-card or overdraft interest is usually far higher than any safe investment return.",
                expensive <= 0, expensive > 0 ? Money.eur(expensive) + " outstanding" : "None"});
        Boolean matchAvail = in.employerMatchAvailable();
        raw.add(new Object[] {"employer-match", "Take your employer's full pension contribution",
                "It is extra pay you only get if you contribute too.",
                Boolean.TRUE.equals(matchAvail) ? Boolean.TRUE.equals(in.employerMatchFullyUsed()) : null,
                Boolean.TRUE.equals(matchAvail) ? (Boolean.TRUE.equals(in.employerMatchFullyUsed()) ? "Fully used" : "Not fully used yet") : "No employer contribution offered"});
        double fullTarget = 6 * essential + extra;
        raw.add(new Object[] {"full-emergency-fund", "Full emergency fund: six months",
                "Covers job loss or illness without selling investments at a bad time.",
                essential > 0 && cash >= fullTarget,
                essential > 0 ? Money.eur(cash) + " saved \u00b7 target " + Money.eur(fullTarget) + (extra > 0 ? " (incl. " + Money.eur(extra) + " health-insurance own risk)" : "") : "Add your essential spending"});
        raw.add(new Object[] {"income-protection", dependants ? "Protect your income and family: disability and term life cover" : "Protect your income: disability cover",
                "Your ability to earn is usually your biggest asset.",
                disability && (!dependants || life), ins == null ? "Not answered yet" : (disability ? "Disability ✓" : "Disability missing") + (dependants ? " · " + (life ? "Term life ✓" : "Term life missing") : "")});
        long behind = goals.stream().filter(g -> !g.onTrack()).count();
        raw.add(new Object[] {"goals", "Fund your goals by their dates",
                "Money for the next few years stays safe; long-term money can grow.",
                goals.isEmpty() ? null : behind == 0, goals.isEmpty() ? "No goals added yet" : (goals.size() - behind) + " of " + goals.size() + " on track"});
        raw.add(new Object[] {"retirement", "Save 15% of gross income for retirement",
                "Time is the biggest driver of long-term growth; starting early matters more than picking winners.",
                gross > 0 ? retirement / gross >= 0.15 : null, gross > 0 ? Money.pct(retirement / gross) + " now" : "Add your gross income"});
        Double fee = in.weightedFundFeePct();
        double largestShare = inv > 0 && in.largestHoldingValue() != null ? in.largestHoldingValue() / inv : 0;
        raw.add(new Object[] {"low-cost-investing", "Invest broadly at low cost",
                "Low fees and wide diversification; own stock picks only with a small slice (about 10%).",
                inv > 0 ? (fee == null || fee <= 0.3) && largestShare <= 0.10 : null,
                inv > 0 ? "Fees " + (fee == null ? "unknown" : Money.pctPoints(fee)) + " · largest company " + Money.pct(largestShare) : "Not investing yet"});

        List<Step> steps = new ArrayList<>();
        boolean nextAssigned = false;
        for (int i = 0; i < raw.size(); i++) {
            Object[] r = raw.get(i);
            Boolean done = (Boolean) r[3];
            Status s;
            if (done == null) {
                s = Status.NOT_APPLICABLE;
            } else if (done) {
                s = Status.DONE;
            } else if (!nextAssigned) {
                s = Status.NEXT;
                nextAssigned = true;
            } else {
                s = Status.TODO;
            }
            steps.add(new Step(i + 1, (String) r[0], (String) r[1], (String) r[2], s, (String) r[4]));
        }
        return steps;
    }
}
