package com.webapp.crazyshit;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Beta-only user-copyable timing diagnostics for native Search. */
final class SearchPerformanceDiagnostics {
    private SearchPerformanceDiagnostics() { }

    static boolean enabled() {
        String version = BuildConfig.VERSION_NAME == null ? "" : BuildConfig.VERSION_NAME.toLowerCase(Locale.US);
        return BuildConfig.DEBUG || version.contains("beta") || version.contains("dev");
    }

    static String report(
            String scope,
            int queryLength,
            long debounceMs,
            long firstResultMs,
            long completeMs,
            int resultCount,
            Map<String, Long> sourceTimings,
            Set<String> cacheHits
    ) {
        StringBuilder report = new StringBuilder();
        report.append("ZEROCHILL ").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(")")
                .append("\nSource: ").append(BuildConfig.SOURCE_REVISION)
                .append("\nAndroid ").append(android.os.Build.VERSION.RELEASE)
                .append(" / ").append(android.os.Build.MODEL)
                .append("\nCaptured: ").append(Instant.now())
                .append("\n\nSearch performance")
                .append("\nScope: ").append(value(scope))
                .append("\nQuery length: ").append(Math.max(0, queryLength))
                .append("\nDebounce: ").append(Math.max(0L, debounceMs)).append(" ms")
                .append("\nFirst result: ").append(duration(firstResultMs))
                .append("\nComplete: ").append(duration(completeMs))
                .append("\nVisible results: ").append(Math.max(0, resultCount));

        Map<String, Long> timings = sourceTimings == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(sourceTimings);
        Set<String> cached = cacheHits == null ? java.util.Collections.emptySet() : cacheHits;
        if (timings.isEmpty()) {
            report.append("\nSources: no timing samples");
        } else {
            report.append("\nSources:");
            for (Map.Entry<String, Long> entry : timings.entrySet()) {
                String name = value(entry.getKey());
                if (name.isEmpty()) continue;
                report.append("\n- ").append(name).append(": ")
                        .append(duration(entry.getValue() == null ? -1L : entry.getValue()));
                if (cached.contains(name)) report.append(" (cache)");
            }
        }
        report.append("\n\nQuery text, cookies, auth headers, signed URLs, and tokens are not included.");
        return report.toString();
    }

    static void show(Activity activity, String report) {
        if (!enabled() || activity == null || activity.isFinishing()
                || report == null || report.isEmpty()) return;
        new ZeroChillDialog.Builder(activity)
                .setTitle("Search performance diagnostics")
                .setMessage(report)
                .setPositiveButton("Copy", (dialog, which) -> copy(activity, report))
                .setNegativeButton("Close", null)
                .show();
    }

    private static void copy(Activity activity, String report) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            ZeroChillToast.makeText(activity, "Clipboard isn't available.", ZeroChillToast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("ZEROCHILL Search diagnostics", report));
        ZeroChillToast.makeText(activity, "Search diagnostics copied.", ZeroChillToast.LENGTH_SHORT).show();
    }

    private static String duration(long value) {
        return value < 0L ? "not recorded" : Math.max(0L, value) + " ms";
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
