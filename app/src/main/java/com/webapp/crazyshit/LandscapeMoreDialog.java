package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.os.Build;
import android.graphics.drawable.RippleDrawable;
import android.widget.FrameLayout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
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
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class LandscapeMoreDialog {
    private static final ExecutorService DOWNLOAD_BADGE_LOADER = Executors.newSingleThreadExecutor();

    private LandscapeMoreDialog() {
    }

    static final int SUPPORT_GITHUB_VIEW_ID = View.generateViewId();
    static final int SUPPORT_CASH_APP_VIEW_ID = View.generateViewId();
    static final int SUPPORT_LATER_VIEW_ID = View.generateViewId();

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
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 10));
        panel.setBackground(compactPanelBackground(activity));
        panel.setClipToOutline(true);

        addHeader(activity, dialog, panel);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);

        int unreadMessages = ZeroChillMessageBadgeStore.unreadCount(activity);
        List<Action> actions = actions(
                new Action(
                        R.drawable.ic_action_download,
                        "Downloads",
                        "Saved videos and active downloads",
                        () -> activity.startActivity(new Intent(activity, DownloadedActivity.class))
                ),
                new Action(
                        R.drawable.ic_action_message,
                        "Messages",
                        unreadMessages > 0
                                ? unreadMessages + (unreadMessages == 1 ? " unread message" : " unread messages")
                                : "Private conversations",
                        unreadMessages,
                        () -> activity.startActivity(new Intent(activity, ZeroChillInboxActivity.class))
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
                ),
                new Action(
                        R.drawable.ic_more_website,
                        "Support ZEROCHILL",
                        "GitHub Sponsors or Cash App",
                        () -> showSupportDialog(activity)
                )
        );

        final TextView activeBadge = populateActions(activity, dialog, content, actions);

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

        Rect visible = new Rect();
        activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        int visibleWidth = visible.width() > 0 ? visible.width() : screenWidth;
        int visibleHeight = visible.height() > 0 ? visible.height() : screenHeight;
        int panelWidth = Math.min(dp(activity, 352), Math.max(1, visibleWidth - dp(activity, 20)));
        int y = fallbackTopOffset(activity);
        int x = dp(activity, 8);
        if (anchor != null && anchor.isAttachedToWindow()) {
            int[] location = new int[2];
            anchor.getLocationOnScreen(location);
            x = Math.max(dp(activity, 6), screenWidth - location[0] - anchor.getWidth());
            y = Math.max(dp(activity, 2), location[1] + anchor.getHeight() - visible.top - dp(activity, 4));
        }
        x = Math.max(0, Math.min(x, visibleWidth - panelWidth));
        // Keep the grid reachable on short landscape displays and at large font scales.
        y = Math.max(0, Math.min(y, visibleHeight / 3));
        int availableHeight = Math.max(1, visibleHeight - y - dp(activity, 12));
        int innerWidth = Math.max(1, panelWidth - panel.getPaddingLeft() - panel.getPaddingRight());
        int widthSpec = View.MeasureSpec.makeMeasureSpec(innerWidth, View.MeasureSpec.EXACTLY);
        int naturalHeight = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        content.measure(widthSpec, naturalHeight);
        View header = panel.getChildAt(0);
        header.measure(widthSpec, naturalHeight);
        scroll.getLayoutParams().height = Math.max(1, Math.min(content.getMeasuredHeight(),
                availableHeight - header.getMeasuredHeight() - panel.getPaddingTop() - panel.getPaddingBottom()));

        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.width = panelWidth;
        attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
        attrs.gravity = Gravity.TOP | Gravity.END;
        attrs.dimAmount = 0.26f;
        attrs.x = x;
        attrs.y = y;
        attrs.windowAnimations = 0;
        if (Build.VERSION.SDK_INT >= 31 && AppearanceTheme.glassBlur(activity) > 0f) {
            attrs.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            attrs.setBlurBehindRadius(dp(activity, Math.min(12,
                    Math.round(8 * AppearanceTheme.glassBlur(activity)))));
        }
        window.setAttributes(attrs);

        if (ZeroChillMotion.animationsEnabled(activity)) {
            panel.setAlpha(0f);
            panel.setScaleX(0.94f);
            panel.setScaleY(0.94f);
            panel.setPivotX(panelWidth);
            panel.setPivotY(0f);
            panel.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(ZeroChillMotion.duration(activity, ZeroChillMotion.QUICK_MS))
                    .setInterpolator(ZeroChillMotion.EASE_OUT).start();
        }
        if (activeBadge != null) loadDownloadBadge(activity, dialog, activeBadge);
    }

    private static void loadDownloadBadge(NativeMainActivity activity, Dialog dialog, TextView badge) {
        Context app = activity.getApplicationContext();
        DOWNLOAD_BADGE_LOADER.execute(() -> {
            int count = 0;
            try {
                for (VideoDownloadStore.Entry entry : VideoDownloadStore.entries(app)) {
                    if (entry.status == DownloadManager.STATUS_PENDING
                            || entry.status == DownloadManager.STATUS_RUNNING) count++;
                }
            } catch (RuntimeException ignored) {
                return;
            }
            final int active = count;
            activity.runOnUiThread(() -> {
                if (!dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
                updateBadge(badge, active);
                if (active > 0 && badge.getParent() instanceof View) {
                    View icons = (View) badge.getParent();
                    if (icons.getParent() instanceof View) {
                        ((View) icons.getParent()).setContentDescription("Downloads, " + active + " active");
                    }
                }
            });
        });
    }

    private static void showSupportDialog(NativeMainActivity activity) {
        if (activity.isFinishing()) return;
        createSupportDialog(activity).show();
    }

    /** A compact branded surface, not a platform AlertDialog with extra list padding. */
    static Dialog createSupportDialog(Activity activity) {
        Dialog dialog = new Dialog(activity) {
            @Override protected void onStart() {
                super.onStart();
                // Set the final window geometry before the first frame is drawn.
                // Resizing inside OnShow creates the small position jump on Android 16.
                Window window = getWindow();
                if (window == null) return;
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams attrs = window.getAttributes();
                attrs.width = Math.min(supportDp(activity, 380),
                        activity.getResources().getDisplayMetrics().widthPixels
                                - supportDp(activity, 32));
                attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
                attrs.gravity = Gravity.CENTER;
                attrs.dimAmount = .72f;
                attrs.windowAnimations = 0; // No second, platform-owned window transition.
                window.setAttributes(attrs);
            }
        };
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = buildSupportContent(activity);
        dialog.setContentView(content);
        dialog.setCanceledOnTouchOutside(true);

        content.findViewById(SUPPORT_GITHUB_VIEW_ID).setOnClickListener(view -> {
            dialog.dismiss();
            openSupportLink(activity, "https://github.com/sponsors/Addy37");
        });
        content.findViewById(SUPPORT_CASH_APP_VIEW_ID).setOnClickListener(view -> {
            dialog.dismiss();
            openSupportLink(activity, "https://cash.app/$SupportZC");
        });
        content.findViewById(SUPPORT_LATER_VIEW_ID).setOnClickListener(view -> dialog.dismiss());

        dialog.setOnShowListener(ignored -> {
            // A short opacity-only entrance keeps a fixed center and fixed size.
            if (ZeroChillMotion.animationsEnabled(activity)) {
                content.setAlpha(0f);
                content.animate().alpha(1f)
                        .setDuration(ZeroChillMotion.QUICK_MS).start();
            }
        });
        return dialog;
    }

    static LinearLayout buildSupportContent(Context context) {
        int accent = AppearanceTheme.accent(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(supportDp(context, 22), supportDp(context, 22),
                supportDp(context, 22), supportDp(context, 14));

        GradientDrawable panel = new GradientDrawable();
        panel.setColor(Color.rgb(3, 6, 10));
        panel.setCornerRadius(supportDp(context, 24));
        panel.setStroke(supportDp(context, 1), (accent & 0x00FFFFFF) | 0xA0000000);
        content.setBackground(panel);
        content.setClipToOutline(true);

        LinearLayout wordmark = new LinearLayout(context);
        wordmark.setGravity(Gravity.CENTER);
        TextView zero = supportText(context, "ZERO", 24, Color.WHITE, true);
        TextView chill = supportText(context, "CHILL", 24, accent, true);
        wordmark.addView(zero);
        wordmark.addView(chill);
        content.addView(wordmark, new LinearLayout.LayoutParams(-1, -2));

        TextView title = supportText(context, "Support development", 19, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = supportDp(context, 12);
        content.addView(title, titleParams);

        TextView message = supportText(context,
                "Enjoying ZEROCHILL? You can help keep it going.", 14,
                AppearanceTheme.secondaryText(context), false);
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(supportDp(context, 2), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(-1, -2);
        messageParams.topMargin = supportDp(context, 8);
        content.addView(message, messageParams);

        TextView github = supportChoice(context, "GitHub Sponsors", true);
        github.setId(SUPPORT_GITHUB_VIEW_ID);
        content.addView(github, supportChoiceParams(context, 20));

        TextView cashApp = supportChoice(context, "Cash App ($SupportZC)", false);
        cashApp.setId(SUPPORT_CASH_APP_VIEW_ID);
        content.addView(cashApp, supportChoiceParams(context, 10));

        TextView footnote = supportText(context, "Optional  •  Opens outside the app", 11,
                AppearanceTheme.secondaryText(context), false);
        footnote.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footnoteParams = new LinearLayout.LayoutParams(-1, -2);
        footnoteParams.topMargin = supportDp(context, 14);
        content.addView(footnote, footnoteParams);

        TextView later = supportText(context, "Not now", 14,
                AppearanceTheme.secondaryText(context), true);
        later.setId(SUPPORT_LATER_VIEW_ID);
        later.setGravity(Gravity.CENTER);
        later.setMinHeight(supportDp(context, 42));
        later.setClickable(true);
        later.setFocusable(true);
        later.setContentDescription("Close Support ZEROCHILL");
        ZeroChillMotion.installPressFeedback(later);
        LinearLayout.LayoutParams laterParams = new LinearLayout.LayoutParams(-1, supportDp(context, 42));
        laterParams.topMargin = supportDp(context, 4);
        content.addView(later, laterParams);
        return content;
    }

    private static TextView supportChoice(Context context, String label, boolean primary) {
        TextView choice = supportText(context, label, 15,
                primary ? AppearanceTheme.onAccent(context) : Color.WHITE, true);
        choice.setGravity(Gravity.CENTER);
        choice.setMinHeight(supportDp(context, 50));
        choice.setClickable(true);
        choice.setFocusable(true);
        choice.setContentDescription("Open " + label + " outside ZEROCHILL");

        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? AppearanceTheme.accent(context) : Color.rgb(17, 22, 29));
        background.setCornerRadius(supportDp(context, 14));
        if (!primary) background.setStroke(supportDp(context, 1), Color.rgb(49, 62, 75));
        choice.setBackground(background);
        ZeroChillMotion.installPressFeedback(choice);
        return choice;
    }

    private static TextView supportText(Context context, String value, float size, int color,
            boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private static LinearLayout.LayoutParams supportChoiceParams(Context context, int topDp) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, supportDp(context, 50));
        params.topMargin = supportDp(context, topDp);
        return params;
    }

    private static int supportDp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void openSupportLink(Activity activity, String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException | SecurityException ignored) {
            ZeroChillToast.makeText(activity, "Couldn't open support link.",
                    ZeroChillToast.LENGTH_SHORT).show();
        }
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

        TextView title = text(activity, "ZEROCHILL", 23, Color.WHITE, false);
        SpannableString wordmark = new SpannableString("ZEROCHILL");
        wordmark.setSpan(new ForegroundColorSpan(AppearanceTheme.accent(activity)),
                4, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setText(wordmark);
        TextView subtitle = text(activity, "Version " + BuildConfig.VERSION_NAME,
                13, AppearanceTheme.secondaryText(activity), false);
        labels.addView(title);
        labels.addView(subtitle);
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView close = iconView(activity, R.drawable.ic_more_close, 48, 15);
        close.setContentDescription("Close More");
        close.setOnClickListener(v -> dialog.dismiss());
        ZeroChillMotion.installPressFeedback(close);
        header.addView(close, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));

        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));
    }

    // Shared layout builder, allowing focused UI tests without starting the media pager.
    static TextView populateActions(Activity activity, Dialog dialog,
            LinearLayout content, List<Action> actions) {
        TextView downloadBadge = null;
        for (int i = 0; i < 4; i += 2) {
            LinearLayout gridRow = new LinearLayout(activity);
            gridRow.setOrientation(LinearLayout.HORIZONTAL);
            gridRow.setBaselineAligned(false);
            for (int j = i; j < i + 2; j++) {
                TextView badge = addActionTile(activity, dialog, gridRow, actions.get(j), j == i);
                if (j == 0) downloadBadge = badge;
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.topMargin = dp(activity, i == 0 ? 10 : 8);
            content.addView(gridRow, rowParams);
        }
        addDivider(activity, content);
        for (int i = 4; i < actions.size(); i++) {
            addActionRow(activity, dialog, content, actions.get(i));
        }
        return downloadBadge;
    }

    private static TextView addActionTile(Activity activity, Dialog dialog,
            LinearLayout row, Action action, boolean first) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 12));
        tile.setMinimumHeight(dp(activity, 82));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(21, 21, 25));
        background.setCornerRadius(dp(activity, 17));
        tile.setBackground(background);
        tile.setClipToOutline(true);

        FrameLayout icons = new FrameLayout(activity);
        ImageView icon = iconView(activity, action.iconRes, 30, 3);
        icons.addView(icon, new FrameLayout.LayoutParams(dp(activity, 30), dp(activity, 30),
                Gravity.START | Gravity.CENTER_VERTICAL));
        TextView badge = text(activity, "", 11, AppearanceTheme.onAccent(activity), true);
        badge.setGravity(Gravity.CENTER);
        badge.setMinWidth(dp(activity, 23));
        badge.setPadding(dp(activity, 6), 0, dp(activity, 6), 0);
        badge.setBackground(ZeroChillUi.rounded(activity, AppearanceTheme.accent(activity),
                Color.TRANSPARENT, R.dimen.zc_radius_pill));
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        icons.addView(badge, new FrameLayout.LayoutParams(-2, dp(activity, 22),
                Gravity.END | Gravity.CENTER_VERTICAL));
        updateBadge(badge, action.badgeCount);
        tile.addView(icons, new LinearLayout.LayoutParams(-1, -2));

        TextView title = text(activity, action.title, 16, Color.WHITE, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = dp(activity, 8);
        tile.addView(title, labelParams);
        bindAction(activity, dialog, tile, action);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        if (first) params.setMarginEnd(dp(activity, 8));
        row.addView(tile, params);
        return badge;
    }

    private static void updateBadge(TextView badge, int count) {
        badge.setText(count > 99 ? "99+" : String.valueOf(count));
        badge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
    }

    private static void addActionRow(Activity activity, Dialog dialog,
            LinearLayout parent, Action action) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(activity, 48));
        row.setPadding(dp(activity, 4), dp(activity, 6), dp(activity, 4), dp(activity, 6));
        ImageView icon = iconView(activity, action.iconRes, 26, 4);
        row.addView(icon, new LinearLayout.LayoutParams(dp(activity, 26), dp(activity, 32)));
        TextView title = text(activity, action.title, 15, Color.WHITE, false);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.setMarginStart(dp(activity, 10));
        row.addView(title, titleParams);
        ImageView chevron = iconView(activity, R.drawable.ic_more_chevron, 24, 5);
        chevron.setImageTintList(ColorStateList.valueOf(AppearanceTheme.mutedText(activity)));
        row.addView(chevron, new LinearLayout.LayoutParams(dp(activity, 24), dp(activity, 32)));
        bindAction(activity, dialog, row, action);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private static void bindAction(Activity activity, Dialog dialog, View view, Action action) {
        view.setClickable(true);
        view.setFocusable(true);
        view.setContentDescription(action.title + ". " + action.subtitle);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(activity, 17));
        view.setForeground(new RippleDrawable(ColorStateList.valueOf(
                (AppearanceTheme.accent(activity) & 0x00FFFFFF) | 0x33000000), null, mask));
        ZeroChillMotion.installPressFeedback(view);
        view.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if ("Support ZEROCHILL".equals(action.title)) {
                dialog.setOnDismissListener(ignored -> {
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        activity.getWindow().getDecorView().post(action.run);
                    }
                });
                dialog.dismiss();
            } else {
                dialog.dismiss();
                action.run.run();
            }
        });
    }

    private static void addDivider(Activity activity, LinearLayout parent) {
        View divider = new View(activity);
        divider.setBackgroundColor(AppearanceTheme.outline(activity));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(activity, 1));
        params.setMargins(0, dp(activity, 14), 0, dp(activity, 6));
        parent.addView(divider, params);
    }

    private static GradientDrawable compactPanelBackground(NativeMainActivity activity) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(10, 10, 12));
        background.setCornerRadius(dp(activity, 23));
        background.setStroke(dp(activity, 1), Color.rgb(54, 54, 60));
        return background;
    }

    private static ImageView iconView(
            Activity activity,
            int iconRes,
            int sizeDp,
            int paddingDp
    ) {
        ImageView view = new ImageView(activity);
        view.setImageResource(iconRes);
        view.setImageTintList(ColorStateList.valueOf(AppearanceTheme.accent(activity)));
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
            Activity activity,
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

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    static final class Action {
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
