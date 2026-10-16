package com.webapp.crazyshit;

import android.app.Application;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.media3.exoplayer.ExoPlayer;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w400dp-h800dp-xhdpi",
        shadows = { ShitTokInteractionUiTest.Account.class, ShitTokInteractionUiTest.Social.class })
public class ShitTokScrubInteractionTest {
    @Test public void dragFreezesMainPlayerAndReleaseSeeksOnceRestoringPriorState() {
        checkDrag(true, true);
        checkDrag(false, true);
    }

    @Test public void touchCancellationNeverSeeksAndRestoresPriorState() {
        checkDrag(true, false);
        checkDrag(false, false);
    }

    @Test public void realSeekBarTouchSequenceCommitsOnUpButNeverOnCancel() {
        for (int end : new int[]{MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL}) {
            try (var f = ShitTokInteractionUiTest.fixture()) {
                ReflectionHelpers.setField(f.selected, "stream", null);
                SeekBar bar = ReflectionHelpers.getField(f.selected, "seekBar");
                long down = SystemClock.uptimeMillis();
                ShitTokInteractionUiTest.event(bar, down, MotionEvent.ACTION_DOWN,
                        bar.getWidth() * .2f, bar.getHeight() / 2f);
                ShitTokInteractionUiTest.event(bar, down, MotionEvent.ACTION_MOVE,
                        bar.getWidth() * .8f, bar.getHeight() / 2f);
                assertTrue(ReflectionHelpers.getField(f.selected, "scrubbing"));
                assertFalse(f.selectedPlayer.readyToPlay);
                assertEquals(0, f.selectedPlayer.seeks);
                ShitTokInteractionUiTest.event(bar, down, end,
                        bar.getWidth() * .8f, bar.getHeight() / 2f);
                assertEquals(end == MotionEvent.ACTION_UP ? 1 : 0, f.selectedPlayer.seeks);
                assertTrue(f.selectedPlayer.readyToPlay);
                assertFalse(ReflectionHelpers.getField(f.selected, "scrubbing"));
            }
        }
    }

    private void checkDrag(boolean playing, boolean commit) {
        try (var f = ShitTokInteractionUiTest.fixture()) {
            AtomicLong position = new AtomicLong(12_000L);
            ExoPlayer player = (ExoPlayer) Proxy.newProxyInstance(ExoPlayer.class.getClassLoader(),
                    new Class<?>[]{ExoPlayer.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getCurrentPosition")) return position.get();
                        if (method.getName().equals("seekTo")) position.set((Long) args[0]);
                        return method.invoke(f.selectedPlayer.player, args);
                    });
            ReflectionHelpers.setField(f.selected, "player", player);
            ReflectionHelpers.setField(f.selected, "stream", null); // Time-only fallback still seeks.
            f.selectedPlayer.readyToPlay = playing;
            SeekBar bar = ReflectionHelpers.getField(f.selected, "seekBar");
            SeekBar.OnSeekBarChangeListener listener = ReflectionHelpers.getField(bar, "mOnSeekBarChangeListener");
            listener.onStartTrackingTouch(bar);
            assertFalse(f.selectedPlayer.readyToPlay);
            ReflectionHelpers.callInstanceMethod(f.selected, "playOwnedPlayer");
            assertFalse("ownership refresh must not unfreeze a drag", f.selectedPlayer.readyToPlay);
            for (int progress : new int[]{250, 800, 500}) {
                bar.setProgress(progress);
                listener.onProgressChanged(bar, progress, true);
                assertEquals(0, f.selectedPlayer.seeks);
                assertEquals(12_000L, position.get());
            }
            if (commit) listener.onStopTrackingTouch(bar);
            else {
                MotionEvent cancel = MotionEvent.obtain(SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 10, 10, 0);
                bar.dispatchTouchEvent(cancel);
                cancel.recycle();
            }
            listener.onStopTrackingTouch(bar); // A late/default stop must not commit again.
            assertEquals(commit ? 1 : 0, f.selectedPlayer.seeks);
            assertEquals(commit ? 30_000L : 12_000L, position.get());
            assertEquals(playing, f.selectedPlayer.readyToPlay);
            assertFalse(ReflectionHelpers.getField(f.selected, "scrubbing"));
        }
    }

    @Test public void portraitFrameFillsRoundedThumbnailWithTimestampOutsideBelow() {
        try (var f = ShitTokInteractionUiTest.fixture()) {
            ShitTokScrubPreviewView card = ReflectionHelpers.getField(f.selected, "scrubPreviewView");
            SeekBar bar = ReflectionHelpers.getField(f.selected, "seekBar");
            card.frame(Bitmap.createBitmap(180, 320, Bitmap.Config.ARGB_8888));
            card.show((FrameLayout) ShitTokInteractionUiTest.root(f.selected), bar, 30_000L, 60_000L);
            card.layout(0, 0, card.getMeasuredWidth(), card.getMeasuredHeight());
            ViewGroup thumbnail = card.findViewWithTag("shittok_scrub_thumbnail");
            TextView time = (TextView) card.getChildAt(1);
            assertTrue(thumbnail.getHeight() > thumbnail.getWidth());
            assertTrue(time.getTop() >= thumbnail.getBottom());
            assertEquals("0:30 / 1:00", time.getText().toString());
            assertNull(card.getBackground());
            assertNotNull(thumbnail.getBackground());
            assertEquals(ImageView.ScaleType.CENTER_CROP, ((ImageView) thumbnail.getChildAt(0)).getScaleType());
            assertEquals(9f / 16f, ShitTokInteractionPolicy.PREVIEW_WIDTH_PX
                    / (float) ShitTokInteractionPolicy.PREVIEW_HEIGHT_PX, 0.001f);
        }
    }
}
