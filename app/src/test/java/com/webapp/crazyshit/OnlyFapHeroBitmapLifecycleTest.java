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
@Config(application = Application.class, sdk = {29, 35}, qualifiers = "w400dp-h800dp-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class OnlyFapHeroBitmapLifecycleTest {
    private Activity activity;
    private org.robolectric.android.controller.ActivityController<Activity> controller;
    private OnlyFapHubView hub;
    private final java.util.Map<String, DataFetcher.DataCallback<? super InputStream>> pending =
            new java.util.concurrent.ConcurrentHashMap<>();
    private byte[] png;
    private NativeContentItem opened;
    private final java.util.concurrent.atomic.AtomicInteger fetchCount = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger decodeCount = new java.util.concurrent.atomic.AtomicInteger();

    @Before public void setup() throws Exception {
        controller = Robolectric.buildActivity(Activity.class).setup();
        activity = controller.get();
        activity.setTheme(R.style.Theme_CrazyShit);
        Glide.init(activity, new com.bumptech.glide.GlideBuilder()
                .setMemoryCache(new com.bumptech.glide.load.engine.cache.MemoryCacheAdapter())
                .setBitmapPool(new com.bumptech.glide.load.engine.bitmap_recycle.BitmapPoolAdapter()));
        java.util.concurrent.CompletableFuture.runAsync(() -> Glide.get(activity).clearDiskCache()).get(10, TimeUnit.SECONDS);
        Bitmap bitmap = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888);
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
                                        fetchCount.incrementAndGet();
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
        Glide.get(activity).getRegistry().prepend(InputStream.class, Bitmap.class,
                new com.bumptech.glide.load.ResourceDecoder<InputStream, Bitmap>() {
                    @Override public boolean handles(InputStream in, com.bumptech.glide.load.Options o) { return true; }
                    @Override public com.bumptech.glide.load.engine.Resource<Bitmap> decode(InputStream in, int w, int h,
                            com.bumptech.glide.load.Options o) {
                        decodeCount.incrementAndGet();
                        return com.bumptech.glide.load.resource.bitmap.BitmapResource.obtain(
                                android.graphics.BitmapFactory.decodeStream(in), Glide.get(activity).getBitmapPool());
                    }
                });
        Glide.get(activity).getRegistry().prepend(java.nio.ByteBuffer.class, Bitmap.class,
                new com.bumptech.glide.load.ResourceDecoder<java.nio.ByteBuffer, Bitmap>() {
                    @Override public boolean handles(java.nio.ByteBuffer in, com.bumptech.glide.load.Options o) { return true; }
                    @Override public com.bumptech.glide.load.engine.Resource<Bitmap> decode(java.nio.ByteBuffer in, int w, int h, com.bumptech.glide.load.Options o) {
                        decodeCount.incrementAndGet();
                        byte[] data = new byte[in.remaining()]; in.get(data);
                        return com.bumptech.glide.load.resource.bitmap.BitmapResource.obtain(
                                android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length), Glide.get(activity).getBitmapPool());
                    }
                });
        Glide.get(activity).getRegistry().prepend(java.nio.ByteBuffer.class, android.graphics.drawable.BitmapDrawable.class,
                new com.bumptech.glide.load.ResourceDecoder<java.nio.ByteBuffer, android.graphics.drawable.BitmapDrawable>() {
                    @Override public boolean handles(java.nio.ByteBuffer in, com.bumptech.glide.load.Options o) { return true; }
                    @Override public com.bumptech.glide.load.engine.Resource<android.graphics.drawable.BitmapDrawable> decode(java.nio.ByteBuffer in, int w, int h, com.bumptech.glide.load.Options o) {
                        decodeCount.incrementAndGet(); byte[] data = new byte[in.remaining()]; in.get(data);
                        return com.bumptech.glide.load.resource.bitmap.LazyBitmapDrawableResource.obtain(activity.getResources(),
                                com.bumptech.glide.load.resource.bitmap.BitmapResource.obtain(
                                        android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length), Glide.get(activity).getBitmapPool()));
                    }
                });
        Glide.get(activity).getRegistry().prepend(InputStream.class, android.graphics.drawable.BitmapDrawable.class,
                new com.bumptech.glide.load.ResourceDecoder<InputStream, android.graphics.drawable.BitmapDrawable>() {
                    @Override public boolean handles(InputStream in, com.bumptech.glide.load.Options o) { return true; }
                    @Override public com.bumptech.glide.load.engine.Resource<android.graphics.drawable.BitmapDrawable> decode(InputStream in, int w, int h, com.bumptech.glide.load.Options o) {
                        decodeCount.incrementAndGet();
                        return com.bumptech.glide.load.resource.bitmap.LazyBitmapDrawableResource.obtain(activity.getResources(),
                                com.bumptech.glide.load.resource.bitmap.BitmapResource.obtain(
                                        android.graphics.BitmapFactory.decodeStream(in), Glide.get(activity).getBitmapPool()));
                    }
                });
        hub = new OnlyFapHubView(activity, new OnlyFapHubView.Listener() {
            @Override public void onOpenCreator(NativeContentItem creator) { opened = creator; }
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
        complete("b"); awaitOneTask(() -> image() != outgoing);
        assertSame(background, image().getBackground());
        assertFalse(ownerA.isCleared());
        ShadowLooper.idleMainLooper(700, TimeUnit.MILLISECONDS);
        assertTrue(ownerA.isCleared());
        await(pixelsA::isRecycled);
        assertNull(outgoing.getDrawable());
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

    @Test public void delayedRotationKeepsIdentityAndPixelsAtomicAndStartsWithout110msGate() throws Exception {
        Object a = load("a"); complete("a"); await(() -> hasPixels(image().getDrawable()));
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);
        ImageView outgoing = image(); Bitmap pixelsA = bitmap(outgoing.getDrawable());
        Object b = newCandidate("b");
        String hintA = ((android.widget.TextView) ReflectionHelpers.getField(hub, "heroHint")).getText().toString();
        String dotsA = ((View) ReflectionHelpers.getField(hub, "heroDots")).getContentDescription().toString();
        long selectedAt = android.os.SystemClock.uptimeMillis();
        ReflectionHelpers.callInstanceMethod(hub, "showHero", ReflectionHelpers.ClassParameter.from(int.class, 1),
                ReflectionHelpers.ClassParameter.from(boolean.class, true));
        await(() -> pending.containsKey(url("b")));
        System.out.println("Rotation request began after " + (android.os.SystemClock.uptimeMillis() - selectedAt) + "ms");
        assertTrue("Network/decode request must not wait for card motion",
                android.os.SystemClock.uptimeMillis() - selectedAt < 110L);
        // The periodic pool refresh must not invalidate an already staged display request.
        ReflectionHelpers.setField(hub, "heroSelectionLoading", false);
        int generation = ReflectionHelpers.getField(hub, "heroGeneration");
        ReflectionHelpers.callInstanceMethod(hub, "refreshHeroSet");
        ReflectionHelpers.setField(hub, "heroSelectionLoading", true);
        assertEquals(generation, (int) ReflectionHelpers.getField(hub, "heroGeneration"));
        assertSame(b, ReflectionHelpers.getField(hub, "pendingHero"));
        assertSame(a, ReflectionHelpers.getField(hub, "heroItem"));
        assertEquals(0, (int) ReflectionHelpers.getField(hub, "heroIndex"));
        assertEquals("a", ((android.widget.TextView) ReflectionHelpers.getField(hub, "heroTitle")).getText().toString());
        assertEquals(hintA, ((android.widget.TextView) ReflectionHelpers.getField(hub, "heroHint")).getText().toString());
        assertEquals(dotsA, ((View) ReflectionHelpers.getField(hub, "heroDots")).getContentDescription().toString());
        assertEquals(1f, ((View) ReflectionHelpers.getField(hub, "heroCard")).getAlpha(), .001f);
        ReflectionHelpers.callInstanceMethod(hub, "openHero"); assertEquals("a", opened.title);
        assertSame(outgoing, image()); assertFalse(request(outgoing).isCleared()); assertFalse(pixelsA.isRecycled()); draw();
        complete("b"); awaitOneTask(() -> ReflectionHelpers.getField(hub, "heroItem") == b);
        assertEquals(1, (int) ReflectionHelpers.getField(hub, "heroIndex"));
        assertEquals("b", ((android.widget.TextView) ReflectionHelpers.getField(hub, "heroTitle")).getText().toString());
        assertEquals("Hint b", ((android.widget.TextView) ReflectionHelpers.getField(hub, "heroHint")).getText().toString());
        assertEquals("Featured creator 2 of 2. Tap an indicator to switch.", ((View) ReflectionHelpers.getField(hub, "heroDots")).getContentDescription().toString());
        assertEquals("Open b gallery", ((View) ReflectionHelpers.getField(hub, "heroCard")).getContentDescription().toString());
        assertNotSame(outgoing, image());
        assertTrue(ReflectionHelpers.getField(hub, "heroCrossfade") != null
                || ReflectionHelpers.getField(hub, "heroCircularReveal") != null);
        assertFalse(request(outgoing).isCleared()); assertFalse(pixelsA.isRecycled()); draw();
        ShadowLooper.idleMainLooper(700, TimeUnit.MILLISECONDS);
        assertNull(outgoing.getDrawable()); assertTrue(pixelsA.isRecycled()); draw();
        ReflectionHelpers.callInstanceMethod(hub, "openHero"); assertEquals("b", opened.title);
    }

    @Test public void qualificationAndVisibleHandoffShareOneDecodeEvenWithNoResourceCache() throws Exception {
        NativeContentItem creator = creator("qualified", "qualified", url("qualified"));
        OnlyFapHeroPolicy.Artwork choice = new OnlyFapHeroPolicy.Artwork(url("qualified"), creator.url, false);
        List<OnlyFapHeroPolicy.Artwork> choices = new java.util.ArrayList<>();
        ReflectionHelpers.callInstanceMethod(hub, "captureHeroDecodeSize");
        OnlyFapHeroTrace trace = new OnlyFapHeroTrace(); trace.mark("candidate_selected");
        final long startedAt = System.nanoTime();
        java.util.concurrent.atomic.AtomicLong qualificationDone = new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.CompletableFuture<Boolean> qualified = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                boolean accepted = ReflectionHelpers.callInstanceMethod(hub, "qualifyPortraitArtwork",
                        ReflectionHelpers.ClassParameter.from(int.class, 0),
                        ReflectionHelpers.ClassParameter.from(NativeContentItem.class, creator),
                        ReflectionHelpers.ClassParameter.from(List.class, choices),
                        ReflectionHelpers.ClassParameter.from(OnlyFapHeroPolicy.Artwork.class, choice),
                        ReflectionHelpers.ClassParameter.from(OnlyFapHeroTrace.class, trace));
                qualificationDone.set(System.nanoTime()); return accepted;
        });
        complete("qualified"); await(() -> qualified.isDone() && ReflectionHelpers.getField(hub, "heroItem") != null);
        System.out.println("NEW qualificationMs=" + TimeUnit.NANOSECONDS.toMillis(qualificationDone.get() - startedAt)
                + " displayAndCommitMs=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - qualificationDone.get()));
        System.out.println("Qualification/display trace:\n" + AppPerformance.summary());
        assertTrue(qualified.get()); assertEquals(1, decodeCount.get()); assertEquals(1, fetchCount.get());
        assertTrue(request(image()).isComplete()); draw();
        assertTrue(((java.util.Set<?>) ReflectionHelpers.getField(hub, "qualificationTargets")).isEmpty());
        System.out.println("Qualification -> visible: decoded stages=" + decodeCount.get() + " fetches=" + fetchCount.get());
    }

    @Test public void landscapeOriginalCannotPassQualificationByBeingCroppedToPortrait() throws Exception {
        Object a = load("a"); complete("a"); await(() -> hasPixels(image().getDrawable()));
        ImageView current = image();
        Bitmap landscape = Bitmap.createBitmap(540, 360, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream out = new ByteArrayOutputStream(); landscape.compress(Bitmap.CompressFormat.PNG, 100, out);
        landscape.recycle(); png = out.toByteArray();
        NativeContentItem creator = creator("landscape", "landscape", url("landscape"));
        ReflectionHelpers.callInstanceMethod(hub, "captureHeroDecodeSize");
        java.util.concurrent.CompletableFuture<Boolean> result = java.util.concurrent.CompletableFuture.supplyAsync(() ->
                ReflectionHelpers.callInstanceMethod(hub, "qualifyPortraitArtwork",
                        ReflectionHelpers.ClassParameter.from(int.class, 0),
                        ReflectionHelpers.ClassParameter.from(NativeContentItem.class, creator),
                        ReflectionHelpers.ClassParameter.from(List.class, new java.util.ArrayList<OnlyFapHeroPolicy.Artwork>()),
                        ReflectionHelpers.ClassParameter.from(OnlyFapHeroPolicy.Artwork.class,
                                new OnlyFapHeroPolicy.Artwork(url("landscape"), creator.url, false)),
                        ReflectionHelpers.ClassParameter.from(OnlyFapHeroTrace.class, new OnlyFapHeroTrace())));
        complete("landscape"); await(result::isDone); assertFalse(result.get());
        assertSame(a, ReflectionHelpers.getField(hub, "heroItem")); assertSame(current, image());
        assertTrue(((java.util.Set<?>) ReflectionHelpers.getField(hub, "qualificationTargets")).isEmpty()); draw();
    }

    private Object newCandidate(String name) throws Exception {
        NativeContentItem creator = new NativeContentItem(NativeContentItem.KIND_CREATOR, name,
                "https://cum.st/creators/onlyfans/" + name, url(name), "", "", "", "Hint " + name, name);
        Class<?> type = Class.forName("com.webapp.crazyshit.OnlyFapHubView$HeroCandidate");
        java.lang.reflect.Constructor<?> ctor = type.getDeclaredConstructor(NativeContentItem.class, String.class, String.class);
        ctor.setAccessible(true); Object candidate = ctor.newInstance(creator, url(name), creator.url);
        ((List<Object>) ReflectionHelpers.getField(hub, "heroItems")).add(candidate); return candidate;
    }

    private Object load(String name) throws Exception {
        NativeContentItem creator = creator(name, name, url(name));
        Class<?> type = Class.forName("com.webapp.crazyshit.OnlyFapHubView$HeroCandidate");
        java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor(
                NativeContentItem.class, String.class, String.class);
        constructor.setAccessible(true);
        Object candidate = constructor.newInstance(creator, url(name), creator.url);
        ((List<Object>) ReflectionHelpers.getField(hub, "heroItems")).add(candidate);
        ReflectionHelpers.callInstanceMethod(hub, "showHero",
                ReflectionHelpers.ClassParameter.from(int.class, ((List<?>) ReflectionHelpers.getField(hub, "heroItems")).size() - 1),
                ReflectionHelpers.ClassParameter.from(boolean.class, false));
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
