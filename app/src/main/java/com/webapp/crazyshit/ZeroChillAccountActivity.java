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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

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
        loadAccount();
    }

    private void buildShell() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(ZeroChillUi.background(this));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(6), dp(12), dp(6));
        ZeroChillUi.styleTopBar(top);
        shell.addView(top, new LinearLayout.LayoutParams(-1, dp(64)));

        TextView back = text("‹", 32, Color.WHITE, false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(6), 0, 0, 0);
        top.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView title = text("Account", 20, Color.WHITE, true);
        titles.addView(title);
        TextView subtitle = text("Your ZeroChill profile", 11,
                ZeroChillUi.color(this, R.color.zc_text_secondary), false);
        titles.addView(subtitle);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(36));
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

        TextView brand = text("ZEROCHILL", 28, Color.WHITE, true);
        content.addView(brand);
        TextView copy = text(
                "Create a profile to sync favorite creators and join comments, likes, replies, and messages.",
                14,
                ZeroChillUi.color(this, R.color.zc_text_secondary),
                false
        );
        copy.setPadding(0, dp(8), 0, dp(20));
        copy.setLineSpacing(0f, 1.1f);
        content.addView(copy);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(tabs, new LinearLayout.LayoutParams(-1, dp(44)));

        TextView signInTab = tab("SIGN IN", !createMode);
        TextView createTab = tab("CREATE ACCOUNT", createMode);
        tabs.addView(signInTab, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(0, -1, 1f);
        createParams.setMarginStart(dp(8));
        tabs.addView(createTab, createParams);
        signInTab.setOnClickListener(v -> {
            createMode = false;
            showAuth();
        });
        createTab.setOnClickListener(v -> {
            createMode = true;
            showAuth();
        });

        EditText email = field("Email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        content.addView(email, fieldParams());

        EditText username = null;
        if (createMode) {
            username = field("Username", InputType.TYPE_CLASS_TEXT);
            content.addView(username, fieldParams());
        }

        EditText password = field("Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        content.addView(password, fieldParams());

        CheckBox adult = null;
        CheckBox terms = null;
        if (createMode) {
            adult = check("I confirm that I am 18 or older.");
            terms = check("I agree to the Terms and Community Rules.");
            content.addView(adult);
            content.addView(terms);
        }

        final EditText usernameField = username;
        final CheckBox adultCheck = adult;
        final CheckBox termsCheck = terms;
        TextView submit = primaryButton(createMode ? "CREATE ACCOUNT" : "SIGN IN");
        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(-1, dp(50));
        submitParams.setMargins(0, dp(18), 0, 0);
        content.addView(submit, submitParams);

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

        TextView privacy = text(
                "Your email stays private. ZeroChill does not require a real name, phone number, or birth date.",
                12,
                ZeroChillUi.color(this, R.color.zc_text_muted),
                false
        );
        privacy.setPadding(0, dp(16), 0, 0);
        content.addView(privacy);
    }

    private void showVerification(String email) {
        content.removeAllViews();
        TextView title = text("CHECK YOUR EMAIL", 23, Color.WHITE, true);
        content.addView(title);
        TextView copy = text(
                "We sent a verification message to " + email
                        + ". Verify the address, then return here and sign in.",
                14,
                ZeroChillUi.color(this, R.color.zc_text_secondary),
                false
        );
        copy.setPadding(0, dp(10), 0, dp(22));
        content.addView(copy);
        TextView signIn = primaryButton("GO TO SIGN IN");
        signIn.setOnClickListener(v -> {
            createMode = false;
            showAuth();
        });
        content.addView(signIn, new LinearLayout.LayoutParams(-1, dp(50)));
    }

    private void showProfile(ZeroChillAccountRepository.AccountState state) {
        content.removeAllViews();

        ImageView avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(circle(Color.rgb(28, 28, 33)));
        String avatarUrl = ZeroChillAccountRepository.avatarUrl(state.avatarPath);
        if (!avatarUrl.isEmpty()) {
            Glide.with(avatar).load(avatarUrl).circleCrop().into(avatar);
        } else {
            avatar.setImageResource(R.drawable.ic_more_account);
            avatar.setPadding(dp(24), dp(24), dp(24), dp(24));
            avatar.setColorFilter(UiPalette.PRIMARY);
        }
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(104), dp(104));
        avatarParams.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(avatar, avatarParams);

        TextView changeAvatar = secondaryButton("CHANGE AVATAR");
        LinearLayout.LayoutParams avatarButtonParams = new LinearLayout.LayoutParams(-1, dp(46));
        avatarButtonParams.setMargins(0, dp(14), 0, dp(22));
        content.addView(changeAvatar, avatarButtonParams);
        changeAvatar.setOnClickListener(v -> chooseAvatar());

        TextView username = text(
                state.username.isEmpty() ? "ZeroChill user" : "@" + state.username,
                24,
                Color.WHITE,
                true
        );
        username.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(username);

        TextView email = text(
                state.email,
                13,
                ZeroChillUi.color(this, R.color.zc_text_secondary),
                false
        );
        email.setGravity(Gravity.CENTER_HORIZONTAL);
        email.setPadding(0, dp(5), 0, dp(22));
        content.addView(email);

        EditText displayName = field("Display name (optional)", InputType.TYPE_CLASS_TEXT);
        displayName.setText(state.displayName);
        content.addView(displayName, fieldParams());

        TextView save = secondaryButton("SAVE PROFILE");
        content.addView(save, new LinearLayout.LayoutParams(-1, dp(46)));
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
                        }
                    })
            );
        });

        TextView synced = text(
                "Favorite creators are linked to this account. Comments, replies, likes, and DMs use this profile identity.",
                13,
                ZeroChillUi.color(this, R.color.zc_text_secondary),
                false
        );
        synced.setPadding(0, dp(22), 0, dp(22));
        synced.setLineSpacing(0f, 1.1f);
        content.addView(synced);

        TextView signOut = secondaryButton("SIGN OUT");
        signOut.setTextColor(Color.rgb(230, 130, 130));
        signOut.setOnClickListener(v -> {
            signOut.setEnabled(false);
            ZeroChillAccountRepository.signOut(this, (ignored, error) -> runOnUiThread(() -> {
                account = null;
                createMode = false;
                showAuth();
            }));
        });
        content.addView(signOut, new LinearLayout.LayoutParams(-1, dp(46)));
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
        view.setTextColor(Color.WHITE);
        view.setTextSize(15);
        view.setSingleLine(true);
        view.setInputType(inputType);
        view.setPadding(dp(14), 0, dp(14), 0);
        view.setBackground(roundRect(Color.rgb(24, 24, 29), 14, Color.rgb(48, 48, 56)));
        return view;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.setMargins(0, dp(12), 0, 0);
        return params;
    }

    private CheckBox check(String label) {
        CheckBox view = new CheckBox(this);
        view.setText(label);
        view.setTextColor(ZeroChillUi.color(this, R.color.zc_text_secondary));
        view.setTextSize(13);
        view.setButtonTintList(android.content.res.ColorStateList.valueOf(UiPalette.PRIMARY));
        view.setPadding(0, dp(9), 0, 0);
        return view;
    }

    private TextView tab(String label, boolean selected) {
        TextView view = text(label, 12, selected ? Color.WHITE : ZeroChillUi.color(this, R.color.zc_text_secondary), true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(roundRect(
                selected ? UiPalette.PRIMARY_CONTAINER : Color.rgb(20, 20, 24),
                13,
                selected ? UiPalette.PRIMARY : Color.rgb(43, 43, 49)
        ));
        return view;
    }

    private TextView primaryButton(String label) {
        TextView view = text(label, 13, Color.WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(roundRect(UiPalette.PRIMARY_DIM, 14, UiPalette.PRIMARY));
        ZeroChillMotion.installPressFeedback(view);
        return view;
    }

    private TextView secondaryButton(String label) {
        TextView view = text(label, 13, UiPalette.PRIMARY, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(roundRect(Color.rgb(22, 22, 27), 14, Color.rgb(48, 48, 56)));
        ZeroChillMotion.installPressFeedback(view);
        return view;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private GradientDrawable roundRect(int fill, int radiusDp, int stroke) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(dp(radiusDp));
        background.setStroke(dp(1), stroke);
        return background;
    }

    private GradientDrawable circle(int fill) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(fill);
        return background;
    }

    private void setFormEnabled(boolean enabled) {
        content.setEnabled(enabled);
        for (int i = 0; i < content.getChildCount(); i++) content.getChildAt(i).setEnabled(enabled);
        progress.setVisibility(enabled ? View.GONE : View.VISIBLE);
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
