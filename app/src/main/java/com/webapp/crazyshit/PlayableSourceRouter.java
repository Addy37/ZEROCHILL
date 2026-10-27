package com.webapp.crazyshit;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;
import java.util.Locale;

/** Routes a page to the matching first-party parser without changing the shared player contract. */
final class PlayableSourceRouter {
    private static final String BUNKR_PAGE_FRAGMENT = "zerochill-bunkr-page";

    private PlayableSourceRouter() {
    }

    static CrazyShitRepository.StreamInfo resolve(Context context, NativeContentItem item)
            throws IOException {
        if (item == null) return null;
        String pageUrl = item.url == null ? "" : item.url;
        return resolve(context, pageUrl);
    }

    static CrazyShitRepository.StreamInfo resolve(Context context, String pageUrl) throws IOException {
        if (BaddieHubRepository.isBaddieHubUrl(pageUrl)) {
            return new BaddieHubRepository().resolvePlayable(context, pageUrl);
        }
        if (XMegaDriveRepository.isXMegaDriveUrl(pageUrl)) {
            return new XMegaDriveRepository().resolvePlayable(context, pageUrl);
        }
        if (WikiFeetRepository.isWikiFeetUrl(pageUrl)) {
            return new WikiFeetRepository().resolvePlayable(pageUrl);
        }
        if (FapelloRepository.isFapelloUrl(pageUrl)) {
            return new FapelloRepository().resolvePlayable(context, pageUrl);
        }
        if (BunkrRepository.isBunkrUrl(pageUrl)) {
            return new BunkrRepository().resolvePlayable(context, bunkrResolverInput(pageUrl));
        }
        if (OnlyHavenRepository.isOnlyHavenUrl(pageUrl)) {
            return new OnlyHavenRepository().resolvePlayable(context, pageUrl);
        }
        WebVideoSourceRepository web = new WebVideoSourceRepository();
        if (WebVideoSourceRepository.isKaoticUrl(pageUrl)) {
            return web.resolvePlayable(context, WebVideoSourceRepository.Source.KAOTIC, pageUrl);
        }
        if (EfuktRepository.isEfuktUrl(pageUrl)) {
            return new EfuktRepository().resolvePlayable(context, pageUrl);
        }
        return new CrazyShitRepository().resolvePlayable(context, pageUrl);
    }

    /**
     * Bunkr file pages can include the original file extension in the page slug, for example
     * /f/name-token.mp4. BunkrRepository also accepts true direct assets, so without a marker that
     * page-shaped URL can be mistaken for the media itself. A fragment is ideal here: it prevents
     * the extension shortcut but is never sent in the HTTP request, so Bunkr still receives the
     * exact original path/query and can resolve/sign the real CDN media URL.
     */
    static String bunkrResolverInput(String pageUrl) {
        if (pageUrl == null || pageUrl.isEmpty() || !BunkrRepository.isBunkrUrl(pageUrl)) {
            return pageUrl;
        }
        try {
            Uri parsed = Uri.parse(pageUrl);
            String path = parsed.getPath();
            if (path == null) return pageUrl;
            String lower = path.toLowerCase(Locale.US);
            if (!(lower.startsWith("/f/") || lower.startsWith("/v/")
                    || lower.startsWith("/i/") || lower.startsWith("/d/"))) {
                return pageUrl;
            }
            if (parsed.getFragment() != null && !parsed.getFragment().isEmpty()) return pageUrl;
            return pageUrl + "#" + BUNKR_PAGE_FRAGMENT;
        } catch (Exception ignored) {
            return pageUrl;
        }
    }
}
