package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.signature.ObjectKey;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {29, 35})
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class OnlyFapHeroBitmapLifecycleTest {
    private Activity activity;
    private org.robolectric.android.controller.ActivityController<Activity> controller;
    private OnlyFapHubView hub;
    private final java.util.Map<String, DataFetcher.DataCallback<? super InputStream>> pending =
            new java.util.concurrent.ConcurrentHashMap<>();
    private byte[] png;

    @Before public void setup() {
        controller = Robolectric.buildActivity(Activity.class).setup();
        activity = controller.get();
        activity.setTheme(R.style.Theme_CrazyShit);
        Glide.init(activity, new com.bumptech.glide.GlideBuilder()
                .setMemoryCache(new com.bumptech.glide.load.engine.cache.MemoryCacheAdapter())
                .setBitmapPool(new com.bumptech.glide.load.engine.bitmap_recycle.BitmapPoolAdapter()));
        Bitmap bitmap = Bitmap.createBitmap(360, 540, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.BLUE);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes);
        bitmap.recycle();
        png = bytes.toByteArray();
        Glide.get(activity).getRegistry().prepend(GlideUrl.class, InputStream.class,
                new ModelLoaderFactory<GlideUrl, InputStream>() {
                    @Override public ModelLoader<GlideUrl, InputStream> build(MultiModelLoaderFactory factory) {
                        return new ModelLoader<GlideUrl, InputStream>() {
                            @Override public boolean handles(GlideUrl model) { return true; }
                            @Override public LoadData<InputStream> buildLoadData(GlideUrl model,
                                    int width, int height, com.bumptech.glide.load.Options options) {
                                return new LoadData<>(new ObjectKey(model.toStringUrl()), new DataFetcher<InputStream>() {
                                    @Override public void loadData(Priority priority, DataCallback<? super InputStream> callback) {
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
        hub = new OnlyFapHubView(activity, new OnlyFapHubView.Listener() {
            @Override public void onOpenCreator(NativeContentItem creator) { }
            @Override public void onSearch() { }
            @Override public void onMore() { }
            @Override public void onViewAllFavorites() { }
        });
        activity.setContentView(hub);
        hub.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        hub.layout(0, 0, 400, 800);
        ReflectionHelpers.setField(hub, "active", true);
        // Isolate startup from source discovery; existing discovery tests cover that system.
        ReflectionHelpers.setField(hub, "heroSelectionLoading", true);
    }

    @After public void close() {
        if (hub != null && !(boolean) ReflectionHelpers.getField(hub, "closed")) hub.close();
        // Expire bounded reveal slots before Robolectric resets the main looper.
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);
        controller.pause().stop().destroy();
        Glide.tearDown(); // Do not leak the fake image loader into other tests.
    }

    @Test public void pendingReplacementNeverDrawsRecycledPreviousFrameAfterMemoryPressure() throws Exception {
        load("a"); complete("a"); await(() -> hasPixels(image().getDrawable()));
        Bitmap pixels = bitmap(image().getDrawable());
        load("b"); await(() -> pending.containsKey(url("b")));
        Glide.get(activity).clearMemory();
        Glide.get(activity).getBitmapPool().clearMemory();
        ShadowLooper.idleMainLooper();
        draw(); // Released bitmap errors must escape here; never catch them in the view.
        assertFalse(pixels.isRecycled());
    }

    @Test public void replacementKeepsAOwnedUntilBReadyThenRecyclesAWithoutDrawingIt() throws Exception {
        Object a = load("a"); complete("a"); await(() -> hasPixels(image().getDrawable()));
        ImageView outgoing = image();
        android.graphics.drawable.Drawable background = outgoing.getBackground();
        com.bumptech.glide.request.Request ownerA = request(outgoing);
        Bitmap pixelsA = bitmap(outgoing.getDrawable());
        load("b"); await(() -> pending.containsKey(url("b")));
        assertSame(outgoing, image());
        assertFalse(ownerA.isCleared());
        Glide.get(activity).clearMemory();
        assertFalse(pixelsA.isRecycled());
        draw();
        complete("b"); await(() -> image() != outgoing);
        assertSame(background, image().getBackground());
        assertTrue(ownerA.isCleared());
        await(pixelsA::isRecycled);
        assertNull(outgoing.getDrawable());
        assertTrue(image().getDrawable() instanceof ThumbnailFades.FadeDrawable);
        draw();
    }

    @Test public void rapidReplacementAndLateCancelledFetchCannotOverwriteC() throws Exception {
        load("a"); complete("a"); await(() -> hasPixels(image().getDrawable()));
        load("b"); await(() -> pending.containsKey(url("b")));
        ImageView pendingView = ReflectionHelpers.getField(hub, "preferenceImage");
        com.bumptech.glide.request.Request ownerB = request(pendingView);
        Object c = load("c"); await(() -> pending.containsKey(url("c")));
        assertTrue(ownerB.isCleared());
        complete("c"); await(() -> image() == pendingView);
        com.bumptech.glide.request.Request ownerC = request(image());
        complete("b"); ShadowLooper.idleMainLooper();
        assertSame(c, ReflectionHelpers.getField(hub, "heroItem"));
        assertSame(ownerC, request(image()));
        assertFalse(ownerC.isCleared());
        draw();
    }

    @Test public void failedReplacementDoesNotUseReleasedAAsErrorOrPlaceholder() throws Exception {
        load("a"); complete("a"); await(() -> hasPixels(image().getDrawable()));
        ImageView outgoing = image();
        Bitmap pixelsA = bitmap(outgoing.getDrawable());
        Object bad = load("bad"); await(() -> pending.containsKey(url("bad")));
        List<Object> candidates = ReflectionHelpers.getField(hub, "heroItems");
        candidates.clear(); candidates.add(bad);
        ImageView incoming = ReflectionHelpers.getField(hub, "preferenceImage");
        assertNull(incoming.getDrawable());
        pending.remove(url("bad")).onLoadFailed(new java.io.IOException("fixture failure"));
        await(() -> !request(incoming).isRunning());
        assertNull(incoming.getDrawable());
        assertSame(outgoing, image());
        assertFalse(pixelsA.isRecycled());
        draw();
    }

    @Test public void closeDuringLoadClearsBothOwnersAndLateDeliveryCannotDraw() throws Exception {
        load("a"); complete("a"); await(() -> hasPixels(image().getDrawable()));
        Bitmap pixelsA = bitmap(image().getDrawable());
        load("b"); await(() -> pending.containsKey(url("b")));
        ImageView incoming = ReflectionHelpers.getField(hub, "preferenceImage");
        com.bumptech.glide.request.Request pendingOwner = request(incoming);
        hub.close();
        assertTrue(pendingOwner.isCleared());
        await(pixelsA::isRecycled);
        complete("b"); ShadowLooper.idleMainLooper();
        assertNull(image().getDrawable());
        assertNull(incoming.getDrawable());
        draw();
        hub = null;
    }

    @Test public void preferenceRiftKeepsOutgoingOwnerUntilSettlement() throws Exception {
        load("a"); complete("a"); await(() -> hasPixels(image().getDrawable()));
        ImageView outgoing = image();
        com.bumptech.glide.request.Request owner = request(outgoing);
        Bitmap pixels = bitmap(outgoing.getDrawable());
        ContentPreference.set(activity, ContentPreference.MEN);
        hub.preparePreference(ContentPreference.MEN);
        NativeContentItem creator = creator("b", "b", url("b"));
        ReflectionHelpers.setField(hub, "discoverCandidates", Collections.singletonList(creator));
        load("b"); complete("b"); awaitOneTask(() -> image() != outgoing);
        android.animation.ValueAnimator morph = ReflectionHelpers.getField(hub, "heroMorph");
        assertNotNull("attached=" + hub.isAttachedToWindow() + " active=" + ReflectionHelpers.getField(hub, "active") + " motion=" + ZeroChillMotion.animationsEnabled(activity), morph);
        assertEquals(1040L, morph.getDuration());
        morph.setCurrentPlayTime(600);
        assertFalse(owner.isCleared());
        assertFalse(pixels.isRecycled());
        draw();
        hub.setActive(false); // Tab exit/background settles before clearing outgoing resources.
        assertTrue(owner.isCleared());
        await(pixels::isRecycled);
        assertEquals(1f, image().getAlpha(), 0f);
        assertNull(outgoing.getDrawable());
        draw();
        ReflectionHelpers.setField(hub, "active", true); // Reentry without unrelated discovery IO.
        ReflectionHelpers.setField(hub, "discoverCandidates",
                Collections.singletonList(creator("c", "c", url("c"))));
        load("c"); complete("c"); await(() -> image() == outgoing);
        draw();
    }

    @Test public void startupCrossfadeOwnsBothFramesAndTabExitReleasesOutgoing() throws Exception {
        Object a = load("a"); complete("a"); await(() -> hasPixels(image().getDrawable()));
        ReflectionHelpers.setField(hub, "startupHero", a);
        ImageView outgoing = image();
        Bitmap pixels = bitmap(outgoing.getDrawable());
        com.bumptech.glide.request.Request owner = request(outgoing);
        load("b"); complete("b"); awaitOneTask(() -> image() != outgoing);
        android.graphics.drawable.TransitionDrawable fade = ReflectionHelpers.getField(hub, "heroCrossfade");
        assertNotNull(fade);
        assertSame(fade, image().getDrawable());
        assertTrue(fade.getDrawable(0) instanceof android.graphics.drawable.BitmapDrawable);
        assertSame(pixels, bitmap(fade.getDrawable(0)));
        assertTrue(fade.isCrossFadeEnabled());
        assertEquals(0f, outgoing.getAlpha(), 0f); // No duplicate outgoing image below the crossfade.
        assertFalse(owner.isCleared());
        assertFalse(pixels.isRecycled());
        draw();
        hub.setActive(false);
        assertTrue(owner.isCleared());
        ShadowLooper.idleMainLooper();
        await(pixels::isRecycled);
        draw();
    }

    @Test public void clearingCompletedPendingPreferenceBeforePostedCommitNeverReinstallsIt() throws Exception {
        load("a"); complete("a"); await(() -> hasPixels(image().getDrawable()));
        ImageView outgoing = image();
        ContentPreference.set(activity, ContentPreference.MEN);
        hub.preparePreference(ContentPreference.MEN);
        ReflectionHelpers.setField(hub, "discoverCandidates",
                Collections.singletonList(creator("b", "b", url("b"))));
        load("b"); complete("b");
        ImageView incoming = ReflectionHelpers.getField(hub, "preferenceImage");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        // Stop between Glide's delivery and the hub's posted commit, not inside a helper.
        while (!request(incoming).isComplete() && System.nanoTime() < deadline) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).runOneTask();
            Thread.sleep(10);
        }
        assertTrue(request(incoming).isComplete());
        Bitmap pixels = bitmap(incoming.getDrawable());
        Glide.with(incoming).clear(incoming);
        ShadowLooper.idleMainLooper();
        await(pixels::isRecycled);
        assertSame(outgoing, image());
        assertNull(incoming.getDrawable());
        assertTrue(ReflectionHelpers.getField(hub, "preferenceHeroPending"));
        draw();
    }

    @Test public void glideClearingEitherCrossfadeOwnerRemovesReferencesBeforeRelease() throws Exception {
        Object a = load("a"); complete("a"); await(() -> hasPixels(image().getDrawable()));
        ReflectionHelpers.setField(hub, "startupHero", a);
        ImageView outgoing = image();
        Bitmap pixelsA = bitmap(outgoing.getDrawable());
        load("b"); complete("b"); awaitOneTask(() -> image() != outgoing);
        assertNotNull(ReflectionHelpers.getField(hub, "heroCrossfade"));
        Glide.with(outgoing).clear(outgoing);
        assertNull(ReflectionHelpers.getField(hub, "heroCrossfade"));
        await(pixelsA::isRecycled);
        assertFalse(image().getDrawable() instanceof android.graphics.drawable.TransitionDrawable);
        draw();
        ReflectionHelpers.setField(hub, "startupHero", ReflectionHelpers.getField(hub, "heroItem"));
        load("c"); complete("c"); awaitOneTask(() -> image() == outgoing);
        assertNotNull(ReflectionHelpers.getField(hub, "heroCrossfade"));
        Glide.with(image()).clear(image());
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);
        assertNull(ReflectionHelpers.getField(hub, "heroCrossfade"));
        assertNull(image().getDrawable());
        draw();
    }

    private Object load(String name) throws Exception {
        NativeContentItem creator = creator(name, name, url(name));
        Class<?> type = Class.forName("com.webapp.crazyshit.OnlyFapHubView$HeroCandidate");
        java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor(
                NativeContentItem.class, String.class, String.class);
        constructor.setAccessible(true);
        Object candidate = constructor.newInstance(creator, url(name), creator.url);
        ((List<Object>) ReflectionHelpers.getField(hub, "heroItems")).add(candidate);
        ReflectionHelpers.setField(hub, "heroItem", candidate);
        java.lang.reflect.Method load = OnlyFapHubView.class.getDeclaredMethod("loadHeroArtwork", type);
        load.setAccessible(true);
        load.invoke(hub, candidate);
        return candidate;
    }
    private String url(String name) { return "https://cdn.example.com/lifecycle-" + name + ".png"; }
    private void complete(String name) throws Exception {
        await(() -> pending.containsKey(url(name)));
        pending.remove(url(name)).onDataReady(new ByteArrayInputStream(png));
    }
    private ImageView image() { return ReflectionHelpers.getField(hub, "heroImage"); }
    private com.bumptech.glide.request.Request request(ImageView view) {
        return new com.bumptech.glide.request.target.DrawableImageViewTarget(view).getRequest();
    }
    private boolean hasPixels(android.graphics.drawable.Drawable drawable) {
        return drawable instanceof android.graphics.drawable.BitmapDrawable
                || drawable instanceof ThumbnailFades.FadeDrawable;
    }
    private Bitmap bitmap(android.graphics.drawable.Drawable drawable) {
        if (drawable instanceof ThumbnailFades.FadeDrawable)
            drawable = ReflectionHelpers.getField(drawable, "content");
        return ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
    }
    private void draw() {
        Bitmap canvas = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888);
        hub.draw(new android.graphics.Canvas(canvas));
        image().draw(new android.graphics.Canvas(canvas));
        canvas.recycle();
    }
    private void awaitOneTask(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).runOneTask();
            Thread.sleep(10);
        }
        assertTrue("Posted handoff did not complete", condition.getAsBoolean());
    }
    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            ShadowLooper.idleMainLooper();
            if (condition.getAsBoolean()) return;
            Thread.sleep(10);
        }
        fail("Glide handoff did not complete");
    }
    private NativeContentItem creator(String title, String key, String image) {
        return new NativeContentItem(NativeContentItem.KIND_CREATOR, title,
                "https://cum.st/creators/onlyfans/" + key, image, "OnlyFap", "", "");
    }
}
