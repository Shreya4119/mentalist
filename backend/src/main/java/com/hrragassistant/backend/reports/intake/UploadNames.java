package com.hrragassistant.backend.reports.intake;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Makes user-supplied filenames safe to store and show: no folders, no
 * control characters, at most {@value #MAX_LENGTH} characters (the database
 * column holds 255), and the extension kept.
 */
public final class UploadNames {

    public static final int MAX_LENGTH = 180;

    private UploadNames() {
    }

    public static String sanitize(String original) {
        String name = original == null ? "" : original;
        // Browsers on Windows sometimes send the full path; keep only the last part.
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = Normalizer.normalize(name, Normalizer.Form.NFC)
                .replaceAll("[\\p{Cntrl}\\p{Cf}]", "")
                .replaceAll("[<>:\"|?*]", "_")
                .replaceAll("\\s+", " ")
                .trim();
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.isBlank()) {
            name = "report";
        }
        if (name.length() > MAX_LENGTH) {
            String ext = extension(name);
            String suffix = ext.isEmpty() || ext.length() > 10 ? "" : "." + ext;
            name = name.substring(0, MAX_LENGTH - suffix.length() - 1).trim() + "…" + suffix;
        }
        return name;
    }

    /** Lower-case extension without the dot, or "" when there is none. */
    public static String extension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** "q3-report.pdf" -> "q3-report". */
    public static String stem(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot <= 0 ? (name == null ? "" : name) : name.substring(0, dot);
    }
}
