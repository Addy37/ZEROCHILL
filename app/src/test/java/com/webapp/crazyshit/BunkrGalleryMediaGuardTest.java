package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.*;

public class BunkrGalleryMediaGuardTest {
    @Test public void missingResolutionMustNotReachGalleryCallbacks() {
        assertFalse(BunkrGalleryActivity.hasPlayableStream(null));
        assertFalse(BunkrGalleryActivity.hasPlayableStream(
                new CrazyShitRepository.StreamInfo(null, "https://example.com/video", "Video")));
        assertFalse(BunkrGalleryActivity.hasPlayableStream(
                new CrazyShitRepository.StreamInfo("", "https://example.com/video", "Video")));
        assertFalse(BunkrGalleryActivity.hasPlayableStream(
                new CrazyShitRepository.StreamInfo("   ", "https://example.com/video", "Video")));
    }

    @Test public void resolvedPlaybackStillWorks() {
        assertTrue(BunkrGalleryActivity.hasPlayableStream(
                new CrazyShitRepository.StreamInfo(
                        "https://example.com/clip.mp4", "https://example.com/video", "Video")));
    }
}
