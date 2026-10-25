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
    static final String CHANNEL_SOCIAL = "social_v1";
    private static final String GROUP_MESSAGES = "zerochill_direct_messages";
    private static final String GROUP_SOCIAL = "zerochill_social";

    private ZeroChillPushNotificationPresenter() { }

    static void initialize(Context context) {
        Context app = context.getApplicationContext();
        createMessagesChannel(app);
        createSocialChannel(app);
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
                .setStyle(new NotificationCompat.BigTextStyle().bigText(name + " sent you a message."))
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

    static void showSocialActivity(
            Context context,
            String eventId,
            String entryId,
            String socialType,
            String actorName
    ) {
        Context app = context.getApplicationContext();
        if (clean(eventId).isEmpty() || clean(entryId).isEmpty() || !isSocialType(socialType) || !canPost(app)) return;
        if (!ZeroChillAccountRepository.hasStoredSession(app)) return;

        createSocialChannel(app);
        String name = cleanSenderName(actorName);
        boolean reply = ZeroChillSocialRepository.SocialActivity.TYPE_REPLY.equals(socialType);
        String action = reply ? "Replied to your comment" : "Liked your comment";
        PendingIntent open = socialPendingIntent(app, entryId, eventId);

        Notification publicVersion = new NotificationCompat.Builder(app, CHANNEL_SOCIAL)
                .setSmallIcon(R.drawable.ic_notification_crazyshit)
                .setContentTitle("New ZEROCHILL social activity")
                .setContentText("Open ZEROCHILL to view it.")
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();

        Notification notification = new NotificationCompat.Builder(app, CHANNEL_SOCIAL)
                .setSmallIcon(R.drawable.ic_notification_crazyshit)
                .setColor(UiPalette.PRIMARY)
                .setContentTitle(name)
                .setContentText(action)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(name + " " + action.toLowerCase() + "."))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setGroup(GROUP_SOCIAL)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .build();

        NotificationManagerCompat.from(app).notify(socialNotificationId(eventId), notification);
    }

    static Intent messageIntent(Context context, String senderId) {
        Intent intent = new Intent(context, ZeroChillMessageActivity.class);
        intent.putExtra(ZeroChillMessageActivity.EXTRA_USER_ID, senderId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    static Intent socialIntent(Context context, String entryId) {
        Intent intent = new Intent(context, UpdateInboxActivity.class);
        intent.putExtra(UpdateInboxActivity.EXTRA_SOCIAL_PUSH_ENTRY_ID, entryId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    static int notificationId(String messageId) {
        return 12_000 + Math.abs((messageId == null ? "" : messageId).hashCode() % 20_000);
    }

    static int socialNotificationId(String eventId) {
        return 32_000 + Math.abs((eventId == null ? "" : eventId).hashCode() % 20_000);
    }

    static String cleanSenderName(String value) {
        String clean = value == null ? "" : value.replaceAll("[\\p{Cntrl}]", "")
                .replaceAll("\\s+", " ").trim();
        if (clean.length() > 64) clean = clean.substring(0, 64).trim();
        return clean.isEmpty() ? "Someone on ZEROCHILL" : clean;
    }

    private static PendingIntent messagePendingIntent(Context context, String senderId, String messageId) {
        return PendingIntent.getActivity(
                context,
                notificationId(messageId),
                messageIntent(context, senderId),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent socialPendingIntent(Context context, String entryId, String eventId) {
        return PendingIntent.getActivity(
                context,
                socialNotificationId(eventId),
                socialIntent(context, entryId),
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

    private static void createSocialChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_SOCIAL,
                "Social",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Replies and likes on your ZEROCHILL comments");
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

    private static boolean isSocialType(String value) {
        return ZeroChillSocialRepository.SocialActivity.TYPE_REPLY.equals(value)
                || ZeroChillSocialRepository.SocialActivity.TYPE_LIKE.equals(value);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
