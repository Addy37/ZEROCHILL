package com.webapp.crazyshit;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;

/** Bounded Library previews, separated by actual thumbnail orientation. No IO or source guesses. */
final class LibraryMediaRows extends LinearLayout {
    private final LinearLayout portraits, landscape;
    private final HorizontalScrollView portraitScroll, landscapeScroll;
    private final List<Entry> entries = new ArrayList<>();

    LibraryMediaRows(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setClipChildren(false);
        portraitScroll = scroll("Portrait thumbnails");
        portraits = (LinearLayout) portraitScroll.getChildAt(0);
        landscapeScroll = scroll("Landscape thumbnails");
        landscape = (LinearLayout) landscapeScroll.getChildAt(0);
        refreshVisibility();
    }

    private HorizontalScrollView scroll(String description) {
        HorizontalScrollView scroll = new HorizontalScrollView(getContext());
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(4), 0, dp(4), 0);
        scroll.setContentDescription(description);
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        scroll.addView(row, new HorizontalScrollView.LayoutParams(-2, -2));
        addView(scroll, new LayoutParams(-1, -2));
        return scroll;
    }

    void addCard(View card, String key, boolean portrait) {
        Entry entry = new Entry(card, key, portrait);
        entries.add(entry);
        place(entry);
        refreshVisibility();
    }

    void setPortrait(String key, boolean portrait) {
        for (Entry entry : entries) {
            if (!entry.key.equals(key) || entry.portrait == portrait) continue;
            entry.portrait = portrait;
            place(entry);
        }
        refreshVisibility();
    }

    private void place(Entry entry) {
        if (entry.card.getParent() instanceof ViewGroup) ((ViewGroup) entry.card.getParent()).removeView(entry.card);
        LinearLayout row = entry.portrait ? portraits : landscape;
        int index = 0;
        for (Entry other : entries) {
            if (other == entry) break;
            if (other.portrait == entry.portrait) index++;
        }
        LayoutParams params = new LayoutParams(dp(entry.portrait ? 120 : 196), dp(entry.portrait ? 213 : 110));
        params.setMarginEnd(dp(10));
        row.addView(entry.card, index, params);
    }

    private void refreshVisibility() {
        portraitScroll.setVisibility(portraits.getChildCount() == 0 ? GONE : VISIBLE);
        landscapeScroll.setVisibility(landscape.getChildCount() == 0 ? GONE : VISIBLE);
        LayoutParams params = (LayoutParams) landscapeScroll.getLayoutParams();
        params.topMargin = portraits.getChildCount() > 0 && landscape.getChildCount() > 0 ? dp(12) : 0;
        landscapeScroll.setLayoutParams(params);
    }

    int[] scrollPositions() { return new int[]{portraitScroll.getScrollX(), landscapeScroll.getScrollX()}; }
    void restoreScroll(int[] positions) {
        portraitScroll.scrollTo(positions[0], 0);
        landscapeScroll.scrollTo(positions[1], 0);
    }
    private static final class Entry {
        final View card; final String key; boolean portrait;
        Entry(View card, String key, boolean portrait) { this.card = card; this.key = key; this.portrait = portrait; }
    }
    private int dp(int value) { return BrowseUi.dp(getContext(), value); }
}
