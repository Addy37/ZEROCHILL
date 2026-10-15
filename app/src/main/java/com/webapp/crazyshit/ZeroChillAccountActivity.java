package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.InputFilter;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

/** First-party ZeroChill account entry point. Existing app use remains account-optional. */
public final class ZeroChillAccountActivity extends Activity {
    static final String EXTRA_START_CREATE = "zerochill_account_start_create";
    private static final String STATE_CREATE_MODE = "state_create_mode";
    private static final int AVATAR_REQUEST = 6401;
    private static final int SECURITY_REQUEST = 6402;
    private static final int AVATAR_CROP_REQUEST = 6403;
    private String avatarOwner = "";
    private static final String DRAFT_USER = "draft_user";
    private static final String DRAFT_NAME = "draft_name";
    private static final String DRAFT_BIO = "draft_bio";

    private LinearLayout content;
    private ProgressBar progress;
    private boolean createMode;
    private ZeroChillAccountRepository.AccountState account;
    private TextView identityPrimary;
    private TextView identitySecondary;
    private String renderedUserId = "";
    private EditText nameField;
    private EditText bioField;
    private String draftUser = "";
    private String draftName = "";
    private String draftBio = "";
    private boolean hasDraft;
    private boolean editingProfile;
    private boolean resumed;
    private LibrarySocialHubView socialHub;
    private TextView profileBio;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener sessionListener =
            (prefs, key) -> runOnUiThread(this::refreshAccountIfChanged);

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        ZeroChillSessionStore.preferences(this).registerOnSharedPreferenceChangeListener(sessionListener);
        refreshAccountIfChanged();
        if (socialHub != null) socialHub.setActive(true);
    }

    @Override protected void onPause() {
        resumed = false;
        if (socialHub != null) socialHub.setActive(false);
        ZeroChillSessionStore.preferences(this).unregisterOnSharedPreferenceChangeListener(sessionListener);
        super.onPause();
    }

    private void refreshAccountIfChanged() {
        if (!resumed || isFinishing() || isDestroyed() || account == null) return;
        String current = ZeroChillSessionStore.currentUserId(this);
        String shown = account.signedIn ? account.userId : "";
        if (!current.equals(shown)) {
            closeSocialHub();
            clearDraft();
            editingProfile = false;
            account = null;
            content.removeAllViews();
            loadAccount();
        }
    }

    private void closeSocialHub() {
        if (socialHub != null) socialHub.close();
        socialHub = null;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ZeroChillUi.applySystemBars(this);
        boolean requestedCreate = getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_START_CREATE, false);
        createMode = state == null
                ? requestedCreate
                : state.getBoolean(STATE_CREATE_MODE, requestedCreate);
        if (state != null) {
            editingProfile = state.getBoolean("editing_profile", false);
            avatarOwner = state.getString("avatar_owner", "");
            draftUser = state.getString(DRAFT_USER, "");
            draftName = state.getString(DRAFT_NAME, "");
            draftBio = state.getString(DRAFT_BIO, "");
            hasDraft = state.containsKey(DRAFT_USER);
        }
        buildShell();
        ResponsiveFitmentController.applySoon(this);
        if (!handleAuthRedirect(getIntent())) loadAccount();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!handleAuthRedirect(intent)) loadAccount();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        captureDraft();
        out.putBoolean("editing_profile", editingProfile);
        out.putString("avatar_owner", avatarOwner);
        out.putBoolean(STATE_CREATE_MODE, createMode);
        if (hasDraft) {
            out.putString(DRAFT_USER, draftUser);
            out.putString(DRAFT_NAME, draftName);
            out.putString(DRAFT_BIO, draftBio);
        }
        super.onSaveInstanceState(out);
    }

    private void captureDraft() {
        if (account == null || !account.signedIn || nameField == null || bioField == null) return;
        draftUser = account.userId;
        draftName = nameField.getText().toString();
        draftBio = bioField.getText().toString();
        hasDraft = true;
    }

    private boolean sameAccount(String userId) {
        return !isFinishing() && !isDestroyed() && account != null
                && account.signedIn && account.userId.equals(userId)
                && userId.equals(ZeroChillSessionStore.currentUserId(this));
    }

    private void clearDraft() {
        hasDraft = false;
        draftUser = draftName = draftBio = "";
        nameField = bioField = null;
    }

    @Override
    protected void onDestroy() {
        closeSocialHub();
        ResponsiveFitmentController.release(this);
        super.onDestroy();
    }

    private boolean handleAuthRedirect(Intent intent) {
        Uri uri = intent == null ? null : intent.getData();
        if (uri == null
                || !"com.addy37.zerochill".equalsIgnoreCase(uri.getScheme())
                || !"auth".equalsIgnoreCase(uri.getHost())
                || !"/confirmed".equals(uri.getPath())) {
            return false;
        }

        showBusy(true);
        ZeroChillAccountRepository.completeAuthRedirect(
                this,
                uri,
                (state, error) -> runOnUiThread(() -> {
                    showBusy(false);
                    if (error != null) {
                        ZeroChillToast.makeText(
                                this,
                                error.getMessage() == null ? "Email confirmation failed." : error.getMessage(),
                                ZeroChillToast.LENGTH_LONG
                        ).show();
                        createMode = false;
                        showAuth();
                    } else if (state != null && state.signedIn) {
                        account = state;
                        ZeroChillToast.makeText(this, "Email verified.", ZeroChillToast.LENGTH_SHORT).show();
                        showProfile(state);
                    } else {
                        showConfirmed();
                    }
                })
        );
        return true;
    }

    private void buildShell() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.TRANSPARENT);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(10), dp(3), dp(14), dp(3));
        top.setBackground(headerBackground());
        top.setElevation(0f);
        top.setMinimumHeight(dp(58));
        shell.addView(top, new LinearLayout.LayoutParams(-1, -2));

        TextView back = text("‹", 31, Color.WHITE, false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        ZeroChillMotion.installPressFeedback(back);
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(6), 0, 0, 0);
        top.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView title = text("Account", 24, Color.WHITE, true);
        titles.addView(title);
        TextView subtitle = text("ZEROCHILL ID", 11,
                ZeroChillUi.color(this, R.color.zc_text_muted), true);
        subtitle.setLetterSpacing(0.10f);
        titles.addView(subtitle);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(24), dp(22), dp(34));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        progress = new ZeroChillProgressBar(this);
        ZeroChillUi.styleProgress(progress);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(dp(42), dp(42));
        progressParams.gravity = Gravity.CENTER;
        root.addView(progress, progressParams);

        setContentView(root);
    }

    private void loadAccount() {
        showBusy(true);
        if (!ZeroChillAccountRepository.isConfigured()) {
            showBusy(false);
            showMessage("Accounts are not configured in this build.");
            return;
        }
        ZeroChillAccountRepository.current(this, (value, error) -> runOnUiThread(() -> {
            showBusy(false);
            if (isFinishing() || isDestroyed()) return;
            if (error != null) {
                ZeroChillToast.makeText(this, error.getMessage() == null ? "Couldn't load your account." : error.getMessage(), ZeroChillToast.LENGTH_LONG).show();
                if (account != null && account.signedIn) return;
                if (ZeroChillAccountRepository.hasStoredSession(this)) {
                    showRetry();
                    return;
                }
            }
            String sessionUser = ZeroChillSessionStore.currentUserId(this);
            if (value != null && value.signedIn && !value.userId.equals(sessionUser)) {
                loadAccount();
                return;
            }
            if (value != null && value.signedIn) showProfile(value);
            else if (!sessionUser.isEmpty()) showRetry();
            else { account = value; clearDraft(); showAuth(); }
        }));
    }

    private void showAuth() {
        closeSocialHub();
        editingProfile = false;
        renderedUserId = "";
        identityPrimary = identitySecondary = null;
        nameField = bioField = null;
        content.removeAllViews();

        addEyebrow(content, createMode ? "NEW IDENTITY" : "WELCOME BACK");
        content.addView(wordmark(32f));

        TextView copy = text(
                createMode
                        ? "Build your ZEROCHILL identity once, then carry your creators and conversations with you."
                        : "Sign in to your ZEROCHILL identity and pick up where you left off.",
                13,
                ZeroChillUi.color(this, R.color.zc_text_secondary),
                false
        );
        copy.setLineSpacing(0f, 1.12f);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(-1, -2);
        copyParams.topMargin = dp(9);
        copyParams.bottomMargin = dp(18);
        content.addView(copy, copyParams);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(12), dp(12), dp(14));
        panel.setBackground(panelBackground(20));
        content.addView(panel, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(tabs, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView signInTab = tab("SIGN IN", !createMode);
        TextView createTab = tab("CREATE ACCOUNT", createMode);
        tabs.addView(signInTab, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(0, -1, 1f);
        createParams.setMarginStart(dp(7));
        tabs.addView(createTab, createParams);
        signInTab.setOnClickListener(v -> {
            createMode = false;
            showAuth();
        });
        createTab.setOnClickListener(v -> {
            createMode = true;
            showAuth();
        });

        EditText email = field(
                "Email",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        );
        email.setAutofillHints(View.AUTOFILL_HINT_EMAIL_ADDRESS);
        panel.addView(email, fieldParams());

        EditText username = null;
        if (createMode) {
            username = field("Username", InputType.TYPE_CLASS_TEXT);
            username.setAutofillHints(View.AUTOFILL_HINT_USERNAME);
            panel.addView(username, fieldParams());
        }

        EditText password = field(
                "Password",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );
        password.setAutofillHints(createMode
                ? "newPassword"
                : View.AUTOFILL_HINT_PASSWORD);
        panel.addView(password, fieldParams());

        ZeroChillCheckBox adult = null;
        ZeroChillCheckBox terms = null;
        if (createMode) {
            adult = check("I confirm that I am 18 or older.");
            terms = check("I agree to the Terms and Community Rules.");
            panel.addView(adult);
            panel.addView(terms);
        }

        final EditText usernameField = username;
        final ZeroChillCheckBox adultCheck = adult;
        final ZeroChillCheckBox termsCheck = terms;

        TextView submit = primaryButton(createMode ? "CREATE ACCOUNT" : "SIGN IN");
        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(-1, dp(50));
        submitParams.setMargins(0, dp(14), 0, 0);
        panel.addView(submit, submitParams);

        submit.setOnClickListener(v -> {
            String emailValue = email.getText().toString();
            String passwordValue = password.getText().toString();
            if (createMode) {
                String usernameValue = usernameField == null ? "" : usernameField.getText().toString();
                String validation = ZeroChillAccountValidation.email(emailValue);
                if (validation.isEmpty()) validation = ZeroChillAccountValidation.username(usernameValue);
                if (validation.isEmpty()) validation = ZeroChillAccountValidation.password(passwordValue);
                if (!validation.isEmpty()) {
                    ZeroChillToast.makeText(this, validation, ZeroChillToast.LENGTH_LONG).show();
                    return;
                }
                setFormEnabled(false);
                ZeroChillAccountRepository.signUp(
                        this,
                        emailValue,
                        usernameValue,
                        passwordValue,
                        adultCheck != null && adultCheck.isChecked(),
                        termsCheck != null && termsCheck.isChecked(),
                        (state, error) -> runOnUiThread(() -> {
                            setFormEnabled(true);
                            if (error != null) {
                                ZeroChillToast.makeText(this, error.getMessage(), ZeroChillToast.LENGTH_LONG).show();
                            } else if (state != null && state.pendingVerification) {
                                showVerification(state.email);
                            } else if (state != null && state.signedIn) {
                                account = state;
                                showProfile(state);
                            }
                        })
                );
            } else {
                setFormEnabled(false);
                ZeroChillAccountRepository.signIn(
                        this,
                        emailValue,
                        passwordValue,
                        (state, error) -> runOnUiThread(() -> {
                            setFormEnabled(true);
                            if (error != null) {
                                ZeroChillToast.makeText(this, error.getMessage(), ZeroChillToast.LENGTH_LONG).show();
                            } else if (state != null && state.signedIn) {
                                account = state;
                                showProfile(state);
                            }
                        })
                );
            }
        });

        LinearLayout privacyCard = new LinearLayout(this);
        privacyCard.setOrientation(LinearLayout.HORIZONTAL);
        privacyCard.setGravity(Gravity.CENTER_VERTICAL);
        privacyCard.setPadding(dp(13), dp(11), dp(13), dp(11));
        privacyCard.setBackground(panelBackground(16));
        TextView lock = text("◆", 12, UiPalette.TEXT_MUTED, true);
        lock.setGravity(Gravity.TOP);
        privacyCard.addView(lock, new LinearLayout.LayoutParams(dp(24), -2));
        TextView privacy = text(
                "Private by default. Your email is never shown on your public ZEROCHILL profile.",
                11,
                ZeroChillUi.color(this, R.color.zc_text_muted),
                false
        );
        privacy.setLineSpacing(0f, 1.08f);
        privacyCard.addView(privacy, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams privacyParams = new LinearLayout.LayoutParams(-1, -2);
        privacyParams.topMargin = dp(12);
        content.addView(privacyCard, privacyParams);
    }

    private void showVerification(String email) {
        showStatusCard(
                "CHECK YOUR EMAIL",
                "IDENTITY PENDING",
                "We sent a verification message to " + email
                        + ". Tap the link and ZEROCHILL will reopen automatically.",
                "GO TO SIGN IN",
                () -> {
                    createMode = false;
                    showAuth();
                }
        );
    }

    private void showConfirmed() {
        showStatusCard(
                "EMAIL VERIFIED",
                "IDENTITY READY",
                "Your email is confirmed. Sign in to finish opening your ZEROCHILL profile.",
                "SIGN IN",
                () -> {
                    createMode = false;
                    showAuth();
                }
        );
    }

    private void showStatusCard(
            String titleValue,
            String eyebrowValue,
            String bodyValue,
            String actionLabel,
            Runnable action
    ) {
        content.removeAllViews();
        addEyebrow(content, eyebrowValue);
        TextView title = text(titleValue, 28, Color.WHITE, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(7);
        content.addView(title, titleParams);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(panelBackground(19));

        TextView copy = text(
                bodyValue,
                13,
                ZeroChillUi.color(this, R.color.zc_text_secondary),
                false
        );
        copy.setLineSpacing(0f, 1.12f);
        card.addView(copy);

        TextView button = primaryButton(actionLabel);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, dp(48));
        buttonParams.topMargin = dp(16);
        card.addView(button, buttonParams);
        button.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.topMargin = dp(18);
        content.addView(card, cardParams);
    }

    private void showProfile(ZeroChillAccountRepository.AccountState state) {
        if (renderedUserId.equals(state.userId)) captureDraft();
        else if (hasDraft && !draftUser.equals(state.userId)) clearDraft();
        closeSocialHub();
        account = state;
        renderedUserId = state.userId;
        content.removeAllViews();

        addEyebrow(content, "YOUR IDENTITY");

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(15), dp(15), dp(15), dp(15));
        hero.setBackground(panelBackground(20));

        FrameLayout avatarHalo = new FrameLayout(this);
        avatarHalo.setBackground(circle(UiPalette.RAISED));
        avatarHalo.setPadding(dp(3), dp(3), dp(3), dp(3));

        ImageView avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setClipToOutline(true);
        avatar.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        avatar.setBackground(circle(Color.rgb(13, 15, 19)));
        AccountAvatarImages.track(avatar, state.userId);
        String avatarUrl = ZeroChillAccountRepository.avatarUrl(state.avatarPath);
        if (!avatarUrl.isEmpty()) {
            AccountAvatarImages.bind(avatar, state.userId, state.avatarPath);
        } else {
            avatar.setImageResource(R.drawable.ic_more_account);
            avatar.setPadding(dp(20), dp(20), dp(20), dp(20));
            avatar.setColorFilter(UiPalette.TEXT_SECONDARY);
        }
        avatarHalo.addView(avatar, new FrameLayout.LayoutParams(-1, -1));
        hero.addView(avatarHalo, new LinearLayout.LayoutParams(dp(82), dp(82)));

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setPadding(dp(14), 0, 0, 0);

        TextView username = text(
                SocialUi.name(state.displayName, state.username),
                21,
                Color.WHITE,
                true
        );
        identity.addView(username);
        identityPrimary = username;

        {
            TextView display = text(
                    SocialUi.cleanName(state.username),
                    13,
                    UiPalette.TEXT_SECONDARY,
                    true
            );
            LinearLayout.LayoutParams displayParams = new LinearLayout.LayoutParams(-1, -2);
            displayParams.topMargin = dp(3);
            identity.addView(display, displayParams);
            display.setVisibility(state.displayName.isEmpty() ? View.GONE : View.VISIBLE);
            identitySecondary = display;
        }

        TextView email = text(
                state.email,
                11,
                ZeroChillUi.color(this, R.color.zc_text_muted),
                false
        );
        LinearLayout.LayoutParams emailParams = new LinearLayout.LayoutParams(-1, -2);
        emailParams.topMargin = dp(5);
        identity.addView(email, emailParams);
        profileBio = text(state.bio, 12, ZeroChillUi.color(this, R.color.zc_text_secondary), false);
        profileBio.setVisibility(state.bio.isEmpty() ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams profileBioParams = new LinearLayout.LayoutParams(-1, -2);
        profileBioParams.topMargin = dp(8);
        identity.addView(profileBio, profileBioParams);
        TextView publicLink = text("View public profile  ›", 12, UiPalette.PRIMARY, true);
        publicLink.setGravity(Gravity.CENTER_VERTICAL);
        publicLink.setMinHeight(dp(48));
        publicLink.setContentDescription("View public profile");
        publicLink.setOnClickListener(v -> {
            Intent intent = new Intent(this, ZeroChillPublicProfileActivity.class);
            intent.putExtra(ZeroChillPublicProfileActivity.EXTRA_USER_ID, state.userId);
            startActivity(intent);
        });
        ZeroChillMotion.installPressFeedback(publicLink);
        identity.addView(publicLink);
        hero.addView(identity, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, -2);
        heroParams.topMargin = dp(9);
        content.addView(hero, heroParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, dp(48));
        actionsParams.topMargin = dp(9);
        content.addView(actions, actionsParams);

        TextView changeAvatar = secondaryButton("CHANGE AVATAR");
        changeAvatar.setOnClickListener(v -> chooseAvatar());
        actions.addView(changeAvatar, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView editProfile = secondaryButton("EDIT PROFILE");
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(0, -1, 1f);
        editParams.setMarginStart(dp(8));
        actions.addView(editProfile, editParams);

        LinearLayout editor = new LinearLayout(this);
        editor.setOrientation(LinearLayout.VERTICAL);
        editor.setVisibility(editingProfile ? View.VISIBLE : View.GONE);
        content.addView(editor, new LinearLayout.LayoutParams(-1, -2));
        editProfile.setOnClickListener(v -> {
            editingProfile = !editingProfile;
            editor.setVisibility(editingProfile ? View.VISIBLE : View.GONE);
            editProfile.setText(editingProfile ? "CLOSE EDITOR" : "EDIT PROFILE");
        });
        editProfile.setText(editingProfile ? "CLOSE EDITOR" : "EDIT PROFILE");

        TextView section = text("PROFILE", 12, UiPalette.TEXT_MUTED, true);
        section.setLetterSpacing(0.11f);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2);
        sectionParams.topMargin = dp(24);
        editor.addView(section, sectionParams);

        TextView nameLabel = text("Display name", 12, ZeroChillUi.color(this, R.color.zc_text_secondary), false);
        LinearLayout.LayoutParams nameLabelParams = new LinearLayout.LayoutParams(-1, -2);
        nameLabelParams.topMargin = dp(12);
        editor.addView(nameLabel, nameLabelParams);
        EditText displayName = field("Display name (optional)", InputType.TYPE_CLASS_TEXT);
        displayName.setFilters(new InputFilter[]{codePointFilter(40)});
        displayName.setText(hasDraft && state.userId.equals(draftUser) ? draftName : state.displayName);
        displayName.setAutofillHints(View.AUTOFILL_HINT_NAME);
        displayName.setContentDescription("Display name");
        editor.addView(displayName, fieldParams());
        nameField = displayName;

        TextView bioLabel = text("Bio", 12, ZeroChillUi.color(this, R.color.zc_text_secondary), false);
        LinearLayout.LayoutParams bioLabelParams = new LinearLayout.LayoutParams(-1, -2);
        bioLabelParams.topMargin = dp(12);
        editor.addView(bioLabel, bioLabelParams);
        EditText bio = field("Bio (optional)",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        bio.setSingleLine(false);
        bio.setMinLines(3);
        bio.setMaxLines(5);
        bio.setGravity(Gravity.TOP | Gravity.START);
        bio.setPadding(dp(15), dp(13), dp(15), dp(13));
        bio.setFilters(new InputFilter[]{codePointFilter(160)});
        bio.setText(hasDraft && state.userId.equals(draftUser) ? draftBio : state.bio);
        bio.setContentDescription("Bio");
        LinearLayout.LayoutParams bioParams = new LinearLayout.LayoutParams(-1, -2);
        bioParams.topMargin = dp(10);
        editor.addView(bio, bioParams);
        bioField = bio;

        TextView counter = text(Character.codePointCount(bio.getText(), 0, bio.length()) + "/160", 11,
                ZeroChillUi.color(this, R.color.zc_text_muted), false);
        counter.setGravity(Gravity.END);
        editor.addView(counter);
        bio.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                counter.setText(Character.codePointCount(s, 0, s.length()) + "/160");
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        TextView save = primaryButton("SAVE PROFILE");
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(48));
        saveParams.topMargin = dp(10);
        editor.addView(save, saveParams);
        save.setOnClickListener(v -> {
            String owner = state.userId;
            if (!sameAccount(owner)) return;
            String newName = displayName.getText().toString();
            String newBio = bio.getText().toString();
            save.setEnabled(false);
            ZeroChillAccountRepository.updateProfile(this, newName, newBio,
                    (updated, error) -> runOnUiThread(() -> {
                        if (!sameAccount(owner)) return;
                        save.setEnabled(true);
                        if (error != null) {
                            ZeroChillToast.makeText(this, error.getMessage(), ZeroChillToast.LENGTH_LONG).show();
                        } else if (updated != null) {
                            account = updated;
                            hasDraft = false;
                            draftUser = draftName = draftBio = "";
                            if (identityPrimary != null) identityPrimary.setText(
                                    SocialUi.name(updated.displayName, updated.username));
                            if (identitySecondary != null) {
                                identitySecondary.setText(SocialUi.cleanName(updated.username));
                                identitySecondary.setVisibility(updated.displayName.isEmpty()
                                        ? View.GONE : View.VISIBLE);
                            }
                            profileBio.setText(updated.bio);
                            profileBio.setVisibility(updated.bio.isEmpty() ? View.GONE : View.VISIBLE);
                            save.setText("SAVED");
                            if (ZeroChillMotion.animationsEnabled(this)) {
                                save.setAlpha(0.6f);
                                save.animate().alpha(1f).setDuration(ZeroChillMotion.QUICK_MS).start();
                            }
                            save.postDelayed(() -> {
                                if (!isFinishing() && !isDestroyed()) save.setText("SAVE PROFILE");
                            }, 1200L);
                            ZeroChillToast.makeText(this, "Profile updated.", ZeroChillToast.LENGTH_SHORT).show();
                        }
                    }));
        });

        socialHub = new LibrarySocialHubView(this, "SOCIAL");
        LinearLayout.LayoutParams socialParams = new LinearLayout.LayoutParams(-1, -2);
        socialParams.topMargin = dp(24);
        content.addView(socialHub, socialParams);
        socialHub.setActive(resumed);

        section("ACCOUNT & SECURITY");
        settingsRow("Change email", android.R.drawable.ic_dialog_email, () -> openSecurity("email"));
        settingsRow("Change password", android.R.drawable.ic_lock_lock, () -> openSecurity("password"));
        settingsRow("Delete account", android.R.drawable.ic_menu_delete, () -> openSecurity("delete"));

        settingsRow("Notification preferences", android.R.drawable.ic_dialog_info, () -> openSocial("notifications"));
        settingsRow("Blocked users", android.R.drawable.ic_menu_close_clear_cancel, () -> openSocial("blocked"));

        TextView signOut = secondaryButton("SIGN OUT");
        signOut.setTextColor(Color.rgb(228, 138, 138));
        signOut.setOnClickListener(v -> {
            signOut.setEnabled(false);
            String owner = state.userId;
            ZeroChillAccountRepository.signOut(this, (ignored, error) -> runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || account == null || !owner.equals(account.userId)) return;
                account = null;
                clearDraft();
                createMode = false;
                showAuth();
            }));
        });
        LinearLayout.LayoutParams signOutParams = new LinearLayout.LayoutParams(-1, dp(48));
        signOutParams.topMargin = dp(16);
        content.addView(signOut, signOutParams);

        TextView footer = text(
                "SYNCED TO ZEROCHILL\nYour creators, comments, replies, likes, and DMs stay linked to this identity.",
                11, ZeroChillUi.color(this, R.color.zc_text_muted), false);
        footer.setLineSpacing(dp(5), 1f);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.topMargin = dp(20);
        content.addView(footer, footerParams);
    }

    static InputFilter codePointFilter(int maximum) {
        return (source, start, end, dest, dstart, dend) -> {
            String retained = dest.subSequence(0, dstart).toString() + dest.subSequence(dend, dest.length());
            int remaining = maximum - retained.codePointCount(0, retained.length());
            if (remaining <= 0) return "";
            int added = Character.codePointCount(source, start, end);
            if (added <= remaining) return null;
            int stop = Character.offsetByCodePoints(source, start, remaining);
            return source.subSequence(start, stop);
        };
    }

    private void section(String title) {
        TextView heading = text(title, 12, UiPalette.TEXT_MUTED, true);
        heading.setLetterSpacing(0.11f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(25);
        params.bottomMargin = dp(7);
        content.addView(heading, params);
    }

    private void settingsRow(String label, int iconRes, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(7), dp(14), dp(7));
        row.setMinimumHeight(dp(54));
        row.setBackground(panelBackground(14));
        ImageView symbol = new ImageView(this);
        symbol.setImageResource(iconRes);
        symbol.setColorFilter(UiPalette.TEXT_SECONDARY);
        symbol.setPadding(dp(4), dp(4), dp(4), dp(4));
        row.addView(symbol, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView title = text(label, 14, Color.WHITE, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.setMarginStart(dp(10));
        row.addView(title, titleParams);
        row.addView(text("›", 24, ZeroChillUi.color(this, R.color.zc_text_muted), false));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = dp(7);
        content.addView(row, rowParams);
        row.setContentDescription(label);
        row.setOnClickListener(v -> { captureDraft(); action.run(); });
        ZeroChillMotion.installPressFeedback(row);
    }

    private void openSecurity(String mode) {
        Intent intent = new Intent(this, ZeroChillAccountSecurityActivity.class);
        intent.putExtra(ZeroChillAccountSecurityActivity.EXTRA_MODE, mode);
        startActivityForResult(intent, SECURITY_REQUEST);
    }

    private void openSocial(String mode) {
        Intent intent = new Intent(this, ZeroChillSocialSettingsActivity.class);
        intent.putExtra(ZeroChillSocialSettingsActivity.EXTRA_MODE, mode);
        startActivity(intent);
    }

    private boolean avatarOwnerCurrent(String owner) {
        return !isFinishing() && !isDestroyed() && !owner.isEmpty()
                && owner.equals(ZeroChillSessionStore.currentUserId(this));
    }

    private void chooseAvatar() {
        if (account == null || !account.signedIn) return;
        avatarOwner = account.userId;
        captureDraft();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, AVATAR_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SECURITY_REQUEST) {
            if (resultCode == RESULT_OK) loadAccount();
            return;
        }
        if (requestCode == AVATAR_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && avatarOwnerCurrent(avatarOwner)) {
                startActivityForResult(CreatorAvatarCropActivity.createLocal(this, data.getData()), AVATAR_CROP_REQUEST);
            }
            return;
        }
        if (requestCode != AVATAR_CROP_REQUEST || resultCode != RESULT_OK || data == null) return;
        String owner = avatarOwner;
        java.io.File crop = new java.io.File(data.getStringExtra(CreatorAvatarCropActivity.EXTRA_CROPPED_FILE) == null
                ? "" : data.getStringExtra(CreatorAvatarCropActivity.EXTRA_CROPPED_FILE));
        try {
            java.io.File folder = new java.io.File(getCacheDir(), "profile-avatar-crops");
            if (!crop.getCanonicalFile().getParentFile().equals(folder.getCanonicalFile())) return;
            if (!avatarOwnerCurrent(owner)) { crop.delete(); return; }
            if (crop.length() == 0 || crop.length() > 512 * 1024) throw new java.io.IOException("Invalid crop.");
            byte[] jpeg = java.nio.file.Files.readAllBytes(crop.toPath());
            crop.delete();
            showBusy(true);
            ZeroChillAccountRepository.uploadAvatar(this, owner, jpeg, (updated, error) -> runOnUiThread(() -> {
                if (!avatarOwnerCurrent(owner)) return;
                showBusy(false);
                if (error != null) {
                    ZeroChillToast.makeText(this, error.getMessage(), ZeroChillToast.LENGTH_LONG).show();
                } else {
                    AccountAvatarImages.changed(owner, updated.avatarPath);
                    UpdateInboxStore.refreshActorAvatar(this, owner, updated.avatarPath);
                    showProfile(updated);
                    ZeroChillToast.makeText(this, "Avatar updated.", ZeroChillToast.LENGTH_SHORT).show();
                }
            }));
        } catch (Exception error) {
            crop.delete();
            ZeroChillToast.makeText(this, "Couldn't read that crop.", ZeroChillToast.LENGTH_LONG).show();
        }
    }

    private EditText field(String hint, int inputType) {
        EditText view = new ZeroChillEditText(this);
        view.setHint(hint);
        view.setHintTextColor(ZeroChillUi.color(this, R.color.zc_text_muted));
        view.setTextColor(Color.rgb(242, 244, 247));
        view.setTextSize(14);
        view.setSingleLine(true);
        view.setInputType(inputType);
        view.setPadding(dp(15), 0, dp(15), 0);
        view.setBackgroundResource(R.drawable.zc_input_field);
        view.setSelectAllOnFocus(false);
        return view;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50));
        params.setMargins(0, dp(10), 0, 0);
        return params;
    }

    private ZeroChillCheckBox check(String label) {
        ZeroChillCheckBox view = new ZeroChillCheckBox(this);
        view.setText(label);
        view.setTextColor(ZeroChillUi.color(this, R.color.zc_text_secondary));
        view.setTextSize(12.5f);
        view.setPadding(0, dp(6), 0, 0);
        return view;
    }

    private TextView tab(String label, boolean selected) {
        TextView view = text(
                label,
                11,
                selected ? UiPalette.PRIMARY : ZeroChillUi.color(this, R.color.zc_text_secondary),
                true
        );
        view.setGravity(Gravity.CENTER);
        view.setLetterSpacing(0.04f);
        view.setBackground(selected ? selectedTabBackground() : quietTabBackground());
        ZeroChillMotion.installPressFeedback(view);
        return view;
    }

    private TextView primaryButton(String label) {
        TextView view = text(label, 14, UiPalette.TEXT_PRIMARY, true);
        ZeroChillUi.styleAction(view, true);
        ZeroChillMotion.installPressFeedback(view);
        return view;
    }

    private TextView secondaryButton(String label) {
        TextView view = text(label, 14, UiPalette.TEXT_PRIMARY, true);
        ZeroChillUi.styleAction(view, false);
        ZeroChillMotion.installPressFeedback(view);
        return view;
    }

    private void addEyebrow(LinearLayout parent, String value) {
        TextView eyebrow = text(value, 12, UiPalette.TEXT_MUTED, true);
        eyebrow.setLetterSpacing(0.12f);
        parent.addView(eyebrow);
    }

    private LinearLayout wordmark(float size) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView zero = text("ZERO", Math.round(size), Color.rgb(243, 244, 246), true);
        TextView chill = text("CHILL", Math.round(size), UiPalette.PRIMARY, false);
        row.addView(zero);
        row.addView(chill);
        return row;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private GradientDrawable fieldBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiPalette.SURFACE);
        background.setCornerRadius(dp(15));
        background.setStroke(dp(1), UiPalette.DIVIDER);
        return background;
    }

    private GradientDrawable panelBackground(int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiPalette.SURFACE);
        background.setCornerRadius(dp(radiusDp));
        background.setStroke(dp(1), UiPalette.DIVIDER);
        return background;
    }

    private GradientDrawable selectedTabBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiPalette.PRIMARY_CONTAINER);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), UiPalette.PRIMARY);
        return background;
    }

    private GradientDrawable quietTabBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiPalette.SURFACE);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), UiPalette.DIVIDER);
        return background;
    }

    private GradientDrawable circle(int fill) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(fill);
        return background;
    }

    private GradientDrawable headerBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiPalette.BACKGROUND);
        return background;
    }

    private void setFormEnabled(boolean enabled) {
        setEnabledRecursive(content, enabled);
        progress.setVisibility(enabled ? View.GONE : View.VISIBLE);
    }

    private void setEnabledRecursive(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (!(view instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup group = (android.view.ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            setEnabledRecursive(group.getChildAt(i), enabled);
        }
    }

    private void showBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        content.setVisibility(busy ? View.INVISIBLE : View.VISIBLE);
    }

    private void showRetry() {
        content.removeAllViews();
        content.addView(text("Couldn't load your account. Check your connection and try again.",
                14, ZeroChillUi.color(this, R.color.zc_text_secondary), false));
        TextView retry = primaryButton("TRY AGAIN");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.topMargin = dp(20);
        content.addView(retry, params);
        retry.setOnClickListener(v -> loadAccount());
    }

    private void showMessage(String message) {
        content.removeAllViews();
        content.addView(text(message, 14, ZeroChillUi.color(this, R.color.zc_text_secondary), false));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

