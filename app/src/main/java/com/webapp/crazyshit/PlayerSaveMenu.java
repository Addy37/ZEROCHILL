package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Dialog;
import android.os.Handler;
import android.os.Looper;

/** One save entry point for ShitTok and Shows. Capture the clip before opening any dialog. */
final class PlayerSaveMenu {
    private Dialog current;
    private final Handler main = new Handler(Looper.getMainLooper());

    boolean isOpen() { return current != null; }

    void dismiss() {
        if (current != null) current.dismiss();
        current = null;
    }

    static VideoActionSheet.Action[] choices(boolean saved, Runnable watchLater, Runnable collection) {
        return new VideoActionSheet.Action[] {
                VideoActionSheet.action(R.drawable.ic_action_save_outline,
                        saved ? "Remove from Watch Later" : "Watch Later",
                        saved ? "Remove this video from Watch Later" : "Save for later viewing", watchLater),
                VideoActionSheet.action(R.drawable.ic_more_library, "Add to collection",
                        "Organize your saved content", collection)
        };
    }

    void show(Activity activity, NativeContentItem target, Runnable watchLater, Runnable closed) {
        if (target == null || target.url.trim().isEmpty() || isOpen()
                || activity.isFinishing() || activity.isDestroyed()) return;
        Runnable collections = () -> MediaCollectionPicker.show(activity, target, "", () ->
                ZeroChillToast.makeText(activity, "Collections updated.", ZeroChillToast.LENGTH_SHORT).show(),
                dialog -> track(dialog, closed));
        Dialog sheet = VideoActionSheet.showCompactWithTitle(activity, target.title, "Save to",
                VideoActionSheet.section("SAVE", choices(FavoriteStore.contains(activity, target.url),
                        watchLater, collections)));
        if (sheet != null) track(sheet, closed);
    }

    private void track(Dialog dialog, Runnable closed) {
        current = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (current != dialog) return;
            current = null;
            // An action may replace the sheet with the collection or naming dialog.
            main.post(() -> {
                if (current == null && closed != null) closed.run();
            });
        });
    }
}
