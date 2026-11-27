package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.ColorUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/** Dedicated two-action save surface. Measures its contents rather than a fixed menu height. */
final class PlayerSaveSheet {
    private PlayerSaveSheet() {}

    static Dialog show(Activity activity, boolean saved, VideoActionSheet.Action[] actions) {
        boolean landscape = activity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        Dialog dialog = landscape ? new Dialog(activity) : new BottomSheetDialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog instanceof BottomSheetDialog)
            ((BottomSheetDialog) dialog).setDismissWithAnimation(ZeroChillMotion.animationsEnabled(activity));
        LinearLayout panel = buildPanel(activity, landscape, saved, actions, dialog::dismiss);
        if (!landscape) {
            ViewCompat.setOnApplyWindowInsetsListener(panel, (v, insets) -> {
                androidx.core.graphics.Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                        | WindowInsetsCompat.Type.displayCutout());
                panel.setPadding(dp(activity, 20) + safe.left, dp(activity, 10),
                        dp(activity, 20) + safe.right, dp(activity, 20) + safe.bottom);
                return insets;
            });
        }
        dialog.setContentView(panel, new ViewGroup.LayoutParams(-1, -2));
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.44f;
            attrs.width = landscape ? Math.min(dp(activity, 360),
                    (int)(activity.getResources().getDisplayMetrics().widthPixels * .58f)) : -1;
            attrs.height = -2;
            attrs.gravity = landscape ? Gravity.END | Gravity.CENTER_VERTICAL : Gravity.BOTTOM;
            window.setAttributes(attrs);
            window.setNavigationBarColor(Color.BLACK);
            androidx.core.view.WindowCompat.getInsetsController(window, window.getDecorView())
                    .setAppearanceLightNavigationBars(false);
        }
        if (dialog instanceof BottomSheetDialog) {
            BottomSheetDialog sheet = (BottomSheetDialog) dialog;
            View bottom = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottom != null) bottom.setBackgroundColor(Color.TRANSPARENT);
            sheet.getBehavior().setSkipCollapsed(true);
            sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        } else {
            ZeroChillMotion.enterFromEnd(panel, dp(activity, 18));
        }
        return dialog;
    }

    static LinearLayout buildPanel(Activity a, boolean landscape, boolean saved,
                                   VideoActionSheet.Action[] actions, Runnable dismiss) {
        int accent = AppearanceTheme.accent(a);
        LinearLayout panel = column(a);
        panel.setTag("player-save-sheet");
        panel.setPadding(dp(a, 20), dp(a, landscape ? 14 : 10), dp(a, 20), dp(a, 20));
        GradientDrawable bg = rounded(a, AppearanceTheme.background(a), 24);
        if (!landscape) {
            float r = dp(a, 24);
            bg.setCornerRadii(new float[]{r,r,r,r,0,0,0,0});
        }
        panel.setBackground(bg);
        if (!landscape) {
            View handle = new View(a);
            handle.setBackground(rounded(a, 0xFF52525B, 2));
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(a, 36), dp(a, 4));
            hp.gravity = Gravity.CENTER_HORIZONTAL;
            hp.bottomMargin = dp(a, 8);
            panel.addView(handle, hp);
        }
        LinearLayout header = row(a);
        TextView title = text(a, "Save to", 22, AppearanceTheme.primaryText(a), true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView close = icon(a, R.drawable.ic_more_close, AppearanceTheme.secondaryText(a), 12);
        close.setBackground(rounded(a, 0xFF202024, 24));
        close.setContentDescription("Close save options");
        close.setFocusable(true);
        close.setOnClickListener(v -> dismiss.run());
        header.addView(close, new LinearLayout.LayoutParams(dp(a, 44), dp(a, 44)));
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(-1, -2);
        headerParams.bottomMargin = dp(a, 8);
        panel.addView(header, headerParams);

        addAction(a, panel, actions[0], "Watch Later", saved ? "Saved. Tap to remove" : actions[0].subtitle,
                saved ? accent : AppearanceTheme.primaryText(a), 0xFF1C1C20,
                saved ? R.drawable.ic_save_sheet_check : R.drawable.ic_save_sheet_plus,
                saved ? accent : AppearanceTheme.secondaryText(a), dismiss, "save-watch-later");
        View divider = new View(a);
        divider.setBackgroundColor(0xFF222226);
        LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, dp(a, 1));
        line.leftMargin = dp(a, 62);
        panel.addView(divider, line);
        addAction(a, panel, actions[1], actions[1].title, actions[1].subtitle, accent,
                ColorUtils.blendARGB(0xFF111116, accent, .15f),
                R.drawable.ic_more_chevron, accent, dismiss, "save-collection");
        return panel;
    }

    private static void addAction(Activity a, LinearLayout panel, VideoActionSheet.Action action,
                                  String label, String subtitle, int tint, int tileColor,
                                  int trailingRes, int trailingTint, Runnable dismiss, String tag) {
        LinearLayout row = row(a);
        row.setTag(tag);
        row.setMinimumHeight(dp(a, 76));
        row.setPadding(0, dp(a, 12), 0, dp(a, 12));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF),
                null, rounded(a, Color.WHITE, 12)));
        row.setFocusable(true);
        row.setContentDescription(action.title + ". " + subtitle);
        ImageView tile = icon(a, action.iconRes, tint, 12);
        tile.setBackground(rounded(a, tileColor, 13));
        row.addView(tile, new LinearLayout.LayoutParams(dp(a, 48), dp(a, 48)));
        LinearLayout labels = column(a);
        TextView title = text(a, label, 16, AppearanceTheme.primaryText(a), true);
        TextView detail = text(a, subtitle, 13, AppearanceTheme.secondaryText(a), false);
        detail.setPadding(0, dp(a, 3), 0, 0);
        labels.addView(title);
        labels.addView(detail);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.leftMargin = dp(a, 14);
        lp.rightMargin = dp(a, 8);
        row.addView(labels, lp);
        ImageView trailing = icon(a, trailingRes, trailingTint, 2);
        trailing.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(trailing, new LinearLayout.LayoutParams(dp(a, 24), dp(a, 24)));
        ZeroChillMotion.installPressFeedback(row);
        row.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            dismiss.run();
            action.run.run();
        });
        panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private static LinearLayout column(Activity a) {
        LinearLayout v = new LinearLayout(a);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private static LinearLayout row(Activity a) {
        LinearLayout v = new LinearLayout(a);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private static TextView text(Activity a, String value, int sp, int color, boolean bold) {
        TextView v = new TextView(a);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(null, Typeface.BOLD);
        return v;
    }

    private static ImageView icon(Activity a, int res, int tint, int padding) {
        ImageView v = new ImageView(a);
        v.setImageResource(res);
        v.setImageTintList(ColorStateList.valueOf(tint));
        int p = dp(a, padding);
        v.setPadding(p, p, p, p);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return v;
    }

    private static GradientDrawable rounded(Activity a, int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(a, radius));
        return d;
    }

    private static int dp(Activity a, int dp) { return BrowseUi.dp(a, dp); }
}
