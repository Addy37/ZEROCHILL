package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.app.DownloadManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Real Library menu builders must all open the shared glass host without navigation. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {35, 36})
public class LibrarySubmenuTest {
    @Test public void savedCollectionHeaderAndItemMenusUseGlass() {
        try (ActivityController<FavoritesActivity> controller = Robolectric.buildActivity(FavoritesActivity.class).setup()) {
            FavoritesActivity activity = controller.get();
            TextView anchor = anchor(activity);
            for (int tab : new int[]{FavoritesActivity.START_CONTINUE, FavoritesActivity.START_HISTORY,
                    FavoritesActivity.START_WATCH_LATER}) {
                ReflectionHelpers.setField(activity, "tab", tab);
                ReflectionHelpers.setField(activity, "mediaFavorites", false);
                ReflectionHelpers.callInstanceMethod(activity, "showSectionMenu",
                        ReflectionHelpers.ClassParameter.from(View.class, anchor));
                check(activity, tab == FavoritesActivity.START_WATCH_LATER
                        ? "Clear Watch Later" : "Clear watch history");
            }
            FavoriteStore.Item saved = new FavoriteStore.Item("Fixture", "fixture", 0);
            for (boolean favorite : new boolean[]{false, true}) {
                ReflectionHelpers.setField(activity, "tab", FavoritesActivity.START_WATCH_LATER);
                ReflectionHelpers.setField(activity, "mediaFavorites", favorite);
                ReflectionHelpers.callInstanceMethod(activity, "showWatchLaterMenu",
                        ReflectionHelpers.ClassParameter.from(View.class, anchor),
                        ReflectionHelpers.ClassParameter.from(FavoriteStore.Item.class, saved));
                check(activity, favorite ? "Unfavorite" : "Remove from Watch Later");
                if (favorite) {
                    ReflectionHelpers.callInstanceMethod(activity, "showSectionMenu",
                            ReflectionHelpers.ClassParameter.from(View.class, anchor));
                    check(activity, "Clear Favorites");
                }
            }
            PlaybackHistoryStore.Item history = new PlaybackHistoryStore.Item("Fixture", "fixture", "",
                    1, 10, 0, false, false);
            for (boolean continuing : new boolean[]{false, true}) {
                ReflectionHelpers.callInstanceMethod(activity, "showHistoryMenu",
                        ReflectionHelpers.ClassParameter.from(View.class, anchor),
                        ReflectionHelpers.ClassParameter.from(PlaybackHistoryStore.Item.class, history),
                        ReflectionHelpers.ClassParameter.from(boolean.class, continuing));
                check(activity, continuing ? "Remove from Continue Watching" : "Delete from history");
            }
        }
    }

    @Test public void downloadMenusKeepTheirStateSpecificActionsInGlass() {
        try (ActivityController<DownloadedActivity> controller = Robolectric.buildActivity(DownloadedActivity.class).setup()) {
            DownloadedActivity activity = controller.get();
            TextView anchor = anchor(activity);
            int[] statuses = {DownloadManager.STATUS_SUCCESSFUL, DownloadManager.STATUS_FAILED,
                    DownloadManager.STATUS_PAUSED, DownloadManager.STATUS_RUNNING};
            String[] actions = {"Play", "Retry", "Resume", "Pause"};
            for (int i = 0; i < statuses.length; i++) {
                VideoDownloadStore.Entry entry = new VideoDownloadStore.Entry(i < 2 ? 1 : -1,
                        "Fixture", "fixture", "", "", "video/mp4", 0, statuses[i], 0, 0, "", 0);
                ReflectionHelpers.callInstanceMethod(activity, "showDownloadMenu",
                        ReflectionHelpers.ClassParameter.from(View.class, anchor),
                        ReflectionHelpers.ClassParameter.from(VideoDownloadStore.Entry.class, entry));
                check(activity, actions[i], "Remove");
            }
        }
    }

    private TextView anchor(Activity activity) {
        TextView anchor = new TextView(activity);
        anchor.setText("⋮");
        ((ViewGroup) activity.findViewById(android.R.id.content)).addView(anchor,
                new android.widget.FrameLayout.LayoutParams(96, 96));
        assertTrue(anchor.isAttachedToWindow());
        return anchor;
    }

    private void check(Activity activity, String... labels) {
        PopupWindow popup = shadowOf(RuntimeEnvironment.getApplication()).getLatestPopupWindow();
        assertNotNull(popup);
        GlassMenuTestSupport.assertGlass(popup);
        for (String label : labels) assertTrue(label, GlassMenuTestSupport.hasAction(popup, label));
        popup.dismiss();
        assertFalse(activity.isFinishing());
        assertNull(shadowOf(activity).getNextStartedActivity());
    }
}
