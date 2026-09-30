package com.webapp.crazyshit;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Account-synced in-app alert choices. Existing history and DM unread state are untouched. */
final class ZeroChillNotificationPreferences {
    private static final String PREFS = "zerochill_account_alert_preferences_v1";
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();
    static final class Values {
        final boolean replies, likes, directMessages, creatorUpdates, appUpdates;
        Values(boolean replies, boolean likes, boolean directMessages, boolean creatorUpdates, boolean appUpdates) {
            this.replies = replies; this.likes = likes; this.directMessages = directMessages;
            this.creatorUpdates = creatorUpdates; this.appUpdates = appUpdates;
        }
        static Values defaults() { return new Values(true, true, true, true, true); }
        static Values from(JSONObject row) {
            return new Values(row.optBoolean("replies", true), row.optBoolean("likes", true),
                    row.optBoolean("direct_messages", true), row.optBoolean("creator_updates", true),
                    row.optBoolean("app_updates", true));
        }
        JSONObject json() throws Exception {
            return new JSONObject().put("replies", replies).put("likes", likes)
                    .put("direct_messages", directMessages).put("creator_updates", creatorUpdates)
                    .put("app_updates", appUpdates);
        }
        boolean allowsSocial(String type) {
            return ZeroChillSocialRepository.SocialActivity.TYPE_REPLY.equals(type) ? replies : likes;
        }
    }
    static Values cached(Context context) {
        return cachedForAccount(context, ZeroChillSessionStore.currentUserId(context));
    }
    static Values cachedForAccount(Context context, String account) {
        if (account == null || account.isEmpty()) return Values.defaults();
        try {
            return Values.from(new JSONObject(context.getApplicationContext().getSharedPreferences(PREFS, 0)
                    .getString(account, "{}")));
        } catch (Exception ignored) { return Values.defaults(); }
    }
    static void load(Context context, ZeroChillAccountRepository.Callback<Values> callback) {
        final Context app = context.getApplicationContext();
        final String account = ZeroChillSessionStore.currentUserId(app);
        NETWORK.execute(() -> {
            try {
                String raw = ZeroChillAccountRepository.restBlocking(app, account, "GET",
                        "/rest/v1/notification_preferences?select=replies,likes,direct_messages,creator_updates,app_updates&user_id=eq."
                                + account + "&limit=1", null);
                JSONArray rows = new JSONArray(raw);
                Values value = rows.length() == 0 ? Values.defaults() : Values.from(rows.getJSONObject(0));
                cache(app, account, value);
                callback.complete(value, null);
            } catch (Exception error) { callback.complete(cachedForAccount(app, account), error); }
        });
    }
    static void save(Context context, Values value, ZeroChillAccountRepository.Callback<Values> callback) {
        final Context app = context.getApplicationContext();
        final String account = ZeroChillSessionStore.currentUserId(app);
        NETWORK.execute(() -> {
            try {
                JSONObject row = value.json().put("user_id", account);
                // Use a dedicated owner-only RPC for atomic insert/update, avoiding table-wide UPDATE grants.
                ZeroChillAccountRepository.restBlocking(app, account, "POST", "/rest/v1/rpc/save_zerochill_notification_preferences",
                        new JSONObject().put("choices", row));
                cache(app, account, value);
                callback.complete(value, null);
            } catch (Exception error) { callback.complete(null, error); }
        });
    }
    private static void cache(Context context, String account, Values value) throws Exception {
        if (!account.equals(ZeroChillSessionStore.currentUserId(context)))
            throw new IllegalStateException("Account changed. Reopen this screen.");
        context.getSharedPreferences(PREFS, 0).edit().putString(account, value.json().toString()).apply();
    }
    static void clearAccount(Context context, String account) {
        context.getApplicationContext().getSharedPreferences(PREFS, 0).edit().remove(account).apply();
    }
}
