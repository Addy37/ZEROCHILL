package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.app.Activity;

import androidx.recyclerview.widget.RecyclerView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;

@RunWith(RobolectricTestRunner.class)
public final class ShitTokCreatorBlockQueueIntegrationTest {
    private Activity activity;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.getSharedPreferences("shittok_blocked_creators", Activity.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void creatorBlockUsesTargetedRemovalsAndPreservesEarlierQueueItem() {
        ChaosFeedView feed = new ChaosFeedView(activity, item -> { });
        ArrayList<NativeContentItem> items = ReflectionHelpers.getField(feed, "items");
        NativeContentItem earlier = media("Earlier", "Other", "https://example.com/earlier");
        NativeContentItem selected = media(
                "Selected", "Sophie Rain", "https://fapello.com/sophie-rain/selected"
        );
        NativeContentItem duplicate = media(
                "Duplicate", "Sophie Rain", "https://fapello.com/sophie-rain/duplicate"
        );
        NativeContentItem next = media("Next", "Other", "https://example.com/next");
        items.add(earlier);
        items.add(selected);
        items.add(duplicate);
        items.add(next);
        ReflectionHelpers.setField(feed, "selectedPosition", 1);

        RecyclerView.Adapter<?> adapter = ReflectionHelpers.getField(feed, "adapter");
        ChangeObserver observer = new ChangeObserver();
        adapter.registerAdapterDataObserver(observer);

        ReflectionHelpers.callInstanceMethod(
                feed,
                "blockCreatorFromChaos",
                ReflectionHelpers.ClassParameter.from(NativeContentItem.class, selected)
        );

        assertEquals(0, observer.fullChanges);
        assertEquals(2, observer.removals);
        assertEquals(2, items.size());
        assertSame(earlier, items.get(0));
        assertSame(next, items.get(1));
        assertEquals(0, (int) ReflectionHelpers.getField(feed, "selectedPosition"));
        feed.close();
    }

    private static NativeContentItem media(String title, String uploader, String url) {
        return new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                title,
                url,
                "",
                "",
                uploader,
                "",
                "",
                ""
        );
    }

    private static final class ChangeObserver extends RecyclerView.AdapterDataObserver {
        int fullChanges;
        int removals;

        @Override
        public void onChanged() {
            fullChanges++;
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            removals += itemCount;
        }
    }
}
