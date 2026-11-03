package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {29, 35}, qualifiers = "w400dp-h800dp-mdpi")
public class OnlyFapGalleryLayoutTest {
    private org.robolectric.android.controller.ActivityController<Activity> controller;
    private Activity activity;
    private RecyclerView list;
    private BunkrGalleryAdapter adapter;
    private final float[] ratios = {.5f, 2f, 1f, .15f, 6f, .75f, 1.7f};

    @Before public void setup() {
        controller = Robolectric.buildActivity(Activity.class).setup();
        activity = controller.get();
        list = new RecyclerView(activity);
        list.setItemAnimator(null); // Same as NativeFeedBrowserActivity.createRecycler().
        list.setLayoutManager(new StaggeredGridLayoutManager(2, RecyclerView.VERTICAL));
        adapter = newAdapter();
        list.setAdapter(adapter);
        activity.setContentView(list);
        adapter.replace(media(80), false);
        layout();
    }
    @After public void teardown() {
        list.setAdapter(null);
        controller.pause().stop().destroy();
    }
    private BunkrGalleryAdapter newAdapter() {
        return new BunkrGalleryAdapter(activity, new BunkrGalleryAdapter.Listener() {
            public void onOpen(int position, NativeContentItem item, View anchor) { }
            public void onLongPress(NativeContentItem item, View anchor) { }
        }, true);
    }
    private List<NativeContentItem> media(int count) {
        List<NativeContentItem> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(item(i, ratios[i % ratios.length]));
        return result;
    }
    private NativeContentItem item(int id, float ratio) {
        return new NativeContentItem(id % 3 == 0 ? NativeContentItem.KIND_MEDIA : NativeContentItem.KIND_IMAGE,
                "Fixture " + id, "https://fixture.invalid/" + id, "", "", "", "", "OnlyFap", "Creator", 0, ratio);
    }
    private void layout() {
        list.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 400, 800);
        list.getViewTreeObserver().dispatchOnPreDraw();
    }
    private BunkrGalleryAdapter.Holder holder(int position) {
        return (BunkrGalleryAdapter.Holder) list.findViewHolderForAdapterPosition(position);
    }
    private void assertGeometry() {
        assertTrue(list.getChildCount() > 1);
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            NativeContentItem item = ((BunkrGalleryAdapter.Holder) list.getChildViewHolder(child)).boundItem;
            if (item.aspectRatioHint > 0) assertEquals(Math.round(child.getWidth() / item.aspectRatioHint), child.getHeight());
            RectF bounds = visualBounds(child);
            for (int j = 0; j < i; j++) assertFalse("Overlapping visual tiles: " + i + ", " + j,
                    RectF.intersects(bounds, visualBounds(list.getChildAt(j))));
        }
    }
    private RectF visualBounds(View child) {
        RectF bounds = new RectF(0, 0, child.getWidth(), child.getHeight());
        child.getMatrix().mapRect(bounds);
        bounds.offset(child.getLeft(), child.getTop());
        return bounds;
    }
    @Test public void mixedRatiosScrollDownAndBackWithoutStaleDimensions() {
        assertGeometry();
        for (int i = 0; i < 18; i++) { list.scrollBy(0, 650); layout(); assertGeometry(); }
        for (int i = 0; i < 18; i++) { list.scrollBy(0, -650); layout(); assertGeometry(); }
    }
    @Test public void sameHolderRebindsPortraitLandscapeSquareTallWideAndUnknown() {
        BunkrGalleryAdapter.Holder holder = adapter.onCreateViewHolder(list, 0);
        for (float ratio : new float[]{.5f, 2f, .5f, 1f, .15f, 6f, 0f}) {
            adapter.replace(Collections.singletonList(item(100, ratio)), false);
            adapter.onBindViewHolder(holder, 0);
            holder.itemView.measure(View.MeasureSpec.makeMeasureSpec(194, View.MeasureSpec.EXACTLY), 0);
            assertEquals(Math.round(194 / (ratio > 0 ? ratio : 1)), holder.itemView.getMeasuredHeight());
        }
        adapter.onViewRecycled(holder);
    }
    @Test public void delayedDimensionsRelayoutNeighbors() throws Exception {
        List<NativeContentItem> media = media(20);
        media.set(0, item(101, 0));
        adapter.replace(media, false);
        layout();
        BunkrGalleryAdapter.Holder holder = holder(0);
        assertNotNull(holder);
        java.lang.reflect.Method apply = BunkrGalleryAdapter.class.getDeclaredMethod("applyAspectRatio",
                BunkrGalleryAdapter.Holder.class, NativeContentItem.class, android.graphics.drawable.Drawable.class);
        apply.setAccessible(true);
        ColorDrawable resource = new ColorDrawable() {
            public int getIntrinsicWidth() { return 100; }
            public int getIntrinsicHeight() { return 800; }
        };
        apply.invoke(adapter, holder, holder.boundItem, resource);
        layout();
        assertEquals(holder.itemView.getWidth() * 8, holder.itemView.getHeight());
        assertGeometry();
    }
    @Test public void actualFavoriteMoveIsRetiredOnDetachBeforeHolderReuse() {
        adapter.setFavoriteUrls(Collections.singleton(adapter.snapshot().get(3).url));
        layout();
        BunkrGalleryAdapter.Holder moving = null;
        for (int i = 0; i < list.getChildCount(); i++) {
            BunkrGalleryAdapter.Holder candidate = (BunkrGalleryAdapter.Holder) list.getChildViewHolder(list.getChildAt(i));
            if (Math.abs(candidate.itemView.getTranslationX()) + Math.abs(candidate.itemView.getTranslationY()) > 1) {
                moving = candidate; break;
            }
        }
        assertNotNull("Fixture must exercise a real favorite move", moving);
        moving.itemView.animate().cancel();
        adapter.onViewDetachedFromWindow(moving);
        assertEquals(0, moving.itemView.getTranslationX(), .001f);
        assertEquals(0, moving.itemView.getTranslationY(), .001f);
        adapter.onViewAttachedToWindow(moving);
    }
    @Test public void favoriteSnapshotIsRetiredWhenDelayedDimensionsChange() throws Exception {
        List<NativeContentItem> media = media(20);
        media.set(0, item(101, 0));
        adapter.replace(media, false); layout();
        adapter.setFavoriteUrls(Collections.singleton(media.get(3).url));
        java.lang.reflect.Method apply = BunkrGalleryAdapter.class.getDeclaredMethod("applyAspectRatio",
                BunkrGalleryAdapter.Holder.class, NativeContentItem.class, android.graphics.drawable.Drawable.class);
        apply.setAccessible(true);
        BunkrGalleryAdapter.Holder first = (BunkrGalleryAdapter.Holder) list.getChildViewHolder(list.getChildAt(0));
        apply.invoke(adapter, first, first.boundItem, new ColorDrawable() {
            public int getIntrinsicWidth() { return 100; }
            public int getIntrinsicHeight() { return 800; }
        });
        layout();
        assertGeometry(); // No old pre-draw snapshot may translate the new layout.
    }
    @Test public void pendingMoveCannotAnimateReplacementOrDetachedAdapter() {
        adapter.setFavoriteUrls(Collections.singleton(adapter.snapshot().get(3).url));
        adapter.replace(adapter.normalSnapshot(), false); layout();
        assertGeometry();
        adapter.setFavoriteUrls(Collections.emptySet());
        list.setAdapter(null); list.setAdapter(adapter); layout();
        assertGeometry();
    }
    @Test public void recycledHolderMustNotRetainInterruptedFavoriteMove() {
        BunkrGalleryAdapter.Holder holder = holder(0);
        // A move animation can be interrupted while offscreen or during a rebind.
        holder.itemView.setTranslationX(12);
        holder.itemView.setTranslationY(-180);
        holder.itemView.animate().translationY(0).setDuration(240).start();
        holder.itemView.animate().cancel();
        adapter.onViewRecycled(holder);
        assertEquals(0, holder.itemView.getTranslationX(), .001f);
        assertEquals(0, holder.itemView.getTranslationY(), .001f);
    }
    @Test public void fullRebindMustNotRetainInterruptedMoveOrPinchScale() {
        BunkrGalleryAdapter.Holder holder = holder(0);
        holder.itemView.setTranslationY(-180);
        holder.itemView.setScaleX(1.2f);
        holder.itemView.setScaleY(1.2f);
        adapter.onBindViewHolder(holder, 0);
        assertEquals(0, holder.itemView.getTranslationY(), .001f);
        assertEquals(1, holder.itemView.getScaleX(), .001f);
        assertEquals(1, holder.itemView.getScaleY(), .001f);
        assertGeometry();
    }
    @Test public void favoritePayloadRetainsDimensionsWithoutRestartingBinding() {
        BunkrGalleryAdapter.Holder first = holder(0);
        NativeContentItem item = first.boundItem;
        int generation = first.bindGeneration;
        int height = first.itemView.getHeight();
        for (int i = 0; i < 8; i++) {
            adapter.setFavoriteUrls(i % 2 == 0 ? Collections.singleton(item.url) : Collections.emptySet());
            layout();
            assertSame(first, holder(0));
            assertEquals(generation, first.bindGeneration);
            assertEquals(height, first.itemView.getHeight());
            assertEquals(i % 2 == 0 ? View.VISIBLE : View.GONE, first.favorite.getVisibility());
            assertGeometry();
        }
    }
    @Test public void retainedAllPicturesVideosAdaptersKeepCorrectGeometry() {
        BunkrGalleryAdapter[] tabs = {adapter, newAdapter(), newAdapter()};
        List<NativeContentItem> all = media(40);
        for (int tab = 1; tab < 3; tab++) {
            List<NativeContentItem> filtered = new ArrayList<>();
            for (NativeContentItem item : all) if (tab == 1 ? item.isImage() : item.isVideo()) filtered.add(item);
            tabs[tab].replace(filtered, false);
        }
        for (int tab : new int[]{1, 2, 0, 1, 0}) {
            adapter.setPreviewsActive(false);
            adapter = tabs[tab]; list.setAdapter(adapter); adapter.setPreviewsActive(true);
            layout(); assertGeometry();
            list.scrollBy(0, 900); layout(); assertGeometry();
            list.scrollBy(0, -900); layout(); assertGeometry();
        }
    }
    @Test public void favoritesRepeatThenSwitchTabsAndReopen() {
        List<NativeContentItem> all = adapter.normalSnapshot();
        for (int i = 0; i < 8; i++) {
            adapter.setFavoriteUrls(Collections.singleton(all.get(3).url)); layout();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));
            assertGeometry();
            adapter.setFavoriteUrls(Collections.emptySet()); layout();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));
            assertGeometry();
        }
        for (boolean images : new boolean[]{true, false, true}) {
            List<NativeContentItem> tab = new ArrayList<>();
            for (NativeContentItem item : all) if (item.isImage() == images) tab.add(item);
            adapter.replace(tab, false); layout(); assertGeometry();
        }
        list.setAdapter(null);
        adapter = newAdapter();
        list.setAdapter(adapter);
        adapter.replace(all, false); layout(); assertGeometry();
    }
}
