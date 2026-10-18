package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public final class ShitTokBlockedCreatorStoreTest {
    @Test
    public void fapelloCreator_canBeMatchedByStableCreatorKey() {
        NativeContentItem item = new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "493857_video_final",
                "https://fapello.com/video/12345/",
                "",
                "",
                "Sophie Rain",
                "",
                "OnlyFap"
        );
        String key = ShitTokBlockedCreatorStore.keyForName("Sophie Rain");
        Set<String> blocked = new HashSet<>(Collections.singletonList(key));

        assertEquals(key, ShitTokBlockedCreatorStore.keyFor(item));
        assertTrue(ShitTokBlockedCreatorStore.isBlocked(blocked, item));
    }

    @Test
    public void onlyHavenCreator_usesSameNameNormalization() {
        NativeContentItem item = new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "Jane Doe",
                "https://img.cum.st/post/video.mp4",
                "",
                "OnlyHaven",
                "OnlyHaven",
                "",
                "onlyfans · OnlyHaven"
        );
        Set<String> blocked = Collections.singleton(
                ShitTokBlockedCreatorStore.keyForName("  Jane   Doe  ")
        );

        assertTrue(ShitTokBlockedCreatorStore.isBlocked(blocked, item));
    }

    @Test
    public void reviewedCreatorAliases_shareBlockKey() {
        assertEquals(
                ShitTokBlockedCreatorStore.keyForName("Belle Del"),
                ShitTokBlockedCreatorStore.keyForName("Belle Delphine")
        );
    }

    @Test
    public void regularShitTokClip_isNeverCreatorBlocked() {
        NativeContentItem item = new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "Normal video",
                "https://crazyshit.com/cnt/medias/123",
                "",
                "",
                "Uploader",
                "",
                ""
        );

        assertEquals("", ShitTokBlockedCreatorStore.keyFor(item));
        assertFalse(ShitTokBlockedCreatorStore.isBlocked(
                Collections.singleton("uploader"),
                item
        ));
    }
}
