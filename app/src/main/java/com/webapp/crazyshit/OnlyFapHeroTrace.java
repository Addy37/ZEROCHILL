package com.webapp.crazyshit;

import android.os.SystemClock;
import java.util.concurrent.atomic.AtomicInteger;

/** Correlates the source/qualification/display pipeline without logging media URLs. */
final class OnlyFapHeroTrace {
    private static final AtomicInteger NEXT = new AtomicInteger();
    final int id = NEXT.incrementAndGet();
    private final long start = SystemClock.elapsedRealtime();
    private long last = start;

    synchronized void mark(String phase) { mark(phase, "-"); }
    synchronized void mark(String phase, String source) {
        long now = SystemClock.elapsedRealtime();
        AppPerformance.heroEvent(id, phase, now - start, now - last, source);
        last = now;
    }
}
