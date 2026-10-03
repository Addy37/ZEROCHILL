package com.webapp.crazyshit;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.SharedElementCallback;
import android.content.Intent;
import android.graphics.Rect;
import android.transition.Fade;
import android.transition.ChangeBounds;
import android.transition.ChangeClipBounds;
import android.transition.ChangeImageTransform;
import android.transition.ChangeTransform;
import android.transition.Transition;
import android.transition.TransitionListenerAdapter;
import android.transition.TransitionSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.PathInterpolator;

import androidx.core.view.ViewCompat;
import java.util.List;
import java.util.Map;

/** Shared-media motion between the creator grid and the full-screen gallery viewer. */
final class GalleryMediaTransition {
    static final long EXPAND_DURATION_MS = 250L;
    static final long CHROME_FADE_MS = 120L;

    private GalleryMediaTransition() {
    }

    static void requestWindowFeature(Activity activity) {
        if (activity == null) return;
        activity.getWindow().requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS);
    }

    static void configureSource(Activity activity) {
        if (activity == null) return;
        Window window = activity.getWindow();
        window.setAllowEnterTransitionOverlap(true);
        window.setAllowReturnTransitionOverlap(true);
        window.setSharedElementExitTransition(sharedElementTransition());
        window.setSharedElementReenterTransition(sharedElementTransition());
        // Source content stays drawn behind the viewer's independent return scrim.
        window.setExitTransition(null);
        window.setReenterTransition(null);
        window.setTransitionBackgroundFadeDuration(0L);
        activity.setExitSharedElementCallback(new SharedElementCallback() {
            @Override public void onMapSharedElements(List<String> names, Map<String, View> elements) {
                // A grid holder can be recycled while paging. Map from the currently
                // visible media identity instead of Android's old View reference.
                for (String name : new java.util.ArrayList<>(names)) {
                    if (!name.startsWith("zerochill_gallery_media_")) continue;
                    View target = findVisibleNamedView(window.getDecorView(), name);
                    if (target == null) { elements.remove(name); names.remove(name); }
                    else elements.put(name, target);
                }
            }
        });
    }

    static View findVisibleNamedView(View view, String name) {
        if (view == null) return null;
        if (name.equals(ViewCompat.getTransitionName(view)) && view.isShown()
                && view.getGlobalVisibleRect(new Rect())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findVisibleNamedView(group.getChildAt(i), name);
                if (found != null) return found;
            }
        }
        return null;
    }

    static void configureReturnSurfaces(Activity activity, View... surfaces) {
        Fade fade = new Fade(Fade.OUT);
        fade.setDuration(EXPAND_DURATION_MS);
        for (View surface : surfaces) if (surface != null) fade.addTarget(surface);
        // Only independent backdrop/chrome siblings leave. The shared ImageView
        // and its pager ancestors remain opaque in alpha throughout the shrink.
        activity.getWindow().setReturnTransition(fade);
    }

    static void configureViewer(Activity activity, Runnable onEnterFinished) {
        if (activity == null) return;
        Window window = activity.getWindow();
        window.setAllowEnterTransitionOverlap(true);
        window.setAllowReturnTransitionOverlap(true);

        Transition enter = sharedElementTransition();
        if (onEnterFinished != null) {
            enter.addListener(new TransitionListenerAdapter() {
                private boolean finished;

                @Override
                public void onTransitionEnd(Transition transition) {
                    finishOnce();
                }

                @Override
                public void onTransitionCancel(Transition transition) {
                    finishOnce();
                }

                private void finishOnce() {
                    if (finished) return;
                    finished = true;
                    onEnterFinished.run();
                }
            });
        }
        window.setSharedElementEnterTransition(enter);
        window.setSharedElementReturnTransition(sharedElementTransition());
        window.setEnterTransition(null);
        window.setReturnTransition(null);
        window.setTransitionBackgroundFadeDuration(0L);
    }

    static boolean canUse(Activity activity, View anchor, NativeContentItem item) {
        return activity != null
                && item != null
                && anchor != null
                && anchor.isAttachedToWindow()
                && anchor.isShown()
                && anchor.getWidth() > 0
                && anchor.getHeight() > 0
                && ZeroChillMotion.animationsEnabled(activity);
    }

    static String transitionName(NativeContentItem item) {
        if (item == null || item.url == null || item.url.trim().isEmpty()) return "";
        long stable = item.url.trim().hashCode() & 0xffffffffL;
        return "zerochill_gallery_media_" + Long.toHexString(stable);
    }

    static void start(Activity activity, Intent intent, View anchor, NativeContentItem item) {
        if (!canUse(activity, anchor, item)) {
            activity.startActivity(intent);
            return;
        }
        String name = transitionName(item);
        if (name.isEmpty()) {
            activity.startActivity(intent);
            return;
        }
        ViewCompat.setTransitionName(anchor, name);
        intent.putExtra(BunkrGalleryActivity.EXTRA_SHARED_ELEMENT_NAME, name);
        ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(
                activity,
                anchor,
                name
        );
        activity.startActivity(intent, options.toBundle());
    }

    static void clearName(View view) {
        if (view != null) ViewCompat.setTransitionName(view, null);
    }

    private static TransitionSet sharedElementTransition() {
        TransitionSet transition = new TransitionSet();
        transition.setOrdering(TransitionSet.ORDERING_TOGETHER);
        transition.addTransition(new ChangeBounds());
        transition.addTransition(new ChangeTransform());
        transition.addTransition(new ChangeImageTransform());
        transition.addTransition(new ChangeClipBounds());
        transition.setDuration(EXPAND_DURATION_MS);
        transition.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        return transition;
    }
}
