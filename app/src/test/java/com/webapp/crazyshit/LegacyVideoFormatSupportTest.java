package com.webapp.crazyshit;

import static org.junit.Assert.*;
import org.junit.Test;

public final class LegacyVideoFormatSupportTest {
    @Test public void wmvAndAsfUseFallbackWithoutProbingMedia3() {
        assertTrue(LegacyVideoFormatSupport.requiresVlc("https://cdn.test/file.wmv?token=abc"));
        assertTrue(LegacyVideoFormatSupport.requiresVlc("https://cdn.test/file.ASF#part"));
        assertFalse(LegacyVideoFormatSupport.requiresVlc("https://cdn.test/video.mp4"));
    }

    @Test public void unsupportedContainersFallbackButMp4AndStreamingStayNative() {
        assertTrue(LegacyVideoFormatSupport.retryWithVlc("https://cdn.test/file.avi", 3003));
        assertTrue(LegacyVideoFormatSupport.retryWithVlc("https://cdn.test/file.wmv", 3003));
        assertFalse(LegacyVideoFormatSupport.retryWithVlc("https://cdn.test/file.mp4", 3003));
        assertFalse(LegacyVideoFormatSupport.retryWithVlc("https://cdn.test/file.m3u8", 3003));
        assertFalse(LegacyVideoFormatSupport.retryWithVlc("https://cdn.test/file.mpd", 3003));
        assertFalse(LegacyVideoFormatSupport.retryWithVlc("https://cdn.test/file.wmv", 2004));
    }

    @Test public void rejectsInvalidOrNonHttpsMediaBeforeOpeningPlayer() {
        assertTrue(LegacyVideoFormatSupport.validHttpsMedia("https://c1no1-b.cdn.cr/storage/media/example.wmv"));
        assertFalse(LegacyVideoFormatSupport.validHttpsMedia("http://cdn.test/file.wmv"));
        assertFalse(LegacyVideoFormatSupport.validHttpsMedia("file:///sdcard/file.wmv"));
        assertFalse(LegacyVideoFormatSupport.validHttpsMedia("https://user:pass@cdn.test/file.wmv"));
        assertFalse(LegacyVideoFormatSupport.validHttpsMedia(null));
    }
}
