package com.webapp.crazyshit;

import java.util.Arrays;
import java.util.Collections;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Erome's ShitTok lane follows the site versions without touching Spotlight. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class EromeShitTokTest {
    private static final String ALBUM = "<html><body>"
            + "<h1 class=\"album-title-page\">Studio clip</h1>"
            + "<a id=\"user_name\" href=\"https://www.erome.com/SampleStudio\">SampleStudio</a>"
            + "<div class=\"media-group\"><div class=\"video\">"
            + "<video class=\"player video-js vjs-portrait\" poster=\"https://s1.erome.com/1/Abcd1234/clip.jpg\">"
            + "<source src=\"https://v1.erome.com/1/Abcd1234/clip_480p.mp4\" type=\"video/mp4\" res=\"480\">"
            + "<source src=\"https://v1.erome.com/1/Abcd1234/clip_720p.mp4\" type=\"video/mp4\" res=\"720\">"
            + "</video></div></div>"
            + "<div class=\"media-group\"><div class=\"img\">"
            + "<img class=\"img-front\" width=\"800\" height=\"1000\" "
            + "src=\"https://s1.erome.com/1/Abcd1234/still.jpg\">"
            + "</div></div></body></html>";

    @Test public void mapsOnlyTheThreeShitTokFeeds() {
        assertEquals("straight", EromeRepository.shitTokVersion(ContentPreference.WOMEN));
        assertEquals("gay", EromeRepository.shitTokVersion(ContentPreference.MEN));
        assertEquals("trans", EromeRepository.shitTokVersion(ContentPreference.TRANS_WOMEN));
        assertEquals("trans", EromeRepository.shitTokVersion(ContentPreference.TRANS_MEN));
        assertEquals("", EromeRepository.shitTokVersion(ContentPreference.ALL));
        assertEquals("", EromeRepository.shitTokVersion(ContentPreference.GROUPS));
        assertEquals("", EromeRepository.shitTokVersion(ContentPreference.PAWCHIVE));
        assertFalse(EromeRepository.shitTokVersionAllowed("hentai"));
        assertFalse(EromeRepository.shitTokVersionAllowed("all"));
        assertTrue(EromeRepository.shitTokVersionAllowed("straight"));
    }

    @Test public void refusedVersionsDoNotOpenTheNetwork() throws Exception {
        EromeRepository.ShitTokBatch hentai = new EromeRepository().videosForShitTok("hentai", 1);
        EromeRepository.ShitTokBatch chaos = new EromeRepository().videosForShitTok("all", 4);
        assertTrue(hentai.items.isEmpty());
        assertFalse(hentai.hasMore);
        assertTrue(chaos.items.isEmpty());
        assertEquals(4, chaos.nextPage);
    }

    @Test public void keepsPlayableVideosAndSkipsPhotosAndGifs() throws Exception {
        NativeContentItem photos = new NativeContentItem(
                NativeContentItem.KIND_SERIES, "photos", "https://www.erome.com/a/PhotoOnly",
                "", "", "", "", "4 pictures");
        NativeContentItem clips = new NativeContentItem(
                NativeContentItem.KIND_SERIES, "clips", "https://www.erome.com/a/Abcd1234",
                "", "", "", "", "1 video · 2 pictures");
        EromePages.SearchPage listing = new EromePages.SearchPage(Arrays.asList(photos, clips), true);
        EromeRepository.ShitTokBatch batch = EromeRepository.collectShitTokVideos(
                listing, 3, url -> {
                    assertEquals("https://www.erome.com/a/Abcd1234", url);
                    return org.jsoup.Jsoup.parse(ALBUM, EromeRepository.ORIGIN + "/");
                });
        assertEquals(1, batch.items.size());
        NativeContentItem video = batch.items.get(0);
        assertTrue(video.isVideo());
        assertEquals("https://www.erome.com/a/Abcd1234#clip", video.url);
        assertEquals("https://v1.erome.com/1/Abcd1234/clip_720p.mp4", video.sourceMediaUrl);
        assertEquals("1 video · 2 photos", video.galleryDetail);
        NativeContentItem photo = EromePages.parseAlbum(ALBUM, "https://www.erome.com/a/Abcd1234").items.get(1);
        NativeContentItem gif = video.withSourceMedia(
                "https://v1.erome.com/1/Abcd1234/anim.gif", "https://www.erome.com/a/Abcd1234");
        assertEquals(Collections.singletonList(video), EromeRepository.shitTokClips(Arrays.asList(video, photo, gif)));
        assertEquals(4, batch.nextPage);
        assertTrue(batch.hasMore);
        assertFalse(video.sourceMediaUrl.contains(".gif"));
        assertEquals("erome.com", ShitTokAspectPriority.sourceKey(video));
        assertTrue(ShitTokFeedSources.allows(ShitTokCategoryStrip.Tab.LADIES, "erome.com"));
        assertFalse(ShitTokFeedSources.allows(ShitTokCategoryStrip.Tab.CHAOS, "erome.com"));
        assertFalse(ShitTokFeedSources.allows(ShitTokCategoryStrip.Tab.GROUPS, "erome.com"));
    }

    @Test public void playbackUsesTheKnownVideoWithoutTheSpotlightCookie() throws Exception {
        NativeContentItem video = EromePages.parseAlbum(ALBUM, "https://www.erome.com/a/Abcd1234").items.get(0);
        CrazyShitRepository.StreamInfo stream = PlayableSourceRouter.resolve(null, video);
        assertEquals(video.sourceMediaUrl, stream.mediaUrl);
        assertEquals("https://www.erome.com/a/Abcd1234", stream.requestReferer);
    }

    @Test public void cookieHeaderStaysSeparateFromAttributes() {
        String header = EromeRepository.cookieHeader(Arrays.asList(
                "erome_version=encrypted; Path=/; HttpOnly",
                "other=1; Secure",
                "erome_version=newer; Path=/"));
        assertEquals("erome_version=newer; other=1", header);
        assertEquals("", EromeRepository.cookieHeader(Collections.emptyList()));
    }

    @Test public void stopsAtThePageCap() throws Exception {
        NativeContentItem clips = new NativeContentItem(
                NativeContentItem.KIND_SERIES, "clips", "https://www.erome.com/a/Abcd1234",
                "", "", "", "", "1 video");
        EromeRepository.ShitTokBatch batch = EromeRepository.collectShitTokVideos(
                new EromePages.SearchPage(Collections.singletonList(clips), true),
                EromeRepository.SHITTOK_MAX_PAGE,
                url -> org.jsoup.Jsoup.parse(ALBUM, EromeRepository.ORIGIN + "/"));
        assertFalse(batch.hasMore);
        assertEquals(EromeRepository.SHITTOK_MAX_PAGE, batch.nextPage);
        assertEquals(1, batch.items.size());
    }
}
