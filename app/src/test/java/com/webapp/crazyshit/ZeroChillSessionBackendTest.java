package com.webapp.crazyshit;

import android.app.Application;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ZeroChillSessionBackendTest {
    private static String session(String issuer) throws Exception {
        String payload = new JSONObject().put("iss", issuer).put("sub", "test-user").toString();
        String jwt = "e30." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".signature";
        return new JSONObject().put("access_token", jwt)
                .put("refresh_token", "test-only").toString();
    }

    @Test public void currentProjectTokenIsAccepted() throws Exception {
        assertTrue(ZeroChillSessionStore.isForCurrentBackend(
                session(BuildConfig.ACCOUNT_SUPABASE_URL + "/auth/v1")));
    }

    @Test public void oldProjectTokenCannotSurviveFreshStartUpgrade() throws Exception {
        assertEquals(!BuildConfig.ZEROCHILL_V7_TRAFFIC,
                ZeroChillSessionStore.isForCurrentBackend(
                        session("https://old-project.invalid/auth/v1")));
    }

    @Test public void invalidTokenCannotSurviveFreshStartUpgrade() {
        assertEquals(!BuildConfig.ZEROCHILL_V7_TRAFFIC,
                ZeroChillSessionStore.isForCurrentBackend(
                        "{\"access_token\":\"not-a-jwt\"}"));
    }
}
