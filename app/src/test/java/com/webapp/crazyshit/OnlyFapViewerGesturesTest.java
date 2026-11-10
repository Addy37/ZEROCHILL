package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.view.MotionEvent;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;
import java.lang.reflect.Proxy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {35, 36})
public class OnlyFapViewerGesturesTest {
    @Test public void galleryTapRoutesToDockWhileNativeSeekAndDefaultControlsStayAvailable() {
        Activity host = Robolectric.buildActivity(Activity.class).setup().get();
        host.setTheme(R.style.Theme_CrazyShit);
        PlayerView surface = (PlayerView) host.getLayoutInflater().inflate(R.layout.view_shows_fullscreen_player, null);
        host.setContentView(surface);
        ShowsFullscreenControls controls = new ShowsFullscreenControls(surface);
        controls.suspend(true);
        int[] taps = {0}; long[] target = {-1};
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "isCommandAvailable": return true;
                case "getCurrentPosition": return 3000L;
                case "getDuration": return 28000L;
                case "seekTo": target[0] = (long) args[0]; return null;
            }
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == long.class) return 0L;
            return null;
        });
        FullscreenVideoGestures gallery = new FullscreenVideoGestures(surface, () -> player,
                controls, () -> true, true, () -> taps[0]++);
        tap(gallery, surface, 1000L);
        assertEquals(1, taps[0]); assertFalse(controls.isVisible());
        gallery.seek(true); assertEquals(13000L, target[0]);
        assertFalse(controls.isVisible()); // Unified chrome remains the normal presentation.
        gallery.cancel(); controls.suspend(false); controls.hide();
        FullscreenVideoGestures original = new FullscreenVideoGestures(surface, () -> player, controls, () -> true, true);
        tap(original, surface, 2000L);
        assertTrue(controls.isVisible()); assertEquals(1, taps[0]);
        original.cancel(); controls.release(); host.finish();
    }
    private void tap(FullscreenVideoGestures gestures, PlayerView surface, long time) {
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 30, 30, 0);
        MotionEvent up = MotionEvent.obtain(time, time + 20, MotionEvent.ACTION_UP, 30, 30, 0);
        gestures.onTouch(surface, down); gestures.onTouch(surface, up); down.recycle(); up.recycle();
    }
}
