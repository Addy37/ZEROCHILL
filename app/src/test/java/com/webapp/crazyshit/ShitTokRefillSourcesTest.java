package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokRefillSourcesTest {
    @Test public void fastProviderReplenishesBeforeSlowProviderCompletes() {
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean streamed = new AtomicBoolean();
        ChaosSourceMixer mixer = new ChaosSourceMixer(new CrazyShitRepository(), new Random(5), source -> {
            if (source == 3) release.await(2, TimeUnit.SECONDS);
            if (source != 3 && source != 5) return Collections.emptyList();
            return Collections.singletonList(ShitTokContinuityTest.item("provider" + source));
        });
        ReflectionHelpers.setField(mixer, "starterPending", false);
        try {
            mixer.loadRandomBatch(RuntimeEnvironment.getApplication(), true, items -> {
                if (items.stream().anyMatch(i -> i.title.equals("provider5"))) {
                    assertEquals(1, release.getCount());
                    streamed.set(true); release.countDown();
                }
            });
            assertTrue(streamed.get());
            assertEquals(0, mixer.activeFetches());
        } finally { release.countDown(); }
    }

    @Test public void shittokRecoveryDoesNotStartEfuktOrXMegaDrive() throws Exception {
        Set<Integer> requested = ConcurrentHashMap.newKeySet();
        CountDownLatch expected = new CountDownLatch(4);
        ChaosSourceMixer mixer = new ChaosSourceMixer(new CrazyShitRepository(), new Random(9), source -> {
            requested.add(source);
            expected.countDown();
            return Collections.emptyList();
        });

        mixer.recoverSources(RuntimeEnvironment.getApplication());

        assertTrue(expected.await(2, TimeUnit.SECONDS));
        assertFalse(requested.contains(2));
        assertFalse(requested.contains(8));
        ShitTokSourcePool pool = ReflectionHelpers.getField(mixer, "sourcePool");
        assertEquals(ShitTokSourcePool.Health.FILTERED, pool.health(2));
        assertEquals(ShitTokSourcePool.Health.FILTERED, pool.health(8));
    }

    @Test public void baddieHubAcquiresInAllAndKeepsItsBufferedVideoAcrossOnlyFap() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        int baddieHubSource = ReflectionHelpers.getStaticField(ChaosSourceMixer.class, "BATCH_BADDIEHUB");
        NativeContentItem clip = new NativeContentItem(NativeContentItem.KIND_MEDIA, "Test creator",
                "https://baddiehub.com/clip/", "", "", "BaddieHub", "", "");
        AtomicInteger baddieHubRequests = new AtomicInteger();
        ChaosSourceMixer mixer = new ChaosSourceMixer(new CrazyShitRepository(), new Random(7), source -> {
            if (source != baddieHubSource) return Collections.emptyList();
            baddieHubRequests.incrementAndGet();
            return Collections.singletonList(clip);
        });
        try {
            mixer.recoverSources(app);
            ShitTokSourcePool pool = ReflectionHelpers.getField(mixer, "sourcePool");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            synchronized (pool) {
                while (!pool.hasReady(baddieHubSource)) {
                    long remaining = deadline - System.nanoTime();
                    assertTrue("All feed never buffered BaddieHub", remaining > 0);
                    TimeUnit.NANOSECONDS.timedWait(pool, remaining);
                }
            }

            ShitTokSourceMode.set(app, ShitTokSourceMode.ONLY_FAP);
            mixer.requestPreference(app, () -> {}, false);
            mixer.recoverSources(app);
            assertEquals(ShitTokSourcePool.Health.FILTERED, pool.health(baddieHubSource));
            assertTrue(mixer.takeReadyCandidates().isEmpty());
            assertFalse(mixer.matchesPreference(clip, ContentPreference.ALL));

            ShitTokSourceMode.set(app, ShitTokSourceMode.ALL);
            mixer.requestPreference(app, () -> {}, false);
            assertEquals(Collections.singletonList(clip), mixer.takeReadyCandidates());
            assertEquals(1, baddieHubRequests.get());
        } finally {
            ShitTokSourceMode.set(app, ShitTokSourceMode.ALL);
            mixer.stopPreference();
        }
    }

    @Test public void shitShowResolverCanReuseLiveHostFromApplicationContext() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ShitShowPlayableResolver.rememberHost(activity);

        assertSame(activity, ShitShowPlayableResolver.hostActivity(RuntimeEnvironment.getApplication()));

        activity.finish();
        assertNull(ShitShowPlayableResolver.hostActivity(RuntimeEnvironment.getApplication()));
    }

    @Test public void postStarterPageDeckWorksWithoutFirstFrameAndDoesNotResetOnRefresh() {
        ChaosSourceMixer mixer = new ChaosSourceMixer(new CrazyShitRepository(), new Random(7));
        Set<Integer> firstCycle = new HashSet<>();
        for (int i = 0; i < 16; i++) assertTrue(firstCycle.add(mixer.nextPage("kaotic", 32)));
        mixer.resetDeck();
        for (int i = 0; i < 16; i++) assertTrue(firstCycle.add(mixer.nextPage("kaotic", 32)));
        assertEquals(32, firstCycle.size());
        assertTrue(firstCycle.contains(32));
        assertFalse((boolean) ReflectionHelpers.getField(mixer, "explorationAllowed"));
    }
}
