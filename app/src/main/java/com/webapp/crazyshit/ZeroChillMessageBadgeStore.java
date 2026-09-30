package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;

/** Small local cache for the current account's unread DM badge. */
final class ZeroChillMessageBadgeStore {
    private static final String PREFS = "zerochill_message_badge";
    private static final String KEY_UNREAD = "unread";

    private ZeroChillMessageBadgeStore() {}

    static int unreadCount(Context context) {
        if (context == null) return 0;
        return Math.max(0, context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_UNREAD, 0));
    }

    static void setUnreadCount(Context context, int value) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_UNREAD, Math.max(0, value))
                .apply();
    }

    static void clear(Context context) {
        setUnreadCount(context, 0);
    }

    static void refresh(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        if (!ZeroChillAccountRepository.hasStoredSession(app)) {
            clear(app);
            return;
        }
        ZeroChillSocialRepository.unreadMessageCount(app, (count, error) -> {
            if (error == null && count != null) setUnreadCount(app, count);
        });
    }
}
