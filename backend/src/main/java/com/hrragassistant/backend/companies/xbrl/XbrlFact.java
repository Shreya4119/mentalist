package com.hrragassistant.backend.companies.xbrl;

import java.time.LocalDate;

/**
 * One reported number from an xBRL-JSON report. {@code start} is null for
 * instant facts (balance-sheet values at a date); {@code end} is the last
 * day the value covers (inclusive).
 */
public record XbrlFact(String concept, double value, LocalDate start, LocalDate end, String unit, boolean dimensional) {

    public boolean instant() {
        return start == null;
    }

    /** About a year long: a full-year figure rather than a quarter or half-year. */
    public boolean fullYear() {
        if (start == null) {
            return false;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        return days >= 350 && days <= 380;
    }

    /** ISO currency of a monetary fact ("iso4217:EUR" → "EUR"), or null. */
    public String currency() {
        return unit != null && unit.startsWith("iso4217:") ? unit.substring("iso4217:".length()) : null;
    }
}
