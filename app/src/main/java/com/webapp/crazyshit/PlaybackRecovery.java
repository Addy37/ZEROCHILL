package com.webapp.crazyshit;

import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.datasource.HttpDataSource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Two bounded refresh attempts per video. A late result cannot replace a different player. */
final class PlaybackRecovery {
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private String page = "";
    private int generation, attempts;
    private boolean pending;
    private Player.Listener metricsListener;

    static final class Recovered {
        final CrazyShitRepository.StreamInfo stream;
        final long position;
        final boolean playWhenReady;
        Recovered(CrazyShitRepository.StreamInfo stream, long position, boolean play) {
            this.stream = stream; this.position = position; this.playWhenReady = play;
        }
    }

    void bind(ExoPlayer next, String pageUrl) {
        detachMetrics();
        generation++;
        pending = false;
        if (!page.equals(pageUrl)) attempts = 0;
        page = pageUrl == null ? "" : pageUrl;
        player = next;
        final long started = android.os.SystemClock.elapsedRealtime();
        metricsListener = new Player.Listener() {
            boolean firstFrame;
            long buffering;
            public void onRenderedFirstFrame() {
                if (!firstFrame) { firstFrame = true; AppPerformance.record("Video first frame", android.os.SystemClock.elapsedRealtime() - started); }
            }
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING && firstFrame && buffering == 0) buffering = android.os.SystemClock.elapsedRealtime();
                if (state == Player.STATE_READY && buffering != 0) {
                    AppPerformance.record("Video rebuffer", android.os.SystemClock.elapsedRealtime() - buffering); buffering = 0;
                }
            }
        };
        next.addListener(metricsListener);
    }

    private void detachMetrics() {
        if (player != null && metricsListener != null) player.removeListener(metricsListener);
        metricsListener = null;
    }

    boolean recover(Activity activity, PlaybackException error, Consumer<Recovered> ready, Runnable failure) {
        if (player == null || pending || page.isEmpty() || (!page.startsWith("https://") && !page.startsWith("http://"))) {
            if (!pending) showBunkrDiagnostics(activity, "player", error, null);
            return false;
        }
        if (error != null && (error.errorCode < 2000 || error.errorCode > 2008 || error.errorCode == 2006)) {
            showBunkrDiagnostics(activity, "player", error, null);
            return false;
        }
        if (!permitsRefresh(page, currentMediaUrl(player), error,
                System.currentTimeMillis() / 1000L, attempts)) {
            showBunkrDiagnostics(activity, "player-denied", error, null);
            return false;
        }
        if (error == null) attempts = 0;
        if (attempts >= 2) {
            showBunkrDiagnostics(activity, "player-retries-exhausted", error, null);
            return false;
        }
        attempts++;
        pending = true;
        int token = generation;
        String requestedPage = page;
        long position = Math.max(0, player.getCurrentPosition());
        boolean play = player.getPlayWhenReady();
        PlaybackException playbackError = error;
        main.postDelayed(() -> IO.execute(() -> {
            CrazyShitRepository.StreamInfo stream = null;
            Throwable refreshError = null;
            try { stream = PlayableSourceRouter.resolve(activity.getApplicationContext(), requestedPage); }
            catch (Exception failureError) { refreshError = failureError; }
            CrazyShitRepository.StreamInfo resolved = stream;
            Throwable resolvedRefreshError = refreshError;
            main.post(() -> {
                if (token != generation || activity.isFinishing() || activity.isDestroyed()) return;
                pending = false;
                if (resolved == null || resolved.mediaUrl == null || resolved.mediaUrl.isEmpty()) {
                    showBunkrDiagnostics(
                            activity,
                            resolvedRefreshError == null ? "refresh-empty" : "refresh",
                            playbackError,
                            resolvedRefreshError
                    );
                    failure.run();
                } else ready.accept(new Recovered(resolved, position, play));
            });
        }), attempts == 1 ? 400L : 1200L);
        return true;
    }

    /** CDN denials should not trigger repeated verification requests. An expired signed link gets one refresh. */
    static boolean permitsRefresh(String pageUrl, String mediaUrl, PlaybackException error,
            long nowSeconds, int previousAttempts) {
        if (!BunkrRepository.isBunkrUrl(pageUrl) || error == null) return true;
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
            if (!(cause instanceof HttpDataSource.InvalidResponseCodeException)) continue;
            int status = ((HttpDataSource.InvalidResponseCodeException) cause).responseCode;
            if (status == 401 || status == 429) return false;
            if (status != 403) return true;
            if (previousAttempts > 0 || mediaUrl == null) return false;
            try {
                Uri uri = Uri.parse(mediaUrl);
                String token = uri.getQueryParameter("token");
                String expiry = uri.getQueryParameter("ex");
                long expiresAt = expiry == null ? 0L : Long.parseLong(expiry);
                return token != null && !token.isEmpty() && expiry != null
                        && expiresAt > 0L && expiresAt <= nowSeconds;
            } catch (Exception ignored) { return false; }
        }
        return true;
    }

    private void showBunkrDiagnostics(
            Activity activity,
            String phase,
            PlaybackException playbackError,
            Throwable refreshError
    ) {
        if (activity == null || !BunkrRepository.isBunkrUrl(page)) return;
        ExoPlayer currentPlayer = player;
        int state = currentPlayer == null ? -1 : currentPlayer.getPlaybackState();
        long position = currentPlayer == null ? 0L : currentPlayer.getCurrentPosition();
        String mediaUrl = currentMediaUrl(currentPlayer);
        BunkrPlaybackDiagnostics.show(
                activity,
                BunkrPlaybackDiagnostics.report(
                        phase,
                        page,
                        mediaUrl,
                        playbackError,
                        refreshError,
                        state,
                        position
                ),
                page
        );
    }

    private static String currentMediaUrl(ExoPlayer player) {
        if (player == null) return "";
        MediaItem item = player.getCurrentMediaItem();
        if (item == null || item.localConfiguration == null || item.localConfiguration.uri == null) return "";
        return item.localConfiguration.uri.toString();
    }

    void reset() { attempts = 0; cancel(); }
    void cancel() { generation++; pending = false; detachMetrics(); player = null; main.removeCallbacksAndMessages(null); }
}
