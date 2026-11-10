package com.webapp.crazyshit;

import android.app.Application;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Exercise real global styling against the new wrapper without starting providers. */
@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {35, 36})
public class CreatorGalleryRevealIntegrationTest {
    @Test public void deferredOledPassesLeaveOuterWindowTransparentAndNormalBrowserBlack() {
        NativeFeedBrowserActivity browser = Robolectric.buildActivity(OnlyFapGalleryActivity.class,
                new Intent().putExtra(CreatorGalleryTransition.EXTRA_RIPPLE_REVEAL, true)).get();
        browser.setTheme(R.style.Theme_ZeroChill_OnlyFapGallery);
        View page = new View(browser);
        browser.setContentView(new CreatorGalleryReveal(browser, page, false));
        View outer = browser.findViewById(android.R.id.content);
        outer.setBackgroundColor(Color.TRANSPARENT);
        // The production coordinator invokes this at 0, 140 and 520ms.
        for (int i = 0; i < 3; i++) ReflectionHelpers.callStaticMethod(OledThemeController.class,
                "apply", ReflectionHelpers.ClassParameter.from(android.app.Activity.class, browser));
        assertEquals(Color.TRANSPARENT, ((ColorDrawable) outer.getBackground()).getColor());
        browser.getIntent().removeExtra(CreatorGalleryTransition.EXTRA_RIPPLE_REVEAL);
        ReflectionHelpers.callStaticMethod(OledThemeController.class, "apply",
                ReflectionHelpers.ClassParameter.from(android.app.Activity.class, browser));
        assertEquals(Color.BLACK, ((ColorDrawable) outer.getBackground()).getColor());
        ((java.util.concurrent.ExecutorService) ReflectionHelpers.getField(browser, "io")).shutdownNow();
    }

    @Test public void immersiveControllerStillStylesOnlyTheToolbarInsideRevealWrapper() {
        NativeFeedBrowserActivity browser = Robolectric.buildActivity(OnlyFapGalleryActivity.class,
                new Intent().putExtra(CreatorGalleryTransition.EXTRA_RIPPLE_REVEAL, true)).get();
        browser.setTheme(R.style.Theme_ZeroChill_OnlyFapGallery);
        LinearLayout page = new LinearLayout(browser);
        page.setOrientation(LinearLayout.VERTICAL);
        TextView toolbar = new TextView(browser);
        toolbar.setText("Gallery toolbar");
        page.addView(toolbar);
        RecyclerView grid = new RecyclerView(browser);
        page.addView(grid);
        ReflectionHelpers.setField(browser, "recycler", grid);
        CreatorGalleryReveal reveal = new CreatorGalleryReveal(browser, page, false);
        browser.setContentView(reveal);
        ReflectionHelpers.callStaticMethod(OledImmersiveUiController.class, "applyBrowser",
                ReflectionHelpers.ClassParameter.from(NativeFeedBrowserActivity.class, browser));
        java.util.Map<?, ?> states = ReflectionHelpers.getStaticField(OledImmersiveUiController.class, "STATES");
        Object state = states.get(browser);
        assertSame(toolbar, ReflectionHelpers.getField(state, "topBar"));
        assertSame(page, ReflectionHelpers.getField(state, "ambientTarget"));
        assertSame(reveal, page.getParent());
        OledImmersiveUiController.detach(browser);
        ((java.util.concurrent.ExecutorService) ReflectionHelpers.getField(browser, "io")).shutdownNow();
    }
    @Test @Config(sdk = 26)
    public void androidEightKeepsOpaqueWindowForPortraitLockedGallery() {
        NativeFeedBrowserActivity browser = Robolectric.buildActivity(NativeFeedBrowserActivity.class,
                new Intent().putExtra(CreatorGalleryTransition.EXTRA_RIPPLE_REVEAL, true)).get();
        assertFalse(CreatorGalleryTransition.ownsRevealWindow(browser));
        assertFalse(CreatorGalleryTransition.shouldAnimate(browser, false));
        ((java.util.concurrent.ExecutorService) ReflectionHelpers.getField(browser, "io")).shutdownNow();
    }

    @Test public void oldNativeBrowserTaskWithRevealFlagNeverClaimsTranslucentWindow() {
        NativeFeedBrowserActivity browser = Robolectric.buildActivity(NativeFeedBrowserActivity.class,
                new Intent().putExtra(CreatorGalleryTransition.EXTRA_RIPPLE_REVEAL, true)).get();
        assertFalse(CreatorGalleryTransition.ownsRevealWindow(browser));
        ((java.util.concurrent.ExecutorService) ReflectionHelpers.getField(browser, "io")).shutdownNow();
    }

    @Test public void manifestHostIsTranslucentBeforeActivityCreation() throws Exception {
        android.app.Application app = org.robolectric.RuntimeEnvironment.getApplication();
        android.content.pm.PackageManager packages = app.getPackageManager();
        android.content.ComponentName component = new android.content.ComponentName(app, OnlyFapGalleryActivity.class);
        android.content.pm.ActivityInfo gallery = packages.getActivityInfo(component, 0);
        android.content.pm.ActivityInfo original = packages.getActivityInfo(
                new android.content.ComponentName(app, NativeFeedBrowserActivity.class), 0);
        android.content.pm.ResolveInfo resolved = packages.resolveActivity(new Intent().setComponent(component), 0);
        assertNotNull(resolved);
        assertEquals(OnlyFapGalleryActivity.class.getName(), resolved.activityInfo.name);
        assertEquals(R.style.Theme_ZeroChill_OnlyFapGallery, gallery.getThemeResource());
        android.content.res.Resources.Theme launchTheme = app.getResources().newTheme();
        launchTheme.applyStyle(gallery.getThemeResource(), true);
        android.content.res.TypedArray attributes = launchTheme.obtainStyledAttributes(new int[]{
                android.R.attr.windowIsTranslucent, android.R.attr.windowBackground,
                android.R.attr.windowAnimationStyle});
        assertTrue(attributes.getBoolean(0, false));
        assertEquals(Color.TRANSPARENT, attributes.getColor(1, Color.BLACK));
        assertEquals(0, attributes.getResourceId(2, 0));
        attributes.recycle();
        android.content.res.Resources.Theme originalTheme = app.getResources().newTheme();
        originalTheme.applyStyle(original.getThemeResource(), true);
        android.content.res.TypedArray originalAttributes = originalTheme.obtainStyledAttributes(
                new int[]{android.R.attr.windowIsTranslucent});
        assertFalse(originalAttributes.getBoolean(0, false));
        originalAttributes.recycle();
        assertFalse(gallery.exported);
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, gallery.screenOrientation);
        assertEquals(original.configChanges, gallery.configChanges);
        assertEquals(original.launchMode, gallery.launchMode);
        assertEquals(original.taskAffinity, gallery.taskAffinity);
        // This resolves real manifest metadata without calling either Activity.onCreate.
        // WindowManager/ActivityRecord source-surface retention still requires a device.
    }

    @Test public void routedCreatorIntentKeepsGalleryDataAndRecreationOrigins() {
        android.app.Activity source = Robolectric.buildActivity(android.app.Activity.class).setup().get();
        Intent intent = NativeFeedBrowserActivity.createCreatorGallery(source, "Fixture creator", "fixture-query",
                "https://fapello.com/fixture/", "existing-session", "existing-cache-key",
                new java.util.ArrayList<>(java.util.Collections.singletonList("Seed name")),
                new java.util.ArrayList<>(java.util.Collections.singletonList("https://example.com/profile")),
                new java.util.ArrayList<>(java.util.Collections.singletonList("https://example.com/image.jpg")));
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        android.os.Bundle expected = new android.os.Bundle(intent.getExtras());
        android.view.MotionEvent up = android.view.MotionEvent.obtain(0, 0,
                android.view.MotionEvent.ACTION_UP, 21f, 632f, 0);
        CreatorGalleryTransition.recordTap(source, up);
        up.recycle();
        NativeContentItem creator = new NativeContentItem(NativeContentItem.KIND_CREATOR,
                "Fixture creator", "https://example.com/profile", "", "", "", "", "", "fixture-query");
        CreatorGalleryTransition.start(source, intent, null, creator);
        Intent launched = org.robolectric.Shadows.shadowOf(source).getNextStartedActivity();
        assertEquals(OnlyFapGalleryActivity.class.getName(), launched.getComponent().getClassName());
        assertTrue((launched.getFlags() & Intent.FLAG_ACTIVITY_NO_ANIMATION) != 0);
        for (String key : expected.keySet()) assertEquals(key, expected.get(key), launched.getExtras().get(key));
        android.os.Parcel parcel = android.os.Parcel.obtain();
        launched.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        Intent restoredIntent = Intent.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        OnlyFapGalleryActivity recreated = Robolectric.buildActivity(OnlyFapGalleryActivity.class, restoredIntent).get();
        assertTrue(CreatorGalleryTransition.ownsRevealWindow(recreated));
        assertFalse(CreatorGalleryTransition.shouldAnimate(recreated, true));
        assertEquals(21f, restoredIntent.getFloatExtra(CreatorGalleryTransition.EXTRA_ORIGIN_SCREEN_X, -1), 0f);
        assertEquals(632f, restoredIntent.getFloatExtra(CreatorGalleryTransition.EXTRA_ORIGIN_SCREEN_Y, -1), 0f);
        assertTrue(recreated instanceof NativeFeedBrowserActivity);
        for (String key : expected.keySet()) assertEquals(key, expected.get(key), restoredIntent.getExtras().get(key));
        ((java.util.concurrent.ExecutorService) ReflectionHelpers.getField(recreated, "io")).shutdownNow();
        source.finish();
    }

    @Test @Config(sdk = 26) public void androidEightLaunchRetainsOriginalOpaqueActivityComponent() {
        android.app.Activity source = Robolectric.buildActivity(android.app.Activity.class).setup().get();
        NativeContentItem creator = new NativeContentItem(NativeContentItem.KIND_CREATOR,
                "Fixture creator", "https://example.com/profile", "", "", "", "", "", "fixture-query");
        Intent intent = NativeFeedBrowserActivity.createCreatorGallery(source, "Fixture creator", "fixture-query");
        CreatorGalleryTransition.start(source, intent, null, creator);
        Intent launched = org.robolectric.Shadows.shadowOf(source).getNextStartedActivity();
        assertEquals(NativeFeedBrowserActivity.class.getName(), launched.getComponent().getClassName());
        NativeFeedBrowserActivity browser = Robolectric.buildActivity(NativeFeedBrowserActivity.class, launched).get();
        assertFalse(CreatorGalleryTransition.ownsRevealWindow(browser));
        assertFalse(CreatorGalleryTransition.shouldAnimate(browser, false));
        assertEquals("fixture-query", launched.getStringExtra(NativeFeedBrowserActivity.EXTRA_BUNKR_CREATOR_QUERY));
        ((java.util.concurrent.ExecutorService) ReflectionHelpers.getField(browser, "io")).shutdownNow();
        source.finish();
    }

    private static final class NoProviderWork extends java.util.concurrent.AbstractExecutorService {
        private boolean shutdown;
        @Override public void execute(Runnable task) { }
        @Override public void shutdown() { shutdown = true; }
        @Override public java.util.List<Runnable> shutdownNow() { shutdown = true; return java.util.Collections.emptyList(); }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long time, java.util.concurrent.TimeUnit unit) { return shutdown; }
    }

    @Test @Config(sdk = {27, 28, 35, 36})
    public void dedicatedHostColdAndRestoredThemesKeepAccentAndTranslucency() {
        AppearanceTheme.setAccent(org.robolectric.RuntimeEnvironment.getApplication(), AppearanceTheme.Accent.PURPLE);
        Intent intent = new Intent().putExtra(CreatorGalleryTransition.EXTRA_RIPPLE_REVEAL, true)
                .putExtra(CreatorGalleryTransition.EXTRA_ORIGIN_SCREEN_X, 23f)
                .putExtra(CreatorGalleryTransition.EXTRA_ORIGIN_SCREEN_Y, 621f);
        org.robolectric.android.controller.ActivityController<OnlyFapGalleryActivity> cold =
                Robolectric.buildActivity(OnlyFapGalleryActivity.class, intent);
        ReflectionHelpers.setField(cold.get(), "io", new NoProviderWork());
        cold.setup(); // No creator query: inspect real host lifecycle without provider IO.
        assertHostTheme(cold.get());
        android.os.Bundle state = new android.os.Bundle();
        cold.saveInstanceState(state).pause().stop().destroy();
        org.robolectric.android.controller.ActivityController<OnlyFapGalleryActivity> restored =
                Robolectric.buildActivity(OnlyFapGalleryActivity.class, new Intent(intent));
        ReflectionHelpers.setField(restored.get(), "io", new NoProviderWork());
        restored.setup(state);
        assertHostTheme(restored.get());
        assertFalse(CreatorGalleryTransition.shouldAnimate(restored.get(), true));
        assertEquals(23f, restored.get().getIntent().getFloatExtra(
                CreatorGalleryTransition.EXTRA_ORIGIN_SCREEN_X, -1), 0f);
        restored.pause().stop().destroy();
    }

    private static void assertHostTheme(OnlyFapGalleryActivity activity) {
        android.content.res.TypedArray attributes = activity.getTheme().obtainStyledAttributes(new int[]{
                android.R.attr.windowIsTranslucent, android.R.attr.windowBackground,
                androidx.appcompat.R.attr.colorPrimary});
        assertTrue(attributes.getBoolean(0, false));
        assertEquals(Color.TRANSPARENT, attributes.getColor(1, Color.BLACK));
        assertEquals(AppearanceTheme.accent(activity), attributes.getColor(2, Color.BLACK));
        attributes.recycle();
        assertTrue(CreatorGalleryTransition.ownsRevealWindow(activity));
    }

    @Test public void actualBrowserBackReversesAndPauseCannotResurrectGallery() {
        org.robolectric.android.controller.ActivityController<OnlyFapGalleryActivity> controller =
                Robolectric.buildActivity(OnlyFapGalleryActivity.class);
        NativeFeedBrowserActivity browser = controller.get();
        browser.setTheme(R.style.Theme_CrazyShit);
        ReflectionHelpers.setField(browser, "io", new NoProviderWork());
        controller.setup(); // Ordinary browser shell; provider tasks intentionally never execute.
        browser.setIntent(new Intent().putExtra(CreatorGalleryTransition.EXTRA_RIPPLE_REVEAL, true)
                .putExtra(CreatorGalleryTransition.EXTRA_ORIGIN_SCREEN_X, 30f)
                .putExtra(CreatorGalleryTransition.EXTRA_ORIGIN_SCREEN_Y, 610f));
        browser.getSharedPreferences("app_prefs", 0).edit()
                .putBoolean("immersive_motion_enabled", true).commit();
        CreatorGalleryReveal reveal = new CreatorGalleryReveal(browser, new View(browser), false);
        ReflectionHelpers.setField(browser, "creatorGalleryReveal", reveal);
        browser.setContentView(reveal);
        reveal.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        reveal.layout(0, 0, 400, 800);
        browser.onBackPressed();
        assertTrue((Boolean) ReflectionHelpers.getField(reveal, "closing"));
        assertFalse(browser.isFinishing());
        browser.onBackPressed(); // Repeated Back does not start another close.
        controller.pause(); // Lifecycle interruption must finish once and hide the tree.
        assertTrue(browser.isFinishing());
        assertEquals(View.INVISIBLE, reveal.getVisibility());
        reveal.cancelAndShow();
        assertEquals(View.INVISIBLE, reveal.getVisibility());
        controller.stop().destroy();
    }

}
