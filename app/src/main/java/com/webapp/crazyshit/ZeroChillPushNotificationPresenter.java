package com.webapp.crazyshit;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.UUID;

/** Builds privacy-preserving Android notifications for server-delivered ZEROCHILL events. */
final class ZeroChillPushNotificationPresenter {
    static final String CHANNEL_MESSAGES = "messages_v1";
    private static final String GROUP_MESSAGES = "zerochill_direct_messages";

    private ZeroChillPushNotificationPresenter() { }

    static void initialize(Context context) {
        createMessagesChannel(context.getApplicationContext());
    }

    static void showDirectMessage(
            Context context,
            String messageId,
            String senderId,
            String senderName
    ) {
        Context app = context.getApplicationContext();
        if (!isUuid(messageId) || !isUuid(senderId) || !canPost(app)) return;
        if (!ZeroChillAccountRepository.hasStoredSession(app)) return;

        createMessagesChannel(app);
        String name = cleanSenderName(senderName);
        PendingIntent open = messagePendingIntent(app, senderId, messageId);

        Notification publicVersion = new NotificationCompat.Builder(app, CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_notification_crazyshit)
                .setContentTitle("New ZEROCHILL message")
                .setContentText("Open ZEROCHILL to view it.")
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();

        Notification notification = new NotificationCompat.Builder(app, CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_notification_crazyshit)
                .setColor(UiPalette.PRIMARY)
                .setContentTitle(name)
                .setContentText("Sent you a message")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        name + " sent you a message."
                ))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setGroup(GROUP_MESSAGES)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .build();

        NotificationManagerCompat.from(app).notify(notificationId(messageId), notification);
    }

    static Intent messageIntent(Context context, String senderId) {
        Intent intent = new Intent(context, ZeroChillMessageActivity.class);
        intent.putExtra(ZeroChillMessageActivity.EXTRA_USER_ID, senderId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    static int notificationId(String messageId) {
        return 12_000 + Math.abs((messageId == null ? "" : messageId).hashCode() % 20_000);
    }

    static String cleanSenderName(String value) {
        String clean = value == null ? "" : value.replaceAll("[\\p{Cntrl}]", "")
                .replaceAll("\\s+", " ").trim();
        if (clean.length() > 64) clean = clean.substring(0, 64).trim();
        return clean.isEmpty() ? "Someone on ZEROCHILL" : clean;
    }

    private static PendingIntent messagePendingIntent(
            Context context,
            String senderId,
            String messageId
    ) {
        return PendingIntent.getActivity(
                context,
                notificationId(messageId),
                messageIntent(context, senderId),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static boolean canPost(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED) return false;
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    private static void createMessagesChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Direct message alerts from other ZEROCHILL users");
        channel.enableLights(true);
        channel.setLightColor(UiPalette.PRIMARY);
        channel.setShowBadge(true);
        manager.createNotificationChannel(channel);
    }

    private static boolean isUuid(String value) {
        try {
            String clean = value == null ? "" : value.trim();
            return UUID.fromString(clean).toString().equalsIgnoreCase(clean);
        } catch (Exception ignored) {
            return false;
        }
    }
}
