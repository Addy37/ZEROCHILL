package com.webapp.crazyshit;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class XMegaDriveRepositoryTest {
    private static final String PAGE =
            "https://www.xmegadrive.com/videos/example-clip/";

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

    @Test public void playerFixtureWithoutMp4ReturnsEmpty() {
        assertEquals("", XMegaDriveRepository.mediaFromHtml("<h1>No player</h1>", PAGE));
        assertEquals("", XMegaDriveRepository.mediaFromHtml("", PAGE));
        assertEquals("", XMegaDriveRepository.mediaFromHtml(null, PAGE));
    }
}
