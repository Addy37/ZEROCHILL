package com.webapp.crazyshit;

/** Presentation only. Account identifiers and stored usernames remain unchanged. */
final class SocialUi {
    private SocialUi() { }

    static String name(String displayName, String username) {
        String display = cleanName(displayName);
        if (!display.isEmpty()) return display;
        String user = cleanName(username);
        return user.isEmpty() ? "ZeroChill user" : user;
    }

    static String cleanName(String value) {
        String result = value == null ? "" : value.trim();
        while (result.startsWith("@")) result = result.substring(1).trim();
        return "null".equals(result) ? "" : result;
    }

    static String relativeTime(String timestamp) {
        try { return relativeTime(java.time.Instant.parse(timestamp).toEpochMilli()); }
        catch (Exception ignored) { return ""; }
    }

    static String relativeTime(long timestamp) {
        if (timestamp <= 0L) return "";
        long minutes = Math.max(0L, System.currentTimeMillis() - timestamp) / 60_000L;
        if (minutes < 1) return "now";
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60L;
        if (hours < 24) return hours + "h";
        return hours / 24L + "d";
    }
}
