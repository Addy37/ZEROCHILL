package com.webapp.crazyshit;

import android.view.View;
import java.util.HashSet;
import java.util.Set;

/** Reveal results on screen once per query, without delaying data or replaying refreshes. */
final class SearchResultFades {
    private final Set<String> revealed = new HashSet<>();
    private long waveStarted;
    private int waveIndex;

    void reset() { revealed.clear(); waveIndex = 0; }

    void bind(View view, String key) {
        // RecyclerView can bind/prefetch before attachment. Save the reveal for the screen.
        if (view.isAttachedToWindow()) show(view, key);
        else recycle(view);
    }

    void show(View view, String key) {
        recycle(view);
        if (!revealed.add(key) || !ZeroChillMotion.animationsEnabled(view.getContext())) {
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        if (waveIndex == 0 || now - waveStarted > 80L) {
            waveStarted = now;
            waveIndex = 0;
        }
        long delay = Math.min(3, waveIndex++) * 20L;
        view.setAlpha(.35f);
        view.setTranslationY(8f * view.getResources().getDisplayMetrics().density);
        view.animate().alpha(1f).translationY(0f).setStartDelay(delay)
                .setDuration(ZeroChillMotion.STANDARD_MS).setInterpolator(ZeroChillMotion.EASE_OUT).start();
    }

    static void recycle(View view) {
        view.animate().cancel();
        view.animate().setStartDelay(0L);
        view.setAlpha(1f);
        view.setTranslationY(0f);
    }
}
