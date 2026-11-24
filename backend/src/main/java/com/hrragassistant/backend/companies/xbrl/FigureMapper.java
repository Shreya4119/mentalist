package com.hrragassistant.backend.companies.xbrl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Turns the facts of one filing into full financial years. An annual report
 * contains its own year and, as comparatives, the year before; both are
 * returned. Only whole-company figures are used (facts broken down by segment,
 * region etc. are skipped), and only in the filing's main currency.
 */
public final class FigureMapper {

    private FigureMapper() {
    }

    public static List<YearFigures> years(Collection<XbrlFact> facts) {
        List<XbrlFact> plain = facts.stream().filter(f -> !f.dimensional()).toList();
        String currency = mainCurrency(plain);

        // Financial years = end dates of full-year revenue or profit figures.
        TreeSet<LocalDate> yearEnds = new TreeSet<>();
        for (XbrlFact f : plain) {
            if (f.fullYear() && (Metric.REVENUE.concepts().contains(f.concept())
                    || Metric.NET_INCOME.concepts().contains(f.concept())
                    || Metric.OPERATING_CASH_FLOW.concepts().contains(f.concept()))) {
                yearEnds.add(f.end());
            }
        }

        List<YearFigures> years = new ArrayList<>();
        for (LocalDate end : yearEnds) {
            Map<Metric, YearFigures.Value> values = new EnumMap<>(Metric.class);
            for (Metric m : Metric.values()) {
                pick(plain, m, end, currency).ifPresent(v -> values.put(m, v));
            }
            if (!values.isEmpty()) {
                years.add(new YearFigures(end, currency, values));
            }
        }
        years.sort(Comparator.comparing(YearFigures::periodEnd));
        return years;
    }

    private static Optional<YearFigures.Value> pick(List<XbrlFact> facts, Metric m, LocalDate end, String currency) {
        for (String concept : m.concepts()) {
            for (XbrlFact f : facts) {
                if (!f.concept().equals(concept) || !f.end().equals(end)) {
                    continue;
                }
                if (m.kind() == Metric.Kind.DURATION ? !f.fullYear() : !f.instant()) {
                    continue;
                }
                if (m.monetary() && currency != null && !currency.equals(f.currency())) {
                    continue;
                }
                double amount = f.value();
                if (m == Metric.CAPEX || m == Metric.COST_OF_SALES || m == Metric.FINANCE_COSTS
                        || m == Metric.SGA || m == Metric.DEPRECIATION) {
                    amount = Math.abs(amount); // reported as positive outflows or as negatives, depending on the company
                }
                return Optional.of(new YearFigures.Value(amount, concept));
            }
        }
        return Optional.empty();
    }

    /** The currency most monetary facts are in. */
    static String mainCurrency(List<XbrlFact> facts) {
        Map<String, Integer> counts = new java.util.HashMap<>();
        for (XbrlFact f : facts) {
            if (f.currency() != null) {
                counts.merge(f.currency(), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }
}
