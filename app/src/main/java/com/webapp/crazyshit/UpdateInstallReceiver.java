package com.webapp.crazyshit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.util.Log;

/** Forwards the user-approved Android PackageInstaller confirmation. */
public final class UpdateInstallReceiver extends BroadcastReceiver {
    private static final String TAG = "ZeroChillUpdateInstall";

    @Override
    public void onReceive(Context context, Intent result) {
        int status = result.getIntExtra(
                PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm;
            if (Build.VERSION.SDK_INT >= 33) {
                confirm = result.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            } else {
                @SuppressWarnings("deprecation")
                Intent legacy = result.getParcelableExtra(Intent.EXTRA_INTENT);
                confirm = legacy;
            }
            if (confirm == null) {
                Log.w(TAG, "Android installer confirmation unavailable");
                return;
            }
            try {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(confirm);
            } catch (Exception error) {
                Log.w(TAG, "Could not open Android installer confirmation", error);
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            Log.i(TAG, "Android accepted update");
        } else if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
            Log.w(TAG, "Android installer returned status " + status);
        }
    }
}
