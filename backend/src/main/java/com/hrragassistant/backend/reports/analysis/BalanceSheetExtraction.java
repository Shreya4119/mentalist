package com.hrragassistant.backend.reports.analysis;

import java.util.List;

/**
 * What the AI model extracts from a financial report. The model only READS
 * figures and writes narrative; every ratio and the health score are
 * computed in plain Java afterwards (RatioCalculator, HealthScorer) so the
 * numbers are reproducible and auditable rather than model arithmetic.
 *
 * All monetary figures are in the report's own unit (see {@link #unit()}),
 * e.g. "INR crore" or "USD millions". Losses / outflows are negative.
 */
public record BalanceSheetExtraction(
        String companyName,
        String reportingPeriod,
        String priorPeriod,
        String currency,
        String unit,
        String industry,
        Boolean financialInstitution,
        String statementBasis,
        Figures figures,
        String summary,
        List<String> highlights,
        List<Finding> strengths,
        List<Finding> redFlags,
        List<String> visualInsights,
        String outlook,
        List<String> dataQualityNotes) {

    /** A current-period and prior-period value plus where it was found. */
    public record FigurePair(Double current, Double prior, String source) {

        public static final FigurePair EMPTY = new FigurePair(null, null, "");
    }

    /** A qualitative point with the evidence (page, note or quote) it rests on. */
    public record Finding(String point, String evidence) {
    }

    /** The standard line items the calculator needs. Any may be null. */
    public record Figures(
            FigurePair revenue,
            FigurePair operatingIncome,
            FigurePair netIncome,
            FigurePair interestExpense,
            FigurePair totalAssets,
            FigurePair currentAssets,
            FigurePair cashAndEquivalents,
            FigurePair inventory,
            FigurePair receivables,
            FigurePair totalLiabilities,
            FigurePair currentLiabilities,
            FigurePair totalDebt,
            FigurePair shareholdersEquity,
            FigurePair operatingCashFlow,
            FigurePair capitalExpenditure) {
    }

    /** Stable keys and display labels for every line item, in display order. */
    public static final List<String[]> FIGURE_LABELS = List.of(
            new String[] {"revenue", "Revenue"},
            new String[] {"operatingIncome", "Operating income (EBIT)"},
            new String[] {"netIncome", "Net income"},
            new String[] {"interestExpense", "Interest expense"},
            new String[] {"totalAssets", "Total assets"},
            new String[] {"currentAssets", "Current assets"},
            new String[] {"cashAndEquivalents", "Cash and equivalents"},
            new String[] {"inventory", "Inventory"},
            new String[] {"receivables", "Trade receivables"},
            new String[] {"totalLiabilities", "Total liabilities"},
            new String[] {"currentLiabilities", "Current liabilities"},
            new String[] {"totalDebt", "Total debt (borrowings)"},
            new String[] {"shareholdersEquity", "Shareholders' equity"},
            new String[] {"operatingCashFlow", "Operating cash flow"},
            new String[] {"capitalExpenditure", "Capital expenditure"});

    /** Looks a figure up by its stable key; never returns null. */
    public static FigurePair figure(Figures f, String key) {
        if (f == null) {
            return FigurePair.EMPTY;
        }
        FigurePair p = switch (key) {
            case "revenue" -> f.revenue();
            case "operatingIncome" -> f.operatingIncome();
            case "netIncome" -> f.netIncome();
            case "interestExpense" -> f.interestExpense();
            case "totalAssets" -> f.totalAssets();
            case "currentAssets" -> f.currentAssets();
            case "cashAndEquivalents" -> f.cashAndEquivalents();
            case "inventory" -> f.inventory();
            case "receivables" -> f.receivables();
            case "totalLiabilities" -> f.totalLiabilities();
            case "currentLiabilities" -> f.currentLiabilities();
            case "totalDebt" -> f.totalDebt();
            case "shareholdersEquity" -> f.shareholdersEquity();
            case "operatingCashFlow" -> f.operatingCashFlow();
            case "capitalExpenditure" -> f.capitalExpenditure();
            default -> throw new IllegalArgumentException("Unknown figure key: " + key);
        };
        return p != null ? p : FigurePair.EMPTY;
    }
}
