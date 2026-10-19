package com.hrragassistant.backend.planning.analysis;

import java.util.ArrayList;
import java.util.List;

import com.hrragassistant.backend.planning.analysis.GoalProjector.GoalResult;
import com.hrragassistant.backend.planning.analysis.HouseholdInput.Insurance;
import com.hrragassistant.backend.planning.analysis.HouseholdRatio.Area;
import com.hrragassistant.backend.planning.analysis.HouseholdRatio.Assessment;

/**
 * Computes the nine household ratios with fixed formulas and common
 * rule-of-thumb thresholds (shown next to every ratio). Thresholds are the
 * same in every country; the country pack only adds local context.
 */
public final class HouseholdRatioCalculator {

    private HouseholdRatioCalculator() {
    }

    public static List<HouseholdRatio> compute(HouseholdInput in, CountryPack pack, List<GoalResult> goals) {
        List<HouseholdRatio> out = new ArrayList<>();
        out.add(emergencyFund(in, pack));
        out.add(insuranceCover(in, pack));
        out.add(savingsRate(in));
        out.add(housingCost(in));
        out.add(consumerDebt(in));
        out.add(retirementSaving(in));
        out.add(goalsOnTrack(goals));
        out.add(feeDrag(in));
        out.add(concentration(in));
        return out;
    }

    static HouseholdRatio emergencyFund(HouseholdInput in, CountryPack pack) {
        String key = "emergencyFund", name = "Emergency fund";
        String bench = "Healthy ≥ 6 months · Caution 3–6 · Concern < 3";
        Double essential = in.essentialMonthlySpending();
        if (essential == null || essential <= 0 || in.cashSavings() == null) {
            return na(key, name, Area.SAFETY_NET, bench, "Add your cash savings and essential monthly spending to see this.");
        }
        double cash = in.cashSavings();
        double months = cash / essential;
        Assessment a = months >= 6 ? Assessment.HEALTHY : months >= 3 ? Assessment.CAUTION : Assessment.CONCERN;
        double extra = pack.emergencyExtra(in.adultsOrOne());
        String meaning = "Your savings would cover " + Money.months(months) + " of essential costs if your income stopped.";
        String next = a == Assessment.HEALTHY
                ? "Keep it in an instant-access account; anything above about 6 months can be put to work for your goals."
                : "Build towards 6 months (" + Money.eur(6 * essential + extra) + (extra > 0 ? ", including the health-insurance own risk" : "")
                        + "): about " + Money.eur(Math.max(0, 6 * essential + extra - cash)) + " more.";
        return new HouseholdRatio(key, name, Area.SAFETY_NET, round2(months), Money.months(months),
                Money.eur(cash) + " cash savings ÷ " + Money.eur(essential) + " essential spending a month = " + Money.months(months),
                a, bench, meaning, next);
    }

    static HouseholdRatio insuranceCover(HouseholdInput in, CountryPack pack) {
        String key = "insuranceCover", name = "Insurance cover";
        String bench = "Healthy: all in place · Caution: one gap · Concern: liability or health missing, or two gaps";
        Insurance ins = in.insurance();
        if (ins == null) {
            return na(key, name, Area.SAFETY_NET, bench, "Tick which insurances you have to see this.");
        }
        boolean needLife = in.dependantsOrZero() > 0;
        List<String> missing = new ArrayList<>();
        int needed = needLife ? 4 : 3;
        if (!Boolean.TRUE.equals(ins.liability())) missing.add(pack.insuranceNames().get("liability"));
        if (!Boolean.TRUE.equals(ins.health())) missing.add(pack.insuranceNames().get("health"));
        if (!Boolean.TRUE.equals(ins.disability())) missing.add(pack.insuranceNames().get("disability"));
        if (needLife && !Boolean.TRUE.equals(ins.termLife())) missing.add(pack.insuranceNames().get("termLife"));
        boolean critical = !Boolean.TRUE.equals(ins.liability()) || !Boolean.TRUE.equals(ins.health());
        Assessment a = missing.isEmpty() ? Assessment.HEALTHY
                : (critical || missing.size() >= 2) ? Assessment.CONCERN : Assessment.CAUTION;
        int inPlace = needed - missing.size();
        String display = inPlace + " of " + needed + " in place";
        String meaning = missing.isEmpty()
                ? "The covers that protect " + (in.isIndividual() ? "you" : "your household") + " from the biggest financial shocks are in place."
                : "Missing: " + String.join("; ", missing) + ".";
        String next = missing.isEmpty()
                ? "Review the sums insured every few years and after big life events."
                : "Compare quotes for " + missing.get(0) + " first" + (needLife ? "; with dependants, term life cover protects them if you die." : ".");
        return new HouseholdRatio(key, name, Area.SAFETY_NET, (double) inPlace, display,
                "Covers in place: liability, health, disability" + (needLife ? ", term life (you have dependants)" : "") + " → " + display,
                a, bench, meaning, next);
    }

    static HouseholdRatio savingsRate(HouseholdInput in) {
        String key = "savingsRate", name = "Savings rate";
        String bench = "Healthy ≥ 20% · Caution 10–20% · Concern < 10%";
        Double net = in.netMonthlyIncome(), spend = in.totalMonthlySpending();
        if (net == null || net <= 0 || spend == null) {
            return na(key, name, Area.CASH_FLOW, bench, "Add your net income and total monthly spending to see this.");
        }
        double rate = (net - spend) / net;
        Assessment a = rate >= 0.20 ? Assessment.HEALTHY : rate >= 0.10 ? Assessment.CAUTION : Assessment.CONCERN;
        String meaning = rate >= 0
                ? "You keep " + Money.pct(rate) + " of your take-home pay (" + Money.eur(net - spend) + " a month) after spending."
                : "You spend " + Money.eur(spend - net) + " a month more than you earn.";
        String next = a == Assessment.HEALTHY
                ? "Automate it: move savings on payday so spending adapts to what is left."
                : "Reaching 20% means saving " + Money.eur(Math.max(0, 0.20 * net - (net - spend))) + " more a month; start with the largest non-essential costs.";
        return new HouseholdRatio(key, name, Area.CASH_FLOW, round4(rate), Money.pct(rate),
                "(" + Money.eur(net) + " net income − " + Money.eur(spend) + " spending) ÷ " + Money.eur(net) + " = " + Money.pct(rate),
                a, bench, meaning, next);
    }

    static HouseholdRatio housingCost(HouseholdInput in) {
        String key = "housingCost", name = "Housing cost";
        String bench = "Healthy ≤ 30% · Caution 30–40% · Concern > 40%";
        Double net = in.netMonthlyIncome(), housing = in.monthlyHousingCost();
        if (net == null || net <= 0 || housing == null) {
            return na(key, name, Area.CASH_FLOW, bench, "Add your rent or mortgage payment to see this.");
        }
        double r = housing / net;
        Assessment a = r <= 0.30 ? Assessment.HEALTHY : r <= 0.40 ? Assessment.CAUTION : Assessment.CONCERN;
        String meaning = "Rent or mortgage takes " + Money.pct(r) + " of your take-home pay.";
        String next = a == Assessment.HEALTHY
                ? "Keep housing at or below 30% when you move or buy."
                : "Above 30% leaves little room for saving; factor this in before any move or home purchase.";
        return new HouseholdRatio(key, name, Area.CASH_FLOW, round4(r), Money.pct(r),
                Money.eur(housing) + " housing ÷ " + Money.eur(net) + " net income = " + Money.pct(r), a, bench, meaning, next);
    }

    static HouseholdRatio consumerDebt(HouseholdInput in) {
        String key = "consumerDebt", name = "Consumer debt load";
        String bench = "Healthy < 10% · Caution 10–20% · Concern > 20%";
        Double net = in.netMonthlyIncome();
        if (net == null || net <= 0 || in.monthlyDebtPayments() == null) {
            return na(key, name, Area.DEBT, bench, "Add your monthly loan and credit repayments (0 if none) to see this.");
        }
        double payments = in.monthlyDebtPayments();
        double r = payments / net;
        Assessment a = r < 0.10 ? Assessment.HEALTHY : r <= 0.20 ? Assessment.CAUTION : Assessment.CONCERN;
        double expensive = HouseholdInput.orZero(in.expensiveDebtBalance());
        // Expensive debt outstanding is never "healthy", however small the repayments.
        if (expensive > 0 && a == Assessment.HEALTHY) {
            a = Assessment.CAUTION;
        }
        String meaning = payments == 0
                ? "No loan or credit repayments besides your home."
                : "Loan and credit repayments (excluding your mortgage) take " + Money.pct(r) + " of your take-home pay.";
        String next = expensive > 0
                ? "Pay off the " + Money.eur(expensive) + " of expensive debt (credit cards, overdraft, consumer loans) before investing: the interest you avoid is a guaranteed return."
                : a == Assessment.HEALTHY ? "Avoid new consumer credit for things that lose value." : "Pay down the most expensive loan first.";
        return new HouseholdRatio(key, name, Area.DEBT, round4(r), Money.pct(r),
                Money.eur(payments) + " monthly repayments ÷ " + Money.eur(net) + " net income = " + Money.pct(r), a, bench, meaning, next);
    }

    static HouseholdRatio retirementSaving(HouseholdInput in) {
        String key = "retirementSaving", name = "Retirement saving";
        String bench = "Healthy ≥ 15% · Caution 8–15% · Concern < 8% (of gross income)";
        Double gross = in.grossMonthlyIncome(), saving = in.monthlyRetirementSaving();
        if (gross == null || gross <= 0 || saving == null) {
            return na(key, name, Area.FUTURE, bench, "Add your gross income and monthly pension + retirement investing to see this.");
        }
        double r = saving / gross;
        Assessment a = r >= 0.15 ? Assessment.HEALTHY : r >= 0.08 ? Assessment.CAUTION : Assessment.CONCERN;
        String meaning = "You put " + Money.pct(r) + " of your gross income towards retirement, on top of the state pension.";
        String next = a == Assessment.HEALTHY
                ? "Keep it up and raise it with every pay rise."
                : (Boolean.TRUE.equals(in.employerMatchAvailable()) && !Boolean.TRUE.equals(in.employerMatchFullyUsed())
                        ? "First take your employer's full pension contribution: it is extra pay you otherwise leave behind."
                        : "Reaching 15% means about " + Money.eur(Math.max(0, 0.15 * gross - saving)) + " more a month; raise it step by step.");
        return new HouseholdRatio(key, name, Area.FUTURE, round4(r), Money.pct(r),
                Money.eur(saving) + " a month ÷ " + Money.eur(gross) + " gross income = " + Money.pct(r), a, bench, meaning, next);
    }

    static HouseholdRatio goalsOnTrack(List<GoalResult> goals) {
        String key = "goalsOnTrack", name = "Goals on track";
        String bench = "Healthy: all · Caution: some · Concern: none";
        if (goals.isEmpty()) {
            return na(key, name, Area.FUTURE, bench, "Add your goals (a home, education, a car, a sabbatical) to see whether you are on track.");
        }
        long on = goals.stream().filter(GoalResult::onTrack).count();
        Assessment a = on == goals.size() ? Assessment.HEALTHY : on > 0 ? Assessment.CAUTION : Assessment.CONCERN;
        String display = on + " of " + goals.size();
        String meaning = on == goals.size() ? "Every goal is projected to be reached on time."
                : (goals.size() - on) + " goal" + (goals.size() - on == 1 ? " is" : "s are") + " projected to fall short.";
        GoalResult worst = goals.stream().filter(g -> !g.onTrack()).findFirst().orElse(null);
        String next = worst == null ? "Review targets once a year; prices and plans change."
                : "For " + worst.name() + ", saving about " + Money.eur(worst.monthlyNeeded()) + " a month (now " + Money.eur(worst.monthlyContribution())
                        + ") or moving the date would close the gap.";
        return new HouseholdRatio(key, name, Area.FUTURE, (double) on / goals.size(), display,
                display + " goals projected to reach their inflation-adjusted target by their date", a, bench, meaning, next);
    }

    static HouseholdRatio feeDrag(HouseholdInput in) {
        String key = "feeDrag", name = "Fee drag";
        String bench = "Healthy ≤ 0.3% · Caution 0.3–1% · Concern > 1% a year";
        double inv = HouseholdInput.orZero(in.investmentsTotal());
        Double fee = in.weightedFundFeePct();
        if (inv <= 0 || fee == null) {
            return na(key, name, Area.INVESTING, bench, inv <= 0 ? "Applies once you invest." : "Add the average yearly fee (TER) of your funds to see this.");
        }
        Assessment a = fee <= 0.3 ? Assessment.HEALTHY : fee <= 1.0 ? Assessment.CAUTION : Assessment.CONCERN;
        double yearly = inv * fee / 100;
        String meaning = "You pay about " + Money.eur(yearly) + " a year in fund fees on " + Money.eur(inv) + ".";
        String next = a == Assessment.HEALTHY ? "Low costs: one of the few things in investing you fully control."
                : "Broad index funds often cost 0.1–0.3% a year; over 30 years, 1% in extra fees can cost roughly a quarter of the final value.";
        return new HouseholdRatio(key, name, Area.INVESTING, fee / 100, Money.pctPoints(fee),
                "Weighted average yearly fund fee (TER) = " + Money.pctPoints(fee) + " → " + Money.eur(yearly) + " a year on " + Money.eur(inv),
                a, bench, meaning, next);
    }

    static HouseholdRatio concentration(HouseholdInput in) {
        String key = "concentration", name = "Concentration";
        String bench = "Healthy ≤ 10% · Caution 10–25% · Concern > 25% (largest single company)";
        double inv = HouseholdInput.orZero(in.investmentsTotal());
        Double largest = in.largestHoldingValue();
        if (inv <= 0 || largest == null) {
            return na(key, name, Area.INVESTING, bench, inv <= 0 ? "Applies once you invest." : "Add your largest single-company holding (0 if you only hold broad funds).");
        }
        double r = Math.min(1, largest / inv);
        Assessment a = r <= 0.10 ? Assessment.HEALTHY : r <= 0.25 ? Assessment.CAUTION : Assessment.CONCERN;
        String meaning = largest == 0 ? "No single company dominates: you hold broad funds."
                : "Your largest single company is " + Money.pct(r) + " of your investments.";
        String next = a == Assessment.HEALTHY ? "Diversified: one company's bad news can't sink your plan."
                : "Consider limiting any one company (including your employer's shares) to about 10% of your investments.";
        return new HouseholdRatio(key, name, Area.INVESTING, round4(r), Money.pct(r),
                Money.eur(largest) + " largest single holding ÷ " + Money.eur(inv) + " investments = " + Money.pct(r), a, bench, meaning, next);
    }

    private static HouseholdRatio na(String key, String name, Area area, String bench, String why) {
        return new HouseholdRatio(key, name, area, null, "—", null, Assessment.NOT_APPLICABLE, bench, why, null);
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10000) / 10000.0;
    }
}
