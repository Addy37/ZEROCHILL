package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

/** First-party ZEROCHILL one-to-one message inbox. */
public final class ZeroChillInboxActivity extends Activity {
    private RecyclerView recycler;
    private InboxAdapter adapter;
    private TextView status;
    private TextView empty;
    private ProgressBar progress;
    private String renderedUser = "";
    private int requestGeneration;
    private boolean clearing;

    interface ConversationClearer {
        void clear(android.content.Context context, String partner,
                   ZeroChillSocialRepository.Callback<Boolean> callback);
    }
    interface InboxLoader {
        void load(android.content.Context context,
                  ZeroChillSocialRepository.Callback<ArrayList<ZeroChillSocialRepository.Conversation>> callback);
    }
    private InboxLoader inboxLoader = ZeroChillSocialRepository::loadInbox;
    private ConversationClearer conversationClearer = ZeroChillSocialRepository::clearConversation;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ZeroChillUi.applySystemBars(this);
        if (!ZeroChillAccountRepository.hasStoredSession(this)) {
            startActivity(new Intent(this, ZeroChillAccountActivity.class));
            finish();
            return;
        }
        renderedUser = ZeroChillSessionStore.currentUserId(this);
        buildUi();
        ResponsiveFitmentController.applySoon(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (recycler != null) {
            if (!renderedUser.equals(ZeroChillSessionStore.currentUserId(this))) {
                finish();
                return;
            }
            loadInbox();
        }
    }

    @Override
    protected void onDestroy() {
        ResponsiveFitmentController.release(this);
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = BrowseUi.screen(this);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(4), dp(14), dp(4));

        TextView back = BrowseUi.action(this, "‹", "Back", v -> finish());
        back.setTextSize(30);
        header.addView(back, new LinearLayout.LayoutParams(dp(46), dp(48)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(8), 0, 0, 0);
        TextView title = BrowseUi.text(this, "Messages", 21, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        labels.addView(title);
        status = BrowseUi.text(this, "ZEROCHILL DMs", 10, BrowseUi.MUTED);
        status.setLetterSpacing(0.08f);
        labels.addView(status);
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(58)));

        progress = new ProgressBar(this);
        ZeroChillUi.styleProgress(progress);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.setMargins(0, dp(30), 0, 0);
        root.addView(progress, progressParams);

        empty = BrowseUi.text(
                this,
                "No conversations yet.\n\nOpen a ZEROCHILL profile and tap Message to start one.",
                14,
                BrowseUi.MUTED
        );
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(28), dp(54), dp(28), dp(24));
        empty.setVisibility(View.GONE);
        root.addView(empty);

        recycler = new RecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setItemAnimator(null);
        recycler.setClipToPadding(false);
        recycler.setPadding(0, dp(4), 0, dp(18));
        adapter = new InboxAdapter();
        recycler.setAdapter(adapter);
        root.addView(recycler, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
    }

    private void loadInbox() {
        final int generation = ++requestGeneration;
        final String user = ZeroChillSessionStore.currentUserId(this);
        progress.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        inboxLoader.load(this, (items, error) -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || generation != requestGeneration
                    || !user.equals(ZeroChillSessionStore.currentUserId(this))) return;
            progress.setVisibility(View.GONE);
            if (error != null || items == null) {
                Toast.makeText(
                        this,
                        error == null ? "Couldn't load messages." : error.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
                return;
            }
            adapter.replace(items);
            renderCounts();
        }));
    }

    private void renderCounts() {
        int unread = 0;
        for (ZeroChillSocialRepository.Conversation item : adapter.items) unread += item.unreadCount;
        ZeroChillMessageBadgeStore.setUnreadCount(this, unread);
        status.setText(unread == 0 ? "ZEROCHILL DMs"
                : unread + (unread == 1 ? " unread message" : " unread messages"));
        empty.setVisibility(adapter.items.isEmpty() ? View.VISIBLE : View.GONE);
        recycler.setVisibility(adapter.items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void confirmRemove(ZeroChillSocialRepository.Conversation item) {
        if (clearing) return;
        final String user = ZeroChillSessionStore.currentUserId(this);
        new AlertDialog.Builder(this)
                .setTitle("Remove conversation?")
                .setMessage("This clears the conversation from your inbox and hides its old messages for you. "
                        + "The other person's history stays. A new message will bring the conversation back.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    if (clearing || !user.equals(ZeroChillSessionStore.currentUserId(this))) return;
                    clearing = true;
                    ++requestGeneration; // A pre-clear inbox response cannot restore the row.
                    conversationClearer.clear(this, item.profile.userId, (ok, error) -> runOnUiThread(() -> {
                        clearing = false;
                        if (isFinishing() || isDestroyed()
                                || !user.equals(ZeroChillSessionStore.currentUserId(this))) return;
                        if (error != null || !Boolean.TRUE.equals(ok)) {
                            Toast.makeText(this, error == null ? "Couldn't remove the conversation."
                                    : error.getMessage(), Toast.LENGTH_LONG).show();
                            loadInbox();
                            return;
                        }
                        ++requestGeneration;
                        adapter.items.removeIf(row -> row.profile.userId.equals(item.profile.userId));
                        adapter.notifyDataSetChanged();
                        progress.setVisibility(View.GONE);
                        renderCounts();
                        loadInbox(); // Includes any message sent after the server cutoff.
                    }));
                }).show();
    }

    private void open(ZeroChillSocialRepository.Conversation item) {
        Intent intent = new Intent(this, ZeroChillMessageActivity.class);
        intent.putExtra(ZeroChillMessageActivity.EXTRA_USER_ID, item.profile.userId);
        startActivity(intent);
    }

    private final class InboxAdapter extends RecyclerView.Adapter<InboxAdapter.Holder> {
        private final ArrayList<ZeroChillSocialRepository.Conversation> items = new ArrayList<>();

        void replace(List<ZeroChillSocialRepository.Conversation> next) {
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
            LinearLayout row = new LinearLayout(ZeroChillInboxActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(10), dp(10));
            row.setBackground(ZeroChillUi.panelGlass(ZeroChillInboxActivity.this));
            row.setClickable(true);
            row.setFocusable(true);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, -2);
            params.setMargins(dp(12), dp(5), dp(12), dp(5));
            row.setLayoutParams(params);

            ImageView avatar = new ImageView(ZeroChillInboxActivity.this);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            row.addView(avatar, new LinearLayout.LayoutParams(dp(52), dp(52)));

            LinearLayout labels = new LinearLayout(ZeroChillInboxActivity.this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(dp(12), 0, dp(8), 0);

            TextView name = BrowseUi.text(ZeroChillInboxActivity.this, "", 15, Color.WHITE);
            name.setTypeface(null, android.graphics.Typeface.BOLD);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(name);

            TextView preview = BrowseUi.text(ZeroChillInboxActivity.this, "", 12, BrowseUi.MUTED);
            preview.setSingleLine(true);
            preview.setEllipsize(TextUtils.TruncateAt.END);
            preview.setPadding(0, dp(3), 0, 0);
            labels.addView(preview);

            TextView time = BrowseUi.text(ZeroChillInboxActivity.this, "", 10, BrowseUi.MUTED);
            time.setPadding(0, dp(3), 0, 0);
            labels.addView(time);
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView unread = BrowseUi.text(ZeroChillInboxActivity.this, "", 10, Color.WHITE);
            unread.setTypeface(null, android.graphics.Typeface.BOLD);
            unread.setGravity(Gravity.CENTER);
            unread.setMinWidth(dp(24));
            unread.setPadding(dp(6), 0, dp(6), 0);
            unread.setBackground(BrowseUi.rounded(
                    ZeroChillInboxActivity.this,
                    UiPalette.PRIMARY,
                    12
            ));
            row.addView(unread, new LinearLayout.LayoutParams(-2, dp(24)));

            return new Holder(row, avatar, name, preview, time, unread);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            ZeroChillSocialRepository.Conversation item = items.get(position);
            ZeroChillSocialRepository.PublicProfile profile = item.profile;
            String label = profile.displayName.isEmpty()
                    ? "@" + profile.username
                    : profile.displayName;
            holder.name.setText(label);
            holder.preview.setText("@" + profile.username + "  ·  " + item.lastMessage.body);
            holder.time.setText(formatTime(item.lastMessage.createdAt));
            holder.unread.setVisibility(item.unreadCount > 0 ? View.VISIBLE : View.INVISIBLE);
            holder.unread.setText(item.unreadCount > 99 ? "99+" : String.valueOf(item.unreadCount));
            holder.itemView.setAlpha(item.unreadCount > 0 ? 1f : 0.82f);
            holder.itemView.setContentDescription(
                    label + ". " + item.lastMessage.body
                            + (item.unreadCount > 0 ? ". " + item.unreadCount + " unread." : "")
                            + ". Long press to remove conversation."
            );
            holder.itemView.setOnClickListener(v -> open(item));
            holder.itemView.setOnLongClickListener(v -> {
                confirmRemove(item);
                return true;
            });

            Glide.with(holder.avatar).clear(holder.avatar);
            AccountAvatarImages.track(holder.avatar, profile.userId);
            String avatarUrl = ZeroChillAccountRepository.avatarUrl(profile.avatarPath);
            if (avatarUrl.isEmpty()) {
                holder.avatar.setImageResource(R.drawable.ic_more_account);
                holder.avatar.setPadding(dp(12), dp(12), dp(12), dp(12));
                holder.avatar.setColorFilter(UiPalette.PRIMARY);
            } else {
                holder.avatar.setPadding(0, 0, 0, 0);
                holder.avatar.clearColorFilter();
                AccountAvatarImages.bind(holder.avatar, profile.userId, profile.avatarPath);
            }
        }

        @Override
        public void onViewRecycled(Holder holder) {
            Glide.with(holder.avatar).clear(holder.avatar);
            super.onViewRecycled(holder);
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView avatar;
            final TextView name;
            final TextView preview;
            final TextView time;
            final TextView unread;

            Holder(
                    View itemView,
                    ImageView avatar,
                    TextView name,
                    TextView preview,
                    TextView time,
                    TextView unread
            ) {
                super(itemView);
                this.avatar = avatar;
                this.name = name;
                this.preview = preview;
                this.time = time;
                this.unread = unread;
            }
        }
    }

    private String formatTime(String raw) {
        try {
            Instant instant = Instant.parse(raw);
            long ageMs = Math.max(0L, System.currentTimeMillis() - instant.toEpochMilli());
            if (ageMs < 24L * 60L * 60L * 1000L) {
                return DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
                        .withZone(ZoneId.systemDefault())
                        .format(instant);
            }
            return DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
                    .withZone(ZoneId.systemDefault())
                    .format(instant);
        } catch (Exception ignored) {
            return "";
        }
    }

    private int dp(int value) {
        return BrowseUi.dp(this, value);
    }
}
