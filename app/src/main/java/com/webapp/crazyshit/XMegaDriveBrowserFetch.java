package com.webapp.crazyshit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loads an XMegaDrive page in the real WebView when a direct request only gets the edge check.
 * The view is full size because a 1 pixel hidden client is rejected. If the check needs a tap,
 * it stays on screen until the listing is readable, then it closes.
 */
final class XMegaDriveBrowserFetch {
    private static final long TIMEOUT_SECONDS = 45L;
    private static final long EXTRACTION_RETRY_MS = 800L;
    private static volatile String sessionUserAgent = "";
    private static volatile boolean passedOnce;

    private XMegaDriveBrowserFetch() {
    }

    static String sessionUserAgent() {
        return sessionUserAgent == null ? "" : sessionUserAgent;
    }

    static Document fetch(Context context, String url, String referer) throws IOException {
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
        AtomicReference<FrameLayout> activeOverlay = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean();
        AtomicInteger generation = new AtomicInteger();
        Handler main = new Handler(Looper.getMainLooper());

        main.post(() -> start(
                activity,
                url,
                referer,
                activeWebView,
                activeOverlay,
                generation,
                completed,
                result,
                error,
                finished
        ));
        try {
            if (!finished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                error.compareAndSet(null, new IOException("XMegaDrive browser check timed out"));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("XMegaDrive browser check was interrupted", interrupted);
        } finally {
            completed.set(true);
            WebView webView = activeWebView.getAndSet(null);
            FrameLayout overlay = activeOverlay.getAndSet(null);
            main.post(() -> destroy(overlay, webView));
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
            String referer,
            AtomicReference<WebView> activeWebView,
            AtomicReference<FrameLayout> activeOverlay,
            AtomicInteger generation,
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
            FrameLayout overlay = new FrameLayout(activity);
            overlay.setBackgroundColor(0xFF101014);
            overlay.setClickable(true);
            overlay.setElevation(80f);
            if (passedOnce) overlay.setVisibility(View.INVISIBLE);
            activeOverlay.set(overlay);

            TextView label = new TextView(activity);
            label.setText("XMegaDrive needs a quick check. Finish it if a box appears.");
            label.setTextColor(Color.WHITE);
            label.setTextSize(15f);
            int pad = dp(activity, 16);
            label.setPadding(pad, pad, dp(activity, 88), pad);
            overlay.addView(label, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(activity, 72)
            ));

            TextView dismiss = new TextView(activity);
            dismiss.setText("Not now");
            dismiss.setTextColor(Color.WHITE);
            dismiss.setTextSize(15f);
            dismiss.setPadding(pad, pad, pad, pad);
            dismiss.setOnClickListener(view -> complete(
                    completed,
                    error,
                    finished,
                    new IOException("XMegaDrive check was dismissed")
            ));
            FrameLayout.LayoutParams dismissParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            dismissParams.gravity = Gravity.END;
            overlay.addView(dismiss, dismissParams);

            WebView webView = new WebView(activity);
            activeWebView.set(webView);
            cookies.setAcceptThirdPartyCookies(webView, true);
            webView.setBackgroundColor(Color.WHITE);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            String agent = settings.getUserAgentString();
            if (agent != null && !agent.trim().isEmpty()) sessionUserAgent = agent.trim();
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String finalUrl) {
                    if (completed.get()) return;
                    int seen = generation.incrementAndGet();
                    view.postDelayed(
                            () -> extract(
                                    view,
                                    overlay,
                                    seen,
                                    generation,
                                    1,
                                    completed,
                                    result,
                                    error,
                                    finished
                            ),
                            EXTRACTION_RETRY_MS
                    );
                }
            });
            FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            webParams.topMargin = dp(activity, 72);
            overlay.addView(webView, webParams);
            activity.addContentView(overlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            webView.requestFocus();
            java.util.HashMap<String, String> headers = new java.util.HashMap<>();
            headers.put("Accept-Language", "en-US,en;q=0.9");
            if (referer != null && !referer.trim().isEmpty()) headers.put("Referer", referer);
            webView.loadUrl(url, headers);
        } catch (Exception failure) {
            complete(completed, error, finished,
                    new IOException("XMegaDrive browser check could not start", failure));
        }
    }

    private static void extract(
            WebView webView,
            FrameLayout overlay,
            int seen,
            AtomicInteger generation,
            int attempt,
            AtomicBoolean completed,
            AtomicReference<Document> result,
            AtomicReference<IOException> error,
            CountDownLatch finished
    ) {
        if (completed.get() || seen != generation.get()) return;
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
            if (completed.get() || seen != generation.get()) return;
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
                if (ready(document, title, body)) {
                    try {
                        CookieManager.getInstance().flush();
                    } catch (Exception ignored) {
                    }
                    passedOnce = true;
                    result.set(document);
                    complete(completed, error, finished, null);
                    return;
                }
                if (permanentlyBlocked(title, body)) {
                    complete(completed, error, finished,
                            new IOException("XMegaDrive blocked this device"));
                    return;
                }
                if (overlay != null && attempt >= 5) overlay.setVisibility(View.VISIBLE);
                webView.postDelayed(
                        () -> extract(
                                webView,
                                overlay,
                                seen,
                                generation,
                                attempt + 1,
                                completed,
                                result,
                                error,
                                finished
                        ),
                        EXTRACTION_RETRY_MS
                );
            } catch (Exception ignored) {
                if (completed.get() || seen != generation.get()) return;
                webView.postDelayed(
                        () -> extract(
                                webView,
                                overlay,
                                seen,
                                generation,
                                attempt + 1,
                                completed,
                                result,
                                error,
                                finished
                        ),
                        EXTRACTION_RETRY_MS
                );
            }
        });
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
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

    private static boolean permanentlyBlocked(String title, String body) {
        String text = ((title == null ? "" : title) + "\n" + (body == null ? "" : body))
                .toLowerCase(java.util.Locale.US);
        return text.contains("sorry, you have been blocked")
                || text.contains("error 1020")
                || text.contains("error 1015");
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

    private static void destroy(FrameLayout overlay, WebView webView) {
        try {
            if (webView != null) {
                webView.stopLoading();
                webView.setWebViewClient(null);
            }
            if (overlay != null) {
                ViewParent parent = overlay.getParent();
                if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(overlay);
            } else if (webView != null) {
                ViewParent parent = webView.getParent();
                if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(webView);
            }
            if (webView != null) webView.destroy();
        } catch (Exception ignored) {
        }
    }
}
