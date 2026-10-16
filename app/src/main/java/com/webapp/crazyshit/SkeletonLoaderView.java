package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** Lightweight animated feed skeleton used while native tabs are fetching content. */
final class SkeletonLoaderView extends LinearLayout {
    private boolean running;
    SkeletonLoaderView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(12), dp(8), dp(12), dp(18));
        setGravity(Gravity.TOP);
        setBackgroundColor(ZeroChillUi.background(context));
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        for (int i = 0; i < 3; i++) addView(card());
        setVisibility(GONE);
    }

    void start() {
        if (running) return;
        running = true;
        animate().cancel();
        setAlpha(1f);
        setVisibility(VISIBLE);
        bringToFront();
    }

    void stop() {
        running = false;
        animate().cancel();
        setVisibility(GONE);
        setAlpha(1f);
    }

    private View card() {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(0, 0, 0, dp(12));
        card.setBackground(round(Color.rgb(24, 24, 28), 16));
        LayoutParams cardParams = new LayoutParams(-1, dp(238));
        cardParams.setMargins(0, dp(6), 0, dp(6));

        View media = new View(getContext());
        media.setBackground(round(Color.rgb(37, 37, 42), 14));
        card.addView(media, new LayoutParams(-1, dp(166)));

        View title = new View(getContext());
        title.setBackground(round(Color.rgb(48, 48, 54), 8));
        LayoutParams titleParams = new LayoutParams((int) (getResources().getDisplayMetrics().widthPixels * 0.58f), dp(15));
        titleParams.setMargins(dp(14), dp(14), dp(14), 0);
        card.addView(title, titleParams);

        View meta = new View(getContext());
        meta.setBackground(round(Color.rgb(39, 39, 45), 7));
        LayoutParams metaParams = new LayoutParams((int) (getResources().getDisplayMetrics().widthPixels * 0.34f), dp(10));
        metaParams.setMargins(dp(14), dp(9), dp(14), 0);
        card.addView(meta, metaParams);

        FrameLayout wrapper = new FrameLayout(getContext());
        wrapper.addView(card, new FrameLayout.LayoutParams(-1, -1));
        wrapper.setLayoutParams(cardParams);
        return wrapper;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        return bg;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
