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

    @Test public void orientationRowsKeepOrderClicksAndHideEmptyRows() {
        LibraryMediaRows rows = new LibraryMediaRows(activity);
        View first = new View(activity), second = new View(activity), third = new View(activity);
        int[] clicks = {0}; second.setOnClickListener(v -> clicks[0]++);
        rows.addCard(first, "first", false);
        rows.addCard(second, "second", false);
        rows.addCard(third, "third", false);
        assertEquals(View.GONE, rows.getChildAt(0).getVisibility());
        rows.setPortrait("second", true);
        rows.setPortrait("first", true);
        android.widget.LinearLayout portrait = row(rows, 0), landscape = row(rows, 1);
        assertSame(first, portrait.getChildAt(0)); assertSame(second, portrait.getChildAt(1));
        assertSame(third, landscape.getChildAt(0));
        assertTrue(first.getLayoutParams().height > first.getLayoutParams().width);
        assertTrue(third.getLayoutParams().width > third.getLayoutParams().height);
        second.performClick(); assertEquals(1, clicks[0]);
        rows.setPortrait("second", false); rows.setPortrait("first", false);
        assertEquals(View.GONE, rows.getChildAt(0).getVisibility());
        assertEquals(3, landscape.getChildCount());
        assertSame(first, landscape.getChildAt(0)); assertSame(second, landscape.getChildAt(1));
    }

    @Test public void favoriteAvatarsUseTwoRowsBeforeLikedVideos() throws Exception {
        for (int i = 0; i < 4; i++) {
            NativeContentItem creator = new NativeContentItem(NativeContentItem.KIND_CREATOR,
                    "Creator " + i, "https://fapello.com/fixture-avatar-" + i + "/", "", "", "", "", "", "Creator " + i);
            CreatorCatalog.remember(activity, java.util.Collections.singletonList(creator));
            CreatorFavoriteStore.toggle(activity, creator);
        }
        NativeContentItem liked = new NativeContentItem(NativeContentItem.KIND_MEDIA, "Liked", "https://fixture.invalid/liked", "", "", "", "");
        SocialContentContextStore.remember(activity, liked);
        SocialContentContextStore.confirmLike(activity, owner, liked.url, true);
        LibraryHubView view = new LibraryHubView(activity, null);
        activity.setContentView(view);
        Map<String, Runnable> work = new LinkedHashMap<>(); view.setWorkScheduler(work::put);
        try {
            view.setSocialActive(true); publish(view, work);
            android.widget.LinearLayout media = ReflectionHelpers.getField(view, "mediaContent");
            assertEquals("Favorite Creators", media.getChildAt(0).getTag());
            assertEquals("Liked Videos", media.getChildAt(1).getTag());
            ViewGroup creators = (ViewGroup) view.findViewWithTag("Favorite Creators");
            android.widget.HorizontalScrollView scroll = (android.widget.HorizontalScrollView) creators.getChildAt(1);
            ViewGroup rail = (ViewGroup) scroll.getChildAt(0);
            assertEquals(2, rail.getChildCount());
            for (int i = 0; i < rail.getChildCount(); i++) {
                ViewGroup column = (ViewGroup) rail.getChildAt(i);
                assertEquals(2, column.getChildCount());
                for (int j = 0; j < column.getChildCount(); j++) {
                    ViewGroup wrapper = (ViewGroup) column.getChildAt(j);
                    com.google.android.material.card.MaterialCardView avatar = (com.google.android.material.card.MaterialCardView) wrapper.getChildAt(0);
                    assertEquals(BrowseUi.dp(activity, 72), avatar.getLayoutParams().width);
                    assertEquals(avatar.getLayoutParams().width, avatar.getLayoutParams().height);
                    assertEquals(BrowseUi.dp(activity, 36), avatar.getRadius(), 0.1f);
                }
            }
            layoutLibrary(view, 320, 800);
            saveRender(view, "library-two-avatar-rows");
        } finally { view.close(); }
    }

    @Test public void realThumbnailDimensionsSeparateEveryMediaCollectionWithoutChangingStores() throws Exception {
        String portrait = thumbnailFile("portrait", 240, 426, android.graphics.Color.rgb(7, 70, 90));
        String horizontal = thumbnailFile("landscape", 480, 270, android.graphics.Color.rgb(26, 42, 65));
        String[] urls = {"https://fixture.invalid/portrait", "https://fixture.invalid/landscape"};
        String[] images = {portrait, horizontal};
        for (int i = 0; i < 2; i++) {
            NativeContentItem video = new NativeContentItem(NativeContentItem.KIND_MEDIA,
                    i == 0 ? "Portrait video" : "Landscape video", urls[i], images[i], "", "", "");
            SocialContentContextStore.remember(activity, video);
            SocialContentContextStore.confirmLike(activity, owner, video.url, true);
            FavoriteStore.add(activity, video.title, video.url);
            PlaybackHistoryStore.record(activity, video.title, video.url, images[i], 60_000, 180_000, false, true);
            VideoDownloadStore.Entry download = new VideoDownloadStore.Entry(-10 - i, video.title, video.url,
                    images[i], "", "video/mp4", 1, android.app.DownloadManager.STATUS_SUCCESSFUL, 10, 10, "file:///fixture.mp4", 0);
            java.lang.reflect.Method save = VideoDownloadStore.class.getDeclaredMethod("saveMetadata", Context.class, VideoDownloadStore.Entry.class);
            save.setAccessible(true); save.invoke(null, activity, download);
        }
        PlaybackHistoryStore.awaitPendingWritesForTests();
        String savedBefore = activity.getSharedPreferences("watch_later", 0).getString("items", "");
        String historyBefore = activity.getSharedPreferences("playback_history", 0).getString("items", "");
        LibraryHubView view = new LibraryHubView(activity, null);
        activity.setContentView(view);
        Map<String, Runnable> work = new LinkedHashMap<>(); view.setWorkScheduler(work::put);
        try {
            view.setSocialActive(true); publish(view, work);
            layoutLibrary(view, 320, 800);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle();
                for (String key : new java.util.ArrayList<>(work.keySet()))
                    if (key.startsWith("library-orientation:")) work.remove(key).run();
                Map<String, Boolean> orientations = ReflectionHelpers.getField(view, "portraitThumbnails");
                if (Boolean.TRUE.equals(orientations.get(urls[0])) && Boolean.FALSE.equals(orientations.get(urls[1]))) break;
                Thread.sleep(10);
            }
            layoutLibrary(view, 320, 800);
            for (String section : new String[]{"Liked Videos", "Downloads", "Watch Later", "History"}) {
                ViewGroup shell = (ViewGroup) view.findViewWithTag(section);
                assertNotNull(section, shell);
                LibraryMediaRows rows = (LibraryMediaRows) shell.getChildAt(1);
                assertEquals(section + " portrait count", 1, row(rows, 0).getChildCount());
                assertEquals(section + " landscape count", 1, row(rows, 1).getChildCount());
                assertTrue(row(rows, 0).getChildAt(0).getHeight() > row(rows, 0).getChildAt(0).getWidth());
                assertTrue(row(rows, 1).getChildAt(0).getWidth() > row(rows, 1).getChildAt(0).getHeight());
            }
            ViewGroup history = (ViewGroup) view.findViewWithTag("History");
            saveRender(history, "library-orientation-history");
            saveRender((View) view.findViewWithTag("Liked Videos"), "library-orientation-likes");
            assertEquals(savedBefore, activity.getSharedPreferences("watch_later", 0).getString("items", ""));
            assertEquals(historyBefore, activity.getSharedPreferences("playback_history", 0).getString("items", ""));
            assertEquals(2, SocialContentContextStore.likedVideos(activity, owner).size());
        } finally { view.close(); }
    }

    @Test public void cleanerPreviewsKeepOriginalMetadataPlaybackAndDownloadStatus() throws Exception {
        String title = "OnlyFap video #32409216", url = "https://fixture.invalid/generated";
        FavoriteStore.add(activity, title, url);
        String before = activity.getSharedPreferences("watch_later", 0).getString("items", "");
        List<NativeContentItem> opened = new java.util.ArrayList<>();
        LibraryHubView view = new LibraryHubView(activity, new LibraryHubView.Listener() {
            public void onOpenWatchLater(NativeContentItem item) { opened.add(item); }
            public void onOpenHistory(PlaybackHistoryStore.Item item) { }
            public void onOpenCreator(NativeContentItem item) { }
        });
        activity.setContentView(view);
        Map<String, Runnable> work = new LinkedHashMap<>(); view.setWorkScheduler(work::put);
        try {
            view.setSocialActive(true); publish(view, work);
            assertNull(text(view, "Saved for another time"));
            assertNull(text(view, "Your personal media collection"));
            assertNull(text(view, title));
            TextView preview = (TextView) text(view, "OnlyFap video");
            assertNotNull(preview);
            assertEquals(2, preview.getMinLines());
            assertEquals(2, preview.getMaxLines());
            assertEquals(android.view.Gravity.BOTTOM,
                    preview.getGravity() & android.view.Gravity.VERTICAL_GRAVITY_MASK);
            description(view, title).performClick();
            assertEquals(title, opened.get(0).title);
            assertEquals(url, opened.get(0).url);
            assertEquals(before, activity.getSharedPreferences("watch_later", 0).getString("items", ""));
            for (int status : new int[]{android.app.DownloadManager.STATUS_SUCCESSFUL,
                    android.app.DownloadManager.STATUS_RUNNING, android.app.DownloadManager.STATUS_FAILED}) {
                VideoDownloadStore.Entry entry = new VideoDownloadStore.Entry(-20, title, url, "", "", "video/mp4",
                        1, status, 50, 100, "file:///fixture.mp4", 0);
                View card = ReflectionHelpers.callInstanceMethod(view, "downloadCard",
                        ReflectionHelpers.ClassParameter.from(VideoDownloadStore.Entry.class, entry));
                assertTrue(card.getContentDescription().toString().contains(title));
                if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                    assertNotNull(card.findViewWithTag("library-offline"));
                    assertNull(text(card, "Ready offline"));
                    assertTrue(card.getContentDescription().toString().contains("Ready offline"));
                } else {
                    assertNull(card.findViewWithTag("library-offline"));
                    assertNotNull(text(card, status == android.app.DownloadManager.STATUS_FAILED
                            ? "Download failed" : VideoDownloadStore.statusText(entry)));
                }
                android.widget.FrameLayout host = new android.widget.FrameLayout(activity);
                host.addView(card, new android.widget.FrameLayout.LayoutParams(BrowseUi.dp(activity, 104), BrowseUi.dp(activity, 185)));
                layoutLibrary(host, 104, 185);
                saveRender(host, "library-clean-download-" + status);
            }
        } finally { view.close(); }
    }

    private android.widget.LinearLayout row(LibraryMediaRows rows, int index) {
        return (android.widget.LinearLayout) ((android.widget.HorizontalScrollView) rows.getChildAt(index)).getChildAt(0);
    }
    private String thumbnailFile(String name, int width, int height, int color) throws Exception {
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        java.io.File file = new java.io.File(activity.getCacheDir(), name + ".png");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out); }
        bitmap.recycle(); return file.toURI().toString();
    }
    private void layoutLibrary(View view, int widthDp, int heightDp) {
        int width = BrowseUi.dp(activity, widthDp), height = BrowseUi.dp(activity, heightDp);
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }
    private void saveRender(View view, String name) throws Exception {
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(view.getWidth(), view.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        view.draw(new android.graphics.Canvas(bitmap));
        java.io.File directory = new java.io.File("build/reports/visual-tests");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out));
        }
        bitmap.recycle();
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
