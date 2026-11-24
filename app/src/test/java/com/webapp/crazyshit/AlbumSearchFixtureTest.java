package com.webapp.crazyshit;

import java.io.StringReader;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class AlbumSearchFixtureTest {
    private static final String DATA = "a1\tMountain landscape\tStudio North\t3\t2\tnature\n"
            + "a2\tCoastal landscape\tStudio South\t6\t0\tcoast nature\n"
            + "a1\tMountain duplicate\tStudio North\t3\t2\tnature\n"
            + "bad\tBad count\tStudio North\t-1\t2\tnature\n"
            + "https://bad.test\tBad identity\tStudio North\t3\t2\tnature\n";

    @Test public void matchesTitleUploaderAndTagsAndKeepsOnlyInertIds() throws Exception {
        List<NativeContentItem> matches = AlbumSearchFixture.read(new StringReader(DATA), "NATURE");
        assertEquals(2, matches.size());
        for (NativeContentItem item : matches) {
            assertTrue(AlbumSearchFixture.isSample(item.url));
            assertTrue(item.isSeries());
            assertTrue(item.imageUrl.isEmpty());
        }
        assertEquals("Mountain landscape", AlbumSearchFixture.read(new StringReader(DATA), "studio north nature").get(0).title);
        assertEquals("3 pictures · 2 videos", matches.get(0).description);
    }

    @Test public void rejectsBlankShortOverlongAndUnmatchedQueries() throws Exception {
        for (String query : new String[]{"", "a", "missing", new String(new char[101]).replace('\0','x')})
            assertTrue(AlbumSearchFixture.read(new StringReader(DATA), query).isEmpty());
        assertFalse(AlbumSearchFixture.isSample("https://example.com/album/1"));
    }
}
