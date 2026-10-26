package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

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
    public void sourceRoutingMatchesShowsProviders() {
        assertEquals(NativeFeedBrowserActivity.SOURCE_XMEGADRIVE,
                ShowsWatchLaunch.sourceForPage("https://www.xmegadrive.com/videos/example"));
        assertEquals(NativeFeedBrowserActivity.SOURCE_EFUKT,
                ShowsWatchLaunch.sourceForPage("https://www.efukt.com/123/title"));
        assertEquals(NativeFeedBrowserActivity.SOURCE_CRAZYSHIT,
                ShowsWatchLaunch.sourceForPage("https://crazyshit.com/cnt/medias/123"));
    }
}
