package com.webapp.crazyshit;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Menu;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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
        if (!anchor.isAttachedToWindow() || menu.size() == 0) return;
        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        int inset = dp(6);
        rows.setPadding(inset, inset, inset, inset);
        rows.setBackground(ZeroChillUi.sheetGlass(context));
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.addView(rows);
        int maxHeight = Math.min(dp(352), context.getResources().getDisplayMetrics().heightPixels - dp(96));
        PopupWindow popup = new PopupWindow(scroll, dp(224), -2, true);
        popup.setHeight(Math.min(maxHeight, dp(12) + menu.size() * dp(48)));
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
        popup.showAsDropDown(anchor, 0, dp(4), Gravity.END);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
