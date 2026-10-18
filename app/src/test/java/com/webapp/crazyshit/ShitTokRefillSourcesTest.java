package com.webapp.crazyshit;

import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
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
