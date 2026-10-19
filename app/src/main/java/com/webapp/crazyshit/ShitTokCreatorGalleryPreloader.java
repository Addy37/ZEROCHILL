package com.webapp.crazyshit;

import android.content.Context;

/** Keeps ShitTok creator taps on the shared creator-gallery prewarm path. */
final class ShitTokCreatorGalleryPreloader {
    private ShitTokCreatorGalleryPreloader() {
    }

    static void warm(Context context, NativeContentItem item) {
        if (context == null) return;
        // Immediate metadata only. Catalog enrichment belongs to the existing async loader.
        NativeContentItem creator = ShitTokCreatorMetadata.creatorIdentity(null, item);
        CreatorGalleryPreloader.warm(context, creator);
    }

    static String sessionId(android.content.Context context, String creator) {
        return CreatorGalleryPreloader.sessionId(context, creator);
    }
}
