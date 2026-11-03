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
    private byte[] tiny;
    private final AtomicInteger fetches = new AtomicInteger();
    @Before public void setup() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        Bitmap bitmap = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.BLUE);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes);
        bitmap.recycle();
        byte[] png = bytes.toByteArray();
        Bitmap small = Bitmap.createBitmap(60, 120, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream smallBytes = new ByteArrayOutputStream(); small.compress(Bitmap.CompressFormat.PNG, 100, smallBytes);
        small.recycle(); tiny = smallBytes.toByteArray();
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
                                        callback.onDataReady(new ByteArrayInputStream(model.toStringUrl().contains("tiny") ? tiny : png));
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
        ReflectionHelpers.setField(hub, "categoryHeroSource", new OnlyFapHubView.CategoryHeroSource() {
            public OnlyHavenRepository.CategoryPage category(ContentPreference mode, int limit, int offset) {
                return new OnlyHavenRepository.CategoryPage(Collections.emptyList(), false);
            }
            public List<NativeContentItem> media(OnlyHavenRepository.Creator creator, int page, int limit) {
                return Collections.emptyList();
            }
        });
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
            assertTrue(requested.contains(OnlyFapCategoryLoader.identity(category.get(i))));
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
        assertEquals(View.INVISIBLE, ((View) ReflectionHelpers.getField(hub, "heroDots")).getVisibility());
        call("requestMoreHeroDiscovery");
        assertEquals(2, (int) ReflectionHelpers.getField(hub, "heroDiscoveryNextPage"));
        assertFalse(ReflectionHelpers.getField(hub, "heroDiscoveryLoading"));
    }

    @Test public void rejectedArtworkDoesNotConsumeSlotAndExhaustedCategoryDoesNotBackfill() throws Exception {
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
        awaitCondition(() -> (boolean) ReflectionHelpers.getField(hub, "heroDiscoveryExhausted"));
        while (!io.queue.isEmpty()) { io.runNext(); ShadowLooper.idleMainLooper(); }
        assertEquals(1, heroes().size());
        assertTrue(io.queue.isEmpty());
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
        assertSame(((View) ReflectionHelpers.getField(hub, "heroHint")).getParent(), copy.getChildAt(copy.indexOfChild(row) - 1));
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

    private void seedVisibleHero() {
        ((android.widget.ImageView) ReflectionHelpers.getField(hub, "heroImage"))
                .setImageDrawable(new android.graphics.drawable.ColorDrawable(Color.BLUE));
        ((android.widget.TextView) ReflectionHelpers.getField(hub, "heroTitle")).setText("Outgoing creator");
        ((android.widget.TextView) ReflectionHelpers.getField(hub, "heroHint")).setText("Outgoing source");
        ((View) ReflectionHelpers.getField(hub, "heroAction")).setVisibility(View.VISIBLE);
        ReflectionHelpers.setField(hub, "heroArtworkReady", true);
    }
    private void changeMode(ContentPreference mode) {
        ContentPreference.set(activity, mode);
        hub.preparePreference(mode);
    }
    private Object pendingHero(String title) throws Exception {
        NativeContentItem creator = new NativeContentItem(NativeContentItem.KIND_CREATOR,
                title, "https://cum.st/creators/onlyfans/" + title, "", "New source", "", "");
        hub.setPreferenceCreators(Collections.singletonList(creator));
        Class<?> type = Class.forName("com.webapp.crazyshit.OnlyFapHubView$HeroCandidate");
        java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor(NativeContentItem.class, String.class, String.class);
        constructor.setAccessible(true);
        Object candidate = constructor.newInstance(creator, "https://cdn.example.invalid/" + title + ".jpg", creator.url);
        ReflectionHelpers.setField(hub, "pendingHero", candidate);
        ((List<Object>) ReflectionHelpers.getField(hub, "heroItems")).add(candidate);
        return candidate;
    }
    private void ready(int token, Object candidate) throws Exception {
        java.lang.reflect.Method commit = OnlyFapHubView.class.getDeclaredMethod("commitPreferenceArtwork",
                int.class, candidate.getClass());
        commit.setAccessible(true);
        android.graphics.drawable.Drawable resource = new android.graphics.drawable.ColorDrawable(Color.RED);
        ((android.widget.ImageView) ReflectionHelpers.getField(hub, "preferenceImage")).setImageDrawable(resource);
        commit.invoke(hub, token, candidate);
    }
    private String title() { return ((android.widget.TextView) ReflectionHelpers.getField(hub, "heroTitle")).getText().toString(); }

    @Test public void modeChangeAndSecondRefreshRetainPixelsAndCopyButDiscardOldCandidates() {
        seedVisibleHero();
        Object originalImage = ReflectionHelpers.getField(hub, "heroImage");
        android.graphics.drawable.Drawable original = ((android.widget.ImageView) originalImage).getDrawable();
        changeMode(ContentPreference.MEN);
        hub.clear(); // MainPagerAdapter's existing second clear must not expose a blank hero.
        assertSame(originalImage, ReflectionHelpers.getField(hub, "heroImage"));
        assertSame(original, ((android.widget.ImageView) originalImage).getDrawable());
        assertEquals("Outgoing creator", title());
        assertTrue(heroes().isEmpty());
        assertNull(ReflectionHelpers.getField(hub, "heroItem"));
        assertFalse(((View) ReflectionHelpers.getField(hub, "heroAction")).isEnabled());
        assertTrue(((View) ReflectionHelpers.getField(hub, "preferenceSelector")).isEnabled());
        assertEquals(View.VISIBLE, ((View) ReflectionHelpers.getField(hub, "preferenceLoading")).getVisibility());
        assertNull(hub.findViewWithTag("content_preference_presentation"));
    }
    @Test public void imageReadyPublishesNewCopyAndImageTogetherThenSettlesAndCleansUp() throws Exception {
        seedVisibleHero(); changeMode(ContentPreference.MEN);
        Object outgoingImage = ReflectionHelpers.getField(hub, "heroImage");
        Object candidate = pendingHero("Incoming");
        assertEquals("Outgoing creator", title());
        ReflectionHelpers.setField(hub, "active", true);
        ready(generation(), candidate);
        assertEquals("Incoming", title());
        assertNotSame(outgoingImage, ReflectionHelpers.getField(hub, "heroImage"));
        assertEquals("Outgoing creator", ((android.widget.TextView) ReflectionHelpers.getField(hub, "previousHeroTitle")).getText().toString());
        assertEquals(0f, ((View) ReflectionHelpers.getField(hub, "heroImage")).getAlpha(), .001f);
        assertEquals(0f, ((View) ReflectionHelpers.getField(hub, "heroTitle")).getAlpha(), .001f);
        ShadowLooper.idleMainLooper(1200, TimeUnit.MILLISECONDS);
        assertNull(ReflectionHelpers.getField(hub, "heroMorph"));
        assertEquals(1f, ((View) ReflectionHelpers.getField(hub, "heroImage")).getAlpha(), 0f);
        assertEquals(1f, ((View) ReflectionHelpers.getField(hub, "heroTitle")).getAlpha(), 0f);
        assertEquals(View.INVISIBLE, ((View) ReflectionHelpers.getField(hub, "preferenceLoading")).getVisibility());
        assertNull(hub.findViewWithTag("content_preference_presentation"));
        assertTrue(((View) ReflectionHelpers.getField(hub, "heroAction")).isEnabled());
    }
    @Test public void staleReadyCannotPublishAfterRapidModeSwitchAndReducedMotionCommitsImmediately() throws Exception {
        seedVisibleHero(); changeMode(ContentPreference.MEN);
        Object stale = pendingHero("Stale"); int oldGeneration = generation();
        changeMode(ContentPreference.GROUPS);
        ready(oldGeneration, stale);
        assertEquals("Outgoing creator", title());
        Object current = pendingHero("Current");
        ContentPreference.prefs(activity).edit().putBoolean("immersive_motion_enabled", false).commit();
        ReflectionHelpers.setField(hub, "active", true);
        ready(generation(), current);
        assertEquals("Current", title());
        assertNull(ReflectionHelpers.getField(hub, "heroMorph"));
        assertEquals(1f, ((View) ReflectionHelpers.getField(hub, "heroImage")).getAlpha(), 0f);
        assertEquals(1f, ((View) ReflectionHelpers.getField(hub, "heroTitle")).getAlpha(), 0f);
    }
    @Test public void emptyCategoryRetiresTheOldHeroWithoutAnUnfilteredFallback() {
        seedVisibleHero(); changeMode(ContentPreference.GROUPS);
        hub.setPreferenceCreators(Collections.emptyList());
        assertFalse((boolean) ReflectionHelpers.getField(hub, "preferenceHeroPending"));
        assertEquals("No featured creator", title());
        assertEquals(View.GONE, ((View) ReflectionHelpers.getField(hub, "heroAction")).getVisibility());
        assertEquals(View.INVISIBLE, ((View) ReflectionHelpers.getField(hub, "preferenceLoading")).getVisibility());
        assertTrue(heroes().isEmpty());
    }
    @Test public void leavingTabSettlesTheFadeAndRemovesTheNotice() throws Exception {
        seedVisibleHero(); changeMode(ContentPreference.MEN);
        Object current = pendingHero("Current");
        ReflectionHelpers.setField(hub, "active", true);
        ready(generation(), current);
        hub.setActive(false);
        assertNull(ReflectionHelpers.getField(hub, "heroMorph"));
        assertEquals(1f, ((View) ReflectionHelpers.getField(hub, "heroImage")).getAlpha(), 0f);
        assertNull(hub.findViewWithTag("content_preference_presentation"));
    }
    @Test public void lastFallbackPageInFlightCannotRetireTheRetainedAllContentHero() {
        seedVisibleHero(); changeMode(ContentPreference.ALL);
        ReflectionHelpers.setField(hub, "heroDiscoveryLoading", true);
        ReflectionHelpers.setField(hub, "heroDiscoveryExhausted", true);
        ReflectionHelpers.setField(hub, "heroDiscoveryNextPage", 9);
        call("requestMoreHeroDiscovery");
        assertTrue((boolean) ReflectionHelpers.getField(hub, "preferenceHeroPending"));
        assertEquals("Outgoing creator", title());
        ReflectionHelpers.setField(hub, "heroDiscoveryLoading", false);
        call("requestMoreHeroDiscovery");
        assertFalse((boolean) ReflectionHelpers.getField(hub, "preferenceHeroPending"));
        assertEquals("No featured creator", title());
    }
    @Test public void detachingTheHubSettlesTheFadeAndReleasesTransientUi() throws Exception {
        seedVisibleHero(); changeMode(ContentPreference.MEN);
        Object current = pendingHero("Current");
        ReflectionHelpers.setField(hub, "active", true);
        ready(generation(), current);
        activity.setContentView(new android.widget.FrameLayout(activity));
        assertNull(ReflectionHelpers.getField(hub, "heroMorph"));
        assertEquals(1f, ((View) ReflectionHelpers.getField(hub, "heroImage")).getAlpha(), 0f);
        assertNull(hub.findViewWithTag("content_preference_presentation"));
    }
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    public void renderPreferenceMorphLoadingMidpointAndSettled() throws Exception {
        seedVisibleHero(); changeMode(ContentPreference.MEN);
        capture(hub, "onlyfap-morph-loading", 411, 700);
        Object current = pendingHero("Incoming creator");
        ReflectionHelpers.setField(hub, "active", true);
        ready(generation(), current);
        ShadowLooper.idleMainLooper(140, TimeUnit.MILLISECONDS);
        capture(hub, "onlyfap-morph-midpoint", 411, 700);
        ShadowLooper.idleMainLooper(1100, TimeUnit.MILLISECONDS);
        capture(hub, "onlyfap-morph-settled", 411, 700);
    }

    @Test public void realGlideStartupReplacesRetainedHeroWithoutWaitingForGalleryResolver() throws Exception {
        seedVisibleHero(); changeMode(ContentPreference.MEN);
        ReflectionHelpers.setField(hub, "active", true);
        NativeContentItem creator = thumbnailCreator("Fast startup");
        hub.setPreferenceCreators(Collections.singletonList(creator));
        layoutHero();
        awaitStartup("Fast startup");
        assertNull(ReflectionHelpers.getField(hub, "heroItem"));
        assertTrue(heroes().isEmpty()); // A startup thumbnail never becomes a qualified rotation slot.
        assertFalse(io.queue.isEmpty()); // Gallery selection is still queued, deliberately not executed.
        assertTrue(((View) ReflectionHelpers.getField(hub, "heroAction")).isEnabled());
        assertEquals(View.INVISIBLE, ((View) ReflectionHelpers.getField(hub, "preferenceLoading")).getVisibility());
        assertNotNull(((android.widget.ImageView) ReflectionHelpers.getField(hub, "heroImage")).getDrawable());
    }

    @Test public void realGlideFirstCategoryShelfShowsStartupBeforeLaterShelvesFinish() throws Exception {
        seedVisibleHero(); changeMode(ContentPreference.MEN);
        ReflectionHelpers.setField(hub, "active", true);
        NativeContentItem creator = thumbnailCreator("Progressive startup");
        hub.setPreferenceShelf(ContentPreference.MEN, 0, Collections.singletonList(creator), false);
        layoutHero();
        awaitStartup("Progressive startup");
        assertFalse((boolean) ReflectionHelpers.getField(hub, "shelvesFinished"));
        int before = generation();
        Object startup = ReflectionHelpers.getField(hub, "startupHero");
        Object morph = ReflectionHelpers.getField(hub, "heroMorph");
        hub.setPreferenceShelf(ContentPreference.MEN, 1,
                Collections.singletonList(thumbnailCreator("Later shelf")), false);
        assertEquals(before, generation());
        assertSame(startup, ReflectionHelpers.getField(hub, "startupHero"));
        assertSame(morph, ReflectionHelpers.getField(hub, "heroMorph"));
        assertTrue(heroes().isEmpty()); // Startup artwork still never bypasses gallery qualification.
    }

    @Test public void realGlideRapidSwitchCannotPublishPreviousCategoryStartup() throws Exception {
        seedVisibleHero(); changeMode(ContentPreference.MEN);
        ReflectionHelpers.setField(hub, "active", true);
        hub.setPreferenceCreators(Collections.singletonList(thumbnailCreator("Stale startup")));
        changeMode(ContentPreference.GROUPS);
        hub.setPreferenceCreators(Collections.singletonList(thumbnailCreator("Current startup")));
        layoutHero();
        awaitStartup("Current startup");
        ShadowLooper.idleMainLooper(700, TimeUnit.MILLISECONDS);
        assertEquals("Current startup", title());
        Object startup = ReflectionHelpers.getField(hub, "startupHero");
        NativeContentItem creator = ReflectionHelpers.getField(startup, "creator");
        assertTrue(OnlyFapHeroPolicy.inCategory(creator,
                ReflectionHelpers.getField(hub, "discoverCandidates")));
    }

    @Test public void titleFadesNeverOverlapAndControlsKeepTheirBounds() throws Exception {
        seedVisibleHero(); changeMode(ContentPreference.MEN);
        layoutHero();
        View gallery = ReflectionHelpers.getField(hub, "heroAction");
        View selector = ReflectionHelpers.getField(hub, "preferenceSelector");
        int[] before = new int[2]; selector.getLocationOnScreen(before);
        Object incoming = pendingHero("A much longer incoming creator name spanning two lines");
        ReflectionHelpers.setField(hub, "active", true);
        ready(generation(), incoming);
        android.animation.ValueAnimator animator = ReflectionHelpers.getField(hub, "heroMorph");
        for (float fraction : new float[]{.1f, .25f, .5f, .75f, .9f}) {
            animator.setCurrentFraction(fraction);
            View title = ReflectionHelpers.getField(hub, "heroTitle");
            View previous = ReflectionHelpers.getField(hub, "previousHeroTitle");
            assertEquals(0f, title.getAlpha() * previous.getAlpha(), .001f);
        }
        animator.end(); layoutHero();
        int[] after = new int[2]; selector.getLocationOnScreen(after);
        assertArrayEquals(before, after);
        assertSame(((View) ReflectionHelpers.getField(hub, "heroImage")).getParent(),
                ((View) ReflectionHelpers.getField(hub, "preferenceImage")).getParent());
    }
    @Test public void liquidRiftUsesHeroClockAndNeverStacksFaces() throws Exception {
        seedVisibleHero(); changeMode(ContentPreference.MEN);
        Object incoming = pendingHero("Rift creator");
        ReflectionHelpers.setField(hub, "active", true);
        ready(generation(), incoming);
        android.animation.ValueAnimator animator = ReflectionHelpers.getField(hub, "heroMorph");
        assertEquals(1040L, animator.getDuration());
        View old = ReflectionHelpers.getField(hub, "preferenceImage");
        View image = ReflectionHelpers.getField(hub, "heroImage");
        for (float fraction : new float[]{.1f, .3f, .35f, .4f, .6f, .8f, .95f}) {
            animator.setCurrentFraction(fraction);
            assertEquals("Faces overlap at " + fraction, 0f, old.getAlpha() * image.getAlpha(), .001f);
            assertNotNull(ReflectionHelpers.getField(hub, "heroMorph"));
        }
        animator.end();
        assertEquals(1f, image.getScaleX(), 0f);
        assertEquals(1f, (float) ReflectionHelpers.getField(image, "reveal"), 0f);
        assertEquals(0, (int) ReflectionHelpers.getField(image, "blurLevel"));
        assertNull(ReflectionHelpers.getField(image, "blurs"));
    }

    @Test public void rapidSelectionDuringCollapseDoesNotFinishSupersededReveal() throws Exception {
        seedVisibleHero(); changeMode(ContentPreference.MEN);
        Object incoming = pendingHero("Superseded");
        ReflectionHelpers.setField(hub, "active", true);
        ready(generation(), incoming);
        android.animation.ValueAnimator animator = ReflectionHelpers.getField(hub, "heroMorph");
        animator.setCurrentFraction(.2f);
        changeMode(ContentPreference.TRANS_WOMEN);
        assertNull(ReflectionHelpers.getField(hub, "heroMorph"));
        assertEquals("Outgoing creator", title());
        Object latest = pendingHero("Latest");
        ready(generation(), latest);
        android.animation.ValueAnimator current = ReflectionHelpers.getField(hub, "heroMorph");
        changeMode(ContentPreference.TRANS_WOMEN); // Same-category tap must leave it running.
        assertSame(current, ReflectionHelpers.getField(hub, "heroMorph"));
        current.end();
        assertEquals("Latest", title());
        assertEquals(0, (int) ReflectionHelpers.getField(ReflectionHelpers.getField(hub, "heroImage"), "blurLevel"));
    }

    @Test public void lateShelvesAndCompletionCannotBeLostBehindAnOlderEmptySelection() {
        List<NativeContentItem> category = creators(3);
        hub.setPreferenceShelf(ContentPreference.WOMEN, 0, category.subList(0, 1), false);
        finish(generation(), category.get(0));
        ((Set<String>) ReflectionHelpers.getField(hub, "requestedHeroCreators"))
                .add(OnlyFapCategoryLoader.identity(category.get(0)));
        ReflectionHelpers.setField(hub, "active", true);
        call("requestHeroCandidates");
        io.runNext(); // Snapshot has no unrequested creators and shelves are still unfinished.
        // These publishes arrive before the UI applies that selection result.
        hub.setPreferenceShelf(ContentPreference.WOMEN, 1, category.subList(1, 3), false);
        hub.finishPreferenceShelves(ContentPreference.WOMEN, false);
        ShadowLooper.idleMainLooper();
        assertEquals("Changed inputs must re-run the coalesced selection", 1, io.queue.size());
        io.runNext(); ShadowLooper.idleMainLooper();
        Set<String> requested = ReflectionHelpers.getField(hub, "requestedHeroCreators");
        assertTrue(requested.contains(OnlyFapCategoryLoader.identity(category.get(1))));
        assertEquals(1, (int) ReflectionHelpers.getField(hub, "heroResolveInFlight"));
    }

    @Test public void femalePoolContinuesPastTinyFirstArtworkAndRejectedShelves() throws Exception {
        filteredPoolFills(ContentPreference.WOMEN);
    }
    @Test public void malePoolContinuesPastTinyFirstArtworkAndRejectedShelves() throws Exception {
        filteredPoolFills(ContentPreference.MEN);
    }
    @Test public void transPoolContinuesPastTinyFirstArtworkAndRejectedShelves() throws Exception {
        filteredPoolFills(ContentPreference.TRANS_WOMEN);
    }

    private void filteredPoolFills(ContentPreference mode) throws Exception {
        changeMode(mode);
        CountDownLatch laterPage = new CountDownLatch(1);
        AtomicInteger pages = new AtomicInteger();
        ReflectionHelpers.setField(hub, "categoryHeroSource", new OnlyFapHubView.CategoryHeroSource() {
            public OnlyHavenRepository.CategoryPage category(ContentPreference selected, int limit, int offset) throws IOException {
                assertEquals(mode, selected); assertEquals(48, offset); pages.incrementAndGet();
                try { if (!laterPage.await(5, TimeUnit.SECONDS)) throw new IOException("test gate timeout"); }
                catch (InterruptedException e) { throw new IOException(e); }
                List<OnlyHavenRepository.Creator> result = new ArrayList<>();
                for (int i = 0; i < 9; i++) result.add(profile(mode.value + "-good" + i));
                result.add(result.get(0)); // A repeated creator cannot take a slot.
                return new OnlyHavenRepository.CategoryPage(result, false);
            }
            public List<NativeContentItem> media(OnlyHavenRepository.Creator creator, int page, int limit) {
                if (creator.id.endsWith("first")) return page == 1
                        ? Collections.singletonList(art("tiny-" + mode.value)) : Collections.emptyList();
                // Two distinct creators with the exact same artwork must share no extra slot.
                String image = creator.id.endsWith("good1") ? mode.value + "-good0" : creator.id;
                return Collections.singletonList(art(image));
            }
        });
        startRealDiscovery();
        NativeContentItem first = OnlyHavenRepository.creatorItem(profile(mode.value + "-first"));
        // Fast avatar is allowed, but the tiny gallery image must not complete the pool.
        first = new NativeContentItem(NativeContentItem.KIND_CREATOR, first.title, first.url,
                "https://cdn.example.invalid/tiny-avatar-" + mode.value + ".png", "", "", "");
        hub.setPreferenceCreators(Collections.singletonList(first)); layoutHero();
        try {
            awaitCondition(() -> pages.get() == 1);
            assertTrue(heroes().isEmpty());
            awaitStartup(first.title);
            assertNotNull(ReflectionHelpers.getField(hub, "startupHero"));
        } finally { laterPage.countDown(); }
        awaitCondition(() -> heroes().size() == 8 && !(boolean) ReflectionHelpers.getField(hub, "heroSelectionLoading"));
        awaitCondition(() -> ReflectionHelpers.getField(hub, "heroItem") != null);
        assertEquals(1, pages.get());
        Set<String> urls = new HashSet<>(); Set<String> keys = new HashSet<>();
        for (Object candidate : heroes()) {
            NativeContentItem creator = ReflectionHelpers.getField(candidate, "creator");
            assertTrue(creator.url.contains(mode.value)); assertTrue(keys.add(creator.url));
            List<OnlyFapHeroPolicy.Artwork> choices = ReflectionHelpers.getField(candidate, "artwork");
            for (OnlyFapHeroPolicy.Artwork choice : choices) { assertFalse(choice.url.contains("tiny")); assertTrue(urls.add(choice.url)); }
        }
        ShadowLooper.idleMainLooper(1100, TimeUnit.MILLISECONDS);
        assertFalse(((android.graphics.drawable.BitmapDrawable) ((android.widget.ImageView)
                ReflectionHelpers.getField(hub, "heroImage")).getDrawable()).getBitmap().isRecycled());
    }

    @Test public void qualifiedModeCacheRestoresOnlyAfterCurrentCategoryConfirmsMembership() throws Exception {
        filteredPoolFills(ContentPreference.WOMEN);
        List<NativeContentItem> women = new ArrayList<>();
        for (Object candidate : heroes()) women.add(ReflectionHelpers.getField(candidate, "creator"));
        hub.setActive(false);
        changeMode(ContentPreference.MEN);
        hub.setPreferenceCreators(Collections.singletonList(OnlyHavenRepository.creatorItem(profile("men-only"))));
        assertTrue(heroes().isEmpty());
        changeMode(ContentPreference.WOMEN);
        hub.setPreferenceCreators(women);
        assertTrue(heroes().isEmpty()); // Inactive tab has no new Glide work.
        hub.setActive(true); layoutHero();
        awaitCondition(() -> heroes().size() == 8 && ReflectionHelpers.getField(hub, "heroItem") != null);
        for (Object candidate : heroes()) assertTrue(((NativeContentItem) ReflectionHelpers.getField(candidate, "creator")).url.contains("women"));
    }

    @Test public void tinyStartupUpgradesToOriginalOnSecondMediaPageWithoutTakingTwoSlots() throws Exception {
        changeMode(ContentPreference.MEN);
        CountDownLatch originalReady = new CountDownLatch(1);
        AtomicInteger mediaPages = new AtomicInteger();
        ReflectionHelpers.setField(hub, "categoryHeroSource", new OnlyFapHubView.CategoryHeroSource() {
            public OnlyHavenRepository.CategoryPage category(ContentPreference mode, int limit, int offset) {
                return new OnlyHavenRepository.CategoryPage(Collections.emptyList(), false);
            }
            public List<NativeContentItem> media(OnlyHavenRepository.Creator creator, int page, int limit) throws IOException {
                mediaPages.incrementAndGet();
                if (page == 1) return Collections.singletonList(art("tiny-gallery"));
                try { if (!originalReady.await(5, TimeUnit.SECONDS)) throw new IOException("test gate timeout"); }
                catch (InterruptedException e) { throw new IOException(e); }
                return Collections.singletonList(art("sharp-original"));
            }
        });
        startRealDiscovery();
        NativeContentItem creator = thumbnailCreator("Upgrade");
        hub.setPreferenceCreators(Collections.singletonList(creator)); layoutHero();
        try { awaitStartup("Upgrade"); awaitCondition(() -> mediaPages.get() == 2); assertTrue(heroes().isEmpty()); }
        finally { originalReady.countDown(); }
        awaitCondition(() -> heroes().size() == 1 && ReflectionHelpers.getField(hub, "heroItem") != null);
        Object current = ReflectionHelpers.getField(hub, "heroItem");
        List<OnlyFapHeroPolicy.Artwork> choices = ReflectionHelpers.getField(current, "artwork");
        assertTrue(choices.get(0).url.contains("sharp-original")); assertEquals("Upgrade", title());
        assertEquals(2, mediaPages.get());
    }

    @Test public void staleCategoryPageCannotCrossRapidGenderGenerations() throws Exception {
        CountDownLatch staleReady = new CountDownLatch(1);
        AtomicInteger started = new AtomicInteger();
        ReflectionHelpers.setField(hub, "categoryHeroSource", new OnlyFapHubView.CategoryHeroSource() {
            public OnlyHavenRepository.CategoryPage category(ContentPreference mode, int limit, int offset) throws IOException {
                started.incrementAndGet();
                if (mode == ContentPreference.WOMEN) {
                    // Deliberately emulate a source callback that ignores cancellation.
                    boolean done = false;
                    while (!done) try { done = staleReady.await(5, TimeUnit.SECONDS); }
                    catch (InterruptedException ignored) { }
                }
                return new OnlyHavenRepository.CategoryPage(Collections.singletonList(profile(mode.value + "-fresh")), false);
            }
            public List<NativeContentItem> media(OnlyHavenRepository.Creator creator, int page, int limit) {
                return Collections.singletonList(art(creator.id));
            }
        });
        startRealDiscovery();
        changeMode(ContentPreference.ALL); changeMode(ContentPreference.WOMEN);
        hub.setPreferenceCreators(Collections.emptyList());
        awaitCondition(() -> started.get() == 1);
        try {
            changeMode(ContentPreference.MEN); hub.setPreferenceCreators(Collections.emptyList());
            changeMode(ContentPreference.TRANS_WOMEN); hub.setPreferenceCreators(Collections.emptyList());
        } finally { staleReady.countDown(); }
        layoutHero();
        awaitCondition(() -> heroes().size() == 1 && ReflectionHelpers.getField(hub, "heroItem") != null);
        for (NativeContentItem item : (List<NativeContentItem>) ReflectionHelpers.getField(hub, "discoverCandidates"))
            assertTrue(item.url.contains("trans_women"));
        assertTrue(title().contains("trans_women"));
    }

    @Test public void repeatingFilteredSourceStopsAtBudgetInsteadOfLoopingOnDuplicates() throws Exception {
        AtomicInteger pages = new AtomicInteger();
        changeMode(ContentPreference.MEN);
        ReflectionHelpers.setField(hub, "categoryHeroSource", new OnlyFapHubView.CategoryHeroSource() {
            public OnlyHavenRepository.CategoryPage category(ContentPreference mode, int limit, int offset) {
                pages.incrementAndGet();
                return new OnlyHavenRepository.CategoryPage(Collections.singletonList(profile("duplicate")), true);
            }
            public List<NativeContentItem> media(OnlyHavenRepository.Creator creator, int page, int limit) {
                return Collections.emptyList();
            }
        });
        startRealDiscovery(); hub.setPreferenceCreators(Collections.emptyList());
        awaitCondition(() -> (boolean) ReflectionHelpers.getField(hub, "heroDiscoveryExhausted")
                && !(boolean) ReflectionHelpers.getField(hub, "heroDiscoveryLoading"));
        assertEquals(8, pages.get()); assertTrue(heroes().isEmpty());
        int before = pages.get(); call("refreshHeroSet"); ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS);
        assertEquals(before, pages.get());
    }

    @Test public void actuallyExhaustedFilteredSourceStopsWithoutRetryingEmptyPages() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        changeMode(ContentPreference.TRANS_MEN);
        ReflectionHelpers.setField(hub, "categoryHeroSource", new OnlyFapHubView.CategoryHeroSource() {
            public OnlyHavenRepository.CategoryPage category(ContentPreference mode, int limit, int offset) {
                assertEquals(ContentPreference.TRANS_MEN, mode); requests.incrementAndGet();
                return new OnlyHavenRepository.CategoryPage(Collections.emptyList(), false);
            }
            public List<NativeContentItem> media(OnlyHavenRepository.Creator creator, int page, int limit) {
                fail("Empty category must not resolve a creator"); return Collections.emptyList();
            }
        });
        startRealDiscovery(); hub.setPreferenceCreators(Collections.emptyList());
        awaitCondition(() -> (boolean) ReflectionHelpers.getField(hub, "heroDiscoveryExhausted")
                && !(boolean) ReflectionHelpers.getField(hub, "heroSelectionLoading"));
        assertEquals(1, requests.get()); assertTrue(heroes().isEmpty());
        call("refreshHeroSet"); ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS);
        assertEquals(1, requests.get());
    }

    @Test public void distinctProviderProfilesSharingANameBothQualifyButRenamedDuplicateDoesNot() throws Exception {
        changeMode(ContentPreference.MEN);
        Set<String> resolved = ConcurrentHashMap.newKeySet();
        ReflectionHelpers.setField(hub, "categoryHeroSource", new OnlyFapHubView.CategoryHeroSource() {
            public OnlyHavenRepository.CategoryPage category(ContentPreference mode, int limit, int offset) {
                return new OnlyHavenRepository.CategoryPage(Collections.emptyList(), false);
            }
            public List<NativeContentItem> media(OnlyHavenRepository.Creator creator, int page, int limit) {
                assertTrue("Provider profile must be resolved only once", resolved.add(creator.id));
                return Collections.singletonList(art(creator.id));
            }
        });
        startRealDiscovery();
        List<NativeContentItem> category = new ArrayList<>();
        for (String id : new String[]{"profileA", "profileB", "profileA"})
            category.add(new NativeContentItem(NativeContentItem.KIND_CREATOR,
                    category.size() == 2 ? "Renamed" : "Same name",
                    "https://cum.st/creators/onlyfans/" + id, "", "", "", ""));
        hub.setPreferenceCreators(category); layoutHero();
        awaitCondition(() -> heroes().size() == 2 && (boolean) ReflectionHelpers.getField(hub, "heroDiscoveryExhausted"));
        assertEquals(new HashSet<>(Arrays.asList("profileA", "profileB")), resolved);
    }

    private void startRealDiscovery() {
        RemoteSourceConfigManager.initialize(activity);
        ReflectionHelpers.setField(hub, "heroIo", Executors.newSingleThreadExecutor());
        ReflectionHelpers.setField(hub, "active", true);
    }
    private OnlyHavenRepository.Creator profile(String id) {
        return new OnlyHavenRepository.Creator("onlyfans", id, id, "https://cum.st/creators/onlyfans/" + id, "");
    }
    private NativeContentItem art(String id) {
        return new NativeContentItem(NativeContentItem.KIND_IMAGE, id, "https://cdn.example.invalid/" + id + ".png", "", "", "", "");
    }
    private void awaitCondition(java.util.function.BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 800; i++) {
            ShadowLooper.idleMainLooper(5, TimeUnit.MILLISECONDS);
            if (condition.getAsBoolean()) return;
            Thread.sleep(5);
        }
        for (String key : new String[]{"selectionTask", "resolutionTask", "discoveryTask"}) {
            Future<?> task = ReflectionHelpers.getField(hub, key);
            if (task != null && task.isDone() && !task.isCancelled()) task.get();
        }
        fail("Discovery did not settle: pool=" + heroes().size() + " title=" + title()
                + " selection=" + ReflectionHelpers.getField(hub, "heroSelectionLoading")
                + " inFlight=" + ReflectionHelpers.getField(hub, "heroResolveInFlight"));
    }

    private NativeContentItem thumbnailCreator(String title) {
        return new NativeContentItem(NativeContentItem.KIND_CREATOR, title,
                "https://cum.st/creators/onlyfans/" + title.replace(' ', '-'),
                "https://cdn.example.invalid/startup-" + title.replace(' ', '-') + ".png", "Source", "", "");
    }
    private void layoutHero() {
        int w = BrowseUi.dp(activity, 360), h = BrowseUi.dp(activity, 800);
        hub.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        hub.layout(0, 0, w, h);
    }
    private void awaitStartup(String expected) throws Exception {
        for (int i = 0; i < 400; i++) {
            ShadowLooper.idleMainLooper(10, TimeUnit.MILLISECONDS);
            if (expected.equals(title()) && !(boolean) ReflectionHelpers.getField(hub, "preferenceHeroPending")) return;
            Thread.sleep(5);
        }
        fail("Real Glide startup did not publish " + expected + "; title=" + title());
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
