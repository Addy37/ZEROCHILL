package com.addy37.crazyshitadmin;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FeedbackThreadActivity extends AppCompatActivity {
    static final String EXTRA_FEEDBACK_ID = "feedback_id";

    private static final String[] STATUSES = {
            "submitted", "reviewing", "planned", "in_progress", "completed", "declined"
    };

    private final ExecutorService network = Executors.newSingleThreadExecutor();

    private String feedbackId;
    private TextView title;
    private TextView meta;
    private Spinner status;
    private SwipeRefreshLayout refresh;
    private ScrollView messageScroll;
    private LinearLayout messages;
    private EditText composer;
    private MaterialButton send;
    private MaterialButton saveStatus;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        feedbackId = getIntent().getStringExtra(EXTRA_FEEDBACK_ID);
        if (feedbackId == null || feedbackId.trim().isEmpty()) {
            finish();
            return;
        }
        buildUi();
        loadThread();
    }

    @Override
    protected void onDestroy() {
        network.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(R.color.app_background));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(8), dp(12), dp(4));

        MaterialButton back = compactButton("‹");
        back.setTextSize(24);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(38)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(9), 0, 0, 0);
        title = text("Feedback conversation", 20, Color.WHITE, true);
        meta = text("Loading…", 11, color(R.color.app_on_surface_variant), false);
        meta.setMaxLines(2);
        titles.addView(title);
        titles.addView(meta);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(header);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(dp(12), 0, dp(12), dp(5));

        status = new Spinner(this);
        status.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                STATUSES
        ));
        statusRow.addView(status, new LinearLayout.LayoutParams(0, -2, 1f));

        saveStatus = compactButton("Save");
        saveStatus.setOnClickListener(v -> saveStatus());
        statusRow.addView(saveStatus);
        root.addView(statusRow);

        refresh = new SwipeRefreshLayout(this);
        refresh.setColorSchemeColors(color(R.color.app_primary));
        refresh.setOnRefreshListener(this::loadThread);

        messageScroll = new ScrollView(this);
        messageScroll.setFillViewport(true);
        messageScroll.setClipToPadding(false);

        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(dp(12), dp(4), dp(12), dp(10));
        messageScroll.addView(messages, new ScrollView.LayoutParams(-1, -2));
        refresh.addView(messageScroll);
        root.addView(refresh, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout composeRow = new LinearLayout(this);
        composeRow.setGravity(Gravity.BOTTOM);
        composeRow.setPadding(dp(10), dp(6), dp(10), dp(9));

        composer = new EditText(this);
        composer.setHint("Reply to user…");
        composer.setHintTextColor(color(R.color.app_on_surface_variant));
        composer.setTextColor(Color.WHITE);
        composer.setTextSize(14);
        composer.setMinLines(1);
        composer.setMaxLines(4);
        composer.setPadding(dp(12), dp(9), dp(12), dp(9));
        composer.setBackground(rounded(color(R.color.app_surface_raised), 12));
        composer.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});
        composeRow.addView(composer, new LinearLayout.LayoutParams(0, -2, 1f));

        send = primaryButton("Send");
        send.setOnClickListener(v -> sendReply());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(76), dp(44));
        sendParams.setMarginStart(dp(7));
        composeRow.addView(send, sendParams);

        root.addView(composeRow);
        setContentView(root);
    }

    private void loadThread() {
        refresh.setRefreshing(true);
        String token = SecureTokenStore.read(this);
        network.execute(() -> {
            try {
                AdminRepository.FeedbackThread thread = AdminRepository.thread(token, feedbackId);
                runOnUiThread(() -> {
                    refresh.setRefreshing(false);
                    renderThread(thread);
                });
            } catch (SecurityException error) {
                runOnUiThread(() -> {
                    refresh.setRefreshing(false);
                    SecureTokenStore.clear(this);
                    toast("Admin token expired.");
                    finish();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    refresh.setRefreshing(false);
                    toast(message(error));
                });
            }
        });
    }

    private void renderThread(AdminRepository.FeedbackThread thread) {
        AdminRepository.Item item = thread.item;
        title.setText(displayType(item.type));
        meta.setText(
                "Version " + item.appVersion + " · Android " + item.androidVersion +
                        "\n" + item.device + " · " + item.section
        );
        selectStatus(item.status);

        messages.removeAllViews();
        for (AdminRepository.FeedbackMessage message : thread.messages) {
            addMessage(message);
        }

        if (thread.messages.isEmpty()) {
            TextView empty = text("No messages yet.", 14,
                    color(R.color.app_on_surface_variant), false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(40), 0, dp(40));
            messages.addView(empty);
        }

        messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void addMessage(AdminRepository.FeedbackMessage message) {
        boolean developer = message.fromDeveloper();

        LinearLayout row = new LinearLayout(this);
        row.setGravity(developer ? Gravity.END : Gravity.START);

        MaterialCardView bubble = new MaterialCardView(this);
        bubble.setCardBackgroundColor(developer
                ? color(R.color.app_surface_raised) : color(R.color.app_surface));
        bubble.setStrokeColor(developer
                ? color(R.color.app_primary) : color(R.color.app_divider));
        bubble.setStrokeWidth(dp(1));
        bubble.setRadius(dp(13));
        bubble.setCardElevation(0);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(11), dp(8), dp(11), dp(8));

        TextView sender = text(
                developer ? "ZEROCHILL" : "USER",
                10,
                developer ? color(R.color.app_primary) : color(R.color.app_on_surface_variant),
                true
        );
        body.addView(sender);

        TextView copy = text(message.message, 14, Color.WHITE, false);
        copy.setMaxWidth(dp(300));
        copy.setPadding(0, dp(2), 0, dp(3));
        body.addView(copy);

        TextView time = text(
                formatDate(message.createdAt),
                10,
                color(R.color.app_on_surface_variant),
                false
        );
        body.addView(time);

        if (developer && message.isRead()) {
            TextView seen = text(
                    "Seen by user · " + formatDate(message.readAt),
                    10,
                    color(R.color.app_primary),
                    false
            );
            seen.setPadding(0, dp(2), 0, 0);
            body.addView(seen);
        }

        bubble.addView(body);
        row.addView(bubble, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.setMargins(0, dp(3), 0, dp(3));
        messages.addView(row, rowParams);
    }

    private void sendReply() {
        String value = composer.getText().toString().trim();
        if (value.isEmpty()) {
            composer.setError("Type a reply.");
            return;
        }

        setSending(true);
        String selectedStatus = String.valueOf(status.getSelectedItem());
        String token = SecureTokenStore.read(this);
        network.execute(() -> {
            try {
                AdminRepository.reply(token, feedbackId, selectedStatus, value);
                runOnUiThread(() -> {
                    composer.setText("");
                    setSending(false);
                    loadThread();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    setSending(false);
                    toast(message(error));
                });
            }
        });
    }

    private void saveStatus() {
        saveStatus.setEnabled(false);
        String selectedStatus = String.valueOf(status.getSelectedItem());
        String token = SecureTokenStore.read(this);
        network.execute(() -> {
            try {
                AdminRepository.updateStatus(token, feedbackId, selectedStatus);
                runOnUiThread(() -> {
                    saveStatus.setEnabled(true);
                    toast("Status updated");
                    loadThread();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    saveStatus.setEnabled(true);
                    toast(message(error));
                });
            }
        });
    }

    private void setSending(boolean sending) {
        send.setEnabled(!sending);
        send.setText(sending ? "Sending…" : "Send");
        saveStatus.setEnabled(!sending);
    }

    private void selectStatus(String value) {
        for (int i = 0; i < STATUSES.length; i++) {
            if (STATUSES[i].equals(value)) {
                status.setSelection(i);
                return;
            }
        }
        status.setSelection(0);
    }

    private MaterialButton button(String value) {
        MaterialButton button = new MaterialButton(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(color(R.color.app_on_surface));
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(color(R.color.app_surface_raised))
        );
        button.setStrokeColor(
                android.content.res.ColorStateList.valueOf(color(R.color.app_divider))
        );
        button.setStrokeWidth(dp(1));
        button.setCornerRadius(dp(10));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        return button;
    }

    private MaterialButton primaryButton(String value) {
        MaterialButton button = button(value);
        button.setTextColor(color(R.color.app_on_primary));
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(color(R.color.app_primary))
        );
        button.setStrokeWidth(0);
        return button;
    }

    private MaterialButton compactButton(String value) {
        MaterialButton button = button(value);
        button.setMinHeight(dp(36));
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value == null ? "" : value);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) text.setTypeface(null, Typeface.BOLD);
        return text;
    }

    private android.graphics.drawable.GradientDrawable rounded(int color, int radius) {
        android.graphics.drawable.GradientDrawable background =
                new android.graphics.drawable.GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radius));
        return background;
    }

    private static String displayType(String type) {
        if ("bug_report".equals(type)) return "Bug report";
        if ("feature_request".equals(type)) return "Feature request";
        return "Feedback conversation";
    }

    private static String formatDate(String raw) {
        if (raw == null || raw.isEmpty() || "null".equals(raw)) return "";
        try {
            return DateTimeFormatter.ofPattern("MMM d · h:mm a", Locale.getDefault())
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.parse(raw));
        } catch (Exception ignored) {
            return raw;
        }
    }

    private static String message(Exception error) {
        return error.getMessage() == null ? "Request failed" : error.getMessage();
    }

    private int color(int id) {
        return getColor(id);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String value) {
        Toast.makeText(this, value == null ? "Something went wrong" : value, Toast.LENGTH_LONG)
                .show();
    }
}
