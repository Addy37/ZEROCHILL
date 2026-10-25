package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, shadows = ZeroChillSocialPushRoutingTest.Sessions.class)
public class ZeroChillSocialPushRoutingTest {
    private static final String ACCOUNT = "550e8400-e29b-41d4-a716-446655440000";
    private static final String OTHER = "6ba7b810-9dad-41d1-80b4-00c04fd430c8";
    private static String current = ACCOUNT;
    private Application app;

    @Implements(value = ZeroChillSessionStore.class, isInAndroidSdk = false)
    public static class Sessions {
        @Implementation protected static String currentUserId(Context context) { return current; }
    }

    @Before public void reset() {
        current = ACCOUNT;
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("zerochill_update_inbox_v1", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void socialPushIsStoredForCurrentAccountAndRoutesByEntryId() {
        UpdateInboxStore.Entry entry = UpdateInboxStore.recordSocialPush(
                app,
                ACCOUNT,
                "reply:7d444840-9dc0-4c2d-bccf-1de3b50ac4d5",
                ZeroChillSocialRepository.SocialActivity.TYPE_REPLY,
                OTHER,
                "Other User",
                "https://www.crazyshit.com/video/example",
                "7d444840-9dc0-4c2d-bccf-1de3b50ac4d5"
        );
        assertNotNull(entry);
        assertEquals("Other User replied to your comment", entry.title);
        assertNotNull(UpdateInboxStore.findVisibleById(app, entry.id, ACCOUNT));

        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Intent intent = ZeroChillPushNotificationPresenter.socialIntent(activity, entry.id);
        assertEquals(UpdateInboxActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals(entry.id, intent.getStringExtra(UpdateInboxActivity.EXTRA_SOCIAL_PUSH_ENTRY_ID));
    }

    @Test public void socialPushCannotLeakAcrossAccounts() {
        assertNull(UpdateInboxStore.recordSocialPush(
                app,
                OTHER,
                "like:7d444840-9dc0-4c2d-bccf-1de3b50ac4d5:" + OTHER,
                ZeroChillSocialRepository.SocialActivity.TYPE_LIKE,
                OTHER,
                "Other User",
                "https://www.crazyshit.com/video/example",
                "7d444840-9dc0-4c2d-bccf-1de3b50ac4d5"
        ));
    }
}
