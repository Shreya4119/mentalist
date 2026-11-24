package com.hrragassistant.backend.companies.xbrl;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/**
 * One financial year of a company as reported: each value remembers the IFRS
 * concept it came from, so the app can always show where a number came from.
 */
public record YearFigures(LocalDate periodEnd, String currency, Map<Metric, Value> values) {

    public record Value(double amount, String concept) {
    }

    public YearFigures {
        Map<Metric, Value> copy = new EnumMap<>(Metric.class);
        if (values != null) {
            copy.putAll(values);
        }
        values = copy;
    }

    public Double get(Metric m) {
        Value v = values.get(m);
        return v == null ? null : v.amount();
    }

    public boolean has(Metric m) {
        return values.containsKey(m);
    }

    /** Fills metrics this year is missing from another source for the same year (e.g. a later filing's comparatives). */
    public YearFigures fillFrom(YearFigures other) {
        Map<Metric, Value> merged = new EnumMap<>(Metric.class);
        merged.putAll(values);
        other.values().forEach(merged::putIfAbsent);
        return new YearFigures(periodEnd, currency != null ? currency : other.currency(), merged);
    }
}
