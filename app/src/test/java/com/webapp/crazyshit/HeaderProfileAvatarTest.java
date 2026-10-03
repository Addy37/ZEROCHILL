package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w320dp-h800dp-xhdpi",
        shadows = HeaderProfileAvatarTest.Sessions.class)
public class HeaderProfileAvatarTest {
    static String user;
    @Implements(value = ZeroChillSessionStore.class, isInAndroidSdk = false)
    public static class Sessions {
        @Implementation protected static String currentUserId(Context context) { return user; }
    }
    private Activity activity;
    private final ArrayList<ZeroChillAccountRepository.Callback<ZeroChillAccountRepository.AccountState>> calls = new ArrayList<>();
    @Before public void setup() {
        user = "a";
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        AccountProfileCache.clear(activity);
    }
    private ZeroChillAccountRepository.AccountState state(String owner, String avatar) {
        return new ZeroChillAccountRepository.AccountState(true, false, owner, "", owner, "Name " + owner, avatar, "");
    }
    private HeaderProfileAvatar avatar() {
        HeaderProfileAvatar view = new HeaderProfileAvatar(activity, context -> user, (context, callback) -> calls.add(callback));
        activity.setContentView(view);
        view.resume();
        return view;
    }
    private void idle() { Shadows.shadowOf(Looper.getMainLooper()).idle(); }

    @Test public void signedInShortcutUsesCachedIdentityAndOpensExistingAccountWithoutPolling() {
        AccountProfileCache.record(activity, state("a", ""));
        HeaderProfileAvatar view = avatar();
        assertEquals(View.VISIBLE, view.getVisibility());
        assertTrue(((ImageView) view.getChildAt(0)).getClipToOutline());
        assertEquals("Your profile: Name a", view.getContentDescription());
        view.performClick();
        assertEquals(ZeroChillAccountActivity.class.getName(), Shadows.shadowOf(activity)
                .getNextStartedActivity().getComponent().getClassName());
        view.pause(); view.resume();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(40, TimeUnit.SECONDS);
        assertEquals(0, calls.size());
        view.close();
    }

    @Test public void signedOutHidesAndUntracksPriorAvatarAndCannotRoute() {
        AccountProfileCache.record(activity, state("a", "a/avatar-old.jpg"));
        HeaderProfileAvatar view = avatar();
        user = "";
        ZeroChillSessionStore.preferences(activity).edit().putString("test-session", "out").apply();
        idle();
        assertEquals(View.GONE, view.getVisibility());
        assertNull(((ImageView) view.getChildAt(0)).getDrawable());
        AccountAvatarImages.changed("a", "a/avatar-new.jpg");
        assertNull(((ImageView) view.getChildAt(0)).getDrawable());
        view.performClick();
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        view.close();
    }

    @Test public void initialHydrationIsBoundedAndLateOtherAccountResultsCannotRender() {
        HeaderProfileAvatar view = avatar();
        view.refresh(); view.pause(); view.resume();
        assertEquals(1, calls.size());
        user = "b";
        view.refresh();
        assertEquals(2, calls.size());
        calls.get(0).complete(state("a", "a/old.jpg"), null);
        idle();
        assertEquals("", ReflectionHelpers.getField(view, "path"));
        assertEquals("Your profile", view.getContentDescription());
        calls.get(1).complete(state("b", "b/current.jpg"), null);
        idle();
        assertEquals("b/current.jpg", ReflectionHelpers.getField(view, "path"));
        AccountProfileCache.record(activity, state("a", "a/stale.jpg"));
        idle();
        assertEquals("b/current.jpg", ReflectionHelpers.getField(view, "path"));
        view.close();
    }

    @Test public void uploadCacheRefreshResumeAndRecreatedViewUseCurrentAccount() {
        AccountProfileCache.record(activity, state("a", "a/old.jpg"));
        HeaderProfileAvatar view = avatar();
        AccountProfileCache.record(activity, state("a", "a/new.jpg"));
        idle();
        assertEquals("a/new.jpg", ReflectionHelpers.getField(view, "path"));
        view.pause();
        user = "b";
        AccountProfileCache.record(activity, state("b", "b/new.jpg"));
        view.resume();
        assertEquals("b/new.jpg", ReflectionHelpers.getField(view, "path"));
        view.close();
        HeaderProfileAvatar recreated = avatar();
        assertEquals("b/new.jpg", ReflectionHelpers.getField(recreated, "path"));
        assertEquals(0, calls.size());
        recreated.close();
    }

    @Test public void nativeHeaderKeepsSearchAvatarMoreOrderAndFitsNarrowWidth() throws Exception {
        // Construct just the header to avoid unrelated feeds or network work.
        NativeMainActivity main = Robolectric.buildActivity(NativeMainActivity.class).get();
        AccountProfileCache.record(main, state("a", ""));
        java.lang.reflect.Method method = NativeMainActivity.class.getDeclaredMethod("buildTopBar");
        method.setAccessible(true);
        LinearLayout bar = (LinearLayout) method.invoke(main);
        HeaderProfileAvatar view = (HeaderProfileAvatar) bar.getChildAt(2);
        view.resume();
        assertEquals("Search", bar.getChildAt(1).getContentDescription());
        assertEquals("More", bar.getChildAt(3).getContentDescription());
        int width = BrowseUi.dp(main, 320), height = BrowseUi.dp(main, 58);
        bar.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        bar.layout(0, 0, width, height);
        assertTrue(bar.getChildAt(3).getRight() <= width);
        assertTrue(view.getWidth() >= BrowseUi.dp(main, 44));
        view.close();
    }
}
