package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.widget.TextView;
import org.json.JSONObject;

/** Versioned card in the existing DM body contract. Contains no tokens or expiring CDN URL. */
final class ZeroChillVideoCard {
    static final String PREFIX = "ZEROCHILL video · v1\n";
    private static final java.util.concurrent.ExecutorService RESOLVER =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final class Binding {
        final String body;
        Binding(String body) { this.body = body; }
    }
    private ZeroChillVideoCard() { }

    static String encode(NativeContentItem item) {
        if (item == null || !supportedPage(item.url)) return null;
        try {
            String body = PREFIX + new JSONObject().put("page", item.url)
                    .put("title", item.title.length() > 240 ? item.title.substring(0, 240) : item.title);
            return body.length() <= 2000 ? body : null;
        } catch (Exception error) { return null; }
    }

    static NativeContentItem decode(String body) {
        if (body == null || body.length() > 2000 || !body.startsWith(PREFIX)) return null;
        try {
            JSONObject value = new JSONObject(body.substring(PREFIX.length()));
            String page = value.getString("page"), title = value.getString("title");
            if (!supportedPage(page) || title.length() > 240) return null;
            return new NativeContentItem(NativeContentItem.KIND_MEDIA,
                    title.trim().isEmpty() ? "Video" : title, page, "", "", "", "");
        } catch (Exception error) { return null; }
    }

    static boolean supportedPage(String page) {
        if (page == null || page.length() > 1500) return false;
        try {
            Uri uri = Uri.parse(page);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                    || uri.getPort() != -1 || uri.getHost() == null
                    || uri.getQuery() != null || uri.getFragment() != null) return false;
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(java.util.Locale.ROOT);
            boolean bunkrPage = BunkrRepository.isBunkrUrl(page)
                    && (path.startsWith("/f/") || path.startsWith("/v/"));
            if (!bunkrPage && path.matches(".*\\.(mp4|webm|m4v|m3u8|mpd)$")) return false;
            String host = uri.getHost();
            return host.equalsIgnoreCase("crazyshit.com") || host.equalsIgnoreCase("www.crazyshit.com")
                    || EfuktRepository.isEfuktUrl(page) || FapelloRepository.isFapelloUrl(page)
                    || BunkrRepository.isBunkrUrl(page) || OnlyHavenRepository.isOnlyHavenUrl(page)
                    || BaddieHubRepository.isBaddieHubUrl(page) || XMegaDriveRepository.isXMegaDriveUrl(page)
                    || WebVideoSourceRepository.isKaoticUrl(page) || WikiFeetRepository.isWikiFeetUrl(page);
        } catch (Exception error) { return false; }
    }

    static String preview(String body) {
        NativeContentItem card = decode(body);
        return card == null ? body : "Video · " + card.title;
    }

    /** Reset the recycled text view for ordinary messages as well as cards. */
    static void bind(Activity activity, TextView view, String body) {
        NativeContentItem card = decode(body);
        Object previous = view.getTag();
        boolean unchanged = card != null && previous instanceof Binding
                && ((Binding) previous).body.equals(body);
        // Inbox refreshes/reactions rebind stable rows while source resolution is pending.
        // Retain an unchanged card's request and disabled state; recycling invalidates the tag.
        if (!unchanged) {
            view.setTag(card == null ? new Object() : new Binding(body));
            view.setEnabled(true);
        }
        view.setText(card == null ? body : "▶  " + card.title + "\n\nVideo · Tap to play in ZEROCHILL");
        view.setOnClickListener(card == null ? null : v -> play(activity, view, card));
        view.setClickable(card != null);
        view.setFocusable(card != null);
    }

    private static void play(Activity activity, TextView view, NativeContentItem card) {
        if (!view.isEnabled()) return;
        final CharSequence boundText = view.getText();
        final Object binding = view.getTag();
        final String owner = ZeroChillSessionStore.currentUserId(activity);
        view.setEnabled(false);
        ZeroChillToast.makeText(activity, "Opening video…", ZeroChillToast.LENGTH_SHORT).show();
        RESOLVER.execute(() -> {
            CrazyShitRepository.StreamInfo stream = null;
            try { stream = PlayableSourceRouter.resolve(activity, card.url); } catch (Exception ignored) { }
            final CrazyShitRepository.StreamInfo resolved = stream;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()
                        || !owner.equals(ZeroChillSessionStore.currentUserId(activity))
                        || view.getTag() != binding || !boundText.equals(view.getText())) return;
                view.setEnabled(true);
                if (resolved == null || resolved.mediaUrl == null || resolved.mediaUrl.isEmpty()) {
                    ZeroChillToast.makeText(activity, "This source's video is unavailable. Try again later.",
                            ZeroChillToast.LENGTH_LONG).show();
                    return;
                }
                Intent intent = new Intent(activity, VideoDetailActivity.class)
                        .putExtra(PlayerActivity.EXTRA_PAGE_URL, card.url)
                        .putExtra(PlayerActivity.EXTRA_TITLE, card.title)
                        .putExtra(PlayerActivity.EXTRA_MEDIA_URL, resolved.mediaUrl)
                        .putExtra(VideoDetailActivity.EXTRA_MEDIA_REFERER, resolved.requestReferer);
                try { intent.putExtra(PlayerActivity.EXTRA_USER_AGENT, WebSettings.getDefaultUserAgent(activity)); }
                catch (Exception ignored) { }
                try { intent.putExtra(PlayerActivity.EXTRA_COOKIES, CookieManager.getInstance().getCookie(resolved.mediaUrl)); }
                catch (Exception ignored) { }
                activity.startActivity(intent);
            });
        });
    }
}
