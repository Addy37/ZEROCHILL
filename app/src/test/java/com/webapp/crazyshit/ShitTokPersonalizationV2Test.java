package com.webapp.crazyshit;

import org.junit.*;
import static org.junit.Assert.*;
import java.util.*;

public class ShitTokPersonalizationV2Test {
    static final long WALL = 1_800_000_000_000L;
    private NativeContentItem alice, bob;
    @Before public void setup() {
        ShitTokSmartRanker.resetForTest(null);
        ShitTokCommunityPopularity.setScoresForTest(Collections.emptyMap());
        alice = clip("alice", 1); bob = clip("bob", 1);
        ShitTokSmartRanker.register(alice); ShitTokSmartRanker.register(bob);
    }
    @After public void cleanup() { ShitTokSmartRanker.resetForTest(null); ShitTokCommunityPopularity.resetForTest(); }
    private NativeContentItem clip(String creator, int id) {
        return new NativeContentItem("media", "clip", "https://fapello.com/" + creator + "/" + id + "/",
                "", "", creator, "", "OnlyFap", "", 0, .5f);
    }
    private void selectAndPlay() {
        ShitTokSmartRanker.selectAt(alice.url, 1_000, WALL);
        ShitTokSmartRanker.playbackAt(alice.url, true, 1_000);
    }
    @Test public void completionAndMeaningfulWatchingRaiseRelatedCreatorAbovePeer() {
        selectAndPlay(); ShitTokSmartRanker.completionAt(alice.url, 10_000, 11_000, WALL);
        assertTrue(ShitTokSmartRanker.localScoreAt(clip("alice", 2), WALL) > ShitTokSmartRanker.localScoreAt(bob, WALL));
        ShitTokSmartRanker.leaveAt(11_000, WALL);
        assertTrue(ShitTokSmartRanker.localScoreAt(alice, WALL) > 1f);
    }
    @Test public void meaningfulWatchWithoutCompletionIsPositive() {
        selectAndPlay(); ShitTokSmartRanker.leaveAt(16_000, WALL);
        assertTrue(ShitTokSmartRanker.localScoreAt(alice, WALL) > 1f);
    }
    @Test public void fastSkipIsNegativeButBufferingFailureAndPauseAreNotDisinterest() {
        selectAndPlay(); ShitTokSmartRanker.leaveAt(2_000, WALL);
        assertTrue(ShitTokSmartRanker.localScoreAt(alice, WALL) < -.5f);
        setup(); ShitTokSmartRanker.selectAt(alice.url, 1_000, WALL);
        ShitTokSmartRanker.leaveAt(60_000, WALL); assertEquals(0f, ShitTokSmartRanker.localScoreAt(alice, WALL), .001f);
        selectAndPlay(); ShitTokSmartRanker.failed(alice.url); ShitTokSmartRanker.leaveAt(2_000, WALL);
        assertEquals(0f, ShitTokSmartRanker.localScoreAt(alice, WALL), .001f);
    }
    @Test public void pausedTimeDoesNotEarnWatchReward() {
        selectAndPlay(); ShitTokSmartRanker.playbackAt(alice.url, false, 2_000);
        ShitTokSmartRanker.leaveAt(900_000, WALL);
        assertEquals(0f, ShitTokSmartRanker.localScoreAt(alice, WALL), .001f);
    }
    @Test public void favoriteStrongPositiveIsBoundedAndRecentSignalOutweighsStale() {
        ShitTokSmartRanker.actionAt(alice, ShitTokSmartRanker.Action.FAVORITE, WALL);
        float favorite = ShitTokSmartRanker.localScoreAt(alice, WALL);
        assertTrue(favorite > 1.5f);
        for (int i = 0; i < 100; i++) ShitTokSmartRanker.actionAt(alice, ShitTokSmartRanker.Action.FAVORITE, WALL);
        assertEquals(favorite, ShitTokSmartRanker.localScoreAt(alice, WALL), .001f);
        assertTrue(ShitTokSmartRanker.localScoreAt(alice, WALL + 28L * 86_400_000) < favorite / 10);
    }
    @Test public void completionCannotBeEarnedBySeekingOrRepeatedCallbacks() {
        selectAndPlay(); ShitTokSmartRanker.completionAt(alice.url, 60_000, 1_100, WALL);
        assertEquals(0f, ShitTokSmartRanker.localScoreAt(alice, WALL), .001f);
        ShitTokSmartRanker.completionAt(alice.url, 60_000, 61_000, WALL);
        float once = ShitTokSmartRanker.localScoreAt(alice, WALL);
        for (int i = 0; i < 50; i++) ShitTokSmartRanker.completionAt(alice.url, 60_000, 61_000, WALL);
        assertEquals(once, ShitTokSmartRanker.localScoreAt(alice, WALL), .001f);
    }
    @Test public void seededExplorationStillContainsLowScoringCreatorsAndIsDeterministic() {
        ShitTokSmartRanker.actionAt(alice, ShitTokSmartRanker.Action.FAVORITE, System.currentTimeMillis());
        List<NativeContentItem> pool = new ArrayList<>();
        for (int i = 0; i < 12; i++) pool.add(clip("alice", i));
        for (int i = 0; i < 12; i++) pool.add(clip("bob", i));
        ShitTokAspectPriority priority = new ShitTokAspectPriority();
        List<NativeContentItem> first = priority.order(pool, new Random(37));
        assertEquals(first, priority.order(pool, new Random(37)));
        assertTrue(first.subList(0, 12).stream().anyMatch(i -> i.uploader.equals("bob")));
    }
}
