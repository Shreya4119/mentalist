package com.hrragassistant.backend.companies.xbrl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Reads the facts of an xBRL-JSON report (the JSON form of an ESEF filing).
 *
 * Periods follow the xBRL-JSON convention: a duration is "start/end" and an
 * instant is a single date-time, where a time of 00:00:00 means "the start of
 * that day", so the value really belongs to the end of the previous day.
 */
public final class XbrlJsonReader {

    /** Dimensions every fact has; anything else (segments, equity components…) makes a fact dimensional. */
    private static final Set<String> CORE_DIMENSIONS = Set.of("concept", "entity", "period", "unit", "language", "noteId");

    private XbrlJsonReader() {
    }

    public static List<XbrlFact> read(JsonNode report) {
        List<XbrlFact> facts = new ArrayList<>();
        JsonNode all = report.path("facts");
        for (Iterator<Map.Entry<String, JsonNode>> it = all.fields(); it.hasNext(); ) {
            JsonNode fact = it.next().getValue();
            JsonNode dims = fact.path("dimensions");
            String concept = dims.path("concept").asText(null);
            String period = dims.path("period").asText(null);
            JsonNode valueNode = fact.get("value");
            if (concept == null || period == null || valueNode == null || valueNode.isNull()) {
                continue;
            }
            Double value = number(valueNode);
            if (value == null) {
                continue; // text facts (notes, names) aren't needed here
            }
            boolean dimensional = false;
            for (Iterator<String> names = dims.fieldNames(); names.hasNext(); ) {
                if (!CORE_DIMENSIONS.contains(names.next())) {
                    dimensional = true;
                    break;
                }
            }
            LocalDate start = null;
            LocalDate end;
            int slash = period.indexOf('/');
            if (slash > 0) {
                start = startDate(period.substring(0, slash));
                end = endDate(period.substring(slash + 1));
            } else {
                end = endDate(period);
            }
            if (end == null || (slash > 0 && start == null)) {
                continue;
            }
            facts.add(new XbrlFact(concept, value, start, end, dims.path("unit").asText(null), dimensional));
        }
        return facts;
    }

    private static Double number(JsonNode node) {
        if (node.isNumber()) {
            return node.asDouble();
        }
        String s = node.asText("").trim();
        if (s.isEmpty() || !s.matches("[-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?")) {
            return null;
        }
        return Double.parseDouble(s);
    }

    /** A period start: the date itself. */
    static LocalDate startDate(String s) {
        return parse(s, false);
    }

    /** A period end or instant: midnight belongs to the previous day. */
    static LocalDate endDate(String s) {
        return parse(s, true);
    }

    private static LocalDate parse(String s, boolean endOfPeriod) {
        try {
            s = s.trim();
            if (s.length() == 10) {
                return LocalDate.parse(s); // a plain date already means "through the end of that day"
            }
            String noZone = s.replaceAll("(Z|[+-][0-9]{2}:[0-9]{2})$", "");
            LocalDateTime t = LocalDateTime.parse(noZone);
            LocalDate d = t.toLocalDate();
            if (endOfPeriod && t.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
                d = d.minusDays(1);
            }
            return d;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
