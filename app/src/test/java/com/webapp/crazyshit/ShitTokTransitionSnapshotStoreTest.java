package com.webapp.crazyshit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Activity;
import android.graphics.Canvas;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class ShitTokTransitionSnapshotStoreTest {
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
