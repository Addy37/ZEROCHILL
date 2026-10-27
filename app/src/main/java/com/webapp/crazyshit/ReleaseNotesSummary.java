package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds the compact updater copy from the existing GitHub release body. */
final class ReleaseNotesSummary {
    private static final int MAX_SUMMARY_CHARS = 180;
    private static final int MAX_BULLETS = 2;
    private static final String DEFAULT_SUMMARY = "A new ZEROCHILL update is available.";

    private ReleaseNotesSummary() {}

    static String fromMarkdown(String markdown, String releaseTitle, String version) {
        String body = markdown == null ? "" : markdown.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = body.split("\n");
        StringBuilder paragraph = new StringBuilder();
        List<String> bullets = new ArrayList<>();
        boolean paragraphStarted = false;

        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) {
                if (paragraphStarted) break;
                continue;
            }
            if (line.startsWith("#") || line.startsWith("```") || line.matches("^-{3,}$")) {
                continue;
            }

            if (isBullet(line)) {
                if (paragraphStarted) break;
                String cleaned = cleanMarkdown(line.replaceFirst("^[-*+]\\s+", ""));
                if (!cleaned.isEmpty()) bullets.add(cleaned);
                if (bullets.size() >= MAX_BULLETS) break;
                continue;
            }

            if (!bullets.isEmpty()) break;
            String cleaned = cleanMarkdown(line);
            if (cleaned.isEmpty()) continue;
            if (paragraph.length() > 0) paragraph.append(' ');
            paragraph.append(cleaned);
            paragraphStarted = true;
        }

        String summary;
        if (paragraph.length() > 0) {
            summary = paragraph.toString();
        } else if (!bullets.isEmpty()) {
            summary = String.join(" • ", bullets);
        } else {
            summary = usefulFallback(releaseTitle, version);
        }
        return truncate(summary, MAX_SUMMARY_CHARS);
    }

    private static boolean isBullet(String line) {
        return line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ");
    }

    private static String cleanMarkdown(String value) {
        if (value == null) return "";
        String cleaned = value
                .replaceAll("!\\[([^]]*)]\\([^)]*\\)", "$1")
                .replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replaceAll("(?<!\\w)[*_](?!\\w)", "")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned;
    }

    private static String usefulFallback(String releaseTitle, String version) {
        String title = releaseTitle == null ? "" : releaseTitle.trim();
        if (title.isEmpty()) return DEFAULT_SUMMARY;

        String normalized = title.toLowerCase(Locale.US)
                .replace("zerochill", "")
                .replace("zero chill", "")
                .replace("version", "")
                .replace("release", "")
                .replace("update", "")
                .replace("v", "")
                .replaceAll("[^a-z0-9]+", "");
        String normalizedVersion = version == null
                ? ""
                : version.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "");
        if (normalized.isEmpty() || normalized.equals(normalizedVersion)) return DEFAULT_SUMMARY;
        return cleanMarkdown(title);
    }

    private static String truncate(String value, int maxChars) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) return DEFAULT_SUMMARY;
        if (text.length() <= maxChars) return text;

        int end = text.lastIndexOf(' ', maxChars - 1);
        if (end < maxChars / 2) end = maxChars - 1;
        return text.substring(0, Math.max(1, end)).trim() + "…";
    }
}
