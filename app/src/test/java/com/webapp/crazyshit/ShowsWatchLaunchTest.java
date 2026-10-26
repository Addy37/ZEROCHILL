package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShowsWatchLaunchTest {
    @Test
    public void transformMapsTappedArtworkIntoPlayerBounds() {
        ShowsWatchLaunch.Transform transform = ShowsWatchLaunch.initialTransform(
                18, 420, 320, 180,
                0, 96, 1080, 608
        );

        assertNotNull(transform);
        assertEquals(320f / 1080f, transform.scaleX, 0.0001f);
        assertEquals(180f / 608f, transform.scaleY, 0.0001f);
        assertEquals(18f, transform.translationX, 0.0001f);
        assertEquals(324f, transform.translationY, 0.0001f);
    }

    @Test
    public void transformRejectsMissingGeometry() {
        assertNull(ShowsWatchLaunch.initialTransform(0, 0, 0, 180, 0, 0, 1080, 608));
        assertNull(ShowsWatchLaunch.initialTransform(0, 0, 320, 180, 0, 0, 0, 608));
    }

    @Test
    public void interruptedTransformNeverVisitsTinyIntermediateSize() {
        ShowsWatchLaunch.Transform source = ShowsWatchLaunch.initialTransform(
                20, 400, 320, 180, 0, 80, 1080, 608);
        float previousWidth = 320f;
        for (float progress : new float[] {0.1f, 0.3f, 0.55f, 0.8f, 1f}) {
            ShowsWatchLaunch.Transform frame = source.at(progress);
            float width = 1080f * frame.scaleX;
            assertTrue(width >= previousWidth);
            assertTrue(width >= 320f && width <= 1080f);
            previousWidth = width;
        }
        ShowsWatchLaunch.Transform reversal = source.at(0.55f);
        assertEquals(1080f * reversal.scaleX,
                1080f * source.at(0.55f).scaleX, 0.01f);
        assertTrue(1080f * source.at(0.4f).scaleX < 1080f * reversal.scaleX);
    }

    @Test
    public void sourceRoutingMatchesShowsProviders() {
        assertEquals(NativeFeedBrowserActivity.SOURCE_XMEGADRIVE,
                ShowsWatchLaunch.sourceForPage("https://www.xmegadrive.com/videos/example"));
        assertEquals(NativeFeedBrowserActivity.SOURCE_EFUKT,
                ShowsWatchLaunch.sourceForPage("https://www.efukt.com/123/title"));
        assertEquals(NativeFeedBrowserActivity.SOURCE_CRAZYSHIT,
                ShowsWatchLaunch.sourceForPage("https://crazyshit.com/cnt/medias/123"));
    }

    @Test
    public void transformedArtworkUsesVisibleClippedBounds() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout frame = new FrameLayout(activity);
        frame.setClipChildren(true);
        ImageView artwork = new ImageView(activity);
        frame.addView(artwork, new FrameLayout.LayoutParams(200, 120));
        activity.setContentView(frame);
        frame.layout(0, 0, 180, 300);
        artwork.layout(0, 50, 200, 170);
        artwork.setTranslationX(20f);
        artwork.setScaleX(0.8f);
        RectF bounds = ShowsWatchLaunch.visibleBounds(artwork);
        assertNotNull(bounds);
        assertTrue(bounds.width() <= 180f);
        assertTrue(bounds.width() >= 130f);
        artwork.setTag(R.id.shows_watch_source_page, "https://example.test/clipped");
        Intent partial = new Intent(activity, VideoDetailActivity.class)
                .putExtra(PlayerActivity.EXTRA_PAGE_URL, "https://example.test/clipped");
        ShowsWatchLaunch.markDeferred(partial, artwork);
        assertEquals(ShowsWatchActivity.class.getName(), partial.getComponent().getClassName());
        assertNull(ShowsWatchLaunch.artwork(partial));
        artwork.setTranslationX(600f);
        assertNull(ShowsWatchLaunch.visibleBounds(artwork));
    }

    @Test
    public void onlyBoundVisibleArtworkChoosesScopedDestination() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout frame = new FrameLayout(activity);
        ImageView artwork = new ImageView(activity);
        frame.addView(artwork, new FrameLayout.LayoutParams(200, 120));
        activity.setContentView(frame);
        frame.layout(0, 0, 360, 600);
        artwork.layout(0, 50, 200, 170);
        String page = "https://example.test/show";
        artwork.setTag(R.id.shows_watch_source_page, page);
        Intent launch = new Intent(activity, VideoDetailActivity.class)
                .putExtra(PlayerActivity.EXTRA_PAGE_URL, page);
        ShowsWatchLaunch.markDeferred(launch, artwork);
        assertEquals(ShowsWatchActivity.class.getName(), launch.getComponent().getClassName());
        assertNotNull(ShowsWatchLaunch.artwork(launch));
        artwork.setTag(R.id.shows_watch_source_page, "https://example.test/recycled");
        Intent recycled = new Intent(activity, VideoDetailActivity.class)
                .putExtra(PlayerActivity.EXTRA_PAGE_URL, page);
        ShowsWatchLaunch.markDeferred(recycled, artwork);
        assertEquals(ShowsWatchActivity.class.getName(), recycled.getComponent().getClassName());
        assertNull(ShowsWatchLaunch.artwork(recycled));
        assertFalse(ShowsWatchLaunch.isScoped(activity));
        ShowsWatchLaunch.release(launch);
    }

    @Test
    public void missingArtworkKeepsScopedFadeWithoutInventedGeometry() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Intent launch = new Intent(activity, VideoDetailActivity.class)
                .putExtra(PlayerActivity.EXTRA_PAGE_URL, "https://example.test/missing");
        ShowsWatchLaunch.markDeferred(launch, null);
        assertEquals(ShowsWatchActivity.class.getName(), launch.getComponent().getClassName());
        assertNull(ShowsWatchLaunch.artwork(launch));
    }
}
