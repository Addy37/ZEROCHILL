package com.webapp.crazyshit;

import java.util.Locale;
import java.util.regex.Pattern;

/** Deterministic validation rules shared by ZeroChill account UI and tests. */
final class ZeroChillAccountValidation {
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,20}$");

    private ZeroChillAccountValidation() {}

    static String username(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!USERNAME.matcher(value).matches()) {
            return "Username must be 3 to 20 characters using letters, numbers, or underscores.";
        }
        return "";
    }

    static String email(String raw) {
        String value = raw == null ? "" : raw.trim();
        int at = value.indexOf('@');
        int dot = value.lastIndexOf('.');
        if (value.length() > 254 || !value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || value.length() < 5 || at <= 0 || dot <= at + 1 || dot >= value.length() - 1) {
            return "Enter a valid email address.";
        }
        return "";
    }

    static String password(String raw) {
        if (raw == null || raw.length() < 8) {
            return "Password must be at least 8 characters.";
        }
        return "";
    }

    static String profile(String displayName, String bio) {
        if (displayName != null && displayName.codePointCount(0, displayName.length()) > 40)
            return "Display name must be 40 characters or fewer.";
        if (bio != null && bio.codePointCount(0, bio.length()) > 160)
            return "Bio must be 160 characters or fewer.";
        return "";
    }

    static String passwordChange(String password, String confirmation) {
        String error = password(password);
        if (!error.isEmpty()) return error;
        return password.equals(confirmation) ? "" : "Passwords do not match.";
    }

    static String normalizedUsername(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.US);
    }
}

