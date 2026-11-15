package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w320dp-h480dp-mdpi")
public final class UpdateCardOverflowTest {
    @Test
    public void longReleaseNotesRemainReadableAndActionsAreReachableByScrolling() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_CrazyShit);
        UpdateCardController controller = new UpdateCardController(activity);

        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < 24; i++) {
            summary.append("Long update note ").append(i)
                    .append(" stays in the card, even with a small viewport and larger copy. ");
        }
        controller.showAvailable("7.0.5", summary.toString(), () -> { }, () -> { });
        MaterialCardView card = activity.getWindow().getDecorView()
                .findViewWithTag("zerochill_update_card");
        assertNotNull(card);

        int width = Math.min(activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 28),
                dp(activity, 420));
        card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.AT_MOST));
        card.layout(0, 0, card.getMeasuredWidth(), card.getMeasuredHeight());

        ScrollView scroll = (ScrollView) card.getChildAt(0);
        LinearLayout content = (LinearLayout) scroll.getChildAt(0);
        TextView body = (TextView) content.getChildAt(3);
        assertEquals(summary.toString().trim(), body.getText().toString());
        assertNull("Update copy must not ellipsize", body.getEllipsize());
        assertTrue("Update copy must allow more than seven lines", body.getMaxLines() > 7);
        assertTrue("Card must fit on a small phone",
                card.getMeasuredHeight() <= Math.round(
                        activity.getResources().getDisplayMetrics().heightPixels * 0.80f));
        assertTrue("Long notes must overflow the scroll viewport",
                content.getMeasuredHeight() > scroll.getMeasuredHeight());
        scroll.scrollTo(0, content.getMeasuredHeight());
        assertTrue("Scrolling must reach the actions", scroll.getScrollY() > 0);
        LinearLayout actions = (LinearLayout) content.getChildAt(content.getChildCount() - 1);
        assertEquals(2, actions.getChildCount());
        assertEquals("Download update", ((TextView) actions.getChildAt(0)).getText().toString());
        assertEquals("Later", ((TextView) actions.getChildAt(1)).getText().toString());
        controller.detachImmediately();
    }

    @Test
    public void shortUpdateStaysCompactInsteadOfFillingTheScreen() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_CrazyShit);
        UpdateCardController controller = new UpdateCardController(activity);
        controller.showAvailable("7.0.5", "A small release summary.", () -> { }, () -> { });
        MaterialCardView card = activity.getWindow().getDecorView()
                .findViewWithTag("zerochill_update_card");
        assertNotNull(card);
        int width = Math.min(activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 28),
                dp(activity, 420));
        card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.AT_MOST));
        assertTrue(card.getMeasuredHeight() > 0);
        ScrollView scroll = (ScrollView) card.getChildAt(0);
        LinearLayout content = (LinearLayout) scroll.getChildAt(0);
        int cap = Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.80f);
        assertTrue("Short updates must remain compact: card=" + card.getMeasuredHeight()
                + ", scroll=" + scroll.getMeasuredHeight()
                + ", content=" + content.getMeasuredHeight()
                + ", cap=" + cap,
                card.getMeasuredHeight() < cap);
        controller.detachImmediately();
    }

    private static int dp(Activity activity, int dp) {
        return Math.round(dp * activity.getResources().getDisplayMetrics().density);
    }
}
