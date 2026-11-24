package com.hrragassistant.backend.companies;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;

class FilingsUrlsTest {

    private static final URI BASE = URI.create("https://filings.xbrl.org/");

    @Test
    void addressesOnTheFilingsServiceAreKept() {
        assertThat(FilingsUrls.onService(BASE, "/724500ABC/2024-12-31/ESEF/NL/0/report.json"))
                .hasToString("https://filings.xbrl.org/724500ABC/2024-12-31/ESEF/NL/0/report.json");
        assertThat(FilingsUrls.onService(BASE, "https://FILINGS.xbrl.org/api/filings?page%5Bnumber%5D=2")).isNotNull();
        assertThat(FilingsUrls.onService(BASE, "/a b/report.json")).hasToString("https://filings.xbrl.org/a%20b/report.json");
    }

    @Test
    void anythingElseIsRefused() {
        assertThat(FilingsUrls.onService(BASE, "http://169.254.169.254/latest/meta-data/")).isNull(); // cloud metadata
        assertThat(FilingsUrls.onService(BASE, "http://localhost:6379/")).isNull();                  // Redis
        assertThat(FilingsUrls.onService(BASE, "http://filings.xbrl.org/report.json")).isNull();     // downgraded to http
        assertThat(FilingsUrls.onService(BASE, "https://filings.xbrl.org.evil.example/x")).isNull();
        assertThat(FilingsUrls.onService(BASE, "https://filings.xbrl.org@evil.example/x")).isNull();
        assertThat(FilingsUrls.onService(BASE, "https://filings.xbrl.org:8443/x")).isNull();
        assertThat(FilingsUrls.onService(BASE, "//evil.example/x")).isNull();
        assertThat(FilingsUrls.onService(BASE, "javascript:alert(1)")).isNull();
        assertThat(FilingsUrls.onService(BASE, "file:///etc/passwd")).isNull();
        assertThat(FilingsUrls.onService(BASE, null)).isNull();
    }
}
