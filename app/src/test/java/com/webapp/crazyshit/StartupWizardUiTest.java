package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w320dp-h800dp-xhdpi",
        shadows = {StartupWizardUiTest.Accounts.class, StartupWizardUiTest.Sessions.class})
public class StartupWizardUiTest {
    private static final String OWNER = "00000000-0000-0000-0000-000000000001";
    private static boolean signedIn;

    @Implements(value = ZeroChillAccountRepository.class, isInAndroidSdk = false)
    public static class Accounts {
        @Implementation protected static boolean isConfigured() { return true; }
        @Implementation protected static boolean hasStoredSession(Context context) { return signedIn; }
        @Implementation protected static void current(
                Context context,
                ZeroChillAccountRepository.Callback<ZeroChillAccountRepository.AccountState> callback
        ) {
            if (!signedIn) {
                callback.complete(new ZeroChillAccountRepository.AccountState(
                        false, false, "", "", "", "", "", "", ""), null);
                return;
            }
            callback.complete(new ZeroChillAccountRepository.AccountState(
                    true, false, OWNER, "addy@example.com", "addy37test", "Addy37", "",
                    "2026-09-30", "Here for creators."), null);
        }
    }

    @Implements(value = ZeroChillSessionStore.class, isInAndroidSdk = false)
    public static class Sessions {
        @Implementation protected static String currentUserId(Context context) {
            return signedIn ? OWNER : "";
        }
    }

    @Before public void reset() {
        signedIn = false;
    }

    @Test public void finalPageOffersOptionalCreateAndSignInEntries() throws Exception {
        StartupWizardActivity activity = create();
        showSettings(activity);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        View root = activity.getWindow().getDecorView();
        assertNotNull(find(root, "YOUR ZEROCHILL ID"));
        assertNotNull(find(root, "Take your identity with you"));
        assertNotNull(find(root, "Optional. You can always do this later."));
        assertNotNull(find(root, "ENTER ZEROCHILL"));

        View create = find(root, "CREATE ACCOUNT");
        assertNotNull(create);
        create.performClick();
        Intent createIntent = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(ZeroChillAccountActivity.class.getName(),
                createIntent.getComponent().getClassName());
        assertTrue(createIntent.getBooleanExtra(
                ZeroChillAccountActivity.EXTRA_START_CREATE, false));

        View signIn = find(root, "SIGN IN");
        assertNotNull(signIn);
        signIn.performClick();
        Intent signInIntent = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(ZeroChillAccountActivity.class.getName(),
                signInIntent.getComponent().getClassName());
        assertFalse(signInIntent.getBooleanExtra(
                ZeroChillAccountActivity.EXTRA_START_CREATE, true));
    }

    @Test public void returningSignedInRefreshesIdentityCard() throws Exception {
        StartupWizardActivity activity = create();
        showSettings(activity);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertNotNull(find(activity.getWindow().getDecorView(), "CREATE ACCOUNT"));

        signedIn = true;
        activity.onResume();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        View root = activity.getWindow().getDecorView();
        assertNotNull(find(root, "ZEROCHILL ID CONNECTED"));
        assertNotNull(find(root, "Signed in as Addy37"));
        assertNotNull(find(root, "MANAGE ACCOUNT"));
        assertNull(find(root, "CREATE ACCOUNT"));
        assertNotNull(find(root, "ENTER ZEROCHILL"));
    }

    private static StartupWizardActivity create() {
        return Robolectric.buildActivity(StartupWizardActivity.class)
                .setup()
                .get();
    }

    private static void showSettings(StartupWizardActivity activity) throws Exception {
        Method showPage = StartupWizardActivity.class.getDeclaredMethod(
                "showPage", int.class, boolean.class);
        showPage.setAccessible(true);
        showPage.invoke(activity, 4, true);
    }

    private static View find(View root, String text) {
        if (root instanceof TextView && text.equals(((TextView) root).getText().toString())) {
            return root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = find(group.getChildAt(i), text);
                if (result != null) return result;
            }
        }
        return null;
    }
}
