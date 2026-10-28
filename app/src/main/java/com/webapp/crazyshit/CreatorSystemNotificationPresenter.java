package com.webapp.crazyshit;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Posts one privacy-safe Android alert for creator activity found by the existing background scan. */
final class CreatorSystemNotificationPresenter {
    static final String CHANNEL_CREATORS = "creator_updates_v1";
    private static final String APP_PREFS = "app_prefs";
    private static final int NOTIFICATION_ID = 56_100;
    private static final int MAX_LINES = 5;

    private CreatorSystemNotificationPresenter() { }

    static void show(Context context, List<NotificationCoordinator.SourceAlert> alerts) {
        if (context == null || alerts == null || alerts.isEmpty()) return;
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(NotificationCoordinator.PREF_NEW_VIDEO_ALERTS, true)) return;
        if (!ZeroChillNotificationPreferences.cached(app).creatorUpdates) return;
        if (!canPost(app)) return;

        List<CreatorUpdate> updates = creatorUpdates(alerts);
        if (updates.isEmpty()) return;
        createChannel(app);

        int totalItems = 0;
        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
        for (int i = 0; i < updates.size(); i++) {
            CreatorUpdate update = updates.get(i);
            totalItems += update.count;
            if (i < MAX_LINES) style.addLine(line(update));
        }
        if (updates.size() > MAX_LINES) {
            style.addLine("+" + (updates.size() - MAX_LINES) + " more creators");
        }

        String title = notificationTitle(updates);
        String text = updates.size() == 1
                ? line(updates.get(0))
                : totalItems + (totalItems == 1 ? " new item" : " new items") + " from your favorites";
        style.setBigContentTitle(title).setSummaryText("Favorite creator updates");

        Intent intent = new Intent(app, UpdateInboxActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(
                app,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification publicVersion = new NotificationCompat.Builder(app, CHANNEL_CREATORS)
                .setSmallIcon(R.drawable.ic_notification_crazyshit)
                .setContentTitle("New creator activity")
                .setContentText("Open ZEROCHILL to view it.")
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();

        Notification notification = new NotificationCompat.Builder(app, CHANNEL_CREATORS)
                .setSmallIcon(R.drawable.ic_notification_crazyshit)
                .setColor(UiPalette.PRIMARY)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(style)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setNumber(totalItems)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .build();

        NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification);
    }

    static List<String> notificationLines(List<NotificationCoordinator.SourceAlert> alerts) {
        ArrayList<String> lines = new ArrayList<>();
        for (CreatorUpdate update : creatorUpdates(alerts)) lines.add(line(update));
        return lines;
    }

    static String notificationTitle(List<NotificationCoordinator.SourceAlert> alerts) {
        return notificationTitle(creatorUpdates(alerts));
    }

    private static String notificationTitle(List<CreatorUpdate> updates) {
        if (updates == null || updates.isEmpty()) return "New creator activity";
        if (updates.size() == 1) return updates.get(0).name + " posted new content";
        return updates.size() + " creators posted new content";
    }

    private static String line(CreatorUpdate update) {
        if (update == null) return "New creator content";
        return update.name + " · " + update.count + (update.count == 1 ? " new item" : " new items");
    }

    private static List<CreatorUpdate> creatorUpdates(List<NotificationCoordinator.SourceAlert> alerts) {
        LinkedHashMap<String, CreatorUpdate> grouped = new LinkedHashMap<>();
        if (alerts == null) return new ArrayList<>();

        for (NotificationCoordinator.SourceAlert alert : alerts) {
            if (alert == null || alert.items == null || alert.items.isEmpty()) continue;
            String name = creatorName(alert);
            if (name.isEmpty()) continue;
            String key = CreatorNameMatcher.normalized(name);
            if (key.isEmpty()) key = name.toLowerCase(java.util.Locale.US);
            CreatorUpdate update = grouped.get(key);
            if (update == null) {
                update = new CreatorUpdate(name);
                grouped.put(key, update);
            }
            update.count += alert.items.size();
        }
        return new ArrayList<>(grouped.values());
    }

    private static String creatorName(NotificationCoordinator.SourceAlert alert) {
        String name = clean(alert.creatorName);
        if (!name.isEmpty()) return name;
        for (NativeContentItem item : alert.items) {
            if (item == null) continue;
            String derived = NotificationCoordinator.onlyFapCreatorName(
                    new NotificationCoordinator.ExperienceItem(alert.key, item)
            );
            if (!derived.isEmpty()) return derived;
        }
        return clean(alert.source);
    }

    private static boolean canPost(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED) return false;
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_CREATORS,
                "Creator updates",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Favorite creator activity found during ZEROCHILL background checks");
        channel.enableLights(true);
        channel.setLightColor(UiPalette.PRIMARY);
        channel.setShowBadge(true);
        manager.createNotificationChannel(channel);
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Cntrl}]", "")
                .replaceAll("\\s+", " ").trim();
    }

    private static final class CreatorUpdate {
        final String name;
        int count;

        CreatorUpdate(String name) {
            this.name = name;
        }
    }
}
