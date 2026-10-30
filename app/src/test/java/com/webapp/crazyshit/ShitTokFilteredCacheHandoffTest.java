package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokFilteredCacheHandoffTest {
    @Test public void allWomenAllPreservesSharedShowStockWithoutOpeningOtherSources() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ChaosSourceMixer mixer = new ChaosSourceMixer(new CrazyShitRepository(), new Random(1));
        ShitTokSourcePool broad = ReflectionHelpers.getField(mixer, "sourcePool");
        NativeContentItem show = ShitTokSourcePoolTest.clip("cached-show", 1);
        NativeContentItem other = ShitTokSourcePoolTest.clip("other", 1);
        broad.putCached(4, Collections.singletonList(show));
        broad.putCached(3, Collections.singletonList(other));
        ShitTokFilteredInventory inventory = new ShitTokFilteredInventory(new ShitTokFilteredInventoryTest.Direct(), () -> 0L,
                new ShitTokFilteredInventory.Backend() {
                    public OnlyHavenRepository.CategoryPage creators(ContentPreference mode, int offset) {
                        return new OnlyHavenRepository.CategoryPage(Collections.emptyList(), false);
                    }
                    public List<NativeContentItem> media(int source, ShitTokFilteredInventory.Cursor cursor) {
                        return Collections.emptyList();
                    }
                    public List<NativeContentItem> show() { return broad.takeCached(4, 12); }
                    public boolean available(int source) { return source == 4; }
                });
        ReflectionHelpers.setField(mixer, "filtered", inventory);
        try {
            ContentPreference.set(activity, ContentPreference.WOMEN);
            mixer.requestPreference(activity, () -> {});
            assertTrue(mixer.hasPreference(ContentPreference.WOMEN));
            assertFalse(broad.hasReady(3));
            assertTrue(broad.take(12).isEmpty());
            assertTrue(inventory.matches(show, ContentPreference.WOMEN));
            ContentPreference.set(activity, ContentPreference.MEN);
            mixer.requestPreference(activity, () -> {});
            assertFalse(mixer.hasPreference(ContentPreference.MEN));
            assertTrue(mixer.takePreference(ContentPreference.MEN).isEmpty());
            ContentPreference.set(activity, ContentPreference.ALL);
            mixer.requestPreference(activity, () -> {});
            assertEquals(Collections.singletonList(show), broad.takeCached(4, 12));
            assertEquals(Collections.singletonList(other), broad.takeCached(3, 12));
            assertEquals(12, broad.cacheRoom(4));
            assertTrue(inventory.take(ContentPreference.WOMEN).isEmpty());
        } finally {
            mixer.stopPreference(); ContentPreference.set(activity, ContentPreference.ALL); activity.finish();
        }
    }
}
