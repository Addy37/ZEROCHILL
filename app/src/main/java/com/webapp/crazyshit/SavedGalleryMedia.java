package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import java.util.Collections;
import java.util.LinkedHashMap;

/** Reopens saved media and preserves its native creator-gallery destination. */
final class SavedGalleryMedia {
    static final String EXTRA_FAVORITE = "saved_media_favorite";
    static final String EXTRA_CREATOR_GALLERY = "saved_media_creator_gallery";

    static void open(Activity activity, NativeContentItem item) {
        activity.startActivity(viewer(activity, item));
    }

    static void open(Activity activity, FavoriteStore.Item saved) {
        if (saved == null || saved.content == null) return;
        Intent intent = viewer(activity, saved.content);
        if (saved.mediaFavorite && saved.content.isImage()) {
            intent.putExtra(EXTRA_FAVORITE, true);
            Intent gallery = creatorGallery(activity, saved);
            if (gallery != null) intent.putExtra(EXTRA_CREATOR_GALLERY, gallery);
        }
        activity.startActivity(intent);
    }

    private static Intent viewer(Activity activity, NativeContentItem item) {
        String session = BunkrGallerySessionStore.create(item.title, item.url);
        BunkrGallerySessionStore.replace(session, Collections.singletonList(item), 0, true);
        return new Intent(activity, BunkrGalleryActivity.class)
                .putExtra(BunkrGalleryActivity.EXTRA_SESSION_ID, session)
                .putExtra(BunkrGalleryActivity.EXTRA_TITLE, item.title)
                .putExtra(BunkrGalleryActivity.EXTRA_ALBUM_URL, item.url)
                .putExtra(BunkrGalleryActivity.EXTRA_INITIAL_URL, item.url);
    }

    /** Called on navigation/menu actions, never during scrolling or thumbnail binding. */
    static Intent creatorGallery(Activity activity, FavoriteStore.Item saved) {
        if (saved == null || saved.content == null) return null;
        String key = saved.galleryKey.trim();
        if (!key.isEmpty()) {
            for (CreatorCatalog.FavoriteGroup group : CreatorCatalog.favoriteGroups(activity)) {
                CreatorGallerySpec spec = CreatorGallerySpec.from(group);
                if (key.equalsIgnoreCase(spec.cacheKey)) return galleryIntent(activity, spec);
            }
        }
        NativeContentItem creator = OnlyFapVideoMetadata.creator(saved.content);
        if (creator == null) creator = ShitTokCreatorMetadata.creatorIdentity(null, saved.content);
        if (creator == null && (WikiFeetRepository.isProfileUrl(saved.content.uploader, WikiFeetRepository.Site.WIKIFEET)
                || WikiFeetRepository.isProfileUrl(saved.content.uploader, WikiFeetRepository.Site.WIKIFEET_X))) {
            String name = saved.content.title.replaceFirst("\\s+#\\d+$", "").trim();
            if (!name.isEmpty()) creator = new NativeContentItem(NativeContentItem.KIND_CREATOR,
                    name, saved.content.uploader, "", "", "", "", "WikiFeet", name);
        }
        // Existing single-creator gallery keys are saved search queries. Merged hashes
        // cannot be used as a creator name if the original group is no longer present.
        if (creator == null && !key.isEmpty() && !key.startsWith("merged:")) {
            creator = new NativeContentItem(NativeContentItem.KIND_CREATOR,
                    key, "", "", "", "", "", "", key);
        }
        if (creator == null) return null;
        return galleryIntent(activity, CreatorGallerySpec.from(new CreatorCatalog.FavoriteGroup(
                creator, new LinkedHashMap<>(), Collections.emptySet(), false)));
    }

    static void openCreator(Activity activity, FavoriteStore.Item saved) {
        Intent intent = creatorGallery(activity, saved);
        if (intent != null) activity.startActivity(intent);
        else ZeroChillToast.makeText(activity, "Creator information is unavailable for this saved item.",
                ZeroChillToast.LENGTH_SHORT).show();
    }

    private static Intent galleryIntent(Activity activity, CreatorGallerySpec spec) {
        String session = CreatorGalleryPreloader.sessionId(activity, spec.cacheKey);
        if (session.isEmpty() && spec.grouped)
            session = CreatorGalleryPreloader.composeInMemoryMergedSession(activity, spec);
        return NativeFeedBrowserActivity.createCreatorGallery(activity, spec.item.title,
                spec.query, spec.profileHint, session, spec.cacheKey,
                spec.seedNames, spec.seedUrls, spec.seedImages);
    }
}
