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
        assertEquals(OnlyFapCreatorResults.key(saved), OnlyFapCreatorResults.key(result.get(0)));
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
}
