package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Erome joins three ShitTok feeds without taking over Chaos, Groups, or OnlyFap. */
public class ShitTokEromeFeedTest {
    @Before public void setup() {
        ShitTokSmartRanker.resetForTest(null);
        ShitTokCommunityPopularity.setScoresForTest(Collections.emptyMap());
    }

    @After public void cleanup() {
        ShitTokSmartRanker.resetForTest(null);
        ShitTokCommunityPopularity.resetForTest();
    }

    @Test public void eromeIsLimitedToLadiesGuysAndTrans() {
        List<String> calls = new ArrayList<>();
        ShitTokFilteredInventory inventory = inventory(new Recording(calls));
        inventory.request(ShitTokSourceMode.ALL, ContentPreference.WOMEN, true, () -> {});
        assertEquals(1, inventory.take(ContentPreference.WOMEN).size());
        inventory.request(ShitTokSourceMode.ALL, ContentPreference.MEN, true, () -> {});
        assertEquals(1, inventory.take(ContentPreference.MEN).size());
        inventory.request(ShitTokSourceMode.ALL, ContentPreference.TRANS_WOMEN, true, () -> {});
        assertEquals(1, inventory.take(ContentPreference.TRANS_WOMEN).size());
        inventory.request(ShitTokSourceMode.ALL, ContentPreference.GROUPS, true, () -> {});
        assertTrue(inventory.take(ContentPreference.GROUPS).isEmpty());
        inventory.request(ShitTokSourceMode.ALL, ContentPreference.ALL, true, () -> {});
        assertTrue(inventory.take(ContentPreference.ALL).isEmpty());
        inventory.request(ShitTokSourceMode.ONLY_FAP, ContentPreference.WOMEN, true, () -> {});
        assertEquals(Arrays.asList("women:1", "men:1", "trans_women:1"), calls);
    }

    @Test public void eachFeedKeepsItsOwnPagesAndDropsRepeatVideos() {
        List<String> calls = new ArrayList<>();
        Recording backend = new Recording(calls);
        ShitTokFilteredInventory inventory = inventory(backend);
        inventory.request(ShitTokSourceMode.ALL, ContentPreference.WOMEN, true, () -> {});
        assertEquals("https://www.erome.com/a/Page1#clip", inventory.take(ContentPreference.WOMEN).get(0).url);
        inventory.request(ShitTokSourceMode.ALL, ContentPreference.WOMEN, true, () -> {});
        List<NativeContentItem> second = inventory.take(ContentPreference.WOMEN);
        assertEquals(1, second.size());
        assertEquals("https://www.erome.com/a/Page2#clip", second.get(0).url);
        inventory.request(ShitTokSourceMode.ALL, ContentPreference.MEN, true, () -> {});
        assertEquals("https://www.erome.com/a/Page1#clip", inventory.take(ContentPreference.MEN).get(0).url);
        assertTrue(inventory.matches(second.get(0), ContentPreference.WOMEN));
        assertFalse(inventory.matches(second.get(0), ContentPreference.MEN));
    }

    @Test public void eromeFailureDoesNotBlockTheOtherProvider() {
        Recording backend = new Recording(new ArrayList<>());
        backend.fail = true;
        backend.haven = true;
        ShitTokFilteredInventory inventory = inventory(backend);
        inventory.request(ShitTokSourceMode.ALL, ContentPreference.WOMEN, true, () -> {});
        List<NativeContentItem> ready = inventory.take(ContentPreference.WOMEN);
        assertFalse(ready.isEmpty());
        assertTrue(ready.stream().noneMatch(item -> item.url.contains("erome.com")));
        assertTrue(ready.stream().anyMatch(item -> item.url.contains("cum.st")));
    }

    @Test public void eromeDoesNotFillTheFeedWhenAnotherSourceIsReady() {
        Recording backend = new Recording(new ArrayList<>());
        backend.haven = true;
        backend.bulk = 6;
        ShitTokFilteredInventory inventory = inventory(backend);
        inventory.request(ShitTokSourceMode.ALL, ContentPreference.WOMEN, true, () -> {});
        List<NativeContentItem> ready = inventory.take(ContentPreference.WOMEN);
        int erome = 0;
        int haven = 0;
        for (NativeContentItem item : ready) {
            if (item.url.contains("erome.com")) erome++;
            else haven++;
        }
        assertTrue(erome > 0);
        assertTrue(haven > 0);
        assertTrue(erome < ready.size());
    }

    private static ShitTokFilteredInventory inventory(Recording backend) {
        return new ShitTokFilteredInventory(new ShitTokFilteredInventoryTest.Direct(), () -> 0L, backend);
    }

    private static final class Recording extends ShitTokFilteredInventoryTest.Fake {
        final List<String> calls;
        boolean fail;
        boolean haven;
        int bulk = 1;

        Recording(List<String> calls) { this.calls = calls; }

        @Override public boolean supportsErome() { return true; }

        @Override public boolean available(int source) {
            return source == ShitTokFilteredInventory.EROME
                    || (haven && source == ShitTokFilteredInventory.HAVEN);
        }

        @Override public java.util.List<NativeContentItem> media(int source, ShitTokFilteredInventory.Cursor cursor)
                throws Exception {
            if (!haven) return super.media(source, cursor);
            ArrayList<NativeContentItem> items = new ArrayList<>();
            for (int index = 0; index < 6; index++) {
                items.add(ShitTokFilteredInventoryTest.clip(cursor.creator.name, source, index));
            }
            return items;
        }

        @Override public EromeRepository.ShitTokBatch erome(ContentPreference mode, int page) throws Exception {
            calls.add(mode.value + ":" + page);
            if (fail) throw new java.io.IOException("Erome timed out");
            ArrayList<NativeContentItem> items = new ArrayList<>();
            if (page > 1) items.add(clip(page - 1, 0));
            for (int index = 0; index < bulk; index++) items.add(clip(page, index));
            return new EromeRepository.ShitTokBatch(items, page + 1, page < 3);
        }

        private NativeContentItem clip(int page, int index) {
            String suffix = bulk == 1 ? "" : "-" + index;
            String url = "https://www.erome.com/a/Page" + page + suffix + "#clip";
            return new NativeContentItem(
                    NativeContentItem.KIND_MEDIA, "clip", url,
                    "https://s1.erome.com/1/Page/clip.jpg", "", "Studio", "", "",
                    "", 0L, 9f / 16f).withSourceMedia(
                    "https://v1.erome.com/1/Page/clip_720p.mp4", "https://www.erome.com/a/Page" + page);
        }
    }
}
