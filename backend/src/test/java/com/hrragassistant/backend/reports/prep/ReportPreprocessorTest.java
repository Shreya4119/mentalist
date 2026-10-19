package com.hrragassistant.backend.reports.prep;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportPreprocessorTest {

    @TempDir
    Path tmp;

    private final ReportPreprocessor prep = new ReportPreprocessor();

    /** A text PDF: filler pages, with the balance sheet and income statement on pages 51 and 52. */
    private Path report(int pages, StandardProtectionPolicy protection) throws Exception {
        Path file = tmp.resolve("report-" + pages + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage();
                doc.addPage(page);
                String line = i == 50 ? "Consolidated balance sheet 1,234 5,678 9,012 3,456 7,890 1,111 2,222 3,333 4,444 5,555 6,666 7,777 8,888 9,999 1,010 2,020"
                        : i == 51 ? "Consolidated income statement 1,234 5,678 9,012 3,456 7,890 1,111 2,222 3,333 4,444 5,555 6,666 7,777 8,888 9,999 1,010 2,020"
                        : "Our people and culture make the difference in everything we do, every single day of the year.";
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(font, 10);
                    cs.newLineAtOffset(40, 700);
                    for (int l = 0; l < 30; l++) {
                        cs.showText(line);
                        cs.newLineAtOffset(0, -14);
                    }
                    cs.endText();
                }
            }
            if (protection != null) {
                doc.protect(protection);
            }
            doc.save(file.toFile());
        }
        return file;
    }

    @Test
    void sendsSmallReportsUnchanged() throws Exception {
        Path f = report(10, null);
        ReportPreprocessor.Prepared p = prep.prepare(f, "small.pdf", "application/pdf", ProviderLimits.GEMINI, "Gemini");
        assertThat(p.notes()).isEmpty();
        assertThat(p.source().content()).isEqualTo(Files.readAllBytes(f));
    }

    @Test
    void cutsLongReportsToTheStatementPagesWithinThePageLimit() throws Exception {
        Path f = report(150, null);
        ProviderLimits tight = new ProviderLimits(50L * 1024 * 1024, 20, 3_500_000, 2000);
        ReportPreprocessor.Prepared p = prep.prepare(f, "long.pdf", "application/pdf", tight, "Claude");
        try (PDDocument out = Loader.loadPDF(p.source().content())) {
            assertThat(out.getNumberOfPages()).isLessThanOrEqualTo(20);
        }
        assertThat(p.notes()).singleElement().asString().contains("Analysed 20 of 150 pages").contains("50–54");
    }

    @Test
    void rasterisesPhotoHeavyPagesThatAreTooLargeEvenWhenSelected() throws Exception {
        // Six pages, each one big lossless photo of noise: about 4 MB a page, no text layer.
        Path f = tmp.resolve("photos.pdf");
        java.util.Random rnd = new java.util.Random(7);
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < 6; i++) {
                BufferedImage noise = new BufferedImage(1200, 1200, BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < 1200; y++) for (int x = 0; x < 1200; x++) noise.setRGB(x, y, rnd.nextInt(0xFFFFFF));
                PDPage page = new PDPage();
                doc.addPage(page);
                var image = org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, noise);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.drawImage(image, 0, 0, page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
                }
            }
            doc.save(f.toFile());
        }
        ProviderLimits limits = new ProviderLimits(Files.size(f) / 3, 100, 3_500_000, 2000);
        ReportPreprocessor.Prepared p = prep.prepare(f, "photos.pdf", "application/pdf", limits, "Claude");
        assertThat(p.source().content().length).isLessThanOrEqualTo((int) limits.maxFileBytes());
        assertThat(String.join(" ", p.notes())).contains("compressed images");
        try (PDDocument out = Loader.loadPDF(p.source().content())) {
            assertThat(out.getNumberOfPages()).isBetween(1, 6);
        }
    }

    @Test
    void explainsPasswordProtectedPdfs() throws Exception {
        Path f = report(3, new StandardProtectionPolicy("owner", "secret", new AccessPermission()));
        assertThatThrownBy(() -> prep.prepare(f, "locked.pdf", "application/pdf", ProviderLimits.GEMINI, "Gemini"))
                .hasMessageContaining("password protected");
    }

    @Test
    void removesCopyRestrictionsWhenNoPasswordIsNeeded() throws Exception {
        AccessPermission noCopy = new AccessPermission();
        noCopy.setCanExtractContent(false);
        Path f = report(3, new StandardProtectionPolicy("owner", "", noCopy));
        ReportPreprocessor.Prepared p = prep.prepare(f, "restricted.pdf", "application/pdf", ProviderLimits.GEMINI, "Gemini");
        assertThat(p.notes()).singleElement().asString().contains("copy/print restrictions");
        try (PDDocument out = Loader.loadPDF(p.source().content())) {
            assertThat(out.isEncrypted()).isFalse();
        }
    }

    @Test
    void shrinksLargeImagesToTheProviderLimit() throws Exception {
        BufferedImage img = new BufferedImage(6000, 4000, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        for (int y = 0; y < 4000; y += 7) {
            g.setColor(new Color((y * 31) % 255, (y * 17) % 255, (y * 7) % 255));
            g.drawLine(0, y, 6000, (y * 3) % 4000);
        }
        g.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(img, "png", png);
        Path f = tmp.resolve("photo.png");
        Files.write(f, png.toByteArray());
        ReportPreprocessor.Prepared p = prep.prepare(f, "photo.png", "image/png", ProviderLimits.CLAUDE, "Claude");
        assertThat(p.source().content().length).isLessThanOrEqualTo(3_500_000);
        assertThat(p.source().mimeType()).isEqualTo("image/jpeg");
        assertThat(p.notes()).singleElement().asString().contains("scaled down");
    }

    @Test
    void buildsOnePdfPagePerPhoto() throws Exception {
        List<BufferedImage> photos = List.of(new BufferedImage(800, 1100, BufferedImage.TYPE_INT_RGB),
                new BufferedImage(800, 1100, BufferedImage.TYPE_INT_RGB), new BufferedImage(1100, 800, BufferedImage.TYPE_INT_ARGB));
        try (PDDocument doc = Loader.loadPDF(PdfTools.imagesToPdf(photos, 0.8f))) {
            assertThat(doc.getNumberOfPages()).isEqualTo(3);
        }
    }
}
