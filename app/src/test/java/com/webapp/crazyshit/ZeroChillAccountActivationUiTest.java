package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35,
        shadows = {ZeroChillAccountActivationUiTest.Accounts.class, ZeroChillAccountActivationUiTest.Sessions.class})
public class ZeroChillAccountActivationUiTest {
    @Implements(value = ZeroChillAccountRepository.class, isInAndroidSdk = false)
    public static class Accounts {
        static int requests, verifications, completions, accepts;
        static boolean deferCompletion;
        static ZeroChillAccountRepository.Callback<ZeroChillAccountRepository.ActivationResult> pending;
        static boolean enabled = true;
        static boolean completeSuccessfully;
        @Implementation protected static boolean isConfigured() { return true; }
        @Implementation protected static boolean activationEnabled() { return enabled; }
        @Implementation protected static void current(Context context,
                ZeroChillAccountRepository.Callback<ZeroChillAccountRepository.AccountState> callback) {
            callback.complete(ZeroChillAccountRepository.AccountState.signedOut(), null);
        }
        @Implementation protected static void requestActivation(String email,
                ZeroChillAccountRepository.Callback<Boolean> callback) {
            requests++;
            callback.complete(true, null);
        }
        @Implementation protected static void verifyActivationCode(String email, String code,
                ZeroChillAccountRepository.Callback<ZeroChillAccountRepository.ActivationSession> callback) {
            verifications++;
            if (!"123456".equals(code)) callback.complete(null, new IllegalStateException("Invalid or expired code."));
            else callback.complete(new ZeroChillAccountRepository.ActivationSession(new JSONObject(), null), null);
        }
        @Implementation protected static void finishActivation(Context context,
                ZeroChillAccountRepository.ActivationSession activation, String password, String confirmation,
                ZeroChillAccountRepository.Callback<ZeroChillAccountRepository.ActivationResult> callback) {
            completions++;
            if (deferCompletion) { pending = callback; return; }
            if (completeSuccessfully) {
                callback.complete(result(), null);
            } else {
                // A server failure must leave the verified password form available for retry.
                callback.complete(null, new IllegalStateException("Temporary server failure."));
            }
        }
        @Implementation protected static ZeroChillAccountRepository.AccountState acceptActivation(Context context,
                ZeroChillAccountRepository.ActivationResult result) {
            accepts++;
            return result.account;
        }
    }
    private static ZeroChillAccountRepository.ActivationResult result() {
        return new ZeroChillAccountRepository.ActivationResult(
                new ZeroChillAccountRepository.AccountState(true, false,
                        "00000000-0000-0000-0000-000000000001", "fake@example.com", "existing_username",
                        "Existing profile", "", "2020-01-01", "Existing bio"), new JSONObject(),
                ZeroChillSessionStore.snapshot(org.robolectric.RuntimeEnvironment.getApplication()));
    }

    @Implements(value = ZeroChillSessionStore.class, isInAndroidSdk = false)
    public static class Sessions {
        @Implementation protected static String currentUserId(Context context) { return ""; }
    }
    @Before public void reset() {
        Accounts.requests = Accounts.verifications = Accounts.completions = Accounts.accepts = 0;
        Accounts.deferCompletion = false;
        Accounts.pending = null;
        Accounts.enabled = true;
        Accounts.completeSuccessfully = false;
    }

    @Test public void passwordFormRequiresVerificationAndMatchingPassword() {
        ZeroChillAccountActivity activity = create(null);
        View root = activity.getWindow().getDecorView();
        assertNotNull(find(root, "SIGN IN"));
        assertNotNull(find(root, "CREATE ACCOUNT"));
        assertNull(field(root, "New password"));
        find(root, "Activate Existing Account").performClick();
        field(root, "Registered email").setText("fake@example.com");
        find(root, "Send verification email").performClick();
        assertEquals(1, Accounts.requests);
        assertNotNull(find(root, "Check your email"));
        field(root, "Verification code").setText("000000");
        find(root, "Verify code").performClick();
        assertNull(field(root, "New password"));
        field(root, "Verification code").setText("123456");
        find(root, "Verify code").performClick();
        assertEquals(2, Accounts.verifications);
        assertNotNull(field(root, "New password"));
        assertFalse(field(root, "New password").isSaveEnabled());
        assertFalse(field(root, "Confirm new password").isSaveEnabled());
        field(root, "New password").setText("new-password-123");
        field(root, "Confirm new password").setText("different-password");
        find(root, "Activate account").performClick();
        assertEquals(0, Accounts.completions);
        field(root, "Confirm new password").setText("new-password-123");
        find(root, "Activate account").performClick();
        assertEquals(1, Accounts.completions);
        assertNotNull(find(root, "Activate account"));
        assertNull(find(root, "YOUR IDENTITY"));
        assertEquals("", ZeroChillSessionStore.currentUserId(activity));
        Accounts.completeSuccessfully = true;
        find(root, "Activate account").performClick();
        assertEquals(2, Accounts.completions);
        assertNotNull(find(root, "YOUR IDENTITY"));
        assertNotNull(find(root, "Existing profile"));
        assertNull(org.robolectric.util.ReflectionHelpers.getField(activity, "activationSession"));
    }

    @Test public void resendWaitAndRecreationDoNotRestoreVerifiedSessionOrPasswords() {
        ZeroChillAccountActivity activity = create(null);
        View root = activity.getWindow().getDecorView();
        find(root, "Activate Existing Account").performClick();
        field(root, "Registered email").setText("fake@example.com");
        find(root, "Send verification email").performClick();
        find(root, "Request another email or change address").performClick();
        find(root, "Send verification email").performClick();
        assertEquals(1, Accounts.requests);
        Bundle state = new Bundle();
        activity.onSaveInstanceState(state);
        activity.finish();
        ZeroChillAccountActivity recreated = create(state);
        View next = recreated.getWindow().getDecorView();
        assertEquals("fake@example.com", field(next, "Registered email").getText().toString());
        assertNull(field(next, "New password"));
        assertNull(org.robolectric.util.ReflectionHelpers.getField(recreated, "activationSession"));
        find(next, "Back to sign in").performClick();
        assertNotNull(find(next, "SIGN IN"));
    }

    @Test public void completionAfterActivityDestructionCannotPersistSession() {
        ZeroChillAccountActivity activity = create(null);
        View root = activity.getWindow().getDecorView();
        find(root, "Activate Existing Account").performClick();
        field(root, "Registered email").setText("fake@example.com");
        find(root, "Send verification email").performClick();
        field(root, "Verification code").setText("123456");
        find(root, "Verify code").performClick();
        field(root, "New password").setText("new-password-123");
        field(root, "Confirm new password").setText("new-password-123");
        Accounts.deferCompletion = true;
        find(root, "Activate account").performClick();
        assertNotNull(Accounts.pending);
        activity.onDestroy();
        Accounts.pending.complete(result(), null);
        assertEquals(0, Accounts.accepts);
        assertEquals("", ZeroChillSessionStore.currentUserId(activity));
    }

    @Test public void productionBuildKeepsExistingSignupAndLoginWithoutActivationOption() {
        Accounts.enabled = false;
        View root = create(null).getWindow().getDecorView();
        assertNotNull(find(root, "SIGN IN"));
        assertNotNull(find(root, "CREATE ACCOUNT"));
        assertNull(find(root, "Activate Existing Account"));
        assertFalse(ZeroChillAccountRepository.activationProject("https://aavjpykssueavbseqdlf.supabase.co"));
        assertFalse(ZeroChillAccountRepository.activationProject("https://fketutffusxgjxjlckci.supabase.co"));
        assertFalse(ZeroChillAccountRepository.activationProject("https://xswtgidwhesajavxjbjb.supabase.co.evil.test"));
        assertTrue(ZeroChillAccountRepository.activationProject("https://xswtgidwhesajavxjbjb.supabase.co"));
    }

    private static ZeroChillAccountActivity create(Bundle state) {
        return Robolectric.buildActivity(ZeroChillAccountActivity.class).create(state).start().resume().visible().get();
    }
    private static EditText field(View root, String description) {
        if (root instanceof EditText && description.equals(root.getContentDescription())) return (EditText) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = field(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static View find(View root, String label) {
        if (root instanceof TextView && label.equals(((TextView) root).getText().toString())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }
}
