package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.InsetDrawable;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Notification history backed by the existing local update inbox. */
public final class UpdateInboxActivity extends Activity {
    private static final String FILTER_ALL = "";
    private static final String FILTER_SOCIAL = UpdateInboxStore.CATEGORY_SOCIAL;
    private static final String FILTER_CREATORS = UpdateInboxStore.CATEGORY_ONLYFAP;
    private static final String FILTER_APP = UpdateInboxStore.CATEGORY_APP;

    private RecyclerView recycler;
    private UpdateAdapter adapter;
    private TextView count;
    private TextView empty;
    private TextView allFilter;
    private TextView socialFilter;
    private TextView creatorsFilter;
    private TextView appFilter;
    private TextView markAll;
    private String activeFilter = FILTER_ALL;
    private SharedPreferences inboxPreferences;
    private boolean observing;
    private boolean socialLoading;
    private int socialGeneration;
    private final Runnable refreshHistory = () -> {
        if (observing && !isFinishing() && !isDestroyed()) render();
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener inboxListener = (prefs, key) -> {
        if (recycler == null) return;
        recycler.removeCallbacks(refreshHistory);
        recycler.post(refreshHistory);
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) activeFilter = state.getString("filter", FILTER_ALL);
        buildUi();
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        inboxPreferences = getSharedPreferences("zerochill_update_inbox_v1", Context.MODE_PRIVATE);
        observing = true;
        inboxPreferences.registerOnSharedPreferenceChangeListener(inboxListener);
        render();
        refreshSocialActivity();
    }

    @Override
    protected void onPause() {
        observing = false;
        if (inboxPreferences != null) {
            inboxPreferences.unregisterOnSharedPreferenceChangeListener(inboxListener);
        }
        if (recycler != null) recycler.removeCallbacks(refreshHistory);
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("filter", activeFilter);
        super.onSaveInstanceState(state);
    }

    private void buildUi() {
        LinearLayout root = BrowseUi.screen(this);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(4));

        header.addView(
                BrowseUi.action(this, "‹", "Back", v -> finish()),
                new LinearLayout.LayoutParams(dp(48), dp(48))
        );

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(12), 0, dp(8), 0);
        TextView title = BrowseUi.text(this, "Notifications", 22, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        titles.addView(title);
        count = BrowseUi.text(this, "", 11, BrowseUi.MUTED);
        titles.addView(count);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        markAll = BrowseUi.action(this, "Read all", "Mark all notifications read", v -> {
            UpdateInboxStore.markAllRead(this);
            render();
        });
        markAll.setTextSize(12);
        ZeroChillMotion.installPressFeedback(markAll);
        header.addView(markAll, new LinearLayout.LayoutParams(dp(80), dp(44)));
        root.addView(header);

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setGravity(Gravity.CENTER);
        filters.setPadding(dp(12), dp(4), dp(12), dp(8));

        allFilter = filter("All", FILTER_ALL);
        socialFilter = filter("Social", FILTER_SOCIAL);
        creatorsFilter = filter("Creators", FILTER_CREATORS);
        appFilter = filter("App", FILTER_APP);
        filters.addView(allFilter, filterParams(0));
        filters.addView(socialFilter, filterParams(1));
        filters.addView(creatorsFilter, filterParams(2));
        filters.addView(appFilter, filterParams(3));
        root.addView(filters);

        empty = BrowseUi.text(this, "", 15, BrowseUi.MUTED);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(24), dp(48), dp(24), dp(24));
        root.addView(empty);

        recycler = new RecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setPadding(0, dp(4), 0, dp(16));
        recycler.setClipToPadding(false);
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        if (ZeroChillMotion.animationsEnabled(this)) {
            itemAnimator.setAddDuration(ZeroChillMotion.STANDARD_MS);
            itemAnimator.setChangeDuration(ZeroChillMotion.QUICK_MS);
            itemAnimator.setRemoveDuration(ZeroChillMotion.QUICK_MS);
            itemAnimator.setMoveDuration(ZeroChillMotion.STANDARD_MS);
        } else {
            itemAnimator.setSupportsChangeAnimations(false);
            itemAnimator.setAddDuration(0);
            itemAnimator.setChangeDuration(0);
            itemAnimator.setRemoveDuration(0);
            itemAnimator.setMoveDuration(0);
        }
        recycler.setItemAnimator(itemAnimator);
        adapter = new UpdateAdapter();
        recycler.setAdapter(adapter);
        root.addView(recycler, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        ZeroChillUi.applySystemBars(this);
    }

    private TextView filter(String label, String filter) {
        TextView view = BrowseUi.action(this, label, label + " notifications", v -> {
            if (activeFilter.equals(filter)) return;
            activeFilter = filter;
            render();
        });
        view.setTextSize(12);
        view.setMinHeight(dp(44));
        view.setPadding(dp(4), 0, dp(4), 0);
        ZeroChillMotion.installPressFeedback(view);
        return view;
    }

    private LinearLayout.LayoutParams filterParams(int index) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1f);
        params.setMargins(index == 0 ? 0 : dp(2), 0, index == 3 ? 0 : dp(2), 0);
        return params;
    }

    private void render() {
        int unread = UpdateInboxStore.unreadCount(this);
        List<UpdateInboxStore.Entry> items = UpdateInboxStore.filtered(this, activeFilter);
        adapter.replace(items);

        count.setText(unread == 0
                ? "You're caught up"
                : unread + (unread == 1 ? " unread notification" : " unread notifications"));
        markAll.setVisibility(unread == 0 ? View.INVISIBLE : View.VISIBLE);

        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(UpdateInboxStore.all(this).isEmpty()
                ? "No notifications yet\n\nReplies, comment likes, creator activity, and app updates will appear here."
                : "No notifications in this section.");

        styleFilter(allFilter, FILTER_ALL.equals(activeFilter));
        styleFilter(socialFilter, FILTER_SOCIAL.equals(activeFilter));
        styleFilter(creatorsFilter, FILTER_CREATORS.equals(activeFilter));
        styleFilter(appFilter, FILTER_APP.equals(activeFilter));
    }

    private void refreshSocialActivity() {
        SocialActivityCoordinator.requestRefresh(this);
    }

    private void styleFilter(TextView view, boolean selected) {
        view.setTextColor(selected ? Color.WHITE : BrowseUi.MUTED);
        view.setBackground(new InsetDrawable(BrowseUi.rounded(
                this,
                selected ? UiPalette.PRIMARY : Color.rgb(24, 27, 32),
                13
        ), 0, dp(5), 0, dp(5)));
        Boolean oldSelection = (Boolean) view.getTag();
        if (oldSelection == null) {
            view.setScaleX(1f);
            view.setScaleY(1f);
        } else if (oldSelection != selected) {
            ZeroChillMotion.animateSelection(view, selected);
        }
        view.setTag(selected);
    }

    private void open(UpdateInboxStore.Entry entry) {
        if (UpdateInboxStore.CATEGORY_SOCIAL.equals(entry.category)
                && entry.pageUrl != null && !entry.pageUrl.trim().isEmpty()) {
            openSocial(entry, false);
            return;
        }

        UpdateInboxStore.markRead(this, entry.id);
        render();

        if (UpdateInboxStore.CATEGORY_APP.equals(entry.category)) {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra(SettingsActivity.EXTRA_CHECK_FOR_UPDATES, true);
            startActivity(intent);
            return;
        }

        if (UpdateInboxStore.CATEGORY_ONLYFAP.equals(entry.category) &&
                entry.creatorName != null && !entry.creatorName.trim().isEmpty()) {
            Intent intent = NativeFeedBrowserActivity.createCreatorGallery(
                    this,
                    entry.creatorName,
                    entry.creatorName,
                    FapelloRepository.isModelUrl(entry.fapelloProfileUrl)
                            ? entry.fapelloProfileUrl
                            : ""
            );
            intent.putStringArrayListExtra(
                    NativeFeedBrowserActivity.EXTRA_NOTIFICATION_FRESH_URLS,
                    new ArrayList<>(entry.freshUrls)
            );
            startActivity(intent);
            return;
        }

        NativeContentItem item = entry.firstItem();
        if (item != null && item.url != null && !item.url.trim().isEmpty()) {
            Intent intent = new Intent(this, WebFallbackActivity.class);
            intent.putExtra(WebFallbackActivity.EXTRA_URL, item.url);
            startActivity(intent);
        }
    }

    private void openSocial(UpdateInboxStore.Entry entry, boolean reply) {
        UpdateInboxStore.markRead(this, entry.id);
        render();
        SocialContentNavigator.open(this, entry, reply);
    }

    private String socialActor(UpdateInboxStore.Entry entry) {
        if (entry.actorName != null && !entry.actorName.trim().isEmpty()) {
            return cleanDisplayName(entry.actorName);
        }
        String title = entry.title == null ? "" : entry.title.trim();
        String[] suffixes = {" replied to your comment", " liked your comment"};
        for (String suffix : suffixes) {
            if (title.endsWith(suffix)) return cleanDisplayName(title.substring(0, title.length() - suffix.length()));
        }
        return cleanDisplayName(title);
    }

    private String cleanDisplayName(String raw) {
        String name = raw == null ? "" : raw.trim();
        while (name.startsWith("@")) name = name.substring(1).trim();
        return name.isEmpty() ? "Someone" : name;
    }

    private int dp(int value) {
        return BrowseUi.dp(this, value);
    }

    private final class UpdateAdapter extends RecyclerView.Adapter<UpdateAdapter.Holder> {
        private final ArrayList<UpdateInboxStore.Entry> items = new ArrayList<>();
        private final Map<String, ZeroChillSocialRepository.Comment> commentStates = new HashMap<>();
        private final Set<String> commentPagesLoading = new HashSet<>();
        private final Set<String> commentLikeRequests = new HashSet<>();
        private String loadedAccount = "";

        void replace(List<UpdateInboxStore.Entry> next) {
            String account = ZeroChillSessionStore.currentUserId(UpdateInboxActivity.this);
            if (!account.equals(loadedAccount)) {
                loadedAccount = account;
                commentStates.clear();
                commentPagesLoading.clear();
                commentLikeRequests.clear();
            }
            ArrayList<UpdateInboxStore.Entry> previous = new ArrayList<>(items);
            ArrayList<UpdateInboxStore.Entry> incoming = new ArrayList<>();
            if (next != null) incoming.addAll(next);
            DiffUtil.DiffResult changes = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return previous.size(); }
                @Override public int getNewListSize() { return incoming.size(); }
                @Override public boolean areItemsTheSame(int oldPosition, int newPosition) {
                    return previous.get(oldPosition).id.equals(incoming.get(newPosition).id);
                }
                @Override public boolean areContentsTheSame(int oldPosition, int newPosition) {
                    try {
                        return previous.get(oldPosition).encode().toString()
                                .equals(incoming.get(newPosition).encode().toString());
                    } catch (Exception ignored) {
                        return false;
                    }
                }
            });
            items.clear();
            items.addAll(incoming);
            changes.dispatchUpdatesTo(this);
        }

        @Override public int getItemCount() { return items.size(); }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout shell = new LinearLayout(UpdateInboxActivity.this);
            shell.setOrientation(LinearLayout.VERTICAL);
            shell.setPadding(dp(10), 0, dp(10), 0);
            RecyclerView.LayoutParams shellParams = new RecyclerView.LayoutParams(-1, -2);
            shell.setLayoutParams(shellParams);

            TextView section = BrowseUi.text(UpdateInboxActivity.this, "", 12, BrowseUi.MUTED);
            section.setTypeface(null, android.graphics.Typeface.BOLD);
            section.setPadding(dp(2), dp(12), 0, dp(5));
            section.setVisibility(View.GONE);
            shell.addView(section, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout row = new LinearLayout(UpdateInboxActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            row.setPadding(dp(10), dp(12), dp(7), dp(12));
            row.setBackground(BrowseUi.rounded(UpdateInboxActivity.this, Color.rgb(17, 20, 24), 14));
            row.setFocusable(true);
            row.setClickable(true);
            ZeroChillMotion.installPressFeedback(row);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.setMargins(0, dp(3), 0, dp(3));
            shell.addView(row, rowParams);

            ImageView avatar = new ImageView(UpdateInboxActivity.this);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.setClipToOutline(true);
            avatar.setBackground(BrowseUi.rounded(UpdateInboxActivity.this, BrowseUi.SURFACE, 24));
            row.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));

            LinearLayout labels = new LinearLayout(UpdateInboxActivity.this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(dp(12), dp(1), dp(5), 0);
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView title = BrowseUi.text(UpdateInboxActivity.this, "", 15, Color.WHITE);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(title, new LinearLayout.LayoutParams(-1, -2));

            TextView subtitle = BrowseUi.text(UpdateInboxActivity.this, "", 13, BrowseUi.MUTED);
            subtitle.setMaxLines(3);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            subtitle.setPadding(0, dp(4), 0, 0);
            labels.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout actions = new LinearLayout(UpdateInboxActivity.this);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            actions.setPadding(0, dp(3), 0, 0);
            labels.addView(actions, new LinearLayout.LayoutParams(-1, -2));

            TextView reply = actionPill("Reply");
            TextView like = actionPill("");
            like.setMinWidth(dp(58));
            like.setPadding(dp(12), 0, dp(12), 0);
            like.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_action_heart_outline, 0, 0, 0);
            like.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
            like.setCompoundDrawablePadding(dp(4));
            like.setContentDescription("Like reply");
            actions.addView(reply, actionPillParams());
            actions.addView(like, actionPillParams());

            android.widget.FrameLayout trailing = new android.widget.FrameLayout(UpdateInboxActivity.this);
            LinearLayout.LayoutParams trailingParams = new LinearLayout.LayoutParams(dp(52), dp(52));
            row.addView(trailing, trailingParams);

            ImageView thumbnail = new ImageView(UpdateInboxActivity.this);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setClipToOutline(true);
            thumbnail.setBackground(BrowseUi.rounded(UpdateInboxActivity.this, BrowseUi.SURFACE, 10));
            ZeroChillMotion.installPressFeedback(thumbnail);
            trailing.addView(thumbnail, new android.widget.FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));

            View unread = new View(UpdateInboxActivity.this);
            unread.setBackground(circle(UiPalette.PRIMARY));
            android.widget.FrameLayout.LayoutParams dotParams =
                    new android.widget.FrameLayout.LayoutParams(dp(9), dp(9), Gravity.TOP | Gravity.END);
            dotParams.setMargins(0, dp(1), dp(1), 0);
            trailing.addView(unread, dotParams);

            View divider = new View(UpdateInboxActivity.this);
            divider.setBackgroundColor(Color.rgb(23, 27, 31));
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
            dividerParams.setMargins(dp(60), 0, 0, 0);
            shell.addView(divider, dividerParams);

            return new Holder(shell, row, section, avatar, title, subtitle, actions, reply, like,
                    trailing, thumbnail, unread);
        }

        private TextView actionPill(String label) {
            TextView view = BrowseUi.text(UpdateInboxActivity.this, label, 12, Color.rgb(220, 220, 226));
            view.setGravity(Gravity.CENTER);
            view.setTypeface(null, android.graphics.Typeface.BOLD);
            view.setMinWidth(dp(74));
            view.setPadding(dp(14), 0, dp(14), 0);
            view.setBackground(new InsetDrawable(BrowseUi.rounded(
                    UpdateInboxActivity.this, Color.rgb(37, 43, 51), 12
            ), 0, dp(4), 0, dp(4)));
            ZeroChillMotion.installPressFeedback(view);
            return view;
        }

        private LinearLayout.LayoutParams actionPillParams() {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(44));
            params.setMargins(0, 0, dp(4), 0);
            return params;
        }

        private android.graphics.drawable.GradientDrawable circle(int color) {
            android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
            drawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            drawable.setColor(color);
            return drawable;
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            UpdateInboxStore.Entry entry = items.get(position);
            boolean social = UpdateInboxStore.CATEGORY_SOCIAL.equals(entry.category);
            boolean replyNotification = social
                    && ZeroChillSocialRepository.SocialActivity.TYPE_REPLY.equals(entry.socialType);

            String section = sectionLabel(position);
            holder.section.setText(section);
            holder.section.setVisibility(section.isEmpty() ? View.GONE : View.VISIBLE);

            holder.title.setText(styledTitle(entry));
            holder.subtitle.setText(detailText(entry));
            holder.subtitle.setVisibility(holder.subtitle.getText().length() == 0 ? View.GONE : View.VISIBLE);

            holder.actions.setVisibility(replyNotification ? View.VISIBLE : View.GONE);
            holder.reply.setVisibility(replyNotification ? View.VISIBLE : View.GONE);
            holder.like.setVisibility(replyNotification ? View.VISIBLE : View.GONE);
            holder.reply.setOnClickListener(v -> openSocial(entry, true));
            bindSocialLike(holder, entry, replyNotification);

            boolean becameRead = holder.itemView.isAttachedToWindow()
                    && entry.read && entry.id.equals(holder.boundId)
                    && holder.unread.getVisibility() == View.VISIBLE;
            holder.unread.animate().cancel();
            holder.unread.setVisibility(entry.read && !becameRead ? View.INVISIBLE : View.VISIBLE);
            if (becameRead && ZeroChillMotion.animationsEnabled(UpdateInboxActivity.this)) {
                holder.unread.animate().alpha(0f).scaleX(0.4f).scaleY(0.4f)
                        .setDuration(ZeroChillMotion.QUICK_MS)
                        .withEndAction(() -> holder.unread.setVisibility(View.INVISIBLE)).start();
            } else {
                holder.unread.setAlpha(1f);
                holder.unread.setScaleX(1f);
                holder.unread.setScaleY(1f);
            }
            holder.boundId = entry.id;
            holder.row.setAlpha(1f);
            holder.row.setContentDescription(
                    styledTitle(entry).toString() + ". " + entry.subtitle
                            + (entry.read ? ". Read." : ". Unread notification.")
            );
            holder.row.setOnClickListener(v -> open(entry));

            bindAvatar(holder.avatar, entry);
            bindThumbnail(holder, entry);
        }

        private void bindSocialLike(
                Holder holder,
                UpdateInboxStore.Entry entry,
                boolean replyNotification
        ) {
            holder.like.animate().cancel();
            holder.like.setScaleX(1f);
            holder.like.setScaleY(1f);
            holder.like.setAlpha(1f);
            holder.like.setText("");
            holder.like.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_action_heart_outline, 0, 0, 0
            );
            holder.like.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
            holder.like.setTextColor(Color.WHITE);
            holder.like.setOnClickListener(null);

            if (!replyNotification) return;
            String commentId = entry.commentId == null ? "" : entry.commentId.trim();
            String page = entry.pageUrl == null ? "" : entry.pageUrl.trim();
            if (commentId.isEmpty() || page.isEmpty()) {
                holder.like.setVisibility(View.GONE);
                return;
            }

            ZeroChillSocialRepository.Comment state = commentStates.get(commentId);
            if (state == null) {
                holder.like.setEnabled(false);
                holder.like.setContentDescription("Loading reply like state");
                requestCommentStates(page);
                return;
            }
            if (state.deleted()) {
                holder.like.setVisibility(View.GONE);
                return;
            }

            holder.like.setEnabled(!commentLikeRequests.contains(commentId));
            holder.like.setCompoundDrawablesWithIntrinsicBounds(
                    state.likedByMe
                            ? R.drawable.ic_action_heart_filled
                            : R.drawable.ic_action_heart_outline,
                    0,
                    0,
                    0
            );
            holder.like.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(
                    state.likedByMe ? UiPalette.PRIMARY : Color.WHITE
            ));
            holder.like.setText(state.likeCount > 0 ? String.valueOf(state.likeCount) : "");
            holder.like.setTextColor(state.likedByMe ? UiPalette.PRIMARY : Color.WHITE);
            holder.like.setContentDescription(state.likedByMe ? "Unlike reply" : "Like reply");
            holder.like.setOnClickListener(v -> toggleNotificationCommentLike(holder, entry));
        }

        private void requestCommentStates(String pageUrl) {
            String pageKey = ZeroChillSocialRepository.contentKey(pageUrl);
            if (pageKey.isEmpty() || !commentPagesLoading.add(pageKey)) return;
            ZeroChillSocialRepository.loadComments(
                    UpdateInboxActivity.this,
                    pageUrl,
                    (loaded, error) -> runOnUiThread(() -> {
                        commentPagesLoading.remove(pageKey);
                        if (isFinishing() || isDestroyed()) return;
                        if (error != null || loaded == null) return;
                        for (ZeroChillSocialRepository.Comment comment : loaded) {
                            if (comment != null && comment.id != null && !comment.id.isEmpty()) {
                                commentStates.put(comment.id, comment);
                            }
                        }
                        if (!items.isEmpty()) notifyItemRangeChanged(0, items.size(), "social-like");
                    })
            );
        }

        private void toggleNotificationCommentLike(
                Holder holder,
                UpdateInboxStore.Entry entry
        ) {
            String commentId = entry.commentId == null ? "" : entry.commentId.trim();
            ZeroChillSocialRepository.Comment state = commentStates.get(commentId);
            if (state == null || state.deleted() || !commentLikeRequests.add(commentId)) return;

            holder.like.setEnabled(false);
            ZeroChillSocialRepository.toggleCommentLike(
                    UpdateInboxActivity.this,
                    commentId,
                    state.likedByMe,
                    (liked, error) -> runOnUiThread(() -> {
                        commentLikeRequests.remove(commentId);
                        if (isFinishing() || isDestroyed()) return;
                        if (error != null) {
                            Toast.makeText(
                                    UpdateInboxActivity.this,
                                    error.getMessage() == null ? "Unable to update the like." : error.getMessage(),
                                    Toast.LENGTH_SHORT
                            ).show();
                        } else {
                            ZeroChillSocialRepository.Comment current = commentStates.get(commentId);
                            if (current != null) commentStates.put(commentId, current.withLikeState(liked));
                        }
                        int index = indexOf(entry.id);
                        if (index >= 0) notifyItemChanged(index, "social-like");
                        if (error == null && liked && entry.id.equals(holder.boundId)) {
                            holder.like.post(() -> {
                                if (entry.id.equals(holder.boundId)) animateHeartPop(holder.like);
                            });
                        }
                    })
            );
        }

        private int indexOf(String id) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).id.equals(id)) return i;
            }
            return -1;
        }

        private void animateHeartPop(View heart) {
            if (heart == null || !ZeroChillMotion.animationsEnabled(UpdateInboxActivity.this)) return;
            heart.animate().cancel();
            heart.setScaleX(0.88f);
            heart.setScaleY(0.88f);
            heart.animate()
                    .scaleX(1.08f)
                    .scaleY(1.08f)
                    .setDuration(ZeroChillMotion.QUICK_MS)
                    .withEndAction(() -> heart.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(ZeroChillMotion.PRESS_IN_MS)
                            .start())
                    .start();
        }

        private String sectionLabel(int position) {
            if (items.size() < 4 || position < 0 || position >= items.size()) return "";
            UpdateInboxStore.Entry current = items.get(position);
            if (position == 0) return current.read ? "Earlier" : "New";
            UpdateInboxStore.Entry previous = items.get(position - 1);
            if (current.read && !previous.read) return "Earlier";
            return "";
        }

        private CharSequence styledTitle(UpdateInboxStore.Entry entry) {
            SpannableStringBuilder text = new SpannableStringBuilder();
            String time = relativeTime(entry.timestamp);
            if (UpdateInboxStore.CATEGORY_SOCIAL.equals(entry.category)) {
                String actor = socialActor(entry);
                String action = ZeroChillSocialRepository.SocialActivity.TYPE_REPLY.equals(entry.socialType)
                        ? " replied to your comment"
                        : " liked your comment";
                int actorStart = text.length();
                text.append(actor);
                text.setSpan(
                        new StyleSpan(android.graphics.Typeface.BOLD),
                        actorStart,
                        text.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                text.append(action);
            } else {
                int start = text.length();
                text.append(entry.title);
                text.setSpan(
                        new StyleSpan(android.graphics.Typeface.BOLD),
                        start,
                        text.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
            if (!time.isEmpty()) {
                int timeStart = text.length();
                text.append("  ").append(time);
                text.setSpan(
                        new ForegroundColorSpan(BrowseUi.MUTED),
                        timeStart,
                        text.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                text.setSpan(
                        new RelativeSizeSpan(0.80f),
                        timeStart,
                        text.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
            return text;
        }

        private String detailText(UpdateInboxStore.Entry entry) {
            String detail = entry.subtitle == null ? "" : entry.subtitle.trim();
            if (UpdateInboxStore.CATEGORY_ONLYFAP.equals(entry.category)) {
                if (!detail.isEmpty()) detail += "  ·  ";
                detail += "Tap to see marked new items";
            }
            return detail;
        }

        private void bindAvatar(ImageView avatar, UpdateInboxStore.Entry entry) {
            Glide.with(avatar).clear(avatar);
            int fallback = UpdateInboxStore.CATEGORY_ONLYFAP.equals(entry.category)
                    ? R.drawable.ic_zerochill_devil
                    : UpdateInboxStore.CATEGORY_SOCIAL.equals(entry.category)
                    ? R.drawable.ic_more_account
                    : R.drawable.ic_more_update;
            avatar.setImageResource(fallback);

            String imageUrl = entry.avatarUrl;
            String referer = entry.avatarReferer;
            if ((imageUrl == null || imageUrl.trim().isEmpty()) && entry.firstItem() != null) {
                NativeContentItem first = entry.firstItem();
                imageUrl = first.imageUrl;
                referer = first.uploader == null || first.uploader.trim().isEmpty()
                        ? first.url
                        : first.uploader;
            }
            loadImage(avatar, imageUrl, referer, fallback, true);
        }

        private void bindThumbnail(Holder holder, UpdateInboxStore.Entry entry) {
            NativeContentItem first = entry.firstItem();
            if (UpdateInboxStore.CATEGORY_SOCIAL.equals(entry.category)
                    && entry.pageUrl != null
                    && !entry.pageUrl.trim().isEmpty()
                    && (first == null || first.imageUrl == null || first.imageUrl.trim().isEmpty())) {
                NativeContentItem cached =
                        SocialContentContextStore.find(UpdateInboxActivity.this, entry.pageUrl);
                if (cached != null && cached.imageUrl != null && !cached.imageUrl.trim().isEmpty()) {
                    first = cached;
                }
            }
            String imageUrl = first == null ? "" : first.imageUrl;
            String referer = first == null ? "" :
                    (first.uploader == null || first.uploader.trim().isEmpty()
                            ? first.url : first.uploader);
            boolean show = imageUrl != null && !imageUrl.trim().isEmpty();
            if (!(referer.startsWith("https://") || referer.startsWith("http://"))) referer = first == null ? "" : first.url;
            holder.thumbnail.setVisibility(show ? View.VISIBLE : View.GONE);
            holder.thumbnail.setClickable(show);
            holder.thumbnail.setFocusable(show);
            holder.thumbnail.setContentDescription(show ? "Open video and conversation" : null);
            holder.thumbnail.setOnClickListener(show ? v -> open(entry) : null);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.trailing.getLayoutParams();
            params.width = dp(show ? 52 : 14);
            holder.trailing.setLayoutParams(params);
            Glide.with(holder.thumbnail).clear(holder.thumbnail);
            if (show) {
                holder.thumbnail.setImageDrawable(null);
                loadImage(holder.thumbnail, imageUrl, referer, 0, false);
            }
        }

        private void loadImage(
                ImageView view,
                String imageUrl,
                String referer,
                int fallback,
                boolean circle
        ) {
            if (imageUrl == null || imageUrl.trim().isEmpty()) return;
            LazyHeaders.Builder headers = new LazyHeaders.Builder()
                    .addHeader(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/139.0 Mobile Safari/537.36"
                    );
            if (referer != null && !referer.trim().isEmpty()) {
                headers.addHeader("Referer", referer.trim());
            }
            GlideUrl model = new GlideUrl(imageUrl.trim(), headers.build());
            com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> request =
                    Glide.with(view)
                            .load(model)
                            .transition(DrawableTransitionOptions.withCrossFade((int) ZeroChillMotion.QUICK_MS));
            if (fallback != 0) request = request.placeholder(fallback).error(fallback);
            if (circle) request = request.circleCrop();
            request.into(view);
        }

        @Override
        public void onViewRecycled(Holder holder) {
            Glide.with(holder.avatar).clear(holder.avatar);
            Glide.with(holder.thumbnail).clear(holder.thumbnail);
            super.onViewRecycled(holder);
        }

        private String relativeTime(long timestamp) {
            long delta = Math.max(0L, System.currentTimeMillis() - timestamp);
            long minutes = delta / 60_000L;
            if (minutes < 1) return "now";
            if (minutes < 60) return minutes + "m";
            long hours = minutes / 60L;
            if (hours < 24) return hours + "h";
            long days = hours / 24L;
            if (days < 7) return days + "d";
            return (days / 7L) + "w";
        }

        final class Holder extends RecyclerView.ViewHolder {
            final LinearLayout row;
            final TextView section;
            final ImageView avatar;
            final TextView title;
            final TextView subtitle;
            final LinearLayout actions;
            final TextView reply;
            final TextView like;
            final android.widget.FrameLayout trailing;
            final ImageView thumbnail;
            final View unread;
            String boundId;

            Holder(
                    LinearLayout shell,
                    LinearLayout row,
                    TextView section,
                    ImageView avatar,
                    TextView title,
                    TextView subtitle,
                    LinearLayout actions,
                    TextView reply,
                    TextView like,
                    android.widget.FrameLayout trailing,
                    ImageView thumbnail,
                    View unread
            ) {
                super(shell);
                this.row = row;
                this.section = section;
                this.avatar = avatar;
                this.title = title;
                this.subtitle = subtitle;
                this.actions = actions;
                this.reply = reply;
                this.like = like;
                this.trailing = trailing;
                this.thumbnail = thumbnail;
                this.unread = unread;
            }
        }
    }
}
