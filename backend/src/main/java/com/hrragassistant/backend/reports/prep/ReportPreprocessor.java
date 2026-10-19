package com.hrragassistant.backend.reports.prep;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import com.hrragassistant.backend.reports.ai.ReportAnalyzer;

/**
 * Makes any uploaded report fit what the chosen AI provider accepts, and
 * says in plain words what was done (the notes appear under "How the
 * figures were read"):
 *
 * PDF, in order of preference:
 *   1. the original file, when it is within the provider's size and page limits;
 *   2. a copy without copy/print restrictions, if the PDF had them;
 *   3. a smaller PDF with the important pages (statements, key figures,
 *      auditor's report, notes) plus as much surrounding text as fits;
 *   4. just the important pages;
 *   5. the important pages as compressed page images (photo-heavy reports);
 *   6. the extracted text, as a last resort.
 * Images are scaled and compressed to the provider's image limit.
 * Spreadsheets, Word and text files pass through (they are read as text).
 */
@Component
public class ReportPreprocessor {

    public record Prepared(ReportAnalyzer.Source source, List<String> notes) {
    }

    /** Pages kept when only the essentials fit: enough for all statements, key figures and main notes. */
    static final int CORE_PAGES = 60;

    public Prepared prepare(Path file, String filename, String mediaType, ProviderLimits limits, String providerLabel) throws IOException {
        String type = mediaType == null ? ReportAnalyzer.Source.mimeTypeFor(filename) : mediaType;
        if ("application/pdf".equals(type)) {
            return preparePdf(file, filename, limits, providerLabel);
        }
        if (type.startsWith("image/")) {
            return prepareImage(file, filename, type, limits, providerLabel);
        }
        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        List<String> notes = ext.equals("txt")
                ? List.of()
                : List.of("Analysed from the file's text (spreadsheet or document); there were no page images to read.");
        return new Prepared(new ReportAnalyzer.Source(filename, Files.readAllBytes(file), "text/plain"), notes);
    }

    private Prepared preparePdf(Path file, String filename, ProviderLimits limits, String provider) throws IOException {
        long size = Files.size(file);
        List<String> notes = new ArrayList<>();
        PDDocument opened;
        try {
            opened = PdfTools.open(file.toFile());
        } catch (PdfTools.PasswordProtected e) {
            throw new IllegalStateException("This PDF is password protected. Open it, save a copy without a password, and upload that.");
        } catch (IOException e) {
            // PDFBox can't parse it; within the limits the provider may still manage, so let it try.
            if (size <= limits.maxFileBytes()) {
                return pdf(filename, Files.readAllBytes(file), notes);
            }
            throw new IllegalStateException("This PDF appears to be damaged and is too large to send as it is. "
                    + "Download the report again, or upload just the financial statement pages.");
        }
        try (PDDocument doc = opened) {
            int n = doc.getNumberOfPages();
            if (n == 0) {
                throw new IllegalStateException("This PDF has no pages. Download the report again and re-upload it.");
            }
            boolean restricted = doc.isEncrypted();
            boolean fits = size <= limits.maxFileBytes() && n <= limits.maxPages();

            // 1. As uploaded.
            if (fits && !restricted) {
                return pdf(filename, Files.readAllBytes(file), notes);
            }
            // 2. Same pages, restrictions removed.
            if (fits) {
                byte[] open = PdfTools.save(doc);
                if (open.length <= limits.maxFileBytes()) {
                    notes.add("The PDF had copy/print restrictions, so an unrestricted copy was analysed.");
                    return pdf(filename, open, notes);
                }
            }

            List<String> texts = PdfTools.pageTexts(doc);
            PageSelector.Selection sel = PageSelector.select(texts, limits.maxPages());
            String why = reason(size, n, limits, provider);

            // The key pages only (statements, key figures, auditor, notes): small and focused.
            PageSelector.Selection core = PageSelector.select(texts, Math.min(limits.maxPages(), CORE_PAGES));

            // 3 & 4. Fewer pages, unchanged (text stays selectable for the model).
            List<SortedSet<Integer>> candidates = new ArrayList<>();
            if (sel.hasText()) {
                SortedSet<Integer> filled = PageSelector.fill(sel.pages(), n, limits.maxPages());
                if (!filled.equals(sel.pages())) candidates.add(filled);
            }
            candidates.add(sel.pages());
            if (!core.pages().equals(sel.pages())) candidates.add(core.pages());
            for (SortedSet<Integer> pages : candidates) {
                if (pages.size() == n && size > limits.maxFileBytes()) {
                    continue; // all pages again: can only be as big as the original
                }
                byte[] subset = PdfTools.subset(doc, pages);
                if (subset.length <= limits.maxFileBytes()) {
                    notes.add(selectionNote(pages, n, pages == core.pages() ? core : sel, why));
                    return pdf(filename, subset, notes);
                }
            }

            // 5. The key pages as compressed images; fewer pages if even that is too big.
            sel = core;
            SortedSet<Integer> pages = core.pages();
            float[][] settings = {{110f, 0.6f}, {80f, 0.5f}};
            for (float[] s : settings) {
                byte[] raster = PdfTools.rasterize(doc, pages, s[0], s[1]);
                if (raster.length <= limits.maxFileBytes()) {
                    notes.add(selectionNote(pages, n, sel, why) + " The pages were converted to compressed images to fit; "
                            + "very small print may be harder to read.");
                    return pdf(filename, raster, notes);
                }
                // Estimate how many pages would fit at this setting and try once more with that many.
                int fitting = (int) Math.floor(pages.size() * (limits.maxFileBytes() * 0.9 / raster.length));
                if (fitting >= 3 && fitting < pages.size()) {
                    SortedSet<Integer> fewer = sel.hasText()
                            ? PageSelector.select(texts, fitting).pages()
                            : firstN(pages, fitting);
                    byte[] smaller = PdfTools.rasterize(doc, fewer, s[0], s[1]);
                    if (smaller.length <= limits.maxFileBytes()) {
                        notes.add(selectionNote(fewer, n, sel, why) + " The pages were converted to compressed images to fit.");
                        return pdf(filename, smaller, notes);
                    }
                }
            }

            // 6. Text only.
            String text = String.join("\n\n", texts).strip();
            if (!text.isEmpty()) {
                notes.add("Because " + why + ", the report was analysed from its extracted text only; charts and images were not read.");
                return new Prepared(new ReportAnalyzer.Source(filename + ".txt", text.getBytes(StandardCharsets.UTF_8), "text/plain"), notes);
            }
            throw new IllegalStateException("This scanned PDF (" + n + " pages, " + mb(size)
                    + ") is too large to analyse even after compression. Upload only the financial statement pages, "
                    + "or the company's ESEF/XBRL filing.");
        }
    }

    static String reason(long size, int pages, ProviderLimits limits, String provider) {
        boolean tooBig = size > limits.maxFileBytes();
        boolean tooLong = pages > limits.maxPages();
        if (tooBig && tooLong) {
            return "the file is " + mb(size) + " with " + pages + " pages, more than " + provider + " accepts in one request ("
                    + mb(limits.maxFileBytes()) + ", " + limits.maxPages() + " pages)";
        }
        if (tooBig) {
            return "the file is " + mb(size) + ", more than " + provider + " accepts in one request (" + mb(limits.maxFileBytes()) + ")";
        }
        return "it has " + pages + " pages, more than " + provider + " reads in one request (" + limits.maxPages() + " pages)";
    }

    private Prepared prepareImage(Path file, String filename, String type, ProviderLimits limits, String provider) throws IOException {
        byte[] data = Files.readAllBytes(file);
        int[] dim = ImageTools.dimensions(data);
        boolean smallEnough = data.length <= limits.maxImageBytes();
        boolean sharpEnough = dim == null || Math.max(dim[0], dim[1]) <= limits.maxImageEdge() * 2;
        if (smallEnough && sharpEnough) {
            return new Prepared(new ReportAnalyzer.Source(filename, data, type), List.of());
        }
        if ("image/webp".equals(type)) {
            if (smallEnough) {
                return new Prepared(new ReportAnalyzer.Source(filename, data, type), List.of());
            }
            throw new IllegalStateException("This WEBP image is " + mb(data.length) + ", more than " + provider + " accepts ("
                    + mb(limits.maxImageBytes()) + "). Save it as JPG and upload again.");
        }
        List<BufferedImage> pages = ImageTools.readPages(data);
        ImageTools.Shrunk s = ImageTools.shrinkToFit(pages.get(0), limits.maxImageEdge(), limits.maxImageBytes());
        String stem = filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename;
        List<String> notes = List.of("The image was scaled down to " + s.width() + "×" + s.height() + " pixels to fit " + provider
                + "'s image limit.");
        return new Prepared(new ReportAnalyzer.Source(stem + ".jpg", s.jpeg(), "image/jpeg"), notes);
    }

    private static Prepared pdf(String filename, byte[] bytes, List<String> notes) {
        return new Prepared(new ReportAnalyzer.Source(filename, bytes, "application/pdf"), notes);
    }

    static String selectionNote(SortedSet<Integer> pages, int total, PageSelector.Selection sel, String why) {
        if (pages.size() == total) {
            return "All " + total + " pages were analysed, but " + why + ".";
        }
        String what = sel.foundStatements()
                ? "the financial statements, key figures, auditor's report and notes"
                : sel.hasText() ? "the pages most likely to hold financial figures" : "the opening pages and a spread across the scan";
        return "Analysed " + pages.size() + " of " + total + " pages (" + what + ": pages " + PageSelector.ranges(pages)
                + ") because " + why + ".";
    }

    private static SortedSet<Integer> firstN(SortedSet<Integer> pages, int n) {
        SortedSet<Integer> out = new TreeSet<>();
        for (int p : pages) {
            if (out.size() >= n) break;
            out.add(p);
        }
        return out;
    }

    static String mb(long bytes) {
        double v = bytes / (1024.0 * 1024.0);
        return (v >= 10 ? String.format(Locale.ROOT, "%.0f", v) : String.format(Locale.ROOT, "%.1f", v)) + " MB";
    }
}
