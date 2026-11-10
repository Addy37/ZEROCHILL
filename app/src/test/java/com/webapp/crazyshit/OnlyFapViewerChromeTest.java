package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import androidx.media3.common.Player;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import java.lang.reflect.Proxy;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {35, 36}, qualifiers = "w360dp-h800dp-mdpi")
public class OnlyFapViewerChromeTest {
    private static class Actions implements OnlyFapViewerChrome.Actions {
        int close, favorite, playback, fullscreen, more, hide;
        public void close() { close++; } public void favorite() { favorite++; }
        public void playback() { playback++; } public void fullscreen() { fullscreen++; }
        public void more(View anchor) { more++; } public void hide() { hide++; }
    }
    private View find(View view, String description) {
        if (view.getVisibility() != View.VISIBLE) return null;
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), description); if (found != null) return found;
            }
        }
        return null;
    }
    @Test public void photoPillsRetainCloseFavoriteOverflowAndSavedCreatorCallbacks() {
        Activity host = Robolectric.buildActivity(Activity.class).setup().get();
        Actions actions = new Actions(); OnlyFapViewerChrome dock = new OnlyFapViewerChrome(host, actions);
        host.setContentView(dock); dock.updateMedia("photo", "Photo title", 2, 7, false);
        find(dock, "Close viewer").performClick(); find(dock, "Favorite media").performClick(); find(dock, "More options").performClick();
        assertEquals(1, actions.close); assertEquals(1, actions.favorite); assertEquals(1, actions.more);
        int[] opens = {0}; dock.setCreatorAction("Creator", () -> opens[0]++);
        assertTrue(find(dock, "Open gallery for Creator").performClick()); assertEquals(1, opens[0]);
        dock.updateFavorite(true); assertNotNull(find(dock, "Unfavorite media"));
        dock.dispose(); host.finish();
    }
    @Test public void videoDockUsesSamePlayerForPlaybackSeekAndFullscreenWithoutCreatingOne() {
        Activity host = Robolectric.buildActivity(Activity.class).setup().get();
        Actions actions = new Actions(); OnlyFapViewerChrome dock = new OnlyFapViewerChrome(host, actions);
        host.setContentView(dock); dock.updateMedia("video", "Video", 1, 3, true);
        long[] seek = {-1}; int[] listeners = {0};
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getDuration": return 120000L;
                case "getCurrentPosition": return 30000L;
                case "getPlayWhenReady": return true;
                case "getPlaybackState": return Player.STATE_READY;
                case "isCommandAvailable": return true;
                case "seekTo": if (args.length == 1) seek[0] = (long) args[0]; return null;
                case "addListener": listeners[0]++; return null;
                case "removeListener": listeners[0]--; return null;
            }
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == long.class) return 0L;
            return null;
        });
        dock.updatePlayer(player); dock.updateOrientation(true, false);
        find(dock, "Pause video").performClick(); find(dock, "Fullscreen").performClick();
        assertEquals(1, actions.playback); assertEquals(1, actions.fullscreen);
        SeekBar bar = (SeekBar) find(dock, "Video position"); assertTrue(bar.isEnabled());
        // Accessibility/keyboard adjustment dispatches the same native seek callback.
        bar.onKeyDown(android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_RIGHT));
        assertTrue(seek[0] > 30000L);
        dock.updatePlayer(null); assertEquals(0, listeners[0]); assertFalse(bar.isEnabled());
        dock.dispose(); host.finish();
    }
    @Test public void photosWaitForArtworkBeforeIdleHideAndHiddenDockCancelsWork() {
        Activity host = Robolectric.buildActivity(Activity.class).setup().get();
        Actions actions = new Actions(); OnlyFapViewerChrome dock = new OnlyFapViewerChrome(host, actions);
        host.setContentView(dock); dock.updateMedia("photo", "Photo", 1, 1, false);
        ShadowLooper.idleMainLooper(3500L, TimeUnit.MILLISECONDS); assertEquals(0, actions.hide);
        dock.artworkReady(); ShadowLooper.idleMainLooper(3300L, TimeUnit.MILLISECONDS); assertEquals(1, actions.hide);
        dock.setVisible(false); dock.artworkReady(); ShadowLooper.idleMainLooper(3500L, TimeUnit.MILLISECONDS);
        assertEquals(1, actions.hide); dock.dispose(); host.finish();
    }
    @Test public void openVideoMenuHoldsHideAcrossPlayerEventsAndDismissalRestartsIt() {
        Activity host = Robolectric.buildActivity(Activity.class).setup().get();
        Actions actions = new Actions(); OnlyFapViewerChrome dock = new OnlyFapViewerChrome(host, actions);
        host.setContentView(dock); dock.updateMedia("video", "Video", 1, 1, true);
        Player.Listener[] listener = {null}; int[] listeners = {0};
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getDuration": return 120000L;
                case "getCurrentPosition": return 30000L;
                case "getPlayWhenReady": return true;
                case "getPlaybackState": return Player.STATE_READY;
                case "isCommandAvailable": return true;
                case "addListener": listener[0] = (Player.Listener) args[0]; listeners[0]++; return null;
                case "removeListener": listeners[0]--; return null;
            }
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == long.class) return 0L;
            return null;
        });
        dock.updatePlayer(player); dock.setMenuOpen(true);
        listener[0].onEvents(player, null);
        ShadowLooper.idleMainLooper(6000L, TimeUnit.MILLISECONDS);
        assertEquals(0, actions.hide);
        dock.setMenuOpen(false);
        ShadowLooper.idleMainLooper(3300L, TimeUnit.MILLISECONDS);
        assertEquals(1, actions.hide);
        dock.setMenuOpen(true); dock.hold(); dock.dispose();
        listener[0].onEvents(player, null);
        dock.setMenuOpen(false); dock.setVisible(true);
        ShadowLooper.idleMainLooper(6000L, TimeUnit.MILLISECONDS);
        assertEquals(1, actions.hide); assertEquals(0, listeners[0]);
        assertFalse((Boolean) org.robolectric.util.ReflectionHelpers.getField(dock, "menuHeld"));
        assertFalse((Boolean) org.robolectric.util.ReflectionHelpers.getField(dock, "held"));
        host.finish();
    }
    @Test public void photoMenuDismissalRearmsIdleHideButReturnHoldRemainsIndependent() {
        Activity host = Robolectric.buildActivity(Activity.class).setup().get();
        Actions actions = new Actions(); OnlyFapViewerChrome dock = new OnlyFapViewerChrome(host, actions);
        host.setContentView(dock); dock.updateMedia("photo", "Photo", 1, 1, false);
        dock.artworkReady(); dock.setMenuOpen(true);
        ShadowLooper.idleMainLooper(6000L, TimeUnit.MILLISECONDS); assertEquals(0, actions.hide);
        dock.setMenuOpen(false);
        ShadowLooper.idleMainLooper(3300L, TimeUnit.MILLISECONDS); assertEquals(1, actions.hide);
        dock.hold(); dock.setMenuOpen(true); dock.setMenuOpen(false);
        ShadowLooper.idleMainLooper(6000L, TimeUnit.MILLISECONDS); assertEquals(1, actions.hide);
        dock.dispose(); host.finish();
    }
    @Test public void lifecycleHideCancelsInterruptedScrubAndRestoresPlaybackSyncAndIdleTimer() {
        Activity host = Robolectric.buildActivity(Activity.class).setup().get();
        Actions actions = new Actions(); OnlyFapViewerChrome dock = new OnlyFapViewerChrome(host, actions);
        host.setContentView(dock); dock.updateMedia("video", "Video", 1, 1, true);
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getDuration": return 120000L;
                case "getCurrentPosition": return 60000L;
                case "getPlayWhenReady": return true;
                case "getPlaybackState": return Player.STATE_READY;
                case "isCommandAvailable": return true;
            }
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == long.class) return 0L;
            return null;
        });
        dock.updatePlayer(player);
        org.robolectric.util.ReflectionHelpers.setField(dock, "scrubbing", true);
        dock.hold(); dock.setMenuOpen(true);
        SeekBar bar = (SeekBar) find(dock, "Video position"); bar.setProgress(10);
        dock.setVisible(false); dock.setVisible(true);
        assertFalse((Boolean) org.robolectric.util.ReflectionHelpers.getField(dock, "scrubbing"));
        assertFalse((Boolean) org.robolectric.util.ReflectionHelpers.getField(dock, "held"));
        assertFalse((Boolean) org.robolectric.util.ReflectionHelpers.getField(dock, "menuHeld"));
        assertEquals(500, bar.getProgress());
        ShadowLooper.idleMainLooper(3300L, TimeUnit.MILLISECONDS); assertEquals(1, actions.hide);
        dock.dispose(); host.finish();
    }
    @Test public void clockSupportsHoursAndNativeDockFitsSmallLandscape() {
        assertEquals("0:00", OnlyFapViewerChrome.clock(-1)); assertEquals("1:01:01", OnlyFapViewerChrome.clock(3661000));
        Activity host = Robolectric.buildActivity(Activity.class).setup().get();
        OnlyFapViewerChrome dock = new OnlyFapViewerChrome(host, new Actions());
        dock.updateMedia("video", "A long title", 9, 20, true); dock.updateOrientation(true, true);
        dock.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.AT_MOST));
        dock.layout(0, 0, dock.getMeasuredWidth(), dock.getMeasuredHeight());
        assertTrue(dock.getMeasuredHeight() < 130); assertEquals(320, dock.getMeasuredWidth());
        assertNotNull(find(dock, "Exit fullscreen")); dock.dispose(); host.finish();
    }
}
