package com.webapp.crazyshit;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.signature.ObjectKey;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.app.Activity;
import android.app.Application;
import android.view.View;
import android.widget.LinearLayout;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class OnlyFapFilteredHeroTest {
    private OnlyFapHubView hub;
    private Activity activity;
    private ManualExecutor io;
    private final AtomicInteger fetches = new AtomicInteger();
    @Before public void setup() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        Bitmap bitmap = Bitmap.createBitmap(360, 540, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.BLUE);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes);
        bitmap.recycle();
        byte[] png = bytes.toByteArray();
        Glide.get(activity).getRegistry().prepend(GlideUrl.class, InputStream.class,
                new ModelLoaderFactory<GlideUrl, InputStream>() {
                    @Override public ModelLoader<GlideUrl, InputStream> build(MultiModelLoaderFactory factory) {
                        return new ModelLoader<GlideUrl, InputStream>() {
                            @Override public boolean handles(GlideUrl model) { return true; }
                            @Override public LoadData<InputStream> buildLoadData(GlideUrl model,
                                    int width, int height, com.bumptech.glide.load.Options options) {
                                return new LoadData<>(new ObjectKey(model.toStringUrl()), new DataFetcher<InputStream>() {
                                    @Override public void loadData(Priority priority, DataCallback<? super InputStream> callback) {
                                        fetches.incrementAndGet();
                                        callback.onDataReady(new ByteArrayInputStream(png));
                                    }
                                    @Override public void cleanup() { }
                                    @Override public void cancel() { }
                                    @Override public Class<InputStream> getDataClass() { return InputStream.class; }
                                    @Override public DataSource getDataSource() { return DataSource.REMOTE; }
                                });
                            }
                        };
                    }
                    @Override public void teardown() { }
                });
        hub = new OnlyFapHubView(activity, new OnlyFapHubView.Listener() {
            @Override public void onOpenCreator(NativeContentItem creator) { }
            @Override public void onSearch() { }
            @Override public void onViewAllFavorites() { }
        });
        activity.setContentView(hub);
        ((ExecutorService) ReflectionHelpers.getField(hub, "heroIo")).shutdownNow();
        io = new ManualExecutor();
        ReflectionHelpers.setField(hub, "heroIo", io);
        hub.setWorkScheduler((key, task) -> task.run());
        ContentPreference.set(activity, ContentPreference.WOMEN);
        hub.preparePreference(ContentPreference.WOMEN);
    }
    @After public void close() { hub.close(); Glide.tearDown(); }

    @Test public void categoryRefillRequestsMultipleCreatorsOneAtATimeAndStopsAtEight() {
        List<NativeContentItem> category = creators(40);
        hub.setPreferenceCreators(category);
        // Avoid startup/network artwork. Feed validated resolver results at the real completion boundary.
        ReflectionHelpers.setField(hub, "active", true);
        call("requestHeroCandidates");
        for (int i = 0; i < 8; i++) {
            assertEquals(1, io.queue.size());
            io.runNext(); // category selection
            ShadowLooper.idleMainLooper();
            Set<String> requested = ReflectionHelpers.getField(hub, "requestedHeroCreators");
            assertEquals(i + 1, requested.size());
            assertTrue(requested.contains(CreatorFavoriteStore.key(category.get(i))));
            assertEquals(1, (int) ReflectionHelpers.getField(hub, "heroResolveInFlight"));
            assertEquals(1, io.queue.size());
            io.queue.remove(); // no network: simulate the bounded resolver's validated result
            finish(generation(), category.get(i));
        }
        assertEquals(8, heroes().size());
        assertTrue(io.queue.isEmpty());
        assertEquals(13_000L, (long) ReflectionHelpers.getStaticField(OnlyFapHubView.class, "HERO_ROTATION_MS"));
    }

    @Test public void categoryPoolIsExclusiveDeduplicatedAndOneCreatorIsValid() {
        NativeContentItem only = creators(1).get(0);
        List<NativeContentItem> category = Arrays.asList(only, only);
        hub.setPreferenceCreators(category);
        assertEquals(Collections.singletonList(only), OnlyFapHeroPolicy.selectCategory(category, 8));
        finish(generation(), creators(2).get(1));
        assertTrue(heroes().isEmpty());
        finish(generation(), only);
        finish(generation(), only);
        assertEquals(1, heroes().size());
        assertEquals(View.GONE, ((View) ReflectionHelpers.getField(hub, "heroDots")).getVisibility());
        call("requestMoreHeroDiscovery");
        assertEquals(2, (int) ReflectionHelpers.getField(hub, "heroDiscoveryNextPage"));
        assertFalse(ReflectionHelpers.getField(hub, "heroDiscoveryLoading"));
    }

    @Test public void rejectedArtworkDoesNotConsumeSlotAndExhaustedCategoryDoesNotBackfill() {
        List<NativeContentItem> category = creators(2);
        hub.setPreferenceCreators(category);
        ReflectionHelpers.setField(hub, "active", true);
        call("requestHeroCandidates");
        io.runNext(); ShadowLooper.idleMainLooper(); io.queue.remove();
        ReflectionHelpers.callInstanceMethod(hub, "finishHeroResolution",
                ReflectionHelpers.ClassParameter.from(int.class, generation()),
                ReflectionHelpers.ClassParameter.from(NativeContentItem.class, category.get(0)),
                ReflectionHelpers.ClassParameter.from(List.class, Collections.emptyList()));
        assertTrue(heroes().isEmpty());
        io.runNext(); ShadowLooper.idleMainLooper(); io.queue.remove();
        finish(generation(), category.get(1));
        io.runNext(); ShadowLooper.idleMainLooper();
        assertEquals(1, heroes().size());
        assertTrue(io.queue.isEmpty());
        assertFalse(ReflectionHelpers.getField(hub, "heroDiscoveryLoading"));
    }

    @Test public void switchingModesCancelsQueuedWorkAndCannotPublishOldCategoryEvenWithSameCreator() {
        NativeContentItem shared = creators(1).get(0);
        hub.setPreferenceCreators(Collections.singletonList(shared));
        ReflectionHelpers.setField(hub, "active", true);
        call("requestHeroCandidates");
        int old = generation();
        Future<?> selection = ReflectionHelpers.getField(hub, "selectionTask");
        hub.preparePreference(ContentPreference.MEN);
        assertTrue(selection.isCancelled());
        hub.setPreferenceCreators(Collections.singletonList(shared));
        finish(old, shared);
        assertTrue(heroes().isEmpty());
        hub.preparePreference(ContentPreference.WOMEN);
        finish(old, shared);
        assertTrue(heroes().isEmpty());
        assertTrue(((Set<?>) ReflectionHelpers.getField(hub, "requestedHeroCreators")).isEmpty());
    }

    @Test public void staleCallbackCannotReplaceCurrentDeferredHeroWork() {
        Map<String, Runnable> deferred = new LinkedHashMap<>();
        hub.setWorkScheduler(deferred::put);
        int old = generation();
        hub.preparePreference(ContentPreference.MEN);
        int current = generation();
        boolean[] ran = {false};
        enqueue(current, "hero-selection", () -> ran[0] = true);
        enqueue(old, "hero-selection", () -> fail("Stale selection must not enter the work queue"));
        ShadowLooper.idleMainLooper();
        assertEquals(1, deferred.size());
        deferred.remove("hero-selection").run();
        assertTrue(ran[0]);
        enqueue(current, "hero-resolution", () -> fail("Queued work must recheck its generation"));
        ShadowLooper.idleMainLooper();
        hub.preparePreference(ContentPreference.WOMEN);
        deferred.remove("hero-resolution").run();
    }
    private void enqueue(int generation, String key, Runnable task) {
        ReflectionHelpers.callInstanceMethod(hub, "postHeroWork",
                ReflectionHelpers.ClassParameter.from(int.class, generation),
                ReflectionHelpers.ClassParameter.from(String.class, key),
                ReflectionHelpers.ClassParameter.from(Runnable.class, task));
    }

    @Test public void filteredCategoryIgnoresShelfFavoriteAndHistoryExclusionsButAllKeepsThem() {
        List<NativeContentItem> category = creators(2);
        hub.setPreferenceCreators(category);
        ReflectionHelpers.setField(hub, "hotItems", category);
        finish(generation(), category.get(0)); finish(generation(), category.get(1));
        assertEquals(2, heroes().size());
        assertTrue(OnlyFapHeroPolicy.select(Collections.emptyList(), category,
                Collections.emptySet(), category, Collections.emptyList(), Collections.emptyList(), 8).isEmpty());
        hub.preparePreference(ContentPreference.ALL);
        ReflectionHelpers.setField(hub, "hotItems", category);
        finish(generation(), category.get(0));
        assertTrue(heroes().isEmpty());
    }

    @Test public void compactPreferenceSitsBesideMatchingGalleryButtonWithAccessibleTouchTarget() {
        View pill = ReflectionHelpers.getField(hub, "preferenceSelector");
        View gallery = ReflectionHelpers.getField(hub, "heroAction");
        LinearLayout row = (LinearLayout) pill.getParent();
        assertEquals(LinearLayout.HORIZONTAL, row.getOrientation());
        assertSame(row, gallery.getParent());
        assertSame(gallery, row.getChildAt(0));
        assertSame(pill, row.getChildAt(1));
        LinearLayout copy = (LinearLayout) row.getParent();
        assertSame(ReflectionHelpers.getField(hub, "heroHint"), copy.getChildAt(copy.indexOfChild(row) - 1));
        assertTrue(pill.isClickable()); assertTrue(pill.isFocusable());
        assertTrue(pill.getMinimumHeight() >= BrowseUi.dp(activity, 48));
        android.graphics.drawable.GradientDrawable first = (android.graphics.drawable.GradientDrawable) pill.getBackground();
        android.graphics.drawable.GradientDrawable second = (android.graphics.drawable.GradientDrawable) gallery.getBackground();
        assertEquals(first.getColor().getDefaultColor(), second.getColor().getDefaultColor());
        assertEquals(first.getCornerRadius(), second.getCornerRadius(), 0f);
        gallery.setVisibility(View.VISIBLE);
        for (int width : new int[]{320, 411}) {
            hub.measure(View.MeasureSpec.makeMeasureSpec(BrowseUi.dp(activity, width), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(BrowseUi.dp(activity, 700), View.MeasureSpec.EXACTLY));
            hub.layout(0, 0, hub.getMeasuredWidth(), hub.getMeasuredHeight());
            assertEquals(gallery.getTop(), pill.getTop());
            assertEquals(gallery.getBottom(), pill.getBottom());
            assertTrue(pill.getLeft() > gallery.getRight());
            assertTrue(row.getWidth() <= copy.getWidth() - copy.getPaddingLeft() - copy.getPaddingRight());
        }
    }
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    public void renderLowerHeroControlsAndGlassPicker() throws Exception {
        ((android.widget.TextView) ReflectionHelpers.getField(hub, "heroTitle")).setText("Featured creator");
        ((View) ReflectionHelpers.getField(hub, "heroAction")).setVisibility(View.VISIBLE);
        ((android.widget.ImageView) ReflectionHelpers.getField(hub, "heroImage")).setImageDrawable(
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                        new int[]{0xFF8397A5, 0xFF344D65, 0xFF101722}));
        capture(hub, "onlyfap-preference-hero", 411, 700);
        ContentPreferenceUi.choose(activity, null);
        android.app.AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        org.robolectric.shadows.ShadowLooper.idleMainLooper(400, java.util.concurrent.TimeUnit.MILLISECONDS);
        capture(dialog.getWindow().getDecorView(), "onlyfap-preference-picker", 292, 510);
        assertNotNull(dialog.getWindow().getDecorView().findViewWithTag("preference_groups"));
        dialog.dismiss();
    }
    private void capture(View root, String name, int width, int height) throws Exception {
        int w = BrowseUi.dp(activity, width), h = BrowseUi.dp(activity, height);
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        Bitmap image = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        root.draw(new android.graphics.Canvas(image));
        java.io.File output = new java.io.File("build/reports/visual-tests/" + name + ".png");
        output.getParentFile().mkdirs();
        try (FileOutputStream stream = new FileOutputStream(output)) {
            image.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
        image.recycle();
    }

    private int generation() { return ReflectionHelpers.getField(hub, "heroGeneration"); }
    private List<?> heroes() { return ReflectionHelpers.getField(hub, "heroItems"); }
    private void call(String name) { ReflectionHelpers.callInstanceMethod(hub, name); }
    private void finish(int generation, NativeContentItem creator) {
        ReflectionHelpers.callInstanceMethod(hub, "finishHeroResolution",
                ReflectionHelpers.ClassParameter.from(int.class, generation),
                ReflectionHelpers.ClassParameter.from(NativeContentItem.class, creator),
                ReflectionHelpers.ClassParameter.from(List.class, Collections.singletonList(
                        new OnlyFapHeroPolicy.Artwork("https://cdn.example.invalid/" + creator.title + ".jpg", creator.url, false))));
    }
    private static List<NativeContentItem> creators(int count) {
        List<NativeContentItem> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(new NativeContentItem(NativeContentItem.KIND_CREATOR,
                "Creator" + i, "https://cum.st/creators/onlyfans/" + i, "", "", "", ""));
        return result;
    }
    private static class ManualExecutor extends AbstractExecutorService {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        public void execute(Runnable command) { queue.add(command); }
        void runNext() { queue.remove().run(); }
        public void shutdown() { }
        public List<Runnable> shutdownNow() { queue.clear(); return Collections.emptyList(); }
        public boolean isShutdown() { return false; }
        public boolean isTerminated() { return false; }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }
}
