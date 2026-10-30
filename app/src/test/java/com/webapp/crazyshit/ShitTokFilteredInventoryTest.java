package com.webapp.crazyshit;

import org.junit.*;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class ShitTokFilteredInventoryTest {
    static final class Direct extends AbstractExecutorService {
        private boolean stopped;
        public void shutdown() { stopped = true; }
        public List<Runnable> shutdownNow() { stopped = true; return Collections.emptyList(); }
        public boolean isShutdown() { return stopped; }
        public boolean isTerminated() { return stopped; }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return stopped; }
        public void execute(Runnable task) { task.run(); }
    }
    static OnlyHavenRepository.Creator creator(String name) {
        return new OnlyHavenRepository.Creator("onlyfans", name, name,
                "https://cum.st/creators/onlyfans/" + name.replace(" ", "-"), "");
    }
    static NativeContentItem clip(String name, int source, int page) {
        String url = source == 5 ? "https://fapello.com/" + name + "/" + page + "/"
                : source == 9 ? "https://bunkr.cr/f/" + name + page
                : "https://e1.cum.st/" + name + page + ".mp4";
        return new NativeContentItem("media", "clip", url, "", "",
                source == 5 ? name : creator(name).url, "", "OnlyHaven", name, 0, .5f);
    }
    static ShitTokFilteredInventory seed(ContentPreference mode, List<NativeContentItem> clips) {
        ShitTokFilteredInventory inventory = new ShitTokFilteredInventory(new Direct(), () -> 0L,
                new ShitTokFilteredInventory.Backend() {
                    public OnlyHavenRepository.CategoryPage creators(ContentPreference m, int offset) {
                        return new OnlyHavenRepository.CategoryPage(Collections.singletonList(creator("Verified")), false);
                    }
                    public List<NativeContentItem> media(int source, ShitTokFilteredInventory.Cursor c) { return clips; }
                    public List<NativeContentItem> show() { return Collections.emptyList(); }
                    public boolean available(int source) { return source == 6; }
                });
        inventory.request(mode, true, () -> {});
        return inventory;
    }
    static class Fake implements ShitTokFilteredInventory.Backend {
        final List<Integer> offsets = new ArrayList<>();
        final Map<Integer, Integer> mediaCalls = new HashMap<>();
        boolean enabled = true;
        public OnlyHavenRepository.CategoryPage creators(ContentPreference mode, int offset) throws Exception {
            offsets.add(offset);
            List<OnlyHavenRepository.Creator> creators = new ArrayList<>();
            for (int i = 0; i < 12; i++) creators.add(creator(mode.value + (offset + i)));
            return new OnlyHavenRepository.CategoryPage(creators, offset < 24);
        }
        public List<NativeContentItem> media(int source, ShitTokFilteredInventory.Cursor c) throws Exception {
            mediaCalls.merge(source, 1, Integer::sum);
            return Collections.singletonList(clip(c.creator.name, source, c.page));
        }
        public List<NativeContentItem> show() { return Collections.singletonList(new NativeContentItem(
                "media", "clip", "https://crazyshit.com/story/1", "", "", "Shit Show", "", "", "", 0, .5f)); }
        public boolean available(int source) { return enabled; }
    }
    @Before public void setup() {
        ShitTokSmartRanker.resetForTest(null);
        ShitTokCommunityPopularity.setScoresForTest(Collections.emptyMap());
    }
    @After public void cleanup() {
        ShitTokSmartRanker.resetForTest(null); ShitTokCommunityPopularity.resetForTest();
    }
    @Test public void confirmedCreatorMediaIsCategoryScopedForEverySupportedSource() {
        Fake backend = new Fake();
        ShitTokFilteredInventory inv = new ShitTokFilteredInventory(new Direct(), () -> 0L, backend);
        inv.request(ContentPreference.WOMEN, true, () -> {});
        List<NativeContentItem> ready = inv.take(ContentPreference.WOMEN);
        assertEquals(6, ready.size());
        assertTrue(ready.stream().anyMatch(i -> i.url.contains("fapello")));
        assertTrue(ready.stream().anyMatch(i -> i.url.contains("bunkr")));
        for (NativeContentItem item : ready) {
            assertTrue(inv.matches(item, ContentPreference.WOMEN));
            for (ContentPreference mode : ContentPreference.values()) if (mode != ContentPreference.ALL && mode != ContentPreference.WOMEN)
                assertFalse(inv.matches(item, mode));
        }
        NativeContentItem guessed = new NativeContentItem("media", "Women", "https://fapello.com/unverified/1/",
                "https://example.invalid/women.jpg", "", "Women", "", "Women", "Women", 0, .5f);
        assertFalse(inv.matches(guessed, ContentPreference.WOMEN));
        backend.enabled = false;
        for (NativeContentItem item : ready) assertFalse(inv.matches(item, ContentPreference.WOMEN));
    }
    @Test public void showIsDirectOnlyInWomenAndNoOtherFilteredModeRequestsIt() {
        for (ContentPreference mode : ContentPreference.values()) {
            Fake backend = new Fake();
            ShitTokFilteredInventory inv = new ShitTokFilteredInventory(new Direct(), () -> 0L, backend);
            inv.request(mode, true, () -> {});
            List<NativeContentItem> ready = inv.take(mode);
            boolean hasShow = ready.stream().anyMatch(i -> i.uploader.equals("Shit Show"));
            assertEquals(mode == ContentPreference.WOMEN, hasShow);
            if (mode == ContentPreference.ALL) assertTrue(backend.offsets.isEmpty());
        }
    }
    @Test public void warmInventoriesAndPaginationSurviveRapidSwitchesWithoutCrossContamination() {
        Fake backend = new Fake();
        ShitTokFilteredInventory inv = new ShitTokFilteredInventory(new Direct(), () -> 0L, backend);
        inv.request(ContentPreference.WOMEN, true, () -> {});
        inv.request(ContentPreference.MEN, true, () -> {});
        assertTrue(inv.take(ContentPreference.WOMEN).isEmpty());
        List<NativeContentItem> men = inv.take(ContentPreference.MEN);
        inv.request(ContentPreference.WOMEN, false, () -> {});
        assertTrue(inv.hasReady(ContentPreference.WOMEN));
        List<NativeContentItem> women = inv.take(ContentPreference.WOMEN);
        for (NativeContentItem item : women) assertFalse(inv.matches(item, ContentPreference.MEN));
        for (NativeContentItem item : men) assertFalse(inv.matches(item, ContentPreference.WOMEN));
        assertEquals(Arrays.asList(0, 0), backend.offsets);
    }
    @Test public void laterCreatorPagesResumeOnDemandAndFullRunwayDoesNotDiscover() {
        Fake backend = new Fake();
        ShitTokFilteredInventory inv = new ShitTokFilteredInventory(new Direct(), () -> 0L, backend);
        for (int i = 0; i < 5; i++) {
            inv.request(ContentPreference.GROUPS, true, () -> {});
            assertFalse("No dry tail with unconsumed creators", inv.take(ContentPreference.GROUPS).isEmpty());
        }
        assertEquals(Arrays.asList(0, 12), backend.offsets);
        Map<Integer, Integer> before = new HashMap<>(backend.mediaCalls);
        for (int i = 0; i < 100; i++) inv.request(ContentPreference.GROUPS, false, () -> fail());
        assertEquals(before, backend.mediaCalls);
        inv.request(ContentPreference.GROUPS, true, () -> {});
        assertNotEquals(before, backend.mediaCalls);
        assertEquals(Arrays.asList(0, 12), backend.offsets);
    }
    @Test public void photoOnlyPagesContinueInsteadOfEmptyBackoffHidingLaterInventory() {
        Fake backend = new Fake() {
            @Override public List<NativeContentItem> media(int source, ShitTokFilteredInventory.Cursor c) {
                if (c.creator.name.matches("men[0-2]")) return Collections.emptyList();
                return Collections.singletonList(clip(c.creator.name, source, 1));
            }
            @Override public boolean available(int source) { return source == 6; }
        };
        ShitTokFilteredInventory inv = new ShitTokFilteredInventory(new Direct(), () -> 0L, backend);
        inv.request(ContentPreference.MEN, true, () -> {});
        assertTrue(inv.take(ContentPreference.MEN).isEmpty());
        inv.request(ContentPreference.MEN, true, () -> {});
        assertEquals(3, inv.take(ContentPreference.MEN).size());
    }
    @Test public void failedLaterCategoryPageBacksOffWithoutBlockingVerifiedCreatorMedia() {
        AtomicInteger laterPages = new AtomicInteger();
        Fake backend = new Fake() {
            @Override public OnlyHavenRepository.CategoryPage creators(ContentPreference mode, int offset) throws Exception {
                if (offset > 0) { laterPages.incrementAndGet(); throw new java.io.IOException("offline page"); }
                return super.creators(mode, offset);
            }
            @Override public boolean available(int source) { return source == 6; }
        };
        ShitTokFilteredInventory inv = new ShitTokFilteredInventory(new Direct(), () -> 0L, backend);
        for (int i = 0; i < 8; i++) {
            inv.request(ContentPreference.MEN, true, () -> {});
            assertFalse("Verified continuation must survive pagination failure", inv.take(ContentPreference.MEN).isEmpty());
        }
        assertEquals(1, laterPages.get());
        assertTrue(inv.summary().contains("offset=12"));
    }

    @Test public void uninterruptibleOldCategoryCannotPublishIntoNewCategory() throws Exception {
        ExecutorService io = Executors.newFixedThreadPool(3);
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), finished = new CountDownLatch(1);
        Fake backend = new Fake() {
            @Override public OnlyHavenRepository.CategoryPage creators(ContentPreference mode, int offset) throws Exception {
                if (mode == ContentPreference.WOMEN) {
                    started.countDown();
                    while (release.getCount() > 0) try { release.await(); } catch (InterruptedException ignored) { }
                    finished.countDown();
                }
                return super.creators(mode, offset);
            }
            @Override public boolean available(int source) { return source == 6; }
        };
        ShitTokFilteredInventory inv = new ShitTokFilteredInventory(io, () -> 0L, backend);
        try {
            inv.request(ContentPreference.WOMEN, true, () -> {});
            assertTrue(started.await(2, TimeUnit.SECONDS));
            CountDownLatch menReady = new CountDownLatch(1);
            inv.request(ContentPreference.MEN, true, menReady::countDown);
            release.countDown();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            assertTrue(menReady.await(2, TimeUnit.SECONDS));
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (!inv.hasReady(ContentPreference.MEN) && System.nanoTime() < end) Thread.yield();
            for (NativeContentItem item : inv.take(ContentPreference.MEN)) {
                assertTrue(item.searchQuery.startsWith("men"));
                assertFalse(inv.matches(item, ContentPreference.WOMEN));
            }
            assertTrue(inv.take(ContentPreference.WOMEN).isEmpty());
        } finally { release.countDown(); inv.pause(); io.shutdownNow(); }
    }
}
