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
 * only brought on-screen when a person has to tap a challenge or the age check.
 * The "; wv" token is removed from the user-agent string. Client hints are not
 * rewritten, because that does not make this WebView the same as Chrome.
 * cf_clearance and __cf_bm are left in place so a valid session is not discarded.
 */
final class XMegaDriveBrowserFetch {
    private static final String TAG = "XMegaDrive";
    private static final String POPUP_GUARD =
            "(function(){window.open=function(){return null;};})();";
    private static final long TIMEOUT_SECONDS = 45L;
    private static final long EXTRACTION_RETRY_MS = 800L;
    private static final long NATIVE_AGE_FALLBACK_GRACE_MS = 6500L;
    private static final long NATIVE_AGE_TARGET_DISCOVERY_TIMEOUT_MS = 12000L;
    private static final long NATIVE_AGE_TARGET_RETRY_MS = 300L;
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
                            EXTRACTION_RETRY_MS
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
                XMegaDriveAgePrompt prompt = agePrompt(webView);
                if (prompt != null) prompt.showRawFallback();
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
                    XMegaDriveAgePrompt prompt = agePrompt(webView);
                    if (prompt != null) prompt.showRawFallback();
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
                + ageGateSafetyScript()
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
                    Runnable finishReady = () -> {
                        result.set(document);
                        complete(completed, error, finished, null);
                    };
                    if (BuildConfig.DEBUG && XMegaDriveAgeDiagnostics.hasBefore(webView)) {
                        XMegaDriveAgeDiagnostics.captureAfterAndShow(
                                webView,
                                webView.getUrl(),
                                finishReady
                        );
                    } else {
                        finishReady.run();
                    }
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
                if (age && attempt >= 4) {
                    XMegaDriveAgePrompt prompt = agePrompt(webView);
                    if (prompt == null) {
                        prompt = showNativeAgePrompt(webView, overlay, completed, error, finished);
                    }
                    if (prompt == null) {
                        revealRawAgeFallback(overlay, webView);
                    } else if (!prompt.isRawFallbackShown()
                            && prompt.hasContinueBeenPressed()
                            && prompt.millisSinceContinue() >= NATIVE_AGE_FALLBACK_GRACE_MS) {
                        prompt.showRawFallback();
                        revealRawAgeFallback(overlay, webView);
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

/**
 * Keep the reliable 5.2.1 visible fallback, but make every known age-decline
 * control inert before that WebView is revealed. Continue remains available.
 */
static String ageGateSafetyScript() {
    return "const ageNorm=s=>String(s||'').replace(String.fromCharCode(8217),\"'\").replace(/\s+/g,' ').trim().toLowerCase();"
            + "const ageDecline=s=>/\b(exit|leave|decline)\b/.test(s)||/under\s*18/.test(s)"
            + "||/\bi(?:'m| am|m) not\b/.test(s)||s==='no'||s==='no thanks'||s==='not 18';"
            + "const age=/age confirmation/i.test(bodyText);"
            + "if(age){const controls=document.querySelectorAll('button,input[type=\"button\"],input[type=\"submit\"],a,[role=\"button\"]');"
            + "const ageLabel=el=>ageNorm(el.innerText||el.textContent||el.value||el.getAttribute('value')||el.getAttribute('aria-label')||'');"
            + "const hideDecline=el=>{const label=ageLabel(el);if(!ageDecline(label))return false;"
            + "el.style.display='none';el.setAttribute('aria-hidden','true');el.setAttribute('tabindex','-1');"
            + "try{if('disabled' in el)el.disabled=true;}catch(e){}return true;};"
            + "for(let i=0;i<controls.length;i++)hideDecline(controls[i]);"
            + "if(!window.__zeroChillAgeExitGuard){window.__zeroChillAgeExitGuard=true;"
            + "document.addEventListener('click',ev=>{const el=ev.target&&ev.target.closest?ev.target.closest('button,input,a,[role=\"button\"]'):null;"
            + "if(!el)return;const text=(document.body&&document.body.innerText)||'';if(!/age confirmation/i.test(text))return;"
            + "if(ageDecline(ageLabel(el))){ev.preventDefault();ev.stopImmediatePropagation();}},true);}}";
}

    static String ageGateNativeTapTargetScript() {
        return "(() => {"
                + "const norm=s=>String(s||'').replace(String.fromCharCode(8217),\"'\").replace(/\s+/g,' ').trim().toLowerCase();"
                + "const decline=s=>/\b(exit|leave|decline)\b/.test(s)||/under\s*18/.test(s)"
                + "||/\bi(?:'m| am|m) not\b/.test(s)||s==='no'||s==='no thanks'||s==='not 18';"
                + "const accept=s=>!!s&&!decline(s)&&(/\bcontinue\b/.test(s)||/\benter\b/.test(s)"
                + "||s==='yes'||/\bi(?:'m| am|m) 18\b/.test(s));"
                + "const label=el=>norm(el&&((el.innerText||el.textContent||el.value||el.getAttribute('value')||el.getAttribute('aria-label'))||''));"
                + "const controls='button,input[type=button],input[type=submit],a,[role=button]';"
                + "const seen=[];"
                + "const visited=root=>{for(let i=0;i<seen.length;i++)if(seen[i]===root)return true;seen.push(root);return false;};"
                + "const scan=(root,ox,oy,depth)=>{"
                + "if(!root||depth>5||visited(root))return null;"
                + "let list=[];try{list=root.querySelectorAll?root.querySelectorAll(controls):[];}catch(e){list=[];}"
                + "for(let i=0;i<list.length;i++){const el=list[i];const value=label(el);if(!decline(value))continue;"
                + "try{el.style.display='none';el.setAttribute('aria-hidden','true');el.setAttribute('tabindex','-1');if('disabled' in el)el.disabled=true;}catch(e){}}"
                + "for(let i=0;i<list.length;i++){const el=list[i];if(!accept(label(el)))continue;"
                + "let r=null;try{r=el.getBoundingClientRect();}catch(e){}"
                + "if(!r||r.width<=0||r.height<=0)continue;"
                + "let hidden=false;try{const s=getComputedStyle(el);hidden=s.display==='none'||s.visibility==='hidden'||Number(s.opacity||1)===0;}catch(e){}"
                + "if(hidden||el.disabled||el.getAttribute('aria-hidden')==='true')continue;"
                + "try{el.scrollIntoView({block:'center',inline:'center'});}catch(e){}"
                + "try{r=el.getBoundingClientRect();}catch(e){}"
                + "return {x:ox+r.left+r.width/2,y:oy+r.top+r.height/2,w:r.width,h:r.height};}"
                + "let all=[];try{all=root.querySelectorAll?root.querySelectorAll('*'):[];}catch(e){all=[];}"
                + "for(let i=0;i<all.length;i++){const el=all[i];"
                + "try{if(el.shadowRoot){const hit=scan(el.shadowRoot,ox,oy,depth+1);if(hit)return hit;}}catch(e){}"
                + "const tag=String(el.tagName||'').toLowerCase();if(tag!=='iframe'&&tag!=='frame')continue;"
                + "try{const doc=el.contentDocument;if(!doc)continue;const fr=el.getBoundingClientRect();"
                + "const hit=scan(doc,ox+fr.left,oy+fr.top,depth+1);if(hit)return hit;}catch(e){}}"
                + "return null;};"
                + "const hit=scan(document,0,0,0);if(!hit)return '';"
                + "hit.vw=window.innerWidth||0;hit.vh=window.innerHeight||0;return JSON.stringify(hit);})()";
    }

    private static final class AgeTapTarget {
        final float x;
        final float y;
        final float width;
        final float height;

        AgeTapTarget(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private static AgeTapTarget decodeAgeTapTarget(WebView webView, String encoded) {
        if (webView == null) return null;
        try {
            Object decoded = new JSONTokener(encoded == null ? "null" : encoded).nextValue();
            if (!(decoded instanceof String) || ((String) decoded).trim().isEmpty()) return null;
            JSONObject target = new JSONObject((String) decoded);
            double cssX = target.optDouble("x", -1d);
            double cssY = target.optDouble("y", -1d);
            double cssWidth = target.optDouble("w", 0d);
            double cssHeight = target.optDouble("h", 0d);
            double viewportWidth = target.optDouble("vw", 0d);
            double viewportHeight = target.optDouble("vh", 0d);
            int viewWidth = webView.getWidth();
            int viewHeight = webView.getHeight();
            if (cssX < 0d || cssY < 0d || cssWidth <= 0d || cssHeight <= 0d
                    || viewportWidth <= 0d || viewportHeight <= 0d
                    || viewWidth <= 1 || viewHeight <= 1) {
                return null;
            }
            float scaleX = (float) (viewWidth / viewportWidth);
            float scaleY = (float) (viewHeight / viewportHeight);
            return new AgeTapTarget(
                    (float) cssX * scaleX,
                    (float) cssY * scaleY,
                    Math.max(1f, (float) cssWidth * scaleX),
                    Math.max(1f, (float) cssHeight * scaleY)
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void stageNativeAgeOverlay(FrameLayout overlay, WebView webView) {
        if (overlay != null) {
            overlay.setTranslationX(0f);
            overlay.setVisibility(View.VISIBLE);
            overlay.setClickable(false);
            // Keep the actual WebView attached and rendered so Android can route the
            // user's physical gesture to it, while keeping the raw site effectively invisible.
            overlay.setAlpha(0.001f);
        }
        if (webView != null) {
            webView.setTranslationX(0f);
            webView.setTranslationY(0f);
            webView.setScaleX(1f);
            webView.setScaleY(1f);
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void alignPhysicalAgeTarget(
            FrameLayout overlay,
            WebView webView,
            XMegaDriveAgePrompt prompt,
            AgeTapTarget target
    ) {
        if (overlay == null || webView == null || prompt == null || target == null
                || prompt.isRawFallbackShown()) {
            return;
        }
        stageNativeAgeOverlay(overlay, webView);
        prompt.postContinueBounds((left, top, width, height) -> {
            if (prompt.isRawFallbackShown() || webView.getParent() == null
                    || width <= 0 || height <= 0) {
                return;
            }
            stageNativeAgeOverlay(overlay, webView);
            int[] webLocation = new int[2];
            webView.getLocationOnScreen(webLocation);

            // Enlarge the invisible real control enough to cover the complete native
            // button. The target pivot stays fixed, then the WebView is translated so
            // the site's real Continue control sits directly beneath the cyan button.
            float scaleX = clamp((width * 1.10f) / target.width, 1f, 6f);
            float scaleY = clamp((height * 1.10f) / target.height, 1f, 6f);
            webView.setPivotX(target.x);
            webView.setPivotY(target.y);
            webView.setScaleX(scaleX);
            webView.setScaleY(scaleY);
            float buttonCenterX = left + width / 2f;
            float buttonCenterY = top + height / 2f;
            webView.setTranslationX(buttonCenterX - (webLocation[0] + target.x));
            webView.setTranslationY(buttonCenterY - (webLocation[1] + target.y));
            prompt.armPhysicalContinue();
            Log.i(TAG, "nativeAge physical bridge armed");
        });
    }

    private static void preparePhysicalAgeBridge(
            FrameLayout overlay,
            WebView webView,
            XMegaDriveAgePrompt prompt,
            long startedAtMs
    ) {
        if (overlay == null || webView == null || prompt == null || prompt.isRawFallbackShown()) return;
        stageNativeAgeOverlay(overlay, webView);
        long started = startedAtMs > 0L ? startedAtMs : android.os.SystemClock.elapsedRealtime();
        try {
            webView.evaluateJavascript(ageGateNativeTapTargetScript(), encoded -> {
                if (prompt.isRawFallbackShown() || webView.getParent() == null) return;
                AgeTapTarget target = decodeAgeTapTarget(webView, encoded);
                if (target != null) {
                    alignPhysicalAgeTarget(overlay, webView, prompt, target);
                    return;
                }
                retryOrRevealAgeTarget(overlay, webView, prompt, started, null);
            });
        } catch (Exception failure) {
            retryOrRevealAgeTarget(overlay, webView, prompt, started, failure);
        }
    }

    private static void retryOrRevealAgeTarget(
            FrameLayout overlay,
            WebView webView,
            XMegaDriveAgePrompt prompt,
            long startedAtMs,
            Exception failure
    ) {
        if (prompt == null || prompt.isRawFallbackShown() || webView == null || webView.getParent() == null) return;
        long elapsed = Math.max(0L, android.os.SystemClock.elapsedRealtime() - startedAtMs);
        if (elapsed < NATIVE_AGE_TARGET_DISCOVERY_TIMEOUT_MS) {
            webView.postDelayed(
                    () -> preparePhysicalAgeBridge(overlay, webView, prompt, startedAtMs),
                    NATIVE_AGE_TARGET_RETRY_MS
            );
            return;
        }
        if (failure == null) {
            Log.i(TAG, "nativeAge physical bridge target unavailable after " + elapsed + "ms");
        } else {
            Log.i(TAG, "nativeAge physical bridge discovery failed after " + elapsed + "ms"
                    + " error=" + failure.getClass().getSimpleName());
        }
        prompt.showRawFallback();
        revealRawAgeFallback(overlay, webView);
    }

    private static XMegaDriveAgePrompt agePrompt(WebView webView) {
        if (webView == null) return null;
        Object tag = webView.getTag();
        return tag instanceof XMegaDriveAgePrompt ? (XMegaDriveAgePrompt) tag : null;
    }

    private static XMegaDriveAgePrompt showNativeAgePrompt(
            WebView webView,
            FrameLayout overlay,
            AtomicBoolean completed,
            AtomicReference<IOException> error,
            CountDownLatch finished
    ) {
        if (webView == null || overlay == null || completed.get()) return null;
        XMegaDriveAgePrompt existing = agePrompt(webView);
        if (existing != null) return existing;
        Activity host = activity(webView.getContext());
        if (host == null || host.isFinishing() || host.isDestroyed()) return null;
        XMegaDriveAgePrompt created = new XMegaDriveAgePrompt(
                host,
                () -> Log.i(TAG, "nativeAge physical Continue gesture received"),
                () -> complete(completed, error, finished,
                        new IOException("XMegaDrive check was dismissed"))
        );
        webView.setTag(created);
        created.show();
        preparePhysicalAgeBridge(overlay, webView, created, android.os.SystemClock.elapsedRealtime());
        return created;
    }

    private static void dismissAgePrompt(WebView webView) {
        XMegaDriveAgePrompt prompt = agePrompt(webView);
        if (prompt == null) return;
        prompt.dismiss();
        try {
            webView.setTag(null);
        } catch (Exception ignored) {
        }
    }

    private static void revealRawAgeFallback(FrameLayout overlay, WebView webView) {
        if (BuildConfig.DEBUG && webView != null) {
            XMegaDriveAgeDiagnostics.captureBefore(
                    webView,
                    () -> revealRawAgeFallbackNow(overlay, webView)
            );
            return;
        }
        revealRawAgeFallbackNow(overlay, webView);
    }

    private static void revealRawAgeFallbackNow(FrameLayout overlay, WebView webView) {
        reveal(overlay, webView);
        if (overlay != null && overlay.getChildCount() > 0
                && overlay.getChildAt(0) instanceof TextView) {
            ((TextView) overlay.getChildAt(0)).setText(
                    "XMegaDrive diagnostic: tap the site's real Continue button once.");
        }
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
            webView.setTranslationX(0f);
            webView.setTranslationY(0f);
            webView.setScaleX(1f);
            webView.setScaleY(1f);
            webView.requestFocus();
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
