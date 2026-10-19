package com.hrragassistant.backend.planning.analysis;

/**
 * One computed household ratio. {@code value} is raw (0.18 for 18%, 4.2 for
 * 4.2 months); {@code display} is formatted. {@code formula} spells out the
 * calculation with the household's own numbers, and {@code nextStep} says
 * what would move it towards Healthy.
 */
public record HouseholdRatio(
        String key,
        String name,
        Area area,
        Double value,
        String display,
        String formula,
        Assessment assessment,
        String benchmark,
        String meaning,
        String nextStep) {

    public enum Area {
        SAFETY_NET("Safety net", 25),
        CASH_FLOW("Cash flow", 20),
        DEBT("Debt", 15),
        FUTURE("Future", 25),
        INVESTING("Investing quality", 15);

        private final String label;
        private final int weight;

        Area(String label, int weight) {
            this.label = label;
            this.weight = weight;
        }

        public String label() {
            return label;
        }

        public int weight() {
            return weight;
        }
    }

    public enum Assessment {
        HEALTHY, CAUTION, CONCERN, NOT_APPLICABLE
    }

    public boolean computed() {
        return assessment != Assessment.NOT_APPLICABLE;
    }
}
