from pathlib import Path
import re

browser_path = Path('app/src/main/java/com/webapp/crazyshit/XMegaDriveBrowserFetch.java')
text = browser_path.read_text()

ready_pattern = re.compile(r'''                if \(ready\(document, title, body, pageUrl, attempt\)\) \{\n                    logPage\("ready", finalUrl, title, false\);\n                    try \{\n                        CookieManager\.getInstance\(\)\.flush\(\);\n                    \} catch \(Exception ignored\) \{\n                    \}\n                    result\.set\(document\);\n                    complete\(completed, error, finished, null\);\n                    return;\n                \}''')
ready_replacement = '''                if (ready(document, title, body, pageUrl, attempt)) {\n                    logPage("ready", finalUrl, title, false);\n                    try {\n                        CookieManager.getInstance().flush();\n                    } catch (Exception ignored) {\n                    }\n                    Runnable finishReady = () -> {\n                        result.set(document);\n                        complete(completed, error, finished, null);\n                    };\n                    if (BuildConfig.DEBUG && XMegaDriveAgeDiagnostics.hasBefore(webView)) {\n                        XMegaDriveAgeDiagnostics.captureAfterAndShow(\n                                webView,\n                                webView.getUrl(),\n                                finishReady\n                        );\n                    } else {\n                        finishReady.run();\n                    }\n                    return;\n                }'''
text, count = ready_pattern.subn(ready_replacement, text, count=1)
if count != 1:
    raise SystemExit(f'failed to patch ready block: {count}')

fallback_pattern = re.compile(r'''    private static void revealRawAgeFallback\(FrameLayout overlay, WebView webView\) \{\n        reveal\(overlay, webView\);\n        if \(overlay != null && overlay\.getChildCount\(\) > 0\n                && overlay\.getChildAt\(0\) instanceof TextView\) \{\n            \(\(TextView\) overlay\.getChildAt\(0\)\)\.setText\(\n                    "XMegaDrive needs a quick check\. Tap Continue if it stays open\."\);\n        \}\n    \}''')
fallback_replacement = '''    private static void revealRawAgeFallback(FrameLayout overlay, WebView webView) {\n        if (BuildConfig.DEBUG && webView != null) {\n            XMegaDriveAgeDiagnostics.captureBefore(\n                    webView,\n                    () -> revealRawAgeFallbackNow(overlay, webView)\n            );\n            return;\n        }\n        revealRawAgeFallbackNow(overlay, webView);\n    }\n\n    private static void revealRawAgeFallbackNow(FrameLayout overlay, WebView webView) {\n        reveal(overlay, webView);\n        if (overlay != null && overlay.getChildCount() > 0\n                && overlay.getChildAt(0) instanceof TextView) {\n            ((TextView) overlay.getChildAt(0)).setText(\n                    "XMegaDrive diagnostic: tap the site's real Continue button once.");\n        }\n    }'''
text, count = fallback_pattern.subn(fallback_replacement, text, count=1)
if count != 1:
    raise SystemExit(f'failed to patch fallback block: {count}')

if 'XMegaDriveAgeDiagnostics.captureBefore' not in text or 'captureAfterAndShow' not in text:
    raise SystemExit('diagnostic hooks missing')
browser_path.write_text(text)

helper = r'''package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.SystemClock;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Debug-only, value-free diagnostics for XMegaDrive age-session state changes. */
final class XMegaDriveAgeDiagnostics {
    private static final long CAPTURE_TIMEOUT_MS = 900L;
    private static final Map<WebView, Session> SESSIONS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private XMegaDriveAgeDiagnostics() {
    }

    static boolean hasBefore(WebView webView) {
        if (webView == null) return false;
        synchronized (SESSIONS) {
            Session session = SESSIONS.get(webView);
            return session != null && session.before != null;
        }
    }

    static void captureBefore(WebView webView, Runnable reveal) {
        if (webView == null) {
            run(reveal);
            return;
        }
        Session session;
        synchronized (SESSIONS) {
            session = SESSIONS.get(webView);
            if (session == null) {
                session = new Session();
                SESSIONS.put(webView, session);
            }
            if (session.before != null) {
                run(reveal);
                return;
            }
            if (session.beforeCaptureStarted) {
                session.revealAfterBefore = reveal;
                return;
            }
            session.beforeCaptureStarted = true;
            session.revealAfterBefore = reveal;
        }
        Session target = session;
        capture(webView, state -> {
            Runnable callback;
            synchronized (SESSIONS) {
                target.before = state;
                callback = target.revealAfterBefore;
                target.revealAfterBefore = null;
            }
            run(callback);
        });
    }

    static void captureAfterAndShow(WebView webView, String finalUrl, Runnable done) {
        if (webView == null) {
            run(done);
            return;
        }
        Session session;
        synchronized (SESSIONS) {
            session = SESSIONS.get(webView);
        }
        if (session == null || session.before == null) {
            run(done);
            return;
        }
        Session target = session;
        capture(webView, after -> {
            if (finalUrl != null && !finalUrl.trim().isEmpty()) {
                after.url = redactUrl(finalUrl);
            }
            String report = buildReport(target.before, after);
            synchronized (SESSIONS) {
                SESSIONS.remove(webView);
            }
            showReport(webView.getContext(), report);
            run(done);
        });
    }

    private static void capture(WebView webView, StateCallback callback) {
        State base = new State();
        base.url = redactUrl(webView.getUrl());
        base.cookies.putAll(cookieFingerprints(webView.getUrl()));
        AtomicBoolean finished = new AtomicBoolean();
        long started = SystemClock.elapsedRealtime();
        Runnable timeout = () -> {
            if (!finished.compareAndSet(false, true)) return;
            base.captureNote = "storage snapshot timed out after "
                    + Math.max(0L, SystemClock.elapsedRealtime() - started) + "ms";
            callback.onState(base);
        };
        webView.postDelayed(timeout, CAPTURE_TIMEOUT_MS);
        try {
            webView.evaluateJavascript(storageSnapshotScript(), encoded -> {
                if (!finished.compareAndSet(false, true)) return;
                parseStorageSnapshot(base, encoded);
                callback.onState(base);
            });
        } catch (Exception failure) {
            if (!finished.compareAndSet(false, true)) return;
            base.captureNote = "storage snapshot unavailable: "
                    + failure.getClass().getSimpleName();
            callback.onState(base);
        }
    }

    static Map<String, String> cookieFingerprints(String currentUrl) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        CookieManager manager;
        try {
            manager = CookieManager.getInstance();
        } catch (Exception ignored) {
            return out;
        }
        TreeSet<String> urls = new TreeSet<>();
        urls.add("https://xmegadrive.com/");
        urls.add("https://www.xmegadrive.com/");
        if (isXMegaDriveUrl(currentUrl)) urls.add(redactUrl(currentUrl));
        for (String url : urls) {
            String raw;
            try {
                raw = manager.getCookie(url);
            } catch (Exception ignored) {
                continue;
            }
            String host = hostOnly(url);
            if (raw == null || raw.trim().isEmpty()) continue;
            for (String part : raw.split(";")) {
                String item = part == null ? "" : part.trim();
                int split = item.indexOf('=');
                if (split <= 0) continue;
                String name = item.substring(0, split).trim();
                String value = item.substring(split + 1);
                if (name.isEmpty()) continue;
                out.put(host + ":" + name, fingerprint(value));
            }
        }
        return out;
    }

    static String storageSnapshotScript() {
        return "(() => {"
                + "const fp=v=>{const s=String(v==null?'':v);let h=2166136261;"
                + "for(let i=0;i<s.length;i++){h^=s.charCodeAt(i);h=Math.imul(h,16777619);}"
                + "return (h>>>0).toString(16)+':'+s.length;};"
                + "const dump=store=>{const out={};if(!store)return out;"
                + "for(let i=0;i<store.length;i++){const k=store.key(i);if(k!=null)out[k]=fp(store.getItem(k));}return out;};"
                + "return JSON.stringify({url:location.origin+location.pathname,"
                + "local:dump(window.localStorage),session:dump(window.sessionStorage)});})()";
    }

    private static void parseStorageSnapshot(State state, String encoded) {
        try {
            Object outer = new JSONTokener(encoded == null ? "null" : encoded).nextValue();
            if (!(outer instanceof String)) {
                state.captureNote = "storage snapshot returned no string";
                return;
            }
            JSONObject json = new JSONObject((String) outer);
            state.url = redactUrl(json.optString("url", state.url));
            copyObject(json.optJSONObject("local"), state.localStorage);
            copyObject(json.optJSONObject("session"), state.sessionStorage);
        } catch (Exception failure) {
            state.captureNote = "storage snapshot parse failed: "
                    + failure.getClass().getSimpleName();
        }
    }

    private static void copyObject(JSONObject source, Map<String, String> target) {
        if (source == null) return;
        java.util.Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key == null || key.trim().isEmpty()) continue;
            target.put(key, source.optString(key, ""));
        }
    }

    static String buildReportForTest(
            String beforeUrl,
            Map<String, String> beforeCookies,
            Map<String, String> beforeLocal,
            Map<String, String> beforeSession,
            String afterUrl,
            Map<String, String> afterCookies,
            Map<String, String> afterLocal,
            Map<String, String> afterSession
    ) {
        State before = new State();
        before.url = redactUrl(beforeUrl);
        before.cookies.putAll(beforeCookies);
        before.localStorage.putAll(beforeLocal);
        before.sessionStorage.putAll(beforeSession);
        State after = new State();
        after.url = redactUrl(afterUrl);
        after.cookies.putAll(afterCookies);
        after.localStorage.putAll(afterLocal);
        after.sessionStorage.putAll(afterSession);
        return buildReport(before, after);
    }

    private static String buildReport(State before, State after) {
        StringBuilder out = new StringBuilder();
        out.append("ZEROCHILL XMegaDrive age diagnostic\n");
        out.append("Values were not included.\n\n");
        out.append("Before: ").append(empty(before.url)).append('\n');
        out.append("After:  ").append(empty(after.url)).append('\n');
        out.append("Navigation: ")
                .append(empty(before.url).equals(empty(after.url)) ? "same" : "changed")
                .append("\n\n");
        appendDiff(out, "Cookies", before.cookies, after.cookies);
        appendDiff(out, "localStorage", before.localStorage, after.localStorage);
        appendDiff(out, "sessionStorage", before.sessionStorage, after.sessionStorage);
        if (!empty(before.captureNote).isEmpty()) {
            out.append("Before note: ").append(before.captureNote).append('\n');
        }
        if (!empty(after.captureNote).isEmpty()) {
            out.append("After note: ").append(after.captureNote).append('\n');
        }
        out.append("\nCopy this report and paste it into ChatGPT.");
        return out.toString();
    }

    private static void appendDiff(
            StringBuilder out,
            String title,
            Map<String, String> before,
            Map<String, String> after
    ) {
        Set<String> all = new TreeSet<>();
        all.addAll(before.keySet());
        all.addAll(after.keySet());
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        for (String key : all) {
            if (!before.containsKey(key)) {
                added.add(key);
            } else if (!after.containsKey(key)) {
                removed.add(key);
            } else if (!empty(before.get(key)).equals(empty(after.get(key)))) {
                changed.add(key);
            }
        }
        out.append(title).append(':').append('\n');
        out.append("  added: ").append(names(added)).append('\n');
        out.append("  removed: ").append(names(removed)).append('\n');
        out.append("  changed: ").append(names(changed)).append("\n\n");
    }

    private static String names(List<String> names) {
        return names.isEmpty() ? "none" : String.join(", ", names);
    }

    private static void showReport(Context context, String report) {
        Activity activity = activity(context);
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            new AlertDialog.Builder(activity)
                    .setTitle("XMegaDrive diagnostic")
                    .setMessage(report)
                    .setPositiveButton("COPY", (dialog, which) -> {
                        ClipboardManager clipboard = (ClipboardManager) activity
                                .getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText(
                                    "XMegaDrive diagnostic",
                                    report
                            ));
                            Toast.makeText(activity, "Diagnostic copied", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("CLOSE", null)
                    .show();
        });
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

    static String redactUrl(String url) {
        if (url == null || url.trim().isEmpty()) return "";
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
            String host = uri.getHost() == null ? "" : uri.getHost();
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (host.isEmpty()) return path;
            return scheme + "://" + host + path;
        } catch (Exception ignored) {
            int query = url.indexOf('?');
            int fragment = url.indexOf('#');
            int cut = query < 0 ? fragment : (fragment < 0 ? query : Math.min(query, fragment));
            return cut < 0 ? url : url.substring(0, cut);
        }
    }

    private static boolean isXMegaDriveUrl(String url) {
        String host = hostOnly(url);
        return "xmegadrive.com".equals(host) || host.endsWith(".xmegadrive.com");
    }

    private static String hostOnly(String url) {
        if (url == null || url.trim().isEmpty()) return "";
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null ? "" : host.toLowerCase(java.util.Locale.US);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String fingerprint(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(empty(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) out.append(String.format("%02x", b & 0xff));
            return out.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(empty(value).hashCode());
        }
    }

    private static String empty(String value) {
        return value == null ? "" : value;
    }

    private static void run(Runnable runnable) {
        if (runnable != null) runnable.run();
    }

    private interface StateCallback {
        void onState(State state);
    }

    private static final class Session {
        State before;
        boolean beforeCaptureStarted;
        Runnable revealAfterBefore;
    }

    private static final class State {
        String url = "";
        String captureNote = "";
        final Map<String, String> cookies = new LinkedHashMap<>();
        final Map<String, String> localStorage = new LinkedHashMap<>();
        final Map<String, String> sessionStorage = new LinkedHashMap<>();
    }
}
'''
Path('app/src/main/java/com/webapp/crazyshit/XMegaDriveAgeDiagnostics.java').write_text(helper)

test = r'''package com.webapp.crazyshit;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XMegaDriveAgeDiagnosticsTest {
    @Test public void reportShowsOnlyNamesAndChangeKinds() {
        Map<String, String> beforeCookies = new LinkedHashMap<>();
        beforeCookies.put("www.xmegadrive.com:cf_clearance", "fingerprint-a");
        beforeCookies.put("www.xmegadrive.com:age_gate", "fingerprint-old");
        Map<String, String> afterCookies = new LinkedHashMap<>();
        afterCookies.put("www.xmegadrive.com:cf_clearance", "fingerprint-a");
        afterCookies.put("www.xmegadrive.com:age_gate", "fingerprint-new");
        afterCookies.put("www.xmegadrive.com:age_verified", "fingerprint-secret");

        Map<String, String> beforeLocal = new LinkedHashMap<>();
        beforeLocal.put("age", "old-hash");
        Map<String, String> afterLocal = new LinkedHashMap<>();
        afterLocal.put("age", "new-hash");

        String report = XMegaDriveAgeDiagnostics.buildReportForTest(
                "https://www.xmegadrive.com/categories/?token=secret",
                beforeCookies,
                beforeLocal,
                new LinkedHashMap<>(),
                "https://www.xmegadrive.com/videos/#private",
                afterCookies,
                afterLocal,
                new LinkedHashMap<>()
        );

        assertTrue(report.contains("www.xmegadrive.com:age_gate"));
        assertTrue(report.contains("www.xmegadrive.com:age_verified"));
        assertTrue(report.contains("changed: age"));
        assertTrue(report.contains("Navigation: changed"));
        assertFalse(report.contains("fingerprint-old"));
        assertFalse(report.contains("fingerprint-new"));
        assertFalse(report.contains("fingerprint-secret"));
        assertFalse(report.contains("token=secret"));
        assertFalse(report.contains("#private"));
    }

    @Test public void storageScriptFingerprintsValuesBeforeReturningThem() {
        String script = XMegaDriveAgeDiagnostics.storageSnapshotScript();
        assertTrue(script.contains("out[k]=fp(store.getItem(k))"));
        assertTrue(script.contains("local:dump(window.localStorage)"));
        assertTrue(script.contains("session:dump(window.sessionStorage)"));
        assertFalse(script.contains("out[k]=store.getItem(k)"));
    }
}
'''
Path('app/src/test/java/com/webapp/crazyshit/XMegaDriveAgeDiagnosticsTest.java').write_text(test)

print('patched XMegaDrive age diagnostics')
