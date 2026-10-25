package com.webapp.crazyshit;

import android.app.Activity;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class ZeroChillPushRegistrationTest {
    @Test
    public void installationIdIsStableAndValidUuid() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        String first = ZeroChillPushRegistration.installationId(activity);
        String second = ZeroChillPushRegistration.installationId(activity);
        assertEquals(first, second);
        assertEquals(first, UUID.fromString(first).toString());
    }

    @Test
    public void sessionCredentialsUseJwtSubjectAndAccessToken() throws Exception {
        String userId = "707ec1ff-d140-4081-a254-b2bf5d9342b2";
        String token = jwt(userId);
        String session = new JSONObject().put("access_token", token).toString();

        ZeroChillPushRegistration.SessionCredentials credentials =
                ZeroChillPushRegistration.sessionCredentials(session);

        assertNotNull(credentials);
        assertEquals(userId, credentials.userId);
        assertEquals(token, credentials.accessToken);
    }

    @Test
    public void invalidOrMissingSessionIsRejected() throws Exception {
        assertNull(ZeroChillPushRegistration.sessionCredentials(""));
        assertNull(ZeroChillPushRegistration.sessionCredentials("not-json"));
        assertNull(ZeroChillPushRegistration.sessionCredentials(new JSONObject().toString()));
        assertNull(ZeroChillPushRegistration.sessionCredentials(
                new JSONObject().put("access_token", "not-a-jwt").toString()));
    }

    private static String jwt(String subject) throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(
                new JSONObject().put("sub", subject).toString().getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".signature";
    }
}
