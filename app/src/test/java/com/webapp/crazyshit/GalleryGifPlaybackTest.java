package com.webapp.crazyshit;

import android.graphics.drawable.Animatable;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class GalleryGifPlaybackTest {
    private static final class Frame implements Animatable {
        boolean running;
        int starts, stops;
        @Override public void start() { running = true; starts++; }
        @Override public void stop() { running = false; stops++; }
        @Override public boolean isRunning() { return running; }
    }

    @Test public void startsAtMostFourAndStopsOffscreenBeforeStartingReplacement() {
        GalleryGifPlayback playback = new GalleryGifPlayback();
        Frame a = new Frame(), b = new Frame(), c = new Frame(), d = new Frame(), e = new Frame();
        playback.update(Arrays.asList(a,b,c,d,e), 100);
        assertTrue(a.running && b.running && c.running && d.running);
        assertFalse(e.running);
        playback.update(Arrays.asList(b,c,d,e), 101);
        assertFalse(a.running);
        assertTrue(e.running);
        assertEquals(1, b.starts);
        playback.stopAll();
        assertFalse(b.running || c.running || d.running || e.running);
    }

    @Test public void scrollingStopsImmediatelyAndRequires250MillisecondsOfIdle() {
        GalleryGifPlayback playback = new GalleryGifPlayback();
        Frame a = new Frame();
        playback.update(Collections.singletonList(a), 100);
        playback.defer(120);
        assertFalse(a.running);
        playback.update(Collections.singletonList(a), 369);
        assertFalse(a.running);
        playback.defer(300);
        playback.update(Collections.singletonList(a), 549);
        assertFalse(a.running);
        playback.update(Collections.singletonList(a), 550);
        assertTrue(a.running);
        assertEquals(2, a.starts);
        playback.stop(a);
        assertFalse(a.running);
        assertEquals(2, a.stops);
    }

    @Test public void emptyViewportAndDuplicateFramesDoNotConsumeExtraSlots() {
        GalleryGifPlayback playback = new GalleryGifPlayback();
        Frame a = new Frame(), b = new Frame(), c = new Frame(), d = new Frame();
        playback.update(Arrays.asList(a,a,a,b,c,d), 1);
        assertTrue(a.running && b.running && c.running && d.running);
        playback.update(Collections.emptyList(), 2);
        assertFalse(a.running || b.running || c.running || d.running);
    }
}
