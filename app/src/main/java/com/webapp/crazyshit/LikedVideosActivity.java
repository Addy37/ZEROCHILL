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
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lazy, account-scoped collection. Network refresh and pagination require an explicit tap. */
public final class LikedVideosActivity extends Activity {
    private final ExecutorService disk = Executors.newSingleThreadExecutor();
    private final List<NativeContentItem> items = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
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
                return position >= 0 && position < rows.size() && rows.get(position).heading() ? 2 : 1;
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
            publish(); showStatus(false);
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
        items.clear(); portraitLikedVideos.clear(); publish();
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
                items.clear(); items.addAll(cached); publish();
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
            publish(); showStatus(true);
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

    private void publish() {
        rebuildRows();
        if (galleryLayout != null) galleryLayout.getSpanSizeLookup().invalidateSpanIndexCache();
        adapter.notifyDataSetChanged();
    }

    private void rebuildRows() {
        rows.clear();
        LinkedHashMap<String, List<NativeContentItem>> groups = new LinkedHashMap<>();
        for (NativeContentItem item : items) {
            groups.computeIfAbsent(LibraryBrowseGroups.sourceLabel(item.url), key -> new ArrayList<>()).add(item);
        }
        for (Map.Entry<String, List<NativeContentItem>> entry : groups.entrySet()) {
            rows.add(Row.heading(entry.getKey()));
            for (NativeContentItem item : entry.getValue()) rows.add(Row.card(item));
        }
    }

    private final class LikesAdapter extends RecyclerView.Adapter<Holder> {
        @Override public int getItemViewType(int position) {
            return rows.get(position).heading() ? Row.HEADING : Row.CARD;
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int type) {
            if (type == Row.HEADING) {
                TextView label = LibraryDestinationChrome.sectionHeading(LikedVideosActivity.this, "");
                label.setTag("library-source-heading");
                label.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                return new Holder(label, null, null, label);
            }
            LinearLayout row = new LinearLayout(LikedVideosActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setGravity(Gravity.TOP);
            row.setClickable(true); row.setFocusable(true);
            RecyclerView.LayoutParams rowParams = new RecyclerView.LayoutParams(-1, -2);
            rowParams.setMargins(dp(4), 0, dp(4), dp(8));
            row.setLayoutParams(rowParams);
            LibraryAspectMediaCard card = new LibraryAspectMediaCard(LikedVideosActivity.this);
            card.setUniform(true);
            LibraryDestinationChrome.styleMediaCard(LikedVideosActivity.this, card);
            card.setRadius(dp(16));
            card.setPreventCornerOverlap(false);
            card.setClipToOutline(true);
            row.addView(card, new LinearLayout.LayoutParams(-1, -2));

            ImageView image = new ImageView(LikedVideosActivity.this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(android.graphics.Color.BLACK);
            card.addView(image, new MaterialCardView.LayoutParams(-1, -1));

            TextView title = LibraryDestinationChrome.mediaCaption(LikedVideosActivity.this, "");
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
            titleParams.setMargins(dp(2), dp(6), dp(2), 0);
            row.addView(title, titleParams);
            ZeroChillMotion.installPressFeedback(row);
            return new Holder(row, card, image, title);
        }

        @Override public void onBindViewHolder(Holder holder, int position) {
            Row row = rows.get(position);
            if (row.heading()) {
                holder.title.setText(row.label);
                holder.itemView.setClickable(false);
                holder.itemView.setOnClickListener(null);
                holder.itemView.setContentDescription(row.label + " likes");
                return;
            }
            NativeContentItem item = row.item;
            String title = LibraryBrowseGroups.readableTitle(item, "");
            if ("Saved video".equals(title)) title = "Liked video";
            else if ("Saved photo".equals(title)) title = "Liked photo";
            holder.title.setText(title);
            holder.itemView.setContentDescription("Open " + title);
            Glide.with(holder.image).clear(holder.image);
            holder.image.setTag(item.url);
            holder.image.setScaleType(item.imageUrl.isEmpty()
                    ? ImageView.ScaleType.CENTER_INSIDE : ImageView.ScaleType.CENTER_CROP);
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
            holder.itemView.setOnClickListener(v ->
                    LibraryPlayback.openSaved(LikedVideosActivity.this, item, -1L, false));
        }
        @Override public int getItemCount() { return rows.size(); }
        @Override public void onViewRecycled(Holder holder) {
            if (holder.image != null) Glide.with(holder.image).clear(holder.image);
        }
    }
    private static final class Row {
        static final int CARD = 0;
        static final int HEADING = 2;
        final String label;
        final NativeContentItem item;
        private Row(String label, NativeContentItem item) {
            this.label = label;
            this.item = item;
        }
        static Row heading(String label) { return new Row(label, null); }
        static Row card(NativeContentItem item) { return new Row(null, item); }
        boolean heading() { return item == null; }
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
        portraitLikedVideos.put(url, portrait);
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
