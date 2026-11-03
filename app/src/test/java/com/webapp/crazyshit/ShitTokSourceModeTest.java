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
    @Test public void selectorOnlyAppearsForAllAndRestoresWithoutChangingCategory() {
        try (org.robolectric.android.controller.ActivityController<Activity> activity = Robolectric.buildActivity(Activity.class).setup()) {
            ChaosFeedView feed = new ChaosFeedView(activity.get(), item -> {}, false);
            try {
                for (ContentPreference mode : ContentPreference.values()) {
                    ContentPreference.set(activity.get(), mode);
                    ReflectionHelpers.callInstanceMethod(feed, "applyContentPreference");
                    assertEquals(mode == ContentPreference.ALL ? android.view.View.VISIBLE : android.view.View.GONE,
                            feed.findViewWithTag("shittok_source_control").getVisibility());
                    assertEquals(mode, ContentPreference.get(activity.get()));
                }
                ContentPreference.set(activity.get(), ContentPreference.ALL);
                ReflectionHelpers.callInstanceMethod(feed, "applyContentPreference");
                assertEquals(android.view.View.VISIBLE, feed.findViewWithTag("shittok_source_control").getVisibility());
            } finally { feed.close(); }
        }
    }
    @Test public void historicalViewingAndLikesDoNotExcludeValidCandidates() {
        try (org.robolectric.android.controller.ActivityController<Activity> activity = Robolectric.buildActivity(Activity.class).setup()) {
            ChaosFeedView feed = new ChaosFeedView(activity.get(), item -> {}, false);
            try {
                NativeContentItem item = new NativeContentItem("media", "fixture", "https://kaotic.com/video/fixture", "", "", "Kaotic", "");
                ShitTokSeenStore store = ShitTokSeenStore.get(activity.get());
                store.watch(item.url, null); store.heart(item.url, null);
                assertFalse(ReflectionHelpers.callInstanceMethod(feed, "discoveryExcluded",
                        from(NativeContentItem.class, item), from(CrazyShitRepository.StreamInfo.class, null)));
            } finally { feed.close(); }
        }
    }
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

    /** Deliver a real pager callback from the old layout while the adapter publishes a shrink. */
    private static void pageCallback(ChaosFeedView feed, int position) {
        androidx.viewpager2.widget.ViewPager2 pager = ReflectionHelpers.getField(feed, "pager");
        // ViewPager's internal callback updates currentItem before invoking application callbacks.
        ReflectionHelpers.setField(pager, "mCurrentItem", position);
        Object callbacks = ReflectionHelpers.getField(pager, "mExternalPageChangeCallbacks");
        ReflectionHelpers.callInstanceMethod(callbacks, "onPageSelected", from(int.class, position));
    }

    @Test @Config(sdk = {29, 35}) public void deepSourceSwitchKeepsSelectedUrlWhenOldLayoutReportsPageTenDuringPrune() {
        try (DeepFeed f = new DeepFeed()) {
            NativeContentItem selected = f.items.get(10);
            int token = f.owner.token(selected.url);
            androidx.recyclerview.widget.RecyclerView.Adapter<?> adapter = ReflectionHelpers.getField(f.feed, "adapter");
            boolean[] delivered = {false};
            adapter.registerAdapterDataObserver(new androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
                @Override public void onItemRangeRemoved(int first, int count) {
                    if (!delivered[0] && f.items.size() == 6) {
                        delivered[0] = true;
                        pageCallback(f.feed, 10); // old ViewPager layout; i=9 against the six-row model
                    }
                }
            });
            ShitTokSourceMode.set(f.host.get(), ShitTokSourceMode.ONLY_FAP);
            ReflectionHelpers.callInstanceMethod(f.feed, "applyContentPreference");
            ReflectionHelpers.callInstanceMethod(f.feed, "settleSelectedPage");
            assertTrue(delivered[0]);
            assertEquals(6, f.items.size());
            assertSame(selected, f.items.get(5));
            assertEquals(5, (int) ReflectionHelpers.getField(f.feed, "selectedPosition"));
            assertEquals(5, f.pager.getCurrentItem());
            assertEquals(selected.url, ReflectionHelpers.getField(f.owner, "selected"));
            assertEquals(token, f.owner.token(selected.url));
        }
    }

    private static void consistent(DeepFeed f, NativeContentItem selected) {
        int position = ReflectionHelpers.getField(f.feed, "selectedPosition");
        assertTrue(position >= 0 && position < f.items.size());
        assertSame(selected, f.items.get(position));
        assertEquals(position, f.pager.getCurrentItem());
        assertEquals(selected.url, ReflectionHelpers.getField(f.feed, "selectedUrl"));
        assertEquals(selected.url, ReflectionHelpers.getField(f.owner, "selected"));
        if ((boolean) ReflectionHelpers.getField(f.feed, "hostResumed")
                && !(boolean) ReflectionHelpers.getField(f.feed, "searchCoverActive")) {
            assertEquals(position, (int) ReflectionHelpers.getField(f.feed, "plannedPosition"));
            assertEquals(selected.url, ReflectionHelpers.getField(f.feed, "plannedUrl"));
        }
        assertTrue(f.engines.stream().filter(e -> e.play && e.volume > 0f).count() <= 1);
        for (ShitTokPlayerPoolTest.Engine engine : f.engines)
            if (engine.play && engine.volume > 0f) assertSame(f.owner.player(selected.url), engine.player);
        Set<?> holders = ReflectionHelpers.getField(f.feed, "playerHolders");
        for (Object holder : holders) {
            NativeContentItem item = ReflectionHelpers.getField(holder, "item");
            int bound = ReflectionHelpers.getField(holder, "boundPosition");
            if (bound == androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                assertNull(ReflectionHelpers.getField(holder, "player"));
                assertFalse(f.items.contains(item));
            } else {
                assertTrue(bound >= 0 && bound < f.items.size());
                assertEquals(item.url, f.items.get(bound).url);
            }
        }
    }

    @Test public void numericallyValidOldLayoutCallbackCannotSelectAnotherClipOrResetResume() {
        try (DeepFeed f = new DeepFeed()) {
            NativeContentItem current = f.items.get(10);
            ShitTokSessionResume resume = ReflectionHelpers.getField(f.feed, "sessionResume");
            resume.remember(current.url, 12_000, 60_000);
            androidx.recyclerview.widget.RecyclerView.Adapter adapter = f.pager.getAdapter();
            androidx.recyclerview.widget.RecyclerView recycler = (androidx.recyclerview.widget.RecyclerView) f.pager.getChildAt(0);
            androidx.recyclerview.widget.RecyclerView.ViewHolder removedHolder = adapter.createViewHolder(recycler, 0);
            adapter.bindViewHolder(removedHolder, 2);
            ((Set<Object>) ReflectionHelpers.getField(f.feed, "playerHolders")).add(removedHolder);
            boolean[] delivered = {false};
            adapter.registerAdapterDataObserver(new androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
                @Override public void onItemRangeRemoved(int first, int count) {
                    if (!delivered[0] && f.items.size() == 6) {
                        delivered[0] = true;
                        pageCallback(f.feed, 4); // fits the new list, but belongs to the old layout
                    }
                }
            });
            ShitTokSourceMode.set(f.host.get(), ShitTokSourceMode.ONLY_FAP);
            ReflectionHelpers.callInstanceMethod(f.feed, "applyContentPreference");
            assertTrue(delivered[0]);
            consistent(f, current);
            assertEquals(12_000, resume.positionFor(current.url));
            assertEquals(androidx.recyclerview.widget.RecyclerView.NO_POSITION,
                    (int) ReflectionHelpers.getField(removedHolder, "boundPosition"));
            assertNull(ReflectionHelpers.getField(removedHolder, "player"));
            pageCallback(f.feed, 10); // delayed invalid callback after the synchronous update
            consistent(f, current);
            f.layout();
            f.pager.setCurrentItem(4, false); // a new, real selection still works after layout
            consistent(f, f.items.get(4));
        }
    }

    @Test public void rapidGenderAndSourceChangesDuringSwipeApplyLatestModeAtIdle() {
        try (DeepFeed f = new DeepFeed()) {
            NativeContentItem current = f.items.get(10);
            for (int i = 0; i < 3; i++) {
                ReflectionHelpers.callInstanceMethod(f.feed, "handlePagerScrollState",
                        from(int.class, androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_DRAGGING));
                ShitTokSourceMode.set(f.host.get(), ShitTokSourceMode.ONLY_FAP);
                ContentPreference.set(f.host.get(), ContentPreference.MEN);
                ReflectionHelpers.callInstanceMethod(f.feed, "applyContentPreference");
                ContentPreference.set(f.host.get(), ContentPreference.ALL);
                ReflectionHelpers.callInstanceMethod(f.feed, "applyContentPreference");
                ReflectionHelpers.callInstanceMethod(f.feed, "handlePagerScrollState",
                        from(int.class, androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_IDLE));
                consistent(f, current);
                assertEquals(ShitTokSourceMode.ONLY_FAP, ReflectionHelpers.getField(f.feed, "queueSources"));
                assertEquals(ContentPreference.ALL, ReflectionHelpers.getField(f.feed, "queuePreference"));
                ReflectionHelpers.callInstanceMethod(f.feed, "settleSelectedPage");
            }
            assertEquals(6, f.items.size());
        }
    }

    @Test public void genderPruneToOneRetainsTheCurrentLeaseAndPosition() {
        try (DeepFeed f = new DeepFeed()) {
            NativeContentItem current = f.items.get(10);
            int token = f.owner.token(current.url);
            ContentPreference.set(f.host.get(), ContentPreference.MEN);
            ReflectionHelpers.callInstanceMethod(f.feed, "applyContentPreference");
            ReflectionHelpers.callInstanceMethod(f.feed, "settleSelectedPage");
            assertEquals(1, f.items.size());
            consistent(f, current);
            assertEquals(token, f.owner.token(current.url));
        }
    }

    @Test public void refreshFromDeepPagePublishesEmptySelectionBeforeOldPageCallbacks() {
        try (DeepFeed f = new DeepFeed()) {
            f.pager.getAdapter().registerAdapterDataObserver(new androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
                @Override public void onChanged() { pageCallback(f.feed, 10); }
            });
            f.feed.refresh();
            ReflectionHelpers.callInstanceMethod(f.feed, "settleSelectedPage");
            assertTrue(f.items.isEmpty());
            assertEquals(0, (int) ReflectionHelpers.getField(f.feed, "selectedPosition"));
            assertEquals(0, f.pager.getCurrentItem());
            assertEquals("", ReflectionHelpers.getField(f.feed, "selectedUrl"));
            assertEquals(-1, (int) ReflectionHelpers.getField(f.feed, "plannedPosition"));
            assertEquals("", ReflectionHelpers.getField(f.feed, "plannedUrl"));
            assertNull(ReflectionHelpers.getField(f.owner, "selected"));
            for (Object holder : (Set<?>) ReflectionHelpers.getField(f.feed, "playerHolders")) {
                assertEquals(androidx.recyclerview.widget.RecyclerView.NO_POSITION,
                        (int) ReflectionHelpers.getField(holder, "boundPosition"));
                assertNull(ReflectionHelpers.getField(holder, "player"));
            }
        }
    }

    @Test public void hidingEarlierClipKeepsCurrentAndHidingCurrentChoosesNearestSurvivor() {
        try (DeepFeed f = new DeepFeed()) {
            NativeContentItem current = f.items.get(10);
            ReflectionHelpers.callInstanceMethod(f.feed, "hideFromChaos", from(NativeContentItem.class, f.items.get(2)));
            consistent(f, current);
            assertEquals(9, (int) ReflectionHelpers.getField(f.feed, "selectedPosition"));
            NativeContentItem nearest = f.items.get(8);
            ReflectionHelpers.callInstanceMethod(f.feed, "hideFromChaos", from(NativeContentItem.class, current));
            consistent(f, nearest);
            assertEquals(8, (int) ReflectionHelpers.getField(f.feed, "selectedPosition"));
        }
    }

    @Test public void backgroundAndSearchReturnReconcilePrunedSelectionWithoutGhostAudio() {
        try (DeepFeed f = new DeepFeed()) {
            NativeContentItem current = f.items.get(10);
            f.feed.onSearchOpened();
            ShitTokSourceMode.set(f.host.get(), ShitTokSourceMode.ONLY_FAP);
            ReflectionHelpers.callInstanceMethod(f.feed, "applyContentPreference");
            consistent(f, current);
            f.feed.onSearchClosed();
            consistent(f, current);
            f.feed.onHostPause();
            ContentPreference.set(f.host.get(), ContentPreference.MEN);
            ReflectionHelpers.callInstanceMethod(f.feed, "applyContentPreference");
            consistent(f, current);
            assertTrue(f.engines.stream().noneMatch(e -> e.play));
            f.feed.onHostResume();
            consistent(f, current);
            assertEquals(1, f.items.size());
        }
    }

    @Test public void settlingRepairsLegacyStaleSelectionByUrlBeforeAnyIndexRead() {
        try (DeepFeed f = new DeepFeed()) {
            NativeContentItem current = f.items.get(10);
            f.items.subList(0, 5).clear(); // emulate a previously inconsistent restored model
            assertEquals(10, (int) ReflectionHelpers.getField(f.feed, "selectedPosition"));
            ReflectionHelpers.callInstanceMethod(f.feed, "settleSelectedPage");
            consistent(f, current);
            assertEquals(5, (int) ReflectionHelpers.getField(f.feed, "selectedPosition"));
        }
    }

    private static final class DeepFeed extends ShitTokContinuityTest.Fixture {
        final androidx.viewpager2.widget.ViewPager2 pager = ReflectionHelpers.getField(feed, "pager");
        DeepFeed() {
            ReflectionHelpers.setField(feed, "queuePreference", ContentPreference.ALL);
            ShitTokFilteredInventory filtered = inventory();
            filtered.request(ShitTokSourceMode.ONLY_FAP, ContentPreference.ALL, true, () -> {});
            List<NativeContentItem> known = filtered.take(ContentPreference.ALL);
            assertTrue(known.size() >= 5);
            for (int i = 1; i <= 10; i++) {
                NativeContentItem item = new NativeContentItem("media", "Deep " + i,
                        "https://fixture.invalid/deep-" + i, "", "", i % 2 == 0 ? "Kaotic" : "Shit Show",
                        "", "", "", 0, .5f);
                if (i % 2 != 0) item = known.get((i - 1) / 2);
                items.add(item); cache.put(item.url, ShitTokContinuityTest.stream(item));
            }
            pager.getAdapter().notifyDataSetChanged();
            host.get().setContentView(feed);
            feed.measure(android.view.View.MeasureSpec.makeMeasureSpec(800, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(1600, android.view.View.MeasureSpec.EXACTLY));
            feed.layout(0, 0, 800, 1600);
            pager.setCurrentItem(10, false);
            layout();
            ReflectionHelpers.callInstanceMethod(feed, "reconcilePlaybackWindow");
            assertEquals(10, (int) ReflectionHelpers.getField(feed, "selectedPosition"));
            ChaosSourceMixer mixer = ReflectionHelpers.getField(feed, "sourceMixer");
            ReflectionHelpers.setField(mixer, "poolSources", ShitTokSourceMode.ONLY_FAP);
            ReflectionHelpers.setField(mixer, "filtered", filtered);
        }
        void layout() {
            feed.forceLayout(); pager.forceLayout();
            feed.measure(android.view.View.MeasureSpec.makeMeasureSpec(800, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(1600, android.view.View.MeasureSpec.EXACTLY));
            feed.layout(0, 0, 800, 1600);
        }
    }

}
