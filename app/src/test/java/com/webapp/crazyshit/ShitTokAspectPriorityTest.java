package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class ShitTokAspectPriorityTest {
    @Test
    public void bucketUsesPortraitThenStandardThenWideOrder() {
        assertEquals(
                ShitTokAspectPriority.BUCKET_VERTICAL,
                ShitTokAspectPriority.bucket(9f / 16f)
        );
        assertEquals(
                ShitTokAspectPriority.BUCKET_STANDARD,
                ShitTokAspectPriority.bucket(4f / 3f)
        );
        assertEquals(
                ShitTokAspectPriority.BUCKET_STANDARD,
                ShitTokAspectPriority.bucket(1f)
        );
        assertEquals(
                ShitTokAspectPriority.BUCKET_WIDE,
                ShitTokAspectPriority.bucket(16f / 9f)
        );
        assertEquals(
                ShitTokAspectPriority.BUCKET_UNKNOWN,
                ShitTokAspectPriority.bucket(0f)
        );
    }

    @Test
    public void orderSoftlyPrefersVerticalThenStandardThenWideWithinSource() {
        ShitTokAspectPriority priority = new ShitTokAspectPriority();
        NativeContentItem wide = item(
                "wide", 16f / 9f, "https://same.example/wide", "", ""
        );
        NativeContentItem standard = item(
                "standard", 4f / 3f, "https://same.example/standard", "", ""
        );
        NativeContentItem vertical = item(
                "vertical", 9f / 16f, "https://same.example/vertical", "", ""
        );

        List<NativeContentItem> ordered = priority.order(
                Arrays.asList(wide, standard, vertical),
                new FixedRandom()
        );

        assertEquals("vertical", ordered.get(0).title);
        assertEquals("standard", ordered.get(1).title);
        assertEquals("wide", ordered.get(2).title);
    }

    @Test
    public void orderBalancesSourcesBeforeDrainingLargerPool() {
        ShitTokAspectPriority priority = new ShitTokAspectPriority();
        ArrayList<NativeContentItem> candidates = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            candidates.add(item(
                    "kaotic-" + i,
                    9f / 16f,
                    "https://kaotic.com/video/" + i,
                    "Kaotic",
                    ""
            ));
        }
        for (int i = 0; i < 2; i++) {
            candidates.add(item(
                    "shitshow-" + i,
                    9f / 16f,
                    "https://crazyshit.com/story/" + i,
                    "Shit Show",
                    ""
            ));
            candidates.add(item(
                    "baddiehub-" + i,
                    9f / 16f,
                    "https://baddiehub.com/post/" + i,
                    "BaddieHub",
                    ""
            ));
        }

        List<NativeContentItem> ordered = priority.order(candidates, new Random(37L));

        assertEquals(10, ordered.size());
        assertEquals(2, countSource(ordered.subList(0, 6), "kaotic"));
        assertEquals(2, countSource(ordered.subList(0, 6), "shit-show"));
        assertEquals(2, countSource(ordered.subList(0, 6), "baddiehub"));
    }

    @Test
    public void sourceKeySeparatesShitShowFromRegularCrazyShit() {
        NativeContentItem shitShow = item(
                "story",
                9f / 16f,
                "https://crazyshit.com/same-host/story",
                "Shit Show",
                ""
        );
        NativeContentItem crazyShit = item(
                "regular",
                9f / 16f,
                "https://crazyshit.com/same-host/regular",
                "Uploader Name",
                ""
        );

        assertEquals("shit-show", ShitTokAspectPriority.sourceKey(shitShow));
        assertEquals("crazyshit.com", ShitTokAspectPriority.sourceKey(crazyShit));
        assertNotEquals(
                ShitTokAspectPriority.sourceKey(shitShow),
                ShitTokAspectPriority.sourceKey(crazyShit)
        );
    }

    @Test
    public void existingPlayerSamplesTeachUnknownItemsFromSameSource() {
        ShitTokAspectPriority priority = new ShitTokAspectPriority();
        NativeContentItem first = item("one", 0f, "https://source.example/one", "", "");
        NativeContentItem second = item("two", 0f, "https://source.example/two", "", "");
        NativeContentItem future = item("future", 0f, "https://source.example/future", "", "");
        NativeContentItem unknown = item("unknown", 0f, "https://other.example/future", "", "");

        priority.record(first, 9f / 16f);
        priority.record(second, 3f / 4f);

        assertTrue(priority.weightFor(future) > priority.weightFor(unknown));
    }

    private static int countSource(List<NativeContentItem> items, String source) {
        int count = 0;
        for (NativeContentItem item : items) {
            if (source.equals(ShitTokAspectPriority.sourceKey(item))) count++;
        }
        return count;
    }

    private static NativeContentItem item(
            String title,
            float aspect,
            String url,
            String uploader,
            String description
    ) {
        return new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                title,
                url,
                "",
                "",
                uploader,
                "",
                description,
                "",
                0L,
                aspect
        );
    }

    private static final class FixedRandom extends Random {
        @Override
        public double nextDouble() {
            return 0.5d;
        }
    }
}
