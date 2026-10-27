package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import android.webkit.CookieManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Compact Shows detail hero; only the artwork rotates, never the collection metadata. */
final class ShowDetailsHeader extends FrameLayout {
    interface Listener {
        void onBrowseVideos();
    }

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private final ImageView artwork;
    private final ImageView[] episodeArtwork = new ImageView[2];
    private final Handler rotation = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final List<NativeContentItem> candidates = new ArrayList<>();
    private final boolean category;
    private int showing = -1;
    private int activeLayer;
    private int generation;
    private boolean active = true;
    private com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> preloadTarget;
    private static final long ROTATION_MS = 13_000L;
    private final TextView count;
    private final TextView action;
    private final String collectionUrl;
    private final String fallbackImageUrl;

    ShowDetailsHeader(
            Context context,
            String title,
            String sourceLabel,
            String description,
            String collectionUrl,
            String imageUrl,
            Listener listener
    ) {
        super(context);
        this.collectionUrl = collectionUrl == null ? "" : collectionUrl;
        fallbackImageUrl = imageUrl;
        setBackgroundColor(Color.BLACK);

        artwork = new ImageView(context);
        artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.setBackground(new ColorDrawable(Color.rgb(14, 16, 19)));
        addView(artwork, new FrameLayout.LayoutParams(-1, -1));

        category = clean(sourceLabel, "SHOW")
                .toUpperCase(java.util.Locale.US)
                .contains("CATEGORY");
        FrameLayout imageHost = new FrameLayout(context);
        if (category) addView(imageHost, new FrameLayout.LayoutParams(-1, -1));
        if (category) for (int i = 0; i < episodeArtwork.length; i++) {
            episodeArtwork[i] = new ImageView(context);
            episodeArtwork[i].setScaleType(ImageView.ScaleType.CENTER_CROP);
            episodeArtwork[i].setAlpha(0f);
            episodeArtwork[i].setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            imageHost.addView(episodeArtwork[i], new FrameLayout.LayoutParams(-1, -1));
        }

        View shade = new View(context);
        shade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {
                        Color.argb(12, 0, 0, 0),
                        Color.argb(58, 0, 0, 0),
                        Color.argb(220, 0, 0, 0),
                        Color.BLACK
                }
        ));
        addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.BOTTOM);
        copy.setPadding(dp(18), dp(18), dp(18), dp(18));
        addView(copy, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        TextView source = text(clean(sourceLabel, "SHOW")
                .replace(" CATEGORY", "").replace(" SERIES", "").replace(" SHOW", ""),
                11f, UiPalette.PRIMARY);
        source.setTypeface(null, android.graphics.Typeface.BOLD);
        source.setLetterSpacing(0.10f);
        copy.addView(source);

        TextView heading = text(clean(title, "Show"), 28f, Color.WHITE);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setMaxLines(2);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.topMargin = dp(4);
        copy.addView(heading, headingParams);

        String cleanDescription = description == null ? "" : description.trim();
        if (!cleanDescription.isEmpty() && !category) {
            TextView descriptionView = text(
                    cleanDescription,
                    13f,
                    ZeroChillUi.color(context, R.color.zc_text_secondary)
            );
            descriptionView.setMaxLines(2);
            descriptionView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            descriptionView.setLineSpacing(0f, 1.06f);
            LinearLayout.LayoutParams descriptionParams =
                    new LinearLayout.LayoutParams(-1, -2);
            descriptionParams.topMargin = dp(5);
            copy.addView(descriptionView, descriptionParams);
        }

        LinearLayout metaRow = new LinearLayout(context);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = dp(8);
        copy.addView(metaRow, metaParams);

        count = text(
                "Loading episodes…",
                12f,
                ZeroChillUi.color(context, R.color.zc_text_secondary)
        );
        metaRow.addView(count, new LinearLayout.LayoutParams(0, -2, 1f));

        action = text("PLAY FIRST", 12f, Color.BLACK);
        action.setTypeface(null, android.graphics.Typeface.BOLD);
        action.setGravity(Gravity.CENTER);
        action.setContentDescription("Play first episode");
        action.setEnabled(false);
        GradientDrawable actionBackground = new GradientDrawable();
        actionBackground.setColor(UiPalette.PRIMARY);
        actionBackground.setCornerRadius(dp(19));
        action.setBackground(actionBackground);
        action.setClickable(true);
        action.setFocusable(true);
        ZeroChillMotion.installPressFeedback(action);
        action.setOnClickListener(v -> {
            if (listener != null) listener.onBrowseVideos();
        });
        metaRow.addView(action, new LinearLayout.LayoutParams(dp(132), dp(38)));

        loadArtwork(imageUrl);
    }

    void setCategoryItems(java.util.List<NativeContentItem> items) {
        if (!category) return;
        List<NativeContentItem> next = ShowsDetailHeroSelector.candidates(items, random);
        // The browser calls this for playback-history refreshes as well as new pages.
        if (sameCandidates(next)) return;
        String currentImage = showing < 0 || showing >= candidates.size()
                ? "" : candidates.get(showing).imageUrl;
        candidates.clear();
        candidates.addAll(next);
        clearPreload();
        generation++;
        pendingIndex = -1;
        showing = -1;
        for (int i = 0; i < candidates.size(); i++) {
            if (currentImage.equals(candidates.get(i).imageUrl)) showing = i;
        }
        for (ImageView image : episodeArtwork) image.animate().cancel();
        if (showing >= 0) {
            episodeArtwork[activeLayer].setAlpha(1f);
            ImageView pending = episodeArtwork[1 - activeLayer];
            pending.setAlpha(0f);
            Glide.with(pending).clear(pending);
        }
        if (candidates.size() < 2) {
            rotation.removeCallbacksAndMessages(null);
            generation++;
            showing = -1;
            for (ImageView image : episodeArtwork) {
                image.animate().cancel();
                image.setAlpha(0f);
                Glide.with(image).clear(image);
            }
            return;
        }
        if (active && isAttachedToWindow() && getWindowVisibility() == VISIBLE) {
            if (showing < 0) showNext();
            else {
                if (ZeroChillMotion.animationsEnabled(getContext())) preloadNext();
                scheduleRotation();
            }
        }
    }

    private boolean sameCandidates(List<NativeContentItem> next) {
        if (next.size() != candidates.size()) return false;
        for (NativeContentItem item : next) {
            boolean found = false;
            for (NativeContentItem existing : candidates) {
                if (item.imageUrl.equals(existing.imageUrl)) { found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }

    private void scheduleRotation() {
        rotation.removeCallbacksAndMessages(null);
        if (active && isAttachedToWindow() && getWindowVisibility() == VISIBLE && candidates.size() >= 2) {
            if (ZeroChillMotion.animationsEnabled(getContext()))
                rotation.postDelayed(this::showNext, ROTATION_MS);
        }
    }

    private void showNext() {
        if (!active || !isAttachedToWindow() || getWindowVisibility() != VISIBLE || candidates.size() < 2) return;
        int next = pendingIndex >= 0 && pendingIndex < candidates.size()
                && pendingIndex != showing ? pendingIndex
                : ShowsDetailHeroSelector.next(showing, candidates.size(), random);
        pendingIndex = -1;
        NativeContentItem item = candidates.get(next);
        int layer = showing < 0 && episodeArtwork[activeLayer].getAlpha() == 0f
                ? activeLayer : 1 - activeLayer;
        ImageView incoming = episodeArtwork[layer];
        int request = ++generation;
        incoming.animate().cancel();
        incoming.setAlpha(0f);
        incoming.bringToFront();
        Glide.with(incoming).load(episodeUrl(item)).centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .disallowHardwareConfig()
                .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                    @Override public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e,
                            Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                            boolean first) {
                        if (request == generation) scheduleRotation();
                        return false;
                    }
                    @Override public boolean onResourceReady(android.graphics.drawable.Drawable resource,
                            Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                            com.bumptech.glide.load.DataSource source, boolean first) {
                        incoming.post(() -> {
                            if (request != generation || !active || !isAttachedToWindow()
                                    || getWindowVisibility() != VISIBLE) return;
                            int old = activeLayer;
                            if (ZeroChillMotion.animationsEnabled(getContext())) {
                                incoming.animate().alpha(1f).setDuration(550L).withEndAction(() -> {
                                    if (request == generation && old != layer) episodeArtwork[old].setAlpha(0f);
                                }).start();
                            } else {
                                incoming.setAlpha(1f);
                                if (old != layer) episodeArtwork[old].setAlpha(0f);
                            }
                            activeLayer = layer;
                            showing = next;
                            if (ZeroChillMotion.animationsEnabled(getContext())) preloadNext();
                            scheduleRotation();
                        });
                        return false;
                    }
                }).into(incoming);
    }

    private void preloadNext() {
        if (candidates.size() < 2) return;
        // Keep the next choice random but fixed until the next rotation.
        pendingIndex = ShowsDetailHeroSelector.next(showing, candidates.size(), random);
        clearPreload();
        preloadTarget = Glide.with(this).load(episodeUrl(candidates.get(pendingIndex)))
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .preload(Math.max(dp(360), getWidth()), Math.max(dp(286), getHeight()));
    }

    private void clearPreload() {
        if (preloadTarget != null) {
            Glide.with(getContext()).clear(preloadTarget);
            preloadTarget = null;
        }
    }

    void setActive(boolean active) {
        this.active = active;
        if (!active) {
            generation++;
            rotation.removeCallbacksAndMessages(null);
            clearPreload();
            pendingIndex = -1;
            for (ImageView image : episodeArtwork) if (image != null) image.animate().cancel();
            if (showing >= 0) {
                episodeArtwork[activeLayer].setAlpha(1f);
                ImageView pending = episodeArtwork[1 - activeLayer];
                pending.setAlpha(0f);
                Glide.with(pending).clear(pending);
            } else for (ImageView image : episodeArtwork) if (image != null) Glide.with(image).clear(image);
        } else if (showing < 0 && candidates.size() >= 2) showNext();
        else {
            if (showing >= 0 && ZeroChillMotion.animationsEnabled(getContext())) preloadNext();
            scheduleRotation();
        }
    }

    private int pendingIndex = -1;

    private GlideUrl episodeUrl(NativeContentItem item) {
        String imageUrl = item.imageUrl.trim();
        String pageUrl = item.url == null ? "" : item.url.trim();
        String agent = USER_AGENT;
        if (XMegaDriveRepository.isXMegaDriveUrl(imageUrl) || XMegaDriveRepository.isXMegaDriveUrl(pageUrl)) {
            String session = XMegaDriveBrowserFetch.sessionUserAgent();
            if (session != null && !session.trim().isEmpty()) agent = session.trim();
        }
        LazyHeaders.Builder headers = new LazyHeaders.Builder().addHeader("User-Agent", agent)
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        if (!pageUrl.isEmpty()) headers.addHeader("Referer", pageUrl);
        try {
            String cookies = CookieManager.getInstance().getCookie(imageUrl);
            if ((cookies == null || cookies.isEmpty()) && !pageUrl.isEmpty())
                cookies = CookieManager.getInstance().getCookie(pageUrl);
            if (cookies != null && !cookies.isEmpty()) headers.addHeader("Cookie", cookies);
        } catch (Exception ignored) { }
        return new GlideUrl(imageUrl, headers.build());
    }

    void setPlaybackAction(boolean resumable, boolean available) {
        action.setText(resumable ? "RESUME" : "PLAY FIRST");
        action.setContentDescription(resumable ? "Resume episode" : "Play first episode");
        action.setEnabled(available);
        action.setAlpha(available ? 1f : 0.45f);
    }

    void setItemCount(int itemCount) {
        if (itemCount <= 0) {
            count.setText(category ? "No videos loaded yet" : "No episodes loaded yet");
        } else if (itemCount == 1) {
            count.setText(category ? "1 video" : "1 episode");
        } else {
            count.setText(itemCount + (category ? " videos" : " episodes"));
        }
    }

    private void loadArtwork(String imageUrl) {
        byte[] embedded = EmbeddedBrowseArtwork.get(getContext(), collectionUrl);
        if (embedded != null && embedded.length >= 512) {
            Glide.with(artwork)
                    .load(embedded)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(false)
                    .centerCrop()
                    .dontAnimate()
                    .transition(ThumbnailFades.hero())
                    .placeholder(new ColorDrawable(Color.rgb(14, 16, 19)))
                    .error(new ColorDrawable(Color.rgb(14, 16, 19)))
                    .into(artwork);
            return;
        }

        String cleanImage = imageUrl == null ? "" : imageUrl.trim();
        if (cleanImage.isEmpty()) return;

        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", USER_AGENT);
        if (collectionUrl.startsWith("http")) {
            headers.addHeader("Referer", collectionUrl);
        }

        Glide.with(artwork)
                .load(new GlideUrl(cleanImage, headers.build()))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .dontAnimate()
                .transition(ThumbnailFades.hero())
                .placeholder(new ColorDrawable(Color.rgb(14, 16, 19)))
                .error(new ColorDrawable(Color.rgb(14, 16, 19)))
                .into(artwork);
    }

    @Override
    protected void onDetachedFromWindow() {
        rotation.removeCallbacksAndMessages(null);
        clearPreload();
        pendingIndex = -1;
        generation++;
        for (ImageView image : episodeArtwork) if (image != null) {
            image.animate().cancel();
            Glide.with(image).clear(image);
        }
        showing = -1;
        Glide.with(artwork).clear(artwork);
        super.onDetachedFromWindow();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        loadArtwork(fallbackImageUrl);
        if (active && candidates.size() >= 2) showNext();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (active && visibility == VISIBLE) {
            if (showing < 0 && candidates.size() >= 2) showNext();
            else scheduleRotation();
        } else rotation.removeCallbacksAndMessages(null);
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private String clean(String value, String fallback) {
        String clean = value == null ? "" : value.trim();
        return clean.isEmpty() ? fallback : clean;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
