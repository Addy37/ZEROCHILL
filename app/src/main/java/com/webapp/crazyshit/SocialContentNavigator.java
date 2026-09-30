package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebSettings;

import java.util.Locale;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Opens a social conversation over its native content while retaining the caller's task stack. */
final class SocialContentNavigator {
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    interface Resolver { CrazyShitRepository.StreamInfo resolve(Activity host, String url, NativeContentItem cached) throws Exception; }
    interface Session { String current(Activity host); }
    static Resolver resolver = (host, url, cached) -> cached == null ? PlayableSourceRouter.resolve(host, url) : PlayableSourceRouter.resolve(host, cached);
    static Session session = ZeroChillSessionStore::currentUserId;
    static java.util.concurrent.Executor executor = IO;
    private static final WeakHashMap<Activity, String> PENDING = new WeakHashMap<>();

    private SocialContentNavigator() {}

    static void open(Activity activity, UpdateInboxStore.Entry entry, boolean reply) {
        if (activity == null || entry == null || activity.isFinishing() || activity.isDestroyed()) return;
        String accountId = session.current(activity);
        if (!UpdateInboxStore.CATEGORY_SOCIAL.equals(entry.category)
                || accountId.isEmpty() || !accountId.equals(entry.accountId)) return;
        String pageUrl = clean(entry.pageUrl);
        if (pageUrl.isEmpty()) return;
        NativeContentItem cached = SocialContentContextStore.find(activity, pageUrl);
        String title = clean(entry.videoTitle);
        String commentId = clean(entry.commentId);
        if (activity instanceof VideoDetailActivity
                && ((VideoDetailActivity) activity)
                .openSocialCommentsIfCurrent(pageUrl, commentId, reply)) {
            return;
        }
        if (!supportsNativeVideo(pageUrl)) {
            showFallback(activity, accountId, pageUrl, title, commentId, reply);
            return;
        }
        String request = pageUrl + "|" + commentId + "|" + reply;
        synchronized (PENDING) {
            if (request.equals(PENDING.get(activity))) return;
            PENDING.put(activity, request);
        }
        executor.execute(() -> {
            CrazyShitRepository.StreamInfo stream = null;
            try {
                // Fapello's existing browser fallback needs the foreground Activity context.
                stream = resolver.resolve(activity, pageUrl, cached);
            } catch (Exception ignored) {
                // Keep the older notification's comment-sheet path if the native source is unavailable.
            }
            CrazyShitRepository.StreamInfo resolved = stream;
            MAIN.post(() -> {
                synchronized (PENDING) {
                    if (!request.equals(PENDING.get(activity))) return;
                    PENDING.remove(activity);
                }
                if (activity.isFinishing() || activity.isDestroyed() || !SocialActivityCoordinator.canNavigate(activity)
                        || !accountId.equals(session.current(activity))) return;
                if (resolved == null || clean(resolved.mediaUrl).isEmpty()
                        || isImageMedia(resolved.mediaUrl)) {
                    showFallback(activity, accountId, pageUrl, title, commentId, reply);
                    return;
                }
                Intent intent = new Intent(activity, VideoDetailActivity.class);
                intent.putExtra(PlayerActivity.EXTRA_MEDIA_URL, resolved.mediaUrl);
                // Comments are indexed by their original page URL. Do not swap in a resolver redirect.
                intent.putExtra(PlayerActivity.EXTRA_PAGE_URL, pageUrl);
                intent.putExtra(PlayerActivity.EXTRA_TITLE,
                        title.isEmpty() ? clean(resolved.title) : title);
                intent.putExtra(VideoDetailActivity.EXTRA_MEDIA_REFERER, resolved.requestReferer);
                intent.putExtra(VideoDetailActivity.EXTRA_SOURCE, source(pageUrl));
                intent.putExtra(VideoDetailActivity.EXTRA_SOCIAL_ENTRY, true);
                intent.putExtra(VideoDetailActivity.EXTRA_SOCIAL_FOCUS_COMMENT_ID, commentId);
                intent.putExtra(VideoDetailActivity.EXTRA_SOCIAL_AUTO_REPLY, reply);
                try {
                    intent.putExtra(PlayerActivity.EXTRA_USER_AGENT,
                            WebSettings.getDefaultUserAgent(activity));
                } catch (Exception ignored) {}
                try {
                    String cookies = CookieManager.getInstance().getCookie(resolved.mediaUrl);
                    if ((cookies == null || cookies.isEmpty()) && resolved.pageUrl != null) {
                        cookies = CookieManager.getInstance().getCookie(resolved.pageUrl);
                    }
                    if (cookies != null) intent.putExtra(PlayerActivity.EXTRA_COOKIES, cookies);
                } catch (Exception ignored) {}
                NativeContentItem artwork = cached == null ? entry.firstItem() : cached;
                if (artwork != null && !artwork.imageUrl.isEmpty()) intent.putExtra(VideoDetailActivity.EXTRA_POSTER_URL, artwork.imageUrl);
                if (activity instanceof VideoDetailActivity) ((VideoDetailActivity) activity).prepareForSocialNavigation();
                activity.startActivity(intent);
            });
        });
    }

    private static void showFallback(Activity activity, String accountId, String pageUrl,
                                     String title, String commentId, boolean reply) {
        if (activity.isFinishing() || activity.isDestroyed() || !SocialActivityCoordinator.canNavigate(activity)
                || !accountId.equals(session.current(activity))) return;
        if (!InlineCommentsDialog.isOpenFor(activity, pageUrl))
            new InlineCommentsDialog(activity, pageUrl, title, "", commentId, reply, null).show();
    }

    /** Only route recognized page hosts through the native resolver; unknown/local URLs use comments. */
    static boolean supportsNativeVideo(String url) {
        String host;
        try {
            Uri parsed = Uri.parse(url);
            String scheme = parsed.getScheme();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) return false;
            host = parsed.getHost();
        } catch (Exception ignored) { return false; }
        if (host == null) return false;
        host = host.toLowerCase(Locale.US);
        String baseHost = Uri.parse(CrazyShitRepository.BASE).getHost();
        if (host.equals(baseHost) || host.equals("www." + baseHost)) return true;
        return EfuktRepository.isEfuktUrl(url)
                || WebVideoSourceRepository.isKaoticUrl(url)
                || WebVideoSourceRepository.isTheYncUrl(url)
                || WebVideoSourceRepository.isItemFixUrl(url)
                || FapelloRepository.isPostUrl(url)
                || BunkrRepository.isBunkrUrl(url) && !BunkrRepository.isAlbumUrl(url)
                || OnlyHavenRepository.isOnlyHavenUrl(url);
    }

    static boolean isImageMedia(String mediaUrl) {
        String path;
        try { path = Uri.parse(mediaUrl).getPath(); }
        catch (Exception ignored) { return false; }
        return path != null && path.toLowerCase(Locale.US)
                .matches(".*\\.(jpg|jpeg|png|gif|webp|avif|heic|bmp)$");
    }

    private static String source(String url) {
        if (EfuktRepository.isEfuktUrl(url)) return NativeFeedBrowserActivity.SOURCE_EFUKT;
        if (BunkrRepository.isBunkrUrl(url)) return NativeFeedBrowserActivity.SOURCE_BUNKR;
        if (WebVideoSourceRepository.isKaoticUrl(url)) return "kaotic";
        if (FapelloRepository.isFapelloUrl(url)) return "fapello";
        if (OnlyHavenRepository.isOnlyHavenUrl(url)) return "onlyhaven";
        return "crazyshit";
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
