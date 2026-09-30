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
        LinearLayout bubble = (LinearLayout) ((LinearLayout) row.getChildAt(1)).getChildAt(0);
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

    @Test public void sentMessageUpsertKeepsCompleteBodyAndNewestPosition() throws Exception {
        ZeroChillMessageActivity activity = shell();
        RecyclerView recycler = (RecyclerView) field(activity, "recycler");
        RecyclerView.Adapter adapter = recycler.getAdapter();
        Method replace = adapter.getClass().getDeclaredMethod("replace", java.util.List.class);
        replace.setAccessible(true);
        Method upsert = adapter.getClass().getDeclaredMethod(
                "upsert",
                ZeroChillSocialRepository.DirectMessage.class
        );
        upsert.setAccessible(true);
        replace.invoke(adapter, Arrays.asList(
                message("me", "2026-09-30T12:00:00Z", "previous")
        ));
        String fullBody = "this looks the same chatgpt";
        upsert.invoke(adapter, message("me", "2026-09-30T12:01:00Z", fullBody));
        assertEquals(2, adapter.getItemCount());

        RecyclerView.ViewHolder holder = adapter.createViewHolder(recycler, 0);
        adapter.bindViewHolder(holder, 1);
        LinearLayout row = (LinearLayout) holder.itemView;
        LinearLayout bubble = (LinearLayout) ((LinearLayout) row.getChildAt(1)).getChildAt(0);
        TextView body = (TextView) bubble.getChildAt(0);
        assertEquals(Gravity.RIGHT, row.getGravity() & Gravity.HORIZONTAL_GRAVITY_MASK);
        assertEquals(fullBody, body.getText().toString());
    }

    @Test public void completeWrappedTextFitsAfterSendRefreshAndHolderReuse() throws Exception {
        ZeroChillMessageActivity activity = shell();
        RecyclerView recycler = (RecyclerView) field(activity, "recycler");
        RecyclerView.Adapter adapter = recycler.getAdapter();
        Method replace = adapter.getClass().getDeclaredMethod("replace", java.util.List.class);
        replace.setAccessible(true);
        String[] bodies = {"this looks the same chatgpt", "Hi", "first line\nsecond line\nthird line",
                new String(new char[2000]).replace('\0', 'W')};
        RecyclerView.ViewHolder holder = adapter.createViewHolder(recycler, 0);
        LinearLayout row = (LinearLayout) holder.itemView;
        LinearLayout bubble = (LinearLayout) ((LinearLayout) row.getChildAt(1)).getChildAt(0);
        TextView body = (TextView) bubble.getChildAt(0);
        // Reproduce the prior default params: all text is in the layout, but lines
        // extend below the measured TextView. A getText/lineCount check misses this.
        replace.invoke(adapter, Arrays.asList(message("me", "2026-09-30T12:00:00Z", bodies[0])));
        adapter.bindViewHolder(holder, 0);
        body.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        measureRow(row);
        assertTrue("Legacy measurement must reproduce visual clipping",
                body.getLayout().getHeight() > body.getHeight() - body.getCompoundPaddingTop()
                        - body.getCompoundPaddingBottom());
        body.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        for (String sender : new String[]{"me", "other"}) {
            for (String value : bodies) {
                ZeroChillSocialRepository.DirectMessage item = message(sender, "2026-09-30T12:00:00Z", value);
                replace.invoke(adapter, Arrays.asList(item, message(sender, "2026-09-30T12:01:00Z", "next")));
                adapter.bindViewHolder(holder, 0); // grouped, hidden timestamp
                assertComplete(row, body, value);
                replace.invoke(adapter, Arrays.asList(item)); // refreshed, visible timestamp
                adapter.bindViewHolder(holder, 0);
                assertComplete(row, body, value);
            }
        }
    }

    private void measureRow(LinearLayout row) {
        row.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        row.layout(0, 0, 320, row.getMeasuredHeight());
    }

    private void assertComplete(LinearLayout row, TextView body, String value) {
        measureRow(row);
        assertEquals(value, body.getText().toString());
        android.text.Layout text = body.getLayout();
        assertNotNull(text);
        assertEquals(value.length(), text.getLineEnd(text.getLineCount() - 1));
        assertTrue("Every line must fit visibly", text.getHeight() <= body.getHeight()
                - body.getCompoundPaddingTop() - body.getCompoundPaddingBottom());
        assertTrue(body.getBottom() <= ((View) body.getParent()).getHeight());
        for (int line = 0; line < text.getLineCount(); line++) {
            assertEquals(0, text.getEllipsisCount(line));
        }
    }

    @Test public void sendUsesExactBodyAndUuidRefreshResolvesPendingWithoutDuplicates() throws Exception {
        ZeroChillMessageActivity activity = shell();
        RecyclerView recycler = (RecyclerView) field(activity, "recycler");
        EditText composer = (EditText) field(activity, "composer");
        java.util.ArrayList<ZeroChillSocialRepository.Callback<ZeroChillSocialRepository.DirectMessage>> callbacks = new java.util.ArrayList<>();
        String[] sent = new String[2];
        setField(activity, "messageSender", (ZeroChillMessageActivity.MessageSender) (context, recipient, id, value, callback) -> {
            sent[0] = id;
            sent[1] = value;
            callbacks.add(callback);
        });
        setField(activity, "threadLoader", (ZeroChillMessageActivity.ThreadLoader) (context, partner, callback) -> {});
        String value = "  this looks the same chatgpt\nsecond line  ";
        composer.setText(value);
        composer.requestFocus();
        LinearLayout root = (LinearLayout) recycler.getParent();
        layout(root, 420, recycler, composer); // keyboard-sized window
        invoke(activity, "sendMessage");
        assertEquals(value, sent[1]);
        invoke(activity, "sendMessage");
        assertEquals("Only one POST while sending", 1, callbacks.size());
        JSONObject payload = ZeroChillSocialRepository.directMessagePayload(sent[0], "other", sent[1]);
        assertEquals(value, payload.getString("body"));
        assertEquals(sent[0], payload.getString("id"));
        RecyclerView.Adapter adapter = recycler.getAdapter();
        assertEquals(1, adapter.getItemCount());
        RecyclerView.ViewHolder holder = adapter.createViewHolder(recycler, 0);
        adapter.bindViewHolder(holder, 0);
        TextView body = (TextView) field(holder, "body");
        TextView status = (TextView) field(holder, "status");
        assertEquals("Sending…", status.getText().toString());
        assertComplete((LinearLayout) holder.itemView, body, value);
        Method replace = adapter.getClass().getDeclaredMethod("replace", java.util.List.class);
        replace.setAccessible(true);
        replace.invoke(adapter, java.util.Collections.emptyList()); // stale refresh
        assertEquals(1, adapter.getItemCount());
        adapter.bindViewHolder(holder, 0);
        assertEquals(value, body.getText().toString());
        ZeroChillSocialRepository.DirectMessage confirmed = new ZeroChillSocialRepository.DirectMessage(
                payload.put("sender_id", "me").put("created_at", "2026-09-30T12:01:00Z"));
        replace.invoke(adapter, Arrays.asList(confirmed)); // server copy arrives before POST callback
        assertEquals(1, adapter.getItemCount());
        adapter.bindViewHolder(holder, 0);
        assertEquals(View.GONE, status.getVisibility());
        assertComplete((LinearLayout) holder.itemView, body, value);
        callbacks.get(0).complete(confirmed, null);
        assertEquals(1, adapter.getItemCount());
        assertEquals("", composer.getText().toString());
        replace.invoke(adapter, java.util.Collections.emptyList()); // keep confirmed local echo
        assertEquals(1, adapter.getItemCount());
        replace.invoke(adapter, Arrays.asList(confirmed));
        adapter.bindViewHolder(holder, 0);
        assertComplete((LinearLayout) holder.itemView, body, value);
        assertEquals(1, adapter.getItemCount());
    }

    @Test public void seenMovesToLatestReadOutgoingAndResetsOnRecycling() throws Exception {
        ZeroChillMessageActivity activity = shell();
        RecyclerView recycler = (RecyclerView) field(activity, "recycler");
        RecyclerView.Adapter adapter = recycler.getAdapter();
        Method replace = adapter.getClass().getDeclaredMethod("replace", java.util.List.class);
        replace.setAccessible(true);
        ZeroChillSocialRepository.DirectMessage first = readMessage("me", "2026-09-30T12:00:00Z", "first");
        ZeroChillSocialRepository.DirectMessage lastRead = readMessage("me", "2026-09-30T12:01:00Z", "read");
        ZeroChillSocialRepository.DirectMessage last = message("me", "2026-09-30T12:02:00Z", "unread");
        ZeroChillSocialRepository.DirectMessage incoming = readMessage("other", "2026-09-30T12:03:00Z", "reply");
        replace.invoke(adapter, Arrays.asList(first, lastRead, last, incoming));
        RecyclerView.ViewHolder holder = adapter.createViewHolder(recycler, 0);
        TextView status = (TextView) field(holder, "status");
        for (int position : new int[]{0, 1, 2, 3, 1, 0}) {
            adapter.bindViewHolder(holder, position);
            assertEquals(position == 1 ? View.VISIBLE : View.GONE, status.getVisibility());
            assertEquals(position == 1 ? "Seen" : "", status.getText().toString());
        }
        replace.invoke(adapter, Arrays.asList(first, lastRead,
                readMessage("me", "2026-09-30T12:02:00Z", "unread"), incoming));
        adapter.bindViewHolder(holder, 1);
        assertEquals(View.GONE, status.getVisibility());
        adapter.bindViewHolder(holder, 2);
        assertEquals("Seen", status.getText().toString());
    }

    @Test public void failedSendKeepsDraftAndEditedComposerSurvivesSuccess() throws Exception {
        ZeroChillMessageActivity activity = shell();
        RecyclerView recycler = (RecyclerView) field(activity, "recycler");
        EditText composer = (EditText) field(activity, "composer");
        java.util.ArrayList<ZeroChillSocialRepository.Callback<ZeroChillSocialRepository.DirectMessage>> callbacks = new java.util.ArrayList<>();
        String[] id = new String[1];
        setField(activity, "messageSender", (ZeroChillMessageActivity.MessageSender) (context, recipient, clientId, body, callback) -> {
            id[0] = clientId;
            callbacks.add(callback);
        });
        setField(activity, "threadLoader", (ZeroChillMessageActivity.ThreadLoader) (context, partner, callback) -> {});
        composer.setText("original complete message");
        invoke(activity, "sendMessage");
        callbacks.get(0).complete(null, new Exception("offline"));
        assertEquals(0, recycler.getAdapter().getItemCount());
        assertEquals("original complete message", composer.getText().toString());
        invoke(activity, "sendMessage");
        composer.setText("next draft");
        callbacks.get(1).complete(new ZeroChillSocialRepository.DirectMessage(new JSONObject()
                .put("id", id[0]).put("sender_id", "me").put("recipient_id", "other")
                .put("body", "original complete message").put("created_at", "2026-09-30T12:00:00Z")), null);
        assertEquals("next draft", composer.getText().toString());
        assertEquals(1, recycler.getAdapter().getItemCount());
    }

    @Test public void identicalMessagesHaveDistinctIdsAndReadRefreshBeatsOlderPostResponse() throws Exception {
        ZeroChillMessageActivity activity = shell();
        RecyclerView.Adapter adapter = ((RecyclerView) field(activity, "recycler")).getAdapter();
        Method upsert = adapter.getClass().getDeclaredMethod("upsert", ZeroChillSocialRepository.DirectMessage.class);
        Method replace = adapter.getClass().getDeclaredMethod("replace", java.util.List.class);
        upsert.setAccessible(true);
        replace.setAccessible(true);
        String firstId = java.util.UUID.randomUUID().toString();
        String secondId = java.util.UUID.randomUUID().toString();
        upsert.invoke(adapter, new ZeroChillSocialRepository.DirectMessage(firstId, "other", "same full text"));
        upsert.invoke(adapter, new ZeroChillSocialRepository.DirectMessage(secondId, "other", "same full text"));
        ZeroChillSocialRepository.DirectMessage first = new ZeroChillSocialRepository.DirectMessage(new JSONObject()
                .put("id", firstId).put("sender_id", "me").put("body", "same full text")
                .put("created_at", "2026-09-30T12:00:00Z").put("read_at", "2026-09-30T12:02:00Z"));
        replace.invoke(adapter, Arrays.asList(first));
        assertEquals(2, adapter.getItemCount());
        upsert.invoke(adapter, new ZeroChillSocialRepository.DirectMessage(new JSONObject()
                .put("id", firstId).put("sender_id", "me").put("body", "same full text")
                .put("created_at", "2026-09-30T12:00:00Z")));
        assertEquals(2, adapter.getItemCount());
        RecyclerView recycler = (RecyclerView) field(activity, "recycler");
        RecyclerView.ViewHolder holder = adapter.createViewHolder(recycler, 0);
        adapter.bindViewHolder(holder, 0);
        assertEquals("Seen", ((TextView) field(holder, "status")).getText().toString());
        adapter.bindViewHolder(holder, 1);
        assertEquals("Sending…", ((TextView) field(holder, "status")).getText().toString());
    }

    @Test public void shortPendingBubbleKeepsRightEdgeAndRecycledIncomingResetsOpacity() throws Exception {
        ZeroChillMessageActivity activity = shell();
        RecyclerView recycler = (RecyclerView) field(activity, "recycler");
        RecyclerView.Adapter adapter = recycler.getAdapter();
        Method upsert = adapter.getClass().getDeclaredMethod("upsert", ZeroChillSocialRepository.DirectMessage.class);
        Method replace = adapter.getClass().getDeclaredMethod("replace", java.util.List.class);
        upsert.setAccessible(true);
        replace.setAccessible(true);
        upsert.invoke(adapter, new ZeroChillSocialRepository.DirectMessage(java.util.UUID.randomUUID().toString(), "other", "Hi"));
        RecyclerView.ViewHolder holder = adapter.createViewHolder(recycler, 0);
        adapter.bindViewHolder(holder, 0);
        LinearLayout row = (LinearLayout) holder.itemView;
        LinearLayout bubble = (LinearLayout) field(holder, "bubble");
        measureRow(row);
        assertEquals(((View) bubble.getParent()).getWidth(), bubble.getRight());
        assertEquals(0.82f, bubble.getAlpha(), 0.001f);
        replace.invoke(adapter, Arrays.asList(message("other", "2026-09-30T12:00:00Z", "incoming")));
        adapter.bindViewHolder(holder, 0);
        assertEquals(View.GONE, ((TextView) field(holder, "status")).getVisibility());
        assertEquals(1f, bubble.getAlpha(), 0.001f);
        assertComplete(row, (TextView) field(holder, "body"), "incoming");
    }

    private ZeroChillSocialRepository.DirectMessage readMessage(String sender, String time, String value) throws Exception {
        return new ZeroChillSocialRepository.DirectMessage(new JSONObject().put("id", time)
                .put("sender_id", sender).put("body", value).put("created_at", time)
                .put("read_at", "2026-09-30T12:05:00Z"));
    }

    private void invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
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
