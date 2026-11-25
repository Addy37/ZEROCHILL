package com.webapp.crazyshit;

import android.content.Context;

/**
 * Erome's site-wide orientation, stored only for Erome.
 * The site applies it with the {@code erome_version} cookie from {@code /version/{slug}}.
 */
final class EromePreference {
    enum Choice {
        ALL("all", "All"),
        STRAIGHT("straight", "Straight"),
        TRANS("trans", "Trans"),
        GAY("gay", "Gay"),
        HENTAI("hentai", "Hentai");

        final String slug;
        final String label;

        Choice(String slug, String label) {
            this.slug = slug;
            this.label = label;
        }

        static Choice fromSlug(String slug) {
            if (slug == null) return null;
            String value = slug.trim().toLowerCase(java.util.Locale.US);
            for (Choice choice : values()) {
                if (choice.slug.equals(value)) return choice;
            }
            return null;
        }
    }

    private static final String PREFS = "erome_preference";
    private static final String KEY = "version";
    private static final String APPLIED = "applied";

    private EromePreference() {}

    static Choice get(Context context) {
        if (context == null) return Choice.ALL;
        Choice stored = Choice.fromSlug(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, Choice.ALL.slug));
        return stored == null ? Choice.ALL : stored;
    }

    static void set(Context context, Choice choice) {
        if (context == null || choice == null) return;
        Choice previous = get(context);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, choice.slug)
                .putString(APPLIED, choice == previous ? applied(context) : "")
                .apply();
    }

    static String applied(Context context) {
        if (context == null) return "";
        String value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(APPLIED, "");
        return Choice.fromSlug(value) == null ? "" : value;
    }

    static void markApplied(Context context, Choice choice) {
        if (context == null || choice == null || get(context) != choice) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(APPLIED, choice.slug)
                .apply();
    }

    /** Only the five paths Erome publishes. Anything else is refused. */
    static String versionUrl(Choice choice) {
        if (choice == null) return "";
        return EromeRepository.ORIGIN + "/version/" + choice.slug;
    }

    static boolean hasVersionCookie(Iterable<String> setCookies) {
        if (setCookies == null) return false;
        for (String header : setCookies) {
            if (header == null) continue;
            String lower = header.toLowerCase(java.util.Locale.US);
            int at = lower.indexOf("erome_version=");
            if (at < 0) continue;
            int value = at + "erome_version=".length();
            if (value < header.length() && header.charAt(value) != ';') return true;
        }
        return false;
    }
}
