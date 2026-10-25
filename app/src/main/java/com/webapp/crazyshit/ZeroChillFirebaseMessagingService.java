package com.webapp.crazyshit;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/** Receives Firebase installation lifecycle callbacks and privacy-safe server push data. */
public final class ZeroChillFirebaseMessagingService extends FirebaseMessagingService {
    @Override
    public void onRegistered(@NonNull String installationId) {
        super.onRegistered(installationId);
        ZeroChillPushRegistration.onRegistered(this, installationId);
    }

    @Override
    public void onUnregistered(@NonNull String installationId) {
        super.onUnregistered(installationId);
        ZeroChillPushRegistration.onUnregistered(this, installationId);
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
