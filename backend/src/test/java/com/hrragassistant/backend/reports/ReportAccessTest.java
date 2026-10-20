package com.hrragassistant.backend.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ReportAccessTest {

    private static FinancialReportAnalysis by(String uploader) {
        return new FinancialReportAnalysis(UUID.randomUUID(), uploader, "gemini", "m");
    }

    @Test
    void peopleSeeTheirOwnReportsAndTheSharedSamplesOnly() {
        Set<String> admins = Set.of("admin");
        assertThat(ReportAccess.canView(by("anna"), "anna", false, admins)).isTrue();
        assertThat(ReportAccess.canView(by("admin"), "anna", false, admins)).isTrue();
        assertThat(ReportAccess.canView(by("ben"), "anna", false, admins)).isFalse();
        assertThat(ReportAccess.canView(by("ben"), "admin", true, admins)).isTrue();
        assertThat(ReportAccess.canView(null, "anna", false, admins)).isFalse();
        assertThat(ReportAccess.isShared(by("admin"), admins)).isTrue();
        assertThat(ReportAccess.isShared(by("anna"), admins)).isFalse();
    }
}
