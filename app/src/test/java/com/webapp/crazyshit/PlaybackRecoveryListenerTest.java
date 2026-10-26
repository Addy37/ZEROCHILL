package com.webapp.crazyshit;

import android.app.Application;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class PlaybackRecoveryListenerTest {
    @Test public void inPlaceMediaSwitchesRetainOnlyOneMetricsListenerAndCancelRemovesIt() {
        Set<Player.Listener> listeners = new HashSet<>();
        ExoPlayer engine = (ExoPlayer) Proxy.newProxyInstance(ExoPlayer.class.getClassLoader(),
                new Class<?>[]{ExoPlayer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("addListener")) listeners.add((Player.Listener) args[0]);
                    if (method.getName().equals("removeListener")) listeners.remove(args[0]);
                    return null;
                });
        PlaybackRecovery recovery = new PlaybackRecovery();
        for (int index = 0; index < 20; index++) {
            recovery.bind(engine, "https://example.test/episode/" + index);
            assertEquals(1, listeners.size());
        }
        recovery.cancel();
        assertTrue(listeners.isEmpty());
    }
}
