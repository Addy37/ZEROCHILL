package com.webapp.crazyshit;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.Locale;

/** Presentation shared by ShitTok and Shows, independent of their playback stacks. */
final class FullscreenPlayerStyle {
    static final int TOP_SCRIM_DP = 112;
    static final int BOTTOM_SCRIM_DP = 156;
    static final int ACTION_ROW_DP = 48;
    static final int PROGRESS_ROW_DP = 48;
    static final int HEADER_DP = 58;
    static final long HIDE_DELAY_MS = 2200L;
    static final long FADE_MS = 180L;

    private FullscreenPlayerStyle() {}

    static GradientDrawable topScrim() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { Color.argb(160, 0, 0, 0), Color.argb(78, 0, 0, 0), Color.TRANSPARENT });
    }

    static GradientDrawable bottomScrim() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { Color.TRANSPARENT, Color.argb(78, 0, 0, 0), Color.argb(170, 0, 0, 0) });
    }

    static int bottomInset(View view) {
        return bottomInset(ViewCompat.getRootWindowInsets(view));
    }

    static int bottomInset(WindowInsetsCompat insets) {
        return insets == null ? 0 : insets.getInsets(
                WindowInsetsCompat.Type.mandatorySystemGestures()
                        | WindowInsetsCompat.Type.displayCutout()).bottom;
    }

    static String time(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        return String.format(Locale.US, "%d:%02d", seconds / 60L, seconds % 60L);
    }

    static String remaining(long position, long duration) {
        long bounded = Math.max(0L, Math.min(position, Math.max(0L, duration)));
        return "-" + time(Math.max(0L, duration - bounded));
    }
}
