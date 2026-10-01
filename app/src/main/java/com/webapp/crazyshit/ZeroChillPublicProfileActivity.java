package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ArrayList;

/** Public ZeroChill identity shown from comments and other social surfaces. */
public final class ZeroChillPublicProfileActivity extends Activity {
    static final String EXTRA_USER_ID = "zerochill_profile_user_id";

    private LinearLayout content;
    private ProgressBar progress;
    private String userId = "";
    private ZeroChillSocialRepository.PublicProfile currentProfile;
    private boolean blockedByMe;
    private TextView blockButton;
    private LinearLayout sharedSection;
    private String loadedForAccount = "";
    private int requestEpoch;
    private boolean openedEdit;

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
        String account = ZeroChillSessionStore.currentUserId(this);
        if (openedEdit || (currentProfile != null && !loadedForAccount.equals(account))) {
            openedEdit = false;
            loadProfile();
        } else if (currentProfile != null && !currentProfile.currentUser) {
            loadBlockState();
        }
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

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(28), dp(22), dp(34));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        progress = new ProgressBar(this);
        ZeroChillUi.styleProgress(progress);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(dp(42), dp(42));
        loadingParams.gravity = Gravity.CENTER;
        root.addView(progress, loadingParams);

        setContentView(root);
    }

    private void loadProfile() {
        int epoch = ++requestEpoch;
        String account = ZeroChillSessionStore.currentUserId(this);
        showBusy(true);
        ZeroChillSocialRepository.loadProfile(this, userId, (profile, error) -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || epoch != requestEpoch
                    || !account.equals(ZeroChillSessionStore.currentUserId(this))) return;
            showBusy(false);
            if (error != null || profile == null) {
                showError(error == null ? "This profile is unavailable." : error.getMessage());
                return;
            }
            loadedForAccount = account;
            render(profile);
        }));
    }

    private void render(ZeroChillSocialRepository.PublicProfile profile) {
        currentProfile = profile;
        blockedByMe = false;
        blockButton = null;
        sharedSection = null;
        content.removeAllViews();

        TextView eyebrow = text("ZEROCHILL MEMBER", 10, UiPalette.PRIMARY, true);
        eyebrow.setLetterSpacing(0.12f);
        content.addView(eyebrow);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(17));
        card.setBackground(panelBackground());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.topMargin = dp(10);
        content.addView(card, cardParams);

        FrameLayout halo = new FrameLayout(this);
        halo.setBackground(circle(UiPalette.PRIMARY_CONTAINER));
        halo.setPadding(dp(3), dp(3), dp(3), dp(3));
        card.addView(halo, new LinearLayout.LayoutParams(dp(96), dp(96)));

        ImageView avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setClipToOutline(true);
        avatar.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
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
            displayParams.topMargin = dp(12);
            card.addView(display, displayParams);
        }

        TextView username = text(SocialUi.cleanName(profile.username), profile.displayName.isEmpty() ? 23 : 14,
                profile.displayName.isEmpty() ? Color.WHITE : UiPalette.PRIMARY, true);
        LinearLayout.LayoutParams userParams = new LinearLayout.LayoutParams(-2, -2);
        userParams.topMargin = dp(profile.displayName.isEmpty() ? 12 : 2);
        card.addView(username, userParams);

        if (!profile.bio.trim().isEmpty()) {
            TextView bio = text(profile.bio, 13, Color.rgb(215, 223, 230), false);
            bio.setGravity(Gravity.CENTER);
            bio.setLineSpacing(dp(3), 1f);
            LinearLayout.LayoutParams bioParams = new LinearLayout.LayoutParams(-1, -2);
            bioParams.topMargin = dp(12);
            card.addView(bio, bioParams);
        }

        TextView joined = text(joinedLabel(profile.createdAt), 11,
                ZeroChillUi.color(this, R.color.zc_text_muted), false);
        LinearLayout.LayoutParams joinedParams = new LinearLayout.LayoutParams(-2, -2);
        joinedParams.topMargin = dp(7);
        card.addView(joined, joinedParams);

        if (profile.currentUser) {
            TextView edit = button("EDIT MY PROFILE");
            edit.setOnClickListener(v -> {
                openedEdit = true;
                startActivity(new Intent(this, ZeroChillAccountActivity.class));
            });
            LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(-1, dp(48));
            editParams.topMargin = dp(14);
            content.addView(edit, editParams);
        } else {
            TextView message = button("MESSAGE");
            message.setOnClickListener(v -> openMessage(profile));
            LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(-1, dp(44));
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

            sharedSection = new LinearLayout(this);
            sharedSection.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams sharedParams = new LinearLayout.LayoutParams(-1, -2);
            sharedParams.topMargin = dp(15);
            content.addView(sharedSection, sharedParams);

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
        if (currentProfile == null || currentProfile.currentUser) return;
        if (!ZeroChillAccountRepository.hasStoredSession(this)
                || ZeroChillSessionStore.currentUserId(this).isEmpty()) {
            if (sharedSection != null) sharedSection.removeAllViews();
            return;
        }
        String account = ZeroChillSessionStore.currentUserId(this);
        String target = currentProfile.userId;
        ZeroChillSocialRepository.blockState(this, target, (blocked, error) ->
                runOnUiThread(() -> {
                    if (!sameRequest(account, target)) return;
                    if (error != null) return;
                    blockedByMe = Boolean.TRUE.equals(blocked);
                    if (blockButton != null) {
                        blockButton.setText(blockedByMe ? "UNBLOCK" : "BLOCK");
                    }
                    if (sharedSection != null) sharedSection.removeAllViews();
                    if (!blockedByMe) loadSharedCreators(account, target);
                })
        );
    }

    private void loadSharedCreators(String account, String target) {
        ZeroChillSocialRepository.loadSharedCreators(this, target, (shared, error) ->
                runOnUiThread(() -> {
                    if (!sameRequest(account, target) || blockedByMe || sharedSection == null) return;
                    sharedSection.removeAllViews();
                    if (error != null || shared == null || shared.isEmpty()) return;
                    sharedSection.setPadding(dp(15), dp(14), dp(15), dp(16));
                    sharedSection.setBackground(panelBackground());
                    TextView label = text("SHARED WITH YOU", 10, UiPalette.PRIMARY, true);
                    label.setLetterSpacing(0.08f);
                    sharedSection.addView(label);
                    TextView title = text("Shared creators", 15, Color.WHITE, true);
                    LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
                    titleParams.topMargin = dp(9);
                    sharedSection.addView(title, titleParams);
                    TextView count = text(shared.size() + (shared.size() == 1
                            ? " creator in common" : " creators in common"), 12,
                            ZeroChillUi.color(this, R.color.zc_text_secondary), false);
                    sharedSection.addView(count);
                    LinearLayout artwork = new LinearLayout(this);
                    artwork.setGravity(Gravity.CENTER_VERTICAL);
                    int shown = 0;
                    for (ZeroChillSocialRepository.SharedCreator creator : shared) {
                        NativeContentItem metadata = creator.metadata;
                        if (metadata == null || metadata.imageUrl.trim().isEmpty()) continue;
                        if (shown++ >= 3) break;
                        ImageView avatar = new ImageView(this);
                        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        avatar.setContentDescription(creator.name.isEmpty() ? creator.key : creator.name);
                        Object model = metadata.imageUrl;
                        String referer = metadata.uploader.isEmpty() ? metadata.url : metadata.uploader;
                        if (!referer.isEmpty() && metadata.imageUrl.startsWith("https://"))
                            model = new com.bumptech.glide.load.model.GlideUrl(metadata.imageUrl,
                                    new com.bumptech.glide.load.model.LazyHeaders.Builder()
                                            .addHeader("Referer", referer).build());
                        Glide.with(avatar).load(model).circleCrop().into(avatar);
                        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(36), dp(36));
                        avatarParams.setMarginEnd(dp(7));
                        artwork.addView(avatar, avatarParams);
                    }
                    if (artwork.getChildCount() > 0) {
                        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(-1, -2);
                        artParams.topMargin = dp(10);
                        sharedSection.addView(artwork, artParams);
                    }
                    TextView names = text(sharedNames(shared), 12,
                            ZeroChillUi.color(this, R.color.zc_text_secondary), false);
                    LinearLayout.LayoutParams namesParams = new LinearLayout.LayoutParams(-1, -2);
                    namesParams.topMargin = dp(8);
                    sharedSection.addView(names, namesParams);
                    sharedSection.setContentDescription("Shared creators. " + shared.size()
                            + " creators in common. Open list.");
                    sharedSection.setOnClickListener(v -> showSharedCreatorsSheet(shared));
                    ZeroChillMotion.installPressFeedback(sharedSection);
                }));
    }

    private void showSharedCreatorsSheet(ArrayList<ZeroChillSocialRepository.SharedCreator> shared) {
        if (shared == null || shared.isEmpty() || isFinishing() || isDestroyed()) return;

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout host = new FrameLayout(this);
        host.setPadding(dp(12), 0, dp(12), dp(12));

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(20), dp(10), dp(20), dp(20));
        sheet.setBackground(sharedSheetBackground());
        host.addView(sheet, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        View handle = new View(this);
        GradientDrawable handleBackground = new GradientDrawable();
        handleBackground.setColor(Color.rgb(74, 85, 94));
        handleBackground.setCornerRadius(dp(99));
        handle.setBackground(handleBackground);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(38), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = dp(14);
        sheet.addView(handle, handleParams);

        TextView title = text("Shared creators", 20, Color.WHITE, true);
        sheet.addView(title);

        TextView subtitle = text(
                shared.size() + (shared.size() == 1 ? " creator you both follow" : " creators you both follow"),
                12,
                ZeroChillUi.color(this, R.color.zc_text_secondary),
                false
        );
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(3);
        sheet.addView(subtitle, subtitleParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                -1,
                shared.size() > 4 ? dp(320) : -2
        );
        scrollParams.topMargin = dp(14);
        sheet.addView(scroll, scrollParams);

        for (ZeroChillSocialRepository.SharedCreator creator : shared) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(9), dp(10), dp(9));
            row.setBackground(sharedCreatorRowBackground());
            String creatorName = creator.name.isEmpty() ? creator.key : creator.name;
            row.setContentDescription("Open creator " + creatorName);

            ImageView avatar = new ImageView(this);
            bindSharedCreatorAvatar(avatar, creator);
            row.addView(avatar, new LinearLayout.LayoutParams(dp(44), dp(44)));

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0, -2, 1f);
            labelsParams.setMarginStart(dp(12));
            row.addView(labels, labelsParams);

            TextView name = text(creatorName, 14, Color.WHITE, true);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            labels.addView(name);

            TextView hint = text(
                    "Open creator",
                    10,
                    ZeroChillUi.color(this, R.color.zc_text_muted),
                    false
            );
            LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
            hintParams.topMargin = dp(2);
            labels.addView(hint, hintParams);

            TextView chevron = text("›", 28, UiPalette.PRIMARY, false);
            chevron.setGravity(Gravity.CENTER);
            row.addView(chevron, new LinearLayout.LayoutParams(dp(32), dp(44)));

            row.setOnClickListener(v -> {
                dialog.dismiss();
                openSharedCreator(creator);
            });
            ZeroChillMotion.installPressFeedback(row);

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.bottomMargin = dp(8);
            rows.addView(row, rowParams);
        }

        TextView close = secondaryButton("CLOSE");
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-1, dp(44));
        closeParams.topMargin = dp(6);
        sheet.addView(close, closeParams);

        dialog.setContentView(host);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.68f;
            window.setAttributes(attributes);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    private void openSharedCreator(ZeroChillSocialRepository.SharedCreator creator) {
        if (creator == null) return;
        NativeContentItem metadata = creator.metadata;
        String fallback = creator.name.isEmpty() ? creator.key : creator.name;
        String title = metadata == null || metadata.title == null || metadata.title.trim().isEmpty()
                ? fallback
                : metadata.title.trim();
        String query = title.isEmpty() ? fallback : title;
        if (query.isEmpty()) return;
        String profileHint = metadata == null
                ? ""
                : NativeFeedBrowserActivity.creatorProfileHint(metadata);
        startActivity(NativeFeedBrowserActivity.createCreatorGallery(
                this,
                query,
                query,
                profileHint
        ));
    }

    private void bindSharedCreatorAvatar(
            ImageView avatar,
            ZeroChillSocialRepository.SharedCreator creator
    ) {
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setClipToOutline(true);
        avatar.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        avatar.setBackground(circle(Color.rgb(12, 14, 18)));

        NativeContentItem metadata = creator == null ? null : creator.metadata;
        if (metadata == null || metadata.imageUrl == null || metadata.imageUrl.trim().isEmpty()) {
            avatar.setImageResource(R.drawable.ic_more_account);
            avatar.setPadding(dp(11), dp(11), dp(11), dp(11));
            avatar.setColorFilter(UiPalette.PRIMARY);
            return;
        }

        Object model = metadata.imageUrl;
        String referer = metadata.uploader == null || metadata.uploader.trim().isEmpty()
                ? metadata.url
                : metadata.uploader;
        if (referer != null && !referer.trim().isEmpty() && metadata.imageUrl.startsWith("https://")) {
            model = new com.bumptech.glide.load.model.GlideUrl(
                    metadata.imageUrl,
                    new com.bumptech.glide.load.model.LazyHeaders.Builder()
                            .addHeader("Referer", referer)
                            .build()
            );
        }
        avatar.clearColorFilter();
        avatar.setPadding(0, 0, 0, 0);
        Glide.with(avatar).load(model).circleCrop().into(avatar);
    }

    private GradientDrawable sharedSheetBackground() {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(18, 23, 29), Color.rgb(6, 9, 13)}
        );
        background.setCornerRadius(dp(26));
        background.setStroke(dp(1), Color.rgb(42, 67, 80));
        return background;
    }

    private GradientDrawable sharedCreatorRowBackground() {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(245, 17, 21, 26), Color.argb(245, 9, 12, 16)}
        );
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), Color.rgb(38, 50, 58));
        return background;
    }

    static String sharedNames(ArrayList<ZeroChillSocialRepository.SharedCreator> shared) {
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < Math.min(shared.size(), 3); i++) {
            if (names.length() > 0) names.append("  ·  ");
            ZeroChillSocialRepository.SharedCreator creator = shared.get(i);
            names.append(creator.name.isEmpty() ? creator.key : creator.name);
        }
        return names.toString();
    }

    private boolean sameRequest(String account, String target) {
        return !isFinishing() && !isDestroyed() && currentProfile != null
                && target.equals(currentProfile.userId)
                && account.equals(ZeroChillSessionStore.currentUserId(this));
    }

    private void confirmBlock(boolean block) {
        if (currentProfile == null) return;
        String account = ZeroChillSessionStore.currentUserId(this);
        String target = currentProfile.userId;
        String username = SocialUi.name(currentProfile.displayName, currentProfile.username);
        new AlertDialog.Builder(this)
                .setTitle(block ? "Block " + username + "?" : "Unblock " + username + "?")
                .setMessage(block
                        ? "You won't be able to message each other while blocked."
                        : "You and this user will be able to message each other again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(block ? "Block" : "Unblock", (dialog, which) ->
                        ZeroChillSocialRepository.setBlocked(
                                this,
                                target,
                                block,
                                (value, error) -> runOnUiThread(() -> {
                                    if (!sameRequest(account, target)) return;
                                    if (error != null) {
                                        Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                                        return;
                                    }
                                    blockedByMe = Boolean.TRUE.equals(value);
                                    if (blockButton != null) {
                                        blockButton.setText(blockedByMe ? "UNBLOCK" : "BLOCK");
                                    }
                                    if (sharedSection != null) sharedSection.removeAllViews();
                                    if (!blockedByMe) loadSharedCreators(account, target);
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
                .setTitle("Report " + SocialUi.name(currentProfile.displayName, currentProfile.username))
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
