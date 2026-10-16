package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** Non-interactive, edge-clamped card. Timestamp remains available without an image. */
final class ShitTokScrubPreviewView extends LinearLayout {
    private final ImageView image;
    private final TextView time;
    private final Rect track = new Rect();
    private boolean hadFrame;

    ShitTokScrubPreviewView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setPadding(dp(4), dp(4), dp(4), dp(2));
        setTag("shittok_scrub_preview");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(240, 10, 14, 18));
        background.setCornerRadius(dp(10));
        background.setStroke(dp(1), Color.argb(95, 8, 146, 208));
        setBackground(background);
        setClipToOutline(true);
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setVisibility(GONE);
        addView(image, new LinearLayout.LayoutParams(-1, dp(82)));
        time = new TextView(context);
        time.setTextColor(Color.WHITE);
        time.setTextSize(12);
        time.setGravity(Gravity.CENTER);
        addView(time, new LinearLayout.LayoutParams(-1, dp(26)));
        setVisibility(GONE);
    }

    void frame(Bitmap bitmap) {
        if (bitmap != null) hadFrame = true;
        image.setImageBitmap(bitmap);
        // A cache miss must not repeatedly collapse/expand the card during a fast drag.
        image.setVisibility(bitmap != null ? VISIBLE : (hadFrame ? INVISIBLE : GONE));
    }

    void show(FrameLayout root, SeekBar bar, long targetMs, long durationMs) {
        time.setText(ChaosFeedView.formatPlaybackTime(targetMs) + " / "
                + ChaosFeedView.formatPlaybackTime(durationMs));
        setVisibility(VISIBLE);
        position(root, bar);
    }

    void position(FrameLayout root, SeekBar bar) {
        if (getVisibility() != VISIBLE || root.getWidth() <= 0) return;
        int width = Math.max(1, Math.min(dp(152), root.getWidth() - dp(16)));
        measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(root.getHeight(), MeasureSpec.AT_MOST));
        track.set(0, 0, bar.getWidth(), bar.getHeight());
        root.offsetDescendantRectToMyCoords(bar, track);
        float fraction = bar.getProgress() / (float) Math.max(1, bar.getMax());
        if (bar.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) fraction = 1f - fraction;
        float center = track.left + bar.getPaddingLeft() + fraction
                * (bar.getWidth() - bar.getPaddingLeft() - bar.getPaddingRight());
        setTranslationX(ShitTokInteractionPolicy.previewLeft(center, width, root.getWidth(), dp(8)));
        // Use the whole hit row's top so the card never overlaps the thumb/finger.
        View row = (View) bar.getParent();
        setTranslationY(Math.max(dp(8), row.getTop() - getMeasuredHeight() - dp(8)));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
        if (params.width != width) { params.width = width; setLayoutParams(params); }
    }

    void hide() { setVisibility(GONE); hadFrame = false; frame(null); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
