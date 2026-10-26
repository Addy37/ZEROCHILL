package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ShowsCategoryMosaicSelectorTest {
    @Test
    public void categoryMosaicIsRandomizedButStableForOneSessionSeed() {
        List<NativeContentItem> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            items.add(new NativeContentItem(
                    NativeContentItem.KIND_MEDIA,
                    "Item " + i,
                    "https://example.com/video/" + i,
                    "https://example.com/thumb/" + i + ".jpg",
                    "",
                    "",
                    ""
            ));
        }
        items.add(new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "Missing art",
                "https://example.com/missing",
                "",
                "",
                "",
                ""
        ));

        List<NativeContentItem> first = ShowsCategoryMosaicSelector.select(
                items, "https://example.com/category/a", 12345L, 4);
        List<NativeContentItem> second = ShowsCategoryMosaicSelector.select(
                items, "https://example.com/category/a", 12345L, 4);

        assertEquals(4, first.size());
        assertEquals(first.get(0).imageUrl, second.get(0).imageUrl);
        assertEquals(first.get(1).imageUrl, second.get(1).imageUrl);
        for (NativeContentItem item : first) assertFalse(item.imageUrl.isEmpty());
    }
}
