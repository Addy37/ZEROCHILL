package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.transition.Fade;
import android.view.View;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.viewpager2.widget.ViewPager2;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.Collections;

import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class GalleryViewerLifecycleTest {
    @Test public void imageAndVideoLaunchDelegateToAndroidForEitherAutoRotateSetting() {
        android.content.ContentResolver resolver = RuntimeEnvironment.getApplication().getContentResolver();
        int original = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0);
        try {
            for (int autoRotate : new int[]{0, 1}) {
                Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, autoRotate);
                for (String kind : new String[]{NativeContentItem.KIND_IMAGE, NativeContentItem.KIND_MEDIA}) {
                    ActivityController<BunkrGalleryActivity> controller = viewer(kind);
                    BunkrGalleryActivity activity = controller.get();
                    assertFalse(activity.isFinishing());
                    assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER, activity.getRequestedOrientation());
                    // Global creation/resume policy must not replace USER with a fixed lock or fullSensor.
                    PhoneOrientationPolicy.applyBrowsingOrientation(activity);
                    assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER, activity.getRequestedOrientation());
                    assertEquals(autoRotate, Settings.System.getInt(resolver,
                            Settings.System.ACCELEROMETER_ROTATION, -1));
                    BunkrGalleryPagerAdapter adapter = ReflectionHelpers.getField(activity, "adapter");
                    assertNotNull(adapter);
                    assertEquals(1, adapter.getItemCount());
                    controller.destroy();
                    PhoneOrientationPolicy.onActivityDestroyed(activity);
                }
            }
        } finally {
            Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, original);
        }
    }

    @Test public void configurationChangesRetainViewerMediaAndSharedReturnTarget() {
        for (String kind : new String[]{NativeContentItem.KIND_IMAGE, NativeContentItem.KIND_MEDIA}) {
            ActivityController<BunkrGalleryActivity> controller = viewer(kind);
            BunkrGalleryActivity activity = controller.get();
            ViewPager2 pager = ReflectionHelpers.getField(activity, "pager");
            BunkrGalleryPagerAdapter adapter = ReflectionHelpers.getField(activity, "adapter");
            String transitionName = ReflectionHelpers.getField(activity, "sharedElementName");
            String initialUrl = ReflectionHelpers.getField(activity, "initialUrl");
            for (int orientation : new int[]{Configuration.ORIENTATION_LANDSCAPE,
                    Configuration.ORIENTATION_PORTRAIT}) {
                Configuration config = new Configuration(activity.getResources().getConfiguration());
                config.orientation = orientation;
                activity.onConfigurationChanged(config);
                PhoneOrientationPolicy.applyBrowsingOrientation(activity);
                assertFalse(activity.isFinishing());
                assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER, activity.getRequestedOrientation());
                assertSame(pager, ReflectionHelpers.getField(activity, "pager"));
                assertSame(adapter, ReflectionHelpers.getField(activity, "adapter"));
                assertEquals(0, pager.getCurrentItem());
                assertTrue(BunkrGalleryActivity.canReturnWithSharedElement(
                        transitionName, initialUrl, adapter.itemAt(0)));
            }
            controller.destroy();
            PhoneOrientationPolicy.onActivityDestroyed(activity);
        }
    }

    @Test public void recreationKeepsUserPolicyAndClosingRestoresBrowsingPortrait() {
        ActivityController<BunkrGalleryActivity> controller = viewer(NativeContentItem.KIND_IMAGE);
        BunkrGalleryActivity activity = controller.get();
        Bundle saved = new Bundle();
        controller.saveInstanceState(saved).destroy();
        PhoneOrientationPolicy.onActivityDestroyed(activity);
        ActivityController<BunkrGalleryActivity> recreated = Robolectric.buildActivity(
                BunkrGalleryActivity.class, activity.getIntent()).create(saved);
        assertFalse(recreated.get().isFinishing());
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER, recreated.get().getRequestedOrientation());
        recreated.get().onBackPressed();
        assertTrue(recreated.get().isFinishing());
        recreated.destroy();
        PhoneOrientationPolicy.onActivityDestroyed(recreated.get());
        Activity browser = Robolectric.buildActivity(Activity.class).setup().get();
        PhoneOrientationPolicy.applyBrowsingOrientation(browser);
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, browser.getRequestedOrientation());
        browser.finish();
        PhoneOrientationPolicy.onActivityDestroyed(browser);
    }

    @Test public void viewerOwnsTransparentContainersAndSeparateBlackBackdrop() {
        ActivityController<BunkrGalleryActivity> controller = viewer(NativeContentItem.KIND_IMAGE);
        BunkrGalleryActivity activity = controller.get();
        FrameLayout content = activity.findViewById(android.R.id.content);
        assertNull(content.getBackground());
        assertNull(content.getChildAt(0).getBackground());
        View backdrop = ReflectionHelpers.getField(activity, "viewerBackdrop");
        assertEquals(Color.BLACK, ((ColorDrawable) backdrop.getBackground()).getColor());
        OledThemeController.applySoon(activity);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(
                java.time.Duration.ofMillis(600));
        assertNull(content.getBackground());
        BunkrGalleryPagerAdapter adapter = ReflectionHelpers.getField(activity, "adapter");
        BunkrGalleryPagerAdapter.Holder holder = adapter.onCreateViewHolder(content, 0);
        assertNull(holder.itemView.getBackground());
        assertNull(holder.image.getBackground());
        GalleryMediaTransition.configureReturnSurfaces(activity, backdrop);
        assertTrue(activity.getWindow().getReturnTransition() instanceof Fade);
        assertTrue(activity.getWindow().getReturnTransition().getTargets().contains(backdrop));
        assertFalse(activity.getWindow().getReturnTransition().getTargets().contains(content.getChildAt(0)));
        controller.destroy();
    }

    @Test public void creatorReentryKeepsExistingLayoutManagerAndHolders() {
        NativeFeedBrowserActivity activity = Robolectric.buildActivity(NativeFeedBrowserActivity.class).get();
        ReflectionHelpers.setField(activity, "creatorGalleryColumns", 3);
        RecyclerView grid = new RecyclerView(activity);
        StaggeredGridLayoutManager manager = new StaggeredGridLayoutManager(3,
                StaggeredGridLayoutManager.VERTICAL);
        grid.setLayoutManager(manager);
        ReflectionHelpers.callInstanceMethod(activity, "applyCreatorGalleryLayout",
                ReflectionHelpers.ClassParameter.from(RecyclerView.class, grid));
        assertSame(manager, grid.getLayoutManager());
    }

    @Test public void sourceSessionGrowthOnlyInsertsNewRowsWithoutRebindingExistingMedia() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        BunkrGalleryAdapter adapter = new BunkrGalleryAdapter(activity, new BunkrGalleryAdapter.Listener() {
            @Override public void onOpen(int p, NativeContentItem item, View anchor) { }
            @Override public void onLongPress(NativeContentItem item, View anchor) { }
        });
        NativeContentItem first = item(NativeContentItem.KIND_IMAGE, "first");
        adapter.replace(Collections.singletonList(first), false);
        final int[] changes = {0, 0};
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override public void onChanged() { changes[0]++; }
            @Override public void onItemRangeChanged(int start, int count) { changes[0]++; }
            @Override public void onItemRangeInserted(int start, int count) { changes[1] += count; }
        });
        adapter.append(java.util.Arrays.asList(first, item(NativeContentItem.KIND_IMAGE, "second")), false);
        assertEquals(0, changes[0]);
        assertEquals(1, changes[1]);
        assertSame(first, adapter.snapshot().get(0));
    }

    @Test public void videoReturnRestoresLoadedPosterWithoutAGlideRebind() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        NativeContentItem video = item(NativeContentItem.KIND_MEDIA, "poster-test");
        BunkrGalleryPagerAdapter adapter = new BunkrGalleryPagerAdapter(activity,
                new BunkrGalleryPagerAdapter.Listener() {
                    @Override public void onMediaTap(int p, NativeContentItem item) { }
                    @Override public void onMediaLongPress(int p, NativeContentItem item) { }
                    @Override public void onResolvedImageFailed(int p, NativeContentItem item) { }
                });
        adapter.setInitialSharedElement(video.url, GalleryMediaTransition.transitionName(video));
        adapter.replace(Collections.singletonList(video), Collections.emptyMap());
        ViewPager2 pager = new ViewPager2(activity);
        pager.setAdapter(adapter);
        activity.setContentView(pager);
        pager.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        pager.layout(0, 0, 400, 800);
        RecyclerView list = (RecyclerView) pager.getChildAt(0);
        BunkrGalleryPagerAdapter.Holder holder = (BunkrGalleryPagerAdapter.Holder)
                list.findViewHolderForAdapterPosition(0);
        assertNotNull(holder);
        android.graphics.drawable.BitmapDrawable poster = new android.graphics.drawable.BitmapDrawable(
                activity.getResources(), android.graphics.Bitmap.createBitmap(10, 10,
                android.graphics.Bitmap.Config.ARGB_8888));
        holder.image.setImageDrawable(poster);
        holder.image.setVisibility(View.GONE);
        holder.playerView.setVisibility(View.VISIBLE);
        ReflectionHelpers.setField(adapter, "activeVideoPosition", 0);
        final int[] rebinds = {0};
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override public void onItemRangeChanged(int start, int count) { rebinds[0]++; }
        });
        assertSame(holder.image, adapter.prepareSharedReturn(pager, 0));
        adapter.clearActiveVideo();
        assertEquals(0, rebinds[0]);
        assertSame(poster, holder.image.getDrawable());
        assertEquals(View.VISIBLE, holder.image.getVisibility());
        assertEquals(1f, holder.image.getAlpha(), 0f);
        assertEquals(View.GONE, holder.playerView.getVisibility());
        assertEquals(View.GONE, holder.play.getVisibility());
    }

    private ActivityController<BunkrGalleryActivity> viewer(String kind) {
        NativeContentItem media = item(kind, "test-media");
        String session = BunkrGallerySessionStore.create("Test", "");
        BunkrGallerySessionStore.replace(session, Collections.singletonList(media), 1, true);
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), BunkrGalleryActivity.class)
                .putExtra(BunkrGalleryActivity.EXTRA_SESSION_ID, session)
                .putExtra(BunkrGalleryActivity.EXTRA_INITIAL_URL, media.url)
                .putExtra(BunkrGalleryActivity.EXTRA_SHARED_ELEMENT_NAME,
                        GalleryMediaTransition.transitionName(media));
        return Robolectric.buildActivity(BunkrGalleryActivity.class, intent).create();
    }

    private NativeContentItem item(String kind, String url) {
        return new NativeContentItem(kind, "Test media", url, "", "", "", "", "");
    }
}
