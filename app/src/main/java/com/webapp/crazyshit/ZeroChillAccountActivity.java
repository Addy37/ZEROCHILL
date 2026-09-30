package com.webapp.crazyshit;

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
import android.view.Gravity;
import android.view.View;
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

    private LinearLayout content;
    private ProgressBar progress;
    private boolean createMode;
    private ZeroChillAccountRepository.AccountState account;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ZeroChillUi.applySystemBars(this);
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
        addAmbientGlow(root, Gravity.TOP | Gravity.END, dp(220), dp(220), dp(64), dp(-70));
        addAmbientGlow(root, Gravity.BOTTOM | Gravity.START, dp(180), dp(180), dp(-58), dp(90));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.TRANSPARENT);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(10), dp(3), dp(14), dp(3));
        ZeroChillUi.styleTopBar(top);
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
            account = value;
            if (value != null && value.signedIn) showProfile(value);
            else showAuth();
        }));
    }

    private void showAuth() {
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
                ? View.AUTOFILL_HINT_NEW_PASSWORD
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
                state.username.isEmpty() ? "ZEROCHILL USER" : "@" + state.username,
                21,
                Color.WHITE,
                true
        );
        identity.addView(username);

        if (!state.displayName.isEmpty()) {
            TextView display = text(
                    state.displayName,
                    13,
                    UiPalette.PRIMARY,
                    true
            );
            LinearLayout.LayoutParams displayParams = new LinearLayout.LayoutParams(-1, -2);
            displayParams.topMargin = dp(3);
            identity.addView(display, displayParams);
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
            ZeroChillAccountRepository.signOut(this, (ignored, error) -> runOnUiThread(() -> {
                account = null;
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

        EditText displayName = field("Display name (optional)", InputType.TYPE_CLASS_TEXT);
        displayName.setText(state.displayName);
        displayName.setAutofillHints(View.AUTOFILL_HINT_NAME);
        content.addView(displayName, fieldParams());

        TextView save = primaryButton("SAVE PROFILE");
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(48));
        saveParams.topMargin = dp(10);
        content.addView(save, saveParams);
        save.setOnClickListener(v -> {
            save.setEnabled(false);
            ZeroChillAccountRepository.updateDisplayName(
                    this,
                    displayName.getText().toString(),
                    (updated, error) -> runOnUiThread(() -> {
                        save.setEnabled(true);
                        if (error != null) {
                            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                        } else {
                            account = updated;
                            Toast.makeText(this, "Profile updated.", Toast.LENGTH_SHORT).show();
                            showProfile(updated);
                        }
                    })
            );
        });

        LinearLayout syncCard = new LinearLayout(this);
        syncCard.setOrientation(LinearLayout.VERTICAL);
        syncCard.setPadding(dp(14), dp(13), dp(14), dp(13));
        syncCard.setBackground(panelBackground(17));

        TextView syncTitle = text("SYNCED TO ZEROCHILL", 10, UiPalette.PRIMARY, true);
        syncTitle.setLetterSpacing(0.08f);
        syncCard.addView(syncTitle);

        TextView synced = text(
                "Favorite creators stay linked to this identity. Comments, replies, likes, and DMs use the same profile.",
                12,
                ZeroChillUi.color(this, R.color.zc_text_secondary),
                false
        );
        synced.setLineSpacing(0f, 1.1f);
        LinearLayout.LayoutParams syncedParams = new LinearLayout.LayoutParams(-1, -2);
        syncedParams.topMargin = dp(5);
        syncCard.addView(synced, syncedParams);

        LinearLayout.LayoutParams syncParams = new LinearLayout.LayoutParams(-1, -2);
        syncParams.topMargin = dp(12);
        content.addView(syncCard, syncParams);
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
        if (requestCode != AVATAR_REQUEST || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            byte[] jpeg = avatarBytes(uri);
            showBusy(true);
            ZeroChillAccountRepository.uploadAvatar(this, jpeg, (url, error) -> runOnUiThread(() -> {
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
                Color.argb(46, 8, 146, 208),
                Color.argb(12, 8, 146, 208),
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

    private void showMessage(String message) {
        content.removeAllViews();
        content.addView(text(message, 14, ZeroChillUi.color(this, R.color.zc_text_secondary), false));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
