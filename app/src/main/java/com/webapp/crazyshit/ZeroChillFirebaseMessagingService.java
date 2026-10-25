package com.webapp.crazyshit;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;

/** Receives Firebase token lifecycle callbacks. Notification display is added in the next slice. */
public final class ZeroChillFirebaseMessagingService extends FirebaseMessagingService {
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        ZeroChillPushRegistration.onTokenRefreshed(this, token);
    }
}
