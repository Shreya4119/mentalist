package com.hrragassistant.backend.reports.ai;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Google Gemini via the generateContent REST API. Gemini reads PDFs
 * natively -- text, tables, charts and scanned pages -- up to 50 MB /
 * 1,000 pages. Files up to {@value #INLINE_LIMIT_BYTES} bytes are sent
 * inline; larger ones go through the Files API (resumable upload) and are
 * deleted again after analysis. Structured output uses responseSchema.
 *
 * Note: Gemini's free tier may use submitted content to improve Google's
 * products -- only analyse public reports with it.
 */
@Component
public class GeminiReportAnalyzer implements ReportAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(GeminiReportAnalyzer.class);

    static final long INLINE_LIMIT_BYTES = 15L * 1024 * 1024;
    static final long NATIVE_LIMIT_BYTES = 50L * 1024 * 1024;

    private final String apiKey;
    private final String model;
    private final RestClient http;

    public GeminiReportAnalyzer(
            @Value("${app.reports.gemini.api-key:}") String apiKey,
            @Value("${app.reports.gemini.model:gemini-3.8-flash}") String model,
            @Value("${app.reports.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.http = AnalyzerSupport.restClient(baseUrl);
    }

    @Override
    public String id() {
        return "gemini";
    }

    @Override
    public String label() {
        return "Google Gemini";
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
    public Result analyze(Source source) throws Exception {
        if (!configured()) {
            throw new IllegalStateException("Gemini is not configured: set GEMINI_API_KEY in backend/.env");
        }
        List<String> notes = new ArrayList<>();
        List<Object> parts = new ArrayList<>();
        String uploadedFileName = null;

        try {
            if (source.isNative() && source.content().length <= INLINE_LIMIT_BYTES) {
                parts.add(Map.of("inlineData", Map.of(
                        "mimeType", source.mimeType(),
                        "data", Base64.getEncoder().encodeToString(source.content()))));
            } else if (source.isNative() && source.content().length <= NATIVE_LIMIT_BYTES) {
                JsonNode file = uploadFile(source);
                uploadedFileName = file.path("name").asText();
                parts.add(Map.of("fileData", Map.of("mimeType", source.mimeType(), "fileUri", file.path("uri").asText())));
            } else {
                // The preprocessor keeps PDFs and images within the limits, so this is a text source
                // (spreadsheet, Word or text file, or extracted text) and it has already said so.
                if (source.isNative()) {
                    notes.add("The file was too large to send as pages, so it was analysed from its extracted text only.");
                }
                parts.add(Map.of("text", "Report filename: " + source.filename() + "\n\nFull report text:\n\n"
                        + AnalyzerSupport.extractText(source, notes)));
            }
            parts.add(Map.of("text", ExtractionPrompts.INSTRUCTION));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", ExtractionPrompts.SYSTEM))));
            body.put("contents", List.of(Map.of("role", "user", "parts", parts)));
            body.put("generationConfig", Map.of(
                    "responseMimeType", "application/json",
                    "responseSchema", ExtractionSchema.build(ExtractionSchema.Dialect.GEMINI),
                    "maxOutputTokens", 16384));

            String response = http.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(AnalyzerSupport.JSON.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);

            return new Result(AnalyzerSupport.parseExtraction(readText(response)), notes);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw AnalyzerSupport.providerError("Gemini", e);
        } finally {
            if (uploadedFileName != null) {
                deleteFile(uploadedFileName);
            }
        }
    }

    /** Pulls the JSON text out of a generateContent response, failing clearly on blocks and truncation. */
    static String readText(String response) throws Exception {
        JsonNode root = AnalyzerSupport.JSON.readTree(response);
        String block = root.path("promptFeedback").path("blockReason").asText("");
        if (!block.isEmpty()) {
            throw new IllegalStateException("Gemini blocked the request (" + block + ")");
        }
        JsonNode candidate = root.path("candidates").path(0);
        String finish = candidate.path("finishReason").asText("");
        if ("MAX_TOKENS".equals(finish)) {
            throw new IllegalStateException("Gemini's answer was cut off (output limit reached). Try again or use Claude.");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false) && part.has("text")) {
                text.append(part.path("text").asText());
            }
        }
        if (text.isEmpty()) {
            throw new IllegalStateException("Gemini returned no content" + (finish.isEmpty() ? "" : " (finish reason " + finish + ")"));
        }
        return text.toString();
    }

    /** Files API resumable upload: start a session, send the bytes, then wait until the file is ACTIVE. */
    private JsonNode uploadFile(Source source) throws Exception {
        ResponseEntity<String> start = http.post()
                .uri("/upload/v1beta/files")
                .header("x-goog-api-key", apiKey)
                .header("X-Goog-Upload-Protocol", "resumable")
                .header("X-Goog-Upload-Command", "start")
                .header("X-Goog-Upload-Header-Content-Length", String.valueOf(source.content().length))
                .header("X-Goog-Upload-Header-Content-Type", source.mimeType())
                .contentType(MediaType.APPLICATION_JSON)
                .body(AnalyzerSupport.JSON.writeValueAsString(Map.of("file", Map.of("displayName", source.filename()))))
                .retrieve()
                .toEntity(String.class);
        HttpHeaders headers = start.getHeaders();
        String uploadUrl = headers.getFirst("x-goog-upload-url");
        if (uploadUrl == null) {
            throw new IllegalStateException("Gemini Files API did not return an upload URL");
        }

        String uploaded = RestClient.create().post()
                .uri(java.net.URI.create(uploadUrl))
                .header("X-Goog-Upload-Offset", "0")
                .header("X-Goog-Upload-Command", "upload, finalize")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(source.content())
                .retrieve()
                .body(String.class);
        JsonNode file = AnalyzerSupport.JSON.readTree(uploaded).path("file");

        Instant deadline = Instant.now().plus(Duration.ofMinutes(3));
        while ("PROCESSING".equals(file.path("state").asText()) && Instant.now().isBefore(deadline)) {
            Thread.sleep(2000);
            String status = http.get()
                    .uri("/v1beta/" + file.path("name").asText())
                    .header("x-goog-api-key", apiKey)
                    .retrieve()
                    .body(String.class);
            file = AnalyzerSupport.JSON.readTree(status);
        }
        if (!"ACTIVE".equals(file.path("state").asText("ACTIVE"))) {
            throw new IllegalStateException("Gemini could not process the uploaded file (state " + file.path("state").asText() + ")");
        }
        return file;
    }

    private void deleteFile(String name) {
        try {
            http.delete().uri("/v1beta/" + name).header("x-goog-api-key", apiKey).retrieve().toBodilessEntity();
        } catch (Exception e) {
            // Files expire on their own after 48 hours; a failed clean-up is not worth failing the analysis.
            log.debug("Could not delete Gemini file {}: {}", name, e.getMessage());
        }
    }
}
