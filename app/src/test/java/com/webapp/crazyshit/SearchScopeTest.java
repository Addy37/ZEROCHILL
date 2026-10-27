package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, shadows = {
        SearchScopeTest.QuietCrazy.class,
        SearchScopeTest.QuietEfukt.class,
        SearchScopeTest.QuietKaotic.class,
        SearchScopeTest.QuietXMega.class,
        SearchScopeTest.QuietBaddie.class,
        SearchScopeTest.QuietBunkr.class
})
public class SearchScopeTest {
    @Implements(value = CrazyShitRepository.class, isInAndroidSdk = false)
    public static class QuietCrazy {
        static final AtomicInteger calls = new AtomicInteger();
        @Implementation
        protected List<NativeContentItem> fetchFeed(Context context, String url, int page) {
            calls.incrementAndGet();
            return Collections.emptyList();
        }
    }

    @Implements(value = EfuktRepository.class, isInAndroidSdk = false)
    public static class QuietEfukt {
        static final AtomicInteger calls = new AtomicInteger();
        @Implementation
        protected List<NativeContentItem> search(Context context, String query) {
            calls.incrementAndGet();
            return Collections.emptyList();
        }
    }

    @Implements(value = WebVideoSourceRepository.class, isInAndroidSdk = false)
    public static class QuietKaotic {
        static final AtomicInteger calls = new AtomicInteger();
        @Implementation
        protected List<NativeContentItem> search(Context context, String query) {
            calls.incrementAndGet();
            return Collections.emptyList();
        }
    }

    @Implements(value = XMegaDriveRepository.class, isInAndroidSdk = false)
    public static class QuietXMega {
        static final AtomicInteger calls = new AtomicInteger();
        static volatile CountDownLatch started = new CountDownLatch(1);
        static volatile CountDownLatch release = new CountDownLatch(0);
        static volatile List<NativeContentItem> results = Collections.emptyList();

        private static List<NativeContentItem> run(XMegaDriveRepository.SearchListener listener) {
            calls.incrementAndGet();
            started.countDown();
            try { if (!release.await(3, TimeUnit.SECONDS)) return Collections.emptyList(); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return Collections.emptyList();
            }
            List<NativeContentItem> next = results;
            if (listener != null && next != null && !next.isEmpty()) listener.onResults(next);
            return next;
        }

        @Implementation
        protected List<NativeContentItem> search(Context context, String query) {
            return run(null);
        }

        @Implementation
        protected List<NativeContentItem> search(
                Context context,
                String query,
                XMegaDriveRepository.SearchListener listener
        ) {
            return run(listener);
        }
    }

    @Implements(value = BaddieHubRepository.class, isInAndroidSdk = false)
    public static class QuietBaddie {
        static final AtomicInteger calls = new AtomicInteger();
        static volatile String lastQuery = "";

        private static List<NativeContentItem> run(
                String query,
                BaddieHubRepository.SearchListener listener
        ) {
            calls.incrementAndGet();
            lastQuery = query;
            List<NativeContentItem> next = Collections.emptyList();
            if (listener != null && !next.isEmpty()) listener.onResults(next);
            return next;
        }

        @Implementation
        protected List<NativeContentItem> search(Context context, String query) {
            return run(query, null);
        }

        @Implementation
        protected List<NativeContentItem> search(
                Context context,
                String query,
                BaddieHubRepository.SearchListener listener
        ) {
            return run(query, listener);
        }
    }

    @Implements(value = BunkrRepository.class, isInAndroidSdk = false)
    public static class QuietBunkr {
        static final AtomicInteger calls = new AtomicInteger();
        static volatile boolean fail;
        static volatile CountDownLatch started = new CountDownLatch(1);
        static volatile CountDownLatch release = new CountDownLatch(0);
        @Implementation protected List<NativeContentItem> searchAlbums(Context context, String query, int page)
                throws java.io.IOException {
            calls.incrementAndGet(); started.countDown();
            // Simulate an upstream socket that does not stop immediately on cancellation.
            while (release.getCount() > 0) {
                try { release.await(50, TimeUnit.MILLISECONDS); }
                catch (InterruptedException ignored) { }
            }
            if (fail) throw new java.io.IOException("unavailable");
            return Collections.singletonList(new NativeContentItem(NativeContentItem.KIND_SERIES,
                    "Matching collection", "https://bunkr.cr/a/sample", "", "", "Bunkr", ""));
        }
    }

    @Before public void reset() {
        QuietBunkr.calls.set(0);
        QuietBunkr.fail = false;
        QuietBunkr.started = new CountDownLatch(1);
        QuietBunkr.release = new CountDownLatch(0);
        QuietCrazy.calls.set(0);
        QuietEfukt.calls.set(0);
        QuietKaotic.calls.set(0);
        QuietXMega.calls.set(0);
        QuietXMega.started = new CountDownLatch(1);
        QuietXMega.release = new CountDownLatch(0);
        QuietXMega.results = Collections.emptyList();
        QuietBaddie.calls.set(0);
        QuietBaddie.lastQuery = "";
    }

    @Test public void tabsAreExactlyTheSixContentAreas() {
        SearchPanel panel = open();
        try {
            List<String> labels = labels(panel);
            assertEquals(Arrays.asList("Shows", "OnlyFap", "Bunkr", "XMegaDrive", "BaddieHub", "Library"), labels);
            assertEquals(SearchPanel.SCOPE_LABELS, labels);
            assertFalse(labels.contains("All"));
            assertFalse(labels.contains("ShitTok"));
            assertFalse(labels.contains("CrazyShit"));
            assertFalse(labels.contains("EFukt"));
            assertFalse(labels.contains("Kaotic"));
            assertEquals(SearchPanel.Scope.SHOWS, ReflectionHelpers.getField(panel, "scope"));
        } finally { close(panel); }
    }

    @Test public void openingAndEmptyScopesDoNotStartXMegaDrive() {
        SearchPanel panel = open();
        try {
            assertEquals(0, QuietXMega.calls.get());
            for (String label : Arrays.asList("Shows", "OnlyFap", "BaddieHub", "Library", "XMegaDrive")) {
                chip(panel, label).performClick();
                shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));
            }
            assertEquals(0, QuietXMega.calls.get());
            assertEquals(0, QuietCrazy.calls.get());
            assertEquals(0, QuietBaddie.calls.get());
            assertEquals(0, QuietKaotic.calls.get());
            assertFalse(SearchPanel.runsXMegaDrive(SearchPanel.Scope.SHOWS, "car"));
            assertFalse(SearchPanel.runsXMegaDrive(SearchPanel.Scope.XMEGADRIVE, ""));
            assertFalse(SearchPanel.runsXMegaDrive(SearchPanel.Scope.XMEGADRIVE, " "));
            assertTrue(SearchPanel.runsXMegaDrive(SearchPanel.Scope.XMEGADRIVE, "car"));
        } finally { close(panel); }
    }

    @Test public void showsSearchesOnlyCrazyShitEfuktAndKaoticAndKeepsSourceIdentity() throws Exception {
        SearchPanel panel = open();
        try {
            ReflectionHelpers.callInstanceMethod(panel, "startShowsSearch",
                    ReflectionHelpers.ClassParameter.from(String.class, "car crash"));
            await(() -> QuietCrazy.calls.get() > 0 && QuietEfukt.calls.get() > 0 && QuietKaotic.calls.get() > 0);
            await(() -> ((int) ReflectionHelpers.getField(panel, "pendingSources")) == 0);
            assertEquals(0, QuietXMega.calls.get());
            assertEquals(0, QuietBaddie.calls.get());
            ReflectionHelpers.setField(panel, "crazyVideos", Collections.singletonList(
                    video("Crazy clip", "https://crazyshit.com/v/1", "CrazyShit")));
            ReflectionHelpers.setField(panel, "efuktVideos", Collections.singletonList(
                    video("Efukt clip", "https://efukt.com/v/1", "EFukt")));
            ReflectionHelpers.setField(panel, "kaoticVideos", Collections.singletonList(
                    video("Kaotic clip", "https://kaotic.com/video/1", "Kaotic")));
            ReflectionHelpers.callInstanceMethod(panel, "renderResults");
            GlobalSearchAdapter adapter = ReflectionHelpers.getField(panel, "adapter");
            assertEquals(6, adapter.getItemCount());
            String shown = boundText(panel, adapter);
            assertTrue(shown.contains("CrazyShit"));
            assertTrue(shown.contains("EFukt"));
            assertTrue(shown.contains("Kaotic"));
            assertFalse(shown.contains("XMegaDrive"));
            assertFalse(shown.contains("BaddieHub"));
            assertFalse(shown.contains("OnlyFap"));
            assertFalse(shown.contains("Fapello"));
        } finally { close(panel); }
    }

    @Test public void onlyFapStaysOnTheCreatorSearchPath() {
        Intent intent = new Intent().putExtra("scope", "bunkr");
        SearchPanel panel = open(intent);
        try {
            RecyclerView recycler = ReflectionHelpers.getField(panel, "recycler");
            assertTrue(recycler.getAdapter() instanceof OnlyFapCreatorSearchAdapter);
            assertEquals(SearchPanel.Scope.ONLYFAP, ReflectionHelpers.getField(panel, "scope"));
        } finally { close(panel); }
    }

    @Test public void bunkrAlbumSearchUsesNativeGalleryAndRetainsRecentQuery() throws Exception {
        SearchPanel panel = open();
        try {
            chip(panel, "Bunkr").performClick();
            panel.field().setText("sample");
            ReflectionHelpers.callInstanceMethod(panel, "runSearch");
            await(() -> QuietBunkr.calls.get() > 0);
            await(() -> ((int) ReflectionHelpers.getField(panel, "pendingSources")) == 0);
            assertEquals(SearchPanel.Scope.BUNKR_ALBUMS, ReflectionHelpers.getField(panel, "scope"));
            assertEquals("sample", panel.field().getText().toString());
            GlobalSearchAdapter adapter = ReflectionHelpers.getField(panel, "adapter");
            assertTrue(boundText(panel, adapter).contains("Matching collection"));
            List<NativeContentItem> results = ReflectionHelpers.getField(panel, "bunkrResults");
            ReflectionHelpers.callInstanceMethod(panel, "openResult",
                    ReflectionHelpers.ClassParameter.from(NativeContentItem.class, results.get(0)));
            Activity host = ((Session) panel.view.getTag()).host.get();
            Intent opened = shadowOf(host).getNextStartedActivity();
            assertEquals(NativeFeedBrowserActivity.class.getName(), opened.getComponent().getClassName());
            assertEquals("https://bunkr.cr/a/sample", opened.getStringExtra(NativeFeedBrowserActivity.EXTRA_BASE_URL));
            assertEquals(0, QuietXMega.calls.get());
            assertEquals(0, QuietCrazy.calls.get());
        } finally { close(panel); }
    }

    @Test public void slowOrFailedBunkrCannotBlockOrOverwriteShows() throws Exception {
        SearchPanel panel = open();
        try {
            QuietBunkr.release = new CountDownLatch(1);
            QuietBunkr.fail = true;
            chip(panel, "Bunkr").performClick();
            panel.field().setText("sample");
            ReflectionHelpers.callInstanceMethod(panel, "runSearch");
            assertTrue(QuietBunkr.started.await(3, TimeUnit.SECONDS));
            chip(panel, "Shows").performClick();
            await(() -> QuietCrazy.calls.get() > 0 && QuietEfukt.calls.get() > 0 && QuietKaotic.calls.get() > 0);
            await(() -> ((int) ReflectionHelpers.getField(panel, "pendingSources")) == 0);
            QuietBunkr.release.countDown();
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));
            TextView state = ReflectionHelpers.getField(panel, "searchState");
            assertEquals("Shows search complete", state.getText().toString());
            assertEquals(SearchPanel.Scope.SHOWS, ReflectionHelpers.getField(panel, "scope"));
        } finally { QuietBunkr.release.countDown(); close(panel); }
    }

    @Test public void librarySearchStaysLocalAndRecentQueriesUseTheSelectedScope() throws Exception {
        ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup();
        RecentSearchStore.remember(host.get(), "sample");
        FavoriteStore.add(host.get(), "Sample download night", "https://example.com/library-sample");
        SearchGlassOwner owner = new SearchGlassOwner(host.get());
        SearchPanel panel = new SearchPanel(host.get(), new Intent(), true, () -> {});
        panel.create(null);
        host.get().setContentView(panel.view);
        try {
            chip(panel, "Library").performClick();
            View recent = find(panel.view, "Search again for sample");
            assertNotNull(recent);
            recent.performClick();
            assertEquals("sample", panel.field().getText().toString());
            assertEquals(SearchPanel.Scope.LIBRARY, ReflectionHelpers.getField(panel, "scope"));
            await(() -> {
                GlobalSearchAdapter adapter = ReflectionHelpers.getField(panel, "adapter");
                return adapter.getItemCount() > 0;
            });
            assertEquals(0, QuietXMega.calls.get());
            assertEquals(0, QuietBaddie.calls.get());
            assertEquals(0, QuietCrazy.calls.get());
            assertEquals(0, QuietKaotic.calls.get());
            assertEquals(0, QuietEfukt.calls.get());
            assertTrue(boundText(panel, ReflectionHelpers.getField(panel, "adapter")).contains("Sample download night"));
        } finally { close(panel); owner.close(); host.pause().stop().destroy(); }
    }

    @Test public void switchingScopesKeepsTheQueryAndDropsStaleXMegaDriveResults() throws Exception {
        SearchPanel panel = open();
        try {
            chip(panel, "XMegaDrive").performClick();
            QuietXMega.results = Collections.singletonList(
                    video("Stale mega clip", "https://www.xmegadrive.com/videos/stale/", "XMegaDrive"));
            QuietXMega.release = new CountDownLatch(1);
            panel.field().setText("car crash");
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(900));
            assertTrue(QuietXMega.started.await(3, TimeUnit.SECONDS));
            assertEquals("car crash", panel.field().getText().toString());
            chip(panel, "Shows").performClick();
            assertEquals("car crash", panel.field().getText().toString());
            assertEquals(SearchPanel.Scope.SHOWS, ReflectionHelpers.getField(panel, "scope"));
            QuietXMega.release.countDown();
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200));
            GlobalSearchAdapter adapter = ReflectionHelpers.getField(panel, "adapter");
            assertFalse(boundText(panel, adapter).contains("Stale mega clip"));
        } finally { close(panel); }
    }

    @Test public void replacingTheQueryAndClosingSearchRejectStaleResults() throws Exception {
        SearchPanel panel = open();
        try {
            chip(panel, "XMegaDrive").performClick();
            QuietXMega.results = Collections.singletonList(
                    video("Old query clip", "https://www.xmegadrive.com/videos/old/", "XMegaDrive"));
            QuietXMega.release = new CountDownLatch(1);
            panel.field().setText("first query");
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(900));
            assertTrue(QuietXMega.started.await(3, TimeUnit.SECONDS));
            panel.field().setText("second query");
            QuietXMega.release.countDown();
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));
            assertFalse(boundText(panel, ReflectionHelpers.getField(panel, "adapter")).contains("Old query clip"));
            assertEquals("second query", panel.field().getText().toString());

            QuietXMega.started = new CountDownLatch(1);
            QuietXMega.release = new CountDownLatch(1);
            QuietXMega.results = Collections.singletonList(
                    video("After close", "https://www.xmegadrive.com/videos/closed/", "XMegaDrive"));
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(900));
            assertTrue(QuietXMega.started.await(3, TimeUnit.SECONDS));
            panel.close();
            QuietXMega.release.countDown();
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));
            assertFalse((boolean) ReflectionHelpers.callInstanceMethod(panel, "delivers",
                    ReflectionHelpers.ClassParameter.from(int.class, 1),
                    ReflectionHelpers.ClassParameter.from(SearchPanel.Scope.class, SearchPanel.Scope.XMEGADRIVE),
                    ReflectionHelpers.ClassParameter.from(String.class, "second query")));
        } finally { close(panel); }
    }

    @Test public void selectedScopeAndTypedQuerySurviveRecreation() {
        ActivityController<SearchActivity> original =
                Robolectric.buildActivity(SearchActivity.class).create().start().resume();
        SearchPanel panel = original.get().panel;
        chip(panel, "BaddieHub").performClick();
        panel.field().setText("kept query");
        ReflectionHelpers.callInstanceMethod(panel, "cancelSearchWork");
        Bundle state = new Bundle();
        original.pause().saveInstanceState(state).stop().destroy();
        ActivityController<SearchActivity> restored =
                Robolectric.buildActivity(SearchActivity.class).create(state).start().resume();
        try {
            SearchPanel again = restored.get().panel;
            assertEquals("kept query", again.field().getText().toString());
            assertEquals(SearchPanel.Scope.BADDIEHUB, ReflectionHelpers.getField(again, "scope"));
        } finally { restored.pause().stop().destroy(); }
    }

    @Test public void baddieHubRecentSearchUsesBaddieHubAndCategoryTapsUseNativeRouting() {
        ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup();
        RecentSearchStore.remember(host.get(), "sample");
        SearchGlassOwner owner = new SearchGlassOwner(host.get());
        SearchPanel panel = new SearchPanel(host.get(), new Intent(), true, () -> {});
        panel.create(null);
        host.get().setContentView(panel.view);
        try {
            chip(panel, "BaddieHub").performClick();
            find(panel.view, "Search again for sample").performClick();
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(SearchPanel.Scope.BADDIEHUB, ReflectionHelpers.getField(panel, "scope"));
            assertEquals("sample", QuietBaddie.lastQuery);
            assertTrue(QuietBaddie.calls.get() > 0);
            assertEquals(0, QuietXMega.calls.get());

            NativeContentItem category = new NativeContentItem(
                    NativeContentItem.KIND_CATEGORY, "Sample",
                    "https://baddiehub.com/category/sample/", "", "", "BaddieHub", "");
            ReflectionHelpers.callInstanceMethod(panel, "openResult",
                    ReflectionHelpers.ClassParameter.from(NativeContentItem.class, category));
            Intent next = shadowOf(host.get()).getNextStartedActivity();
            assertEquals(NativeFeedBrowserActivity.SOURCE_BADDIEHUB,
                    next.getStringExtra(NativeFeedBrowserActivity.EXTRA_SOURCE));

            NativeContentItem xmega = new NativeContentItem(
                    NativeContentItem.KIND_CATEGORY, "Femdom",
                    "https://www.xmegadrive.com/categories/femdom/", "", "", "XMegaDrive", "");
            ReflectionHelpers.callInstanceMethod(panel, "openResult",
                    ReflectionHelpers.ClassParameter.from(NativeContentItem.class, xmega));
            Intent xNext = shadowOf(host.get()).getNextStartedActivity();
            assertEquals(NativeFeedBrowserActivity.SOURCE_XMEGADRIVE,
                    xNext.getStringExtra(NativeFeedBrowserActivity.EXTRA_SOURCE));

            Intent playback = new Intent();
            ReflectionHelpers.callInstanceMethod(panel, "putPlaybackSource",
                    ReflectionHelpers.ClassParameter.from(Intent.class, playback),
                    ReflectionHelpers.ClassParameter.from(NativeContentItem.class, new NativeContentItem(
                            NativeContentItem.KIND_MEDIA, "Clip",
                            "https://www.xmegadrive.com/videos/example/", "", "", "XMegaDrive", "")));
            assertEquals(NativeFeedBrowserActivity.SOURCE_XMEGADRIVE,
                    playback.getStringExtra(VideoDetailActivity.EXTRA_SOURCE));
            Intent baddiePlayback = new Intent();
            ReflectionHelpers.callInstanceMethod(panel, "putPlaybackSource",
                    ReflectionHelpers.ClassParameter.from(Intent.class, playback),
                    ReflectionHelpers.ClassParameter.from(NativeContentItem.class, new NativeContentItem(
                            NativeContentItem.KIND_MEDIA, "Clip",
                            "https://baddiehub.com/lala-koi-54/", "", "", "BaddieHub", "")));
            assertEquals(NativeFeedBrowserActivity.SOURCE_BADDIEHUB,
                    playback.getStringExtra(VideoDetailActivity.EXTRA_SOURCE));
        } finally { close(panel); owner.close(); host.pause().stop().destroy(); }
    }

    @Test public void scopeSelectorReusesSearchHeaderGlass() {
        ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup();
        SearchGlassOwner owner = new SearchGlassOwner(host.get());
        SearchPanel panel = new SearchPanel(host.get(), new Intent(), false, () -> {});
        panel.create(null);
        host.get().setContentView(panel.view);
        try {
            layout(panel.view, 400, 800);
            GlassHeaderView header = ReflectionHelpers.getField(panel, "header");
            GlassHeaderView scopes = ReflectionHelpers.getField(panel, "scopeSurface");
            GlassHeaderView results = ReflectionHelpers.getField(panel, "resultSurface");
            assertTrue(scopes.sharesBackdropWith(header));
            assertTrue(scopes.usesHeaderGlass());
            assertTrue(header.usesHeaderGlass());
            assertFalse(results.usesHeaderGlass());
            assertEquals(header.getLeft(), scopes.getLeft());
            assertTrue(scopes.getTop() >= header.getBottom());
            assertEquals(BrowseUi.dp(host.get(), SearchGlassLayout.SCOPE_HEIGHT_DP), scopes.getHeight());
            assertEquals(BrowseUi.dp(host.get(), 56), header.getHeight());
            layout(panel.view, 320, 640);
            View scroll = ReflectionHelpers.getField(panel, "scopeScroll");
            assertTrue(scroll instanceof android.widget.HorizontalScrollView);
            View row = ((ViewGroup) scroll).getChildAt(0);
            assertTrue(row.getWidth() >= scroll.getWidth());
        } finally { close(panel); owner.close(); host.pause().stop().destroy(); }
    }

    @Test public void compiledSearchPathsStayOnTheConfiguredOrigin() {
        assertEquals("https://www.xmegadrive.com/search/car-crash/",
                XMegaDriveRepository.searchUrl("car crash"));
        assertEquals("", XMegaDriveRepository.searchUrl("   "));
        assertEquals("https://cdn.example/?s=car+crash",
                BaddieHubRepository.searchUrl("https://cdn.example/", "car crash"));
        assertEquals("", BaddieHubRepository.searchUrl("https://cdn.example/", "a"));
        assertEquals("https://kaotic.example/search/car%20crash",
                WebVideoSourceRepository.kaoticSearchUrl("https://kaotic.example/", "car crash"));
        assertEquals("", WebVideoSourceRepository.kaoticSearchUrl("https://kaotic.example/", "a"));
    }

    private static SearchPanel open() { return open(new Intent()); }

    private static SearchPanel open(Intent intent) {
        ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup();
        SearchGlassOwner owner = new SearchGlassOwner(host.get());
        SearchPanel panel = new SearchPanel(host.get(), intent, true, () -> {});
        panel.create(null);
        host.get().setContentView(panel.view);
        panel.view.setTag(new Session(host, owner));
        return panel;
    }

    private static void close(SearchPanel panel) {
        Object tag = panel.view.getTag();
        panel.close();
        if (tag instanceof Session) {
            Session session = (Session) tag;
            session.owner.close();
            session.host.pause().stop().destroy();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> labels(SearchPanel panel) {
        List<TextView> chips = ReflectionHelpers.getField(panel, "scopeViews");
        ArrayList<String> labels = new ArrayList<>();
        for (TextView chip : chips) labels.add(chip.getText().toString());
        return labels;
    }

    private static TextView chip(SearchPanel panel, String label) {
        for (TextView chip : ReflectionHelpers.<List<TextView>>getField(panel, "scopeViews")) {
            if (label.contentEquals(chip.getText())) return chip;
        }
        throw new AssertionError(label);
    }

    private static NativeContentItem video(String title, String url, String source) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, title, url, "", "", source, "");
    }

    private static String boundText(SearchPanel panel, GlobalSearchAdapter adapter) {
        StringBuilder text = new StringBuilder();
        FrameHost host = new FrameHost(panel.view.getContext());
        for (int i = 0; i < adapter.getItemCount(); i++) {
            RecyclerView.ViewHolder holder = adapter.onCreateViewHolder(host, adapter.getItemViewType(i));
            adapter.onBindViewHolder(holder, i);
            collect(holder.itemView, text);
        }
        return text.toString();
    }

    private static void collect(View view, StringBuilder text) {
        if (view instanceof TextView) text.append(((TextView) view).getText()).append('\n');
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), text);
        }
    }

    private static View find(View root, String description) {
        if (root.getContentDescription() != null && description.contentEquals(root.getContentDescription())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void layout(View view, int width, int height) {
        for (int i = 0; i < 2; i++) {
            view.forceLayout();
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            view.layout(0, 0, width, height);
        }
    }

    private static void await(java.util.function.BooleanSupplier done) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!done.getAsBoolean() && System.nanoTime() < end) {
            shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(10);
        }
        assertTrue(done.getAsBoolean());
    }

    private static final class Session {
        final ActivityController<?> host;
        final SearchGlassOwner owner;
        Session(ActivityController<?> host, SearchGlassOwner owner) {
            this.host = host;
            this.owner = owner;
        }
    }

    private static final class FrameHost extends android.widget.FrameLayout {
        FrameHost(Context context) { super(context); }
    }
}
