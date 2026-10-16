package com.webapp.crazyshit;

import android.content.Intent;

/** Shows manual fullscreen remains available; automatic fullscreen follows the user's rotation setting. */
final class ShowsPlaybackOrientationPolicy {
    static final String EXTRA_MANUAL_LANDSCAPE_FULLSCREEN =
            "manual_landscape_fullscreen";

    private ShowsPlaybackOrientationPolicy() {
    }

    static void requireManualLandscapeFullscreen(Intent intent) {
        if (intent != null) {
            intent.putExtra(EXTRA_MANUAL_LANDSCAPE_FULLSCREEN, true);
        }
    }

    static boolean shouldAutoRotateFromSensor(
            boolean manualLandscapeFullscreen,
            boolean showsOrigin,
            boolean portraitVideo
    ) {
        return !showsOrigin && !portraitVideo;
    }

    static boolean autoRotateEnabled(android.content.Context context) {
        return android.provider.Settings.System.getInt(context.getContentResolver(),
                android.provider.Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
    }

    static boolean shouldForceLandscapeOnFullscreen(
            boolean manualLandscapeFullscreen,
            boolean showsOrigin,
            boolean portraitVideo
    ) {
        return !portraitVideo && (manualLandscapeFullscreen || showsOrigin);
    }
}
