package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;

import org.jsoup.Jsoup;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public final class SourceExpansionTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("remote_source_config", Context.MODE_PRIVATE)
                .edit().clear().commit();
        RemoteSourceConfigManager.resetForTests();
        RemoteSourceConfigManager.initialize(context);
    }

    @After public void tearDown() {
        context.getSharedPreferences("remote_source_config", Context.MODE_PRIVATE)
                .edit().clear().commit();
        RemoteSourceConfigManager.resetForTests();
    }

    @Test public void kaoticParserKeepsDateSectionHeaders() {
        String html = "<main>"
                + "<h2>Sunday September 21st, 2026</h2>"
                + "<article><a href='/video/sample-one' title='Sample one'>"
                + "<img src='/thumb1.jpg'></a></article>"
                + "<h2>Saturday September 20th, 2026</h2>"
                + "<article><a href='/video/sample-two' title='Sample two'>"
                + "<img src='/thumb2.jpg'></a></article>"
                + "</main>";
        List<NativeContentItem> items = new WebVideoSourceRepository().parseFeed(
                Jsoup.parse(html, "https://kaotic.com/"),
                WebVideoSourceRepository.Source.KAOTIC,
                1
        );
        assertEquals(4, items.size());
        assertTrue(items.get(0).isSection());
        assertEquals("Sunday September 21st, 2026", items.get(0).title);
        assertEquals("https://kaotic.com/video/sample-one", items.get(1).url);
        assertTrue(items.get(2).isSection());
        assertEquals("Saturday September 20th, 2026", items.get(2).title);
    }

    @Test public void onlyHavenCreatorSearchApiFindsCanonicalCreator() throws Exception {
        OnlyHavenRepository repository = new OnlyHavenRepository();
        SourceConfig.OnlyHaven config = RemoteSourceConfigManager.snapshot().onlyHaven;
        assertEquals("api/v1/creators?q={query}&n={limit}&o={offset}",
                config.creatorSearchApiRoute);
        String json = "{\"creators\":[{"
                + "\"id\":\"30340311\","
                + "\"name\":\"samplecreator\","
                + "\"displayName\":\"Sample Creator\","
                + "\"service\":\"onlyfans\"}]}";

        List<OnlyHavenRepository.Creator> creators =
                repository.parseCreatorSearchJson(json, config, "Sample Creator", 4);

        assertEquals(1, creators.size());
        assertEquals("onlyfans", creators.get(0).service);
        assertEquals("30340311", creators.get(0).id);
        assertEquals("Sample Creator", creators.get(0).name);
        assertEquals("samplecreator", creators.get(0).searchAlias);
        assertEquals("https://cum.st/creators/onlyfans/30340311", creators.get(0).url);
    }

    @Test public void onlyHavenSearchRoutesKeepProviderQueryAndCategoryTokens() throws Exception {
        SourceConfig.OnlyHaven config = RemoteSourceConfigManager.snapshot().onlyHaven;
        assertEquals("creators?cq={query}", config.creatorSearchRoute);
        assertEquals("api/v1/creators?q={query}&gender={category}&content=imported&n={limit}&o={offset}",
                config.creatorCategorySearchApiRoute);
        String json = "{\"creators\":[{\"service\":\"onlyfans\",\"id\":\"nolan-1\","
                + "\"displayName\":\"Nolan\",\"username\":\"nolantwink\"},{\"service\":\"onlyfans\","
                + "\"id\":\"unrelated\",\"displayName\":\"Other Creator\"}]}";
        List<OnlyHavenRepository.Creator> result = new OnlyHavenRepository()
                .parseCreatorSearchJson(json, config, "nolantwink", 20);
        assertEquals(1, result.size());
        assertEquals("https://cum.st/creators/onlyfans/nolan-1", result.get(0).url);
        assertEquals("Nolan", result.get(0).name);
        NativeContentItem item = FapzoneCreatorSearchRepository.onlyHavenItem(result.get(0), "nolantwink");
        assertEquals("nolantwink", item.searchQuery);
        CreatorCatalog.remember(context, java.util.Collections.singletonList(item));
        assertEquals(item.url, CreatorCatalog.matching(context, "nolantwink", false, 10).get(0).url);
    }

    @Test public void currentClientParsesLiveOnlyHavenSearchAndMenCategory() throws Exception {
        assumeTrue("true".equals(System.getenv("VERIFY_PRODUCTION_SOURCE_CONFIG")));
        OnlyHavenRepository repository = new OnlyHavenRepository();
        SourceConfig.OnlyHaven config = RemoteSourceConfigManager.snapshot().onlyHaven;
        List<OnlyHavenRepository.Creator> broad = repository.searchCreators(context, "nolantwink", 20);
        OnlyHavenRepository.Creator nolan = null;
        for (OnlyHavenRepository.Creator creator : broad) {
            if (CreatorNameMatcher.rank(creator.searchAlias, "nolantwink") != Integer.MAX_VALUE
                    || CreatorNameMatcher.rank(creator.name, "nolantwink") != Integer.MAX_VALUE
                    || "nolantwink".equalsIgnoreCase(creator.id)) { nolan = creator; break; }
        }
        assertNotNull("Query search should return its canonical creator", nolan);
        assertTrue(OnlyHavenRepository.isOnlyHavenUrl(nolan.url));
        assertTrue(nolan.url.startsWith(config.baseUrl + "creators/"));
        assertTrue(nolan.service.matches("[A-Za-z0-9_-]+"));
        assertTrue(nolan.id.matches("[A-Za-z0-9_-]+"));
        assertFalse("Live result should include avatar metadata", nolan.imageUrl.isEmpty());
        String canonicalUrl = nolan.url;
        List<OnlyHavenRepository.Creator> men = repository.searchCategoryCreators(
                context, "nolantwink", 20, ContentPreference.MEN);
        assertTrue("The provider must confirm Men membership", men.stream()
                .anyMatch(creator -> creator.url.equals(canonicalUrl)));
        assertFalse("Second stable query must produce creator results",
                repository.searchCreators(context, "Nolan", 20).isEmpty());
    }

    @Test public void brokenOnlyHavenApiEnvelopeIsUnavailableRatherThanConfirmedEmpty() throws Exception {
        try {
            new OnlyHavenRepository().parseCreatorSearchJson("{\"error\":\"Site Unavailable\"}",
                    RemoteSourceConfigManager.snapshot().onlyHaven, "nolantwink", 20);
            fail("Malformed API envelope must not claim no creator matches");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("unreadable JSON"));
        }
    }

    @Test public void malformedOnlyHavenIdentityCannotBeLearnedAsCanonicalCreator() throws Exception {
        SourceConfig.OnlyHaven config = RemoteSourceConfigManager.snapshot().onlyHaven;
        String json = "{\"creators\":["
                + "{\"service\":\"onlyfans/other\",\"id\":\"nolan-1\",\"displayName\":\"Nolan\"},"
                + "{\"service\":\"onlyfans\",\"id\":\"nolan 2\",\"displayName\":\"Nolan\"},"
                + "{\"service\":\"onlyfans\",\"id\":\"nolan-1\",\"displayName\":\"Nolan\",\"username\":\"nolantwink\"}]}";
        List<OnlyHavenRepository.Creator> found = new OnlyHavenRepository()
                .parseCreatorSearchJson(json, config, "nolantwink", 20);
        assertEquals(1, found.size());
        assertEquals("https://cum.st/creators/onlyfans/nolan-1", found.get(0).url);
        assertEquals("nolantwink", found.get(0).searchAlias);
    }

    @Test public void onlyHavenApiBuildsOriginalVariantMediaUrls() throws Exception {
        OnlyHavenRepository repository = new OnlyHavenRepository();
        OnlyHavenRepository.Creator creator = new OnlyHavenRepository.Creator(
                "onlyfans", "12345", "Sample Creator",
                "https://cum.st/creators/onlyfans/12345", ""
        );
        SourceConfig.OnlyHaven config = RemoteSourceConfigManager.snapshot().onlyHaven;
        String videoKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        String imageKey = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
        String json = "{\"total\":2,\"posts\":["
                + "{\"title\":\"Video post\",\"attachments\":["
                + "{\"storageKey\":\"" + videoKey + "\","
                + "\"mimeType\":\"video/mp4\","
                + "\"variants\":[{\"name\":\"720p.mp4\"},{\"name\":\"original.mp4\"}]}]},"
                + "{\"title\":\"Image post\",\"file\":"
                + "{\"storageKey\":\"" + imageKey + "\","
                + "\"mimeType\":\"image/jpeg\","
                + "\"variants\":[{\"name\":\"original.jpg\"}]}}]}";

        List<NativeContentItem> items =
                repository.parseCreatorMediaJson(json, config, creator, 10);

        assertEquals(2, items.size());
        assertEquals(NativeContentItem.KIND_MEDIA, items.get(0).kind);
        assertEquals("https://e1.cum.st/media/" + videoKey + "/original.mp4", items.get(0).url);
        assertEquals("https://img.cum.st/thumbnail/" + videoKey + "/preview.webp",
                items.get(0).imageUrl);
        assertEquals(NativeContentItem.KIND_IMAGE, items.get(1).kind);
        assertEquals("https://e1.cum.st/media/" + imageKey + "/original.jpg", items.get(1).url);
        assertEquals("https://img.cum.st/thumbnail/" + imageKey + "/preview.webp",
                items.get(1).imageUrl);
    }

    @Test public void onlyHavenDirectImagesAreRecognizedWithoutPageResolution() {
        assertTrue(OnlyHavenRepository.isDirectImageUrl(
                "https://e1.cum.st/media/abc/original.jpg"
        ));
        assertTrue(OnlyHavenRepository.isOnlyHavenUrl(
                "https://e1.cum.st/media/abc/original.jpg"
        ));
        assertFalse(OnlyHavenRepository.isDirectImageUrl(
                "https://e1.cum.st/media/abc/original.mp4"
        ));
    }

}
