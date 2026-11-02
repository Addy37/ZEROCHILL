package com.webapp.crazyshit;

/** Crash-only redaction. Never applied to ordinary user feedback or social content. */
final class CrashSanitizer {
    static String clean(String text) {
        if (text == null) return "";
        String value = text.substring(0, Math.min(text.length(), 32_000));
        value =
                value.replaceAll(
                        "(?im)\\b(?:authorization|proxy-authorization|cookie|set-cookie)[\"']?\\s*[:=][^\\r"
                            + "\\n"
                            + "]*",
                        "[redacted header]");
        value =
                value.replaceAll(
                        "(?im)(?:dm[_ ]?(?:body|text)|comment[_"
                            + " ]?draft|draft|message_body)\\s*[:=][^\\r"
                            + "\\n"
                            + "]*",
                        "[private field]");
        value = value.replaceAll("(?i)\\bBearer\\s+[^\\s,;]+", "Bearer [redacted]");
        value = value.replaceAll("(?i)\\b(?:https?|wss?|ftp|file|content)://[^\\s<>]+", "[URL]");
        value = value.replaceAll("(?i)[A-Z0-9._%+\\-]+@[A-Z0-9.\\-]+\\.[A-Z]{2,}", "[email]");
        value =
                value.replaceAll(
                        "(?i)(?:[\"']?(?:access_token|refresh_token|password|passwd|api[_-]?key|supabase[_-]?key|secret|dm[_"
                            + " ]?body|comment[_"
                            + " ]?draft|draft|message_body)[\"']?\\s*[:=]\\s*)(?:\"[^\"]*\"|'[^']*'|[^\\s,;}]+)",
                        "[redacted value]");
        value =
                value.replaceAll(
                        "\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b", "[token]");
        value =
                value.replaceAll(
                        "\\b(?:sb_(?:secret|publishable)_[A-Za-z0-9_-]+|AIza[A-Za-z0-9_-]+)\\b",
                        "[key]");
        value =
                value.replaceAll(
                        "\\b(?:sk-(?:proj-)?[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9]{20,}|(?:AKIA|ASIA)[A-Z0-9]{16})\\b",
                        "[key]");
        return value.replaceAll("(?<![A-Za-z0-9])[A-Za-z0-9_+/=-]{48,}(?![A-Za-z0-9])", "[token]");
    }

    static String message(Throwable error) {
        // Free-form exceptions can contain a message body/draft without any recognizable label.
        // Keep only known platform diagnostic forms; stack frames remain available for every crash.
        String name = error.getClass().getName();
        if (!name.startsWith("java.")
                && !name.startsWith("android.")
                && !name.startsWith("androidx.")) return "Message omitted for privacy";
        String message = error.getMessage();
        if (message == null) return "";
        if (message.equals("Controlled ZEROCHILL Beta crash")) return message;
        if (message.matches("Index [0-9]+ out of bounds for length [0-9]+")
                || message.matches("Index: [0-9]+, Size: [0-9]+")) return message;
        if (message.startsWith("Failed to allocate")) return "Failed to allocate memory";
        if (message.startsWith("Attempt to invoke"))
            return "Attempt to invoke a method on a null reference";
        if (message.startsWith("Cannot call this method while RecyclerView"))
            return "Cannot call this method while RecyclerView is computing layout or scrolling";
        return "Message omitted for privacy";
    }

    static String thread(String name) {
        return name != null
                        && name.matches(
                                "(?:main|pool-[0-9]+-thread-[0-9]+|(?:glide|ExoPlayer|crash-reports|FinalizerDaemon)[A-Za-z0-9"
                                    + " ._:-]{0,50})")
                ? name
                : "background thread (name omitted for privacy)";
    }

    static String bound(String text, int limit) {
        int end = Math.min(text.length(), limit);
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }
}
