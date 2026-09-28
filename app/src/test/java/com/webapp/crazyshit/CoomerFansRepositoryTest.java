package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public final class CoomerFansRepositoryTest {
    private SourceConfig.CoomerFans config;
    private CoomerFansRepository repository;

    @Before public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("remote_source_config", Context.MODE_PRIVATE)
                .edit().clear().commit();
        RemoteSourceConfigManager.resetForTests();
        RemoteSourceConfigManager.initialize(context);
        config = RemoteSourceConfigManager.snapshot().coomerFans;
        repository = new CoomerFansRepository();
    }

    @Test public void creatorSearchParsesMatchingProfileOnly() {
        Document document = Jsoup.parse(
                "<section><h2>Names of Models - Sophie Rain. Total 2</h2>" +
                        "<div class='thumb'><a href='/u/onlyfans/123/sophie_rain'>" +
                        "<span>Sophie Rain</span></a></div>" +
                        "<div class='thumb'><a href='/u/fansly/999/unrelated'>" +
                        "<span>Unrelated Creator</span></a></div></section>",
                "https://coomerfans.com/?q=Sophie%20Rain"
        );

        List<CoomerFansRepository.Creator> creators =
                repository.parseCreators(document, config, "Sophie Rain", 4);

        assertEquals(1, creators.size());
        assertEquals("onlyfans", creators.get(0).service);
        assertEquals("123", creators.get(0).id);
        assertEquals("sophie_rain", creators.get(0).username);
        assertEquals("https://coomerfans.com/u/onlyfans/123/sophie_rain",
                creators.get(0).url);
    }

    @Test public void creatorSearchAllowsOneCharacterHandleAlias() {
        Document document = Jsoup.parse(
                "<section><div class='thumb'><a href='/u/onlyfans/456/sophieraiin'>" +
                        "<span>sophieraiin</span></a></div></section>",
                "https://coomerfans.com/?q=Sophie%20Rain"
        );

        List<CoomerFansRepository.Creator> creators =
                repository.parseCreators(document, config, "Sophie Rain", 1);

        assertEquals(1, creators.size());
        assertEquals("sophieraiin", creators.get(0).username);
    }

    @Test public void profileParserFindsPostLinksBeforeMediaFetch() {
        Document document = Jsoup.parse(
                "<a href='/p/111/123/onlyfans'>Post one</a>" +
                        "<a href='/p/222/123/onlyfans'>Post two</a>" +
                        "<a href='/u/onlyfans/123/sophie_rain'>Profile</a>",
                "https://coomerfans.com/u/onlyfans/123/sophie_rain"
        );

        ArrayList<String> posts = repository.parseProfilePostUrls(document, 12);

        assertEquals(2, posts.size());
        assertEquals("https://coomerfans.com/p/111/123/onlyfans", posts.get(0));
        assertEquals("https://coomerfans.com/p/222/123/onlyfans", posts.get(1));
    }

    @Test public void creatorSearchKeepsServerFilteredAliasWhenNameChanged() {
        Document document = Jsoup.parse(
                "<section><div class='thumb'><a href='/u/onlyfans/456/mysticsoles444'>" +
                        "<span>MysticSoles444</span></a></div></section>",
                "https://coomerfans.com/?q=mysticsiren444"
        );

        List<CoomerFansRepository.Creator> creators =
                repository.parseCreators(document, config, "mysticsiren444", 1);

        assertEquals(1, creators.size());
        assertEquals("mysticsoles444", creators.get(0).username);
    }

    @Test public void postParserReadsHrefDataSrcAndSrcsetMedia() {
        CoomerFansRepository.Creator creator = new CoomerFansRepository.Creator(
                "onlyfans",
                "123",
                "mysticsoles444",
                "MysticSoles444",
                "https://coomerfans.com/u/onlyfans/123/mysticsoles444",
                ""
        );
        Document document = Jsoup.parse(
                "<div class='post-wrap'>" +
                        "<a href='/data/a/photo-one.jpg'>one</a>" +
                        "<img data-src='https://storage.coomerfans.com/b/photo-two.webp'>" +
                        "<source srcset='https://img5.coomerfans.com/c/opaque-id 1x'>" +
                        "<img src='/istorage/123.jpg'>" +
                        "</div>",
                "https://coomerfans.com/p/999/123/onlyfans"
        );

        ArrayList<NativeContentItem> items =
                repository.parseCreatorMedia(
                        document,
                        config,
                        creator,
                        "https://coomerfans.com/p/999/123/onlyfans",
                        12
                );

        assertEquals(3, items.size());
        assertTrue(items.get(0).isImage());
        assertTrue(items.get(1).isImage());
        assertTrue(items.get(2).isImage());
    }

    @Test public void postParserIncludesDirectCoomerFansVideos() {
        CoomerFansRepository.Creator creator = new CoomerFansRepository.Creator(
                "onlyfans",
                "123",
                "mysticsiren444",
                "MysticSiren444",
                "https://coomerfans.com/u/onlyfans/123/mysticsiren444",
                ""
        );
        String postUrl = "https://coomerfans.com/p/999/123/onlyfans";
        Document document = Jsoup.parse(
                "<div class='post-wrap'>" +
                        "<video poster='https://img5.coomerfans.com/poster.jpg'>" +
                        "<source src='https://storage.coomerfans.com/video/clip.mp4'>" +
                        "</video>" +
                        "</div>",
                postUrl
        );

        ArrayList<NativeContentItem> items =
                repository.parseCreatorMedia(document, config, creator, postUrl, 12);

        assertEquals(1, items.size());
        NativeContentItem video = null;
        for (NativeContentItem item : items) if (item.isVideo()) video = item;
        assertTrue(video != null);
        assertEquals(postUrl, video.comments);
        assertEquals("https://img5.coomerfans.com/poster.jpg", video.imageUrl);
        assertTrue(CoomerFansRepository.isDirectVideoUrl(video.url));
    }

    @Test public void profileParserKeepsOnlyCoomerFansImages() {
        CoomerFansRepository.Creator creator = new CoomerFansRepository.Creator(
                "onlyfans",
                "123",
                "sophie_rain",
                "Sophie Rain",
                "https://coomerfans.com/u/onlyfans/123/sophie_rain",
                ""
        );
        Document document = Jsoup.parse(
                "<div class='post'><img src='https://img5.coomerfans.com/a/photo-one.jpg'></div>" +
                        "<div class='post'><img src='https://img10.coomerfans.com/b/photo-two.webp'></div>" +
                        "<div class='post'><img src='https://img3.coomerfans.com/c/opaque-media-id'></div>" +
                        "<div class='post'><img src='https://evil.example/not-allowed.jpg'></div>" +
                        "<div class='post'><img src='https://img5.coomerfans.com/avatar/user.jpg'></div>",
                creator.url
        );

        ArrayList<NativeContentItem> items =
                repository.parseCreatorMedia(
                        document,
                        config,
                        creator,
                        "https://coomerfans.com/p/999/123/onlyfans",
                        24
                );

        assertEquals(3, items.size());
        assertTrue(items.get(0).isImage());
        assertEquals(creator.url, items.get(0).uploader);
        assertTrue(items.get(0).description.contains("CoomerFans"));
        assertTrue(CoomerFansRepository.isDirectImageUrl(items.get(0).url));
    }
}
