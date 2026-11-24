package com.hrragassistant.backend.companies.analysis;

import static com.hrragassistant.backend.companies.xbrl.Metric.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.hrragassistant.backend.companies.analysis.CompanyHistory.Finding;
import com.hrragassistant.backend.companies.analysis.CompanyHistory.Result;
import com.hrragassistant.backend.companies.analysis.CompanyHistory.Signal;
import com.hrragassistant.backend.companies.xbrl.Metric;
import com.hrragassistant.backend.companies.xbrl.YearFigures;

/**
 * Multi-year analysis of a company from its official yearly figures. Pure
 * code, no AI: the same filings always give the same result.
 *
 * Scores follow their original papers, with two simplifications stated in
 * the app: Piotroski uses year-end (not start-of-year) assets, and Altman uses
 * the Z'' variant with book equity, because share prices aren't used here.
 */
public final class CompanyAnalyzer {

    /** Beneish (1999) 8-variable model cut-off: above it, earnings deserve a closer look. */
    static final double BENEISH_THRESHOLD = -1.78;

    private CompanyAnalyzer() {
    }

    public static CompanyHistory analyze(List<YearFigures> sortedYears) {
        List<YearFigures> ys = sortedYears;
        List<CompanyHistory.Year> years = new ArrayList<>();
        for (int i = 0; i < ys.size(); i++) {
            years.add(new CompanyHistory.Year(ys.get(i).periodEnd(), ys.get(i).currency(), figures(ys.get(i)),
                    ratios(ys.get(i), i > 0 ? ys.get(i - 1) : null)));
        }
        List<CompanyHistory.Piotroski> piotroski = new ArrayList<>();
        List<CompanyHistory.Beneish> beneish = new ArrayList<>();
        List<CompanyHistory.Altman> altman = new ArrayList<>();
        for (int i = 0; i < ys.size(); i++) {
            altman.add(altman(ys.get(i)));
            if (i > 0) {
                piotroski.add(piotroski(ys.get(i), ys.get(i - 1)));
                beneish.add(beneish(ys.get(i), ys.get(i - 1)));
            }
        }
        List<Finding> warnings = new ArrayList<>();
        List<Finding> strengths = new ArrayList<>();
        patterns(ys, warnings, strengths);
        return new CompanyHistory(years, trends(ys), piotroski, altman, beneish, warnings, strengths);
    }

    // ------------------------------------------------------------------ per year

    static Map<String, Double> figures(YearFigures y) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (Metric metric : Metric.values()) {
            m.put(metric.name(), y.get(metric));
        }
        return m;
    }

    static Map<String, Double> ratios(YearFigures y, YearFigures prev) {
        Map<String, Double> r = new LinkedHashMap<>();
        r.put("revenueGrowth", prev == null ? null : growth(y.get(REVENUE), prev.get(REVENUE)));
        r.put("netIncomeGrowth", prev == null ? null : growth(y.get(NET_INCOME), prev.get(NET_INCOME)));
        r.put("grossMargin", div(grossProfit(y), y.get(REVENUE)));
        r.put("operatingMargin", div(y.get(OPERATING_INCOME), y.get(REVENUE)));
        r.put("netMargin", div(y.get(NET_INCOME), y.get(REVENUE)));
        r.put("roe", div(y.get(NET_INCOME), y.get(EQUITY)));
        r.put("roa", div(y.get(NET_INCOME), y.get(TOTAL_ASSETS)));
        r.put("currentRatio", div(y.get(CURRENT_ASSETS), y.get(CURRENT_LIABILITIES)));
        r.put("liabilitiesToEquity", div(y.get(TOTAL_LIABILITIES), y.get(EQUITY)));
        r.put("cashConversion", div(y.get(OPERATING_CASH_FLOW), y.get(NET_INCOME)));
        r.put("freeCashFlow", y.has(OPERATING_CASH_FLOW) && y.has(CAPEX) ? y.get(OPERATING_CASH_FLOW) - y.get(CAPEX) : null);
        r.put("interestCoverage", div(y.get(OPERATING_INCOME), y.get(FINANCE_COSTS)));
        r.put("assetTurnover", div(y.get(REVENUE), y.get(TOTAL_ASSETS)));
        return r;
    }

    // ------------------------------------------------------------------ trends

    static CompanyHistory.Trends trends(List<YearFigures> ys) {
        if (ys.isEmpty()) {
            return new CompanyHistory.Trends(0, null, null, null, null, null, 0, 0);
        }
        YearFigures first = ys.get(0);
        YearFigures last = ys.get(ys.size() - 1);
        double span = (last.periodEnd().toEpochDay() - first.periodEnd().toEpochDay()) / 365.25;
        int up = 0;
        int down = 0;
        for (int i = 1; i < ys.size(); i++) {
            Double g = growth(ys.get(i).get(REVENUE), ys.get(i - 1).get(REVENUE));
            if (g != null && g > 0) {
                up++;
            } else if (g != null && g < 0) {
                down++;
            }
        }
        return new CompanyHistory.Trends(ys.size(), first.periodEnd(), last.periodEnd(),
                cagr(first.get(REVENUE), last.get(REVENUE), span),
                cagr(first.get(NET_INCOME), last.get(NET_INCOME), span),
                cagr(first.get(OPERATING_CASH_FLOW), last.get(OPERATING_CASH_FLOW), span),
                up, down);
    }

    /** Compound annual growth; only meaningful when both ends are positive. */
    static Double cagr(Double from, Double to, double years) {
        if (from == null || to == null || from <= 0 || to <= 0 || years < 0.9) {
            return null;
        }
        return Math.pow(to / from, 1 / years) - 1;
    }

    // ------------------------------------------------------------------ Piotroski

    static CompanyHistory.Piotroski piotroski(YearFigures y, YearFigures p) {
        List<Signal> s = new ArrayList<>();
        Double roa = div(y.get(NET_INCOME), y.get(TOTAL_ASSETS));
        Double roaPrev = div(p.get(NET_INCOME), p.get(TOTAL_ASSETS));
        Double cfo = y.get(OPERATING_CASH_FLOW);
        s.add(signal("profitable", "Made a profit", roa == null ? null : roa > 0,
                roa == null ? "Net income or total assets missing." : "Return on assets " + pct(roa) + "."));
        s.add(signal("cashPositive", "Operations brought in cash", cfo == null ? null : cfo > 0,
                cfo == null ? "Operating cash flow missing." : "Operating cash flow " + amount(cfo, y.currency()) + "."));
        s.add(signal("roaUp", "Return on assets improved", roa == null || roaPrev == null ? null : roa > roaPrev,
                roa == null || roaPrev == null ? "Needs net income and assets for both years." : pct(roaPrev) + " → " + pct(roa) + "."));
        s.add(signal("cashBacksProfit", "Cash flow exceeded profit", cfo == null || !y.has(NET_INCOME) ? null : cfo > y.get(NET_INCOME),
                cfo == null || !y.has(NET_INCOME) ? "Needs operating cash flow and net income."
                        : "Cash flow " + amount(cfo, y.currency()) + " vs net income " + amount(y.get(NET_INCOME), y.currency()) + "."));
        Double lev = div(y.get(NONCURRENT_BORROWINGS), y.get(TOTAL_ASSETS));
        Double levPrev = div(p.get(NONCURRENT_BORROWINGS), p.get(TOTAL_ASSETS));
        s.add(signal("lessDebt", "Long-term debt fell relative to assets", lev == null || levPrev == null ? null : lev < levPrev || (lev == 0 && levPrev == 0),
                lev == null || levPrev == null ? "Long-term borrowings not reported in both years." : pct(levPrev) + " → " + pct(lev) + " of assets."));
        Double cr = div(y.get(CURRENT_ASSETS), y.get(CURRENT_LIABILITIES));
        Double crPrev = div(p.get(CURRENT_ASSETS), p.get(CURRENT_LIABILITIES));
        s.add(signal("liquidityUp", "Current ratio improved", cr == null || crPrev == null ? null : cr > crPrev,
                cr == null || crPrev == null ? "Current assets/liabilities not reported in both years." : times(crPrev) + " → " + times(cr) + "."));
        Double sh = y.get(SHARES_OUTSTANDING);
        Double shPrev = p.get(SHARES_OUTSTANDING);
        s.add(signal("noNewShares", "No new shares issued", sh == null || shPrev == null ? null : sh <= shPrev,
                sh == null || shPrev == null ? "Share count not reported in both years." : String.format(Locale.ROOT, "%,.0f → %,.0f shares.", shPrev, sh)));
        Double gm = div(grossProfit(y), y.get(REVENUE));
        Double gmPrev = div(grossProfit(p), p.get(REVENUE));
        s.add(signal("marginUp", "Gross margin improved", gm == null || gmPrev == null ? null : gm > gmPrev,
                gm == null || gmPrev == null ? "Gross profit not reported in both years." : pct(gmPrev) + " → " + pct(gm) + "."));
        Double at = div(y.get(REVENUE), y.get(TOTAL_ASSETS));
        Double atPrev = div(p.get(REVENUE), p.get(TOTAL_ASSETS));
        s.add(signal("turnoverUp", "Asset turnover improved", at == null || atPrev == null ? null : at > atPrev,
                at == null || atPrev == null ? "Revenue or assets missing." : times(atPrev) + " → " + times(at) + "."));

        int score = (int) s.stream().filter(x -> x.result() == Result.PASS).count();
        int known = (int) s.stream().filter(x -> x.result() != Result.UNKNOWN).count();
        String verdict;
        if (known < 6) {
            verdict = "Too few of the 9 checks could be made from this filing to judge.";
        } else if (known == 9 && score >= 8) {
            verdict = "Strong: the business improved on almost every check.";
        } else if (score <= 2) {
            verdict = "Weak: most checks point the wrong way this year.";
        } else {
            verdict = "Mixed: " + score + " of " + known + " checks passed.";
        }
        return new CompanyHistory.Piotroski(y.periodEnd(), score, known, s, verdict);
    }

    // ------------------------------------------------------------------ Altman Z''

    static CompanyHistory.Altman altman(YearFigures y) {
        List<String> missing = missing(y, CURRENT_ASSETS, CURRENT_LIABILITIES, TOTAL_ASSETS, RETAINED_EARNINGS,
                OPERATING_INCOME, EQUITY, TOTAL_LIABILITIES);
        if (!missing.isEmpty() || y.get(TOTAL_ASSETS) == 0 || y.get(TOTAL_LIABILITIES) == 0) {
            return new CompanyHistory.Altman(y.periodEnd(), null, null, Map.of(), missing,
                    "Not enough figures in the filing. (Banks and insurers don't report current assets, so this score doesn't apply to them.)");
        }
        double ta = y.get(TOTAL_ASSETS);
        double x1 = (y.get(CURRENT_ASSETS) - y.get(CURRENT_LIABILITIES)) / ta;
        double x2 = y.get(RETAINED_EARNINGS) / ta;
        double x3 = y.get(OPERATING_INCOME) / ta;
        double x4 = y.get(EQUITY) / y.get(TOTAL_LIABILITIES);
        double z = 6.56 * x1 + 3.26 * x2 + 6.72 * x3 + 1.05 * x4;
        Map<String, Double> parts = new LinkedHashMap<>();
        parts.put("workingCapitalToAssets", x1);
        parts.put("retainedEarningsToAssets", x2);
        parts.put("operatingIncomeToAssets", x3);
        parts.put("equityToLiabilities", x4);
        String zone = z > 2.6 ? "SAFE" : z >= 1.1 ? "GREY" : "DISTRESS";
        String verdict = switch (zone) {
            case "SAFE" -> "Safe zone: the figures look like companies that rarely get into financial distress.";
            case "GREY" -> "Grey zone: not alarming, but worth watching.";
            default -> "Distress zone: the figures resemble companies that later ran into serious financial trouble.";
        };
        return new CompanyHistory.Altman(y.periodEnd(), z, zone, parts, List.of(), verdict);
    }

    // ------------------------------------------------------------------ Beneish M

    static CompanyHistory.Beneish beneish(YearFigures y, YearFigures p) {
        Metric[] needed = {REVENUE, RECEIVABLES, CURRENT_ASSETS, PPE, TOTAL_ASSETS, DEPRECIATION, SGA,
                CURRENT_LIABILITIES, NONCURRENT_BORROWINGS, NET_INCOME, OPERATING_CASH_FLOW};
        List<String> missing = new ArrayList<>(missing(y, needed));
        for (String m : missing(p, needed)) {
            if (!missing.contains(m)) {
                missing.add(m + " (previous year)");
            }
        }
        if (grossProfit(y) == null || grossProfit(p) == null) {
            missing.add("Gross profit");
        }
        if (!missing.isEmpty()) {
            return new CompanyHistory.Beneish(y.periodEnd(), null, false, Map.of(), missing,
                    "Not enough figures in the filings to compute this score.");
        }
        try {
            double dsri = (y.get(RECEIVABLES) / y.get(REVENUE)) / (p.get(RECEIVABLES) / p.get(REVENUE));
            double gmi = (grossProfit(p) / p.get(REVENUE)) / (grossProfit(y) / y.get(REVENUE));
            double aqi = (1 - (y.get(CURRENT_ASSETS) + y.get(PPE)) / y.get(TOTAL_ASSETS))
                    / (1 - (p.get(CURRENT_ASSETS) + p.get(PPE)) / p.get(TOTAL_ASSETS));
            double sgi = y.get(REVENUE) / p.get(REVENUE);
            double depi = (p.get(DEPRECIATION) / (p.get(DEPRECIATION) + p.get(PPE)))
                    / (y.get(DEPRECIATION) / (y.get(DEPRECIATION) + y.get(PPE)));
            double sgai = (y.get(SGA) / y.get(REVENUE)) / (p.get(SGA) / p.get(REVENUE));
            double lvgi = ((y.get(CURRENT_LIABILITIES) + y.get(NONCURRENT_BORROWINGS)) / y.get(TOTAL_ASSETS))
                    / ((p.get(CURRENT_LIABILITIES) + p.get(NONCURRENT_BORROWINGS)) / p.get(TOTAL_ASSETS));
            double tata = (y.get(NET_INCOME) - y.get(OPERATING_CASH_FLOW)) / y.get(TOTAL_ASSETS);
            double m = -4.84 + 0.920 * dsri + 0.528 * gmi + 0.404 * aqi + 0.892 * sgi + 0.115 * depi
                    - 0.172 * sgai + 4.679 * tata - 0.327 * lvgi;
            if (!Double.isFinite(m)) {
                throw new ArithmeticException();
            }
            Map<String, Double> parts = new LinkedHashMap<>();
            parts.put("receivablesVsSales", dsri);
            parts.put("grossMarginChange", gmi);
            parts.put("assetQuality", aqi);
            parts.put("salesGrowth", sgi);
            parts.put("depreciationRate", depi);
            parts.put("overheadsVsSales", sgai);
            parts.put("leverageChange", lvgi);
            parts.put("accruals", tata);
            boolean flagged = m > BENEISH_THRESHOLD;
            String verdict = flagged
                    ? "Above the warning line: some patterns resemble companies that overstated earnings. This is a reason to read the notes carefully, not proof of anything."
                    : "Below the warning line: no unusual earnings patterns by this measure.";
            return new CompanyHistory.Beneish(y.periodEnd(), m, flagged, parts, List.of(), verdict);
        } catch (ArithmeticException e) {
            return new CompanyHistory.Beneish(y.periodEnd(), null, false, Map.of(), List.of("A figure was zero"),
                    "The score can't be computed because one of the figures is zero.");
        }
    }

    // ------------------------------------------------------------------ patterns

    static void patterns(List<YearFigures> ys, List<Finding> warnings, List<Finding> strengths) {
        int cashBelowProfitStreak = 0;
        int negativeFcfStreak = 0;
        int revenueDeclineStreak = 0;
        for (int i = 0; i < ys.size(); i++) {
            YearFigures y = ys.get(i);
            YearFigures p = i > 0 ? ys.get(i - 1) : null;
            String cur = y.currency();
            String yr = Integer.toString(y.periodEnd().getYear());

            if (y.has(EQUITY) && y.get(EQUITY) < 0) {
                warnings.add(new Finding("negativeEquity", "The company owes more than it owns",
                        "Shareholders' equity was " + amount(y.get(EQUITY), cur) + " at the end of " + yr + ".", y.periodEnd()));
            }
            Double cover = div(y.get(OPERATING_INCOME), y.get(FINANCE_COSTS));
            if (cover != null && y.get(FINANCE_COSTS) > 0 && cover < 2) {
                warnings.add(new Finding("lowInterestCover", "Profit barely covers interest",
                        "Operating income covered finance costs only " + times(cover) + " in " + yr + ".", y.periodEnd()));
            }
            Double conv = div(y.get(OPERATING_CASH_FLOW), y.get(NET_INCOME));
            if (conv != null && y.get(NET_INCOME) > 0 && conv < 0.8) {
                cashBelowProfitStreak++;
                if (cashBelowProfitStreak == 2) {
                    warnings.add(new Finding("cashBelowProfit", "Profit isn't turning into cash",
                            "Operating cash flow was below 80% of net income two years running (" + yr + ": " + times(conv) + ").", y.periodEnd()));
                }
            } else {
                cashBelowProfitStreak = 0;
            }
            if (y.has(OPERATING_CASH_FLOW) && y.has(CAPEX) && y.get(OPERATING_CASH_FLOW) - y.get(CAPEX) < 0) {
                negativeFcfStreak++;
                if (negativeFcfStreak == 2) {
                    warnings.add(new Finding("negativeFcf", "Spending more cash than it makes",
                            "Free cash flow was negative two years running (" + yr + ": "
                                    + amount(y.get(OPERATING_CASH_FLOW) - y.get(CAPEX), cur) + ").", y.periodEnd()));
                }
            } else {
                negativeFcfStreak = 0;
            }
            if (p == null) {
                continue;
            }
            Double rev = growth(y.get(REVENUE), p.get(REVENUE));
            if (rev != null && rev < 0) {
                revenueDeclineStreak++;
                if (revenueDeclineStreak == 2) {
                    warnings.add(new Finding("revenueDecline", "Sales fell two years in a row",
                            "Revenue changed " + pct(rev) + " in " + yr + " after falling the year before.", y.periodEnd()));
                }
            } else {
                revenueDeclineStreak = 0;
            }
            Double rec = growth(y.get(RECEIVABLES), p.get(RECEIVABLES));
            if (rec != null && rev != null && rec - rev > 0.10 && rec > 0.10) {
                warnings.add(new Finding("receivablesOutpaceSales", "Customers are taking longer to pay",
                        "Trade receivables grew " + pct(rec) + " while revenue grew " + pct(rev) + " in " + yr + ".", y.periodEnd()));
            }
            Double inv = growth(y.get(INVENTORIES), p.get(INVENTORIES));
            if (inv != null && rev != null && inv - rev > 0.10 && inv > 0.10) {
                warnings.add(new Finding("inventoryBuildUp", "Stock is piling up",
                        "Inventories grew " + pct(inv) + " while revenue grew " + pct(rev) + " in " + yr + ".", y.periodEnd()));
            }
            Double ni = growth(y.get(NET_INCOME), p.get(NET_INCOME));
            Double cfo = growth(y.get(OPERATING_CASH_FLOW), p.get(OPERATING_CASH_FLOW));
            if (ni != null && cfo != null && ni > 0.05 && cfo < -0.05 && p.get(NET_INCOME) > 0) {
                warnings.add(new Finding("profitUpCashDown", "Profit rose while cash flow fell",
                        "Net income " + pct(ni) + ", operating cash flow " + pct(cfo) + " in " + yr + ".", y.periodEnd()));
            }
            Double shares = growth(y.get(SHARES_OUTSTANDING), p.get(SHARES_OUTSTANDING));
            if (shares != null && shares > 0.05) {
                warnings.add(new Finding("dilution", "Existing shareholders were diluted",
                        "The number of shares grew " + pct(shares) + " in " + yr + ".", y.periodEnd()));
            }
            Double debt = growth(y.get(NONCURRENT_BORROWINGS), p.get(NONCURRENT_BORROWINGS));
            if (debt != null && rev != null && debt > 0.25 && rev < 0.05 && y.get(NONCURRENT_BORROWINGS) > 0.1 * nz(y.get(TOTAL_ASSETS))) {
                warnings.add(new Finding("debtUpSalesFlat", "Borrowing more without growing",
                        "Long-term borrowings rose " + pct(debt) + " while revenue changed " + pct(rev) + " in " + yr + ".", y.periodEnd()));
            }
        }

        // Strengths over the whole period.
        if (ys.size() >= 3) {
            boolean allGrowth = true;
            boolean cashAlwaysBacks = true;
            int checked = 0;
            for (int i = 0; i < ys.size(); i++) {
                YearFigures y = ys.get(i);
                if (i > 0) {
                    Double g = growth(y.get(REVENUE), ys.get(i - 1).get(REVENUE));
                    allGrowth &= g != null && g > 0;
                }
                if (y.has(OPERATING_CASH_FLOW) && y.has(NET_INCOME)) {
                    checked++;
                    cashAlwaysBacks &= y.get(OPERATING_CASH_FLOW) >= y.get(NET_INCOME);
                }
            }
            YearFigures last = ys.get(ys.size() - 1);
            if (allGrowth) {
                strengths.add(new Finding("steadyGrowth", "Sales grew every year",
                        "Revenue rose in each of the last " + (ys.size() - 1) + " years.", last.periodEnd()));
            }
            if (cashAlwaysBacks && checked >= 3) {
                strengths.add(new Finding("cashBacked", "Profit is backed by cash",
                        "Operating cash flow was at least as large as net income in every year shown.", last.periodEnd()));
            }
            Double mFirst = div(ys.get(0).get(OPERATING_INCOME), ys.get(0).get(REVENUE));
            Double mLast = div(last.get(OPERATING_INCOME), last.get(REVENUE));
            if (mFirst != null && mLast != null && mLast - mFirst > 0.02) {
                strengths.add(new Finding("marginsUp", "Margins widened",
                        "Operating margin went from " + pct(mFirst) + " to " + pct(mLast) + ".", last.periodEnd()));
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    static Double grossProfit(YearFigures y) {
        if (y.has(GROSS_PROFIT)) {
            return y.get(GROSS_PROFIT);
        }
        return y.has(REVENUE) && y.has(COST_OF_SALES) ? y.get(REVENUE) - y.get(COST_OF_SALES) : null;
    }

    static Double div(Double a, Double b) {
        return a == null || b == null || b == 0 ? null : a / b;
    }

    static Double growth(Double now, Double before) {
        return now == null || before == null || before == 0 || before < 0 ? null : (now - before) / before;
    }

    private static double nz(Double d) {
        return d == null ? 0 : d;
    }

    private static List<String> missing(YearFigures y, Metric... metrics) {
        List<String> out = new ArrayList<>();
        for (Metric m : metrics) {
            if (!y.has(m)) {
                out.add(m.label());
            }
        }
        return out;
    }

    private static Signal signal(String key, String label, Boolean pass, String detail) {
        return new Signal(key, label, pass == null ? Result.UNKNOWN : pass ? Result.PASS : Result.FAIL, detail);
    }

    static String pct(double v) {
        return String.format(Locale.ROOT, "%.1f%%", v * 100);
    }

    static String times(double v) {
        return String.format(Locale.ROOT, "%.2f×", v);
    }

    /** "EUR 1.2 bn" / "EUR 350.0 m" style amounts. */
    static String amount(double v, String currency) {
        String c = currency == null ? "" : currency + " ";
        double a = Math.abs(v);
        String sign = v < 0 ? "-" : "";
        if (a >= 1e9) {
            return sign + c + String.format(Locale.ROOT, "%.1f bn", a / 1e9);
        }
        if (a >= 1e6) {
            return sign + c + String.format(Locale.ROOT, "%.1f m", a / 1e6);
        }
        return sign + c + String.format(Locale.ROOT, "%,.0f", a);
    }
}
