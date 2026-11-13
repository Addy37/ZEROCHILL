package com.webapp.crazyshit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class VideoSocialSummaryCacheTest {
    private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    private final Executor executor = tasks::add;
    private final ArrayList<ZeroChillSocialRepository.VideoSocialState> values = new ArrayList<>();
    private final ArrayList<Exception> errors = new ArrayList<>();
    private String owner = "account-a";
    private int requests;
    private final ZeroChillSocialRepository.Callback<ZeroChillSocialRepository.VideoSocialState> callback =
            (value, error) -> { values.add(value); errors.add(error); };

    @Before public void clear() { ZeroChillSocialRepository.invalidateVideoSummary(); }
    private ZeroChillSocialRepository.VideoSocialState result() {
        return new ZeroChillSocialRepository.VideoSocialState(7, 3, owner.equals("account-a"));
    }
    private void load() {
        ZeroChillSocialRepository.videoSocialState("content", () -> owner,
                () -> { requests++; return result(); }, executor, callback);
    }
    private void drain() { while (!tasks.isEmpty()) tasks.remove().run(); }

    @Test public void concurrentLikeAndCountConsumersAndRepeatedReadsUseOneRequest() {
        load(); load(); drain(); load(); drain();
        assertEquals(1, requests);
        assertEquals(3, values.size());
        assertTrue(errors.stream().allMatch(e -> e == null));
    }
    @Test public void queuedCacheHitCannotLeakAcrossAccountSwitchOrLogout() {
        load(); drain(); load(); owner = "account-b"; drain();
        assertNull(values.get(1)); assertNotNull(errors.get(1));
        load(); drain(); assertFalse(values.get(2).liked);
        load(); owner = ""; drain();
        assertNull(values.get(3)); assertNotNull(errors.get(3));
        assertEquals(2, requests);
    }
    @Test public void accountChangeDuringRequestCannotPopulateOrDeliverCache() {
        ZeroChillSocialRepository.videoSocialState("content", () -> owner,
                () -> { requests++; owner = "account-b"; return result(); }, executor, callback);
        drain(); assertNull(values.get(0)); assertNotNull(errors.get(0));
        owner = "account-a"; load(); drain(); assertEquals(2, requests);
    }
    @Test public void writeDuringRequestRetriesOnceAndDeliversFreshSummary() {
        ZeroChillSocialRepository.videoSocialState("content", () -> owner, () -> {
            requests++;
            if (requests == 1) ZeroChillSocialRepository.invalidateVideoSummary();
            return result();
        }, executor, callback);
        drain(); assertEquals(2, requests); assertNotNull(values.get(0)); assertNull(errors.get(0));
    }
    @Test public void repeatedInvalidationHasBoundedRetryAndNoStaleValue() {
        ZeroChillSocialRepository.videoSocialState("content", () -> owner, () -> {
            requests++; ZeroChillSocialRepository.invalidateVideoSummary(); return result();
        }, executor, callback);
        drain(); assertEquals(2, requests); assertNull(values.get(0)); assertNotNull(errors.get(0));
    }
    @Test public void queuedCacheHitAfterWriteReloadsInsteadOfOverwritingOptimisticState() {
        load(); drain(); load(); ZeroChillSocialRepository.invalidateVideoSummary(); drain();
        assertEquals(2, requests); assertNull(errors.get(1));
    }
    @Test public void networkFailureDoesNotAutomaticallyLoop() {
        ZeroChillSocialRepository.videoSocialState("content", () -> owner, () -> {
            requests++; throw new java.io.IOException("offline");
        }, executor, callback);
        drain(); assertEquals(1, requests); assertNull(values.get(0)); assertNotNull(errors.get(0));
    }
    @Test public void ttlExpiryRequestsFreshData() {
        load(); drain();
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(121));
        load(); drain(); assertEquals(2, requests);
    }
    @Test public void anonymousSummarySupportsCountsWithoutOwnLike() {
        owner = ""; load(); load(); drain();
        assertEquals(1, requests); assertEquals(3, values.get(0).commentCount);
        assertFalse(values.get(0).liked); assertNull(errors.get(0));
    }

}
