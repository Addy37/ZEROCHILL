package com.webapp.crazyshit;

import android.app.Activity;
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

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ZeroChillUi.applySystemBars(this);
        if (!ZeroChillAccountRepository.hasStoredSession(this)) {
            startActivity(new Intent(this, ZeroChillAccountActivity.class));
            finish();
            return;
        }
        buildUi();
        ResponsiveFitmentController.applySoon(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (recycler != null) loadInbox();
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
        progress.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        ZeroChillSocialRepository.loadInbox(this, (items, error) -> runOnUiThread(() -> {
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
            int unread = 0;
            for (ZeroChillSocialRepository.Conversation item : items) unread += item.unreadCount;
            ZeroChillMessageBadgeStore.setUnreadCount(this, unread);
            status.setText(unread == 0
                    ? "ZEROCHILL DMs"
                    : unread + (unread == 1 ? " unread message" : " unread messages"));
            empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            recycler.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        }));
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
            );
            holder.itemView.setOnClickListener(v -> open(item));

            Glide.with(holder.avatar).clear(holder.avatar);
            String avatarUrl = ZeroChillAccountRepository.avatarUrl(profile.avatarPath);
            if (avatarUrl.isEmpty()) {
                holder.avatar.setImageResource(R.drawable.ic_more_account);
                holder.avatar.setPadding(dp(12), dp(12), dp(12), dp(12));
                holder.avatar.setColorFilter(UiPalette.PRIMARY);
            } else {
                holder.avatar.setPadding(0, 0, 0, 0);
                holder.avatar.clearColorFilter();
                Glide.with(holder.avatar)
                        .load(avatarUrl)
                        .circleCrop()
                        .placeholder(R.drawable.ic_more_account)
                        .into(holder.avatar);
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
