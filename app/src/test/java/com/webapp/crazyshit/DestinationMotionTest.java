package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.app.Activity;
import android.graphics.Rect;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 35)
public class DestinationMotionTest {
    @Test public void detachedHiddenAndForeignOriginsFallBack() {
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            FrameLayout frame = new FrameLayout(activity);
            View tile = new View(activity);
            frame.addView(tile);
            activity.setContentView(frame);
            controller.visible();
            View root = activity.getWindow().getDecorView();
            Object attachInfo = ReflectionHelpers.getField(root, "mAttachInfo");
            ReflectionHelpers.setField(attachInfo, "mWindowVisibility", View.VISIBLE);
            root.dispatchWindowVisibilityChanged(View.VISIBLE);
            frame.layout(0, 0, 100, 100);
            tile.layout(0, 0, 100, 100);
            assertEquals(new Rect(0, 0, 100, 100), DestinationMotion.visibleOrigin(tile, root));
            assertNull(DestinationMotion.visibleOrigin(tile, frame));
            tile.setVisibility(View.GONE);
            assertNull(DestinationMotion.visibleOrigin(tile, root));
            tile.setVisibility(View.VISIBLE);
            frame.removeView(tile);
            assertNull(DestinationMotion.visibleOrigin(tile, root));
        }
    }

    @Test public void asynchronousLaunchRejectsReboundResult() {
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            View card = new View(controller.get());
            Object clicked = new Object();
            card.setTag(R.id.zerochill_destination_item, clicked);
            assertSame(card, DestinationMotion.boundOrigin(card, clicked));
            card.setTag(R.id.zerochill_destination_item, new Object());
            assertNull(DestinationMotion.boundOrigin(card, clicked));
        }
    }
}
