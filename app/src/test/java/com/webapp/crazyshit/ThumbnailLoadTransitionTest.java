package com.webapp.crazyshit;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.request.transition.Transition;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;

import java.time.Duration;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ThumbnailLoadTransitionTest {
    private org.robolectric.android.controller.ActivityController<Activity> controller;
    private Activity activity;
    private FrameLayout root;
    private ImageView image;

    @Before public void setUp() {
        controller = Robolectric.buildActivity(Activity.class).setup();
        activity = controller.get();
        root = new FrameLayout(activity);
        image = new ImageView(activity);
        root.addView(image, new FrameLayout.LayoutParams(100, 100));
        activity.setContentView(root);
        controller.visible();
        Object info = ReflectionHelpers.getField(activity.getWindow().getDecorView(), "mAttachInfo");
        ReflectionHelpers.setField(info, "mWindowVisibility", View.VISIBLE);
        activity.getWindow().getDecorView().dispatchWindowVisibilityChanged(View.VISIBLE);
        root.layout(0, 0, 100, 100);
        image.layout(0, 0, 100, 100);
    }

    @After public void tearDown() {
        idle(300);
        controller.pause().stop().destroy();
    }

    private void idle(long ms) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }

    private Drawable source() {
        Bitmap bitmap = Bitmap.createBitmap(31, 47, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.CYAN);
        return new BitmapDrawable(activity.getResources(), bitmap);
    }

    private Transition.ViewAdapter target(ImageView image) {
        return new Transition.ViewAdapter() {
            @Override public View getView() { return image; }
            @Override public Drawable getCurrentDrawable() { return image.getDrawable(); }
            @Override public void setDrawable(Drawable drawable) { image.setImageDrawable(drawable); }
        };
    }

    private boolean reveal(ImageView view, Drawable drawable) {
        return ThumbnailFades.factoryForTest(140).build(DataSource.REMOTE, true)
                .transition(drawable, target(view));
    }

    @Test public void cacheMotionAndScrollGate() {
        assertTrue(ThumbnailFades.shouldStart(DataSource.REMOTE, image));
        assertFalse(ThumbnailFades.shouldStart(DataSource.MEMORY_CACHE, image));
        assertFalse(ThumbnailFades.factoryForTest(140).build(DataSource.MEMORY_CACHE, true)
                .transition(source(), target(image)));
        activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
                .putBoolean("immersive_motion_enabled", false).commit();
        assertFalse(ThumbnailFades.shouldStart(DataSource.REMOTE, image));
        activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
                .putBoolean("immersive_motion_enabled", true).commit();
        ReflectionHelpers.callInstanceMethod(root.getViewTreeObserver(), "dispatchOnScrollChanged");
        assertFalse(ThumbnailFades.shouldStart(DataSource.REMOTE, image));
        idle(101);
        assertTrue(ThumbnailFades.shouldStart(DataSource.REMOTE, image));
        image.setVisibility(View.GONE);
        assertFalse(ThumbnailFades.shouldStart(DataSource.REMOTE, image));
    }

    @Test public void dimensionsCompletionAndRecycling() {
        Drawable original = source();
        assertTrue(reveal(image, original));
        ThumbnailFades.FadeDrawable fade = (ThumbnailFades.FadeDrawable) image.getDrawable();
        assertEquals(31, fade.getIntrinsicWidth());
        assertEquals(47, fade.getIntrinsicHeight());
        image.setImageDrawable(source());
        Drawable replacement = image.getDrawable();
        fade.finish();
        assertSame(replacement, image.getDrawable());
        assertTrue(fade.isFinished());
        assertTrue(reveal(image, original));
        fade = (ThumbnailFades.FadeDrawable) image.getDrawable();
        fade.finish();
        assertSame(original, image.getDrawable());
        assertSame(image, original.getCallback());
    }

    @Test public void windowAndDetachedTargetsDoNotAnimate() {
        Object info = ReflectionHelpers.getField(activity.getWindow().getDecorView(), "mAttachInfo");
        ReflectionHelpers.setField(info, "mWindowVisibility", View.GONE);
        root.dispatchWindowVisibilityChanged(View.GONE);
        assertFalse(reveal(image, source()));
        ReflectionHelpers.setField(info, "mWindowVisibility", View.VISIBLE);
        root.dispatchWindowVisibilityChanged(View.VISIBLE);
        assertTrue(reveal(image, source()));
        ThumbnailFades.FadeDrawable fade = (ThumbnailFades.FadeDrawable) image.getDrawable();
        root.removeView(image);
        assertFalse(reveal(image, source()));
        idle(160);
        assertTrue(fade.isFinished());
    }

    @Test public void settlingRecyclerRowsAppearImmediately() {
        root.removeView(image);
        RecyclerView list = new RecyclerView(activity);
        list.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(activity));
        root.addView(list, new FrameLayout.LayoutParams(100, 100));
        list.addView(image);
        list.layout(0, 0, 100, 100);
        image.layout(0, 0, 100, 100);
        ReflectionHelpers.setField(list, "mScrollState", RecyclerView.SCROLL_STATE_SETTLING);
        assertFalse(reveal(image, source()));
        ReflectionHelpers.setField(list, "mScrollState", RecyclerView.SCROLL_STATE_IDLE);
        idle(101);
        assertTrue(reveal(image, source()));
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void imageFadesThenReturnsToItsOriginalDrawable() {
        Drawable original = source();
        assertTrue(reveal(image, original));
        ThumbnailFades.FadeDrawable fade = (ThumbnailFades.FadeDrawable) image.getDrawable();
        fade.setBounds(0, 0, 100, 100);
        Bitmap frame = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        fade.draw(new Canvas(frame));
        assertEquals(0, Color.alpha(frame.getPixel(10, 10)));
        idle(70);
        frame.eraseColor(Color.TRANSPARENT);
        fade.draw(new Canvas(frame));
        int midpointAlpha = Color.alpha(frame.getPixel(10, 10));
        assertTrue("Midpoint opacity " + midpointAlpha, midpointAlpha >= 110 && midpointAlpha <= 145);
        idle(100);
        assertSame(original, image.getDrawable());
        frame.eraseColor(Color.TRANSPARENT);
        original.draw(new Canvas(frame));
        assertEquals(Color.CYAN, frame.getPixel(10, 10));
        assertEquals(1f, image.getAlpha(), 0f);
        assertEquals(1f, image.getScaleX(), 0f);
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test public void admissionCapAndScrollInterrupt() {
        ImageView[] views = new ImageView[5];
        for (int i = 0; i < views.length; i++) {
            views[i] = new ImageView(activity);
            root.addView(views[i], new FrameLayout.LayoutParams(100, 100));
            views[i].layout(0, 0, 100, 100);
        }
        for (int i = 0; i < 4; i++) assertTrue(reveal(views[i], source()));
        assertFalse(reveal(views[4], source()));
        ReflectionHelpers.callInstanceMethod(root.getViewTreeObserver(), "dispatchOnScrollChanged");
        ThumbnailFades.FadeDrawable first = (ThumbnailFades.FadeDrawable) views[0].getDrawable();
        first.setBounds(0, 0, 100, 100);
        Bitmap frame = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        first.draw(new Canvas(frame));
        assertTrue(first.isFinished());
        assertEquals(Color.CYAN, frame.getPixel(10, 10));
        idle(250);
        assertTrue(reveal(views[4], source()));
    }
}
