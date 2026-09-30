package com.webapp.crazyshit;

import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ZeroChillMessageThreadUiTest {
    private ZeroChillSocialRepository.DirectMessage message(String sender, String time, String body) throws Exception {
        return new ZeroChillSocialRepository.DirectMessage(new JSONObject()
                .put("id", time).put("sender_id", sender).put("recipient_id", "me")
                .put("created_at", time).put("body", body));
    }

    @Test public void groupingBreaksOnSenderPauseInvalidTimeAndDayBoundary() throws Exception {
        ZeroChillSocialRepository.DirectMessage a = message("other", "2026-09-30T12:00:00Z", "one");
        assertTrue(ZeroChillMessageActivity.grouped(a, message("other", "2026-09-30T12:05:00Z", "two")));
        assertFalse(ZeroChillMessageActivity.grouped(a, message("me", "2026-09-30T12:01:00Z", "reply")));
        assertFalse(ZeroChillMessageActivity.grouped(a, message("other", "2026-09-30T12:05:01Z", "later")));
        assertFalse(ZeroChillMessageActivity.grouped(a, message("other", "bad", "invalid")));
        assertFalse(ZeroChillMessageActivity.grouped(a, message("other", "2026-09-30T11:59:00Z", "older")));
        assertFalse(ZeroChillMessageActivity.grouped(null, a));
        java.util.TimeZone previous = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
            assertFalse(ZeroChillMessageActivity.grouped(
                    message("other", "2026-09-30T23:59:00Z", "night"),
                    message("other", "2026-10-01T00:01:00Z", "morning")));
        } finally { java.util.TimeZone.setDefault(previous); }
    }

    @Test public void rowsDistinguishSendersGroupAvatarsAndResetRecycledState() throws Exception {
        ZeroChillMessageActivity activity = shell();
        RecyclerView recycler = (RecyclerView) field(activity, "recycler");
        RecyclerView.Adapter adapter = recycler.getAdapter();
        Method replace = adapter.getClass().getDeclaredMethod("replace", java.util.List.class);
        replace.setAccessible(true);
        replace.invoke(adapter, Arrays.asList(
                message("other", "2026-09-30T12:00:00Z", "Hi"),
                message("other", "2026-09-30T12:01:00Z", "Another incoming message"),
                message("me", "2026-09-30T12:02:00Z", "Hello"),
                message("me", "2026-09-30T12:03:00Z", new String(new char[2000]).replace('\0', 'W'))));
        RecyclerView.ViewHolder holder = adapter.createViewHolder(recycler, 0);
        LinearLayout row = (LinearLayout) holder.itemView;
        ImageView avatar = (ImageView) row.getChildAt(0);
        LinearLayout bubble = (LinearLayout) row.getChildAt(1);
        TextView body = (TextView) bubble.getChildAt(0);
        TextView time = (TextView) bubble.getChildAt(1);
        adapter.bindViewHolder(holder, 0);
        assertEquals(Gravity.LEFT, row.getGravity() & Gravity.HORIZONTAL_GRAVITY_MASK);
        assertEquals(View.INVISIBLE, avatar.getVisibility());
        assertEquals(View.GONE, time.getVisibility());
        assertEquals(Color.rgb(35, 38, 45), ((GradientDrawable) bubble.getBackground()).getColor().getDefaultColor());
        int firstGap = ((RecyclerView.LayoutParams) row.getLayoutParams()).topMargin;
        adapter.bindViewHolder(holder, 1);
        assertEquals(View.VISIBLE, avatar.getVisibility());
        assertEquals(View.VISIBLE, time.getVisibility());
        assertNotNull(avatar.getDrawable());
        assertTrue(((RecyclerView.LayoutParams) row.getLayoutParams()).topMargin < firstGap);
        adapter.bindViewHolder(holder, 2);
        assertEquals(Gravity.RIGHT, row.getGravity() & Gravity.HORIZONTAL_GRAVITY_MASK);
        assertEquals(View.GONE, avatar.getVisibility());
        assertEquals(Color.rgb(8, 146, 208), ((GradientDrawable) bubble.getBackground()).getColor().getDefaultColor());
        assertEquals(Color.WHITE, body.getCurrentTextColor());
        assertEquals(View.GONE, time.getVisibility());
        adapter.bindViewHolder(holder, 3);
        assertEquals(View.VISIBLE, time.getVisibility());
        assertFalse(time.getText().toString().isEmpty());
        row.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        row.layout(0, 0, 320, row.getMeasuredHeight());
        assertEquals(2000, body.getText().length());
        assertTrue(body.getLineCount() > 1);
        assertTrue(bubble.getMeasuredWidth() < row.getMeasuredWidth());
        adapter.bindViewHolder(holder, 1);
        assertEquals(View.VISIBLE, avatar.getVisibility());
        assertEquals(View.VISIBLE, time.getVisibility());
        assertEquals("Another incoming message", body.getText().toString());
    }

    @Test public void resizedConversationKeepsComposerBelowScrollableThreadAndRestoresHeight() throws Exception {
        ZeroChillMessageActivity activity = shell();
        RecyclerView recycler = (RecyclerView) field(activity, "recycler");
        EditText composer = (EditText) field(activity, "composer");
        LinearLayout root = (LinearLayout) recycler.getParent();
        int fullHeight = layout(root, 800, recycler, composer);
        composer.setText("Typing with the keyboard open");
        composer.requestFocus();
        int resizedHeight = layout(root, 420, recycler, composer);
        assertTrue(resizedHeight < fullHeight);
        assertEquals("Typing with the keyboard open", composer.getText().toString());
        assertTrue(recycler.isFocusableInTouchMode() || recycler.getLayoutManager().canScrollVertically());
        assertEquals(fullHeight, layout(root, 800, recycler, composer));
    }

    private int layout(LinearLayout root, int height, RecyclerView recycler, EditText composer) {
        root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 360, height);
        View compose = (View) composer.getParent();
        assertTrue(compose.getBottom() <= height);
        assertTrue(recycler.getBottom() <= compose.getTop());
        assertTrue(composer.getBottom() <= compose.getHeight());
        return recycler.getHeight();
    }

    // Build the real UI without account/network calls. This tests resize geometry, not a device IME.
    private ZeroChillMessageActivity shell() throws Exception {
        ZeroChillMessageActivity activity = Robolectric.buildActivity(ZeroChillMessageActivity.class).get();
        Field partnerId = ZeroChillMessageActivity.class.getDeclaredField("partnerId");
        partnerId.setAccessible(true);
        partnerId.set(activity, "other");
        Method build = ZeroChillMessageActivity.class.getDeclaredMethod("buildUi");
        build.setAccessible(true);
        build.invoke(activity);
        return activity;
    }

    private Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
}
