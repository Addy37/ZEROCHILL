package com.webapp.crazyshit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Activity;
import android.app.Application;
import android.graphics.Canvas;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public final class ShitTokTransitionSnapshotStoreTest {
    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void boundedOverlayCapturePreservesPixelsAndLegacyCaptureDimensions() {
        var host = Robolectric.buildActivity(Activity.class).setup();
        View source = new View(host.get()); source.setBackgroundColor(android.graphics.Color.MAGENTA);
        source.layout(0, 0, 1080, 2160);
        android.graphics.Bitmap bounded = ShitTokTransitionSnapshotStore.captureFrame(source, null, 720);
        org.junit.Assert.assertEquals(720, bounded.getWidth());
        org.junit.Assert.assertEquals(1440, bounded.getHeight());
        org.junit.Assert.assertEquals(android.graphics.Color.MAGENTA, bounded.getPixel(360, 720));
        String token = ShitTokTransitionSnapshotStore.beginCapture(host.get(), source, null);
        org.junit.Assert.assertEquals(1080, ShitTokTransitionSnapshotStore.snapshot(token).getWidth());
        org.junit.Assert.assertEquals(2160, ShitTokTransitionSnapshotStore.snapshot(token).getHeight());
        ShitTokTransitionSnapshotStore.remove(token); bounded.recycle();
        host.pause().stop().destroy();
    }

    @Test
    public void failedChildDraw_keepsReturnAndGalleryHandoffFrames() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        View source = new View(activity) {
            @Override protected void onDraw(Canvas canvas) {
                throw new IllegalStateException("unavailable child frame");
            }
        };
        source.layout(0, 0, 300, 600);

        String token = ShitTokTransitionSnapshotStore.beginCapture(activity, source, null);
        assertNotNull(ShitTokTransitionSnapshotStore.snapshot(token));

        View preview = new View(activity);
        preview.layout(0, 0, 300, 600);
        ShitTokTransitionSnapshotStore.captureGalleryPreview(token, preview);
        assertNotNull(ShitTokTransitionSnapshotStore.galleryPreview(token));

        ShitTokTransitionSnapshotStore.remove(token);
        assertNull(ShitTokTransitionSnapshotStore.snapshot(token));
        assertNull(ShitTokTransitionSnapshotStore.galleryPreview(token));
    }
}
