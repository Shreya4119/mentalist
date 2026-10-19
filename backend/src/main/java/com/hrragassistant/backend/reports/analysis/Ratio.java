package com.hrragassistant.backend.reports.analysis;

/**
 * One computed financial ratio. {@code value}/{@code priorValue} are raw
 * numbers (0.124 for 12.4%); {@code display} is the formatted current value.
 * {@code formula} spells out the calculation with the actual figures used,
 * so a reader can check it against the report.
 */
public record Ratio(
        String key,
        String name,
        Category category,
        Format format,
        Double value,
        Double priorValue,
        String display,
        String priorDisplay,
        String formula,
        Assessment assessment,
        String benchmark,
        String explanation) {

    public enum Category {
        PROFITABILITY("Profitability"),
        LIQUIDITY("Liquidity"),
        LEVERAGE("Leverage & solvency"),
        CASH_FLOW("Cash flow"),
        EFFICIENCY("Efficiency"),
        GROWTH("Growth");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Format {
        MULTIPLE, PERCENT, AMOUNT
    }

    public enum Assessment {
        HEALTHY, CAUTION, CONCERN, NOT_APPLICABLE
    }

    public boolean computed() {
        return value != null && assessment != Assessment.NOT_APPLICABLE;
    }
}
