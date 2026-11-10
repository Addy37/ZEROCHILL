package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

import java.lang.ref.WeakReference;

/** OnlyFap launch polish. Owns no artwork, snapshots, media requests or gallery state. */
final class CreatorGalleryTransition {
    static final String EXTRA_RIPPLE_REVEAL = "zerochill_onlyfap_gallery_ripple_reveal";
    private static long lastLaunch;
    private static WeakReference<Activity> launchActivity = new WeakReference<>(null);

    private CreatorGalleryTransition() {}

    static boolean isScoped(Intent intent) {
        return intent != null && intent.getBooleanExtra(EXTRA_RIPPLE_REVEAL, false);
    }

    static boolean ownsRevealWindow(Activity activity) {
        // Android 8.0 rejects translucent Activities with a requested orientation.
        return android.os.Build.VERSION.SDK_INT >= 27
                && activity instanceof NativeFeedBrowserActivity && isScoped(activity.getIntent());
    }

    static boolean shouldAnimate(Activity activity, boolean recreated) {
        return android.os.Build.VERSION.SDK_INT >= 27 && !recreated && isScoped(activity.getIntent())
                && ZeroChillMotion.animationsEnabled(activity);
    }

    static void start(Activity activity, Intent intent, View anchor, NativeContentItem creator) {
        if (activity == null || creator == null) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (launchActivity.get() == activity && now - lastLaunch < 700L) return;
        launchActivity = new WeakReference<>(activity);
        lastLaunch = now;
        // The whole live gallery is revealed with the same center/timing as the hero
        // ripple. The tapped image is never captured, borrowed, resized or morphed.
        intent.putExtra(EXTRA_RIPPLE_REVEAL, true);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
    }
}
