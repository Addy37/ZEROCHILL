package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.media3.common.Player;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Full-screen horizontal pager for Bunkr photos and videos. */
final class BunkrGalleryPagerAdapter
        extends RecyclerView.Adapter<BunkrGalleryPagerAdapter.Holder> {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    interface Listener {
        void onMediaTap(int position, NativeContentItem item);

        void onMediaLongPress(int position, NativeContentItem item);

        void onResolvedImageFailed(int position, NativeContentItem item);

        default void onSharedElementReady(View target) {
        }
    }

    private final Context context;
    private final Listener listener;
    private final ArrayList<NativeContentItem> items = new ArrayList<>();
    private final Map<String, String> resolvedUrls = new HashMap<>();
    private final Set<String> preloadedImages = new HashSet<>();
    private final Set<String> loading = new HashSet<>();
    private final Set<String> failed = new HashSet<>();
    private static final Object STATE_PAYLOAD = new Object();
    private boolean activeVideoFrameRendered;
    private boolean activeVideoBuffering;
    private boolean sharedElementOpening;
    private boolean revealsStopped;
    private RecyclerView attachedList;
    private int activeVideoPosition = RecyclerView.NO_POSITION;
    private Player activePlayer;
    private String sharedElementUrl = "";
    private String sharedElementName = "";
    private boolean sharedElementDelivered;

    BunkrGalleryPagerAdapter(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        setHasStableIds(true);
    }

    void setInitialSharedElement(String itemUrl, String transitionName) {
        sharedElementUrl = value(itemUrl);
        sharedElementName = value(transitionName);
        sharedElementDelivered = false;
        sharedElementOpening = !sharedElementName.isEmpty();
    }

    void replace(List<NativeContentItem> incoming, Map<String, String> resolved) {
        items.clear();
        if (incoming != null) items.addAll(incoming);
        resolvedUrls.clear();
        preloadedImages.clear();
        if (resolved != null) resolvedUrls.putAll(resolved);
        loading.clear();
        failed.clear();
        activeVideoPosition = RecyclerView.NO_POSITION;
        activePlayer = null;
        notifyDataSetChanged();
    }

    int append(List<NativeContentItem> incoming) {
        if (incoming == null || incoming.isEmpty()) return 0;
        int start = items.size();
        for (NativeContentItem candidate : incoming) {
            if (candidate == null || indexOfUrl(candidate.url) >= 0) continue;
            items.add(candidate);
        }
        int added = items.size() - start;
        if (added > 0) notifyItemRangeInserted(start, added);
        return added;
    }

    NativeContentItem itemAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position) : null;
    }

    int indexOfUrl(String url) {
        if (url == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (url.equals(items.get(i).url)) return i;
        }
        return -1;
    }

    void setLoading(int position, boolean value) {
        NativeContentItem item = itemAt(position);
        if (item == null) return;
        if (value) {
            failed.remove(item.url);
            loading.add(item.url);
        } else {
            loading.remove(item.url);
        }
        notifyItemChanged(position, STATE_PAYLOAD);
    }

    boolean isLoading(int position) {
        NativeContentItem item = itemAt(position);
        return item != null && loading.contains(item.url);
    }

    boolean isFailed(int position) {
        NativeContentItem item = itemAt(position);
        return item != null && failed.contains(item.url);
    }

    void setFailed(int position, boolean value) {
        NativeContentItem item = itemAt(position);
        if (item == null) return;
        if (value) {
            loading.remove(item.url);
            failed.add(item.url);
        } else {
            failed.remove(item.url);
        }
        notifyItemChanged(position, STATE_PAYLOAD);
    }

    void setResolvedUrl(int position, String mediaUrl) {
        NativeContentItem item = itemAt(position);
        if (item == null || mediaUrl == null || mediaUrl.isEmpty()) return;
        resolvedUrls.put(item.url, mediaUrl);
        loading.remove(item.url);
        failed.remove(item.url);
        notifyItemChanged(position);
    }

    String resolvedUrl(int position) {
        NativeContentItem item = itemAt(position);
        return item == null ? "" : value(resolvedUrls.get(item.url));
    }

    /** Warm one full-screen image with the same Glide model and size used by the pager. */
    void preloadImage(int position) {
        NativeContentItem item = itemAt(position);
        if (item == null || !item.isImage()) return;
        String url = value(resolvedUrls.get(item.url));
        if (url.isEmpty()) url = value(item.imageUrl);
        if (url.isEmpty() || !preloadedImages.add(url)) return;
        Glide.with(context)
                .load(withHeaders(url, imageReferer(item)))
                .fitCenter()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(context.getResources().getDisplayMetrics().widthPixels,
                        context.getResources().getDisplayMetrics().heightPixels)
                .preload();
    }

    void activateVideo(int position, Player player) {
        int old = activeVideoPosition;
        activeVideoPosition = position;
        activePlayer = player;
        activeVideoFrameRendered = false;
        activeVideoBuffering = true;
        if (old != RecyclerView.NO_POSITION && old < items.size()) notifyItemChanged(old, STATE_PAYLOAD);
        if (position >= 0 && position < items.size()) notifyItemChanged(position, STATE_PAYLOAD);
    }

    void onVideoFirstFrame(int position, Player player) {
        if (revealsStopped || player == null || player != activePlayer || position != activeVideoPosition) return;
        activeVideoFrameRendered = true;
        notifyItemChanged(position, STATE_PAYLOAD);
    }

    void onVideoBuffering(int position, Player player, boolean buffering) {
        if (revealsStopped || player == null || player != activePlayer || position != activeVideoPosition) return;
        if (activeVideoBuffering == buffering) return;
        activeVideoBuffering = buffering;
        notifyItemChanged(position, STATE_PAYLOAD);
    }

    void clearActiveVideo() {
        int old = activeVideoPosition;
        activeVideoPosition = RecyclerView.NO_POSITION;
        activePlayer = null;
        activeVideoFrameRendered = false;
        activeVideoBuffering = false;
        if (old != RecyclerView.NO_POSITION && old < items.size()) notifyItemChanged(old, STATE_PAYLOAD);
    }

    void finishOpeningTransition() {
        sharedElementOpening = false;
    }

    void stopReveals() {
        revealsStopped = true;
        if (attachedList == null) return;
        for (int i = 0; i < attachedList.getChildCount(); i++) {
            RecyclerView.ViewHolder holder = attachedList.getChildViewHolder(attachedList.getChildAt(i));
            if (holder instanceof Holder) ((Holder) holder).reveal.cancelAndSettle();
        }
    }

    View prepareSharedReturn(ViewPager2 pager, int position) {
        RecyclerView list = (RecyclerView) pager.getChildAt(0);
        RecyclerView.ViewHolder raw = list.findViewHolderForAdapterPosition(position);
        if (!(raw instanceof Holder)) return null;
        Holder holder = (Holder) raw;
        if (holder.image.getDrawable() == null || holder.image.getDrawable() instanceof ColorDrawable
                || !sharedElementName.equals(ViewCompat.getTransitionName(holder.image))) return null;
        // Preserve the already loaded poster. notifyItemChanged here would restart
        // Glide and briefly clear the drawable just as Android captures the return.
        activeVideoPosition = RecyclerView.NO_POSITION;
        activePlayer = null;
        holder.reveal.cancelAndSettle();
        holder.reveal.showPoster();
        holder.playerView.setPlayer(null);
        holder.playerView.setVisibility(View.GONE);
        holder.play.setVisibility(View.GONE);
        holder.progress.setVisibility(View.GONE);
        holder.failure.setVisibility(View.GONE);
        return holder.image;
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).url.hashCode();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FrameLayout root = new FrameLayout(parent.getContext());
        root.setLayoutParams(new RecyclerView.LayoutParams(-1, -1));

        ZoomableImageView image = new ZoomableImageView(parent.getContext());
        image.setBackground(null);

        PlayerView playerView = new PlayerView(parent.getContext());
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setUseController(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER);
        playerView.setVisibility(View.GONE);
        root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(image, new FrameLayout.LayoutParams(-1, -1));

        TextView play = new TextView(parent.getContext());
        play.setText("▶");
        play.setTextColor(Color.WHITE);
        play.setTextSize(30);
        play.setGravity(Gravity.CENTER);
        GradientDrawable playBackground = new GradientDrawable();
        playBackground.setShape(GradientDrawable.OVAL);
        playBackground.setColor(Color.argb(190, 0, 0, 0));
        play.setBackground(playBackground);
        FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(
                dp(parent, 62),
                dp(parent, 62)
        );
        playParams.gravity = Gravity.CENTER;
        root.addView(play, playParams);

        ProgressBar progress = new ProgressBar(parent.getContext());
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                dp(parent, 48),
                dp(parent, 48)
        );
        progressParams.gravity = Gravity.CENTER;
        root.addView(progress, progressParams);

        TextView failure = new TextView(parent.getContext());
        failure.setText("Couldn't load this item.\nTap to retry or use the menu to open its page.");
        failure.setTextColor(Color.rgb(205, 205, 212));
        failure.setTextSize(14);
        failure.setGravity(Gravity.CENTER);
        failure.setPadding(dp(parent, 28), dp(parent, 28), dp(parent, 28), dp(parent, 28));
        failure.setBackgroundColor(Color.argb(185, 0, 0, 0));
        failure.setVisibility(View.GONE);
        root.addView(failure, new FrameLayout.LayoutParams(-1, -1));

        return new Holder(root, image, playerView, play, progress, failure);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        NativeContentItem item = items.get(position);
        boolean sameItem = item.url.equals(holder.boundUrl);
        if (!sameItem) {
            holder.reveal.cancelAndSettle();
            holder.boundUrl = item.url;
            holder.requestedImageUrl = "";
            holder.imageLoading = false;
            holder.image.resetZoom();
            holder.reveal.showPoster();
        }
        GalleryMediaTransition.clearName(holder.image);
        holder.image.setZoomEnabled(item.isImage());
        String resolved = value(resolvedUrls.get(item.url));
        String preview = item.isVideo() || resolved.isEmpty() ? value(item.imageUrl) : resolved;
        boolean activeVideo = item.isVideo() && position == activeVideoPosition && activePlayer != null;
        if (!activeVideo && (!sameItem || !preview.equals(holder.requestedImageUrl))) {
            holder.requestedImageUrl = preview;
            int request = ++holder.imageRequest;
            holder.imageLoading = !preview.isEmpty();
            updateState(holder, position);
            if (preview.isEmpty()) {
                Glide.with(holder.image).clear(holder.image);
                holder.image.setImageDrawable(new ColorDrawable(Color.BLACK));
            } else {
                RequestBuilder<Drawable> imageRequest = Glide.with(holder.image)
                        .load(withHeaders(preview, imageReferer(item)))
                        .fitCenter()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .transition(DrawableTransitionOptions.with((source, first) ->
                                holder.reveal.imageTransition(source, !revealsStopped
                                        && !(sharedElementOpening && item.url.equals(sharedElementUrl)))))
                        .placeholder(sameItem ? holder.image.getDrawable() : new ColorDrawable(Color.BLACK))
                        .error(new ColorDrawable(Color.BLACK))
                        .listener(new RequestListener<Drawable>() {
                            @Override
                            public boolean onLoadFailed(
                                    GlideException error,
                                    Object model,
                                    Target<Drawable> target,
                                    boolean firstResource
                            ) {
                                if (holder.imageRequest != request || !item.url.equals(holder.boundUrl)) return true;
                                holder.imageLoading = false;
                                holder.itemView.post(() -> {
                                    if (holder.imageRequest == request) updateState(holder, position);
                                });
                                if (!item.isImage() || resolved.isEmpty()) return false;
                                holder.itemView.post(() -> {
                                    int current = holder.getBindingAdapterPosition();
                                    if (holder.imageRequest != request || revealsStopped ||
                                            current == RecyclerView.NO_POSITION || current >= items.size() ||
                                            !item.url.equals(items.get(current).url) ||
                                            !resolved.equals(resolvedUrls.get(item.url))) return;
                                    resolvedUrls.remove(item.url);
                                    loading.remove(item.url);
                                    failed.add(item.url);
                                    listener.onResolvedImageFailed(current, item);
                                    notifyItemChanged(current);
                                });
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(
                                    Drawable resource,
                                    Object model,
                                    Target<Drawable> target,
                                    DataSource source,
                                    boolean firstResource
                            ) {
                                if (holder.imageRequest != request || !item.url.equals(holder.boundUrl)) return true;
                                holder.imageLoading = false;
                                holder.itemView.post(() -> {
                                    if (holder.imageRequest == request) updateState(holder, position);
                                });
                                return false;
                            }
                        });
                // Glide owns both resources during the preview/full-resolution handoff.
                if (item.isImage() && !resolved.isEmpty() && !value(item.imageUrl).isEmpty()
                        && !preview.equals(item.imageUrl)) {
                    imageRequest.thumbnail(Glide.with(holder.image)
                            .load(withHeaders(item.imageUrl, imageReferer(item)))
                            .fitCenter().diskCacheStrategy(DiskCacheStrategy.ALL).dontAnimate());
                }
                imageRequest.into(holder.image);
            }
        }
        updateState(holder, position);

        if (!sharedElementName.isEmpty() && item.url.equals(sharedElementUrl)) {
            ViewCompat.setTransitionName(holder.image, sharedElementName);
            if (!sharedElementDelivered) {
                holder.image.post(() -> {
                    int current = holder.getBindingAdapterPosition();
                    if (sharedElementDelivered
                            || current == RecyclerView.NO_POSITION
                            || current >= items.size()
                            || !item.url.equals(items.get(current).url)
                            || !item.url.equals(sharedElementUrl)
                            || !sharedElementName.equals(
                                    ViewCompat.getTransitionName(holder.image))) {
                        return;
                    }
                    sharedElementDelivered = true;
                    listener.onSharedElementReady(holder.image);
                });
            }
        }

        holder.itemView.setContentDescription(
                (item.isVideo() ? "Video, " : "Photo, ") + item.title +
                        ". Long press to download."
        );
        View.OnClickListener openItem = v -> {
            int current = holder.getBindingAdapterPosition();
            if (current == RecyclerView.NO_POSITION || current >= items.size()) return;
            listener.onMediaTap(current, items.get(current));
        };
        View.OnLongClickListener downloadItem = v -> {
            int current = holder.getBindingAdapterPosition();
            if (current == RecyclerView.NO_POSITION || current >= items.size()) return false;
            listener.onMediaLongPress(current, items.get(current));
            return true;
        };
        holder.itemView.setOnClickListener(openItem);
        holder.itemView.setOnLongClickListener(downloadItem);
        // The preview sits above the page root, so give it the same actions for videos too.
        holder.image.setOnClickListener(openItem);
        holder.image.setOnLongClickListener(downloadItem);
        holder.playerView.setOnLongClickListener(downloadItem);
        holder.play.setOnClickListener(openItem);
        holder.failure.setOnClickListener(openItem);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && items.get(position).url.equals(holder.boundUrl)) {
            updateState(holder, position);
        } else onBindViewHolder(holder, position);
    }

    private void updateState(Holder holder, int position) {
        NativeContentItem item = itemAt(position);
        if (item == null || !item.url.equals(holder.boundUrl)) return;
        boolean active = item.isVideo() && position == activeVideoPosition && activePlayer != null;
        if (holder.playerView.getPlayer() != (active ? activePlayer : null)) {
            holder.playerView.setPlayer(active ? activePlayer : null);
        }
        holder.playerView.setVisibility(active ? View.VISIBLE : View.GONE);
        if (active && activeVideoFrameRendered) holder.reveal.revealVideo(!revealsStopped);
        else holder.reveal.showPoster();
        holder.play.setVisibility(item.isVideo() && !active ? View.VISIBLE : View.GONE);
        boolean showFailure = failed.contains(item.url) && !active;
        holder.failure.setVisibility(showFailure ? View.VISIBLE : View.GONE);
        holder.reveal.setLoading(!revealsStopped && !showFailure && (loading.contains(item.url)
                || (!active && holder.imageLoading)
                || (active && (!activeVideoFrameRendered || activeVideoBuffering))));
    }

    @Override public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        attachedList = recyclerView;
    }

    @Override public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        stopReveals();
        attachedList = null;
        super.onDetachedFromRecyclerView(recyclerView);
    }

    @Override public void onViewDetachedFromWindow(@NonNull Holder holder) {
        holder.reveal.cancelAndSettle();
        super.onViewDetachedFromWindow(holder);
    }

    @Override public void onViewAttachedToWindow(@NonNull Holder holder) {
        super.onViewAttachedToWindow(holder);
        int position = holder.getBindingAdapterPosition();
        if (position != RecyclerView.NO_POSITION) updateState(holder, position);
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        holder.reveal.cancelAndSettle();
        holder.imageRequest++;
        holder.boundUrl = "";
        holder.requestedImageUrl = "";
        holder.imageLoading = false;
        GalleryMediaTransition.clearName(holder.image);
        holder.playerView.setPlayer(null);
        holder.itemView.setOnLongClickListener(null);
        holder.image.setOnClickListener(null);
        holder.image.setOnLongClickListener(null);
        holder.playerView.setOnLongClickListener(null);
        holder.play.setOnClickListener(null);
        holder.failure.setOnClickListener(null);
        holder.image.resetZoom();
        Glide.with(holder.image).clear(holder.image);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private GlideUrl withHeaders(String url, String pageUrl) {
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", pageUrl == null
                        ? BunkrRepository.DEFAULT_PAGE_ORIGIN + "/" : pageUrl)
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        try {
            String cookies = CookieManager.getInstance().getCookie(url);
            if ((cookies == null || cookies.isEmpty()) && pageUrl != null) {
                cookies = CookieManager.getInstance().getCookie(pageUrl);
            }
            if (cookies != null && !cookies.isEmpty()) headers.addHeader("Cookie", cookies);
        } catch (Exception ignored) {
        }
        return new GlideUrl(url, headers.build());
    }

    private String imageReferer(NativeContentItem item) {
        if (item != null && WikiFeetRepository.isWikiFeetUrl(item.url) &&
                WikiFeetRepository.isWikiFeetUrl(item.uploader)) return item.uploader;
        if (item != null && !FapelloRepository.isPostUrl(item.url) &&
                FapelloRepository.isModelUrl(item.uploader)) return item.uploader;
        return item == null ? null : item.url;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ZoomableImageView image;
        final PlayerView playerView;
        final TextView play;
        final ProgressBar progress;
        final TextView failure;
        final GalleryMediaReveal reveal;
        String boundUrl = "";
        String requestedImageUrl = "";
        int imageRequest;
        boolean imageLoading;

        Holder(
                View root,
                ZoomableImageView image,
                PlayerView playerView,
                TextView play,
                ProgressBar progress,
                TextView failure
        ) {
            super(root);
            this.image = image;
            this.playerView = playerView;
            this.play = play;
            this.progress = progress;
            this.failure = failure;
            this.reveal = new GalleryMediaReveal(image, progress);
        }
    }
}
