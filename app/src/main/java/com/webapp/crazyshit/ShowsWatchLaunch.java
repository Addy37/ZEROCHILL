package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.view.View;
import android.view.animation.PathInterpolator;

/** Lightweight Shows launch handoff modeled after a shared-thumbnail watch transition. */
final class ShowsWatchLaunch {
    private static final String EXTRA_SOURCE_LEFT = "shows_launch_left";
    private static final String EXTRA_SOURCE_TOP = "shows_launch_top";
    private static final String EXTRA_SOURCE_WIDTH = "shows_launch_width";
    private static final String EXTRA_SOURCE_HEIGHT = "shows_launch_height";
    private static final long ENTER_MS = 260L;
    private static final long DETAILS_MS = 205L;
    private static final long EXIT_MS = 185L;

    private ShowsWatchLaunch() {
    }

    static void markDeferred(Intent intent, View sourceView) {
        if (intent == null) return;
        intent.putExtra(VideoDetailActivity.EXTRA_DEFERRED_SHOWS_RESOLVE, true);
        if (sourceView == null || sourceView.getWidth() <= 0 || sourceView.getHeight() <= 0) return;
        int[] location = new int[2];
        sourceView.getLocationOnScreen(location);
        intent.putExtra(EXTRA_SOURCE_LEFT, location[0]);
        intent.putExtra(EXTRA_SOURCE_TOP, location[1]);
        intent.putExtra(EXTRA_SOURCE_WIDTH, sourceView.getWidth());
        intent.putExtra(EXTRA_SOURCE_HEIGHT, sourceView.getHeight());
    }

    static boolean animateEntrance(
            Activity activity,
            Intent intent,
            View player,
            View details
    ) {
        if (!canAnimate(activity, intent, player)) return false;
        Transform transform = transform(intent, player);
        if (transform == null) return false;

        player.animate().cancel();
        player.setPivotX(0f);
        player.setPivotY(0f);
        player.setScaleX(transform.scaleX);
        player.setScaleY(transform.scaleY);
        player.setTranslationX(transform.translationX);
        player.setTranslationY(transform.translationY);
        player.setAlpha(1f);
        player.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(ENTER_MS)
                .setInterpolator(curve())
                .start();

        if (details != null) {
            details.animate().cancel();
            details.setAlpha(0f);
            details.setTranslationY(dp(activity, 10));
            details.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(70L)
                    .setDuration(DETAILS_MS)
                    .setInterpolator(curve())
                    .start();
        }
        return true;
    }

    static boolean animateExit(
            Activity activity,
            Intent intent,
            View player,
            View details,
            Runnable finish
    ) {
        if (finish == null || !canAnimate(activity, intent, player)) return false;
        Transform transform = transform(intent, player);
        if (transform == null) return false;

        if (details != null) {
            details.animate().cancel();
            details.animate()
                    .alpha(0f)
                    .translationY(dp(activity, 6))
                    .setDuration(105L)
                    .setInterpolator(curve())
                    .start();
        }
        player.animate().cancel();
        player.setPivotX(0f);
        player.setPivotY(0f);
        player.animate()
                .scaleX(transform.scaleX)
                .scaleY(transform.scaleY)
                .translationX(transform.translationX)
                .translationY(transform.translationY)
                .alpha(0.98f)
                .setDuration(EXIT_MS)
                .setInterpolator(curve())
                .withEndAction(finish)
                .start();
        return true;
    }

    static String sourceForPage(String pageUrl) {
        if (WebVideoSourceRepository.isKaoticUrl(pageUrl)) {
            return NativeFeedBrowserActivity.SOURCE_KAOTIC;
        }
        if (BaddieHubRepository.isBaddieHubUrl(pageUrl)) {
            return NativeFeedBrowserActivity.SOURCE_BADDIEHUB;
        }
        if (XMegaDriveRepository.isXMegaDriveUrl(pageUrl)) {
            return NativeFeedBrowserActivity.SOURCE_XMEGADRIVE;
        }
        if (EfuktRepository.isEfuktUrl(pageUrl)) {
            return NativeFeedBrowserActivity.SOURCE_EFUKT;
        }
        return NativeFeedBrowserActivity.SOURCE_CRAZYSHIT;
    }

    static Transform initialTransform(
            int sourceLeft,
            int sourceTop,
            int sourceWidth,
            int sourceHeight,
            int destinationLeft,
            int destinationTop,
            int destinationWidth,
            int destinationHeight
    ) {
        if (sourceWidth <= 0 || sourceHeight <= 0 ||
                destinationWidth <= 0 || destinationHeight <= 0) return null;
        return new Transform(
                sourceWidth / (float) destinationWidth,
                sourceHeight / (float) destinationHeight,
                sourceLeft - destinationLeft,
                sourceTop - destinationTop
        );
    }

    private static Transform transform(Intent intent, View destination) {
        int width = intent.getIntExtra(EXTRA_SOURCE_WIDTH, 0);
        int height = intent.getIntExtra(EXTRA_SOURCE_HEIGHT, 0);
        if (width <= 0 || height <= 0) return null;
        int[] location = new int[2];
        destination.getLocationOnScreen(location);
        return initialTransform(
                intent.getIntExtra(EXTRA_SOURCE_LEFT, 0),
                intent.getIntExtra(EXTRA_SOURCE_TOP, 0),
                width,
                height,
                location[0],
                location[1],
                destination.getWidth(),
                destination.getHeight()
        );
    }

    private static boolean canAnimate(Activity activity, Intent intent, View player) {
        return activity != null && intent != null && player != null &&
                player.getWidth() > 0 && player.getHeight() > 0 &&
                activity.getResources().getConfiguration().orientation !=
                        Configuration.ORIENTATION_LANDSCAPE &&
                ZeroChillMotion.animationsEnabled(activity) &&
                intent.getIntExtra(EXTRA_SOURCE_WIDTH, 0) > 0 &&
                intent.getIntExtra(EXTRA_SOURCE_HEIGHT, 0) > 0;
    }

    private static PathInterpolator curve() {
        return new PathInterpolator(0.2f, 0f, 0f, 1f);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    static final class Transform {
        final float scaleX;
        final float scaleY;
        final float translationX;
        final float translationY;

        Transform(float scaleX, float scaleY, float translationX, float translationY) {
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.translationX = translationX;
            this.translationY = translationY;
        }
    }
}
