package com.webapp.crazyshit;

import org.junit.*;
import static org.junit.Assert.*;
import java.util.*;

public class ShitTokCreatorDiversityTest {
    @Before public void setup() {
        ShitTokSmartRanker.resetForTest(null); ShitTokCommunityPopularity.setScoresForTest(Collections.emptyMap());
    }
    @After public void cleanup() {
        ShitTokSmartRanker.resetForTest(null); ShitTokCommunityPopularity.resetForTest();
    }
    @Test public void sameCanonicalCreatorAcrossSourcesIsAvoidedWhenAlternativeIsReady() {
        ShitTokCreatorDiversity diversity = new ShitTokCreatorDiversity();
        NativeContentItem haven = ShitTokFilteredInventoryTest.clip("Alice", 6, 1);
        NativeContentItem fapello = ShitTokFilteredInventoryTest.clip("Alice", 5, 1);
        NativeContentItem bob = ShitTokFilteredInventoryTest.clip("Bob", 5, 1);
        assertEquals(ShitTokCreatorDiversity.key(haven), ShitTokCreatorDiversity.key(fapello));
        diversity.remember(haven);
        assertEquals(Arrays.asList(bob, fapello), diversity.order(Arrays.asList(fapello, bob)));
        assertEquals(Collections.singletonList(fapello), diversity.order(Collections.singletonList(fapello)));
    }
    @Test public void lastFiveCreatorsRotateWithoutPermanentBansOrDroppingCandidates() {
        ShitTokCreatorDiversity diversity = new ShitTokCreatorDiversity();
        NativeContentItem alice = ShitTokFilteredInventoryTest.clip("Alice", 5, 1);
        NativeContentItem bob = ShitTokFilteredInventoryTest.clip("Bob", 5, 1);
        NativeContentItem carol = ShitTokFilteredInventoryTest.clip("Carol", 5, 1);
        diversity.remember(alice); diversity.remember(bob);
        assertEquals(carol, diversity.order(Arrays.asList(alice, carol)).get(0));
        for (int i = 0; i < 6; i++) diversity.remember(ShitTokFilteredInventoryTest.clip("Other" + i, 5, 1));
        assertEquals(alice, diversity.order(Arrays.asList(alice, carol)).get(0));
        assertEquals(3, diversity.order(Arrays.asList(alice, alice, alice)).size());
    }
}
