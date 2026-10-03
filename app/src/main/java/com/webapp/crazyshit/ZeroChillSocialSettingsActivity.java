package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.util.ArrayList;

/** Account-scoped in-app alerts and blocked-user management. */
public final class ZeroChillSocialSettingsActivity extends Activity {
    static final String EXTRA_MODE = "zerochill_social_settings_mode";
    static final String MODE_NOTIFICATIONS = "notifications";
    static final String MODE_BLOCKED = "blocked";

    private LinearLayout content;
    private ProgressBar progress;
    private String accountId = "";
    private String mode = MODE_NOTIFICATIONS;
    private int generation;
    private boolean saving;
    private boolean updatingControls;
    private ZeroChillNotificationPreferences.Values preferenceValues;
    private final ArrayList<Switch> controls = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ZeroChillUi.applySystemBars(this);
        mode = MODE_BLOCKED.equals(getIntent().getStringExtra(EXTRA_MODE))
                ? MODE_BLOCKED : MODE_NOTIFICATIONS;
        buildShell();
        ResponsiveFitmentController.applySoon(this);
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!accountId.equals(ZeroChillSessionStore.currentUserId(this))) refresh();
    }

    @Override protected void onDestroy() {
        generation++;
        ResponsiveFitmentController.release(this);
        super.onDestroy();
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), 0, dp(20), 0);
        header.setMinimumHeight(dp(58));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));
        TextView back = label("‹", 31, Color.WHITE, false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        ZeroChillMotion.installPressFeedback(back);
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = label(MODE_BLOCKED.equals(mode) ? "Blocked users" : "Notification preferences",
                19, Color.WHITE, true);
        title.setPadding(dp(6), dp(12), 0, dp(12));
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        FrameLayout body = new FrameLayout(this);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        body.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(32));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        progress = new ProgressBar(this);
        ZeroChillUi.styleProgress(progress);
        FrameLayout.LayoutParams loading = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER);
        body.addView(progress, loading);
        setContentView(root);
    }

    private void refresh() {
        final int request = ++generation;
        accountId = ZeroChillSessionStore.currentUserId(this);
        content.removeAllViews();
        saving = false;
        if (accountId.isEmpty()) {
            progress.setVisibility(View.GONE);
            content.addView(label("Sign in to manage your social settings.", 14,
                    Color.rgb(174, 188, 198), false));
            TextView signIn = action("SIGN IN");
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
            params.topMargin = dp(16);
            content.addView(signIn, params);
            signIn.setOnClickListener(v -> startActivity(new Intent(this, ZeroChillAccountActivity.class)));
            return;
        }
        progress.setVisibility(View.VISIBLE);
        if (MODE_BLOCKED.equals(mode)) {
            ZeroChillSocialRepository.loadBlockedUsers(this, (users, error) ->
                    runOnUiThread(() -> {
                        if (!valid(request)) return;
                        progress.setVisibility(View.GONE);
                        if (error != null) showError(error, this::refresh);
                        else renderBlocked(users == null ? new ArrayList<>() : users, request);
                    }));
        } else {
            ZeroChillNotificationPreferences.load(this, (values, error) ->
                    runOnUiThread(() -> {
                        if (!valid(request)) return;
                        progress.setVisibility(View.GONE);
                        if (error != null) showError(error, this::refresh);
                        else renderPreferences(values);
                    }));
        }
    }

    private boolean valid(int request) {
        return !isFinishing() && !isDestroyed() && request == generation
                && accountId.equals(ZeroChillSessionStore.currentUserId(this));
    }

    private void renderBlocked(ArrayList<ZeroChillSocialRepository.PublicProfile> users, int request) {
        content.removeAllViews();
        if (users.isEmpty()) {
            content.addView(label("You haven't blocked anyone.", 14,
                    Color.rgb(174, 188, 198), false));
            return;
        }
        section("BLOCKED USERS");
        for (ZeroChillSocialRepository.PublicProfile profile : users) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(11), dp(10), dp(11));
            row.setBackground(panel());
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.bottomMargin = dp(9);
            content.addView(row, rowParams);
            ImageView avatar = new ImageView(this);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            AccountAvatarImages.track(avatar, profile.userId);
            String url = ZeroChillAccountRepository.avatarUrl(profile.avatarPath);
            if (url.isEmpty()) avatar.setImageResource(R.drawable.ic_more_account);
            else AccountAvatarImages.bind(avatar, profile.userId, profile.avatarPath);
            avatar.setBackground(circle());
            avatar.setClipToOutline(true);
            avatar.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            row.addView(avatar, new LinearLayout.LayoutParams(dp(45), dp(45)));
            LinearLayout names = new LinearLayout(this);
            names.setOrientation(LinearLayout.VERTICAL);
            names.setPadding(dp(10), 0, dp(5), 0);
            row.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
            names.addView(label(SocialUi.name(profile.displayName, profile.username), 14,
                    Color.WHITE, true));
            if (!profile.displayName.isEmpty()) names.addView(label(SocialUi.cleanName(profile.username),
                    11, Color.rgb(155, 171, 182), false));
            TextView unblock = label("Unblock", 12, UiPalette.PRIMARY, true);
            unblock.setGravity(Gravity.CENTER);
            unblock.setPadding(dp(8), 0, dp(8), 0);
            row.addView(unblock, new LinearLayout.LayoutParams(-2, dp(48)));
            ZeroChillMotion.installPressFeedback(unblock);
            unblock.setOnClickListener(v -> {
                unblock.setEnabled(false);
                ZeroChillSocialRepository.setBlocked(this, profile.userId, false, (blocked, error) ->
                        runOnUiThread(() -> {
                            if (!valid(request)) return;
                            if (error != null) {
                                unblock.setEnabled(true);
                                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                                return;
                            }
                            users.remove(profile);
                            if (ZeroChillMotion.animationsEnabled(this)) {
                                row.animate().alpha(0f).translationX(dp(22))
                                        .setDuration(ZeroChillMotion.QUICK_MS)
                                        .withEndAction(() -> { if (valid(request)) renderBlocked(users, request); })
                                        .start();
                            } else renderBlocked(users, request);
                        }));
            });
        }
    }

    private void renderPreferences(ZeroChillNotificationPreferences.Values values) {
        content.removeAllViews();
        if (values == null) return;
        preferenceValues = values;
        controls.clear();
        TextView note = label("These settings control new in-app activity and alerts. Existing notification history stays in place.",
                12, Color.rgb(155, 171, 182), false);
        note.setLineSpacing(dp(3), 1f);
        content.addView(note);
        section("SOCIAL");
        toggle("Replies to my comments", values.replies, enabled -> save(new ZeroChillNotificationPreferences.Values(
                enabled, preferenceValues.likes, preferenceValues.directMessages,
                preferenceValues.creatorUpdates, preferenceValues.appUpdates)));
        toggle("Likes on my comments", values.likes, enabled -> save(new ZeroChillNotificationPreferences.Values(
                preferenceValues.replies, enabled, preferenceValues.directMessages,
                preferenceValues.creatorUpdates, preferenceValues.appUpdates)));
        TextView dmNote = label("Messages remain in your inbox. Social push alerts are not available.", 12,
                Color.rgb(130, 145, 155), false);
        LinearLayout.LayoutParams dmParams = new LinearLayout.LayoutParams(-1, -2);
        dmParams.topMargin = dp(9);
        content.addView(dmNote, dmParams);
        section("CREATORS");
        toggle("Favorite creator updates", values.creatorUpdates, enabled -> save(new ZeroChillNotificationPreferences.Values(
                preferenceValues.replies, preferenceValues.likes, preferenceValues.directMessages,
                enabled, preferenceValues.appUpdates)));
        section("APP");
        toggle("ZEROCHILL app updates", values.appUpdates, enabled -> save(new ZeroChillNotificationPreferences.Values(
                preferenceValues.replies, preferenceValues.likes, preferenceValues.directMessages,
                preferenceValues.creatorUpdates, enabled)));
    }

    private void save(ZeroChillNotificationPreferences.Values values) {
        if (saving) return;
        saving = true;
        final int request = generation;
        setControlsEnabled(false);
        ZeroChillNotificationPreferences.save(this, values, (saved, error) -> runOnUiThread(() -> {
            if (!valid(request)) return;
            saving = false;
            if (error != null) {
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                applyChecks(preferenceValues);
            } else {
                preferenceValues = saved;
                applyChecks(saved);
            }
            setControlsEnabled(true);
        }));
    }

    private void setControlsEnabled(boolean enabled) {
        for (Switch control : controls) {
            control.setEnabled(enabled);
            if (control.getParent() instanceof View) ((View) control.getParent()).setEnabled(enabled);
        }
    }

    private void applyChecks(ZeroChillNotificationPreferences.Values values) {
        if (values == null || controls.size() != 4) return;
        updatingControls = true;
        controls.get(0).setChecked(values.replies);
        controls.get(1).setChecked(values.likes);
        controls.get(2).setChecked(values.creatorUpdates);
        controls.get(3).setChecked(values.appUpdates);
        updatingControls = false;
    }

    private interface ToggleAction { void changed(boolean enabled); }

    private void toggle(String title, boolean checked, ToggleAction action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(15), dp(8), dp(9), dp(8));
        row.setBackground(panel());
        row.setMinimumHeight(dp(60));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        content.addView(row, params);
        TextView name = label(title, 14, Color.WHITE, false);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        toggle.setContentDescription(title);
        row.addView(toggle);
        controls.add(toggle);
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        ZeroChillMotion.installPressFeedback(row);
        toggle.setOnCheckedChangeListener((button, enabled) -> {
            if (!updatingControls && !saving) action.changed(enabled);
        });
    }

    private void section(String title) {
        TextView label = label(title, 11, UiPalette.PRIMARY, true);
        label.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(23);
        params.bottomMargin = dp(10);
        content.addView(label, params);
    }

    private void showError(Exception error, Runnable retry) {
        content.removeAllViews();
        content.addView(label(error.getMessage() == null ? "Unable to load settings." : error.getMessage(),
                14, Color.rgb(174, 188, 198), false));
        TextView again = action("TRY AGAIN");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.topMargin = dp(16);
        content.addView(again, params);
        again.setOnClickListener(v -> retry.run());
    }

    private TextView action(String value) {
        TextView view = label(value, 12, Color.rgb(5, 19, 28), true);
        view.setGravity(Gravity.CENTER);
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiPalette.PRIMARY);
        background.setCornerRadius(dp(16));
        view.setBackground(background);
        ZeroChillMotion.installPressFeedback(view);
        return view;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) text.setTypeface(null, android.graphics.Typeface.BOLD);
        return text;
    }

    private GradientDrawable panel() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.rgb(15, 19, 24));
        drawable.setCornerRadius(dp(16));
        drawable.setStroke(dp(1), Color.rgb(36, 54, 65));
        return drawable;
    }

    private GradientDrawable circle() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(Color.rgb(22, 31, 38));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
