package com.webapp.crazyshit;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** The optical finish only. Live content is sampled separately by FrostedBackdropRenderer. */
final class LiquidGlassDrawable extends Drawable {
    enum Material { NAVIGATION, LENS, FLOATING }
    private final Material material;
    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pane = new RectF();
    private Shader film;
    private Shader rim;
    private int opacity = 255;
    private boolean backdropAvailable;

    void setBackdropAvailable(boolean available) {
        if (available == backdropAvailable) return;
        backdropAvailable = available;
        onBoundsChange(getBounds());
        invalidateSelf();
    }

    LiquidGlassDrawable(android.content.Context context, Material material) {
        this.material = material;
        density = context.getResources().getDisplayMetrics().density;
    }

    @Override protected void onBoundsChange(Rect bounds) {
        pane.set(bounds);
        boolean lens = material == Material.LENS;
        boolean floating = material == Material.FLOATING;
        film = new LinearGradient(0, pane.top, 0, pane.bottom,
                floating ? (backdropAvailable ? new int[]{0xAF303740, 0xB3181F27, 0xBF10161D}
                        : new int[]{0xFF2D323A, 0xFF13171D, 0xFF0C1015})
                        : lens ? new int[]{0x40080E15, 0x24070C12, 0x3A07121A}
                        : new int[]{0xBE18222D, 0xB8070D14, 0xCD070C13},
                null, Shader.TileMode.CLAMP);
        rim = new LinearGradient(pane.left, pane.top, pane.right, pane.bottom,
                new int[]{lens ? 0xB8FFFFFF : 0x70FFFFFF, 0x10FFFFFF, 0x08FFFFFF, 0x5285C9E5},
                new float[]{0f, .38f, .65f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override public void draw(@NonNull Canvas canvas) {
        float r = material == Material.FLOATING ? 26 * density : pane.height() / 2f;
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(opacity);
        paint.setShader(film);
        canvas.drawRoundRect(pane, r, r, paint);
        // The edge is light reflected on a bevel, never a cyan perimeter.
        paint.setShader(rim);
        paint.setStrokeWidth(density);
        paint.setStyle(Paint.Style.STROKE);
        float half = density / 2f;
        pane.inset(half, half);
        canvas.drawRoundRect(pane, r, r, paint);
        pane.inset(-half, -half);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override public void setAlpha(int alpha) { opacity = alpha; invalidateSelf(); }
    @Override public void setColorFilter(@Nullable ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
