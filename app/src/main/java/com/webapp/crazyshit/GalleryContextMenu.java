package com.webapp.crazyshit;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Same-window glass menu. A second finger dismisses it and hands the gesture to the gallery. */
final class GalleryContextMenu extends FrameLayout {
    private final ViewGroup host;
    private final View anchor;
    private final Runnable dismissed;
    private boolean closed;

    GalleryContextMenu(Activity activity, View anchor, View backdrop, boolean favorite,
                       Runnable[] actions, Runnable dismissed) {
        super(activity);
        this.anchor = anchor;
        this.dismissed = dismissed;
        host = activity.findViewById(android.R.id.content);
        setBackgroundColor(0x22000000);
        setClickable(true); setFocusableInTouchMode(true);
        setOnClickListener(v -> dismiss());
        setOnKeyListener((v, key, event) -> {
            if (key != KeyEvent.KEYCODE_BACK) return false;
            if (event.getAction() == KeyEvent.ACTION_UP) dismiss();
            return true;
        });
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(6), dp(8), dp(6), dp(8));
        String[] labels = {"Open", favorite ? "Unfavorite" : "Favorite", "Share", "Copy Link", "Download"};
        int[] icons = {R.drawable.ic_player_play, favorite ? R.drawable.ic_action_heart_filled : R.drawable.ic_action_heart_outline,
                R.drawable.ic_action_share, R.drawable.ic_action_link, R.drawable.ic_action_download};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView row = new TextView(activity);
            row.setText(labels[i]); row.setTextSize(15);
            row.setTextColor(i == 1 && favorite ? UiPalette.PRIMARY : Color.WHITE);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(4), dp(16), dp(4));
            row.setCompoundDrawablePadding(dp(16));
            android.graphics.drawable.Drawable icon = activity.getDrawable(icons[i]).mutate();
            icon.setTint(i == 1 && favorite ? UiPalette.PRIMARY : Color.WHITE);
            icon.setBounds(0, 0, dp(22), dp(22));
            row.setCompoundDrawables(icon, null, null, null);
            row.setMinHeight(dp(48)); row.setFocusable(true);
            row.setBackgroundResource(R.drawable.zc_dialog_action);
            ZeroChillMotion.installPressFeedback(row);
            row.setOnClickListener(v -> { dismiss(); actions[index].run(); });
            rows.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(rows);
        PreferenceGlassPanel glass = new PreferenceGlassPanel(activity, scroll, backdrop);
        glass.setClickable(true);
        int width = Math.min(dp(264), activity.getResources().getDisplayMetrics().widthPixels - dp(32));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, -2);
        addView(glass, params);
        host.addView(this, new ViewGroup.LayoutParams(-1, -1));
        requestFocus();
        post(() -> {
            if (closed) return;
            Rect safe = new Rect(); host.getWindowVisibleDisplayFrame(safe);
            int[] origin = new int[2]; host.getLocationOnScreen(origin);
            int[] tile = new int[2]; anchor.getLocationOnScreen(tile);
            int top = Math.max(dp(8), safe.top - origin[1] + dp(8));
            int bottom = Math.min(getHeight() - dp(8), safe.bottom - origin[1] - dp(8));
            int height = Math.min(glass.getHeight(), Math.max(dp(48), bottom - top));
            params.height = height;
            params.leftMargin = Math.max(dp(8), Math.min(getWidth() - width - dp(8), tile[0] - origin[0]));
            params.topMargin = Math.max(top, Math.min(bottom - height, tile[1] - origin[1] + anchor.getHeight() / 2));
            glass.setLayoutParams(params);
        });
    }

    void dismiss() {
        if (closed) return;
        closed = true;
        host.removeView(this);
        anchor.requestFocus();
        dismissed.run();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            dismiss();
            // RecyclerView receives the complete beginning of a two-pointer gesture in host coordinates.
            MotionEvent down = MotionEvent.obtain(event.getDownTime(), event.getEventTime(),
                    MotionEvent.ACTION_DOWN, event.getX(0), event.getY(0), event.getMetaState());
            host.dispatchTouchEvent(down); down.recycle();
            MotionEvent next = MotionEvent.obtain(event);
            host.dispatchTouchEvent(next); next.recycle();
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
