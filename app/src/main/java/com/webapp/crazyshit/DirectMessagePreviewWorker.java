package com.webapp.crazyshit;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Upgrades an already-posted generic DM notification with authenticated local preview text. */
public final class DirectMessagePreviewWorker extends Worker {
    static final String INPUT_MESSAGE_ID = "message_id";
    static final String INPUT_SENDER_ID = "sender_id";
    static final String INPUT_SENDER_NAME = "sender_name";

    public DirectMessagePreviewWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String messageId = clean(getInputData().getString(INPUT_MESSAGE_ID));
        String senderId = clean(getInputData().getString(INPUT_SENDER_ID));
        String senderName = getInputData().getString(INPUT_SENDER_NAME);
        if (messageId.isEmpty() || senderId.isEmpty()) return Result.success();

        ZeroChillPushNotificationPresenter.upgradeDirectMessagePreview(
                getApplicationContext(),
                messageId,
                senderId,
                senderName
        );
        return Result.success();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
