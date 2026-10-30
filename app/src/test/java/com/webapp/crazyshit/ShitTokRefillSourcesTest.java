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
