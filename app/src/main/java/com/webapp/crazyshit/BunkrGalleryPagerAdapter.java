package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
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

        default void onVideoBack() { }
        default void onVideoMenu(View anchor) { }
        default void onVideoFullscreen() { }

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
    private final Set<String> retryImages = new HashSet<>();
    private static final Object STATE_PAYLOAD = new Object();
    private boolean activeVideoFrameRendered;
    private boolean activeVideoBuffering;
    private boolean sharedElementOpening;
    private boolean revealsStopped;
    private RecyclerView attachedList;
    private int activeVideoPosition = RecyclerView.NO_POSITION;
    private Player activePlayer;
    private boolean videoFullscreen;
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
        retryImages.clear();
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

    void toggleVideoControls(int position) {
        RecyclerView.ViewHolder raw = attachedList == null ? null : attachedList.findViewHolderForAdapterPosition(position);
        if (raw instanceof Holder) ((Holder) raw).controls.onVideoTap();
    }

    void setVideoFullscreen(boolean fullscreen) {
        if (videoFullscreen == fullscreen) return;
        videoFullscreen = fullscreen;
        if (activeVideoPosition != RecyclerView.NO_POSITION) notifyItemChanged(activeVideoPosition, STATE_PAYLOAD);
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
            if (failed.remove(item.url)) retryImages.add(item.url);
        }
        notifyItemChanged(position, value ? STATE_PAYLOAD : null);
    }

    void setResolvedUrl(int position, String mediaUrl) {
        NativeContentItem item = itemAt(position);
        if (item == null || mediaUrl == null) return;
        if (mediaUrl.isEmpty()) resolvedUrls.remove(item.url);
        else resolvedUrls.put(item.url, mediaUrl);
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
                .disallowHardwareConfig()
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
        int initial = indexOfUrl(sharedElementUrl);
        if (initial >= 0 && !resolvedUrl(initial).isEmpty()) notifyItemChanged(initial);
        if (attachedList == null) return;
        for (int i = 0; i < attachedList.getChildCount(); i++) {
            RecyclerView.ViewHolder raw = attachedList.getChildViewHolder(attachedList.getChildAt(i));
            if (raw instanceof Holder) {
                Holder holder = (Holder) raw;
                updateState(holder, holder.getBindingAdapterPosition());
            }
        }
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
        holder.controls.release();
        holder.gestures.cancel();
        holder.playerView.setPlayer(null);
        holder.playerView.setVisibility(View.GONE);
        holder.play.setVisibility(View.GONE);
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

        // TextureView keeps video in the same composition as the loading poster.
        PlayerView playerView = (PlayerView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.view_shows_fullscreen_player, root, false);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(false);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER);
        playerView.setVisibility(View.GONE);
        root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
        ShowsFullscreenControls controls = new ShowsFullscreenControls(playerView);
        FullscreenVideoGestures gestures = new FullscreenVideoGestures(
                playerView, playerView::getPlayer, controls, () -> playerView.getPlayer() != null);
        playerView.setOnTouchListener(gestures);
        for (int id : new int[] { R.id.shows_save, R.id.shows_share, R.id.shows_like })
            playerView.findViewById(id).setVisibility(View.GONE);
        playerView.findViewById(androidx.media3.ui.R.id.exo_settings).setVisibility(View.VISIBLE);
        playerView.findViewById(R.id.shows_fullscreen_toggle).setVisibility(View.VISIBLE);
        playerView.findViewById(R.id.shows_fullscreen_toggle).setOnClickListener(v -> listener.onVideoFullscreen());
        playerView.findViewById(R.id.player_back).setOnClickListener(v -> listener.onVideoBack());
        playerView.findViewById(R.id.player_menu).setOnClickListener(listener::onVideoMenu);
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

        GalleryVideoLoadingView loadingDots = new GalleryVideoLoadingView(parent.getContext());
        FrameLayout.LayoutParams dotsParams = new FrameLayout.LayoutParams(dp(parent, 60), dp(parent, 32));
        dotsParams.gravity = Gravity.CENTER;
        root.addView(loadingDots, dotsParams);

        TextView failure = new TextView(parent.getContext());
        failure.setText("Couldn't load this item.\nTap to retry or use the menu to open its page.");
        failure.setTextColor(Color.rgb(205, 205, 212));
        failure.setTextSize(14);
        failure.setGravity(Gravity.CENTER);
        failure.setPadding(dp(parent, 28), dp(parent, 28), dp(parent, 28), dp(parent, 28));
        failure.setBackgroundColor(Color.argb(185, 0, 0, 0));
        failure.setVisibility(View.GONE);
        root.addView(failure, new FrameLayout.LayoutParams(-1, -1));

        return new Holder(root, image, playerView, play, loadingDots, failure, controls, gestures);
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
            holder.hasUsableImage = false;
            holder.image.resetZoom();
            holder.reveal.showPoster();
        }
        GalleryMediaTransition.clearName(holder.image);
        holder.image.setZoomEnabled(item.isImage());
        String resolved = value(resolvedUrls.get(item.url));
        boolean opening = sharedElementOpening && item.url.equals(sharedElementUrl)
                && !value(item.imageUrl).isEmpty();
        // The thumbnail owns the shared expansion. Upgrade it once Android finishes.
        String preview = item.isVideo() || resolved.isEmpty() || opening ? value(item.imageUrl) : resolved;
        boolean activeVideo = item.isVideo() && position == activeVideoPosition && activePlayer != null;
        boolean retryImage = retryImages.remove(item.url);
        if (!activeVideo && (!sameItem || retryImage || !preview.equals(holder.requestedImageUrl))) {
            holder.requestedImageUrl = preview;
            int request = ++holder.imageRequest;
            holder.imageLoading = !preview.isEmpty();
            updateState(holder, position);
            if (preview.isEmpty()) {
                Glide.with(holder.image).clear(holder.image);
                holder.image.setImageDrawable(new ColorDrawable(Color.BLACK));
            } else {
                // The old request is cleared by into(). Keep only pixels we own while waiting.
                Drawable placeholder = sameItem ? holder.image.ownedPreviewSnapshot() : null;
                RequestBuilder<Drawable> imageRequest = Glide.with(holder.image)
                        .load(withHeaders(preview, imageReferer(item)))
                        .disallowHardwareConfig()
                        .fitCenter()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .transition(DrawableTransitionOptions.with((source, first) ->
                                holder.reveal.imageTransition(source, !revealsStopped
                                        && !(sharedElementOpening && item.url.equals(sharedElementUrl)),
                                        item.isImage() && !resolved.isEmpty() && preview.equals(resolved))))
                        .placeholder(placeholder != null ? placeholder : new ColorDrawable(Color.BLACK))
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
                                    if (holder.imageRequest == request) {
                                        updateState(holder, holder.getBindingAdapterPosition());
                                    }
                                });
                                if (item.isImage()) holder.itemView.post(() -> {
                                    int current = holder.getBindingAdapterPosition();
                                    if (holder.imageRequest != request || revealsStopped ||
                                            current == RecyclerView.NO_POSITION || current >= items.size() ||
                                            !item.url.equals(items.get(current).url)) return;
                                    if (!resolved.isEmpty() && resolved.equals(resolvedUrls.get(item.url))) {
                                        resolvedUrls.remove(item.url);
                                        listener.onResolvedImageFailed(current, item);
                                    }
                                    setFailed(current, true);
                                    updateState(holder, current);
                                });
                                // Glide must not replace a successful thumbnail/owned snapshot with black.
                                return item.isImage() && holder.hasUsableImage;
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
                                holder.hasUsableImage = true;
                                if (item.isImage() && !resolved.isEmpty() && preview.equals(resolved)) {
                                    failed.remove(item.url);
                                    loading.remove(item.url);
                                }
                                holder.itemView.post(() -> {
                                    if (holder.imageRequest == request) {
                                        updateState(holder, holder.getBindingAdapterPosition());
                                    }
                                });
                                return false;
                            }
                        });
                // Glide owns both resources during the preview/full-resolution handoff.
                if (item.isImage() && !resolved.isEmpty() && !value(item.imageUrl).isEmpty()
                        && !preview.equals(item.imageUrl)) {
                    imageRequest = imageRequest.thumbnail(Glide.with(holder.image)
                            .load(withHeaders(item.imageUrl, imageReferer(item)))
                            .disallowHardwareConfig()
                            .fitCenter().diskCacheStrategy(DiskCacheStrategy.ALL).dontAnimate()
                            .listener(new RequestListener<Drawable>() {
                                @Override public boolean onLoadFailed(GlideException error, Object model,
                                        Target<Drawable> target, boolean first) { return false; }
                                @Override public boolean onResourceReady(Drawable resource, Object model,
                                        Target<Drawable> target, DataSource source, boolean first) {
                                    if (holder.imageRequest != request || !item.url.equals(holder.boundUrl)) return true;
                                    holder.hasUsableImage = true;
                                    holder.itemView.post(() -> {
                                        if (holder.imageRequest == request) updateState(holder, holder.getBindingAdapterPosition());
                                    });
                                    return false;
                                }
                            }));
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
        holder.playerView.findViewById(R.id.shows_download).setOnClickListener(v -> downloadItem.onLongClick(v));
        ((TextView) holder.playerView.findViewById(R.id.player_title)).setText(
                item.title + " · " + (position + 1) + " of " + items.size());
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
            holder.controls.release();
            holder.gestures.cancel();
            holder.playerView.setPlayer(active ? activePlayer : null);
            if (active) holder.controls.bind(activePlayer);
        }
        holder.playerView.setVisibility(active ? View.VISIBLE : View.GONE);
        android.widget.ImageButton fullscreen = holder.playerView.findViewById(R.id.shows_fullscreen_toggle);
        androidx.media3.common.VideoSize size = active ? activePlayer.getVideoSize() : null;
        boolean landscapeVideo = size != null && size.width > 0 && size.height > 0
                && size.width * size.pixelWidthHeightRatio >= size.height;
        fullscreen.setVisibility(landscapeVideo ? View.VISIBLE : View.GONE);
        fullscreen.setImageResource(videoFullscreen ? R.drawable.ic_action_fullscreen_exit : R.drawable.ic_action_fullscreen);
        fullscreen.setContentDescription(videoFullscreen ? "Exit fullscreen" : "Fullscreen");
        if (active && activeVideoFrameRendered) {
            if (!activeVideoBuffering || holder.reveal.isVideoRevealed()) {
                holder.reveal.revealVideo(!revealsStopped);
            }
        } else holder.reveal.showPoster();
        boolean showFailure = failed.contains(item.url) && !active
                && !(item.isImage() && holder.hasUsableImage);
        holder.failure.setVisibility(showFailure ? View.VISIBLE : View.GONE);
        boolean openingPreview = sharedElementOpening && item.url.equals(sharedElementUrl)
                && (item.isVideo() || resolvedUrl(position).isEmpty()
                || !resolvedUrl(position).equals(holder.requestedImageUrl) || holder.imageLoading);
        // Resolution may finish during expansion, but the thumbnail still awaits its full request.
        boolean awaitingFullImage = item.isImage() && !resolvedUrl(position).isEmpty()
                && !resolvedUrl(position).equals(holder.requestedImageUrl);
        boolean videoLoading = item.isVideo() && !revealsStopped && !showFailure
                && (openingPreview || loading.contains(item.url)
                || (active && (!activeVideoFrameRendered || activeVideoBuffering)));
        holder.reveal.setVideoLoading(videoLoading);
        holder.play.setVisibility(item.isVideo() && !active && !videoLoading && !showFailure
                ? View.VISIBLE : View.GONE);
        holder.reveal.setLoading(!revealsStopped && !showFailure && (openingPreview
                || awaitingFullImage || loading.contains(item.url)
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
        holder.hasUsableImage = false;
        GalleryMediaTransition.clearName(holder.image);
        holder.controls.release();
        holder.gestures.cancel();
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
        SourceConfig sourceConfig = RemoteSourceConfigManager.snapshotOrNull();
        if (sourceConfig != null && (FapelloRepository.isFapelloUrl(url)
                || FapelloRepository.isFapelloUrl(pageUrl))) {
            SourceConfig.Fapello config = sourceConfig.fapello;
            headers.setHeader("User-Agent", config.userAgent);
            if (!config.refererOverride.isEmpty()) headers.setHeader("Referer", config.refererOverride);
            for (Map.Entry<String, String> header : config.requestHeaders.entrySet()) {
                headers.setHeader(header.getKey(), header.getValue());
            }
        }
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
        final ShowsFullscreenControls controls;
        final FullscreenVideoGestures gestures;
        final TextView play;
        final GalleryVideoLoadingView loadingDots;
        final TextView failure;
        final GalleryMediaReveal reveal;
        String boundUrl = "";
        String requestedImageUrl = "";
        int imageRequest;
        boolean imageLoading;
        boolean hasUsableImage;

        Holder(
                View root,
                ZoomableImageView image,
                PlayerView playerView,
                TextView play,
                GalleryVideoLoadingView loadingDots,
                TextView failure,
                ShowsFullscreenControls controls,
                FullscreenVideoGestures gestures
        ) {
            super(root);
            this.image = image;
            this.playerView = playerView;
            this.controls = controls;
            this.gestures = gestures;
            this.play = play;
            this.loadingDots = loadingDots;
            this.failure = failure;
            this.reveal = new GalleryMediaReveal(image, loadingDots);
        }
    }
}
