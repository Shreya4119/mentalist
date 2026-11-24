package com.hrragassistant.backend.companies;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/** Turns pages of the filings index (filings with their entity included) into one entry per company. */
final class FilingsDirectory {

    private FilingsDirectory() {
    }

    /** Adds one page of filings (with included entities) to the list; returns how many filings it held. */
    static int addPage(JsonNode body, String country, Map<String, DirectoryEntry> byLei) {
        Map<String, JsonNode> entities = new HashMap<>();
        for (JsonNode inc : body.path("included")) {
            if ("entity".equals(inc.path("type").asText())) {
                entities.put(inc.path("id").asText(), inc.path("attributes"));
            }
        }
        int count = 0;
        for (JsonNode f : body.path("data")) {
            count++;
            JsonNode ent = entities.get(f.path("relationships").path("entity").path("data").path("id").asText(""));
            if (ent == null) {
                continue;
            }
            String lei = ent.path("identifier").asText(null);
            String name = ent.path("name").asText(null);
            if (lei == null || name == null || name.isBlank()) {
                continue;
            }
            LocalDate end = null;
            String pe = f.path("attributes").path("period_end").asText(null);
            if (pe != null && pe.length() >= 10) {
                try {
                    end = LocalDate.parse(pe.substring(0, 10));
                } catch (RuntimeException ignored) {
                    // keep the company, without a year
                }
            }
            DirectoryEntry old = byLei.get(lei);
            if (old == null || (end != null && (old.lastPeriodEnd() == null || end.isAfter(old.lastPeriodEnd())))) {
                byLei.put(lei, new DirectoryEntry(lei, name.trim(), country, end));
            }
        }
        return count;
    }

}
