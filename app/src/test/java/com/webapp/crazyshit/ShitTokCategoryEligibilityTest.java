package com.webapp.crazyshit;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class ShitTokCategoryEligibilityTest {
    @Test public void allAllowsEligibleSourcesAndEveryFilteredModeRequiresExactProviderEvidence() {
        NativeContentItem unknown = ShitTokSourcePoolTest.clip("unknown", 1);
        Set<String> evidence = new HashSet<>();
        assertTrue(ShitTokPreferenceMix.eligible(unknown, ContentPreference.ALL, evidence));
        for (ContentPreference selected : ContentPreference.values()) {
            if (selected == ContentPreference.ALL) continue;
            assertFalse(ShitTokPreferenceMix.eligible(unknown, selected, evidence));
            NativeContentItem verified = ShitTokSourcePoolTest.clip("verified", selected.ordinal());
            evidence.add(selected.value + "|" + verified.url);
            for (ContentPreference mode : ContentPreference.values()) {
                assertEquals(mode == selected || mode == ContentPreference.ALL,
                        ShitTokPreferenceMix.eligible(verified, mode, evidence));
            }
        }
    }
    @Test public void titlesNamesAndProviderLabelsNeverClassifyUnknownContent() {
        NativeContentItem unknown = new NativeContentItem("media", "Women Trans Men Groups",
                "https://example.invalid/women", "", "", "Women", "");
        for (ContentPreference mode : ContentPreference.values()) if (mode != ContentPreference.ALL)
            assertFalse(ShitTokPreferenceMix.eligible(unknown, mode, Collections.emptySet()));
    }
}
