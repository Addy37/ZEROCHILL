package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;

/** Small local cache for the current account's unread DM badge. */
final class ZeroChillMessageBadgeStore {
    static final String PREFS = "zerochill_message_badge";
    private static final String KEY_UNREAD = "unread";

    private ZeroChillMessageBadgeStore() {}

    static int unreadCount(Context context) {
        if (context == null) return 0;
        String user = ZeroChillSessionStore.currentUserId(context);
        if (user.isEmpty()) return 0;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!user.equals(prefs.getString("owner", ""))) return 0;
        return Math.max(0, prefs.getInt(KEY_UNREAD, 0));
    }

    static void setUnreadCount(Context context, int value) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString("owner", ZeroChillSessionStore.currentUserId(context))
                .putInt(KEY_UNREAD, Math.max(0, value))
                .apply();
    }

    static void clear(Context context) {
        setUnreadCount(context, 0);
    }

    static long cleanupRevision(Context context, String userId) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong("cleanup:" + userId, 0L);
    }

    static synchronized void conversationCleared(Context context, String userId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putLong("cleanup:" + userId, cleanupRevision(context, userId) + 1).apply();
    }

    static void refresh(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        if (!ZeroChillAccountRepository.hasStoredSession(app)) {
            clear(app);
            return;
        }
        String user = ZeroChillSessionStore.currentUserId(app);
        long revision = cleanupRevision(app, user);
        ZeroChillSocialRepository.unreadMessageCount(app, (count, error) -> {
            if (error == null && count != null
                    && user.equals(ZeroChillSessionStore.currentUserId(app))
                    && revision == cleanupRevision(app, user)) setUnreadCount(app, count);
        });
    }
}
