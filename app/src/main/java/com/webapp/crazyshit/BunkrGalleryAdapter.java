package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.load.resource.bitmap.DrawableTransformation;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.target.Target;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Dense mixed-media grid used for Bunkr albums. */
final class BunkrGalleryAdapter extends RecyclerView.Adapter<BunkrGalleryAdapter.Holder> {
    private static final Object PAYLOAD_FAVORITE = new Object();
    private static final BunkrThumbnailMatte BUNKR_MATTE = new BunkrThumbnailMatte();
    private static final com.bumptech.glide.load.Transformation<BitmapDrawable> BUNKR_MATTE_DRAWABLE =
            new DrawableTransformation(BUNKR_MATTE, false).asBitmapDrawable();
    private final java.util.Set<String> favoriteUrls = new java.util.HashSet<>();
    private final java.util.LinkedHashMap<String, Integer> normalOrder = new java.util.LinkedHashMap<>();
    private static final Object PAYLOAD_OVERLAY_DENSITY = new Object();
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    interface Listener {
        void onOpen(int position, NativeContentItem item, View transitionAnchor);

        void onLongPress(NativeContentItem item, View anchor);
    }

    private final ArrayList<NativeContentItem> items = new ArrayList<>();
    private final Map<String, Float> aspectRatios = new HashMap<>();
    private final LinkedHashSet<String> highlightedUrls = new LinkedHashSet<>();
    private final Context context;
    private final Listener listener;
    private final boolean adaptiveAspectRatios;
    private int gridColumns = 2;
    private RecyclerView previewViewport;
    private androidx.core.view.OneShotPreDrawListener pendingFavoriteMove;
    private final LinkedHashSet<View> favoriteMovingTiles = new LinkedHashSet<>();
    private int favoriteMoveGeneration;
    private boolean previewsActive = true;
    private boolean gifPageActive = true;
    private long gifResumeAt;
    private static final java.util.concurrent.ExecutorService GIF_RESOLVER =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "GalleryGifResolver");
                thread.setDaemon(true);
                return thread;
            });
    private final Runnable resumeGifs = this::updateGifTiles;
    private final RecyclerView.OnScrollListener gifScroll = new RecyclerView.OnScrollListener() {
        @Override public void onScrollStateChanged(@NonNull RecyclerView recycler, int state) {
            deferGifs();
        }
        @Override public void onScrolled(@NonNull RecyclerView recycler, int dx, int dy) {
            if (dx != 0 || dy != 0) deferGifs();
        }
    };
    private final java.util.function.Consumer<String> playbackPreviewListener = this::showPlaybackPreview;
    private final LinkedHashSet<Holder> previewHolders = new LinkedHashSet<>();
    private final Rect previewBounds = new Rect();
    private final android.view.ViewTreeObserver.OnPreDrawListener previewVisibility = () -> {
        RecyclerView viewport = previewViewport;
        if (viewport == null) return true;
        for (int i = 0; i < viewport.getChildCount(); i++) {
            RecyclerView.ViewHolder raw = viewport.getChildViewHolder(viewport.getChildAt(i));
            if (!(raw instanceof Holder)) continue;
            Holder holder = (Holder) raw;
            if (!previewVisible(holder)) {
                cancelPreview(holder);
                holder.previewAttempted = false;
            } else if (holder.boundItem != null) {
                if (holder.wantsMediaPreview) requestMediaPreview(holder, holder.boundItem);
                else if (holder.needsRepair) repairArtwork(holder, holder.boundItem, holder.boundItem.imageUrl);
            }
        }
        updateGifTiles();
        return true;
    };
    private final PreloadRequestTracker imagePreloads =
            new PreloadRequestTracker(320);

    BunkrGalleryAdapter(Context context, Listener listener) {
        this(context, listener, false);
    }

    BunkrGalleryAdapter(Context context, Listener listener, boolean adaptiveAspectRatios) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.adaptiveAspectRatios = adaptiveAspectRatios;
        setHasStableIds(true);
    }

    void replace(List<NativeContentItem> incoming) {
        replace(incoming, true);
    }

    void replace(List<NativeContentItem> incoming, boolean preloadAhead) {
        stopFavoriteMoves();
        items.clear();
        normalOrder.clear();
        addUnique(incoming);
        for (NativeContentItem item : items) normalOrder.put(item.url, normalOrder.size());
        rememberAndSort();
        notifyDataSetChanged();
        if (preloadAhead) preloadRange(0, Math.min(items.size(), adaptiveAspectRatios ? 6 : 12));
    }

    void append(List<NativeContentItem> incoming) {
        append(incoming, true);
    }

    void append(List<NativeContentItem> incoming, boolean preloadAhead) {
        int start = items.size();
        addUnique(incoming);
        if (incoming != null) for (NativeContentItem item : incoming)
            if (item != null) normalOrder.putIfAbsent(item.url, normalOrder.size());
        int added = items.size() - start;
        if (added > 0) {
            notifyItemRangeInserted(start, added);
            if (preloadAhead) preloadRange(start, Math.min(items.size(), start + (adaptiveAspectRatios ? 6 : 12)));
        }
        reorderFavorites();
    }

    void appendPinned(List<NativeContentItem> incoming) {
        int start = items.size();
        addUnique(incoming);
        if (items.size() > start) notifyItemRangeInserted(start, items.size() - start);
        reorderFavorites();
    }

    private void rememberAndSort() {
        items.sort(java.util.Comparator.comparingInt((NativeContentItem item) -> favoriteUrls.contains(item.url) ? 0 : 1)
                .thenComparingInt(item -> normalOrder.getOrDefault(item.url, Integer.MAX_VALUE)));
    }

    void setFavoriteUrls(java.util.Set<String> urls) {
        if (favoriteUrls.equals(urls)) return;
        java.util.Set<String> changed = new java.util.HashSet<>(favoriteUrls);
        for (String url : urls) if (!changed.add(url)) changed.remove(url);
        favoriteUrls.clear(); favoriteUrls.addAll(urls);
        reorderFavorites(changed);
        for (int i = 0; i < items.size(); i++) if (changed.contains(items.get(i).url))
            notifyItemChanged(i, PAYLOAD_FAVORITE);
    }

    private void reorderFavorites() { reorderFavorites(java.util.Collections.emptySet()); }

    private void reorderFavorites(java.util.Set<String> changed) {
        ArrayList<NativeContentItem> before = snapshot();
        rememberAndSort();
        if (before.equals(items)) return;
        RecyclerView list = previewViewport;
        java.util.Map<String, android.graphics.PointF> locations = new HashMap<>();
        String anchorUrl = ""; int anchorOffset = 0;
        if (list != null) for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            int position = list.getChildAdapterPosition(child);
            if (position < 0 || position >= before.size()) continue;
            String url = before.get(position).url;
            locations.put(url, new android.graphics.PointF(child.getX(), child.getY()));
            if (anchorUrl.isEmpty() && !changed.contains(url) && child.getBottom() > list.getPaddingTop()) {
                anchorUrl = url; anchorOffset = child.getTop() - list.getPaddingTop();
            }
        }
        stopFavoriteMoves();
        if (list != null) for (int i = 0; i < list.getChildCount(); i++)
            resetTileTransform(list.getChildAt(i));
        androidx.recyclerview.widget.DiffUtil.calculateDiff(new androidx.recyclerview.widget.DiffUtil.Callback() {
            public int getOldListSize() { return before.size(); }
            public int getNewListSize() { return items.size(); }
            public boolean areItemsTheSame(int oldIndex, int newIndex) { return before.get(oldIndex).url.equals(items.get(newIndex).url); }
            public boolean areContentsTheSame(int oldIndex, int newIndex) { return true; }
        }).dispatchUpdatesTo(this);
        if (list == null) return;
        if (!anchorUrl.isEmpty() && list.getLayoutManager() instanceof androidx.recyclerview.widget.StaggeredGridLayoutManager)
            ((androidx.recyclerview.widget.StaggeredGridLayoutManager) list.getLayoutManager())
                    .scrollToPositionWithOffset(indexOfUrl(anchorUrl), anchorOffset);
        if (!previewsActive) return;
        final int moveGeneration = favoriteMoveGeneration;
        pendingFavoriteMove = androidx.core.view.OneShotPreDrawListener.add(list, () -> {
            if (moveGeneration != favoriteMoveGeneration || previewViewport != list) return;
            pendingFavoriteMove = null;
            if (!android.animation.ValueAnimator.areAnimatorsEnabled()) return;
            for (int i = 0; i < list.getChildCount(); i++) {
                View child = list.getChildAt(i);
                int position = list.getChildAdapterPosition(child);
                if (position < 0 || position >= items.size()) continue;
                android.graphics.PointF old = locations.get(items.get(position).url);
                if (old == null) continue;
                favoriteMovingTiles.add(child);
                child.setTranslationX(old.x - child.getLeft()); child.setTranslationY(old.y - child.getTop());
                child.animate().translationX(0).translationY(0).setDuration(240)
                        .setInterpolator(new android.view.animation.PathInterpolator(.20f, 0f, .05f, 1f))
                        .withEndAction(() -> favoriteMovingTiles.remove(child)).start();
            }
        });
    }

    // RecyclerView has no ItemAnimator here: it cannot retire our ViewPropertyAnimator
    // transforms. A canceled/detached move otherwise survives in a recycled tile and
    // draws outside the bounds used by the staggered layout manager.
    private void resetTileTransform(View tile) {
        favoriteMovingTiles.remove(tile);
        tile.animate().cancel();
        tile.setTranslationX(0f);
        tile.setTranslationY(0f);
        tile.setScaleX(1f);
        tile.setScaleY(1f);
    }

    private void stopFavoriteMoves() {
        favoriteMoveGeneration++;
        if (pendingFavoriteMove != null) pendingFavoriteMove.removeListener();
        pendingFavoriteMove = null;
        for (View tile : new ArrayList<>(favoriteMovingTiles)) resetTileTransform(tile);
    }

    void setHighlightedUrls(Collection<String> urls) {
        highlightedUrls.clear();
        if (urls != null) {
            for (String url : urls) {
                if (url != null && !url.trim().isEmpty()) highlightedUrls.add(url.trim());
            }
        }
        if (!items.isEmpty()) notifyDataSetChanged();
    }


    void preloadVisible(int first, int last) {
        int from = Math.max(0, first);
        int lookAhead = adaptiveAspectRatios ? 6 : 12;
        int to = Math.min(items.size(), Math.max(from, last + lookAhead));
        preloadRange(from, to);
    }

    ArrayList<NativeContentItem> snapshot() {
        return new ArrayList<>(items);
    }

    ArrayList<NativeContentItem> normalSnapshot() {
        ArrayList<NativeContentItem> result = snapshot();
        result.removeIf(item -> !normalOrder.containsKey(item.url));
        result.sort(java.util.Comparator.comparingInt(item -> normalOrder.get(item.url)));
        return result;
    }

    int indexOfUrl(String url) {
        if (url == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (url.equals(items.get(i).url)) return i;
        }
        return -1;
    }

    int size() {
        return items.size();
    }

    void setGridColumns(int columns) {
        int next = Math.max(1, columns);
        if (gridColumns == next) return;
        gridColumns = next;
        deferGifs();
        if (!items.isEmpty()) {
            notifyItemRangeChanged(0, items.size(), PAYLOAD_OVERLAY_DENSITY);
        }
    }

    static float overlayScaleForColumns(int columns) {
        if (columns <= 2) return 1f;
        if (columns == 3) return 0.82f;
        if (columns == 4) return 0.68f;
        if (columns == 5) return 0.58f;
        return Math.max(0.46f, 0.58f - ((columns - 5) * 0.05f));
    }

    void pulseGridMorph(RecyclerView list) {
        if (list == null || !android.animation.ValueAnimator.areAnimatorsEnabled()) return;
        for (int index = 0; index < list.getChildCount(); index++) {
            View child = list.getChildAt(index);
            RecyclerView.ViewHolder raw = list.getChildViewHolder(child);
            if (!(raw instanceof Holder)) continue;
            View pulse = ((Holder) raw).morphPulse;
            pulse.animate().cancel();
            pulse.setAlpha(0f);
            pulse.animate()
                    .alpha(0.58f)
                    .setDuration(55L)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .withEndAction(() -> pulse.animate()
                            .alpha(0f)
                            .setDuration(115L)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .start())
                    .start();
        }
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).url.hashCode();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AspectRatioFrameLayout tile = new AspectRatioFrameLayout(parent.getContext());
        tile.setBackgroundColor(Color.rgb(20, 20, 23));
        RecyclerView.LayoutParams tileParams = new RecyclerView.LayoutParams(-1, -2);
        int gap = dp(parent, 3);
        tile.setBackground(BrowseUi.rounded(parent.getContext(), BrowseUi.SURFACE, 10));
        tile.setClipToOutline(true);
        tile.setHapticFeedbackEnabled(false);
        tileParams.setMargins(gap, gap, gap, gap);
        tile.setLayoutParams(tileParams);

        ImageView image = new ImageView(parent.getContext());
        image.setBackgroundColor(Color.rgb(20, 20, 23));
        tile.addView(image, new FrameLayout.LayoutParams(-1, -1));

        TextView unavailable = new TextView(parent.getContext());
        unavailable.setText(R.string.media_preview_unavailable);
        unavailable.setTextColor(Color.LTGRAY);
        unavailable.setTextSize(12);
        unavailable.setGravity(Gravity.CENTER);
        unavailable.setVisibility(View.GONE);
        tile.addView(unavailable, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout source = new FrameLayout(parent.getContext());
        source.setBackground(BrowseUi.rounded(
                parent.getContext(),
                Color.argb(215, 0, 0, 0),
                7
        ));
        source.setElevation(dp(parent, 5));
        source.setAlpha(0.85f);
        FrameLayout.LayoutParams sourceParams = new FrameLayout.LayoutParams(
                dp(parent, 26),
                dp(parent, 26)
        );
        sourceParams.gravity = Gravity.BOTTOM | Gravity.START;
        sourceParams.setMargins(dp(parent, 6), 0, 0, dp(parent, 6));
        tile.addView(source, sourceParams);

        ImageView sourceIcon = new ImageView(parent.getContext());
        sourceIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        sourceIcon.setPadding(dp(parent, 3), dp(parent, 3), dp(parent, 3), dp(parent, 3));
        source.addView(sourceIcon, new FrameLayout.LayoutParams(-1, -1));

        TextView sourceVariant = new TextView(parent.getContext());
        GradientDrawable variantBackground = new GradientDrawable();
        variantBackground.setShape(GradientDrawable.OVAL);
        variantBackground.setColor(Color.rgb(190, 24, 93));
        sourceVariant.setBackground(variantBackground);
        sourceVariant.setGravity(Gravity.CENTER);
        sourceVariant.setIncludeFontPadding(false);
        sourceVariant.setText("X");
        sourceVariant.setTextColor(Color.WHITE);
        sourceVariant.setTextSize(7);
        sourceVariant.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        FrameLayout.LayoutParams variantParams = new FrameLayout.LayoutParams(
                dp(parent, 12),
                dp(parent, 12)
        );
        variantParams.gravity = Gravity.BOTTOM | Gravity.END;
        source.addView(sourceVariant, variantParams);

        ImageView fresh = new ImageView(parent.getContext());
        fresh.setImageResource(R.drawable.ic_new_content);
        fresh.setContentDescription("New content");
        fresh.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        fresh.setVisibility(View.GONE);
        fresh.setElevation(dp(parent, 7));
        FrameLayout.LayoutParams freshParams = new FrameLayout.LayoutParams(
                dp(parent, 26),
                dp(parent, 26)
        );
        freshParams.gravity = Gravity.TOP | Gravity.END;
        freshParams.setMargins(0, dp(parent, 6), dp(parent, 6), 0);
        tile.addView(fresh, freshParams);

        ImageView favorite = new ImageView(parent.getContext());
        favorite.setImageResource(R.drawable.ic_action_heart_filled);
        favorite.setColorFilter(AppearanceTheme.accent(context));
        favorite.setBackground(BrowseUi.rounded(parent.getContext(), Color.argb(160, 0, 0, 0), 16));
        favorite.setPadding(dp(parent, 5), dp(parent, 5), dp(parent, 5), dp(parent, 5));
        favorite.setContentDescription("Favorited media");
        FrameLayout.LayoutParams favoriteParams = new FrameLayout.LayoutParams(dp(parent, 28), dp(parent, 28), Gravity.TOP | Gravity.START);
        favoriteParams.setMargins(dp(parent, 6), dp(parent, 6), 0, 0);
        tile.addView(favorite, favoriteParams);
        BunkrMaintenanceUi.Badge maintenance = BunkrMaintenanceUi.addBadge(
                tile, Gravity.TOP | Gravity.CENTER_HORIZONTAL);

        FrameLayout play = new FrameLayout(parent.getContext());
        GradientDrawable playBackground = new GradientDrawable();
        playBackground.setCornerRadius(dp(parent, 8));
        playBackground.setColor(Color.argb(185, 0, 0, 0));
        play.setBackground(playBackground);
        play.setElevation(dp(parent, 5));
        FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(
                dp(parent, 42),
                dp(parent, 42)
        );
        playParams.gravity = Gravity.BOTTOM | Gravity.END;
        playParams.setMargins(0, 0, dp(parent, 6), dp(parent, 6));
        tile.addView(play, playParams);

        ImageView playIcon = new ImageView(parent.getContext());
        playIcon.setImageResource(R.drawable.ic_player_play);
        playIcon.setColorFilter(Color.WHITE);
        playIcon.setPadding(dp(parent, 11), dp(parent, 11), dp(parent, 9), dp(parent, 11));
        play.addView(playIcon, new FrameLayout.LayoutParams(-1, -1));

        TextView gif = new TextView(parent.getContext());
        gif.setText("GIF");
        gif.setTextColor(Color.WHITE);
        gif.setTextSize(11);
        gif.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        gif.setIncludeFontPadding(false);
        gif.setGravity(Gravity.CENTER);
        gif.setPadding(dp(parent, 6), dp(parent, 4), dp(parent, 6), dp(parent, 4));
        gif.setBackground(BrowseUi.rounded(parent.getContext(), Color.argb(215, 0, 0, 0), 7));
        gif.setElevation(dp(parent, 5));
        gif.setVisibility(View.GONE);
        FrameLayout.LayoutParams gifParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        gifParams.setMargins(0, 0, dp(parent, 6), dp(parent, 6));
        tile.addView(gif, gifParams);

        View morphPulse = new View(parent.getContext());
        GradientDrawable morphPulseBackground = new GradientDrawable();
        morphPulseBackground.setColor(Color.TRANSPARENT);
        morphPulseBackground.setCornerRadius(dp(parent, 10));
        morphPulseBackground.setStroke(dp(parent, 1), AppearanceTheme.accent(context));
        morphPulse.setBackground(morphPulseBackground);
        morphPulse.setAlpha(0f);
        morphPulse.setClickable(false);
        morphPulse.setFocusable(false);
        tile.addView(morphPulse, new FrameLayout.LayoutParams(-1, -1));

        return new Holder(
                tile,
                image,
                source,
                sourceIcon,
                sourceVariant,
                fresh,
                play,
                morphPulse,
                unavailable, favorite, maintenance, gif
        );
    }

    @Override
    public void onBindViewHolder(
            @NonNull Holder holder,
            int position,
            @NonNull List<Object> payloads
    ) {
        if (payloads.contains(PAYLOAD_FAVORITE)) {
            holder.favorite.setVisibility(favoriteUrls.contains(items.get(position).url) ? View.VISIBLE : View.GONE);
            if (payloads.contains(PAYLOAD_OVERLAY_DENSITY)) applyOverlayDensity(holder);
            return;
        }
        if (!payloads.isEmpty() && payloads.contains(PAYLOAD_OVERLAY_DENSITY)) {
            applyOverlayDensity(holder);
            return;
        }
        onBindViewHolder(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        cancelGifResolution(holder);
        setGifRunning(holder.image.getDrawable(), false);
        holder.gifUpgradeAttempted = false;
        holder.gifThumbnailReady = false;
        resetTileTransform(holder.itemView);
        holder.morphPulse.animate().cancel();
        holder.morphPulse.setAlpha(0f);
        NativeContentItem item = items.get(position);
        holder.favorite.setVisibility(favoriteUrls.contains(item.url) ? View.VISIBLE : View.GONE);
        holder.maintenance.bind(item);
        ViewCompat.setTransitionName(holder.image, GalleryMediaTransition.transitionName(item));
        holder.image.setScaleType(adaptiveAspectRatios && !BunkrRepository.isBunkrUrl(item.url)
                ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
        holder.tile.setAspectRatio(adaptiveAspectRatios
                ? aspectRatios.getOrDefault(item.url, item.aspectRatioHint > 0f ? item.aspectRatioHint : 1f)
                : 1f);
        holder.play.setVisibility(item.isVideo() ? View.VISIBLE : View.GONE);
        holder.gif.setVisibility(item.isGif() ? View.VISIBLE : View.GONE);
        applyOverlayDensity(holder);
        boolean fresh = isHighlighted(item);
        holder.fresh.setVisibility(fresh ? View.VISIBLE : View.GONE);
        SourceBadge source = sourceBadge(item);
        holder.source.setVisibility(source == null ? View.GONE : View.VISIBLE);
        if (source != null) {
            holder.sourceIcon.setImageResource(source.drawableRes);
            holder.sourceVariant.setVisibility(source.wikiFeetX ? View.VISIBLE : View.GONE);
        } else {
            holder.sourceIcon.setImageDrawable(null);
            holder.sourceVariant.setVisibility(View.GONE);
        }
        holder.itemView.setContentDescription(
                (item.isVideo() ? "Video, " : item.isGif() ? "GIF, " : "Photo, ") + item.title +
                        (source == null ? "" : ", source " + source.name) +
                        (fresh ? ", new from Updates" : "")
        );

        holder.bindGeneration++;
        holder.unavailable.setVisibility(View.GONE);
        cancelPreview(holder);
        holder.boundItem = item;
        holder.needsRepair = false;
        holder.repairPending = false;
        holder.wantsMediaPreview = false;
        holder.previewAttempted = false;
        boolean pawchiveVideo = pawchiveVideoPreview(item);
        File cachedPreview = BunkrRepository.isBunkrUrl(item.url) || pawchiveVideo
                ? BunkrMediaPreview.cached(context, item) : null;
        if (cachedPreview != null) {
            holder.wantsMediaPreview = true;
            holder.previewAttempted = true;
            loadMediaPreview(holder, item, cachedPreview);
        } else if (item.imageUrl == null || item.imageUrl.isEmpty()
                || (BunkrRepository.isBunkrUrl(item.url) && !BunkrRepository.isUsableArtwork(item.imageUrl))) {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setImageDrawable(new ColorDrawable(Color.rgb(20, 20, 23)));
            if (PawchiveRepository.isDirectMedia(item.url)) {
                if (item.isImage()) holder.unavailable.setVisibility(View.VISIBLE);
            } else repairArtwork(holder, item, item.imageUrl);
        } else {
            loadArtwork(holder, item, item.imageUrl, true);
        }

        holder.itemView.setOnClickListener(v -> {
            int current = holder.getBindingAdapterPosition();
            if (current == RecyclerView.NO_POSITION || current >= items.size()) return;
            listener.onOpen(current, items.get(current), holder.image);
        });
        holder.itemView.setOnLongClickListener(v -> {
            int current = holder.getBindingAdapterPosition();
            if (current == RecyclerView.NO_POSITION || current >= items.size()) return false;
            listener.onLongPress(items.get(current), v);
            return true;
        });
    }

    private void loadArtwork(Holder holder, NativeContentItem item, String url, boolean repair) {
        int binding = holder.bindGeneration;
        RequestBuilder<Drawable> request = Glide.with(holder.image)
                .load(withHeaders(url, imageReferer(item)))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transition(ThumbnailFades.thumbnail())
                .placeholder(new ColorDrawable(Color.rgb(20, 20, 23)))
                .error(new ColorDrawable(Color.rgb(20, 20, 23)))
                .listener(new RequestListener<Drawable>() {
                    @Override public boolean onLoadFailed(GlideException error, Object model,
                            Target<Drawable> target, boolean firstResource) {
                        // Glide forbids starting another request synchronously inside its listener.
                        holder.image.post(() -> {
                            if (binding != holder.bindGeneration) return;
                            if (PawchiveRepository.isDirectMedia(item.url)) {
                                holder.unavailable.setVisibility(View.VISIBLE);
                                holder.gifThumbnailReady = true;
                                updateGifTiles();
                                return;
                            }
                            if (repair) repairArtwork(holder, item, url);
                            else requestMediaPreview(holder, item);
                        });
                        return false;
                    }
                    @Override public boolean onResourceReady(Drawable resource, Object model,
                            Target<Drawable> target, DataSource dataSource, boolean firstResource) {
                        if (adaptiveAspectRatios && binding == holder.bindGeneration)
                            applyAspectRatio(holder, item, resource);
                        if (item.isGif()) holder.image.post(() -> {
                            if (binding == holder.bindGeneration) {
                                holder.gifThumbnailReady = true;
                                updateGifTiles();
                            }
                        });
                        return false;
                    }
                });
        int decodeSize = adaptiveAspectRatios ? 384 : 360;
        request = formatThumbnail(request.override(decodeSize, decodeSize), item);
        if (isOnlyHavenImagePreview(item)) {
            RequestBuilder<Drawable> fallback = Glide.with(holder.image)
                    .load(withHeaders(item.url, imageReferer(item)))
                    .diskCacheStrategy(DiskCacheStrategy.ALL).dontAnimate()
                    .transition(ThumbnailFades.thumbnail());
            fallback = adaptiveAspectRatios ? fallback.dontTransform().override(384, 384)
                    : fallback.centerCrop().override(360, 360);
            fallback = fallback.listener(new RequestListener<Drawable>() {
                @Override public boolean onLoadFailed(GlideException error, Object model,
                        Target<Drawable> target, boolean firstResource) { return false; }
                @Override public boolean onResourceReady(Drawable resource, Object model,
                        Target<Drawable> target, DataSource source, boolean firstResource) {
                    if (adaptiveAspectRatios && binding == holder.bindGeneration)
                        applyAspectRatio(holder, item, resource);
                    return false;
                }
            });
            request = request.error(fallback);
        }
        request.into(holder.image);
    }

    // Use the same normal Glide ImageView target and original model as the fullscreen viewer.
    // Only the ordinary thumbnail is fetched during initial binding and scrolling.
    static void setGifRunning(Drawable drawable, boolean running) {
        if (!(drawable instanceof android.graphics.drawable.Animatable)) return;
        android.graphics.drawable.Animatable animation = (android.graphics.drawable.Animatable) drawable;
        if (running && !animation.isRunning()) animation.start();
        else if (!running && animation.isRunning()) animation.stop();
    }

    private static boolean twoColumns(RecyclerView recycler) {
        RecyclerView.LayoutManager layout = recycler.getLayoutManager();
        if (layout instanceof androidx.recyclerview.widget.StaggeredGridLayoutManager)
            return ((androidx.recyclerview.widget.StaggeredGridLayoutManager) layout).getSpanCount() == 2;
        if (layout instanceof androidx.recyclerview.widget.GridLayoutManager)
            return ((androidx.recyclerview.widget.GridLayoutManager) layout).getSpanCount() == 2;
        return false;
    }

    private boolean gifsMayRun() {
        RecyclerView recycler = previewViewport;
        return recycler != null && previewsActive && gifPageActive && twoColumns(recycler)
                && recycler.isAttachedToWindow() && recycler.isShown()
                && recycler.getWindowVisibility() == View.VISIBLE
                && recycler.getScrollState() == RecyclerView.SCROLL_STATE_IDLE
                && android.os.SystemClock.uptimeMillis() >= gifResumeAt;
    }

    private void pauseGifTiles() {
        for (Holder holder : previewHolders) setGifRunning(holder.image.getDrawable(), false);
    }

    private void deferGifs() {
        gifResumeAt = android.os.SystemClock.uptimeMillis() + 250L;
        pauseGifTiles();
        if (previewViewport != null) {
            previewViewport.removeCallbacks(resumeGifs);
            previewViewport.postDelayed(resumeGifs, 250L);
        }
    }

    void setGifPageActive(boolean active) {
        if (gifPageActive == active) return;
        gifPageActive = active;
        deferGifs();
        if (!active) for (Holder holder : previewHolders) cancelGifResolution(holder);
    }

    private void updateGifTiles() {
        boolean ready = gifsMayRun();
        for (Holder holder : previewHolders) {
            NativeContentItem item = holder.boundItem;
            if (item == null || !item.isGif()) continue;
            Drawable drawable = holder.image.getDrawable();
            boolean visible = ready && previewVisible(holder);
            if (!visible) {
                setGifRunning(drawable, false);
                continue;
            }
            if (drawable instanceof com.bumptech.glide.load.resource.gif.GifDrawable) {
                com.bumptech.glide.load.resource.gif.GifDrawable gif =
                        (com.bumptech.glide.load.resource.gif.GifDrawable) drawable;
                if (gif.getFrameCount() > 1) {
                    gif.setLoopCount(com.bumptech.glide.load.resource.gif.GifDrawable.LOOP_FOREVER);
                    setGifRunning(gif, true);
                    continue;
                }
            } else if (drawable instanceof android.graphics.drawable.Animatable) {
                setGifRunning(drawable, true);
                continue;
            }
            if (PawchiveRepository.isDirectMedia(item.url) && !holder.gifUpgradeAttempted
                    && (holder.gifThumbnailReady || item.imageUrl == null || item.imageUrl.isEmpty()))
                upgradeGif(holder, item);
        }
    }

    private void cancelGifResolution(Holder holder) {
        if (holder.gifResolution != null) {
            holder.gifResolution.cancel(true);
            holder.gifResolution = null;
            holder.gifUpgradeAttempted = false;
            holder.gifResolutionGeneration++;
        }
    }

    private void upgradeGif(Holder holder, NativeContentItem item) {
        holder.gifUpgradeAttempted = true;
        String original = PawchiveRepository.original(item);
        if (!original.isEmpty()) {
            loadOriginalGif(holder, item, original);
            return;
        }
        // Older saved records may lack the shard. Resolve the same way as fullscreen.
        int binding = holder.bindGeneration;
        int generation = ++holder.gifResolutionGeneration;
        holder.gifResolution = GIF_RESOLVER.submit(() -> {
            String resolved = "";
            try {
                CrazyShitRepository.StreamInfo info = PlayableSourceRouter.resolve(context, item);
                if (info != null && PawchiveRepository.isDeliveryFor(item.url, info.mediaUrl))
                    resolved = info.mediaUrl;
            } catch (Exception error) {
                PawchiveMediaRequests.trace(item.url, "grid GIF resolution failed");
            }
            final String delivery = resolved;
            holder.image.post(() -> {
                if (binding != holder.bindGeneration || generation != holder.gifResolutionGeneration) return;
                holder.gifResolution = null;
                if (!gifsMayRun() || !previewVisible(holder)) {
                    holder.gifUpgradeAttempted = false;
                    return;
                }
                if (!delivery.isEmpty()) loadOriginalGif(holder, item, delivery);
                else PawchiveMediaRequests.trace(item.url, "grid GIF missing original");
            });
        });
    }

    private void loadOriginalGif(Holder holder, NativeContentItem item, String original) {
        int binding = holder.bindGeneration;
        PawchiveMediaRequests.trace(item.url, "grid GIF original request");
        // The original is decoded at tile size. No separate overlay, custom target or file-size cap.
        RequestBuilder<Drawable> request = Glide.with(holder.image)
                .load(PawchiveOriginalImageLoader.model(context, item.url, original))
                // Require GIF decoding for this upgrade, but keep Drawable as the output type
                // so the normal static thumbnail remains a valid separate fallback request.
                .decode(com.bumptech.glide.load.resource.gif.GifDrawable.class)
                .disallowHardwareConfig()
                .dontTransform()
                .override(384, 384)
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .dontAnimate()
                .placeholder(new ColorDrawable(Color.rgb(20, 20, 23)))
                .listener(new RequestListener<Drawable>() {
                    @Override public boolean onLoadFailed(GlideException error, Object model,
                            Target<Drawable> target, boolean first) {
                        PawchiveMediaRequests.trace(item.url, "grid GIF Glide failed");
                        if (BuildConfig.DEBUG && error != null) {
                            // Log exception types only; remote URLs and response content stay private.
                            for (Throwable cause : error.getRootCauses())
                                PawchiveMediaRequests.trace(item.url,
                                        "grid GIF decoder cause " + cause.getClass().getSimpleName());
                        }
                        return false;
                    }
                    @Override public boolean onResourceReady(Drawable resource, Object model,
                            Target<Drawable> target, DataSource source, boolean first) {
                        if (binding != holder.bindGeneration) return true;
                        int frames = resource instanceof com.bumptech.glide.load.resource.gif.GifDrawable
                                ? ((com.bumptech.glide.load.resource.gif.GifDrawable) resource).getFrameCount() : 0;
                        PawchiveMediaRequests.trace(item.url, "grid GIF decoded frames " + frames + " " + source);
                        if (adaptiveAspectRatios) applyAspectRatio(holder, item, resource);
                        holder.unavailable.setVisibility(View.GONE);
                        holder.image.post(() -> {
                            if (binding == holder.bindGeneration) updateGifTiles();
                        });
                        return false;
                    }
                });
        if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
            RequestBuilder<Drawable> thumb = formatThumbnail(Glide.with(holder.image)
                    .load(withHeaders(item.imageUrl, imageReferer(item)))
                    .diskCacheStrategy(DiskCacheStrategy.ALL).override(384, 384).dontAnimate(), item);
            request = request.thumbnail(thumb).error(thumb.clone());
        }
        request.into(holder.image);
    }

    private void repairArtwork(Holder holder, NativeContentItem item, String rejected) {
        if (!BunkrRepository.isBunkrUrl(item.url)) return;
        holder.needsRepair = true;
        if (!previewVisible(holder) || holder.repairPending) return;
        holder.repairPending = true;
        int binding = holder.bindGeneration;
        boolean accepted = BunkrArtworkResolver.request(context, item.url, rejected == null ? "" : rejected, image -> {
            if (binding != holder.bindGeneration) return;
            holder.maintenance.refresh();
            holder.repairPending = false;
            if (!previewVisible(holder)) return;
            holder.needsRepair = false;
            if (!image.isEmpty()) loadArtwork(holder, item, image, false);
            else requestMediaPreview(holder, item);
        });
        if (!accepted) {
            holder.repairPending = false;
            holder.image.postDelayed(() -> {
                if (binding == holder.bindGeneration && previewVisible(holder)) repairArtwork(holder, item, rejected);
            }, 250L);
        }
    }

    private static boolean pawchiveVideoPreview(NativeContentItem item) {
        return item.isVideo() && !PawchiveRepository.original(item).isEmpty();
    }

    private void requestMediaPreview(Holder holder, NativeContentItem item) {
        boolean pawchive = PawchiveRepository.isNativeVideo(item);
        if (pawchive) {
            File saved = BunkrMediaPreview.cached(context, item);
            if (saved != null) loadMediaPreview(holder, item, saved);
            return;
        }
        if (!BunkrRepository.isBunkrUrl(item.url) || (!item.isImage() && !item.isVideo())) return;
        holder.wantsMediaPreview = true;
        if (!previewVisible(holder) || holder.previewAttempted) return;
        if (item.isVideo() && BunkrMaintenance.isKnown(item.url)) {
            holder.maintenance.refresh();
            return;
        }
        holder.previewAttempted = true;
        int binding = holder.bindGeneration;
        holder.preview = BunkrMediaPreview.request(context, item, file -> {
            if (binding != holder.bindGeneration || !previewVisible(holder)) return;
            holder.preview = null;
            holder.maintenance.refresh();
            if (file == null) {
                if (!pawchive) holder.unavailable.setVisibility(View.VISIBLE);
                else PawchiveMediaRequests.trace(item.url, "tile no poster");
                return;
            }
            loadMediaPreview(holder, item, file);
        });
    }

    private void showPlaybackPreview(String url) {
        for (Holder holder : previewHolders) {
            NativeContentItem item = holder.boundItem;
            if (item == null || !item.url.equals(url) || !previewVisible(holder)) continue;
            File cached = BunkrMediaPreview.cached(context, item);
            if (cached != null) {
                cancelPreview(holder);
                holder.previewAttempted = true;
                loadMediaPreview(holder, item, cached);
            }
        }
    }

    private void loadMediaPreview(Holder holder, NativeContentItem item, File file) {
        int binding = holder.bindGeneration;
        RequestBuilder<Drawable> preview = Glide.with(holder.image).load(file).override(384, 384)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .transition(ThumbnailFades.thumbnail())
                .listener(new RequestListener<Drawable>() {
                    @Override public boolean onLoadFailed(GlideException error, Object model,
                            Target<Drawable> target, boolean firstResource) {
                        if (binding == holder.bindGeneration) {
                            file.delete(); // Regenerate an evicted/corrupt entry on the next attachment.
                            holder.unavailable.setVisibility(View.VISIBLE);
                            if (PawchiveRepository.isDirectMedia(item.url))
                                PawchiveMediaRequests.trace(item.url, "tile Glide failed");
                        }
                        return false;
                    }
                    @Override public boolean onResourceReady(Drawable resource, Object model,
                            Target<Drawable> target, DataSource source, boolean firstResource) {
                        if (binding == holder.bindGeneration && PawchiveRepository.isDirectMedia(item.url))
                            PawchiveMediaRequests.trace(item.url, "tile displayed");
                        if (adaptiveAspectRatios && binding == holder.bindGeneration)
                            applyAspectRatio(holder, item, resource);
                        return false;
                    }
                });
        formatThumbnail(preview, item).into(holder.image);
    }

    private void cancelPreview(Holder holder) {
        if (holder.preview != null) holder.preview.cancel();
        holder.preview = null;
    }

    private boolean previewVisible(Holder holder) {
        if (!holder.attached || !previewsActive) return false;
        // Retained All/Pictures/Videos pages may be attached beyond the pager's clipped viewport.
        return previewViewport == null || (holder.itemView.isShown()
                && holder.itemView.getGlobalVisibleRect(previewBounds));
    }

    void setPreviewsActive(boolean active) {
        if (previewsActive == active) return;
        previewsActive = active;
        deferGifs();
        if (!active) stopFavoriteMoves();
        for (Holder holder : previewHolders) {
            if (!active) {
                cancelGifResolution(holder);
                cancelPreview(holder);
                holder.previewAttempted = false;
            } else if (holder.boundItem != null) {
                holder.maintenance.refresh();
                if (holder.wantsMediaPreview) requestMediaPreview(holder, holder.boundItem);
                else if (holder.needsRepair) repairArtwork(holder, holder.boundItem, holder.boundItem.imageUrl);
            }
        }
    }

    @Override public void onAttachedToRecyclerView(@NonNull RecyclerView recycler) {
        super.onAttachedToRecyclerView(recycler);
        previewViewport = recycler;
        recycler.addOnScrollListener(gifScroll);
        deferGifs();
        BunkrMediaPreview.addPlaybackPreviewListener(playbackPreviewListener);
        recycler.getViewTreeObserver().addOnPreDrawListener(previewVisibility);
    }

    @Override public void onDetachedFromRecyclerView(@NonNull RecyclerView recycler) {
        stopFavoriteMoves();
        pauseGifTiles();
        for (Holder holder : previewHolders) cancelGifResolution(holder);
        recycler.removeCallbacks(resumeGifs);
        recycler.removeOnScrollListener(gifScroll);
        BunkrMediaPreview.removePlaybackPreviewListener(playbackPreviewListener);
        recycler.getViewTreeObserver().removeOnPreDrawListener(previewVisibility);
        for (int i = 0; i < recycler.getChildCount(); i++) {
            RecyclerView.ViewHolder raw = recycler.getChildViewHolder(recycler.getChildAt(i));
            if (raw instanceof Holder) cancelPreview((Holder) raw);
        }
        previewViewport = null;
        super.onDetachedFromRecyclerView(recycler);
    }

    @Override public void onViewAttachedToWindow(@NonNull Holder holder) {
        super.onViewAttachedToWindow(holder);
        previewHolders.add(holder);
        holder.attached = true;
        NativeContentItem item = holder.boundItem;
        if (item == null) return;
        if (holder.wantsMediaPreview) requestMediaPreview(holder, item);
        else if (holder.needsRepair) repairArtwork(holder, item, item.imageUrl);
    }

    @Override public void onViewDetachedFromWindow(@NonNull Holder holder) {
        resetTileTransform(holder.itemView);
        previewHolders.remove(holder);
        setGifRunning(holder.image.getDrawable(), false);
        cancelGifResolution(holder);
        holder.attached = false;
        cancelPreview(holder);
        holder.previewAttempted = false;
        super.onViewDetachedFromWindow(holder);
    }

    private void applyOverlayDensity(Holder holder) {
        float scale = overlayScaleForColumns(gridColumns);

        holder.source.setPivotX(0f);
        holder.source.setPivotY(dp(holder.source, 26));
        holder.source.setScaleX(scale);
        holder.source.setScaleY(scale);

        holder.fresh.setPivotX(dp(holder.fresh, 26));
        holder.fresh.setPivotY(0f);
        holder.fresh.setScaleX(scale);
        holder.fresh.setScaleY(scale);

        holder.favorite.setPivotX(0f); holder.favorite.setPivotY(0f);
        holder.favorite.setScaleX(scale); holder.favorite.setScaleY(scale);

        holder.maintenance.setPivotY(0f);
        holder.maintenance.setScaleX(scale);
        holder.maintenance.setScaleY(scale);

        holder.gif.setPivotX(holder.gif.getWidth());
        holder.gif.setPivotY(holder.gif.getHeight());
        holder.gif.setScaleX(scale);
        holder.gif.setScaleY(scale);

        holder.play.setPivotX(dp(holder.play, 42));
        holder.play.setPivotY(dp(holder.play, 42));
        holder.play.setScaleX(scale);
        holder.play.setScaleY(scale);
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        resetTileTransform(holder.itemView);
        previewHolders.remove(holder);
        setGifRunning(holder.image.getDrawable(), false);
        cancelGifResolution(holder);
        holder.bindGeneration++;
        cancelPreview(holder);
        holder.boundItem = null;
        holder.maintenance.bind(null);
        holder.needsRepair = false;
        holder.wantsMediaPreview = false;
        GalleryMediaTransition.clearName(holder.image);
        Glide.with(holder.image).clear(holder.image);
        holder.morphPulse.animate().cancel();
        holder.morphPulse.setAlpha(0f);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private void addUnique(List<NativeContentItem> incoming) {
        if (incoming == null) return;
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (NativeContentItem item : items) existing.add(item.url);
        for (NativeContentItem candidate : incoming) {
            if (candidate == null || candidate.url == null || candidate.url.isEmpty()) continue;
            if (existing.add(candidate.url)) items.add(candidate);
        }
    }

    private void preloadRange(int start, int end) {
        for (int i = start; i < end; i++) {
            NativeContentItem item = items.get(i);
            if (item.imageUrl == null || item.imageUrl.isEmpty()) continue;
            if (BunkrRepository.isBunkrUrl(item.url) && !BunkrRepository.isUsableArtwork(item.imageUrl)) continue;
            String imageUrl = item.imageUrl.trim();
            if (imageUrl.isEmpty()) continue;
            String referer = imageReferer(item);
            String preloadKey = imageUrl + "\n" + (referer == null ? "" : referer.trim());
            if (!imagePreloads.markIfNew(preloadKey)) continue;
            RequestBuilder<Drawable> request = Glide.with(context)
                    .load(withHeaders(imageUrl, referer))
                    .diskCacheStrategy(DiskCacheStrategy.ALL);
            formatThumbnail(request, item).preload(adaptiveAspectRatios ? 384 : 360,
                    adaptiveAspectRatios ? 384 : 360);
        }
    }

    private RequestBuilder<Drawable> formatThumbnail(RequestBuilder<Drawable> request, NativeContentItem item) {
        if (item.isImage() && BunkrRepository.isBunkrUrl(item.url)) {
            // GIF and other animated drawables keep their existing presentation.
            return request.disallowHardwareConfig()
                    .optionalTransform(Bitmap.class, BUNKR_MATTE)
                    .optionalTransform(BitmapDrawable.class, BUNKR_MATTE_DRAWABLE);
        }
        return adaptiveAspectRatios ? request.dontTransform() : request.centerCrop();
    }

    private void applyAspectRatio(
            Holder holder,
            NativeContentItem item,
            Drawable resource
    ) {
        if (resource == null || resource.getIntrinsicWidth() <= 0 ||
                resource.getIntrinsicHeight() <= 0) return;
        float ratio = (float) resource.getIntrinsicWidth() / resource.getIntrinsicHeight();
        if (item.aspectRatioHint > 0f && !BunkrRepository.isBunkrUrl(item.url)) {
            ratio = item.aspectRatioHint;
        }
        if (!Float.isFinite(ratio) || ratio <= 0f) return;
        aspectRatios.put(item.url, ratio);
        // The request's binding generation is valid even before RecyclerView assigns a position.
        // A delayed decode changes this tile and its neighbors' layout coordinates.
        // An earlier favorite snapshot must not translate those new bounds.
        if (holder.tile.setAspectRatio(ratio)) stopFavoriteMoves();
    }

    private GlideUrl withHeaders(String imageUrl, String pageUrl) {
        if (PawchiveRepository.isArtwork(imageUrl) || PawchiveRepository.isDeliveryFor(pageUrl, imageUrl))
            return new GlideUrl(imageUrl, new LazyHeaders.Builder()
                    .setHeader("User-Agent", USER_AGENT).setHeader("Referer", PawchiveRepository.ORIGIN).build());
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", pageUrl == null
                        ? BunkrRepository.DEFAULT_PAGE_ORIGIN + "/" : pageUrl)
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        SourceConfig current = RemoteSourceConfigManager.snapshotOrNull();
        if (current != null && BunkrRepository.isBunkrUrl(pageUrl)) {
            SourceConfig.Bunkr config = current.bunkr;
            headers.setHeader("User-Agent", config.userAgent);
            if (!config.refererOverride.isEmpty()) headers.setHeader("Referer", config.refererOverride);
            for (Map.Entry<String, String> header : config.requestHeaders.entrySet())
                headers.setHeader(header.getKey(), header.getValue());
        }
        try {
            String cookies = CookieManager.getInstance().getCookie(imageUrl);
            if ((cookies == null || cookies.isEmpty()) && pageUrl != null) {
                cookies = CookieManager.getInstance().getCookie(pageUrl);
            }
            if (cookies != null && !cookies.isEmpty()) headers.addHeader("Cookie", cookies);
        } catch (Exception ignored) {
        }
        return new GlideUrl(imageUrl, headers.build());
    }

    private boolean isOnlyHavenImagePreview(NativeContentItem item) {
        return item != null && item.isImage() &&
                OnlyHavenRepository.isOnlyHavenUrl(item.uploader) &&
                item.imageUrl != null && !item.imageUrl.isEmpty() &&
                item.url != null && !item.url.equals(item.imageUrl);
    }

    private String imageReferer(NativeContentItem item) {
        String wikiFeetReferer = WikiFeetRepository.imageReferer(item);
        if (!wikiFeetReferer.isEmpty()) return wikiFeetReferer;
        if (item != null && !FapelloRepository.isPostUrl(item.url) &&
                FapelloRepository.isModelUrl(item.uploader)) return item.uploader;
        if (item != null && OnlyHavenRepository.isOnlyHavenUrl(item.uploader)) {
            return item.uploader;
        }
        return item == null ? null : item.url;
    }

    private SourceBadge sourceBadge(NativeContentItem item) {
        if (item == null) return null;
        if (PawchiveRepository.isDirectMedia(item.url))
            return new SourceBadge(R.drawable.ic_content_pawchive, "Pawchive", false);
        if (FapelloRepository.isFapelloUrl(item.url) ||
                FapelloRepository.isModelUrl(item.uploader)) {
            return new SourceBadge(R.drawable.ic_source_fapello, "Fapello", false);
        }
        if (OnlyHavenRepository.isOnlyHavenUrl(item.url) ||
                OnlyHavenRepository.isOnlyHavenUrl(item.uploader) ||
                containsIgnoreCase(item.description, "OnlyHaven")) {
            return new SourceBadge(R.drawable.ic_source_onlyhaven, "OnlyHaven", false);
        }
        if (WikiFeetRepository.isXItem(item)) {
            return new SourceBadge(R.drawable.ic_source_wikifeet, "WikiFeet X", true);
        }
        if (BunkrRepository.isBunkrUrl(item.url)) {
            return new SourceBadge(R.drawable.ic_source_bunkr, "Bunkr", false);
        }
        return null;
    }

    private boolean isHighlighted(NativeContentItem item) {
        if (item == null || highlightedUrls.isEmpty()) return false;
        for (String raw : highlightedUrls) if (CreatorUpdateIdentity.matches(item, raw)) return true;
        return false;
    }

    private String canonicalHighlightUrl(String value) {
        if (value == null) return "";
        String clean = value.trim();
        int fragment = clean.indexOf('#');
        if (fragment >= 0) clean = clean.substring(0, fragment);
        int query = clean.indexOf('?');
        if (query >= 0) clean = clean.substring(0, query);
        while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        return clean.toLowerCase(Locale.US);
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase(Locale.US).contains(query);
    }

    private static final class SourceBadge {
        final int drawableRes;
        final String name;
        final boolean wikiFeetX;

        SourceBadge(int drawableRes, String name, boolean wikiFeetX) {
            this.drawableRes = drawableRes;
            this.name = name;
            this.wikiFeetX = wikiFeetX;
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        int bindGeneration;
        boolean attached, needsRepair, repairPending, wantsMediaPreview, previewAttempted;
        NativeContentItem boundItem;
        BunkrMediaPreview.Task preview;
        boolean gifUpgradeAttempted, gifThumbnailReady;
        int gifResolutionGeneration;
        java.util.concurrent.Future<?> gifResolution;
        final AspectRatioFrameLayout tile;
        final ImageView image;
        final FrameLayout source;
        final ImageView sourceIcon;
        final TextView sourceVariant;
        final ImageView fresh;
        final View play;
        final View morphPulse;
        final TextView unavailable;
        final ImageView favorite;
        final BunkrMaintenanceUi.Badge maintenance;
        final TextView gif;

        Holder(
                AspectRatioFrameLayout itemView,
                ImageView image,
                FrameLayout source,
                ImageView sourceIcon,
                TextView sourceVariant,
                ImageView fresh,
                View play,
                View morphPulse,
                TextView unavailable, ImageView favorite, BunkrMaintenanceUi.Badge maintenance,
                TextView gif
        ) {
            super(itemView);
            this.tile = itemView;
            this.image = image;
            this.source = source;
            this.sourceIcon = sourceIcon;
            this.sourceVariant = sourceVariant;
            this.fresh = fresh;
            this.play = play;
            this.morphPulse = morphPulse;
            this.unavailable = unavailable;
            this.favorite = favorite;
            this.maintenance = maintenance;
            this.gif = gif;
        }
    }

    private static final class AspectRatioFrameLayout extends FrameLayout {
        private float aspectRatio = 1f;

        AspectRatioFrameLayout(Context context) {
            super(context);
        }

        boolean setAspectRatio(float next) {
            float safe = Float.isFinite(next) && next > 0f ? next : 1f;
            if (Math.abs(aspectRatio - safe) < 0.01f) return false;
            aspectRatio = safe;
            requestLayout();
            return true;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            if (width <= 0) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                return;
            }
            int height = Math.max(1, Math.round(width / aspectRatio));
            super.onMeasure(
                    widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            );
        }
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}
