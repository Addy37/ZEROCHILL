package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Authenticated account changes. Passwords stay only in live input fields. */
public final class ZeroChillAccountSecurityActivity extends Activity {
    static final String EXTRA_MODE = "mode";
    private String mode;
    private LinearLayout content;
    private TextView action;
    private String ownerId;
    private boolean busy;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ZeroChillUi.applySystemBars(this);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (!"email".equals(mode) && !"password".equals(mode) && !"delete".equals(mode)) {
            finish();
            return;
        }
        ownerId = ZeroChillSessionStore.currentUserId(this);
        if (ownerId.isEmpty()) { finish(); return; }
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.BLACK);
        TextView header = label("‹     " + ("email".equals(mode) ? "Change email" :
                "password".equals(mode) ? "Change password" : "Delete account"), 19, Color.WHITE);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), 0, dp(18), 0);
        header.setOnClickListener(v -> finish());
        ZeroChillMotion.installPressFeedback(header);
        shell.addView(header, new LinearLayout.LayoutParams(-1, dp(56)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(24), dp(22), dp(32));
        scroll.addView(content);
        setContentView(shell);
        if ("email".equals(mode)) emailForm();
        else if ("password".equals(mode)) passwordForm();
        else deleteForm();
    }

    private boolean active() {
        return !isFinishing() && !isDestroyed() && ownerId.equals(ZeroChillSessionStore.currentUserId(this));
    }

    private void emailForm() {
        heading("CHANGE EMAIL");
        copy("Enter your new email address. Your current email remains on your account until you complete any required confirmation.");
        EditText address = field("New email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        address.setAutofillHints(View.AUTOFILL_HINT_EMAIL_ADDRESS);
        button("UPDATE EMAIL", () -> {
            String value = address.getText().toString().trim();
            String error = ZeroChillAccountValidation.email(value);
            if (!error.isEmpty()) { address.setError(error); return; }
            setBusy(true);
            ZeroChillAccountRepository.changeEmail(this, value, (state, failure) -> runOnUiThread(() -> {
                if (!active()) { expired(); return; }
                setBusy(false);
                if (failure != null) { showError(failure); return; }
                boolean accepted = state != null && value.equalsIgnoreCase(state.email);
                new ZeroChillDialog.Builder(this)
                        .setTitle(accepted ? "Email updated" : "Check your email")
                        .setMessage(accepted
                                ? "Your account now uses " + value + "."
                                : "Your email change needs confirmation. Check your current and new inboxes for confirmation links. Your account keeps showing your verified email until the change completes.")
                        .setPositiveButton("OK", (dialog, which) -> { setResult(RESULT_OK); finish(); }).show();
            }));
        });
    }

    private void passwordForm() {
        heading("CHANGE PASSWORD");
        copy("Choose a new password for your ZEROCHILL identity.");
        EditText password = field("New password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText confirm = field("Confirm new password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setAutofillHints("newPassword");
        confirm.setAutofillHints("newPassword");
        button("UPDATE PASSWORD", () -> {
            String value = password.getText().toString();
            String error = ZeroChillAccountValidation.password(value);
            if (!error.isEmpty()) { password.setError(error); return; }
            if (!matchingPasswords(value, confirm.getText().toString())) {
                confirm.setError("Passwords don't match."); return;
            }
            setBusy(true);
            ZeroChillAccountRepository.changePassword(this, value, confirm.getText().toString(),
                    (updated, failure) -> runOnUiThread(() -> {
                        if (!active()) { expired(); return; }
                        setBusy(false);
                        if (failure != null) { showError(failure); return; }
                        password.setText("");
                        confirm.setText("");
                        ZeroChillToast.showAfterNavigation(this, "Password updated.", ZeroChillToast.LENGTH_SHORT);
                        finish();
                    }));
        });
    }

    private void deleteForm() {
        heading("DELETE ACCOUNT");
        copy("Deleting your account removes your profile, avatar, favorite creators, comments and replies, likes, messages, blocks, reports, and notification activity. Replies left by other people under your comments and messages on both sides of your conversations are removed too. Your local downloads, playback history, and app settings stay on this device.");
        button("CONTINUE", () -> new ZeroChillDialog.Builder(this)
                .setTitle("Delete your ZEROCHILL identity?")
                .setMessage("This cannot be undone. Your social content and conversations will be removed.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("CONTINUE", (dialog, which) -> finalConfirmation())
                .show());
    }

    private void finalConfirmation() {
        EditText phrase = new ZeroChillEditText(this);
        phrase.setSingleLine(true);
        phrase.setHint("Type DELETE");
        phrase.setTextColor(Color.WHITE);
        phrase.setHintTextColor(Color.GRAY);
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(dp(20), 0, dp(20), 0);
        wrapper.addView(phrase, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new ZeroChillDialog.Builder(this).destructive()
                .setTitle("Final confirmation")
                .setMessage("Type DELETE to permanently remove your account.")
                .setView(wrapper)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("DELETE ACCOUNT", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!validDeleteConfirmation(phrase.getText().toString())) {
                phrase.setError("Type DELETE to confirm."); return;
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            setBusy(true);
            ZeroChillAccountRepository.deleteAccount(this, (deleted, failure) -> runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                if (failure != null || !Boolean.TRUE.equals(deleted)) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    showError(failure == null ? new IllegalStateException("Account deletion failed.") : failure);
                    return;
                }
                dialog.dismiss();
                setResult(RESULT_OK, new Intent().putExtra("account_deleted", true));
                finish();
            }));
        }));
        dialog.show();
    }

    static boolean matchingPasswords(String first, String second) {
        return first != null && first.equals(second);
    }

    static boolean validDeleteConfirmation(String phrase) {
        return phrase != null && "DELETE".equals(phrase.trim());
    }

    private void heading(String value) {
        TextView heading = label(value, 12, UiPalette.PRIMARY);
        heading.setLetterSpacing(0.1f);
        content.addView(heading);
    }

    private void copy(String value) {
        TextView copy = label(value, 14, Color.rgb(177, 188, 198));
        copy.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12);
        params.bottomMargin = dp(14);
        content.addView(copy, params);
    }

    private EditText field(String hint, int type) {
        EditText field = new ZeroChillEditText(this);
        field.setHint(hint);
        field.setInputType(type);
        field.setSingleLine(true);
        field.setTextColor(Color.WHITE);
        field.setHintTextColor(Color.rgb(126, 137, 147));
        field.setTextSize(15);
        field.setPadding(dp(16), 0, dp(16), 0);
        field.setBackgroundResource(R.drawable.zc_input_field);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(54));
        params.bottomMargin = dp(10);
        content.addView(field, params);
        return field;
    }

    private void button(String label, Runnable click) {
        action = label(label, 12, Color.WHITE);
        action.setGravity(Gravity.CENTER);
        action.setBackgroundColor("delete".equals(mode) ? Color.rgb(135, 42, 50) : UiPalette.PRIMARY);
        action.setMinHeight(dp(52));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12);
        content.addView(action, params);
        action.setOnClickListener(v -> { if (!busy && active()) click.run(); });
        ZeroChillMotion.installPressFeedback(action);
    }

    private void setBusy(boolean value) {
        busy = value;
        if (action != null) { action.setEnabled(!value); action.setText(value ? "PLEASE WAIT…" :
                "delete".equals(mode) ? "CONTINUE" : "email".equals(mode) ? "UPDATE EMAIL" : "UPDATE PASSWORD"); }
    }

    private void expired() {
        if (isFinishing() || isDestroyed()) return;
        setBusy(false);
        ZeroChillToast.showAfterNavigation(this, "Sign in again to manage your account.", ZeroChillToast.LENGTH_LONG);
        setResult(RESULT_OK);
        finish();
    }

    private void showError(Exception error) {
        ZeroChillToast.makeText(this, error.getMessage() == null ? "Please try again." : error.getMessage(), ZeroChillToast.LENGTH_LONG).show();
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
