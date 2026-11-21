package com.webapp.crazyshit;

import org.junit.Test;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class BundledCreatorIndexTest {
    private BundledCreatorIndex index(String rows) throws Exception {
        return BundledCreatorIndex.parse(new BufferedReader(new StringReader(rows)));
    }

    private NativeContentItem creator(String name, String url, String avatar) {
        return new NativeContentItem(NativeContentItem.KIND_CREATOR, name, url,
                avatar, "", "", "", "", name);
    }

    @Test public void aliasesRankWithExactNamesAndAccents() throws Exception {
        BundledCreatorIndex index = index("Annabelle\tanna-belle\nMía Rose\tmia_rose|rose mia\nAnna\n");
        assertEquals(3, index.size());
        assertEquals("Anna", index.matching("anna", 4).get(0).title);
        assertEquals("Mía Rose", index.matching("rose mia", 4).get(0).title);
        assertEquals("Mía Rose", index.matching("mia rose", 4).get(0).title);
    }

    @Test public void onlyUnambiguousCuratedAliasesReachGallery() throws Exception {
        BundledCreatorIndex index = index("Creator One\tunique_handle|shared_handle\n"
                + "Creator Two\tshared_handle\n");
        NativeContentItem selected = index.matching("unique_handle", 3).get(0);
        assertEquals("Creator One", selected.title);
        assertEquals(Collections.singletonList("unique_handle"), selected.creatorAliases);
        assertEquals("Creator One", selected.searchQuery);
        assertNull(index.exact("shared_handle"));
    }

    @Test public void largeIndexLimitsResultsAndRepeatedQueries() throws Exception {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 5000; i++) rows.append("Creator ").append(i).append('\n');
        BundledCreatorIndex index = index(rows.toString());
        assertEquals(5000, index.size());
        for (int i = 0; i < 20; i++) {
            assertEquals(12, index.matching("creator", 12).size());
            assertEquals("Creator 4999", index.matching("creator 4999", 12).get(0).title);
        }
    }

    @Test public void liveMetadataEnrichesSameIdentityWithoutRemovingLocalMatches() {
        NativeContentItem saved = creator("Mía Rose", "", "");
        NativeContentItem live = creator("Mia-Rose", "https://example.org/profile", "https://example.org/avatar");
        List<NativeContentItem> result = OnlyFapCreatorResults.merge(
                Arrays.asList(saved, creator("Zoe", "", "")), Collections.singletonList(live), 10);
        assertEquals(2, result.size());
        assertEquals("Mía Rose", result.get(0).title);
        assertEquals(live.imageUrl, result.get(0).imageUrl);
        assertEquals(OnlyFapCreatorResults.key(live), OnlyFapCreatorResults.key(result.get(0)));
        assertEquals("Zoe", result.get(1).title);
    }

    @Test public void reviewedBelleVariantsCollapseWithOneReadableTitle() {
        List<NativeContentItem> variants = Arrays.asList(
                creator("Belle Del", "", ""), creator("Belle Delphine", "", ""),
                creator("Belle Delph", "", ""), creator("Belle Delphi", "", ""),
                creator("Belle Delphin", "", ""), creator("belledelphiine", "", ""));
        List<NativeContentItem> result = OnlyFapCreatorResults.merge(variants, null, 20);
        assertEquals(1, result.size());
        assertEquals("Belle Delphine", result.get(0).title);
    }

    @Test public void reviewedTaliyaAliasesCollapseButSimilarNamesRemainSeparate() {
        List<NativeContentItem> result = OnlyFapCreatorResults.merge(Arrays.asList(
                creator("Taliya &Amp; Gustavo", "", ""),
                creator("Taliya & Gustavo", "https://fapello.com/taliya-gustavo/", "avatar"),
                creator("Taliyaandgustavo", "", ""),
                creator("Taliya and Gustavo Jr", "https://fapello.com/taliya-gustavo-jr/", "")), null, 20);
        assertEquals(2, result.size());
        assertEquals("Taliya & Gustavo", result.get(0).title);
        assertEquals("avatar", result.get(0).imageUrl);
        assertEquals("Taliya and Gustavo Jr", result.get(1).title);
    }

    @Test public void exactProfileIdentityMergesDifferentNamesButDistinctProfilesDoNot() {
        List<NativeContentItem> result = OnlyFapCreatorResults.merge(Arrays.asList(
                creator("Profile A", "https://fapello.com/person/", ""),
                creator("Profile B", "https://fapello.com/person", "art"),
                creator("Profile A", "https://fapello.com/another-person/", "")), null, 20);
        assertEquals(2, result.size());
        assertEquals("art", result.get(0).imageUrl);
    }
    @Test public void matchingTitlesAndHandlesDoNotAssertCrossSourceIdentity() {
        NativeContentItem fapello = creator("ryn! >_<", "https://fapello.com/rynkerbelle/", "art-a")
                .withCreatorIdentity(Collections.singletonList("rynkerbelle"), Collections.emptyList());
        NativeContentItem onlyHaven = creator("ryn", "https://cum.st/creators/fansly/23", "art-b")
                .withCreatorIdentity(Collections.singletonList("rynkerbelle"), Collections.emptyList());
        List<NativeContentItem> result = OnlyFapCreatorResults.merge(
                Collections.singletonList(fapello), Collections.singletonList(onlyHaven), 10);
        assertEquals(2, result.size());
        assertNotEquals(OnlyFapCreatorResults.key(result.get(0)), OnlyFapCreatorResults.key(result.get(1)));
        assertEquals("art-a", result.get(0).imageUrl);
        assertEquals("art-b", result.get(1).imageUrl);
    }

    @Test public void reviewedCrossSourceIdentityRetainsProfilesAndProviderAliases() {
        NativeContentItem fapello = creator("Belle Delphine", "https://fapello.com/belledelphine/", "art-a")
                .withCreatorIdentity(Collections.singletonList("belle-provider-handle"), Collections.emptyList());
        NativeContentItem onlyHaven = creator("Belle Del", "https://cum.st/creators/onlyfans/24", "art-b")
                .withCreatorIdentity(Collections.singletonList("alternate_handle"), Collections.emptyList());
        List<NativeContentItem> result = OnlyFapCreatorResults.merge(
                Arrays.asList(fapello, onlyHaven), null, 10);
        assertEquals(1, result.size());
        assertEquals(fapello.url, result.get(0).url);
        assertTrue(result.get(0).creatorProfileUrls.containsAll(Arrays.asList(fapello.url, onlyHaven.url)));
        assertTrue(result.get(0).creatorAliases.containsAll(Arrays.asList(
                "belle-provider-handle", "alternate_handle")));
    }

    @Test public void reviewedNameCannotOverrideDifferentIdsFromOneProvider() {
        List<NativeContentItem> result = OnlyFapCreatorResults.merge(Arrays.asList(
                creator("Belle Del", "https://fapello.com/profile-one/", ""),
                creator("Belle Delphine", "https://fapello.com/profile-two/", "")), null, 10);
        assertEquals(2, result.size());
    }
}
