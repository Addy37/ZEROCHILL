package com.webapp.crazyshit;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.SharedElementCallback;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.view.View;
import android.view.Window;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Window-managed launch motion so destination work starts with the launch. */
final class DestinationMotion {
    private static final String EXTRA_ORIGIN = "zerochill.destination.origin";
    private static final String ORIGIN_PREFIX = "zerochill_library_";
    private static final long CONNECTED_MS = 320L;

    private DestinationMotion() {}

    static void startLibrary(Activity activity, Intent intent, int request, View origin) {
        Window window = activity.getWindow();
        Bundle options;
        if (ZeroChillMotion.animationsEnabled(activity)
                && window.hasFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
                && visibleOrigin(origin, window.getDecorView()) != null) {
            String name = ORIGIN_PREFIX + UUID.randomUUID();
            origin.setTransitionName(name);
            intent.putExtra(EXTRA_ORIGIN, name);
            window.setSharedElementExitTransition(boundsTransition());
            window.setSharedElementReenterTransition(boundsTransition());
            window.setExitTransition(null);
            window.setReenterTransition(null);
            activity.setExitSharedElementCallback(new SharedElementCallback() {
                @Override public void onMapSharedElements(List<String> names, Map<String, View> elements) {
                    if (!names.contains(name)) return;
                    View current = GalleryMediaTransition.findVisibleNamedView(window.getDecorView(), name);
                    if (visibleOrigin(current, window.getDecorView()) == null) {
                        names.remove(name);
                        elements.remove(name);
                    } else elements.put(name, current);
                }
            });
            options = ActivityOptions.makeSceneTransitionAnimation(activity, origin, name).toBundle();
        } else {
            options = options(activity, origin);
        }
        if (request >= 0) activity.startActivityForResult(intent, request, options);
        else activity.startActivity(intent, options);
    }

    static void prepareLibraryDestination(Activity activity) {
        if (originName(activity) == null || !ZeroChillMotion.animationsEnabled(activity)) return;
        GalleryMediaTransition.requestWindowFeature(activity);
        Window window = activity.getWindow();
        window.setSharedElementEnterTransition(boundsTransition());
        window.setSharedElementReturnTransition(boundsTransition());
        window.setEnterTransition(new Fade(Fade.IN).setDuration(220L));
        window.setReturnTransition(new Fade(Fade.OUT).setDuration(180L));
        window.setTransitionBackgroundFadeDuration(180L);
    }

    static void attachLibraryHeader(Activity activity, View header) {
        String name = originName(activity);
        if (name != null && activity.getWindow().hasFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)) {
            header.setTransitionName(name);
        }
    }

    static void finishLibrary(Activity activity) {
        if (originName(activity) != null && ZeroChillMotion.animationsEnabled(activity)) {
            activity.finishAfterTransition();
        } else {
            activity.finish();
        }
    }

    private static String originName(Activity activity) {
        Intent intent = activity.getIntent();
        String name = intent == null ? null : intent.getStringExtra(EXTRA_ORIGIN);
        return name != null && name.startsWith(ORIGIN_PREFIX) ? name : null;
    }

    private static ChangeBounds boundsTransition() {
        ChangeBounds transition = new ChangeBounds();
        transition.setResizeClip(true);
        transition.setDuration(CONNECTED_MS);
        transition.setInterpolator(ZeroChillMotion.EASE_OUT);
        return transition;
    }

    static void start(Activity activity, Intent intent, View origin) {
        activity.startActivity(intent, options(activity, origin));
    }

    private static Bundle options(Activity activity, View origin) {
        if (!ZeroChillMotion.animationsEnabled(activity)) {
            return ActivityOptions.makeCustomAnimation(activity, 0, 0).toBundle();
        }
        Rect bounds = visibleOrigin(origin, activity.getWindow().getDecorView());
        if (bounds == null) {
            return ActivityOptions.makeCustomAnimation(activity, R.anim.zerochill_destination_fade,
                    0).toBundle();
        }
        return ActivityOptions.makeClipRevealAnimation(origin, bounds.left, bounds.top,
                bounds.width(), bounds.height()).toBundle();
    }

    static View boundOrigin(View origin, Object item) {
        return origin != null && origin.getTag(R.id.zerochill_destination_item) == item ? origin : null;
    }

    static Rect visibleOrigin(View origin, View windowRoot) {
        if (origin == null || !origin.isAttachedToWindow() || !origin.isShown()
                || origin.getRootView() != windowRoot || origin.getWindowVisibility() != View.VISIBLE
                || origin.getAlpha() <= 0f) return null;
        Rect bounds = new Rect();
        return origin.getLocalVisibleRect(bounds) && !bounds.isEmpty() ? bounds : null;
    }
}
