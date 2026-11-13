package com.webapp.crazyshit;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

/** Install-local acknowledgement state, deliberately excluded from user backups. */
final class WhatsNewPolicy {
    static final String EXTRA_LAUNCHER_ENTRY = "whats_new_launcher_entry";
    static final String STORE = "whats_new_state";
    static final String ACK = "acknowledged_version_code";
    private static final String INSTALL = "first_install_time";

    private WhatsNewPolicy() {}

    static boolean isLauncherEntry(Intent intent) {
        return intent != null && Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
                && intent.getData() == null && intent.getExtras() == null;
    }

    static boolean allowsAutomaticDisplay(Intent intent) {
        return isLauncherEntry(intent) || (intent != null
                && intent.getBooleanExtra(EXTRA_LAUNCHER_ENTRY, false)
                && intent.getData() == null && intent.getAction() == null
                && intent.getExtras().size() == 1);
    }

    static boolean acknowledged(Context context) {
        return context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getLong(ACK, 0L)
                >= BuildConfig.VERSION_CODE;
    }

    static boolean pending(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            long install = info.firstInstallTime;
            long storedInstall = prefs.getLong(INSTALL, 0L);
            // Restoring preferences onto a different installation must not import its acknowledgement.
            if (storedInstall != install) {
                prefs.edit().clear().putLong(INSTALL, install).apply();
            }
            boolean upgrade = isUpgrade(install, info.lastUpdateTime);
            if (!upgrade) {
                suppressForCurrentVersion(context);
                return false;
            }
            return BuildConfig.VERSION_CODE > prefs.getLong(ACK, 0L);
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    static boolean isUpgrade(long firstInstall, long lastUpdate) {
        return firstInstall > 0L && lastUpdate - firstInstall > 1000L;
    }

    static void suppressForCurrentVersion(Context context) {
        acknowledge(context, BuildConfig.VERSION_CODE);
    }

    static void acknowledge(Context context, long versionCode) {
        SharedPreferences prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = prefs.edit().putLong(ACK, Math.max(versionCode, prefs.getLong(ACK, 0L)));
        try {
            edit.putLong(INSTALL, context.getPackageManager().getPackageInfo(context.getPackageName(), 0).firstInstallTime);
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
        edit.apply();
    }
}
