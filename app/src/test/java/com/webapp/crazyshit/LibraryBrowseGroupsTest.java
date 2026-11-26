package com.webapp.crazyshit;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LibraryBrowseGroupsTest {
    @Test public void dateBucketsKeepTodayYesterdayAndTheRestOfTheWeek() {
        Calendar now = Calendar.getInstance();
        now.set(2026, Calendar.NOVEMBER, 25, 22, 0, 0);
        long today = now.getTimeInMillis();
        Calendar yesterday = (Calendar) now.clone();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        Calendar week = (Calendar) now.clone();
        week.add(Calendar.DAY_OF_YEAR, -4);
        Calendar old = (Calendar) now.clone();
        old.add(Calendar.DAY_OF_YEAR, -20);
        assertEquals(LibraryBrowseGroups.TODAY, LibraryBrowseGroups.dateBucket(today, today));
        assertEquals(LibraryBrowseGroups.YESTERDAY,
                LibraryBrowseGroups.dateBucket(yesterday.getTimeInMillis(), today));
        assertEquals(LibraryBrowseGroups.THIS_WEEK,
                LibraryBrowseGroups.dateBucket(week.getTimeInMillis(), today));
        assertEquals(LibraryBrowseGroups.EARLIER,
                LibraryBrowseGroups.dateBucket(old.getTimeInMillis(), today));
        assertEquals(LibraryBrowseGroups.EARLIER, LibraryBrowseGroups.dateBucket(0L, today));
    }

    @Test public void rawIdsAreNeverUsedAsTitles() {
        assertTrue(LibraryBrowseGroups.isRawIdentifier("a1b2c3d4e5f60718"));
        assertTrue(LibraryBrowseGroups.isRawIdentifier("12345678"));
        assertTrue(LibraryBrowseGroups.isRawIdentifier("Video #12345678"));
        assertTrue(LibraryBrowseGroups.isRawIdentifier("abc123456.mp4"));
        assertTrue(LibraryBrowseGroups.isRawIdentifier("p744ZWo1file99"));
        assertFalse(LibraryBrowseGroups.isRawIdentifier("CarrotSoki"));
        assertFalse(LibraryBrowseGroups.isRawIdentifier("Night drive"));
        NativeContentItem raw = new NativeContentItem(NativeContentItem.KIND_MEDIA,
                "a1b2c3d4e5f60718", "https://example.test/v", "", "", "Alex", "", "");
        assertEquals("Alex", LibraryBrowseGroups.readableTitle(raw, raw.title));
        NativeContentItem named = new NativeContentItem(NativeContentItem.KIND_MEDIA,
                "Night drive", "https://example.test/v", "", "", "", "", "");
        assertEquals("Night drive", LibraryBrowseGroups.readableTitle(named, named.title));
    }

    @Test public void previewStackKeepsTheFirstRealUrls() {
        assertEquals(Arrays.asList("https://a", "https://b"),
                LibraryBrowseGroups.firstUrls(Arrays.asList("", "https://a", " https://b ", "https://c"), 2));
    }

    @Test public void lettersStayAZ() {
        assertEquals("A", LibraryBrowseGroups.letter("alex"));
        assertEquals("#", LibraryBrowseGroups.letter("3am"));
        assertEquals("#", LibraryBrowseGroups.letter(""));
    }
}
