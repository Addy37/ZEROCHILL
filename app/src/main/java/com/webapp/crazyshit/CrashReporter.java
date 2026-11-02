package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.os.Build;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class CrashReporter {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(),
            SCHEDULED = new AtomicBoolean(),
            REFRESHING = new AtomicBoolean();
    private static final ScheduledExecutorService WORK =
            Executors.newSingleThreadScheduledExecutor();
    private static volatile String screen = "startup",
            diagnostics = "No session diagnostics captured before this crash.";
    private static CrashStore store;
    private static volatile boolean foreground;
    private static final AtomicBoolean SNAPSHOT_SCHEDULED = new AtomicBoolean();

    interface Capture {
        void save(Thread thread, Throwable error) throws Exception;
    }

    static final class Handler implements Thread.UncaughtExceptionHandler {
        private final Thread.UncaughtExceptionHandler previous;
        private final Capture capture;

        Handler(Thread.UncaughtExceptionHandler previous, Capture capture) {
            this.previous = previous;
            this.capture = capture;
        }

        @Override
        public void uncaughtException(Thread thread, Throwable error) {
            try {
                capture.save(thread, error);
            } catch (Throwable ignored) {
            } finally {
                if (previous != null) previous.uncaughtException(thread, error);
                else {
                    Thread.setDefaultUncaughtExceptionHandler(null);
                    thread.getThreadGroup().uncaughtException(thread, error);
                }
            }
        }
    }

    static void install(Context context) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            store = new CrashStore(new File(context.getFilesDir(), "zerochill-crashes.json"));
            Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(
                    new Handler(
                            previous,
                            (thread, error) ->
                                    store.capture(
                                            CrashReport.capture(
                                                    error,
                                                    thread.getName(),
                                                    screen,
                                                    BuildConfig.VERSION_NAME,
                                                    BuildConfig.VERSION_CODE,
                                                    BuildConfig.SOURCE_REVISION,
                                                    Build.VERSION.RELEASE,
                                                    Build.MANUFACTURER
                                                            + " / "
                                                            + Build.MODEL
                                                            + " / "
                                                            + Build.DEVICE,
                                                    System.currentTimeMillis(),
                                                    diagnostics))));
        } catch (Throwable ignored) {
        }
    }

    static void resumed(Activity activity) {
        try {
            foreground = true;
            screen = activity.getClass().getSimpleName();
            scheduleSnapshot();
            if (REFRESHING.compareAndSet(false, true))
                WORK.execute(
                        () -> {
                            try {
                                diagnostics = snapshot();
                            } catch (Throwable ignored) {
                            } finally {
                                REFRESHING.set(false);
                            }
                        });
            if (SCHEDULED.compareAndSet(false, true)) {
                Context app = activity.getApplicationContext();
                WORK.schedule(
                        () -> {
                            try {
                                if (store != null && FeedbackRepository.isConfigured())
                                    CrashDelivery.flush(
                                            store,
                                            FeedbackRepository.crashTransport(app),
                                            System.currentTimeMillis());
                            } catch (Throwable ignored) {
                                /* Keep pending reports; never toast or retry in a loop. */
                            }
                        },
                        10,
                        TimeUnit.SECONDS);
            }
        } catch (Throwable ignored) {
        }
    }

    static void paused() {
        foreground = false;
    }

    private static void scheduleSnapshot() {
        if (!SNAPSHOT_SCHEDULED.compareAndSet(false, true)) return;
        WORK.schedule(
                () -> {
                    SNAPSHOT_SCHEDULED.set(false);
                    if (!foreground) return;
                    try {
                        diagnostics = snapshot();
                    } catch (Throwable ignored) {
                    }
                    scheduleSnapshot();
                },
                30,
                TimeUnit.SECONDS);
    }

    static String snapshot() {
        // userReport already contains AppPerformance. Reserve space for its tail and recent events.
        String report = ShitTokPoolDiagnostics.userReport();
        int recent = report.indexOf("Recent events"),
                performance = report.indexOf("This app session");
        int headEnd = recent >= 0 ? recent : performance >= 0 ? performance : report.length();
        String head = CrashSanitizer.bound(report.substring(0, headEnd), 900);
        String events =
                recent < 0
                        ? ""
                        : report.substring(
                                recent, performance < recent ? report.length() : performance);
        if (events.length() > 900) events = events.substring(events.length() - 900);
        String timings =
                performance < 0 ? "" : CrashSanitizer.bound(report.substring(performance), 800);
        return CrashSanitizer.clean(head + "\nRecent events (tail)\n" + events + "\n" + timings);
    }

    private CrashReporter() {}
}
