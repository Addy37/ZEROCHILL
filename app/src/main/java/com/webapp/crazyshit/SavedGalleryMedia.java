package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import java.util.Collections;

/** Reopens a saved image or video using the existing gallery resolver and viewer. */
final class SavedGalleryMedia {
    static void open(Activity activity, NativeContentItem item) {
        String session = BunkrGallerySessionStore.create(item.title, item.url);
        BunkrGallerySessionStore.replace(session, Collections.singletonList(item), 0, true);
        activity.startActivity(new Intent(activity, BunkrGalleryActivity.class)
                .putExtra(BunkrGalleryActivity.EXTRA_SESSION_ID, session)
                .putExtra(BunkrGalleryActivity.EXTRA_TITLE, item.title)
                .putExtra(BunkrGalleryActivity.EXTRA_ALBUM_URL, item.url)
                .putExtra(BunkrGalleryActivity.EXTRA_INITIAL_URL, item.url));
    }
}
