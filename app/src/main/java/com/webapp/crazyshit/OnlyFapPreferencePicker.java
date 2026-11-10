package com.webapp.crazyshit;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** OnlyFap's centered presentation; the shared preference store and actions remain unchanged. */
final class OnlyFapPreferencePicker {
    private OnlyFapPreferencePicker() { }

    static void show(View anchor, Runnable changed) {
        if (!anchor.isAttachedToWindow()) {
            ContentPreferenceUi.choose(anchor.getContext(), changed);
            return;
        }
        // A selector owns at most one window, including rapid repeated taps.
        if (anchor.getTag(R.id.content_preference_picker) instanceof AlertDialog) {
            AlertDialog existing = (AlertDialog) anchor.getTag(R.id.content_preference_picker);
            if (existing.isShowing()) return;
            anchor.setTag(R.id.content_preference_picker, null);
        }
        Context context = anchor.getContext();
        int accent = AppearanceTheme.accent(context);
        LinearLayout controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 6));
        TextView heading = text(context, "Content preference", 15, Color.WHITE);
        heading.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        controls.addView(heading, new LinearLayout.LayoutParams(-1, dp(context, 32)));
        GridLayout grid = new GridLayout(context);
        grid.setTag("onlyfap_preference_grid");
        grid.setColumnCount(2);
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.addView(grid, new ScrollView.LayoutParams(-1, -2));
        controls.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        PreferenceGlassPanel glass = new PreferenceGlassPanel(context, controls);
        AlertDialog dialog = new ZeroChillDialog.Builder(context).setView(glass).create();
        ContentPreference current = ContentPreference.get(context);
        for (ContentPreference option : ContentPreference.values()) {
            boolean selected = option == current;
            LinearLayout cell = new LinearLayout(context) {
                @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(info);
                    info.setClassName("android.widget.RadioButton");
                    info.setCheckable(true); info.setChecked(isSelected());
                }
            };
            cell.setTag("preference_" + option.value);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setSelected(selected);
            cell.setMinimumHeight(dp(context, 64));
            cell.setPadding(dp(context, 4), dp(context, 6), dp(context, 4), dp(context, 6));
            cell.setContentDescription(option.label);
            cell.setClickable(true); cell.setFocusable(true);
            cell.setBackground(ZeroChillUi.rounded(context,
                    selected ? alpha(accent, 48) : 0x12000000,
                    selected ? alpha(accent, 144) : 0x18FFFFFF, R.dimen.zc_radius_medium));
            if (option.icon() != 0) {
                ImageView icon = new ImageView(context);
                icon.setImageResource(option.icon()); icon.setColorFilter(selected ? accent : Color.WHITE);
                icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                cell.addView(icon, new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)));
            } else {
                TextView icon = text(context, option.symbol, option == ContentPreference.ALL ? 11 : 23,
                        selected ? accent : Color.WHITE);
                icon.setTypeface(null, android.graphics.Typeface.BOLD);
                icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                cell.addView(icon, new LinearLayout.LayoutParams(-1, dp(context, 26)));
            }
            TextView label = text(context, option.label, 12, selected ? accent : Color.WHITE);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            label.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            cell.addView(label, new LinearLayout.LayoutParams(-1, -2));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            params.width = 0;
            params.height = -2;
            params.setMargins(dp(context, 3), dp(context, 3), dp(context, 3), dp(context, 3));
            grid.addView(cell, params);
            ZeroChillMotion.installPressFeedback(cell);
            cell.setOnClickListener(v -> {
                if (!dialog.isShowing()) return;
                boolean actualChange = ContentPreference.set(context, option);
                dialog.dismiss();
                if (actualChange && changed != null) changed.run();
            });
        }
        TextView cancel = text(context, "Cancel", 13, Color.WHITE);
        cancel.setTag("preference_cancel");
        cancel.setMinimumHeight(dp(context, 48));
        cancel.setClickable(true); cancel.setFocusable(true);
        cancel.setOnClickListener(v -> dialog.dismiss());
        controls.addView(cancel, new LinearLayout.LayoutParams(-1, -2));
        ZeroChillMotion.installPressFeedback(cancel);
        dialog.setView(glass, 0, 0, 0, 0);
        Window window = dialog.getWindow();
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
            window.getDecorView();
            window.setWindowAnimations(ZeroChillMotion.animationsEnabled(context)
                    ? R.style.Animation_ZeroChill_Preference : 0);
        }
        View.OnAttachStateChangeListener attachment = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { }
            @Override public void onViewDetachedFromWindow(View view) { dialog.dismiss(); }
        };
        View.OnLayoutChangeListener layout = (v, l, t, r, b, ol, ot, or, ob) -> dialog.dismiss();
        dialog.setOnDismissListener(ignored -> {
            anchor.removeOnAttachStateChangeListener(attachment);
            anchor.removeOnLayoutChangeListener(layout);
            if (anchor.getTag(R.id.content_preference_picker) == dialog)
                anchor.setTag(R.id.content_preference_picker, null);
        });
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        anchor.setTag(R.id.content_preference_picker, dialog);
        anchor.addOnAttachStateChangeListener(attachment);
        anchor.addOnLayoutChangeListener(layout);
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        Rect frame = new Rect();
        anchor.getWindowVisibleDisplayFrame(frame);
        if (frame.width() <= 0 || frame.height() <= 0)
            frame.set(0, 0, context.getResources().getDisplayMetrics().widthPixels,
                    context.getResources().getDisplayMetrics().heightPixels);
        int margin = dp(context, 12);
        int width = Math.min(dp(context, 304), Math.max(1, frame.width() - margin * 2));
        controls.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int chrome = controls.getMeasuredHeight() - scroll.getMeasuredHeight();
        scroll.getLayoutParams().height = Math.min(scroll.getMeasuredHeight(),
                Math.max(1, frame.height() - margin * 2 - chrome));
        controls.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        WindowManager.LayoutParams attributes = window.getAttributes();
        // Let the window manager center within the task's safe, available area.
        // The selector still owns dismissal, but no longer pulls the grid sideways.
        attributes.gravity = Gravity.CENTER;
        attributes.x = 0;
        attributes.y = 0;
        attributes.width = width; attributes.height = -2;
        attributes.dimAmount = .12f;
        window.setAttributes(attributes);
    }

    private static TextView text(Context context, String value, int size, int color) {
        TextView view = new TextView(context);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setShadowLayer(dp(context, 2), 0, dp(context, 1), Color.BLACK);
        return view;
    }
    private static int alpha(int color, int opacity) { return color & 0x00FFFFFF | opacity << 24; }
    private static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }
}
