package com.webapp.crazyshit;

import android.os.SystemClock;
import java.io.InterruptedIOException;

/** Shared cadence for Pawchive playback and poster byte requests. */
final class PawchiveMediaRequests {
    static final String USER_AGENT = "ZEROCHILL/7.5.0 (+https://github.com/Addy37/ZEROCHILL)";
    // The CDN asks clients to start at most one request per second.
    private static final long MIN_INTERVAL_MS = 1100L;
    private static long nextStart;
    private static final java.util.concurrent.atomic.AtomicInteger TRACE_COUNT = new java.util.concurrent.atomic.AtomicInteger();

    private PawchiveMediaRequests() { }

    static void awaitStart() throws InterruptedIOException {
        awaitStart(() -> false);
    }

    static synchronized void awaitStart(java.util.function.BooleanSupplier canceled) throws InterruptedIOException {
        try {
            while (true) {
                if (canceled.getAsBoolean()) throw new InterruptedIOException("Pawchive request canceled");
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                long now = SystemClock.elapsedRealtime();
                long remaining = nextStart - now;
                if (remaining <= 0) {
                    nextStart = now + MIN_INTERVAL_MS;
                    return;
                }
                PawchiveMediaRequests.class.wait(remaining);
            }
        } catch (InterruptedException canceled) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Pawchive request canceled");
        }
    }

    static synchronized void wakeWaiters() { PawchiveMediaRequests.class.notifyAll(); }

    // Debug-only, bounded, no media URLs, titles, response bodies or credentials.
    static void trace(String identity, String stage) {
        if (!BuildConfig.DEBUG || identity == null || identity.isEmpty() || TRACE_COUNT.incrementAndGet() > 300) return;
        android.util.Log.d("PawchiveMedia", Integer.toHexString(identity.hashCode()) + " " + stage);
    }

    static boolean shouldStopRetrying(int status) {
        return status == 403 || status == 429;
    }
}
