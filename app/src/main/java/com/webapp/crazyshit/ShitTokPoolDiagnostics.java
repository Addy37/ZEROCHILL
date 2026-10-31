package com.webapp.crazyshit;

import android.os.SystemClock;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded opt-in event ring. No production per-frame logging. */
final class ShitTokPoolDiagnostics {
    private static volatile boolean enabled = BuildConfig.DEBUG || BuildConfig.SHITTOK_POOL_DIAGNOSTICS;
    static boolean isEnabled() { return enabled; }
    private static final ArrayDeque<String> events = new ArrayDeque<>();
    private static long lastInventoryLog;
    private static String snapshot = "No ShitTok session captured yet.";
    private static final LinkedHashMap<String, LinkedHashMap<String, Integer>> admissionBySource = new LinkedHashMap<>();
    private static final LinkedHashMap<String, String> finalAdmissionOutcomes = new LinkedHashMap<String, String>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 4096;
        }
    };
    private static final java.util.concurrent.atomic.AtomicLong fetched = new java.util.concurrent.atomic.AtomicLong();
    private static long modeGeneration, switchedAt;
    private static String selection = "all:all";
    private static final Map<String, Long> modeCounters = new LinkedHashMap<>();
    private static final Map<String, Map<String, Integer>> modeOutcomes = new LinkedHashMap<>();
    private static final java.util.Set<String> modeFinal = new java.util.HashSet<>();
    static synchronized long beginMode(ShitTokSourceMode sources, ContentPreference gender) {
        selection = sources.key(gender); modeGeneration++; switchedAt = SystemClock.elapsedRealtime();
        modeCounters.clear(); modeOutcomes.clear(); modeFinal.clear();
        return modeGeneration;
    }
    static synchronized long modeToken() { return modeGeneration; }
    static synchronized void modeEvent(long token, String name, long count) {
        if (!enabled || token != modeGeneration || modeCounters.size() >= 24 && !modeCounters.containsKey(name)) return;
        modeCounters.put(name, modeCounters.getOrDefault(name, 0L) + count);
    }
    static synchronized String modeSummary() {
        return "currentMode=" + selection + " preferenceGeneration=" + modeGeneration
                + " switchAgeMs=" + Math.max(0, SystemClock.elapsedRealtime() - switchedAt)
                + " currentOutcomes=" + modeOutcomes + " currentWork=" + modeCounters;
    }
    static java.util.List<NativeContentItem> fetched(int provider, java.util.List<NativeContentItem> items) {
        fetched.addAndGet(items.size());
        record("listing", "source=" + provider + " raw=" + items.size());
        return items;
    }
    static long rawFetched() { return fetched.get(); }
    static synchronized void resetAdmissionOutcomes() {
        admissionBySource.clear();
        finalAdmissionOutcomes.clear();
    }
    static synchronized void admissionOutcome(NativeContentItem item, String outcome) {
        admissionOutcome(item, outcome, modeGeneration);
    }
    static synchronized void admissionOutcome(NativeContentItem item, String outcome, long token) {
        if (!enabled || item == null || item.url == null || item.url.isEmpty() || outcome == null || outcome.isEmpty()) return;
        String source = ShitTokAspectPriority.sourceKey(item);
        if (token == modeGeneration && modeFinal.add(item.url)) {
            if (modeFinal.size() > 4096) modeFinal.clear();
            Map<String, Integer> counts = modeOutcomes.computeIfAbsent(source, ignored -> new LinkedHashMap<>());
            counts.put(outcome, counts.getOrDefault(outcome, 0) + 1);
        }
        if (finalAdmissionOutcomes.containsKey(item.url)) return;
        finalAdmissionOutcomes.put(item.url, source + "|" + outcome);
        LinkedHashMap<String, Integer> counts = admissionBySource.computeIfAbsent(source, ignored -> new LinkedHashMap<>());
        counts.put(outcome, counts.getOrDefault(outcome, 0) + 1);
    }
    static synchronized String admissionSummary() {
        if (admissionBySource.isEmpty()) return "Admission outcomes: none yet";
        StringBuilder text = new StringBuilder("Admission outcomes by source: ");
        boolean firstSource = true;
        for (Map.Entry<String, LinkedHashMap<String, Integer>> source : admissionBySource.entrySet()) {
            if (!firstSource) text.append("; ");
            firstSource = false;
            text.append(source.getKey()).append("{");
            boolean firstOutcome = true;
            for (Map.Entry<String, Integer> outcome : source.getValue().entrySet()) {
                if (!firstOutcome) text.append(",");
                firstOutcome = false;
                text.append(outcome.getKey()).append("=").append(outcome.getValue());
            }
            text.append("}");
        }
        return text.toString();
    }
    static synchronized void snapshot(String value) { snapshot = redact(value); }
    static synchronized String userReport() {
        return "ZEROCHILL " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"
                + "\nSource: " + BuildConfig.SOURCE_REVISION
                + "\nAndroid " + android.os.Build.VERSION.RELEASE + " / " + android.os.Build.MODEL
                + "\nCaptured: " + java.time.Instant.now()
                + "\n\nShitTok last session\n" + snapshot + "\n" + modeSummary()
                + "\n" + admissionSummary() + "\n\nRecent events\n" + redact(report())
                + "\n\n" + AppPerformance.summary();
    }
    static String redact(String value) {
        return value.replaceAll("https?://\\S+", "[content]");
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
