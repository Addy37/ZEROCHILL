package com.webapp.crazyshit;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** First-party ZeroChill account entry point. Existing app use remains account-optional. */
public final class ZeroChillAccountActivity extends Activity {
    private static final int AVATAR_REQUEST = 6401;
    private static final int SECURITY_REQUEST = 6402;
    private static final String DRAFT_USER = "draft_user";
    private static final String DRAFT_NAME = "draft_name";
    private static final String DRAFT_BIO = "draft_bio";

    private LinearLayout content;
    private ProgressBar progress;
    private FrameLayout accountRoot;
    private View ambientGlow;
    private AnimatorSet ambientAnimator;
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

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ZeroChillUi.applySystemBars(this);
        if (state != null) {
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
    protected void onStart() {
        super.onStart();
        if (accountRoot != null) accountRoot.post(this::startAmbientMotion);
    }

    @Override
    protected void onStop() {
        stopAmbientMotion();
        super.onStop();
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
                        Toast.makeText(
                                this,
                                error.getMessage() == null ? "Email confirmation failed." : error.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                        createMode = false;
                        showAuth();
                    } else if (state != null && state.signedIn) {
                        account = state;
                        Toast.makeText(this, "Email verified.", Toast.LENGTH_SHORT).show();
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
        accountRoot = root;
        ambientGlow = addAmbientGlow(root, dp(320));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.TRANSPARENT);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(10), dp(3), dp(14), dp(3));
        top.setBackground(headerBackground());
        top.setElevation(0f);
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

        TextView title = text("Account", 19, Color.WHITE, true);
        titles.addView(title);
        TextView subtitle = text("ZEROCHILL ID", 9,
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

        progress = new ProgressBar(this);
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
                Toast.makeText(this, error.getMessage() == null ? "Couldn't load your account." : error.getMessage(), Toast.LENGTH_LONG).show();
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
        panel.addView(tabs, new LinearLayout.LayoutParams(-1, dp(42)));

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

        MaterialCheckBox adult = null;
        MaterialCheckBox terms = null;
        if (createMode) {
            adult = check("I confirm that I am 18 or older.");
            terms = check("I agree to the Terms and Community Rules.");
            panel.addView(adult);
            panel.addView(terms);
        }

        final EditText usernameField = username;
        final MaterialCheckBox adultCheck = adult;
        final MaterialCheckBox termsCheck = terms;

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
                    Toast.makeText(this, validation, Toast.LENGTH_LONG).show();
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
                                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
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
                                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
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
        TextView lock = text("◆", 10, UiPalette.PRIMARY, true);
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
        avatarHalo.setBackground(circle(UiPalette.PRIMARY_CONTAINER));
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
        String avatarUrl = ZeroChillAccountRepository.avatarUrl(state.avatarPath);
        if (!avatarUrl.isEmpty()) {
            Glide.with(avatar).load(avatarUrl).circleCrop().into(avatar);
        } else {
            avatar.setImageResource(R.drawable.ic_more_account);
            avatar.setPadding(dp(20), dp(20), dp(20), dp(20));
            avatar.setColorFilter(UiPalette.PRIMARY);
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
                    UiPalette.PRIMARY,
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
        TextView publicLink = text("View public profile  ›", 12, UiPalette.PRIMARY, true);
        publicLink.setGravity(Gravity.CENTER_VERTICAL);
        publicLink.setMinHeight(dp(42));
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
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, dp(44));
        actionsParams.topMargin = dp(9);
        content.addView(actions, actionsParams);

        TextView changeAvatar = secondaryButton("CHANGE AVATAR");
        changeAvatar.setOnClickListener(v -> chooseAvatar());
        actions.addView(changeAvatar, new LinearLayout.LayoutParams(0, -1, 1f));

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
        LinearLayout.LayoutParams signOutParams = new LinearLayout.LayoutParams(0, -1, 1f);
        signOutParams.setMarginStart(dp(8));
        actions.addView(signOut, signOutParams);

        TextView section = text("PROFILE", 10, UiPalette.PRIMARY, true);
        section.setLetterSpacing(0.11f);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2);
        sectionParams.topMargin = dp(24);
        content.addView(section, sectionParams);

        TextView nameLabel = text("Display name", 12, ZeroChillUi.color(this, R.color.zc_text_secondary), false);
        LinearLayout.LayoutParams nameLabelParams = new LinearLayout.LayoutParams(-1, -2);
        nameLabelParams.topMargin = dp(12);
        content.addView(nameLabel, nameLabelParams);
        EditText displayName = field("Display name (optional)", InputType.TYPE_CLASS_TEXT);
        displayName.setFilters(new InputFilter[]{codePointFilter(40)});
        displayName.setText(hasDraft && state.userId.equals(draftUser) ? draftName : state.displayName);
        displayName.setAutofillHints(View.AUTOFILL_HINT_NAME);
        displayName.setContentDescription("Display name");
        content.addView(displayName, fieldParams());
        nameField = displayName;

        TextView bioLabel = text("Bio", 12, ZeroChillUi.color(this, R.color.zc_text_secondary), false);
        LinearLayout.LayoutParams bioLabelParams = new LinearLayout.LayoutParams(-1, -2);
        bioLabelParams.topMargin = dp(12);
        content.addView(bioLabel, bioLabelParams);
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
        content.addView(bio, bioParams);
        bioField = bio;

        TextView counter = text(Character.codePointCount(bio.getText(), 0, bio.length()) + "/160", 11,
                ZeroChillUi.color(this, R.color.zc_text_muted), false);
        counter.setGravity(Gravity.END);
        content.addView(counter);
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
        content.addView(save, saveParams);
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
                            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
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
                            save.setText("SAVED");
                            if (ZeroChillMotion.animationsEnabled(this)) {
                                save.setAlpha(0.6f);
                                save.animate().alpha(1f).setDuration(ZeroChillMotion.QUICK_MS).start();
                            }
                            save.postDelayed(() -> {
                                if (!isFinishing() && !isDestroyed()) save.setText("SAVE PROFILE");
                            }, 1200L);
                            Toast.makeText(this, "Profile updated.", Toast.LENGTH_SHORT).show();
                        }
                    }));
        });

        section("ACCOUNT & SECURITY");
        settingsRow("Change email", android.R.drawable.ic_dialog_email, () -> openSecurity("email"));
        settingsRow("Change password", android.R.drawable.ic_lock_lock, () -> openSecurity("password"));
        settingsRow("Delete account", android.R.drawable.ic_menu_delete, () -> openSecurity("delete"));

        section("SOCIAL");
        settingsRow("Notification preferences", android.R.drawable.ic_dialog_info, () -> openSocial("notifications"));
        settingsRow("Blocked users", android.R.drawable.ic_menu_close_clear_cancel, () -> openSocial("blocked"));

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
        TextView heading = text(title, 10, UiPalette.PRIMARY, true);
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
        symbol.setColorFilter(UiPalette.PRIMARY);
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

    private void chooseAvatar() {
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
        if (requestCode != AVATAR_REQUEST || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            String owner = account == null ? "" : account.userId;
            captureDraft();
            byte[] jpeg = avatarBytes(uri);
            showBusy(true);
            ZeroChillAccountRepository.uploadAvatar(this, jpeg, (url, error) -> runOnUiThread(() -> {
                if (!sameAccount(owner)) return;
                showBusy(false);
                if (error != null) {
                    Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Avatar updated.", Toast.LENGTH_SHORT).show();
                    loadAccount();
                }
            }));
        } catch (Exception error) {
            Toast.makeText(this, "Couldn't read that image.", Toast.LENGTH_LONG).show();
        }
    }

    private byte[] avatarBytes(Uri uri) throws Exception {
        Bitmap source;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            source = BitmapFactory.decodeStream(input);
        }
        if (source == null) throw new IllegalArgumentException("Invalid image.");
        int side = Math.min(source.getWidth(), source.getHeight());
        int left = Math.max(0, (source.getWidth() - side) / 2);
        int top = Math.max(0, (source.getHeight() - side) / 2);
        Bitmap square = Bitmap.createBitmap(source, left, top, side, side);
        Bitmap scaled = Bitmap.createScaledBitmap(square, 512, 512, true);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 84, output);
        if (square != source) square.recycle();
        if (scaled != square) scaled.recycle();
        source.recycle();
        return output.toByteArray();
    }

    private EditText field(String hint, int inputType) {
        EditText view = new EditText(this);
        view.setHint(hint);
        view.setHintTextColor(ZeroChillUi.color(this, R.color.zc_text_muted));
        view.setTextColor(Color.rgb(242, 244, 247));
        view.setTextSize(14);
        view.setSingleLine(true);
        view.setInputType(inputType);
        view.setPadding(dp(15), 0, dp(15), 0);
        view.setBackground(fieldBackground());
        view.setSelectAllOnFocus(false);
        return view;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50));
        params.setMargins(0, dp(10), 0, 0);
        return params;
    }

    private MaterialCheckBox check(String label) {
        MaterialCheckBox view = new MaterialCheckBox(this);
        view.setText(label);
        view.setTextColor(ZeroChillUi.color(this, R.color.zc_text_secondary));
        view.setTextSize(12.5f);
        view.setButtonTintList(new android.content.res.ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{UiPalette.PRIMARY, Color.rgb(98, 104, 112)}
        ));
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
        TextView view = text(label, 12, UiPalette.ON_PRIMARY, true);
        view.setGravity(Gravity.CENTER);
        view.setLetterSpacing(0.06f);
        view.setBackground(primaryButtonBackground());
        ZeroChillMotion.installPressFeedback(view);
        return view;
    }

    private TextView secondaryButton(String label) {
        TextView view = text(label, 11, UiPalette.PRIMARY, true);
        view.setGravity(Gravity.CENTER);
        view.setLetterSpacing(0.05f);
        view.setBackground(secondaryButtonBackground());
        ZeroChillMotion.installPressFeedback(view);
        return view;
    }

    private void addEyebrow(LinearLayout parent, String value) {
        TextView eyebrow = text(value, 10, UiPalette.PRIMARY, true);
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
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(238, 18, 21, 26), Color.argb(238, 9, 11, 15)}
        );
        background.setCornerRadius(dp(15));
        background.setStroke(dp(1), Color.rgb(43, 56, 66));
        return background;
    }

    private GradientDrawable panelBackground(int radiusDp) {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(235, 18, 21, 26), Color.argb(235, 8, 10, 14)}
        );
        background.setCornerRadius(dp(radiusDp));
        background.setStroke(dp(1), Color.rgb(43, 57, 66));
        return background;
    }

    private GradientDrawable selectedTabBackground() {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(210, 6, 45, 66), Color.argb(180, 5, 31, 47)}
        );
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), Color.rgb(24, 163, 222));
        return background;
    }

    private GradientDrawable quietTabBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(125, 10, 11, 14));
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), Color.rgb(42, 46, 53));
        return background;
    }

    private GradientDrawable primaryButtonBackground() {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(8, 146, 208), Color.rgb(35, 174, 229)}
        );
        background.setCornerRadius(dp(17));
        background.setStroke(dp(1), Color.rgb(80, 198, 244));
        return background;
    }

    private GradientDrawable secondaryButtonBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(180, 11, 13, 17));
        background.setCornerRadius(dp(15));
        background.setStroke(dp(1), Color.rgb(43, 52, 60));
        return background;
    }

    private GradientDrawable circle(int fill) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(fill);
        return background;
    }

    private View addAmbientGlow(FrameLayout parent, int size) {
        View glow = new View(this);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        background.setGradientRadius(size * 0.31f);
        background.setColors(new int[]{
                Color.argb(52, 8, 146, 208),
                Color.argb(18, 8, 146, 208),
                Color.argb(4, 8, 146, 208),
                Color.TRANSPARENT
        });
        glow.setBackground(background);
        glow.setAlpha(0.62f);
        parent.addView(glow, new FrameLayout.LayoutParams(size, size));
        return glow;
    }

    private void startAmbientMotion() {
        if (accountRoot == null || ambientGlow == null || !ZeroChillMotion.animationsEnabled(this)) return;
        int width = accountRoot.getWidth();
        int height = accountRoot.getHeight();
        if (width <= 0 || height <= 0) return;

        stopAmbientMotion();

        float startX = -ambientGlow.getWidth() * 0.42f;
        float endX = Math.max(startX, width - ambientGlow.getWidth() * 0.58f);
        float startY = Math.max(0f, height * 0.16f);
        float endY = Math.max(startY, height * 0.70f - ambientGlow.getHeight() * 0.5f);

        ambientGlow.setTranslationX(startX);
        ambientGlow.setTranslationY(startY);

        ObjectAnimator driftX = ObjectAnimator.ofFloat(
                ambientGlow,
                View.TRANSLATION_X,
                startX,
                endX
        );
        driftX.setDuration(15_000L);
        driftX.setRepeatCount(ValueAnimator.INFINITE);
        driftX.setRepeatMode(ValueAnimator.REVERSE);
        driftX.setInterpolator(new LinearInterpolator());

        ObjectAnimator driftY = ObjectAnimator.ofFloat(
                ambientGlow,
                View.TRANSLATION_Y,
                startY,
                endY
        );
        driftY.setDuration(19_000L);
        driftY.setRepeatCount(ValueAnimator.INFINITE);
        driftY.setRepeatMode(ValueAnimator.REVERSE);
        driftY.setInterpolator(new LinearInterpolator());

        ObjectAnimator breathe = ObjectAnimator.ofFloat(
                ambientGlow,
                View.ALPHA,
                0.42f,
                0.72f
        );
        breathe.setDuration(8_000L);
        breathe.setRepeatCount(ValueAnimator.INFINITE);
        breathe.setRepeatMode(ValueAnimator.REVERSE);
        breathe.setInterpolator(new LinearInterpolator());

        ambientAnimator = new AnimatorSet();
        ambientAnimator.playTogether(driftX, driftY, breathe);
        ambientAnimator.start();
    }

    private void stopAmbientMotion() {
        if (ambientAnimator == null) return;
        ambientAnimator.cancel();
        ambientAnimator = null;
    }

    private GradientDrawable headerBackground() {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.argb(246, 0, 0, 0),
                        Color.argb(238, 12, 15, 20)
                }
        );
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

