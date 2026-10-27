package com.webapp.crazyshit;

import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w360dp-h800dp-port-mdpi")
public class ShowsDetailPagingTest {
    private NativeFeedBrowserActivity browser() {
        NativeFeedBrowserActivity browser = Robolectric.buildActivity(NativeFeedBrowserActivity.class).get();
        browser.setTheme(R.style.Theme_CrazyShit);
        ReflectionHelpers.setField(browser, "showDetailsMode", true);
        ReflectionHelpers.setField(browser, "showsBrowserResumed", true);
        ReflectionHelpers.setField(browser, "currentPage", 1);
        ArrayList<NativeContentItem> items = ReflectionHelpers.getField(browser, "showEpisodes");
        for (int i = 0; i < 40; i++) items.add(new NativeContentItem(
                NativeContentItem.KIND_MEDIA, "Source " + i, "https://example.test/" + i, "", "", "", ""));
        return browser;
    }

    private void prefetch(NativeFeedBrowserActivity browser) {
        ReflectionHelpers.callInstanceMethod(browser, "maybePrefetchShowEpisodes");
    }

    @Test public void prefetchStartsOnEarlierRangeWithOnlyOneRequestInFlight() {
        NativeFeedBrowserActivity browser = browser();
        RecordingExecutor requests = new RecordingExecutor();
        ReflectionHelpers.setField(browser, "io", requests);
        prefetch(browser);
        assertEquals(0, requests.count);
        ReflectionHelpers.setField(browser, "episodeRangeStart", 10);
        prefetch(browser);
        assertEquals(1, requests.count);
        assertTrue((boolean) ReflectionHelpers.getField(browser, "loading"));
        prefetch(browser);
        assertEquals(1, requests.count);
        assertEquals(10, (int) ReflectionHelpers.getField(browser, "episodeRangeStart"));
    }

    @Test public void pauseRestoreExhaustionAndFailedPagePreventAutomaticRequests() {
        NativeFeedBrowserActivity browser = browser();
        RecordingExecutor requests = new RecordingExecutor();
        ReflectionHelpers.setField(browser, "io", requests);
        ReflectionHelpers.setField(browser, "episodeRangeStart", 30);
        for (String gate : new String[] {"endReached", "restoringBrowser", "loading"}) {
            ReflectionHelpers.setField(browser, gate, true);
            prefetch(browser);
            ReflectionHelpers.setField(browser, gate, false);
        }
        ReflectionHelpers.setField(browser, "showsBrowserResumed", false);
        prefetch(browser);
        ReflectionHelpers.setField(browser, "showsBrowserResumed", true);
        ReflectionHelpers.setField(browser, "episodePrefetchFailedPage", 2);
        prefetch(browser);
        assertEquals(0, requests.count);
        ReflectionHelpers.setField(browser, "episodePrefetchFailedPage", -1);
        prefetch(browser);
        assertEquals(1, requests.count);
    }

    @Test public void addingUnseenItemsDoesNotRebindCurrentRowsOrChangeSelectedRange() {
        NativeFeedBrowserActivity browser = browser();
        NativeFeedAdapter adapter = new NativeFeedAdapter(browser, new NativeFeedAdapter.Listener() {
            public void onOpen(NativeContentItem item) { }
            public void onLongPress(NativeContentItem item, View anchor) { }
            public void onComments(NativeContentItem item) { }
        });
        adapter.setEpisodePresentation(true);
        ReflectionHelpers.setField(browser, "adapter", adapter);
        ReflectionHelpers.setField(browser, "episodeRangeStart", 10);
        apply(browser);
        List<NativeContentItem> visible = adapter.snapshot();
        int[] changes = {0};
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override public void onChanged() { changes[0]++; }
        });
        ArrayList<NativeContentItem> all = ReflectionHelpers.getField(browser, "showEpisodes");
        all.add(new NativeContentItem(NativeContentItem.KIND_MEDIA, "Later source", "https://example.test/40", "", "", "", ""));
        apply(browser);
        assertEquals(0, changes[0]);
        assertEquals(visible, adapter.snapshot());
        assertEquals(10, adapter.getItemCount());
        assertEquals(41, all.size());
        assertEquals(10, (int) ReflectionHelpers.getField(browser, "episodeRangeStart"));
        adapter.close();
    }

    private void apply(NativeFeedBrowserActivity browser) {
        ReflectionHelpers.callInstanceMethod(browser, "applyEpisodeRange",
                ReflectionHelpers.ClassParameter.from(boolean.class, false),
                ReflectionHelpers.ClassParameter.from(int.class, -1));
    }

    @Test public void pagingControlsStayFlatAndReadable() {
        NativeFeedBrowserActivity browser = browser();
        TextView next = ReflectionHelpers.callInstanceMethod(browser, "episodePageButton",
                ReflectionHelpers.ClassParameter.from(String.class, "Next 10 →"),
                ReflectionHelpers.ClassParameter.from(int.class, 1));
        assertTrue(next.getBackground() instanceof RippleDrawable);
        android.graphics.drawable.Drawable content =
                ((RippleDrawable) next.getBackground()).getDrawable(0);
        assertTrue(content instanceof ColorDrawable);
        assertEquals(Color.TRANSPARENT, ((ColorDrawable) content).getColor());
        assertEquals(UiPalette.PRIMARY, next.getCurrentTextColor());
        assertEquals(0f, next.getElevation(), 0f);
        assertTrue(next.getMinHeight() >= 48);
    }

    /** Records source work without performing any network requests. */
    private static final class RecordingExecutor extends AbstractExecutorService {
        int count;
        boolean closed;
        @Override public void execute(Runnable task) { count++; }
        @Override public void shutdown() { closed = true; }
        @Override public List<Runnable> shutdownNow() { closed = true; return Collections.emptyList(); }
        @Override public boolean isShutdown() { return closed; }
        @Override public boolean isTerminated() { return closed; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return closed; }
    }
}
