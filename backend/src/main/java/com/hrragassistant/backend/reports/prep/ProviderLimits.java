package com.hrragassistant.backend.reports.prep;

/**
 * What one AI provider accepts in a single request. The preprocessor shrinks
 * reports to fit: fewer pages, compressed page images, or text as a last
 * resort.
 *
 * @param maxFileBytes largest PDF sent as a file
 * @param maxPages     most PDF pages per request
 * @param maxImageBytes largest single image
 * @param maxImageEdge longest image side in pixels worth sending
 */
public record ProviderLimits(long maxFileBytes, int maxPages, long maxImageBytes, int maxImageEdge) {

    /** Gemini: Files API up to 50 MB and 1,000 pages; inline images up to about 20 MB per request. */
    public static final ProviderLimits GEMINI = new ProviderLimits(48L * 1024 * 1024, 500, 15L * 1024 * 1024, 3072);

    /** Claude: 32 MB per request (base64 adds a third), 100 PDF pages, 5 MB per image. */
    public static final ProviderLimits CLAUDE = new ProviderLimits(22L * 1024 * 1024, 100, 3_500_000, 2000);
}
