package com.webapp.crazyshit;

/** Main-thread inventory pressure. Independent of player count and media buffering. */
final class ShitTokRunwayPolicy {
    static final int NORMAL = 6, RAPID = 10;
    static final long RAPID_WINDOW_MS = 1_500L, DECAY_MS = 4_000L;
    private long lastForward = Long.MIN_VALUE / 2;
    private volatile long rapidUntil;
    private int streak;
    private boolean refilling = true;

    void forward(long now) {
        streak = now - lastForward <= RAPID_WINDOW_MS ? streak + 1 : 1;
        lastForward = now;
        if (streak >= 2) rapidUntil = now + DECAY_MS;
    }
    int target(long now) { return rapid(now) ? RAPID : NORMAL; }
    boolean rapid(long now) { return rapidUntil > now; }
    int threshold(long now) { return threshold(now, false); }
    int threshold(long now, boolean filtered) { return rapid(now) ? (filtered ? 8 : 6) : (filtered ? 5 : 4); }
    boolean needsWork(int usable, long now) {
        return needsWork(usable, now, false);
    }
    boolean needsWork(int usable, long now, boolean filtered) {
        if (usable >= target(now)) refilling = false;
        else if (usable <= threshold(now, filtered)) refilling = true;
        return refilling;
    }
    void reset() { streak = 0; rapidUntil = 0; lastForward = Long.MIN_VALUE / 2; refilling = true; }
}
