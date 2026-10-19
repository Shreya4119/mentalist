package com.hrragassistant.backend.reports.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrragassistant.backend.reports.analysis.BalanceSheetExtraction;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Plumbing shared by the provider implementations. Request and response
 * bodies are handled as JSON strings with this class's own Jackson 2
 * mapper, so the providers don't depend on which Jackson version Spring's
 * HTTP message converters happen to use.
 */
final class AnalyzerSupport {

    /** Bulky reports can take minutes to read; keep connect short and read long. */
    static final Duration READ_TIMEOUT = Duration.ofMinutes(10);

    /** ~150k tokens of text: generous for an annual report, safe for both providers' context windows. */
    static final int MAX_TEXT_CHARS = 600_000;

    static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private AnalyzerSupport() {
    }

    static RestClient restClient(String baseUrl) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** Parses the model's JSON, tolerating a ```json fence some models add despite JSON mode. */
    static BalanceSheetExtraction parseExtraction(String raw) throws Exception {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        if (s.isEmpty()) {
            throw new IllegalStateException("The model returned an empty response");
        }
        return JSON.readValue(s, BalanceSheetExtraction.class);
    }

    /** Text fallback for spreadsheets, Word files, plain text -- and PDFs too large to send natively. */
    static String extractText(ReportAnalyzer.Source source, List<String> notes) {
        Resource resource = new ByteArrayResource(source.content()) {
            @Override
            public String getFilename() {
                return source.filename();
            }
        };
        String text = new TikaDocumentReader(resource).read().stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
        if (text.isBlank()) {
            throw new IllegalStateException("No readable text found in " + source.filename()
                    + ". If it is a scan or photo, upload it as a PDF or image so the AI can read the pages.");
        }
        if (text.length() > MAX_TEXT_CHARS) {
            notes.add("The report's text was very long; only the first " + (MAX_TEXT_CHARS / 1000)
                    + "k characters were analysed.");
            text = text.substring(0, MAX_TEXT_CHARS);
        }
        return text;
    }

    /** A provider error the user can act on, without echoing request bodies or keys. */
    static IllegalStateException providerError(String provider, Exception e) {
        if (e instanceof RestClientResponseException r) {
            String body = r.getResponseBodyAsString();
            String detail = body.length() > 300 ? body.substring(0, 300) + "…" : body;
            String hint = switch (r.getStatusCode().value()) {
                case 400 -> "The provider rejected the request (often an unreadable, encrypted or too-large file).";
                case 401, 403 -> "The API key was rejected. Check the key in backend/.env.";
                case 404 -> "The configured model was not found. Check the model name in the settings.";
                case 413 -> "The file is too large for this provider.";
                case 429 -> "Rate limit or quota reached. Wait a minute and try again, or switch provider.";
                default -> "The provider returned an error.";
            };
            return new IllegalStateException(provider + ": " + hint + " (HTTP " + r.getStatusCode().value() + ": " + detail + ")", e);
        }
        return new IllegalStateException(provider + ": " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
    }
}
