package com.webapp.crazyshit;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ArgbEvaluator;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;


/** Full-screen first-run experience for genuinely new ZEROCHILL installs. */
public final class StartupWizardActivity extends Activity {
    private static final int PAGE_WELCOME = 0;
    private static final int PAGE_ACCESS = 1;
    private static final int PAGE_MEET = 2;
    private static final int PAGE_SETTINGS = 3;
    private static final int PAGE_COUNT = 4;
    private int showcaseScene;
    private boolean preferencesExpanded;
    private final ArrayList<View> ambientGlows = new ArrayList<>();
    private CinematicTourView tour;

    private FrameLayout root;
    private FrameLayout pageHost;
    private LinearLayout progressRow;
    private TextView backButton;
    private TextView nextButton;
    private int page;
    private boolean preview;
    private boolean ageConfirmed;
    private boolean xmegaAccessAuthorized;
    private boolean xmegaAccessReady;
    private boolean accessSetupInProgress;
    private TextView accessSetupButton;
    private TextView accessSetupStatus;
    private boolean favoriteAlerts;
    private boolean updateAlerts;
    private boolean hapticsEnabled;
    private boolean awaitingNotificationPermission;
    private boolean transitioning;
    private ObjectAnimator ambientPulse;
    private LinearLayout accountCardHost;
    private int accountRefreshEpoch;
    private AppearanceTheme.Accent selectedAccent;
    private int previewAccentColor;
    private FrameLayout accentPreviewMascot;
    private TextView accentPreviewChill;
    private TextView accentPreviewButton;
    private final ArrayList<LinearLayout> accentSwatches = new ArrayList<>();
    private ValueAnimator accentAnimator;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        preview = getIntent() != null
                && getIntent().getBooleanExtra(StartupWizardPolicy.EXTRA_PREVIEW, false);
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        ageConfirmed = AccessNoticeDialog.isAccepted(this);
        xmegaAccessReady = XMegaDriveAgeSession.isCookieReady(this);
        xmegaAccessAuthorized = XMegaDriveAgeSession.isSetupComplete(this)
                || (ageConfirmed && xmegaAccessReady);
        favoriteAlerts = prefs.getBoolean(NotificationCoordinator.PREF_NEW_VIDEO_ALERTS, true);
        updateAlerts = prefs.getBoolean(NotificationCoordinator.PREF_UPDATE_ALERTS, true);
        hapticsEnabled = prefs.getBoolean("haptics_enabled", true);
        selectedAccent = AppearanceTheme.accentChoice(this);
        previewAccentColor = selectedAccent.color;
        if (state != null) {
            int savedPage = state.getInt("wizard_page", PAGE_WELCOME);
            if (!state.getBoolean("wizard_four_steps", false) && savedPage == 3) savedPage = PAGE_MEET;
            page = Math.max(PAGE_WELCOME, Math.min(PAGE_SETTINGS, savedPage));
            showcaseScene = state.getInt("wizard_scene", 0);
            preferencesExpanded = state.getBoolean("wizard_preferences_expanded", false);
            ageConfirmed = state.getBoolean("wizard_age", ageConfirmed);
            xmegaAccessAuthorized = state.getBoolean("wizard_access", xmegaAccessAuthorized);
            xmegaAccessReady = XMegaDriveAgeSession.isCookieReady(this);
            if (state.getBoolean("wizard_access_pending", false)) {
                // The callback belongs to the old Activity. Require a retry unless
                // setup actually completed while this instance was recreated.
                xmegaAccessAuthorized = XMegaDriveAgeSession.isSetupComplete(this)
                        || xmegaAccessReady;
            }
            favoriteAlerts = state.getBoolean("wizard_favorite_alerts", favoriteAlerts);
            updateAlerts = state.getBoolean("wizard_update_alerts", updateAlerts);
            hapticsEnabled = state.getBoolean("wizard_haptics", hapticsEnabled);
            awaitingNotificationPermission = state.getBoolean("wizard_waiting_permission", false);
        }

        ZeroChillUi.applySystemBars(this);
        buildShell();
        showPage(page, false);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        out.putInt("wizard_page", page);
        out.putBoolean("wizard_four_steps", true);
        out.putInt("wizard_scene", showcaseScene);
        out.putBoolean("wizard_preferences_expanded", preferencesExpanded);
        out.putBoolean("wizard_age", ageConfirmed);
        out.putBoolean("wizard_access", xmegaAccessAuthorized);
        out.putBoolean("wizard_access_pending", accessSetupInProgress);
        out.putBoolean("wizard_favorite_alerts", favoriteAlerts);
        out.putBoolean("wizard_update_alerts", updateAlerts);
        out.putBoolean("wizard_haptics", hapticsEnabled);
        out.putBoolean("wizard_waiting_permission", awaitingNotificationPermission);
        super.onSaveInstanceState(out);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (page == PAGE_SETTINGS && accountCardHost != null) refreshAccountCard();
        if (tour != null) tour.resumeTour();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (tour != null) {
            if (hasFocus) tour.resumeTour(); else tour.pauseTour();
        }
    }

    @Override protected void onPause() {
        if (tour != null) tour.pauseTour();
        cancelAmbientPulse();
        super.onPause();
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
            if (page == PAGE_ACCESS
                    && (!ageConfirmed || !xmegaAccessAuthorized || accessSetupInProgress)) return;
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
        if (tour != null) { tour.pauseTour(); tour = null; }
        accountRefreshEpoch++;
        accountCardHost = null;
        View incoming = buildPage(next);
        View outgoing = pageHost.getChildCount() == 0 ? null : pageHost.getChildAt(0);
        page = next;
        updateChrome();

        if (outgoing == null || !ZeroChillMotion.animationsEnabled(this)) {
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
        column.setGravity(Gravity.CENTER);
        column.setPadding(dp(24), dp(20), dp(24), dp(20));

        FrameLayout mascot = mascot(dp(176));
        LinearLayout.LayoutParams mascotParams = new LinearLayout.LayoutParams(dp(176), dp(176));
        mascotParams.bottomMargin = dp(14);
        column.addView(mascot, mascotParams);

        LinearLayout branding = wordmark(40f);
        branding.setTag("wizard_wordmark");
        column.addView(branding);

        TextView tagline = text("NO LIMITS. ALL CONTENT.", 11f,
                color(R.color.zc_text_secondary), true);
        tagline.setLetterSpacing(0.17f);
        tagline.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams taglineParams = new LinearLayout.LayoutParams(-1, -2);
        taglineParams.topMargin = dp(13);
        column.addView(tagline, taglineParams);

        TextView shortline = text("Your entertainment. Your rules.", 23f, Color.WHITE, true);
        shortline.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams shortParams = new LinearLayout.LayoutParams(-1, -2);
        shortParams.topMargin = dp(30);
        column.addView(shortline, shortParams);

        return scroll;
    }

    private View buildAccessPage() {
        ScrollView scroll = pageScroll();
        LinearLayout column = pageColumn(scroll);

        addEyebrow(column, "18+ ACCESS");
        addTitle(column, "Before you continue");
        addSubtitle(column,
                "ZEROCHILL can open adult and sensitive content. Confirm your age before entering.");

        TextView notice = text("ZEROCHILL can provide access to adult, graphic, violent, and other sensitive material.\n\nContent and source availability may vary by region. Follow local laws and the terms of the services you access.\n\nZEROCHILL is independent and is not affiliated with or endorsed by the websites and services it accesses.", 14f, color(R.color.zc_text_secondary), false);
        notice.setLineSpacing(dp(3), 1.12f);
        column.addView(notice, cardParams());

        accessSetupButton = actionButton(
                xmegaAccessAuthorized && xmegaAccessReady
                        ? "✓ ACCESS READY"
                        : "CONFIRM 18+ & SET UP ACCESS",
                true
        );
        accessSetupButton.setOnClickListener(v -> beginAccessSetup());
        LinearLayout.LayoutParams setupParams = new LinearLayout.LayoutParams(-1, dp(52));
        setupParams.topMargin = dp(9);
        column.addView(accessSetupButton, setupParams);

        accessSetupStatus = text(
                xmegaAccessAuthorized
                        ? (xmegaAccessReady
                        ? "✓ Age confirmed. XMegaDrive access is ready."
                        : "Age confirmed. ZEROCHILL will retry XMegaDrive access when needed.")
                        : "One confirmation prepares both ZEROCHILL 18+ access and XMegaDrive.",
                12.5f,
                color(R.color.zc_text_muted),
                false
        );
        accessSetupStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(10);
        column.addView(accessSetupStatus, statusParams);
        return scroll;
    }

    private void beginAccessSetup() {
        if (accessSetupInProgress || xmegaAccessAuthorized && xmegaAccessReady) return;
        ageConfirmed = true;
        xmegaAccessAuthorized = true;
        accessSetupInProgress = true;
        if (accessSetupButton != null) {
            accessSetupButton.setText("SETTING UP…");
            accessSetupButton.setEnabled(false);
            accessSetupButton.setAlpha(0.78f);
        }
        if (accessSetupStatus != null) {
            accessSetupStatus.setText("Preparing XMegaDrive access on this device…");
        }
        updateNextButtonState();
        XMegaDriveAgeSession.authorize(this, ready -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            accessSetupInProgress = false;
            xmegaAccessReady = ready;
            if (accessSetupButton != null) {
                accessSetupButton.setText(ready ? "✓ ACCESS READY" : "RETRY XMEGADRIVE SETUP");
                accessSetupButton.setEnabled(!ready);
                accessSetupButton.setAlpha(1f);
            }
            if (accessSetupStatus != null) {
                accessSetupStatus.setText(ready
                        ? "✓ Age confirmed. XMegaDrive access is ready."
                        : "Age confirmed. ZEROCHILL will retry XMegaDrive access when needed.");
            }
            updateNextButtonState();
        }));
    }

    private View buildMeetPage() {
        tour = new CinematicTourView(this, previewAccentColor, showcaseScene, index -> showcaseScene = index);
        return tour;
    }

    private View buildSettingsPage() {
        ScrollView scroll = pageScroll();
        LinearLayout column = pageColumn(scroll);
        addEyebrow(column, "YOUR DEFAULTS");
        addTitle(column, "Make it yours");
        addSubtitle(column, "Choose your color. Account setup is optional.");

        column.addView(buildAccentCard(), cardParams());

        accountCardHost = new LinearLayout(this);
        accountCardHost.setOrientation(LinearLayout.VERTICAL);
        column.addView(accountCardHost, cardParams());
        refreshAccountCard();

        LinearLayout preferences = new LinearLayout(this);
        preferences.setOrientation(LinearLayout.VERTICAL);
        TextView expand = actionButton(preferencesExpanded ? "−  Notification & haptic preferences" : "+  Notification & haptic preferences", false);
        column.addView(expand, new LinearLayout.LayoutParams(-1, dp(48)));
        column.addView(preferences, cardParams());
        preferences.setVisibility(preferencesExpanded ? View.VISIBLE : View.GONE);
        expand.setContentDescription("Notification and haptic preferences, " + (preferencesExpanded ? "expanded" : "collapsed"));
        expand.setOnClickListener(v -> {
            preferencesExpanded = !preferencesExpanded;
            expand.setContentDescription("Notification and haptic preferences, " + (preferencesExpanded ? "expanded" : "collapsed"));
            preferences.setVisibility(preferencesExpanded ? View.VISIBLE : View.GONE);
            expand.setText((preferencesExpanded ? "−  " : "+  ") + "Notification & haptic preferences");
        });

        preferences.addView(settingsSwitch(
                "Favorite creator alerts",
                "Notify me when creators I follow have new content.",
                favoriteAlerts,
                checked -> favoriteAlerts = checked
        ), cardParams());

        preferences.addView(settingsSwitch(
                "ZEROCHILL update alerts",
                "Notify me when a new app version is available.",
                updateAlerts,
                checked -> updateAlerts = checked
        ), cardParams());

        preferences.addView(settingsSwitch(
                "Haptic feedback",
                "Use subtle vibration for controls and interactions.",
                hapticsEnabled,
                checked -> hapticsEnabled = checked
        ), cardParams());

        TextView note = text(
                "If alerts are on, Android may ask for notification permission when you finish.",
                12f,
                color(R.color.zc_text_muted),
                false
        );
        note.setLineSpacing(0f, 1.1f);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.topMargin = dp(8);
        column.addView(note, noteParams);
        TextView later = text("Change these later in Settings. Replay from About ZEROCHILL.", 12f,
                color(R.color.zc_text_muted), false);
        later.setGravity(Gravity.CENTER);
        column.addView(later, cardParams());
        return scroll;
    }

    private View buildAccentCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));

        TextView title = text("MAKE IT YOUR COLOR", 12f, UiPalette.TEXT_SECONDARY, true);
        title.setLetterSpacing(.11f);
        card.addView(title);
        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(dp(8), dp(5), dp(8), dp(13));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, -2);
        previewParams.topMargin = dp(4);
        card.addView(preview, previewParams);
        accentPreviewMascot = mascot(dp(76));
        preview.addView(accentPreviewMascot, new LinearLayout.LayoutParams(dp(76), dp(76)));
        LinearLayout mark = wordmark(27f);
        accentPreviewChill = (TextView) mark.getChildAt(1);
        preview.addView(mark);
        accentPreviewButton = null;
        updateAccentPreviewColor(previewAccentColor);

        LinearLayout swatchRows = new LinearLayout(this);
        swatchRows.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        swatchRows.addView(row, new LinearLayout.LayoutParams(-1, -2));
        accentSwatches.clear();
        for (AppearanceTheme.Accent accent : AppearanceTheme.Accent.values()) {
            if (accentSwatches.size() == 4) {
                row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER);
                swatchRows.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout swatch = new LinearLayout(this);
            swatch.setOrientation(LinearLayout.VERTICAL);
            swatch.setGravity(Gravity.CENTER);
            swatch.setClickable(true);
            swatch.setFocusable(true);
            swatch.setTag(accent);
            swatch.setOnClickListener(v -> selectAccent(accent, v));
            TextView circle = text("", 23f, Color.BLACK, true);
            circle.setGravity(Gravity.CENTER);
            swatch.addView(circle, new LinearLayout.LayoutParams(dp(48), dp(48)));
            TextView label = text(accent.label, 11f, color(R.color.zc_text_secondary), true);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, dp(22));
            labelParams.topMargin = dp(2);
            swatch.addView(label, labelParams);
            swatch.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(
                        View host, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName("android.widget.RadioButton");
                    info.setCheckable(true);
                    info.setChecked(selectedAccent == accent);
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(72), 1f);
            row.addView(swatch, params);
            accentSwatches.add(swatch);
        }
        card.addView(swatchRows, new LinearLayout.LayoutParams(-1, -2));
        updateAccentSwatches();
        return card;
    }

    private void selectAccent(AppearanceTheme.Accent accent, View clicked) {
        if (selectedAccent == accent) return;
        haptic(clicked);
        selectedAccent = accent;
        AppearanceTheme.setAccent(this, accent);
        updateAccentSwatches();
        if (accentAnimator != null) accentAnimator.cancel();
        if (ZeroChillMotion.animationsEnabled(this)) {
            accentAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), previewAccentColor, accent.color);
            accentAnimator.setDuration(ZeroChillMotion.STANDARD_MS);
            accentAnimator.setInterpolator(new DecelerateInterpolator());
            accentAnimator.addUpdateListener(animation ->
                    updateAccentPreviewColor((int) animation.getAnimatedValue()));
            accentAnimator.start();
        } else {
            updateAccentPreviewColor(accent.color);
        }
    }

    private void updateAccentSwatches() {
        for (LinearLayout swatch : accentSwatches) {
            AppearanceTheme.Accent accent = (AppearanceTheme.Accent) swatch.getTag();
            boolean selected = accent == selectedAccent;
            swatch.setSelected(selected);
            swatch.setContentDescription(accent.label + " accent" + (selected ? ", selected" : ""));
            TextView circle = (TextView) swatch.getChildAt(0);
            TextView label = (TextView) swatch.getChildAt(1);
            circle.setText(selected ? "✓" : "");
            label.setTextColor(selected ? accent.color : color(R.color.zc_text_secondary));
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(accent.color);
            bg.setStroke(dp(selected ? 3 : 1), selected ? Color.WHITE : Color.rgb(86, 92, 102));
            circle.setBackground(bg);
        }
    }

    private void updateAccentPreviewColor(int accent) {
        previewAccentColor = accent;
        for (View glow : ambientGlows) tintAmbient(glow, accent);
        if (accentPreviewMascot != null) {
            MascotAccent.applyGlow(accentPreviewMascot.getChildAt(0), accent);
            MascotAccent.apply((ImageView) accentPreviewMascot.getChildAt(1),
                    (ImageView) accentPreviewMascot.getChildAt(7),
                    (ImageView) accentPreviewMascot.getChildAt(3),
                    (ImageView) accentPreviewMascot.getChildAt(4),
                    (ImageView) accentPreviewMascot.getChildAt(6), accent);
        }
        if (accentPreviewChill != null) accentPreviewChill.setTextColor(accent);
        if (accentPreviewButton != null) {
            GradientDrawable background = new GradientDrawable();
            background.setColor(accent);
            background.setCornerRadius(dp(12));
            accentPreviewButton.setBackground(background);
            accentPreviewButton.setTextColor(selectedAccent == AppearanceTheme.Accent.WHITE
                    ? Color.rgb(21, 32, 46) : Color.rgb(2, 15, 23));
        }
        if (nextButton != null) {
            nextButton.setBackground(primaryButtonBackground(nextButton.isEnabled()));
            nextButton.setTextColor(selectedAccent == AppearanceTheme.Accent.WHITE
                    ? Color.rgb(21, 32, 46) : Color.rgb(2, 15, 23));
        }
        if (progressRow != null) {
            for (int i = 0; i < progressRow.getChildCount(); i++) {
                if (i == page) {
                    GradientDrawable dot = new GradientDrawable();
                    dot.setColor(accent);
                    dot.setCornerRadius(dp(5));
                    progressRow.getChildAt(i).setBackground(dot);
                }
            }
        }
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
        TextView label = text(eyebrow, 12f, UiPalette.TEXT_SECONDARY, true);
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
                "Connect your identity, favorite creators, comments and messages.",
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
            bg.setColor(active ? previewAccentColor : Color.rgb(61, 65, 72));
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
        boolean enabled = page != PAGE_ACCESS
                || (ageConfirmed && xmegaAccessAuthorized && !accessSetupInProgress);
        nextButton.setEnabled(enabled);
        nextButton.setAlpha(enabled ? 1f : 0.42f);
        nextButton.setBackground(primaryButtonBackground(enabled));
        nextButton.setTextColor(AppearanceTheme.onAccent(this));
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
        if (xmegaAccessAuthorized) XMegaDriveAgeSession.markSetupComplete(this);
        GestureGuideDialog.markSeen(this);
        NotificationCoordinator.markOnboardingHandled(this);
        StartupWizardPolicy.markComplete(this);
        if (!preview) WhatsNewPolicy.suppressForCurrentVersion(this);
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
        if (page == PAGE_MEET && tour != null && hasWindowFocus()) tour.resumeTour();
        if (page == PAGE_WELCOME && ZeroChillMotion.animationsEnabled(this)) {
            nextButton.setScaleX(.94f);
            nextButton.setScaleY(.94f);
            nextButton.animate().scaleX(1f).scaleY(1f).setDuration(420L)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(.9f)).start();
            View branding = view.findViewWithTag("wizard_wordmark");
            if (branding != null) {
                branding.setAlpha(0f);
                branding.setTranslationY(dp(7));
                branding.animate().alpha(1f).translationY(0f).setDuration(420L)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
            View outline = view.findViewWithTag("wizard_mascot_outline");
            if (outline != null) {
                ambientPulse = ObjectAnimator.ofFloat(outline, View.ALPHA, .15f, 1f, .72f, 1f);
                ambientPulse.setDuration(1450L);
                ambientPulse.setRepeatCount(1);
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
        if (accentAnimator != null) accentAnimator.cancel();
        super.onDestroy();
    }

    private FrameLayout mascot(int size) {
        FrameLayout mascot = new FrameLayout(this);
        View glow = new View(this);
        mascot.addView(glow, new FrameLayout.LayoutParams(size, size));
        MascotAccent.applyGlow(glow, previewAccentColor);
        int[] layers = {
                R.drawable.zc_devil_horns,
                R.drawable.zc_devil_face,
                R.drawable.zc_devil_x_eye,
                R.drawable.zc_devil_angry_eye,
                R.drawable.zc_devil_teeth,
                R.drawable.zc_devil_tongue,
                R.drawable.zc_devil_outline
        };
        ImageView horns = null, xEye = null, angryEye = null, tongue = null, outline = null;
        for (int index = 0; index < layers.length; index++) {
            ImageView layer = new ImageView(this);
            layer.setImageResource(layers[index]);
            layer.setScaleType(ImageView.ScaleType.FIT_CENTER);
            layer.setContentDescription(index == layers.length - 1 ? "ZEROCHILL mascot" : null);
            if (index == layers.length - 1) layer.setTag("wizard_mascot_outline");
            if (index == 0) horns = layer;
            if (index == 2) xEye = layer;
            if (index == 3) angryEye = layer;
            if (index == 5) tongue = layer;
            if (index == 6) outline = layer;
            mascot.addView(layer, new FrameLayout.LayoutParams(size, size));
        }
        MascotAccent.apply(horns, outline, xEye, angryEye, tongue, previewAccentColor);
        return mascot;
    }

    private LinearLayout wordmark(float size) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        TextView zero = text("ZERO", size, Color.rgb(243, 244, 246), true);
        TextView chill = text("CHILL", size, previewAccentColor, false);
        row.addView(zero);
        row.addView(chill);
        return row;
    }

    private View experienceCard(int iconRes, String title, String body) {
        LinearLayout card = cardRow();
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(UiPalette.TEXT_SECONDARY);
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
        TextView badgeView = text(badge, badge.length() > 3 ? 12f : 17f, UiPalette.TEXT_PRIMARY, true);
        badgeView.setGravity(Gravity.CENTER);
        badgeView.setBackground(pill(UiPalette.RAISED));
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
        TextView labelView = text(label, 12f, UiPalette.TEXT_SECONDARY, true);
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
        TextView eyebrow = text(value, 12f, UiPalette.TEXT_SECONDARY, true);
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
                primary ? AppearanceTheme.onAccent(this) : Color.WHITE,
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
        background.setColor(enabled ? previewAccentColor : Color.rgb(47, 54, 59));
        background.setCornerRadius(dp(17));
        background.setStroke(dp(1), enabled ? previewAccentColor : Color.rgb(67, 72, 78));
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
        background.setColor(UiPalette.SURFACE);
        background.setCornerRadius(dp(radius));
        background.setStroke(dp(1), UiPalette.DIVIDER);
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
        ambientGlows.add(glow);
        glow.setTag((float) Math.max(width, height) * .52f);
        tintAmbient(glow, previewAccentColor);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.gravity = gravity;
        params.leftMargin = marginX;
        params.rightMargin = marginX;
        params.topMargin = marginY;
        params.bottomMargin = marginY;
        parent.addView(glow, params);
    }

    private void tintAmbient(View glow, int accent) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        background.setGradientRadius((Float) glow.getTag());
        background.setColors(new int[]{(accent & 0xffffff) | 0x36000000,
                (accent & 0xffffff) | 0x10000000, Color.TRANSPARENT});
        glow.setBackground(background);
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

}
