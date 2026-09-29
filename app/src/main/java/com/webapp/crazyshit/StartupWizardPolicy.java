package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

/** Decides whether the full-screen ZEROCHILL startup wizard belongs in this install. */
final class StartupWizardPolicy {
    static final String PREF_COMPLETE = "startup_wizard_v1_complete";
    static final String EXTRA_PREVIEW = "startup_wizard_preview";

    private static final String PREFS = "app_prefs";
    private static final long FRESH_INSTALL_CLOCK_SLOP_MS = 1_000L;

    private StartupWizardPolicy() {
    }

    static boolean shouldShow(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_COMPLETE, false)) return false;

        long firstInstallTime = 0L;
        long lastUpdateTime = 0L;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(
                    context.getPackageName(),
                    0
            );
            firstInstallTime = info.firstInstallTime;
            lastUpdateTime = info.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        boolean legacyInstall = !shouldShowFromState(
                false,
                firstInstallTime,
                lastUpdateTime,
                AccessNoticeDialog.isAccepted(context)
        );
        if (legacyInstall) {
            markComplete(context);
            return false;
        }
        return true;
    }

    static boolean shouldShowFromState(
            boolean complete,
            long firstInstallTime,
            long lastUpdateTime,
            boolean legacyAccessAccepted
    ) {
        if (complete) return false;
        if (legacyAccessAccepted) return false;
        if (firstInstallTime <= 0L || lastUpdateTime <= 0L) return true;
        return lastUpdateTime - firstInstallTime <= FRESH_INSTALL_CLOCK_SLOP_MS;
    }

    static boolean isComplete(Context context) {
        return context != null && context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_COMPLETE, false);
    }

    static void markComplete(Context context) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_COMPLETE, true)
                .apply();
    }
}
