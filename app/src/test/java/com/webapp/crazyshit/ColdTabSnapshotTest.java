package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;

import com.bumptech.glide.request.Request;
import com.bumptech.glide.request.target.DrawableImageViewTarget;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.ReflectionHelpers;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28)
public class ColdTabSnapshotTest {
    // Exercise the real decoder; legacy Robolectric accepts junk bytes as synthetic images.
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test public void missingCorruptAndOversizedFilesFallBack() throws Exception {
        File file = new File(Files.createTempDirectory("snapshot").toFile(), "tab.jpg");
        assertNull(ColdTabSnapshot.read(file));
        Files.write(file.toPath(), new byte[] {1, 2, 3});
        assertNull(ColdTabSnapshot.read(file));
        Files.write(file.toPath(), new byte[262_145]);
        assertNull(ColdTabSnapshot.read(file));
        Bitmap large = Bitmap.createBitmap(513, 1, Bitmap.Config.ARGB_8888);
        ColdTabSnapshot.write(file, large);
        large.recycle();
        assertNull(ColdTabSnapshot.read(file));
    }

    @Test public void compressedSnapshotSurvivesNewReaderAndReplacesOldFile() throws Exception {
        File file = new File(Files.createTempDirectory("snapshot").toFile(), "tab.jpg");
        Bitmap bitmap = Bitmap.createBitmap(64, 128, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.BLUE);
        ColdTabSnapshot.write(file, bitmap);
        Bitmap first = ColdTabSnapshot.read(new File(file.getAbsolutePath()));
        assertNotNull(first);
        assertEquals(128, first.getHeight());
        first.recycle();
        bitmap.eraseColor(Color.RED);
        ColdTabSnapshot.write(file, bitmap);
        bitmap.recycle();
        Bitmap replaced = ColdTabSnapshot.read(file);
        assertNotNull(replaced);
        assertTrue(Color.red(replaced.getPixel(20, 20)) > 200);
        assertTrue(file.length() < 32_768);
        replaced.recycle();
        assertEquals(1, file.getParentFile().list().length);
    }

    @Test public void failedWriteKeepsLastGoodSnapshot() throws Exception {
        File file = new File(Files.createTempDirectory("snapshot").toFile(), "tab.jpg");
        Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        ColdTabSnapshot.write(file, bitmap);
        bitmap.recycle();
        ColdTabSnapshot.write(file, bitmap);
        Bitmap saved = ColdTabSnapshot.read(file);
        assertNotNull(saved);
        saved.recycle();
    }

    @Test public void blurIsBoundedAndWorksBeforeApi31() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout view = new FrameLayout(activity);
        view.setBackgroundColor(Color.WHITE);
        view.layout(0, 0, 1080, 2400);
        Bitmap bitmap = GalleryPreviewBlur.snapshot(view, view::draw);
        assertEquals(128, bitmap.getHeight());
        assertTrue(bitmap.getWidth() <= 128);
        bitmap.recycle();
    }

    @Test public void coldCoverFadesAfterArtworkAndDoesNotReturnOnWarmRecreation() throws Exception {
        Fixture f = new Fixture("test-ready", true);
        Bitmap saved = Bitmap.createBitmap(64, 128, Bitmap.Config.ARGB_8888);
        saved.eraseColor(Color.BLUE);
        ColdTabSnapshot.write(f.file, saved);
        saved.recycle();
        f.install();
        drainIo();
        assertEquals(2, f.root.getChildCount());
        ColdTabSnapshot controller = f.controller;
        controller.onPreDraw();
        ShadowSystemClock.advanceBy(Duration.ofMillis(600));
        controller.onPreDraw();
        ImageView cover = (ImageView) f.root.getChildAt(1);
        assertEquals(450, cover.animate().getDuration());
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
        assertEquals(1, f.root.getChildCount());
        drainIo();
        assertNotNull(ColdTabSnapshot.read(f.file));
        Fixture warm = new Fixture("test-ready", true);
        warm.install();
        drainIo();
        assertEquals(1, warm.root.getChildCount());
    }

    @Test public void noSnapshotKeepsLiveUiAndFailureRemovesExistingCover() throws Exception {
        Fixture first = new Fixture("test-missing", false);
        first.file.delete();
        first.install();
        drainIo();
        assertEquals(1, first.root.getChildCount());
        Fixture failed = new Fixture("test-failure", false);
        Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        ColdTabSnapshot.write(failed.file, bitmap);
        bitmap.recycle();
        failed.install();
        drainIo();
        ColdTabSnapshot controller = failed.controller;
        controller.onPreDraw();
        ShadowSystemClock.advanceBy(Duration.ofSeconds(16));
        controller.onPreDraw();
        assertEquals(1, failed.root.getChildCount());
    }

    @Test public void leavingBeforeHeroReadinessStillCapturesAfterReturning() throws Exception {
        Fixture f = new Fixture("test-return", false);
        f.file.delete();
        f.install();
        drainIo();
        f.controller.onPreDraw();
        f.activity.setContentView(new FrameLayout(f.activity));
        f.ready = true;
        f.activity.setContentView(f.root);
        f.root.layout(0, 0, 400, 800);
        ShadowSystemClock.advanceBy(Duration.ofMillis(200));
        f.controller.onPreDraw();
        ShadowSystemClock.advanceBy(Duration.ofMillis(600));
        f.controller.onPreDraw();
        assertTrue(ReflectionHelpers.getField(f.controller, "done"));
        assertEquals(1, f.root.getChildCount());
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
        drainIo();
        Bitmap captured = ColdTabSnapshot.read(f.file);
        assertNotNull(captured);
        int edge = Math.max(captured.getWidth(), captured.getHeight());
        assertTrue(edge > 128 && edge <= 512);
        captured.recycle();
    }

    @Test public void scrollingFadesCoverWithoutCapturingScrolledContent() throws Exception {
        Fixture f = new Fixture("test-scroll-fade", false);
        Bitmap seed = Bitmap.createBitmap(16, 32, Bitmap.Config.ARGB_8888);
        ColdTabSnapshot.write(f.file, seed);
        seed.recycle();
        byte[] before = Files.readAllBytes(f.file.toPath());
        f.hero.setMinimumHeight(1600);
        f.hero.setLayoutParams(new ScrollView.LayoutParams(-1, 1600));
        f.install();
        drainIo();
        f.live.scrollTo(0, 100);
        assertTrue(f.live.getScrollY() > 0);
        ShadowSystemClock.advanceBy(Duration.ofMillis(200));
        f.controller.onPreDraw();
        ReflectionHelpers.callInstanceMethod(f.controller, "capture");
        ShadowLooper.idleMainLooper(250, TimeUnit.MILLISECONDS);
        drainIo();
        assertEquals(1, f.root.getChildCount());
        assertFalse(ReflectionHelpers.getField(f.controller, "done"));
        assertArrayEquals(before, Files.readAllBytes(f.file.toPath()));
    }

    @Test public void smootherSnapshotResolutionPersists() throws Exception {
        File file = new File(Files.createTempDirectory("snapshot").toFile(), "tab.jpg");
        Bitmap bitmap = Bitmap.createBitmap(256, 512, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.BLUE);
        GalleryPreviewBlur.soften(bitmap, 6);
        ColdTabSnapshot.write(file, bitmap);
        bitmap.recycle();
        Bitmap saved = ColdTabSnapshot.read(file);
        assertNotNull(saved);
        assertEquals(512, saved.getHeight());
        saved.recycle();
    }

    @Test public void usableContentRevealsBeforeSlowShelvesWithoutSavingPartialSnapshot() throws Exception {
        Fixture f = new Fixture("test-progressive", false);
        Bitmap bitmap = Bitmap.createBitmap(16, 32, Bitmap.Config.ARGB_8888);
        ColdTabSnapshot.write(f.file, bitmap);
        bitmap.recycle();
        byte[] previous = Files.readAllBytes(f.file.toPath());
        f.progressive = true;
        f.install();
        drainIo();
        assertEquals(2, f.root.getChildCount());
        f.controller.onPreDraw();
        assertTrue(f.controller.isCoverVisible());
        f.usable = true;
        ShadowSystemClock.advanceBy(Duration.ofMillis(200));
        f.controller.onPreDraw();
        ShadowLooper.idleMainLooper(250, TimeUnit.MILLISECONDS);
        drainIo();
        assertFalse(f.controller.isCoverVisible());
        assertFalse(ReflectionHelpers.getField(f.controller, "done"));
        assertArrayEquals(previous, Files.readAllBytes(f.file.toPath()));
        f.ready = true;
        ShadowSystemClock.advanceBy(Duration.ofMillis(200));
        f.controller.onPreDraw();
        ShadowSystemClock.advanceBy(Duration.ofMillis(600));
        f.controller.onPreDraw();
        assertTrue(ReflectionHelpers.getField(f.controller, "done"));
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
        drainIo();
        Bitmap captured = ColdTabSnapshot.read(f.file);
        assertNotNull(captured);
        assertTrue(captured.getHeight() > 32);
        captured.recycle();
    }

    @Test public void earlyRevealWaitsForACompletedHeroImageRequest() throws Exception {
        Fixture f = new Fixture("test-progressive-hero", false);
        Bitmap bitmap = Bitmap.createBitmap(16, 32, Bitmap.Config.ARGB_8888);
        ColdTabSnapshot.write(f.file, bitmap);
        bitmap.recycle();
        f.progressive = true;
        f.usable = true;
        new DrawableImageViewTarget(f.hero).setRequest(null);
        f.install();
        drainIo();
        f.controller.onPreDraw();
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
        assertTrue(f.controller.isCoverVisible());
        assertFalse(ReflectionHelpers.getField(f.controller, "done"));
    }

    @Test public void lateSnapshotReadCannotCoverAlreadyUsableContent() throws Exception {
        Fixture f = new Fixture("test-progressive-late-read", false);
        Bitmap bitmap = Bitmap.createBitmap(16, 32, Bitmap.Config.ARGB_8888);
        ColdTabSnapshot.write(f.file, bitmap);
        bitmap.recycle();
        f.progressive = true;
        f.usable = true;
        f.install();
        // The read result is posted to the main queue; release before dispatching it.
        f.controller.onPreDraw();
        drainIo();
        assertFalse(f.controller.isCoverVisible());
        assertEquals(1, f.root.getChildCount());
        assertFalse(ReflectionHelpers.getField(f.controller, "done"));
    }

    private static void drainIo() throws Exception {
        ExecutorService io = ReflectionHelpers.getStaticField(ColdTabSnapshot.class, "IO");
        io.submit(() -> { }).get(5, TimeUnit.SECONDS);
        ShadowLooper.idleMainLooper();
    }

    private static final class Fixture {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final FrameLayout root = new FrameLayout(activity);
        final ScrollView live = new ScrollView(activity);
        final ImageView hero = new ImageView(activity);
        final String tab;
        boolean ready;
        boolean usable;
        boolean progressive;
        final File file;
        ColdTabSnapshot controller;
        Fixture(String tab, boolean ready) {
            this.tab = tab;
            this.ready = ready;
            file = new File(activity.getNoBackupFilesDir(), "cold-tab-" + tab + ".jpg");
            root.addView(live, new FrameLayout.LayoutParams(-1, -1));
            live.addView(hero, new ScrollView.LayoutParams(-1, 600));
            hero.setImageDrawable(new android.graphics.drawable.ColorDrawable(Color.BLUE));
            Request request = (Request) Proxy.newProxyInstance(Request.class.getClassLoader(),
                    new Class<?>[] {Request.class}, (proxy, method, args) -> {
                        if (method.getReturnType() == boolean.class) return method.getName().equals("isComplete");
                        return null;
                    });
            new DrawableImageViewTarget(hero).setRequest(request);
        }
        void install() {
            controller = progressive
                    ? ColdTabSnapshot.install(root, live, hero, tab, () -> ready, () -> usable)
                    : ColdTabSnapshot.install(root, live, hero, tab, () -> ready);
            activity.setContentView(root);
            root.measure(android.view.View.MeasureSpec.makeMeasureSpec(400, 1073741824),
                    android.view.View.MeasureSpec.makeMeasureSpec(800, 1073741824));
            root.layout(0, 0, 400, 800);
        }
    }
}
