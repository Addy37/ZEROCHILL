package com.webapp.crazyshit;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.work.ForegroundInfo;

/** Silent, grouped progress notifications; individual worker IDs avoid foreground-service collisions. */
final class GalleryIndexNotifications {
    static final String EXTRA_OPEN_QUEUE = "open_gallery_index_queue";
    private static final String CHANNEL = "gallery_index_progress";
    private static final String GROUP = "gallery_index_tasks";
    private static final int SUMMARY_ID = 93400;
    private GalleryIndexNotifications() {}

    private static NotificationManager manager(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null && Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Gallery preparation",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Background gallery and thumbnail preparation");
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }
        return manager;
    }

    private static NotificationCompat.Builder builder(Context context) {
        manager(context);
        Intent intent = new Intent(context, LibraryHubActivity.class)
                .putExtra(EXTRA_OPEN_QUEUE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open = PendingIntent.getActivity(context, SUMMARY_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentIntent(open).setOnlyAlertOnce(true).setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setGroup(GROUP).setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY);
    }

    static Notification progress(Context context, String title, String status, int items,
            int ready, int total) {
        boolean preparing = "Preparing thumbnails".equals(status);
        String detail = preparing ? ready + " / " + total + " thumbnails" : items + " items · " + ready + " previews";
        return builder(context).setContentTitle(title).setContentText(status + " · " + detail)
                .setOngoing(true).setProgress(preparing ? Math.max(1, total) : 0,
                        preparing ? ready : 0, !preparing).build();
    }

    static ForegroundInfo foreground(Context context, int lane, String title, String status, int items,
            int ready, int total) {
        return new ForegroundInfo(SUMMARY_ID + 1 + lane, progress(context, title, status, items, ready, total),
                Build.VERSION.SDK_INT >= 29 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC : 0);
    }

    static void update(Context context, int lane, String title, String status, int items,
            int ready, int total, int active, int waiting) {
        try {
            NotificationManager manager = manager(context);
            if (manager == null) return;
            if ("Fetching".equals(status) || "Preparing thumbnails".equals(status))
                manager.notify(SUMMARY_ID + 1 + lane, progress(context, title, status, items, ready, total));
            else if ("Ready".equals(status) || "Partial".equals(status) || "Failed".equals(status))
                manager.cancel(SUMMARY_ID + 1 + lane);
            if (active > 0) {
                manager.notify(SUMMARY_ID, builder(context).setGroupSummary(true)
                        .setContentTitle("Preparing galleries")
                        .setContentText(Math.max(0, active - waiting) + " active · " + waiting + " waiting")
                        .setOngoing(true).setProgress(0, 0, true).build());
            } else {
                manager.notify(SUMMARY_ID, builder(context).setGroupSummary(true)
                        .setContentTitle("Gallery preparation finished")
                        .setContentText("Open Gallery Queue for ready or missing previews")
                        .setAutoCancel(true).setOngoing(false).build());
            }
        } catch (RuntimeException ignored) { /* Notification permission may be denied. */ }
    }
}
