package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class PlayerSaveMenuTest {
    @Test public void menuRequiresExplicitChoiceAndUsesSeparateActions() {
        AtomicInteger watch = new AtomicInteger(), collection = new AtomicInteger();
        VideoActionSheet.Action[] choices = PlayerSaveMenu.choices(false, watch::incrementAndGet, collection::incrementAndGet);
        assertEquals(2, choices.length);
        assertEquals("Watch Later", choices[0].title);
        assertEquals("Add to collection", choices[1].title);
        assertEquals(0, watch.get());
        assertEquals(0, collection.get());
        choices[1].run.run();
        assertEquals(1, collection.get());
        assertEquals(0, watch.get());
        choices[0].run.run();
        assertEquals(1, watch.get());
        assertEquals("Remove from Watch Later", PlayerSaveMenu.choices(true, () -> {}, () -> {})[0].title);
    }

    @Test public void dedicatedPanelHasTwoRowsAndNoScrollAtNarrowWidth() {
        android.app.Activity a = org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup().get();
        a.setTheme(R.style.Theme_CrazyShit);
        AtomicInteger watch = new AtomicInteger(), collections = new AtomicInteger();
        android.widget.LinearLayout panel = PlayerSaveSheet.buildPanel(a, false, true,
                PlayerSaveMenu.choices(true, watch::incrementAndGet, collections::incrementAndGet), () -> {});
        android.view.View first = panel.findViewWithTag("save-watch-later");
        android.view.View second = panel.findViewWithTag("save-collection");
        assertNotNull(first);
        assertNotNull(second);
        assertTrue(first.getContentDescription().toString().contains("Remove from Watch Later"));
        assertEquals(5, panel.getChildCount());
        for (int i = 0; i < panel.getChildCount(); i++)
            assertFalse(panel.getChildAt(i) instanceof android.widget.ScrollView);
        int width = BrowseUi.dp(a, 320);
        panel.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
        assertTrue(first.getMeasuredHeight() >= BrowseUi.dp(a, 76));
        assertTrue(second.getMeasuredHeight() >= BrowseUi.dp(a, 76));
        assertTrue(panel.getMeasuredHeight() >= first.getMeasuredHeight() + second.getMeasuredHeight());
        second.performClick();
        assertEquals(1, collections.get());
        assertEquals(0, watch.get());
    }

    @Test public void freshVideoCanJoinCollectionWithoutWatchLaterAndRetainsArtwork() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        NativeContentItem item = OnlyFapVideoMetadata.basic("Example", "https://example.com/video/one", "https://example.com/cover.jpg");
        String group = MediaCollectionStore.create(context, "Videos");
        assertFalse(FavoriteStore.contains(context, item.url));
        MediaCollectionStore.assign(context, item, "", Collections.singleton(group));
        assertFalse(FavoriteStore.contains(context, item.url));
        assertEquals(1, MediaCollectionStore.items(context, group).size());
        assertEquals(item.imageUrl, MediaCollectionStore.items(context, group).get(0).content.imageUrl);
        FavoriteStore.add(context, item);
        assertTrue(FavoriteStore.contains(context, item.url));
        FavoriteStore.remove(context, item.url);
        assertFalse(FavoriteStore.contains(context, item.url));
        assertEquals(item.imageUrl, MediaCollectionStore.items(context, group).get(0).content.imageUrl);
    }
}
