package com.hrragassistant.backend.companies;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads official EU annual reports (ESEF) from filings.xbrl.org, the free
 * index run by XBRL International. It isn't complete or official, so the app
 * always links to each filing and names it as the source.
 *
 * The service asks API users to identify themselves; we send a user agent
 * naming the app and its public repository (no personal data).
 */
@Component
public class FilingsClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    /** xBRL-JSON reports of large groups can be tens of MB; refuse anything absurd. */
    private static final long MAX_REPORT_BYTES = 80L * 1024 * 1024;

    private static final long MAX_PAGE_BYTES = 20L * 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;

    private final String baseUrl;
    private final URI base;
    private final RestClient http;

    public FilingsClient(@Value("${app.filings.base-url:https://filings.xbrl.org}") String baseUrl,
                         @Value("${app.filings.user-agent:Mentalist (+https://github.com/Shreya4119/mentalist)}") String userAgent) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.base = URI.create(this.baseUrl + "/");
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER) // followed by hand, only on the service's own host
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(90));
        this.http = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", userAgent)
                .defaultHeader("Accept", "application/vnd.api+json, application/json")
                .build();
    }

    public record EntityHit(String lei, String name) {
    }

    public record Filing(String id, LocalDate periodEnd, String country, String jsonUrl, String viewerUrl,
                         int errorCount, String processed) {
    }

    private static final int DIRECTORY_PAGE = 500;
    private static final int DIRECTORY_MAX_PAGES = 40;

    /**
     * Every company with an annual filing from one country, newest filing year
     * per company. Pages through the filings index (filings with their entity
     * included), so a large country takes a few requests.
     */
    public List<DirectoryEntry> directory(String country) {
        ArrayNode filter = JsonNodeFactory.instance.arrayNode();
        filter.addObject().put("name", "country").put("op", "eq").put("val", country);
        String first = "/api/filings?filter=" + enc(filter.toString()) + "&include=entity&page%5Bsize%5D=" + DIRECTORY_PAGE;
        Map<String, DirectoryEntry> byLei = new LinkedHashMap<>();
        String next = baseUrl + first;
        for (int page = 1; next != null && page <= DIRECTORY_MAX_PAGES; page++) {
            JsonNode body = getAbsolute(next);
            int count = FilingsDirectory.addPage(body, country, byLei);
            String link = body.path("links").path("next").asText(null);
            if (link != null && !link.isBlank()) {
                next = absolute(link);
            } else if (count == DIRECTORY_PAGE) {
                next = baseUrl + first + "&page%5Bnumber%5D=" + (page + 1);
            } else {
                next = null;
            }
        }
        return new ArrayList<>(byLei.values());
    }

    /** Companies whose name contains the query (case-insensitive). */
    public List<EntityHit> searchEntities(String query) {
        ArrayNode filter = JsonNodeFactory.instance.arrayNode();
        filter.addObject().put("name", "name").put("op", "ilike").put("val", "%" + query.replace("%", "") + "%");
        JsonNode body = get("/api/entities?filter=" + enc(filter.toString()) + "&page%5Bsize%5D=25");
        List<EntityHit> hits = new ArrayList<>();
        for (JsonNode e : body.path("data")) {
            String lei = e.path("attributes").path("identifier").asText(null);
            String name = e.path("attributes").path("name").asText(null);
            if (lei != null && name != null) {
                hits.add(new EntityHit(lei, name));
            }
        }
        return hits;
    }

    public String entityName(String lei) {
        JsonNode body = get("/api/entities/" + enc(lei));
        return body.path("data").path("attributes").path("name").asText(null);
    }

    public List<Filing> filings(String lei) {
        JsonNode body = get("/api/entities/" + enc(lei) + "/filings?page%5Bsize%5D=100");
        List<Filing> filings = new ArrayList<>();
        for (JsonNode f : body.path("data")) {
            JsonNode a = f.path("attributes");
            String periodEnd = a.path("period_end").asText(null);
            if (periodEnd == null) {
                continue;
            }
            try {
                filings.add(new Filing(f.path("id").asText(), LocalDate.parse(periodEnd.substring(0, 10)),
                        a.path("country").asText(null), a.path("json_url").asText(null),
                        absolute(a.path("viewer_url").asText(null)), a.path("error_count").asInt(0),
                        a.path("processed").asText("")));
            } catch (RuntimeException ignored) {
                // a malformed date: skip that filing
            }
        }
        return filings;
    }

    /** Downloads a filing's xBRL-JSON report (only from the filings service itself). */
    public JsonNode report(String jsonUrl) throws IOException {
        URI uri = FilingsUrls.onService(base, jsonUrl);
        if (uri == null) {
            throw new IOException("The filing's report address is not on " + base.getHost());
        }
        byte[] bytes = fetch(uri, MAX_REPORT_BYTES);
        if (bytes.length == 0) {
            throw new IOException("Empty report");
        }
        return JSON.readTree(bytes);
    }

    private JsonNode get(String pathAndQuery) {
        return getAbsolute(baseUrl + pathAndQuery);
    }

    private JsonNode getAbsolute(String url) {
        URI uri = FilingsUrls.onService(base, url);
        if (uri == null) {
            throw new RestClientException("The filings service pointed to another site; refused");
        }
        try {
            byte[] body = fetch(uri, MAX_PAGE_BYTES);
            return JSON.readTree(body.length == 0 ? "{}".getBytes(StandardCharsets.UTF_8) : body);
        } catch (IOException e) {
            throw new RestClientException("Unreadable answer from the filings service", e);
        }
    }

    /**
     * GET with a size cap that is enforced while reading (a huge answer never
     * fills the memory) and redirects followed only on the service's host.
     */
    private byte[] fetch(URI uri, long maxBytes) throws IOException {
        URI current = uri;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            final URI target = current;
            Object result = http.get().uri(target).exchange((request, response) -> {
                int status = response.getStatusCode().value();
                if (status >= 300 && status < 400) {
                    URI location = response.getHeaders().getLocation();
                    URI next = location == null ? null : FilingsUrls.onService(base, target.resolve(location).toString());
                    if (next == null) {
                        throw new IOException("Redirect away from " + base.getHost() + " refused");
                    }
                    return next;
                }
                if (status >= 400) {
                    throw new RestClientException("Filings service answered " + status + " for " + target.getPath());
                }
                try (InputStream in = response.getBody()) {
                    byte[] bytes = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1));
                    if (bytes.length > maxBytes) {
                        throw new IOException("Answer is too large to read");
                    }
                    return bytes;
                }
            });
            if (result instanceof byte[] bytes) {
                return bytes;
            }
            current = (URI) result;
        }
        throw new IOException("Too many redirects");
    }

    /** Viewer links are shown to users, so only addresses on the service are kept. */
    private String absolute(String url) {
        URI u = FilingsUrls.onService(base, url);
        return u == null ? null : u.toString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
