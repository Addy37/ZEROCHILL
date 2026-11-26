package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class LibraryPlaybackTest {
    private static final String ALBUM = "https://www.erome.com/a/AbCd12";
    private static final String CLIP = ALBUM + "#VideoToken";
    private static final String MEDIA = "https://v1.erome.com/123/AbCd12/VideoToken_720p.mp4";
    private final Context context = RuntimeEnvironment.getApplication();

    @Before public void clear() {
        PlaybackHistoryStore.resetForTests();
        context.getSharedPreferences("watch_later", 0).edit().clear().commit();
        context.getSharedPreferences("playback_history", 0).edit().clear().commit();
    }

    @Test public void routesSavedMediaByOriginalSource() {
        assertEquals(LibraryPlayback.Route.EROME_ALBUM, LibraryPlayback.route(album()));
        assertEquals(LibraryPlayback.Route.EROME_MEDIA, LibraryPlayback.route(clip()));
        assertEquals(LibraryPlayback.Route.EROME_MEDIA, LibraryPlayback.route(
                OnlyFapVideoMetadata.basic("Video", MEDIA, "")));
        assertEquals(LibraryPlayback.Route.SHOWS, LibraryPlayback.route(
                OnlyFapVideoMetadata.basic("Show", "https://crazyshit.com/cnt/medias/1", "")));
        assertEquals(LibraryPlayback.Route.ONLYFAP_VIDEO, LibraryPlayback.route(
                OnlyFapVideoMetadata.basic("OnlyFap", "https://fapello.com/video/week/1/", "")));
        assertEquals(LibraryPlayback.Route.GALLERY, LibraryPlayback.route(new NativeContentItem(
                NativeContentItem.KIND_IMAGE, "Picture", "https://pawchive.example/a.jpg",
                "", "", "", "")));
        assertEquals(LibraryPlayback.Route.STANDALONE, LibraryPlayback.route(
                OnlyFapVideoMetadata.basic("Clip", "https://example.invalid/watch/1", "")));
        assertFalse(LibraryPlayback.viewAlbum(1));
        assertTrue(LibraryPlayback.viewAlbum(2));
    }

    @Test public void shitTokEromeClipIsNotTreatedAsShows() {
        assertEquals(LibraryPlayback.Route.EROME_MEDIA, LibraryPlayback.route(clip()));
        assertFalse(LibraryPlayback.isShowsPage(CLIP));
    }

    @Test public void oldUrlOnlyEntryStillOpensTheMatchingViewer() throws Exception {
        org.json.JSONArray items = new org.json.JSONArray().put(new org.json.JSONObject()
                .put("title", "Saved video").put("url", CLIP).put("savedAt", 1L)
                .put("mediaFavorite", false).put("watchLater", true));
        context.getSharedPreferences("watch_later", 0).edit().putString("items", items.toString()).commit();
        FavoriteStore.Item saved = FavoriteStore.load(context).get(0);
        assertNull(saved.content);
        assertEquals(LibraryPlayback.Route.EROME_MEDIA, LibraryPlayback.route(
                OnlyFapVideoMetadata.basic(saved.title, saved.url, "")));
    }

    @Test public void watchLaterKeepsEromeAlbumContextAndLegacyResaveDoesNotDropIt() {
        FavoriteStore.add(context, clip());
        FavoriteStore.Item saved = FavoriteStore.load(context).get(0);
        assertEquals(MEDIA, saved.content.sourceMediaUrl);
        assertEquals(ALBUM, saved.content.sourcePageUrl);
        FavoriteStore.add(context, "Saved video", CLIP);
        FavoriteStore.Item again = FavoriteStore.load(context).get(0);
        assertEquals(MEDIA, again.content.sourceMediaUrl);
        assertEquals(ALBUM, LibraryPlayback.albumOf(again.content));
        NativeContentItem recovered = LibraryPlayback.recover(context,
                OnlyFapVideoMetadata.basic("Saved video", CLIP, ""));
        assertEquals(MEDIA, recovered.sourceMediaUrl);
    }

    @Test public void historyResumesEromeWithoutEnteringShowsContinue() {
        PlaybackHistoryStore.record(context, clip(), 42_000L, 90_000L, false, true);
        PlaybackHistoryStore.awaitPendingWritesForTests();
        PlaybackHistoryStore.resetForTests();
        PlaybackHistoryStore.Item item = PlaybackHistoryStore.load(context).get(0);
        assertEquals(MEDIA, item.content.sourceMediaUrl);
        assertEquals(42_000L, LibraryPlayback.resumePosition(context, CLIP));
        assertTrue(PlaybackHistoryStore.continueWatchingShows(context).isEmpty());
    }

    @Test public void directProvidersKeepTheirOwnViewerAndAlbumActions() {
        assertEquals(LibraryPlayback.Route.GALLERY, LibraryPlayback.route(
                OnlyFapVideoMetadata.basic("Pawchive", "https://file.pawchive.pw/data/ab/cd/clip.mp4", "")));
        NativeContentItem bunkrAlbum = new NativeContentItem(NativeContentItem.KIND_SERIES, "Set",
                "https://bunkr.cr/a/collection", "", "", "", "", "Bunkr album");
        assertEquals(LibraryPlayback.Route.BUNKR_ALBUM, LibraryPlayback.route(bunkrAlbum));
        NativeContentItem bunkrVideo = new NativeContentItem(NativeContentItem.KIND_MEDIA, "Clip",
                "https://bunkr.cr/f/clip", "", "", "", "", "Bunkr")
                .withSourceMedia("https://cdn.example/clip.mp4", "https://bunkr.cr/a/collection");
        assertEquals(LibraryPlayback.Route.ONLYFAP_VIDEO, LibraryPlayback.route(bunkrVideo));
        assertEquals("https://bunkr.cr/a/collection", LibraryPlayback.bunkrAlbum(bunkrVideo));
        assertEquals("", LibraryPlayback.albumAction(1));
        assertEquals("View Album · 4", LibraryPlayback.albumAction(4));
        assertEquals("", LibraryPlayback.creatorAction(1));
        assertEquals("View Creator · 6", LibraryPlayback.creatorAction(6));
        assertEquals("View Creator", LibraryPlayback.creatorAction(-1));
    }

    @Test public void albumContextKeepsTheSavedItemWhenTheParentIsMissingOrLarger() {
        assertEquals(CLIP, LibraryPlayback.albumContext(clip(), null).get(0).url);
        java.util.List<NativeContentItem> unmatched = LibraryPlayback.albumContext(
                clip(), Collections.singletonList(sibling("Other")));
        assertEquals(CLIP, unmatched.get(0).url);
        assertEquals(2, unmatched.size());
        NativeContentItem listed = new NativeContentItem(NativeContentItem.KIND_MEDIA, "Listed",
                ALBUM + "#VideoToken", "", "", "creator", "", "0:12");
        assertEquals(1, LibraryPlayback.albumContext(clip(), Collections.singletonList(listed)).size());
        NativeContentItem image = new NativeContentItem(NativeContentItem.KIND_IMAGE, "Still",
                ALBUM + "#img-Still", "", "", "creator", "", "");
        NativeContentItem saved = clip();
        java.util.List<NativeContentItem> ordered = LibraryPlayback.albumContext(
                saved, Arrays.asList(sibling("Before"), listed, image));
        assertEquals(ALBUM + "#Before", ordered.get(0).url);
        NativeContentItem current = null;
        for (NativeContentItem item : ordered) if (CLIP.equals(item.url)) current = item;
        assertSame(saved, current);
        assertEquals(1, ordered.indexOf(current));
        assertEquals(ALBUM + "#img-Still", ordered.get(2).url);
        assertEquals(3, ordered.size());
        assertTrue(LibraryPlayback.sameMedia(clip(), listed));
        assertFalse(LibraryPlayback.viewAlbum(1));
    }

    @Test public void savedEromeClipIsSeededBeforeItsAlbumLoads() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LibraryPlayback.openMedia(activity, clip(), 1_200L);
        Intent intent = shadowOf(activity).getNextStartedActivity();
        assertEquals(BunkrGalleryActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals(CLIP, intent.getStringExtra(BunkrGalleryActivity.EXTRA_INITIAL_URL));
        assertEquals(ALBUM, intent.getStringExtra(BunkrGalleryActivity.EXTRA_ALBUM_URL));
        assertTrue(intent.getBooleanExtra(LibraryPlayback.EXTRA_ENRICH_ALBUM, false));
        assertEquals(1_200L, intent.getLongExtra(PlayerActivity.EXTRA_START_POSITION, -1L));
        BunkrGallerySessionStore.Snapshot snapshot = BunkrGallerySessionStore.snapshot(
                intent.getStringExtra(BunkrGalleryActivity.EXTRA_SESSION_ID));
        assertNotNull(snapshot);
        assertEquals(1, snapshot.items.size());
        assertEquals(MEDIA, snapshot.items.get(0).sourceMediaUrl);
    }

    @Test public void bunkrAlbumOpensTheAlbumWithoutResolvingPlayback() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        NativeContentItem album = new NativeContentItem(NativeContentItem.KIND_SERIES, "Set",
                "https://bunkr.cr/a/collection", "", "", "", "", "Bunkr album");
        LibraryPlayback.open(activity, album, -1L);
        Intent intent = shadowOf(activity).getNextStartedActivity();
        assertEquals(NativeFeedBrowserActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals(NativeFeedBrowserActivity.SOURCE_BUNKR, intent.getStringExtra(NativeFeedBrowserActivity.EXTRA_SOURCE));
        assertEquals(album.url, intent.getStringExtra(NativeFeedBrowserActivity.EXTRA_BASE_URL));
    }

    private static NativeContentItem sibling(String token) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, token, ALBUM + "#" + token,
                "", "", "creator", "", "");
    }

    private static NativeContentItem album() {
        return new NativeContentItem(NativeContentItem.KIND_SERIES, "Album", ALBUM,
                "", "", "creator", "", "2 videos");
    }

    private static NativeContentItem clip() {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, "Album", CLIP,
                "https://s1.erome.com/123/AbCd12/VideoToken.jpg", "", "creator", "", "0:12")
                .withSourceMedia(MEDIA, ALBUM);
    }
}
