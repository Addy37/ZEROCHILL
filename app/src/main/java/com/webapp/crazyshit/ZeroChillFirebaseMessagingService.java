package com.webapp.crazyshit;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/** Receives Firebase token lifecycle callbacks and privacy-safe server push data. */
public final class ZeroChillFirebaseMessagingService extends FirebaseMessagingService {
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        ZeroChillPushRegistration.onTokenRefreshed(this, token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Map<String, String> data = remoteMessage.getData();
        if (!"direct_message".equals(clean(data.get("type")))) return;

        String recipientId = clean(data.get("recipient_id"));
        String currentUserId = ZeroChillSessionStore.currentUserId(this);
        if (currentUserId.isEmpty() || !currentUserId.equalsIgnoreCase(recipientId)) return;

        ZeroChillPushNotificationPresenter.showDirectMessage(
                this,
                clean(data.get("message_id")),
                clean(data.get("sender_id")),
                clean(data.get("sender_name"))
        );
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
