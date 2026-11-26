package com.webapp.crazyshit;
import org.junit.Test;
import static org.junit.Assert.*;

public class SharedArtworkRevealTest {
    @Test public void completedIdentityDoesNotReplayAndScopesStaySeparate() {
        SharedArtworkReveal.Session session = new SharedArtworkReveal.Session();
        assertFalse(session.seen("shows:one"));
        session.mark("shows:one");
        assertTrue(session.seen("shows:one"));
        assertFalse(session.seen("library:one"));
    }

    @Test public void denseRevealsOverlapWithoutLongQueueDelay() {
        SharedArtworkReveal.Session session = new SharedArtworkReveal.Session();
        assertEquals(1000L, session.reserve(1000L, 5));
        assertEquals(1025L, session.reserve(1000L, 5));
        for (int i = 0; i < 100; i++) assertTrue(session.reserve(1000L, 5) <= 1150L);
    }
}
