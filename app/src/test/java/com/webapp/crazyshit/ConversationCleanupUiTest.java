package com.webapp.crazyshit;

import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ConversationCleanupUiTest {
    @Test public void longPressEntersSelectionAndBrandedConfirmationCancelKeepsConversation() throws Exception {
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
        assertEquals("1 selected", ((TextView) field(activity, "title")).getText().toString());
        assertNull(field(activity, "removeDialog"));

        ((TextView) field(activity, "deleteAction")).performClick();
        idle();
        Dialog dialog = (Dialog) field(activity, "removeDialog");
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        View cancel = dialog.findViewWithTag("conversation-remove-cancel");
        assertNotNull(cancel);
        cancel.performClick();
        idle();

        assertEquals(0, clears.size());
        assertEquals(1, adapter.getItemCount());
        assertEquals("1 selected", ((TextView) field(activity, "title")).getText().toString());

        activity.onBackPressed();
        assertEquals("Messages", ((TextView) field(activity, "title")).getText().toString());
        assertEquals(1, adapter.getItemCount());
    }

    @Test public void multiSelectConfirmedClearUpdatesUnreadAndRejectsPendingInbox() throws Exception {
        ZeroChillInboxActivity activity = shell();
        RecyclerView recycler = (RecyclerView) field(activity, "recycler");
        RecyclerView.Adapter adapter = recycler.getAdapter();
        ZeroChillSocialRepository.Conversation first = conversation("other", 3);
        ZeroChillSocialRepository.Conversation second = conversation("fourth", 4);
        ZeroChillSocialRepository.Conversation kept = conversation("third", 2);
        replace(adapter, first, second, kept);

        ArrayList<ZeroChillSocialRepository.Callback<ArrayList<ZeroChillSocialRepository.Conversation>>> loads =
                new ArrayList<>();
        set(activity, "inboxLoader", (ZeroChillInboxActivity.InboxLoader)
                (context, callback) -> loads.add(callback));
        ArrayList<String> partners = new ArrayList<>();
        ArrayList<ZeroChillSocialRepository.Callback<Boolean>> clears = new ArrayList<>();
        set(activity, "conversationClearer", (ZeroChillInboxActivity.ConversationClearer)
                (context, partner, callback) -> {
                    partners.add(partner);
                    clears.add(callback);
                });

        invoke(activity, "loadInbox");
        RecyclerView.ViewHolder firstHolder = adapter.createViewHolder(recycler, 0);
        RecyclerView.ViewHolder secondHolder = adapter.createViewHolder(recycler, 0);
        adapter.bindViewHolder(firstHolder, 0);
        adapter.bindViewHolder(secondHolder, 1);
        firstHolder.itemView.performLongClick();
        secondHolder.itemView.performClick();
        assertEquals("2 selected", ((TextView) field(activity, "title")).getText().toString());

        ((TextView) field(activity, "deleteAction")).performClick();
        confirmLatest(activity);
        idle();
        assertEquals(Arrays.asList("other"), partners);

        clears.get(0).complete(true, null);
        idle();
        assertEquals(Arrays.asList("other", "fourth"), partners);
        clears.get(1).complete(true, null);
        idle();

        assertEquals(1, adapter.getItemCount());
        assertEquals("2 unread messages", ((TextView) field(activity, "status")).getText().toString());
        assertEquals("Messages", ((TextView) field(activity, "title")).getText().toString());

        loads.get(0).complete(new ArrayList<>(Arrays.asList(first, second, kept)), null);
        idle();
        assertEquals("Old inbox callback cannot restore removed rows", 1, adapter.getItemCount());

        loads.get(1).complete(new ArrayList<>(Arrays.asList(kept)), null);
        idle();
        assertEquals(1, adapter.getItemCount());

        invoke(activity, "loadInbox");
        loads.get(2).complete(new ArrayList<>(Arrays.asList(conversation("other", 1), kept)), null);
        idle();
        assertEquals("Later server messages can restore conversation", 2, adapter.getItemCount());
        assertEquals("3 unread messages", ((TextView) field(activity, "status")).getText().toString());
    }

    @Test public void selectAllAndPartialFailureKeepsOnlyFailedConversationSelected() throws Exception {
        ZeroChillInboxActivity activity = shell();
        RecyclerView recycler = (RecyclerView) field(activity, "recycler");
        RecyclerView.Adapter adapter = recycler.getAdapter();
        replace(adapter, conversation("one", 1), conversation("two", 2), conversation("three", 3));
        set(activity, "inboxLoader", (ZeroChillInboxActivity.InboxLoader) (context, callback) -> {});

        ArrayList<ZeroChillSocialRepository.Callback<Boolean>> clears = new ArrayList<>();
        set(activity, "conversationClearer", (ZeroChillInboxActivity.ConversationClearer)
                (context, partner, callback) -> clears.add(callback));

        RecyclerView.ViewHolder holder = adapter.createViewHolder(recycler, 0);
        adapter.bindViewHolder(holder, 0);
        holder.itemView.performLongClick();
        ((TextView) field(activity, "selectAllAction")).performClick();
        assertEquals("3 selected", ((TextView) field(activity, "title")).getText().toString());

        ((TextView) field(activity, "deleteAction")).performClick();
        confirmLatest(activity);
        idle();

        clears.get(0).complete(true, null);
        idle();
        clears.get(1).complete(false, new Exception("offline"));
        idle();
        clears.get(2).complete(true, null);
        idle();

        assertEquals(1, adapter.getItemCount());
        assertEquals("1 selected", ((TextView) field(activity, "title")).getText().toString());
        assertEquals(2, ((ZeroChillSocialRepository.Conversation) adapterItem(adapter, 0)).unreadCount);
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

    private void confirmLatest(ZeroChillInboxActivity activity) throws Exception {
        Dialog dialog = (Dialog) field(activity, "removeDialog");
        assertNotNull(dialog);
        View confirm = dialog.findViewWithTag("conversation-remove-confirm");
        assertNotNull(confirm);
        confirm.performClick();
    }

    private Object adapterItem(RecyclerView.Adapter adapter, int index) throws Exception {
        Field items = adapter.getClass().getDeclaredField("items");
        items.setAccessible(true);
        return ((ArrayList<?>) items.get(adapter)).get(index);
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
