package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.core.view.ViewCompat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {29, 35})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CreatorGalleryTransitionTest {
    private NativeContentItem creator(String query) {
        return new NativeContentItem(NativeContentItem.KIND_CREATOR, "Creator", "https://example.com", "", "", "", "", "", query);
    }

    @Test public void namesFollowCreatorIdentityRatherThanArtwork() {
        assertEquals(CreatorGalleryTransition.transitionName(creator(" Name ")),
                CreatorGalleryTransition.transitionName(creator("name")));
        assertNotEquals(CreatorGalleryTransition.transitionName(creator("name")),
                CreatorGalleryTransition.transitionName(creator("other")));
    }

    @Test public void captureIsBoundedAndIndependentOfGlideOwnedBitmap() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ImageView source = new ImageView(activity);
        Bitmap owned = Bitmap.createBitmap(400, 1600, Bitmap.Config.ARGB_8888);
        owned.eraseColor(Color.BLUE);
        source.setImageBitmap(owned);
        source.setScaleType(ImageView.ScaleType.CENTER_CROP);
        source.layout(0, 0, 400, 1600);
        Bitmap captured = CreatorGalleryTransition.capture(source);
        assertNotNull(captured);
        assertEquals(1024, captured.getHeight());
        assertNotSame(owned, captured);
        owned.recycle();
        assertFalse(captured.isRecycled());
        assertEquals(Color.BLUE, captured.getPixel(100, 500));
        activity.finish();
    }

    @Test public void cornersFollowRealContainerRatherThanImageSize() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout card = new FrameLayout(activity);
        ImageView image = new ImageView(activity);
        card.addView(image);
        image.layout(0, 0, 60, 60);
        assertEquals(0f, CreatorGalleryTransition.sourceRadiusDp(image), 0f);
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setCornerRadius(17f * activity.getResources().getDisplayMetrics().density);
        card.setBackground(shape);
        assertEquals(17f, CreatorGalleryTransition.sourceRadiusDp(image), .01f);
        activity.finish();
    }

    @Test public void reducedMotionAndDetachedAnchorsCannotMorph() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        GalleryMediaTransition.requestWindowFeature(activity);
        ImageView source = new ImageView(activity);
        source.setImageDrawable(new android.graphics.drawable.ColorDrawable(Color.BLUE));
        FrameLayout root = new FrameLayout(activity);
        root.addView(source, new FrameLayout.LayoutParams(200, 200));
        activity.setContentView(root);
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 800);
        activity.getSharedPreferences("app_prefs", 0).edit().putBoolean("immersive_motion_enabled", false).commit();
        assertFalse(CreatorGalleryTransition.canUse(activity, source, creator("name")));
        activity.getSharedPreferences("app_prefs", 0).edit().putBoolean("immersive_motion_enabled", true).commit();
        root.removeView(source);
        assertFalse(CreatorGalleryTransition.canUse(activity, source, creator("name")));
        CreatorGalleryTransition.clearSource(source);
        assertNull(ViewCompat.getTransitionName(source));
        activity.finish();
    }

    @Test public void recreatedGalleryWithoutPendingSourceKeepsNormalHeader() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ImageView target = new ImageView(activity);
        assertFalse(CreatorGalleryTransition.seedArtwork(target, "zerochill_creator_gallery_missing"));
        assertNull(target.getDrawable());
        assertFalse(CreatorGalleryTransition.sourceAvailable("zerochill_creator_gallery_missing"));
        activity.finish();
    }
    private static final String NAME = "zerochill_creator_gallery_fixture";

    private static final class Fixture {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final FrameLayout root = new FrameLayout(activity);
        final ImageView source = new ImageView(activity);
        final CreatorProfileHeader header;
        Fixture(boolean seed) {
            activity.setTheme(R.style.Theme_CrazyShit);
            activity.getSharedPreferences("app_prefs", 0).edit()
                    .putBoolean("immersive_motion_enabled", true).commit();
            source.setImageDrawable(new android.graphics.drawable.ColorDrawable(Color.BLUE));
            ViewCompat.setTransitionName(source, NAME);
            root.addView(source, new FrameLayout.LayoutParams(200, 200));
            Bitmap copy = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
            copy.eraseColor(Color.BLUE);
            org.robolectric.util.ReflectionHelpers.setStaticField(CreatorGalleryTransition.class,
                    "latestAnchor", new java.lang.ref.WeakReference<View>(source));
            org.robolectric.util.ReflectionHelpers.setStaticField(CreatorGalleryTransition.class,
                    "pendingName", seed ? NAME : "");
            org.robolectric.util.ReflectionHelpers.setStaticField(CreatorGalleryTransition.class,
                    "pendingArtwork", seed ? copy : null);
            // Empty query avoids discovery/network; this still builds the actual header.
            header = new CreatorProfileHeader(activity, "Motion fixture", "", "");
            header.setSharedTransitionName(NAME);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(400, 184);
            params.topMargin = 300;
            root.addView(header, params);
            activity.setContentView(root);
            root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 400, 800);
        }
        void close() { root.removeAllViews(); activity.finish(); }
    }

    @Test public void actualHeaderConsumesSourceAndKeepsIndependentImageAfterReveal() {
        Fixture fixture = new Fixture(true);
        ImageView artwork = org.robolectric.util.ReflectionHelpers.getField(fixture.header, "transitionArtwork");
        assertNotNull(artwork.getDrawable());
        assertFalse(CreatorGalleryTransition.seedArtwork(new ImageView(fixture.activity), NAME));
        fixture.source.setImageDrawable(null); // Original owner's clear cannot erase seed.
        org.robolectric.shadows.ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        ImageView banner = org.robolectric.util.ReflectionHelpers.getField(fixture.header, "banner");
        assertTrue(banner.getDrawable() instanceof BitmapDrawable);
        assertEquals(Color.BLUE, ((BitmapDrawable) banner.getDrawable()).getBitmap().getPixel(10, 10));
        View identity = org.robolectric.util.ReflectionHelpers.getField(fixture.header, "identityChrome");
        assertEquals(1f, identity.getAlpha(), .01f);
        fixture.close();
    }

    @Test public void actualHeaderReturnsOnceAndClearedIdentityFallsBack() {
        Fixture fixture = new Fixture(true);
        final int[] calls = {0};
        assertTrue(fixture.header.prepareForSharedReturn(() -> calls[0]++));
        assertTrue(fixture.header.prepareForSharedReturn(() -> calls[0]++));
        assertEquals(1, calls[0]);
        fixture.close();
        Fixture recycled = new Fixture(true);
        CreatorGalleryTransition.clearSource(recycled.source);
        assertFalse(recycled.header.prepareForSharedReturn(() -> fail("Rebound source must fade")));
        recycled.close();
    }

    @Test public void retainedSourceSurvivesStoppedDecorButHiddenCardDoesNot() {
        Fixture fixture = new Fixture(true);
        fixture.activity.getWindow().getDecorView().setVisibility(View.INVISIBLE);
        assertTrue(CreatorGalleryTransition.sourceAvailable(NAME));
        fixture.source.setVisibility(View.INVISIBLE);
        assertFalse(CreatorGalleryTransition.sourceAvailable(NAME));
        fixture.activity.getWindow().getDecorView().setVisibility(View.VISIBLE);
        fixture.close();
    }

    @Test public void recreatedActualHeaderWithoutSeedNeverHidesChromeOrWaitsForReturn() {
        Fixture fixture = new Fixture(false);
        assertFalse(fixture.header.prepareForSharedReturn(() -> fail("No source on recreation")));
        View identity = org.robolectric.util.ReflectionHelpers.getField(fixture.header, "identityChrome");
        assertEquals(1f, identity.getAlpha(), 0f);
        ImageView artwork = org.robolectric.util.ReflectionHelpers.getField(fixture.header, "transitionArtwork");
        assertEquals(View.INVISIBLE, artwork.getVisibility());
        fixture.close();
    }

}
