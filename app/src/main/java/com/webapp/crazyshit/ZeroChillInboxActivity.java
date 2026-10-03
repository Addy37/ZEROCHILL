package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** First-party ZEROCHILL one-to-one message inbox. */
public final class ZeroChillInboxActivity extends Activity {
    private static final String STATE_SELECTED = "zerochill_selected_conversations";

    private RecyclerView recycler;
    private InboxAdapter adapter;
    private TextView title;
    private TextView status;
    private TextView selectAllAction;
    private TextView deleteAction;
    private TextView cancelAction;
    private LinearLayout selectionActions;
    private TextView empty;
    private ProgressBar progress;
    private Dialog removeDialog;
    private final LinkedHashSet<String> selectedPartners = new LinkedHashSet<>();
    private String renderedUser = "";
    private int requestGeneration;
    private int unreadCount;
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
        if (state != null) {
            ArrayList<String> restored = state.getStringArrayList(STATE_SELECTED);
            if (restored != null) selectedPartners.addAll(restored);
        }
        buildUi();
        updateSelectionUi();
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
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!selectedPartners.isEmpty()) {
            outState.putStringArrayList(STATE_SELECTED, new ArrayList<>(selectedPartners));
        }
    }

    @Override
    public void onBackPressed() {
        if (selectionMode()) {
            clearSelection();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (removeDialog != null) {
            removeDialog.dismiss();
            removeDialog = null;
        }
        ResponsiveFitmentController.release(this);
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = BrowseUi.screen(this);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(4), dp(14), dp(4));

        TextView back = BrowseUi.action(this, "‹", "Back", v -> {
            if (selectionMode()) clearSelection();
            else finish();
        });
        back.setTextSize(30);
        header.addView(back, new LinearLayout.LayoutParams(dp(46), dp(48)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(8), 0, 0, 0);
        title = BrowseUi.text(this, "Messages", 21, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(title);
        status = BrowseUi.text(this, "ZEROCHILL DMs", 10, BrowseUi.MUTED);
        status.setLetterSpacing(0.08f);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(status);
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        selectionActions = new LinearLayout(this);
        selectionActions.setGravity(Gravity.CENTER_VERTICAL);
        selectionActions.setVisibility(View.GONE);

        selectAllAction = headerAction("All", "Select all conversations", v -> selectAll());
        deleteAction = headerAction("Delete", "Remove selected conversations", v -> confirmRemoveSelected());
        cancelAction = headerAction("Cancel", "Cancel conversation selection", v -> clearSelection());
        selectionActions.addView(selectAllAction);
        selectionActions.addView(deleteAction);
        selectionActions.addView(cancelAction);
        header.addView(selectionActions, new LinearLayout.LayoutParams(-2, dp(48)));
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

    private TextView headerAction(String label, String description, View.OnClickListener click) {
        TextView action = BrowseUi.text(this, label, 12, UiPalette.PRIMARY);
        action.setGravity(Gravity.CENTER);
        action.setMinWidth(dp(48));
        action.setMinHeight(dp(48));
        action.setPadding(dp(7), 0, dp(7), 0);
        action.setContentDescription(description);
        action.setFocusable(true);
        action.setClickable(true);
        action.setOnClickListener(click);
        ZeroChillMotion.installPressFeedback(action);
        return action;
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
        unreadCount = 0;
        for (ZeroChillSocialRepository.Conversation item : adapter.items) unreadCount += item.unreadCount;
        ZeroChillMessageBadgeStore.setUnreadCount(this, unreadCount);
        updateSelectionUi();
        empty.setVisibility(adapter.items.isEmpty() ? View.VISIBLE : View.GONE);
        recycler.setVisibility(adapter.items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private boolean selectionMode() {
        return !selectedPartners.isEmpty();
    }

    private void toggleSelection(ZeroChillSocialRepository.Conversation item) {
        if (clearing || item == null || item.profile == null) return;
        String partner = item.profile.userId;
        if (selectedPartners.contains(partner)) selectedPartners.remove(partner);
        else selectedPartners.add(partner);
        updateSelectionUi();
        adapter.notifyDataSetChanged();
    }

    private void selectAll() {
        if (clearing) return;
        selectedPartners.clear();
        for (ZeroChillSocialRepository.Conversation item : adapter.items) {
            if (item != null && item.profile != null && !TextUtils.isEmpty(item.profile.userId)) {
                selectedPartners.add(item.profile.userId);
            }
        }
        updateSelectionUi();
        adapter.notifyDataSetChanged();
    }

    private void clearSelection() {
        if (clearing) return;
        selectedPartners.clear();
        updateSelectionUi();
        adapter.notifyDataSetChanged();
    }

    private void updateSelectionUi() {
        if (title == null || status == null || selectionActions == null) return;
        boolean selecting = selectionMode();
        if (clearing) {
            title.setText("Removing…");
            status.setText(selectedPartners.size() == 1
                    ? "Removing conversation"
                    : "Removing " + selectedPartners.size() + " conversations");
        } else if (selecting) {
            int count = selectedPartners.size();
            title.setText(count + (count == 1 ? " selected" : " selected"));
            status.setText("Tap conversations to change selection");
        } else {
            title.setText("Messages");
            status.setText(unreadCount == 0 ? "ZEROCHILL DMs"
                    : unreadCount + (unreadCount == 1 ? " unread message" : " unread messages"));
        }
        selectionActions.setVisibility(selecting ? View.VISIBLE : View.GONE);
        selectAllAction.setEnabled(!clearing);
        deleteAction.setEnabled(!clearing);
        cancelAction.setEnabled(!clearing);
        float alpha = clearing ? 0.42f : 1f;
        selectAllAction.setAlpha(alpha);
        deleteAction.setAlpha(alpha);
        cancelAction.setAlpha(alpha);
    }

    private ArrayList<ZeroChillSocialRepository.Conversation> selectedItems() {
        ArrayList<ZeroChillSocialRepository.Conversation> chosen = new ArrayList<>();
        for (ZeroChillSocialRepository.Conversation item : adapter.items) {
            if (item != null && item.profile != null && selectedPartners.contains(item.profile.userId)) {
                chosen.add(item);
            }
        }
        return chosen;
    }

    private void confirmRemoveSelected() {
        if (clearing || selectedPartners.isEmpty()) return;
        ArrayList<ZeroChillSocialRepository.Conversation> chosen = selectedItems();
        if (chosen.isEmpty()) {
            clearSelection();
            return;
        }
        showRemoveDialog(chosen);
    }

    private void showRemoveDialog(ArrayList<ZeroChillSocialRepository.Conversation> chosen) {
        if (removeDialog != null) removeDialog.dismiss();
        final int count = chosen.size();
        Dialog dialog = new Dialog(this);
        removeDialog = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(22), dp(20), dp(22), dp(14));
        sheet.setBackground(ZeroChillUi.rounded(
                this,
                Color.rgb(13, 16, 20),
                ZeroChillUi.color(this, R.color.zc_edge),
                R.dimen.zc_radius_large
        ));

        TextView heading = BrowseUi.text(
                this,
                count == 1 ? "Remove conversation?" : "Remove " + count + " conversations?",
                20,
                Color.WHITE
        );
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        sheet.addView(heading);

        TextView body = BrowseUi.text(
                this,
                count == 1
                        ? "It’ll disappear from your inbox and its old messages will stay hidden for you. "
                                + "The other person’s history stays. A new message can bring it back."
                        : "They’ll disappear from your inbox and their old messages will stay hidden for you. "
                                + "The other people’s history stays. New messages can bring the conversations back.",
                14,
                ZeroChillUi.color(this, R.color.zc_text_secondary)
        );
        body.setLineSpacing(0f, 1.12f);
        body.setPadding(0, dp(10), 0, dp(14));
        sheet.addView(body);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        TextView cancel = dialogAction("Cancel", false);
        cancel.setTag("conversation-remove-cancel");
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(-2, dp(44)));

        TextView remove = dialogAction(count == 1 ? "Remove" : "Remove " + count, true);
        remove.setTag("conversation-remove-confirm");
        remove.setOnClickListener(v -> {
            dialog.dismiss();
            if (!clearing) beginClear(chosen);
        });
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(-2, dp(44));
        removeParams.setMarginStart(dp(8));
        actions.addView(remove, removeParams);
        sheet.addView(actions);

        dialog.setContentView(sheet);
        dialog.setOnDismissListener(ignored -> {
            if (removeDialog == dialog) removeDialog = null;
        });
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            attributes.dimAmount = 0.72f;
            attributes.y = dp(18);
            window.setAttributes(attributes);
            int screen = getResources().getDisplayMetrics().widthPixels;
            window.setLayout(Math.min(screen - dp(24), dp(560)), WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private TextView dialogAction(String label, boolean primary) {
        TextView action = BrowseUi.text(
                this,
                label,
                14,
                primary ? UiPalette.PRIMARY : ZeroChillUi.color(this, R.color.zc_text_secondary)
        );
        action.setTypeface(null, android.graphics.Typeface.BOLD);
        action.setGravity(Gravity.CENTER);
        action.setMinWidth(dp(72));
        action.setPadding(dp(14), 0, dp(14), 0);
        action.setBackground(ZeroChillUi.rounded(
                this,
                primary ? Color.rgb(5, 27, 36) : Color.rgb(22, 25, 30),
                primary ? UiPalette.PRIMARY : ZeroChillUi.color(this, R.color.zc_edge),
                R.dimen.zc_radius_medium
        ));
        action.setFocusable(true);
        action.setClickable(true);
        ZeroChillMotion.installPressFeedback(action);
        return action;
    }

    private void beginClear(ArrayList<ZeroChillSocialRepository.Conversation> chosen) {
        if (chosen.isEmpty() || clearing) return;
        final String user = ZeroChillSessionStore.currentUserId(this);
        if (!user.equals(renderedUser)) return;
        clearing = true;
        ++requestGeneration; // A pre-clear inbox response cannot restore cleared rows.
        updateSelectionUi();
        adapter.notifyDataSetChanged();
        clearNext(chosen, 0, user, new ArrayList<>(), new ArrayList<>());
    }

    private void clearNext(
            ArrayList<ZeroChillSocialRepository.Conversation> chosen,
            int index,
            String user,
            ArrayList<String> cleared,
            ArrayList<String> failed
    ) {
        if (!user.equals(ZeroChillSessionStore.currentUserId(this)) || isFinishing() || isDestroyed()) {
            clearing = false;
            return;
        }
        if (index >= chosen.size()) {
            finishClearBatch(user, cleared, failed);
            return;
        }
        ZeroChillSocialRepository.Conversation item = chosen.get(index);
        String partner = item.profile.userId;
        conversationClearer.clear(this, partner, (ok, error) -> runOnUiThread(() -> {
            if (!user.equals(ZeroChillSessionStore.currentUserId(this)) || isFinishing() || isDestroyed()) {
                clearing = false;
                return;
            }
            if (error == null && Boolean.TRUE.equals(ok)) cleared.add(partner);
            else failed.add(partner);
            clearNext(chosen, index + 1, user, cleared, failed);
        }));
    }

    private void finishClearBatch(String user, ArrayList<String> cleared, ArrayList<String> failed) {
        if (!user.equals(ZeroChillSessionStore.currentUserId(this))) {
            clearing = false;
            return;
        }
        clearing = false;
        if (!cleared.isEmpty()) adapter.removePartners(cleared);
        selectedPartners.clear();
        selectedPartners.addAll(failed);
        progress.setVisibility(View.GONE);
        renderCounts();
        adapter.notifyDataSetChanged();

        if (!failed.isEmpty()) {
            String message = failed.size() == 1
                    ? "Couldn't remove 1 conversation."
                    : "Couldn't remove " + failed.size() + " conversations.";
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
        loadInbox(); // Includes any message sent after each server cutoff.
    }

    private Drawable conversationBackground(boolean selected) {
        if (!selected) return ZeroChillUi.panelGlass(this);
        return ZeroChillUi.rounded(
                this,
                Color.rgb(6, 15, 20),
                UiPalette.PRIMARY,
                R.dimen.zc_radius_large
        );
    }

    private Drawable selectionMarkBackground(boolean selected) {
        return ZeroChillUi.rounded(
                this,
                selected ? UiPalette.PRIMARY : Color.TRANSPARENT,
                UiPalette.PRIMARY,
                R.dimen.zc_radius_pill
        );
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
            Set<String> visible = new LinkedHashSet<>();
            for (ZeroChillSocialRepository.Conversation item : items) {
                if (item != null && item.profile != null) visible.add(item.profile.userId);
            }
            selectedPartners.retainAll(visible);
            notifyDataSetChanged();
        }

        void removePartners(List<String> partners) {
            if (partners == null || partners.isEmpty()) return;
            items.removeIf(row -> row != null && row.profile != null && partners.contains(row.profile.userId));
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

            TextView selection = BrowseUi.text(ZeroChillInboxActivity.this, "", 14, Color.BLACK);
            selection.setTypeface(null, android.graphics.Typeface.BOLD);
            selection.setGravity(Gravity.CENTER);
            selection.setVisibility(View.GONE);
            LinearLayout.LayoutParams selectionParams = new LinearLayout.LayoutParams(dp(26), dp(26));
            selectionParams.setMarginStart(dp(6));
            row.addView(selection, selectionParams);

            return new Holder(row, avatar, name, preview, time, unread, selection);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            ZeroChillSocialRepository.Conversation item = items.get(position);
            ZeroChillSocialRepository.PublicProfile profile = item.profile;
            String label = profile.displayName.isEmpty()
                    ? "@" + profile.username
                    : profile.displayName;
            boolean selecting = selectionMode();
            boolean selected = selectedPartners.contains(profile.userId);
            holder.name.setText(label);
            holder.preview.setText("@" + profile.username + "  ·  " + item.lastMessage.body);
            holder.time.setText(formatTime(item.lastMessage.createdAt));
            holder.unread.setVisibility(!selecting && item.unreadCount > 0 ? View.VISIBLE : View.GONE);
            holder.unread.setText(item.unreadCount > 99 ? "99+" : String.valueOf(item.unreadCount));
            holder.selection.setVisibility(selecting ? View.VISIBLE : View.GONE);
            holder.selection.setText(selected ? "✓" : "");
            holder.selection.setTextColor(selected ? Color.BLACK : UiPalette.PRIMARY);
            holder.selection.setBackground(selectionMarkBackground(selected));
            holder.itemView.setBackground(conversationBackground(selected));
            holder.itemView.setAlpha(selecting ? (selected ? 1f : 0.84f) : (item.unreadCount > 0 ? 1f : 0.82f));
            holder.itemView.setContentDescription(selecting
                    ? label + (selected ? ". Selected. Tap to deselect." : ". Tap to select.")
                    : label + ". " + item.lastMessage.body
                            + (item.unreadCount > 0 ? ". " + item.unreadCount + " unread." : "")
                            + ". Long press to select conversation.");
            holder.itemView.setOnClickListener(v -> {
                if (selectionMode()) toggleSelection(item);
                else open(item);
            });
            holder.itemView.setOnLongClickListener(v -> {
                toggleSelection(item);
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
            final TextView selection;

            Holder(
                    View itemView,
                    ImageView avatar,
                    TextView name,
                    TextView preview,
                    TextView time,
                    TextView unread,
                    TextView selection
            ) {
                super(itemView);
                this.avatar = avatar;
                this.name = name;
                this.preview = preview;
                this.time = time;
                this.unread = unread;
                this.selection = selection;
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
