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
    private boolean refreshQueued;
    private long threadRevision;
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
        composer.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) return;
            recycler.postDelayed(() -> {
                int count = adapter == null ? 0 : adapter.getItemCount();
                if (count > 0) recycler.scrollToPosition(count - 1);
            }, 180L);
        });
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
        if (loading) {
            refreshQueued = true;
            return;
        }
        loading = true;
        long revisionAtStart = threadRevision;
        if (showLoading) progress.setVisibility(View.VISIBLE);
        ZeroChillSocialRepository.loadDirectMessages(this, partnerId, (items, error) ->
                runOnUiThread(() -> {
                    loading = false;
                    progress.setVisibility(View.GONE);
                    boolean stale = revisionAtStart != threadRevision;
                    boolean rerun = refreshQueued || stale;
                    refreshQueued = false;
                    if (error != null || items == null) {
                        if (showLoading) Toast.makeText(
                                this,
                                error == null ? "Couldn't load messages." : error.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                        if (rerun) loadThread(false);
                        return;
                    }
                    if (stale) {
                        loadThread(false);
                        return;
                    }
                    String newest = items.isEmpty() ? "" : items.get(items.size() - 1).id;
                    boolean changed = !newest.equals(lastMessageId) || adapter.getItemCount() != items.size();
                    adapter.replace(items);
                    lastMessageId = newest;
                    if (changed && !items.isEmpty()) {
                        recycler.scrollToPosition(items.size() - 1);
                    }
                    boolean hasUnreadIncoming = false;
                    for (ZeroChillSocialRepository.DirectMessage item : items) {
                        if (partnerId.equals(item.senderId) && item.readAt.isEmpty()) {
                            hasUnreadIncoming = true;
                            break;
                        }
                    }
                    if (hasUnreadIncoming) {
                        ZeroChillSocialRepository.markDirectMessagesRead(
                                this,
                                partnerId,
                                (ignored, markError) -> {
                                    if (markError == null) ZeroChillMessageBadgeStore.refresh(this);
                                }
                        );
                    }
                    if (rerun) loadThread(false);
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
                    threadRevision++;
                    if (message != null) {
                        adapter.upsert(message);
                        lastMessageId = message.id;
                        if (adapter.getItemCount() > 0) {
                            recycler.scrollToPosition(adapter.getItemCount() - 1);
                        }
                    }
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

    // A pause or a local calendar-day boundary starts a new visual group.
    static boolean grouped(ZeroChillSocialRepository.DirectMessage first,
                           ZeroChillSocialRepository.DirectMessage second) {
        if (first == null || second == null || !first.senderId.equals(second.senderId)) return false;
        try {
            Instant a = Instant.parse(first.createdAt);
            Instant b = Instant.parse(second.createdAt);
            long gap = b.toEpochMilli() - a.toEpochMilli();
            ZoneId zone = ZoneId.systemDefault();
            return gap >= 0 && gap <= 300_000L
                    && a.atZone(zone).toLocalDate().equals(b.atZone(zone).toLocalDate());
        } catch (Exception ignored) {
            return false;
        }
    }

    private final class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.Holder> {
        private final ArrayList<ZeroChillSocialRepository.DirectMessage> items = new ArrayList<>();

        void replace(List<ZeroChillSocialRepository.DirectMessage> next) {
            items.clear();
            if (next != null) items.addAll(next);
            notifyDataSetChanged();
        }

        void upsert(ZeroChillSocialRepository.DirectMessage message) {
            if (message == null) return;
            for (int i = 0; i < items.size(); i++) {
                if (message.id.equals(items.get(i).id)) {
                    items.set(i, message);
                    notifyItemChanged(i);
                    return;
                }
            }
            items.add(message);
            notifyItemInserted(items.size() - 1);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(ZeroChillMessageActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.BOTTOM);
            row.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));

            ImageView senderAvatar = new ImageView(ZeroChillMessageActivity.this);
            senderAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            senderAvatar.setBackground(circle(Color.rgb(24, 27, 33)));
            senderAvatar.setClipToOutline(true);
            senderAvatar.setOnClickListener(v -> openProfile());
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(28), dp(28));
            avatarParams.setMarginEnd(dp(7));
            avatarParams.bottomMargin = dp(2);
            row.addView(senderAvatar, avatarParams);

            LinearLayout bubble = new LinearLayout(ZeroChillMessageActivity.this);
            bubble.setOrientation(LinearLayout.VERTICAL);
            bubble.setPadding(dp(12), dp(9), dp(12), dp(8));

            TextView body = text("", 14, Color.WHITE, false);
            body.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
            // Vertical LinearLayout defaults to MATCH_PARENT width. Its uniform-width
            // second pass freezes the first height while narrowing the text, clipping
            // wrapped lines. Measure both dimensions from the text in the first pass.
            bubble.addView(body, new LinearLayout.LayoutParams(-2, -2));

            TextView time = text("", 10, Color.rgb(166, 172, 184), false);
            time.setPadding(0, dp(4), 0, 0);
            LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(-2, -2);
            timeParams.gravity = Gravity.END;
            bubble.addView(time, timeParams);

            row.addView(bubble, new LinearLayout.LayoutParams(-2, -2));
            return new Holder(row, senderAvatar, bubble, body, time);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            ZeroChillSocialRepository.DirectMessage item = items.get(position);
            boolean incoming = partnerId.equals(item.senderId);
            boolean joinsPrevious = position > 0 && grouped(items.get(position - 1), item);
            boolean joinsNext = position + 1 < items.size() && grouped(item, items.get(position + 1));
            LinearLayout row = (LinearLayout) holder.itemView;
            row.setGravity((incoming ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM);
            // Keep the avatar gutter throughout an incoming group; outgoing rows have no gutter.
            holder.senderAvatar.setVisibility(incoming
                    ? (joinsNext ? View.INVISIBLE : View.VISIBLE) : View.GONE);
            Glide.with(holder.senderAvatar).clear(holder.senderAvatar);
            holder.senderAvatar.setImageDrawable(null);
            holder.senderAvatar.clearColorFilter();
            holder.senderAvatar.setPadding(0, 0, 0, 0);
            if (incoming && !joinsNext) {
                String url = partner == null ? "" : ZeroChillAccountRepository.avatarUrl(partner.avatarPath);
                holder.senderAvatar.setContentDescription(partner == null
                        ? "Sender profile" : "@" + partner.username + " profile");
                if (url.isEmpty()) {
                    holder.senderAvatar.setImageResource(R.drawable.ic_more_account);
                    holder.senderAvatar.setPadding(dp(6), dp(6), dp(6), dp(6));
                    holder.senderAvatar.setColorFilter(UiPalette.PRIMARY);
                } else {
                    Glide.with(holder.senderAvatar).load(url).circleCrop()
                            .placeholder(R.drawable.ic_more_account)
                            .error(R.drawable.ic_more_account).into(holder.senderAvatar);
                }
            }
            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) row.getLayoutParams();
            params.topMargin = dp(joinsPrevious ? 2 : 10);
            params.bottomMargin = dp(joinsNext ? 0 : 4);
            row.setLayoutParams(params);
            int width = recycler.getWidth() > 0 ? recycler.getWidth()
                    : getResources().getDisplayMetrics().widthPixels;
            int available = width - recycler.getPaddingLeft() - recycler.getPaddingRight();
            // Leave an opposite-side gutter even for long/unbroken text on narrow phones.
            int bodyWidth = Math.max(dp(48), Math.min(dp(300),
                    (int) (available * 0.78f) - dp(24) - (incoming ? dp(35) : 0)));
            holder.body.setMaxWidth(bodyWidth);
            holder.time.setMaxWidth(bodyWidth);
            holder.bubble.setBackground(messageBubble(!incoming, joinsPrevious, joinsNext));
            holder.body.setText(item.body);
            holder.time.setText(formatTime(item.createdAt));
            holder.time.setVisibility(joinsNext ? View.GONE : View.VISIBLE);
            holder.time.setTextColor(incoming ? Color.rgb(166, 172, 184) : Color.rgb(226, 240, 255));
            holder.itemView.setContentDescription((incoming ? "From " : "You: ") + item.body);
            holder.itemView.setOnLongClickListener(v -> {
                if (incoming) showReportDialog(item.id);
                return incoming;
            });
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView senderAvatar;
            final LinearLayout bubble;
            final TextView body;
            final TextView time;

            Holder(View itemView, ImageView senderAvatar, LinearLayout bubble, TextView body, TextView time) {
                super(itemView);
                this.senderAvatar = senderAvatar;
                this.bubble = bubble;
                this.body = body;
                this.time = time;
            }
        }
    }

    private GradientDrawable messageBubble(boolean mine, boolean joinsPrevious, boolean joinsNext) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(mine ? Color.rgb(8, 146, 208) : Color.rgb(35, 38, 45));
        float round = dp(22);
        float joined = dp(7);
        float top = joinsPrevious ? joined : round;
        float bottom = joinsNext ? joined : round;
        background.setCornerRadii(mine
                ? new float[]{round, round, top, top, bottom, bottom, round, round}
                : new float[]{top, top, round, round, round, round, bottom, bottom});
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
