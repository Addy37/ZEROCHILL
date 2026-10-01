package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

/** One-time 4.3 upgrade announcement for existing users who have not created a ZEROCHILL ID. */
final class ZeroChillIdUpgradeAnnouncement {
    private static final String PREFS = "app_prefs";
    private static final String PREF_SEEN = "zerochill_id_43_announcement_seen";
    private static final int MIN_VERSION_CODE = 4_003_000;
    private static final long UPDATE_CLOCK_SLOP_MS = 1_000L;

    private ZeroChillIdUpgradeAnnouncement() {
    }

    static boolean shouldShow(Context context) {
        if (context == null) return false;

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean configured = ZeroChillAccountRepository.isConfigured();
        boolean hasStoredSession = ZeroChillAccountRepository.hasStoredSession(context);
        long firstInstallTime = 0L;
        long lastUpdateTime = 0L;

        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(
                    context.getPackageName(),
                    0
            );
            firstInstallTime = info.firstInstallTime;
            lastUpdateTime = info.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        return shouldShowFromState(
                BuildConfig.VERSION_CODE,
                prefs.getBoolean(PREF_SEEN, false),
                configured,
                hasStoredSession,
                firstInstallTime,
                lastUpdateTime
        );
    }

    static boolean shouldShowFromState(
            int versionCode,
            boolean seen,
            boolean configured,
            boolean hasStoredSession,
            long firstInstallTime,
            long lastUpdateTime
    ) {
        if (versionCode < MIN_VERSION_CODE || seen || !configured || hasStoredSession) return false;
        if (firstInstallTime <= 0L || lastUpdateTime <= 0L) return false;
        return lastUpdateTime - firstInstallTime > UPDATE_CLOCK_SLOP_MS;
    }

    static boolean maybeShow(Activity activity, Runnable afterDismiss) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || !shouldShow(activity)) {
            return false;
        }

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        MaterialCardView card = new MaterialCardView(activity);
        ZeroChillUi.styleMaterialCard(card, R.dimen.zc_radius_large);
        card.setCardBackgroundColor(Color.rgb(10, 13, 17));
        card.setStrokeColor(Color.argb(205, 8, 146, 208));
        card.setStrokeWidth(dp(activity, 1));
        card.setCardElevation(dp(activity, 10));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 22), dp(activity, 21), dp(activity, 22), dp(activity, 18));
        card.addView(content, new MaterialCardView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout hero = new LinearLayout(activity);
        hero.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_more_account);
        icon.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        icon.setPadding(dp(activity, 11), dp(activity, 11), dp(activity, 11), dp(activity, 11));
        icon.setBackground(circle(UiPalette.PRIMARY));
        hero.addView(icon, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));

        LinearLayout heroText = new LinearLayout(activity);
        heroText.setOrientation(LinearLayout.VERTICAL);
        heroText.setPadding(dp(activity, 13), 0, 0, 0);

        TextView eyebrow = text(activity, "NEW IN 4.3", 10f, UiPalette.PRIMARY, true);
        eyebrow.setLetterSpacing(0.10f);
        heroText.addView(eyebrow);

        TextView wordmark = text(activity, "ZEROCHILL ID", 23f, Color.WHITE, true);
        heroText.addView(wordmark);

        hero.addView(heroText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(hero);

        TextView title = text(activity, "Your account. Your social side.", 18f, Color.WHITE, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(activity, 20);
        content.addView(title, titleParams);

        TextView body = text(
                activity,
                "Create a ZEROCHILL ID to join conversations and keep your social activity connected across the app.",
                13.5f,
                ZeroChillUi.color(activity, R.color.zc_text_secondary),
                false
        );
        body.setLineSpacing(0f, 1.12f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.topMargin = dp(activity, 7);
        bodyParams.bottomMargin = dp(activity, 15);
        content.addView(body, bodyParams);

        content.addView(feature(activity, "COMMENT & LIKE", "Reply, react, and like videos with your ZEROCHILL ID."));
        content.addView(feature(activity, "MESSAGE", "Start private conversations with other ZEROCHILL users."));
        content.addView(feature(activity, "SYNC", "Keep creator favorites and account preferences tied to you."));

        MaterialButton create = button(activity, "CREATE ACCOUNT", true);
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(-1, dp(activity, 48));
        createParams.topMargin = dp(activity, 18);
        content.addView(create, createParams);

        MaterialButton signIn = button(activity, "SIGN IN", false);
        LinearLayout.LayoutParams signInParams = new LinearLayout.LayoutParams(-1, dp(activity, 46));
        signInParams.topMargin = dp(activity, 8);
        content.addView(signIn, signInParams);

        TextView later = text(
                activity,
                "Maybe later",
                12.5f,
                ZeroChillUi.color(activity, R.color.zc_text_muted),
                true
        );
        later.setGravity(Gravity.CENTER);
        later.setMinHeight(dp(activity, 44));
        later.setPadding(dp(activity, 8), dp(activity, 11), dp(activity, 8), dp(activity, 9));
        later.setClickable(true);
        later.setFocusable(true);
        ZeroChillMotion.installPressFeedback(later);
        content.addView(later, new LinearLayout.LayoutParams(-1, dp(activity, 44)));

        dialog.setContentView(card);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(ignored -> {
            markSeen(activity);
            if (afterDismiss != null && !activity.isFinishing() && !activity.isDestroyed()) {
                afterDismiss.run();
            }
        });

        View.OnClickListener createAction = v -> {
            dialog.dismiss();
            Intent account = new Intent(activity, ZeroChillAccountActivity.class);
            account.putExtra(ZeroChillAccountActivity.EXTRA_START_CREATE, true);
            activity.startActivity(account);
        };
        View.OnClickListener signInAction = v -> {
            dialog.dismiss();
            activity.startActivity(new Intent(activity, ZeroChillAccountActivity.class));
        };
        create.setOnClickListener(createAction);
        signIn.setOnClickListener(signInAction);
        later.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = Math.min(
                    activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 28),
                    dp(activity, 420)
            );
            attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attrs.gravity = Gravity.CENTER;
            attrs.dimAmount = 0.66f;
            window.setAttributes(attrs);
        }

        card.setAlpha(0f);
        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        if (ZeroChillMotion.animationsEnabled(activity)) {
            card.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(ZeroChillMotion.STANDARD_MS)
                    .start();
        } else {
            card.setAlpha(1f);
            card.setScaleX(1f);
            card.setScaleY(1f);
        }
        return true;
    }

    private static View feature(Activity activity, String title, String description) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(activity, 5), 0, dp(activity, 5));

        View dot = new View(activity);
        dot.setBackground(circle(UiPalette.PRIMARY));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(activity, 7), dp(activity, 7));
        dotParams.topMargin = dp(activity, 6);
        dotParams.rightMargin = dp(activity, 11);
        row.addView(dot, dotParams);

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(activity, title, 11f, Color.WHITE, true));
        TextView detail = text(
                activity,
                description,
                12f,
                ZeroChillUi.color(activity, R.color.zc_text_secondary),
                false
        );
        detail.setLineSpacing(0f, 1.08f);
        labels.addView(detail);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private static MaterialButton button(Activity activity, String label, boolean primary) {
        MaterialButton button = new MaterialButton(activity);
        button.setText(label);
        button.setTextSize(12f);
        button.setTypeface(null, Typeface.BOLD);
        button.setAllCaps(false);
        button.setCornerRadius(dp(activity, 14));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinHeight(0);
        button.setTextColor(primary ? Color.WHITE : ZeroChillUi.color(activity, R.color.zc_text_primary));
        button.setBackgroundTintList(ColorStateList.valueOf(
                primary ? UiPalette.PRIMARY : Color.rgb(22, 27, 33)
        ));
        button.setStrokeWidth(dp(activity, 1));
        button.setStrokeColor(ColorStateList.valueOf(
                primary ? UiPalette.PRIMARY : ZeroChillUi.color(activity, R.color.zc_divider)
        ));
        ZeroChillMotion.installPressFeedback(button);
        return button;
    }

    private static TextView text(Activity activity, String value, float size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private static GradientDrawable circle(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private static void markSeen(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_SEEN, true)
                .apply();
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
