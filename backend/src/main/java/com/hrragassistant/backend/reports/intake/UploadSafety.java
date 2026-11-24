package com.hrragassistant.backend.reports.intake;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.pdfbox.pdmodel.PDDocument;

import com.hrragassistant.backend.reports.prep.ImageTools;
import com.hrragassistant.backend.reports.prep.PdfTools;

/**
 * Checks that an upload is not built to exhaust the server: a PDF with
 * thousands of pages, an image of gigapixel size that would take gigabytes
 * to decode, or an Office file (a zip) that unpacks to far more than its
 * size ("zip bomb"). Runs before any parser or AI sees the file.
 *
 * Each method returns a message for the user, or null when the file is fine.
 */
public final class UploadSafety {

    /** Annual reports of large banks run to ~700 pages; anything far beyond is not a report. */
    public static final int MAX_PDF_PAGES = 1000;
    /** 50 megapixels: well above a phone photo (12-48 MP), far below a decompression bomb. */
    public static final long MAX_IMAGE_PIXELS = 50_000_000L;
    static final int MAX_ZIP_ENTRIES = 10_000;
    static final long MAX_ZIP_UNPACKED = 400L * 1024 * 1024;
    /** A real spreadsheet compresses up to ~20x; 100x or more on a big entry is a bomb. */
    static final long MAX_RATIO = 100;
    static final long RATIO_CHECK_FROM = 10L * 1024 * 1024;

    private UploadSafety() {
    }

    public static String imageProblem(byte[] data) {
        int[] d = ImageTools.dimensions(data);
        if (d == null) {
            return null; // unreadable images are rejected later with their own message
        }
        long pixels = (long) d[0] * d[1];
        if (d[0] <= 0 || d[1] <= 0 || pixels > MAX_IMAGE_PIXELS) {
            return "This image is too large (" + d[0] + " × " + d[1] + " pixels). Use a photo of up to 50 megapixels.";
        }
        return null;
    }

    public static String pdfProblem(Path file) {
        try (PDDocument doc = PdfTools.open(file.toFile())) {
            int pages = doc.getNumberOfPages();
            if (pages > MAX_PDF_PAGES) {
                return "This PDF has " + pages + " pages. Reports are limited to " + MAX_PDF_PAGES
                        + " pages; upload just the financial statements.";
            }
            return null;
        } catch (PdfTools.PasswordProtected e) {
            return null; // handled with its own message during the analysis
        } catch (IOException e) {
            return "This PDF could not be opened. It may be damaged.";
        }
    }

    /** XLSX and DOCX files are zip archives; check what they would unpack to without unpacking. */
    public static String zipProblem(Path file) {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            int entries = 0;
            long total = 0;
            Enumeration<? extends ZipEntry> all = zip.entries();
            while (all.hasMoreElements()) {
                ZipEntry e = all.nextElement();
                if (++entries > MAX_ZIP_ENTRIES) {
                    return tooBig();
                }
                long size = e.getSize();
                long packed = e.getCompressedSize();
                if (size < 0) {
                    continue;
                }
                total += size;
                if (total > MAX_ZIP_UNPACKED) {
                    return tooBig();
                }
                if (size > RATIO_CHECK_FROM && packed > 0 && size / packed > MAX_RATIO) {
                    return tooBig();
                }
            }
            return null;
        } catch (IOException e) {
            return "This Office file could not be opened. It may be damaged.";
        }
    }

    private static String tooBig() {
        return "This file unpacks to far more data than a financial report contains, so it was not opened. "
                + "Save it again from Excel or Word, or upload it as a PDF.";
    }
}
