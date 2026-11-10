package com.webapp.crazyshit;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Rect;
import android.transition.ChangeBounds;
import android.transition.ChangeClipBounds;
import android.transition.ChangeImageTransform;
import android.transition.ChangeTransform;
import android.transition.TransitionSet;
import android.graphics.drawable.Drawable;
import android.transition.Fade;
import android.view.View;
import android.view.Window;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;

import androidx.core.view.ViewCompat;

import android.app.SharedElementCallback;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Creator-card to gallery-photo motion. Only artwork is shared, never gallery text
 * or controls. Uses Android's proven platform transitions, not Material's
 * cross-Activity container overlay, which can fail on certain Android devices.
 */
public final class CreatorGalleryTransition {
    static final String EXTRA_NAME = "zerochill_creator_gallery_transition_name";
    static final String EXTRA_SOURCE_RADIUS = "zerochill_creator_gallery_source_radius_dp";
    private static final String PREFIX = "zerochill_creator_gallery_";
    static final long MORPH_DURATION_MS = 330L;
    private static android.graphics.Bitmap pendingArtwork;
    private static String pendingName = "";
    private static long lastLaunch;
    private static WeakReference<Activity> launchActivity = new WeakReference<>(null);
    private static WeakReference<View> latestAnchor = new WeakReference<>(null);

    private CreatorGalleryTransition() {}

    /** Configure only at an eligible OnlyFap tap. Only artwork becomes a shared element. */
    public static void configureSource(Activity activity) {
        Window window = activity.getWindow();
        window.setAllowEnterTransitionOverlap(true);
        window.setAllowReturnTransitionOverlap(true);
        window.setSharedElementExitTransition(morph());
        window.setSharedElementReenterTransition(morph());

        Fade dimOut = new Fade(Fade.OUT);
        dimOut.setDuration(175L);
        window.setExitTransition(dimOut);
        Fade restore = new Fade(Fade.IN);
        restore.setDuration(165L);
        window.setReenterTransition(restore);

        SharedElementCallback callback = new SharedElementCallback() {
            @Override public void onMapSharedElements(List<String> names, Map<String, View> elements) {
                for (String name : new ArrayList<>(names)) {
                    if (!name.startsWith(PREFIX)) continue;
                    View anchor = latestAnchor.get();
                    if (sourceAvailable(name)) {
                        elements.put(name, anchor);
                    } else {
                        elements.remove(name);
                        names.remove(name);
                    }
                }
            }
        };
        activity.setExitSharedElementCallback(callback);
    }

    static void configureGallery(Activity activity) {
        Window window = activity.getWindow();
        if (!ZeroChillMotion.animationsEnabled(activity)) {
            window.setSharedElementEnterTransition(null);
            window.setSharedElementReturnTransition(null);
            window.setEnterTransition(null);
            window.setReturnTransition(null);
            return;
        }
        window.setAllowEnterTransitionOverlap(true);
        window.setAllowReturnTransitionOverlap(true);
        float radius = activity.getIntent().getFloatExtra(EXTRA_SOURCE_RADIUS, 0f)
                * activity.getResources().getDisplayMetrics().density;
        window.setSharedElementEnterTransition(morph(radius));
        window.setSharedElementReturnTransition(morph(radius));

        Fade enter = new Fade(Fade.IN);
        enter.setStartDelay(95L);
        enter.setDuration(175L);
        Fade leave = new Fade(Fade.OUT);
        leave.setDuration(125L);
        window.setEnterTransition(enter);
        window.setReturnTransition(leave);
    }

    static String transitionName(NativeContentItem creator) {
        if (creator == null) return "";
        String key = creator.searchQuery == null || creator.searchQuery.trim().isEmpty()
                ? creator.title : creator.searchQuery;
        if (key == null || key.trim().isEmpty()) return "";
        return PREFIX + Integer.toHexString(key.trim().toLowerCase(Locale.ROOT).hashCode());
    }

    static boolean canUse(Activity activity, View anchor, NativeContentItem creator) {
        return activity != null && anchor instanceof ImageView
                && ((ImageView) anchor).getDrawable() != null
                && !transitionName(creator).isEmpty()
                && activity.getWindow().hasFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
                && anchor.isAttachedToWindow() && anchor.isShown()
                && anchor.getWidth() > 0 && anchor.getHeight() > 0
                && anchor.getGlobalVisibleRect(new Rect())
                && ZeroChillMotion.animationsEnabled(activity);
    }

    static void start(Activity activity, Intent intent, View anchor, NativeContentItem creator) {
        long now = android.os.SystemClock.uptimeMillis();
        if (launchActivity.get() == activity && now - lastLaunch < 700L) return;
        launchActivity = new WeakReference<>(activity);
        lastLaunch = now;
        pendingArtwork = null;
        pendingName = "";
        if (!canUse(activity, anchor, creator)) {
            activity.startActivity(intent);
            return;
        }
        String name = transitionName(creator);
        // Copy only the already rendered image, once per navigation. A Glide bitmap
        // cannot be borrowed across Activities: its owner may clear/recycle on stop.
        pendingArtwork = capture((ImageView) anchor);
        if (pendingArtwork == null) { activity.startActivity(intent); return; }
        pendingName = name;
        configureSource(activity);
        anchor.postDelayed(() -> {
            if (name.equals(pendingName)) { pendingArtwork = null; pendingName = ""; }
        }, 2000L);
        View previous = latestAnchor.get();
        if (previous != null && previous != anchor) ViewCompat.setTransitionName(previous, null);
        ViewCompat.setTransitionName(anchor, name);
        latestAnchor = new WeakReference<>(anchor);
        intent.putExtra(EXTRA_NAME, name);
        intent.putExtra(EXTRA_SOURCE_RADIUS, sourceRadiusDp(anchor));
        try {
            activity.startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(
                    activity, anchor, name).toBundle());
        } catch (RuntimeException failedTransition) {
            // RecyclerView can recycle an image after a tap; opening must still work.
            intent.removeExtra(EXTRA_NAME);
            intent.removeExtra(EXTRA_SOURCE_RADIUS);
            ViewCompat.setTransitionName(anchor, null);
            latestAnchor.clear();
            pendingArtwork = null;
            pendingName = "";
            activity.startActivity(intent);
        }
    }

    /** Consume a bounded, independent source image; no network request or decode. */
    static boolean seedArtwork(ImageView target, String name) {
        if (name == null || !name.equals(pendingName) || pendingArtwork == null) return false;
        target.setImageBitmap(pendingArtwork);
        target.setScaleType(ImageView.ScaleType.CENTER_CROP);
        pendingArtwork = null;
        pendingName = "";
        return true;
    }

    static android.graphics.Bitmap capture(ImageView source) {
        if (source.getWidth() <= 0 || source.getHeight() <= 0) return null;
        float scale = Math.min(1f, 1024f / Math.max(source.getWidth(), source.getHeight()));
        try {
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                    Math.max(1, Math.round(source.getWidth() * scale)),
                    Math.max(1, Math.round(source.getHeight() * scale)),
                    android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            canvas.scale(scale, scale);
            Drawable drawable = source.getDrawable();
            if (drawable instanceof android.graphics.drawable.BitmapDrawable
                    && ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap().getConfig()
                    == android.graphics.Bitmap.Config.HARDWARE) {
                // Normally Glide centerCrop/circleCrop requests use software bitmaps.
                // Safely handle a hardware-backed cache entry without mutating its owner.
                android.graphics.Bitmap software = ((android.graphics.drawable.BitmapDrawable) drawable)
                        .getBitmap().copy(android.graphics.Bitmap.Config.ARGB_8888, false);
                if (software == null) return null;
                android.graphics.drawable.BitmapDrawable copy = new android.graphics.drawable.BitmapDrawable(
                        source.getResources(), software);
                copy.setBounds(drawable.getBounds());
                canvas.translate(source.getPaddingLeft(), source.getPaddingTop());
                canvas.concat(source.getImageMatrix());
                copy.draw(canvas);
                software.recycle();
            } else source.draw(canvas);
            return bitmap;
        } catch (RuntimeException unsupportedDrawable) {
            return null;
        }
    }

    static boolean sourceAvailable(String name) {
        View source = latestAnchor.get();
        if (source == null || name == null || !name.equals(ViewCompat.getTransitionName(source))
                || !source.isAttachedToWindow() || !source.getGlobalVisibleRect(new Rect())) return false;
        // The source Activity's decor is normally invisible while this gallery is on
        // top. Check retained card/tab visibility without mistaking that for removal.
        View root = source.getRootView();
        View current = source;
        while (current != null && current != root) {
            if (current.getVisibility() != View.VISIBLE || current.getAlpha() <= 0f) return false;
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return true;
    }

    static void clearSource(View image) {
        if (image != null) ViewCompat.setTransitionName(image, null);
    }

    static float sourceRadiusDp(View anchor) {
        float density = anchor.getResources().getDisplayMetrics().density;
        View current = anchor;
        // Only source artwork corners, never inferred from image dimensions. The
        // full-bleed OG hero is square while portrait cards and avatar cards round.
        for (int i = 0; current != null && i < 5; i++) {
            if (current instanceof com.google.android.material.card.MaterialCardView) {
                return ((com.google.android.material.card.MaterialCardView) current).getRadius() / density;
            }
            if (current.getBackground() instanceof android.graphics.drawable.GradientDrawable) {
                return ((android.graphics.drawable.GradientDrawable) current.getBackground()).getCornerRadius() / density;
            }
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return 0f;
    }

    private static TransitionSet morph() { return morph(-1f); }

    private static TransitionSet morph(float sourceRadius) {
        TransitionSet transition = new TransitionSet();
        transition.setOrdering(TransitionSet.ORDERING_TOGETHER);
        transition.addTransition(new ChangeBounds());
        transition.addTransition(new ChangeTransform());
        transition.addTransition(new ChangeImageTransform());
        transition.addTransition(new ChangeClipBounds());
        transition.addTransition(new ArtworkCorners(sourceRadius));
        transition.setDuration(MORPH_DURATION_MS);
        transition.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        return transition;
    }
    /** Radius is animated on the image itself, independently of gallery controls. */
    private static final class ArtworkCorners extends android.transition.Transition {
        private static final String RADIUS = "zerochill:creator:radius";
        private final float sourceRadius;
        ArtworkCorners(float sourceRadius) { this.sourceRadius = sourceRadius; }
        @Override public void captureStartValues(android.transition.TransitionValues values) { capture(values); }
        @Override public void captureEndValues(android.transition.TransitionValues values) { capture(values); }
        private void capture(android.transition.TransitionValues values) {
            values.values.put(RADIUS, values.view instanceof GalleryArtwork ? 0f
                    : sourceRadius >= 0f ? sourceRadius
                    : sourceRadiusDp(values.view) * values.view.getResources().getDisplayMetrics().density);
        }
        @Override public android.animation.Animator createAnimator(android.view.ViewGroup root,
                android.transition.TransitionValues start, android.transition.TransitionValues end) {
            if (start == null || end == null) return null;
            View target = end.view;
            float from = (Float) start.values.get(RADIUS), to = (Float) end.values.get(RADIUS);
            android.view.ViewOutlineProvider original = target.getOutlineProvider();
            boolean clipped = target.getClipToOutline();
            final float[] radius = {from};
            target.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius[0]);
                }
            });
            target.setClipToOutline(true);
            android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(from, to);
            animator.addUpdateListener(a -> { radius[0] = (float) a.getAnimatedValue(); target.invalidateOutline(); });
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator a) {
                    target.setOutlineProvider(original); target.setClipToOutline(clipped);
                }
            });
            return animator;
        }
    }

    static final class GalleryArtwork extends androidx.appcompat.widget.AppCompatImageView {
        GalleryArtwork(android.content.Context context) { super(context); }
    }

}
