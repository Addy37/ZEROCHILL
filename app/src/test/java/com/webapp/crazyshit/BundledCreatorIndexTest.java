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

    @Test public void knownAliasCollapsesDuplicateCreatorCardsWithoutFuzzyMerging() throws Exception {
        BundledCreatorIndex index = index("Sasha Foxx\tSasha Foxxx\nSasha Foxxx\nSasha Foxxy\n");
        NativeContentItem canonical = creator("Sasha Foxx", "", "");
        NativeContentItem alias = creator("Sasha Foxxx", "https://example.org/sasha", "https://example.org/avatar");
        NativeContentItem nearby = creator("Sasha Foxxy", "", "");

        List<NativeContentItem> result = OnlyFapCreatorResults.merge(
                Arrays.asList(canonical, nearby),
                Collections.singletonList(alias),
                10,
                index);

        assertEquals(2, result.size());
        assertEquals("Sasha Foxx", result.get(0).title);
        assertEquals(alias.imageUrl, result.get(0).imageUrl);
        assertEquals("Sasha Foxxy", result.get(1).title);
        assertEquals(index.canonicalKey("Sasha Foxx"), index.canonicalKey("Sasha Foxxx"));
        assertNotEquals(index.canonicalKey("Sasha Foxx"), index.canonicalKey("Sasha Foxxy"));
    }

    @Test public void safeVariantsCollapseToCanonicalCreatorWithoutBroadFuzzyMatching() throws Exception {
        BundledCreatorIndex index = index("Belle Delphine\nBelle Rose\nSasha Foxx\nSasha Foxxy\n");
        NativeContentItem truncated = creator("Belle Del", "https://fapello.com/belle-del/", "truncated");
        NativeContentItem canonical = creator("Belle Delphine",
                "https://fapello.com/belle-delphine/", "canonical");
        NativeContentItem shortVariant = creator("Belle Delph",
                "https://fapello.com/belle-delph/", "short");
        NativeContentItem doubledLetter = creator("belledelphiine",
                "https://fapello.com/belledelphiine/", "typo");

        List<NativeContentItem> result = OnlyFapCreatorResults.merge(
                Arrays.asList(truncated, canonical, shortVariant, doubledLetter),
                null,
                10,
                index);

        assertEquals(1, result.size());
        assertEquals("Belle Delphine", result.get(0).title);
        assertEquals(canonical.url, result.get(0).url);
        assertEquals(index.canonicalKey("Belle Delphine"), index.canonicalKey("Belle Del"));
        assertEquals(index.canonicalKey("Belle Delphine"), index.canonicalKey("Belle Delph"));
        assertEquals(index.canonicalKey("Belle Delphine"), index.canonicalKey("belledelphiine"));
        assertNotEquals(index.canonicalKey("Sasha Foxx"), index.canonicalKey("Sasha Foxxy"));
    }

    @Test public void ambiguousTruncationDoesNotMergeDifferentCanonicalCreators() throws Exception {
        BundledCreatorIndex index = index("Belle Delphine\nBelle Delia\n");
        assertEquals("belle del", index.canonicalKey("Belle Del"));
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
}
