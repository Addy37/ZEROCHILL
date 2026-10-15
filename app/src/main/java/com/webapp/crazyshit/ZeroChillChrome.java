package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Static identity and solid depth for information surfaces. No capture, loops or media work. */
final class ZeroChillChrome {
    private ZeroChillChrome() { }

    static LinearLayout masthead(Context context, String title, String detail) {
        LinearLayout band = new LinearLayout(context);
        band.setOrientation(LinearLayout.VERTICAL);
        int pad = BrowseUi.dp(context, 22);
        band.setPadding(pad, pad, pad, pad);
        band.setBackground(identitySurface(context));
        band.setTag(R.id.zerochill_surface_styled, Boolean.TRUE);
        TextView brand = new TextView(context);
        brand.setText(ZeroChillUi.sectionTitle(context, "ZEROCHILL", 4, 9));
        brand.setTextSize(10);
        brand.setLetterSpacing(.16f);
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        band.addView(brand);
        TextView heading = new TextView(context);
        heading.setText(title);
        heading.setTextColor(UiPalette.TEXT_PRIMARY);
        heading.setTextSize(34);
        heading.setLetterSpacing(-.04f);
        heading.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        heading.setPadding(0, BrowseUi.dp(context, 9), 0, BrowseUi.dp(context, 4));
        band.addView(heading);
        TextView description = new TextView(context);
        description.setText(detail);
        description.setTextColor(UiPalette.TEXT_SECONDARY);
        description.setTextSize(13);
        description.setMaxLines(2);
        band.addView(description);
        return band;
    }

    static void header(android.view.View header) {
        header.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF18202A, 0xFF0B1017, 0xFF000000}));
        header.setTag(R.id.zerochill_surface_styled, Boolean.TRUE);
    }

    static Drawable identitySurface(Context context) { return new IdentitySurface(context); }

    static void section(TextView title) {
        title.setTextSize(23);
        title.setLetterSpacing(-.025f);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        title.setTextColor(UiPalette.TEXT_PRIMARY);
    }

    /** A cut near-black plane, with the existing mascot etched into the far edge. */
    private static final class IdentitySurface extends Drawable {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        final RectF bounds = new RectF();
        final float density;
        final Drawable mascot;
        Shader tone;
        IdentitySurface(Context context) {
            density = context.getResources().getDisplayMetrics().density;
            mascot = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_zerochill_devil).mutate();
            mascot.setAlpha(20);
        }
        @Override protected void onBoundsChange(Rect b) {
            bounds.set(b);
            tone = new LinearGradient(b.left, b.top, b.right, b.bottom,
                    new int[]{0xFF252D37, 0xFF11161D, 0xFF06090D}, null, Shader.TileMode.CLAMP);
        }
        @Override public void draw(Canvas canvas) {
            int save = canvas.save();
            path.rewind();
            path.addRoundRect(bounds, 24*density, 24*density, Path.Direction.CW);
            canvas.clipPath(path);
            paint.setShader(tone);
            canvas.drawRect(bounds, paint);
            paint.setShader(null);
            // A stationary light plane defines the identity area, not every content card.
            path.rewind();
            path.moveTo(bounds.right*.63f, bounds.top);
            path.lineTo(bounds.right, bounds.top);
            path.lineTo(bounds.right, bounds.bottom);
            path.lineTo(bounds.right*.4f, bounds.bottom);
            path.close();
            paint.setColor(0x0CFFFFFF);
            canvas.drawPath(path, paint);
            int size = (int)Math.min(bounds.width()*.46f, bounds.height()*1.25f);
            mascot.setBounds((int)bounds.right-size, (int)bounds.centerY()-size/2,
                    (int)bounds.right, (int)bounds.centerY()+size/2);
            mascot.draw(canvas);
            paint.setColor(0x26FFFFFF);
            paint.setStrokeWidth(density);
            canvas.drawLine(bounds.left+24*density, bounds.top+density/2,
                    bounds.right-24*density, bounds.top+density/2, paint);
            canvas.restoreToCount(save);
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
