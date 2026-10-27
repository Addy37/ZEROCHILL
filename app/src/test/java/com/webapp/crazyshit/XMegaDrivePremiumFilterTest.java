package com.webapp.crazyshit;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class XMegaDrivePremiumFilterTest {
    @Test public void omitsPremiumCardsButKeepsFreeVideos() {
        String html = ""
                + "<div class='item'><a href='/videos/free-clip/'>"
                + "<strong class='title'>Free Clip</strong></a></div>"
                + "<div class='item premium'><a href='/videos/paid-clip/'>"
                + "<strong class='title'>Paid Clip</strong><span class='badge'>Premium</span>"
                + "</a></div>"
                + "<div class='item' data-is-private='2'><a href='/videos/paid-by-data/'>"
                + "<strong class='title'>Paid By Data</strong></a></div>"
                + "<div class='item'><a href='/videos/premium-quality-free/'>"
                + "<strong class='title'>Premium Quality Free Clip</strong></a></div>";

        List<NativeContentItem> items = XMegaDriveRepository.parseListingHtml(html);

        assertEquals(2, items.size());
        assertEquals("https://www.xmegadrive.com/videos/free-clip/", items.get(0).url);
        assertEquals(
                "https://www.xmegadrive.com/videos/premium-quality-free/",
                items.get(1).url
        );
    }

    @Test public void exactPremiumUserRequestSlugIsOmitted() {
        String html = ""
                + "<div class='item'><a href='https://www.xmegadrive.com/videos/"
                + "umiliazioniitaliane-fucked-by-feet-full-version-fottuti-dai-piedi-"
                + "versione-integrale-30-99-premium-user-request/'>"
                + "<strong class='title'>Umiliazioniitaliane - Fucked By Feet Full Version</strong>"
                + "</a></div>"
                + "<div class='item'><a href='/videos/premium-quality-free/'>"
                + "<strong class='title'>Premium Quality Free Clip</strong></a></div>";

        List<NativeContentItem> items = XMegaDriveRepository.parseListingHtml(html);

        assertEquals(1, items.size());
        assertEquals(
                "https://www.xmegadrive.com/videos/premium-quality-free/",
                items.get(0).url
        );
    }

    @Test public void premiumMarkerDoesNotBleedIntoSiblingFreeCard() {
        String html = ""
                + "<section>"
                + "<div class='item'><a href='/videos/paid-clip/'>"
                + "<span class='premium-badge'>Premium</span>"
                + "<strong class='title'>Paid Clip</strong></a></div>"
                + "<div class='item'><a href='/videos/free-sibling/'>"
                + "<strong class='title'>Free Sibling</strong></a></div>"
                + "</section>";

        List<NativeContentItem> items = XMegaDriveRepository.parseListingHtml(html);

        assertEquals(1, items.size());
        assertEquals("https://www.xmegadrive.com/videos/free-sibling/", items.get(0).url);
    }

    @Test public void premiumOnlySearchPageStillCountsAsUsefulMarkup() throws Exception {
        String html = ""
                + "<div class='item' data-is-private='2'>"
                + "<a href='/videos/paid-only/'><strong class='title'>Paid Only</strong></a>"
                + "</div>";
        Document document = Jsoup.parse(html, "https://www.xmegadrive.com/search/test/");
        Method method = XMegaDriveRepository.class.getDeclaredMethod(
                "hasUsefulMarkup", Document.class, String.class
        );
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(
                null,
                document,
                "https://www.xmegadrive.com/search/test/"
        ));
        assertTrue(XMegaDriveRepository.parseListingHtml(html).isEmpty());
    }
}
