package com.webapp.crazyshit;

import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerControlView;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.TimeBar;

/** Shows chrome uses ShitTok's timing and layout without owning playback or orientation. */
@UnstableApi
final class ShowsFullscreenControls {
    private final PlayerControlView view;
    private final View chrome;
    private final PlayerControlView controller;
    private Player player;
    private boolean scrubbing;
    private boolean visible;
    private boolean suspended;
    private boolean standaloneLiked;
    private String standaloneLikeTarget = "";
    private int standaloneLikeGeneration;
    private final Runnable autoHide = this::hide;
    private final Player.Listener listener = new Player.Listener() {
        @Override public void onEvents(Player player, Player.Events events) {
            syncPausedChrome();
            if (!suspended && !shouldAutoHide(player.getPlayWhenReady(), player.getPlaybackState())) {
                show();
            } else {
                scheduleHide();
            }
        }
    };

    ShowsFullscreenControls(PlayerView playerView) {
        this((PlayerControlView) playerView.findViewById(androidx.media3.ui.R.id.exo_controller));
        playerView.setControllerShowTimeoutMs(0);
        playerView.setControllerHideOnTouch(false);
    }

    ShowsFullscreenControls(PlayerControlView view) {
        this.view = view;
        chrome = view.findViewById(R.id.shows_fullscreen_chrome);
        controller = view;
        controller.setAnimationEnabled(false);
        controller.setShowTimeoutMs(0);
        view.findViewById(R.id.shows_top_scrim).setBackground(FullscreenPlayerStyle.topScrim());
        view.findViewById(R.id.shows_bottom_scrim).setBackground(FullscreenPlayerStyle.bottomScrim());
        controller.setProgressUpdateListener((position, buffered) -> {
            if (!scrubbing) updateTimes(position);
        });
        TimeBar progress = view.findViewById(androidx.media3.ui.R.id.exo_progress);
        progress.addListener(new TimeBar.OnScrubListener() {
            @Override public void onScrubStart(TimeBar timeBar, long position) {
                scrubbing = true;
                show();
                updateTimes(position);
            }
            @Override public void onScrubMove(TimeBar timeBar, long position) {
                updateTimes(position);
            }
            @Override public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
                scrubbing = false;
                updateTimes(canceled && player != null ? player.getCurrentPosition() : position);
                scheduleHide();
            }
        });
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            applyInsets(insets);
            return insets;
        });
        view.post(() -> {
            applyInsets(ViewCompat.getRootWindowInsets(view));
            ViewCompat.requestApplyInsets(view);
        });
        if (view.getContext() instanceof PlayerActivity) {
            bindStandaloneLike((PlayerActivity) view.getContext());
        }
    }

    void bind(Player player) {
        if (this.player != null) this.player.removeListener(listener);
        this.player = player;
        player.addListener(listener);
        syncPausedChrome();
    }

    boolean isVisible() { return visible; }

    void show() {
        if (suspended) return;
        visible = true;
        chrome.animate().cancel();
        chrome.setAlpha(1f);
        view.show();
        syncPausedChrome();
        scheduleHide();
    }

    void hide() {
        view.removeCallbacks(autoHide);
        visible = false;
        chrome.animate().cancel();
        chrome.animate().alpha(0f).setDuration(FullscreenPlayerStyle.FADE_MS)
                .withEndAction(() -> { if (!visible) view.hide(); }).start();
    }

    void suspend(boolean suspended) {
        this.suspended = suspended;
        if (suspended) {
            visible = false;
            view.removeCallbacks(autoHide);
            chrome.animate().cancel();
            view.hide();
        } else show();
    }

    void release() {
        view.removeCallbacks(autoHide);
        chrome.animate().cancel();
        standaloneLikeGeneration++;
        if (player != null) player.removeListener(listener);
        controller.setPlayer(null);
        player = null;
    }

    void onVideoTap() {
        if (!visible) { show(); return; }
        if (player != null) {
            if (player.isPlaying()) player.pause();
            else {
                if (player.getPlaybackState() == Player.STATE_ENDED) player.seekTo(0L);
                player.play();
            }
        }
        show();
    }

    static boolean shouldAutoHide(boolean playWhenReady, int playbackState) {
        return playWhenReady && playbackState != Player.STATE_ENDED;
    }

    private void scheduleHide() {
        view.removeCallbacks(autoHide);
        if (visible && !suspended && !scrubbing && player != null &&
                shouldAutoHide(player.getPlayWhenReady(), player.getPlaybackState())) {
            view.postDelayed(autoHide, FullscreenPlayerStyle.HIDE_DELAY_MS);
        }
    }

    private void syncPausedChrome() {
        // Like ShitTok, playback has no permanent center pause button obscuring the video.
        view.findViewById(androidx.media3.ui.R.id.exo_center_controls).setVisibility(
                player != null && !player.isPlaying() ? View.VISIBLE : View.GONE);
    }

    private void updateTimes(long position) {
        long duration = player == null ? 0L : Math.max(0L, player.getDuration());
        TextView elapsed = view.findViewById(androidx.media3.ui.R.id.exo_position);
        elapsed.setText(FullscreenPlayerStyle.time(position));
        TextView remaining = view.findViewById(R.id.shows_remaining);
        remaining.setText(FullscreenPlayerStyle.remaining(position, duration));
    }

    void syncSaved(boolean saved) {
        ImageButton save = view.findViewById(R.id.shows_save);
        save.setImageResource(saved ? R.drawable.ic_action_save_outline : R.drawable.ic_action_save_outline);
        save.setColorFilter(saved ? UiPalette.PRIMARY : Color.WHITE);
        save.setContentDescription(saved ? "Remove from Watch Later" : "Save to Watch Later");
    }

    void syncLiked(boolean liked, boolean enabled) {
        ImageButton like = view.findViewById(R.id.shows_like);
        like.setImageResource(liked ? R.drawable.ic_action_heart_filled : R.drawable.ic_action_heart_outline);
        like.setColorFilter(liked ? UiPalette.PRIMARY : Color.WHITE);
        like.setEnabled(enabled);
        like.setContentDescription(liked ? "Unlike this video" : "Like this video");
    }

    private void bindStandaloneLike(PlayerActivity host) {
        String target = host.getIntent().getStringExtra(PlayerActivity.EXTRA_PAGE_URL);
        target = target == null ? "" : target.trim();
        standaloneLikeTarget = target;
        ImageButton like = view.findViewById(R.id.shows_like);
        if (target.isEmpty()) {
            syncLiked(false, false);
            return;
        }

        syncLiked(false, false);
        final String page = target;
        int generation = ++standaloneLikeGeneration;
        ZeroChillSocialRepository.videoLikeState(host, page, (state, error) ->
                host.runOnUiThread(() -> {
                    if (generation != standaloneLikeGeneration || !page.equals(standaloneLikeTarget)) return;
                    if (error == null && state != null) standaloneLiked = state.liked;
                    syncLiked(standaloneLiked, true);
                })
        );

        like.setOnClickListener(v -> {
            if (!ZeroChillAccountRepository.hasStoredSession(host)) {
                host.startActivity(new Intent(host, ZeroChillAccountActivity.class));
                return;
            }
            boolean wasLiked = standaloneLiked;
            syncLiked(standaloneLiked, false);
            ZeroChillSocialRepository.toggleVideoLike(host, page, wasLiked, (state, error) ->
                    host.runOnUiThread(() -> {
                        if (!page.equals(standaloneLikeTarget)) return;
                        if (error != null || state == null) {
                            syncLiked(standaloneLiked, true);
                            ZeroChillToast.makeText(
                                    host,
                                    error == null ? "Unable to update the like." : error.getMessage(),
                                    ZeroChillToast.LENGTH_LONG
                            ).show();
                            return;
                        }
                        standaloneLiked = state.liked;
                        syncLiked(standaloneLiked, true);
                        show();
                    })
            );
        });
    }

    void applyInsets(WindowInsetsCompat insets) {
        Insets edges = insets == null ? Insets.NONE : insets.getInsets(
                WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.systemBars());
        int bottom = FullscreenPlayerStyle.bottomInset(insets) + dp(4);
        if (insets != null) bottom = Math.max(bottom, edges.bottom + dp(4));
        chrome.setPadding(edges.left, 0, edges.right, 0);
        View header = view.findViewById(R.id.player_top_controls);
        FrameLayout.LayoutParams headerParams = (FrameLayout.LayoutParams) header.getLayoutParams();
        headerParams.topMargin = edges.top;
        header.setLayoutParams(headerParams);
        setBottomMargin(R.id.shows_action_row, bottom);
        setBottomMargin(R.id.shows_progress_row, bottom + dp(FullscreenPlayerStyle.ACTION_ROW_DP));
        view.findViewById(R.id.shows_top_scrim).getLayoutParams().height =
                dp(FullscreenPlayerStyle.TOP_SCRIM_DP) + edges.top;
        view.findViewById(R.id.shows_bottom_scrim).getLayoutParams().height =
                dp(FullscreenPlayerStyle.BOTTOM_SCRIM_DP) + bottom;
        chrome.requestLayout();
    }

    private void setBottomMargin(int id, int margin) {
        View child = view.findViewById(id);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) child.getLayoutParams();
        params.bottomMargin = margin;
        child.setLayoutParams(params);
    }

    private int dp(int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}
