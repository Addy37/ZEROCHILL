package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Isolated software-capable player for legacy Bunkr containers.
 * This activity never replaces Media3 for ordinary media or changes gallery sessions.
 */
public final class LegacyVideoPlayerActivity extends Activity {
    private static final String EXTRA_MEDIA = "legacy_media";
    private static final String EXTRA_REFERER = "legacy_referer";
    private static final String EXTRA_PAGE = "legacy_page";
    private static final String STATE_TIME = "legacy_time";
    private static final String STATE_PLAYING = "legacy_playing";
    private static final int CYAN = Color.rgb(0, 217, 255);
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private LibVLC libVlc;
    private MediaPlayer vlcPlayer;
    private VLCVideoLayout videoLayout;
    private TextView stateText;
    private TextView playButton;
    private TextView timeText;
    private SeekBar seekBar;
    private long resumeAtMs;
    private boolean playOnStart = true;
    private boolean seeking;
    private boolean active;
    private boolean pauseAfterStart;
    private boolean reachedEnd;
    private String mediaUrl;
    private String referer;

    static Intent intent(Context context, String pageUrl, String mediaUrl, String requestReferer) {
        return new Intent(context, LegacyVideoPlayerActivity.class)
                .putExtra(EXTRA_PAGE, pageUrl)
                .putExtra(EXTRA_MEDIA, mediaUrl)
                .putExtra(EXTRA_REFERER, requestReferer);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        mediaUrl = getIntent().getStringExtra(EXTRA_MEDIA);
        referer = getIntent().getStringExtra(EXTRA_REFERER);
        String page = getIntent().getStringExtra(EXTRA_PAGE);
        if (!LegacyVideoFormatSupport.validHttpsMedia(mediaUrl)
                || !BunkrRepository.isBunkrUrl(page)) {
            finish();
            return;
        }
        if (state != null) {
            resumeAtMs = state.getLong(STATE_TIME, 0L);
            playOnStart = state.getBoolean(STATE_PLAYING, true);
        }
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        videoLayout = new VLCVideoLayout(this);
        root.addView(videoLayout, new FrameLayout.LayoutParams(-1, -1));

        TextView close = label("‹  Back to gallery", 16);
        close.setBackgroundColor(0xb8000000);
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                -2, dp(52), Gravity.TOP | Gravity.START);
        closeParams.topMargin = dp(12);
        closeParams.leftMargin = dp(12);
        close.setPadding(dp(12), 0, dp(12), 0);
        root.addView(close, closeParams);
        close.setOnClickListener(v -> finish());

        stateText = label("Opening video…", 14);
        stateText.setBackgroundColor(0x99000000);
        FrameLayout.LayoutParams stateParams = new FrameLayout.LayoutParams(
                -2, dp(48), Gravity.CENTER);
        stateText.setPadding(dp(16), 0, dp(16), 0);
        root.addView(stateText, stateParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(14), dp(9), dp(14), dp(20));
        controls.setBackgroundColor(0xd9000000);
        FrameLayout.LayoutParams controlParams = new FrameLayout.LayoutParams(
                -1, -2, Gravity.BOTTOM);
        root.addView(controls, controlParams);

        LinearLayout controlsRow = new LinearLayout(this);
        controlsRow.setGravity(Gravity.CENTER_VERTICAL);
        controls.addView(controlsRow, new LinearLayout.LayoutParams(-1, dp(48)));

        playButton = label("Pause", 16);
        playButton.setTextColor(CYAN);
        playButton.setPadding(dp(8), 0, dp(16), 0);
        controlsRow.addView(playButton, new LinearLayout.LayoutParams(-2, -1));
        playButton.setOnClickListener(v -> {
            if (vlcPlayer == null) return;
            if (vlcPlayer.isPlaying()) {
                playOnStart = false;
                vlcPlayer.pause();
            } else {
                playOnStart = true;
                if (reachedEnd) { vlcPlayer.setTime(0L); reachedEnd = false; }
                vlcPlayer.play();
            }
            updatePlayButton();
        });

        timeText = label("0:00 / 0:00", 13);
        controlsRow.addView(timeText, new LinearLayout.LayoutParams(-2, -1));

        seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));
        seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(CYAN));
        controls.addView(seekBar, new LinearLayout.LayoutParams(-1, dp(38)));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) { seeking = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (vlcPlayer != null) {
                    long length = vlcPlayer.getLength();
                    if (length > 0L && vlcPlayer.isSeekable()) {
                        vlcPlayer.setTime(length * bar.getProgress() / 1000L);
                    }
                }
                seeking = false;
            }
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) { }
        });
        setContentView(root);
    }

    @Override protected void onStart() {
        super.onStart();
        active = true;
        if (videoLayout != null) videoLayout.post(() -> {
            if (active && !isFinishing() && !isDestroyed() && vlcPlayer == null) startPlayback();
        });
    }

    private void startPlayback() {
        try {
            libVlc = new LibVLC(this, new ArrayList<>());
            MediaPlayer started = new MediaPlayer(libVlc);
            vlcPlayer = started;
            started.attachViews(videoLayout, null, false, false);
            started.setEventListener(event -> {
                int type = event.type;
                runOnUiThread(() -> {
                if (vlcPlayer != started || isFinishing()) return;
                if (type == MediaPlayer.Event.Playing) {
                    if (resumeAtMs > 0L) {
                        started.setTime(resumeAtMs);
                        resumeAtMs = 0L;
                    }
                    stateText.setVisibility(View.GONE);
                    if (pauseAfterStart) {
                        pauseAfterStart = false;
                        started.pause();
                    }
                    updatePlayButton();
                } else if (type == MediaPlayer.Event.TimeChanged
                        || type == MediaPlayer.Event.LengthChanged) {
                    updateSeek();
                } else if (type == MediaPlayer.Event.Paused) {
                    updatePlayButton();
                } else if (type == MediaPlayer.Event.EndReached) {
                    playOnStart = false;
                    reachedEnd = true;
                    playButton.setText("Replay");
                } else if (type == MediaPlayer.Event.EncounteredError) {
                    playOnStart = false;
                    stateText.setText("This video could not play. Go back to the gallery to retry.");
                    stateText.setVisibility(View.VISIBLE);
                    updatePlayButton();
                }
                });
            });
            Media media = new Media(libVlc, Uri.parse(mediaUrl));
            media.setHWDecoderEnabled(true, false);
            media.addOption(":http-user-agent=" + USER_AGENT);
            if (referer != null && LegacyVideoFormatSupport.validHttpsMedia(referer)) {
                media.addOption(":http-referrer=" + referer);
            }
            started.setMedia(media);
            media.release();
            // Parse and seek before restoring a paused session.
            pauseAfterStart = !playOnStart;
            started.play();
        } catch (RuntimeException | LinkageError failure) {
            releasePlayer();
            stateText.setText("Legacy playback isn't available on this device.");
            stateText.setVisibility(View.VISIBLE);
        }
    }

    private void updatePlayButton() {
        if (vlcPlayer != null) playButton.setText(vlcPlayer.isPlaying() ? "Pause" : "Play");
    }

    private void updateSeek() {
        if (vlcPlayer == null) return;
        long length = Math.max(0L, vlcPlayer.getLength());
        long current = Math.max(0L, vlcPlayer.getTime());
        if (!seeking) seekBar.setProgress(length > 0L
                ? (int) Math.min(1000L, current * 1000L / length) : 0);
        seekBar.setEnabled(length > 0L && vlcPlayer.isSeekable());
        timeText.setText(format(current) + " / " + format(length));
    }

    private static String format(long ms) {
        long sec = Math.max(0L, ms / 1000L);
        return String.format(Locale.US, "%d:%02d", sec / 60L, sec % 60L);
    }

    private TextView label(String text, int sp) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(sp);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        long time = vlcPlayer == null ? resumeAtMs : vlcPlayer.getTime();
        state.putLong(STATE_TIME, Math.max(0L, time));
        state.putBoolean(STATE_PLAYING, vlcPlayer != null ? vlcPlayer.isPlaying() : playOnStart);
        super.onSaveInstanceState(state);
    }

    @Override protected void onStop() {
        active = false;
        if (vlcPlayer != null) {
            resumeAtMs = Math.max(0L, vlcPlayer.getTime());
            playOnStart = vlcPlayer.isPlaying();
        }
        releasePlayer();
        super.onStop();
    }

    private void releasePlayer() {
        if (vlcPlayer != null) {
            MediaPlayer old = vlcPlayer;
            vlcPlayer = null;
            old.setEventListener(null);
            old.stop();
            old.detachViews();
            old.release();
        }
        if (libVlc != null) {
            libVlc.release();
            libVlc = null;
        }
    }
}
