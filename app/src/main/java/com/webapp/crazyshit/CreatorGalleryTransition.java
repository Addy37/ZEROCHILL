package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.View;

import java.lang.ref.WeakReference;

/** OnlyFap launch polish. Owns no artwork, snapshots, media requests or gallery state. */
final class CreatorGalleryTransition {
    static final String EXTRA_RIPPLE_REVEAL = "zerochill_onlyfap_gallery_ripple_reveal";
    static final String EXTRA_ORIGIN_SCREEN_X = "zerochill_onlyfap_gallery_origin_x";
    static final String EXTRA_ORIGIN_SCREEN_Y = "zerochill_onlyfap_gallery_origin_y";
    static final long TAP_MAX_AGE_MS = 250L;
    private static long lastLaunch;
    private static WeakReference<Activity> launchActivity = new WeakReference<>(null);
    private static WeakReference<Activity> tapActivity = new WeakReference<>(null);
    private static long tapTime;
    private static float tapX;
    private static float tapY;

    private CreatorGalleryTransition() {}

    static void recordTap(Context context, MotionEvent event) {
        if (event == null || event.getActionMasked() != MotionEvent.ACTION_UP) return;
        Activity activity = activity(context);
        if (activity == null || !Float.isFinite(event.getRawX()) || !Float.isFinite(event.getRawY())) return;
        tapActivity = new WeakReference<>(activity);
        tapTime = android.os.SystemClock.uptimeMillis();
        tapX = event.getRawX();
        tapY = event.getRawY();
    }

    static void clearTap(Context context) {
        if (tapActivity.get() == activity(context)) tapActivity.clear();
    }

    private static Activity activity(Context context) {
        for (int i = 0; context != null && i < 12; i++) {
            if (context instanceof Activity) return (Activity) context;
            if (!(context instanceof ContextWrapper)) break;
            Context next = ((ContextWrapper) context).getBaseContext();
            if (next == context) break;
            context = next;
        }
        return null;
    }

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

    static PointF resolveOrigin(View root, Intent intent) {
        int[] location = new int[2];
        root.getLocationOnScreen(location);
        float screenX = intent == null ? Float.NaN : intent.getFloatExtra(EXTRA_ORIGIN_SCREEN_X, Float.NaN);
        float screenY = intent == null ? Float.NaN : intent.getFloatExtra(EXTRA_ORIGIN_SCREEN_Y, Float.NaN);
        if (!Float.isFinite(screenX) || !Float.isFinite(screenY)) {
            return new PointF(root.getWidth() * .5f, root.getHeight() * .47f);
        }
        // Retain the screen point through recreation; clamp if window geometry changed.
        return new PointF(Math.max(0f, Math.min(root.getWidth(), screenX - location[0])),
                Math.max(0f, Math.min(root.getHeight(), screenY - location[1])));
    }

    static void start(Activity activity, Intent intent, View anchor, NativeContentItem creator) {
        if (activity == null || creator == null) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (launchActivity.get() == activity && now - lastLaunch < 700L) { clearTap(activity); return; }
        launchActivity = new WeakReference<>(activity);
        lastLaunch = now;
        PointF origin;
        if (tapActivity.get() == activity && now - tapTime >= 0L && now - tapTime <= TAP_MAX_AGE_MS) {
            origin = new PointF(tapX, tapY);
        } else {
            View fallback = anchor != null && anchor.isAttachedToWindow() && anchor.isShown()
                    && anchor.getWidth() > 0 && anchor.getHeight() > 0 ? anchor : activity.getWindow().getDecorView();
            int[] location = new int[2];
            fallback.getLocationOnScreen(location);
            origin = new PointF(location[0] + fallback.getWidth() * .5f,
                    location[1] + fallback.getHeight() * .5f);
        }
        clearTap(activity);
        intent.putExtra(EXTRA_RIPPLE_REVEAL, true);
        intent.putExtra(EXTRA_ORIGIN_SCREEN_X, origin.x);
        intent.putExtra(EXTRA_ORIGIN_SCREEN_Y, origin.y);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
    }
}
