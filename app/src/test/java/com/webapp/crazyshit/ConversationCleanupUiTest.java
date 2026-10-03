package com.webapp.crazyshit;

import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ConversationCleanupUiTest {
    @Test public void longPressConfirmationCancelAndFailureKeepConversation() throws Exception {
        ZeroChillInboxActivity activity = shell();
        RecyclerView recycler = (RecyclerView) field(activity, "recycler");
        RecyclerView.Adapter adapter = recycler.getAdapter();
        ZeroChillSocialRepository.Conversation item = conversation("other", 3);
        replace(adapter, item);
        ArrayList<ZeroChillSocialRepository.Callback<Boolean>> clears = new ArrayList<>();
        set(activity, "conversationClearer", (ZeroChillInboxActivity.ConversationClearer)
                (context, partner, callback) -> clears.add(callback));
        set(activity, "inboxLoader", (ZeroChillInboxActivity.InboxLoader) (context, callback) -> {});
        RecyclerView.ViewHolder holder = adapter.createViewHolder(recycler, 0);
        adapter.bindViewHolder(holder, 0);
        assertTrue(holder.itemView.performLongClick());
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        idle();
        assertEquals(0, clears.size());
        assertEquals(1, adapter.getItemCount());
        holder.itemView.performLongClick();
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertEquals(1, clears.size());
        assertEquals(1, adapter.getItemCount()); // Wait for server confirmation.
        clears.get(0).complete(false, new Exception("offline"));
        idle();
        assertEquals(1, adapter.getItemCount());
    }

    @Test public void confirmedClearRemovesOnlyPartnerUpdatesUnreadAndRejectsPendingInbox() throws Exception {
        ZeroChillInboxActivity activity = shell();
        RecyclerView recycler = (RecyclerView) field(activity, "recycler");
        RecyclerView.Adapter adapter = recycler.getAdapter();
        ZeroChillSocialRepository.Conversation cleared = conversation("other", 3);
        ZeroChillSocialRepository.Conversation kept = conversation("third", 2);
        replace(adapter, cleared, kept);
        ArrayList<ZeroChillSocialRepository.Callback<ArrayList<ZeroChillSocialRepository.Conversation>>> loads = new ArrayList<>();
        set(activity, "inboxLoader", (ZeroChillInboxActivity.InboxLoader)
                (context, callback) -> loads.add(callback));
        ArrayList<ZeroChillSocialRepository.Callback<Boolean>> clears = new ArrayList<>();
        set(activity, "conversationClearer", (ZeroChillInboxActivity.ConversationClearer)
                (context, partner, callback) -> clears.add(callback));
        invoke(activity, "loadInbox");
        RecyclerView.ViewHolder holder = adapter.createViewHolder(recycler, 0);
        adapter.bindViewHolder(holder, 0);
        holder.itemView.performLongClick();
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        clears.get(0).complete(true, null);
        idle();
        assertEquals(1, adapter.getItemCount());
        assertEquals("2 unread messages", ((TextView) field(activity, "status")).getText().toString());
        loads.get(0).complete(new ArrayList<>(Arrays.asList(cleared, kept)), null);
        idle();
        assertEquals("Old inbox callback cannot restore removed row", 1, adapter.getItemCount());
        loads.get(1).complete(new ArrayList<>(Arrays.asList(kept)), null);
        idle();
        assertEquals(1, adapter.getItemCount());
        invoke(activity, "loadInbox");
        loads.get(2).complete(new ArrayList<>(Arrays.asList(conversation("other", 1), kept)), null);
        idle();
        assertEquals("Later server messages can restore conversation", 2, adapter.getItemCount());
        assertEquals("3 unread messages", ((TextView) field(activity, "status")).getText().toString());
    }

    @Test public void cleanupRevisionAndBadgeOwnerStayAccountScoped() {
        Context context = org.robolectric.RuntimeEnvironment.getApplication();
        ZeroChillMessageBadgeStore.conversationCleared(context, "account-a");
        assertEquals(1, ZeroChillMessageBadgeStore.cleanupRevision(context, "account-a"));
        assertEquals(0, ZeroChillMessageBadgeStore.cleanupRevision(context, "account-b"));
        context.getSharedPreferences(ZeroChillMessageBadgeStore.PREFS, Context.MODE_PRIVATE).edit()
                .putString("owner", "account-a").putInt("unread", 9).apply();
        assertEquals("Signed-out account must not inherit cached badge", 0,
                ZeroChillMessageBadgeStore.unreadCount(context));
    }

    @Test public void serverCutoffRemovesConfirmedLocalEchoFromReopenedThread() throws Exception {
        ZeroChillMessageActivity activity = Robolectric.buildActivity(ZeroChillMessageActivity.class).get();
        set(activity, "partnerId", "other");
        invoke(activity, "buildUi");
        RecyclerView.Adapter adapter = ((RecyclerView) field(activity, "recycler")).getAdapter();
        Method upsert = adapter.getClass().getDeclaredMethod("upsert", ZeroChillSocialRepository.DirectMessage.class);
        upsert.setAccessible(true);
        upsert.invoke(adapter, conversation("other", 0).lastMessage);
        assertEquals(1, adapter.getItemCount());
        ZeroChillSocialRepository.ClearedMessageList messages =
                new ZeroChillSocialRepository.ClearedMessageList("2026-10-03T12:00:00Z");
        Method replace = adapter.getClass().getDeclaredMethod("replace", java.util.List.class);
        replace.setAccessible(true);
        replace.invoke(adapter, messages);
        assertEquals("Clear cutoff must also remove confirmed local send echoes", 0, adapter.getItemCount());
        messages.add(new ZeroChillSocialRepository.DirectMessage(new JSONObject().put("id", "new")
                .put("sender_id", "other").put("body", "new reply")
                .put("created_at", "2026-10-03T12:01:00Z")));
        replace.invoke(adapter, messages);
        assertEquals(1, adapter.getItemCount());
    }

    private void idle() { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); }

    private ZeroChillInboxActivity shell() throws Exception {
        ZeroChillInboxActivity activity = Robolectric.buildActivity(ZeroChillInboxActivity.class).get();
        invoke(activity, "buildUi");
        return activity;
    }
    private ZeroChillSocialRepository.Conversation conversation(String id, int unread) throws Exception {
        return new ZeroChillSocialRepository.Conversation(new ZeroChillSocialRepository.PublicProfile(
                new JSONObject().put("user_id", id).put("username", id), false),
                new ZeroChillSocialRepository.DirectMessage(new JSONObject().put("id", id)
                        .put("sender_id", id).put("recipient_id", "me").put("body", "message")
                        .put("created_at", "2026-10-03T12:00:00Z")), unread);
    }
    private void replace(RecyclerView.Adapter adapter, ZeroChillSocialRepository.Conversation... items) throws Exception {
        Method method = adapter.getClass().getDeclaredMethod("replace", java.util.List.class);
        method.setAccessible(true);
        method.invoke(adapter, Arrays.asList(items));
    }
    private Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    private void invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }
}
