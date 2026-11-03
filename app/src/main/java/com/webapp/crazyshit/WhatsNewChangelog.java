package com.webapp.crazyshit;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Bundled, release-specific highlights. Add one entry before each stable release. */
final class WhatsNewChangelog {
    static final int FIRST_SUPPORTED_VERSION_CODE = 6_000_003;

    static final class Entry {
        final int versionCode;
        final String versionName;
        final List<String> highlights;

        Entry(int versionCode, String versionName, String... highlights) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.highlights = Collections.unmodifiableList(Arrays.asList(highlights));
        }
    }

    private WhatsNewChangelog() {}

    static Entry forVersion(int versionCode) {
        if (versionCode == 6_000_003) {
            return new Entry(6_000_003, "6.0.3",
                    "See what changed after each ZEROCHILL update.",
                    "Release notes appear once, then stay out of your way.");
        }
        // Do not recycle stale release notes for unlisted future versions.
        return null;
    }

    /**
     * A missing preference is normal for users upgrading from 6.0.2 or earlier.
     * Android's package timestamps distinguish them from brand-new installs.
     */
    static boolean shouldShow(int currentVersionCode, Integer acknowledgedVersionCode,
                              long firstInstallTime, long lastUpdateTime, boolean hasNotes) {
        if (!hasNotes || currentVersionCode < FIRST_SUPPORTED_VERSION_CODE) return false;
        if (acknowledgedVersionCode != null) {
            return currentVersionCode > acknowledgedVersionCode;
        }
        return firstInstallTime > 0 && lastUpdateTime > firstInstallTime + 1_000L;
    }
}
