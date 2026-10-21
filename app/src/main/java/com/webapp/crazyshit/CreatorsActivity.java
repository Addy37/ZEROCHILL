package com.webapp.crazyshit;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** All starred creators, including favorites saved before the creator catalog existed. */
public final class CreatorsActivity extends androidx.activity.ComponentActivity {
    private static final int REQUEST_PICK_AVATAR = 4106;

    private LibraryCompose presentation;
    private RecyclerView recycler;
    private CreatorGridAdapter adapter;
    private Parcelable pendingScroll;
    private ArrayList<String> pendingAvatarKeys;
    private boolean badgesDirty;
    private boolean stopped;
    private android.content.SharedPreferences catalogPreferences, favoritePreferences;
    private final Runnable creatorRefresh = () -> { if (!stopped) render(); };
    private final SharedPreferences.OnSharedPreferenceChangeListener creatorMetadataListener = (prefs, key) -> {
        recycler.removeCallbacks(creatorRefresh);
        recycler.post(creatorRefresh);
        if (prefs == favoritePreferences && !stopped) FavoriteCreatorArtworkHydrator.schedule(this);
    };
    private int scrollDirection = 1;
    private boolean prefetchPosted;
    private final Runnable avatarPrefetch = () -> {
        prefetchPosted = false;
        if (!stopped && adapter != null) adapter.prefetchAvatars(scrollDirection);
    };
    private final Runnable badgeRefresh = () -> {
        if (!badgesDirty || stopped
                || recycler.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) return;
        if (recycler.isComputingLayout()) {
            recycler.postOnAnimation(this.badgeRefresh);
            return;
        }
        badgesDirty = false;
        adapter.refreshBadges();
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener inboxListener = (prefs, key) -> {
        badgesDirty = true;
        recycler.removeCallbacks(badgeRefresh);
        recycler.post(badgeRefresh);
    };

    private void scheduleAvatarPrefetch() {
        if (stopped || prefetchPosted) return;
        prefetchPosted = true;
        recycler.postOnAnimation(avatarPrefetch);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ZeroChillUi.applySystemBars(this);
        presentation = new LibraryCompose(this, "Favorite creators", value -> render());
        presentation.setActionLabel("Find creators");
        presentation.setAction(() -> startActivity(SearchActivity.createBunkrSearch(this)));
        recycler = new RecyclerView(this);
        GridLayoutManager grid = new GridLayoutManager(this, creatorColumns());
        grid.setInitialPrefetchItemCount(6);
        recycler.setLayoutManager(grid);
        // The grid fills a weighted, fixed viewport; item count does not size the RecyclerView.
        recycler.setHasFixedSize(true);
        recycler.setItemViewCacheSize(6);
        recycler.getRecycledViewPool().setMaxRecycledViews(0, 12);
        recycler.setItemAnimator(null);
        recycler.setClipToPadding(false);
        recycler.setPadding(dp(8), dp(2), dp(8), dp(20));
        adapter = new CreatorGridAdapter();
        recycler.setAdapter(adapter);
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView view, int dx, int dy) {
                if (dy != 0) scrollDirection = dy > 0 ? 1 : -1;
                scheduleAvatarPrefetch();
            }
            @Override public void onScrollStateChanged(RecyclerView view, int state) {
                scheduleAvatarPrefetch();
                if (state == RecyclerView.SCROLL_STATE_IDLE) view.post(badgeRefresh);
            }
        });
        presentation.install(recycler);
        if (state != null) {
            presentation.setQuery(state.getString("query", ""));
            pendingScroll = state.getParcelable("scroll");
            pendingAvatarKeys = state.getStringArrayList("pending_avatar_keys");
        }
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private int creatorColumns() {
        android.content.res.Configuration config = getResources().getConfiguration();
        return config.screenWidthDp >= 600 && config.fontScale < 1.5f ? 2 : 1;
    }

    private void render() {
        List<CreatorCatalog.FavoriteGroup> creators = CreatorCatalog.favoriteGroups(this);
        String query = presentation.getQuery();
        creators.removeIf(group -> CreatorNameMatcher.rank(group.item.title, query) == Integer.MAX_VALUE
                && group.members.values().stream().noneMatch(member ->
                CreatorNameMatcher.rank(member.title, query) != Integer.MAX_VALUE));
        adapter.replace(creators);
        presentation.setSummary(creators.size() + (creators.size() == 1 ? " creator · A–Z" : " creators · A–Z")
                + "\nHold a creator for options or merge");
        presentation.setEmptyMessage(!creators.isEmpty() ? "" : presentation.getQuery().isEmpty()
                ? "No favorite creators yet\n\nTap Find creators, then tap ☆ to save them here."
                : "No favorite creators match your search.");
        if (pendingScroll != null) {
            recycler.getLayoutManager().onRestoreInstanceState(pendingScroll);
            pendingScroll = null;
        }
    }

    private void openCreator(CreatorCatalog.FavoriteGroup group) {
        if (group == null || group.item == null) return;
        getWindow().getDecorView().clearFocus();
        ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
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

    private ZeroChillMenu creatorMenu(View anchor, CreatorCatalog.FavoriteGroup group) {
        ZeroChillMenu menu = new ZeroChillMenu(this, anchor);
        android.view.SubMenu merge = menu.getMenu().addSubMenu("Merge with…");
        for (CreatorCatalog.FavoriteGroup target : CreatorCatalog.favoriteGroups(this)) {
            if (!java.util.Collections.disjoint(group.relationshipKeys, target.relationshipKeys)) continue;
            merge.add(target.item.title).setOnMenuItemClickListener(item -> {
                confirmMerge(group, target); return true;
            });
        }
        merge.getItem().setEnabled(merge.size() > 0);
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
        return menu;
    }

    private void pickAvatar(CreatorCatalog.FavoriteGroup group) {
        if (group == null || group.item == null || group.relationshipKeys.isEmpty()) return;
        getWindow().getDecorView().clearFocus();
        ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
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

    private final class CreatorGridAdapter
            extends RecyclerView.Adapter<CreatorGridAdapter.Holder> {
        private final List<Card> cards = new ArrayList<>();
        private final Map<Integer, CustomTarget<Drawable>> prefetchTargets = new HashMap<>();
        private final int avatarSide = dp(56);

        final class Card {
            final CreatorCatalog.FavoriteGroup group;
            final List<String> aliases;
            final CreatorAvatarOverrideStore.Override avatarOverride;
            final GlideUrl avatarUrl;
            final int decodeSide;
            int unread;

            Card(CreatorCatalog.FavoriteGroup group,
                    List<CreatorAvatarOverrideStore.Override> overrides, Map<String, Integer> counts) {
                this.group = group;
                aliases = creatorAliases(group);
                avatarOverride = group.customAvatar
                        ? CreatorAvatarOverrideStore.find(overrides, group.relationshipKeys) : null;
                // Retain the full aspect ratio for focus/zoom. Decode only the pixels the crop needs.
                decodeSide = Math.round(avatarSide * (avatarOverride == null ? 1f : avatarOverride.zoom));
                NativeContentItem item = group.item;
                avatarUrl = item.imageUrl.isEmpty() ? null : new GlideUrl(item.imageUrl,
                        new LazyHeaders.Builder()
                                .addHeader("Referer", item.uploader.isEmpty() ? item.url : item.uploader)
                                .addHeader("User-Agent",
                                        "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/139.0 Mobile Safari/537.36")
                                .build());
                unread = UpdateInboxStore.unreadCreatorContentCount(counts, aliases);
            }
        }

        void replace(List<CreatorCatalog.FavoriteGroup> next) {
            clearPrefetch();
            cards.clear();
            List<CreatorAvatarOverrideStore.Override> overrides =
                    CreatorAvatarOverrideStore.load(CreatorsActivity.this);
            Map<String, Integer> counts = UpdateInboxStore.unreadCreatorContentCounts(CreatorsActivity.this);
            for (CreatorCatalog.FavoriteGroup group : next) cards.add(new Card(group, overrides, counts));
            badgesDirty = false;
            notifyDataSetChanged();
            scheduleAvatarPrefetch();
        }

        void refreshBadges() {
            Map<String, Integer> counts = UpdateInboxStore.unreadCreatorContentCounts(CreatorsActivity.this);
            for (int i = 0; i < cards.size(); i++) {
                Card card = cards.get(i);
                int unread = UpdateInboxStore.unreadCreatorContentCount(counts, card.aliases);
                if (card.unread != unread) {
                    card.unread = unread;
                    notifyItemChanged(i, "badges");
                }
            }
        }

        private RequestBuilder<Drawable> avatarRequest(Card card) {
            return Glide.with(CreatorsActivity.this).load(card.avatarUrl)
                    .override(card.decodeSide, card.decodeSide)
                    .downsample(AVATAR_DOWNSAMPLE)
                    .dontTransform()
                    .dontAnimate();
        }

        void prefetchAvatars(int direction) {
            GridLayoutManager grid = (GridLayoutManager) recycler.getLayoutManager();
            if (grid == null) return;
            int first = grid.findFirstVisibleItemPosition();
            int last = grid.findLastVisibleItemPosition();
            int[] upcoming = avatarPrefetchPositions(first, last, cards.size(), grid.getSpanCount(), direction);
            java.util.Set<Integer> wanted = new java.util.HashSet<>();
            for (int position : upcoming) wanted.add(position);
            // Cancel requests that are now behind the user, including on direction reversals.
            for (int position : new ArrayList<>(prefetchTargets.keySet())) {
                if (!wanted.contains(position)) Glide.with(CreatorsActivity.this)
                        .clear(prefetchTargets.remove(position));
            }
            for (int position : upcoming) {
                Card card = cards.get(position);
                if (card.avatarUrl == null || prefetchTargets.containsKey(position)) continue;
                CustomTarget<Drawable> target = new CustomTarget<Drawable>(card.decodeSide, card.decodeSide) {
                    @Override public void onResourceReady(Drawable drawable, Transition<? super Drawable> transition) { }
                    @Override public void onLoadCleared(Drawable placeholder) { }
                };
                prefetchTargets.put(position, target);
                avatarRequest(card).priority(Priority.LOW).into(target);
            }
        }

        void clearPrefetch() {
            for (CustomTarget<Drawable> target : prefetchTargets.values())
                Glide.with(CreatorsActivity.this).clear(target);
            prefetchTargets.clear();
        }

        @Override
        public int getItemCount() {
            return cards.size();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            MaterialCardView avatarCard = new MaterialCardView(CreatorsActivity.this);
            avatarCard.setRadius(dp(28));
            avatarCard.setCardElevation(0f);
            avatarCard.setStrokeWidth(0);
            avatarCard.setCardBackgroundColor(Color.rgb(19, 23, 27));
            avatarCard.setClipToOutline(true);
            CreatorAvatarImageView avatar = new CreatorAvatarImageView(CreatorsActivity.this);
            avatarCard.addView(avatar, new MaterialCardView.LayoutParams(-1, -1));
            CreatorCardCompose compose = new CreatorCardCompose(CreatorsActivity.this, avatarCard);
            View wrapper = compose.getView();
            wrapper.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new Holder(wrapper, avatar, compose);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            Card card = cards.get(position);
            CreatorCatalog.FavoriteGroup group = card.group;
            NativeContentItem item = group.item;
            holder.bound = group;
            bindBadges(holder, card);
            holder.itemView.setOnClickListener(v -> openCreator(group));

            Glide.with(holder.avatar).clear(holder.avatar);
            CreatorAvatarOverrideStore.Override avatarOverride = card.avatarOverride;
            if (avatarOverride != null) {
                holder.avatar.setAvatarCrop(avatarOverride.focusX, avatarOverride.focusY, avatarOverride.zoom);
            } else {
                holder.avatar.clearAvatarCrop();
            }
            if (card.avatarUrl != null) {
                // MaterialCardView clips the original drawable to a circle. No bitmap circle/crop copy.
                avatarRequest(card)
                        .priority(Priority.HIGH)
                        .transition(ThumbnailFades.avatar())
                        .placeholder(R.drawable.creator_avatar_fallback)
                        .error(R.drawable.creator_avatar_fallback)
                        .listener(new com.bumptech.glide.request.RequestListener<Drawable>() {
                            @Override public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException error,
                                    Object model, com.bumptech.glide.request.target.Target<Drawable> target,
                                    boolean first) {
                                // A failed custom image must not apply its photo crop to the mascot.
                                holder.avatar.clearAvatarCrop();
                                return false;
                            }
                            @Override public boolean onResourceReady(Drawable resource, Object model,
                                    com.bumptech.glide.request.target.Target<Drawable> target,
                                    com.bumptech.glide.load.DataSource source, boolean first) { return false; }
                        })
                        .into(holder.avatar);
            } else {
                holder.avatar.setImageResource(R.drawable.creator_avatar_fallback);
            }
        }

        private void bindBadges(Holder holder, Card card) {
            CreatorCatalog.FavoriteGroup group = card.group;
            holder.compose.bind(String.join("|", new java.util.TreeSet<>(group.relationshipKeys)),
                    group.item.title, card.unread, CreatorMergeUi.isMerged(group) ? group.members.size() : 1,
                    () -> openCreator(group), () -> CreatorMergeUi.showMembers(CreatorsActivity.this, group),
                    () -> creatorMenu(holder.itemView, group));
            holder.itemView.setContentDescription(card.unread > 0
                    ? "Open " + group.item.title + ". " + card.unread + " new items."
                    : "Open " + group.item.title);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position, List<Object> payloads) {
            if (!payloads.isEmpty()) bindBadges(holder, cards.get(position));
            else onBindViewHolder(holder, position);
        }

        @Override
        public void onViewRecycled(Holder holder) {
            holder.bound = null;
            holder.compose.clear();
            Glide.with(holder.avatar).clear(holder.avatar);
            super.onViewRecycled(holder);
        }

        final class Holder extends RecyclerView.ViewHolder {
            final CreatorAvatarImageView avatar;
            final CreatorCardCompose compose;
            CreatorCatalog.FavoriteGroup bound;

            Holder(View itemView, CreatorAvatarImageView avatar, CreatorCardCompose compose) {
                super(itemView);
                this.avatar = avatar;
                this.compose = compose;
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
        float focusX = data.getFloatExtra(
                NativeFeedBrowserActivity.EXTRA_PICKED_AVATAR_FOCUS_X,
                CreatorAvatarOverrideStore.DEFAULT_FOCUS
        );
        float focusY = data.getFloatExtra(
                NativeFeedBrowserActivity.EXTRA_PICKED_AVATAR_FOCUS_Y,
                CreatorAvatarOverrideStore.DEFAULT_FOCUS
        );
        float zoom = data.getFloatExtra(
                NativeFeedBrowserActivity.EXTRA_PICKED_AVATAR_ZOOM,
                CreatorAvatarOverrideStore.DEFAULT_ZOOM
        );
        if (CreatorAvatarOverrideStore.save(
                this,
                new java.util.LinkedHashSet<>(keys),
                imageUrl,
                referer,
                focusX,
                focusY,
                zoom
        )) {
            render();
            ZeroChillToast.makeText(this, "Avatar updated.", ZeroChillToast.LENGTH_SHORT)
                    .show();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        stopped = false;
        catalogPreferences = getSharedPreferences(CreatorCatalog.PREFS, MODE_PRIVATE);
        favoritePreferences = getSharedPreferences("creator_favorites", MODE_PRIVATE);
        catalogPreferences.registerOnSharedPreferenceChangeListener(creatorMetadataListener);
        favoritePreferences.registerOnSharedPreferenceChangeListener(creatorMetadataListener);
        FavoriteCreatorArtworkHydrator.schedule(this);
        UpdateInboxStore.registerChangeListener(this, inboxListener);
        render();
    }
    @Override protected void onStop() {
        stopped = true;
        catalogPreferences.unregisterOnSharedPreferenceChangeListener(creatorMetadataListener);
        favoritePreferences.unregisterOnSharedPreferenceChangeListener(creatorMetadataListener);
        recycler.removeCallbacks(creatorRefresh);
        UpdateInboxStore.unregisterChangeListener(this, inboxListener);
        recycler.removeCallbacks(avatarPrefetch);
        recycler.removeCallbacks(badgeRefresh);
        prefetchPosted = false;
        adapter.clearPrefetch();
        super.onStop();
    }

    /** Two rows ahead of the viewport, never the entire favorite catalog. */
    static int[] avatarPrefetchPositions(int first, int last, int count, int spans, int direction) {
        if (first < 0 || last < first || count <= 0 || spans <= 0) return new int[0];
        int start = direction >= 0 ? last + 1 : first - 1;
        int step = direction >= 0 ? 1 : -1;
        ArrayList<Integer> positions = new ArrayList<>();
        for (int position = start; position >= 0 && position < count && positions.size() < spans * 2;
                position += step) positions.add(position);
        int[] result = new int[positions.size()];
        for (int i = 0; i < result.length; i++) result[i] = positions.get(i);
        return result;
    }

    static final DownsampleStrategy AVATAR_DOWNSAMPLE = new DownsampleStrategy() {
        @Override public float getScaleFactor(int sourceWidth, int sourceHeight, int width, int height) {
            return Math.min(1f, DownsampleStrategy.CENTER_OUTSIDE
                    .getScaleFactor(sourceWidth, sourceHeight, width, height));
        }
        @Override public SampleSizeRounding getSampleSizeRounding(
                int sourceWidth, int sourceHeight, int width, int height) {
            return SampleSizeRounding.QUALITY;
        }
    };
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("query", presentation.getQuery());
        state.putParcelable("scroll", recycler.getLayoutManager().onSaveInstanceState());
        if (pendingAvatarKeys != null) {
            state.putStringArrayList("pending_avatar_keys", pendingAvatarKeys);
        }
        super.onSaveInstanceState(state);
    }
    private int dp(int value) { return BrowseUi.dp(this, value); }
}
