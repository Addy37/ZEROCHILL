package com.webapp.crazyshit;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Real creator-gallery components revealed under ShitTok during the interactive forward swipe.
 *
 * Using the shared profile header and gallery adapter keeps the drag surface visually aligned with
 * the destination activity, so the final handoff does not jump between two different layouts.
 */
final class ShitTokCreatorSwipePreview extends FrameLayout {
    private final Activity activity;
    private final LinearLayout shell;
    private final FrameLayout profileSlot;
    private final TabLayout tabs;
    private final RecyclerView gallery;
    private final CreatorGallerySkeleton gallerySkeleton;
    private final ZeroChillLoadingView galleryLoading;
    private final BunkrGalleryAdapter adapter;
    private String currentCreator = "";
    private String currentSessionId = "";
    private int refreshGeneration;

    ShitTokCreatorSwipePreview(Activity activity) {
        super(activity);
        this.activity = activity;
        setBackgroundColor(Color.BLACK);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.BLACK);
        addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(6), dp(8), dp(6));
        top.setBackgroundColor(Color.BLACK);

        TextView back = text("‹", 34, Color.WHITE);
        back.setGravity(Gravity.CENTER);
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(52)));

        TextView heading = text("OnlyFap", 20, Color.WHITE);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        top.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView more = text("⋮", 28, Color.rgb(220, 220, 226));
        more.setGravity(Gravity.CENTER);
        top.addView(more, new LinearLayout.LayoutParams(dp(48), dp(52)));
        shell.addView(top, new LinearLayout.LayoutParams(-1, dp(64)));

        profileSlot = new FrameLayout(activity);
        shell.addView(profileSlot, new LinearLayout.LayoutParams(-1, dp(184)));

        tabs = new TabLayout(activity);
        tabs.setBackgroundColor(Color.BLACK);
        tabs.setSelectedTabIndicatorColor(UiPalette.PRIMARY);
        tabs.setTabTextColors(Color.rgb(174, 174, 182), UiPalette.PRIMARY);
        tabs.setTabMode(TabLayout.MODE_FIXED);
        tabs.setTabGravity(TabLayout.GRAVITY_FILL);
        shell.addView(tabs, new LinearLayout.LayoutParams(-1, dp(48)));

        tabs.addTab(tabs.newTab().setText("All"));
        tabs.addTab(tabs.newTab().setText("Pictures"));
        tabs.addTab(tabs.newTab().setText("Videos"));

        adapter = new BunkrGalleryAdapter(
                activity,
                new BunkrGalleryAdapter.Listener() {
                    @Override
                    public void onOpen(int position, NativeContentItem item) {
                    }

                    @Override
                    public void onLongPress(NativeContentItem item, View anchor) {
                    }
                },
                true
        );

        gallery = new RecyclerView(activity);
        gallery.setBackgroundColor(Color.BLACK);
        gallery.setClipToPadding(false);
        gallery.setPadding(0, dp(5), 0, dp(18));
        gallery.setItemAnimator(null);
        gallery.setAdapter(adapter);
        gallery.setLayoutManager(new StaggeredGridLayoutManager(
                creatorColumns(),
                StaggeredGridLayoutManager.VERTICAL
        ));
        gallery.setNestedScrollingEnabled(false);
        FrameLayout galleryHost = new FrameLayout(activity);
        galleryHost.addView(gallery, new FrameLayout.LayoutParams(-1, -1));
        gallerySkeleton = new CreatorGallerySkeleton(activity);
        galleryHost.addView(gallerySkeleton, new FrameLayout.LayoutParams(-1, -1));
        galleryLoading = new ZeroChillLoadingView(activity, "Loading gallery...", true);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(dp(160), dp(132));
        loadingParams.gravity = Gravity.CENTER;
        galleryHost.addView(galleryLoading, loadingParams);
        galleryLoading.setVisibility(View.GONE);
        shell.addView(galleryHost, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    void showCreator(String creator, String sessionId) {
        String cleanCreator = clean(creator);
        if (!cleanCreator.equals(currentCreator)) {
            currentCreator = cleanCreator;
            rebuildProfile();
        }
        currentSessionId = clean(sessionId);
        setVisibility(View.VISIBLE);
        refreshCreator(currentCreator, currentSessionId);
    }

    void refreshCreator(String creator, String sessionId) {
        String cleanCreator = clean(creator);
        if (!cleanCreator.isEmpty() && !cleanCreator.equals(currentCreator)) {
            currentCreator = cleanCreator;
            rebuildProfile();
        }
        String cleanSession = clean(sessionId);
        if (!cleanSession.isEmpty()) currentSessionId = cleanSession;

        BunkrGallerySessionStore.Snapshot snapshot =
                BunkrGallerySessionStore.snapshot(currentSessionId);
        if (snapshot != null && !snapshot.items.isEmpty()) {
            bindItems(snapshot.items);
            return;
        }

        adapter.replace(new ArrayList<>(), false);
        updateTabs(new ArrayList<>());
        gallerySkeleton.setVisibility(View.VISIBLE);
        galleryLoading.setVisibility(View.VISIBLE);
        int generation = ++refreshGeneration;
        scheduleRefresh(generation, 0);
    }

    private void rebuildProfile() {
        profileSlot.removeAllViews();
        if (currentCreator.isEmpty()) return;
        CreatorProfileHeader profile = new CreatorProfileHeader(
                activity,
                currentCreator,
                currentCreator,
                BunkrRepository.searchUrl(currentCreator)
        );
        profileSlot.addView(profile, new FrameLayout.LayoutParams(-1, -1));
    }

    private void scheduleRefresh(int generation, int attempt) {
        if (attempt >= 6) return;
        postDelayed(() -> {
            if (generation != refreshGeneration || getVisibility() != View.VISIBLE) return;
            String newest = ShitTokCreatorGalleryPreloader.sessionId(activity, currentCreator);
            if (!newest.isEmpty()) currentSessionId = newest;
            BunkrGallerySessionStore.Snapshot later =
                    BunkrGallerySessionStore.snapshot(currentSessionId);
            if (later != null && !later.items.isEmpty()) {
                bindItems(later.items);
                return;
            }
            scheduleRefresh(generation, attempt + 1);
        }, attempt == 0 ? 60L : 100L);
    }

    private void bindItems(List<NativeContentItem> items) {
        List<NativeContentItem> visible = adapter.snapshot();
        boolean same = visible.size() == items.size();
        for (int i = 0; same && i < items.size(); i++) {
            NativeContentItem old = visible.get(i);
            NativeContentItem next = items.get(i);
            same = old != null && next != null && old.url != null
                    && old.url.equals(next.url);
        }
        if (!same) adapter.replace(items, false);
        gallerySkeleton.setVisibility(View.GONE);
        galleryLoading.setVisibility(View.GONE);
        updateTabs(items);
        if (!same && gallery.getLayoutManager() instanceof StaggeredGridLayoutManager) {
            ((StaggeredGridLayoutManager) gallery.getLayoutManager())
                    .scrollToPositionWithOffset(0, 0);
        }
    }

    private void updateTabs(List<NativeContentItem> items) {
        int all = 0;
        int pictures = 0;
        int videos = 0;
        if (items != null) {
            for (NativeContentItem item : items) {
                if (item == null) continue;
                all++;
                if (item.isImage()) pictures++;
                if (item.isVideo()) videos++;
            }
        }
        setTab(0, "All", all);
        setTab(1, "Pictures", pictures);
        setTab(2, "Videos", videos);
    }

    private void setTab(int index, String label, int count) {
        TabLayout.Tab tab = tabs.getTabAt(index);
        if (tab != null) tab.setText(count > 0 ? label + "  " + count : label);
    }

    private int creatorColumns() {
        Configuration config = getResources().getConfiguration();
        String size = config.screenWidthDp >= 600 ? "tablet" : "phone";
        String orientation = config.orientation == Configuration.ORIENTATION_LANDSCAPE
                ? "wide"
                : "tall";
        int fallback = config.orientation == Configuration.ORIENTATION_LANDSCAPE
                ? (config.screenWidthDp >= 900 ? 7 : 5)
                : (config.screenWidthDp >= 600 ? 4 : 2);
        int minimum = config.screenWidthDp >= 600 ? 3 : 2;
        int maximum = config.screenWidthDp >= 900 ? 8
                : config.screenWidthDp >= 600 ? 7
                : config.orientation == Configuration.ORIENTATION_LANDSCAPE ? 7 : 5;
        int saved = activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .getInt("creator_gallery_columns_" + size + "_" + orientation, fallback);
        return Math.max(minimum, Math.min(maximum, saved));
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
