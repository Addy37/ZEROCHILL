package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.transition.Fade;
import android.view.View;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

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
    @Test public void launchPoseMapsToFixedOrientationsOnly() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                GalleryLaunchOrientation.fixedOrientation(0, -1));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
                GalleryLaunchOrientation.fixedOrientation(90, -1));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
                GalleryLaunchOrientation.fixedOrientation(180, -1));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                GalleryLaunchOrientation.fixedOrientation(270, -1));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                GalleryLaunchOrientation.fixedOrientation(-1, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                GalleryLaunchOrientation.fromIntent(new Intent().putExtra(
                        GalleryLaunchOrientation.EXTRA_ORIENTATION, ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR)));
    }

    @Test public void imageAndVideoLaunchUseIdenticalFixedPolicy() {
        for (String kind : new String[]{NativeContentItem.KIND_IMAGE, NativeContentItem.KIND_MEDIA}) {
            for (int orientation : new int[]{ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE}) {
                ActivityController<BunkrGalleryActivity> controller = viewer(kind, orientation);
                BunkrGalleryActivity activity = controller.get();
                assertFalse(activity.isFinishing());
                assertEquals(orientation, activity.getRequestedOrientation());
                // Global creation/resume policy must never overwrite the launch lock.
                PhoneOrientationPolicy.applyBrowsingOrientation(activity);
                assertEquals(orientation, activity.getRequestedOrientation());
                BunkrGalleryPagerAdapter adapter = ReflectionHelpers.getField(activity, "adapter");
                assertNotNull(adapter);
                assertEquals(1, adapter.getItemCount());
                controller.destroy();
                PhoneOrientationPolicy.onActivityDestroyed(activity);
            }
        }
    }

    @Test public void sampledRotationCannotChangeAnOpenedViewerAndBrowsingRestoresPortrait() {
        ActivityController<BunkrGalleryActivity> controller = viewer(
                NativeContentItem.KIND_IMAGE, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        BunkrGalleryActivity activity = controller.get();
        GalleryLaunchOrientation sourceSampler = new GalleryLaunchOrientation(activity);
        sourceSampler.onOrientationChanged(0);
        sourceSampler.onOrientationChanged(90);
        sourceSampler.onOrientationChanged(270);
        PhoneOrientationPolicy.applyBrowsingOrientation(activity);
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, activity.getRequestedOrientation());
        Bundle saved = new Bundle();
        controller.saveInstanceState(saved).destroy();
        PhoneOrientationPolicy.onActivityDestroyed(activity);
        ActivityController<BunkrGalleryActivity> recreated = Robolectric.buildActivity(
                BunkrGalleryActivity.class, activity.getIntent()).create(saved);
        assertFalse(recreated.get().isFinishing());
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, recreated.get().getRequestedOrientation());
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
        ActivityController<BunkrGalleryActivity> controller = viewer(
                NativeContentItem.KIND_IMAGE, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
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

    private ActivityController<BunkrGalleryActivity> viewer(String kind, int orientation) {
        NativeContentItem media = item(kind, "test-media");
        String session = BunkrGallerySessionStore.create("Test", "");
        BunkrGallerySessionStore.replace(session, Collections.singletonList(media), 1, true);
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), BunkrGalleryActivity.class)
                .putExtra(BunkrGalleryActivity.EXTRA_SESSION_ID, session)
                .putExtra(BunkrGalleryActivity.EXTRA_INITIAL_URL, media.url)
                .putExtra(BunkrGalleryActivity.EXTRA_SHARED_ELEMENT_NAME,
                        GalleryMediaTransition.transitionName(media))
                .putExtra(GalleryLaunchOrientation.EXTRA_ORIENTATION, orientation);
        return Robolectric.buildActivity(BunkrGalleryActivity.class, intent).create();
    }

    private NativeContentItem item(String kind, String url) {
        return new NativeContentItem(kind, "Test media", url, "", "", "", "", "");
    }
}
