package com.webapp.crazyshit;

/** Bounded viewport-based presentation budget. Network preparation remains independently limited. */
final class GalleryWorkload {
    private GalleryWorkload() { }
    static int columns(int value) { return Math.max(1, Math.min(8, value)); }

    static int revealCapacity(int columns, int visible, boolean constrained) {
        int normal = Math.min(48, Math.max(columns(columns) * 4, visible));
        return constrained ? Math.max(4, (normal + 1) / 2) : normal;
    }

    static long staggerMs(int columns, boolean constrained) {
        if (constrained) return 50L;
        return Math.max(25L, 50L - (columns(columns) - 1) * 8L);
    }

    static int estimatedScreenTiles(int columns, int width, int height) {
        int cols = columns(columns);
        if (width <= 0 || height <= 0) return Math.min(40, cols * 4);
        // Square placeholders provide a conservative estimate before real tile ratios are known.
        return Math.min(40, Math.max(cols * 2,
                (int) Math.ceil((double) height * cols / width) * cols));
    }

    static int preparationWindow(int columns, int estimatedVisible) {
        return Math.min(48, Math.max(6, estimatedVisible + 2 * columns(columns)));
    }

    static int preparationConcurrency(int columns, boolean constrained) {
        return constrained ? 3 : Math.min(6, Math.max(3, columns(columns) + 1));
    }

    static final class FramePressure {
        private long previousFrame;
        private long constrainedUntil;
        private int slowFrames;
        void sample(long nanos, long nowMs) {
            if (previousFrame != 0L) {
                long delta = nanos - previousFrame;
                if (delta > 32_000_000L) {
                    if (++slowFrames >= 3) { constrainedUntil = nowMs + 1500L; slowFrames = 0; }
                } else if (delta > 0L) slowFrames = 0;
            }
            previousFrame = nanos;
        }
        boolean constrained(long nowMs) { return nowMs < constrainedUntil; }
        void idle() { previousFrame = 0L; slowFrames = 0; }
    }
}
