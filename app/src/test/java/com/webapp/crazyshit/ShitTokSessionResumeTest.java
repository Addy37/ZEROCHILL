package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ShitTokSessionResumeTest {
    @Test public void sameClipResumesAfterPlayerRecreationOnlyInThisView() {
        ShitTokSessionResume state = new ShitTokSessionResume();
        state.remember("clip-one", 31_250L, 90_000L);
        assertEquals(31_250L, state.positionFor("clip-one"));
        assertEquals(0L, state.positionFor("clip-two"));
        assertEquals(0L, new ShitTokSessionResume().positionFor("clip-one"));
    }

    @Test public void swipeRefreshAndCompletionClearObsoletePosition() {
        ShitTokSessionResume state = new ShitTokSessionResume();
        state.remember("clip-one", 10_000L, 20_000L);
        state.clear();
        assertEquals(0L, state.positionFor("clip-one"));
        state.remember("clip-two", 19_800L, 20_000L);
        assertEquals(0L, state.positionFor("clip-two"));
        state.remember("clip-three", 5_000L, 20_000L);
        state.remember("clip-four", 7_000L, 20_000L);
        assertEquals(0L, state.positionFor("clip-three"));
        assertEquals(7_000L, state.positionFor("clip-four"));
    }
}
