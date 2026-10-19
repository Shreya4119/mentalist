package com.hrragassistant.backend.reports.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hrragassistant.backend.reports.analysis.BalanceSheetExtraction;
import org.junit.jupiter.api.Test;

class ProviderResponseParsingTest {

    static final String EXTRACTION = """
            {"companyName":"Acme Ltd","reportingPeriod":"FY2026","priorPeriod":"FY2025","currency":"INR",
             "unit":"INR crore","industry":"IT","financialInstitution":false,"statementBasis":"Consolidated",
             "figures":{"revenue":{"current":1000,"prior":880,"source":"p. 10"},"netIncome":{"current":150,"prior":null,"source":""}},
             "summary":"s","highlights":["h"],"strengths":[{"point":"p","evidence":"e"}],"redFlags":[],
             "visualInsights":[],"outlook":"","dataQualityNotes":[],"someNewField":"ignored"}
            """;

    @Test
    void geminiTextIsJoinedSkippingThoughtParts() throws Exception {
        String response = """
                {"candidates":[{"finishReason":"STOP","content":{"parts":[
                  {"thought":true,"text":"thinking..."},{"text":%s}]}}]}
                """.formatted(AnalyzerSupport.JSON.writeValueAsString(EXTRACTION));
        BalanceSheetExtraction x = AnalyzerSupport.parseExtraction(GeminiReportAnalyzer.readText(response));

        assertThat(x.companyName()).isEqualTo("Acme Ltd");
        assertThat(x.figures().revenue().current()).isEqualTo(1000.0);
        assertThat(x.figures().netIncome().prior()).isNull();
        assertThat(x.figures().totalAssets()).isNull();
    }

    @Test
    void geminiTruncationAndBlocksFailClearly() {
        assertThatThrownBy(() -> GeminiReportAnalyzer.readText("{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}"))
                .hasMessageContaining("cut off");
        assertThatThrownBy(() -> GeminiReportAnalyzer.readText("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}"))
                .hasMessageContaining("blocked");
    }

    @Test
    void claudeToolInputIsTheResult() throws Exception {
        String response = """
                {"stop_reason":"tool_use","content":[{"type":"text","text":"ok"},
                 {"type":"tool_use","name":"record_financial_report","input":%s}]}
                """.formatted(EXTRACTION);
        BalanceSheetExtraction x = AnalyzerSupport.parseExtraction(ClaudeReportAnalyzer.readToolInput(response));

        assertThat(x.unit()).isEqualTo("INR crore");
        assertThat(x.strengths()).hasSize(1);
    }

    @Test
    void codeFencesAreTolerated() throws Exception {
        assertThat(AnalyzerSupport.parseExtraction("```json\n" + EXTRACTION + "\n```").currency()).isEqualTo("INR");
    }
}
