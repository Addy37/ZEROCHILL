package com.webapp.crazyshit;

import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;

/** Colors only the mascot's accent layers; the horn fill stays near black. */
final class MascotAccent {
    private MascotAccent() { }

    static void apply(ImageView horns, ImageView outline, ImageView tongue, int accent) {
        // The horn vector contains both the dark fill and cyan stroke. A matrix maps
        // those two source colors independently, including for the white accent.
        float[] from = {5f, 9f, 12f};
        float[] cyan = {8f, 146f, 208f};
        float[] to = {Color.red(accent), Color.green(accent), Color.blue(accent)};
        float[] matrix = new float[20];
        for (int channel = 0; channel < 3; channel++) {
            float scale = (to[channel] - from[channel]) / (cyan[channel] - from[channel]);
            matrix[channel * 5 + channel] = scale;
            matrix[channel * 5 + 4] = from[channel] * (1f - scale);
        }
        matrix[18] = 1f;
        horns.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(matrix)));
        outline.setColorFilter(accent, PorterDuff.Mode.SRC_IN);
        tongue.setColorFilter(accent, PorterDuff.Mode.SRC_IN);
    }

    static void applyGlow(View glow, int accent) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        int width = glow.getLayoutParams() == null ? 0 : glow.getLayoutParams().width;
        if (width <= 0 && glow.getParent() instanceof View) {
            View parent = (View) glow.getParent();
            width = parent.getLayoutParams() == null ? 0 : parent.getLayoutParams().width;
        }
        if (width <= 0) width = Math.round(120f * glow.getResources().getDisplayMetrics().density);
        background.setGradientRadius(width * .52f);
        int red = Color.red(accent), green = Color.green(accent), blue = Color.blue(accent);
        background.setColors(new int[]{Color.argb(75, red, green, blue),
                Color.argb(24, red, green, blue), Color.TRANSPARENT});
        glow.setBackground(background);
    }
}
