package com.webapp.crazyshit;

import android.content.Context;
import android.net.Uri;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** The same source headers, cookies, media identity and disk cache for playback and metadata. */
@UnstableApi
final class ShitTokMediaSources {
    private ShitTokMediaSources() {}

    static MediaSource create(Context context, String url, CrazyShitRepository.StreamInfo stream) {
        DataSource.Factory original = factory(context, stream, null);
        DataSource.Factory observed = () -> {
            DataSource source = original.createDataSource();
            source.addTransferListener(new TransferListener() {
                private boolean opening, started, received;

                @Override public void onTransferInitializing(DataSource source, androidx.media3.datasource.DataSpec spec, boolean network) {
                    if (!opening) { opening = true; AppPerformance.startupItem("media_open_start", url, "network=" + network); }
                }
                @Override public void onTransferStart(DataSource source, androidx.media3.datasource.DataSpec spec, boolean network) {
                    if (!started) { started = true; AppPerformance.startupItem("media_open_end", url, "network=" + network); }
                }
                @Override public void onBytesTransferred(DataSource source, androidx.media3.datasource.DataSpec spec, boolean network, int bytes) {
                    if (!received) { received = true; AppPerformance.startupItem("media_bytes", url, "network=" + network); }
                }
                @Override public void onTransferEnd(DataSource source, androidx.media3.datasource.DataSpec spec, boolean network) { }
            });
            return source;
        };
        return new DefaultMediaSourceFactory(context).setDataSourceFactory(observed)
                .createMediaSource(mediaItem(url, stream));
    }

    static MediaItem mediaItem(String url, CrazyShitRepository.StreamInfo stream) {
        MediaItem.Builder media = new MediaItem.Builder().setMediaId(url).setUri(stream.mediaUrl);
        String lower = stream.mediaUrl.toLowerCase(Locale.US);
        if (lower.contains(".m3u8")) media.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if (lower.contains(".mpd")) media.setMimeType(MimeTypes.APPLICATION_MPD);
        else if (lower.contains(".mp4") || lower.contains(".m4v")) media.setMimeType(MimeTypes.VIDEO_MP4);
        return media.build();
    }

    static DataSource.Factory factory(Context context, CrazyShitRepository.StreamInfo stream,
                                     TransferListener probeListener) {
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory();
        try { http.setUserAgent(WebSettings.getDefaultUserAgent(context)); } catch (Exception ignored) {}
        Map<String, String> headers = new HashMap<>();
        try { headers.putAll(ShitShowPlayableResolver.playbackHeaders(stream.mediaUrl)); }
        catch (Exception ignored) {}
        if (stream.pageUrl != null && !stream.pageUrl.isEmpty()) {
            put(headers, "Referer", stream.requestReferer);
            try {
                Uri page = Uri.parse(stream.pageUrl);
                if (page.getScheme() != null && page.getHost() != null)
                    put(headers, "Origin", page.getScheme() + "://" + page.getHost());
            } catch (Exception ignored) {}
        }
        try {
            String cookie = CookieManager.getInstance().getCookie(stream.mediaUrl);
            if (cookie == null || cookie.isEmpty()) cookie = CookieManager.getInstance().getCookie(stream.pageUrl);
            put(headers, "Cookie", cookie);
        } catch (Exception ignored) {}
        http.setDefaultRequestProperties(headers);
        if (probeListener != null) {
            // Separate probe factory: playback keeps its existing default HTTP timeouts.
            http.setConnectTimeoutMs(900).setReadTimeoutMs(900).setTransferListener(probeListener);
        }
        return ShitTokMediaCache.wrap(context, http);
    }

    private static void put(Map<String, String> headers, String name, String value) {
        if (value == null || value.isEmpty()) return;
        for (String existing : headers.keySet()) if (existing.equalsIgnoreCase(name)) return;
        headers.put(name, value);
    }
}
