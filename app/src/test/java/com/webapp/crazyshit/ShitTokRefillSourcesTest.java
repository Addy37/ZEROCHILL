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
            if (source != 3 && source != 1) return Collections.emptyList();
            return Collections.singletonList(ShitTokContinuityTest.item("provider" + source));
        });
        ReflectionHelpers.setField(mixer, "starterPending", false);
        try {
            mixer.loadRandomBatch(RuntimeEnvironment.getApplication(), true, items -> {
                if (items.stream().anyMatch(i -> i.title.equals("provider1"))) {
                    assertEquals(1, release.getCount());
                    streamed.set(true); release.countDown();
                }
            });
            assertTrue(streamed.get());
        } finally { release.countDown(); }
    }

    @Test public void chaosRecoveryUsesOnlyApprovedWildSources() throws Exception {
        Set<Integer> requested = ConcurrentHashMap.newKeySet();
        CountDownLatch expected = new CountDownLatch(4);
        ChaosSourceMixer mixer = new ChaosSourceMixer(new CrazyShitRepository(), new Random(9), source -> {
            requested.add(source);
            expected.countDown();
            return Collections.emptyList();
        });
        try {
            mixer.onFirstFrame();
            mixer.recoverSources(RuntimeEnvironment.getApplication());
            assertTrue(expected.await(2, TimeUnit.SECONDS));
            assertEquals(new HashSet<>(Arrays.asList(1, 2, 3, 4)), requested);
            ShitTokSourcePool pool = ReflectionHelpers.getField(mixer, "sourcePool");
            for (int source : new int[]{5, 6, 7, 8})
                assertEquals(ShitTokSourcePool.Health.FILTERED, pool.health(source));
        } finally { mixer.stopPreference(); }
    }

    @Test public void chaosCannotDrainCachedCreatorMedia() {
        Application app = RuntimeEnvironment.getApplication();
        int baddieHubSource = ReflectionHelpers.getStaticField(ChaosSourceMixer.class, "BATCH_BADDIEHUB");
        NativeContentItem clip = new NativeContentItem(NativeContentItem.KIND_MEDIA, "Test creator",
                "https://baddiehub.com/clip/", "", "", "BaddieHub", "", "");
        ChaosSourceMixer mixer = new ChaosSourceMixer(new CrazyShitRepository(), new Random(7),
                source -> Collections.emptyList());
        try {
            ShitTokCategoryStrip.Tab.select(app, ShitTokCategoryStrip.Tab.CHAOS);
            ShitTokSourcePool pool = ReflectionHelpers.getField(mixer, "sourcePool");
            pool.putCached(baddieHubSource, Collections.singletonList(clip));
            mixer.requestPreference(app, () -> {}, false);
            assertEquals(ShitTokSourcePool.Health.FILTERED, pool.health(baddieHubSource));
            assertTrue(mixer.takeReadyCandidates().isEmpty());
            assertEquals(Collections.singletonList(clip), pool.peekCached(baddieHubSource));
        } finally { mixer.stopPreference(); }
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
