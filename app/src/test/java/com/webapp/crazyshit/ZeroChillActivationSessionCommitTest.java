package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import static org.junit.Assert.*;

/** A late activation response must not replace a newer login or undo signout. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35,
        shadows = ZeroChillActivationSessionCommitTest.Sessions.class)
public class ZeroChillActivationSessionCommitTest {
    @Implements(value = ZeroChillSessionStore.class, isInAndroidSdk = false)
    public static class Sessions {
        static String stored;
        static int saves;
        @Implementation protected static String read(Context context) { return stored; }
        @Implementation protected static String currentUserId(Context context) { return ""; }
        @Implementation protected static void save(Context context, String value) { stored = value; saves++; }
    }
    @Before public void reset() { Sessions.stored = ""; Sessions.saves = 0; }

    private ZeroChillAccountRepository.ActivationResult result(String expected) throws Exception {
        String current = Sessions.stored;
        Sessions.stored = expected;
        ZeroChillSessionStore.Snapshot snapshot = ZeroChillSessionStore.snapshot(RuntimeEnvironment.getApplication());
        Sessions.stored = current;
        return new ZeroChillAccountRepository.ActivationResult(
                new ZeroChillAccountRepository.AccountState(true, false,
                        "fake-user", "fake@example.com", "old_username", "Old profile", "", "2020-01-01"),
                new JSONObject().put("access_token", "test-access").put("refresh_token", "test-refresh")
                        .put("expires_in", 3600), snapshot);
    }

    @Test public void newerLoginIsNotReplacedByLateActivation() throws Exception {
        ZeroChillAccountRepository.ActivationResult response = result("");
        Sessions.stored = "a different login completed while activation was in flight";
        try {
            ZeroChillAccountRepository.acceptActivation(RuntimeEnvironment.getApplication(), response);
            fail("Expected changed-session rejection");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Account changed"));
        }
        assertEquals(0, Sessions.saves);
        assertEquals("a different login completed while activation was in flight", Sessions.stored);
    }

    @Test public void signoutWhileActivationIsInFlightIsPreserved() throws Exception {
        ZeroChillAccountRepository.ActivationResult response = result("previous signed-in session");
        assertEquals("", Sessions.stored);
        try {
            ZeroChillAccountRepository.acceptActivation(RuntimeEnvironment.getApplication(), response);
            fail("Expected signout rejection");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Account changed"));
        }
        assertEquals(0, Sessions.saves);
        assertEquals("", Sessions.stored);
    }

    @Test public void clearWhileAlreadySignedOutInvalidatesInFlightActivation() throws Exception {
        ZeroChillAccountRepository.ActivationResult response = result("");
        ZeroChillSessionStore.clear(RuntimeEnvironment.getApplication());
        assertEquals("", Sessions.stored);
        try {
            ZeroChillAccountRepository.acceptActivation(RuntimeEnvironment.getApplication(), response);
            fail("Expected mutation-generation rejection even though the stored value is unchanged");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Account changed"));
        }
        assertEquals(0, Sessions.saves);
    }

    @Test public void unchangedSessionAllowsCompletedActivation() throws Exception {
        ZeroChillAccountRepository.ActivationResult response = result("");
        assertSame(response.account,
                ZeroChillAccountRepository.acceptActivation(RuntimeEnvironment.getApplication(), response));
        assertEquals(1, Sessions.saves);
        assertEquals("test-access", new JSONObject(Sessions.stored).getString("access_token"));
    }
}
