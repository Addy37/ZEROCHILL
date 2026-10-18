package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Looper;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import java.util.ArrayList;
import java.util.Arrays;
import java.time.Duration;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class SocialActivityCoordinatorTest {
    private Activity host;
    private String account;
    private final ArrayList<ZeroChillSocialRepository.Callback<ArrayList<ZeroChillSocialRepository.SocialActivity>>> requests = new ArrayList<>();
    private final ArrayList<UpdateInboxStore.Entry> alerts = new ArrayList<>();
    private SocialActivityCoordinator coordinator;

    @Before public void setup() {
        host = Robolectric.buildActivity(Activity.class).setup().get();
        host.getSharedPreferences("zerochill_update_inbox_v1", Context.MODE_PRIVATE).edit().clear().commit();
        host.getSharedPreferences("zerochill_account_alert_preferences_v1", 0).edit().clear().commit();
        account = "me";
        coordinator = new SocialActivityCoordinator(host, (context, callback) -> requests.add(callback), context -> account,
                new SocialActivityCoordinator.Presenter() {
                    public void show(Activity activity, UpdateInboxStore.Entry entry) { alerts.add(entry); }
                    public void hide() { }
                });
        coordinator.resume(host);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private ZeroChillSocialRepository.SocialActivity event(String id) throws Exception {
        ZeroChillSocialRepository.PublicProfile actor = new ZeroChillSocialRepository.PublicProfile(new JSONObject()
                .put("user_id", "other").put("username", "addy37").put("display_name", "Addy"), false);
        return new ZeroChillSocialRepository.SocialActivity(id, "reply", "other", actor, id,
                "https://crazyshit.com/video/clip", "Video", "Original", "Reply", java.time.Instant.now().toString());
    }

    private void complete(int index, ZeroChillSocialRepository.SocialActivity... events) {
        requests.get(index).complete(new ArrayList<>(Arrays.asList(events)), null);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private void tick() { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(12)); }

    @Test public void firstSyncBackfillsQuietlyThenNewEventsAppearOnce() throws Exception {
        ZeroChillSocialRepository.SocialActivity old = event("old");
        complete(0, old);
        assertEquals(1, UpdateInboxStore.allForAccount(host, "me").size());
        assertTrue(alerts.isEmpty());
        coordinator.refresh();
        assertEquals(1, requests.size());
        tick();
        ZeroChillSocialRepository.SocialActivity fresh = event("fresh");
        complete(1, fresh, old);
        assertEquals(1, alerts.size());
        assertEquals("fresh", alerts.get(0).commentId);
        tick();
        complete(2, fresh, old);
        assertEquals(1, alerts.size());
        coordinator.pause(host);
    }

    @Test public void pausedCallbacksFillHistoryButNeverShowOrKeepPolling() throws Exception {
        complete(0, event("old")); tick();
        coordinator.pause(host);
        complete(1, event("new"));
        assertTrue(alerts.isEmpty());
        assertEquals(2, UpdateInboxStore.allForAccount(host, "me").size());
        tick(); assertEquals(2, requests.size());
    }

    @Test public void accountChangeDiscardsOldRequestAndBaselinesNewAccount() throws Exception {
        account = "other-account";
        coordinator.refresh();
        assertEquals(2, requests.size());
        complete(0, event("wrong-account"));
        assertTrue(UpdateInboxStore.allForAccount(host, "me").isEmpty());
        complete(1, event("new-account-history"));
        assertEquals(1, UpdateInboxStore.allForAccount(host, "other-account").size());
        assertTrue(alerts.isEmpty());
        coordinator.pause(host);
    }
    @Test public void olderBackfillNeverBecomesAnAlertAfterBoundedHistoryEviction() throws Exception {
        ZeroChillSocialRepository.SocialActivity old = event("old");
        ZeroChillSocialRepository.SocialActivity recent = event("recent");
        old = new ZeroChillSocialRepository.SocialActivity(old.eventId, old.type, old.actorUserId, old.actor,
                old.commentId, old.pageUrl, old.videoTitle, old.originalBody, old.replyBody,
                java.time.Instant.now().minusSeconds(60).toString());
        complete(0, recent, old);
        host.getSharedPreferences("zerochill_update_inbox_v1", Context.MODE_PRIVATE).edit().clear().commit();
        java.lang.reflect.Field field = SocialActivityCoordinator.class.getDeclaredField("seen"); field.setAccessible(true);
        ((java.util.Set<?>) field.get(coordinator)).clear();
        tick(); complete(1, old);
        assertTrue(alerts.isEmpty());
        assertEquals(1, UpdateInboxStore.allForAccount(host,"me").size());
        coordinator.pause(host);
    }

    @Test public void disablingRepliesPreservesHistoryAndSuppressesNewEntriesAndBanners() throws Exception {
        ZeroChillSocialRepository.SocialActivity old = event("old");
        complete(0, old);
        host.getSharedPreferences("zerochill_account_alert_preferences_v1", 0).edit()
                .putString("me", new ZeroChillNotificationPreferences.Values(false, true, true, true, true).json().toString())
                .commit();
        tick();
        complete(1, event("disabled"), old);
        assertTrue(alerts.isEmpty());
        assertEquals(1, UpdateInboxStore.allForAccount(host, "me").size());
        assertEquals("old", UpdateInboxStore.allForAccount(host, "me").get(0).commentId);
        host.getSharedPreferences("zerochill_account_alert_preferences_v1", 0).edit().remove("me").commit();
        tick();
        complete(2, event("enabled"), old);
        assertEquals(1, alerts.size());
        assertEquals("enabled", alerts.get(0).commentId);
        assertEquals(2, UpdateInboxStore.allForAccount(host, "me").size());
        coordinator.pause(host);
    }

}

