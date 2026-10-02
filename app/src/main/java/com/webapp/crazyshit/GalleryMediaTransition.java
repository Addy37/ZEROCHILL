package com.webapp.crazyshit;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.transition.ChangeBounds;
import android.transition.ChangeClipBounds;
import android.transition.ChangeImageTransform;
import android.transition.ChangeTransform;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionListenerAdapter;
import android.transition.TransitionSet;
import android.view.View;
import android.view.Window;
import android.view.animation.PathInterpolator;

import androidx.core.view.ViewCompat;

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
        window.setExitTransition(new Fade(Fade.OUT).setDuration(90L));
        // Keep the source grid fully visible behind the returning shared media.\n        // A regular-view reenter fade briefly darkens the grid and reads as a black flash.\n        window.setReenterTransition(null);
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

        Fade fadeIn = new Fade(Fade.IN);
        fadeIn.setStartDelay(75L);
        fadeIn.setDuration(145L);
        window.setEnterTransition(fadeIn);
        window.setReturnTransition(new Fade(Fade.OUT).setDuration(110L));
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
