package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.widget.LinearLayout;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, shadows = CreatorNotificationsTest.OfflineImages.class)
public class CreatorNotificationsTest {
    @org.robolectric.annotation.Implements(value = com.bumptech.glide.load.data.HttpUrlFetcher.class,
            isInAndroidSdk = false)
    public static class OfflineImages {
        @org.robolectric.annotation.Implementation
        protected void loadData(com.bumptech.glide.Priority priority,
                com.bumptech.glide.load.data.DataFetcher.DataCallback<? super java.io.InputStream> callback) {
            byte[] png = java.util.Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aT7sAAAAASUVORK5CYII=");
            callback.onDataReady(new java.io.ByteArrayInputStream(png));
        }
    }

    private Application app;
    @Before public void clear() {
        app = RuntimeEnvironment.getApplication();
        for (String prefs : Arrays.asList("zerochill_update_inbox_v1", "creator_favorites",
                "creator_catalog_v1", "manual_creator_merges_v1", "creator_avatar_overrides_v1"))
            app.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().clear().commit();
        ManualCreatorMergeStore.clearCacheForTest();
        CreatorAvatarOverrideStore.clearCacheForTest();
        favorite("Creator One");
    }
    private void favorite(String name) {
        CreatorFavoriteStore.toggle(app, new NativeContentItem(NativeContentItem.KIND_CREATOR,
                name, "https://fapello.com/" + name.toLowerCase().replace(' ', '-') + "/",
                "https://img.example/" + name + ".jpg", "", "", "", "Fapello", name));
    }
    private NativeContentItem item(String creator, int id) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, "Video " + id,
                "https://fapello.com/video/" + creator.toLowerCase().replace(' ', '-') + "/" + id + "/",
                "https://img.example/" + id + ".jpg", "", creator, "", "Fapello");
    }
    private void record(NativeContentItem... items) {
        UpdateInboxStore.record(app, Collections.singletonList(
                new NotificationCoordinator.SourceAlert("fapello", "Fapello", Arrays.asList(items))));
    }
    private UpdateInboxStore.Entry group() { return UpdateInboxStore.all(app).get(0); }

    @Test public void oneItemIsOneCollapsedGroupWithExactThumbnail() {
        NativeContentItem media = item("Creator One", 101);
        record(media);
        assertEquals(1, UpdateInboxStore.all(app).size());
        assertEquals(1, group().children.size());
        assertEquals("1 new item", group().subtitle);
        assertEquals(media.imageUrl, group().children.get(0).item.imageUrl);
        assertEquals(1, new CreatorNotificationRows().replace(UpdateInboxStore.all(app)).size());
    }

    @Test public void fourItemsExpansionReadOneReexpandAndMarkAllPreserveOtherChildren() {
        record(item("Creator One", 101), item("Creator One", 102), item("Creator One", 103), item("Creator One", 104));
        CreatorNotificationRows rows = new CreatorNotificationRows();
        assertEquals(1, rows.replace(UpdateInboxStore.all(app)).size());
        assertEquals("4 new items", group().subtitle);
        List<UpdateInboxStore.Entry> expanded = rows.toggle(group());
        assertEquals(5, expanded.size());
        assertEquals(4, group().unreadItems());
        assertEquals("https://img.example/101.jpg", expanded.get(1).firstItem().imageUrl);
        UpdateInboxStore.markCreatorItemRead(app, expanded.get(1).creatorItem.id);
        assertEquals("3 new items", group().subtitle);
        assertEquals(3, UpdateInboxStore.unreadCreatorContentCount(app, Collections.singletonList("Creator One")));
        assertTrue(group().children.get(0).read);
        for (int i = 1; i < 4; i++) assertFalse(group().children.get(i).read);
        rows.replace(UpdateInboxStore.all(app));
        assertEquals(1, rows.toggle(group()).size());
        assertEquals(5, rows.toggle(group()).size());
        UpdateInboxStore.markAllRead(app);
        assertEquals(0, group().unreadItems());
        assertTrue(group().read);
        assertEquals(0, UpdateInboxStore.unreadCreatorContentCount(app, Collections.singletonList("Creator One")));
        assertEquals(1, UpdateInboxStore.all(app).size()); // history retained
    }

    @Test public void laterBatchesMergeReadPostsWithoutResurrectingReadState() {
        record(item("Creator One", 101), item("Creator One", 102));
        UpdateInboxStore.markCreatorItemRead(app, group().children.get(0).id);
        record(item("Creator One", 101));
        assertEquals(2, group().children.size());
        assertEquals(1, group().unreadItems());
        record(item("Creator One", 103));
        assertEquals(1, UpdateInboxStore.all(app).size());
        assertEquals(3, group().children.size());
        assertEquals(2, group().unreadItems());
        assertEquals("https://img.example/103.jpg", group().firstItem().imageUrl);
    }

    @Test public void sameBatchRouteVariantsDeduplicateAndUnrelatedSourceIdsStaySeparate() {
        NativeContentItem original = item("Creator One", 32318226);
        NativeContentItem variant = new NativeContentItem(NativeContentItem.KIND_MEDIA, "OnlyFap video",
                "https://fapello.com/video/new/32318226/", original.imageUrl, "", "Creator One", "", "Fapello");
        record(original, variant);
        assertEquals(1, group().children.size());
        NativeContentItem unrelated = new NativeContentItem(NativeContentItem.KIND_MEDIA, "Video",
                "https://other.example/32318226/", "", "", "Creator One", "", "OnlyHaven");
        UpdateInboxStore.record(app, Collections.singletonList(new NotificationCoordinator.SourceAlert(
                "onlyhaven", "OnlyHaven", Collections.singletonList(unrelated), "Creator One")));
        assertEquals(2, group().children.size());
    }

    @Test public void fapelloDirectMediaAndRoutesMatchWithoutCrossSourceNumericMatch() {
        NativeContentItem direct = new NativeContentItem(NativeContentItem.KIND_MEDIA, "Video",
                "https://video.fapello.com/content/c/r/creator-one/creator-one_32318226.mp4",
                "https://fapello.com/content/c/r/creator-one/creator-one_32318226_300px.jpg", "", "", "");
        assertTrue(CreatorUpdateIdentity.matches(direct, "https://fapello.com/video/new/32318226/"));
        assertEquals(CreatorUpdateIdentity.key("fapello", direct),
                CreatorUpdateIdentity.key("fapello", item("Creator One", 32318226)));
        assertFalse(CreatorUpdateIdentity.matches(new NativeContentItem(NativeContentItem.KIND_MEDIA,
                "Video", "https://other.example/video/32318226/", "https://other.example/creator_32318226.jpg",
                "", "", ""), "https://fapello.com/video/new/32318226/"));
        assertNotEquals(CreatorUpdateIdentity.canonical("https://other.example/media?id=1"),
                CreatorUpdateIdentity.canonical("https://other.example/media?id=2"));
        assertEquals(item("Creator One", 32318226).url,
                NativeFeedBrowserActivity.prioritizeNotificationItems(Arrays.asList(item("Creator One", 100),
                        item("Creator One", 32318226)), Collections.singletonList("https://fapello.com/video/new/32318226/")).get(0).url);
    }

    @Test public void mergedAliasesUsePrimaryDisplayAndOneCountWithUnderlyingRoutes() {
        favorite("Creator Two");
        LinkedHashSet<String> one = new LinkedHashSet<>(Collections.singletonList("creator one"));
        LinkedHashSet<String> two = new LinkedHashSet<>(Collections.singletonList("creator two"));
        assertTrue(ManualCreatorMergeStore.merge(app, one, two, "creator two", "creator one"));
        record(item("Creator One", 101), item("Creator Two", 102), item("Creator Two", 101));
        assertEquals(1, UpdateInboxStore.all(app).size());
        assertEquals("Creator Two", group().title);
        assertEquals(2, group().children.size());
        assertEquals("https://img.example/Creator One.jpg", group().avatarUrl);
        assertEquals(2, UpdateInboxStore.unreadCreatorContentCount(app, Arrays.asList("Creator One", "Creator Two")));
        UpdateInboxStore.markCreatorItemRead(app, group().children.get(0).id);
        assertEquals(1, UpdateInboxStore.unreadCreatorContentCount(app, Collections.singletonList("Creator One")));
        assertEquals(1, UpdateInboxStore.unreadCreatorContentCount(app, Collections.singletonList("Creator Two")));
    }

    @Test public void oldSinglePreviewSnapshotsRecoverUrlsAndReadStateWithoutLosingHistory() throws Exception {
        NativeContentItem first = item("Creator One", 101);
        JSONObject old = new JSONObject().put("id", "old").put("fingerprint", "old")
                .put("category", "onlyfap").put("creatorName", "Creator One")
                .put("title", "Creator One").put("count", 4).put("read", false)
                .put("items", ContentItemCodec.encodeList(Collections.singletonList(first), 1))
                .put("freshUrls", new JSONArray(Arrays.asList(first.url, item("Creator One", 102).url,
                        item("Creator One", 103).url, item("Creator One", 104).url)));
        app.getSharedPreferences("zerochill_update_inbox_v1", 0).edit()
                .putString("entries", new JSONArray().put(old).toString()).commit();
        assertEquals(4, group().children.size());
        assertEquals(first.imageUrl, group().children.get(0).item.imageUrl);
        assertEquals("", group().children.get(1).item.imageUrl); // old data never retained these previews
        UpdateInboxStore.markCreatorItemRead(app, group().children.get(1).id);
        assertEquals(3, group().unreadItems());
        UpdateInboxStore.markAllRead(app);
        assertTrue(group().read);
        assertEquals("old", group().id);
    }

    @Test public void groupDeletePreventsRepeatedChildrenButAllowsNewPosts() {
        record(item("Creator One", 101), item("Creator One", 102));
        assertTrue(UpdateInboxStore.delete(app, group().id, ""));
        record(item("Creator One", 101));
        assertTrue(UpdateInboxStore.all(app).isEmpty());
        record(item("Creator One", 103));
        assertEquals(1, group().children.size());
    }

    @Test public void actualInboxExpansionAndChildTapDoNotConsumeSiblingsAndSurviveRecreation() throws Exception {
        record(item("Creator One", 101), item("Creator One", 102), item("Creator One", 103), item("Creator One", 104));
        ActivityController<UpdateInboxActivity> controller = Robolectric.buildActivity(UpdateInboxActivity.class).setup();
        try {
            UpdateInboxActivity activity = controller.get();
            NotificationInboxTestIdle.await(activity);
            RecyclerView recycler = (RecyclerView) field(activity, "recycler");
            RecyclerView.Adapter adapter = recycler.getAdapter();
            assertEquals(1, adapter.getItemCount());
            RecyclerView.ViewHolder holder = adapter.createViewHolder(recycler, 0);
            adapter.bindViewHolder(holder, 0);
            LinearLayout row = (LinearLayout) ((LinearLayout) holder.itemView).getChildAt(1);
            LinearLayout labels = (LinearLayout) row.getChildAt(1);
            LinearLayout actions = (LinearLayout) labels.getChildAt(2);
            assertTrue(actions.getChildAt(0).performClick());
            NotificationInboxTestIdle.await(activity);
            assertEquals(5, adapter.getItemCount());
            assertEquals(4, group().unreadItems());
            RecyclerView.ViewHolder child = adapter.createViewHolder(recycler, adapter.getItemViewType(1));
            adapter.bindViewHolder(child, 1);
            child.itemView.performClick();
            Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
            assertEquals(BunkrGalleryActivity.class.getName(), intent.getComponent().getClassName());
            assertEquals(item("Creator One", 101).url, intent.getStringExtra(BunkrGalleryActivity.EXTRA_INITIAL_URL));
            assertEquals(3, group().unreadItems());
            Intent fallback = intent.getParcelableExtra(BunkrGalleryActivity.EXTRA_NOTIFICATION_GALLERY_FALLBACK);
            assertEquals(Collections.singletonList(item("Creator One", 101).url),
                    fallback.getStringArrayListExtra(NativeFeedBrowserActivity.EXTRA_NOTIFICATION_FRESH_URLS));
            assertEquals(1, BunkrGallerySessionStore.snapshot(intent.getStringExtra(BunkrGalleryActivity.EXTRA_SESSION_ID)).items.size());
            controller.recreate();
            NotificationInboxTestIdle.await(controller.get());
            assertEquals(5, ((RecyclerView) field(controller.get(), "recycler")).getAdapter().getItemCount());
            assertEquals(3, group().unreadItems());
        } finally { controller.pause().stop().destroy(); }
    }

    @Test public void unavailableContentUsesCreatorGalleryAndSystemAlertsDoNotFanOut() {
        record(item("Creator One", 101));
        UpdateInboxStore.Entry parent = group();
        UpdateInboxStore.CreatorItem removed = new UpdateInboxStore.CreatorItem("fapello", "Creator One",
                new NativeContentItem(NativeContentItem.KIND_MEDIA, "Unavailable", "", "", "", "", ""), 0, false);
        ActivityController<UpdateInboxActivity> controller = Robolectric.buildActivity(UpdateInboxActivity.class).setup();
        try {
            Intent fallback = CreatorNotificationNavigator.itemIntent(controller.get(), parent, removed);
            assertEquals(NativeFeedBrowserActivity.class.getName(), fallback.getComponent().getClassName());
            NotificationCoordinator.showNewVideoNotifications(app, Collections.singletonList(
                    new NotificationCoordinator.SourceAlert("fapello", "Fapello", Arrays.asList(
                            item("Creator One", 102), item("Creator One", 103)))));
            assertEquals(0, Shadows.shadowOf((android.app.NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE)).size());
            assertEquals(1, UpdateInboxStore.all(app).size());
        } finally { controller.pause().stop().destroy(); }
    }

    @Test public void favoritePostPollingBaselinesThenDetectsExactNewMediaWithoutAvatarPlaceholders() {
        android.content.SharedPreferences state = app.getSharedPreferences("test_favorite_detection", 0);
        state.edit().clear().commit();
        FavoriteCreatorUpdateScanner.Profile profile = FavoriteCreatorUpdateScanner.plan(app, state).get(0);
        NativeContentItem first = item("Creator One", 32318226);
        assertNull(FavoriteCreatorUpdateScanner.detect(state, profile, Collections.singletonList(first)));
        NativeContentItem routeVariant = new NativeContentItem(NativeContentItem.KIND_MEDIA, "Video",
                "https://video.fapello.com/content/c/r/creator-one/creator-one_32318226.mp4",
                first.imageUrl, "", "https://fapello.com/creator-one/", "", "Fapello");
        assertNull(FavoriteCreatorUpdateScanner.detect(state, profile, Collections.singletonList(routeVariant)));
        NativeContentItem fresh = item("Creator One", 32318227);
        NotificationCoordinator.SourceAlert update = FavoriteCreatorUpdateScanner.detect(state, profile,
                Arrays.asList(fresh, routeVariant));
        assertEquals("Creator One", update.creatorName);
        assertEquals(1, update.items.size());
        assertEquals(fresh.url, update.items.get(0).url);
        assertEquals(fresh.imageUrl, update.items.get(0).imageUrl);
        UpdateInboxStore.record(app, Collections.singletonList(update));
        assertEquals(1, group().children.size());
        assertEquals(fresh.url, group().children.get(0).item.url);
        assertNull(FavoriteCreatorUpdateScanner.detect(state, profile, Collections.emptyList()));
        assertNull(FavoriteCreatorUpdateScanner.detect(state, profile, Arrays.asList(fresh, routeVariant)));
    }

    @Test public void favoritePollingRotatesBoundedProfilesAndMergedAliasesKeepSeparateSeenState() {
        for (int i = 0; i < 6; i++) favorite("Alias " + i);
        android.content.SharedPreferences state = app.getSharedPreferences("test_favorite_rotation", 0);
        state.edit().clear().commit();
        List<FavoriteCreatorUpdateScanner.Profile> first = FavoriteCreatorUpdateScanner.plan(app, state);
        List<FavoriteCreatorUpdateScanner.Profile> second = FavoriteCreatorUpdateScanner.plan(app, state);
        assertEquals(4, first.size());
        assertEquals(4, second.size());
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (FavoriteCreatorUpdateScanner.Profile profile : first) keys.add(profile.watchKey);
        for (FavoriteCreatorUpdateScanner.Profile profile : second) keys.add(profile.watchKey);
        assertEquals(7, keys.size());
        NativeContentItem media = item("Creator One", 101);
        assertNull(FavoriteCreatorUpdateScanner.detect(state, first.get(0), Collections.singletonList(media)));
        assertNull(FavoriteCreatorUpdateScanner.detect(state, first.get(1), Collections.singletonList(media)));
        assertTrue(state.getBoolean("seen_initialized_" + first.get(0).watchKey, false));
        assertTrue(state.getBoolean("seen_initialized_" + first.get(1).watchKey, false));
    }

    @Test public void photoThumbnailOwnsNavigationEvenAfterViewerFailure() throws Exception {
        NativeContentItem photo = new NativeContentItem(NativeContentItem.KIND_IMAGE, "Photo",
                "https://fapello.com/creator-one/101/", "https://img.example/101.jpg",
                "", "Creator One", "", "Fapello");
        record(photo);
        ActivityController<UpdateInboxActivity> inbox = Robolectric.buildActivity(UpdateInboxActivity.class).setup();
        try {
            UpdateInboxActivity activity = inbox.get();
            NotificationInboxTestIdle.await(activity);
            RecyclerView recycler = (RecyclerView) field(activity, "recycler");
            RecyclerView.Adapter adapter = recycler.getAdapter();
            RecyclerView.ViewHolder holder = adapter.createViewHolder(recycler, adapter.getItemViewType(0));
            adapter.bindViewHolder(holder, 0);
            assertTrue(((android.view.View) field(holder, "thumbnail")).performClick());
            Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
            assertEquals(BunkrGalleryActivity.class.getName(), intent.getComponent().getClassName());
            assertFalse(intent.hasExtra(BunkrGalleryActivity.EXTRA_NOTIFICATION_GALLERY_FALLBACK));
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
            BunkrGalleryActivity viewer = Robolectric.buildActivity(BunkrGalleryActivity.class, intent).get();
            org.robolectric.util.ReflectionHelpers.setField(viewer, "initialUrl", photo.url);
            java.lang.reflect.Method fallback = BunkrGalleryActivity.class.getDeclaredMethod("openNotificationFallback", String.class);
            fallback.setAccessible(true);
            assertFalse((boolean) fallback.invoke(viewer, photo.url));
            assertFalse((boolean) fallback.invoke(viewer, photo.url));
            assertNull(Shadows.shadowOf(viewer).getNextStartedActivity());
            assertFalse(viewer.isFinishing());
            ((android.view.View) field(holder, "avatar")).performClick();
            assertEquals(NativeFeedBrowserActivity.class.getName(),
                    Shadows.shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
        } finally { inbox.pause().stop().destroy(); }
    }

    @Test public void notificationViewerFailureReturnsOnlyOnceToTheFocusedGallery() throws Exception {
        record(item("Creator One", 101));
        ActivityController<UpdateInboxActivity> inbox = Robolectric.buildActivity(UpdateInboxActivity.class).setup();
        ActivityController<BunkrGalleryActivity> viewer = null;
        try {
            Intent intent = CreatorNotificationNavigator.itemIntent(inbox.get(), group(), group().children.get(0));
            // Test the fallback without launching network/player work from the viewer.
            viewer = Robolectric.buildActivity(BunkrGalleryActivity.class, intent);
            BunkrGalleryActivity activity = viewer.get();
            Field initial = BunkrGalleryActivity.class.getDeclaredField("initialUrl");
            initial.setAccessible(true);
            initial.set(activity, item("Creator One", 101).url);
            java.lang.reflect.Method fallback = BunkrGalleryActivity.class.getDeclaredMethod("openNotificationFallback", String.class);
            fallback.setAccessible(true);
            assertFalse((boolean) fallback.invoke(activity, item("Creator One", 102).url));
            assertTrue((boolean) fallback.invoke(activity, item("Creator One", 101).url));
            Intent gallery = Shadows.shadowOf(activity).getNextStartedActivity();
            assertEquals(NativeFeedBrowserActivity.class.getName(), gallery.getComponent().getClassName());
            assertEquals(Collections.singletonList(item("Creator One", 101).url),
                    gallery.getStringArrayListExtra(NativeFeedBrowserActivity.EXTRA_NOTIFICATION_FRESH_URLS));
            assertFalse((boolean) fallback.invoke(activity, item("Creator One", 101).url));
        } finally { inbox.pause().stop().destroy(); }
    }

    @Test public void favoriteCardBadgeDecrementsAndOpeningTheGalleryDoesNotConsumeUnread() throws Exception {
        record(item("Creator One", 101), item("Creator One", 102), item("Creator One", 103), item("Creator One", 104));
        ActivityController<CreatorsActivity> controller = Robolectric.buildActivity(CreatorsActivity.class).setup();
        try {
            CreatorsActivity activity = controller.get();
            RecyclerView recycler = (RecyclerView) field(activity, "recycler");
            RecyclerView.Adapter adapter = recycler.getAdapter();
            RecyclerView.ViewHolder holder = adapter.createViewHolder(recycler, 0);
            adapter.bindViewHolder(holder, 0);
            android.widget.TextView badge = (android.widget.TextView) field(holder, "updateBadge");
            assertEquals("4", badge.getText().toString());
            UpdateInboxStore.markCreatorItemRead(app, group().children.get(0).id);
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            adapter.onBindViewHolder(holder, 0, Collections.singletonList("badges"));
            assertEquals("3", badge.getText().toString());
            holder.itemView.performClick();
            assertEquals(3, group().unreadItems());
            UpdateInboxStore.markAllRead(app);
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            adapter.onBindViewHolder(holder, 0, Collections.singletonList("badges"));
            assertEquals(android.view.View.GONE, badge.getVisibility());
        } finally { controller.pause().stop().destroy(); }
    }

    private Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
