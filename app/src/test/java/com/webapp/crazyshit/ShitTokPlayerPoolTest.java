package com.webapp.crazyshit;

import android.app.Application;
import android.os.Looper;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.ui.PlayerView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.Assert.*;

/** Real Media3 PlayerPool and feed owner, decoder-free engines. No live source requests. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokPlayerPoolTest {
    static class Engine {
        int prepares, releases, sourceAssignments, stops;
        int state = Player.STATE_READY;
        boolean play;
        float volume;
        long position;
        PlaybackException error;
        TrackSelectionParameters parameters = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT;
        MediaItem media;
        final List<Player.Listener> listeners = new ArrayList<>();
        final ExoPlayer player = (ExoPlayer) Proxy.newProxyInstance(ExoPlayer.class.getClassLoader(),
            new Class<?>[] { ExoPlayer.class }, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    case "getApplicationLooper": return Looper.getMainLooper();
                    case "getTrackSelectionParameters": return parameters;
                    case "setTrackSelectionParameters": parameters = (TrackSelectionParameters) args[0]; return null;
                    case "getCurrentTimeline": return Timeline.EMPTY;
                    case "getCurrentTracks": return Tracks.EMPTY;
                    case "getMediaMetadata": return MediaMetadata.EMPTY;
                    case "getVideoSize": return VideoSize.UNKNOWN;
                    case "getPlaybackParameters": return PlaybackParameters.DEFAULT;
                    case "getCurrentPosition": return position;
                    case "getPlaybackState": return state;
                    case "getPlayerError": return error;
                    case "getPlayWhenReady": case "isPlaying": return play;
                    case "play": play=true; return null;
                    case "pause": play=false; return null;
                    case "setPlayWhenReady": play=(Boolean)args[0];return null;
                    case "getVolume": return volume;
                    case "setVolume": volume=(Float)args[0];return null;
                    case "prepare": prepares++; error=null; return null;
                    case "release": releases++; return null;
                    case "stop": stops++; play=false; return null;
                    case "setMediaSource": sourceAssignments++; if(args.length>1 && args[1] instanceof Long) position=(Long)args[1]; return null;
                    case "addListener": listeners.add((Player.Listener)args[0]); return null;
                    case "removeListener": listeners.remove(args[0]);return null;
                    default:
                        if(method.getReturnType()==boolean.class) return false;
                        if(method.getReturnType()==int.class) return 0;
                        if(method.getReturnType()==long.class) return 0L;
                        if(method.getReturnType()==float.class) return 0f;
                        return null;
                }
            });
    }
    static class Harness implements AutoCloseable {
        final List<Engine> engines = new ArrayList<>();
        final List<String> retired = new ArrayList<>();
        final ShitTokPlaybackOwner owner = new ShitTokPlaybackOwner(RuntimeEnvironment.getApplication(),
            new ShitTokPlaybackOwner.Callbacks() {
                public void onLeaseChanged(String url) { }
                public void onLeaseRetired(String url) { retired.add(url); }
            });
        Harness() {
            owner.setPlayerFactoryForTest(() -> { Engine e=new Engine(); engines.add(e);return e.player; });
            owner.permission(true,false);
        }
        void select(int i) { owner.select(id(i),id(i+1),i>0?id(i-1):null,true); }
        void prepare(int i) { String id=id(i); assertTrue(owner.source(id,new CrazyShitRepository.StreamInfo("https://fixture.invalid/"+i+".mp4",id,"Fixture"),owner.token(id))); }
        void attach(int i) { owner.attach(id(i),view());owner.syncAudio(); }
        Engine engine(int i) { ExoPlayer p=owner.player(id(i)); return engines.stream().filter(e->e.player==p).findFirst().get(); }
        int audible() { return (int)engines.stream().filter(e->e.play&&e.volume>0).count(); }
        public void close() { owner.close(); }
    }
    static PlayerView view() { PlayerView view = new PlayerView(RuntimeEnvironment.getApplication());view.setUseController(false);return view; }
    static String id(int i) { return "https://fixture.invalid/post/"+i; }

    @Test public void boundedCapacityPromotesForwardAndReverseWithoutPreparingAgain() {
        try(Harness h=new Harness()) {
            h.select(1); h.prepare(1);h.prepare(2);h.prepare(0);h.attach(1);
            ExoPlayer first=h.owner.player(id(1)),next=h.owner.player(id(2));
            int prepared=h.engine(2).prepares;
            h.select(2); h.attach(2);
            assertSame(next,h.owner.player(id(2)));
            assertSame(first,h.owner.player(id(1)));
            assertEquals(prepared,h.engine(2).prepares);
            assertEquals(3,h.engines.size());assertEquals(1,h.audible());
            h.select(1);h.attach(1);
            assertSame(first,h.owner.player(id(1)));assertEquals(1,h.audible());
            assertTrue(h.retired.contains(id(0)));assertTrue(h.retired.contains(id(3)));
            assertEquals(3,h.engines.size());
        }
    }
    @Test public void rapidDirectionChangesYieldBeforeAcquireAndNeverGrowPool() {
        try(Harness h=new Harness()) {
            for(int i:new int[]{0,1,2,10,11,10,2,1,0,8}) {
                h.select(i); h.prepare(i);h.attach(i);
                assertTrue(h.engines.size()<=3);assertEquals(1,h.audible());
            }
            assertTrue(h.owner.summary().contains("acquired=3"));
            assertEquals(0,h.engines.stream().mapToInt(e->e.releases).sum());
        }
    }
    @Test public void staleAResultCannotAssignToReusedEngine() {
        try(Harness h=new Harness()) {
            h.select(0);int stale=h.owner.token(id(0));h.select(3);
            int assignments=h.engines.stream().mapToInt(e->e.sourceAssignments).sum();
            assertFalse(h.owner.source(id(0),new CrazyShitRepository.StreamInfo("https://fixture.invalid/a.mp4",id(0),"A"),stale));
            assertEquals(assignments,h.engines.stream().mapToInt(e->e.sourceAssignments).sum());
            h.select(0); assertFalse(h.owner.accepts(id(0),stale));
        }
    }
    @Test public void dragSettleBackgroundAndHiddenHaveNoAudioAndPauseSurvivesCanceledSwipe() {
        try(Harness h=new Harness()) {
            h.select(0);h.prepare(0);h.attach(0);assertEquals(1,h.audible());
            for(int i=0;i<4;i++) { h.owner.permission(false,false); assertEquals(0,h.audible());h.owner.permission(true,false);assertEquals(1,h.audible()); }
            h.owner.setUserPaused(id(0),true);h.owner.permission(false,false);h.owner.permission(true,false);
            assertEquals(0,h.audible());assertTrue(h.owner.isUserPaused(id(0)));
            h.owner.setUserPaused(id(0),false);assertEquals(1,h.audible());
        }
    }
    @Test public void scrollStateOnlyDoesNotReplanOrReprepareSources() {
        try(Harness h=new Harness()) {
            h.select(1);h.prepare(1);h.attach(1);
            String initial=h.owner.summary();
            for(int i=0;i<20;i++) {h.owner.permission(false,false);h.owner.permission(true,false);h.select(1);}
            assertTrue(initial.contains("plans=1")); assertTrue(h.owner.summary().contains("plans=1"));
            assertEquals(1,h.engine(1).prepares);assertEquals(1,h.engine(1).sourceAssignments);
        }
    }
    @Test public void holderSurfaceDetachRetainsLeaseAndSavedBackwardPosition() {
        try(Harness h=new Harness()) {
            h.select(1);h.prepare(1);h.engine(1).position=4200L;
            PlayerView view=view();
            ExoPlayer retained=h.owner.attach(id(1),view);h.owner.syncAudio();
            h.owner.detach(id(1),view);assertSame(retained,h.owner.player(id(1)));assertEquals(0,h.audible());
            h.select(4);h.select(1);h.prepare(1);
            assertEquals(4200L,h.engine(1).position);
        }
    }
    @Test public void minimalModeOnlyAcquiresCurrentAndLifecycleReleasesAll() {
        Harness h=new Harness();h.owner.select(id(0),id(1),null,false);h.prepare(0);h.attach(0);
        assertEquals(1,h.engines.size());h.owner.release();assertEquals(0,h.audible());
        assertEquals(1,h.engines.get(0).releases);assertNull(h.owner.player(id(0)));
        h.close();
    }
    @Test public void decoderPressureBacksOffThreeTwoOneAndStopsRetrying() {
        try(Harness h=new Harness()) {
            h.select(1);h.prepare(1);h.attach(1);Engine current=h.engine(1);
            PlaybackException failure=ExoPlaybackException.createForUnexpected(new IllegalStateException("resource pressure"),PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
            current.error=failure;assertTrue(h.owner.recoverDecoder(current.player,failure));
            assertTrue(h.owner.summary().contains("preparedLimit=2"));assertNull(h.owner.player(id(0)));
            failure=ExoPlaybackException.createForUnexpected(new IllegalStateException("second event"),PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
            current.error=failure;assertTrue(h.owner.recoverDecoder(current.player,failure));
            assertTrue(h.owner.summary().contains("preparedLimit=1"));assertNull(h.owner.player(id(2)));
            failure=ExoPlaybackException.createForUnexpected(new IllegalStateException("third event"),PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
            current.error=failure;assertFalse(h.owner.recoverDecoder(current.player,failure));
            assertSame(current.player,h.owner.player(id(1)));assertEquals(1,h.audible());
        }
    }
    @Test public void ordinarySourceFailureDoesNotTriggerDecoderBackoff() {
        try(Harness h=new Harness()) {
            h.select(1); Engine current=h.engine(1);
            PlaybackException error=new PlaybackException("HTTP 404",null,PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS);
            current.error=error;assertFalse(h.owner.recoverDecoder(current.player,error));
            assertTrue(h.owner.summary().contains("preparedLimit=3"));
        }
    }
    @Test public void muteAndFullscreenSurfaceHandoffKeepOnePlaybackOwner() {
        try(Harness h=new Harness()) {
            h.select(1);h.prepare(1);h.prepare(2);h.attach(1);
            ExoPlayer current=h.owner.player(id(1));h.attach(1);assertSame(current,h.owner.player(id(1)));assertEquals(1,h.audible());
            h.owner.permission(true,true);assertEquals(0,h.audible());assertTrue(h.engine(1).play);
            h.owner.permission(true,false);assertEquals(1,h.audible());
        }
    }
    @Test public void decoderFailureWithOnlyCurrentCannotRetireLastPlayer() {
        try(Harness h=new Harness()) {
            h.owner.select(id(0),null,null,false);h.prepare(0);h.attach(0);
            Engine current=h.engine(0);
            PlaybackException error=ExoPlaybackException.createForUnexpected(new IllegalStateException("decoder"),PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
            current.error=error;assertFalse(h.owner.recoverDecoder(current.player,error));
            assertSame(current.player,h.owner.player(id(0)));assertTrue(h.owner.summary().contains("preparedLimit=3"));
        }
    }
    @Test public void sameDecoderErrorDeliveredToTwoListenersReducesOnlyOnce() {
        try(Harness h=new Harness()) {
            h.select(1);h.prepare(1);h.attach(1);Engine current=h.engine(1);
            PlaybackException error=ExoPlaybackException.createForUnexpected(new IllegalStateException("decoder"),PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
            current.error=error;
            assertTrue(h.owner.recoverDecoder(current.player,error));
            assertTrue(h.owner.recoverDecoder(current.player,error));
            assertTrue(h.owner.summary().contains("preparedLimit=2"));
        }
    }
    @Test public void currentBufferingDefersNeighborPreparationUntilReady() {
        try(Harness h=new Harness()) {
            h.select(1);h.engine(1).state=Player.STATE_BUFFERING;h.prepare(1);h.prepare(2);
            assertEquals(0,h.engine(2).prepares);
            h.engine(1).state=Player.STATE_READY;
            for(Player.Listener listener:new ArrayList<>(h.engine(1).listeners)) listener.onPlaybackStateChanged(Player.STATE_READY);
            assertEquals(1,h.engine(2).prepares);
        }
    }
    @Test public void refreshedSourceAndLifecycleReturnKeepPositionAndManualPause() {
        try(Harness h=new Harness()) {
            h.select(1);h.prepare(1);h.attach(1);h.engine(1).position=12345L;
            h.owner.setUserPaused(id(1),true);h.owner.invalidateSource(id(1));h.prepare(1);
            assertEquals(12345L,h.engine(1).position);assertEquals(0,h.audible());
            h.owner.release();h.select(1);h.owner.permission(true,false);h.prepare(1);h.attach(1);
            assertEquals(12345L,h.engine(1).position);assertTrue(h.owner.isUserPaused(id(1)));assertEquals(0,h.audible());
        }
    }

}
