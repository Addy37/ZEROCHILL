package com.webapp.crazyshit;

import android.content.Intent;

/** Keeps Shows landscape playback portrait until the user explicitly requests fullscreen. */
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
        return !manualLandscapeFullscreen && !showsOrigin && !portraitVideo;
    }

    static boolean shouldForceLandscapeOnFullscreen(
            boolean manualLandscapeFullscreen,
            boolean showsOrigin,
            boolean portraitVideo
    ) {
        return !portraitVideo && (manualLandscapeFullscreen || showsOrigin);
    }
}
