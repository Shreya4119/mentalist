package com.hrragassistant.backend.reports.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.hrragassistant.backend.reports.analysis.BalanceSheetExtraction;
import org.junit.jupiter.api.Test;

/** Guards against the schema the model fills drifting away from the record it is parsed into. */
class ExtractionSchemaTest {

    @SuppressWarnings("unchecked")
    @Test
    void schemaPropertiesMatchTheExtractionRecord() {
        for (ExtractionSchema.Dialect d : ExtractionSchema.Dialect.values()) {
            Map<String, Object> root = ExtractionSchema.build(d);
            Map<String, Object> props = (Map<String, Object>) root.get("properties");
            assertThat(props.keySet()).isEqualTo(components(BalanceSheetExtraction.class));

            Map<String, Object> figures = (Map<String, Object>) ((Map<String, Object>) props.get("figures")).get("properties");
            assertThat(figures.keySet()).isEqualTo(components(BalanceSheetExtraction.Figures.class));
            assertThat((List<String>) root.get("required")).containsAll(props.keySet());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void dialectsUseTheirOwnNullableSyntax() {
        Map<String, Object> claude = figureCurrent(ExtractionSchema.build(ExtractionSchema.Dialect.JSON_SCHEMA));
        Map<String, Object> gemini = figureCurrent(ExtractionSchema.build(ExtractionSchema.Dialect.GEMINI));

        assertThat(claude.get("type")).isEqualTo(List.of("number", "null"));
        assertThat(gemini.get("type")).isEqualTo("NUMBER");
        assertThat(gemini.get("nullable")).isEqualTo(true);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> figureCurrent(Map<String, Object> root) {
        Map<String, Object> props = (Map<String, Object>) root.get("properties");
        Map<String, Object> figures = (Map<String, Object>) ((Map<String, Object>) props.get("figures")).get("properties");
        return (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) figures.get("revenue")).get("properties")).get("current");
    }

    private static Set<String> components(Class<?> record) {
        return Arrays.stream(record.getRecordComponents()).map(c -> c.getName()).collect(Collectors.toSet());
    }
}
