package com.webapp.crazyshit;

import android.app.Application;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class OnlyHavenHtmlFallbackTest {
    private JSONObject bundled() throws Exception {
        try (var input = RuntimeEnvironment.getApplication().getAssets().open("source_config_defaults.json")) {
            return new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test public void bundledPatternsMatchCanonicalCreatorAndScriptMedia() throws Exception {
        SourceConfig.OnlyHaven config = SourceConfig.parseAndValidate(bundled().toString()).onlyHaven;
        assertTrue(config.creatorUrlPattern.matcher("https://cum.st/creators/onlyfans/123").matches());
        assertTrue(config.scriptMediaUrlPattern.matcher("https://cdn.cum.st/media/clip.mp4").find());
    }

    @Test public void htmlParserKeepsCanonicalCreatorsAndRejectsForeignLinks() throws Exception {
        JSONObject document = bundled();
        SourceConfig.OnlyHaven config = SourceConfig.parseAndValidate(document.toString()).onlyHaven;
        String html = "<a href='/creators/onlyfans/123'>Nolan</a>"
                + "<a href='/creators/fansly/abc-12/?page=1'>Other creator</a>"
                + "<a href='https://foreign.example/creators/onlyfans/456'>Foreign</a>"
                + "<a href='https://cum.st@foreign.example/creators/onlyfans/456'>User info</a>"
                + "<a href='/creators/onlyfans/%2e%2e'>Encoded traversal</a>"
                + "<a href='/creators/onlyfans/123/extra'>Not canonical</a>";
        LinkedHashMap<String, OnlyHavenRepository.Creator> creators = new LinkedHashMap<>();
        var parser = OnlyHavenRepository.class.getDeclaredMethod("parseCreators",
                org.jsoup.nodes.Document.class, SourceConfig.OnlyHaven.class, LinkedHashMap.class, int.class);
        parser.setAccessible(true);
        parser.invoke(new OnlyHavenRepository(), Jsoup.parse(html, config.baseUrl), config, creators, 20);
        assertEquals(2, creators.size());
        assertEquals("Nolan", creators.get("onlyfans:123").name);
        assertEquals("abc-12", creators.get("fansly:abc-12").id);
    }
}
