package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;

@SuppressWarnings("deprecation")
public final class PawchivePreviewNotice extends DialogFragment {
    static final String TAG = "pawchive_preview_notice";
    private static final String ACKNOWLEDGED = "acknowledged";

    static void maybeShow(Activity activity, String profileUrl) {
        if (!PawchiveRepository.isProfile(profileUrl) || activity.isFinishing()
                || activity.isDestroyed() || preferences(activity).getBoolean(ACKNOWLEDGED, false)) {
            return;
        }
        FragmentManager fragments = activity.getFragmentManager();
        if (!fragments.isStateSaved() && fragments.findFragmentByTag(TAG) == null) {
            new PawchivePreviewNotice().show(fragments, TAG);
            fragments.executePendingTransactions();
        }
    }

    @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
        Activity activity = getActivity();
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.pawchive_preview_notice_title)
                .setMessage(R.string.pawchive_preview_notice_body)
                .setPositiveButton(R.string.pawchive_preview_notice_dismiss,
                        (ignored, which) -> acknowledge())
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(AppearanceTheme.accent(activity)));
        return dialog;
    }

    @Override public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        acknowledge();
    }

    private void acknowledge() {
        Activity activity = getActivity();
        if (activity != null) preferences(activity).edit().putBoolean(ACKNOWLEDGED, true).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(TAG, Context.MODE_PRIVATE);
    }
}
