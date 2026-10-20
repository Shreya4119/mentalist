package com.hrragassistant.backend.hosting;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Plain rules for new accounts, with messages a visitor can act on. */
public final class SignupRules {

    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9][a-z0-9._-]{2,29}$");
    private static final Set<String> RESERVED = Set.of(
            "admin", "administrator", "root", "system", "support", "mentalist", "demo", "guest", "owner", "api", "null");
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password12", "password123", "password1234", "1234567890", "12345678910", "qwertyuiop", "qwerty1234",
            "iloveyou12", "welcome123", "abcdefghij", "0123456789", "1111111111", "letmein123", "monkey1234");

    public static final int MIN_PASSWORD = 10;

    private SignupRules() {
    }

    /** Usernames are case-insensitive: "Shreya" and "shreya" are the same account. */
    public static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    /** Returns what's wrong with this username/password, or null when they're fine. */
    public static String problem(String username, String password) {
        String name = normalize(username);
        if (!USERNAME.matcher(name).matches()) {
            return "Choose a username of 3–30 characters: letters, numbers, dots, dashes or underscores, starting with a letter or number.";
        }
        if (RESERVED.contains(name) || name.startsWith("admin")) {
            return "That username is reserved. Please choose another.";
        }
        if (password == null || password.length() < MIN_PASSWORD) {
            return "Use a password of at least " + MIN_PASSWORD + " characters.";
        }
        if (password.length() > 128) {
            return "That password is too long (128 characters at most).";
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (lower.contains(name) || COMMON_PASSWORDS.contains(lower) || lower.chars().distinct().count() < 4) {
            return "That password is too easy to guess. Try a short sentence or a mix of words and numbers.";
        }
        return null;
    }
}
