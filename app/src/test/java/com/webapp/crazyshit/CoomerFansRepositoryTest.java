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
                        "<div class='post'><img src='https://evil.example/not-allowed.jpg'></div>" +
                        "<div class='post'><img src='https://img5.coomerfans.com/avatar/user.jpg'></div>",
                creator.url
        );

        ArrayList<NativeContentItem> items =
                repository.parseCreatorImages(document, config, creator, 24);

        assertEquals(2, items.size());
        assertTrue(items.get(0).isImage());
        assertEquals(creator.url, items.get(0).uploader);
        assertTrue(items.get(0).description.contains("CoomerFans"));
        assertTrue(CoomerFansRepository.isDirectImageUrl(items.get(0).url));
    }
}
