package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
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
    private final FrameLayout thumbnail;
    private final TextView time;
    private final Rect track = new Rect();
    private boolean hadFrame;

    ShitTokScrubPreviewView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setTag("shittok_scrub_preview");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(240, 10, 14, 18));
        background.setCornerRadius(dp(10));
        background.setStroke(dp(1), Color.argb(95, 8, 146, 208));
        thumbnail = new FrameLayout(context);
        thumbnail.setTag("shittok_scrub_thumbnail");
        thumbnail.setPadding(dp(2), dp(2), dp(2), dp(2));
        thumbnail.setBackground(background);
        thumbnail.setClipToOutline(true);
        thumbnail.setVisibility(GONE);
        addView(thumbnail, new LinearLayout.LayoutParams(dp(112), dp(196)));
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbnail.addView(image, new FrameLayout.LayoutParams(-1, -1));
        time = new TextView(context);
        time.setTextColor(Color.WHITE);
        time.setTextSize(12);
        time.setGravity(Gravity.CENTER);
        time.setShadowLayer(dp(2), 0, dp(1), Color.BLACK);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(-1, dp(28));
        timeParams.topMargin = dp(4);
        addView(time, timeParams);
        setVisibility(GONE);
    }

    void frame(Bitmap bitmap) {
        if (bitmap != null) hadFrame = true;
        image.setImageBitmap(bitmap);
        // A cache miss must not repeatedly collapse/expand the card during a fast drag.
        image.setVisibility(bitmap != null ? VISIBLE : INVISIBLE);
        thumbnail.setVisibility(bitmap != null ? VISIBLE : (hadFrame ? INVISIBLE : GONE));
    }

    void show(FrameLayout root, SeekBar bar, long targetMs, long durationMs) {
        String current = ChaosFeedView.formatPlaybackTime(targetMs);
        SpannableString label = new SpannableString(current + " / "
                + ChaosFeedView.formatPlaybackTime(durationMs));
        label.setSpan(new ForegroundColorSpan(Color.rgb(170, 178, 188)), current.length(),
                label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        time.setText(label);
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
