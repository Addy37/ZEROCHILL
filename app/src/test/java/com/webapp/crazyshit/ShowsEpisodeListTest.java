package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w360dp-h800dp-port-mdpi")
public class ShowsEpisodeListTest {
    private Context context;
    @Before public void clear() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("player_positions", 0).edit().clear().commit();
    }
    private NativeContentItem episode(String id) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, "Episode " + id,
                "https://example.test/" + id, "", "123", "", "", "Long episode description");
    }
    private PlaybackHistoryStore.Item watched(String id, long position, long last, boolean complete) {
        return new PlaybackHistoryStore.Item("Episode", episode(id).url, "", position, 100_000, last, complete, true);
    }
    @Test public void firstPlaybackSkipsNonMediaAndEmptyUrls() {
        NativeContentItem first = episode("first");
        ShowsEpisodePlayback.Selection choice = ShowsEpisodePlayback.select(Arrays.asList(
                new NativeContentItem(NativeContentItem.KIND_SECTION, "Season", "", "", "", "", ""),
                new NativeContentItem(NativeContentItem.KIND_MEDIA, "Invalid", "", "", "", "", ""), first), Collections.emptyList());
        assertSame(first, choice.item);
        assertEquals(0L, choice.positionMs);
        assertNull(ShowsEpisodePlayback.select(Collections.emptyList(), Collections.emptyList()).item);
    }
    @Test public void resumeSelectsLatestUnfinishedSeriesMemberUsingHistoryThreshold() {
        NativeContentItem first = episode("first"), second = episode("second");
        ShowsEpisodePlayback.Selection choice = ShowsEpisodePlayback.select(Arrays.asList(first, second), Arrays.asList(
                watched("other", 20_000, 100, false), watched("first", 40_000, 10, false),
                watched("second", 5_000, 20, false)));
        assertSame(second, choice.item);
        assertEquals(5_000, choice.positionMs);
        choice = ShowsEpisodePlayback.select(Arrays.asList(first, second), Arrays.asList(
                watched("first", 95_000, 100, false), watched("second", 4_999, 200, false),
                watched("second", 30_000, 300, true)));
        assertSame(first, choice.item);
        assertEquals(0, choice.positionMs);
    }
    @Test public void playNextPersistsSingleSelectionAndCannotClearNewerSelection() throws Exception {
        VideoDetailActivity.setShowsPlayNext(context, episode("one"), "efukt", "series");
        String old = VideoDetailActivity.pendingShowsNext(context, "current").toString();
        VideoDetailActivity.setShowsPlayNext(context, episode("two"), "kaotic", "series2");
        VideoDetailActivity.clearShowsNext(context, old);
        org.json.JSONObject next = VideoDetailActivity.pendingShowsNext(context, "current");
        assertEquals(episode("two").url, ContentItemCodec.decode(next).url);
        assertEquals("kaotic", next.getString("source"));
        VideoDetailActivity.setShowsPlayNext(context, episode("two"), "kaotic", "series2");
        assertNull(VideoDetailActivity.pendingShowsNext(context, episode("two").url));
        assertNull(VideoDetailActivity.pendingShowsNext(context, "current"));
    }
    @Test public void invalidNextDoesNotReplacePendingEpisodeAndFailureLeavesItPending() {
        VideoDetailActivity.setShowsPlayNext(context, episode("one"), "efukt", "series");
        VideoDetailActivity.setShowsPlayNext(context, null, "", "");
        assertNotNull(VideoDetailActivity.pendingShowsNext(context, "current"));
        assertNotNull(VideoDetailActivity.pendingShowsNext(context, "current"));
    }
    @Test public void oldStylesCannotChangeEpisodeRowsAndDescriptionsStayHidden() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_CrazyShit);
        NativeFeedAdapter adapter = new NativeFeedAdapter(activity, new NativeFeedAdapter.Listener() {
            public void onOpen(NativeContentItem item) { }
            public void onLongPress(NativeContentItem item, View anchor) { }
            public void onComments(NativeContentItem item) { }
        });
        adapter.setEpisodePresentation(true);
        adapter.replace(Collections.singletonList(episode("one")));
        FrameLayout parent = new FrameLayout(activity);
        for (int mode = 0; mode <= 3; mode++) {
            context.getSharedPreferences("app_prefs", 0).edit().putInt("native_view_collection", mode).commit();
            adapter.setViewMode(mode);
            assertEquals(NativeFeedAdapter.VIEW_LIST, adapter.getItemViewType(0));
        }
        NativeFeedAdapter.Holder holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0));
        adapter.onBindViewHolder(holder, 0);
        View description = ReflectionHelpers.getField(holder, "description");
        assertEquals(View.GONE, description.getVisibility());
        assertNotNull(holder.itemView.findViewWithTag("video_options"));
        assertTrue(holder.itemView.getLayoutParams().height <= 110);
        adapter.close(); activity.finish();
    }
    @Test public void progressUsesExistingHistoryAndRecyclingClearsIt() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_CrazyShit);
        NativeFeedAdapter adapter = new NativeFeedAdapter(activity, new NativeFeedAdapter.Listener() {
            public void onOpen(NativeContentItem item) { }
            public void onLongPress(NativeContentItem item, View anchor) { }
            public void onComments(NativeContentItem item) { }
        });
        adapter.setEpisodePresentation(true);
        adapter.replace(Arrays.asList(episode("one"), episode("two")));
        java.util.Map<String, PlaybackHistoryStore.Item> history = ReflectionHelpers.getField(adapter, "playbackByUrl");
        history.put(episode("one").url, watched("one", 25_000, 10, false));
        NativeFeedAdapter.Holder holder = adapter.onCreateViewHolder(new FrameLayout(activity), NativeFeedAdapter.VIEW_LIST);
        adapter.onBindViewHolder(holder, 0);
        View track = ReflectionHelpers.getField(holder, "progressTrack");
        View fill = ReflectionHelpers.getField(holder, "progressFill");
        TextView badge = ReflectionHelpers.getField(holder, "watchBadge");
        assertEquals(View.VISIBLE, track.getVisibility());
        assertEquals(0.25f, fill.getScaleX(), 0.001f);
        history.put(episode("one").url, watched("one", 100_000, 10, true));
        adapter.onBindViewHolder(holder, 0);
        assertTrue(badge.getText().toString().contains("Watched"));
        assertEquals(View.GONE, track.getVisibility());
        adapter.onBindViewHolder(holder, 1);
        assertEquals(View.GONE, badge.getVisibility());
        assertEquals(0f, fill.getScaleX(), 0.001f);
        adapter.close(); activity.finish();
    }

    private String dialogText(View view) {
        StringBuilder text = new StringBuilder();
        if (view instanceof TextView) text.append(((TextView) view).getText()).append('\n');
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            text.append(dialogText(((ViewGroup) view).getChildAt(i)));
        return text.toString();
    }

    @Test public void episodeSheetKeepsInternalActionsAndWatchLaterState() {
        NativeFeedBrowserActivity browser = Robolectric.buildActivity(NativeFeedBrowserActivity.class).get();
        browser.setTheme(R.style.Theme_CrazyShit);
        ReflectionHelpers.setField(browser, "showDetailsMode", true);
        NativeContentItem item = episode("menu");
        FavoriteStore.remove(browser, item.url);
        ReflectionHelpers.callInstanceMethod(browser, "showVideoItemMenu",
                ReflectionHelpers.ClassParameter.from(NativeContentItem.class, item));
        android.app.Dialog sheet = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        String text = dialogText(sheet.getWindow().getDecorView());
        assertTrue(text.contains(item.title));
        for (String action : new String[] {"Download", "Watch Later", "Comments", "Share", "Play Next"})
            assertTrue(action + " missing: " + text, text.contains(action));
        assertFalse(text.contains("Open source website"));
        assertFalse(text.contains("Video details"));
        assertFalse(text.contains("Video actions"));
        sheet.dismiss();
        FavoriteStore.add(browser, item);
        ReflectionHelpers.callInstanceMethod(browser, "showVideoItemMenu",
                ReflectionHelpers.ClassParameter.from(NativeContentItem.class, item));
        sheet = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertTrue(dialogText(sheet.getWindow().getDecorView()).contains("Remove from Watch Later"));
        sheet.dismiss(); FavoriteStore.remove(browser, item.url);
    }

    @Test public void seriesSettingsIgnoreLegacyStylesAndOptionsCannotOpen() {
        NativeFeedBrowserActivity browser = Robolectric.buildActivity(NativeFeedBrowserActivity.class).get();
        browser.setTheme(R.style.Theme_CrazyShit);
        ReflectionHelpers.setField(browser, "showDetailsMode", true);
        for (int mode = 0; mode <= 3; mode++) {
            browser.getSharedPreferences("app_prefs", 0).edit().putInt("native_view_collection", mode).commit();
            assertEquals((Integer) NativeFeedAdapter.VIEW_LIST, ReflectionHelpers.callInstanceMethod(browser, "viewMode"));
        }
        android.app.Dialog before = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        ReflectionHelpers.callInstanceMethod(browser, "showViewStyleDialog");
        ReflectionHelpers.callInstanceMethod(browser, "showOptions", ReflectionHelpers.ClassParameter.from(View.class, new View(browser)));
        assertSame(before, org.robolectric.shadows.ShadowDialog.getLatestDialog());
    }

    @Test public void episodeHeaderReflectsPlaybackAvailabilityAndResume() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_CrazyShit);
        ShowDetailsHeader header = new ShowDetailsHeader(activity, "Series", "Source", "Description", "", "", () -> {});
        TextView action = ReflectionHelpers.getField(header, "action");
        assertFalse(action.isEnabled());
        header.setPlaybackAction(false, true);
        assertEquals("PLAY FIRST", action.getText().toString());
        header.setPlaybackAction(true, true);
        assertEquals("RESUME", action.getText().toString());
        assertTrue(action.isEnabled());
        activity.finish();
    }
}
