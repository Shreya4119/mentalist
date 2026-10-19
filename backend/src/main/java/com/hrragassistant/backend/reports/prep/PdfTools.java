package com.hrragassistant.backend.reports.prep;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * The PDF operations the preprocessor needs, on Apache PDFBox 3 (already on
 * the classpath through Tika). Kept small and separate so the decisions
 * live in plain, testable Java (PageSelector, ReportPreprocessor).
 */
public final class PdfTools {

    private PdfTools() {
    }

    /** Thrown for a PDF that needs a password to open. */
    public static class PasswordProtected extends IOException {
        public PasswordProtected() {
            super("password protected");
        }
    }

    /** Opens a PDF; owner-password restrictions (no printing/copying) are removed so the copy can be read. */
    public static PDDocument open(File file) throws IOException {
        try {
            PDDocument doc = Loader.loadPDF(file);
            if (doc.isEncrypted()) {
                doc.setAllSecurityToBeRemoved(true);
            }
            return doc;
        } catch (InvalidPasswordException e) {
            throw new PasswordProtected();
        }
    }

    /** The text of every page; empty strings for pages that are images only. */
    public static List<String> pageTexts(PDDocument doc) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(false);
        List<String> texts = new ArrayList<>(doc.getNumberOfPages());
        for (int i = 1; i <= doc.getNumberOfPages(); i++) {
            stripper.setStartPage(i);
            stripper.setEndPage(i);
            try {
                texts.add(stripper.getText(doc));
            } catch (IOException | RuntimeException e) {
                texts.add(""); // one broken page must not stop the whole report
            }
        }
        return texts;
    }

    /** A new PDF holding only the given 0-based pages, unchanged. */
    public static byte[] subset(PDDocument src, SortedSet<Integer> pages) throws IOException {
        try (PDDocument out = new PDDocument()) {
            for (int i : pages) {
                out.importPage(src.getPage(i));
            }
            return save(out);
        }
    }

    /** The full document, saved without its security restrictions. */
    public static byte[] save(PDDocument doc) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        doc.save(bytes);
        return bytes.toByteArray();
    }

    /**
     * Renders the given pages as compressed JPEG images into a new PDF. The
     * text layer is lost, but the AI reads page images, and a 300 DPI
     * photo-heavy page shrinks from megabytes to around 150 KB.
     */
    public static byte[] rasterize(PDDocument src, SortedSet<Integer> pages, float dpi, float quality) throws IOException {
        PDFRenderer renderer = new PDFRenderer(src);
        try (PDDocument out = new PDDocument()) {
            for (int i : pages) {
                BufferedImage img = renderer.renderImageWithDPI(i, dpi, ImageType.RGB);
                addImagePage(out, img, quality);
            }
            return save(out);
        }
    }

    /** Builds a PDF with one page per image (page photos, multi-page TIFF scans). */
    public static byte[] imagesToPdf(List<BufferedImage> images, float quality) throws IOException {
        try (PDDocument out = new PDDocument()) {
            for (BufferedImage img : images) {
                addImagePage(out, ImageTools.scaleToFit(img, 2400), quality);
            }
            return save(out);
        }
    }

    private static void addImagePage(PDDocument doc, BufferedImage img, float quality) throws IOException {
        PDImageXObject x = JPEGFactory.createFromImage(doc, ImageTools.toRgb(img), quality);
        // Page size in points keeps the image's aspect ratio; ~A4 width.
        float width = 595f;
        float height = width * img.getHeight() / img.getWidth();
        PDPage page = new PDPage(new PDRectangle(width, height));
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.drawImage(x, 0, 0, width, height);
        }
    }
}
