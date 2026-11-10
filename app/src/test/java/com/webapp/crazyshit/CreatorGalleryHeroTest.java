package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.signature.ObjectKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {35, 36}, qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CreatorGalleryHeroTest {
    private Activity activity;
    private FrameLayout root;
    private ImageView first, second;
    private CreatorGalleryHero hero;
    private final Map<String, DataFetcher.DataCallback<? super InputStream>> pending = new ConcurrentHashMap<>();
    @Before public void setup() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_CrazyShit);
        activity.getSharedPreferences("app_prefs", 0).edit().putBoolean("immersive_motion_enabled", true).commit();
        Glide.init(activity, new com.bumptech.glide.GlideBuilder()
                .setMemoryCache(new com.bumptech.glide.load.engine.cache.MemoryCacheAdapter())
                .setBitmapPool(new com.bumptech.glide.load.engine.bitmap_recycle.BitmapPoolAdapter()));
        java.util.concurrent.CompletableFuture.runAsync(() -> Glide.get(activity).clearDiskCache()).get(10, TimeUnit.SECONDS);
        Glide.get(activity).getRegistry().prepend(GlideUrl.class, InputStream.class,
                new ModelLoaderFactory<GlideUrl, InputStream>() {
                    @Override public ModelLoader<GlideUrl, InputStream> build(MultiModelLoaderFactory factory) {
                        return new ModelLoader<GlideUrl, InputStream>() {
                            @Override public boolean handles(GlideUrl model) { return true; }
                            @Override public LoadData<InputStream> buildLoadData(GlideUrl model, int w, int h,
                                    com.bumptech.glide.load.Options options) {
                                return new LoadData<>(new ObjectKey(model.toStringUrl()), new DataFetcher<InputStream>() {
                                    @Override public void loadData(com.bumptech.glide.Priority priority, DataCallback<? super InputStream> callback) {
                                        pending.put(model.toStringUrl(), callback);
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
        root = new FrameLayout(activity);
        first = new ImageView(activity); second = new ImageView(activity);
        first.setScaleType(ImageView.ScaleType.CENTER_CROP); second.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(first, new FrameLayout.LayoutParams(-1, -1));
        root.addView(second, new FrameLayout.LayoutParams(-1, -1));
        activity.setContentView(root);
        layout();
        hero = new CreatorGalleryHero(first, second);
    }
    @After public void close() {
        if (hero != null) hero.close();
        if (root != null) root.removeAllViews();
        if (activity != null) activity.finish();
        Glide.tearDown();
    }
    private void layout() {
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(280, android.view.View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 280);
    }
    private NativeContentItem image(String name) {
        return new NativeContentItem(NativeContentItem.KIND_IMAGE, name, "https://fixtures/" + name + ".png",
                "https://fixtures/" + name + ".png", "", "", "", "");
    }
    private void waitUntil(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean() && System.nanoTime() < end) {
            ShadowLooper.idleMainLooper(); Thread.sleep(10);
        }
        assertTrue("Timed out waiting for controlled Glide result", condition.getAsBoolean());
    }
    private void deliver(String url, int color) throws Exception {
        waitUntil(() -> pending.containsKey(url));
        Bitmap bitmap = Bitmap.createBitmap(400, 280, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes); bitmap.recycle();
        pending.remove(url).onDataReady(new ByteArrayInputStream(bytes.toByteArray()));
    }
    private String displayed() { return ReflectionHelpers.getField(hero, "displayedUrl"); }
    private ImageView visible() {
        Object slot = ReflectionHelpers.getField(hero, "visible");
        return ReflectionHelpers.getField(slot, "view");
    }
    private void settleFade() { ShadowLooper.idleMainLooper(250, TimeUnit.MILLISECONDS); }

    @Test public void realGlideKeepsOldOwnerWhileLoadingAndBothThroughCrossfade() throws Exception {
        hero.items(Collections.singletonList(image("one")), false); hero.active(true);
        deliver(image("one").url, Color.BLUE); waitUntil(() -> displayed().equals(image("one").url));
        ImageView oldView = visible(); Bitmap old = ((BitmapDrawable) oldView.getDrawable()).getBitmap();
        hero.items(Collections.singletonList(image("two")), false);
        waitUntil(() -> pending.containsKey(image("two").url));
        assertSame(old, ((BitmapDrawable) oldView.getDrawable()).getBitmap());
        assertFalse(old.isRecycled());
        deliver(image("two").url, Color.GREEN);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!displayed().equals(image("two").url) && System.nanoTime() < deadline) {
            // Stop at the ownership handoff, before draining the animation frames.
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).runOneTask();
            Thread.sleep(10);
        }
        assertEquals(image("two").url, displayed());
        assertNotNull(ReflectionHelpers.getField(hero, "fade"));
        assertNotNull(oldView.getDrawable());
        assertFalse(old.isRecycled());
        assertNotSame(oldView, visible());
        settleFade();
        assertNull(oldView.getDrawable());
        assertEquals(1f, visible().getAlpha(), 0f);
        assertEquals(Color.GREEN, ((BitmapDrawable) visible().getDrawable()).getBitmap().getPixel(20, 20));
    }

    @Test public void replacementInvalidatesLateResultsAndPauseRetainsCommittedArtwork() throws Exception {
        hero.items(Collections.singletonList(image("one")), false); hero.active(true);
        waitUntil(() -> pending.containsKey(image("one").url));
        hero.items(Collections.singletonList(image("two")), false);
        deliver(image("one").url, Color.BLUE);
        deliver(image("two").url, Color.GREEN); waitUntil(() -> displayed().equals(image("two").url));
        assertEquals(Color.GREEN, ((BitmapDrawable) visible().getDrawable()).getBitmap().getPixel(20, 20));
        hero.items(Arrays.asList(image("two"), image("three")), true);
        ShadowLooper.idleMainLooper(CreatorGalleryHero.ROTATION_MS, TimeUnit.MILLISECONDS);
        waitUntil(() -> pending.containsKey(image("three").url));
        hero.active(false);
        deliver(image("three").url, Color.RED);
        ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS);
        assertEquals(image("two").url, displayed());
        assertNotNull(visible().getDrawable());
        assertFalse((Boolean) ReflectionHelpers.getField(hero, "scheduled"));
        hero.close();
        assertNull(first.getDrawable()); assertNull(second.getDrawable());
    }

    @Test public void randomRotationUsesOnlyPhotosAndNeverImmediatelyRepeats() throws Exception {
        NativeContentItem video = new NativeContentItem(NativeContentItem.KIND_MEDIA, "Video",
                "https://fixtures/video.mp4", "https://fixtures/poster.png", "", "", "", "");
        hero.items(Arrays.asList(image("one"), image("two"), video), false); hero.active(true);
        waitUntil(() -> !pending.isEmpty());
        String initial = pending.keySet().iterator().next();
        deliver(initial, Color.BLUE); waitUntil(() -> displayed().equals(initial));
        ShadowLooper.idleMainLooper(CreatorGalleryHero.ROTATION_MS - 1, TimeUnit.MILLISECONDS);
        assertTrue(pending.isEmpty());
        ShadowLooper.idleMainLooper(1, TimeUnit.MILLISECONDS);
        waitUntil(() -> !pending.isEmpty());
        String next = pending.keySet().iterator().next();
        assertNotEquals(initial, next);
        assertFalse(next.contains("poster")); assertFalse(next.contains("mp4"));
        deliver(next, Color.GREEN); waitUntil(() -> displayed().equals(next));
    }

    @Test public void failedPhotoDoesNotStrandTheRemainingSingleCandidateOnOldCover() throws Exception {
        hero.fallback("https://fixtures/cover.png", ""); hero.active(true);
        deliver("https://fixtures/cover.png", Color.BLUE); waitUntil(() -> displayed().endsWith("cover.png"));
        hero.items(Arrays.asList(image("one"), image("two")), false);
        waitUntil(() -> !pending.isEmpty());
        String failed = pending.keySet().iterator().next();
        pending.remove(failed).onLoadFailed(new java.io.IOException("fixture failure"));
        waitUntil(() -> !pending.isEmpty());
        String remaining = pending.keySet().iterator().next();
        assertNotEquals(failed, remaining);
        deliver(remaining, Color.GREEN); waitUntil(() -> displayed().equals(remaining));
        settleFade(); assertNotNull(visible().getDrawable());
    }

    @Test public void cancelledCoverIsRecoveredWhenEveryGalleryPhotoFails() throws Exception {
        String cover = "https://fixtures/cover.png";
        hero.fallback(cover, ""); hero.active(true);
        waitUntil(() -> pending.containsKey(cover));
        pending.remove(cover); // Keep its original in-flight fetch cancelled by pool replacement.
        hero.items(Collections.singletonList(image("bad")), false);
        waitUntil(() -> pending.containsKey(image("bad").url));
        pending.remove(image("bad").url).onLoadFailed(new java.io.IOException("bad photo"));
        deliver(cover, Color.BLUE);
        waitUntil(() -> displayed().equals(cover));
        assertNotNull(visible().getDrawable());
        assertFalse((Boolean) ReflectionHelpers.getField(hero, "scheduled"));
    }

    @Test public void exhaustedPoolAndFailedFallbackDoNotRetryForeverOrRecrawl() throws Exception {
        String cover = "https://fixtures/cover.png";
        hero.fallback(cover, ""); hero.active(true);
        waitUntil(() -> pending.containsKey(cover)); pending.remove(cover);
        hero.items(Collections.singletonList(image("bad")), false);
        waitUntil(() -> pending.containsKey(image("bad").url));
        pending.remove(image("bad").url).onLoadFailed(new java.io.IOException("bad photo"));
        waitUntil(() -> pending.containsKey(cover));
        pending.remove(cover).onLoadFailed(new java.io.IOException("bad cover"));
        waitUntil(() -> (Boolean) ReflectionHelpers.getField(hero, "fallbackFailed"));
        hero.active(false); hero.active(true);
        ShadowLooper.idleMainLooper(26_000L, TimeUnit.MILLISECONDS);
        assertTrue(pending.isEmpty());
        assertNull(ReflectionHelpers.getField(hero, "pending"));
        assertFalse((Boolean) ReflectionHelpers.getField(hero, "scheduled"));
    }

    @Test public void metadataPoolIsBoundedAndReducedMotionDoesNotScheduleRotation() throws Exception {
        java.util.ArrayList<NativeContentItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < 90; i++) items.add(image("p" + i));
        hero.items(items, false);
        Map<?, ?> photos = ReflectionHelpers.getField(hero, "photos");
        assertEquals(CreatorGalleryHero.MAX_PHOTOS, photos.size());
        activity.getSharedPreferences("app_prefs", 0).edit().putBoolean("immersive_motion_enabled", false).commit();
        hero.active(true); waitUntil(() -> !pending.isEmpty());
        String selected = pending.keySet().iterator().next();
        deliver(selected, Color.BLUE); waitUntil(() -> displayed().equals(selected));
        assertFalse((Boolean) ReflectionHelpers.getField(hero, "scheduled"));
        assertNull(ReflectionHelpers.getField(hero, "fade"));
    }

    @Test public void preLayoutActivationWaitsForOneLayoutWithoutPostingRetryLoop() throws Exception {
        hero.close();
        root.removeAllViews();
        // Keep this fixture detached until explicit layout; draining the attached
        // window would legitimately lay it out and consume waitingForLayout.
        root = new FrameLayout(activity);
        first = new ImageView(activity); second = new ImageView(activity);
        root.addView(first, new FrameLayout.LayoutParams(-1, -1));
        root.addView(second, new FrameLayout.LayoutParams(-1, -1));
        hero = new CreatorGalleryHero(first, second);
        hero.items(Collections.singletonList(image("layout")), false);
        hero.active(true);
        ShadowLooper.idleMainLooper();
        assertTrue(pending.isEmpty());
        assertNotNull(ReflectionHelpers.getField(hero, "waitingForLayout"));
        layout();
        deliver(image("layout").url, Color.BLUE);
        waitUntil(() -> displayed().equals(image("layout").url));
        assertNotNull(visible().getDrawable());
    }

    @Test public void originalsAreDerivedWithoutAnyProviderLookup() throws Exception {
        NativeContentItem onlyHaven = new NativeContentItem(NativeContentItem.KIND_IMAGE, "Photo",
                "https://cdn.cum.st/original.jpg", "https://cdn.cum.st/thumb.jpg", "",
                "https://cum.st/creators/test/", "", "");
        NativeContentItem fapello = new NativeContentItem(NativeContentItem.KIND_IMAGE, "Photo",
                "https://fapello.com/test/1/", "https://fapello.com/content/a/a/photo_300px.jpg", "", "", "", "");
        String derived = ReflectionHelpers.callStaticMethod(CreatorGalleryHero.class, "photoUrl",
                ReflectionHelpers.ClassParameter.from(NativeContentItem.class, fapello));
        assertEquals("https://fapello.com/content/a/a/photo.jpg", derived);
        String only = ReflectionHelpers.callStaticMethod(CreatorGalleryHero.class, "photoUrl",
                ReflectionHelpers.ClassParameter.from(NativeContentItem.class, onlyHaven));
        assertEquals(onlyHaven.url, only);
    }
}
