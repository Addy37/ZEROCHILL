package com.webapp.crazyshit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** No network: validates the small 7.0 counter response contract and permanent-error rules. */
@RunWith(RobolectricTestRunner.class)
public class VideoSocialSummaryTest {
    @Test public void combinedSummaryParsesCountsAndCallerLike() throws Exception {
        ZeroChillSocialRepository.VideoSocialState own = ZeroChillSocialRepository
                .parseVideoSocialSummary("[{\"like_count\":42,\"comment_count\":3,\"liked_by_me\":true}]");
        assertEquals(42, own.likeCount);
        assertEquals(3, own.commentCount);
        assertTrue(own.liked);

        ZeroChillSocialRepository.VideoSocialState signedOut = ZeroChillSocialRepository
                .parseVideoSocialSummary("[{\"like_count\":42,\"comment_count\":3,\"liked_by_me\":false}]");
        assertFalse(signedOut.liked);
    }

    @Test(expected = IllegalStateException.class)
    public void missingFieldsDoNotSilentlyMarkLikeAsFalse() throws Exception {
        ZeroChillSocialRepository.parseVideoSocialSummary("[{\"like_count\":42,\"comment_count\":3}]");
    }

    @Test(expected = IllegalStateException.class)
    public void missingSummaryRowFailsClosed() throws Exception {
        ZeroChillSocialRepository.parseVideoSocialSummary("[]");
    }

    @Test public void permanentHttpErrorsStopAutomaticRetryButServerErrorsCanBackOff() {
        assertTrue(ZeroChillSocialRepository.isPermanentHttpError(
                new ZeroChillSocialRepository.BackendHttpException(401, "Session expired")));
        assertTrue(ZeroChillSocialRepository.isPermanentHttpError(
                new ZeroChillSocialRepository.BackendHttpException(404, "Route missing")));
        assertFalse(ZeroChillSocialRepository.isPermanentHttpError(
                new ZeroChillSocialRepository.BackendHttpException(429, "Slow down")));
        assertFalse(ZeroChillSocialRepository.isPermanentHttpError(
                new ZeroChillSocialRepository.BackendHttpException(503, "Unavailable")));
        assertFalse(ZeroChillSocialRepository.isPermanentHttpError(
                new IllegalStateException("No connectivity")));
    }
}
