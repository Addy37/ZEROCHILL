package com.webapp.crazyshit;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adaptive saved-media gallery. Landscape cards fill a 16:9 row; portrait items
 * share a row, without changing item order or recreating the original click targets.
 * Changes in decoded artwork dimensions reflow existing views once per UI frame.
 */
final class LibraryMixedMediaGrid extends LinearLayout {
    private static final class Entry {
        final View view;
        final String key;
        boolean portrait;
        Entry(View view, String key, boolean portrait) {
            this.view = view;
            this.key = key;
            this.portrait = portrait;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private boolean reflowPending;
    private final Runnable reflowTask = () -> {
        reflowPending = false;
        reflow();
    };

    LibraryMixedMediaGrid(Context context) {
        super(context);
        setOrientation(VERTICAL);
    }

    void setItems(List<View> cards, List<String> keys, Map<String, Boolean> orientations) {
        if (cards.size() != keys.size()) throw new IllegalArgumentException("Mismatched gallery entries");
        entries.clear();
        for (int i = 0; i < cards.size(); i++) {
            String key = keys.get(i);
            boolean portrait = Boolean.TRUE.equals(orientations.get(key));
            entries.add(new Entry(cards.get(i), key, portrait));
        }
        reflow();
    }

    void updateOrientation(String key, boolean portrait) {
        boolean changed = false;
        for (Entry entry : entries) {
            if (!entry.key.equals(key) || entry.portrait == portrait) continue;
            entry.portrait = portrait;
            changed = true;
        }
        if (changed && !reflowPending) {
            reflowPending = true;
            postOnAnimation(reflowTask);
        }
    }

    void dispose() {
        removeCallbacks(reflowTask);
        reflowPending = false;
        entries.clear();
    }

    private void reflow() {
        // Move existing views. Do not re-run Glide, thumbnail lookups, or media actions.
        for (Entry entry : entries) {
            if (entry.view.getParent() instanceof ViewGroup)
                ((ViewGroup) entry.view.getParent()).removeView(entry.view);
        }
        removeAllViews();
        LinearLayout pendingPortrait = null;
        for (Entry entry : entries) {
            LibraryAspectMediaCard mediaCard = findAspectCard(entry.view);
            if (mediaCard != null) mediaCard.setPortrait(entry.portrait);
            if (entry.portrait) {
                if (pendingPortrait == null) {
                    pendingPortrait = row();
                    addView(pendingPortrait, rowParams());
                }
                LinearLayout.LayoutParams params = new LayoutParams(0, -2, 1f);
                params.setMargins(dp(4), 0, dp(4), 0);
                pendingPortrait.addView(entry.view, params);
                if (pendingPortrait.getChildCount() >= 2) pendingPortrait = null;
            } else {
                pendingPortrait = null;
                LinearLayout fullRow = row();
                addView(fullRow, rowParams());
                LinearLayout.LayoutParams params = new LayoutParams(-1, -2);
                params.setMargins(dp(4), 0, dp(4), 0);
                fullRow.addView(entry.view, params);
            }
        }
        // A single portrait keeps half width and the next item retains its order.
        for (int i = 0; i < getChildCount(); i++) {
            LinearLayout row = (LinearLayout) getChildAt(i);
            if (row.getChildCount() == 1 && row.getChildAt(0).getLayoutParams().width == 0) {
                View spacer = new View(getContext());
                row.addView(spacer, new LayoutParams(0, 1, 1f));
            }
        }
    }

    private LibraryAspectMediaCard findAspectCard(View wrapper) {
        if (!(wrapper instanceof ViewGroup) || ((ViewGroup) wrapper).getChildCount() == 0) return null;
        View child = ((ViewGroup) wrapper).getChildAt(0);
        return child instanceof LibraryAspectMediaCard ? (LibraryAspectMediaCard) child : null;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.TOP);
        return row;
    }

    private LayoutParams rowParams() {
        LayoutParams params = new LayoutParams(-1, -2);
        params.setMargins(0, dp(5), 0, dp(9));
        return params;
    }

    private int dp(int value) { return BrowseUi.dp(getContext(), value); }
}
