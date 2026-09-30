package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Public ZeroChill identity shown from comments and other social surfaces. */
public final class ZeroChillPublicProfileActivity extends Activity {
    static final String EXTRA_USER_ID = "zerochill_profile_user_id";

    private LinearLayout content;
    private ProgressBar progress;
    private String userId = "";
    private ZeroChillSocialRepository.PublicProfile currentProfile;
    private boolean blockedByMe;
    private TextView blockButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ZeroChillUi.applySystemBars(this);
        userId = clean(getIntent().getStringExtra(EXTRA_USER_ID));
        buildShell();
        ResponsiveFitmentController.applySoon(this);
        loadProfile();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentProfile != null && !currentProfile.currentUser) loadBlockState();
    }

    @Override
    protected void onDestroy() {
        ResponsiveFitmentController.release(this);
        super.onDestroy();
    }

    private void buildShell() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(10), dp(3), dp(14), dp(3));
        top.setBackground(headerBackground());
        shell.addView(top, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView back = text("‹", 31, Color.WHITE, false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        ZeroChillMotion.installPressFeedback(back);
        top.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(6), 0, 0, 0);
        top.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        titles.addView(text("Profile", 19, Color.WHITE, true));
        TextView subtitle = text("ZEROCHILL ID", 9, ZeroChillUi.color(this, R.color.zc_text_muted), true);
        subtitle.setLetterSpacing(0.10f);
        titles.addView(subtitle);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(28), dp(22), dp(34));
        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        progress = new ProgressBar(this);
        ZeroChillUi.styleProgress(progress);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(dp(42), dp(42));
        loadingParams.gravity = Gravity.CENTER;
        root.addView(progress, loadingParams);

        setContentView(root);
    }

    private void loadProfile() {
        showBusy(true);
        ZeroChillSocialRepository.loadProfile(this, userId, (profile, error) -> runOnUiThread(() -> {
            showBusy(false);
            if (error != null || profile == null) {
                showError(error == null ? "This profile is unavailable." : error.getMessage());
                return;
            }
            render(profile);
        }));
    }

    private void render(ZeroChillSocialRepository.PublicProfile profile) {
        currentProfile = profile;
        content.removeAllViews();

        TextView eyebrow = text("ZEROCHILL MEMBER", 10, UiPalette.PRIMARY, true);
        eyebrow.setLetterSpacing(0.12f);
        content.addView(eyebrow);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(18), dp(22), dp(18), dp(20));
        card.setBackground(panelBackground());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.topMargin = dp(10);
        content.addView(card, cardParams);

        FrameLayout halo = new FrameLayout(this);
        halo.setBackground(circle(UiPalette.PRIMARY_CONTAINER));
        halo.setPadding(dp(3), dp(3), dp(3), dp(3));
        card.addView(halo, new LinearLayout.LayoutParams(dp(112), dp(112)));

        ImageView avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(circle(Color.rgb(12, 14, 18)));
        String avatarUrl = ZeroChillAccountRepository.avatarUrl(profile.avatarPath);
        if (avatarUrl.isEmpty()) {
            avatar.setImageResource(R.drawable.ic_more_account);
            avatar.setPadding(dp(28), dp(28), dp(28), dp(28));
            avatar.setColorFilter(UiPalette.PRIMARY);
        } else {
            Glide.with(avatar).load(avatarUrl).circleCrop().into(avatar);
        }
        halo.addView(avatar, new FrameLayout.LayoutParams(-1, -1));

        if (!profile.displayName.isEmpty()) {
            TextView display = text(profile.displayName, 22, Color.WHITE, true);
            LinearLayout.LayoutParams displayParams = new LinearLayout.LayoutParams(-2, -2);
            displayParams.topMargin = dp(15);
            card.addView(display, displayParams);
        }

        TextView username = text("@" + profile.username, profile.displayName.isEmpty() ? 23 : 14,
                profile.displayName.isEmpty() ? Color.WHITE : UiPalette.PRIMARY, true);
        LinearLayout.LayoutParams userParams = new LinearLayout.LayoutParams(-2, -2);
        userParams.topMargin = dp(profile.displayName.isEmpty() ? 15 : 3);
        card.addView(username, userParams);

        TextView joined = text(joinedLabel(profile.createdAt), 11,
                ZeroChillUi.color(this, R.color.zc_text_muted), false);
        LinearLayout.LayoutParams joinedParams = new LinearLayout.LayoutParams(-2, -2);
        joinedParams.topMargin = dp(7);
        card.addView(joined, joinedParams);

        if (profile.currentUser) {
            TextView edit = button("EDIT MY PROFILE");
            edit.setOnClickListener(v -> startActivity(new Intent(this, ZeroChillAccountActivity.class)));
            LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(-1, dp(48));
            editParams.topMargin = dp(14);
            content.addView(edit, editParams);
        } else {
            TextView message = button("MESSAGE");
            message.setOnClickListener(v -> openMessage(profile));
            LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(-1, dp(48));
            messageParams.topMargin = dp(14);
            content.addView(message, messageParams);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, dp(44));
            actionsParams.topMargin = dp(8);
            content.addView(actions, actionsParams);

            blockButton = secondaryButton("BLOCK");
            blockButton.setOnClickListener(v -> {
                if (!requireSignedIn()) return;
                confirmBlock(!blockedByMe);
            });
            actions.addView(blockButton, new LinearLayout.LayoutParams(0, -1, 1f));

            TextView report = secondaryButton("REPORT");
            report.setTextColor(Color.rgb(228, 138, 138));
            report.setOnClickListener(v -> {
                if (!requireSignedIn()) return;
                showReportDialog();
            });
            LinearLayout.LayoutParams reportParams = new LinearLayout.LayoutParams(0, -1, 1f);
            reportParams.setMarginStart(dp(8));
            actions.addView(report, reportParams);

            LinearLayout note = new LinearLayout(this);
            note.setOrientation(LinearLayout.VERTICAL);
            note.setPadding(dp(15), dp(13), dp(15), dp(13));
            note.setBackground(panelBackground());
            TextView label = text("SOCIAL IDENTITY", 10, UiPalette.PRIMARY, true);
            label.setLetterSpacing(0.08f);
            note.addView(label);
            TextView body = text(
                    "ZEROCHILL comments, replies, likes, and private messages use this identity.",
                    12,
                    ZeroChillUi.color(this, R.color.zc_text_secondary),
                    false
            );
            body.setLineSpacing(0f, 1.10f);
            LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
            bodyParams.topMargin = dp(5);
            note.addView(body, bodyParams);
            LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
            noteParams.topMargin = dp(14);
            content.addView(note, noteParams);

            loadBlockState();
        }
    }

    private void openMessage(ZeroChillSocialRepository.PublicProfile profile) {
        if (!requireSignedIn()) return;
        Intent intent = new Intent(this, ZeroChillMessageActivity.class);
        intent.putExtra(ZeroChillMessageActivity.EXTRA_USER_ID, profile.userId);
        startActivity(intent);
    }

    private boolean requireSignedIn() {
        if (ZeroChillAccountRepository.hasStoredSession(this)) return true;
        startActivity(new Intent(this, ZeroChillAccountActivity.class));
        return false;
    }

    private void loadBlockState() {
        if (!ZeroChillAccountRepository.hasStoredSession(this) || currentProfile == null) return;
        ZeroChillSocialRepository.blockState(this, currentProfile.userId, (blocked, error) ->
                runOnUiThread(() -> {
                    if (error != null) return;
                    blockedByMe = Boolean.TRUE.equals(blocked);
                    if (blockButton != null) {
                        blockButton.setText(blockedByMe ? "UNBLOCK" : "BLOCK");
                    }
                })
        );
    }

    private void confirmBlock(boolean block) {
        if (currentProfile == null) return;
        String username = "@" + currentProfile.username;
        new AlertDialog.Builder(this)
                .setTitle(block ? "Block " + username + "?" : "Unblock " + username + "?")
                .setMessage(block
                        ? "You won't be able to message each other while blocked."
                        : "You and this user will be able to message each other again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(block ? "Block" : "Unblock", (dialog, which) ->
                        ZeroChillSocialRepository.setBlocked(
                                this,
                                currentProfile.userId,
                                block,
                                (value, error) -> runOnUiThread(() -> {
                                    if (error != null) {
                                        Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                                        return;
                                    }
                                    blockedByMe = Boolean.TRUE.equals(value);
                                    if (blockButton != null) {
                                        blockButton.setText(blockedByMe ? "UNBLOCK" : "BLOCK");
                                    }
                                    Toast.makeText(
                                            this,
                                            blockedByMe ? "User blocked." : "User unblocked.",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                })
                        ))
                .show();
    }

    private void showReportDialog() {
        if (currentProfile == null) return;
        String[] reasons = {"Spam", "Harassment", "Other"};
        new AlertDialog.Builder(this)
                .setTitle("Report @" + currentProfile.username)
                .setItems(reasons, (dialog, which) -> {
                    String reason = which == 0 ? "spam" : which == 1 ? "harassment" : "other";
                    ZeroChillSocialRepository.reportUser(
                            this,
                            currentProfile.userId,
                            reason,
                            (ok, error) -> runOnUiThread(() -> Toast.makeText(
                                    this,
                                    error == null ? "Report submitted." : error.getMessage(),
                                    error == null ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG
                            ).show())
                    );
                })
                .show();
    }

    private TextView button(String label) {
        TextView view = text(label, 12, Color.rgb(5, 19, 28), true);
        view.setGravity(Gravity.CENTER);
        view.setLetterSpacing(0.06f);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(8, 146, 208), Color.rgb(35, 174, 229)}
        );
        background.setCornerRadius(dp(17));
        background.setStroke(dp(1), Color.rgb(80, 198, 244));
        view.setBackground(background);
        ZeroChillMotion.installPressFeedback(view);
        return view;
    }

    private TextView secondaryButton(String label) {
        TextView view = text(label, 11, UiPalette.PRIMARY, true);
        view.setGravity(Gravity.CENTER);
        view.setLetterSpacing(0.05f);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(180, 11, 13, 17));
        background.setCornerRadius(dp(15));
        background.setStroke(dp(1), Color.rgb(43, 52, 60));
        view.setBackground(background);
        ZeroChillMotion.installPressFeedback(view);
        return view;
    }

    private void showError(String message) {
        content.removeAllViews();
        TextView error = text(
                message == null || message.trim().isEmpty() ? "This profile is unavailable." : message,
                14,
                ZeroChillUi.color(this, R.color.zc_text_secondary),
                false
        );
        content.addView(error);
    }

    private void showBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        content.setVisibility(busy ? View.INVISIBLE : View.VISIBLE);
    }

    private String joinedLabel(String raw) {
        try {
            OffsetDateTime value = OffsetDateTime.parse(raw);
            return "Joined " + value.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US));
        } catch (Exception ignored) {
            return "ZEROCHILL member";
        }
    }

    private GradientDrawable headerBackground() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(246, 0, 0, 0), Color.argb(238, 12, 15, 20)}
        );
    }

    private GradientDrawable panelBackground() {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(235, 18, 21, 26), Color.argb(235, 8, 10, 14)}
        );
        background.setCornerRadius(dp(20));
        background.setStroke(dp(1), Color.rgb(43, 57, 66));
        return background;
    }

    private GradientDrawable circle(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(color);
        return background;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
