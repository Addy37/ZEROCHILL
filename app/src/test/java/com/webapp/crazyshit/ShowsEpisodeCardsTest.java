package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import androidx.recyclerview.widget.RecyclerView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShowsEpisodeCardsTest {
    @Test public void cachedRangeSwitchDoesNotFetchOrRebindWhenUnseenSourceItemsAppend() {
        NativeFeedBrowserActivity browser = Robolectric.buildActivity(NativeFeedBrowserActivity.class).get();
        browser.setTheme(R.style.Theme_CrazyShit);
        NativeFeedAdapter adapter = new NativeFeedAdapter(browser, new NativeFeedAdapter.Listener() {
            public void onOpen(NativeContentItem item) { }
            public void onLongPress(NativeContentItem item, View anchor) { }
            public void onComments(NativeContentItem item) { }
        });
        RecyclerView recycler = new RecyclerView(browser);
        recycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(browser));
        recycler.setAdapter(adapter);
        ReflectionHelpers.setField(browser, "showDetailsMode", true);
        ReflectionHelpers.setField(browser, "source", NativeFeedBrowserActivity.SOURCE_CRAZYSHIT);
        ReflectionHelpers.setField(browser, "adapter", adapter);
        ReflectionHelpers.setField(browser, "recycler", recycler);
        List<NativeContentItem> full = new ArrayList<>();
        for (int i = 0; i < 24; i++) full.add(new NativeContentItem(
                NativeContentItem.KIND_MEDIA, "Episode " + i, "https://example.com/video/" + i,
                "", "", "", ""));
        ReflectionHelpers.callInstanceMethod(browser, "mergeShowEpisodes",
                ReflectionHelpers.ClassParameter.from(List.class, full),
                ReflectionHelpers.ClassParameter.from(boolean.class, false));
        ReflectionHelpers.callInstanceMethod(browser, "changeEpisodeRange",
                ReflectionHelpers.ClassParameter.from(int.class, 10));
        assertEquals(10, adapter.getItemCount());
        assertSame(full.get(10), adapter.snapshot().get(0));
        assertFalse((Boolean) ReflectionHelpers.getField(browser, "loading"));
        final int[] changes = {0};
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override public void onChanged() { changes[0]++; }
        });
        List<NativeContentItem> tail = new ArrayList<>(full);
        for (int i = 24; i < 30; i++) tail.add(new NativeContentItem(
                NativeContentItem.KIND_MEDIA, "Episode " + i, "https://example.com/video/" + i,
                "", "", "", ""));
        ReflectionHelpers.callInstanceMethod(browser, "mergeShowEpisodes",
                ReflectionHelpers.ClassParameter.from(List.class, tail),
                ReflectionHelpers.ClassParameter.from(boolean.class, true));
        assertEquals(0, changes[0]);
        assertEquals(30, ((List<?>) ReflectionHelpers.getField(browser, "showEpisodes")).size());
        assertEquals(10, (int) ReflectionHelpers.getField(browser, "episodeRangeStart"));
        adapter.close();
    }

    @Test public void laterRangeKeepsSourceOrderAndFullCardTapWithFixedMissingArtworkSlot() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_CrazyShit);
        RecyclerView parent = new RecyclerView(activity);
        NativeContentItem[] opened = new NativeContentItem[1];
        NativeFeedAdapter adapter = new NativeFeedAdapter(activity, new NativeFeedAdapter.Listener() {
            public void onOpen(NativeContentItem item) { opened[0] = item; }
            public void onLongPress(NativeContentItem item, View anchor) { }
            public void onComments(NativeContentItem item) { }
        });
        List<NativeContentItem> full = new ArrayList<>();
        for (int i = 0; i < 24; i++) full.add(new NativeContentItem(
                NativeContentItem.KIND_MEDIA, "Source title " + (i + 1), "", "", "", "", ""));
        adapter.setEpisodePresentation(true);
        adapter.setEpisodeOffset(10);
        adapter.replace(ShowsEpisodeRanges.slice(full, 10));
        NativeFeedAdapter.Holder holder = adapter.createViewHolder(parent, adapter.getItemViewType(0));
        adapter.bindViewHolder(holder, 0);
        assertEquals("EPISODE 11", holder.info.getText().toString());
        assertEquals("Source title 11", holder.title.getText().toString());
        assertEquals(3, holder.title.getMaxLines());
        assertEquals(View.GONE, holder.play.getVisibility());
        assertEquals(View.GONE, holder.comments.getVisibility());
        assertEquals(ImageView.ScaleType.CENTER_CROP, holder.image.getScaleType());
        int height = holder.itemView.getLayoutParams().height;
        int imageHeight = ((View) holder.image.getParent()).getLayoutParams().height;
        holder.image.setImageBitmap(Bitmap.createBitmap(40, 90, Bitmap.Config.ARGB_8888));
        assertEquals(height, holder.itemView.getLayoutParams().height);
        assertEquals(imageHeight, ((View) holder.image.getParent()).getLayoutParams().height);
        holder.card.performClick();
        assertSame(full.get(10), opened[0]);
        adapter.close();
        activity.finish();
    }

    @Test public void rangeClampsFinalPageAndUsesSavedHostAccent() {
        assertEquals("21–24", ShowsEpisodeRanges.label(20, 24));
        assertFalse(ShowsEpisodeRanges.hasNext(20, 24));
        assertTrue(ShowsEpisodeRanges.hasPrevious(20, 24));
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.getSharedPreferences("app_prefs", 0).edit().putString("appearance_accent", "GREEN").apply();
        assertEquals(0xFF42D39B, ShowsAppearance.accent(activity));
        activity.getSharedPreferences("app_prefs", 0).edit().putString("appearance_accent", "invalid").apply();
        assertEquals(UiPalette.PRIMARY, ShowsAppearance.accent(activity));
        activity.getSharedPreferences("app_prefs", 0).edit().remove("appearance_accent").apply();
        activity.finish();
    }
}
