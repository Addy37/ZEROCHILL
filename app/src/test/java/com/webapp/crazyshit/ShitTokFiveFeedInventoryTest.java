package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** No network required: verify that optional BaddieHub inventory cannot leak across feeds. */
public class ShitTokFiveFeedInventoryTest {
    @Test public void baddieHubIsRequestedOnlyForLadiesAndGroups() {
        List<ContentPreference> requested = new ArrayList<>();
        ShitTokFilteredInventory.Backend backend = new ShitTokFilteredInventory.Backend() {
            public OnlyHavenRepository.CategoryPage creators(ContentPreference mode, int offset) {
                return new OnlyHavenRepository.CategoryPage(Collections.emptyList(), false);
            }
            public List<NativeContentItem> media(int source, ShitTokFilteredInventory.Cursor cursor) {
                return Collections.emptyList();
            }
            public List<NativeContentItem> show() { return Collections.emptyList(); }
            public boolean available(int source) { return source == ShitTokFilteredInventory.BADDIEHUB; }
            public boolean supportsBaddieHub() { return true; }
            public List<NativeContentItem> baddieHub(ContentPreference mode, int page) {
                requested.add(mode);
                return Collections.singletonList(new NativeContentItem(
                        NativeContentItem.KIND_MEDIA, mode.value,
                        "https://baddiehub.com/videos/" + mode.value + page,
                        "", "", "BaddieHub", "", "", "", 0L, .5f));
            }
        };
        ShitTokFilteredInventory inventory = new ShitTokFilteredInventory(
                new ShitTokFilteredInventoryTest.Direct(), () -> 0L, backend);
        for (ContentPreference mode : new ContentPreference[]{
                ContentPreference.WOMEN, ContentPreference.MEN, ContentPreference.TRANS_WOMEN,
                ContentPreference.GROUPS}) {
            inventory.request(ShitTokSourceMode.ALL, mode, true, () -> {});
            List<NativeContentItem> items = inventory.take(mode);
            if (mode == ContentPreference.WOMEN || mode == ContentPreference.GROUPS) {
                assertEquals(1, items.size());
                assertTrue(inventory.matches(items.get(0), mode));
            } else assertTrue(items.isEmpty());
        }
        assertEquals(java.util.Arrays.asList(ContentPreference.WOMEN, ContentPreference.GROUPS), requested);
    }
}
