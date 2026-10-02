package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.view.OrientationEventListener;
import android.view.Surface;

/** Samples the phone while browsing; never requests a rotation from a sensor callback. */
final class GalleryLaunchOrientation extends OrientationEventListener {
    static final String EXTRA_ORIENTATION = "gallery_launch_orientation";
    private final Activity activity;
    private int degrees = ORIENTATION_UNKNOWN;

    GalleryLaunchOrientation(Activity activity) {
        super(activity);
        this.activity = activity;
    }

    @Override public void onOrientationChanged(int orientation) {
        degrees = orientation;
    }

    void startSampling() {
        degrees = ORIENTATION_UNKNOWN;
        if (canDetectOrientation()) enable();
    }

    int capture() {
        boolean landscape = activity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int fallback = landscape
                ? (rotation == Surface.ROTATION_270
                    ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        // Tablets already browse in their active display orientation. Phone browsing
        // is portrait-locked, so sample physical pose before opening the viewer.
        return PhoneOrientationPolicy.isPhoneSized(activity)
                ? fixedOrientation(degrees, fallback) : fallback;
    }

    static int fixedOrientation(int degrees, int fallback) {
        if (degrees < 0 || degrees >= 360) return sanitize(fallback);
        if (degrees >= 45 && degrees < 135)
            return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
        if (degrees >= 135 && degrees < 225)
            return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
        if (degrees >= 225 && degrees < 315)
            return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    }

    static int fromIntent(Intent intent) {
        return sanitize(intent == null ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : intent.getIntExtra(EXTRA_ORIENTATION, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
    }

    private static int sanitize(int orientation) {
        switch (orientation) {
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                return orientation;
            default: return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        }
    }
}
