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
}
