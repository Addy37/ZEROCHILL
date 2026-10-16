package com.webapp.crazyshit;

import android.app.Activity;

/** Compatibility consumer for an old launch extra. Content readiness never covers the app shell. */
final class ChaosStartupOverlayController {
    static final String EXTRA_STARTUP_HANDOFF = "startup_splash_handoff";
    private ChaosStartupOverlayController() { }
    static void attach(Activity activity) {
        if (activity != null && activity.getIntent() != null)
            activity.getIntent().removeExtra(EXTRA_STARTUP_HANDOFF);
        ChaosStartupHandoff.finish();
    }
}
