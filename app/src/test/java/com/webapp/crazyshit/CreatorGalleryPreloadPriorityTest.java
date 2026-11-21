package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 35)
public class CreatorGalleryPreloadPriorityTest {
    @Test public void visibleCreatorPromotesExistingQueueEntry() throws Exception {
        CreatorGallerySpec selected = CreatorGallerySpec.from(new NativeContentItem(
                NativeContentItem.KIND_CREATOR, "Priority fixture",
                "https://fapello.com/priority_fixture/", "", "", "", ""));
        String taskKey = CreatorNameMatcher.normalized(selected.sessionKey);
        ThreadPoolExecutor io = ReflectionHelpers.getStaticField(CreatorGalleryPreloader.class, "IO");
        Map<String, Runnable> pending = ReflectionHelpers.getStaticField(
                CreatorGalleryPreloader.class, "PENDING");

        CountDownLatch occupied = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Runnable block = () -> {
            occupied.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        };
        Runnable older = task("other", 0, () -> { });
        Runnable promoted = task(taskKey, 1, () -> { });

        io.execute(task("worker1", 0, block));
        io.execute(task("worker2", 1, block));
        try {
            assertTrue(occupied.await(3, TimeUnit.SECONDS));
            io.execute(older);
            pending.put(taskKey, promoted);
            io.execute(promoted);
            assertSame(older, io.getQueue().peek());

            CreatorGalleryPreloader.warm(RuntimeEnvironment.getApplication(), selected,
                    CreatorGalleryPreloader.PRIORITY_HIGH);

            assertEquals(2, io.getQueue().size());
            assertSame(promoted, io.getQueue().peek());
            assertSame(promoted, pending.get(taskKey));
        } finally {
            io.remove(older);
            io.remove(promoted);
            pending.remove(taskKey, promoted);
            release.countDown();
        }
    }

    private static Runnable task(String key, long sequence, Runnable work) {
        Class<?> type = ReflectionHelpers.loadClass(CreatorGalleryPreloader.class.getClassLoader(),
                CreatorGalleryPreloader.class.getName() + "$WarmTask");
        return (Runnable) ReflectionHelpers.callConstructor(type,
                ReflectionHelpers.ClassParameter.from(String.class, key),
                ReflectionHelpers.ClassParameter.from(int.class, CreatorGalleryPreloader.PRIORITY_NORMAL),
                ReflectionHelpers.ClassParameter.from(long.class, sequence),
                ReflectionHelpers.ClassParameter.from(Runnable.class, work));
    }
}
