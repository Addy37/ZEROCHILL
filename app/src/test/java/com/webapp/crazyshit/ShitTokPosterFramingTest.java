package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.widget.ImageView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** The still poster must have the same framing as the video during category handoff. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokPosterFramingTest {
    @Test public void horizontalClipLoadingPosterFitsWithoutZoomingOrHidingAmbient() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        try {
            ImageView poster = new ImageView(activity);
            ChaosFeedView.configurePosterPresentation(poster, true);
            assertEquals(ImageView.ScaleType.FIT_CENTER, poster.getScaleType());
            assertEquals(Color.TRANSPARENT,
                    ((ColorDrawable) poster.getBackground()).getColor());
        } finally {
            activity.finish();
        }
    }

    @Test public void switchingBackToPortraitRestoresExistingPosterFraming() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        try {
            ImageView poster = new ImageView(activity);
            ChaosFeedView.configurePosterPresentation(poster, true);
            ChaosFeedView.configurePosterPresentation(poster, false);
            assertEquals(ImageView.ScaleType.CENTER_CROP, poster.getScaleType());
            assertEquals(Color.rgb(20, 20, 22),
                    ((ColorDrawable) poster.getBackground()).getColor());
        } finally {
            activity.finish();
        }
    }
}
