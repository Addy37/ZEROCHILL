package com.webapp.crazyshit;

import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/** Verify warm-up targets never consume the selected category or bypass source restrictions. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokBackgroundCategoryWarmupTest {
    @Test public void chaosOpensFourOtherCreatorPreferencesInOrder() {
        Set<String> visited = new HashSet<>();
        ContentPreference[] expected = {
                ContentPreference.WOMEN, ContentPreference.MEN,
                ContentPreference.TRANS_WOMEN, ContentPreference.GROUPS
        };
        for (ContentPreference next : expected) {
            assertEquals(next, ShitTokBackgroundCategoryWarmup.nextPreference(
                    ShitTokSourceMode.ALL, ContentPreference.ALL, visited));
            visited.add(ShitTokSourceMode.ALL.key(next));
        }
        assertNull(ShitTokBackgroundCategoryWarmup.nextPreference(
                ShitTokSourceMode.ALL, ContentPreference.ALL, visited));
        assertEquals(512 * 1024, ShitTokBackgroundCategoryWarmup.MAX_WARM_BYTES);
    }

    @Test public void creatorTabNeverBackgroundsItselfAndOnlyFapCanWarmAll() {
        ContentPreference next = ShitTokBackgroundCategoryWarmup.nextPreference(
                ShitTokSourceMode.ONLY_FAP, ContentPreference.WOMEN, Collections.emptySet());
        assertEquals(ContentPreference.ALL, next);
        Set<String> tried = new HashSet<>();
        tried.add(ShitTokSourceMode.ALL.key(ContentPreference.WOMEN));
        ContentPreference fromGuys = ShitTokBackgroundCategoryWarmup.nextPreference(
                ShitTokSourceMode.ALL, ContentPreference.MEN, tried);
        assertNotEquals(ContentPreference.MEN, fromGuys);
        assertNotEquals(ContentPreference.WOMEN, fromGuys);
    }

    @Test public void categoryPoolSeedingNeverChangesActivePreference() {
        ShitTokFilteredInventory.Backend backend = new ShitTokFilteredInventory.Backend() {
            public OnlyHavenRepository.CategoryPage creators(ContentPreference mode, int offset) {
                return new OnlyHavenRepository.CategoryPage(Collections.emptyList(), false);
            }
            public java.util.List<NativeContentItem> media(int source,
                    ShitTokFilteredInventory.Cursor cursor) {
                return Collections.emptyList();
            }
            public java.util.List<NativeContentItem> show() { return Collections.emptyList(); }
            public boolean available(int source) { return source == ShitTokFilteredInventory.HAVEN; }
        };
        ShitTokFilteredInventory inventory = new ShitTokFilteredInventory(
                new ShitTokFilteredInventoryTest.Direct(), () -> 100L, backend);
        NativeContentItem ladies = new NativeContentItem("media", "Warm lady",
                "https://example.invalid/lady.mp4", "", "", "", "", "", "", 0, 0.55f);
        inventory.offerBackgroundStarter(ShitTokSourceMode.ALL, ContentPreference.WOMEN, ladies);
        assertFalse(inventory.hasReady(ContentPreference.WOMEN));
        inventory.select(ShitTokSourceMode.ALL, ContentPreference.WOMEN);
        assertTrue(inventory.hasReady(ContentPreference.WOMEN));
        assertEquals(1, inventory.take(ContentPreference.WOMEN).size());
        assertTrue(inventory.matches(ladies, ContentPreference.WOMEN));
        inventory.select(ShitTokSourceMode.ALL, ContentPreference.MEN);
        assertFalse(inventory.hasReady(ContentPreference.MEN));
        assertFalse(inventory.matches(ladies, ContentPreference.MEN));
    }

    @Test public void wrongSourceCannotSeedUnclassifiedChaos() {
        ShitTokFilteredInventory.Backend backend = new ShitTokFilteredInventory.Backend() {
            public OnlyHavenRepository.CategoryPage creators(ContentPreference mode, int offset) {
                return new OnlyHavenRepository.CategoryPage(Collections.emptyList(), false);
            }
            public java.util.List<NativeContentItem> media(int source, ShitTokFilteredInventory.Cursor cursor) {
                return Collections.emptyList();
            }
            public java.util.List<NativeContentItem> show() { return Collections.emptyList(); }
            public boolean available(int source) { return true; }
        };
        ShitTokFilteredInventory inventory = new ShitTokFilteredInventory(
                new ShitTokFilteredInventoryTest.Direct(), () -> 100L, backend);
        NativeContentItem clip = new NativeContentItem("media", "No category",
                "https://example.invalid/clip.mp4", "", "", "", "", "", "", 0, 0.55f);
        inventory.offerBackgroundStarter(ShitTokSourceMode.ALL, ContentPreference.ALL, clip);
        inventory.select(ShitTokSourceMode.ALL, ContentPreference.ALL);
        assertFalse(inventory.hasReady(ContentPreference.ALL));
    }
}
