package com.webapp.crazyshit;

import android.app.Application;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class NotificationEntryLatencyTest {
    @Before public void clear() {
        RuntimeEnvironment.getApplication().getSharedPreferences("zerochill_update_inbox_v1", 0)
                .edit().clear().commit();
        RuntimeEnvironment.getApplication().getSharedPreferences("zerochill_account_secure_state", 0)
                .edit().clear().commit();
    }

    @Test public void populatedInboxDoesNotBlockScreenCreationAndLoadsAfterEntry() {
        Application app = RuntimeEnvironment.getApplication();
        for (int i = 0; i < 80; i++) UpdateInboxStore.recordAppUpdate(app, "test-" + i, "Update " + i, false);
        ActivityController<UpdateInboxActivity> controller = Robolectric.buildActivity(UpdateInboxActivity.class).create();
        UpdateInboxActivity activity = controller.get();
        ExecutorService worker = ReflectionHelpers.getField(activity, "historyIo");
        CountDownLatch release = new CountDownLatch(1);
        worker.execute(() -> { try { release.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } });
        try {
            RecyclerView recycler = ReflectionHelpers.getField(activity, "recycler");
            assertNotNull(activity.findViewById(android.R.id.content));
            assertEquals(0, recycler.getAdapter().getItemCount());
            controller.start().resume().visible();
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
            assertEquals(0, recycler.getAdapter().getItemCount());
            assertEquals("Loading notifications…", ((TextView) ReflectionHelpers.getField(activity, "count")).getText().toString());
            release.countDown();
            NotificationInboxTestIdle.await(activity);
            assertEquals(80, recycler.getAdapter().getItemCount());
            assertEquals("80 unread notifications", ((TextView) ReflectionHelpers.getField(activity, "count")).getText().toString());
        } finally { release.countDown(); controller.pause().stop().destroy(); }
    }

    @Test public void pausedSnapshotCannotPublishAndResumeReadsChanges() {
        Application app = RuntimeEnvironment.getApplication();
        UpdateInboxStore.recordAppUpdate(app, "first", "First", false);
        ActivityController<UpdateInboxActivity> controller = Robolectric.buildActivity(UpdateInboxActivity.class).setup();
        UpdateInboxActivity activity = controller.get();
        NotificationInboxTestIdle.await(activity);
        ExecutorService worker = ReflectionHelpers.getField(activity, "historyIo");
        CountDownLatch release = new CountDownLatch(1);
        worker.execute(() -> { try { release.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } });
        try {
            UpdateInboxStore.recordAppUpdate(app, "second", "Second", false);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
            controller.pause();
            release.countDown();
            NotificationInboxTestIdle.await(activity);
            assertEquals("1 unread notification", ((TextView) ReflectionHelpers.getField(activity, "count")).getText().toString());
            controller.resume();
            NotificationInboxTestIdle.await(activity);
            assertEquals("2 unread notifications", ((TextView) ReflectionHelpers.getField(activity, "count")).getText().toString());
        } finally { release.countDown(); controller.pause().stop().destroy(); }
    }
}
