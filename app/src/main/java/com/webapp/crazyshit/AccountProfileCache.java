package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;

/** Derived public identity only. The encrypted session remains the authority for ownership. */
final class AccountProfileCache {
    private static String loadingOwner = "";
    static final String PREFS = "zerochill_header_profile_v1";

    static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void record(Context context, ZeroChillAccountRepository.AccountState state) {
        if (state == null || !state.signedIn || state.userId.isEmpty()
                || !state.userId.equals(ZeroChillSessionStore.currentUserId(context))) return;
        preferences(context).edit().putString("owner", state.userId)
                .putString("avatar", state.avatarPath)
                .putString("name", SocialUi.name(state.displayName, state.username)).apply();
    }

    static synchronized void hydrate(Context context,
            ZeroChillAccountRepository.Callback<ZeroChillAccountRepository.AccountState> callback) {
        String owner = ZeroChillSessionStore.currentUserId(context);
        if (owner.isEmpty() || owner.equals(loadingOwner) || !ZeroChillAccountRepository.isConfigured()) return;
        loadingOwner = owner;
        ZeroChillAccountRepository.current(context, (state, error) -> {
            synchronized (AccountProfileCache.class) {
                if (owner.equals(loadingOwner)) loadingOwner = "";
            }
            callback.complete(state, error);
        });
    }

    static void clear(Context context) { preferences(context).edit().clear().apply(); }
}
