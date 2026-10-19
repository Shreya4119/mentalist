package com.hrragassistant.backend.reports.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hrragassistant.backend.reports.analysis.BalanceSheetExtraction;

/**
 * The JSON schema the model must fill -- one definition, rendered in the two
 * dialects the providers expect:
 *
 *  - {@link Dialect#JSON_SCHEMA}: standard JSON Schema (Claude tool input_schema);
 *    nullable fields use a type array, e.g. ["number","null"].
 *  - {@link Dialect#GEMINI}: Gemini's OpenAPI-style responseSchema; upper-case
 *    type names and "nullable": true.
 *
 * Field names match {@link BalanceSheetExtraction} exactly so the model's JSON
 * deserializes straight into it.
 */
public final class ExtractionSchema {

    public enum Dialect { JSON_SCHEMA, GEMINI }

    private final Dialect d;

    private ExtractionSchema(Dialect d) {
        this.d = d;
    }

    public static Map<String, Object> build(Dialect dialect) {
        return new ExtractionSchema(dialect).root();
    }

    private Map<String, Object> root() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("companyName", str("Legal name of the company the report is about."));
        props.put("reportingPeriod", str("Current period covered, e.g. 'FY 2025-26' or 'Year ended 31 Dec 2025'."));
        props.put("priorPeriod", str("Comparative (prior) period shown alongside, or empty string if none."));
        props.put("currency", str("ISO currency code of the figures, e.g. 'INR', 'USD', 'EUR'."));
        props.put("unit", str("Unit every figure below is expressed in, e.g. 'INR crore', 'USD millions', 'EUR thousands'."));
        props.put("industry", str("Industry or sector in a few words, e.g. 'IT services', 'Private bank'."));
        props.put("financialInstitution", bool("True for banks, insurers, NBFCs and similar lenders, whose balance sheets work differently."));
        props.put("statementBasis", str("'Consolidated', 'Standalone' or 'Unknown' -- which statements the figures come from."));

        Map<String, Object> figureProps = new LinkedHashMap<>();
        for (String[] kl : BalanceSheetExtraction.FIGURE_LABELS) {
            figureProps.put(kl[0], figurePair(kl[1]));
        }
        props.put("figures", obj(figureProps, "Standard line items, each with current and prior values in the stated unit."));

        props.put("summary", str("3-4 plain-language sentences on the company's financial condition for a first-time investor. No jargon, no advice."));
        props.put("highlights", arr(str("A concrete fact with a number."), "3-6 most notable concrete facts from the report."));
        props.put("strengths", arr(finding(), "2-5 genuine financial strengths, each backed by evidence from the report."));
        props.put("redFlags", arr(finding(), "0-6 risks or warning signs stated or clearly shown in the report (e.g. auditor qualifications, going-concern doubts, large contingent liabilities, rising debt, falling margins, related-party dealings, pledged shares)."));
        props.put("visualInsights", arr(str("One insight."), "What the report's charts, graphs and images show (trends, segment splits). Empty if there are none."));
        props.put("outlook", str("Management's stated outlook or guidance in 1-2 sentences, or empty string if none."));
        props.put("dataQualityNotes", arr(str("One note."), "Assumptions made, figures derived, unit conversions, missing statements or unreadable pages."));

        return obj(props, "Structured extraction of a company's financial report.");
    }

    private Map<String, Object> figurePair(String label) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("current", num("Current-period " + label + " in the stated unit, or null if not in the report. Never estimate."));
        p.put("prior", num("Prior-period " + label + " in the stated unit, or null if not shown."));
        p.put("source", str("Where it was found, e.g. 'Consolidated balance sheet, p. 142'. Empty if not found."));
        return obj(p, label);
    }

    private Map<String, Object> finding() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("point", str("The point, in one plain-language sentence."));
        p.put("evidence", str("The figure, page or note it rests on."));
        return obj(p, "A finding backed by evidence.");
    }

    // ------------------------------------------------------------------ primitives

    private Map<String, Object> str(String description) {
        return node(d == Dialect.GEMINI ? "STRING" : "string", description, false);
    }

    private Map<String, Object> bool(String description) {
        return node(d == Dialect.GEMINI ? "BOOLEAN" : "boolean", description, false);
    }

    private Map<String, Object> num(String description) {
        return node(d == Dialect.GEMINI ? "NUMBER" : "number", description, true);
    }

    private Map<String, Object> arr(Map<String, Object> items, String description) {
        Map<String, Object> m = node(d == Dialect.GEMINI ? "ARRAY" : "array", description, false);
        m.put("items", items);
        return m;
    }

    private Map<String, Object> obj(Map<String, Object> props, String description) {
        Map<String, Object> m = node(d == Dialect.GEMINI ? "OBJECT" : "object", description, false);
        m.put("properties", props);
        m.put("required", new ArrayList<>(props.keySet()));
        if (d == Dialect.GEMINI) {
            m.put("propertyOrdering", new ArrayList<>(props.keySet()));
        } else {
            m.put("additionalProperties", false);
        }
        return m;
    }

    private Map<String, Object> node(String type, String description, boolean nullable) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (nullable && d == Dialect.JSON_SCHEMA) {
            m.put("type", List.of(type, "null"));
        } else {
            m.put("type", type);
        }
        if (nullable && d == Dialect.GEMINI) {
            m.put("nullable", true);
        }
        m.put("description", description);
        return m;
    }
}
