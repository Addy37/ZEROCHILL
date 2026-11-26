package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import java.util.ArrayList;
import java.util.Collections;

/** Exact-item viewer routing. Creator galleries are explicit profile actions only. */
final class CreatorNotificationNavigator {
    private CreatorNotificationNavigator() { }

    static Intent gallery(Activity activity, UpdateInboxStore.Entry entry, String creator, String focusUrl) {
        CreatorCatalog.FavoriteGroup group = UpdateInboxStore.favoriteGroup(activity, creator);
        Intent fallback;
        if (group != null) {
            CreatorGallerySpec spec = CreatorGallerySpec.from(group);
            fallback = NativeFeedBrowserActivity.createCreatorGallery(activity, spec,
                    CreatorGalleryPreloader.sessionId(activity, spec.sessionKey));
        } else {
            fallback = NativeFeedBrowserActivity.createCreatorGallery(activity,
                    entry.creatorName, creator, entry.fapelloProfileUrl);
        }
        if (focusUrl != null && !focusUrl.isEmpty()) fallback.putStringArrayListExtra(
                NativeFeedBrowserActivity.EXTRA_NOTIFICATION_FRESH_URLS,
                new ArrayList<>(Collections.singletonList(focusUrl)));
        return fallback;
    }

    static boolean isRetired(UpdateInboxStore.CreatorItem child) {
        return child != null && ("wikifeet".equalsIgnoreCase(child.sourceKey)
                || WikiFeetRepository.isRetiredItem(child.item));
    }

    static Intent itemIntent(Activity activity, UpdateInboxStore.Entry parent, UpdateInboxStore.CreatorItem child) {
        if (isRetired(child)) return gallery(activity, parent, child.creatorName, null);
        Intent fallback = gallery(activity, parent, child.creatorName, child.item.url);
        if (!(child.item.isImage() || child.item.isVideo())) return fallback;
        ArrayList<NativeContentItem> items = new ArrayList<>();
        if (parent != null) {
            for (UpdateInboxStore.CreatorItem sibling : parent.children) {
                if (sibling == null || sibling.item == null || isRetired(sibling)) continue;
                if (!(sibling.item.isImage() || sibling.item.isVideo())) continue;
                items.add(sibling.item);
            }
        }
        if (items.isEmpty()) items.add(child.item);
        String album = parent != null && parent.fapelloProfileUrl != null && !parent.fapelloProfileUrl.isEmpty()
                ? parent.fapelloProfileUrl : child.item.url;
        String session = BunkrGallerySessionStore.create(parent == null ? child.creatorName : parent.title, album);
        BunkrGallerySessionStore.replace(session, items, 1, true);
        BunkrGallerySessionStore.persist(activity, session);
        Intent intent = new Intent(activity, BunkrGalleryActivity.class);
        intent.putExtra(BunkrGalleryActivity.EXTRA_SESSION_ID, session);
        intent.putExtra(BunkrGalleryActivity.EXTRA_TITLE, parent == null ? child.creatorName : parent.title);
        intent.putExtra(BunkrGalleryActivity.EXTRA_ALBUM_URL, album);
        intent.putExtra(BunkrGalleryActivity.EXTRA_INITIAL_URL, child.item.url);
        return intent;
    }
}
