package com.hrragassistant.backend.reports.analysis;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.hrragassistant.backend.reports.analysis.BalanceSheetExtraction.FigurePair;
import com.hrragassistant.backend.reports.analysis.BalanceSheetExtraction.Figures;
import com.hrragassistant.backend.reports.analysis.Ratio.Assessment;
import com.hrragassistant.backend.reports.analysis.Ratio.Category;
import com.hrragassistant.backend.reports.analysis.Ratio.Format;

/**
 * Computes standard fundamental-analysis ratios from extracted figures with
 * fixed formulas and published rule-of-thumb thresholds. Pure Java, no I/O,
 * deterministic: the same figures always give the same ratios.
 *
 * Thresholds are general-purpose guides for non-financial companies; they
 * vary by industry, which the UI states next to every benchmark. For banks,
 * insurers and NBFCs, liquidity and leverage ratios built for operating
 * companies are reported as NOT_APPLICABLE rather than misjudged.
 */
public final class RatioCalculator {

    private RatioCalculator() {
    }

    /** How a raw ratio maps to an assessment: higher-is-better or lower-is-better, with two cut-offs. */
    private record Rule(boolean higherIsBetter, double healthy, double caution, String benchmark) {

        Assessment assess(double v) {
            if (higherIsBetter) {
                return v >= healthy ? Assessment.HEALTHY : v >= caution ? Assessment.CAUTION : Assessment.CONCERN;
            }
            return v <= healthy ? Assessment.HEALTHY : v <= caution ? Assessment.CAUTION : Assessment.CONCERN;
        }
    }

    public static List<Ratio> calculate(BalanceSheetExtraction x) {
        Figures f = x.figures();
        String unit = x.unit() != null && !x.unit().isBlank() ? x.unit() : "";
        boolean financial = Boolean.TRUE.equals(x.financialInstitution());
        List<Ratio> out = new ArrayList<>();

        // ---------- Profitability
        out.add(divide("netMargin", "Net profit margin", Category.PROFITABILITY, Format.PERCENT, f,
                "netIncome", "revenue", "Net income", "Revenue", unit,
                new Rule(true, 0.10, 0.03, "Healthy ≥ 10% · Caution 3–10% · Concern < 3%"),
                "How much of each unit of sales is left as profit after all costs and taxes."));
        out.add(divide("operatingMargin", "Operating margin", Category.PROFITABILITY, Format.PERCENT, f,
                "operatingIncome", "revenue", "Operating income", "Revenue", unit,
                new Rule(true, 0.15, 0.05, "Healthy ≥ 15% · Caution 5–15% · Concern < 5%"),
                "Profit from the core business before interest and tax, as a share of sales."));
        out.add(withEquityGuard(divide("roe", "Return on equity (ROE)", Category.PROFITABILITY, Format.PERCENT, f,
                "netIncome", "shareholdersEquity", "Net income", "Shareholders' equity", unit,
                new Rule(true, 0.15, 0.08, "Healthy ≥ 15% · Caution 8–15% · Concern < 8%"),
                "Profit generated on the money shareholders have invested in the company."), f));
        out.add(divide("roa", "Return on assets (ROA)", Category.PROFITABILITY, Format.PERCENT, f,
                "netIncome", "totalAssets", "Net income", "Total assets", unit,
                financial
                        ? new Rule(true, 0.01, 0.005, "Banks/insurers: Healthy ≥ 1% · Caution 0.5–1% · Concern < 0.5%")
                        : new Rule(true, 0.05, 0.02, "Healthy ≥ 5% · Caution 2–5% · Concern < 2%"),
                "How efficiently the company turns everything it owns into profit."));

        // ---------- Liquidity (not meaningful for financial institutions)
        out.add(notForFinancials(financial, divide("currentRatio", "Current ratio", Category.LIQUIDITY, Format.MULTIPLE, f,
                "currentAssets", "currentLiabilities", "Current assets", "Current liabilities", unit,
                new Rule(true, 1.5, 1.0, "Healthy ≥ 1.5x · Caution 1.0–1.5x · Concern < 1.0x"),
                "Can short-term assets cover bills due within a year? Below 1x means they can't.")));
        out.add(notForFinancials(financial, quickRatio(f, unit)));
        out.add(notForFinancials(financial, divide("cashRatio", "Cash ratio", Category.LIQUIDITY, Format.MULTIPLE, f,
                "cashAndEquivalents", "currentLiabilities", "Cash and equivalents", "Current liabilities", unit,
                new Rule(true, 0.5, 0.2, "Healthy ≥ 0.5x · Caution 0.2–0.5x · Concern < 0.2x"),
                "How much of the next year's bills could be paid from cash alone.")));

        // ---------- Leverage & solvency
        out.add(notForFinancials(financial, withEquityGuard(divide("debtToEquity", "Debt-to-equity", Category.LEVERAGE, Format.MULTIPLE, f,
                "totalDebt", "shareholdersEquity", "Total debt", "Shareholders' equity", unit,
                new Rule(false, 0.5, 1.5, "Healthy ≤ 0.5x · Caution 0.5–1.5x · Concern > 1.5x"),
                "How much the company borrows for every unit of shareholder money. Higher means more risk."), f)));
        out.add(notForFinancials(financial, divide("debtRatio", "Liabilities to assets", Category.LEVERAGE, Format.PERCENT, f,
                "totalLiabilities", "totalAssets", "Total liabilities", "Total assets", unit,
                new Rule(false, 0.5, 0.7, "Healthy ≤ 50% · Caution 50–70% · Concern > 70%"),
                "Share of the company's assets that is financed by what it owes.")));
        out.add(interestCoverage(f, unit));

        // ---------- Cash flow
        out.add(cashConversion(f, unit));
        out.add(freeCashFlow(f, unit));

        // ---------- Efficiency
        out.add(divide("assetTurnover", "Asset turnover", Category.EFFICIENCY, Format.MULTIPLE, f,
                "revenue", "totalAssets", "Revenue", "Total assets", unit,
                financial
                        ? new Rule(true, 0.08, 0.05, "Banks/insurers: Healthy ≥ 0.08x · Caution 0.05–0.08x")
                        : new Rule(true, 1.0, 0.5, "Healthy ≥ 1.0x · Caution 0.5–1.0x · Concern < 0.5x (asset-heavy industries run lower)"),
                "Sales generated for every unit of assets. Varies a lot by industry."));

        // ---------- Growth (needs prior period)
        out.add(growth("revenueGrowth", "Revenue growth", f, "revenue", "Revenue", unit));
        out.add(growth("netIncomeGrowth", "Net income growth", f, "netIncome", "Net income", unit));
        out.add(growth("equityGrowth", "Equity growth", f, "shareholdersEquity", "Shareholders' equity", unit));

        return out;
    }

    // ------------------------------------------------------------------ builders

    private static Ratio divide(String key, String name, Category category, Format format, Figures f,
                                String numKey, String denKey, String numLabel, String denLabel, String unit,
                                Rule rule, String explanation) {
        FigurePair num = BalanceSheetExtraction.figure(f, numKey);
        FigurePair den = BalanceSheetExtraction.figure(f, denKey);
        Double value = safeDivide(num.current(), den.current());
        Double prior = safeDivide(num.prior(), den.prior());
        String formula = value == null
                ? numLabel + " ÷ " + denLabel + " (" + missing(num.current(), numLabel, den.current(), denLabel) + ")"
                : numLabel + " (" + amount(num.current(), unit) + ") ÷ " + denLabel + " (" + amount(den.current(), unit) + ")";
        return new Ratio(key, name, category, format, value, prior, display(value, format, unit),
                display(prior, format, unit), formula,
                value == null ? Assessment.NOT_APPLICABLE : rule.assess(value), rule.benchmark(), explanation);
    }

    private static Ratio quickRatio(Figures f, String unit) {
        FigurePair ca = BalanceSheetExtraction.figure(f, "currentAssets");
        FigurePair inv = BalanceSheetExtraction.figure(f, "inventory");
        FigurePair cl = BalanceSheetExtraction.figure(f, "currentLiabilities");
        // Inventory missing is treated as zero only when the company plainly has none
        // would be a guess, so the quick ratio needs inventory to be reported.
        Double value = ca.current() == null || inv.current() == null ? null : safeDivide(ca.current() - inv.current(), cl.current());
        Double prior = ca.prior() == null || inv.prior() == null ? null : safeDivide(ca.prior() - inv.prior(), cl.prior());
        Rule rule = new Rule(true, 1.0, 0.7, "Healthy ≥ 1.0x · Caution 0.7–1.0x · Concern < 0.7x");
        String formula = value == null
                ? "(Current assets − Inventory) ÷ Current liabilities (needs all three figures)"
                : "(Current assets " + amount(ca.current(), unit) + " − Inventory " + amount(inv.current(), unit)
                        + ") ÷ Current liabilities " + amount(cl.current(), unit);
        return new Ratio("quickRatio", "Quick ratio", Category.LIQUIDITY, Format.MULTIPLE, value, prior,
                display(value, Format.MULTIPLE, unit), display(prior, Format.MULTIPLE, unit), formula,
                value == null ? Assessment.NOT_APPLICABLE : rule.assess(value), rule.benchmark(),
                "Like the current ratio, but ignores stock that may be slow to sell.");
    }

    private static Ratio interestCoverage(Figures f, String unit) {
        FigurePair ebit = BalanceSheetExtraction.figure(f, "operatingIncome");
        FigurePair interest = BalanceSheetExtraction.figure(f, "interestExpense");
        Rule rule = new Rule(true, 5.0, 2.0, "Healthy ≥ 5x · Caution 2–5x · Concern < 2x");
        String explanation = "How many times operating profit covers interest payments.";
        if (interest.current() != null && interest.current() == 0 && ebit.current() != null) {
            return new Ratio("interestCoverage", "Interest coverage", Category.LEVERAGE, Format.MULTIPLE, null, null,
                    "No interest", "—", "Interest expense is zero: no interest burden to cover.",
                    ebit.current() > 0 ? Assessment.HEALTHY : Assessment.NOT_APPLICABLE, rule.benchmark(), explanation);
        }
        Double interestAbs = interest.current() == null ? null : Math.abs(interest.current());
        Double priorInterestAbs = interest.prior() == null ? null : Math.abs(interest.prior());
        Double value = safeDivide(ebit.current(), interestAbs);
        Double prior = safeDivide(ebit.prior(), priorInterestAbs);
        String formula = value == null
                ? "Operating income ÷ Interest expense (" + missing(ebit.current(), "Operating income", interest.current(), "Interest expense") + ")"
                : "Operating income (" + amount(ebit.current(), unit) + ") ÷ Interest expense (" + amount(interestAbs, unit) + ")";
        return new Ratio("interestCoverage", "Interest coverage", Category.LEVERAGE, Format.MULTIPLE, value, prior,
                display(value, Format.MULTIPLE, unit), display(prior, Format.MULTIPLE, unit), formula,
                value == null ? Assessment.NOT_APPLICABLE : rule.assess(value), rule.benchmark(), explanation);
    }

    private static Ratio cashConversion(Figures f, String unit) {
        FigurePair ocf = BalanceSheetExtraction.figure(f, "operatingCashFlow");
        FigurePair ni = BalanceSheetExtraction.figure(f, "netIncome");
        Rule rule = new Rule(true, 1.0, 0.7, "Healthy ≥ 1.0x · Caution 0.7–1.0x · Concern < 0.7x");
        String explanation = "Is reported profit backed by real cash? Below 1x means profit is running ahead of cash.";
        if (ni.current() != null && ni.current() <= 0) {
            boolean ocfPositive = ocf.current() != null && ocf.current() > 0;
            return new Ratio("cashConversion", "Cash conversion", Category.CASH_FLOW, Format.MULTIPLE, null, null,
                    ocfPositive ? "Cash positive despite a loss" : "—",
                    "—", "Not meaningful when net income is zero or negative.",
                    ocfPositive ? Assessment.CAUTION : Assessment.NOT_APPLICABLE, rule.benchmark(), explanation);
        }
        Double value = safeDivide(ocf.current(), ni.current());
        Double prior = ni.prior() != null && ni.prior() > 0 ? safeDivide(ocf.prior(), ni.prior()) : null;
        String formula = value == null
                ? "Operating cash flow ÷ Net income (" + missing(ocf.current(), "Operating cash flow", ni.current(), "Net income") + ")"
                : "Operating cash flow (" + amount(ocf.current(), unit) + ") ÷ Net income (" + amount(ni.current(), unit) + ")";
        return new Ratio("cashConversion", "Cash conversion", Category.CASH_FLOW, Format.MULTIPLE, value, prior,
                display(value, Format.MULTIPLE, unit), display(prior, Format.MULTIPLE, unit), formula,
                value == null ? Assessment.NOT_APPLICABLE : rule.assess(value), rule.benchmark(), explanation);
    }

    private static Ratio freeCashFlow(Figures f, String unit) {
        FigurePair ocf = BalanceSheetExtraction.figure(f, "operatingCashFlow");
        FigurePair capex = BalanceSheetExtraction.figure(f, "capitalExpenditure");
        // Capex is reported as an outflow; use its magnitude whatever sign the report used.
        Double value = ocf.current() == null || capex.current() == null ? null : ocf.current() - Math.abs(capex.current());
        Double prior = ocf.prior() == null || capex.prior() == null ? null : ocf.prior() - Math.abs(capex.prior());
        String formula = value == null
                ? "Operating cash flow − Capital expenditure (" + missing(ocf.current(), "Operating cash flow", capex.current(), "Capital expenditure") + ")"
                : "Operating cash flow (" + amount(ocf.current(), unit) + ") − Capital expenditure (" + amount(Math.abs(capex.current()), unit) + ")";
        Assessment a = value == null ? Assessment.NOT_APPLICABLE : value > 0 ? Assessment.HEALTHY : Assessment.CONCERN;
        return new Ratio("freeCashFlow", "Free cash flow", Category.CASH_FLOW, Format.AMOUNT, value, prior,
                display(value, Format.AMOUNT, unit), display(prior, Format.AMOUNT, unit), formula, a,
                "Healthy > 0 · Concern ≤ 0",
                "Cash left after running and investing in the business — what can fund dividends, debt repayment or growth.");
    }

    private static Ratio growth(String key, String name, Figures f, String figKey, String label, String unit) {
        FigurePair p = BalanceSheetExtraction.figure(f, figKey);
        Rule rule = new Rule(true, 0.10, 0.0, "Healthy ≥ 10% · Caution 0–10% · Concern < 0%");
        Double value = null;
        String formula;
        if (p.current() == null || p.prior() == null) {
            formula = "(Current − Prior) ÷ Prior " + label.toLowerCase(Locale.ROOT) + " (needs both periods)";
        } else if (p.prior() <= 0) {
            formula = "Not meaningful: prior-period " + label.toLowerCase(Locale.ROOT) + " was zero or negative ("
                    + amount(p.prior(), unit) + " → " + amount(p.current(), unit) + ")";
        } else {
            value = (p.current() - p.prior()) / p.prior();
            formula = "(" + amount(p.current(), unit) + " − " + amount(p.prior(), unit) + ") ÷ " + amount(p.prior(), unit);
        }
        return new Ratio(key, name, Category.GROWTH, Format.PERCENT, value, null, display(value, Format.PERCENT, unit), "—",
                formula, value == null ? Assessment.NOT_APPLICABLE : rule.assess(value), rule.benchmark(),
                "Year-over-year change in " + label.toLowerCase(Locale.ROOT) + ".");
    }

    /** Negative equity makes ROE and D/E meaningless as ratios but is itself a red flag. */
    private static Ratio withEquityGuard(Ratio r, Figures f) {
        Double equity = BalanceSheetExtraction.figure(f, "shareholdersEquity").current();
        if (equity != null && equity <= 0) {
            return new Ratio(r.key(), r.name(), r.category(), r.format(), null, null, "Negative equity", "—",
                    "Shareholders' equity is zero or negative, so this ratio is not meaningful.",
                    Assessment.CONCERN, r.benchmark(), r.explanation());
        }
        return r;
    }

    private static Ratio notForFinancials(boolean financial, Ratio r) {
        if (!financial) {
            return r;
        }
        return new Ratio(r.key(), r.name(), r.category(), r.format(), null, null, "Not applicable", "—",
                "Not used for banks, insurers and other financial institutions, whose balance sheets work differently.",
                Assessment.NOT_APPLICABLE, r.benchmark(), r.explanation());
    }

    // ------------------------------------------------------------------ formatting

    static Double safeDivide(Double num, Double den) {
        if (num == null || den == null || den == 0 || Double.isNaN(num) || Double.isNaN(den)) {
            return null;
        }
        double v = num / den;
        return Double.isFinite(v) ? v : null;
    }

    private static String missing(Double a, String aLabel, Double b, String bLabel) {
        List<String> gaps = new ArrayList<>();
        if (a == null) {
            gaps.add(aLabel.toLowerCase(Locale.ROOT));
        }
        if (b == null) {
            gaps.add(bLabel.toLowerCase(Locale.ROOT));
        } else if (b == 0) {
            gaps.add(bLabel.toLowerCase(Locale.ROOT) + " is zero");
        }
        return gaps.isEmpty() ? "not computable" : "missing: " + String.join(", ", gaps);
    }

    private static final Function<String, DecimalFormat> FMT =
            p -> new DecimalFormat(p, DecimalFormatSymbols.getInstance(Locale.ENGLISH));

    public static String amount(Double v, String unit) {
        if (v == null) {
            return "—";
        }
        String n = FMT.apply(Math.abs(v) >= 100 ? "#,##0" : "#,##0.##").format(v);
        return unit == null || unit.isBlank() ? n : n + " " + unit;
    }

    public static String display(Double v, Format format, String unit) {
        if (v == null) {
            return "—";
        }
        return switch (format) {
            case MULTIPLE -> FMT.apply("0.00").format(v) + "x";
            case PERCENT -> FMT.apply("0.0").format(v * 100) + "%";
            case AMOUNT -> amount(v, unit);
        };
    }
}
