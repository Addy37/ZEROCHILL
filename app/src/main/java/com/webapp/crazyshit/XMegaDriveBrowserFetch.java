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

import androidx.webkit.UserAgentMetadata;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads an XMegaDrive page in the real WebView when a direct request cannot.
 * The view stays full size because a hidden client is rejected. Its identity is this
 * device's Chrome, not the embedded-WebView tag, so the site's own check can finish.
 */
final class XMegaDriveBrowserFetch {
    private static final long TIMEOUT_SECONDS = 45L;
    private static final long EXTRACTION_RETRY_MS = 800L;
    private static final Pattern CHROME_VERSION = Pattern.compile("Chrome/([0-9.]+)");
    private static final Pattern ANDROID_MODEL = Pattern.compile(
            "Android\\s+[0-9.]+;\\s+(.*?)\\s+Build/");
    private static volatile String sessionUserAgent = "";
    private static volatile boolean passedOnce;

    private XMegaDriveBrowserFetch() {
    }

    static String sessionUserAgent() {
        return sessionUserAgent == null ? "" : sessionUserAgent;
    }

    static boolean passedBrowserCheck() {
        return passedOnce && sessionUserAgent != null && !sessionUserAgent.isEmpty();
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
            if (passedOnce) {
                overlay.setVisibility(View.INVISIBLE);
                overlay.setClickable(false);
            }
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
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            settings.setLoadsImagesAutomatically(true);
            settings.setBlockNetworkImage(false);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            String agent = XMegaDriveRepository.browserIdentity(settings.getUserAgentString());
            if (!agent.isEmpty()) {
                settings.setUserAgentString(agent);
                sessionUserAgent = agent;
                applyClientHints(settings, agent);
            }
            if (!passedOnce) clearChallengeCookies();
            AtomicBoolean reloaded = new AtomicBoolean();
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Accept-Language", "en-US,en;q=0.9");
            if (referer != null && !referer.trim().isEmpty()) headers.put("Referer", referer);
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String finalUrl) {
                    if (completed.get()) return;
                    int seen = generation.incrementAndGet();
                    view.postDelayed(
                            () -> consider(
                                    view,
                                    overlay,
                                    url,
                                    headers,
                                    seen,
                                    generation,
                                    reloaded,
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
            if (!passedOnce) webView.requestFocus();
            webView.loadUrl(url, headers);
        } catch (Exception failure) {
            complete(completed, error, finished,
                    new IOException("XMegaDrive browser check could not start", failure));
        }
    }

    private static void consider(
            WebView webView,
            FrameLayout overlay,
            String requestedUrl,
            HashMap<String, String> headers,
            int seen,
            AtomicInteger generation,
            AtomicBoolean reloaded,
            AtomicBoolean completed,
            AtomicReference<Document> result,
            AtomicReference<IOException> error,
            CountDownLatch finished
    ) {
        if (completed.get() || seen != generation.get()) return;
        String titleNow = webView.getTitle() == null ? "" : webView.getTitle();
        if (isInterstitial(titleNow, "", webView.getUrl())) {
            reveal(overlay, webView);
            webView.postDelayed(
                    () -> consider(
                            webView,
                            overlay,
                            requestedUrl,
                            headers,
                            seen,
                            generation,
                            reloaded,
                            completed,
                            result,
                            error,
                            finished
                    ),
                    EXTRACTION_RETRY_MS
            );
            return;
        }
        String probe = "(() => JSON.stringify({title:document.title||'',"
                + "text:((document.body&&document.body.innerText)||'').slice(0,400)}))()";
        webView.evaluateJavascript(probe, encoded -> {
            if (completed.get() || seen != generation.get()) return;
            String title = webView.getTitle() == null ? "" : webView.getTitle();
            String text = "";
            try {
                Object decoded = new JSONTokener(encoded == null ? "null" : encoded).nextValue();
                if (decoded instanceof String) {
                    JSONObject json = new JSONObject((String) decoded);
                    if (!json.optString("title").isEmpty()) title = json.optString("title");
                    text = json.optString("text");
                }
            } catch (Exception ignored) {
            }
            String location = webView.getUrl();
            if (isInterstitial(title, text, location)) {
                reveal(overlay, webView);
                webView.postDelayed(
                        () -> consider(
                                webView,
                                overlay,
                                requestedUrl,
                                headers,
                                seen,
                                generation,
                                reloaded,
                                completed,
                                result,
                                error,
                                finished
                        ),
                        EXTRACTION_RETRY_MS
                );
                return;
            }
            if (isForbidden(title, text)) {
                reveal(overlay, webView);
                if (reloaded.compareAndSet(false, true)) {
                    setLabel(overlay, "That check was blocked. One more try.");
                    clearChallengeCookies();
                    webView.loadUrl(requestedUrl, headers);
                    return;
                }
                complete(completed, error, finished,
                        new IOException("XMegaDrive blocked this device"));
                return;
            }
            extract(
                    webView,
                    overlay,
                    requestedUrl,
                    seen,
                    generation,
                    1,
                    completed,
                    result,
                    error,
                    finished
            );
        });
    }

    private static void extract(
            WebView webView,
            FrameLayout overlay,
            String requestedUrl,
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
                + "const q=String.fromCharCode(34);"
                + "const abs=u=>{if(!u)return ''; try {return new URL(u, location.href).href;} catch(e){return '';}};"
                + "const bgOf=el=>{"
                + "if(!el)return '';"
                + "let raw='';"
                + "try {raw=(el.style&&el.style.backgroundImage)||'';} catch(e){raw='';}"
                + "if(!raw||raw==='none'){try {raw=getComputedStyle(el).backgroundImage||'';} catch(e){raw='';}}"
                + "const start=raw.indexOf('url('); if(start<0)return '';"
                + "let value=raw.slice(start+4); const end=value.lastIndexOf(')');"
                + "if(end>=0)value=value.slice(0,end); value=value.trim();"
                + "const quote=value.charAt(0);"
                + "if(quote==='\\''||quote===q){value=value.slice(1);"
                + "if(value.charAt(value.length-1)===quote)value=value.slice(0,value.length-1);}"
                + "return abs(value);};"
                + "const imgFields=(img,host)=>{"
                + "const o={};"
                + "if(img){['src','data-src','data-original','data-lazy-src','alt'].forEach(k=>{"
                + "const v=img.getAttribute(k); if(v)o[k]=v;});"
                + "const current=img.currentSrc||img.src||'';"
                + "if(current && current.indexOf('data:')!==0)o.src=abs(current);}"
                + "const bg=bgOf(img)||bgOf(host)||bgOf(img&&img.parentElement)||bgOf(host&&host.querySelector('.img'));"
                + "if(bg)o['data-bg']=bg; return o;};"
                + "const pick=e=>{"
                + "const o={tag:e.tagName.toLowerCase()};"
                + "const href=e.getAttribute('href'); if(href)o.href=href;"
                + "const title=e.getAttribute('title'); if(title)o.title=title;"
                + "if(e.tagName==='A'){"
                + "const img=e.querySelector('img');"
                + "const box=e.querySelector('.img')||e;"
                + "const info=imgFields(img, box);"
                + "if(info.src||info['data-original']||info['data-src']||info['data-bg']||info['data-lazy-src'])o.img=info;"
                + "const strong=e.querySelector('strong.title'); if(strong)o.label=strong.textContent||'';}"
                + "if(e.tagName==='VIDEO'||e.tagName==='SOURCE'){"
                + "const src=e.getAttribute('src')||e.src||''; if(src)o.src=src;}"
                + "if(e.tagName==='H1')o.text=e.textContent||'';"
                + "return o;};"
                + "const bodyText=(document.body&&document.body.innerText)||'';"
                + "const age=/age confirmation/i.test(bodyText);"
                + "if(age){const controls=document.querySelectorAll('button,input,a');"
                + "for(let i=0;i<controls.length;i++){const el=controls[i];"
                + "const label=(el.innerText||el.value||el.getAttribute('value')||'');"
                + "if(/continue/i.test(label)){el.click(); break;}}}"
                + "const scripts=[]; const list=document.scripts||[];"
                + "for(let i=0;i<list.length&&scripts.length<3;i++){"
                + "const t=list[i].textContent||'';"
                + "if(t.indexOf('video_url')>=0||t.indexOf('license_code')>=0||t.indexOf('/get_file/')>=0){"
                + "scripts.push(t.slice(0,12000));}}"
                + "const nodes=Array.from(document.querySelectorAll('a[href],video,source,h1')).slice(0,1500).map(pick);"
                + "return JSON.stringify({url:location.href,title:document.title||'',"
                + "text:bodyText.slice(0,1500),age:age,nodes:nodes,scripts:scripts});})()";
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
                if (payload.optBoolean("age")) body = "Age confirmation\n" + body;
                String finalUrl = payload.optString("url");
                if (finalUrl == null || finalUrl.trim().isEmpty()) finalUrl = requestedUrl;
                Document document = documentFrom(payload, finalUrl);
                String pageUrl = requestedUrl == null || requestedUrl.trim().isEmpty()
                        ? finalUrl
                        : requestedUrl;
                if (ready(document, title, body, pageUrl, attempt)) {
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
                boolean age = payload.optBoolean("age")
                        || (body != null && body.toLowerCase(java.util.Locale.US).contains("age confirmation"));
                if (overlay != null && (attempt >= 5 || age)) {
                    overlay.setVisibility(View.VISIBLE);
                    overlay.setClickable(true);
                    if (age && overlay.getChildCount() > 0 && overlay.getChildAt(0) instanceof TextView) {
                        ((TextView) overlay.getChildAt(0)).setText(
                                "XMegaDrive needs a quick check. Tap Continue if it stays open.");
                    }
                }
                webView.postDelayed(
                        () -> extract(
                                webView,
                                overlay,
                                requestedUrl,
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
                                requestedUrl,
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
                                .append("\" data-lazy-src=\"")
                                .append(Entities.escape(image.optString("data-lazy-src")))
                                .append("\" data-bg=\"")
                                .append(Entities.escape(image.optString("data-bg")))
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
        if (payload.optBoolean("age")) html.append("<p>Age confirmation</p>");
        JSONArray scripts = payload.optJSONArray("scripts");
        if (scripts != null) {
            for (int index = 0; index < scripts.length(); index++) {
                String script = rawScript(scripts.optString(index));
                if (script.isEmpty()) continue;
                html.append("<script>").append(script).append("</script>");
            }
        }
        html.append("</body></html>");
        return Jsoup.parse(html.toString(), finalUrl == null ? "" : finalUrl);
    }

    private static String rawScript(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.replace("</", "<\\/");
    }

    private static boolean ready(Document document, String title, String body, String pageUrl, int attempt) {
        if (document == null || XMegaDriveRepository.looksBlocked(document)) return false;
        String heading = title == null ? "" : title.toLowerCase(java.util.Locale.US);
        String text = body == null ? "" : body.toLowerCase(java.util.Locale.US);
        if (heading.contains("just a moment")
                || heading.contains("attention required")
                || text.contains("checking your browser")
                || text.contains("age confirmation")
                || (document.text() != null
                && document.text().toLowerCase(java.util.Locale.US).contains("age confirmation"))) {
            return false;
        }
        if (XMegaDriveRepository.isPlayablePage(pageUrl)) {
            return !XMegaDriveRepository.mediaFromHtml(document.html(), pageUrl).isEmpty();
        }
        if (document.select("a[href*=/categories/], a[href*=/videos/]").isEmpty()) {
            return !XMegaDriveRepository.mediaFromHtml(document.html(), pageUrl).isEmpty();
        }
        return hasArtwork(document) || attempt >= 8;
    }

    private static boolean hasArtwork(Document document) {
        for (org.jsoup.nodes.Element image : document.select("img")) {
            String[] values = {
                    image.attr("data-bg"),
                    image.attr("data-original"),
                    image.attr("data-src"),
                    image.attr("data-lazy-src"),
                    image.attr("src")
            };
            for (String value : values) {
                String lower = value == null ? "" : value.toLowerCase(java.util.Locale.US);
                if (lower.contains("blank.gif") || lower.contains("placeholder")
                        || lower.contains("noimage") || lower.contains("no-thumb")
                        || lower.contains("no_image") || lower.endsWith(".gif")) {
                    continue;
                }
                if (lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png")
                        || lower.contains(".webp") || lower.contains("/contents/")
                        || lower.contains("videos_screenshots")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void applyClientHints(WebSettings settings, String agent) {
        try {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) return;
            Matcher matcher = CHROME_VERSION.matcher(agent == null ? "" : agent);
            if (!matcher.find()) return;
            String full = matcher.group(1);
            String major = full.contains(".") ? full.substring(0, full.indexOf('.')) : full;
            List<UserAgentMetadata.BrandVersion> brands = new ArrayList<>();
            brands.add(brand("Chromium", major, full));
            brands.add(brand("Google Chrome", major, full));
            brands.add(brand("Not_A Brand", "24", "24.0.0.0"));
            UserAgentMetadata.Builder metadata = new UserAgentMetadata.Builder()
                    .setBrandVersionList(brands)
                    .setMobile(true)
                    .setPlatform("Android");
            Matcher model = ANDROID_MODEL.matcher(agent);
            if (model.find() && !model.group(1).trim().isEmpty()) {
                metadata.setModel(model.group(1).trim());
            }
            WebSettingsCompat.setUserAgentMetadata(settings, metadata.build());
        } catch (Throwable ignored) {
        }
    }

    private static UserAgentMetadata.BrandVersion brand(String name, String major, String full) {
        return new UserAgentMetadata.BrandVersion.Builder()
                .setBrand(name)
                .setMajorVersion(major)
                .setFullVersion(full)
                .build();
    }

    private static void clearChallengeCookies() {
        CookieManager manager = CookieManager.getInstance();
        String[] urls = {"https://www.xmegadrive.com/", "https://xmegadrive.com/"};
        String[] names = {"cf_clearance", "__cf_bm", "cf_chl_2", "cf_chl_prog"};
        for (String page : urls) {
            for (String name : names) {
                manager.setCookie(page, name + "=; Max-Age=0; Path=/; Secure");
            }
        }
        try {
            manager.flush();
        } catch (Exception ignored) {
        }
    }

    private static boolean isInterstitial(String title, String text, String url) {
        String blob = ((title == null ? "" : title) + "\n" + (text == null ? "" : text))
                .toLowerCase(Locale.US);
        String location = url == null ? "" : url.toLowerCase(Locale.US);
        return blob.contains("just a moment")
                || blob.contains("checking your browser")
                || blob.contains("attention required")
                || blob.contains("verify you are human")
                || location.contains("/cdn-cgi/");
    }

    private static boolean isForbidden(String title, String text) {
        String heading = title == null ? "" : title.toLowerCase(Locale.US);
        String body = text == null ? "" : text.toLowerCase(Locale.US);
        return heading.contains("forbidden")
                || heading.contains("access denied")
                || body.contains("sorry, you have been blocked")
                || body.contains("error 1020")
                || body.contains("error 1015");
    }

    private static void reveal(FrameLayout overlay, WebView webView) {
        if (overlay != null) {
            overlay.setVisibility(View.VISIBLE);
            overlay.setClickable(true);
        }
        if (webView != null) webView.requestFocus();
    }

    private static void setLabel(FrameLayout overlay, String message) {
        if (overlay == null || overlay.getChildCount() == 0) return;
        if (overlay.getChildAt(0) instanceof TextView) {
            ((TextView) overlay.getChildAt(0)).setText(message);
        }
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
