package com.webapp.crazyshit;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class FeedbackThreadActivity extends Activity {
    static final String EXTRA_FEEDBACK_ID = "feedback_id";

    private static final int MUTED = Color.rgb(172, 172, 181);
    private static final int SURFACE = Color.rgb(24, 24, 28);
    private static final int USER_BUBBLE = Color.rgb(8, 55, 72);

    private String feedbackId;
    private TextView heading;
    private TextView status;
    private LinearLayout messages;
    private ScrollView messageScroll;
    private SwipeRefreshLayout refresh;
    private EditText composer;
    private MaterialButton send;

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

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(12), dp(14), dp(8));

        MaterialButton back = button("‹");
        back.setTextSize(28);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(10), 0, 0, 0);
        heading = text("Feedback conversation", 22, Color.WHITE, true);
        status = text("Loading…", 12, MUTED, true);
        titles.addView(heading);
        titles.addView(status);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(header);

        refresh = new SwipeRefreshLayout(this);
        refresh.setColorSchemeColors(UiPalette.PRIMARY);
        refresh.setOnRefreshListener(this::loadThread);

        messageScroll = new ScrollView(this);
        messageScroll.setFillViewport(true);
        messageScroll.setClipToPadding(false);

        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(dp(14), dp(12), dp(14), dp(18));
        messageScroll.addView(messages, new ScrollView.LayoutParams(-1, -2));
        refresh.addView(messageScroll);
        root.addView(refresh, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout composeRow = new LinearLayout(this);
        composeRow.setGravity(Gravity.BOTTOM);
        composeRow.setPadding(dp(12), dp(8), dp(12), dp(12));

        composer = new EditText(this);
        composer.setHint("Reply to ZEROCHILL…");
        composer.setHintTextColor(Color.rgb(115, 115, 124));
        composer.setTextColor(Color.WHITE);
        composer.setTextSize(15);
        composer.setMinLines(1);
        composer.setMaxLines(5);
        composer.setPadding(dp(14), dp(11), dp(14), dp(11));
        composer.setBackground(rounded(SURFACE, 16));
        composer.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});
        composeRow.addView(composer, new LinearLayout.LayoutParams(0, -2, 1f));

        send = button("Send");
        send.setTextColor(UiPalette.ON_PRIMARY);
        send.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(UiPalette.PRIMARY)
        );
        send.setOnClickListener(v -> sendReply());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(84), dp(52));
        sendParams.setMarginStart(dp(8));
        composeRow.addView(send, sendParams);

        root.addView(composeRow);
        setContentView(root);
    }

    private void loadThread() {
        refresh.setRefreshing(true);
        FeedbackRepository.thread(this, feedbackId, (thread, error) -> runOnUiThread(() -> {
            refresh.setRefreshing(false);
            if (error != null || thread == null) {
                Toast.makeText(
                        this,
                        error == null ? "Couldn't load conversation." : error.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
                return;
            }
            renderThread(thread);
        }));
    }

    private void renderThread(FeedbackRepository.FeedbackThread thread) {
        heading.setText(typeLabel(thread.item.type));
        status.setText(statusLabel(thread.item.status));
        status.setTextColor(statusColor(thread.item.status));
        messages.removeAllViews();

        for (FeedbackRepository.FeedbackMessage message : thread.messages) {
            addMessage(message);
        }

        if (thread.messages.isEmpty()) {
            TextView empty = text("No messages yet.", 14, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(40), 0, dp(40));
            messages.addView(empty);
        }

        messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void addMessage(FeedbackRepository.FeedbackMessage message) {
        boolean developer = message.fromDeveloper();

        LinearLayout row = new LinearLayout(this);
        row.setGravity(developer ? Gravity.START : Gravity.END);

        MaterialCardView bubble = new MaterialCardView(this);
        bubble.setCardBackgroundColor(developer ? SURFACE : USER_BUBBLE);
        bubble.setStrokeWidth(dp(1));
        bubble.setStrokeColor(
                developer ? Color.rgb(45, 45, 51) : Color.rgb(10, 106, 137)
        );
        bubble.setRadius(dp(16));
        bubble.setCardElevation(0);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(11), dp(14), dp(10));

        TextView sender = text(
                developer ? "ZEROCHILL" : "YOU",
                11,
                developer ? UiPalette.PRIMARY : Color.WHITE,
                true
        );
        body.addView(sender);

        TextView copy = text(message.message, 15, Color.WHITE, false);
        copy.setMaxWidth(dp(300));
        copy.setPadding(0, dp(4), 0, dp(5));
        body.addView(copy);

        TextView time = text(formatDate(message.createdAt), 11, MUTED, false);
        body.addView(time);

        if (!developer && message.isRead()) {
            TextView seen = text(
                    "Seen by ZEROCHILL · " + formatDate(message.readAt),
                    11,
                    UiPalette.PRIMARY,
                    false
            );
            seen.setPadding(0, dp(3), 0, 0);
            body.addView(seen);
        }

        bubble.addView(body);
        row.addView(bubble, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.setMargins(0, dp(5), 0, dp(5));
        messages.addView(row, rowParams);
    }

    private void sendReply() {
        String value = composer.getText().toString().trim();
        if (value.isEmpty()) {
            composer.setError("Type a reply.");
            return;
        }

        send.setEnabled(false);
        send.setText("Sending…");
        FeedbackRepository.reply(this, feedbackId, value, (message, error) ->
                runOnUiThread(() -> {
                    send.setEnabled(true);
                    send.setText("Send");
                    if (error != null) {
                        Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }
                    composer.setText("");
                    loadThread();
                }));
    }

    private MaterialButton button(String value) {
        MaterialButton button = new MaterialButton(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(SURFACE)
        );
        button.setCornerRadius(dp(14));
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private android.graphics.drawable.GradientDrawable rounded(int color, int radius) {
        android.graphics.drawable.GradientDrawable background =
                new android.graphics.drawable.GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radius));
        return background;
    }

    private static String typeLabel(String type) {
        if ("feature_request".equals(type)) return "Feature request";
        if ("bug_report".equals(type)) return "Bug report";
        return "Feedback conversation";
    }

    private static String statusLabel(String value) {
        if (value == null || value.isEmpty()) return "SUBMITTED";
        return value.replace('_', ' ').toUpperCase(Locale.US);
    }

    private int statusColor(String value) {
        if ("completed".equals(value)) return Color.rgb(80, 210, 130);
        if ("planned".equals(value) || "in_progress".equals(value)) return UiPalette.PRIMARY;
        return MUTED;
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
