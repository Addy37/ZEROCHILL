package com.webapp.crazyshit;

import android.view.View;
import java.util.HashSet;
import java.util.Set;

/** Fade a result only on its first presentation in a query, never on metadata refresh. */
final class SearchResultFades {
    private final Set<String> revealed = new HashSet<>();
    void reset() { revealed.clear(); }
    void show(View view, String key) {
        view.animate().cancel();
        if (!revealed.add(key) || !ZeroChillMotion.animationsEnabled(view.getContext())) {
            view.setAlpha(1f);
            return;
        }
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(180L).setInterpolator(ZeroChillMotion.EASE_OUT).start();
    }
    static void recycle(View view) { view.animate().cancel(); view.setAlpha(1f); }
}
