package com.webapp.crazyshit;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.UUID;

import com.google.android.material.card.MaterialCardView;

/** A single retained player surface, animated between live artwork and the watch slot. */
final class ShowsWatchLaunch {
    private static final String EXTRA_TOKEN = "shows_launch_token";
    private static final long ENTER_MS = 300L;
    private static final long EXIT_MS = 400L;
    private static Handoff pending;

    private ShowsWatchLaunch() {}

    static void markDeferred(Intent intent, View sourceView) {
        if (intent == null) return;
        intent.putExtra(VideoDetailActivity.EXTRA_DEFERRED_SHOWS_RESOLVE, true);
        if (Build.VERSION.SDK_INT < 27) return;
        // A scoped host still gives missing/recycled artwork a transparent fade.
        if (intent.getComponent() != null) intent.setClassName(
                intent.getComponent().getPackageName(), ShowsWatchActivity.class.getName());
        if (sourceView == null) return;
        Activity source = activity(sourceView.getContext());
        String page = intent.getStringExtra(PlayerActivity.EXTRA_PAGE_URL);
        SourceGeometry geometry = sourceGeometry(sourceView);
        if (source == null || page == null || geometry == null ||
                !page.equals(sourceView.getTag(R.id.shows_watch_source_page))) return;
        String token = UUID.randomUUID().toString();
        pending = new Handoff(token, source, sourceView, page,
                source.getWindowManager().getDefaultDisplay().getRotation(), snapshot(sourceView),
                cardRadius(sourceView));
        intent.putExtra(EXTRA_TOKEN, token);
        intent.setClass(source, ShowsWatchActivity.class);
    }

    static boolean isScoped(Activity activity) {
        return activity instanceof ShowsWatchActivity;
    }

    static Bitmap artwork(Intent intent) {
        Handoff handoff = handoff(intent);
        return handoff == null ? null : handoff.artwork;
    }

    static void release(Intent intent) {
        Handoff handoff = handoff(intent);
        if (handoff != null) pending = null;
    }

    static boolean animateEntrance(Activity activity, Intent intent, View root, View player,
                                   View details, ImageView poster, View loading) {
        if (!canAnimate(activity, player)) return false;
        Handoff handoff = handoff(intent);
        if (handoff == null || handoff.artwork == null) return false;
        SourceGeometry source = currentBounds(activity, intent);
        if (source == null) return false;
        Transform transform = transform(source.full, player);
        if (transform == null) return false;
        Motion motion = new Motion(root, player, details, poster, loading, transform,
                clipInPlayer(source, player), handoff.cornerRadius, handoff.artwork);
        root.setTag(R.id.shows_watch_motion, motion);
        motion.progress = 0f;
        motion.apply();
        motion.animateTo(1f, ENTER_MS, false, null);
        return true;
    }

    static boolean animateExit(Activity activity, Intent intent, View root, View player,
                               View details, ImageView poster, Runnable finish) {
        if (finish == null || !canAnimate(activity, player)) return false;
        SourceGeometry source = currentBounds(activity, intent);
        Motion motion = root == null ? null : (Motion) root.getTag(R.id.shows_watch_motion);
        if (source == null || motion == null) return false;
        Transform transform = transform(source.full, player);
        if (transform == null) return false;
        Handoff handoff = handoff(intent);
        if (poster != null && handoff != null && handoff.artwork != null) {
            motion.posterStartAlpha = poster.getVisibility() == View.VISIBLE &&
                    poster.getDrawable() != null ? poster.getAlpha() : 0f;
            poster.animate().cancel();
            poster.setImageBitmap(handoff.artwork);
            poster.setVisibility(View.VISIBLE);
            poster.setAlpha(motion.posterStartAlpha);
        }
        motion.captureReverseStart();
        motion.returning = true;
        motion.target = transform;
        motion.targetClip = clipInPlayer(source, player);
        motion.animateTo(0f, EXIT_MS, true, finish);
        return true;
    }

    static void cancel(View root) {
        if (root == null) return;
        Motion motion = (Motion) root.getTag(R.id.shows_watch_motion);
        if (motion != null) {
            motion.cancel();
            motion.player.setClipToOutline(false);
            motion.player.setClipBounds(null);
            motion.player.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            if (motion.poster != null) motion.poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            root.setTag(R.id.shows_watch_motion, null);
        }
    }

    static RectF visibleBounds(View view) {
        if (view == null || !view.isAttachedToWindow() || !view.isShown() ||
                view.getWidth() <= 0 || view.getHeight() <= 0) return null;
        RectF visible = screenBounds(view);
        View parent = view;
        while (parent.getParent() instanceof View) {
            parent = (View) parent.getParent();
            if (parent.getVisibility() != View.VISIBLE || parent.getAlpha() <= 0f) return null;
            if (parent instanceof ViewGroup && ((ViewGroup) parent).getClipChildren()) {
                ViewGroup group = (ViewGroup) parent;
                RectF clip = group.getClipToPadding()
                        ? screenBounds(group, group.getPaddingLeft(), group.getPaddingTop(),
                                group.getWidth() - group.getPaddingRight(),
                                group.getHeight() - group.getPaddingBottom())
                        : screenBounds(parent);
                if (!visible.intersect(clip)) return null;
            }
        }
        return visible.width() >= 24f && visible.height() >= 24f ? visible : null;
    }

    private static SourceGeometry sourceGeometry(View view) {
        RectF visible = visibleBounds(view);
        if (visible == null) return null;
        Matrix matrix = new Matrix();
        view.transformMatrixToGlobal(matrix);
        float[] values = new float[9];
        matrix.getValues(values);
        // A rotated or mirrored view cannot be represented by an axis-aligned player clip.
        if (values[Matrix.MSCALE_X] <= 0f || values[Matrix.MSCALE_Y] <= 0f ||
                Math.abs(values[Matrix.MSKEW_X]) > 0.001f ||
                Math.abs(values[Matrix.MSKEW_Y]) > 0.001f) return null;
        return new SourceGeometry(screenBounds(view), visible);
    }

    private static RectF clipInPlayer(SourceGeometry source, View player) {
        RectF full = source.full, visible = source.visible;
        float width = player.getWidth(), height = player.getHeight();
        return new RectF((visible.left - full.left) * width / full.width(),
                (visible.top - full.top) * height / full.height(),
                (visible.right - full.left) * width / full.width(),
                (visible.bottom - full.top) * height / full.height());
    }

    private static RectF screenBounds(View view) {
        return screenBounds(view, 0, 0, view.getWidth(), view.getHeight());
    }

    private static RectF screenBounds(View view, float left, float top, float right, float bottom) {
        Matrix matrix = new Matrix();
        view.transformMatrixToGlobal(matrix);
        RectF rect = new RectF(left, top, right, bottom);
        matrix.mapRect(rect);
        View root = view.getRootView();
        int[] screen = new int[2];
        int[] window = new int[2];
        root.getLocationOnScreen(screen);
        root.getLocationInWindow(window);
        rect.offset(screen[0] - window[0], screen[1] - window[1]);
        return rect;
    }

    private static SourceGeometry currentBounds(Activity destination, Intent intent) {
        Handoff handoff = handoff(intent);
        if (handoff == null) return null;
        Activity source = handoff.source.get();
        View artwork = handoff.view.get();
        if (source == null || artwork == null || source.isFinishing() || source.isDestroyed() ||
                source.getWindowManager().getDefaultDisplay().getRotation() != handoff.rotation ||
                destination.getWindowManager().getDefaultDisplay().getRotation() != handoff.rotation ||
                !handoff.page.equals(artwork.getTag(R.id.shows_watch_source_page)) ||
                artwork.getRootView() != source.getWindow().getDecorView().getRootView()) return null;
        return sourceGeometry(artwork);
    }

    private static boolean canAnimate(Activity activity, View player) {
        return isScoped(activity) && player != null && player.getWidth() > 0 &&
                player.getHeight() > 0 &&
                activity.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE &&
                ZeroChillMotion.animationsEnabled(activity);
    }

    private static Handoff handoff(Intent intent) {
        return intent == null || pending == null ||
                !pending.token.equals(intent.getStringExtra(EXTRA_TOKEN)) ? null : pending;
    }

    private static Activity activity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    private static Bitmap snapshot(View view) {
        try {
            float size = Math.min(1f, Math.min(512f / view.getWidth(), 512f / view.getHeight()));
            int width = Math.max(1, Math.round(view.getWidth() * size));
            int height = Math.max(1, Math.round(view.getHeight() * size));
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(width / (float) view.getWidth(), height / (float) view.getHeight());
            view.draw(canvas);
            return bitmap;
        } catch (RuntimeException | OutOfMemoryError ignored) {
            return null;
        }
    }

    private static float cardRadius(View view) {
        for (android.view.ViewParent parent = view.getParent(); parent instanceof View;
                parent = parent.getParent()) {
            if (parent instanceof MaterialCardView) return ((MaterialCardView) parent).getRadius();
        }
        return 0f;
    }

    static Transform initialTransform(int sourceLeft, int sourceTop, int sourceWidth, int sourceHeight,
                                      int destinationLeft, int destinationTop, int destinationWidth,
                                      int destinationHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || destinationWidth <= 0 || destinationHeight <= 0) return null;
        return new Transform(sourceWidth / (float) destinationWidth,
                sourceHeight / (float) destinationHeight, sourceLeft - destinationLeft,
                sourceTop - destinationTop);
    }

    private static Transform transform(RectF source, View player) {
        // The same attached surface may still be partway through its entrance animation.
        float sx = player.getScaleX(), sy = player.getScaleY();
        float tx = player.getTranslationX(), ty = player.getTranslationY();
        player.setScaleX(1f);
        player.setScaleY(1f);
        player.setTranslationX(0f);
        player.setTranslationY(0f);
        RectF destination = screenBounds(player);
        player.setScaleX(sx);
        player.setScaleY(sy);
        player.setTranslationX(tx);
        player.setTranslationY(ty);
        if (destination.width() <= 0 || destination.height() <= 0) return null;
        return new Transform(source.width() / destination.width(), source.height() / destination.height(),
                source.left - destination.left, source.top - destination.top);
    }

    static String sourceForPage(String pageUrl) {
        if (WebVideoSourceRepository.isKaoticUrl(pageUrl)) return NativeFeedBrowserActivity.SOURCE_KAOTIC;
        if (BaddieHubRepository.isBaddieHubUrl(pageUrl)) return NativeFeedBrowserActivity.SOURCE_BADDIEHUB;
        if (XMegaDriveRepository.isXMegaDriveUrl(pageUrl)) return NativeFeedBrowserActivity.SOURCE_XMEGADRIVE;
        if (EfuktRepository.isEfuktUrl(pageUrl)) return NativeFeedBrowserActivity.SOURCE_EFUKT;
        return NativeFeedBrowserActivity.SOURCE_CRAZYSHIT;
    }

    static final class Transform {
        final float scaleX, scaleY, translationX, translationY;
        Transform(float scaleX, float scaleY, float translationX, float translationY) {
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.translationX = translationX;
            this.translationY = translationY;
        }
        Transform at(float progress) {
            return new Transform(scaleX + (1f - scaleX) * progress,
                    scaleY + (1f - scaleY) * progress,
                    translationX * (1f - progress),
                    translationY * (1f - progress));
        }
    }

    static final class SourceGeometry {
        final RectF full, visible;
        SourceGeometry(RectF full, RectF visible) {
            this.full = full;
            this.visible = visible;
        }
    }

    private static final class Handoff {
        final String token, page;
        final WeakReference<Activity> source;
        final WeakReference<View> view;
        final int rotation;
        final Bitmap artwork;
        final float cornerRadius;
        Handoff(String token, Activity source, View view, String page, int rotation,
                Bitmap artwork, float cornerRadius) {
            this.token = token;
            this.source = new WeakReference<>(source);
            this.view = new WeakReference<>(view);
            this.page = page;
            this.rotation = rotation;
            this.artwork = artwork;
            this.cornerRadius = cornerRadius;
        }
    }

    private static final class Motion {
        final View root, player, details;
        final View loading;
        final ImageView poster;
        final Bitmap artwork;
        final ColorDrawable scrim = new ColorDrawable(Color.BLACK);
        final float cornerRadius;
        Transform target;
        RectF targetClip;
        Transform reverseStart;
        RectF reverseStartClip;
        float reverseDetailsY, reverseDetailsAlpha;
        int reverseScrimAlpha;
        float progress;
        boolean returning;
        float posterStartAlpha;
        float returnStartProgress;
        ValueAnimator animator;
        Motion(View root, View player, View details, ImageView poster, View loading,
               Transform target, RectF targetClip, float cornerRadius, Bitmap artwork) {
            this.root = root;
            this.player = player;
            this.details = details;
            this.poster = poster;
            this.loading = loading;
            this.artwork = artwork;
            this.target = target;
            this.targetClip = targetClip;
            this.cornerRadius = cornerRadius;
            root.setBackground(scrim);
            player.setPivotX(0f);
            player.setPivotY(0f);
            if (cornerRadius > 0f) player.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    float radius = cornerRadius * (1f - progress) /
                            Math.max(0.01f, view.getScaleX());
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
        }
        void captureReverseStart() {
            returnStartProgress = progress;
            reverseStart = new Transform(player.getScaleX(), player.getScaleY(),
                    player.getTranslationX(), player.getTranslationY());
            Rect clip = player.getClipBounds();
            reverseStartClip = clip == null ?
                    new RectF(0, 0, player.getWidth(), player.getHeight()) : new RectF(clip);
            reverseDetailsY = details == null ? 0f : details.getTranslationY();
            reverseDetailsAlpha = details == null ? 0f : details.getAlpha();
            reverseScrimAlpha = scrim.getAlpha();
        }
        void apply() {
            float t = progress;
            float returned = returning ? Math.max(0f, Math.min(1f,
                    (returnStartProgress - t) / Math.max(0.001f, returnStartProgress))) : 0f;
            Transform frame = returning ? new Transform(
                    lerp(reverseStart.scaleX, target.scaleX, returned),
                    lerp(reverseStart.scaleY, target.scaleY, returned),
                    lerp(reverseStart.translationX, target.translationX, returned),
                    lerp(reverseStart.translationY, target.translationY, returned)) : target.at(t);
            player.setScaleX(frame.scaleX);
            player.setScaleY(frame.scaleY);
            player.setTranslationX(frame.translationX);
            player.setTranslationY(frame.translationY);
            RectF full = new RectF(0, 0, player.getWidth(), player.getHeight());
            RectF from = returning ? reverseStartClip : targetClip;
            RectF to = returning ? targetClip : full;
            float fraction = returning ? returned : t;
            if (!returning && t >= 1f) player.setClipBounds(null);
            else player.setClipBounds(new Rect(
                    Math.round(lerp(from.left, to.left, fraction)),
                    Math.round(lerp(from.top, to.top, fraction)),
                    Math.round(lerp(from.right, to.right, fraction)),
                    Math.round(lerp(from.bottom, to.bottom, fraction))));
            player.setAlpha(1f);
            if (poster != null && artwork != null && poster.getWidth() > 0 && poster.getHeight() > 0) {
                // Cancel the parent's nonuniform card-to-player scale in image space.
                // The artwork remains uniformly cropped in visible screen pixels.
                float visibleWidth = poster.getWidth() * frame.scaleX;
                float visibleHeight = poster.getHeight() * frame.scaleY;
                float uniform = Math.max(visibleWidth / artwork.getWidth(),
                        visibleHeight / artwork.getHeight());
                Matrix matrix = new Matrix();
                matrix.setScale(uniform / frame.scaleX, uniform / frame.scaleY);
                matrix.postTranslate((visibleWidth - artwork.getWidth() * uniform) /
                                (2f * frame.scaleX),
                        (visibleHeight - artwork.getHeight() * uniform) /
                                (2f * frame.scaleY));
                poster.setScaleType(ImageView.ScaleType.MATRIX);
                poster.setImageMatrix(matrix);
                if (returning) {
                    poster.setAlpha(posterStartAlpha + (1f - posterStartAlpha) * returned);
                }
            }
            if (cornerRadius > 0f) {
                player.setClipToOutline(t < 1f);
                player.invalidateOutline();
            }
            if (details != null) {
                float sourceBottomOffset = target.translationY +
                        player.getHeight() * (target.scaleY - 1f);
                details.setTranslationY(returning
                        ? lerp(reverseDetailsY, sourceBottomOffset, returned)
                        : sourceBottomOffset * (1f - t));
                details.setAlpha(returning ? reverseDetailsAlpha * (1f - returned) : t);
            }
            if (loading != null) loading.setAlpha(returning ? 0f : t);
            scrim.setAlpha(returning ? Math.round(reverseScrimAlpha * (1f - returned))
                    : Math.round(255f * t * t));
        }
        private float lerp(float from, float to, float t) {
            return from + (to - from) * t;
        }
        void cancel() {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
        }
        void animateTo(float end, long duration, boolean returning, Runnable complete) {
            cancel();
            float start = progress;
            animator = ValueAnimator.ofFloat(start, end);
            animator.setDuration(Math.max(1L, Math.round(duration * Math.abs(end - start))));
            animator.setInterpolator(returning
                    ? new PathInterpolator(0.15f, 0f, 0.22f, 1f)
                    : new PathInterpolator(0.44f, 0.37f, 0.65f, 1f));
            animator.addUpdateListener(animation -> {
                progress = (float) animation.getAnimatedValue();
                apply();
            });
            animator.addListener(new AnimatorListenerAdapter() {
                boolean cancelled;
                @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
                @Override public void onAnimationEnd(Animator animation) {
                    if (!cancelled && complete != null) complete.run();
                }
            });
            animator.start();
        }
    }
}
