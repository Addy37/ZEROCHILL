package com.webapp.crazyshit;

import android.app.Activity;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.FrameMetrics;
import android.view.Window;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Candidate-only window metrics, scoped to visible ShitTok. No production frame logging. */
final class ShitTokFrameDiagnostics {
    private HandlerThread thread;
    private Window window;
    private final AtomicInteger frames = new AtomicInteger(), longFrames = new AtomicInteger(), dropped = new AtomicInteger();
    private final AtomicLong worstNs = new AtomicLong();
    private long budgetNs;
    private final Window.OnFrameMetricsAvailableListener listener = (w, metrics, lost) -> {
        frames.incrementAndGet(); dropped.addAndGet(lost);
        long duration = metrics.getMetric(FrameMetrics.TOTAL_DURATION);
        worstNs.accumulateAndGet(duration, Math::max);
        if (duration > budgetNs) longFrames.incrementAndGet();
    };
    void start(Activity activity) {
        if (thread != null || !ShitTokPoolDiagnostics.isEnabled()) return;
        float refresh = activity.getWindowManager().getDefaultDisplay().getRefreshRate();
        budgetNs = (long)(1_000_000_000d / Math.max(30f, refresh));
        thread = new HandlerThread("ShitTokFrames"); thread.start();
        window = activity.getWindow();
        window.addOnFrameMetricsAvailableListener(listener, new Handler(thread.getLooper()));
    }
    void stop() {
        if (window != null) window.removeOnFrameMetricsAvailableListener(listener);
        window = null;
        if (thread != null) thread.quitSafely();
        thread = null;
    }
    String summary() {
        return "frames=" + frames.get() + " overBudget=" + longFrames.get() + " worstMs=" + (worstNs.get()/1_000_000d)
                + " droppedMetricReports=" + dropped.get() + " budgetMs=" + (budgetNs/1_000_000d);
    }
}
