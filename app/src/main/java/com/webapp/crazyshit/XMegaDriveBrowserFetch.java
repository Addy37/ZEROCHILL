package com.webapp.crazyshit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loads an XMegaDrive page in a full-size WebView and reads it there.
 * The WebView stays off-screen so a cold start does not cover the app. It is
 * only brought on-screen for a human/browser challenge. XMegaDrive age access is
 * handled by ZEROCHILL's explicit 18+ confirmation and the site's kt_agecheck marker.
 * The "; wv" token is removed from the user-agent string. Client hints are not
 * rewritten, because that does not make this WebView the same as Chrome.
 * cf_clearance and __cf_bm are left in place so a valid session is not discarded.
 */
final class XMegaDriveBrowserFetch {
    private static final String TAG = "XMegaDrive";
    private static final String POPUP_GUARD =
            "(function(){window.open=function(){return null;};})();";
    private static final long TIMEOUT_SECONDS = 45L;
    private static final long INITIAL_EXTRACTION_DELAY_MS = 200L;
    private static final long EXTRACTION_RETRY_MS = 800L;
    private static volatile String sessionUserAgent = "";

    private XMegaDriveBrowserFetch() {
    }

    static String sessionUserAgent() {
        return sessionUserAgent == null ? "" : sessionUserAgent;
    }

    static Document fetch(Context context, String url, String referer) throws IOException {
        // A cancelled directory request may have been waiting for another provider's browser lock.
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("XMegaDrive browser check was cancelled before loading");
        }
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
            // Read ownership on main: cancellation can race with start() creating the views.
            main.post(() -> {
                WebView webView = activeWebView.getAndSet(null);
                dismissAgePrompt(webView);
                destroy(activeOverlay.getAndSet(null), webView);
            });
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
        if (completed.get()) return;
        if (activity.isFinishing() || activity.isDestroyed()) {
            complete(completed, error, finished,
                    new IOException("XMegaDrive screen closed before loading"));
            return;
        }
        try {
            XMegaDriveAgeSession.restoreIfAuthorized(activity);
            CookieManager cookies = CookieManager.getInstance();
            cookies.setAcceptCookie(true);
            FrameLayout overlay = new FrameLayout(activity);
            overlay.setBackgroundColor(0xFF101014);
            overlay.setClickable(false);
            overlay.setFocusable(false);
            overlay.setElevation(80f);
            park(overlay);
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
            webView.setTag(new AgeGateState());
            activeWebView.set(webView);
            cookies.setAcceptThirdPartyCookies(webView, true);
            webView.setBackgroundColor(Color.WHITE);
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            // This browser only needs DOM/image URLs while parked off-screen. Avoid spending the
            // page-load critical path downloading thumbnails that the native UI will load later.
            settings.setBlockNetworkImage(true);
            settings.setLoadsImagesAutomatically(false);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            settings.setJavaScriptCanOpenWindowsAutomatically(false);
            settings.setSupportMultipleWindows(false);
            settings.setMediaPlaybackRequiresUserGesture(true);
            String rawAgent = settings.getUserAgentString();
            String agent = XMegaDriveRepository.browserIdentity(rawAgent);
            Log.i(TAG, "userAgent default=" + oneLine(rawAgent));
            Log.i(TAG, "userAgent applied=" + oneLine(agent));
            if (!agent.isEmpty()) {
                settings.setUserAgentString(agent);
                sessionUserAgent = agent;
            }
            Log.i(TAG, "session cleared=false " + cookieReport(url));
            AtomicBoolean identityLogged = new AtomicBoolean();
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Accept-Language", "en-US,en;q=0.9");
            if (referer != null && !referer.trim().isEmpty()) headers.put("Referer", referer);
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                    Log.w(TAG, "rendererGone crashed=" + (detail != null && detail.didCrash()));
                    complete(completed, error, finished,
                            new IOException("XMegaDrive browser renderer exited"));
                    // Remove this renderer's WebView even if completion already raced with cancel.
                    dismissAgePrompt(view);
                    destroy(activeOverlay.getAndSet(null), activeWebView.getAndSet(null));
                    return true;
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    if (request == null || request.getUrl() == null || !request.isForMainFrame()) {
                        return false;
                    }
                    return blockOffsite(request.getUrl().toString());
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return blockOffsite(url);
                }

                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    if (request != null && request.getUrl() != null
                            && XMegaDriveRepository.isPopunder(request.getUrl().toString())) {
                        return emptyResponse();
                    }
                    return super.shouldInterceptRequest(view, request);
                }

                @Override
                public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                    if (blockOffsite(url)) {
                        view.stopLoading();
                        return;
                    }
                    view.evaluateJavascript(POPUP_GUARD, null);
                }

                @Override
                public void onPageFinished(WebView view, String finalUrl) {
                    if (completed.get()) return;
                    int seen = generation.incrementAndGet();
                    logPage("finished", finalUrl, view.getTitle(), isInterstitial(
                            view.getTitle(), "", finalUrl));
                    view.postDelayed(
                            () -> consider(
                                    view,
                                    overlay,
                                    url,
                                    seen,
                                    generation,
                                    identityLogged,
                                    completed,
                                    result,
                                    error,
                                    finished
                            ),
                            INITIAL_EXTRACTION_DELAY_MS
                    );
                }

                @Override
                public void onReceivedHttpError(
                        WebView view,
                        WebResourceRequest request,
                        WebResourceResponse response
                ) {
                    if (request != null && !request.isForMainFrame()) return;
                    int status = response == null ? 0 : response.getStatusCode();
                    String reason = response == null || response.getReasonPhrase() == null
                            ? ""
                            : response.getReasonPhrase();
                    Log.i(TAG, "httpError status=" + status
                            + " reason=" + oneLine(reason)
                            + " url=" + redactUrl(request == null || request.getUrl() == null
                            ? ""
                            : request.getUrl().toString()));
                }

                @Override
                public void onReceivedError(
                        WebView view,
                        WebResourceRequest request,
                        WebResourceError error
                ) {
                    if (request != null && !request.isForMainFrame()) return;
                    CharSequence description = error == null ? "" : error.getDescription();
                    Log.i(TAG, "loadError code=" + (error == null ? 0 : error.getErrorCode())
                            + " desc=" + oneLine(description == null ? "" : description.toString())
                            + " url=" + redactUrl(request == null || request.getUrl() == null
                            ? ""
                            : request.getUrl().toString()));
                }
            });
            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onCreateWindow(
                        WebView view,
                        boolean isDialog,
                        boolean isUserGesture,
                        android.os.Message resultMsg
                ) {
                    Log.i(TAG, "blockedWindow");
                    return false;
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
            park(overlay);
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    WebViewCompat.addDocumentStartJavaScript(
                            webView,
                            POPUP_GUARD,
                            Collections.singleton("*")
                    );
                }
            } catch (Exception ignored) {
            }
            if (!completed.get()) webView.loadUrl(url, headers);
        } catch (Exception failure) {
            complete(completed, error, finished,
                    new IOException("XMegaDrive browser check could not start", failure));
        }
    }

    private static boolean blockOffsite(String url) {
        if (url == null || url.trim().isEmpty() || XMegaDriveRepository.allowsMainFrame(url)) {
            return false;
        }
        Log.i(TAG, "blockedNavigation host=" + hostOnly(url));
        return true;
    }

    private static String hostOnly(String url) {
        if (url == null) return "";
        try {
            String host = java.net.URI.create(url.trim()).getHost();
            return host == null ? "" : host;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static WebResourceResponse emptyResponse() {
        return new WebResourceResponse(
                "text/plain",
                "utf-8",
                new ByteArrayInputStream(new byte[0])
        );
    }

    private static void consider(
            WebView webView,
            FrameLayout overlay,
            String requestedUrl,
            int seen,
            AtomicInteger generation,
            AtomicBoolean identityLogged,
            AtomicBoolean completed,
            AtomicReference<Document> result,
            AtomicReference<IOException> error,
            CountDownLatch finished
    ) {
        if (completed.get() || seen != generation.get()) return;
        String titleNow = webView.getTitle() == null ? "" : webView.getTitle();
        String locationNow = webView.getUrl();
        boolean waiting = isInterstitial(titleNow, "", locationNow);
        logPage(waiting ? "interstitial" : "checking", locationNow, titleNow, waiting);
        if (identityLogged.compareAndSet(false, true)) logIdentity(webView);
        if (waiting) {
            if (needsPerson(titleNow, "")) {
                dismissAgePrompt(webView);
                reveal(overlay, webView);
            }
            webView.postDelayed(
                    () -> consider(
                            webView,
                            overlay,
                            requestedUrl,
                            seen,
                            generation,
                            identityLogged,
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
            if (isInterstitial(title, text, location) || isForbidden(title, text)) {
                boolean blocked = permanentlyBlocked(title, text);
                logPage(blocked ? "blocked" : "interstitial", location, title, true);
                if (needsPerson(title, text)) {
                    dismissAgePrompt(webView);
                    reveal(overlay, webView);
                }
                if (blocked) {
                    complete(completed, error, finished,
                            new IOException("XMegaDrive blocked this device"));
                    return;
                }
                webView.postDelayed(
                        () -> consider(
                                webView,
                                overlay,
                                requestedUrl,
                                seen,
                                generation,
                                identityLogged,
                                completed,
                                result,
                                error,
                                finished
                        ),
                        EXTRACTION_RETRY_MS
                );
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
                + "window.open=function(){return null;};"
                + "document.querySelectorAll('iframe,frame').forEach(frame=>{"
                + "const src=frame.getAttribute('src')||'';"
                + "if(!src||src.indexOf('about:')===0)return;"
                + "let host=''; try{host=new URL(src, location.href).hostname.toLowerCase();}catch(e){return;}"
                + "const ok=host==='xmegadrive.com'||host.endsWith('.xmegadrive.com')||host.endsWith('.cloudflare.com');"
                + "if(!ok)frame.remove();});"
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
                + "const bad=u=>!u||u.indexOf('data:')===0||u.indexOf('/cdn-cgi/challenge')>=0||u.indexOf('/cdn-cgi/images/')>=0||u.indexOf('/cdn-cgi/styles/')>=0;"
                + "const imgFields=(img,host)=>{"
                + "const o={};"
                + "if(img){['src','data-src','data-original','data-lazy-src','data-thumb','alt'].forEach(k=>{"
                + "const v=img.getAttribute(k); if(v)o[k]=v;});"
                + "const set=img.getAttribute('srcset')||img.getAttribute('data-srcset')||'';"
                + "if(set){const first=set.split(',')[0].trim().split(/\\s+/)[0]; if(first&&!o['data-src'])o['data-src']=abs(first);}"
                + "const current=img.currentSrc||img.src||'';"
                + "if(!bad(current))o.src=abs(current);}"
                + "let bg=bgOf(img)||bgOf(host)||bgOf(img&&img.parentElement);"
                + "if((!bg||bad(bg))&&host&&host.querySelectorAll){"
                + "const nodes=host.querySelectorAll('*');"
                + "for(let i=0;i<nodes.length&&i<24;i++){const found=bgOf(nodes[i]); if(found&&!bad(found)){bg=found; break;}}}"
                + "if(bg&&!bad(bg))o['data-bg']=bg; return o;};"
                + "const singleCard=e=>{"
                + "let node=e; let best=e;"
                + "for(let depth=0; depth<6 && node && node!==document.body && node!==document.documentElement; depth++){"
                + "const seen={}; let count=0;"
                + "const links=node.querySelectorAll?node.querySelectorAll('a[href*=\"/videos/\"],a[href*=\"/categories/\"]'):[];"
                + "for(let i=0;i<links.length;i++){"
                + "const key=(links[i].getAttribute('href')||'').split('?')[0].split('#')[0];"
                + "if(!key||seen[key])continue; seen[key]=1; count++; if(count>2)return best;}"
                + "best=node; node=node.parentElement;} return best;};"
                + "const pick=e=>{"
                + "const o={tag:e.tagName.toLowerCase()};"
                + "const href=e.getAttribute('href'); if(href)o.href=href;"
                + "const title=e.getAttribute('title'); if(title)o.title=title;"
                + "if(e.tagName==='A'){"
                + "const card=singleCard(e);"
                + "const img=e.querySelector('img')||(card&&card.querySelector('img'));"
                + "const box=e.querySelector('.img,.thumb,.image')||(card&&card.querySelector('.img,.thumb,.image'))||card||e;"
                + "const info=imgFields(img, box);"
                + "if(info.src||info['data-original']||info['data-src']||info['data-bg']||info['data-lazy-src'])o.img=info;"
                + "const strong=e.querySelector('strong.title'); if(strong)o.label=strong.textContent||'';}"
                + "if(e.tagName==='VIDEO'||e.tagName==='SOURCE'){"
                + "const src=e.getAttribute('src')||e.src||''; if(src)o.src=src;}"
                + "if(e.tagName==='H1')o.text=e.textContent||'';"
                + "return o;};"
                + "const bodyText=(document.body&&document.body.innerText)||'';"
                + "const age=/age confirmation/i.test(bodyText);"
                + "const scripts=[]; const list=document.scripts||[];"
                + "for(let i=0;i<list.length&&scripts.length<3;i++){"
                + "const t=list[i].textContent||'';"
                + "if(t.indexOf('video_url')>=0||t.indexOf('license_code')>=0||t.indexOf('/get_file/')>=0){"
                + "scripts.push(t.slice(0,12000));}}"
                + "const files=[];"
                + "const raw=document.documentElement?document.documentElement.innerHTML:'';"
                + "const fileRe=/https?:\\/\\/[^\"'\\s<>]{0,500}(?:\\/get_file\\/[^\"'\\s<>]{0,300}\\.mp4|\\/[0-9a-fA-F]{32}\\.mp4)[^\"'\\s<>]*/gi;"
                + "let fm; let guard=0;"
                + "while((fm=fileRe.exec(raw))&&files.length<8&&guard<40){"
                + "guard++; files.push(fm[0]);}"
                + "const nodes=Array.from(document.querySelectorAll('a[href],video,source,h1')).slice(0,1500).map(pick);"
                + "return JSON.stringify({url:location.href,title:document.title||'',"
                + "text:bodyText.slice(0,1500),age:age,nodes:nodes,scripts:scripts,files:files});})()";
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
                    logPage("ready", finalUrl, title, false);
                    try {
                        CookieManager.getInstance().flush();
                    } catch (Exception ignored) {
                    }
                    result.set(document);
                    complete(completed, error, finished, null);
                    return;
                }
                if (permanentlyBlocked(title, body)) {
                    logPage("blocked", finalUrl, title, false);
                    complete(completed, error, finished,
                            new IOException("XMegaDrive blocked this device"));
                    return;
                }
                boolean age = payload.optBoolean("age")
                        || (body != null && body.toLowerCase(java.util.Locale.US).contains("age confirmation"));
                if (age && attempt >= 2
                        && handleAgeGate(webView, completed, error, finished)) {
                    return;
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
        JSONArray files = payload.optJSONArray("files");
        if (files != null) {
            for (int index = 0; index < files.length(); index++) {
                String file = files.optString(index);
                if (file.isEmpty()) continue;
                html.append("<a href=\"").append(Entities.escape(file)).append("\"></a>");
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
        for (org.jsoup.nodes.Element anchor : document.select("a[href*=/categories/], a[href*=/videos/]")) {
            for (org.jsoup.nodes.Element image : anchor.select("img")) {
                if (imageLooksReal(image)) return true;
            }
        }
        return false;
    }

    private static boolean imageLooksReal(org.jsoup.nodes.Element image) {
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
                    || lower.contains("no_image") || lower.contains("/cdn-cgi/challenge")
                    || lower.contains("/cdn-cgi/images/") || lower.contains("/cdn-cgi/styles/")
                    || lower.endsWith(".gif")) {
                continue;
            }
            if (lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png")
                    || lower.contains(".webp") || lower.contains(".avif")
                    || lower.contains("/contents/")
                    || lower.contains("videos_screenshots")) {
                return true;
            }
        }
        return false;
    }

    private static void logIdentity(WebView webView) {
        String script = "(() => {"
                + "const data=navigator.userAgentData||null;"
                + "const brands=[];"
                + "if(data&&data.brands){for(let i=0;i<data.brands.length;i++){"
                + "const b=data.brands[i]||{}; brands.push((b.brand||'')+'/'+(b.version||''));}}"
                + "const base={ua:navigator.userAgent||'',brands:brands,"
                + "mobile:!!(data&&data.mobile),platform:(data&&data.platform)||''};"
                + "if(!data||!data.getHighEntropyValues) return JSON.stringify(base);"
                + "return data.getHighEntropyValues(['model','platform','platformVersion']).then(high=>{"
                + "base.model=(high&&high.model)||'';"
                + "base.platform=(high&&high.platform)||base.platform;"
                + "base.platformVersion=(high&&high.platformVersion)||'';"
                + "return JSON.stringify(base);"
                + "}).catch(()=>JSON.stringify(base));})()";
        webView.evaluateJavascript(script, encoded -> {
            String ua = "";
            String brands = "";
            String mobile = "";
            String platform = "";
            String platformVersion = "";
            String model = "";
            try {
                Object decoded = new JSONTokener(encoded == null ? "null" : encoded).nextValue();
                if (decoded instanceof String) {
                    JSONObject json = new JSONObject((String) decoded);
                    ua = json.optString("ua");
                    brands = String.valueOf(json.opt("brands"));
                    mobile = String.valueOf(json.opt("mobile"));
                    platform = json.optString("platform");
                    platformVersion = json.optString("platformVersion");
                    model = json.optString("model");
                }
            } catch (Exception ignored) {
            }
            Log.i(TAG, "identity url=" + redactUrl(webView.getUrl())
                    + " ua=" + oneLine(ua)
                    + " brands=" + oneLine(brands)
                    + " mobile=" + oneLine(mobile)
                    + " platform=" + oneLine(platform)
                    + " platformVersion=" + oneLine(platformVersion)
                    + " model=" + oneLine(model)
                    + " " + cookieReport(webView.getUrl()));
        });
    }

    private static volatile String lastLoggedPage = "";

    private static void logPage(String phase, String url, String title, boolean interstitial) {
        String line = phase
                + " url=" + redactUrl(url)
                + " title=" + oneLine(title)
                + " interstitial=" + interstitial
                + " " + cookieReport(url);
        if (line.equals(lastLoggedPage)) return;
        lastLoggedPage = line;
        Log.i(TAG, line);
    }

    private static String cookieReport(String url) {
        try {
            return XMegaDriveRepository.cookiePresence(
                    CookieManager.getInstance().getCookie(url));
        } catch (Exception ignored) {
            return "cf_clearance=unavailable __cf_bm=unavailable";
        }
    }

    private static String redactUrl(String url) {
        if (url == null) return "";
        int query = url.indexOf('?');
        int fragment = url.indexOf('#');
        int cut = query < 0 ? fragment : (fragment < 0 ? query : Math.min(query, fragment));
        return cut < 0 ? url : url.substring(0, cut);
    }

    private static String oneLine(String value) {
        if (value == null) return "";
        return value.replace('\n', ' ').replace('\r', ' ').trim();
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

private static final class AgeGateState {
        XMegaDriveAgePrompt prompt;
        boolean autoRepairAttempted;
    }

    private static AgeGateState ageState(WebView webView) {
        if (webView == null) return null;
        Object tag = webView.getTag();
        if (tag instanceof AgeGateState) return (AgeGateState) tag;
        AgeGateState state = new AgeGateState();
        webView.setTag(state);
        return state;
    }

    private static XMegaDriveAgePrompt agePrompt(WebView webView) {
        AgeGateState state = ageState(webView);
        return state == null ? null : state.prompt;
    }

    private static boolean handleAgeGate(
            WebView webView,
            AtomicBoolean completed,
            AtomicReference<IOException> error,
            CountDownLatch finished
    ) {
        if (webView == null || completed.get()) return true;
        Activity host = activity(webView.getContext());
        if (host == null || host.isFinishing() || host.isDestroyed()) {
            complete(completed, error, finished,
                    new IOException("XMegaDrive age confirmation is unavailable"));
            return true;
        }
        AgeGateState state = ageState(webView);
        if (state != null
                && !state.autoRepairAttempted
                && AccessNoticeDialog.isAccepted(host)
                && XMegaDriveAgeSession.isSetupComplete(host)) {
            state.autoRepairAttempted = true;
            XMegaDriveAgeSession.authorize(host, ready -> host.runOnUiThread(() -> {
                if (completed.get() || webView.getParent() == null) return;
                if (ready) {
                    webView.reload();
                } else {
                    showNativeAgePrompt(webView, completed, error, finished);
                }
            }));
            return true;
        }
        if (agePrompt(webView) == null) {
            showNativeAgePrompt(webView, completed, error, finished);
        }
        return true;
    }

    private static XMegaDriveAgePrompt showNativeAgePrompt(
            WebView webView,
            AtomicBoolean completed,
            AtomicReference<IOException> error,
            CountDownLatch finished
    ) {
        if (webView == null || completed.get()) return null;
        AgeGateState state = ageState(webView);
        if (state != null && state.prompt != null) return state.prompt;
        Activity host = activity(webView.getContext());
        if (host == null || host.isFinishing() || host.isDestroyed()) return null;
        XMegaDriveAgePrompt created = new XMegaDriveAgePrompt(
                host,
                prompt -> {
                    XMegaDriveAgeSession.markSetupComplete(host);
                    XMegaDriveAgeSession.authorize(host, ready -> host.runOnUiThread(() -> {
                        if (completed.get() || webView.getParent() == null) return;
                        if (!ready) {
                            prompt.showFailure();
                            return;
                        }
                        prompt.showReady();
                        dismissAgePrompt(webView);
                        webView.reload();
                    }));
                },
                () -> complete(completed, error, finished,
                        new IOException("XMegaDrive age access was not confirmed"))
        );
        if (state != null) state.prompt = created;
        created.show();
        return created;
    }

    private static void dismissAgePrompt(WebView webView) {
        AgeGateState state = ageState(webView);
        if (state == null || state.prompt == null) return;
        state.prompt.dismiss();
        state.prompt = null;
    }

    private static boolean needsPerson(String title, String text) {
        String blob = ((title == null ? "" : title) + "\n" + (text == null ? "" : text))
                .toLowerCase(Locale.US);
        return blob.contains("verify you are human")
                || blob.contains("turnstile");
    }

    /** Full-size, but shifted off the display. A 1px WebView is rejected by the check. */
    private static void park(View overlay) {
        if (overlay == null) return;
        int width = overlay.getResources().getDisplayMetrics().widthPixels;
        if (width < 1) width = 1080;
        overlay.setTranslationX(width * 3f);
        overlay.setVisibility(View.VISIBLE);
        overlay.setClickable(false);
    }

    private static void reveal(FrameLayout overlay, WebView webView) {
        if (overlay != null) {
            overlay.setAlpha(1f);
            overlay.setTranslationX(0f);
            overlay.setVisibility(View.VISIBLE);
            overlay.setClickable(true);
        }
        if (webView != null) {
            allowNetworkImages(webView);
            webView.setTranslationX(0f);
            webView.setTranslationY(0f);
            webView.setScaleX(1f);
            webView.setScaleY(1f);
            webView.requestFocus();
        }
    }

    private static void allowNetworkImages(WebView webView) {
        if (webView == null) return;
        try {
            WebSettings settings = webView.getSettings();
            settings.setLoadsImagesAutomatically(true);
            settings.setBlockNetworkImage(false);
        } catch (Exception ignored) {
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
        // A renderer exit can make stopLoading fail; hierarchy removal and destroy must still run.
        try {
            if (webView != null) {
                webView.stopLoading();
                webView.setWebViewClient(null);
                webView.setWebChromeClient(null);
            }
        } catch (Exception ignored) {
        }
        try {
            if (overlay != null) {
                ViewParent parent = overlay.getParent();
                if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(overlay);
            }
        } catch (Exception ignored) {
        }
        try {
            if (webView != null) {
                ViewParent parent = webView.getParent();
                if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(webView);
            }
        } catch (Exception ignored) {
        }
        try {
            if (webView != null) {
                webView.destroy();
                Log.i(TAG, "browser released");
            }
        } catch (Exception ignored) {
        }
    }
}
