package com.webapp.crazyshit;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CreatorGalleryMatchTest {
    @Test public void onlyHavenRetainsReportedUsernameWhenProviderSuppliesIt() throws Exception {
        OnlyHavenRepository repository = new OnlyHavenRepository();
        String json = "{\"creators\":["
                + "{\"service\":\"onlyfans\",\"id\":\"100\",\"displayName\":\"Rynkerbelle Archive\","
                + "\"name\":\"rynkerbellearchive\"},"
                + "{\"service\":\"onlyfans\",\"id\":\"200\",\"displayName\":\"ryn! >_<\","
                + "\"name\":\"rynkerbelle\"}]}";
        List<OnlyHavenRepository.Creator> candidates = repository.parseCreatorSearchJson(json,
                OnlyHavenTrendingTest.config("https://example.test/"), "rynkerbelle", 10);
        assertEquals(2, candidates.size());
        List<OnlyHavenRepository.Creator> verified = OnlyHavenRepository.verifiedGalleryCreators(
                candidates, "@Rynkerbelle");
        assertEquals(1, verified.size());
        assertEquals("200", verified.get(0).id);
        assertEquals("ryn! >_<", verified.get(0).name);
        assertEquals("rynkerbelle", verified.get(0).username);
        assertFalse(CreatorGalleryMatch.exactName(verified.get(0).name, verified.get(0).username));
    }

    @Test public void duplicateDisplayNamesRequireProfileEvidence() throws Exception {
        OnlyHavenRepository repository = new OnlyHavenRepository();
        String json = "{\"creators\":["
                + "{\"service\":\"onlyfans\",\"id\":\"100\",\"displayName\":\"Shared Name\","
                + "\"username\":\"first.example\"},"
                + "{\"service\":\"fansly\",\"id\":\"200\",\"displayName\":\"Shared Name\","
                + "\"username\":\"second.example\"}]}";
        List<OnlyHavenRepository.Creator> candidates = repository.parseCreatorSearchJson(json,
                OnlyHavenTrendingTest.config("https://example.test/"), "Shared Name", 10);
        assertTrue(OnlyHavenRepository.verifiedGalleryCreators(candidates, "Shared Name").isEmpty());
        assertEquals("100", OnlyHavenRepository.verifiedGalleryCreators(candidates, "100").get(0).id);
        assertEquals("200", OnlyHavenRepository.verifiedGalleryCreators(candidates, "second.example").get(0).id);
    }

    @Test public void onlyHavenDisplayOnlyAndSimilarHandleDoNotProveGadzooksExample() {
        OnlyHavenRepository.Creator displayOnly = new OnlyHavenRepository.Creator(
                "onlyfans", "200", "ryn! >_<", "https://cum.st/creators/onlyfans/200", "");
        assertFalse(CreatorGalleryMatch.onlyHavenMatches(displayOnly, "rynkerbelle"));
        assertFalse(CreatorGalleryMatch.exactHandle("ry.n", "ry_n"));
        assertFalse(CreatorGalleryMatch.exactName("ry.n", "ry_n"));
        assertFalse(CreatorGalleryMatch.exactName("ryn", "Rynkerbelle Archive"));
        assertTrue(CreatorGalleryMatch.exactName("Belle Delphi", "Belle Delphine"));
    }

    @Test public void albumsRequireWholeIdentityAndAllowExplicitMultipartTitles() {
        assertTrue(CreatorGalleryMatch.albumMatches("Rynkerbelle", "rynkerbelle"));
        assertTrue(CreatorGalleryMatch.albumMatches("RYNKERBELLE - Pt. 2", "@rynkerbelle"));
        assertTrue(CreatorGalleryMatch.albumMatches("rynkerbelle_vol3", "rynkerbelle"));
        assertTrue(CreatorGalleryMatch.albumMatches("rynkerbelle (4)", "rynkerbelle"));
        assertTrue(CreatorGalleryMatch.albumMatches("rynkerbelle Part 1 of 3", "rynkerbelle"));
        assertFalse(CreatorGalleryMatch.albumMatches("rynkerbelle and Other Creator Part 2", "rynkerbelle"));
        assertFalse(CreatorGalleryMatch.albumMatches("Creator Compilation - rynkerbelle", "rynkerbelle"));
        assertFalse(CreatorGalleryMatch.albumMatches("rynkerbelle 2", "rynkerbelle"));
        assertFalse(CreatorGalleryMatch.albumMatches("rynkerbelle2", "rynkerbelle"));
        assertFalse(CreatorGalleryMatch.albumMatches("rynkerbelle", "ryn! >_<"));
        assertTrue(CreatorGalleryMatch.albumMatches("ry.n Part 1", "ry.n"));
        assertFalse(CreatorGalleryMatch.albumMatches("ry_n Part 1", "ry.n"));
        assertFalse(CreatorGalleryMatch.albumMatches("ry n Part 1", "ry.n"));
    }
}
