package com.webapp.crazyshit;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.FrameMetrics;
import android.view.MotionEvent;
import android.view.Window;

import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Temporary in-memory diagnostics for ShitTok render stalls. */
final class ShitTokRenderDiagnostics {
    private static final int MAX_EVENTS = 320;
    private static final String BASELINE = "9723afe (PR #217 behavior + diagnostics)";

    private final Object lock = new Object();
    private final Activity activity;
    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private final HandlerThread frameThread = new HandlerThread("ShitTokRenderMetrics");
    private final long startedNs = SystemClock.elapsedRealtimeNanos();
    private final float refreshRateHz;
    private final long frameBudgetNs;
    private final long frameGapThresholdNs;

    private Window.OnFrameMetricsAvailableListener frameListener;
    private boolean frameMetricsAvailable;
    private long touchDownNs;
    private float touchDownX;
    private float touchDownY;
    private long lastMoveLoggedNs;

    ShitTokRenderDiagnostics(Activity activity) {
        this.activity = activity;
        float refresh = 60f;
        try {
            refresh = activity.getWindowManager().getDefaultDisplay().getRefreshRate();
        } catch (Exception ignored) {
        }
        if (refresh < 30f || refresh > 240f) refresh = 60f;
        refreshRateHz = refresh;
        frameBudgetNs = Math.max(1L, Math.round(1_000_000_000d / refreshRateHz));
        frameGapThresholdNs = Math.max(24_000_000L, frameBudgetNs * 2L);
        startFrameMetrics();
        event("DIAG_START", -1, "frameBudget=" + formatMs(frameBudgetNs) + "ms");
    }

    long nowNs() {
        return SystemClock.elapsedRealtimeNanos();
    }

    void event(String name, int position, String detail) {
        addEvent(new Event(nowNs(), name, position, detail == null ? "" : detail, 0L));
    }

    void duration(String name, long startedAtNs, int position, String detail) {
        long now = nowNs();
        addEvent(new Event(now, name, position, detail == null ? "" : detail,
                Math.max(0L, now - startedAtNs)));
    }

    void onPagerTouch(MotionEvent event, int scrollState, int position) {
        if (event == null) return;
        int action = event.getActionMasked();
        long now = nowNs();
        if (action == MotionEvent.ACTION_DOWN) {
            touchDownNs = now;
            lastMoveLoggedNs = 0L;
            touchDownX = event.getX();
            touchDownY = event.getY();
            addEvent(new Event(now, "TOUCH_DOWN", position,
                    "state=" + stateName(scrollState), 0L));
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (lastMoveLoggedNs == 0L || now - lastMoveLoggedNs >= 80_000_000L) {
                lastMoveLoggedNs = now;
                addEvent(new Event(now, "TOUCH_MOVE", position,
                        String.format(Locale.US,
                                "state=%s dt=%.1fms dx=%.0f dy=%.0f",
                                stateName(scrollState),
                                touchDownNs == 0L ? 0d : (now - touchDownNs) / 1_000_000d,
                                event.getX() - touchDownX,
                                event.getY() - touchDownY),
                        0L));
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            addEvent(new Event(now,
                    action == MotionEvent.ACTION_UP ? "TOUCH_UP" : "TOUCH_CANCEL",
                    position,
                    String.format(Locale.US,
                            "state=%s dt=%.1fms",
                            stateName(scrollState),
                            touchDownNs == 0L ? 0d : (now - touchDownNs) / 1_000_000d),
                    0L));
            touchDownNs = 0L;
            lastMoveLoggedNs = 0L;
        }
    }

    void onPagerState(int state, int position) {
        event("PAGER_" + stateName(state), position, "");
    }

    void reset() {
        synchronized (lock) {
            events.clear();
        }
        touchDownNs = 0L;
        lastMoveLoggedNs = 0L;
        event("DIAG_RESET", -1, "");
    }

    void close() {
        if (frameListener != null) {
            try {
                activity.getWindow().removeOnFrameMetricsAvailableListener(frameListener);
            } catch (Exception ignored) {
            }
        }
        frameListener = null;
        try {
            frameThread.quitSafely();
        } catch (Exception ignored) {
        }
    }

    String report() {
        List<Event> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(events);
        }
        snapshot.sort(Comparator.comparingLong(event -> event.timeNs));

        StringBuilder out = new StringBuilder();
        out.append("ZEROCHILL ShitTok render diagnostics\n");
        out.append("Baseline: ").append(BASELINE).append('\n');
        out.append("Device: ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append(" / Android ")
                .append(Build.VERSION.RELEASE).append(" (SDK ")
                .append(Build.VERSION.SDK_INT).append(")\n");
        out.append(String.format(Locale.US,
                "Display: %.1f Hz | frame budget %.2f ms | gap threshold %.2f ms | FrameMetrics %s\n",
                refreshRateHz,
                frameBudgetNs / 1_000_000d,
                frameGapThresholdNs / 1_000_000d,
                frameMetricsAvailable ? "ON" : "OFF"));

        if (snapshot.isEmpty()) {
            out.append("\nNo events captured.");
            return out.toString();
        }

        Map<String, Long> maxDurations = new HashMap<>();
        int frameGapCount = 0;
        long maxFrameNs = 0L;
        for (Event event : snapshot) {
            if (event.durationNs > 0L) {
                Long current = maxDurations.get(event.name);
                if (current == null || event.durationNs > current) {
                    maxDurations.put(event.name, event.durationNs);
                }
            }
            if ("FRAME_GAP".equals(event.name)) {
                frameGapCount++;
                maxFrameNs = Math.max(maxFrameNs, event.durationNs);
            }
        }

        out.append("\nSummary\n");
        out.append("Events: ").append(snapshot.size())
                .append(" | frame gaps: ").append(frameGapCount)
                .append(" | max frame: ").append(formatMs(maxFrameNs)).append(" ms\n");
        appendMax(out, maxDurations, "BATCH_FETCH");
        appendMax(out, maxDurations, "BATCH_UI_APPLY");
        appendMax(out, maxDurations, "RESOLVE_BG");
        appendMax(out, maxDurations, "RESOLVE_UI");
        appendMax(out, maxDurations, "PLAYER_PREPARE");
        appendMax(out, maxDurations, "PLAYER_DETACH");
        appendMax(out, maxDurations, "PLAYER_RELEASE");

        out.append("\nTimeline (oldest to newest)\n");
        for (Event event : snapshot) {
            out.append(String.format(Locale.US, "+%8.1fms %-18s",
                    (event.timeNs - startedNs) / 1_000_000d,
                    event.name));
            if (event.position >= 0) out.append(" p=").append(event.position);
            if (event.durationNs > 0L) {
                out.append(" dur=").append(formatMs(event.durationNs)).append("ms");
            }
            if (!event.detail.isEmpty()) out.append(" ").append(event.detail);
            out.append('\n');
        }
        return out.toString();
    }

    private void startFrameMetrics() {
        try {
            frameThread.start();
            Handler handler = new Handler(frameThread.getLooper());
            frameListener = (window, metrics, dropCountSinceLastInvocation) -> recordFrame(metrics);
            activity.getWindow().addOnFrameMetricsAvailableListener(frameListener, handler);
            frameMetricsAvailable = true;
        } catch (Exception ignored) {
            frameMetricsAvailable = false;
            try {
                frameThread.quitSafely();
            } catch (Exception ignoredAgain) {
            }
        }
    }

    private void recordFrame(FrameMetrics metrics) {
        if (metrics == null) return;
        long total = positive(metrics.getMetric(FrameMetrics.TOTAL_DURATION));
        if (total < frameGapThresholdNs) return;

        String detail = String.format(Locale.US,
                "input=%.1f anim=%.1f layout=%.1f draw=%.1f sync=%.1f cmd=%.1f swap=%.1f unknown=%.1f",
                ms(metrics, FrameMetrics.INPUT_HANDLING_DURATION),
                ms(metrics, FrameMetrics.ANIMATION_DURATION),
                ms(metrics, FrameMetrics.LAYOUT_MEASURE_DURATION),
                ms(metrics, FrameMetrics.DRAW_DURATION),
                ms(metrics, FrameMetrics.SYNC_DURATION),
                ms(metrics, FrameMetrics.COMMAND_ISSUE_DURATION),
                ms(metrics, FrameMetrics.SWAP_BUFFERS_DURATION),
                ms(metrics, FrameMetrics.UNKNOWN_DELAY_DURATION));
        addEvent(new Event(nowNs(), "FRAME_GAP", -1, detail, total));
    }

    private void addEvent(Event event) {
        synchronized (lock) {
            events.addLast(event);
            while (events.size() > MAX_EVENTS) events.removeFirst();
        }
    }

    private static void appendMax(StringBuilder out, Map<String, Long> values, String name) {
        Long value = values.get(name);
        if (value != null) {
            out.append(name).append(" max ").append(formatMs(value)).append(" ms\n");
        }
    }

    private static String stateName(int state) {
        if (state == ViewPager2.SCROLL_STATE_DRAGGING) return "DRAGGING";
        if (state == ViewPager2.SCROLL_STATE_SETTLING) return "SETTLING";
        return "IDLE";
    }

    private static double ms(FrameMetrics metrics, int key) {
        return positive(metrics.getMetric(key)) / 1_000_000d;
    }

    private static long positive(long value) {
        return Math.max(0L, value);
    }

    private static String formatMs(long nanos) {
        return String.format(Locale.US, "%.2f", nanos / 1_000_000d);
    }

    private static final class Event {
        final long timeNs;
        final String name;
        final int position;
        final String detail;
        final long durationNs;

        Event(long timeNs, String name, int position, String detail, long durationNs) {
            this.timeNs = timeNs;
            this.name = name;
            this.position = position;
            this.detail = detail;
            this.durationNs = durationNs;
        }
    }
}
