package com.webapp.crazyshit;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class ShitTokFilteredIdentityTest {
    @Test public void exactNormalizedAndReviewedAliasesQualifyButWeakNamesDoNot() {
        assertTrue(ShitTokFilteredIdentity.strong("Alice Smith", "ALICE-SMITH"));
        assertTrue(ShitTokFilteredIdentity.strong("Belle Delphine", "Belle Delphi"));
        for (String weak : Arrays.asList("Alice", "Alice Smith Extra", "Alicia Smith", "Alice and Bob", "AliceSmith"))
            assertFalse(weak, ShitTokFilteredIdentity.strong("Alice Smith", weak));
        assertFalse(ShitTokFilteredIdentity.strong("", ""));
    }
    @Test public void fapelloRequiresUniqueExactProfileIdentity() {
        OnlyHavenRepository.Creator verified = ShitTokFilteredInventoryTest.creator("Alice Smith");
        FapelloRepository.Model match = new FapelloRepository.Model("Alice Smith", "https://fapello.com/alice-smith/", "");
        FapelloRepository.Model weak = new FapelloRepository.Model("Alice", "https://fapello.com/alice/", "");
        assertSame(match, ShitTokFilteredIdentity.uniqueFapello(verified, Arrays.asList(weak, match)));
        assertNull(ShitTokFilteredIdentity.uniqueFapello(verified, Arrays.asList(weak)));
        assertNull(ShitTokFilteredIdentity.uniqueFapello(verified, Arrays.asList(match,
                new FapelloRepository.Model("Alice Smith", "https://fapello.com/other/", ""))));
    }
    private NativeContentItem album(String name, String url) { return new NativeContentItem("category", name, url, "", "", "", ""); }
    @Test public void bunkrRequiresUniqueWholeGalleryIdentityAndRejectsMentionsAndCompilations() {
        OnlyHavenRepository.Creator verified = ShitTokFilteredInventoryTest.creator("Alice Smith");
        NativeContentItem exact = album("ALICE-SMITH", "https://bunkr.cr/a/abc123");
        assertSame(exact, ShitTokFilteredIdentity.uniqueBunkr(verified, Arrays.asList(exact)));
        assertNull(ShitTokFilteredIdentity.uniqueBunkr(verified, Arrays.asList(
                album("Alice Smith and Bob", "https://bunkr.cr/a/mixed"))));
        assertNull(ShitTokFilteredIdentity.uniqueBunkr(verified, Arrays.asList(exact,
                album("Alice Smith", "https://bunkr.cr/a/other"))));
        assertNull(ShitTokFilteredIdentity.uniqueBunkr(verified, Arrays.asList(
                album("Alice Smith", "https://example.invalid/a/fake"))));
    }
}
