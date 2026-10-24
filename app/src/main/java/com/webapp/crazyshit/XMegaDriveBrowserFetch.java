package com.webapp.crazyshit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One bounded browser load for XMegaDrive when a direct request only gets the edge challenge.
 * The rendered listing is what Shows parses. This does not run for a normal HTML response.
 */
final class XMegaDriveBrowserFetch {
    private static final long TIMEOUT_SECONDS = 20L;
    private static final int MAX_EXTRACTION_ATTEMPTS = 12;
    private static final long EXTRACTION_RETRY_MS = 800L;

    private XMegaDriveBrowserFetch() {
    }

    static Document fetch(Context context, String url, String userAgent, String referer)
            throws IOException {
        Activity activity = activity(context);
        if (activity == null) {
            throw new IOException("XMegaDrive browser fallback has no active screen");
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IOException("XMegaDrive browser fallback cannot block the main thread");
        }

        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Document> result = new AtomicReference<>();
        AtomicReference<IOException> error = new AtomicReference<>();
        AtomicReference<WebView> activeWebView = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean();
        Handler main = new Handler(Looper.getMainLooper());

        main.post(() -> start(
                activity,
                url,
                userAgent,
                referer,
                activeWebView,
                completed,
                result,
                error,
                finished
        ));
        try {
            if (!finished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                error.compareAndSet(null, new IOException("XMegaDrive browser fallback timed out"));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("XMegaDrive browser fallback was interrupted", interrupted);
        } finally {
            completed.set(true);
            main.post(() -> destroy(activeWebView.getAndSet(null)));
        }

        Document page = result.get();
        if (page != null) return page;
        IOException failure = error.get();
        throw failure == null
                ? new IOException("XMegaDrive browser returned no readable page")
                : failure;
    }

    private static Activity activity(Context context) {
        Context current = context;
        while (current instanceof android.content.ContextWrapper) {
            if (current instanceof Activity) return (Activity) current;
            Context next = ((android.content.ContextWrapper) current).getBaseContext();
            if (next == current) break;
            current = next;
        }
        return current instanceof Activity ? (Activity) current : null;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private static void start(
            Activity activity,
            String url,
            String userAgent,
            String referer,
            AtomicReference<WebView> activeWebView,
            AtomicBoolean completed,
            AtomicReference<Document> result,
            AtomicReference<IOException> error,
            CountDownLatch finished
    ) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            complete(completed, error, finished,
                    new IOException("XMegaDrive screen closed before loading"));
            return;
        }
        try {
            CookieManager cookies = CookieManager.getInstance();
            cookies.setAcceptCookie(true);
            WebView webView = new WebView(activity);
            activeWebView.set(webView);
            cookies.setAcceptThirdPartyCookies(webView, true);
            webView.setBackgroundColor(Color.TRANSPARENT);
            webView.setVisibility(android.view.View.INVISIBLE);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setBlockNetworkImage(true);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            if (userAgent != null && !userAgent.isEmpty()) {
                settings.setUserAgentString(userAgent);
            }
            webView.setWebViewClient(new WebViewClient() {
                private boolean extractionStarted;

                @Override
                public void onPageFinished(WebView view, String finalUrl) {
                    if (extractionStarted || completed.get()) return;
                    extractionStarted = true;
                    view.postDelayed(
                            () -> extract(view, 1, completed, result, error, finished),
                            EXTRACTION_RETRY_MS
                    );
                }
            });
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(1, 1);
            params.gravity = Gravity.BOTTOM | Gravity.END;
            activity.addContentView(webView, params);
            java.util.HashMap<String, String> headers = new java.util.HashMap<>();
            headers.put("Accept-Language", "en-US,en;q=0.9");
            if (referer != null && !referer.trim().isEmpty()) headers.put("Referer", referer);
            webView.loadUrl(url, headers);
        } catch (Exception failure) {
            complete(completed, error, finished,
                    new IOException("XMegaDrive browser fallback could not start", failure));
        }
    }

    private static void extract(
            WebView webView,
            int attempt,
            AtomicBoolean completed,
            AtomicReference<Document> result,
            AtomicReference<IOException> error,
            CountDownLatch finished
    ) {
        if (completed.get()) return;
        String script = "(() => {"
                + "const attrs=['href','src','data-src','data-original','data-lazy-src','poster','title','alt'];"
                + "const pick=e=>{const o={tag:e.tagName.toLowerCase()};"
                + "for (const k of attrs){const v=e.getAttribute(k); if(v) o[k]=v;}"
                + "if(e.tagName==='A'){const img=e.querySelector('img');"
                + "if(img){o.img={}; for (const k of attrs){const v=img.getAttribute(k); if(v) o.img[k]=v;}}"
                + "const strong=e.querySelector('strong.title'); if(strong) o.label=strong.textContent||'';}"
                + "if(e.tagName==='H1') o.text=e.textContent||'';"
                + "return o;};"
                + "const nodes=Array.from(document.querySelectorAll('a[href],video,source,h1')).slice(0,1500).map(pick);"
                + "return JSON.stringify({url:location.href,title:document.title||'',"
                + "text:(document.body&&document.body.innerText||'').slice(0,500),nodes});})()";
        webView.evaluateJavascript(script, encoded -> {
            if (completed.get()) return;
            try {
                Object decoded = new JSONTokener(encoded == null ? "null" : encoded).nextValue();
                if (!(decoded instanceof String)) {
                    throw new IOException("XMegaDrive browser returned invalid page data");
                }
                JSONObject payload = new JSONObject((String) decoded);
                String title = payload.optString("title");
                String body = payload.optString("text");
                String finalUrl = payload.optString("url", webView.getUrl());
                Document document = documentFrom(payload, finalUrl);
                if (ready(document, title, body) || attempt >= MAX_EXTRACTION_ATTEMPTS) {
                    try {
                        CookieManager.getInstance().flush();
                    } catch (Exception ignored) {
                    }
                    result.set(document);
                    complete(completed, error, finished, null);
                    return;
                }
                webView.postDelayed(
                        () -> extract(webView, attempt + 1, completed, result, error, finished),
                        EXTRACTION_RETRY_MS
                );
            } catch (Exception failure) {
                if (attempt < MAX_EXTRACTION_ATTEMPTS) {
                    webView.postDelayed(
                            () -> extract(webView, attempt + 1, completed, result, error, finished),
                            EXTRACTION_RETRY_MS
                    );
                } else {
                    complete(completed, error, finished, new IOException(
                            "XMegaDrive browser response could not be read", failure));
                }
            }
        });
    }

    private static Document documentFrom(JSONObject payload, String finalUrl) {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><title>")
                .append(Entities.escape(payload.optString("title")))
                .append("</title></head><body>");
        JSONArray nodes = payload.optJSONArray("nodes");
        if (nodes != null) {
            for (int index = 0; index < nodes.length(); index++) {
                JSONObject node = nodes.optJSONObject(index);
                if (node == null) continue;
                String tag = node.optString("tag");
                if ("a".equals(tag)) {
                    html.append("<a href=\"")
                            .append(Entities.escape(node.optString("href")))
                            .append("\" title=\"")
                            .append(Entities.escape(node.optString("title")))
                            .append("\">");
                    JSONObject image = node.optJSONObject("img");
                    if (image != null) {
                        html.append("<img src=\"")
                                .append(Entities.escape(image.optString("src")))
                                .append("\" data-original=\"")
                                .append(Entities.escape(image.optString("data-original")))
                                .append("\" data-src=\"")
                                .append(Entities.escape(image.optString("data-src")))
                                .append("\" alt=\"")
                                .append(Entities.escape(image.optString("alt")))
                                .append("\">");
                    }
                    String label = node.optString("label");
                    if (!label.isEmpty()) {
                        html.append("<strong class=\"title\">")
                                .append(Entities.escape(label))
                                .append("</strong>");
                    }
                    html.append("</a>");
                } else if ("video".equals(tag) || "source".equals(tag)) {
                    html.append("<").append(tag)
                            .append(" src=\"")
                            .append(Entities.escape(node.optString("src")))
                            .append("\"></").append(tag).append(">");
                } else if ("h1".equals(tag)) {
                    html.append("<h1>")
                            .append(Entities.escape(node.optString("text")))
                            .append("</h1>");
                }
            }
        }
        html.append("</body></html>");
        return Jsoup.parse(html.toString(), finalUrl == null ? "" : finalUrl);
    }

    private static boolean ready(Document document, String title, String body) {
        if (XMegaDriveRepository.looksBlocked(document)) return false;
        String heading = title == null ? "" : title.toLowerCase(java.util.Locale.US);
        String text = body == null ? "" : body.toLowerCase(java.util.Locale.US);
        if (heading.contains("just a moment") || text.contains("checking your browser")) return false;
        if (!document.select("a[href*=/categories/], a[href*=/videos/]").isEmpty()) return true;
        return !document.select("video[src*=.mp4], source[src*=.mp4], [src*=.mp4]").isEmpty();
    }

    private static void complete(
            AtomicBoolean completed,
            AtomicReference<IOException> error,
            CountDownLatch finished,
            IOException failure
    ) {
        if (!completed.compareAndSet(false, true)) return;
        if (failure != null) error.compareAndSet(null, failure);
        finished.countDown();
    }

    private static void destroy(WebView webView) {
        if (webView == null) return;
        try {
            webView.stopLoading();
            ViewParent parent = webView.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(webView);
            webView.destroy();
        } catch (Exception ignored) {
        }
    }
}
