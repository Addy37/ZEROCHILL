package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Looper;
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
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Implementation;
import org.robolectric.util.ReflectionHelpers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, shadows = {LikedVideosActivityTest.Session.class, LikedVideosActivityTest.Repository.class})
public class LikedVideosActivityTest {
    private static String owner;
    private static final ArrayList<ZeroChillSocialRepository.Callback<SocialContentContextStore.LikedPage>> calls = new ArrayList<>();
    private static final ArrayList<Integer> offsets = new ArrayList<>();
    @Implements(value = ZeroChillSessionStore.class, isInAndroidSdk = false)
    public static class Session {
        @Implementation protected static String currentUserId(Context context) { return owner; }
    }
    @Implements(value = ZeroChillSocialRepository.class, isInAndroidSdk = false)
    public static class Repository {
        @Implementation protected static void loadLikedVideoPage(Context context, int offset,
                ZeroChillSocialRepository.Callback<SocialContentContextStore.LikedPage> callback) {
            offsets.add(offset); calls.add(callback);
        }
    }
    @Before public void reset() {
        owner = "account-a"; calls.clear(); offsets.clear();
        Context context = org.robolectric.RuntimeEnvironment.getApplication();
        context.getSharedPreferences(SocialContentContextStore.PREFS, 0).edit().clear().commit();
        context.getSharedPreferences("playback_history", 0).edit().clear().commit();
        context.getSharedPreferences("watch_later", 0).edit().clear().commit();
        PlaybackHistoryStore.resetForTests();
    }
    @Test public void openingIsLocalAndExplicitRefreshPagesLazilyAndReturnsExistingVideoRoute() throws Exception {
        ActivityController<LikedVideosActivity> controller = Robolectric.buildActivity(LikedVideosActivity.class).setup();
        LikedVideosActivity activity = controller.get();
        try {
            settle(activity);
            assertTrue(calls.isEmpty());
            LibraryProfileTest.description(activity.getWindow().getDecorView(), "Refresh liked videos").performClick();
            assertEquals(Integer.valueOf(0), offsets.get(0));
            NativeContentItem item = video("First", "first");
            calls.get(0).complete(new SocialContentContextStore.LikedPage(Collections.singletonList(item), 1, 100, true), null);
            shadowOf(Looper.getMainLooper()).idle();
            RecyclerView list = ReflectionHelpers.getField(activity, "recycler");
            assertEquals(1, list.getAdapter().getItemCount());
            assertTrue(((TextView) ReflectionHelpers.getField(activity, "status")).getText().toString().contains("older likes"));
            LibraryProfileTest.description(activity.getWindow().getDecorView(), "Load more liked videos").performClick();
            assertEquals(Integer.valueOf(100), offsets.get(1));
            calls.get(1).complete(new SocialContentContextStore.LikedPage(Collections.singletonList(video("Second", "second")), 0, 101, false), null);
            shadowOf(Looper.getMainLooper()).idle();
            list.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY));
            list.layout(0, 0, 600, 900);
            assertEquals(2, list.getAdapter().getItemCount());
            list.findViewHolderForAdapterPosition(0).itemView.performClick();
            assertEquals(Activity.RESULT_OK, shadowOf(activity).getResultCode());
            assertEquals(item.url, shadowOf(activity).getResultIntent().getStringExtra(FavoritesActivity.EXTRA_SELECTED_URL));
            assertEquals(FavoritesActivity.START_WATCH_LATER, shadowOf(activity).getResultIntent().getIntExtra(FavoritesActivity.EXTRA_SELECTED_TAB, -1));
        } finally { controller.pause().stop().destroy(); }
    }
    @Test public void accountChangeDropsCachedRowsAndDiscardsOldResponse() throws Exception {
        Context context = org.robolectric.RuntimeEnvironment.getApplication();
        SocialContentContextStore.confirmLike(context, owner, "https://fixture.invalid/a", true);
        ActivityController<LikedVideosActivity> controller = Robolectric.buildActivity(LikedVideosActivity.class).setup();
        LikedVideosActivity activity = controller.get();
        try {
            settle(activity);
            RecyclerView list = ReflectionHelpers.getField(activity, "recycler");
            assertEquals(1, list.getAdapter().getItemCount());
            ((View) ReflectionHelpers.getField(activity, "refresh")).performClick();
            owner = "account-b";
            ZeroChillSessionStore.preferences(activity).edit().putString("test-owner", owner).apply();
            shadowOf(Looper.getMainLooper()).idle(); settle(activity);
            calls.get(0).complete(new SocialContentContextStore.LikedPage(Collections.singletonList(video("Old", "old")), 0, 1, false), null);
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(0, list.getAdapter().getItemCount());
            assertTrue(((View) ReflectionHelpers.getField(activity, "refresh")).isEnabled());
        } finally { controller.pause().stop().destroy(); }
    }
    @Test public void failedRefreshKeepsLocalLikesAndRecreationKeepsCollection() throws Exception {
        Context context = org.robolectric.RuntimeEnvironment.getApplication();
        SocialContentContextStore.confirmLike(context, owner, "https://fixture.invalid/a", true);
        ActivityController<LikedVideosActivity> controller = Robolectric.buildActivity(LikedVideosActivity.class).setup();
        try {
            LikedVideosActivity activity = controller.get(); settle(activity);
            ((View) ReflectionHelpers.getField(activity, "refresh")).performClick();
            calls.get(0).complete(null, new Exception("offline"));
            shadowOf(Looper.getMainLooper()).idle();
            RecyclerView list = ReflectionHelpers.getField(activity, "recycler");
            assertEquals(1, list.getAdapter().getItemCount());
            controller.recreate();
            activity = controller.get(); settle(activity);
            list = ReflectionHelpers.getField(activity, "recycler");
            assertEquals(1, list.getAdapter().getItemCount());
            assertEquals("Recreation must not start network work", 1, calls.size());
        } finally { controller.pause().stop().destroy(); }
    }
    private void settle(LikedVideosActivity activity) throws Exception {
        ((ExecutorService) ReflectionHelpers.getField(activity, "disk")).submit(() -> {}).get(5, TimeUnit.SECONDS);
        shadowOf(Looper.getMainLooper()).idle();
    }
    private NativeContentItem video(String title, String slug) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, title, "https://fixture.invalid/" + slug, "", "", "", "");
    }
}
