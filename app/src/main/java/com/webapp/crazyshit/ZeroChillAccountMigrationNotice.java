package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** One-time account-migration notice for existing installs of ZEROCHILL 7. */
final class ZeroChillAccountMigrationNotice {
    private static final String PREFS = "app_prefs";
    private static final String PREF_SEEN = "zerochill_7_account_migration_notice_seen";

    private ZeroChillAccountMigrationNotice() {}

    static boolean shouldShow(Context context) {
        if (context == null || !BuildConfig.ZEROCHILL_V7_TRAFFIC) return false;

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long firstInstallTime = 0L;
        long lastUpdateTime = 0L;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            firstInstallTime = info.firstInstallTime;
            lastUpdateTime = info.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }

        return shouldShowFromState(
                BuildConfig.ZEROCHILL_V7_TRAFFIC,
                prefs.getBoolean(PREF_SEEN, false),
                ZeroChillAccountRepository.isConfigured(),
                ZeroChillAccountRepository.hasStoredSession(context),
                firstInstallTime,
                lastUpdateTime
        );
    }

    static boolean shouldShowFromState(
            boolean v7Backend,
            boolean seen,
            boolean configured,
            boolean hasCurrentAccountSession,
            long firstInstallTime,
            long lastUpdateTime
    ) {
        return v7Backend && !seen && configured && !hasCurrentAccountSession
                && WhatsNewPolicy.isUpgrade(firstInstallTime, lastUpdateTime);
    }

    static boolean maybeShow(Activity activity, Runnable afterDismiss) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()
                || !shouldShow(activity)) return false;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 24), dp(activity, 24), dp(activity, 24), dp(activity, 17));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(6, 9, 13));
        background.setCornerRadius(dp(activity, 24));
        background.setStroke(dp(activity, 1), AppearanceTheme.accent(activity));
        content.setBackground(background);
        scroll.addView(content);

        TextView eyebrow = text(activity, "ZEROCHILL 7.0", 12,
                AppearanceTheme.accent(activity), true);
        eyebrow.setLetterSpacing(.10f);
        content.addView(eyebrow);

        TextView title = text(activity, "Your ZEROCHILL ID needs a fresh start", 22,
                Color.WHITE, true);
        title.setPadding(0, dp(activity, 13), 0, dp(activity, 9));
        content.addView(title);

        TextView details = text(activity,
                "We've moved to a new account system. Your previous ZEROCHILL account "
                        + "cannot sign in to 7.0. To keep using comments, likes and messages, "
                        + "create a new account. You can use the same email address.",
                14, AppearanceTheme.secondaryText(activity), false);
        details.setLineSpacing(dp(activity, 3), 1f);
        content.addView(details);

        TextView localData = text(activity,
                "Your on-device favorites, history, downloads and settings remain on "
                        + "this phone when you update. Do not uninstall ZEROCHILL.",
                13, AppearanceTheme.secondaryText(activity), false);
        localData.setPadding(0, dp(activity, 15), 0, dp(activity, 4));
        localData.setLineSpacing(dp(activity, 2), 1f);
        content.addView(localData);

        TextView create = button(activity, "CREATE NEW ACCOUNT", true);
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(-1, dp(activity, 50));
        createParams.topMargin = dp(activity, 22);
        content.addView(create, createParams);

        TextView signIn = button(activity, "I already have a 7.0 account", false);
        LinearLayout.LayoutParams signInParams = new LinearLayout.LayoutParams(-1, dp(activity, 46));
        signInParams.topMargin = dp(activity, 8);
        content.addView(signIn, signInParams);

        TextView later = text(activity, "Maybe later", 13,
                AppearanceTheme.secondaryText(activity), true);
        later.setGravity(Gravity.CENTER);
        later.setClickable(true);
        later.setFocusable(true);
        ZeroChillMotion.installPressFeedback(later);
        LinearLayout.LayoutParams laterParams = new LinearLayout.LayoutParams(-1, dp(activity, 44));
        laterParams.topMargin = dp(activity, 4);
        content.addView(later, laterParams);

        dialog.setContentView(scroll);
        dialog.setCanceledOnTouchOutside(false);

        final boolean[] openingAccount = {false};
        dialog.setOnDismissListener(ignored -> {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(PREF_SEEN, true).apply();
            if (!openingAccount[0] && afterDismiss != null
                    && !activity.isFinishing() && !activity.isDestroyed()) {
                afterDismiss.run();
            }
        });

        create.setOnClickListener(v -> {
            openingAccount[0] = true;
            dialog.dismiss();
            Intent account = new Intent(activity, ZeroChillAccountActivity.class);
            account.putExtra(ZeroChillAccountActivity.EXTRA_START_CREATE, true);
            activity.startActivity(account);
        });
        signIn.setOnClickListener(v -> {
            openingAccount[0] = true;
            dialog.dismiss();
            activity.startActivity(new Intent(activity, ZeroChillAccountActivity.class));
        });
        later.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            int width = Math.min(dp(activity, 420),
                    activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 28));
            int maxHeight = Math.round(activity.getResources().getDisplayMetrics().heightPixels * .85f);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = width;
            attrs.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            attrs.dimAmount = .72f;
            attrs.gravity = Gravity.CENTER;
            window.setAttributes(attrs);
            View decor = window.getDecorView();
            decor.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST)
            );
            window.setLayout(width, Math.min(decor.getMeasuredHeight(), maxHeight));
        }
        return true;
    }

    private static TextView text(Activity activity, String value, float size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private static TextView button(Activity activity, String label, boolean primary) {
        TextView view = text(activity, label, 14,
                primary ? AppearanceTheme.onAccent(activity) : Color.WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        view.setFocusable(true);
        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? AppearanceTheme.accent(activity) : Color.rgb(29, 35, 42));
        background.setCornerRadius(dp(activity, 15));
        view.setBackground(background);
        ZeroChillMotion.installPressFeedback(view);
        return view;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
