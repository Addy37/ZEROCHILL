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
import java.util.Collections;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class NotificationCenterUiTest {
    @Before public void clearHistory() {
        org.robolectric.RuntimeEnvironment.getApplication()
                .getSharedPreferences("zerochill_update_inbox_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
        org.robolectric.RuntimeEnvironment.getApplication()
                .getSharedPreferences("zerochill_social_content_context_v1", Context.MODE_PRIVATE)
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
        android.widget.LinearLayout shell = (android.widget.LinearLayout) holder.itemView;
        android.widget.LinearLayout row = (android.widget.LinearLayout) shell.getChildAt(1);
        assertTrue(row.getChildAt(0).getClipToOutline());
        assertEquals(row.getChildAt(0).getLayoutParams().width, row.getChildAt(0).getLayoutParams().height);
        android.widget.LinearLayout labels = (android.widget.LinearLayout) row.getChildAt(1);
        assertEquals(2, ((TextView) labels.getChildAt(0)).getMaxLines());
        assertEquals(3, ((TextView) labels.getChildAt(1)).getMaxLines());
        assertNotNull(((android.widget.ImageView) row.getChildAt(0)).getDrawable());
        android.widget.FrameLayout trailing = (android.widget.FrameLayout) row.getChildAt(2);
        assertEquals(View.VISIBLE, trailing.getChildAt(1).getVisibility());
        row.performClick();
        assertTrue(UpdateInboxStore.all(activity).get(0).read);
        adapter.bindViewHolder(holder, 0);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200));
        assertEquals(View.INVISIBLE, trailing.getChildAt(1).getVisibility());
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

    @Test public void cachedSocialNamesAreCleanAndLikesUseTheWholeRow() throws Exception {
        ActivityController<UpdateInboxActivity> controller = Robolectric.buildActivity(UpdateInboxActivity.class).setup();
        UpdateInboxActivity activity = controller.get();
        UpdateInboxStore.Entry entry = new UpdateInboxStore.Entry();
        entry.id = "social:like";
        entry.category = UpdateInboxStore.CATEGORY_SOCIAL;
        entry.socialType = ZeroChillSocialRepository.SocialActivity.TYPE_LIKE;
        entry.actorName = "@Addy37";
        entry.title = "@Addy37 liked your comment";
        entry.subtitle = "Original comment text";
        RecyclerView recycler = (RecyclerView) value(activity, "recycler");
        Object adapter = value(activity, "adapter");
        Method replace = adapter.getClass().getDeclaredMethod("replace", java.util.List.class);
        replace.setAccessible(true);
        replace.invoke(adapter, Collections.singletonList(entry));
        RecyclerView.Adapter rows = (RecyclerView.Adapter) adapter;
        RecyclerView.ViewHolder holder = rows.createViewHolder(recycler, 0);
        rows.bindViewHolder(holder, 0);
        android.widget.LinearLayout row = (android.widget.LinearLayout) ((android.widget.LinearLayout) holder.itemView).getChildAt(1);
        android.widget.LinearLayout labels = (android.widget.LinearLayout) row.getChildAt(1);
        assertTrue(((TextView) labels.getChildAt(0)).getText().toString().startsWith("Addy37 liked your comment"));
        assertFalse(((TextView) labels.getChildAt(0)).getText().toString().contains("@"));
        assertFalse(row.getContentDescription().toString().contains("@"));
        assertEquals(View.GONE, labels.getChildAt(2).getVisibility());
        assertTrue(row.isClickable());
        controller.pause().stop().destroy();
    }

    @Test public void replyActionsHaveGenerousTouchTargetsAndFilterStateSurvivesRefresh() throws Exception {
        ActivityController<UpdateInboxActivity> controller = Robolectric.buildActivity(UpdateInboxActivity.class).setup();
        UpdateInboxActivity activity = controller.get();
        UpdateInboxStore.Entry entry = new UpdateInboxStore.Entry();
        entry.id = "social:reply";
        entry.category = UpdateInboxStore.CATEGORY_SOCIAL;
        entry.socialType = ZeroChillSocialRepository.SocialActivity.TYPE_REPLY;
        entry.actorName = "Addy37";
        RecyclerView recycler = (RecyclerView) value(activity, "recycler");
        Object adapter = value(activity, "adapter");
        Method replace = adapter.getClass().getDeclaredMethod("replace", java.util.List.class);
        replace.setAccessible(true);
        replace.invoke(adapter, Collections.singletonList(entry));
        RecyclerView.Adapter rows = (RecyclerView.Adapter) adapter;
        RecyclerView.ViewHolder holder = rows.createViewHolder(recycler, 0);
        rows.bindViewHolder(holder, 0);
        android.widget.LinearLayout row = (android.widget.LinearLayout) ((android.widget.LinearLayout) holder.itemView).getChildAt(1);
        android.widget.LinearLayout labels = (android.widget.LinearLayout) row.getChildAt(1);
        android.widget.LinearLayout actions = (android.widget.LinearLayout) labels.getChildAt(2);
        assertEquals(View.VISIBLE, actions.getVisibility());
        assertEquals(44, Math.round(actions.getChildAt(0).getLayoutParams().height
                / activity.getResources().getDisplayMetrics().density));
        assertEquals(44, Math.round(actions.getChildAt(1).getLayoutParams().height
                / activity.getResources().getDisplayMetrics().density));
        TextView inlineLike = (TextView) actions.getChildAt(1);
        assertNotEquals("View", inlineLike.getText().toString());
        assertNotNull(inlineLike.getCompoundDrawables()[0]);
        TextView social = text(activity, "socialFilter");
        social.performClick();
        render(activity);
        assertEquals(Boolean.TRUE, social.getTag());
        assertEquals(Boolean.FALSE, text(activity, "allFilter").getTag());
        controller.pause().stop().destroy();
    }

    @Test public void socialRowsReuseLocallyCapturedVideoThumbnailWithoutStoredEntryArtwork() throws Exception {
        ActivityController<UpdateInboxActivity> controller = Robolectric.buildActivity(UpdateInboxActivity.class).setup();
        UpdateInboxActivity activity = controller.get();
        String pageUrl = "https://crazyshit.com/video/thumbnail-test";
        SocialContentContextStore.remember(
                activity,
                new NativeContentItem(
                        NativeContentItem.KIND_MEDIA,
                        "Thumbnail test",
                        pageUrl,
                        "https://cdn.example/thumb.jpg",
                        "",
                        pageUrl,
                        ""
                )
        );
        UpdateInboxStore.Entry entry = new UpdateInboxStore.Entry();
        entry.id = "social:thumb";
        entry.category = UpdateInboxStore.CATEGORY_SOCIAL;
        entry.socialType = ZeroChillSocialRepository.SocialActivity.TYPE_LIKE;
        entry.actorName = "Addy37";
        entry.pageUrl = pageUrl;

        RecyclerView recycler = (RecyclerView) value(activity, "recycler");
        Object adapter = value(activity, "adapter");
        Method replace = adapter.getClass().getDeclaredMethod("replace", java.util.List.class);
        replace.setAccessible(true);
        replace.invoke(adapter, Collections.singletonList(entry));
        RecyclerView.Adapter rows = (RecyclerView.Adapter) adapter;
        RecyclerView.ViewHolder holder = rows.createViewHolder(recycler, 0);
        rows.bindViewHolder(holder, 0);

        android.widget.LinearLayout row =
                (android.widget.LinearLayout) ((android.widget.LinearLayout) holder.itemView).getChildAt(1);
        android.widget.FrameLayout trailing = (android.widget.FrameLayout) row.getChildAt(2);
        View thumbnail = trailing.getChildAt(0);
        assertEquals(View.VISIBLE, thumbnail.getVisibility());
        assertTrue(thumbnail.isClickable());
        assertEquals("Open video and conversation", thumbnail.getContentDescription());
        controller.pause().stop().destroy();
    }

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w320dp-h800dp-xhdpi")
    public void renderDenseActivityFeedAtNarrowPhoneWidth() throws Exception {
        ActivityController<UpdateInboxActivity> controller = Robolectric.buildActivity(UpdateInboxActivity.class).setup();
        UpdateInboxActivity activity = controller.get();
        activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().putBoolean("immersive_motion_enabled", false).commit();
        java.util.ArrayList<UpdateInboxStore.Entry> entries = new java.util.ArrayList<>();
        for (int i=0;i<5;i++) {
            UpdateInboxStore.Entry entry = new UpdateInboxStore.Entry();
            entry.id = "visual-"+i; entry.category = UpdateInboxStore.CATEGORY_SOCIAL;
            entry.socialType = i%2==0 ? "reply" : "like"; entry.actorName = i%2==0 ? "Addy37" : "Alex";
            entry.timestamp = System.currentTimeMillis() - (23L+i*15L)*60_000L; entry.read = i>2;
            entry.subtitle = i%2==0 ? "There should be someone hopefully eventually" : "This is the comment where the conversation started.";
            entries.add(entry);
        }
        Object adapter = value(activity,"adapter");
        Method replace = adapter.getClass().getDeclaredMethod("replace",java.util.List.class); replace.setAccessible(true); replace.invoke(adapter,entries);
        text(activity,"count").setText("3 unread notifications");
        text(activity,"empty").setVisibility(View.GONE);
        ((RecyclerView) value(activity,"recycler")).setItemAnimator(null);
        View root = activity.findViewById(android.R.id.content);
        root.measure(View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.EXACTLY));
        root.layout(0,0,640,1600);
        android.graphics.Bitmap image = android.graphics.Bitmap.createBitmap(640,1600,android.graphics.Bitmap.Config.ARGB_8888);
        root.draw(new android.graphics.Canvas(image));
        java.io.File folder = new java.io.File("build/reports/visual-tests"); assertTrue(folder.exists() || folder.mkdirs());
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(folder,"notifications-social-polish.png"))) {
            assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));
        }
        image.recycle(); controller.pause().stop().destroy();
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
