package com.hrragassistant.backend.reports.ai;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Anthropic Claude via the Messages REST API. Claude reads PDFs as page
 * images plus text, so charts, tables and scanned pages are understood.
 * Limits: 32 MB per request and up to 600 pages; base64 adds ~33%, so PDFs
 * over {@value #NATIVE_LIMIT_BYTES} bytes fall back to extracted text.
 *
 * Structured output is enforced by forcing a single tool call whose
 * input_schema is the extraction schema -- the tool's input IS the result.
 */
@Component
public class ClaudeReportAnalyzer implements ReportAnalyzer {

    static final long NATIVE_LIMIT_BYTES = 22L * 1024 * 1024;
    private static final String TOOL = "record_financial_report";

    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final RestClient http;

    public ClaudeReportAnalyzer(
            @Value("${app.reports.claude.api-key:}") String apiKey,
            @Value("${app.reports.claude.model:claude-sonnet-5-5}") String model,
            @Value("${app.reports.claude.max-tokens:16000}") int maxTokens,
            @Value("${app.reports.claude.base-url:https://api.anthropic.com}") String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.http = AnalyzerSupport.restClient(baseUrl);
    }

    @Override
    public String id() {
        return "claude";
    }

    @Override
    public String label() {
        return "Anthropic Claude";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public com.hrragassistant.backend.reports.prep.ProviderLimits limits() {
        return com.hrragassistant.backend.reports.prep.ProviderLimits.CLAUDE;
    }

    @Override
    public Result analyze(Source source) throws Exception {
        if (!configured()) {
            throw new IllegalStateException("Claude is not configured: set ANTHROPIC_API_KEY in backend/.env");
        }
        List<String> notes = new ArrayList<>();
        List<Object> content = new ArrayList<>();

        try {
            if (source.isNative() && source.content().length <= NATIVE_LIMIT_BYTES) {
                String data = Base64.getEncoder().encodeToString(source.content());
                content.add(Map.of(
                        "type", source.isPdf() ? "document" : "image",
                        "source", Map.of("type", "base64", "media_type", source.mimeType(), "data", data)));
            } else {
                // See GeminiReportAnalyzer: text sources arrive already explained by the preprocessor.
                if (source.isNative()) {
                    notes.add("The file was too large to send as pages, so it was analysed from its extracted text only.");
                }
                content.add(Map.of("type", "text", "text", "Report filename: " + source.filename()
                        + "\n\nFull report text:\n\n" + AnalyzerSupport.extractText(source, notes)));
            }
            content.add(Map.of("type", "text", "text", ExtractionPrompts.INSTRUCTION
                    + "\nRecord your answer by calling the " + TOOL + " tool."));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("max_tokens", maxTokens);
            body.put("system", ExtractionPrompts.SYSTEM);
            body.put("tools", List.of(Map.of(
                    "name", TOOL,
                    "description", "Record the structured extraction of the financial report.",
                    "input_schema", ExtractionSchema.build(ExtractionSchema.Dialect.JSON_SCHEMA))));
            body.put("tool_choice", Map.of("type", "tool", "name", TOOL));
            body.put("messages", List.of(Map.of("role", "user", "content", content)));

            String response = http.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(AnalyzerSupport.JSON.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);

            return new Result(AnalyzerSupport.parseExtraction(readToolInput(response)), notes);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw AnalyzerSupport.providerError("Claude", e);
        }
    }

    static String readToolInput(String response) throws Exception {
        JsonNode root = AnalyzerSupport.JSON.readTree(response);
        if ("max_tokens".equals(root.path("stop_reason").asText())) {
            throw new IllegalStateException("Claude's answer was cut off (output limit reached). Raise app.reports.claude.max-tokens and try again.");
        }
        for (JsonNode block : root.path("content")) {
            if ("tool_use".equals(block.path("type").asText()) && TOOL.equals(block.path("name").asText())) {
                return AnalyzerSupport.JSON.writeValueAsString(block.path("input"));
            }
        }
        throw new IllegalStateException("Claude did not return the structured result");
    }
}
