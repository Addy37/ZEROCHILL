package com.webapp.crazyshit;

import android.os.Looper;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.robolectric.util.ReflectionHelpers;
import static org.robolectric.Shadows.shadowOf;

/** Drain frame dispatch, the snapshot worker, and asynchronous list publication in order. */
final class NotificationInboxTestIdle {
    static void await(UpdateInboxActivity activity) {
        ExecutorService worker = ReflectionHelpers.getField(activity, "historyIo");
        try {
            for (int i = 0; i < 6; i++) {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
                worker.submit(() -> { }).get(5, TimeUnit.SECONDS);
                shadowOf(Looper.getMainLooper()).idle();
            }
        } catch (Exception error) {
            throw new AssertionError("Notification worker did not drain", error);
        }
    }
}
