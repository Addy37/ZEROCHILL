package com.webapp.crazyshit;

import android.content.Context;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Implementation;
import java.util.HashSet;
import java.util.Arrays;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, shadows = ProfileAccountIsolationTest.Session.class)
public class ProfileAccountIsolationTest {
    private Context context;
    private static String account = "";
    @Implements(ZeroChillSessionStore.class)
    public static class Session {
        @Implementation protected static String currentUserId(Context context) { return account; }
    }
    @Before public void reset() {
        context = RuntimeEnvironment.getApplication();
        account = "";
        context.getSharedPreferences("zerochill_update_inbox_v1", 0).edit().clear().commit();
        context.getSharedPreferences("creator_favorites", 0).edit().clear().commit();
        context.getSharedPreferences("zerochill_account_alert_preferences_v1", 0).edit().clear().commit();
    }
    @Test public void legacyFavoritesImportOnceAndAccountsKeepSeparateCollections() {
        CreatorFavoriteStore.mergeNames(context, new HashSet<>(Arrays.asList("local")));
        assertEquals(new HashSet<>(Arrays.asList("local")), CreatorFavoriteStore.activateAccount(context, "a"));
        CreatorFavoriteStore.replaceAccountNames(context, "a", new HashSet<>(Arrays.asList("local", "alice")));
        CreatorFavoriteStore.deactivateAccount(context);
        assertEquals(new HashSet<>(Arrays.asList("local")), CreatorFavoriteStore.names(context));
        assertTrue(CreatorFavoriteStore.activateAccount(context, "b").isEmpty());
        CreatorFavoriteStore.replaceAccountNames(context, "b", new HashSet<>(Arrays.asList("bob")));
        assertEquals(new HashSet<>(Arrays.asList("bob")), CreatorFavoriteStore.names(context));
        CreatorFavoriteStore.activateAccount(context, "a");
        assertEquals(new HashSet<>(Arrays.asList("local", "alice")), CreatorFavoriteStore.names(context));
        CreatorFavoriteStore.removeAccount(context, "a");
        assertEquals(new HashSet<>(Arrays.asList("local")), CreatorFavoriteStore.names(context));
        CreatorFavoriteStore.activateAccount(context, "b");
        assertEquals(new HashSet<>(Arrays.asList("bob")), CreatorFavoriteStore.names(context));
    }
    @Test public void oldInstallsAndMissingRowsPreserveEnabledDefaults() {
        ZeroChillNotificationPreferences.Values defaults = ZeroChillNotificationPreferences.cachedForAccount(context, "old");
        assertTrue(defaults.replies && defaults.likes && defaults.directMessages && defaults.creatorUpdates && defaults.appUpdates);
        assertTrue(ZeroChillNotificationPreferences.Values.from(new JSONObject()).allowsSocial("reply"));
    }
    @Test public void accountPreferenceCacheNeverLeaksBetweenAccounts() throws Exception {
        context.getSharedPreferences("zerochill_account_alert_preferences_v1", 0).edit()
                .putString("a", new ZeroChillNotificationPreferences.Values(false, true, true, false, true).json().toString()).commit();
        assertFalse(ZeroChillNotificationPreferences.cachedForAccount(context, "a").allowsSocial("reply"));
        assertTrue(ZeroChillNotificationPreferences.cachedForAccount(context, "a").allowsSocial("like"));
        assertTrue(ZeroChillNotificationPreferences.cachedForAccount(context, "b").allowsSocial("reply"));
        ZeroChillNotificationPreferences.clearAccount(context, "a");
        assertTrue(ZeroChillNotificationPreferences.cachedForAccount(context, "a").replies);
    }
    @Test public void profileValidationMatchesDatabaseCodePointLimits() {
        assertEquals("", ZeroChillAccountValidation.profile("", ""));
        assertEquals("", ZeroChillAccountValidation.profile("x".repeat(40), "b".repeat(160)));
        assertFalse(ZeroChillAccountValidation.profile("x".repeat(41), "").isEmpty());
        assertFalse(ZeroChillAccountValidation.profile("", "b".repeat(161)).isEmpty());
        assertEquals("", ZeroChillAccountValidation.profile("", "\uD83D\uDE00".repeat(160)));
        assertFalse(ZeroChillAccountValidation.email("bad@@email.com").isEmpty());
        assertFalse(ZeroChillAccountValidation.email("space @email.com").isEmpty());
        assertFalse(ZeroChillAccountValidation.passwordChange("password123", "different").isEmpty());
    }
    @Test public void creatorAndAppChoicesFilterNewActivityWithoutDeletingHistory() throws Exception {
        account = "a";
        CreatorFavoriteStore.mergeNames(context, new HashSet<>(Arrays.asList("Emily Rinaudo")));
        NativeContentItem first = new NativeContentItem(NativeContentItem.KIND_MEDIA, "One",
                "https://fapello.com/video/emily-rinaudo/1/", "", "", "Emily Rinaudo", "", "Fapello");
        UpdateInboxStore.record(context, Arrays.asList(new NotificationCoordinator.SourceAlert("fapello", "Fapello", Arrays.asList(first))));
        UpdateInboxStore.recordAppUpdate(context, "1", "Update one", false);
        assertEquals(2, UpdateInboxStore.all(context).size());
        context.getSharedPreferences("zerochill_account_alert_preferences_v1", 0).edit()
                .putString("a", new ZeroChillNotificationPreferences.Values(true, true, true, false, false).json().toString()).commit();
        NativeContentItem second = new NativeContentItem(NativeContentItem.KIND_MEDIA, "Two",
                "https://fapello.com/video/emily-rinaudo/2/", "", "", "Emily Rinaudo", "", "Fapello");
        UpdateInboxStore.record(context, Arrays.asList(new NotificationCoordinator.SourceAlert("fapello", "Fapello", Arrays.asList(second))));
        UpdateInboxStore.recordAppUpdate(context, "2", "Update two", false);
        assertEquals(2, UpdateInboxStore.all(context).size());
        account = "b";
        UpdateInboxStore.recordAppUpdate(context, "2", "Update two", false);
        assertEquals(3, UpdateInboxStore.all(context).size());
    }

}
