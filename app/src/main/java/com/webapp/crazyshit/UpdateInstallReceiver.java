package com.webapp.crazyshit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

/** Handles Android PackageInstaller callbacks for user-confirmed ZEROCHILL updates. */
public final class UpdateInstallReceiver extends BroadcastReceiver {
    static final String ACTION_INSTALL_STATUS = "com.addy37.crazyshitunofficial.UPDATE_INSTALL_STATUS";
    static final String EXTRA_VERSION = "zerochill_update_version";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        int status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE
        );

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            } else {
                @SuppressWarnings("deprecation")
                Intent legacy = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                confirmation = legacy;
            }
            if (confirmation != null) {
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(confirmation);
            }
            return;
        }

        if (status == PackageInstaller.STATUS_SUCCESS) {
            return;
        }

        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        String version = intent.getStringExtra(EXTRA_VERSION);
        String detail = (message == null || message.trim().isEmpty())
                ? "Android couldn't install the update."
                : "Android couldn't install " + (version == null ? "the update" : version) + ".";
        ZeroChillToast.makeText(context, detail, ZeroChillToast.LENGTH_LONG).show();
    }
}
