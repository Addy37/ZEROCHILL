package com.webapp.crazyshit;

import android.content.Context;
import android.os.Looper;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.SimpleBasePlayer;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IMedia;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.Collections;

/** Exposes LibVLC playback to the same controls and lifecycle as the regular watch player. */
@UnstableApi
final class LegacyWatchPlayer extends SimpleBasePlayer {
    private final LibVLC libVlc;
    private final MediaPlayer engine;
    private final MediaItem media;
    private final String referer;
    private final String userAgent;
    private int state = STATE_IDLE;
    private boolean playWhenReady;
    private boolean priming;
    private boolean starting;
    private boolean released;
    private boolean firstFrame;
    private boolean firstFrameSent;
    private boolean videoOutput;
    private long positionMs = C.TIME_UNSET;
    private float volume = 1f;
    private PlaybackParameters speed = PlaybackParameters.DEFAULT;
    private PlaybackException error;
    private VideoSize videoSize = VideoSize.UNKNOWN;
    private Tracks tracks = Tracks.EMPTY;

    LegacyWatchPlayer(Context context, VLCVideoLayout video, MediaItem media, String referer, String userAgent) {
        super(Looper.getMainLooper());
        this.media = media;
        this.referer = referer;
        this.userAgent = userAgent;
        libVlc = LegacyVideoPlayerActivity.createDecoder(context);
        MediaPlayer created = null;
        try {
            created = new MediaPlayer(libVlc);
            engine = created;
            created.attachViews(video, null, false, true);
            created.setEventListener(this::onEvent);
        } catch (RuntimeException | LinkageError failure) {
            if (created != null) {
                created.detachViews();
                created.release();
            }
            libVlc.release();
            throw failure;
        }
    }

    @Override protected State getState() {
        long length = released ? 0L : engine.getLength();
        boolean seekable = !released && length > 0L && engine.isSeekable();
        Commands commands = new Commands.Builder()
                .add(COMMAND_PLAY_PAUSE).add(COMMAND_PREPARE).add(COMMAND_STOP).add(COMMAND_RELEASE)
                .add(COMMAND_GET_CURRENT_MEDIA_ITEM).add(COMMAND_GET_TIMELINE).add(COMMAND_GET_TRACKS)
                .add(COMMAND_SET_SPEED_AND_PITCH).add(COMMAND_GET_VOLUME).add(COMMAND_SET_VOLUME)
                .addIf(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, seekable)
                .addIf(COMMAND_SEEK_BACK, seekable).addIf(COMMAND_SEEK_FORWARD, seekable)
                .addIf(COMMAND_SEEK_TO_DEFAULT_POSITION, seekable || state == STATE_ENDED)
                .build();
        MediaItemData item = new MediaItemData.Builder(media)
                .setMediaItem(media).setTracks(tracks).setIsSeekable(seekable)
                .setDurationUs(length > 0L ? length * 1000L : C.TIME_UNSET).build();
        return new State.Builder().setAvailableCommands(commands)
                .setPlaylist(Collections.singletonList(item))
                .setPlaybackState(state).setPlayerError(error)
                .setPlayWhenReady(playWhenReady, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .setPlaybackParameters(speed).setVolume(volume).setVideoSize(videoSize)
                .setContentPositionMs(() -> positionMs != C.TIME_UNSET ? positionMs
                        : released ? 0L : Math.max(0L, engine.getTime()))
                .setSeekBackIncrementMs(10_000L).setSeekForwardIncrementMs(10_000L)
                .setNewlyRenderedFirstFrame(firstFrame).build();
    }

    private void start() {
        error = null;
        state = STATE_BUFFERING;
        priming = true;
        starting = true;
        firstFrameSent = false;
        videoOutput = false;
        engine.setVolume(0);
        Media source = new Media(libVlc, media.localConfiguration.uri);
        try {
            source.setHWDecoderEnabled(true, false);
            if (!userAgent.isEmpty()) source.addOption(":http-user-agent=" + userAgent);
            if (LegacyVideoFormatSupport.validHttpsMedia(referer)) source.addOption(":http-referrer=" + referer);
            engine.setMedia(source);
        } finally {
            source.release();
        }
        engine.play();
    }

    void restorePosition(long position) {
        positionMs = Math.max(0L, position);
    }

    @Override protected ListenableFuture<?> handlePrepare() {
        if (state == STATE_IDLE) start();
        return Futures.immediateVoidFuture();
    }

    @Override protected ListenableFuture<?> handleSetPlayWhenReady(boolean play) {
        playWhenReady = play;
        if (state == STATE_ENDED && play) {
            restorePosition(0L);
            start();
        } else if (state != STATE_IDLE && !priming) {
            if (play) engine.play();
            else if (engine.isPlaying()) engine.pause();
        }
        return Futures.immediateVoidFuture();
    }

    @Override protected ListenableFuture<?> handleSeek(int index, long position, int command) {
        restorePosition(position == C.TIME_UNSET ? 0L : position);
        if (state == STATE_ENDED) {
            start();
        } else if (!priming && engine.isSeekable()) {
            engine.setTime(positionMs);
            positionMs = C.TIME_UNSET;
        }
        return Futures.immediateVoidFuture();
    }

    @Override protected ListenableFuture<?> handleSetVolume(float value) {
        volume = value;
        if (!priming) engine.setVolume(Math.round(volume * 100));
        return Futures.immediateVoidFuture();
    }

    @Override protected ListenableFuture<?> handleSetPlaybackParameters(PlaybackParameters parameters) {
        speed = new PlaybackParameters(parameters.speed);
        if (!priming) engine.setRate(speed.speed);
        return Futures.immediateVoidFuture();
    }

    @Override protected ListenableFuture<?> handleStop() {
        engine.stop();
        state = STATE_IDLE;
        return Futures.immediateVoidFuture();
    }

    @Override protected ListenableFuture<?> handleRelease() {
        released = true;
        engine.setEventListener(null);
        try {
            engine.stop();
            engine.detachViews();
        } finally {
            engine.release();
            libVlc.release();
        }
        return Futures.immediateVoidFuture();
    }

    private void onEvent(MediaPlayer.Event event) {
        if (released) return;
        switch (event.type) {
            case MediaPlayer.Event.Playing:
                starting = false;
                state = STATE_READY;
                updateVideoSize();
                if (priming) {
                    priming = false;
                    seekWhenReady();
                    engine.setRate(speed.speed);
                    if (!playWhenReady) engine.pause();
                    else engine.setVolume(Math.round(volume * 100));
                } else if (playWhenReady) {
                    engine.setVolume(Math.round(volume * 100));
                }
                break;
            case MediaPlayer.Event.Paused:
                state = STATE_READY;
                break;
            case MediaPlayer.Event.Buffering:
                if (!starting && playWhenReady && (state == STATE_READY || state == STATE_BUFFERING)) {
                    state = event.getBuffering() >= 100 ? STATE_READY : STATE_BUFFERING;
                }
                break;
            case MediaPlayer.Event.Vout:
                videoOutput = event.getVoutCount() > 0;
                updateVideoSize();
                break;
            case MediaPlayer.Event.TimeChanged:
                if (firstFrameSent || starting) return;
                if (videoSize.width <= 0) updateVideoSize();
                if (videoSize.width > 0) videoOutput = true;
                break;
            case MediaPlayer.Event.SeekableChanged:
            case MediaPlayer.Event.LengthChanged:
                if (!priming) seekWhenReady();
                break;
            case MediaPlayer.Event.EndReached:
                long length = engine.getLength();
                if (length > 0L) positionMs = length;
                state = STATE_ENDED;
                break;
            case MediaPlayer.Event.EncounteredError:
                priming = false;
                starting = false;
                state = STATE_IDLE;
                error = new PlaybackException("Legacy video could not be played", null,
                        PlaybackException.ERROR_CODE_UNSPECIFIED);
                break;
            default:
                return;
        }
        firstFrame = !firstFrameSent && videoOutput && !starting && state == STATE_READY;
        if (firstFrame) firstFrameSent = true;
        invalidateState();
        firstFrame = false;
    }

    private void seekWhenReady() {
        if (positionMs == C.TIME_UNSET) return;
        if (positionMs > 0L) {
            if (!engine.isSeekable()) return;
            engine.setTime(positionMs);
        }
        positionMs = C.TIME_UNSET;
    }

    private void updateVideoSize() {
        IMedia.VideoTrack track = engine.getCurrentVideoTrack();
        if (track == null || track.width < 1 || track.height < 1) return;
        VideoSize current = videoSize(track.width, track.height, track.sarNum, track.sarDen, track.orientation);
        if (current.equals(videoSize)) return;
        videoSize = current;
        Format video = new Format.Builder().setSampleMimeType("video/x-ms-wmv")
                .setWidth(current.width).setHeight(current.height).build();
        tracks = new Tracks(Collections.singletonList(
                new Tracks.Group(new TrackGroup("vlc-video", video), false,
                        new int[]{C.FORMAT_HANDLED}, new boolean[]{true})));
    }

    static VideoSize videoSize(int width, int height, int sarNum, int sarDen, int orientation) {
        float ratio = sarNum > 0 && sarDen > 0 ? (float) sarNum / sarDen : 1f;
        if (orientation >= IMedia.VideoTrack.Orientation.LeftTop) {
            return new VideoSize(height, width, 1f / ratio);
        }
        return new VideoSize(width, height, ratio);
    }
}
