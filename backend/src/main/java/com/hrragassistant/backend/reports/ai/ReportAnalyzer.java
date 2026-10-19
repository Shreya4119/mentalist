package com.hrragassistant.backend.reports.ai;

import java.util.List;
import java.util.Locale;

import com.hrragassistant.backend.reports.analysis.BalanceSheetExtraction;
import com.hrragassistant.backend.reports.prep.ProviderLimits;

/**
 * One AI provider that can read a financial report and return the
 * structured extraction. Implementations: Gemini (default, free tier) and
 * Claude (paid). Selected per analysis via {@link ReportAnalyzers}.
 */
public interface ReportAnalyzer {

    /** Stable id used in the API and stored on each analysis, e.g. "gemini". */
    String id();

    String label();

    String model();

    /** True when an API key is configured; unconfigured providers are shown but disabled. */
    boolean configured();

    /** What one request may contain; the preprocessor shrinks reports to fit. */
    default ProviderLimits limits() {
        return ProviderLimits.GEMINI;
    }

    Result analyze(Source source) throws Exception;

    /** The report as uploaded. {@code mimeType} decides whether it is sent natively or as extracted text. */
    record Source(String filename, byte[] content, String mimeType) {

        public boolean isPdf() {
            return "application/pdf".equals(mimeType);
        }

        public boolean isImage() {
            return mimeType != null && mimeType.startsWith("image/");
        }

        /** PDFs and images go to the model as-is so it can read charts, tables and scans. */
        public boolean isNative() {
            return isPdf() || isImage();
        }

        public static String mimeTypeFor(String filename) {
            String f = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
            if (f.endsWith(".pdf")) {
                return "application/pdf";
            }
            if (f.endsWith(".png")) {
                return "image/png";
            }
            if (f.endsWith(".jpg") || f.endsWith(".jpeg")) {
                return "image/jpeg";
            }
            if (f.endsWith(".webp")) {
                return "image/webp";
            }
            return "text/plain"; // xlsx, xls, csv, docx, txt, html: sent as text extracted by Tika
        }
    }

    /** The extraction plus notes the application adds about how the report was read. */
    record Result(BalanceSheetExtraction extraction, List<String> processingNotes) {
    }
}
