package com.webapp.crazyshit;

import android.os.SystemClock;
import java.util.ArrayDeque;

/** Bounded opt-in event ring. No production per-frame logging. */
final class ShitTokPoolDiagnostics {
    private static volatile boolean enabled = BuildConfig.DEBUG || BuildConfig.SHITTOK_POOL_DIAGNOSTICS;
    static boolean isEnabled() { return enabled; }
    private static final ArrayDeque<String> events = new ArrayDeque<>();
    private static long lastInventoryLog;
    private static String snapshot = "No ShitTok session captured yet.";
    static synchronized void snapshot(String value) { snapshot = redact(value); }
    static synchronized String userReport() {
        return "ZEROCHILL " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"
                + "\nSource: " + BuildConfig.SOURCE_REVISION
                + "\nAndroid " + android.os.Build.VERSION.RELEASE + " / " + android.os.Build.MODEL
                + "\nCaptured: " + java.time.Instant.now()
                + "\n\nShitTok last session\n" + snapshot + "\n\nRecent events\n" + redact(report())
                + "\n\n" + AppPerformance.summary();
    }
    static String redact(String value) {
        return value.replaceAll("https?://[^\\s\\]\\},]+", "[content]");
    }
    static void setEnabled(boolean value) { enabled = value; }
    static synchronized void record(String event, String detail) {
        if (!enabled) return;
        if (events.size() >= 160) events.removeFirst();
        events.addLast(SystemClock.elapsedRealtime() + " " + event + " " + detail);
        // Short stress runs can collect counters without automating a private report screen.
        // This tag contains aggregate counters/provider IDs only, never content URLs.
        long now = SystemClock.elapsedRealtime();
        if (event.equals("runway") && now - lastInventoryLog >= 1000L) {
            lastInventoryLog = now;
            android.util.Log.i("ShitTokInventory", detail);
        }
    }
    static synchronized String report() { return String.join("\n", events); }
    private ShitTokPoolDiagnostics() { }
}
