package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ShitTokAutoScrollPolicyTest {
    @Test public void selectedCompletionLoopsByDefaultAndAdvancesWhenEnabled() {
        assertEquals(ShitTokAutoScrollPolicy.Completion.LOOP,
                ShitTokAutoScrollPolicy.onCompleted(true, true, true, false, false, false));
        assertEquals(ShitTokAutoScrollPolicy.Completion.ADVANCE,
                ShitTokAutoScrollPolicy.onCompleted(true, true, true, false, true, false));
    }

    @Test public void pausedOffTabOldPageAndDraggingCannotAdvance() {
        for (boolean[] state : new boolean[][] {
                {false, true, true, false, false},
                {true, false, true, false, false},
                {true, true, false, false, false},
                {true, true, true, true, false},
                {true, true, true, false, true}
        }) {
            assertEquals(ShitTokAutoScrollPolicy.Completion.IGNORE,
                    ShitTokAutoScrollPolicy.onCompleted(
                            state[0], state[1], state[2], state[3], true, state[4]));
        }
    }

    @Test public void manualPauseOffModeDoesNotLoopUntilResumed() {
        assertEquals(ShitTokAutoScrollPolicy.Completion.IGNORE,
                ShitTokAutoScrollPolicy.onCompleted(true, true, true, true, false, false));
        assertEquals(ShitTokAutoScrollPolicy.Completion.LOOP,
                ShitTokAutoScrollPolicy.onCompleted(true, true, true, false, false, false));
        assertEquals(ShitTokAutoScrollPolicy.Completion.IGNORE,
                ShitTokAutoScrollPolicy.onCompleted(true, true, true, false, false, true));
    }
}
