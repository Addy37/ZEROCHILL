package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.CookieManager;

/** Owns the explicit XMegaDrive 18+ age-session marker after ZEROCHILL confirmation. */
final class XMegaDriveAgeSession {
    static final String COOKIE_NAME = "kt_agecheck";
    static final String COOKIE_VALUE = "1";
    static final String COOKIE_URL = "https://www.xmegadrive.com/";
    static final String PREF_SETUP_COMPLETE = "xmegadrive_access_setup_v1_complete";
    static final String PREF_REPAIR_HANDLED = "xmegadrive_access_setup_v1_repair_handled";

    private static final String PREFS = "app_prefs";
    private static final long COOKIE_MAX_AGE_SECONDS = 365L * 24L * 60L * 60L;

    interface Callback {
        void onComplete(boolean ready);
    }

    private XMegaDriveAgeSession() {
    }

    static String cookieDirective() {
        return COOKIE_NAME + "=" + COOKIE_VALUE
                + "; Max-Age=" + COOKIE_MAX_AGE_SECONDS
                + "; Path=/; SameSite=Lax; Secure";
    }

    static boolean hasAgeCookieHeader(String header) {
        if (header == null || header.trim().isEmpty()) return false;
        for (String part : header.split(";")) {
            String item = part == null ? "" : part.trim();
            int split = item.indexOf('=');
            if (split <= 0) continue;
            String name = item.substring(0, split).trim();
            String value = item.substring(split + 1).trim();
            if (COOKIE_NAME.equals(name) && COOKIE_VALUE.equals(value)) return true;
        }
        return false;
    }

    static boolean isCookieReady(Context context) {
        try {
            return hasAgeCookieHeader(CookieManager.getInstance().getCookie(COOKIE_URL));
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean isSetupComplete(Context context) {
        return context != null && prefs(context).getBoolean(PREF_SETUP_COMPLETE, false);
    }

    static boolean isRepairHandled(Context context) {
        return context != null && prefs(context).getBoolean(PREF_REPAIR_HANDLED, false);
    }

    static boolean shouldShowRepair(Context context) {
        if (context == null || !AccessNoticeDialog.isAccepted(context)) return false;
        if (isSetupComplete(context)) {
            restoreIfAuthorized(context);
            return false;
        }
        if (isCookieReady(context)) {
            markSetupComplete(context);
            return false;
        }
        return !isRepairHandled(context);
    }

    static boolean shouldShowRepairFromState(
            boolean accessAccepted,
            boolean setupComplete,
            boolean repairHandled,
            boolean cookieReady
    ) {
        return accessAccepted && !setupComplete && !repairHandled && !cookieReady;
    }

    static void markSetupComplete(Context context) {
        if (context == null) return;
        prefs(context).edit()
                .putBoolean(PREF_SETUP_COMPLETE, true)
                .putBoolean(PREF_REPAIR_HANDLED, true)
                .apply();
    }

    static void markRepairHandled(Context context) {
        if (context == null) return;
        prefs(context).edit().putBoolean(PREF_REPAIR_HANDLED, true).apply();
    }

    static void restoreIfAuthorized(Context context) {
        if (context == null
                || !AccessNoticeDialog.isAccepted(context)
                || !isSetupComplete(context)
                || isCookieReady(context)) {
            return;
        }
        writeCookie(context, null);
    }

    static void authorize(Context context, Callback callback) {
        writeCookie(context, callback);
    }

    private static void writeCookie(Context context, Callback callback) {
        if (context == null) {
            finish(callback, false);
            return;
        }
        try {
            CookieManager cookies = CookieManager.getInstance();
            cookies.setAcceptCookie(true);
            cookies.setCookie(COOKIE_URL, cookieDirective(), accepted -> {
                try {
                    cookies.flush();
                } catch (Exception ignored) {
                }
                finish(callback, isCookieReady(context));
            });
        } catch (Exception failure) {
            finish(callback, false);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void finish(Callback callback, boolean ready) {
        if (callback != null) callback.onComplete(ready);
    }
}
