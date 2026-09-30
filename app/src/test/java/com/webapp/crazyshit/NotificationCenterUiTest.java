package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.Shadows;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class NotificationCenterUiTest {
    @Before public void clearHistory() {
        org.robolectric.RuntimeEnvironment.getApplication()
                .getSharedPreferences("zerochill_update_inbox_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test public void emptyHistoryAndZeroUnreadRemainCleanAcrossRecreation() throws Exception {
        ActivityController<UpdateInboxActivity> controller = Robolectric.buildActivity(UpdateInboxActivity.class).setup();
        UpdateInboxActivity activity = controller.get();
        assertEquals("You're caught up", text(activity, "count").getText().toString());
        assertEquals(View.INVISIBLE, text(activity, "markAll").getVisibility());
        assertTrue(text(activity, "empty").getText().toString().startsWith("No notifications yet"));
        controller.recreate();
        assertEquals(View.VISIBLE, text(controller.get(), "empty").getVisibility());
        controller.pause().stop().destroy();
    }

    @Test public void newestFirstReadAllAndAppActionReuseExistingHistory() throws Exception {
        ActivityController<UpdateInboxActivity> controller = Robolectric.buildActivity(UpdateInboxActivity.class).setup();
        UpdateInboxActivity activity = controller.get();
        UpdateInboxStore.recordAppUpdate(activity, "4.2.2", "ZeroChill 4.2.2", false);
        UpdateInboxStore.recordAppUpdate(activity, "4.2.3", "ZeroChill 4.2.3", false);
        render(activity);
        assertEquals(2, UpdateInboxStore.unreadCount(activity));
        assertEquals("2 unread notifications", text(activity, "count").getText().toString());
        assertEquals("4.2.3", UpdateInboxStore.all(activity).get(0).appVersion);
        text(activity, "markAll").performClick();
        assertEquals(0, UpdateInboxStore.unreadCount(activity));
        assertEquals(View.INVISIBLE, text(activity, "markAll").getVisibility());
        Method open = UpdateInboxActivity.class.getDeclaredMethod("open", UpdateInboxStore.Entry.class);
        open.setAccessible(true);
        open.invoke(activity, UpdateInboxStore.all(activity).get(0));
        Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(SettingsActivity.class.getName(), intent.getComponent().getClassName());
        assertTrue(intent.getBooleanExtra(SettingsActivity.EXTRA_CHECK_FOR_UPDATES, false));
        controller.pause().stop().destroy();
    }

    @Test public void historyRowsClipAvatarsAndConstrainLongCopy() throws Exception {
        ActivityController<UpdateInboxActivity> controller = Robolectric.buildActivity(UpdateInboxActivity.class).setup();
        UpdateInboxActivity activity = controller.get();
        UpdateInboxStore.recordAppUpdate(activity, "4.2.2", new String(new char[500]).replace('\0', 'W'), false);
        render(activity);
        RecyclerView recycler = (RecyclerView) value(activity, "recycler");
        RecyclerView.Adapter adapter = recycler.getAdapter();
        RecyclerView.ViewHolder holder = adapter.createViewHolder(recycler, 0);
        adapter.bindViewHolder(holder, 0);
        android.widget.LinearLayout row = (android.widget.LinearLayout) holder.itemView;
        assertTrue(row.getChildAt(0).getClipToOutline());
        assertEquals(row.getChildAt(0).getLayoutParams().width, row.getChildAt(0).getLayoutParams().height);
        android.widget.LinearLayout labels = (android.widget.LinearLayout) row.getChildAt(1);
        assertEquals(1, ((TextView) labels.getChildAt(0)).getMaxLines());
        assertEquals(2, ((TextView) labels.getChildAt(1)).getMaxLines());
        assertNotNull(((android.widget.ImageView) row.getChildAt(0)).getDrawable());
        assertEquals(View.VISIBLE, row.getChildAt(2).getVisibility());
        row.performClick();
        assertTrue(UpdateInboxStore.all(activity).get(0).read);
        adapter.bindViewHolder(holder, 0);
        assertEquals(View.INVISIBLE, row.getChildAt(2).getVisibility());
        controller.pause().stop().destroy();
    }

    @Test public void foregroundHistoryRefreshesWithoutRestartAndStopsObservingWhenPaused() throws Exception {
        ActivityController<UpdateInboxActivity> controller = Robolectric.buildActivity(UpdateInboxActivity.class).setup();
        UpdateInboxActivity activity = controller.get();
        UpdateInboxStore.recordAppUpdate(activity, "4.2.2", "ZeroChill 4.2.2", false);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals("1 unread notification", text(activity, "count").getText().toString());
        UpdateInboxStore.markAllRead(activity);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals("You're caught up", text(activity, "count").getText().toString());
        controller.pause();
        UpdateInboxStore.recordAppUpdate(activity, "4.2.3", "ZeroChill 4.2.3", false);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals("You're caught up", text(activity, "count").getText().toString());
        controller.resume();
        assertEquals("1 unread notification", text(activity, "count").getText().toString());
        controller.pause().stop().destroy();
    }

    private void render(UpdateInboxActivity activity) throws Exception {
        Method method = UpdateInboxActivity.class.getDeclaredMethod("render");
        method.setAccessible(true);
        method.invoke(activity);
    }
    private TextView text(Object target, String name) throws Exception { return (TextView) value(target, name); }
    private Object value(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
