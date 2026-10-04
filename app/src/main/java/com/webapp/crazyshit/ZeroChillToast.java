package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Dialog;
import android.content.ContextWrapper;
import java.util.ArrayList;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/** In-app transient message. It attaches to the resumed Activity and never owns a window. */
final class ZeroChillToast {
    static final int LENGTH_SHORT = 0;
    static final int LENGTH_LONG = 1;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> resumed = new WeakReference<>(null);
    private static final ArrayList<WeakReference<Dialog>> dialogs = new ArrayList<>();
    private static View visible;
    private static Activity visibleOwner;
    private static CharSequence visibleText;
    private static Runnable pendingRemoval;

    private final Context context;
    private final CharSequence message;
    private final int duration;

    private ZeroChillToast(Context context, CharSequence message, int duration) {
        this.context = context;
        this.message = message == null || message.length() == 0 ? "Something went wrong." : message;
        this.duration = duration;
    }

    static ZeroChillToast makeText(Context context, CharSequence message, int duration) {
        return new ZeroChillToast(context, message, duration);
    }

    static ZeroChillToast makeText(Context context, int message, int duration) {
        return makeText(context, context.getText(message), duration);
    }

    void show() { MAIN.post(() -> display(context, message, duration)); }

    static void onResumed(Activity activity) { resumed = new WeakReference<>(activity); }

    static void registerDialog(Dialog dialog) {
        for (int i = dialogs.size() - 1; i >= 0; i--) {
            if (dialogs.get(i).get() == null) dialogs.remove(i);
        }
        dialogs.add(new WeakReference<>(dialog));
    }

    static void onPaused(Activity activity) {
        if (resumed.get() == activity) resumed.clear();
        if (visibleOwner == activity) clear();
    }

    private static void display(Context context, CharSequence message, int duration) {
        Activity activity = activityOf(context);
        if (activity == null) activity = resumed.get();
        if (activity == null || resumed.get() != activity || activity.isFinishing()
                || activity.isDestroyed()) return;
        Dialog focusedDialog = null;
        for (int i = dialogs.size() - 1; i >= 0; i--) {
            Dialog candidate = dialogs.get(i).get();
            if (candidate == null) { dialogs.remove(i); continue; }
            if (candidate.isShowing() && activityOf(candidate.getContext()) == activity) {
                if (focusedDialog == null) focusedDialog = candidate;
                if (candidate.getWindow() != null && candidate.getWindow().getDecorView().hasWindowFocus()) {
                    focusedDialog = candidate;
                    break;
                }
            }
        }
        FrameLayout host = focusedDialog == null
                ? activity.findViewById(android.R.id.content)
                : focusedDialog.findViewById(android.R.id.content);
        if (host == null) return;
        if (visibleOwner == activity && visible != null && visible.getParent() == host
                && message != null && message.equals(visibleText)) return;
        clear();
        TextView view = new TextView(activity);
        view.setText(message);
        view.setTextSize(14);
        view.setTextColor(ZeroChillUi.color(activity, R.color.zc_text_primary));
        view.setGravity(Gravity.CENTER_VERTICAL);
        int horizontal = dp(activity, 16);
        view.setPadding(horizontal, dp(activity, 11), horizontal, dp(activity, 11));
        view.setBackground(ZeroChillUi.sheetGlass(activity));
        view.setElevation(dp(activity, 8));
        view.setMaxLines(3);
        view.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.leftMargin = dp(activity, 20);
        params.rightMargin = dp(activity, 20);
        boolean keyboardVisible = false;
        if (Build.VERSION.SDK_INT >= 30 && host.getRootWindowInsets() != null) {
            keyboardVisible = host.getRootWindowInsets().isVisible(
                    android.view.WindowInsets.Type.ime());
        }
        params.bottomMargin = dp(activity, focusedDialog == null
                ? (keyboardVisible ? 16 : 88) : 72);
        host.addView(view, params);
        visible = view;
        visibleOwner = activity;
        visibleText = message;
        int timeout = duration == LENGTH_LONG ? 4000 : 2400;
        AccessibilityManager accessibility = (AccessibilityManager) activity.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (Build.VERSION.SDK_INT >= 29 && accessibility != null) {
            timeout = accessibility.getRecommendedTimeoutMillis(timeout,
                    AccessibilityManager.FLAG_CONTENT_TEXT);
        } else if (accessibility != null && accessibility.isTouchExplorationEnabled()) {
            timeout *= 2;
        }
        pendingRemoval = ZeroChillToast::clear;
        MAIN.postDelayed(pendingRemoval, timeout);
    }

    private static void clear() {
        if (pendingRemoval != null) MAIN.removeCallbacks(pendingRemoval);
        pendingRemoval = null;
        if (visible != null && visible.getParent() instanceof FrameLayout) {
            ((FrameLayout) visible.getParent()).removeView(visible);
        }
        visible = null;
        visibleOwner = null;
        visibleText = null;
    }

    private static Activity activityOf(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            Context next = ((ContextWrapper) context).getBaseContext();
            if (next == context) break;
            context = next;
        }
        return context instanceof Activity ? (Activity) context : null;
    }

    private static int dp(Context context, int size) {
        return Math.round(size * context.getResources().getDisplayMetrics().density);
    }
}
