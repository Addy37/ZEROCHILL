package com.webapp.crazyshit;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class SocialContentNavigationTest {
    @Test
    public void nativeRouteAcceptsRecognizedVideoPagesButNotUnknownOrCreatorPages() {
        assertTrue(SocialContentNavigator.supportsNativeVideo("https://crazyshit.com/video/clip"));
        assertTrue(SocialContentNavigator.supportsNativeVideo("https://efukt.com/watch/clip"));
        assertTrue(SocialContentNavigator.supportsNativeVideo(
                "https://fapello.com/video/creator/123/"));
        assertFalse(SocialContentNavigator.supportsNativeVideo("https://fapello.com/creator/"));
        assertFalse(SocialContentNavigator.supportsNativeVideo("https://fakecrazyshit.com/video/clip"));
        assertFalse(SocialContentNavigator.supportsNativeVideo("file:///sdcard/clip.mp4"));
        assertFalse(SocialContentNavigator.supportsNativeVideo("https://unknown.example/watch"));
    }

    @Test
    public void resolvedImagesStayOnCommentFallbackInsteadOfVideoPlayer() {
        assertTrue(SocialContentNavigator.isImageMedia("https://cdn.example/42.jpg?token=abc"));
        assertTrue(SocialContentNavigator.isImageMedia("https://cdn.example/42.webp"));
        assertFalse(SocialContentNavigator.isImageMedia("https://cdn.example/42.mp4?token=abc"));
    }
    @Test public void replyRouteKeepsTheOriginalCanonicalThreadAndTaskStack() {
        android.app.Activity host = org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup().get();
        SocialContentNavigator.Session savedSession = SocialContentNavigator.session;
        SocialContentNavigator.Resolver savedResolver = SocialContentNavigator.resolver;
        java.util.concurrent.Executor savedExecutor = SocialContentNavigator.executor;
        try {
            SocialContentNavigator.session = activity -> "me";
            SocialContentNavigator.executor = Runnable::run;
            SocialContentNavigator.resolver = (activity, url, cached) -> new CrazyShitRepository.StreamInfo(
                    "https://cdn.example/video.mp4", "https://crazyshit.com/video/redirect", "Resolved video", url);
            UpdateInboxStore.Entry entry = new UpdateInboxStore.Entry();
            entry.category = UpdateInboxStore.CATEGORY_SOCIAL; entry.accountId = "me";
            entry.pageUrl = "https://crazyshit.com/video/original"; entry.videoTitle = "Original video";
            entry.commentId = "exact-reply";
            SocialContentNavigator.open(host, entry, true);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            android.content.Intent launched = org.robolectric.Shadows.shadowOf(host).getNextStartedActivity();
            org.junit.Assert.assertNotNull(launched);
            org.junit.Assert.assertEquals(VideoDetailActivity.class.getName(), launched.getComponent().getClassName());
            org.junit.Assert.assertEquals(entry.pageUrl, launched.getStringExtra(PlayerActivity.EXTRA_PAGE_URL));
            org.junit.Assert.assertEquals("exact-reply", launched.getStringExtra(VideoDetailActivity.EXTRA_SOCIAL_FOCUS_COMMENT_ID));
            org.junit.Assert.assertTrue(launched.getBooleanExtra(VideoDetailActivity.EXTRA_SOCIAL_AUTO_REPLY, false));
            org.junit.Assert.assertEquals(0, launched.getFlags());
        } finally {
            SocialContentNavigator.session = savedSession; SocialContentNavigator.resolver = savedResolver;
            SocialContentNavigator.executor = savedExecutor;
        }
    }

    @Test public void switchedAccountCannotPublishDelayedResolutionAndRepeatedTapsResolveOnce() {
        android.app.Activity host = org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup().get();
        SocialContentNavigator.Session savedSession = SocialContentNavigator.session;
        SocialContentNavigator.Resolver savedResolver = SocialContentNavigator.resolver;
        java.util.concurrent.Executor savedExecutor = SocialContentNavigator.executor;
        final String[] account = {"me"};
        java.util.ArrayList<Runnable> work = new java.util.ArrayList<>();
        try {
            SocialContentNavigator.session = activity -> account[0];
            SocialContentNavigator.executor = work::add;
            SocialContentNavigator.resolver = (activity, url, cached) -> new CrazyShitRepository.StreamInfo(
                    "https://cdn.example/video.mp4", url, "Video");
            UpdateInboxStore.Entry entry = new UpdateInboxStore.Entry();
            entry.category = UpdateInboxStore.CATEGORY_SOCIAL; entry.accountId = "me";
            entry.pageUrl = "https://crazyshit.com/video/original"; entry.commentId = "reply";
            SocialContentNavigator.open(host, entry, false); SocialContentNavigator.open(host, entry, false);
            org.junit.Assert.assertEquals(1, work.size());
            account[0] = "another-account";
            work.get(0).run();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            org.junit.Assert.assertNull(org.robolectric.Shadows.shadowOf(host).getNextStartedActivity());
        } finally {
            SocialContentNavigator.session = savedSession; SocialContentNavigator.resolver = savedResolver;
            SocialContentNavigator.executor = savedExecutor;
        }
    }

}
