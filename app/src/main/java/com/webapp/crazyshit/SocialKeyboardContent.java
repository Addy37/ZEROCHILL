package com.webapp.crazyshit;

import android.app.Activity;
import android.content.ClipData;
import android.net.Uri;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.widget.EditText;
import androidx.core.view.ContentInfoCompat;
import androidx.core.view.ViewCompat;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Opt-in receive-content and the narrowly allowlisted keyboard URL fallback. */
final class SocialKeyboardContent {
    private static final String[] TYPES = {"image/gif", "image/jpeg", "image/png", "image/webp"};
    private static final Pattern URL_TOKEN = Pattern.compile("https://[^\\s<>\\\"']+", Pattern.CASE_INSENSITIVE);
    private final Activity activity;
    private final EditText input;
    private final SocialMedia.Composer composer;
    private final BooleanSupplier allowed;
    private final Executor work, main;
    private final RemoteGif remote;
    // Keep the IME permission grant strongly reachable until background reading has finished.
    private ContentInfoCompat pendingContent;

    interface RemoteGif { SocialMedia.Prepared load(String url) throws Exception; }

    SocialKeyboardContent(Activity activity, EditText input, SocialMedia.Composer composer,
                          BooleanSupplier allowed) {
        this(activity, input, composer, allowed, SocialMedia.WORK, activity::runOnUiThread,
                url -> download(activity, url, value -> (HttpURLConnection) new URL(value).openConnection()));
    }

    SocialKeyboardContent(Activity activity, EditText input, SocialMedia.Composer composer,
                          BooleanSupplier allowed, Executor work, Executor main, RemoteGif remote) {
        this.activity = activity;
        this.input = input;
        this.composer = composer;
        this.allowed = allowed;
        this.work = work;
        this.main = main;
        this.remote = remote;
    }

    void install() {
        ViewCompat.setOnReceiveContentListener(input, TYPES, (view, payload) -> {
            // Partition so ordinary pasted captions and unsupported items retain Android behavior.
            android.util.Pair<ContentInfoCompat, ContentInfoCompat> parts = payload.partition(item ->
                    item.getUri() != null && "content".equals(item.getUri().getScheme())
                            && supported(payload));
            if (parts.first == null) return payload;
            ClipData clip = parts.first.getClip();
            // This composer has one slot, just like the existing single-selection picker.
            Uri uri = clip.getItemAt(0).getUri();
            ContentInfoCompat retained = parts.first;
            prepare(() -> {
                pendingContent = retained;
                return SocialMedia.prepare(activity, uri);
            }, () -> pendingContent == retained, () -> {}, () -> {
                if (pendingContent == retained) pendingContent = null;
            });
            return parts.second;
        });
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable text) { convertUrl(text); }
        });
    }

    private static boolean supported(ContentInfoCompat payload) {
        for (String mime : TYPES) if (payload.getClip().getDescription().hasMimeType(mime)) return true;
        return false;
    }

    private boolean available() {
        return !activity.isDestroyed() && input.isEnabled() && allowed.getAsBoolean()
                && ZeroChillAccountRepository.hasStoredSession(activity);
    }

    private void convertUrl(Editable text) {
        if (!available() || composer.busy || composer.preparing) return;
        Matcher matcher = URL_TOKEN.matcher(text);
        while (matcher.find()) {
            String url = matcher.group();
            if (!supportedUrl(url)) continue;
            // A span follows caption edits while the download runs. Never delete a stale range.
            Object marker = new Object();
            text.setSpan(marker, matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            BooleanSupplier unchanged = () -> {
                int start = text.getSpanStart(marker), end = text.getSpanEnd(marker);
                return input.getText() == text && start >= 0 && end >= start
                        && url.contentEquals(text.subSequence(start, end));
            };
            prepare(() -> remote.load(url), unchanged, () -> {
                int start = text.getSpanStart(marker), end = text.getSpanEnd(marker);
                text.delete(start, end);
            }, () -> text.removeSpan(marker));
            return;
        }
    }

    private void prepare(Callable<SocialMedia.Prepared> source, BooleanSupplier valid, Runnable converted,
                         Runnable finished) {
        if (!available() || composer.busy || composer.preparing) {
            input.setError("Unable to attach now. Sign in and wait for the current image or message to finish.");
            finished.run();
            return;
        }
        String owner = ZeroChillSessionStore.currentUserId(activity);
        int generation = ++composer.preparationGeneration;
        composer.preparing = true;
        composer.refresh();
        work.execute(() -> {
            SocialMedia.Prepared media = null;
            Exception failure = null;
            try { media = source.call(); } catch (Exception e) { failure = e; }
            SocialMedia.Prepared result = media;
            Exception error = failure;
            main.execute(() -> {
                // The span belongs to the old Editable and must also be removed after cancellation.
                if (generation != composer.preparationGeneration) { finished.run(); return; }
                if (available() && owner.equals(ZeroChillSessionStore.currentUserId(activity)) && valid.getAsBoolean()) {
                    if (error == null) {
                        composer.select(result);
                        converted.run();
                    } else input.setError("Could not attach GIF/image. "
                            + (error instanceof IllegalArgumentException ? error.getMessage() : "Please try again."));
                }
                finished.run();
                composer.preparing = false;
                composer.refresh();
            });
        });
    }

    static boolean supportedUrl(String value) {
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "static.klipy.com".equalsIgnoreCase(uri.getHost())
                    && uri.getRawUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getRawFragment() == null && uri.getRawPath() != null
                    && uri.getRawPath().toLowerCase(java.util.Locale.ROOT).endsWith(".gif");
        } catch (Exception ignored) { return false; }
    }

    interface ConnectionFactory { HttpURLConnection open(String url) throws Exception; }

    static SocialMedia.Prepared download(android.content.Context context, String url,
                                         ConnectionFactory connections) throws Exception {
        if (!supportedUrl(url)) throw new IllegalArgumentException("This GIF host is not supported.");
        HttpURLConnection connection = connections.open(url);
        try {
            // Reject every redirect, including redirects back to this CDN. No auth/session headers.
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(20_000);
            connection.setRequestProperty("Accept", "image/gif");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                throw new IllegalArgumentException("The GIF could not be downloaded. Try another GIF.");
            if (connection.getContentLengthLong() > SocialMedia.GIF_LIMIT)
                throw new IllegalArgumentException("This GIF is too large. Maximum size is 10 MB.");
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[16384];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (out.size() + count > SocialMedia.GIF_LIMIT)
                        throw new IllegalArgumentException("This GIF is too large. Maximum size is 10 MB.");
                    out.write(buffer, 0, count);
                }
                return SocialMedia.prepareBytes(context, out.toByteArray(), "image/gif");
            }
        } finally { connection.disconnect(); }
    }
}
