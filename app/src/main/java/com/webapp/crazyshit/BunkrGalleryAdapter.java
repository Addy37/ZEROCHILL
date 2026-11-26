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
import android.widget.LinearLayout;
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

        default void onOmittedImage(NativeContentItem item) { }
        default void onBatchPublished() { }
    }

    private final ArrayList<NativeContentItem> items = new ArrayList<>();
    private final java.util.Set<String> omittedBlackImages = new java.util.HashSet<>();
    private int galleryGeneration;
    private GalleryThumbnailPipeline thumbnailPipeline;
    private final java.util.LinkedHashMap<String, NativeContentItem> pendingTiles = new java.util.LinkedHashMap<>();
    private final java.util.LinkedHashSet<String> batchInFlight = new java.util.LinkedHashSet<>();
    private final java.util.LinkedHashMap<String, GalleryThumbnailPipeline.Result> batchResults = new java.util.LinkedHashMap<>();
    private final android.os.Handler batchUi = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean batchEnabled, batchActive;
    private int lastVisible;
    private static final int BATCH_LIMIT = 6;
    private static final int SKELETON_TYPE = 1;
    private final Runnable flushBatch = this::publishPreparedBatch;
    private static final int MAX_REVEALS = 3;
    private int activeReveals;
    private long lastFastScroll = -GalleryRevealImageView.SETTLE_MS;
    private long lastRevealStart = -GalleryRevealImageView.STAGGER_MS;
    private long lastScrollEvent;
    private final Rect revealBounds = new Rect();
    private final Runnable revealTick = this::pumpReveals;
    private boolean revealTickPosted;
    private final RecyclerView.OnScrollListener revealScroll = new RecyclerView.OnScrollListener() {
        @Override public void onScrolled(@NonNull RecyclerView list, int dx, int dy) {
            if (dx == 0 && dy == 0) return;
            long now = android.os.SystemClock.uptimeMillis();
            long elapsed = lastScrollEvent == 0 ? 16L : Math.max(8L, Math.min(50L, now - lastScrollEvent));
            lastScrollEvent = now;
            float density = list.getResources().getDisplayMetrics().density;
            if (list.getScrollState() == RecyclerView.SCROLL_STATE_SETTLING
                    || Math.hypot(dx, dy) / Math.max(1f, density) > elapsed * .7f)
                lastFastScroll = now;
            scheduleRevealTick(0L);
        }
        @Override public void onScrollStateChanged(@NonNull RecyclerView list, int state) {
            if (state == RecyclerView.SCROLL_STATE_SETTLING)
                lastFastScroll = android.os.SystemClock.uptimeMillis();
            scheduleRevealTick(0L);
        }
    };

    private boolean revealVisible(Holder holder) {
        return previewVisible(holder) && holder.image.getHeight() > 0
                && holder.image.getGlobalVisibleRect(revealBounds)
                && revealBounds.height() >= holder.image.getHeight() * .55f
                && revealBounds.width() >= holder.image.getWidth() * .55f;
    }

    private void scheduleRevealTick(long delay) {
        if (!batchEnabled || !batchActive || !previewsActive || previewViewport == null
                || revealTickPosted) return;
        revealTickPosted = true;
        batchUi.postDelayed(revealTick, Math.max(0L, delay));
    }

    private void stopRevealTick() {
        batchUi.removeCallbacks(revealTick);
        revealTickPosted = false;
    }

    private void pumpReveals() {
        revealTickPosted = false;
        RecyclerView list = previewViewport;
        if (!batchEnabled || !batchActive || !previewsActive || list == null) return;
        ArrayList<Holder> candidates = new ArrayList<>();
        for (Holder holder : previewHolders)
            if (holder.revealPending && holder.revealAnimator == null
                    && holder.boundItem != null && revealVisible(holder)) candidates.add(holder);
        if (candidates.isEmpty()) return;
        if (!ZeroChillMotion.animationsEnabled(context)) {
            for (Holder holder : candidates) finishReveal(holder);
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        if (!GalleryRevealImageView.canStart(list.getScrollState(), now - lastFastScroll)) {
            if (list.getScrollState() != RecyclerView.SCROLL_STATE_SETTLING)
                scheduleRevealTick(GalleryRevealImageView.SETTLE_MS);
            return;
        }
        if (activeReveals >= MAX_REVEALS) return;
        long wait = GalleryRevealImageView.STAGGER_MS - (now - lastRevealStart);
        if (wait > 0L) { scheduleRevealTick(wait); return; }
        candidates.sort(java.util.Comparator.comparingInt((Holder h) -> h.itemView.getTop())
                .thenComparingInt(h -> h.itemView.getLeft()));
        Holder holder = candidates.get(0);
        if (thumbnailPipeline.hasRevealed(holder.boundItem.url)) {
            finishReveal(holder);
            scheduleRevealTick(0L);
            return;
        }
        lastRevealStart = now;
        activeReveals++;
        android.animation.ValueAnimator animation = android.animation.ValueAnimator.ofFloat(0f, 1f);
        holder.revealAnimator = animation;
        animation.setDuration(GalleryRevealImageView.DURATION_MS);
        animation.setInterpolator(new android.view.animation.PathInterpolator(.25f, .1f, .25f, 1f));
        animation.addUpdateListener(value ->
                holder.image.setRevealProgress((float) value.getAnimatedValue()));
        animation.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator ignored) {
                if (holder.revealAnimator != animation) return;
                holder.revealAnimator = null;
                activeReveals--;
                finishReveal(holder);
                scheduleRevealTick(0L);
            }
        });
        animation.start();
        scheduleRevealTick(GalleryRevealImageView.STAGGER_MS);
    }

    private void finishReveal(Holder holder) {
        holder.revealPending = false;
        holder.image.setRevealProgress(1f);
        if (holder.boundItem != null && thumbnailPipeline != null)
            thumbnailPipeline.markRevealed(holder.boundItem.url);
    }

    private void cancelReveal(Holder holder, boolean preservePending) {
        android.animation.ValueAnimator animation = holder.revealAnimator;
        if (animation != null) {
            holder.revealAnimator = null;
            activeReveals--;
            animation.cancel();
        }
        holder.revealPending &= preservePending;
        holder.image.setRevealProgress(holder.revealPending ? 0f : 1f);
    }

    private void queueReveal(Holder holder, NativeContentItem item) {
        if (!batchEnabled || thumbnailPipeline.hasRevealed(item.url)
                || !ZeroChillMotion.animationsEnabled(context)) {
            finishReveal(holder);
            return;
        }
        holder.revealPending = true;
        holder.image.setRevealProgress(0f);
        scheduleRevealTick(0L);
    }

    void setThumbnailPipeline(GalleryThumbnailPipeline pipeline) { thumbnailPipeline = pipeline; }
    void setBatchEnabled(boolean enabled) { batchEnabled = enabled; }
    void setBatchActive(boolean active) {
        batchActive = active;
        if (active) {
            scheduleBatch();
            scheduleRevealTick(0L);
        } else {
            stopRevealTick();
            for (Holder holder : previewHolders) cancelReveal(holder, true);
        }
    }
    boolean hasPendingThumbnails() { return !pendingTiles.isEmpty(); }
    static int skeletonCount(int pending, int columns) {
        return Math.min(Math.max(0, pending), Math.min(6, Math.max(2, columns * 2)));
    }
    private int skeletonCount() { return skeletonCount(pendingTiles.size(), gridColumns); }
    private void resetBatch() {
        batchUi.removeCallbacks(flushBatch);
        if (thumbnailPipeline != null) thumbnailPipeline.unsubscribe(this);
        pendingTiles.clear(); batchInFlight.clear(); batchResults.clear();
        lastVisible = 0;
    }
    private void scheduleBatch() {
        if (!batchEnabled || !batchActive || !previewsActive || thumbnailPipeline == null
                || pendingTiles.isEmpty() || !batchInFlight.isEmpty() || !batchResults.isEmpty()
                || items.size() - lastVisible > Math.max(12, gridColumns * 4)) return;
        int generation = galleryGeneration;
        int count = 0;
        for (NativeContentItem item : new ArrayList<>(pendingTiles.values())) {
            if (count++ >= BATCH_LIMIT) break;
            batchInFlight.add(item.url);
            requestPreparedThumbnail(item, true, this, result -> {
                if (generation != galleryGeneration || !batchInFlight.remove(item.url)) return;
                if (pendingTiles.containsKey(item.url)) batchResults.put(item.url, result);
                // Publish completed items together, without waiting for the slowest server response.
                batchUi.removeCallbacks(flushBatch);
                batchUi.postDelayed(flushBatch, batchInFlight.isEmpty() ? 0L : 120L);
            });
        }
    }

    private void publishPreparedBatch() {
        if (batchResults.isEmpty()) { scheduleBatch(); return; }
        int oldSkeletons = skeletonCount();
        int oldSize = items.size();
        ArrayList<NativeContentItem> rejected = new ArrayList<>();
        for (Map.Entry<String, GalleryThumbnailPipeline.Result> entry : batchResults.entrySet()) {
            NativeContentItem item = pendingTiles.remove(entry.getKey());
            if (item == null) continue;
            if (entry.getValue().state == GalleryThumbnailPipeline.State.OMIT) {
                omittedBlackImages.add(item.url);
                rejected.add(item);
            } else items.add(item);
        }
        batchResults.clear();
        // The ready portion of the grid never gets rebound or rearranged for a failed candidate.
        if (oldSkeletons > 0) notifyItemRangeRemoved(oldSize, oldSkeletons);
        if (items.size() > oldSize) notifyItemRangeInserted(oldSize, items.size() - oldSize);
        int skeletons = skeletonCount();
        if (skeletons > 0) notifyItemRangeInserted(items.size(), skeletons);
        for (NativeContentItem item : rejected) listener.onOmittedImage(item);
        listener.onBatchPublished();
        scheduleBatch();
    }

    void omitBlackImages(Collection<String> urls) {
        int oldSkeletons = skeletonCount();
        omittedBlackImages.addAll(urls);
        for (String url : urls) {
            pendingTiles.remove(url);
            batchResults.remove(url);
        }
        int newSkeletons = skeletonCount();
        if (oldSkeletons > newSkeletons)
            notifyItemRangeRemoved(items.size() + newSkeletons, oldSkeletons - newSkeletons);
        stopFavoriteMoves();
        for (int end = items.size() - 1; end >= 0;) {
            if (!items.get(end).isImage() || !omittedBlackImages.contains(items.get(end).url)) {
                end--; continue;
            }
            int start = end;
            while (start > 0 && items.get(start - 1).isImage()
                    && omittedBlackImages.contains(items.get(start - 1).url)) start--;
            items.subList(start, end + 1).clear();
            notifyItemRangeRemoved(start, end - start + 1);
            end = start - 1;
        }
    }

    void resetBlackImageOmissions() {
        galleryGeneration++;
        omittedBlackImages.clear();
        resetBatch();
    }

    void omitBlackImage(String url) {
        if (url == null || url.isEmpty()) return;
        omittedBlackImages.add(url);
        int position = indexOfUrl(url);
        if (position < 0 || !items.get(position).isImage()) return;
        stopFavoriteMoves();
        items.remove(position);
        notifyItemRemoved(position);
    }
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
        if (batchEnabled && batchActive) {
            for (Holder holder : previewHolders) {
                if (holder.revealAnimator != null && !previewVisible(holder))
                    cancelReveal(holder, true);
                if (holder.revealPending && revealVisible(holder)) scheduleRevealTick(0L);
            }
        }
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
        galleryGeneration++;
        resetBatch();
        items.clear();
        normalOrder.clear();
        addUnique(incoming);
        if (incoming != null) for (NativeContentItem item : incoming)
            if (item != null) normalOrder.putIfAbsent(item.url, normalOrder.size());
        rememberAndSort();
        notifyDataSetChanged();
        if (preloadAhead) {
            preloadRange(0, Math.min(items.size(), adaptiveAspectRatios ? 6 : 12));
            scheduleBatch();
        }
    }

    void append(List<NativeContentItem> incoming) {
        append(incoming, true);
    }

    void append(List<NativeContentItem> incoming, boolean preloadAhead) {
        int start = items.size();
        int oldSkeletons = skeletonCount();
        addUnique(incoming);
        if (oldSkeletons > 0) notifyItemRangeRemoved(start, oldSkeletons);
        if (incoming != null) for (NativeContentItem item : incoming)
            if (item != null) normalOrder.putIfAbsent(item.url, normalOrder.size());
        int added = items.size() - start;
        if (added > 0) {
            notifyItemRangeInserted(start, added);
            if (preloadAhead) preloadRange(start, Math.min(items.size(), start + (adaptiveAspectRatios ? 6 : 12)));
        }
        if (skeletonCount() > 0) notifyItemRangeInserted(items.size(), skeletonCount());
        reorderFavorites();
        if (preloadAhead) scheduleBatch();
    }

    void appendPinned(List<NativeContentItem> incoming) {
        int start = items.size();
        int oldSkeletons = skeletonCount();
        addUnique(incoming);
        if (oldSkeletons > 0) notifyItemRangeRemoved(start, oldSkeletons);
        if (items.size() > start) notifyItemRangeInserted(start, items.size() - start);
        if (skeletonCount() > 0) notifyItemRangeInserted(items.size(), skeletonCount());
        reorderFavorites();
        scheduleBatch();
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
        lastVisible = Math.max(0, last);
        scheduleBatch();
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
        result.addAll(pendingTiles.values());
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
        return items.size() + pendingTiles.size();
    }

    void setGridColumns(int columns) {
        int next = Math.max(1, columns);
        if (gridColumns == next) return;
        int oldSkeletons = skeletonCount();
        gridColumns = next;
        int newSkeletons = skeletonCount();
        if (oldSkeletons > newSkeletons) notifyItemRangeRemoved(items.size() + newSkeletons, oldSkeletons - newSkeletons);
        else if (newSkeletons > oldSkeletons) notifyItemRangeInserted(items.size() + oldSkeletons, newSkeletons - oldSkeletons);
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
        return position < items.size() ? items.get(position).url.hashCode()
                : Long.MIN_VALUE + position - items.size();
    }

    @Override public int getItemViewType(int position) {
        return position < items.size() ? 0 : SKELETON_TYPE;
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

        GalleryRevealImageView image = new GalleryRevealImageView(parent.getContext());
        image.setBackgroundColor(Color.rgb(20, 20, 23));
        tile.addView(image, new FrameLayout.LayoutParams(-1, -1));

        android.widget.ProgressBar preparing = new android.widget.ProgressBar(parent.getContext());
        preparing.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(
                AppearanceTheme.accent(parent.getContext())));
        FrameLayout.LayoutParams preparingParams = new FrameLayout.LayoutParams(dp(parent, 22), dp(parent, 22),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        preparingParams.topMargin = dp(parent, 12);
        preparing.setVisibility(View.GONE);
        tile.addView(preparing, preparingParams);

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

        LinearLayout play = new LinearLayout(parent.getContext());
        play.setOrientation(LinearLayout.HORIZONTAL);
        play.setGravity(Gravity.CENTER);
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
        play.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            v.setPivotX(Math.max(1, v.getWidth()));
            v.setPivotY(Math.max(1, v.getHeight()));
        });

        ImageView playIcon = new ImageView(parent.getContext());
        playIcon.setImageResource(R.drawable.ic_player_play);
        playIcon.setColorFilter(Color.WHITE);
        playIcon.setPadding(dp(parent, 11), dp(parent, 11), dp(parent, 9), dp(parent, 11));
        play.addView(playIcon, new LinearLayout.LayoutParams(dp(parent, 42), dp(parent, 42)));

        TextView playDuration = new TextView(parent.getContext());
        playDuration.setTextColor(Color.WHITE);
        playDuration.setTextSize(11);
        playDuration.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        playDuration.setIncludeFontPadding(false);
        playDuration.setSingleLine(true);
        playDuration.setGravity(Gravity.CENTER_VERTICAL);
        playDuration.setVisibility(View.GONE);
        play.addView(playDuration, new LinearLayout.LayoutParams(-2, -2));

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
                playIcon,
                playDuration,
                morphPulse,
                unavailable, favorite, maintenance, gif, preparing
        );
    }

    @Override
    public void onBindViewHolder(
            @NonNull Holder holder,
            int position,
            @NonNull List<Object> payloads
    ) {
        if (position >= items.size()) { onBindViewHolder(holder, position); return; }
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
        cancelReveal(holder, false);
        resetTileTransform(holder.itemView);
        holder.morphPulse.animate().cancel();
        holder.morphPulse.setAlpha(0f);
        if (position >= items.size()) {
            holder.bindGeneration++;
            holder.boundItem = null;
            Glide.with(holder.image).clear(holder.image);
            holder.tile.setAspectRatio(1f);
            for (int i = 0; i < holder.tile.getChildCount(); i++)
                holder.tile.getChildAt(i).setVisibility(View.GONE);
            holder.image.setVisibility(View.VISIBLE);
            holder.image.setImageDrawable(new ColorDrawable(Color.rgb(30, 34, 40)));
            holder.itemView.setContentDescription("Preparing media");
            holder.itemView.setOnClickListener(null);
            holder.itemView.setOnLongClickListener(null);
            holder.itemView.setClickable(false);
            holder.itemView.setLongClickable(false);
            return;
        }
        NativeContentItem item = items.get(position);
        holder.favorite.setVisibility(favoriteUrls.contains(item.url) ? View.VISIBLE : View.GONE);
        holder.maintenance.bind(item);
        ViewCompat.setTransitionName(holder.image, GalleryMediaTransition.transitionName(item));
        holder.image.setScaleType(adaptiveAspectRatios && !BunkrRepository.isBunkrUrl(item.url)
                ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
        float cachedAspect = aspectRatios.containsKey(item.url) ? aspectRatios.get(item.url) : 0f;
        holder.tile.setAspectRatio(adaptiveAspectRatios
                ? initialTileAspect(item.url, item.aspectRatioHint, cachedAspect,
                        EromeRepository.gridArtwork(item))
                : 1f);
        bindPlayBadge(holder, item);
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
        String duration = GalleryDuration.label(item);
        holder.itemView.setContentDescription(
                (item.isVideo() ? "Video, " : item.isGif() ? "GIF, " : "Photo, ") + item.title +
                        (duration.isEmpty() ? "" : ", " + duration) +
                        (source == null ? "" : ", source " + source.name) +
                        (fresh ? ", new from Updates" : "")
        );

        holder.bindGeneration++;
        if (thumbnailPipeline != null) thumbnailPipeline.unsubscribe(holder);
        holder.preparing.setVisibility(View.GONE);
        holder.unavailable.setVisibility(View.GONE);
        cancelPreview(holder);
        holder.boundItem = item;
        holder.needsRepair = false;
        holder.repairPending = false;
        holder.wantsMediaPreview = false;
        holder.previewAttempted = false;
        holder.failedArtwork.clear();
        boolean pawchiveVideo = pawchiveVideoPreview(item);
        File cachedPreview = BunkrRepository.isBunkrUrl(item.url) || pawchiveVideo
                ? BunkrMediaPreview.cached(context, item) : null;
        String eromeArtwork = EromeRepository.gridArtwork(item);
        if (thumbnailPipeline != null && GalleryThumbnailPipeline.supports(item)) {
            prepareThumbnail(holder, item);
        } else if (!eromeArtwork.isEmpty()) {
            loadArtwork(holder, item, eromeArtwork, true);
        } else if (cachedPreview != null) {
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

    private void prepareThumbnail(Holder holder, NativeContentItem item) {
        int binding = holder.bindGeneration;
        int generation = galleryGeneration;
        Glide.with(holder.image).clear(holder.image);
        holder.image.setImageDrawable(new ColorDrawable(batchEnabled
                ? GalleryRevealImageView.MATTE : Color.rgb(20, 20, 23)));
        // The tail skeleton owns loading. A rare cache miss uses a quiet matte, never a spinner.
        holder.preparing.setVisibility(View.GONE);
        requestPreparedThumbnail(item, true, holder, result -> {
            if (binding != holder.bindGeneration || generation != galleryGeneration) return;
            if (result.state == GalleryThumbnailPipeline.State.OMIT) {
                listener.onOmittedImage(item);
                return;
            }
            if (result.state == GalleryThumbnailPipeline.State.UNAVAILABLE) {
                holder.preparing.setVisibility(View.GONE);
                holder.image.setImageDrawable(new ColorDrawable(Color.rgb(30, 34, 40)));
                holder.unavailable.setVisibility(item.isVideo() ? View.GONE : View.VISIBLE);
                return;
            }
            RequestBuilder<Drawable> display =
                    GalleryThumbnailPipeline.request(Glide.with(holder.image), result.model, result.signature);
            if (batchEnabled) display = display.dontAnimate();
            else display = display.transition(ThumbnailFades.thumbnail());
            display.placeholder(new ColorDrawable(batchEnabled
                    ? GalleryRevealImageView.MATTE : Color.rgb(20, 20, 23)))
                    .listener(new RequestListener<Drawable>() {
                        @Override public boolean onLoadFailed(GlideException error, Object model,
                                Target<Drawable> target, boolean first) {
                            if (binding != holder.bindGeneration || generation != galleryGeneration) return true;
                            thumbnailPipeline.invalidateReady(item.url);
                            cancelReveal(holder, false);
                            holder.preparing.setVisibility(View.GONE);
                            holder.unavailable.setVisibility(View.VISIBLE);
                            return false;
                        }
                        @Override public boolean onResourceReady(Drawable resource, Object model,
                                Target<Drawable> target, DataSource source, boolean first) {
                            if (binding != holder.bindGeneration || generation != galleryGeneration) return true;
                            if (adaptiveAspectRatios) applyAspectRatio(holder, item, resource);
                            holder.preparing.setVisibility(View.GONE);
                            holder.unavailable.setVisibility(View.GONE);
                            if (batchEnabled) queueReveal(holder, item);
                            return false;
                        }
                    }).into(holder.image);
        });
    }

    private void requestPreparedThumbnail(NativeContentItem item, boolean visible,
            Object subscriber, java.util.function.Consumer<GalleryThumbnailPipeline.Result> callback) {
        String referer = imageReferer(item);
        GlideUrl original = GalleryBlackImage.eligible(item) ? withHeaders(item.url, referer) : null;
        GlideUrl preview = item.imageUrl.isEmpty() || item.imageUrl.equals(item.url)
                ? null : withHeaders(item.imageUrl, referer);
        thumbnailPipeline.prepare(item, preview, original, visible, subscriber, callback);
    }

    private void loadArtwork(Holder holder, NativeContentItem item, String url, boolean repair) {
        loadArtwork(holder, item, url, repair, false, false);
    }

    private void loadArtwork(Holder holder, NativeContentItem item, String url,
            boolean repair, boolean verifyOriginal, boolean finalOriginalAttempt) {
        int binding = holder.bindGeneration;
        int generation = galleryGeneration;
        int artworkRequest = ++holder.artworkRequest;
        RequestBuilder<Drawable> request = Glide.with(holder.image)
                .load(withHeaders(url, imageReferer(item)))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transition(ThumbnailFades.thumbnail())
                .placeholder(new ColorDrawable(Color.rgb(20, 20, 23)))
                .error(new ColorDrawable(Color.rgb(20, 20, 23)))
                .listener(new RequestListener<Drawable>() {
                    @Override public boolean onLoadFailed(GlideException error, Object model,
                            Target<Drawable> target, boolean firstResource) {
                        if (binding == holder.bindGeneration)
                            GalleryDiagnostics.imageFailure(finalOriginalAttempt ? "grid_original_retry"
                                    : verifyOriginal ? "grid_original_check" : "grid_artwork",
                                    item, url, imageReferer(item), model, error);
                        // Glide forbids starting another request synchronously inside its listener.
                        holder.image.post(() -> {
                            if (!currentArtworkRequest(holder, item, binding, generation, artworkRequest)) return;
                            if (handleOriginalFailure(holder, item, url, binding, generation,
                                    artworkRequest, finalOriginalAttempt)) return;
                            if (verifyOriginal) {
                                holder.unavailable.setVisibility(View.VISIBLE);
                                return;
                            }
                            if (PawchiveRepository.isDirectMedia(item.url)) {
                                holder.unavailable.setVisibility(View.VISIBLE);
                                return;
                            }
                            if (EromeRepository.isAlbumUrl(item.url)) {
                                if (!holder.failedArtwork.contains(url)) holder.failedArtwork.add(url);
                                String next = EromeRepository.nextArtwork(item, holder.failedArtwork);
                                if (!next.isEmpty()) {
                                    loadArtwork(holder, item, next, false);
                                    return;
                                }
                            }
                            if (repair) repairArtwork(holder, item, url);
                            else requestMediaPreview(holder, item);
                        });
                        return false;
                    }
                    @Override public boolean onResourceReady(Drawable resource, Object model,
                            Target<Drawable> target, DataSource dataSource, boolean firstResource) {
                        if (binding == holder.bindGeneration)
                            GalleryDiagnostics.imageReady("grid_artwork", item, url,
                                    resource.getIntrinsicWidth(), resource.getIntrinsicHeight());
                        if (adaptiveAspectRatios && binding == holder.bindGeneration)
                            applyAspectRatio(holder, item, resource);
                        if (binding == holder.bindGeneration && artworkRequest == holder.artworkRequest) {
                            holder.unavailable.setVisibility(View.GONE);
                            inspectBlackImage(holder, item, resource, binding, generation, artworkRequest, verifyOriginal);
                        }
                        return false;
                    }
                });
        int decodeSize = adaptiveAspectRatios ? 384 : 360;
        request = formatThumbnail(request.override(decodeSize, decodeSize), item);
        if (GalleryBlackImage.eligible(item)) request = request.disallowHardwareConfig();
        if (verifyOriginal) request = request.dontTransform().disallowHardwareConfig()
                .skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE);
        if (!verifyOriginal && isOnlyHavenImagePreview(item)) {
            RequestBuilder<Drawable> fallback = Glide.with(holder.image)
                    .load(withHeaders(item.url, imageReferer(item)))
                    .diskCacheStrategy(DiskCacheStrategy.ALL).dontAnimate()
                    .transition(ThumbnailFades.thumbnail());
            fallback = adaptiveAspectRatios ? fallback.dontTransform().override(384, 384)
                    : fallback.centerCrop().override(360, 360);
            if (GalleryBlackImage.eligible(item)) fallback = fallback.disallowHardwareConfig();
            fallback = fallback.listener(new RequestListener<Drawable>() {
                @Override public boolean onLoadFailed(GlideException error, Object model,
                        Target<Drawable> target, boolean firstResource) {
                    if (binding == holder.bindGeneration)
                        GalleryDiagnostics.imageFailure("grid_original_fallback", item, item.url, imageReferer(item), model, error);
                    holder.image.post(() -> {
                        if (!currentArtworkRequest(holder, item, binding, generation, artworkRequest)) return;
                        handleOriginalFailure(holder, item, item.url, binding, generation, artworkRequest, false);
                    });
                    return false;
                }
                @Override public boolean onResourceReady(Drawable resource, Object model,
                        Target<Drawable> target, DataSource source, boolean firstResource) {
                    if (binding == holder.bindGeneration)
                        GalleryDiagnostics.imageReady("grid_original_fallback", item, item.url,
                                resource.getIntrinsicWidth(), resource.getIntrinsicHeight());
                    if (adaptiveAspectRatios && binding == holder.bindGeneration)
                        applyAspectRatio(holder, item, resource);
                    if (binding == holder.bindGeneration && artworkRequest == holder.artworkRequest) {
                        holder.unavailable.setVisibility(View.GONE);
                        inspectBlackImage(holder, item, resource, binding, generation, artworkRequest, false);
                    }
                    return false;
                }
            });
            request = request.error(fallback);
        }
        request.into(holder.image);
    }

    private boolean currentArtworkRequest(Holder holder, NativeContentItem item,
            int binding, int generation, int request) {
        return binding == holder.bindGeneration && generation == galleryGeneration
                && request == holder.artworkRequest && previewViewport != null
                && holder.boundItem != null && item.url.equals(holder.boundItem.url)
                && indexOfUrl(item.url) >= 0;
    }

    private boolean handleOriginalFailure(Holder holder, NativeContentItem item, String requested,
            int binding, int generation, int request, boolean finalAttempt) {
        GalleryBlackImage.FailureAction action =
                GalleryBlackImage.originalFailureAction(item, requested, finalAttempt);
        if (action == GalleryBlackImage.FailureAction.KEEP) return false;
        holder.unavailable.setVisibility(View.VISIBLE);
        if (action == GalleryBlackImage.FailureAction.RETRY) {
            // One fresh original request, bypassing Glide's caches. Never loop on a server outage.
            holder.image.postDelayed(() -> {
                if (currentArtworkRequest(holder, item, binding, generation, request))
                    loadArtwork(holder, item, item.url, false, true, true);
            }, 650L);
        } else {
            GalleryDiagnostics.unavailableImage(item);
            omitBlackImage(item.url);
            listener.onOmittedImage(item);
        }
        return true;
    }

    private void inspectBlackImage(Holder holder, NativeContentItem item, Drawable resource,
            int binding, int generation, int request, boolean verifiedOriginal) {
        if (!GalleryBlackImage.eligible(item)) return;
        GalleryBlackImage.check(resource, black -> holder.image.post(() -> {
            if (!black || !currentArtworkRequest(holder, item, binding, generation, request)) return;
            if (!verifiedOriginal) {
                // A blank thumbnail alone is not evidence that its original is blank.
                loadArtwork(holder, item, item.url, false, true, false);
                return;
            }
            GalleryDiagnostics.blackImage(item);
            omitBlackImage(item.url);
            listener.onOmittedImage(item);
        }));
    }

    private void repairArtwork(Holder holder, NativeContentItem item, String rejected) {
        if (EromeRepository.isAlbumUrl(item == null ? "" : item.url)) {
            String next = EromeRepository.alternateArtwork(item, rejected);
            if (!next.isEmpty()) loadArtwork(holder, item, next, false);
            return;
        }
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
        if (!active) {
            stopFavoriteMoves();
            stopRevealTick();
            for (Holder holder : previewHolders) cancelReveal(holder, true);
        } else {
            scheduleBatch();
            scheduleRevealTick(0L);
        }
        for (Holder holder : previewHolders) {
            if (!active) {
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
        BunkrMediaPreview.addPlaybackPreviewListener(playbackPreviewListener);
        recycler.getViewTreeObserver().addOnPreDrawListener(previewVisibility);
        recycler.addOnScrollListener(revealScroll);
    }

    @Override public void onDetachedFromRecyclerView(@NonNull RecyclerView recycler) {
        stopFavoriteMoves();
        BunkrMediaPreview.removePlaybackPreviewListener(playbackPreviewListener);
        recycler.getViewTreeObserver().removeOnPreDrawListener(previewVisibility);
        recycler.removeOnScrollListener(revealScroll);
        stopRevealTick();
        for (Holder holder : previewHolders) cancelReveal(holder, true);
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
        if (holder.revealPending) scheduleRevealTick(0L);
        NativeContentItem item = holder.boundItem;
        if (item == null) return;
        if (holder.wantsMediaPreview) requestMediaPreview(holder, item);
        else if (holder.needsRepair) repairArtwork(holder, item, item.imageUrl);
    }

    @Override public void onViewDetachedFromWindow(@NonNull Holder holder) {
        resetTileTransform(holder.itemView);
        previewHolders.remove(holder);
        holder.attached = false;
        cancelReveal(holder, true);
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

        holder.play.setPivotX(Math.max(1, holder.play.getWidth()));
        holder.play.setPivotY(Math.max(1, holder.play.getHeight()));
        holder.play.setScaleX(scale);
        holder.play.setScaleY(scale);
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        cancelReveal(holder, false);
        resetTileTransform(holder.itemView);
        previewHolders.remove(holder);
        holder.bindGeneration++;
        if (thumbnailPipeline != null) thumbnailPipeline.unsubscribe(holder);
        holder.preparing.setVisibility(View.GONE);
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
        return items.size() + skeletonCount();
    }

    private void addUnique(List<NativeContentItem> incoming) {
        if (incoming == null) return;
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (NativeContentItem item : items) existing.add(item.url);
        existing.addAll(pendingTiles.keySet());
        for (NativeContentItem candidate : incoming) {
            if (candidate == null || candidate.url == null || candidate.url.isEmpty()) continue;
            if (candidate.isImage() && omittedBlackImages.contains(candidate.url)) continue;
            if (!existing.add(candidate.url)) continue;
            if (batchEnabled && thumbnailPipeline != null && GalleryThumbnailPipeline.supports(candidate)) {
                GalleryThumbnailPipeline.Result cached = thumbnailPipeline.cached(candidate.url);
                if (cached == null) pendingTiles.put(candidate.url, candidate);
                else if (cached.state != GalleryThumbnailPipeline.State.OMIT) items.add(candidate);
                else omittedBlackImages.add(candidate.url);
            } else items.add(candidate);
        }
    }

    private void preloadRange(int start, int end) {
        for (int i = start; i < end; i++) {
            NativeContentItem item = items.get(i);
            if (thumbnailPipeline != null && GalleryThumbnailPipeline.supports(item)) {
                if (batchEnabled) continue;
                int generation = galleryGeneration;
                requestPreparedThumbnail(item, false, this, result -> {
                    if (generation == galleryGeneration && result.state == GalleryThumbnailPipeline.State.OMIT)
                        listener.onOmittedImage(item);
                });
                continue;
            }
            String imageUrl = EromeRepository.gridArtwork(item);
            if (imageUrl.isEmpty()) imageUrl = item.imageUrl == null ? "" : item.imageUrl.trim();
            if (imageUrl.isEmpty()) continue;
            if (BunkrRepository.isBunkrUrl(item.url) && !BunkrRepository.isUsableArtwork(item.imageUrl)) continue;
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

    private void bindPlayBadge(Holder holder, NativeContentItem item) {
        String duration = GalleryDuration.label(item);
        boolean video = item.isVideo();
        holder.play.setVisibility(video ? View.VISIBLE : View.GONE);
        if (!video) {
            holder.playDuration.setVisibility(View.GONE);
            holder.playDuration.setText("");
            return;
        }
        FrameLayout.LayoutParams badge = (FrameLayout.LayoutParams) holder.play.getLayoutParams();
        LinearLayout.LayoutParams icon = (LinearLayout.LayoutParams) holder.playIcon.getLayoutParams();
        if (duration.isEmpty()) {
            holder.playDuration.setVisibility(View.GONE);
            holder.playDuration.setText("");
            holder.play.setPadding(0, 0, 0, 0);
            int size = dp(holder.play, 42);
            badge.width = size;
            badge.height = size;
            icon.width = size;
            icon.height = size;
            holder.playIcon.setPadding(dp(holder.play, 11), dp(holder.play, 11),
                    dp(holder.play, 9), dp(holder.play, 11));
        } else {
            holder.playDuration.setText(duration);
            holder.playDuration.setVisibility(View.VISIBLE);
            int padV = dp(holder.play, 4);
            holder.play.setPadding(dp(holder.play, 5), padV, dp(holder.play, 7), padV);
            badge.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            badge.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            int iconSize = dp(holder.play, 16);
            icon.width = iconSize;
            icon.height = iconSize;
            holder.playIcon.setPadding(dp(holder.play, 2), dp(holder.play, 2),
                    dp(holder.play, 1), dp(holder.play, 2));
            holder.playDuration.setPadding(dp(holder.play, 3), 0, 0, 0);
        }
        holder.play.setLayoutParams(badge);
        holder.playIcon.setLayoutParams(icon);
    }

    /** First layout pass. A square thumb fallback starts square. The real poster uses the site's shape. */
    static float initialTileAspect(String url, float hint, float cached, String artworkUrl) {
        if (cached > 0f && Float.isFinite(cached)) return cached;
        if (EromeRepository.isAlbumUrl(url) && artworkUrl != null && artworkUrl.contains("/thumbs/")) {
            return 1f;
        }
        if (hint > 0f && Float.isFinite(hint)) return hint;
        return 1f;
    }

    /**
     * Erome tiles follow the artwork that is actually on screen.
     * Other galleries keep their source hint so a square preview cannot flatten a portrait photo.
     */
    static float displayedTileAspect(String url, float hint, int decodedWidth, int decodedHeight) {
        float decoded = 0f;
        if (decodedWidth > 0 && decodedHeight > 0) {
            decoded = (float) decodedWidth / (float) decodedHeight;
            if (!Float.isFinite(decoded) || decoded <= 0.2f || decoded > 5f) decoded = 0f;
        }
        if (EromeRepository.isAlbumUrl(url) && decoded > 0f) return decoded;
        if (hint > 0f && Float.isFinite(hint) && !BunkrRepository.isBunkrUrl(url)) return hint;
        if (decoded > 0f) return decoded;
        return hint > 0f && Float.isFinite(hint) ? hint : 1f;
    }

    private void applyAspectRatio(
            Holder holder,
            NativeContentItem item,
            Drawable resource
    ) {
        if (resource == null || resource.getIntrinsicWidth() <= 0 ||
                resource.getIntrinsicHeight() <= 0) return;
        float ratio = displayedTileAspect(
                item.url,
                item.aspectRatioHint,
                resource.getIntrinsicWidth(),
                resource.getIntrinsicHeight()
        );
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
        String eromeReferer = EromeRepository.imageReferer(item);
        if (!eromeReferer.isEmpty()) return eromeReferer;
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
        int artworkRequest;
        boolean revealPending;
        android.animation.ValueAnimator revealAnimator;
        boolean attached, needsRepair, repairPending, wantsMediaPreview, previewAttempted;
        NativeContentItem boundItem;
        final ArrayList<String> failedArtwork = new ArrayList<>();
        BunkrMediaPreview.Task preview;
        final AspectRatioFrameLayout tile;
        final GalleryRevealImageView image;
        final FrameLayout source;
        final ImageView sourceIcon;
        final TextView sourceVariant;
        final ImageView fresh;
        final LinearLayout play;
        final ImageView playIcon;
        final TextView playDuration;
        final View morphPulse;
        final TextView unavailable;
        final ImageView favorite;
        final BunkrMaintenanceUi.Badge maintenance;
        final TextView gif;
        final android.widget.ProgressBar preparing;

        Holder(
                AspectRatioFrameLayout itemView,
                GalleryRevealImageView image,
                FrameLayout source,
                ImageView sourceIcon,
                TextView sourceVariant,
                ImageView fresh,
                LinearLayout play,
                ImageView playIcon,
                TextView playDuration,
                View morphPulse,
                TextView unavailable, ImageView favorite, BunkrMaintenanceUi.Badge maintenance,
                TextView gif, android.widget.ProgressBar preparing
        ) {
            super(itemView);
            this.tile = itemView;
            this.image = image;
            this.source = source;
            this.sourceIcon = sourceIcon;
            this.sourceVariant = sourceVariant;
            this.fresh = fresh;
            this.play = play;
            this.playIcon = playIcon;
            this.playDuration = playDuration;
            this.morphPulse = morphPulse;
            this.unavailable = unavailable;
            this.favorite = favorite;
            this.maintenance = maintenance;
            this.gif = gif;
            this.preparing = preparing;
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
