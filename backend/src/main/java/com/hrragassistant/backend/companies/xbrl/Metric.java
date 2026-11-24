package com.hrragassistant.backend.companies.xbrl;

import java.util.List;

/**
 * The yearly figures Mentalist reads from official IFRS filings (ESEF), and
 * which IFRS taxonomy concepts carry them, in order of preference. Companies
 * label the same thing slightly differently, so each metric lists fallbacks.
 */
public enum Metric {

    REVENUE("Revenue", Kind.DURATION, "ifrs-full:Revenue", "ifrs-full:RevenueFromContractsWithCustomers"),
    COST_OF_SALES("Cost of sales", Kind.DURATION, "ifrs-full:CostOfSales"),
    GROSS_PROFIT("Gross profit", Kind.DURATION, "ifrs-full:GrossProfit"),
    SGA("Selling, general & admin costs", Kind.DURATION, "ifrs-full:SellingGeneralAndAdministrativeExpense",
            "ifrs-full:AdministrativeExpense"),
    DEPRECIATION("Depreciation & amortisation", Kind.DURATION, "ifrs-full:DepreciationAndAmortisationExpense",
            "ifrs-full:DepreciationAmortisationAndImpairmentLossReversalOfImpairmentLossRecognisedInProfitOrLoss",
            "ifrs-full:DepreciationExpense"),
    OPERATING_INCOME("Operating income", Kind.DURATION, "ifrs-full:ProfitLossFromOperatingActivities"),
    FINANCE_COSTS("Finance costs", Kind.DURATION, "ifrs-full:FinanceCosts", "ifrs-full:InterestExpense"),
    PROFIT_BEFORE_TAX("Profit before tax", Kind.DURATION, "ifrs-full:ProfitLossBeforeTax"),
    NET_INCOME("Net income", Kind.DURATION, "ifrs-full:ProfitLoss", "ifrs-full:ProfitLossAttributableToOwnersOfParent"),
    OPERATING_CASH_FLOW("Operating cash flow", Kind.DURATION, "ifrs-full:CashFlowsFromUsedInOperatingActivities"),
    CAPEX("Capital expenditure", Kind.DURATION,
            "ifrs-full:PurchaseOfPropertyPlantAndEquipmentClassifiedAsInvestingActivities",
            "ifrs-full:PurchaseOfPropertyPlantAndEquipment"),

    TOTAL_ASSETS("Total assets", Kind.INSTANT, "ifrs-full:Assets"),
    CURRENT_ASSETS("Current assets", Kind.INSTANT, "ifrs-full:CurrentAssets"),
    CASH("Cash and equivalents", Kind.INSTANT, "ifrs-full:CashAndCashEquivalents"),
    INVENTORIES("Inventories", Kind.INSTANT, "ifrs-full:Inventories", "ifrs-full:CurrentInventoriesHeldForSale"),
    RECEIVABLES("Trade receivables", Kind.INSTANT, "ifrs-full:CurrentTradeReceivables",
            "ifrs-full:TradeAndOtherCurrentReceivables", "ifrs-full:TradeReceivables"),
    PPE("Property, plant & equipment", Kind.INSTANT, "ifrs-full:PropertyPlantAndEquipment"),
    TOTAL_LIABILITIES("Total liabilities", Kind.INSTANT, "ifrs-full:Liabilities"),
    CURRENT_LIABILITIES("Current liabilities", Kind.INSTANT, "ifrs-full:CurrentLiabilities"),
    NONCURRENT_BORROWINGS("Long-term borrowings", Kind.INSTANT, "ifrs-full:NoncurrentPortionOfNoncurrentBorrowings",
            "ifrs-full:LongtermBorrowings", "ifrs-full:NoncurrentFinancialLiabilities"),
    EQUITY("Shareholders' equity", Kind.INSTANT, "ifrs-full:Equity", "ifrs-full:EquityAttributableToOwnersOfParent"),
    RETAINED_EARNINGS("Retained earnings", Kind.INSTANT, "ifrs-full:RetainedEarnings"),
    SHARES_OUTSTANDING("Shares outstanding", Kind.INSTANT, "ifrs-full:NumberOfSharesOutstanding",
            "ifrs-full:NumberOfSharesIssued");

    public enum Kind { DURATION, INSTANT }

    private final String label;
    private final Kind kind;
    private final List<String> concepts;

    Metric(String label, Kind kind, String... concepts) {
        this.label = label;
        this.kind = kind;
        this.concepts = List.of(concepts);
    }

    public String label() {
        return label;
    }

    public Kind kind() {
        return kind;
    }

    public List<String> concepts() {
        return concepts;
    }

    /** Share counts aren't money. */
    public boolean monetary() {
        return this != SHARES_OUTSTANDING;
    }
}
