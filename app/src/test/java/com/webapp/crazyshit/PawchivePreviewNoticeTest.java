package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class PawchivePreviewNoticeTest {
    @Test public void onlyPawchiveShowsNoticeAndDismissalPersistsAcrossGalleries() {
        String firstProfile = "https://pawchive.pw/patreon/user/1";
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = controller.get();
        AppearanceTheme.setAccent(activity, AppearanceTheme.Accent.PURPLE);
        PawchivePreviewNotice.maybeShow(activity, "https://fapello.com/creator/");
        assertNull(activity.getFragmentManager().findFragmentByTag(PawchivePreviewNotice.TAG));

        PawchivePreviewNotice.maybeShow(activity, firstProfile);
        PawchivePreviewNotice notice = (PawchivePreviewNotice) activity.getFragmentManager()
                .findFragmentByTag(PawchivePreviewNotice.TAG);
        assertNotNull(notice);
        PawchivePreviewNotice.maybeShow(activity, firstProfile);
        assertSame(notice, activity.getFragmentManager().findFragmentByTag(PawchivePreviewNotice.TAG));

        controller.recreate();
        activity = controller.get();
        notice = (PawchivePreviewNotice) activity.getFragmentManager()
                .findFragmentByTag(PawchivePreviewNotice.TAG);
        assertNotNull(notice);
        AlertDialog dialog = (AlertDialog) notice.getDialog();
        assertEquals(AppearanceTheme.accent(activity),
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).getCurrentTextColor());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        activity.getFragmentManager().executePendingTransactions();
        controller.pause().stop().destroy();

        try (ActivityController<Activity> next = Robolectric.buildActivity(Activity.class).setup()) {
            PawchivePreviewNotice.maybeShow(next.get(), "https://pawchive.pw/fanbox/user/2");
            assertNull(next.get().getFragmentManager().findFragmentByTag(PawchivePreviewNotice.TAG));
        }
    }
}
