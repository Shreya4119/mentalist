package com.hrragassistant.backend.reports.intake;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntakeChecksTest {

    private static byte[] b(int... v) {
        byte[] r = new byte[v.length];
        for (int i = 0; i < v.length; i++) r[i] = (byte) v[i];
        return r;
    }

    @Test
    void detectsRealTypeFromContents() {
        assertThat(FileSniffer.detect("%PDF-1.7\n".getBytes())).isEqualTo(FileSniffer.Kind.PDF);
        assertThat(FileSniffer.detect("\r\n%PDF-1.4".getBytes())).isEqualTo(FileSniffer.Kind.PDF);
        assertThat(FileSniffer.detect(b(0xFF, 0xD8, 0xFF, 0xE0))).isEqualTo(FileSniffer.Kind.JPEG);
        assertThat(FileSniffer.detect(b(0x89, 'P', 'N', 'G', 13, 10, 26, 10))).isEqualTo(FileSniffer.Kind.PNG);
        assertThat(FileSniffer.detect(b(0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'))).isEqualTo(FileSniffer.Kind.HEIC);
        assertThat(FileSniffer.detect(b('P', 'K', 3, 4))).isEqualTo(FileSniffer.Kind.ZIP_OFFICE);
        assertThat(FileSniffer.detect("Year,Revenue\n2025,100\n".getBytes())).isEqualTo(FileSniffer.Kind.TEXT);
    }

    @Test
    void explainsRenamedOrUnsupportedFiles() {
        assertThat(FileSniffer.mismatch("pdf", FileSniffer.Kind.PDF)).isNull();
        assertThat(FileSniffer.mismatch("png", FileSniffer.Kind.JPEG)).isNull();
        assertThat(FileSniffer.mismatch("pdf", FileSniffer.Kind.TEXT)).contains("named .pdf").contains("a text file");
        assertThat(FileSniffer.mismatch("jpg", FileSniffer.Kind.HEIC)).contains("HEIC").contains("JPG");
    }

    @Test
    void sanitisesFilenames() {
        assertThat(UploadNames.sanitize("C:\\Users\\me\\Adyen Annual Report.pdf")).isEqualTo("Adyen Annual Report.pdf");
        assertThat(UploadNames.sanitize("../../etc/passwd")).isEqualTo("passwd");
        assertThat(UploadNames.sanitize("")).isEqualTo("report");
        String longName = UploadNames.sanitize("x".repeat(400) + ".pdf");
        assertThat(longName).hasSizeLessThanOrEqualTo(UploadNames.MAX_LENGTH).endsWith(".pdf");
    }
}
