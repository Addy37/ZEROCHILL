package com.webapp.crazyshit;

import android.app.Application;
import android.os.Looper;

import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

/** A saved category starts fetching its selected stream before the slider completes, silently. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokCategoryPrebufferTest {
    private static final class FakePlayer {
        int mediaSourceCalls;
        int prepareCalls;
        int playCalls;
        int pauses;
        long startAtMs = -1L;
        float volume = 1f;
        boolean playWhenReady;

        final ExoPlayer player = (ExoPlayer) Proxy.newProxyInstance(
                ExoPlayer.class.getClassLoader(),
                new Class<?>[]{ExoPlayer.class},
                (object, method, args) -> {
                    switch (method.getName()) {
                        case "hashCode": return System.identityHashCode(object);
                        case "equals": return object == args[0];
                        case "getApplicationLooper": return Looper.getMainLooper();
                        case "getTrackSelectionParameters":
                            return TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT;
                        case "getCurrentTimeline": return Timeline.EMPTY;
                        case "getCurrentTracks": return Tracks.EMPTY;
                        case "getMediaMetadata": return MediaMetadata.EMPTY;
                        case "getVideoSize": return VideoSize.UNKNOWN;
                        case "getPlaybackState": return Player.STATE_IDLE;
                        case "getCurrentPosition": return startAtMs < 0 ? 0L : startAtMs;
                        case "getVolume": return volume;
                        case "setVolume": volume = (Float) args[0]; return null;
                        case "getPlayWhenReady": return playWhenReady;
                        case "isPlaying": return playWhenReady;
                        case "setPlayWhenReady": playWhenReady = (Boolean) args[0]; return null;
                        case "pause": pauses++; playWhenReady = false; return null;
                        case "play": playCalls++; playWhenReady = true; return null;
                        case "setMediaSource":
                            mediaSourceCalls++;
                            if (args != null && args.length > 1 && args[1] instanceof Long) {
                                startAtMs = (Long) args[1];
                            }
                            return null;
                        case "prepare": prepareCalls++; return null;
                        default:
                            if (method.getReturnType() == boolean.class) return false;
                            if (method.getReturnType() == int.class) return 0;
                            if (method.getReturnType() == long.class) return 0L;
                            if (method.getReturnType() == float.class) return 0f;
                            return null;
                    }
                });
    }

    @Test public void savedReturnPreparesAtBookmarkWithoutAudioOrExtraPlayers() {
        FakePlayer fake = new FakePlayer();
        final int[] acquired = {0};
        ShitTokPlaybackOwner owner = new ShitTokPlaybackOwner(
                RuntimeEnvironment.getApplication(),
                new ShitTokPlaybackOwner.Callbacks() {
                    @Override public void onLeaseChanged(String url) { }
                    @Override public void onLeaseRetired(String url) { }
                });
        try {
            owner.setPlayerFactoryForTest(() -> {
                acquired[0]++;
                return fake.player;
            });
            String url = "https://example.test/saved-ladies";
            CrazyShitRepository.StreamInfo stream =
                    new CrazyShitRepository.StreamInfo(
                            "https://example.test/clip.mp4", url, "Remembered");
            owner.permission(true, false);
            owner.permission(false, false);
            owner.remember(url, 27_000L);
            owner.select(url, null, null, false);
            assertNotNull(owner.player(url));
            assertFalse("Normal source must respect playback permission",
                    owner.source(url, stream, owner.token(url)));
            assertEquals(0, fake.mediaSourceCalls);
            assertTrue("Saved return may silently prepare",
                    owner.prebufferSelectedReturn(url, stream));
            assertEquals(27_000L, fake.startAtMs);
            assertEquals(1, fake.prepareCalls);
            assertEquals(1, fake.mediaSourceCalls);
            assertEquals(1, acquired[0]);
            assertEquals(0, fake.playCalls);
            assertFalse(fake.playWhenReady);
            assertEquals(0f, fake.volume, 0f);
            // Normal activation adopts the prepared source without re-preparing.
            owner.permission(true, false);
            assertTrue(owner.source(url, stream, owner.token(url)));
            assertEquals(1, fake.mediaSourceCalls);
            assertEquals(1, fake.prepareCalls);
        } finally {
            owner.close();
        }
    }

    @Test public void wrongCategoryAndCoveredSearchCannotStartSilentPreparation() {
        FakePlayer fake = new FakePlayer();
        ShitTokPlaybackOwner owner = new ShitTokPlaybackOwner(
                RuntimeEnvironment.getApplication(),
                new ShitTokPlaybackOwner.Callbacks() {
                    @Override public void onLeaseChanged(String url) { }
                    @Override public void onLeaseRetired(String url) { }
                });
        try {
            owner.setPlayerFactoryForTest(() -> fake.player);
            String url = "https://example.test/saved-chaos";
            CrazyShitRepository.StreamInfo stream =
                    new CrazyShitRepository.StreamInfo(
                            "https://example.test/clip.mp4", url, "Remembered");
            owner.permission(true, false);
            owner.permission(false, false);
            owner.select(url, null, null, false);
            assertFalse(owner.prebufferSelectedReturn("https://example.test/stale", stream));
            owner.setSearchCovered(true);
            assertFalse(owner.prebufferSelectedReturn(url, stream));
            assertEquals(0, fake.prepareCalls);
            assertEquals(0, fake.mediaSourceCalls);
            assertEquals(0, fake.playCalls);
        } finally {
            owner.close();
        }
    }
}
