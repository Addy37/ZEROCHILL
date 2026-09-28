package com.webapp.crazyshit;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.FrameMetrics;
import android.view.Window;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Temporary device-side timing probe for ShitTok swipe stalls.
 *
 * Keeps measurements in memory only. Frame metrics are delivered on a background handler so the
 * diagnostic path does not add per-frame work to the UI thread.
 */
final class ShitTokSwipeDiagnostics {
    private static final int MAX_SAMPLES = 64;
    private static final long FRAME_WINDOW_NS = 900_000_000L;
    private static final String BASELINE = "018cc3a (PR #207)";

    private final Object lock = new Object();
    private final Activity activity;
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private final HandlerThread frameThread = new HandlerThread("ShitTokFrameMetrics");
    private final long frameBudgetNs;
    private final float refreshRateHz;

    private Window.OnFrameMetricsAvailableListener frameListener;
    private volatile Sample activeCriticalSample;
    private volatile Sample activeMaintenanceSample;
    private long nextId;
    private boolean frameMetricsAvailable;

    ShitTokSwipeDiagnostics(Activity activity) {
        this.activity = activity;
        float refresh = 60f;
        try {
            refresh = activity.getWindowManager().getDefaultDisplay().getRefreshRate();
        } catch (Exception ignored) {
        }
        if (refresh < 30f || refresh > 240f) refresh = 60f;
        refreshRateHz = refresh;
        frameBudgetNs = Math.max(1L, Math.round(1_000_000_000d / refreshRateHz));
        startFrameMetrics();
    }

    Sample begin(int fromPosition, int toPosition, boolean userDriven) {
        if (fromPosition == toPosition) return null;
        Sample sample = new Sample(
                ++nextId,
                fromPosition,
                toPosition,
                userDriven,
                SystemClock.elapsedRealtimeNanos()
        );
        synchronized (lock) {
            samples.addLast(sample);
            while (samples.size() > MAX_SAMPLES) samples.removeFirst();
        }
        activeCriticalSample = sample;
        return sample;
    }

    void finishCallback(Sample sample, long callbackNs) {
        if (sample == null) return;
        synchronized (lock) {
            sample.callbackNs = Math.max(0L, callbackNs);
        }
        if (activeCriticalSample == sample) activeCriticalSample = null;
    }

    void recordReset(Sample sample, long durationNs) {
        if (sample != null) sample.resetNs += Math.max(0L, durationNs);
    }

    void recordPause(Sample sample, long durationNs) {
        if (sample != null) sample.pauseNs += Math.max(0L, durationNs);
    }

    void recordReleaseDistant(Sample sample, long durationNs) {
        if (sample != null) sample.releaseDistantNs += Math.max(0L, durationNs);
    }

    void recordResolveAhead(Sample sample, long durationNs) {
        if (sample != null) sample.resolveAheadNs += Math.max(0L, durationNs);
    }

    void recordPlaySelected(Sample sample, long durationNs) {
        if (sample != null) sample.playSelectedNs += Math.max(0L, durationNs);
    }

    void recordWarmCreators(Sample sample, long durationNs) {
        if (sample != null) sample.warmCreatorsNs += Math.max(0L, durationNs);
    }

    void recordHistory(long durationNs) {
        Sample sample = activeCriticalSample;
        if (sample == null) return;
        synchronized (lock) {
            sample.historyNs += Math.max(0L, durationNs);
            sample.historyCalls++;
        }
    }

    void recordPlayerRelease(long durationNs) {
        Sample sample = activeCriticalSample != null ? activeCriticalSample : activeMaintenanceSample;
        if (sample == null) return;
        synchronized (lock) {
            sample.playerReleaseNs += Math.max(0L, durationNs);
            sample.playerReleaseCalls++;
        }
    }

    void recordPlayerPrepare(long durationNs) {
        Sample sample = activeCriticalSample != null ? activeCriticalSample : activeMaintenanceSample;
        if (sample == null) return;
        synchronized (lock) {
            sample.playerPrepareNs += Math.max(0L, durationNs);
            sample.playerPrepareCalls++;
        }
    }

    void beginMaintenance(Sample sample) {
        activeMaintenanceSample = sample;
    }

    void finishPrepareMaintenance(Sample sample, long durationNs) {
        if (sample != null) {
            synchronized (lock) {
                sample.deferredPrepareNs += Math.max(0L, durationNs);
            }
        }
        if (activeMaintenanceSample == sample) activeMaintenanceSample = null;
    }

    void finishReleaseMaintenance(Sample sample, long durationNs) {
        if (sample != null) {
            synchronized (lock) {
                sample.deferredReleaseNs += Math.max(0L, durationNs);
            }
        }
        if (activeMaintenanceSample == sample) activeMaintenanceSample = null;
    }

    void reset() {
        synchronized (lock) {
            samples.clear();
        }
        activeCriticalSample = null;
        activeMaintenanceSample = null;
    }

    void close() {
        activeCriticalSample = null;
        activeMaintenanceSample = null;
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
        List<Sample> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(samples);
        }

        StringBuilder out = new StringBuilder();
        out.append("ZEROCHILL ShitTok swipe diagnostics\n");
        out.append("Baseline: ").append(BASELINE).append('\n');
        out.append("Device: ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append(" / Android ")
                .append(Build.VERSION.RELEASE).append(" (SDK ")
                .append(Build.VERSION.SDK_INT).append(")\n");
        out.append(String.format(Locale.US,
                "Display: %.1f Hz | frame budget %.2f ms | FrameMetrics %s\n",
                refreshRateHz, ms(frameBudgetNs), frameMetricsAvailable ? "ON" : "OFF"));

        if (snapshot.isEmpty()) {
            out.append("\nNo swipe samples yet. Swipe through ShitTok, then open this report again.");
            return out.toString();
        }

        int manual = 0;
        long totalFrames = 0L;
        long overBudget = 0L;
        long over2x = 0L;
        ArrayList<Long> callback = new ArrayList<>();
        ArrayList<Long> pause = new ArrayList<>();
        ArrayList<Long> history = new ArrayList<>();
        ArrayList<Long> releaseDistant = new ArrayList<>();
        ArrayList<Long> playerRelease = new ArrayList<>();
        ArrayList<Long> resolve = new ArrayList<>();
        ArrayList<Long> play = new ArrayList<>();
        ArrayList<Long> playerPrepare = new ArrayList<>();
        ArrayList<Long> warm = new ArrayList<>();
        ArrayList<Long> deferredPrepare = new ArrayList<>();
        ArrayList<Long> deferredRelease = new ArrayList<>();
        ArrayList<Long> worstFrames = new ArrayList<>();

        Sample worst = snapshot.get(0);
        long worstScore = -1L;
        for (Sample sample : snapshot) {
            if (sample.userDriven) manual++;
            callback.add(sample.callbackNs);
            pause.add(sample.pauseNs);
            history.add(sample.historyNs);
            releaseDistant.add(sample.releaseDistantNs);
            playerRelease.add(sample.playerReleaseNs);
            resolve.add(sample.resolveAheadNs);
            play.add(sample.playSelectedNs);
            playerPrepare.add(sample.playerPrepareNs);
            warm.add(sample.warmCreatorsNs);
            deferredPrepare.add(sample.deferredPrepareNs);
            deferredRelease.add(sample.deferredReleaseNs);
            worstFrames.add(sample.maxFrameNs);
            totalFrames += sample.frameCount;
            overBudget += sample.overBudgetFrames;
            over2x += sample.over2xBudgetFrames;
            long score = Math.max(sample.callbackNs, sample.maxFrameNs);
            if (score > worstScore) {
                worstScore = score;
                worst = sample;
            }
        }

        out.append("\nSamples: ").append(snapshot.size())
                .append(" (manual ").append(manual)
                .append(", auto ").append(snapshot.size() - manual).append(")\n\n");
        appendStats(out, "onPageSelected callback", callback);
        appendStats(out, "pause non-selected", pause);
        appendStats(out, "history JSON/persist", history);
        appendStats(out, "release distant players", releaseDistant);
        appendStats(out, "player detach/release", playerRelease);
        appendStats(out, "resolve ahead", resolve);
        appendStats(out, "play selected", play);
        appendStats(out, "player build/prepare", playerPrepare);
        appendStats(out, "creator warm", warm);
        appendStats(out, "deferred prepare job", deferredPrepare);
        appendStats(out, "deferred release job", deferredRelease);
        appendStats(out, "worst frame per swipe", worstFrames);

        out.append(String.format(Locale.US,
                "\nFrames in 900 ms swipe windows: %d | over budget %d (%.1f%%) | over 2x budget %d (%.1f%%)\n",
                totalFrames,
                overBudget,
                percent(overBudget, totalFrames),
                over2x,
                percent(over2x, totalFrames)));

        out.append("\nWorst sample #").append(worst.id)
                .append(" ").append(worst.fromPosition).append("->").append(worst.toPosition)
                .append(worst.userDriven ? " manual" : " auto").append('\n');
        out.append(String.format(Locale.US,
                "callback %.2f ms | max frame %.2f ms | frames %d | over budget %d\n",
                ms(worst.callbackNs), ms(worst.maxFrameNs), worst.frameCount, worst.overBudgetFrames));
        out.append(String.format(Locale.US,
                "steps: reset %.2f | pause %.2f | history %.2f (%d) | release-window %.2f | player-release %.2f (%d) | resolve %.2f | play %.2f | player-prepare %.2f (%d) | warm %.2f | deferred-prep %.2f | deferred-release %.2f ms\n",
                ms(worst.resetNs),
                ms(worst.pauseNs),
                ms(worst.historyNs), worst.historyCalls,
                ms(worst.releaseDistantNs),
                ms(worst.playerReleaseNs), worst.playerReleaseCalls,
                ms(worst.resolveAheadNs),
                ms(worst.playSelectedNs),
                ms(worst.playerPrepareNs), worst.playerPrepareCalls,
                ms(worst.warmCreatorsNs),
                ms(worst.deferredPrepareNs),
                ms(worst.deferredReleaseNs)));
        if (worst.maxFrameNs > 0L) {
            out.append(String.format(Locale.US,
                    "worst-frame breakdown: input %.2f | animation %.2f | layout %.2f | draw %.2f | sync %.2f | command %.2f | swap %.2f | unknown %.2f ms\n",
                    ms(worst.worstInputNs),
                    ms(worst.worstAnimationNs),
                    ms(worst.worstLayoutNs),
                    ms(worst.worstDrawNs),
                    ms(worst.worstSyncNs),
                    ms(worst.worstCommandNs),
                    ms(worst.worstSwapNs),
                    ms(worst.worstUnknownNs)));
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
        long now = SystemClock.elapsedRealtimeNanos();
        Sample sample;
        synchronized (lock) {
            sample = samples.peekLast();
            if (sample == null || now > sample.frameWindowEndsNs) return;

            long total = positive(metrics.getMetric(FrameMetrics.TOTAL_DURATION));
            sample.frameCount++;
            if (total > frameBudgetNs) sample.overBudgetFrames++;
            if (total > frameBudgetNs * 2L) sample.over2xBudgetFrames++;
            if (total <= sample.maxFrameNs) return;

            sample.maxFrameNs = total;
            sample.worstInputNs = positive(metrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION));
            sample.worstAnimationNs = positive(metrics.getMetric(FrameMetrics.ANIMATION_DURATION));
            sample.worstLayoutNs = positive(metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION));
            sample.worstDrawNs = positive(metrics.getMetric(FrameMetrics.DRAW_DURATION));
            sample.worstSyncNs = positive(metrics.getMetric(FrameMetrics.SYNC_DURATION));
            sample.worstCommandNs = positive(metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION));
            sample.worstSwapNs = positive(metrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION));
            sample.worstUnknownNs = positive(metrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION));
        }
    }

    private static void appendStats(StringBuilder out, String label, List<Long> values) {
        out.append(String.format(Locale.US,
                "%-24s avg %7.2f | p50 %7.2f | p95 %7.2f | max %7.2f ms\n",
                label,
                ms(average(values)),
                ms(percentile(values, 0.50)),
                ms(percentile(values, 0.95)),
                ms(max(values))));
    }

    private static long average(List<Long> values) {
        if (values.isEmpty()) return 0L;
        long total = 0L;
        for (Long value : values) total += Math.max(0L, value == null ? 0L : value);
        return total / values.size();
    }

    static long percentile(List<Long> values, double fraction) {
        if (values == null || values.isEmpty()) return 0L;
        ArrayList<Long> copy = new ArrayList<>(values);
        Collections.sort(copy);
        int index = (int) Math.ceil(Math.max(0d, Math.min(1d, fraction)) * copy.size()) - 1;
        index = Math.max(0, Math.min(copy.size() - 1, index));
        return copy.get(index);
    }

    private static long max(List<Long> values) {
        long max = 0L;
        for (Long value : values) if (value != null && value > max) max = value;
        return max;
    }

    private static long positive(long value) {
        return Math.max(0L, value);
    }

    private static double ms(long nanos) {
        return nanos / 1_000_000d;
    }

    private static double percent(long value, long total) {
        return total <= 0L ? 0d : value * 100d / total;
    }

    static final class Sample {
        final long id;
        final int fromPosition;
        final int toPosition;
        final boolean userDriven;
        final long frameWindowEndsNs;

        long callbackNs;
        long resetNs;
        long pauseNs;
        long historyNs;
        int historyCalls;
        long releaseDistantNs;
        long playerReleaseNs;
        int playerReleaseCalls;
        long resolveAheadNs;
        long playSelectedNs;
        long playerPrepareNs;
        int playerPrepareCalls;
        long warmCreatorsNs;
        long deferredPrepareNs;
        long deferredReleaseNs;

        long frameCount;
        long overBudgetFrames;
        long over2xBudgetFrames;
        long maxFrameNs;
        long worstInputNs;
        long worstAnimationNs;
        long worstLayoutNs;
        long worstDrawNs;
        long worstSyncNs;
        long worstCommandNs;
        long worstSwapNs;
        long worstUnknownNs;

        Sample(long id, int fromPosition, int toPosition, boolean userDriven, long startedNs) {
            this.id = id;
            this.fromPosition = fromPosition;
            this.toPosition = toPosition;
            this.userDriven = userDriven;
            this.frameWindowEndsNs = startedNs + FRAME_WINDOW_NS;
        }
    }
}
