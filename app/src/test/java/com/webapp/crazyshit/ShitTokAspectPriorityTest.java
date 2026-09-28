package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
    public void orderSoftlyPrefersVerticalThenStandardThenWide() {
        ShitTokAspectPriority priority = new ShitTokAspectPriority();
        NativeContentItem wide = item("wide", 16f / 9f, "https://wide.example/video");
        NativeContentItem standard = item("standard", 4f / 3f, "https://standard.example/video");
        NativeContentItem vertical = item("vertical", 9f / 16f, "https://vertical.example/video");

        List<NativeContentItem> ordered = priority.order(
                Arrays.asList(wide, standard, vertical),
                new FixedRandom()
        );

        assertEquals("vertical", ordered.get(0).title);
        assertEquals("standard", ordered.get(1).title);
        assertEquals("wide", ordered.get(2).title);
    }

    @Test
    public void existingPlayerSamplesTeachUnknownItemsFromSameSource() {
        ShitTokAspectPriority priority = new ShitTokAspectPriority();
        NativeContentItem first = item("one", 0f, "https://source.example/one");
        NativeContentItem second = item("two", 0f, "https://source.example/two");
        NativeContentItem future = item("future", 0f, "https://source.example/future");
        NativeContentItem unknown = item("unknown", 0f, "https://other.example/future");

        priority.record(first, 9f / 16f);
        priority.record(second, 3f / 4f);

        assertTrue(priority.weightFor(future) > priority.weightFor(unknown));
    }

    private static NativeContentItem item(String title, float aspect, String url) {
        return new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                title,
                url,
                "",
                "",
                "",
                "",
                "",
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
