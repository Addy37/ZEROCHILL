package com.webapp.crazyshit;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Menu;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.view.menu.MenuBuilder;

/** Compact anchored glass actions, with MenuItem callbacks retained for existing callers. */
@SuppressLint("RestrictedApi") // MenuBuilder is the appcompat menu model; only this helper touches it.
final class ZeroChillMenu {
    interface OnMenuItemClickListener { boolean onMenuItemClick(MenuItem item); }

    private final Context context;
    private final View anchor;
    private final MenuBuilder menu;
    private OnMenuItemClickListener listener;

    ZeroChillMenu(Context context, View anchor) {
        this.context = context;
        this.anchor = anchor;
        menu = new MenuBuilder(context);
        menu.setCallback(new MenuBuilder.Callback() {
            @Override public boolean onMenuItemSelected(MenuBuilder source, MenuItem item) {
                return listener != null && listener.onMenuItemClick(item);
            }
            @Override public void onMenuModeChange(MenuBuilder source) { }
        });
    }

    Menu getMenu() { return menu; }
    void dispatch(MenuItem item) { menu.performItemAction(item, 0); }
    void setOnMenuItemClickListener(OnMenuItemClickListener listener) { this.listener = listener; }

    void show() {
        showInternal(true);
    }

    /** Explicit platform-rendered variant; show() uses the shared liquid-glass presentation. */
    void showNative() {
        showInternal(false);
    }

    private void showInternal(boolean preferCompose) {
        if (!anchor.isAttachedToWindow() || menu.size() == 0) return;
        if (preferCompose && ZeroChillComposeMenu.show(context, anchor, menu, this::dispatch)) return;
        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        int inset = dp(6);
        rows.setPadding(inset, inset, inset, inset);
        // PopupWindow is a separate window, so use a theme-aware translucent
        // fallback here. Full backdrop refraction will be integrated separately.
        int baseColor = AppearanceTheme.surface(context);
        float tint = AppearanceTheme.menuTint(context);
        int tinted = Color.argb(Math.round(86 + tint * 120),
                Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
        GradientDrawable sheet = new GradientDrawable();
        sheet.setColor(tinted);
        sheet.setCornerRadius(dp(18));
        sheet.setStroke(dp(1), AppearanceTheme.outline(context));
        rows.setBackground(sheet);
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.addView(rows);
        int maxHeight = Math.min(dp(352), context.getResources().getDisplayMetrics().heightPixels - dp(96));
        int width = Math.min(dp(264), context.getResources().getDisplayMetrics().widthPixels - dp(32));
        PopupWindow popup = new PopupWindow(scroll, width, -2, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        View.OnAttachStateChangeListener attachment = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { }
            @Override public void onViewDetachedFromWindow(View view) { popup.dismiss(); }
        };
        anchor.addOnAttachStateChangeListener(attachment);
        popup.setOnDismissListener(() -> anchor.removeOnAttachStateChangeListener(attachment));
        popup.setElevation(dp(12));
        for (int index = 0; index < menu.size(); index++) {
            MenuItem item = menu.getItem(index);
            if (!item.isVisible()) continue;
            TextView row = new TextView(context);
            row.setText(item.getTitle());
            row.setTextSize(14);
            row.setTextColor(ZeroChillUi.color(context,
                    item.isEnabled() ? R.color.zc_text_primary : R.color.zc_text_muted));
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinHeight(dp(48));
            row.setBackground(ZeroChillUi.actionSurface(context, false));
            row.setPadding(dp(14), 0, dp(14), 0);
            row.setContentDescription(item.getTitle());
            row.setEnabled(item.isEnabled());
            row.setClickable(item.isEnabled());
            row.setFocusable(true);
            ZeroChillMotion.installPressFeedback(row);
            row.setOnClickListener(v -> {
                popup.dismiss();
                dispatch(item);
            });
            rows.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        rows.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        popup.setHeight(Math.min(maxHeight, rows.getMeasuredHeight()));
        popup.showAsDropDown(anchor, 0, dp(4), Gravity.END);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
