package com.webapp.crazyshit;

/** Selected-session starvation accounting. All observations arrive on the application looper. */
final class ShitTokBufferProtection {
    static final long STEADY_PLAYBACK_MS = 15_000L;
    static final long REPEAT_WINDOW_MS = 30_000L;
    static final long MAX_EXTRA_WAIT_MS = 12_000L;
    private String selected = "";
    private boolean started;
    private boolean protectedPlayback;
    private long stallAt = -1L;
    private long lastStallAt = -1L;
    private long sampleAt = -1L;
    private long samplePosition = -1L;
    private boolean samplePlaying;
    private int rapidStalls;
    private int rebufferCount;
    private int startupBuffers;
    private boolean startupBuffering;
    private long totalRebufferMs;
    private long longestRebufferMs;
    private long playedMs;
    private long steadyMs;
    private long beforeFirstRebufferMs = -1L;
    private long stalledBufferedMs;
    private long recoveryTargetMs;

    void select(String url) {
        if (selected.equals(url)) return;
        selected = url;
        started = protectedPlayback = startupBuffering = samplePlaying = false;
        stallAt = lastStallAt = sampleAt = samplePosition = -1L;
        rapidStalls = rebufferCount = startupBuffers = 0;
        totalRebufferMs = longestRebufferMs = playedMs = steadyMs = recoveryTargetMs = 0L;
        beforeFirstRebufferMs = -1L;
        stalledBufferedMs = 0L;
    }

    void observe(long now, int state, boolean playing, boolean playRequested,
                 boolean ownsPlayback, boolean userPaused, boolean error,
                 long positionMs, long bufferedMs) {
        // Player states: IDLE=1, BUFFERING=2, READY=3, ENDED=4.
        boolean eligible = ownsPlayback && playRequested && !userPaused && !error;
        if (eligible && state == 3 && playing) {
            if (samplePlaying && sampleAt >= 0L && positionMs > samplePosition) {
                long elapsed = Math.max(0L, Math.min(2_000L, now - sampleAt));
                playedMs += elapsed;
                steadyMs += elapsed;
            }
            started = true;
            if (protectedPlayback && steadyMs >= STEADY_PLAYBACK_MS) {
                protectedPlayback = false;
                rapidStalls = 0;
                recoveryTargetMs = 0L;
            }
        } else {
            steadyMs = 0L;
        }
        boolean buffering = eligible && state == 2;
        if (buffering && started) {
            if (stallAt < 0L) {
                rapidStalls = lastStallAt >= 0L && now - lastStallAt <= REPEAT_WINDOW_MS
                        ? Math.min(3, rapidStalls + 1) : 1;
                lastStallAt = stallAt = now;
                rebufferCount++;
                if (beforeFirstRebufferMs < 0L) beforeFirstRebufferMs = playedMs;
                stalledBufferedMs = Math.max(0L, bufferedMs);
                recoveryTargetMs = rapidStalls == 1 ? 5_000L : rapidStalls == 2 ? 8_000L : 12_000L;
                protectedPlayback = true;
            }
        } else finishStall(now);
        if (buffering && !started && !startupBuffering) startupBuffers++;
        startupBuffering = buffering && !started;
        if (error || state == 4) {
            protectedPlayback = false;
            recoveryTargetMs = 0L;
            rapidStalls = 0;
            if (error) started = false;
        }
        samplePlaying = eligible && state == 3 && playing;
        sampleAt = now;
        samplePosition = positionMs;
    }

    /** A user seek or lifecycle interruption is not a network rebuffer. */
    void interrupt(long now, boolean seek) {
        finishStall(now);
        samplePlaying = false;
        steadyMs = 0L;
        if (seek) started = false;
    }

    private void finishStall(long now) {
        if (stallAt < 0L) return;
        long duration = Math.max(0L, now - stallAt);
        totalRebufferMs += duration;
        longestRebufferMs = Math.max(longestRebufferMs, duration);
        stallAt = -1L;
    }

    boolean isProtected() { return protectedPlayback; }
    long recoveryTargetMs() { return recoveryTargetMs; }
    long recoveryDeadlineMs() { return stallAt < 0L ? 0L : stallAt + MAX_EXTRA_WAIT_MS; }
    int rebufferCount() { return rebufferCount; }
    static boolean mayResume(boolean normal, long bufferedUs, float speed,
                             long targetUs, long deadlineMs, long nowMs) {
        return normal && (targetUs <= 0L || deadlineMs <= 0L || nowMs >= deadlineMs
                || bufferedUs / Math.max(0.1f, speed) >= targetUs);
    }
    String summary(long now) {
        long open = stallAt < 0L ? 0L : Math.max(0L, now - stallAt);
        return "startupBuffers=" + startupBuffers + " realRebuffers=" + rebufferCount
                + " rebufferMs=" + (totalRebufferMs + open)
                + " longestRebufferMs=" + Math.max(longestRebufferMs, open)
                + " playedBeforeFirstRebufferMs=" + beforeFirstRebufferMs
                + " stalledBufferedMs=" + stalledBufferedMs
                + " recoveryTargetMs=" + recoveryTargetMs
                + " state=" + (stallAt >= 0L ? "starving" : protectedPlayback ? "recovering" : "healthy");
    }
}
