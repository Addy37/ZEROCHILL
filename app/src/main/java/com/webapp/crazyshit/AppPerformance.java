package com.webapp.crazyshit;

import android.app.Activity;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.FrameMetrics;
import android.view.Window;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** In-memory timings for this app session. No URLs, saved history or network reporting. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class AppPerformance {
    private static final long STARTED = SystemClock.elapsedRealtime();
    private static final Map<String, long[]> TIMINGS = new LinkedHashMap<>();
    private static final WeakHashMap<Activity, Window.OnFrameMetricsAvailableListener> LISTENERS = new WeakHashMap<>();
    private static Handler frameHandler;
    private static long frames, slowFrames;
    private static boolean firstDraw;
    private static volatile String shitTokSelectedUrl = "";
    private static volatile boolean shitTokSelectedReady;
    private AppPerformance() { }
    static void begin() {
        record("Application entry from process start", SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime());
    }

    private static final java.util.Set<String> STARTUP_EVENTS = new java.util.HashSet<>();

    static synchronized void startup(String event) {
        if (!STARTUP_EVENTS.add(event)) return;
        long elapsed = SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime();
        record("Process -> " + event, elapsed);
        android.os.Trace.beginSection("zc.startup." + event);
        android.os.Trace.endSection();
        if (ShitTokPoolDiagnostics.isEnabled())
            android.util.Log.i("ZeroChillStartup", event + " processMs=" + elapsed);
    }

    private static final Map<String, Integer> STARTUP_CLIPS = new LinkedHashMap<>();
    private static final java.util.Set<String> CLIP_EVENTS = new java.util.HashSet<>();

    /** Bounded, correlated cold-start phases. URLs remain on-device and never enter the report. */
    static synchronized void startupItem(String event, String url, String detail) {
        trackShitTokSelection(event, url);
        if (STARTUP_EVENTS.contains("frame") || CLIP_EVENTS.size() >= 200 || url == null) return;
        int clip = STARTUP_CLIPS.computeIfAbsent(url, ignored -> STARTUP_CLIPS.size() + 1);
        if (!CLIP_EVENTS.add(event + ":" + clip)) return;
        long elapsed = SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime();
        record("Startup clip " + clip + " " + event + " " + detail, elapsed);
        if (ShitTokPoolDiagnostics.isEnabled())
            android.util.Log.i("ZeroChillStartup", event + " clip=" + clip + " processMs=" + elapsed + " " + detail);
    }

    private static void trackShitTokSelection(String event, String url) {
        if (event == null || url == null || url.isEmpty()) return;
        if ("owner_select".equals(event)) {
            boolean changed = !url.equals(shitTokSelectedUrl);
            shitTokSelectedUrl = url;
            if (changed) shitTokSelectedReady = false;
            if (changed) ShitTokCreatorMetadataLoader.onShitTokSelected(url);
            return;
        }
        if (!event.startsWith("player_state_") || !url.equals(shitTokSelectedUrl)) return;
        boolean ready = "player_state_3".equals(event);
        boolean becameReady = ready && !shitTokSelectedReady;
        shitTokSelectedReady = ready;
        if (becameReady) ShitTokCreatorMetadataLoader.onShitTokPlaybackReady(url);
    }

    static boolean isShitTokSelected(String url) {
        return url != null && !url.isEmpty() && url.equals(shitTokSelectedUrl);
    }

    static boolean isShitTokSelectedReady(String url) {
        return isShitTokSelected(url) && shitTokSelectedReady;
    }

    static void startupCost(String phase, long startedAt) {
        long elapsed = SystemClock.elapsedRealtime() - startedAt;
        record("Startup " + phase, elapsed);
        if (ShitTokPoolDiagnostics.isEnabled())
            android.util.Log.i("ZeroChillStartup", phase + " durationMs=" + elapsed);
    }

    static synchronized void record(String label, long milliseconds) {
        if (milliseconds < 0) return;
        long[] totals = TIMINGS.computeIfAbsent(label, key -> new long[3]);
        totals[0]++; totals[1] += milliseconds; totals[2] = milliseconds;
    }

    static void started(Activity activity) {
        if (!(activity instanceof NativeMainActivity || activity instanceof SearchActivity
                || activity instanceof NativeFeedBrowserActivity || activity instanceof DownloadedActivity)) return;
        if (frameHandler == null) {
            HandlerThread thread = new HandlerThread("app-frame-timings"); thread.start(); frameHandler = new Handler(thread.getLooper());
        }
        if (activity instanceof NativeMainActivity && !firstDraw) {
            firstDraw = true;
            android.view.View decor = activity.getWindow().getDecorView();
            decor.getViewTreeObserver().addOnDrawListener(new android.view.ViewTreeObserver.OnDrawListener() {
                boolean recorded;
                @Override public void onDraw() {
                    if (recorded) return;
                    recorded = true;
                    record("Main screen first draw", SystemClock.elapsedRealtime() - STARTED);
                    decor.post(() -> decor.getViewTreeObserver().removeOnDrawListener(this));
                }
            });
        }
        float rate = activity.getWindowManager().getDefaultDisplay().getRefreshRate();
        long budget = (long) (1_000_000_000d / Math.max(30, rate));
        Window.OnFrameMetricsAvailableListener listener = (window, metrics, dropped) -> {
            if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1) return;
            synchronized (AppPerformance.class) {
                frames++;
                if (metrics.getMetric(FrameMetrics.TOTAL_DURATION) > budget) slowFrames++;
            }
        };
        LISTENERS.put(activity, listener);
        activity.getWindow().addOnFrameMetricsAvailableListener(listener, frameHandler);
    }

    static void stopped(Activity activity) {
        Window.OnFrameMetricsAvailableListener listener = LISTENERS.remove(activity);
        if (listener != null) activity.getWindow().removeOnFrameMetricsAvailableListener(listener);
    }

    static synchronized String summary() {
        StringBuilder text = new StringBuilder("This app session\n\n");
        for (Map.Entry<String, long[]> entry : TIMINGS.entrySet()) {
            long[] totals = entry.getValue();
            text.append(entry.getKey()).append(": ").append(totals[2]).append(" ms last, ")
                    .append(totals[1] / Math.max(1, totals[0])).append(" ms average\n");
        }
        text.append("\nFrames over the display's timing budget: ").append(slowFrames).append(" / ").append(frames);
        text.append("\n\nTimings reset when the app process restarts. They stay on your device.");
        return text.toString();
    }
}
