package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** All starred creators, including favorites saved before the creator catalog existed. */
public final class CreatorsActivity extends Activity {
    private static final int REQUEST_PICK_AVATAR = 4106;
    // Temporary feature-branch fixtures for device-testing the badge UI. Remove before merge.
    private static final int[] TEST_UPDATE_BADGES = {1, 3, 12};

    private final java.util.Set<String> dismissedTestUpdateBadges = new java.util.HashSet<>();
    private EditText input;
    private TextView empty, count;
    private RecyclerView recycler;
    private CreatorGridAdapter adapter;
    private Parcelable pendingScroll;
    private ItemTouchHelper dragHelper;
    private CreatorGridAdapter.Holder dragged, hovered;
    private ArrayList<String> pendingAvatarKeys;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = BrowseUi.screen(this);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(8));
        header.addView(BrowseUi.action(this, "‹", "Back", v -> finish()), new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = BrowseUi.text(this, "Favorite creators", 20, Color.WHITE);
        title.setPadding(dp(12), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(BrowseUi.action(this, "+", "Find creators", v -> startActivity(SearchActivity.createBunkrSearch(this))),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header);
        input = new EditText(this);
        input.setHint("Search your favorite creators");
        input.setHintTextColor(BrowseUi.MUTED);
        input.setTextColor(Color.WHITE);
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(BrowseUi.rounded(this, BrowseUi.SURFACE, 14));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, dp(50));
        inputParams.setMargins(dp(12), 0, dp(12), dp(8));
        root.addView(input, inputParams);
        count = BrowseUi.text(this, "", 12, BrowseUi.MUTED);
        count.setPadding(dp(16), dp(4), dp(16), dp(8));
        root.addView(count);
        empty = BrowseUi.text(this, "", 15, BrowseUi.MUTED);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(24), dp(32), dp(24), dp(24));
        root.addView(empty);
        recycler = new RecyclerView(this);
        recycler.setLayoutManager(new GridLayoutManager(this, 3));
        recycler.setItemAnimator(null);
        recycler.setClipToPadding(false);
        recycler.setPadding(dp(8), dp(2), dp(8), dp(20));
        adapter = new CreatorGridAdapter();
        recycler.setAdapter(adapter);
        dragHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
            @Override public boolean isLongPressDragEnabled() { return true; }
            @Override public boolean onMove(RecyclerView list, RecyclerView.ViewHolder source,
                                            RecyclerView.ViewHolder target) { return false; }
            @Override public void onSwiped(RecyclerView.ViewHolder holder, int direction) { }
            @Override public void onSelectedChanged(RecyclerView.ViewHolder selected, int state) {
                super.onSelectedChanged(selected, state);
                if (state == ItemTouchHelper.ACTION_STATE_DRAG && selected instanceof CreatorGridAdapter.Holder) {
                    dragged = (CreatorGridAdapter.Holder) selected;
                    dragged.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    dragged.itemView.setScaleX(1.05f); dragged.itemView.setScaleY(1.05f);
                    dragged.itemView.setElevation(dp(12));
                }
            }
            @Override public void onChildDraw(android.graphics.Canvas canvas, RecyclerView list,
                                              RecyclerView.ViewHolder selected, float dx, float dy,
                                              int state, boolean active) {
                super.onChildDraw(canvas, list, selected, dx, dy, state, active);
                if (state != ItemTouchHelper.ACTION_STATE_DRAG || !active || dragged == null) return;
                float x = selected.itemView.getLeft() + dx + selected.itemView.getWidth() / 2f;
                float y = selected.itemView.getTop() + dy + selected.itemView.getHeight() / 2f;
                CreatorGridAdapter.Holder next = null;
                for (int i = 0; i < list.getChildCount(); i++) {
                    View child = list.getChildAt(i);
                    if (child == selected.itemView || x < child.getLeft() || x > child.getRight()
                            || y < child.getTop() || y > child.getBottom()) continue;
                    RecyclerView.ViewHolder holder = list.getChildViewHolder(child);
                    if (holder instanceof CreatorGridAdapter.Holder) next = (CreatorGridAdapter.Holder) holder;
                    break;
                }
                if (hovered != next) {
                    if (hovered != null) hovered.itemView.setBackground(null);
                    hovered = next;
                    if (hovered != null) hovered.itemView.setBackground(
                            BrowseUi.rounded(CreatorsActivity.this, Color.rgb(18, 40, 52), 16));
                }
            }
            @Override public void clearView(RecyclerView list, RecyclerView.ViewHolder selected) {
                CreatorCatalog.FavoriteGroup from = dragged == null ? null : dragged.bound;
                CreatorCatalog.FavoriteGroup to = hovered == null ? null : hovered.bound;
                if (hovered != null) hovered.itemView.setBackground(null);
                selected.itemView.setScaleX(1f); selected.itemView.setScaleY(1f);
                selected.itemView.setElevation(0f);
                dragged = hovered = null;
                super.clearView(list, selected);
                if (from != null && to != null && from != to)
                    list.post(() -> confirmMerge(from, to));
            }
        });
        dragHelper.attachToRecyclerView(recycler);
        root.addView(recycler, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        input.addTextChangedListener(BrowseUi.onText(value -> render()));
        if (state != null) {
            input.setText(state.getString("query", ""));
            pendingScroll = state.getParcelable("scroll");
            pendingAvatarKeys = state.getStringArrayList("pending_avatar_keys");
        }
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void render() {
        List<CreatorCatalog.FavoriteGroup> creators = CreatorCatalog.favoriteGroups(this);
        String query = input.getText().toString();
        creators.removeIf(group -> CreatorNameMatcher.rank(group.item.title, query) == Integer.MAX_VALUE
                && group.members.values().stream().noneMatch(member ->
                CreatorNameMatcher.rank(member.title, query) != Integer.MAX_VALUE));
        adapter.replace(creators);
        count.setText(creators.size() + (creators.size() == 1 ? " creator · A–Z" : " creators · A–Z"));
        empty.setVisibility(creators.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(input.length() == 0 ? "No favorite creators yet\n\nTap + to find creators, then tap ☆ to save them here."
                : "No favorite creators match your search.");
        if (pendingScroll != null) {
            recycler.getLayoutManager().onRestoreInstanceState(pendingScroll);
            pendingScroll = null;
        }
    }

    private void openCreator(CreatorCatalog.FavoriteGroup group) {
        if (group == null || group.item == null) return;
        UpdateInboxStore.markCreatorRead(this, creatorAliases(group));
        dismissedTestUpdateBadges.add(creatorBadgeKey(group));
        BrowseUi.hideKeyboard(this, input);
        CreatorGallerySpec spec = CreatorGallerySpec.from(group);
        String sessionId = CreatorGalleryPreloader.sessionId(this, spec.cacheKey);
        if (sessionId.isEmpty() && spec.grouped) {
            sessionId = CreatorGalleryPreloader.composeInMemoryMergedSession(this, spec);
        }
        startActivity(NativeFeedBrowserActivity.createCreatorGallery(
                this,
                spec.item.title,
                spec.query,
                spec.profileHint,
                sessionId,
                spec.cacheKey,
                spec.seedNames,
                spec.seedUrls,
                spec.seedImages
        ));
    }

    private void confirmMerge(CreatorCatalog.FavoriteGroup source, CreatorCatalog.FavoriteGroup target) {
        if (isFinishing() || isDestroyed()) return;
        java.util.LinkedHashMap<String, NativeContentItem> members = new java.util.LinkedHashMap<>(target.members);
        members.putAll(source.members);
        String name = target.members.keySet().iterator().next();
        for (Map.Entry<String, NativeContentItem> entry : target.members.entrySet())
            if (entry.getValue().title.equals(target.item.title)) { name = entry.getKey(); break; }
        String avatar = name;
        if (target.item.imageUrl.isEmpty() || members.get(name).imageUrl.isEmpty()) {
            for (Map.Entry<String, NativeContentItem> entry : members.entrySet())
                if (!entry.getValue().imageUrl.isEmpty()) { avatar = entry.getKey(); break; }
        }
        CreatorMergeSheet.show(this, "Merge creators", members, name, avatar, "Merge", (chosenName, chosenAvatar) -> {
            if (ManualCreatorMergeStore.merge(this, source.relationshipKeys, target.relationshipKeys,
                    chosenName, chosenAvatar)) render();
        });
    }

    private void showMenu(View anchor, CreatorCatalog.FavoriteGroup group) {
        PopupMenu menu = new PopupMenu(this, anchor);
        if (group.manual) {
            menu.getMenu().add("Merged creators").setOnMenuItemClickListener(item -> {
                CreatorMergeSheet.showMembers(this, group.members);
                return true;
            });
            menu.getMenu().add("Change primary").setOnMenuItemClickListener(item -> {
                String name = group.members.keySet().iterator().next();
                String avatar = name;
                for (ManualCreatorMergeStore.Group relation : ManualCreatorMergeStore.load(this))
                    if (relation.keys.equals(group.relationshipKeys)) {
                        name = relation.nameKey; avatar = relation.avatarKey; break;
                    }
                CreatorMergeSheet.show(this, "Change primary", group.members, name, avatar,
                        "Save", (chosenName, chosenAvatar) -> {
                            if (ManualCreatorMergeStore.changePrimary(this, group.relationshipKeys,
                                    chosenName, chosenAvatar)) render();
                        });
                return true;
            });
            menu.getMenu().add("Unmerge").setOnMenuItemClickListener(item -> {
                if (ManualCreatorMergeStore.unmerge(this, group.relationshipKeys)) render();
                return true;
            });
        }
        menu.getMenu().add("Change avatar").setOnMenuItemClickListener(item -> {
            pickAvatar(group);
            return true;
        });
        if (CreatorAvatarOverrideStore.has(this, group.relationshipKeys)) {
            menu.getMenu().add("Use default avatar").setOnMenuItemClickListener(item -> {
                if (CreatorAvatarOverrideStore.clear(this, group.relationshipKeys)) render();
                return true;
            });
        }
        menu.getMenu().add("Remove from favorites").setOnMenuItemClickListener(item -> {
            removeGroup(group); return true;
        });
        menu.show();
    }

    private void pickAvatar(CreatorCatalog.FavoriteGroup group) {
        if (group == null || group.item == null || group.relationshipKeys.isEmpty()) return;
        BrowseUi.hideKeyboard(this, input);
        pendingAvatarKeys = new ArrayList<>(group.relationshipKeys);
        CreatorGallerySpec spec = CreatorGallerySpec.from(group);
        String sessionId = CreatorGalleryPreloader.sessionId(this, spec.cacheKey);
        if (sessionId.isEmpty() && spec.grouped) {
            sessionId = CreatorGalleryPreloader.composeInMemoryMergedSession(this, spec);
        }
        Intent intent = NativeFeedBrowserActivity.createCreatorAvatarPicker(
                this,
                spec.item.title,
                spec.query,
                spec.profileHint,
                sessionId,
                spec.cacheKey,
                spec.seedNames,
                spec.seedUrls,
                spec.seedImages
        );
        startActivityForResult(intent, REQUEST_PICK_AVATAR);
    }

    private void removeGroup(CreatorCatalog.FavoriteGroup group) {
        // Remove just the displayed primary and its reviewed aliases, never other manual members.
        CreatorFavoriteStore.toggle(this, group.item);
        render();
    }

    private List<String> creatorAliases(CreatorCatalog.FavoriteGroup group) {
        ArrayList<String> aliases = new ArrayList<>();
        if (group == null) return aliases;
        aliases.addAll(group.relationshipKeys);
        if (group.item != null) {
            aliases.add(group.item.title);
            aliases.add(group.item.searchQuery);
        }
        for (Map.Entry<String, NativeContentItem> member : group.members.entrySet()) {
            aliases.add(member.getKey());
            NativeContentItem item = member.getValue();
            if (item != null) {
                aliases.add(item.title);
                aliases.add(item.searchQuery);
            }
        }
        return aliases;
    }

    private String creatorBadgeKey(CreatorCatalog.FavoriteGroup group) {
        ArrayList<String> keys = new ArrayList<>(group.relationshipKeys);
        java.util.Collections.sort(keys);
        return keys.toString();
    }

    private int creatorUpdateCount(CreatorCatalog.FavoriteGroup group, int position) {
        int real = UpdateInboxStore.unreadCreatorContentCount(this, creatorAliases(group));
        if (real > 0) return real;

        // Temporary feature-branch fixture so the signed device-test APK can exercise 1/3/12 badges
        // without waiting for live creator updates. This fallback must be removed before merge.
        if (position >= 0 && position < TEST_UPDATE_BADGES.length
                && !dismissedTestUpdateBadges.contains(creatorBadgeKey(group))) {
            return TEST_UPDATE_BADGES[position];
        }
        return 0;
    }

    private final class CreatorGridAdapter
            extends RecyclerView.Adapter<CreatorGridAdapter.Holder> {
        private final List<CreatorCatalog.FavoriteGroup> items = new ArrayList<>();

        void replace(List<CreatorCatalog.FavoriteGroup> next) {
            items.clear();
            items.addAll(next);
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout wrapper = new LinearLayout(CreatorsActivity.this);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.setGravity(Gravity.CENTER_HORIZONTAL);
            wrapper.setPadding(dp(4), dp(7), dp(4), dp(8));
            RecyclerView.LayoutParams params =
                    new RecyclerView.LayoutParams(-1, -2);
            params.setMargins(dp(2), dp(2), dp(2), dp(4));
            wrapper.setLayoutParams(params);
            wrapper.setFocusable(true);
            wrapper.setClickable(true);
            ZeroChillMotion.installPressFeedback(wrapper);

            FrameLayout avatarFrame = new FrameLayout(CreatorsActivity.this);
            wrapper.addView(avatarFrame, new LinearLayout.LayoutParams(dp(86), dp(86)));

            MaterialCardView avatarCard = new MaterialCardView(CreatorsActivity.this);
            avatarCard.setRadius(dp(43));
            avatarCard.setCardElevation(0f);
            avatarCard.setStrokeWidth(0);
            avatarCard.setCardBackgroundColor(Color.rgb(19, 23, 27));
            avatarFrame.addView(avatarCard, new FrameLayout.LayoutParams(-1, -1));

            ImageView avatar = new ImageView(CreatorsActivity.this);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.setBackgroundColor(Color.rgb(19, 23, 27));
            avatarCard.addView(avatar, new MaterialCardView.LayoutParams(-1, -1));

            TextView updateBadge = BrowseUi.text(CreatorsActivity.this, "", 11, Color.BLACK);
            updateBadge.setTypeface(null, android.graphics.Typeface.BOLD);
            updateBadge.setGravity(Gravity.CENTER);
            updateBadge.setMinWidth(dp(26));
            updateBadge.setPadding(dp(7), 0, dp(7), 0);
            updateBadge.setBackground(BrowseUi.rounded(
                    CreatorsActivity.this,
                    UiPalette.PRIMARY,
                    13
            ));
            updateBadge.setVisibility(View.GONE);
            FrameLayout.LayoutParams updateBadgeParams =
                    new FrameLayout.LayoutParams(-2, dp(26), Gravity.TOP | Gravity.END);
            updateBadgeParams.setMargins(0, dp(2), dp(2), 0);
            avatarFrame.addView(updateBadge, updateBadgeParams);

            TextView more = BrowseUi.text(CreatorsActivity.this, "⋮", 23, Color.WHITE);
            more.setGravity(Gravity.CENTER);
            more.setBackground(BrowseUi.rounded(CreatorsActivity.this, Color.argb(190, 0, 0, 0), 14));
            FrameLayout.LayoutParams moreParams = new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.TOP | Gravity.START);
            avatarFrame.addView(more, moreParams);

            TextView name = BrowseUi.text(CreatorsActivity.this, "", 13, Color.WHITE);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nameParams =
                    new LinearLayout.LayoutParams(-1, -2);
            nameParams.setMargins(dp(2), dp(7), dp(2), 0);
            wrapper.addView(name, nameParams);

            return new Holder(wrapper, avatar, name, updateBadge, more);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            CreatorCatalog.FavoriteGroup group = items.get(position);
            NativeContentItem item = group.item;
            holder.bound = group;
            holder.name.setText(item.title);
            int unread = creatorUpdateCount(group, position);
            if (unread > 0) {
                holder.updateBadge.setText(unread > 99 ? "99+" : String.valueOf(unread));
                holder.updateBadge.setContentDescription(
                        unread + (unread == 1 ? " new item from " : " new items from ") + item.title
                );
                holder.updateBadge.setVisibility(View.VISIBLE);
            } else {
                holder.updateBadge.setText("");
                holder.updateBadge.setContentDescription(null);
                holder.updateBadge.setVisibility(View.GONE);
            }
            holder.itemView.setContentDescription(
                    unread > 0
                            ? "Open " + item.title + ". " + unread
                            + (unread == 1 ? " new item." : " new items.")
                            : "Open " + item.title
            );

            holder.itemView.setOnClickListener(v -> openCreator(group));
            holder.more.setContentDescription("Creator options for " + item.title);
            holder.more.setOnClickListener(v -> showMenu(v, group));

            Glide.with(holder.avatar).clear(holder.avatar);
            holder.avatar.setImageDrawable(new ColorDrawable(Color.rgb(19, 23, 27)));
            if (!item.imageUrl.isEmpty()) {
                GlideUrl url = new GlideUrl(
                        item.imageUrl,
                        new LazyHeaders.Builder()
                                .addHeader(
                                        "Referer",
                                        item.uploader.isEmpty() ? item.url : item.uploader
                                )
                                .addHeader(
                                        "User-Agent",
                                        "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/139.0 Mobile Safari/537.36"
                                )
                                .build()
                );
                com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> network =
                        Glide.with(holder.avatar)
                                .load(url)
                                .circleCrop()
                                .dontAnimate()
                                .placeholder(new ColorDrawable(Color.rgb(19, 23, 27)))
                                .error(R.drawable.ic_more_account);
                if (group.customAvatar) {
                    network.into(holder.avatar);
                } else {
                    Glide.with(holder.avatar)
                            .load(url)
                            .onlyRetrieveFromCache(true)
                            .circleCrop()
                            .dontAnimate()
                            .placeholder(new ColorDrawable(Color.rgb(19, 23, 27)))
                            .error(network)
                            .into(holder.avatar);
                }
            } else {
                holder.avatar.setImageResource(R.drawable.ic_more_account);
            }
        }

        @Override
        public void onViewAttachedToWindow(Holder holder) {
            super.onViewAttachedToWindow(holder);
            CreatorCatalog.FavoriteGroup group = holder.bound;
            if (group == null || group.members.size() < 2) return;
            CreatorGalleryPreloader.warm(
                    CreatorsActivity.this,
                    CreatorGallerySpec.from(group),
                    CreatorGalleryPreloader.PRIORITY_NORMAL
            );
        }

        @Override
        public void onViewDetachedFromWindow(Holder holder) {
            CreatorCatalog.FavoriteGroup group = holder.bound;
            if (group != null && group.members.size() > 1) {
                CreatorGalleryPreloader.cancelQueued(CreatorGallerySpec.from(group));
            }
            super.onViewDetachedFromWindow(holder);
        }

        @Override
        public void onViewRecycled(Holder holder) {
            holder.bound = null;
            Glide.with(holder.avatar).clear(holder.avatar);
            super.onViewRecycled(holder);
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView avatar;
            final TextView name;
            final TextView updateBadge;
            final TextView more;
            CreatorCatalog.FavoriteGroup bound;

            Holder(View itemView, ImageView avatar, TextView name, TextView updateBadge, TextView more) {
                super(itemView);
                this.avatar = avatar;
                this.name = name;
                this.updateBadge = updateBadge;
                this.more = more;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_AVATAR) return;
        ArrayList<String> keys = pendingAvatarKeys;
        pendingAvatarKeys = null;
        if (resultCode != RESULT_OK || data == null || keys == null || keys.isEmpty()) return;
        String imageUrl = data.getStringExtra(NativeFeedBrowserActivity.EXTRA_PICKED_AVATAR_URL);
        String referer = data.getStringExtra(NativeFeedBrowserActivity.EXTRA_PICKED_AVATAR_REFERER);
        if (CreatorAvatarOverrideStore.save(
                this,
                new java.util.LinkedHashSet<>(keys),
                imageUrl,
                referer
        )) {
            render();
            android.widget.Toast.makeText(this, "Avatar updated.", android.widget.Toast.LENGTH_SHORT)
                    .show();
        }
    }

    @Override protected void onResume() { super.onResume(); render(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("query", input.getText().toString());
        state.putParcelable("scroll", recycler.getLayoutManager().onSaveInstanceState());
        if (pendingAvatarKeys != null) {
            state.putStringArrayList("pending_avatar_keys", pendingAvatarKeys);
        }
        super.onSaveInstanceState(state);
    }
    private int dp(int value) { return BrowseUi.dp(this, value); }
}
