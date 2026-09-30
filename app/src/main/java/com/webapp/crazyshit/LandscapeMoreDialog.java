package com.webapp.crazyshit;

import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Compact top-right overflow surface used by the app header and OnlyFap hero. */
final class LandscapeMoreDialog {
    private LandscapeMoreDialog() {
    }

    static void attachSoon(NativeMainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        activity.getWindow().getDecorView().postDelayed(() -> {
            if (activity.isFinishing()) return;
            View more = findLandscapeMore(activity.findViewById(android.R.id.content));
            if (more == null) return;
            more.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                show(activity, v);
            });
        }, 120L);
    }

    static void show(NativeMainActivity activity) {
        show(activity, null);
    }

    static void show(NativeMainActivity activity, View anchor) {
        if (activity == null || activity.isFinishing()) return;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8));
        panel.setBackground(compactPanelBackground(activity));
        panel.setClipToOutline(true);

        addHeader(activity, dialog, panel);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);

        List<Action> actions = actions(
                new Action(
                        R.drawable.ic_action_download,
                        "Downloads",
                        "Saved videos and active downloads",
                        () -> activity.startActivity(new Intent(activity, DownloadedActivity.class))
                ),
                new Action(
                        R.drawable.ic_more_settings,
                        "Settings",
                        "Playback and app options",
                        () -> activity.startActivity(new Intent(activity, SettingsActivity.class))
                ),
                new Action(
                        R.drawable.ic_more_account,
                        "Account",
                        "Profile, favorites and social",
                        () -> activity.startActivity(new Intent(activity, ZeroChillAccountActivity.class))
                ),
                new Action(
                        R.drawable.ic_action_feedback,
                        "Send feedback",
                        "Suggest a feature or report a problem",
                        () -> activity.startActivity(new Intent(activity, FeedbackActivity.class))
                )
        );

        for (int i = 0; i < actions.size(); i++) {
            addActionRow(activity, dialog, content, actions.get(i));
            if (i < actions.size() - 1) addDivider(activity, content);
        }

        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, -2);
        panel.addView(scroll, scrollParams);

        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();

        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        int panelWidth = Math.min(dp(activity, 286), screenWidth - dp(activity, 20));
        int maxHeight = Math.min(dp(activity, 430), (int) (screenHeight * 0.66f));

        ViewGroup.LayoutParams panelParams = panel.getLayoutParams();
        if (panelParams != null) {
            panelParams.width = panelWidth;
            panelParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            panel.setLayoutParams(panelParams);
        }
        int actionContentHeight = actions.size() * 48 + Math.max(0, actions.size() - 1);
        scroll.getLayoutParams().height = Math.min(
                maxHeight - dp(activity, 58),
                dp(activity, actionContentHeight)
        );

        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.width = panelWidth;
        attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
        attrs.gravity = Gravity.TOP | Gravity.END;
        attrs.dimAmount = 0.22f;

        int y = fallbackTopOffset(activity);
        int x = dp(activity, 8);
        if (anchor != null && anchor.isAttachedToWindow()) {
            int[] location = new int[2];
            anchor.getLocationOnScreen(location);
            Rect visible = new Rect();
            activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
            int anchorRight = location[0] + anchor.getWidth();
            x = Math.max(dp(activity, 6), screenWidth - anchorRight);
            y = Math.max(
                    dp(activity, 2),
                    location[1] + anchor.getHeight() - visible.top - dp(activity, 4)
            );
        }
        attrs.x = x;
        attrs.y = y;
        window.setAttributes(attrs);

        panel.setAlpha(0f);
        panel.setScaleX(0.94f);
        panel.setScaleY(0.94f);
        panel.setPivotX(panelWidth);
        panel.setPivotY(0f);
        panel.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(150L)
                .start();
    }

    private static void addHeader(
            NativeMainActivity activity,
            Dialog dialog,
            LinearLayout panel
    ) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(activity, 4), 0, 0, dp(activity, 4));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);

        TextView title = text(activity, "More", 18, Color.WHITE, true);
        TextView subtitle = text(
                activity,
                "ZeroChill " + BuildConfig.VERSION_NAME,
                9,
                Color.rgb(136, 136, 146),
                false
        );
        labels.addView(title);
        labels.addView(subtitle);
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView close = iconView(activity, R.drawable.ic_more_close, 34, 8);
        close.setContentDescription("Close More");
        close.setOnClickListener(v -> dialog.dismiss());
        ZeroChillMotion.installPressFeedback(close);
        header.addView(close, new LinearLayout.LayoutParams(dp(activity, 34), dp(activity, 34)));

        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));
    }

    private static void addActionRow(
            NativeMainActivity activity,
            Dialog dialog,
            LinearLayout parent,
            Action action
    ) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 7), dp(activity, 3), dp(activity, 5), dp(activity, 3));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(action.title + ". " + action.subtitle);
        applySelectableForeground(activity, row);

        ImageView icon = iconView(activity, action.iconRes, 32, 7);
        icon.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        icon.setBackground(circle(Color.rgb(29, 29, 34)));
        row.addView(icon, new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 40)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(activity, 9), 0, dp(activity, 4), 0);

        TextView title = text(activity, action.title, 13, Color.WHITE, true);
        title.setMaxLines(1);
        labels.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = text(
                activity,
                action.subtitle,
                9,
                Color.rgb(136, 136, 146),
                false
        );
        subtitle.setMaxLines(1);
        labels.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        if (action.badgeCount > 0) {
            TextView badge = text(
                    activity,
                    action.badgeCount > 99 ? "99+" : String.valueOf(action.badgeCount),
                    10,
                    Color.WHITE,
                    true
            );
            badge.setGravity(Gravity.CENTER);
            badge.setMinWidth(dp(activity, 24));
            badge.setPadding(dp(activity, 6), 0, dp(activity, 6), 0);
            badge.setBackground(ZeroChillUi.rounded(
                    activity,
                    UiPalette.PRIMARY,
                    Color.TRANSPARENT,
                    R.dimen.zc_radius_pill
            ));
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(-2, dp(activity, 22));
            badgeParams.setMargins(dp(activity, 2), 0, dp(activity, 4), 0);
            row.addView(badge, badgeParams);
        }

        ImageView chevron = iconView(activity, R.drawable.ic_more_chevron, 22, 5);
        chevron.setImageTintList(ColorStateList.valueOf(
                ZeroChillUi.color(activity, R.color.zc_text_muted)
        ));
        row.addView(chevron, new LinearLayout.LayoutParams(dp(activity, 22), dp(activity, 40)));

        ZeroChillMotion.installPressFeedback(row);
        row.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            dialog.dismiss();
            action.run.run();
        });

        parent.addView(row, new LinearLayout.LayoutParams(-1, dp(activity, 48)));
    }

    private static void addDivider(NativeMainActivity activity, LinearLayout parent) {
        View divider = new View(activity);
        divider.setBackgroundColor(ZeroChillUi.color(activity, R.color.zc_divider));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(activity, 1));
        params.setMargins(dp(activity, 44), 0, dp(activity, 6), 0);
        parent.addView(divider, params);
    }

    private static GradientDrawable compactPanelBackground(NativeMainActivity activity) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(Color.rgb(8, 8, 10));
        background.setCornerRadius(dp(activity, 20));
        background.setStroke(dp(activity, 1), Color.rgb(43, 43, 50));
        return background;
    }

    private static GradientDrawable circle(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(color);
        return background;
    }

    private static ImageView iconView(
            NativeMainActivity activity,
            int iconRes,
            int sizeDp,
            int paddingDp
    ) {
        ImageView view = new ImageView(activity);
        view.setImageResource(iconRes);
        view.setImageTintList(ColorStateList.valueOf(UiPalette.PRIMARY));
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        view.setPadding(
                dp(activity, paddingDp),
                dp(activity, paddingDp),
                dp(activity, paddingDp),
                dp(activity, paddingDp)
        );
        view.setMinimumWidth(dp(activity, sizeDp));
        view.setMinimumHeight(dp(activity, sizeDp));
        return view;
    }

    private static TextView text(
            NativeMainActivity activity,
            String value,
            int size,
            int color,
            boolean bold
    ) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private static void applySelectableForeground(NativeMainActivity activity, View view) {
        TypedValue typed = new TypedValue();
        if (!activity.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground,
                typed,
                true
        )) return;
        try {
            view.setForeground(activity.getDrawable(typed.resourceId));
        } catch (Exception ignored) {
        }
    }

    private static int fallbackTopOffset(NativeMainActivity activity) {
        Rect visible = new Rect();
        activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
        return visible.top
                + ZeroChillUi.dimension(activity, R.dimen.zc_top_bar_height)
                + dp(activity, 1);
    }

    private static View findLandscapeMore(View view) {
        if (view == null) return null;
        if (view instanceof TextView) {
            CharSequence description = view.getContentDescription();
            if (description != null && "More".contentEquals(description)) return view;
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findLandscapeMore(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static List<Action> actions(Action... values) {
        ArrayList<Action> result = new ArrayList<>();
        for (Action value : values) result.add(value);
        return result;
    }

    private static int dp(NativeMainActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class Action {
        final int iconRes;
        final String title;
        final String subtitle;
        final int badgeCount;
        final Runnable run;

        Action(int iconRes, String title, String subtitle, Runnable run) {
            this(iconRes, title, subtitle, 0, run);
        }

        Action(int iconRes, String title, String subtitle, int badgeCount, Runnable run) {
            this.iconRes = iconRes;
            this.title = title;
            this.subtitle = subtitle;
            this.badgeCount = Math.max(0, badgeCount);
            this.run = run;
        }
    }
}
