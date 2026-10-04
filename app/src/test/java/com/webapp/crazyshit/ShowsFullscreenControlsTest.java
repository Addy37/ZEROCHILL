package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.Player;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerControlView;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.TimeBar;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w800dp-h360dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ShowsFullscreenControlsTest {
    private Activity host() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_CrazyShit);
        return activity;
    }

    private PlayerView view(Activity activity) {
        PlayerView view = (PlayerView) activity.getLayoutInflater().inflate(
                R.layout.view_shows_fullscreen_player, null);
        activity.setContentView(view);
        return view;
    }

    private Player player(AtomicBoolean playing) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[] { Player.class }, (proxy, method, args) -> {
                    if (method.getName().equals("isPlaying")) return playing.get();
                    if (method.getName().equals("getDuration")) return 28000L;
                    if (method.getName().equals("getCurrentPosition")) return 3000L;
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    if (method.getReturnType() == float.class) return 0f;
                    return null;
                });
    }

    @Test public void scrubAndPauseKeepControlsVisibleAndPipCancelsHide() {
        Activity host = host();
        PlayerView view = view(host);
        ShowsFullscreenControls controls = new ShowsFullscreenControls(view);
        AtomicBoolean playing = new AtomicBoolean(true);
        controls.bind(player(playing));
        controls.show();
        assertEquals(View.GONE, view.findViewById(androidx.media3.ui.R.id.exo_center_controls).getVisibility());
        TimeBar bar = view.findViewById(androidx.media3.ui.R.id.exo_progress);
        Set<TimeBar.OnScrubListener> listeners = ReflectionHelpers.getField(bar, "listeners");
        for (TimeBar.OnScrubListener listener : listeners) listener.onScrubStart(bar, 14000L);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2600));
        assertTrue(controls.isVisible());
        assertEquals("-0:14", ((TextView) view.findViewById(R.id.shows_remaining)).getText().toString());
        for (TimeBar.OnScrubListener listener : listeners) listener.onScrubStop(bar, 14000L, false);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2600));
        assertFalse(controls.isVisible());
        assertFalse(view.isControllerFullyVisible());
        controls.show();
        assertEquals(1f, view.findViewById(R.id.shows_fullscreen_chrome).getAlpha(), 0f);
        playing.set(false);
        controls.show();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3000));
        assertTrue(controls.isVisible());
        assertEquals(View.VISIBLE, view.findViewById(androidx.media3.ui.R.id.exo_center_controls).getVisibility());
        controls.suspend(true);
        assertFalse(view.isControllerFullyVisible());
        controls.suspend(false);
        assertTrue(view.isControllerFullyVisible());
        controls.release();
        host.finish();
    }

    @Test public void fittedViewportAndControlsRemainSeparateAcrossAspectRatios() throws Exception {
        Activity host = host();
        PlayerView view = view(host);
        ShowsFullscreenControls controls = new ShowsFullscreenControls(view);
        ((TextView) view.findViewById(R.id.player_title)).setText("Shows fullscreen");
        controls.show();
        AspectRatioFrameLayout content = view.findViewById(androidx.media3.ui.R.id.exo_content_frame);
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, view.getResizeMode());
        for (int[] viewport : new int[][] {{800,360}, {360,800}}) {
            for (float ratio : new float[] {4f/3f, 16f/9f, 9f/16f, 2.76f}) {
                content.setAspectRatio(ratio);
                layout(view, viewport[0], viewport[1]);
                assertEquals(ratio, content.getWidth() / (float) content.getHeight(), 0.02f);
                assertEquals(viewport[0], view.getWidth());
                assertEquals(viewport[1], view.getHeight());
                android.view.ViewGroup.MarginLayoutParams barParams = (android.view.ViewGroup.MarginLayoutParams)
                        view.findViewById(androidx.media3.ui.R.id.exo_progress).getLayoutParams();
                barParams.bottomMargin = 24;
                view.findViewById(androidx.media3.ui.R.id.exo_progress).setLayoutParams(barParams);
                assertEquals(0, barParams.bottomMargin);
                View progress = view.findViewById(R.id.shows_progress_row);
                View actions = view.findViewById(R.id.shows_action_row);
                assertTrue(progress.getBottom() <= actions.getTop());
            }
        }
        for (int mode : new int[] {AspectRatioFrameLayout.RESIZE_MODE_FILL, AspectRatioFrameLayout.RESIZE_MODE_ZOOM}) {
            view.setResizeMode(mode);
            assertEquals(mode, content.getResizeMode());
        }
        view.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        for (int[] viewport : new int[][] {{800,360}, {360,800}}) {
            layout(view, viewport[0], viewport[1]);
            File file = new File("build/reports/visual-tests/shows-fullscreen-" + viewport[0] + ".png");
            file.getParentFile().mkdirs();
            Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(bitmap));
            try (FileOutputStream out = new FileOutputStream(file)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
        }
        controls.release();
        host.finish();
    }

    @Test public void bothLandscapeCutoutEdgesAndGestureBottomLeaveActionsSafe() {
        Activity host = host();
        PlayerView view = view(host);
        ShowsFullscreenControls controls = new ShowsFullscreenControls(view);
        for (int cutoutLeft : new int[] {30, 0}) {
            WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(cutoutLeft, 0, 30-cutoutLeft, 0))
                    .setInsets(WindowInsetsCompat.Type.mandatorySystemGestures(), Insets.of(0,0,0,20)).build();
            controls.applyInsets(insets);
            View chrome = view.findViewById(R.id.shows_fullscreen_chrome);
            assertEquals(cutoutLeft, chrome.getPaddingLeft());
            assertEquals(30-cutoutLeft, chrome.getPaddingRight());
            FrameLayout.LayoutParams actions = (FrameLayout.LayoutParams) view.findViewById(R.id.shows_action_row).getLayoutParams();
            FrameLayout.LayoutParams progress = (FrameLayout.LayoutParams) view.findViewById(R.id.shows_progress_row).getLayoutParams();
            assertEquals(24, actions.bottomMargin);
            assertEquals(actions.bottomMargin + 48, progress.bottomMargin);
        }
        assertEquals("-0:00", FullscreenPlayerStyle.remaining(3000, -1));
        assertEquals("-0:25", FullscreenPlayerStyle.remaining(3000, 28000));
        controls.release();
        host.finish();
    }

    private void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }
}
