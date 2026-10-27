package com.webapp.crazyshit;

import static org.junit.Assert.*;
import android.app.Application;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.util.ArrayList;
import java.util.LinkedHashMap;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class BunkrArtworkTest {
    @Test public void currentAlbumCardSkipsLeadingTypeIconAndKeepsRealThumbnail() {
        Document doc = Jsoup.parse("<a href='/f/sample'><img src='/images/video.svg'>"
                + "<img loading='lazy' src='https://static.scdn.st/id/thumbs/movie.png'>"
                + "<p>Example.mp4</p></a>", "https://bunkr.cr/a/album");
        ArrayList<NativeContentItem> files = ReflectionHelpers.callInstanceMethod(new BunkrRepository(),
                "parseAlbumDom", ReflectionHelpers.ClassParameter.from(Document.class, doc),
                ReflectionHelpers.ClassParameter.from(String.class, "https://bunkr.cr"));
        assertEquals(1, files.size());
        assertEquals("https://static.scdn.st/id/thumbs/movie.png", files.get(0).imageUrl);
    }

    @Test public void missingFileArtworkUsesSocialPosterThenGridFallbackWithoutTruncatingFilename() {
        BunkrRepository repo = new BunkrRepository();
        Document doc = Jsoup.parse("<meta property='og:image' content='https://static.scdn.st/thumbs/a.png'>"
                + "<script>var videoCoverUrl='https:\\/\\/static.scdn.st\\/thumbs\\/a.mp4_grid.png';</script>",
                "https://bunkr.cr/f/sample");
        assertEquals("https://static.scdn.st/thumbs/a.png", repo.fileArtwork(doc, ""));
        assertEquals("https://static.scdn.st/thumbs/a.mp4_grid.png",
                repo.fileArtwork(doc, "https://static.scdn.st/thumbs/a.png"));
        assertEquals("", repo.fileArtwork(Jsoup.parse("<img src='/images/video.svg'>"), ""));
    }

    @Test public void relativeMetadataArtworkUsesProvidedCdnAndLegacyDoubleExtensionStillNormalizes() {
        BunkrRepository repo = new BunkrRepository();
        String thumb = ReflectionHelpers.callInstanceMethod(repo, "normalizeThumbnailUrl",
                ReflectionHelpers.ClassParameter.from(String.class, "/thumbs/a.mp4.png?x=1"),
                ReflectionHelpers.ClassParameter.from(String.class, "https://bunkr.cr/a/album"),
                ReflectionHelpers.ClassParameter.from(String.class, "https://static.scdn.st/storage/a.mp4"));
        assertEquals("https://static.scdn.st/thumbs/a.png?x=1", thumb);
        assertFalse(BunkrRepository.isUsableArtwork("https://bunkr.cr/images/video.svg"));
        assertFalse(BunkrRepository.isUsableArtwork("data:image/png;base64,abc"));
    }
}
