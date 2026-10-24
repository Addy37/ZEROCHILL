package com.webapp.crazyshit;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class XMegaDriveRepositoryTest {
    private static final String PAGE =
            "https://www.xmegadrive.com/videos/example-clip/";
    private static final String FEMDOM =
            "https://www.xmegadrive.com/categories/femdom/";

    @Test public void browserIdentityDropsWebViewMarker() {
        String raw = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/UP1A.231005.007; wv) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 "
                + "Chrome/131.0.6778.135 Mobile Safari/537.36";
        assertEquals(
                "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/UP1A.231005.007) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/131.0.6778.135 Mobile Safari/537.36",
                XMegaDriveRepository.browserIdentity(raw)
        );
        assertEquals("", XMegaDriveRepository.browserIdentity(null));
        assertEquals("", XMegaDriveRepository.browserIdentity("  "));
    }

    @Test public void cookiePresenceReportsNamesWithoutValues() {
        String header = "cf_clearance=secret-value; __cf_bm=other-secret; theme=dark";
        String report = XMegaDriveRepository.cookiePresence(header);
        assertEquals("cf_clearance=present __cf_bm=present", report);
        assertFalse(report.contains("secret"));
        assertFalse(report.contains("dark"));
        assertEquals(
                "cf_clearance=absent __cf_bm=absent",
                XMegaDriveRepository.cookiePresence(null)
        );
        assertEquals(
                "cf_clearance=absent __cf_bm=present",
                XMegaDriveRepository.cookiePresence("__cf_bm=value")
        );
        assertEquals(
                "cf_clearance=absent __cf_bm=absent",
                XMegaDriveRepository.cookiePresence("cf_clearance=")
        );
    }

    @Test public void acceptsOnlyXMegaDriveHosts() {
        assertTrue(XMegaDriveRepository.isXMegaDriveUrl(PAGE));
        assertTrue(XMegaDriveRepository.isXMegaDriveUrl("https://xmegadrive.com/videos/example/"));
        assertFalse(XMegaDriveRepository.isXMegaDriveUrl("https://baddiehub.com/videos/example/"));
        assertFalse(XMegaDriveRepository.isXMegaDriveUrl("https://example.com/videos/example/"));
        assertFalse(XMegaDriveRepository.isXMegaDriveUrl("http://www.xmegadrive.com/videos/example/"));
        assertFalse(XMegaDriveRepository.isXMegaDriveUrl(null));
        assertFalse(XMegaDriveRepository.isXMegaDriveUrl(""));
    }

    @Test public void listingFixtureExtractsVideoPageAndSkipsNonVideoLinks() {
        String html = ""
                + "<a href=\"/videos/example-clip/?ref=home\">"
                + "<img src=\"/contents/videos_screenshots/1/2/preview.jpg\" alt=\"Ignored Alt\">"
                + "Example Clip HD 1:02"
                + "</a>"
                + "<a href=\"/videos/example-clip/\">Example Clip duplicate</a>"
                + "<a href=\"/videos/?page=2\">Next</a>"
                + "<a href=\"/categories/femdom/\">Femdom</a>"
                + "<a href=\"https://ads.example/out\">Ad</a>";

        List<NativeContentItem> items = XMegaDriveRepository.parseListingHtml(html);

        assertEquals(1, items.size());
        NativeContentItem item = items.get(0);
        assertEquals(PAGE, item.url);
        assertEquals("Example Clip HD 1:02", item.title);
        assertEquals("https://www.xmegadrive.com/contents/videos_screenshots/1/2/preview.jpg", item.imageUrl);
        assertEquals(XMegaDriveRepository.LABEL, item.uploader);
        assertTrue(item.isVideo());
    }

    @Test public void emptyListingFixtureReturnsNothing() {
        assertTrue(XMegaDriveRepository.parseListingHtml("<div>no cards</div>").isEmpty());
        assertTrue(XMegaDriveRepository.parseListingHtml("").isEmpty());
        assertTrue(XMegaDriveRepository.parseListingHtml(null).isEmpty());
    }

    @Test public void playerFixtureExtractsGetFileMp4() {
        String html = ""
                + "<h1>Example Clip</h1>"
                + "<video src=\"/get_file/4/abc/459000/459766/459766.mp4/?v-acctoken=token\">"
                + "<source src=\"/ignored.mp4\">"
                + "</video>";

        assertEquals(
                "https://www.xmegadrive.com/get_file/4/abc/459000/459766/459766.mp4/?v-acctoken=token",
                XMegaDriveRepository.mediaFromHtml(html, PAGE)
        );
    }

    @Test public void playerFixtureExtractsSourceMp4WhenVideoSrcMissing() {
        String html = "<video><source src=\"/get_file/1/deadbeef/100/101/101.mp4/\"></video>";

        assertEquals(
                "https://www.xmegadrive.com/get_file/1/deadbeef/100/101/101.mp4/",
                XMegaDriveRepository.mediaFromHtml(html, PAGE)
        );
    }

    @Test public void playerFixtureDecodesObfuscatedGetFileUrl() {
        String html = ""
                + "<script>var flashvars = {"
                + "video_url: 'function/0/https://www.xmegadrive.com/get_file/4/0123456789abcdef0123456789abcdefEXTRA/459000/459766/459766.mp4/',"
                + "license_code: '86123456789012345678901234567890'"
                + "};</script>";
        assertEquals(
                "https://www.xmegadrive.com/get_file/4/29bb1ccd988407a16f753540deea2f36EXTRA/459000/459766/459766.mp4/",
                XMegaDriveRepository.mediaFromHtml(html, PAGE)
        );
    }

    @Test public void categoryArtworkPrefersBackgroundAndSkipsPlaceholder() {
        String html = ""
                + "<a href=\"/categories/hairy/\">"
                + "<img data-bg=\"/contents/categories/9/s1_cover.jpg\" src=\"/blank.gif\">"
                + "<strong class=\"title\">Hairy</strong>"
                + "</a>"
                + "<a href=\"/categories/feet/\">"
                + "<img src=\"https://www.xmegadrive.com/categories/feet/\" "
                + "data-original=\"https://cdn.example/placeholder.jpg\">"
                + "<strong class=\"title\">Feet</strong>"
                + "</a>";
        List<NativeContentItem> items = XMegaDriveRepository.parseCategoriesHtml(html);
        assertEquals(2, items.size());
        assertEquals("Hairy", items.get(0).title);
        assertEquals(
                "https://www.xmegadrive.com/contents/categories/9/s1_cover.jpg",
                items.get(0).imageUrl
        );
        assertEquals("", items.get(1).imageUrl);
    }

    @Test public void playerFixtureWithoutMp4ReturnsEmpty() {
        assertEquals("", XMegaDriveRepository.mediaFromHtml("<h1>No player</h1>", PAGE));
        assertEquals("", XMegaDriveRepository.mediaFromHtml("", PAGE));
        assertEquals("", XMegaDriveRepository.mediaFromHtml(null, PAGE));
    }

    @Test public void challengeHtmlIsBlockedAndYieldsNoCategories() {
        String challenge = ""
                + "<html><head><title>Just a moment...</title></head><body>"
                + "Checking your browser before accessing www.xmegadrive.com"
                + "<script src=\"https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/g/orchestrate/chl_page\"></script>"
                + "</body></html>";
        assertTrue(XMegaDriveRepository.looksBlocked(challenge));
        assertTrue(XMegaDriveRepository.parseCategoriesHtml(challenge).isEmpty());
        assertFalse(XMegaDriveRepository.looksBlocked(categoryDirectoryHtml()));
    }

    @Test public void categoryDiscoveryUsesRealCategoryCardsAndArtwork() {
        List<NativeContentItem> items = XMegaDriveRepository.parseCategoriesHtml(categoryDirectoryHtml());

        assertEquals(3, items.size());
        NativeContentItem femdom = items.get(0);
        assertEquals(NativeContentItem.KIND_CATEGORY, femdom.kind);
        assertEquals("FemDom", femdom.title);
        assertEquals(FEMDOM, femdom.url);
        assertEquals(
                "https://www.xmegadrive.com/contents/categories/10/s1_cover.jpg",
                femdom.imageUrl
        );
        assertEquals(XMegaDriveRepository.LABEL, femdom.uploader);

        NativeContentItem tickling = items.get(1);
        assertEquals("Tickling", tickling.title);
        assertEquals("https://www.xmegadrive.com/categories/tickling/", tickling.url);
        assertEquals("", tickling.imageUrl);

        NativeContentItem dildos = items.get(2);
        assertEquals("Big Dildos", dildos.title);
        assertEquals("https://www.xmegadrive.com/categories/big-dildos/", dildos.url);
        assertEquals(
                "https://www.xmegadrive.com/contents/categories/4/cover.jpg",
                dildos.imageUrl
        );
        assertFalse(dildos.imageUrl.contains("data:image"));
    }

    @Test public void categoryDiscoveryFiltersDuplicatesAndNonCategories() {
        List<NativeContentItem> items = XMegaDriveRepository.parseCategoriesHtml(categoryDirectoryHtml());
        assertEquals(1, items.stream().filter(item -> FEMDOM.equals(item.url)).count());
        for (NativeContentItem item : items) {
            assertTrue(item.url.contains("/categories/"));
            assertFalse(item.url.contains("/tags/"));
            assertFalse(item.url.contains("/videos/"));
            assertFalse(item.url.contains("/playlists/"));
            assertFalse(item.url.endsWith("/categories/"));
            assertFalse(item.url.contains("/categories/2/"));
            assertFalse(item.title.contains("videos"));
        }
    }

    @Test public void categoryUrlsNormalizeHostSlugAndQuery() {
        assertEquals(FEMDOM, XMegaDriveRepository.normalizeCategoryUrl(
                "https://xmegadrive.com/categories/FemDom/?ref=nav"));
        assertEquals(FEMDOM, XMegaDriveRepository.normalizeCategoryUrl(
                "https://www.xmegadrive.com/categories/femdom"));
        assertEquals("", XMegaDriveRepository.normalizeCategoryUrl(
                "https://www.xmegadrive.com/categories/"));
        assertEquals("", XMegaDriveRepository.normalizeCategoryUrl(
                "https://www.xmegadrive.com/categories/2/"));
        assertEquals("", XMegaDriveRepository.normalizeCategoryUrl(
                "https://www.xmegadrive.com/categories/femdom/2/"));
        assertEquals("", XMegaDriveRepository.normalizeCategoryUrl(
                "https://www.xmegadrive.com/tags/femdom/"));
        assertEquals("", XMegaDriveRepository.normalizeCategoryUrl(
                "https://www.xmegadrive.com/videos/example-clip/"));
        assertEquals("", XMegaDriveRepository.normalizeCategoryUrl(
                "http://www.xmegadrive.com/categories/femdom/"));
        assertEquals("", XMegaDriveRepository.normalizeCategoryUrl(
                "https://ads.example/categories/femdom/"));
        assertEquals("", XMegaDriveRepository.normalizeCategoryUrl(null));
        assertFalse(XMegaDriveRepository.isCategoryUrl("https://www.xmegadrive.com/videos/example/"));
        assertTrue(XMegaDriveRepository.isCategoryUrl(FEMDOM));
    }

    @Test public void categoryPageUrlUsesPathPagination() {
        assertEquals(FEMDOM, XMegaDriveRepository.categoryPageUrl(FEMDOM, 1));
        assertEquals(FEMDOM, XMegaDriveRepository.categoryPageUrl(
                "https://xmegadrive.com/categories/femdom/?from=9", 0));
        assertEquals(
                "https://www.xmegadrive.com/categories/femdom/2/",
                XMegaDriveRepository.categoryPageUrl(FEMDOM, 2)
        );
        assertEquals("", XMegaDriveRepository.categoryPageUrl(PAGE, 2));
        assertEquals("", XMegaDriveRepository.categoryPageUrl("", 1));
    }

    @Test public void categoryPageExtractsVideosAndPrefersLazyArtwork() {
        List<NativeContentItem> items = XMegaDriveRepository.parseCategoryVideosHtml(categoryPageHtml());

        assertEquals(2, items.size());
        NativeContentItem first = items.get(0);
        assertEquals("https://www.xmegadrive.com/videos/clip-one/", first.url);
        assertEquals("Clip One", first.title);
        assertEquals(
                "https://www.xmegadrive.com/contents/videos_screenshots/1/1/320x180/1.jpg",
                first.imageUrl
        );
        assertTrue(first.isVideo());
        assertEquals(XMegaDriveRepository.LABEL, first.uploader);

        NativeContentItem second = items.get(1);
        assertEquals("https://www.xmegadrive.com/videos/clip-two/", second.url);
        assertEquals("Clip Two", second.title);
        assertEquals(
                "https://www.xmegadrive.com/contents/videos_screenshots/2/2/preview.jpg",
                second.imageUrl
        );
    }

    @Test public void categoryPageFiltersDuplicateVideos() {
        List<NativeContentItem> items = XMegaDriveRepository.parseCategoryVideosHtml(categoryPageHtml());
        assertEquals(1, items.stream()
                .filter(item -> "https://www.xmegadrive.com/videos/clip-one/".equals(item.url))
                .count());
    }

    @Test public void malformedCategoryHtmlDoesNotThrow() {
        String broken = "<a href=\"/categories/femdom/\"><strong class=\"title\">FemDom";
        List<NativeContentItem> categories = XMegaDriveRepository.parseCategoriesHtml(broken);
        assertEquals(1, categories.size());
        assertEquals(FEMDOM, categories.get(0).url);

        List<NativeContentItem> videos = XMegaDriveRepository.parseCategoryVideosHtml(
                "<a href=\"/videos/broken<div>no");
        assertTrue(videos.size() <= 1);
        assertEquals("", XMegaDriveRepository.mediaFromHtml("<video src=\"/nope", PAGE));
    }

    @Test public void emptyCategoryPageReturnsNothing() {
        assertTrue(XMegaDriveRepository.parseCategoryVideosHtml(
                "<div class=\"list-videos\"></div>").isEmpty());
        assertTrue(XMegaDriveRepository.parseCategoryVideosHtml("").isEmpty());
        assertTrue(XMegaDriveRepository.parseCategoryVideosHtml(null).isEmpty());
        assertTrue(XMegaDriveRepository.parseCategoriesHtml("<div>no categories</div>").isEmpty());
        assertTrue(XMegaDriveRepository.parseCategoriesHtml(null).isEmpty());
    }

    private static String categoryDirectoryHtml() {
        return ""
                + "<a class=\"item\" href=\"/categories/femdom/?ref=home\" title=\"FemDom\">"
                + "<div class=\"img\"><img class=\"thumb\" src=\"/contents/categories/10/s1_cover.jpg\" alt=\"FemDom\"></div>"
                + "<strong class=\"title\">FemDom</strong>"
                + "<div class=\"wrap\"><div class=\"videos\">205796 videos</div><div class=\"rating\">94%</div></div>"
                + "</a>"
                + "<a class=\"item\" href=\"https://xmegadrive.com/categories/femdom/\">FemDom duplicate</a>"
                + "<a class=\"item\" href=\"https://xmegadrive.com/categories/Tickling/\" title=\"Tickling\">"
                + "<span class=\"no-thumb\">no image</span>"
                + "<strong class=\"title\">Tickling</strong>"
                + "<div class=\"videos\">1744 videos</div>"
                + "</a>"
                + "<a class=\"item\" href=\"/categories/\">All categories</a>"
                + "<a class=\"item\" href=\"/categories/2/\">2</a>"
                + "<a href=\"/tags/femdom/\">Femdom tag</a>"
                + "<a href=\"/videos/example-clip/\">Example clip</a>"
                + "<a href=\"/playlists/\">Playlists</a>"
                + "<a href=\"https://ads.example/categories/femdom/\">External</a>"
                + "<a class=\"item\" href=\"/categories/big-dildos/\">"
                + "<img src=\"data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7\" "
                + "data-original=\"/contents/categories/4/cover.jpg\" alt=\"ignored\">"
                + "<strong class=\"title\">Big Dildos</strong>"
                + "<div class=\"videos\">10 videos</div>"
                + "</a>";
    }

    private static String categoryPageHtml() {
        return ""
                + "<div class=\"item\"><a href=\"/videos/clip-one/?ref=1\" title=\"Clip One attribute\">"
                + "<img class=\"thumb lazy-load\" src=\"data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7\" "
                + "data-original=\"/contents/videos_screenshots/1/1/320x180/1.jpg\" alt=\"Clip One\">"
                + "<strong class=\"title\">Clip One</strong>"
                + "<span class=\"is-hd\">HD</span>"
                + "</a></div>"
                + "<a href=\"https://xmegadrive.com/videos/clip-one/\">Clip One duplicate</a>"
                + "<a href=\"/videos/?page=2\">Next</a>"
                + "<a href=\"/categories/femdom/\">FemDom</a>"
                + "<a href=\"/tags/feet/\">Feet</a>"
                + "<a href=\"https://ads.example/videos/nope/\">Ad</a>"
                + "<div class=\"item\"><a href=\"https://xmegadrive.com/videos/clip-two/\" title=\"Clip Two\">"
                + "<img src=\"/contents/videos_screenshots/2/2/preview.jpg\">"
                + "<strong class=\"title\">Clip Two</strong>"
                + "</a></div>";
    }
}
