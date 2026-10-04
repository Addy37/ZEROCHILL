package com.webapp.crazyshit;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;


/** Full-screen first-run experience for genuinely new ZEROCHILL installs. */
public final class StartupWizardActivity extends Activity {
    private static final int PAGE_WELCOME = 0;
    private static final int PAGE_ACCESS = 1;
    private static final int PAGE_MEET = 2;
    private static final int PAGE_CONTROLS = 3;
    private static final int PAGE_SETTINGS = 4;
    private static final int PAGE_COUNT = 5;

    private FrameLayout root;
    private FrameLayout pageHost;
    private LinearLayout progressRow;
    private TextView backButton;
    private TextView nextButton;
    private int page;
    private boolean preview;
    private boolean ageConfirmed;
    private boolean favoriteAlerts;
    private boolean updateAlerts;
    private boolean hapticsEnabled;
    private boolean awaitingNotificationPermission;
    private boolean transitioning;
    private ObjectAnimator ambientPulse;
    private LinearLayout accountCardHost;
    private int accountRefreshEpoch;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        preview = getIntent() != null
                && getIntent().getBooleanExtra(StartupWizardPolicy.EXTRA_PREVIEW, false);
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        ageConfirmed = AccessNoticeDialog.isAccepted(this);
        favoriteAlerts = prefs.getBoolean(NotificationCoordinator.PREF_NEW_VIDEO_ALERTS, true);
        updateAlerts = prefs.getBoolean(NotificationCoordinator.PREF_UPDATE_ALERTS, true);
        hapticsEnabled = prefs.getBoolean("haptics_enabled", true);

        ZeroChillUi.applySystemBars(this);
        buildShell();
        showPage(PAGE_WELCOME, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (page == PAGE_SETTINGS && accountCardHost != null) refreshAccountCard();
    }

    private void buildShell() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        addAmbientGlow(root, Gravity.TOP | Gravity.END, dp(230), dp(230), dp(72), dp(-54));
        addAmbientGlow(root, Gravity.BOTTOM | Gravity.START, dp(190), dp(190), dp(-66), dp(84));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.TRANSPARENT);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        pageHost = new FrameLayout(this);
        shell.addView(pageHost, new LinearLayout.LayoutParams(-1, 0, 1f));

        progressRow = new LinearLayout(this);
        progressRow.setGravity(Gravity.CENTER);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        shell.addView(progressRow, new LinearLayout.LayoutParams(-1, dp(30)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(20), dp(5), dp(20), dp(14));

        backButton = actionButton("BACK", false);
        backButton.setOnClickListener(v -> {
            if (transitioning) return;
            haptic(v);
            if (page > PAGE_WELCOME) showPage(page - 1, false);
            else closeWizard();
        });
        actions.addView(backButton, new LinearLayout.LayoutParams(dp(92), dp(52)));

        View spacer = new View(this);
        actions.addView(spacer, new LinearLayout.LayoutParams(dp(12), 1));

        nextButton = actionButton("GET STARTED", true);
        nextButton.setOnClickListener(v -> {
            if (transitioning) return;
            haptic(v);
            if (page == PAGE_SETTINGS) {
                finishWizard();
                return;
            }
            if (page == PAGE_ACCESS && !ageConfirmed) return;
            showPage(page + 1, true);
        });
        actions.addView(nextButton, new LinearLayout.LayoutParams(0, dp(52), 1f));
        shell.addView(actions, new LinearLayout.LayoutParams(-1, dp(76)));

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets system = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            int top = Math.max(system.top, cutout.top);
            int bottom = Math.max(system.bottom, cutout.bottom);
            view.setPadding(
                    Math.max(system.left, cutout.left),
                    top,
                    Math.max(system.right, cutout.right),
                    bottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
        setContentView(root);
    }

    private void showPage(int next, boolean forward) {
        if (transitioning) return;
        next = Math.max(PAGE_WELCOME, Math.min(PAGE_SETTINGS, next));
        cancelAmbientPulse();
        accountRefreshEpoch++;
        accountCardHost = null;
        View incoming = buildPage(next);
        View outgoing = pageHost.getChildCount() == 0 ? null : pageHost.getChildAt(0);
        page = next;
        updateChrome();

        if (outgoing == null || !ValueAnimator.areAnimatorsEnabled()) {
            transitioning = false;
            pageHost.removeAllViews();
            pageHost.addView(incoming, new FrameLayout.LayoutParams(-1, -1));
            onPageVisible(incoming);
            return;
        }

        transitioning = true;
        float direction = forward ? 1f : -1f;
        incoming.setAlpha(0f);
        incoming.setTranslationX(direction * dp(18));
        pageHost.addView(incoming, new FrameLayout.LayoutParams(-1, -1));

        outgoing.animate()
                .alpha(0f)
                .translationX(-direction * dp(14))
                .setDuration(190L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> pageHost.removeView(outgoing))
                .start();

        incoming.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(210L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    transitioning = false;
                    onPageVisible(incoming);
                })
                .start();
    }

    private View buildPage(int target) {
        if (target == PAGE_WELCOME) return buildWelcomePage();
        if (target == PAGE_ACCESS) return buildAccessPage();
        if (target == PAGE_MEET) return buildMeetPage();
        if (target == PAGE_CONTROLS) return buildControlsPage();
        return buildSettingsPage();
    }

    private ScrollView pageScroll() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        return scroll;
    }

    private LinearLayout pageColumn(ScrollView scroll) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(24), dp(26), dp(24), dp(22));
        scroll.addView(column, new ScrollView.LayoutParams(-1, -2));
        return column;
    }

    private View buildWelcomePage() {
        ScrollView scroll = pageScroll();
        LinearLayout column = pageColumn(scroll);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(dp(24), dp(66), dp(24), dp(30));

        FrameLayout mascot = mascot(dp(176));
        LinearLayout.LayoutParams mascotParams = new LinearLayout.LayoutParams(dp(176), dp(176));
        mascotParams.bottomMargin = dp(14);
        column.addView(mascot, mascotParams);

        column.addView(wordmark(40f));

        TextView tagline = text("NO LIMITS. ALL CONTENT.", 11f,
                color(R.color.zc_text_secondary), true);
        tagline.setLetterSpacing(0.17f);
        tagline.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams taglineParams = new LinearLayout.LayoutParams(-1, -2);
        taglineParams.topMargin = dp(13);
        column.addView(tagline, taglineParams);

        TextView shortline = text("Swipe. Watch. Save. Repeat.", 20f, Color.WHITE, true);
        shortline.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams shortParams = new LinearLayout.LayoutParams(-1, -2);
        shortParams.topMargin = dp(30);
        column.addView(shortline, shortParams);

        TextView sub = text(
                "A faster way to move through the content you want, without getting in your way.",
                13.5f,
                color(R.color.zc_text_secondary),
                false
        );
        sub.setGravity(Gravity.CENTER);
        sub.setLineSpacing(0f, 1.12f);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.topMargin = dp(10);
        subParams.leftMargin = dp(18);
        subParams.rightMargin = dp(18);
        column.addView(sub, subParams);
        return scroll;
    }

    private View buildAccessPage() {
        ScrollView scroll = pageScroll();
        LinearLayout column = pageColumn(scroll);

        addEyebrow(column, "18+ ACCESS");
        addTitle(column, "Before you continue");
        addSubtitle(column,
                "ZEROCHILL can open adult and sensitive content. Confirm your age before entering.");

        column.addView(infoCard(
                "CONTENT WARNING",
                "ZEROCHILL can provide access to adult, graphic, violent, and other sensitive material."
        ), cardParams());

        column.addView(infoCard(
                "REGIONAL ACCESS",
                "Content and source availability may vary by region. Follow your local laws and the terms of the services you access."
        ), cardParams());

        column.addView(infoCard(
                "UNOFFICIAL APP",
                "ZEROCHILL is an independent application and is not affiliated with or endorsed by the websites and services it accesses."
        ), cardParams());

        ZeroChillCheckBox confirm = new ZeroChillCheckBox(this);
        confirm.setText("I confirm that I am 18 or older.");
        confirm.setTextColor(Color.WHITE);
        confirm.setTextSize(15f);
        confirm.setChecked(ageConfirmed);
        confirm.setPadding(dp(2), dp(8), dp(2), dp(8));
        confirm.setOnCheckedChangeListener((button, checked) -> {
            ageConfirmed = checked;
            updateNextButtonState();
            haptic(button);
        });
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(-1, -2);
        checkParams.topMargin = dp(5);
        column.addView(confirm, checkParams);
        return scroll;
    }

    private View buildMeetPage() {
        ScrollView scroll = pageScroll();
        LinearLayout column = pageColumn(scroll);
        addEyebrow(column, "THE EXPERIENCE");
        addTitle(column, "Meet ZEROCHILL");
        addSubtitle(column, "Four spaces. One app. Everything stays close.");

        column.addView(experienceCard(
                R.drawable.ic_nav_chaos,
                "ShitTok",
                "Swipe through a mixed video feed built for fast discovery."
        ), cardParams());
        column.addView(experienceCard(
                R.drawable.ic_nav_series,
                "Shows",
                "Browse featured content, series, categories, and weekly picks."
        ), cardParams());
        column.addView(experienceCard(
                R.drawable.ic_nav_onlyfap,
                "OnlyFap",
                "Explore creators, galleries, favorites, and the randomized Discover shelf."
        ), cardParams());
        column.addView(experienceCard(
                R.drawable.ic_more_library,
                "Library",
                "Continue watching, revisit history, manage Watch Later, downloads, and favorite creators."
        ), cardParams());
        return scroll;
    }

    private View buildControlsPage() {
        ScrollView scroll = pageScroll();
        LinearLayout column = pageColumn(scroll);
        addEyebrow(column, "FLUID CONTROLS");
        addTitle(column, "Built around gestures");
        addSubtitle(column, "The important controls stay close. Everything else stays out of the way.");

        GridMorphPreviewView previewView = new GridMorphPreviewView(this);
        previewView.setBackground(panelBackground(18));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, dp(150));
        previewParams.bottomMargin = dp(12);
        column.addView(previewView, previewParams);

        column.addView(controlCard("↑ ↓", "Swipe ShitTok",
                "Move to the next or previous clip."), cardParams());
        column.addView(controlCard("TAP", "Pause or resume",
                "Tap the current ShitTok clip to pause or keep watching."), cardParams());
        column.addView(controlCard("↔", "Open creator flow",
                "Swipe sideways from supported ShitTok clips to move into the creator gallery."), cardParams());
        column.addView(controlCard("PINCH", "Resize OnlyFap",
                "Pinch a creator gallery and the holographic grid morphs smoothly around your fingers."), cardParams());
        column.addView(controlCard("HOLD", "More actions",
                "Long press supported media for downloads and additional actions."), cardParams());
        return scroll;
    }

    private View buildSettingsPage() {
        ScrollView scroll = pageScroll();
        LinearLayout column = pageColumn(scroll);
        addEyebrow(column, "YOUR DEFAULTS");
        addTitle(column, "Make it yours");
        addSubtitle(column, "Choose your starting preferences. You can change them later in Settings.");

        accountCardHost = new LinearLayout(this);
        accountCardHost.setOrientation(LinearLayout.VERTICAL);
        column.addView(accountCardHost, cardParams());
        refreshAccountCard();

        column.addView(settingsSwitch(
                "Favorite creator alerts",
                "Notify me when creators I follow have new content.",
                favoriteAlerts,
                checked -> favoriteAlerts = checked
        ), cardParams());

        column.addView(settingsSwitch(
                "ZEROCHILL update alerts",
                "Notify me when a new app version is available.",
                updateAlerts,
                checked -> updateAlerts = checked
        ), cardParams());

        column.addView(settingsSwitch(
                "Haptic feedback",
                "Use subtle vibration for controls and interactions.",
                hapticsEnabled,
                checked -> hapticsEnabled = checked
        ), cardParams());

        TextView note = text(
                "Android notification permission is only requested after you choose ENTER ZEROCHILL, and only when an alert option is enabled.",
                12f,
                color(R.color.zc_text_muted),
                false
        );
        note.setLineSpacing(0f, 1.1f);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.topMargin = dp(8);
        column.addView(note, noteParams);
        return scroll;
    }

    private void refreshAccountCard() {
        if (accountCardHost == null) return;
        final LinearLayout target = accountCardHost;
        final int epoch = ++accountRefreshEpoch;

        if (!ZeroChillAccountRepository.isConfigured()) {
            renderAccountUnavailable(target);
            return;
        }

        boolean storedSession = ZeroChillAccountRepository.hasStoredSession(this);
        String sessionUser = ZeroChillSessionStore.currentUserId(this);
        if (!storedSession || sessionUser.isEmpty()) {
            renderAccountSignedOut(target);
            return;
        }

        renderAccountChecking(target);
        ZeroChillAccountRepository.current(this, (state, error) -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || epoch != accountRefreshEpoch
                    || accountCardHost != target) return;
            if (error == null && state != null && state.signedIn) {
                renderAccountSignedIn(target, state);
            } else {
                renderAccountConnectedFallback(target);
            }
        }));
    }

    private void prepareAccountCard(LinearLayout card, String eyebrow) {
        card.removeAllViews();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.setBackground(panelBackground(17));
        TextView label = text(eyebrow, 10.5f, UiPalette.PRIMARY, true);
        label.setLetterSpacing(0.09f);
        card.addView(label);
    }

    private void renderAccountSignedOut(LinearLayout card) {
        prepareAccountCard(card, "YOUR ZEROCHILL ID");

        TextView title = text("Take your identity with you", 16.5f, Color.WHITE, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(7);
        card.addView(title, titleParams);

        TextView body = text(
                "Create an account to keep your identity, favorite creators, comments, messages, and social activity with you.",
                12.3f,
                color(R.color.zc_text_secondary),
                false
        );
        body.setLineSpacing(0f, 1.10f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.topMargin = dp(4);
        card.addView(body, bodyParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(46));
        actionParams.topMargin = dp(12);
        card.addView(actions, actionParams);

        TextView create = actionButton("CREATE ACCOUNT", true);
        create.setContentDescription("Create ZEROCHILL account");
        create.setOnClickListener(v -> {
            haptic(v);
            openAccount(true);
        });
        actions.addView(create, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView signIn = actionButton("SIGN IN", false);
        signIn.setContentDescription("Sign in to ZEROCHILL");
        signIn.setOnClickListener(v -> {
            haptic(v);
            openAccount(false);
        });
        LinearLayout.LayoutParams signInParams = new LinearLayout.LayoutParams(0, -1, 1f);
        signInParams.setMarginStart(dp(8));
        actions.addView(signIn, signInParams);

        TextView optional = text(
                "Optional. You can always do this later.",
                11f,
                color(R.color.zc_text_muted),
                false
        );
        optional.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams optionalParams = new LinearLayout.LayoutParams(-1, -2);
        optionalParams.topMargin = dp(9);
        card.addView(optional, optionalParams);
    }

    private void renderAccountChecking(LinearLayout card) {
        prepareAccountCard(card, "YOUR ZEROCHILL ID");
        TextView checking = text(
                "Checking your signed-in identity…",
                12.5f,
                color(R.color.zc_text_secondary),
                false
        );
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(7);
        card.addView(checking, params);
    }

    private void renderAccountSignedIn(
            LinearLayout card,
            ZeroChillAccountRepository.AccountState state
    ) {
        prepareAccountCard(card, "ZEROCHILL ID CONNECTED");
        String name = state.displayName == null ? "" : state.displayName.trim();
        if (name.isEmpty()) name = SocialUi.cleanName(state.username);
        if (name.isEmpty()) name = "Your account";

        TextView title = text("Signed in as " + name, 16.5f, Color.WHITE, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(7);
        card.addView(title, titleParams);

        TextView body = text(
                "Your identity, creators, comments, messages, and social activity are connected.",
                12.3f,
                color(R.color.zc_text_secondary),
                false
        );
        body.setLineSpacing(0f, 1.10f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.topMargin = dp(4);
        card.addView(body, bodyParams);

        TextView manage = actionButton("MANAGE ACCOUNT", false);
        manage.setOnClickListener(v -> {
            haptic(v);
            openAccount(false);
        });
        LinearLayout.LayoutParams manageParams = new LinearLayout.LayoutParams(-1, dp(44));
        manageParams.topMargin = dp(11);
        card.addView(manage, manageParams);
    }

    private void renderAccountConnectedFallback(LinearLayout card) {
        prepareAccountCard(card, "ZEROCHILL ID CONNECTED");
        TextView body = text(
                "Your account is connected on this device. Open Account to manage your ZEROCHILL ID.",
                12.3f,
                color(R.color.zc_text_secondary),
                false
        );
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.topMargin = dp(7);
        card.addView(body, bodyParams);

        TextView manage = actionButton("MANAGE ACCOUNT", false);
        manage.setOnClickListener(v -> {
            haptic(v);
            openAccount(false);
        });
        LinearLayout.LayoutParams manageParams = new LinearLayout.LayoutParams(-1, dp(44));
        manageParams.topMargin = dp(11);
        card.addView(manage, manageParams);
    }

    private void renderAccountUnavailable(LinearLayout card) {
        prepareAccountCard(card, "YOUR ZEROCHILL ID");
        TextView body = text(
                "Account setup is unavailable in this build. You can still finish setup and use ZEROCHILL.",
                12.3f,
                color(R.color.zc_text_secondary),
                false
        );
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.topMargin = dp(7);
        card.addView(body, bodyParams);
    }

    private void openAccount(boolean create) {
        Intent intent = new Intent(this, ZeroChillAccountActivity.class);
        intent.putExtra(ZeroChillAccountActivity.EXTRA_START_CREATE, create);
        startActivity(intent);
    }

    private void updateChrome() {
        progressRow.removeAllViews();
        for (int index = 0; index < PAGE_COUNT; index++) {
            View dot = new View(this);
            boolean active = index == page;
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(active ? UiPalette.PRIMARY : Color.rgb(61, 65, 72));
            bg.setCornerRadius(dp(5));
            dot.setBackground(bg);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    active ? dp(24) : dp(7),
                    dp(7)
            );
            params.leftMargin = dp(4);
            params.rightMargin = dp(4);
            progressRow.addView(dot, params);
        }

        backButton.setVisibility(page == PAGE_WELCOME && !preview ? View.INVISIBLE : View.VISIBLE);
        if (page == PAGE_WELCOME) nextButton.setText("GET STARTED");
        else if (page == PAGE_SETTINGS) nextButton.setText(preview ? "FINISH TOUR" : "ENTER ZEROCHILL");
        else nextButton.setText("CONTINUE");
        updateNextButtonState();
    }

    private void updateNextButtonState() {
        if (nextButton == null) return;
        boolean enabled = page != PAGE_ACCESS || ageConfirmed;
        nextButton.setEnabled(enabled);
        nextButton.setAlpha(enabled ? 1f : 0.42f);
        nextButton.setBackground(primaryButtonBackground(enabled));
    }

    private void finishWizard() {
        if (!ageConfirmed) {
            showPage(PAGE_ACCESS, false);
            return;
        }

        getSharedPreferences("app_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean(NotificationCoordinator.PREF_NEW_VIDEO_ALERTS, favoriteAlerts)
                .putBoolean(NotificationCoordinator.PREF_UPDATE_ALERTS, updateAlerts)
                .putBoolean("haptics_enabled", hapticsEnabled)
                .apply();

        AccessNoticeDialog.markAccepted(this);
        GestureGuideDialog.markSeen(this);
        NotificationCoordinator.markOnboardingHandled(this);
        StartupWizardPolicy.markComplete(this);
        NotificationCoordinator.onPreferencesChanged(this);

        if ((favoriteAlerts || updateAlerts)
                && NotificationCoordinator.requestPermissionForOnboarding(this)) {
            awaitingNotificationPermission = true;
            return;
        }
        completeLaunch();
    }

    private void completeLaunch() {
        awaitingNotificationPermission = false;
        if (preview) {
            finish();
            overridePendingTransition(0, 0);
            return;
        }

        Intent app = new Intent(this, NativeMainActivity.class);
        String action = getIntent() == null ? null : getIntent().getAction();
        if (AppShortcuts.isShortcutAction(action)) {
            app.setAction(action);
            app.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        }
        startActivity(app);
        overridePendingTransition(0, 0);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == NotificationCoordinator.REQUEST_NOTIFICATIONS
                && awaitingNotificationPermission) {
            completeLaunch();
        }
    }

    @Override
    public void onBackPressed() {
        if (transitioning) return;
        if (page > PAGE_WELCOME) {
            showPage(page - 1, false);
            return;
        }
        closeWizard();
    }

    private void closeWizard() {
        if (preview) {
            finish();
        } else {
            finishAffinity();
        }
    }

    private void onPageVisible(View view) {
        if (page == PAGE_WELCOME && ValueAnimator.areAnimatorsEnabled()) {
            View outline = view.findViewWithTag("wizard_mascot_outline");
            if (outline != null) {
                ambientPulse = ObjectAnimator.ofFloat(outline, View.ALPHA, 1f, 0.58f, 1f);
                ambientPulse.setDuration(1450L);
                ambientPulse.setRepeatCount(ValueAnimator.INFINITE);
                ambientPulse.setRepeatMode(ValueAnimator.RESTART);
                ambientPulse.start();
            }
        }


    }

    private void cancelAmbientPulse() {
        if (ambientPulse != null) {
            ambientPulse.cancel();
            ambientPulse = null;
        }
    }

    @Override
    protected void onDestroy() {
        cancelAmbientPulse();
        super.onDestroy();
    }

    private FrameLayout mascot(int size) {
        FrameLayout mascot = new FrameLayout(this);
        int[] layers = {
                R.drawable.zc_devil_horns,
                R.drawable.zc_devil_face,
                R.drawable.zc_devil_x_eye,
                R.drawable.zc_devil_angry_eye,
                R.drawable.zc_devil_teeth,
                R.drawable.zc_devil_tongue,
                R.drawable.zc_devil_outline
        };
        for (int index = 0; index < layers.length; index++) {
            ImageView layer = new ImageView(this);
            layer.setImageResource(layers[index]);
            layer.setScaleType(ImageView.ScaleType.FIT_CENTER);
            layer.setContentDescription(index == layers.length - 1 ? "ZEROCHILL mascot" : null);
            if (index == layers.length - 1) layer.setTag("wizard_mascot_outline");
            mascot.addView(layer, new FrameLayout.LayoutParams(size, size));
        }
        return mascot;
    }

    private LinearLayout wordmark(float size) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        TextView zero = text("ZERO", size, Color.rgb(243, 244, 246), true);
        TextView chill = text("CHILL", size, UiPalette.PRIMARY, false);
        row.addView(zero);
        row.addView(chill);
        return row;
    }

    private View experienceCard(int iconRes, String title, String body) {
        LinearLayout card = cardRow();
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(UiPalette.PRIMARY);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        card.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(14), 0, 0, 0);
        copy.addView(text(title, 17f, Color.WHITE, true));
        TextView detail = text(body, 12.5f, color(R.color.zc_text_secondary), false);
        detail.setLineSpacing(0f, 1.08f);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.topMargin = dp(3);
        copy.addView(detail, detailParams);
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        return card;
    }

    private View controlCard(String badge, String title, String body) {
        LinearLayout card = cardRow();
        TextView badgeView = text(badge, badge.length() > 3 ? 10f : 17f, UiPalette.ON_PRIMARY, true);
        badgeView.setGravity(Gravity.CENTER);
        badgeView.setBackground(pill(UiPalette.PRIMARY));
        card.addView(badgeView, new LinearLayout.LayoutParams(dp(58), dp(40)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(13), 0, 0, 0);
        copy.addView(text(title, 15.5f, Color.WHITE, true));
        TextView detail = text(body, 12.3f, color(R.color.zc_text_secondary), false);
        detail.setLineSpacing(0f, 1.08f);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.topMargin = dp(3);
        copy.addView(detail, detailParams);
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        return card;
    }

    private View infoCard(String label, String body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.setBackground(panelBackground(17));
        TextView labelView = text(label, 10.5f, UiPalette.PRIMARY, true);
        labelView.setLetterSpacing(0.08f);
        card.addView(labelView);
        TextView bodyView = text(body, 13f, color(R.color.zc_text_secondary), false);
        bodyView.setLineSpacing(0f, 1.12f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.topMargin = dp(5);
        card.addView(bodyView, bodyParams);
        return card;
    }

    private View settingsSwitch(
            String title,
            String body,
            boolean checked,
            ToggleChanged changed
    ) {
        LinearLayout card = cardRow();
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 15.5f, Color.WHITE, true));
        TextView detail = text(body, 12.3f, color(R.color.zc_text_secondary), false);
        detail.setLineSpacing(0f, 1.08f);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.topMargin = dp(3);
        copy.addView(detail, detailParams);
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        ZeroChillSwitch toggle = new ZeroChillSwitch(this);
        toggle.setChecked(checked);
        toggle.setContentDescription(title);
        toggle.setOnCheckedChangeListener((button, value) -> {
            changed.onChanged(value);
            haptic(button);
        });
        card.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        card.addView(toggle, new LinearLayout.LayoutParams(dp(58), dp(48)));
        return card;
    }

    private LinearLayout cardRow() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(panelBackground(17));
        return card;
    }

    private void addEyebrow(LinearLayout column, String value) {
        TextView eyebrow = text(value, 10.5f, UiPalette.PRIMARY, true);
        eyebrow.setLetterSpacing(0.11f);
        column.addView(eyebrow);
    }

    private void addTitle(LinearLayout column, String value) {
        TextView title = text(value, 29f, Color.WHITE, true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(7);
        column.addView(title, params);
    }

    private void addSubtitle(LinearLayout column, String value) {
        TextView subtitle = text(value, 13.5f, color(R.color.zc_text_secondary), false);
        subtitle.setLineSpacing(0f, 1.12f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        params.bottomMargin = dp(20);
        column.addView(subtitle, params);
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(9);
        return params;
    }

    private TextView actionButton(String label, boolean primary) {
        TextView button = text(
                label,
                13f,
                primary ? UiPalette.ON_PRIMARY : Color.WHITE,
                true
        );
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setLetterSpacing(0.05f);
        button.setBackground(primary
                ? primaryButtonBackground(true)
                : secondaryButtonBackground());
        ZeroChillMotion.installPressFeedback(button);
        return button;
    }

    private GradientDrawable primaryButtonBackground(boolean enabled) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(enabled ? UiPalette.PRIMARY : Color.rgb(47, 54, 59));
        background.setCornerRadius(dp(17));
        background.setStroke(dp(1), enabled ? Color.rgb(74, 191, 240) : Color.rgb(67, 72, 78));
        return background;
    }

    private GradientDrawable secondaryButtonBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(150, 12, 13, 16));
        background.setCornerRadius(dp(17));
        background.setStroke(dp(1), Color.rgb(54, 59, 66));
        return background;
    }

    private GradientDrawable panelBackground(int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(220, 12, 14, 18));
        background.setCornerRadius(dp(radius));
        background.setStroke(dp(1), Color.rgb(45, 57, 66));
        return background;
    }

    private GradientDrawable pill(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(13));
        return background;
    }

    private void addAmbientGlow(
            FrameLayout parent,
            int gravity,
            int width,
            int height,
            int marginX,
            int marginY
    ) {
        View glow = new View(this);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        background.setGradientRadius(Math.max(width, height) * 0.52f);
        background.setColors(new int[]{
                Color.argb(54, 8, 146, 208),
                Color.argb(16, 8, 146, 208),
                Color.TRANSPARENT
        });
        glow.setBackground(background);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.gravity = gravity;
        params.leftMargin = marginX;
        params.rightMargin = marginX;
        params.topMargin = marginY;
        params.bottomMargin = marginY;
        parent.addView(glow, params);
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return view;
    }

    private int color(int res) {
        return ZeroChillUi.color(this, res);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void haptic(View view) {
        if (view == null || !hapticsEnabled) return;
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private interface ToggleChanged {
        void onChanged(boolean checked);
    }

    /** Tiny looping visualization of the new holographic creator-gallery density morph. */
    private static final class GridMorphPreviewView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private ValueAnimator animator;
        private float fraction;

        GridMorphPreviewView(Activity context) {
            super(context);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(Color.argb(26, 8, 146, 208));
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(context, 1.25f));
            stroke.setColor(Color.argb(210, 8, 146, 208));
            setContentDescription("Animated creator gallery grid resizing preview");
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!ValueAnimator.areAnimatorsEnabled()) {
                fraction = 1f;
                invalidate();
                return;
            }
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(1350L);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.setInterpolator(new AccelerateDecelerateInterpolator());
            animator.addUpdateListener(value -> {
                fraction = (float) value.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (animator != null) animator.cancel();
            animator = null;
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float pad = dp(getContextActivity(), 14f);
            float gap = dp(getContextActivity(), 7f);
            float usableWidth = Math.max(1f, getWidth() - (pad * 2f));
            float usableHeight = Math.max(1f, getHeight() - (pad * 2f));

            for (int index = 0; index < 8; index++) {
                RectF start = cell(index, 2, 4, usableWidth, usableHeight, pad, gap);
                RectF end = cell(index, 4, 2, usableWidth, usableHeight, pad, gap);
                rect.set(
                        lerp(start.left, end.left, fraction),
                        lerp(start.top, end.top, fraction),
                        lerp(start.right, end.right, fraction),
                        lerp(start.bottom, end.bottom, fraction)
                );
                float radius = dp(getContextActivity(), 8f);
                canvas.drawRoundRect(rect, radius, radius, fill);
                canvas.drawRoundRect(rect, radius, radius, stroke);
            }
        }

        private RectF cell(
                int index,
                int columns,
                int rows,
                float width,
                float height,
                float pad,
                float gap
        ) {
            float cellWidth = (width - (gap * (columns - 1))) / columns;
            float cellHeight = (height - (gap * (rows - 1))) / rows;
            int column = index % columns;
            int row = index / columns;
            float left = pad + column * (cellWidth + gap);
            float top = pad + row * (cellHeight + gap);
            return new RectF(left, top, left + cellWidth, top + cellHeight);
        }

        private float lerp(float start, float end, float value) {
            return start + ((end - start) * value);
        }

        private Activity getContextActivity() {
            return (Activity) getContext();
        }

        private static float dp(Activity activity, float value) {
            return value * activity.getResources().getDisplayMetrics().density;
        }
    }
}
