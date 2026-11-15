package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Point;
import android.view.Gravity;
import android.view.View;
import android.widget.PopupWindow;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Window-relative placement that does not need a detached Compose menu's height. */
final class AnchoredMenuPopup {
    private AnchoredMenuPopup() { }

    static int maxHeight(View anchor, int requested, boolean above) {
        if (!above || anchor.getRootView().getHeight() <= 0) return requested;
        Point location = locationInRoot(anchor);
        Insets safe = safeInsets(anchor);
        return Math.max(1, Math.min(requested, location.y - safe.top - dp(anchor, 12)));
    }

    /** Defer reentrant detach dismissal until the framework finishes using its decor. */
    static final class Window extends PopupWindow {
        private boolean positioning;
        private boolean dismissAfterPositioning;

        Window(View content, int width, int height, boolean focusable) {
            super(content, width, height, focusable);
        }

        @Override public void dismiss() {
            if (positioning) dismissAfterPositioning = true;
            else super.dismiss();
        }

        @Override public void showAsDropDown(View anchor, int xoff, int yoff, int gravity) {
            positioning = true;
            try { super.showAsDropDown(anchor, xoff, yoff, gravity); }
            finally { finishPositioning(); }
        }

        @Override public void showAtLocation(View parent, int gravity, int x, int y) {
            positioning = true;
            try { super.showAtLocation(parent, gravity, x, y); }
            finally { finishPositioning(); }
        }

        private void finishPositioning() {
            positioning = false;
            if (dismissAfterPositioning) {
                dismissAfterPositioning = false;
                super.dismiss();
            }
        }
    }

    static boolean canShow(Context context, View anchor) {
        if (!anchor.isAttachedToWindow() || anchor.getWindowToken() == null) return false;
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                return !activity.isFinishing() && !activity.isDestroyed();
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return false;
    }

    static boolean show(PopupWindow popup, View anchor, boolean above) {
        if (!canShow(anchor.getContext(), anchor)) return false;
        try {
            if (!above) {
                popup.showAsDropDown(anchor, 0, dp(anchor, 4), Gravity.END);
                return true;
            }
            View root = anchor.getRootView();
            Point location = locationInRoot(anchor);
            int margin = dp(anchor, 8);
            Insets safe = safeInsets(anchor);
            int left = Math.max(margin + safe.left, Math.min(location.x + anchor.getWidth() - popup.getWidth(),
                    root.getWidth() - safe.right - popup.getWidth() - margin));
            int right = Math.max(0, root.getWidth() - left - popup.getWidth());
            int bottom = Math.max(0, root.getHeight() - location.y + dp(anchor, 4));
            popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
            // BOTTOM anchors the menu's lower edge above the button. WRAP_CONTENT can
            // then resolve during composition without being clipped beneath the dock.
            popup.showAtLocation(anchor, Gravity.BOTTOM | Gravity.RIGHT, right, bottom);
            return true;
        } catch (android.view.WindowManager.BadTokenException expiredWindow) {
            return false;
        }
    }

    private static Point locationInRoot(View anchor) {
        int[] position = new int[2], origin = new int[2];
        anchor.getLocationInWindow(position);
        anchor.getRootView().getLocationInWindow(origin);
        return new Point(position[0] - origin[0], position[1] - origin[1]);
    }

    private static int dp(View anchor, int value) {
        return Math.round(value * anchor.getResources().getDisplayMetrics().density);
    }

    private static Insets safeInsets(View anchor) {
        WindowInsetsCompat window = ViewCompat.getRootWindowInsets(anchor);
        return window == null ? Insets.NONE : window.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
    }
}
