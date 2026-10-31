package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokSourceModeTest {
    @Before public void setup() {
        ContentPreference.prefs(RuntimeEnvironment.getApplication()).edit().clear().commit();
        ShitTokSmartRanker.resetForTest(null);
        ShitTokCommunityPopularity.setScoresForTest(Collections.emptyMap());
    }
    @After public void cleanup() { ShitTokSmartRanker.resetForTest(null); ShitTokCommunityPopularity.resetForTest(); }

    @Test public void defaultAndSavedChoiceUseAnOptionalIndependentLocalKey() {
        Application app = RuntimeEnvironment.getApplication();
        assertEquals(ShitTokSourceMode.ALL, ShitTokSourceMode.get(app));
        ContentPreference.set(app, ContentPreference.MEN);
        assertTrue(ShitTokSourceMode.set(app, ShitTokSourceMode.ONLY_FAP));
        assertEquals(ShitTokSourceMode.ONLY_FAP, ShitTokSourceMode.get(app));
        assertEquals(ContentPreference.MEN, ContentPreference.get(app));
        ContentPreference.prefs(app).edit().putString(ShitTokSourceMode.KEY, "unknown").commit();
        assertEquals(ShitTokSourceMode.ALL, ShitTokSourceMode.get(app));
    }
    @Test public void fullMixtureIsUnchangedAndOnlyFapExplicitlyExcludesBroadProviders() {
        for (String source : Arrays.asList("crazyshit.com", "kaotic", "efukt", "xmegadrive.com")) {
            assertTrue(ShitTokSourceMode.ALL.allowsSource(source));
            assertFalse(ShitTokSourceMode.ONLY_FAP.allowsSource(source));
        }
        for (String source : Arrays.asList("baddiehub", "shit-show", "onlyhaven", "fapello", "bunkr"))
            assertTrue(ShitTokSourceMode.ONLY_FAP.allowsSource(source));
        assertFalse(ShitTokSourceMode.ALL.creatorInventory(ContentPreference.ALL));
    }
    @Test public void onlyFapAllCanAcquireCreatorProvidersAndShowWithoutGenderClassification() {
        ShitTokFilteredInventory inv = inventory();
        inv.request(ShitTokSourceMode.ONLY_FAP, ContentPreference.ALL, true, () -> {});
        List<NativeContentItem> clips = inv.take(ContentPreference.ALL);
        Set<String> providers = new HashSet<>();
        for (NativeContentItem clip : clips) {
            assertTrue(inv.matches(clip, ContentPreference.ALL));
            providers.add(ShitTokAspectPriority.sourceKey(clip));
        }
        assertTrue(providers.toString(), providers.containsAll(Arrays.asList("onlyhaven", "fapello", "bunkr", "baddiehub", "shit-show")));
    }
    private static NativeContentItem baddie(String gender) {
        return new NativeContentItem("media", gender + "0", "https://baddiehub.com/" + gender + "/", "", "", "BaddieHub", "", "", "", 0, .5f);
    }
    private static ShitTokFilteredInventory inventory() {
        return new ShitTokFilteredInventory(new ShitTokFilteredInventoryTest.Direct(), () -> 0L,
                new ShitTokFilteredInventoryTest.Fake() {
                    @Override public List<NativeContentItem> media(int source, ShitTokFilteredInventory.Cursor cursor) throws Exception {
                        List<NativeContentItem> items = super.media(source, cursor);
                        if (source != 5) return items;
                        NativeContentItem item = items.get(0);
                        return Collections.singletonList(new NativeContentItem(item.kind, item.title, item.url,
                                item.imageUrl, item.views, item.uploader, item.comments, "Fapello",
                                item.searchQuery, item.publishedAtMillis, item.aspectRatioHint));
                    }
                    public List<NativeContentItem> baddie(ContentPreference gender, List<OnlyHavenRepository.Creator> verified) {
                        return Collections.singletonList(ShitTokSourceModeTest.baddie(gender.value));
                    }
                });
    }
    @Test public void showEligibilityIsExactAndInventoryIsIsolatedAcrossBothDimensions() {
        ShitTokFilteredInventory inv = inventory();
        for (ContentPreference gender : ContentPreference.values()) {
            inv.request(ShitTokSourceMode.ONLY_FAP, gender, true, () -> {});
            List<NativeContentItem> clips = inv.take(gender);
            boolean show = clips.stream().anyMatch(i -> ShitTokAspectPriority.sourceKey(i).equals("shit-show"));
            assertEquals(gender == ContentPreference.ALL || gender == ContentPreference.WOMEN, show);
            for (NativeContentItem clip : clips) assertTrue(inv.matches(clip, gender));
        }
        inv.request(ShitTokSourceMode.ALL, ContentPreference.WOMEN, false, () -> {});
        assertFalse(inv.matches(baddie("women"), ContentPreference.WOMEN));
        inv.select(ShitTokSourceMode.ONLY_FAP, ContentPreference.WOMEN);
        assertTrue(inv.matches(baddie("women"), ContentPreference.WOMEN));
        assertFalse(inv.matches(baddie("men"), ContentPreference.WOMEN));
    }
    @Test public void baddieNeedsAnExistingResolvedIdentityAndUniqueAuthoritativeCreator() {
        OnlyHavenRepository.Creator women = ShitTokFilteredInventoryTest.creator("Jane");
        NativeContentItem known = new NativeContentItem("creator", "Jane", women.url, "", "", "", "");
        assertSame(women, ShitTokFilteredInventory.linkedBaddie(known, Arrays.asList(women)));
        assertNull(ShitTokFilteredInventory.linkedBaddie(null, Arrays.asList(women)));
        assertNull(ShitTokFilteredInventory.linkedBaddie(baddie("Jane"), Arrays.asList(women)));
        assertNull(ShitTokFilteredInventory.linkedBaddie(known, Arrays.asList(ShitTokFilteredInventoryTest.creator("John"))));
        OnlyHavenRepository.Creator ambiguous = new OnlyHavenRepository.Creator("fansly", "other", "Jane", "https://cum.st/creators/fansly/other", "");
        assertNull(ShitTokFilteredInventory.linkedBaddie(known, Arrays.asList(women, ambiguous)));
    }
    @Test public void warmCacheSeparatesBothDimensionsAndCanRestoreEachRecentCombination() {
        ShitTokWarmCategoryCache cache = new ShitTokWarmCategoryCache();
        NativeContentItem clip = ShitTokFilteredInventoryTest.clip("Jane", 6, 1);
        CrazyShitRepository.StreamInfo stream = new CrazyShitRepository.StreamInfo(clip.url, clip.url, "clip");
        for (ShitTokSourceMode source : ShitTokSourceMode.values())
            for (ContentPreference gender : ContentPreference.values())
                cache.save(source, gender, Arrays.asList(clip), Collections.singletonMap(clip.url, stream), 0);
        for (ShitTokSourceMode source : ShitTokSourceMode.values())
            for (ContentPreference gender : ContentPreference.values()) {
                assertEquals(1, cache.take(source, gender, 1).size());
                assertTrue(cache.take(source, gender, 1).isEmpty());
            }
    }
    @Test public void switchPrunesFutureKeepsCurrentAndRejectsStaleBroadPublicationWithoutResettingLearning() {
        try (org.robolectric.android.controller.ActivityController<Activity> activity = Robolectric.buildActivity(Activity.class).setup()) {
            ChaosFeedView feed = new ChaosFeedView(activity.get(), item -> {}, false);
            ReflectionHelpers.setField(feed, "queuePreference", ContentPreference.ALL);
            ArrayList<NativeContentItem> queue = ReflectionHelpers.getField(feed, "items");
            NativeContentItem current = ShitTokInventoryAdmissionTest.item("current"), broad = new NativeContentItem("media", "broad", "https://kaotic.com/video/fixture", "", "", "Kaotic", "", "", "", 0, .5f);
            queue.addAll(Arrays.asList(current, broad));
            // Compare the same instant: normal wall-clock decay is independent
            // of whether a source switch preserved the learned signal.
            long rankingInstant = 1_700_000_000_000L;
            ShitTokSmartRanker.actionAt(broad, ShitTokSmartRanker.Action.FAVORITE, rankingInstant);
            float learned = ShitTokSmartRanker.localScoreAt(broad, rankingInstant);
            int generation = ReflectionHelpers.getField(feed, "feedGeneration");
            long before = ShitTokPoolDiagnostics.modeToken();
            ShitTokSourceMode.set(activity.get(), ShitTokSourceMode.ONLY_FAP);
            ReflectionHelpers.callInstanceMethod(feed, "applyContentPreference");
            assertEquals(Arrays.asList(current), queue);
            assertEquals(generation, (int) ReflectionHelpers.getField(feed, "feedGeneration"));
            assertTrue(ShitTokPoolDiagnostics.modeToken() > before);
            ReflectionHelpers.callInstanceMethod(feed, "appendUnique", from(List.class, Arrays.asList(broad)));
            assertEquals(Arrays.asList(current), queue);
            assertEquals(learned, ShitTokSmartRanker.localScoreAt(broad, rankingInstant), 0f);
            assertTrue(feed.findViewWithTag("shittok_source_onlyfap").isSelected());
            feed.close();
        }
    }
    @Test public void sourceSwitchPreservesPlayerFocusMuteSpeedAndReturningRestoresBroadWarmClips() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            f.install(f.selected, f.selectedPlayer);
            ReflectionHelpers.setField(f.feed, "queuePreference", ContentPreference.ALL);
            ReflectionHelpers.setField(f.feed, "clearDisplay", true);
            ArrayList<NativeContentItem> items = ReflectionHelpers.getField(f.feed, "items");
            NativeContentItem current = items.get(0), future = ShitTokInventoryAdmissionTest.item("warm");
            items.subList(1, items.size()).clear(); items.add(future);
            Map<String, CrazyShitRepository.StreamInfo> streams = ReflectionHelpers.getField(f.feed, "streamCache");
            CrazyShitRepository.StreamInfo stream = new CrazyShitRepository.StreamInfo(future.url, future.url, "warm");
            streams.put(future.url, stream);
            boolean muted = ReflectionHelpers.getField(f.feed, "chaosMuted");
            Object player = ReflectionHelpers.getField(f.selected, "player");
            float speed = f.selectedPlayer.speed.speed;
            ShitTokSourceMode.set(f.host.get(), ShitTokSourceMode.ONLY_FAP);
            ReflectionHelpers.callInstanceMethod(f.feed, "applyContentPreference");
            assertEquals(Arrays.asList(current), items);
            assertSame(player, ReflectionHelpers.getField(f.selected, "player"));
            assertTrue((boolean) ReflectionHelpers.getField(f.feed, "clearDisplay"));
            assertEquals(muted, (boolean) ReflectionHelpers.getField(f.feed, "chaosMuted"));
            assertEquals(speed, f.selectedPlayer.speed.speed, 0f);
            ShitTokSourceMode.set(f.host.get(), ShitTokSourceMode.ALL);
            ReflectionHelpers.callInstanceMethod(f.feed, "applyContentPreference");
            assertEquals(Arrays.asList(current, future), items);
            assertSame(stream, streams.get(future.url));
        }
    }
    @Test public void modeDiagnosticsRejectOldEpochCompletionsAndKeepWholeSessionTotals() {
        boolean wasEnabled = ShitTokPoolDiagnostics.isEnabled();
        try {
            ShitTokPoolDiagnostics.setEnabled(true);
            long old = ShitTokPoolDiagnostics.beginMode(ShitTokSourceMode.ALL, ContentPreference.WOMEN);
            NativeContentItem item = ShitTokInventoryAdmissionTest.item("epoch");
            long current = ShitTokPoolDiagnostics.beginMode(ShitTokSourceMode.ONLY_FAP, ContentPreference.MEN);
            ShitTokPoolDiagnostics.modeEvent(old, "showRaw", 12);
            ShitTokPoolDiagnostics.admissionOutcome(item, "accepted", old);
            assertFalse(ShitTokPoolDiagnostics.modeSummary().contains("accepted="));
            assertFalse(ShitTokPoolDiagnostics.modeSummary().contains("showRaw="));
            assertTrue(ShitTokPoolDiagnostics.admissionSummary().contains("accepted="));
            ShitTokPoolDiagnostics.modeEvent(current, "metadataHits", 3);
            assertTrue(ShitTokPoolDiagnostics.modeSummary().contains("currentMode=onlyfap:men"));
            assertTrue(ShitTokPoolDiagnostics.modeSummary().contains("metadataHits=3"));
        } finally { ShitTokPoolDiagnostics.setEnabled(wasEnabled); }
    }

}
