package com.webapp.crazyshit;

import android.graphics.drawable.Animatable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Main-thread animation budget for an idle, visible gallery; never owns image requests. */
final class GalleryGifPlayback {
    static final int MAX_RUNNING = 4;
    static final long RESUME_DELAY_MS = 250L;
    private final Set<Animatable> running = new LinkedHashSet<>();
    private long resumeAt;

    void defer(long now) {
        resumeAt = now + RESUME_DELAY_MS;
        stopAll();
    }

    void update(List<Animatable> visible, long now) {
        if (now < resumeAt) {
            stopAll();
            return;
        }
        Set<Animatable> desired = new LinkedHashSet<>();
        for (Animatable drawable : visible) {
            if (drawable != null) desired.add(drawable);
            if (desired.size() == MAX_RUNNING) break;
        }
        // Retire old slots before starting new slots.
        for (Animatable drawable : new LinkedHashSet<>(running))
            if (!desired.contains(drawable)) stop(drawable);
        for (Animatable drawable : desired) {
            running.add(drawable);
            if (!drawable.isRunning()) drawable.start();
        }
    }

    void stop(Animatable drawable) {
        if (running.remove(drawable) || drawable.isRunning()) drawable.stop();
    }

    void stopAll() {
        for (Animatable drawable : new LinkedHashSet<>(running)) stop(drawable);
    }
}
