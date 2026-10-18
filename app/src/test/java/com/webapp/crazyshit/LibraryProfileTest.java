package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w320dp-h800dp", shadows = LibraryProfileTest.Session.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LibraryProfileTest {
    private static String owner;
    @Implements(value = ZeroChillSessionStore.class, isInAndroidSdk = false)
    public static class Session {
        @Implementation protected static String currentUserId(Context context) { return owner; }
    }
    private Activity activity;
    @Before public void setUp() {
        owner = "account-a";
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        PlaybackHistoryStore.resetForTests();
        for (String prefs : new String[]{"playback_history", "watch_later", "creator_favorites",
                "native_video_downloads", SocialContentContextStore.PREFS, AccountProfileCache.PREFS})
            activity.getSharedPreferences(prefs, 0).edit().clear().commit();
    }

    @Test public void loadingNeverShowsEmptyAndResolvedEmptyKeepsCompactActions() throws Exception {
        LibraryHubView view = new LibraryHubView(activity, null);
        activity.setContentView(view);
        Map<String, Runnable> work = new LinkedHashMap<>(); view.setWorkScheduler(work::put);
        try {
            assertNull(text(view, "Your Library is ready"));
            assertNotNull(description(view, "Loading your Library"));
            view.setSocialActive(true);
            assertNull(text(view, "Your Library is ready"));
            publish(view, work);
            assertNotNull(text(view, "Your Library is ready"));
            assertNull(description(view, "Loading your Library"));
            assertNull(view.findViewWithTag("History"));
            assertNull(view.findViewWithTag("Downloads"));
            assertNull(view.findViewWithTag("Favorite Creators"));
            description(view, "Messages.").performClick();
            assertEquals(ZeroChillInboxActivity.class.getName(), shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
            description(view, "Notifications.").performClick();
            assertEquals(UpdateInboxActivity.class.getName(), shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
            description(view, "Settings").performClick();
            assertEquals(SettingsActivity.class.getName(), shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
            description(view, "Open Liked Videos").performClick();
            assertEquals(LikedVideosActivity.class.getName(), shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
        } finally { view.close(); }
    }

    @Test public void historyUnifiesResumeAndCompletedEntriesAndKeepsContentWhileRefreshing() throws Exception {
        PlaybackHistoryStore.record(activity, "In progress", "https://fixture.invalid/in-progress", "",
                60_000, 180_000, false, true);
        PlaybackHistoryStore.record(activity, "Finished", "https://fixture.invalid/finished", "",
                180_000, 180_000, true, true);
        PlaybackHistoryStore.awaitPendingWritesForTests();
        List<PlaybackHistoryStore.Item> selected = new java.util.ArrayList<>();
        LibraryHubView view = new LibraryHubView(activity, new LibraryHubView.Listener() {
            public void onOpenWatchLater(NativeContentItem item) { }
            public void onOpenCreator(NativeContentItem item) { }
            public void onOpenHistory(PlaybackHistoryStore.Item item) { selected.add(item); }
        });
        activity.setContentView(view);
        Map<String, Runnable> work = new LinkedHashMap<>(); view.setWorkScheduler(work::put);
        try {
            view.setSocialActive(true); publish(view, work);
            assertNull(view.findViewWithTag("Continue Watching"));
            assertNotNull(view.findViewWithTag("History"));
            description(view, "Resume In progress").performClick();
            assertEquals(60_000L, selected.get(0).positionMs);
            description(view, "Watch again Finished").performClick();
            assertTrue(selected.get(1).complete);
            View rail = view.findViewWithTag("History");
            view.refresh();
            assertSame(rail, view.findViewWithTag("History"));
            assertNull(text(view, "Your Library is ready"));
            // Wait for the unchanged snapshot before closing/recreating the view.
            ((ExecutorService) ReflectionHelpers.getField(view, "refreshIo")).submit(() -> {}).get(5, TimeUnit.SECONDS);
            shadowOf(Looper.getMainLooper()).idle();
            Runnable apply = work.remove("library-publish"); if (apply != null) apply.run();
            LibraryHubView recreated = new LibraryHubView(activity, null);
            activity.setContentView(recreated);
            Map<String, Runnable> recreatedWork = new LinkedHashMap<>(); recreated.setWorkScheduler(recreatedWork::put);
            try {
                recreated.setSocialActive(true); publish(recreated, recreatedWork);
                assertNotNull(description(recreated, "Resume In progress"));
                android.widget.LinearLayout content = ReflectionHelpers.getField(recreated, "content");
                assertTrue(content.getPaddingBottom() >= ZeroChillUi.dimension(activity, R.dimen.zc_bottom_nav_height) + BrowseUi.dp(activity, 24));
            } finally { recreated.close(); }
        } finally { view.close(); }
    }

    @Test public void confirmedLikesReuseMetadataAndRemoveOnUnlikeWithoutChangingSavedOrHistory() throws Exception {
        String url = "https://fixture.invalid/video";
        NativeContentItem video = new NativeContentItem(NativeContentItem.KIND_MEDIA, "A liked video", url, "", "", "", "");
        SocialContentContextStore.remember(activity, video);
        FavoriteStore.add(activity, "Saved separately", url);
        SocialContentContextStore.confirmLike(activity, owner, url, true);
        assertEquals(1, SocialContentContextStore.likedVideos(activity, owner).size());
        LibraryHubView view = new LibraryHubView(activity, null);
        activity.setContentView(view);
        Map<String, Runnable> work = new LinkedHashMap<>(); view.setWorkScheduler(work::put);
        try {
            view.setSocialActive(true); publish(view, work);
            assertNotNull(view.findViewWithTag("Liked Videos"));
            assertNotNull(text(view, "A liked video"));
            assertNotNull(view.findViewWithTag("Watch Later"));
            SocialContentContextStore.confirmLike(activity, owner, url, false);
            shadowOf(Looper.getMainLooper()).idle(); publish(view, work);
            assertNull(view.findViewWithTag("Liked Videos"));
            assertEquals(1, FavoriteStore.load(activity).size());
            assertTrue(PlaybackHistoryStore.load(activity).isEmpty());
        } finally { view.close(); }
    }

    @Test public void likesNeverLeakAcrossAccountsOrAcceptLateConfirmation() throws Exception {
        SocialContentContextStore.confirmLike(activity, "account-a", "https://fixture.invalid/a", true);
        owner = "account-b";
        SocialContentContextStore.confirmLike(activity, "account-a", "https://fixture.invalid/late", true);
        assertTrue(SocialContentContextStore.likedVideos(activity, "account-b").isEmpty());
        assertTrue(SocialContentContextStore.likedVideos(activity, "account-a").isEmpty());
        try {
            SocialContentContextStore.cacheLikedPage(activity, "account-a", new JSONArray(), 0);
            fail("Stale account response must be discarded");
        } catch (IllegalStateException expected) { }
        owner = "account-a";
        assertEquals(1, SocialContentContextStore.likedVideos(activity, owner).size());
        owner = "";
        assertTrue(SocialContentContextStore.likedVideos(activity, owner).isEmpty());
    }

    @Test public void serverPageMatchesExistingHistoryReportsUnknownAndClearsRemovedLikes() throws Exception {
        String url = "https://fixture.invalid/older";
        PlaybackHistoryStore.record(activity, "Older liked video", url, "", 60_000, 180_000, false, true);
        PlaybackHistoryStore.awaitPendingWritesForTests();
        JSONArray rows = new JSONArray().put(new JSONObject().put("content_key", ZeroChillSocialRepository.contentKey(url))
                .put("created_at", "2026-10-17T12:00:00Z"))
                .put(new JSONObject().put("content_key", "unrecoverable-hash").put("created_at", "2026-10-16T12:00:00Z"));
        SocialContentContextStore.LikedPage page = SocialContentContextStore.cacheLikedPage(activity, owner, rows, 0);
        assertEquals(1, page.items.size()); assertEquals(1, page.unavailable); assertFalse(page.hasMore);
        assertEquals("Older liked video", page.items.get(0).title);
        PlaybackHistoryStore.clear(activity);
        assertEquals("Metadata survives history deletion", "Older liked video",
                SocialContentContextStore.likedVideos(activity, owner).get(0).title);
        SocialContentContextStore.cacheLikedPage(activity, owner, new JSONArray(), 0);
        assertTrue(SocialContentContextStore.likedVideos(activity, owner).isEmpty());
        assertTrue(PlaybackHistoryStore.load(activity).isEmpty());
    }

    @Test public void profileUsesOnlyMatchingCachedIdentityAndRoutesToExistingAccount() {
        activity.getSharedPreferences(AccountProfileCache.PREFS, 0).edit().putString("owner", owner)
                .putString("name", "Addy").commit();
        LibraryProfileHeader header = new LibraryProfileHeader(activity);
        activity.setContentView(header);
        try {
            assertNotNull(text(header, "Addy"));
            owner = "account-b"; header.refresh();
            assertNull(text(header, "Addy"));
            description(header, "Your profile and account").performClick();
            assertEquals(ZeroChillAccountActivity.class.getName(), shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
        } finally { header.close(); }
    }

    @Test public void accountSwitchClearsPreviousMediaBeforeNextSnapshot() throws Exception {
        SocialContentContextStore.remember(activity, new NativeContentItem(NativeContentItem.KIND_MEDIA,
                "Private account like", "https://fixture.invalid/private", "", "", "", ""));
        SocialContentContextStore.confirmLike(activity, owner, "https://fixture.invalid/private", true);
        LibraryHubView view = new LibraryHubView(activity, null);
        activity.setContentView(view);
        Map<String, Runnable> work = new LinkedHashMap<>(); view.setWorkScheduler(work::put);
        try {
            view.setSocialActive(true); publish(view, work);
            assertNotNull(text(view, "Private account like"));
            owner = "account-b";
            ZeroChillSessionStore.preferences(activity).edit().putString("test-owner", owner).apply();
            shadowOf(Looper.getMainLooper()).idle();
            assertNull(text(view, "Private account like"));
            assertNotNull(description(view, "Loading your Library"));
            publish(view, work);
            assertNull(text(view, "Private account like"));
        } finally { view.close(); }
    }

    @Test public void populatedProfileNativeRender() throws Exception { renderProfile("library-profile-phone", 320, 800); }
    @Test @Config(qualifiers = "w800dp-h360dp-land")
    public void landscapeProfileNativeRender() throws Exception { renderProfile("library-profile-landscape", 800, 360); }

    private void renderProfile(String fileName, int widthDp, int heightDp) throws Exception {
        activity.getSharedPreferences(AccountProfileCache.PREFS, 0).edit().putString("owner", owner)
                .putString("name", "Addy").commit();
        NativeContentItem liked = new NativeContentItem(NativeContentItem.KIND_MEDIA,
                "A liked video with a longer title", "https://fixture.invalid/liked", "", "", "", "");
        SocialContentContextStore.remember(activity, liked);
        SocialContentContextStore.confirmLike(activity, owner, liked.url, true);
        PlaybackHistoryStore.record(activity, "An unfinished show", "https://fixture.invalid/show", "", 60_000, 180_000, false, true);
        PlaybackHistoryStore.awaitPendingWritesForTests();
        NativeContentItem creator = new NativeContentItem(NativeContentItem.KIND_CREATOR,
                "Favorite Creator", "https://fapello.com/fixture-creator/", "", "", "", "", "", "Favorite Creator");
        CreatorCatalog.remember(activity, java.util.Collections.singletonList(creator));
        CreatorFavoriteStore.toggle(activity, creator);
        LibraryHubView view = new LibraryHubView(activity, null);
        activity.setContentView(view);
        Map<String, Runnable> work = new LinkedHashMap<>(); view.setWorkScheduler(work::put);
        try {
            view.setSocialActive(true); publish(view, work);
            int width = BrowseUi.dp(activity, widthDp), height = BrowseUi.dp(activity, heightDp);
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            view.layout(0, 0, width, height);
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
            view.draw(new android.graphics.Canvas(bitmap));
            java.io.File dir = new java.io.File("build/reports/visual-tests");
            assertTrue(dir.isDirectory() || dir.mkdirs());
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(dir, fileName + ".png"))) {
                assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output));
            }
            bitmap.recycle();
        } finally { view.close(); }
    }

    private void publish(LibraryHubView view, Map<String, Runnable> work) throws Exception {
        Runnable refresh = work.remove("library-refresh"); assertNotNull(refresh); refresh.run();
        ((ExecutorService) ReflectionHelpers.getField(view, "refreshIo")).submit(() -> {}).get(5, TimeUnit.SECONDS);
        shadowOf(Looper.getMainLooper()).idle();
        Runnable apply = work.remove("library-publish"); assertNotNull(apply); apply.run();
        shadowOf(Looper.getMainLooper()).idle();
    }
    static View description(View root, String prefix) {
        return find(root, view -> view.getContentDescription() != null && view.getContentDescription().toString().startsWith(prefix));
    }
    static View text(View root, String value) {
        return find(root, view -> view instanceof TextView && value.contentEquals(((TextView) view).getText()));
    }
    private static View find(View root, java.util.function.Predicate<View> predicate) {
        if (predicate.test(root)) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View found = find(((ViewGroup) root).getChildAt(i), predicate); if (found != null) return found;
        }
        return null;
    }
}
