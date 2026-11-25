package com.webapp.crazyshit;

/** The Erome album that owns a ShitTok clip. Never resolved by creator name. */
final class ShitTokEromeGallery {
    private ShitTokEromeGallery() {}

    static String albumUrl(NativeContentItem item) {
        if (item == null) return "";
        String page = EromeRepository.canonicalAlbum(item.sourcePageUrl);
        if (!page.isEmpty()) return page;
        return EromeRepository.canonicalAlbum(item.url);
    }
}
