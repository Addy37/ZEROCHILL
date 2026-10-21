package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Exercise the actual View All adapter, rather than CreatorListAdapter. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, shadows = {FavoriteCreatorsScrollTest.Preloads.class, FavoriteArtworkScheduleShadow.class})
public class FavoriteCreatorsScrollTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @Implements(value = CreatorGalleryPreloader.class, isInAndroidSdk = false)
    public static class Preloads {
        static int warmCalls;
        @Implementation protected static void warm(Context context, CreatorGallerySpec spec, int priority) {
            warmCalls++;
        }
    }

    @Before public void reset() {
        for (String prefs : Arrays.asList("creator_favorites", CreatorCatalog.PREFS,
                "manual_creator_merges_v1", "creator_avatar_overrides_v1", "zerochill_update_inbox_v1",
                "onlyfap_hot_gallery_sessions"))
            context.getSharedPreferences(prefs, 0).edit().clear().commit();
        ManualCreatorMergeStore.clearCacheForTest();
        CreatorAvatarOverrideStore.clearCacheForTest();
        Map<?, ?> sessions = ReflectionHelpers.getStaticField(BunkrGallerySessionStore.class, "SESSIONS");
        sessions.clear();
        Preloads.warmCalls = 0;
    }

    private void seedFavorites(int size) {
        List<NativeContentItem> creators = new ArrayList<>();
        HashSet<String> keys = new HashSet<>();
        for (int i = 0; i < size; i++) {
            String name = String.format(java.util.Locale.US, "Testcreator%03d", i);
            creators.add(new NativeContentItem(NativeContentItem.KIND_CREATOR, name,
                    "https://fapello.com/" + name.toLowerCase(java.util.Locale.US) + "/",
                    "", "", "", "", "", name));
            keys.add(name.toLowerCase(java.util.Locale.US));
        }
        context.getSharedPreferences("creator_favorites", 0).edit().putStringSet("creators", keys).commit();
        CreatorCatalog.remember(context, creators);
    }

    private RecyclerView grid(CreatorsActivity activity) {
        return ReflectionHelpers.getField(activity, "recycler");
    }

    private void layout(RecyclerView grid) {
        grid.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        grid.layout(0, 0, 1080, 800);
    }

    @Test public void bindingAndAttachingMergedCardsNeverWarmsGalleries() {
        seedFavorites(48);
        assertTrue(ManualCreatorMergeStore.merge(context,
                Collections.singleton("testcreator000"), Collections.singleton("testcreator001"),
                "testcreator000", "testcreator000"));
        try (ActivityController<CreatorsActivity> controller = Robolectric.buildActivity(CreatorsActivity.class).setup()) {
            RecyclerView grid = grid(controller.get());
            layout(grid);
            RecyclerView.Adapter adapter = grid.getAdapter();
            assertEquals(47, adapter.getItemCount());
            for (int position = 0; position < adapter.getItemCount(); position++) {
                RecyclerView.ViewHolder holder = adapter.createViewHolder(grid, 0);
                adapter.bindViewHolder(holder, position);
                adapter.onViewAttachedToWindow(holder);
                adapter.onViewDetachedFromWindow(holder);
                adapter.onViewRecycled(holder);
            }
            assertEquals(0, Preloads.warmCalls);
        }
    }

    @Test public void prefetchIsTwoRowsAndFollowsReversalsAndListEdges() {
        assertArrayEquals(new int[]{12, 13, 14, 15, 16, 17},
                CreatorsActivity.avatarPrefetchPositions(3, 11, 48, 3, 1));
        assertArrayEquals(new int[]{8, 7, 6, 5, 4, 3},
                CreatorsActivity.avatarPrefetchPositions(9, 17, 48, 3, -1));
        assertArrayEquals(new int[]{46, 47},
                CreatorsActivity.avatarPrefetchPositions(36, 45, 48, 3, 1));
        assertArrayEquals(new int[0], CreatorsActivity.avatarPrefetchPositions(0, 8, 48, 3, -1));
        assertArrayEquals(new int[0], CreatorsActivity.avatarPrefetchPositions(-1, -1, 48, 3, 1));
    }

    @Test public void avatarDecodeRetainsAspectRatioAndDoesNotUpscaleSmallSources() {
        assertEquals(0.086f, CreatorsActivity.AVATAR_DOWNSAMPLE.getScaleFactor(2000, 1000, 86, 86), 0.001f);
        assertEquals(1f, CreatorsActivity.AVATAR_DOWNSAMPLE.getScaleFactor(40, 60, 86, 86), 0.001f);
    }

    @Test public void inboxChangesRefreshBadgesWithoutWaitingForAnotherVisit() throws Exception {
        seedFavorites(12);
        try (ActivityController<CreatorsActivity> controller = Robolectric.buildActivity(CreatorsActivity.class).setup()) {
            RecyclerView grid = grid(controller.get());
            layout(grid);
            RecyclerView.Adapter adapter = grid.getAdapter();
            RecyclerView.ViewHolder holder = adapter.createViewHolder(grid, 0);
            adapter.bindViewHolder(holder, 0);
            List<?> cards = ReflectionHelpers.getField(adapter, "cards");
            assertEquals(0, (int) ReflectionHelpers.getField(cards.get(0), "unread"));
            writeUnread("Testcreator000", 7);
            shadowOf(Looper.getMainLooper()).idle();
            adapter.onBindViewHolder(holder, 0, Collections.singletonList("badges"));
            assertEquals(7, (int) ReflectionHelpers.getField(cards.get(0), "unread"));
            assertTrue(holder.itemView.getContentDescription().toString().contains("7 new items"));
            UpdateInboxStore.markCreatorRead(context, Arrays.asList("Testcreator000"));
            shadowOf(Looper.getMainLooper()).idle();
            adapter.onBindViewHolder(holder, 0, Collections.singletonList("badges"));
            assertEquals(0, (int) ReflectionHelpers.getField(cards.get(0), "unread"));
        }
    }

    @Test public void tapPassesPersistedSessionWithoutRestoringOnTheListThread() throws Exception {
        seedFavorites(1);
        String query = "Testcreator000";
        String id = BunkrGallerySessionStore.createCreator(query, "", query);
        NativeContentItem item = new NativeContentItem(NativeContentItem.KIND_IMAGE, "Cached photo",
                "https://fapello.com/testcreator000/1/", "", "", "", "", "");
        BunkrGallerySessionStore.recordCreatorBatch(context, id, Collections.singletonList(item), false,
                new JSONObject().put("query", query));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!id.equals(context.getSharedPreferences("onlyfap_hot_gallery_sessions", 0)
                .getString("creator:testcreator000", "")) && System.nanoTime() < deadline) Thread.sleep(10);
        Map<?, ?> sessions = ReflectionHelpers.getStaticField(BunkrGallerySessionStore.class, "SESSIONS");
        sessions.clear();
        try (ActivityController<CreatorsActivity> controller = Robolectric.buildActivity(CreatorsActivity.class).setup()) {
            CreatorsActivity activity = controller.get();
            RecyclerView grid = grid(activity);
            RecyclerView.Adapter adapter = grid.getAdapter();
            RecyclerView.ViewHolder holder = adapter.createViewHolder(grid, 0);
            adapter.bindViewHolder(holder, 0);
            holder.itemView.performClick();
            Intent launched = shadowOf(activity).getNextStartedActivity();
            assertEquals(id, launched.getStringExtra(NativeFeedBrowserActivity.EXTRA_CREATOR_GALLERY_SESSION));
            assertNull(BunkrGallerySessionStore.snapshot(id));
            assertEquals(0, Preloads.warmCalls);
            assertNotNull(BunkrGallerySessionStore.restore(context, id));
        }
    }

    @Test public void customAvatarUsesZoomSizedOriginalAndCircularContainer() throws Exception {
        seedFavorites(1);
        assertTrue(CreatorAvatarOverrideStore.save(context, Collections.singleton("testcreator000"),
                "https://cdn.example.com/custom.jpg", "https://fapello.com/testcreator000/", 0.27f, 0.72f, 2.4f));
        try (ActivityController<CreatorsActivity> controller = Robolectric.buildActivity(CreatorsActivity.class).setup()) {
            RecyclerView grid = grid(controller.get());
            Object adapter = grid.getAdapter();
            List<?> cards = ReflectionHelpers.getField(adapter, "cards");
            Object card = cards.get(0);
            java.lang.reflect.Method requestMethod = adapter.getClass().getDeclaredMethod("avatarRequest", card.getClass());
            requestMethod.setAccessible(true);
            com.bumptech.glide.RequestBuilder<?> request = (com.bumptech.glide.RequestBuilder<?>) requestMethod.invoke(adapter, card);
            int expected = Math.round(BrowseUi.dp(controller.get(), 56) * 2.4f);
            assertEquals(expected, request.getOverrideWidth());
            assertEquals(expected, request.getOverrideHeight());
            assertFalse(request.isTransformationAllowed());
            CreatorAvatarOverrideStore.Override crop = ReflectionHelpers.getField(card, "avatarOverride");
            assertEquals(0.27f, crop.focusX, 0.001f);
            assertEquals(0.72f, crop.focusY, 0.001f);
            RecyclerView.ViewHolder holder = grid.getAdapter().createViewHolder(grid, 0);
            CreatorAvatarImageView avatar = ReflectionHelpers.getField(holder, "avatar");
            assertTrue(((View) avatar.getParent()).getClipToOutline());
        }
    }

    @Test public void unreadAliasesDoNotCountTheSameCreatorTwice() throws Exception {
        seedFavorites(1);
        writeUnread("Testcreator000", 3);
        Map<String, Integer> counts = UpdateInboxStore.unreadCreatorContentCounts(context);
        assertEquals(3, UpdateInboxStore.unreadCreatorContentCount(counts,
                Arrays.asList("Testcreator000", "testcreator000", "Testcreator000")));
        UpdateInboxStore.markCreatorRead(context, Collections.singletonList("Testcreator000"));
        assertEquals(3, UpdateInboxStore.unreadCreatorContentCount(counts,
                Collections.singletonList("Testcreator000")));
        assertEquals(0, UpdateInboxStore.unreadCreatorContentCount(
                UpdateInboxStore.unreadCreatorContentCounts(context), Collections.singletonList("Testcreator000")));
    }

    private void writeUnread(String name, int count) throws Exception {
        UpdateInboxStore.Entry entry = new UpdateInboxStore.Entry();
        entry.id = "test-unread";
        entry.fingerprint = "test-unread";
        entry.category = UpdateInboxStore.CATEGORY_ONLYFAP;
        entry.creatorName = name;
        entry.count = count;
        context.getSharedPreferences("zerochill_update_inbox_v1", 0).edit()
                .putString("entries", new org.json.JSONArray().put(entry.encode()).toString()).commit();
    }
}
