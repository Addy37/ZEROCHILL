package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One-to-one ZEROCHILL direct-message thread. */
public final class ZeroChillMessageActivity extends Activity {
    static final String EXTRA_USER_ID = "zerochill_dm_user_id";
    private static final long REFRESH_MS = 2500L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!resumed) return;
            loadThread(false);
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    private String partnerId = "";
    private ZeroChillSocialRepository.PublicProfile partner;
    private boolean blockedByMe;
    private boolean resumed;
    private boolean loading;
    private String lastMessageId = "";

    private ImageView avatar;
    private TextView title;
    private TextView subtitle;
    private RecyclerView recycler;
    private MessageAdapter adapter;
    private ProgressBar progress;
    private EditText composer;
    private ImageView send;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ZeroChillUi.applySystemBars(this);
        partnerId = clean(getIntent().getStringExtra(EXTRA_USER_ID));
        if (partnerId.isEmpty() || !ZeroChillAccountRepository.hasStoredSession(this)) {
            if (!ZeroChillAccountRepository.hasStoredSession(this)) {
                startActivity(new Intent(this, ZeroChillAccountActivity.class));
            }
            finish();
            return;
        }
        buildUi();
        ResponsiveFitmentController.applySoon(this);
        loadPartner();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, REFRESH_MS);
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        ResponsiveFitmentController.release(this);
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(4), dp(8), dp(4));

        TextView back = text("‹", 31, Color.WHITE, false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        ZeroChillMotion.installPressFeedback(back);
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(48)));

        avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setImageResource(R.drawable.ic_more_account);
        avatar.setPadding(dp(8), dp(8), dp(8), dp(8));
        avatar.setColorFilter(UiPalette.PRIMARY);
        avatar.setOnClickListener(v -> openProfile());
        header.addView(avatar, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(10), 0, dp(6), 0);
        labels.setOnClickListener(v -> openProfile());
        title = text("Loading…", 16, Color.WHITE, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(title);
        subtitle = text("ZEROCHILL DM", 10, ZeroChillUi.color(this, R.color.zc_text_muted), false);
        labels.addView(subtitle);
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView more = new ImageView(this);
        more.setImageResource(R.drawable.ic_more_overflow);
        more.setColorFilter(Color.WHITE);
        more.setPadding(dp(9), dp(9), dp(9), dp(9));
        more.setContentDescription("Conversation options");
        more.setOnClickListener(this::showOptions);
        ZeroChillMotion.installPressFeedback(more);
        header.addView(more, new LinearLayout.LayoutParams(dp(44), dp(44)));

        root.addView(header, new LinearLayout.LayoutParams(-1, dp(58)));

        recycler = new RecyclerView(this);
        LinearLayoutManager manager = new LinearLayoutManager(this);
        manager.setStackFromEnd(true);
        recycler.setLayoutManager(manager);
        recycler.setItemAnimator(null);
        recycler.setClipToPadding(false);
        recycler.setPadding(dp(12), dp(10), dp(12), dp(12));
        adapter = new MessageAdapter();
        recycler.setAdapter(adapter);
        root.addView(recycler, new LinearLayout.LayoutParams(-1, 0, 1f));

        progress = new ProgressBar(this);
        ZeroChillUi.styleProgress(progress);
        progress.setVisibility(View.GONE);

        LinearLayout compose = new LinearLayout(this);
        compose.setGravity(Gravity.BOTTOM | Gravity.CENTER_VERTICAL);
        compose.setPadding(dp(10), dp(7), dp(10), dp(10));
        compose.setBackgroundColor(Color.rgb(5, 6, 8));

        composer = new EditText(this);
        composer.setHint("Message @" + partnerId);
        composer.setHintTextColor(Color.rgb(116, 122, 132));
        composer.setTextColor(Color.WHITE);
        composer.setTextSize(14);
        composer.setMinLines(1);
        composer.setMaxLines(5);
        composer.setPadding(dp(14), dp(10), dp(14), dp(10));
        composer.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});
        composer.setBackground(inputBackground());
        compose.addView(composer, new LinearLayout.LayoutParams(0, -2, 1f));

        send = new ImageView(this);
        send.setImageResource(R.drawable.ic_action_send);
        send.setColorFilter(Color.rgb(5, 19, 28));
        send.setPadding(dp(12), dp(12), dp(12), dp(12));
        send.setBackground(circle(UiPalette.PRIMARY));
        send.setContentDescription("Send message");
        send.setOnClickListener(v -> sendMessage());
        ZeroChillMotion.installPressFeedback(send);
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        sendParams.setMarginStart(dp(8));
        compose.addView(send, sendParams);

        root.addView(compose);
        setContentView(root);
    }

    private void loadPartner() {
        ZeroChillSocialRepository.loadProfile(this, partnerId, (profile, error) -> runOnUiThread(() -> {
            if (error != null || profile == null || profile.currentUser) {
                Toast.makeText(
                        this,
                        error == null ? "This conversation is unavailable." : error.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
                finish();
                return;
            }
            partner = profile;
            String label = profile.displayName.isEmpty()
                    ? "@" + profile.username
                    : profile.displayName;
            title.setText(label);
            subtitle.setText("@" + profile.username);
            composer.setHint("Message @" + profile.username);
            String url = ZeroChillAccountRepository.avatarUrl(profile.avatarPath);
            if (url.isEmpty()) {
                avatar.setImageResource(R.drawable.ic_more_account);
                avatar.setPadding(dp(8), dp(8), dp(8), dp(8));
                avatar.setColorFilter(UiPalette.PRIMARY);
            } else {
                avatar.setPadding(0, 0, 0, 0);
                avatar.clearColorFilter();
                Glide.with(avatar).load(url).circleCrop().into(avatar);
            }
            loadBlockState();
            loadThread(true);
        }));
    }

    private void loadThread(boolean showLoading) {
        if (loading) return;
        loading = true;
        if (showLoading) progress.setVisibility(View.VISIBLE);
        ZeroChillSocialRepository.loadDirectMessages(this, partnerId, (items, error) ->
                runOnUiThread(() -> {
                    loading = false;
                    progress.setVisibility(View.GONE);
                    if (error != null || items == null) {
                        if (showLoading) Toast.makeText(
                                this,
                                error == null ? "Couldn't load messages." : error.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }
                    String newest = items.isEmpty() ? "" : items.get(items.size() - 1).id;
                    boolean changed = !newest.equals(lastMessageId) || adapter.getItemCount() != items.size();
                    adapter.replace(items);
                    lastMessageId = newest;
                    if (changed && !items.isEmpty()) {
                        recycler.scrollToPosition(items.size() - 1);
                    }
                    ZeroChillSocialRepository.markDirectMessagesRead(
                            this,
                            partnerId,
                            (ignored, markError) -> {
                                if (markError == null) ZeroChillMessageBadgeStore.refresh(this);
                            }
                    );
                })
        );
    }

    private void sendMessage() {
        if (blockedByMe) {
            Toast.makeText(this, "Unblock this user before messaging them.", Toast.LENGTH_SHORT).show();
            return;
        }
        String value = composer.getText().toString().trim();
        if (value.isEmpty()) return;
        send.setEnabled(false);
        ZeroChillSocialRepository.sendDirectMessage(this, partnerId, value, (message, error) ->
                runOnUiThread(() -> {
                    send.setEnabled(true);
                    if (error != null) {
                        Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }
                    composer.setText("");
                    loadThread(false);
                })
        );
    }

    private void loadBlockState() {
        ZeroChillSocialRepository.blockState(this, partnerId, (blocked, error) ->
                runOnUiThread(() -> {
                    if (error == null) {
                        blockedByMe = Boolean.TRUE.equals(blocked);
                        updateComposerState();
                    }
                })
        );
    }

    private void updateComposerState() {
        composer.setEnabled(!blockedByMe);
        send.setEnabled(!blockedByMe);
        composer.setHint(blockedByMe
                ? "You blocked this user"
                : "Message @" + (partner == null ? "" : partner.username));
        send.setAlpha(blockedByMe ? 0.42f : 1f);
    }

    private void showOptions(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("View profile");
        menu.getMenu().add(blockedByMe ? "Unblock user" : "Block user");
        menu.getMenu().add("Report user");
        menu.setOnMenuItemClickListener(item -> {
            String label = String.valueOf(item.getTitle());
            if ("View profile".equals(label)) {
                openProfile();
            } else if ("Block user".equals(label) || "Unblock user".equals(label)) {
                confirmBlock(!blockedByMe);
            } else if ("Report user".equals(label)) {
                showReportDialog("");
            }
            return true;
        });
        menu.show();
    }

    private void confirmBlock(boolean block) {
        String username = partner == null ? "this user" : "@" + partner.username;
        new AlertDialog.Builder(this)
                .setTitle(block ? "Block " + username + "?" : "Unblock " + username + "?")
                .setMessage(block
                        ? "You won't be able to message each other while blocked."
                        : "You and this user will be able to message each other again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(block ? "Block" : "Unblock", (dialog, which) ->
                        ZeroChillSocialRepository.setBlocked(
                                this,
                                partnerId,
                                block,
                                (value, error) -> runOnUiThread(() -> {
                                    if (error != null) {
                                        Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                                        return;
                                    }
                                    blockedByMe = Boolean.TRUE.equals(value);
                                    updateComposerState();
                                    Toast.makeText(
                                            this,
                                            blockedByMe ? "User blocked." : "User unblocked.",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                })
                        ))
                .show();
    }

    private void showReportDialog(String messageId) {
        String[] reasons = {"Spam", "Harassment", "Other"};
        new AlertDialog.Builder(this)
                .setTitle(messageId.isEmpty() ? "Report user" : "Report message")
                .setItems(reasons, (dialog, which) -> {
                    String reason = which == 0 ? "spam" : which == 1 ? "harassment" : "other";
                    ZeroChillSocialRepository.Callback<Boolean> callback =
                            (ok, error) -> runOnUiThread(() -> Toast.makeText(
                                    this,
                                    error == null ? "Report submitted." : error.getMessage(),
                                    error == null ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG
                            ).show());
                    if (messageId.isEmpty()) {
                        ZeroChillSocialRepository.reportUser(this, partnerId, reason, callback);
                    } else {
                        ZeroChillSocialRepository.reportDirectMessage(
                                this,
                                partnerId,
                                messageId,
                                reason,
                                callback
                        );
                    }
                })
                .show();
    }

    private void openProfile() {
        Intent intent = new Intent(this, ZeroChillPublicProfileActivity.class);
        intent.putExtra(ZeroChillPublicProfileActivity.EXTRA_USER_ID, partnerId);
        startActivity(intent);
    }

    private final class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.Holder> {
        private final ArrayList<ZeroChillSocialRepository.DirectMessage> items = new ArrayList<>();

        void replace(List<ZeroChillSocialRepository.DirectMessage> next) {
            items.clear();
            if (next != null) items.addAll(next);
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(ZeroChillMessageActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            LinearLayout bubble = new LinearLayout(ZeroChillMessageActivity.this);
            bubble.setOrientation(LinearLayout.VERTICAL);
            bubble.setPadding(dp(12), dp(9), dp(12), dp(8));

            TextView body = text("", 14, Color.WHITE, false);
            body.setMaxWidth(dp(300));
            bubble.addView(body);

            TextView time = text("", 9, Color.rgb(148, 154, 164), false);
            time.setPadding(0, dp(4), 0, 0);
            bubble.addView(time);

            row.addView(bubble, new LinearLayout.LayoutParams(-2, -2));
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, -2);
            params.setMargins(0, dp(3), 0, dp(3));
            row.setLayoutParams(params);
            return new Holder(row, bubble, body, time);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            ZeroChillSocialRepository.DirectMessage item = items.get(position);
            boolean incoming = partnerId.equals(item.senderId);
            ((LinearLayout) holder.itemView).setGravity(incoming ? Gravity.START : Gravity.END);
            holder.bubble.setBackground(messageBubble(!incoming));
            holder.body.setText(item.body);
            holder.time.setText(formatTime(item.createdAt));
            holder.itemView.setContentDescription((incoming ? "From " : "You: ") + item.body);
            holder.itemView.setOnLongClickListener(v -> {
                if (incoming) showReportDialog(item.id);
                return incoming;
            });
        }

        final class Holder extends RecyclerView.ViewHolder {
            final LinearLayout bubble;
            final TextView body;
            final TextView time;

            Holder(View itemView, LinearLayout bubble, TextView body, TextView time) {
                super(itemView);
                this.bubble = bubble;
                this.body = body;
                this.time = time;
            }
        }
    }

    private GradientDrawable messageBubble(boolean mine) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(mine ? Color.rgb(7, 57, 78) : Color.rgb(20, 23, 28));
        background.setCornerRadius(dp(17));
        background.setStroke(dp(1), mine ? Color.rgb(10, 116, 154) : Color.rgb(45, 51, 59));
        return background;
    }

    private GradientDrawable inputBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(18, 21, 26));
        background.setCornerRadius(dp(17));
        background.setStroke(dp(1), Color.rgb(45, 54, 64));
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

    private String formatTime(String raw) {
        try {
            return DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.parse(raw));
        } catch (Exception ignored) {
            return "";
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
