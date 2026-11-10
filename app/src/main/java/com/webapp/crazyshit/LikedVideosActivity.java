package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.GridLayoutManager;
import com.google.android.material.card.MaterialCardView;
import java.util.HashMap;
import java.util.Map;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lazy, account-scoped collection. Network refresh and pagination require an explicit tap. */
public final class LikedVideosActivity extends Activity {
    private final ExecutorService disk = Executors.newSingleThreadExecutor();
    private final List<NativeContentItem> items = new ArrayList<>();
    private final LikesAdapter adapter = new LikesAdapter();
    private RecyclerView recycler;
    private GridLayoutManager galleryLayout;
    private final Map<String, Boolean> portraitLikedVideos = new HashMap<>();
    private TextView status, refresh, more;
    private String owner = "";
    private boolean loading;
    private int generation, nextOffset, unavailable;
    private boolean hasMore;
    private android.os.Parcelable restoredScroll;
    private final Runnable sessionChanged = this::checkAccount;
    private final SharedPreferences.OnSharedPreferenceChangeListener sessionListener = (prefs, key) -> runOnUiThread(sessionChanged);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ZeroChillUi.applySystemBars(this);
        LibraryDestinationChrome.prepareWindow(this);
        owner = ZeroChillSessionStore.currentUserId(this);
        LinearLayout root = BrowseUi.screen(this);
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(BrowseUi.action(this, "‹", "Back", v -> finish()), new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = BrowseUi.text(this, "Liked Videos", 22, UiPalette.TEXT_PRIMARY);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        refresh = BrowseUi.action(this, "Refresh", "Refresh liked videos", v -> loadPage(0));
        heading.addView(refresh, new LinearLayout.LayoutParams(-2, dp(48)));
        LibraryDestinationChrome.attachHeader(root, heading, title);
        status = BrowseUi.text(this, "Loading your likes…", 13, UiPalette.TEXT_MUTED);
        status.setPadding(dp(16), dp(8), dp(16), dp(12));
        LibraryDestinationChrome.styleSummary(this, status);
        root.addView(status);
        recycler = new RecyclerView(this);
        galleryLayout = new GridLayoutManager(this, 2);
        galleryLayout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                return position >= 0 && position < items.size()
                        && Boolean.TRUE.equals(portraitLikedVideos.get(items.get(position).url)) ? 1 : 2;
            }
        });
        recycler.setLayoutManager(galleryLayout);
        recycler.setAdapter(adapter);
        recycler.setClipToPadding(false);
        recycler.setPadding(dp(12), 0, dp(12), dp(24));
        root.addView(recycler, new LinearLayout.LayoutParams(-1, 0, 1));
        more = BrowseUi.action(this, "Load more", "Load more liked videos", v -> loadPage(nextOffset));
        more.setVisibility(View.GONE);
        LibraryDestinationChrome.styleAction(more, this);
        root.addView(more, new LinearLayout.LayoutParams(-1, dp(48)));
        setContentView(root);
        ZeroChillSessionStore.preferences(this).registerOnSharedPreferenceChangeListener(sessionListener);
        if (state != null) restoredScroll = state.getParcelable("scroll");
        Retained retained = (Retained) getLastNonConfigurationInstance();
        if (retained != null && owner.equals(retained.owner)) {
            items.addAll(retained.items);
            nextOffset = retained.nextOffset; unavailable = retained.unavailable; hasMore = retained.hasMore;
            adapter.notifyDataSetChanged(); showStatus(false);
            if (restoredScroll != null) recycler.getLayoutManager().onRestoreInstanceState(restoredScroll);
            restoredScroll = null;
        } else loadLocal();
    }

    @Override public Object onRetainNonConfigurationInstance() {
        return new Retained(owner, new ArrayList<>(items), nextOffset, unavailable, hasMore);
    }

    private static final class Retained {
        final String owner; final List<NativeContentItem> items;
        final int nextOffset, unavailable; final boolean hasMore;
        Retained(String owner, List<NativeContentItem> items, int nextOffset, int unavailable, boolean hasMore) {
            this.owner = owner; this.items = items; this.nextOffset = nextOffset;
            this.unavailable = unavailable; this.hasMore = hasMore;
        }
    }

    private void checkAccount() {
        if (isFinishing() || isDestroyed()) return;
        String current = ZeroChillSessionStore.currentUserId(this);
        if (owner.equals(current)) return;
        generation++;
        owner = current;
        loading = false;
        nextOffset = unavailable = 0;
        hasMore = false;
        items.clear(); portraitLikedVideos.clear(); adapter.notifyDataSetChanged();
        more.setVisibility(View.GONE);
        status.setText("Loading your likes…");
        loadLocal();
    }

    private void loadLocal() {
        final String requested = owner;
        final int request = ++generation;
        disk.execute(() -> {
            List<NativeContentItem> cached = SocialContentContextStore.likedVideos(this, requested);
            runOnUiThread(() -> {
                if (!valid(request, requested)) return;
                items.clear(); items.addAll(cached); adapter.notifyDataSetChanged();
                showStatus(false);
                if (restoredScroll != null) {
                    recycler.getLayoutManager().onRestoreInstanceState(restoredScroll);
                    restoredScroll = null;
                }
            });
        });
    }

    private void loadPage(int offset) {
        if (loading) return;
        if (owner.isEmpty()) {
            startActivity(new Intent(this, ZeroChillAccountActivity.class));
            return;
        }
        loading = true;
        final int request = ++generation;
        final String requested = owner;
        refresh.setEnabled(false); more.setEnabled(false);
        status.setText("Loading liked videos…");
        ZeroChillSocialRepository.loadLikedVideoPage(this, offset, (page, error) -> runOnUiThread(() -> {
            if (!valid(request, requested)) return;
            loading = false;
            if (error != null || page == null) {
                status.setText("Couldn't refresh likes. Your cached videos are still available. Tap Refresh to retry.");
                refresh.setEnabled(true); more.setEnabled(true);
                return;
            }
            if (offset == 0) { items.clear(); unavailable = 0; }
            java.util.Set<String> present = new java.util.HashSet<>();
            for (NativeContentItem item : items) present.add(ZeroChillSocialRepository.contentKey(item.url));
            for (NativeContentItem item : page.items)
                if (present.add(ZeroChillSocialRepository.contentKey(item.url))) items.add(item);
            unavailable += page.unavailable;
            nextOffset = page.nextOffset; hasMore = page.hasMore;
            adapter.notifyDataSetChanged(); showStatus(true);
        }));
    }

    private boolean valid(int request, String requested) {
        return !isFinishing() && !isDestroyed() && request == generation
                && requested.equals(ZeroChillSessionStore.currentUserId(this));
    }

    private void showStatus(boolean synced) {
        refresh.setEnabled(true); more.setEnabled(true);
        more.setVisibility(hasMore ? View.VISIBLE : View.GONE);
        status.setText(owner.isEmpty() ? "Sign in, then tap Refresh to view your likes."
                : unavailable > 0 ? items.size() + " available · " + unavailable
                        + " older likes have no video details on this device."
                : items.isEmpty() ? synced ? "No liked videos yet." : "Tap Refresh to load your previous likes."
                : synced ? items.size() + " liked videos" : "Saved on this device · Refresh to check your account likes");
    }

    private final class LikesAdapter extends RecyclerView.Adapter<Holder> {
        @Override public Holder onCreateViewHolder(ViewGroup parent, int type) {
            LinearLayout row = new LinearLayout(LikedVideosActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setClickable(true); row.setFocusable(true);
            row.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            LibraryDestinationChrome.styleLikedRow(LikedVideosActivity.this, row);
            LibraryAspectMediaCard card = new LibraryAspectMediaCard(LikedVideosActivity.this);
            LibraryDestinationChrome.styleMediaCard(LikedVideosActivity.this, card);
            row.addView(card, new LinearLayout.LayoutParams(-1, -2));

            ImageView image = new ImageView(LikedVideosActivity.this);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackgroundColor(android.graphics.Color.BLACK);
            card.addView(image, new MaterialCardView.LayoutParams(-1, -1));

            TextView title = BrowseUi.text(LikedVideosActivity.this, "", 13,
                    AppearanceTheme.primaryText(LikedVideosActivity.this));
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
            titleParams.topMargin = dp(8);
            row.addView(title, titleParams);
            ZeroChillMotion.installPressFeedback(row);
            return new Holder(row, card, image, title);
        }

        @Override public void onBindViewHolder(Holder holder, int position) {
            NativeContentItem item = items.get(position);
            holder.card.setPortrait(Boolean.TRUE.equals(portraitLikedVideos.get(item.url)));
            holder.title.setText(OnlyFapVideoMetadata.displayTitle(item));
            holder.itemView.setContentDescription("Play liked video " + item.title);
            Glide.with(holder.image).clear(holder.image);
            holder.image.setTag(item.url);
            holder.image.setImageResource(R.drawable.ic_more_account);
            if (!item.imageUrl.isEmpty()) {
                Glide.with(holder.image).load(LibraryHubView.remoteImage(item.imageUrl, item.url))
                    .dontTransform()
                    .downsample(com.bumptech.glide.load.resource.bitmap.DownsampleStrategy.AT_MOST)
                    .override(dp(512), dp(512))
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException error,
                                Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                boolean first) { return false; }
                        @Override public boolean onResourceReady(android.graphics.drawable.Drawable drawable,
                                Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                com.bumptech.glide.load.DataSource source, boolean first) {
                            if (drawable.getIntrinsicWidth() > 0 && drawable.getIntrinsicHeight() > 0) {
                                boolean portrait = drawable.getIntrinsicHeight() > drawable.getIntrinsicWidth();
                                holder.image.post(() -> {
                                    if (!isFinishing() && !isDestroyed() && holder.image.isAttachedToWindow()
                                            && item.url.equals(holder.image.getTag()))
                                        onLikedOrientation(item.url, portrait);
                                });
                            }
                            return false;
                        }
                    })
                    .transition(ThumbnailFades.thumbnail()).into(holder.image);
            }
            holder.itemView.setOnClickListener(v -> {
                Intent result = new Intent().putExtra(FavoritesActivity.EXTRA_SELECTED_URL, item.url)
                        .putExtra(FavoritesActivity.EXTRA_SELECTED_TITLE, item.title)
                        .putExtra(FavoritesActivity.EXTRA_SELECTED_TAB, FavoritesActivity.START_WATCH_LATER);
                setResult(RESULT_OK, result); finish();
            });
        }
        @Override public int getItemCount() { return items.size(); }
        @Override public void onViewRecycled(Holder holder) { Glide.with(holder.image).clear(holder.image); }
    }
    private static final class Holder extends RecyclerView.ViewHolder {
        final LibraryAspectMediaCard card;
        final ImageView image;
        final TextView title;
        Holder(View root, LibraryAspectMediaCard card, ImageView image, TextView title) {
            super(root);
            this.card = card;
            this.image = image;
            this.title = title;
        }
    }

    private void onLikedOrientation(String url, boolean portrait) {
        Boolean previous = portraitLikedVideos.put(url, portrait);
        if (previous != null && previous == portrait) return;
        if (galleryLayout == null || recycler == null) return;
        galleryLayout.getSpanSizeLookup().invalidateSpanIndexCache();
        for (int i = 0; i < items.size(); i++) {
            if (url.equals(items.get(i).url)) {
                adapter.notifyItemChanged(i);
                break;
            }
        }
    }
    @Override protected void onResume() { super.onResume(); if (status != null) checkAccount(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putParcelable("scroll", recycler.getLayoutManager().onSaveInstanceState());
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        generation++;
        ZeroChillSessionStore.preferences(this).unregisterOnSharedPreferenceChangeListener(sessionListener);
        recycler.setAdapter(null);
        disk.shutdownNow();
        super.onDestroy();
    }
    private int dp(int value) { return BrowseUi.dp(this, value); }
}
