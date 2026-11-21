package com.webapp.crazyshit;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

import java.util.List;

import static org.junit.Assert.*;

public class WikiFeetRepositoryTest {
    private static String profile(String name, long... ids) {
        StringBuilder gallery = new StringBuilder();
        for (long id : ids) {
            if (gallery.length() > 0) gallery.append(',');
            gallery.append("{\"pid\":").append(id)
                    .append(",\"pw\":1200,\"ph\":1800,\"removed\":0}");
        }
        return "<script>let ignored={\"brace\":\"}\"}; tdata = {\"cid\":42," +
                "\"cname\":\"" + name + "\",\"gallery\":[" + gallery + "]};" +
                " tbody = [];</script>";
    }

    @Test public void parsesPredictiveCreatorLinksAndCounts() {
        String html = "<div id='searchresults'>" +
                "<a href='/Scarlett_Johansson'><div>Scarlett Johansson</div><small>1784 photos</small></a>" +
                "<a href='https://other.example/nope'><div>Nope</div></a></div>";
        List<WikiFeetRepository.Creator> creators = WikiFeetRepository.parseSearch(
                html, WikiFeetRepository.Site.WIKIFEET_X, "https://wikifeetx.com/", 8);
        assertEquals(1, creators.size());
        assertEquals("Scarlett Johansson", creators.get(0).name);
        assertEquals(1784, creators.get(0).photoCount);
        assertEquals("https://wikifeetx.com/Scarlett_Johansson", creators.get(0).url);
    }

    @Test public void parsesScriptBackedSearchCardsAndTheirThumbnails() {
        String html = "<script>tdata={}; tbody = [[\"CelebBox\",{" +
                "\"cid\":42,\"fetchname\":\"Scarlett_Johansson\"," +
                "\"name\":\"Scarlett Johansson\",\"pics\":\"9001,9000\"}]];</script>";
        List<WikiFeetRepository.Creator> creators = WikiFeetRepository.parseSearch(
                html, WikiFeetRepository.Site.WIKIFEET_X, "https://wikifeetx.com/search/scarlett", 8);
        assertEquals(1, creators.size());
        assertEquals("https://thumbs.wikifeet.com/9001.jpg", creators.get(0).imageUrl);
        assertEquals(42, creators.get(0).profileId);
        assertEquals("https://wikifeetx.com/Scarlett_Johansson", creators.get(0).url);
    }

    @Test public void profileThumbnailAndPagedOriginalsUseTheSelectedSource() throws Exception {
        String html = profile("Abella Danger", 1001, 1002, 1003);
        WikiFeetRepository.Creator creator = WikiFeetRepository.parseProfile(
                html, WikiFeetRepository.Site.WIKIFEET_X,
                "https://wikifeetx.com/Abella_Danger");
        assertEquals(3, creator.photoCount);
        assertEquals(42, creator.profileId);
        assertEquals("https://thumbs.wikifeet.com/1003.jpg", creator.imageUrl);

        List<NativeContentItem> firstPage = WikiFeetRepository.parseMedia(html, creator, 1, 2);
        assertEquals(2, firstPage.size());
        assertEquals("https://pics.wikifeet.com/Abella-Danger-Feet-1003.jpg", firstPage.get(0).url);
        List<NativeContentItem> page = WikiFeetRepository.parseMedia(html, creator, 2, 2);
        assertEquals(1, page.size());
        assertEquals("https://pics.wikifeet.com/Abella-Danger-Feet-1001.jpg", page.get(0).url);
        assertEquals("https://thumbs.wikifeet.com/1001.jpg", page.get(0).imageUrl);
        assertEquals("https://wikifeetx.com/Abella_Danger", page.get(0).uploader);
        assertTrue(WikiFeetRepository.isXItem(page.get(0)));
        assertFalse(WikiFeetRepository.isRetiredItem(page.get(0)));
        assertEquals(creator.url, WikiFeetRepository.imageReferer(page.get(0)));
        assertThrows(IOException.class, () -> WikiFeetRepository.parseMedia(
                html.replace("\"cid\":42", "\"cid\":43"), creator, 2, 2));
    }

    @Test public void profileDataScannerHandlesQuotedBraces() throws Exception {
        String html = "before tdata = {\"cid\":7,\"cname\":\"A } B\",\"gallery\":[]}; after";
        assertEquals("A } B", WikiFeetRepository.profileData(html).getString("cname"));
    }

    @Test public void rejectsLookalikeHostsAndNonImageRoutes() {
        assertEquals("https://wikifeetx.com/search/scarlett%20johansson",
                WikiFeetRepository.searchUrl(
                        WikiFeetRepository.Site.WIKIFEET_X, "scarlett johansson"));
        assertFalse(WikiFeetRepository.isWikiFeetUrl("https://wikifeet.com.evil.example/A"));
        assertFalse(WikiFeetRepository.isOriginalImageUrl("https://wikifeet.com/A"));
        assertTrue(WikiFeetRepository.isOriginalImageUrl(
                "https://pics.wikifeet.com/A-Feet-1.jpg"));
    }
    @Test public void retiredOriginDoesNotClaimWikiFeetXOnTheSharedImageHost() throws Exception {
        String media = "https://pics.wikifeet.com/Sample-Feet-42.jpg";
        NativeContentItem retired = image(media, "https://wikifeet.com/Sample", "WikiFeet");
        NativeContentItem x = image(media, "https://wikifeetx.com/Sample", "WikiFeet X");
        NativeContentItem unlabeled = image(media, "", "");
        assertTrue(WikiFeetRepository.isRetiredItem(retired));
        assertFalse(WikiFeetRepository.isXItem(retired));
        assertTrue(WikiFeetRepository.isXItem(x));
        assertFalse(WikiFeetRepository.isRetiredItem(x));
        assertFalse(WikiFeetRepository.isRetiredItem(unlabeled));
        assertFalse(WikiFeetRepository.isXItem(unlabeled));
        assertEquals(x.uploader, new WikiFeetRepository().resolvePlayable(x.url, x.uploader).requestReferer);
        assertNull(PlayableSourceRouter.resolve(null, retired));
        assertNull(PlayableSourceRouter.resolve(null, retired.uploader));
    }

    @Test public void galleryPrefersAnExactProfileHandleAndRejectsAmbiguousNames() {
        WikiFeetRepository.Creator exact = creator("ryn! >_<", "rynkerbelle", 42);
        WikiFeetRepository.Creator similar = creator("Ryn Example", "rynexample", 43);
        WikiFeetRepository.Creator sameName = creator("ryn! >_<", "ryn-other", 44);
        assertEquals(exact, WikiFeetRepository.confirmedMatches(
                Arrays.asList(similar, exact, sameName), "rynkerbelle").get(0));
        assertTrue(WikiFeetRepository.confirmedMatches(
                Arrays.asList(similar, exact, sameName), "ryn").isEmpty());
        assertTrue(WikiFeetRepository.confirmedMatches(
                Arrays.asList(similar, exact, sameName), "rynker").isEmpty());
        assertEquals("rynkerbelle", exact.asItem().creatorAliases.get(0));
    }

    @Test public void backgroundPollingSkipsRetiredProfiles() {
        NativeContentItem retired = new WikiFeetRepository.Creator(WikiFeetRepository.Site.WIKIFEET,
                "Sample", "https://wikifeet.com/Sample", "", 0).asItem();
        NativeContentItem x = creator("Sample", "Sample", 42).asItem();
        assertEquals("", FavoriteCreatorUpdateScanner.profileSource(retired));
        assertEquals("wikifeetx", FavoriteCreatorUpdateScanner.profileSource(x));
    }

    private static WikiFeetRepository.Creator creator(String name, String handle, long id) {
        return new WikiFeetRepository.Creator(WikiFeetRepository.Site.WIKIFEET_X,
                name, "https://wikifeetx.com/" + handle, "", 0, id);
    }

    private static NativeContentItem image(String url, String profile, String source) {
        return new NativeContentItem(NativeContentItem.KIND_IMAGE, "Sample", url,
                "", source, profile, "", source);
    }

}
