package com.hrragassistant.backend.reports.intake;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Identifies what an upload really is from its first bytes ("magic
 * numbers"), so a renamed, damaged or mislabelled file is rejected with a
 * clear message instead of failing deep inside the analysis.
 */
public final class FileSniffer {

    public enum Kind {
        PDF("PDF", "application/pdf", "pdf"),
        PNG("PNG image", "image/png", "png"),
        JPEG("JPG image", "image/jpeg", "jpg"),
        WEBP("WEBP image", "image/webp", "webp"),
        TIFF("TIFF image", "image/tiff", "tiff"),
        GIF("GIF image", "image/gif", "gif"),
        BMP("BMP image", "image/bmp", "bmp"),
        HEIC("HEIC photo", "image/heic", "heic"),
        ZIP_OFFICE("Office file (XLSX/DOCX)", "application/zip", "zip"),
        OLE_OFFICE("old Office file (XLS/DOC)", "application/x-ole-storage", "xls"),
        TEXT("text file", "text/plain", "txt"),
        BINARY("unknown binary file", "application/octet-stream", "bin");

        private final String label;
        private final String mediaType;
        private final String extension;

        Kind(String label, String mediaType, String extension) {
            this.label = label;
            this.mediaType = mediaType;
            this.extension = extension;
        }

        public String label() {
            return label;
        }

        public String mediaType() {
            return mediaType;
        }

        public String extension() {
            return extension;
        }

        public boolean isImage() {
            return this == PNG || this == JPEG || this == WEBP || this == TIFF || this == GIF || this == BMP;
        }

        /** Images Java can decode and re-encode (WEBP and HEIC can't be, without extra libraries). */
        public boolean isConvertibleImage() {
            return this == PNG || this == JPEG || this == TIFF || this == GIF || this == BMP;
        }

        /** Image types the AI providers accept directly; the others are converted to PDF on upload. */
        public boolean isProviderImage() {
            return this == PNG || this == JPEG || this == WEBP;
        }
    }

    /** Extensions accepted on upload. */
    public static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "png", "jpg", "jpeg", "webp", "tif", "tiff", "gif", "bmp",
            "xlsx", "xls", "csv", "docx", "txt");

    private FileSniffer() {
    }

    /** What the first bytes say the file is. Needs at least the first 16 bytes; 4 KB is better for text detection. */
    public static Kind detect(byte[] head) {
        if (head == null || head.length == 0) {
            return Kind.BINARY;
        }
        if (startsWith(head, "%PDF-") || containsWithin(head, "%PDF-", 1024)) {
            return Kind.PDF;
        }
        if (startsWith(head, 0x89, 'P', 'N', 'G')) {
            return Kind.PNG;
        }
        if (startsWith(head, 0xFF, 0xD8, 0xFF)) {
            return Kind.JPEG;
        }
        if (startsWith(head, "RIFF") && head.length >= 12 && ascii(head, 8, 4).equals("WEBP")) {
            return Kind.WEBP;
        }
        if (startsWith(head, 'I', 'I', 0x2A, 0x00) || startsWith(head, 'M', 'M', 0x00, 0x2A)) {
            return Kind.TIFF;
        }
        if (startsWith(head, "GIF87a") || startsWith(head, "GIF89a")) {
            return Kind.GIF;
        }
        if (startsWith(head, "BM") && head.length >= 14) {
            return Kind.BMP;
        }
        if (head.length >= 12 && ascii(head, 4, 4).equals("ftyp")) {
            String brand = ascii(head, 8, 4).toLowerCase(Locale.ROOT);
            if (brand.startsWith("hei") || brand.startsWith("hev") || brand.equals("mif1") || brand.equals("msf1") || brand.startsWith("avif")) {
                return Kind.HEIC;
            }
        }
        if (startsWith(head, 'P', 'K', 0x03, 0x04)) {
            return Kind.ZIP_OFFICE;
        }
        if (startsWith(head, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) {
            return Kind.OLE_OFFICE;
        }
        return looksLikeText(head) ? Kind.TEXT : Kind.BINARY;
    }

    /**
     * Checks the detected kind against the extension and returns a message
     * for the user when they don't fit, or null when the file is acceptable.
     */
    public static String mismatch(String extension, Kind kind) {
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        if (kind == Kind.HEIC) {
            return "This looks like an iPhone HEIC photo, which can't be read here. Export or save it as JPG (on iPhone: Settings → Camera → Formats → Most Compatible) and upload again.";
        }
        boolean ok = switch (ext) {
            case "pdf" -> kind == Kind.PDF;
            // A photo saved with the wrong image extension is still a readable image.
            case "png", "jpg", "jpeg", "webp", "tif", "tiff", "gif", "bmp" -> kind.isImage();
            case "xlsx", "docx" -> kind == Kind.ZIP_OFFICE;
            case "xls" -> kind == Kind.OLE_OFFICE;
            case "csv", "txt" -> kind == Kind.TEXT;
            default -> false;
        };
        if (ok) {
            return null;
        }
        return "This file is named ." + ext + " but its contents look like " + article(kind.label()) + " " + kind.label()
                + ". It may be damaged or renamed; try downloading the report again.";
    }

    private static String article(String label) {
        return "aeiouAEIOU".indexOf(label.charAt(0)) >= 0 ? "an" : "a";
    }

    private static boolean looksLikeText(byte[] head) {
        int n = Math.min(head.length, 4096);
        int control = 0;
        for (int i = 0; i < n; i++) {
            int b = head[i] & 0xFF;
            if (b == 0) {
                return false;
            }
            if (b < 0x09 || (b > 0x0D && b < 0x20)) {
                control++;
            }
        }
        return control <= n / 100;
    }

    private static boolean startsWith(byte[] data, String prefix) {
        return startsWith(data, prefix.chars().toArray());
    }

    private static boolean startsWith(byte[] data, int... prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** Some PDFs carry a few junk bytes before the %PDF- header; readers accept them within the first 1 KB. */
    private static boolean containsWithin(byte[] data, String needle, int limit) {
        byte[] n = needle.getBytes(StandardCharsets.US_ASCII);
        int max = Math.min(data.length, limit) - n.length;
        outer:
        for (int i = 0; i <= max; i++) {
            for (int j = 0; j < n.length; j++) {
                if (data[i + j] != n[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static String ascii(byte[] data, int offset, int len) {
        if (data.length < offset + len) {
            return "";
        }
        return new String(data, offset, len, StandardCharsets.ISO_8859_1);
    }
}
