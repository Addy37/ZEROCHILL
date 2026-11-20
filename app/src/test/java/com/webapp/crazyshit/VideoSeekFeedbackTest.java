package com.webapp.crazyshit;

import android.app.Application;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class VideoSeekFeedbackTest {
    @Test public void rippleAndDirectionalLabelsFollowAccentOnTheSameView() {
        Application context = RuntimeEnvironment.getApplication();
        AppearanceTheme.prefs(context).edit().remove(AppearanceTheme.KEY_ACCENT)
                .putBoolean("immersive_motion_enabled", true).apply();
        VideoSeekFeedback feedback = new VideoSeekFeedback(context);
        feedback.layout(0, 0, 360, 240);

        assertFeedback(feedback, true, 10, AppearanceTheme.Accent.CYAN, "+10s ››");
        AppearanceTheme.setAccent(context, AppearanceTheme.Accent.PURPLE);
        assertFeedback(feedback, false, 10, AppearanceTheme.Accent.PURPLE, "‹‹ -10s");
        AppearanceTheme.setAccent(context, AppearanceTheme.Accent.GREEN);
        assertFeedback(feedback, true, 20, AppearanceTheme.Accent.GREEN, "+20s ››");
        feedback.hide();
        assertEquals(View.INVISIBLE, feedback.getVisibility());
    }

    private static void assertFeedback(VideoSeekFeedback feedback, boolean forward, int seconds,
            AppearanceTheme.Accent accent, String label) {
        feedback.show(forward, seconds, forward ? 270f : 90f, 120f);
        FeedbackCanvas canvas = new FeedbackCanvas();
        feedback.onDraw(canvas);

        int rgb = accent.color & 0xFFFFFF;
        assertEquals(List.of(rgb, rgb), canvas.rippleColors);
        assertEquals(rgb, canvas.labelColor);
        assertEquals(label, canvas.label);
    }

    private static final class FeedbackCanvas extends Canvas {
        final List<Integer> rippleColors = new ArrayList<>();
        int labelColor;
        String label;

        @Override public void drawCircle(float x, float y, float radius, Paint paint) {
            rippleColors.add(paint.getColor() & 0xFFFFFF);
        }

        @Override public void drawText(String text, float x, float y, Paint paint) {
            label = text;
            labelColor = paint.getColor() & 0xFFFFFF;
        }
    }
}
