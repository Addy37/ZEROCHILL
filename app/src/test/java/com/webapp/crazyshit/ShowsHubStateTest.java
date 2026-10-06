package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.widget.ScrollView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class ShowsHubStateTest {
    @Test
    public void restoresVerticalHubPositionAfterRecreation() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ShowsHubView first = hub(activity);
        activity.setContentView(first);
        first.setCrazyShit(items(12, "/series/"));
        first.setEfukt(items(8, "https://efukt.com/series/test-"));
        first.setCategories(items(10, "/category/"));
        first.setBaddieHubCategories(items(7, "https://baddiehub.com/category/test-"));
        first.finishLoading();

        layout(first);
        ScrollView firstScroll = (ScrollView) first.getChildAt(0);
        firstScroll.scrollTo(0, 260);

        Bundle saved = new Bundle();
        first.saveState(saved);
        assertTrue(saved.getInt("scroll_y", 0) > 0);

        ShowsHubView restored = hub(activity);
        activity.setContentView(restored);
        restored.restoreState(saved);
        restored.setCrazyShit(items(12, "/series/"));
        restored.setEfukt(items(8, "https://efukt.com/series/test-"));
        restored.setCategories(items(10, "/category/"));
        restored.setBaddieHubCategories(items(7, "https://baddiehub.com/category/test-"));
        layout(restored);
        restored.finishLoading();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        ScrollView restoredScroll = (ScrollView) restored.getChildAt(0);
        assertTrue(restoredScroll.getScrollY() > 0);
    }

    @Test
    public void baddieHubShelfCountsAndClearsIndependently() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ShowsHubView hub = hub(activity);
        hub.setCrazyShit(items(2, "/series/"));
        hub.setBaddieHubCategories(items(3, "https://baddiehub.com/category/test-"));

        assertEquals(5, hub.itemCount());

        hub.setBaddieHubCategories(new ArrayList<>());
        assertEquals(2, hub.itemCount());
        hub.clear();
        assertEquals(0, hub.itemCount());
    }

    @Test
    public void restoresBaddieHubHorizontalShelfPosition() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ShowsHubView first = hub(activity);
        activity.setContentView(first);
        first.setBaddieHubCategories(items(12, "https://baddiehub.com/category/test-"));
        layout(first);
        RecyclerView firstRail = baddieHubRail(first);
        LinearLayoutManager firstLayout = (LinearLayoutManager) firstRail.getLayoutManager();
        firstLayout.scrollToPositionWithOffset(4, -18);
        layout(firstRail);

        Bundle saved = new Bundle();
        first.saveState(saved);
        assertTrue(saved.containsKey("baddiehub_categories_position"));
        assertEquals(4, saved.getInt("baddiehub_categories_position"));

        ShowsHubView restored = hub(activity);
        activity.setContentView(restored);
        restored.restoreState(saved);
        restored.setBaddieHubCategories(items(12, "https://baddiehub.com/category/test-"));
        layout(restored);
        restored.finishLoading();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        RecyclerView restoredRail = baddieHubRail(restored);
        layout(restoredRail);
        LinearLayoutManager restoredLayout = (LinearLayoutManager) restoredRail.getLayoutManager();
        assertEquals(saved.getInt("baddiehub_categories_position"),
                restoredLayout.findFirstVisibleItemPosition());
    }

    private static ShowsHubView hub(Activity activity) {
        return new ShowsHubView(activity, item -> { }, item -> { }, item -> { });
    }

    private static List<NativeContentItem> items(int count, String prefix) {
        ArrayList<NativeContentItem> items = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String url = prefix.startsWith("http")
                    ? prefix + index + "/"
                    : CrazyShitRepository.BASE + prefix.substring(1) + index + "/";
            items.add(new NativeContentItem(
                    NativeContentItem.KIND_SERIES,
                    "Show " + index,
                    url,
                    "",
                    "",
                    "",
                    ""
            ));
        }
        return items;
    }

    private static RecyclerView baddieHubRail(ShowsHubView hub) {
        Object shelf = ReflectionHelpers.getField(hub, "baddieHubCategoryShelf");
        return ReflectionHelpers.getField(shelf, "rail");
    }

    private static void layout(View view) {
        int width = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY);
        int height = View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY);
        view.measure(width, height);
        view.layout(0, 0, 1080, 600);
    }
}
