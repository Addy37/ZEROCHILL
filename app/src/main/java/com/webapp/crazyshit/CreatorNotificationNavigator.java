package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import java.util.ArrayList;
import java.util.Collections;

/** Exact-item viewer routing, with a gallery fallback carrying only the tapped item's focus. */
final class CreatorNotificationNavigator {
    private CreatorNotificationNavigator() { }

    static Intent gallery(Activity activity, UpdateInboxStore.Entry entry, String creator, String focusUrl) {
        CreatorCatalog.FavoriteGroup group = UpdateInboxStore.favoriteGroup(activity, creator);
        Intent fallback;
        if (group != null) {
            CreatorGallerySpec spec = CreatorGallerySpec.from(group);
            fallback = NativeFeedBrowserActivity.createCreatorGallery(activity, spec.item.title, spec.query,
                    spec.profileHint, CreatorGalleryPreloader.sessionId(activity, spec.cacheKey),
                    spec.cacheKey, spec.seedNames, spec.seedUrls, spec.seedImages);
        } else {
            fallback = NativeFeedBrowserActivity.createCreatorGallery(activity,
                    entry.creatorName, creator, entry.fapelloProfileUrl);
        }
        if (focusUrl != null && !focusUrl.isEmpty()) fallback.putStringArrayListExtra(
                NativeFeedBrowserActivity.EXTRA_NOTIFICATION_FRESH_URLS,
                new ArrayList<>(Collections.singletonList(focusUrl)));
        return fallback;
    }

    static Intent itemIntent(Activity activity, UpdateInboxStore.Entry parent, UpdateInboxStore.CreatorItem child) {
        Intent fallback = gallery(activity, parent, child.creatorName, child.item.url);
        if (child.item.url.isEmpty() || !(child.item.isImage() || child.item.isVideo())) return fallback;
        // A separate bounded snapshot avoids changing a cached full creator gallery.
        String session = BunkrGallerySessionStore.create(parent.title, child.item.url);
        BunkrGallerySessionStore.replace(session, Collections.singletonList(child.item), 1, true);
        BunkrGallerySessionStore.persist(activity, session);
        Intent intent = new Intent(activity, BunkrGalleryActivity.class);
        intent.putExtra(BunkrGalleryActivity.EXTRA_SESSION_ID, session);
        intent.putExtra(BunkrGalleryActivity.EXTRA_TITLE, parent.title);
        intent.putExtra(BunkrGalleryActivity.EXTRA_ALBUM_URL, child.item.url);
        intent.putExtra(BunkrGalleryActivity.EXTRA_INITIAL_URL, child.item.url);
        // A photo owns this navigation, including resolution/load failures and retries.
        // Videos retain their existing gallery recovery path.
        if (!child.item.isImage()) {
            intent.putExtra(BunkrGalleryActivity.EXTRA_NOTIFICATION_GALLERY_FALLBACK, fallback);
        }
        return intent;
    }
}
