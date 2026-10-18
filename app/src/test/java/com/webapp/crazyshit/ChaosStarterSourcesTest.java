package com.webapp.crazyshit;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChaosStarterSourcesTest {
    private NativeContentItem media(String url) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, "Video", url, "", "", "", "");
    }

    @Test public void slowCrazyShitDoesNotHoldBackKaotic() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        long started = System.nanoTime();
        try {
            List<NativeContentItem> queue = ChaosStarterSources.first(source -> {
                if (source == ChaosStarterSources.CRAZYSHIT) {
                    release.await(5, TimeUnit.SECONDS);
                    throw new IOException("offline");
                }
                if (source == ChaosStarterSources.FAPELLO) throw new IOException("offline");
                return Arrays.asList(media("https://kaotic.com/one"), media("https://kaotic.com/two"));
            }, new Random(1), 800);
            assertEquals(2, queue.size());
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1500);
        } finally {
            release.countDown();
        }
    }

    @Test public void streamingStarterDoesNotWaitForSecondarySourcesOrSixCandidates() throws Exception {
        CountDownLatch early = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean sawFast = new java.util.concurrent.atomic.AtomicBoolean();
        ChaosStarterSources.first(source -> {
            if (source != ChaosStarterSources.KAOTIC) {
                if (!early.await(2, TimeUnit.SECONDS)) throw new AssertionError("starter blocked on slow source");
                return Collections.emptyList();
            }
            return Collections.singletonList(media("https://kaotic.com/fast"));
        }, new Random(1), 2500, candidates -> {
            if (candidates.stream().anyMatch(item -> item.url.endsWith("fast"))) {
                sawFast.set(true); early.countDown();
            }
        });
        assertTrue(sawFast.get());
    }

    @Test public void fapelloWorksWhenCrazyShitAndKaoticFail() throws Exception {
        List<NativeContentItem> queue = ChaosStarterSources.first(source -> {
            if (source == ChaosStarterSources.FAPELLO) return Collections.singletonList(media("https://fapello.com/one"));
            throw new IOException("offline");
        }, new Random(2), 800);
        assertEquals("https://fapello.com/one", queue.get(0).url);
    }

    @Test public void firstSuccessfulFapelloDoesNotWaitForOtherListings() throws Exception {
        CountDownLatch early = new CountDownLatch(1);
        ChaosStarterSources.first(source -> {
            if (source != ChaosStarterSources.FAPELLO) {
                assertTrue("portrait listing must progress without the general feeds", early.await(1, TimeUnit.SECONDS));
                return Collections.emptyList();
            }
            return Collections.singletonList(media("https://fapello.com/fast/video/"));
        }, new Random(1), 1500, items -> { if (!items.isEmpty()) early.countDown(); });
        assertEquals(0, early.getCount());
    }

    @Test public void eachStreamingCallbackContainsOnlyTheProviderThatJustFinished() throws Exception {
        java.util.Set<String> emitted = new java.util.HashSet<>();
        ChaosStarterSources.first(source -> Collections.singletonList(media("https://fixture.invalid/" + source)),
                new Random(1), 1000, items -> {
                    assertEquals(1, items.size());
                    assertTrue("a later source must not re-emit and reshuffle earlier candidates", emitted.add(items.get(0).url));
                });
        assertEquals(3, emitted.size());
    }
}
