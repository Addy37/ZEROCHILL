package com.webapp.crazyshit;

/** Playback decision for a completed, selected ShitTok clip. */
final class ShitTokAutoScrollPolicy {
    enum Completion { IGNORE, LOOP, ADVANCE }

    private ShitTokAutoScrollPolicy() {
    }

    static Completion onCompleted(boolean active, boolean resumed, boolean selected,
                                  boolean userPaused, boolean autoScrollEnabled,
                                  boolean pagerDragging) {
        if (!active || !resumed || !selected || userPaused) return Completion.IGNORE;
        if (pagerDragging) return Completion.IGNORE;
        return autoScrollEnabled ? Completion.ADVANCE : Completion.LOOP;
    }
}
