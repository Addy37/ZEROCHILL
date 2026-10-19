package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, shadows = OnlyFapLibraryMetadataTest.Session.class)
public class OnlyFapLibraryMetadataTest {
    private static final String URL = "https://fapello.com/video/week/31142429/";
    private static final String PROFILE = "https://fapello.com/carrotsoki-2/";
    private final Context context = RuntimeEnvironment.getApplication();
    private static String owner;

    @Implements(value = ZeroChillSessionStore.class, isInAndroidSdk = false)
    public static class Session {
        @Implementation protected static String currentUserId(Context context) { return owner; }
    }

    private NativeContentItem clip() {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, "OnlyFap video #31142429", URL,
                "https://fixture.invalid/poster.jpg", "", "CarrotSoki", "", "OnlyFap", PROFILE);
    }

    @Before public void clear() {
        owner = "account-a";
        PlaybackHistoryStore.resetForTests();
        for (String prefs : new String[]{"playback_history", "watch_later", "native_video_downloads",
                "creator_favorites", CreatorCatalog.PREFS, SocialContentContextStore.PREFS})
            context.getSharedPreferences(prefs, 0).edit().clear().commit();
    }

    @Test public void watchLaterPersistsProfileAndLegacyUpdatesKeepIt() throws Exception {
        FavoriteStore.add(context, clip());
        FavoriteStore.Item saved = FavoriteStore.load(context).get(0);
        assertEquals(PROFILE, OnlyFapVideoMetadata.creator(saved.content).url);
        assertEquals(URL, saved.content.url);
        FavoriteStore.add(context, "OnlyFap video", URL);
        assertEquals(PROFILE, OnlyFapVideoMetadata.creator(FavoriteStore.load(context).get(0).content).url);
        long time = FavoriteStore.load(context).get(0).savedAt;
        FavoriteStore.enrich(context, clip());
        assertEquals(time, FavoriteStore.load(context).get(0).savedAt);
        FavoriteStore.remove(context, URL);
        FavoriteStore.enrich(context, clip());
        assertTrue(FavoriteStore.load(context).isEmpty());
    }

    @Test public void historyRoundTripKeepsResumeAndCreatorThroughTitleOnlyUpdates() {
        PlaybackHistoryStore.record(context, clip(), 12_000, 120_000, false, false);
        PlaybackHistoryStore.awaitPendingWritesForTests();
        PlaybackHistoryStore.resetForTests();
        PlaybackHistoryStore.Item item = PlaybackHistoryStore.load(context).get(0);
        assertEquals(12_000, item.positionMs);
        assertEquals(PROFILE, OnlyFapVideoMetadata.creator(item.content).url);
        PlaybackHistoryStore.record(context, "OnlyFap video", URL, 24_000, 120_000, false);
        PlaybackHistoryStore.awaitPendingWritesForTests();
        item = PlaybackHistoryStore.load(context).get(0);
        assertEquals(24_000, item.positionMs);
        assertEquals(PROFILE, OnlyFapVideoMetadata.creator(item.content).url);
        assertFalse(item.complete);
        assertFalse(item.fromShows);
    }

    @Test public void legacyRecordsStillLoadAndIncompleteMetadataCannotRedirectAnotherClip() throws Exception {
        JSONObject legacy = new JSONObject().put("title", "OnlyFap video").put("url", URL).put("savedAt", 5);
        context.getSharedPreferences("watch_later", 0).edit()
                .putString("items", new JSONArray().put(legacy).toString()).commit();
        assertNull(FavoriteStore.load(context).get(0).content);
        Intent intent = new Intent();
        OnlyFapVideoMetadata.put(intent, clip());
        assertEquals(PROFILE, OnlyFapVideoMetadata.creator(OnlyFapVideoMetadata.fromIntent(intent, URL)).url);
        assertNull(OnlyFapVideoMetadata.fromIntent(intent, "https://fapello.com/other/123/"));
        assertNull(OnlyFapVideoMetadata.creator(OnlyFapVideoMetadata.basic("OnlyFap video", URL, "")));
    }

    @Test public void likedCreatorSurvivesGenericHistoryAndRemainsAccountScoped() {
        SocialContentContextStore.remember(context, clip());
        SocialContentContextStore.confirmLike(context, owner, URL, true);
        PlaybackHistoryStore.record(context, "OnlyFap video", URL, 15_000, 120_000, false);
        PlaybackHistoryStore.awaitPendingWritesForTests();
        NativeContentItem liked = SocialContentContextStore.likedVideos(context, owner).get(0);
        assertEquals(PROFILE, OnlyFapVideoMetadata.creator(liked).url);
        assertEquals("CarrotSoki", OnlyFapVideoMetadata.displayTitle(liked));
        owner = "account-b";
        assertTrue(SocialContentContextStore.likedVideos(context, owner).isEmpty());
    }

    @Test public void downloadStateChangesAndOfflineLaunchRetainCreatorMetadata() throws Exception {
        VideoDownloadStore.Entry entry = new VideoDownloadStore.Entry(-1, "OnlyFap video", URL, "", "",
                "video/mp4", 5, DownloadManager.STATUS_PENDING, 0, 100, "content://fixture/video", 0, clip());
        VideoDownloadStore.Entry done = entry.withState(DownloadManager.STATUS_SUCCESSFUL, 100, 100,
                "content://fixture/video", 0);
        ReflectionHelpers.callStaticMethod(VideoDownloadStore.class, "saveMetadata",
                from(Context.class, context), from(VideoDownloadStore.Entry.class, done));
        NativeContentItem stored = VideoDownloadStore.savedContent(context).get(0);
        assertEquals(PROFILE, OnlyFapVideoMetadata.creator(stored).url);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        VideoDownloadStore.open(activity, done);
        Intent intent = shadowOf(activity).getNextStartedActivity();
        assertEquals("content://fixture/video", intent.getStringExtra(PlayerActivity.EXTRA_MEDIA_URL));
        assertEquals(URL, intent.getStringExtra(PlayerActivity.EXTRA_PAGE_URL));
        assertEquals(PROFILE, OnlyFapVideoMetadata.creator(OnlyFapVideoMetadata.fromIntent(intent, URL)).url);
        assertFalse(intent.getBooleanExtra(VideoDetailActivity.EXTRA_SHOWS_ORIGIN, true));
    }

    @Test public void creatorHeaderOpensExactGalleryAndFavoriteUsesCreatorName() {
        VideoDetailActivity activity = Robolectric.buildActivity(VideoDetailActivity.class).get();
        LinearLayout row = new LinearLayout(activity);
        ReflectionHelpers.setField(activity, "onlyFapCreatorRow", row);
        ReflectionHelpers.setField(activity, "onlyFapCreator", OnlyFapVideoMetadata.creator(clip()));
        ReflectionHelpers.setField(activity, "relatedTitle", new TextView(activity));
        ReflectionHelpers.callInstanceMethod(activity, "updateOnlyFapCreatorRow", from(boolean.class, false));
        View favorite = find(row, "Add CarrotSoki to Favorite Creators");
        assertNotNull(favorite);
        favorite.performClick();
        assertTrue(CreatorFavoriteStore.contains(activity, OnlyFapVideoMetadata.creator(clip())));
        View gallery = find(row, "Open CarrotSoki gallery");
        assertNotNull(gallery);
        gallery.performClick();
        Intent intent = shadowOf(activity).getNextStartedActivity();
        assertEquals(PROFILE, intent.getStringExtra(NativeFeedBrowserActivity.EXTRA_FAPELLO_PROFILE_URL));
        assertEquals("CarrotSoki", intent.getStringExtra(NativeFeedBrowserActivity.EXTRA_BUNKR_CREATOR_QUERY));
    }

    @Test public void libraryShowsCreatorForSavedClipAndRetainsItsPlaybackUrl() throws Exception {
        owner = "";
        FavoriteStore.add(context, clip());
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        NativeContentItem[] opened = {null};
        LibraryHubView view = new LibraryHubView(activity, new LibraryHubView.Listener() {
            @Override public void onOpenWatchLater(NativeContentItem item) { opened[0] = item; }
            @Override public void onOpenHistory(PlaybackHistoryStore.Item item) { }
            @Override public void onOpenCreator(NativeContentItem creator) { }
        });
        activity.setContentView(view);
        Map<String, Runnable> work = new LinkedHashMap<>();
        view.setWorkScheduler(work::put);
        try {
            view.setSocialActive(true);
            Runnable refresh = work.remove("library-refresh");
            assertNotNull(refresh); refresh.run();
            ((ExecutorService) ReflectionHelpers.getField(view, "refreshIo")).submit(() -> {}).get(5, TimeUnit.SECONDS);
            shadowOf(Looper.getMainLooper()).idle();
            Runnable publish = work.remove("library-publish");
            assertNotNull(publish); publish.run();
            View card = find(view, "CarrotSoki");
            assertNotNull(card); card.performClick();
            assertEquals(URL, opened[0].url);
            assertEquals(PROFILE, OnlyFapVideoMetadata.creator(opened[0]).url);
        } finally { view.close(); }
    }

    @Test public void profileRecoveryUsesExplicitCreatorLinkAndRejectsAmbiguousPages() {
        NativeContentItem old = OnlyFapVideoMetadata.basic("OnlyFap video", URL, "");
        FapelloRepository repository = new FapelloRepository();
        String html = "<a href='/unrelated/'>Unrelated creator</a><a href='/carrotsoki-2/'>See all content of CarrotSoki</a>";
        NativeContentItem recovered = repository.parseVideoMetadata(Jsoup.parse(html, URL), old);
        assertEquals(PROFILE, OnlyFapVideoMetadata.creator(recovered).url);
        assertEquals(URL, recovered.url);
        html += "<a href='/other/'>See all content of Other</a>";
        assertNull(OnlyFapVideoMetadata.creator(repository.parseVideoMetadata(Jsoup.parse(html, URL), old)));
    }

    @Test public void galleryMediaNamesUseKnownProfileAndOnlyHavenCaptionsAreNotGuessed() {
        NativeContentItem gallery = new NativeContentItem(NativeContentItem.KIND_MEDIA, "CarrotSoki #123", URL,
                "", "", PROFILE, "", "Fapello");
        assertEquals("CarrotSoki", OnlyFapVideoMetadata.displayTitle(gallery));
        assertEquals(PROFILE, OnlyFapVideoMetadata.creator(gallery).url);
        NativeContentItem legacy = new NativeContentItem(NativeContentItem.KIND_MEDIA, "An unrelated caption",
                "https://e1.cum.st/clip.mp4", "", "", "https://cum.st/creators/onlyfans/id", "", "OnlyHaven");
        assertNull(OnlyFapVideoMetadata.creator(legacy));
    }

    @Test public void backupRoundTripRetainsCreatorAndRejectsNonWebMetadataImages() throws Exception {
        FavoriteStore.add(context, clip());
        JSONObject backup = AppBackupStore.export(context);
        FavoriteStore.clear(context);
        AppBackupStore.restore(context, backup);
        assertEquals(PROFILE, OnlyFapVideoMetadata.creator(FavoriteStore.load(context).get(0).content).url);
        backup.getJSONArray("watchLater").getJSONObject(0).getJSONObject("content").put("image", "file:///private.jpg");
        try { BackupDocument.validate(backup); fail("Local metadata image must not be accepted"); }
        catch (java.io.IOException expected) { }
    }

    private static View find(View root, String description) {
        if (root.getContentDescription() != null
                && description.contentEquals(root.getContentDescription())) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View found = find(((ViewGroup) root).getChildAt(i), description);
            if (found != null) return found;
        }
        return null;
    }
}
