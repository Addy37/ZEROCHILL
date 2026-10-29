package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** One stationary overlay above the pager. Its backdrop excludes the controls themselves. */
final class ShitTokFocusControls extends FrameLayout {
    static final String TAG = "shittok_focus_controls";
    static final int HEIGHT_DP = 84;
    final SeekBar seek;
    final TextView play;
    final TextView speed;
    final View close;
    final View pill;
    ShitTokDynamicPinchController owner;
    private final View source;
    private final ViewPageBackdrop page;
    private boolean backdropDirty = true;

    ShitTokFocusControls(Context context, View source) {
        super(context);
        this.source = source;
        page = new ViewPageBackdrop(source);
        setTag(TAG);
        setVisibility(GONE);
        setBackgroundColor(Color.TRANSPARENT);

        seek = new SeekBar(context);
        seek.setMax(1000);
        seek.setPadding(0, 0, 0, 0);
        seek.setContentDescription("Video progress");
        seek.setSplitTrack(false);
        seek.setThumb(new ColorDrawable(Color.TRANSPARENT));
        seek.setProgressTintList(ColorStateList.valueOf(Color.argb(185, 235, 235, 238)));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(Color.argb(70, 205, 205, 210)));
        LayoutParams progress = new LayoutParams(-1, dp(24), Gravity.TOP);
        progress.setMargins(dp(12), 0, dp(12), 0);
        addView(seek, progress);

        TextView exit = button("×", "Exit focus mode", 20f);
        exit.setOnClickListener(v -> { if (owner != null) owner.exitFocusModeAnimated(); });
        close = glass(exit, 24f);
        LayoutParams exitParams = new LayoutParams(dp(48), dp(48), Gravity.BOTTOM | Gravity.START);
        exitParams.setMargins(dp(8), 0, 0, dp(8));
        addView(close, exitParams);

        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER);
        play = button("❚❚", "Pause video", 14f);
        speed = button("1×", "Playback speed 1x", 13f);
        row.addView(play, new LinearLayout.LayoutParams(0, -1, 1f));
        View divider = new View(context);
        divider.setBackgroundColor(Color.argb(60, 255, 255, 255));
        row.addView(divider, new LinearLayout.LayoutParams(dp(1), dp(16)));
        row.addView(speed, new LinearLayout.LayoutParams(0, -1, 1f));
        pill = glass(row, 24f);
        LayoutParams pillParams = new LayoutParams(dp(104), dp(48), Gravity.BOTTOM | Gravity.END);
        pillParams.setMargins(0, 0, dp(8), dp(8));
        addView(pill, pillParams);
        play.setOnClickListener(v -> { if (owner != null) owner.toggleFocusPlayback(); });
        speed.setOnClickListener(v -> { if (owner != null) owner.cycleFocusSpeed(); });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser && owner != null) owner.seekFocusTo(value);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {
                if (owner != null) owner.setFocusSeeking(true);
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (owner != null) owner.setFocusSeeking(false);
            }
        });
    }

    void bind(ShitTokDynamicPinchController next) {
        owner = next;
        play.setEnabled(next != null);
        speed.setEnabled(next != null);
        close.setEnabled(next != null);
        seek.setEnabled(next != null);
        invalidateBackdrop();
    }

    void invalidateBackdrop() {
        backdropDirty = true;
        if (getVisibility() == VISIBLE) invalidate();
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        if (backdropDirty && source != getParent() && canvas.isHardwareAccelerated()
                && source.getWidth() > 0 && source.getHeight() > 0) {
            backdropDirty = false;
            page.record(source.getWidth(), source.getHeight(), recording -> {
                source.draw(recording);
                return kotlin.Unit.INSTANCE;
            });
        }
        super.dispatchDraw(canvas);
    }

    @Override protected void onDetachedFromWindow() {
        page.release();
        backdropDirty = true;
        super.onDetachedFromWindow();
    }

    private View glass(View controls, float radius) {
        Activity activity = activity(getContext());
        // The real shell installs Compose owners. Standalone JVM fixtures and legacy
        // hosts still get functional native controls without fabricating an Activity owner.
        View ancestor = source;
        while (ancestor != null && !(ancestor instanceof FrostedNavigationLayout)) {
            ancestor = ancestor.getParent() instanceof View ? (View) ancestor.getParent() : null;
        }
        if (activity != null && ancestor != null) {
            GlassHeaderView glass = new GlassHeaderView(activity, controls, radius, false, false, true, 4f);
            glass.setPageBackdrop(page);
            return glass;
        }
        FrameLayout fallback = new FrameLayout(getContext());
        GradientDrawable film = new GradientDrawable();
        film.setColor(Color.argb(90, 30, 30, 34));
        film.setCornerRadius(dp(20));
        View surface = new View(getContext());
        surface.setBackground(film);
        LayoutParams inset = new LayoutParams(-1, -1);
        inset.setMargins(dp(4), dp(4), dp(4), dp(4));
        fallback.addView(surface, inset);
        fallback.addView(controls, new LayoutParams(-1, -1));
        return fallback;
    }

    private TextView button(String text, String description, float size) {
        TextView button = new TextView(getContext());
        button.setText(text);
        button.setTextColor(Color.rgb(235, 235, 238));
        button.setTextSize(size);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setContentDescription(description);
        button.setMinWidth(dp(48));
        button.setMinHeight(dp(48));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static Activity activity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return context instanceof Activity ? (Activity) context : null;
    }
}
