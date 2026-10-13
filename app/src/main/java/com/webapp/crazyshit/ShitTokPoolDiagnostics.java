package com.webapp.crazyshit;

import android.os.SystemClock;
import java.util.ArrayDeque;

/** Bounded opt-in event ring. No production per-frame logging. */
final class ShitTokPoolDiagnostics {
    private static volatile boolean enabled = BuildConfig.DEBUG || BuildConfig.SHITTOK_POOL_DIAGNOSTICS;
    static boolean isEnabled() { return enabled; }
    private static final ArrayDeque<String> events = new ArrayDeque<>();
    static void setEnabled(boolean value) { enabled = value; }
    static synchronized void record(String event, String detail) {
        if (!enabled) return;
        if (events.size() >= 160) events.removeFirst();
        events.addLast(SystemClock.elapsedRealtime() + " " + event + " " + detail);
    }
    static synchronized String report() { return String.join("\n", events); }
    private ShitTokPoolDiagnostics() { }
}
