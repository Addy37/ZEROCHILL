from pathlib import Path
import re

path = Path("app/src/main/java/com/webapp/crazyshit/XMegaDriveBrowserFetch.java")
text = path.read_text()

old_grace = "private static final long NATIVE_AGE_FALLBACK_GRACE_MS = 4500L;"
if old_grace not in text:
    raise SystemExit("Expected old native age fallback grace constant")
text = text.replace(old_grace, "private static final long NATIVE_AGE_FALLBACK_GRACE_MS = 6500L;", 1)

safety_pattern = re.compile(
    r"static String ageGateSafetyScript\(\) \{.*?\n\}\n\n    static String ageGateNativeTapTargetScript",
    re.S,
)
safety_replacement = r'''static String ageGateSafetyScript() {
    return "const ageNorm=s=>String(s||'').replace(String.fromCharCode(8217),\"'\").replace(/\\s+/g,' ').trim().toLowerCase();"
            + "const ageDecline=s=>/\\b(exit|leave|decline)\\b/.test(s)||/under\\s*18/.test(s)"
            + "||/\\bi(?:'m| am|m) not\\b/.test(s)||s==='no'||s==='no thanks'||s==='not 18';"
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

    static String ageGateNativeTapTargetScript'''
text, count = safety_pattern.subn(safety_replacement, text, count=1)
if count != 1:
    raise SystemExit(f"Expected one ageGateSafetyScript block, replaced {count}")

bridge_pattern = re.compile(
    r"    static String ageGateNativeTapTargetScript\(\) \{.*?\n    private static XMegaDriveAgePrompt agePrompt",
    re.S,
)
bridge_replacement = r'''    static String ageGateNativeTapTargetScript() {
        return "(() => {"
                + "const norm=s=>String(s||'').replace(String.fromCharCode(8217),\"'\").replace(/\\s+/g,' ').trim().toLowerCase();"
                + "const decline=s=>/\\b(exit|leave|decline)\\b/.test(s)||/under\\s*18/.test(s)"
                + "||/\\bi(?:'m| am|m) not\\b/.test(s)||s==='no'||s==='no thanks'||s==='not 18';"
                + "const accept=s=>!!s&&!decline(s)&&(/\\bcontinue\\b/.test(s)||/\\benter\\b/.test(s)"
                + "||s==='yes'||/\\bi(?:'m| am|m) 18\\b/.test(s));"
                + "const body=(document.body&&document.body.innerText)||'';if(!/age confirmation/i.test(body))return '';"
                + "const controls=document.querySelectorAll('button,input[type=button],input[type=submit],a,[role=button]');"
                + "const label=el=>norm(el.innerText||el.textContent||el.value||el.getAttribute('value')||el.getAttribute('aria-label')||'');"
                + "for(let i=0;i<controls.length;i++){const el=controls[i];if(!decline(label(el)))continue;"
                + "el.style.display='none';el.setAttribute('aria-hidden','true');el.setAttribute('tabindex','-1');"
                + "try{if('disabled' in el)el.disabled=true;}catch(e){}}"
                + "for(let i=0;i<controls.length;i++){const el=controls[i];if(!accept(label(el)))continue;"
                + "try{el.scrollIntoView({block:'center',inline:'center'});}catch(e){}"
                + "const r=el.getBoundingClientRect();if(!r||r.width<=0||r.height<=0)return '';"
                + "return JSON.stringify({x:r.left+r.width/2,y:r.top+r.height/2,w:r.width,h:r.height,"
                + "vw:window.innerWidth||0,vh:window.innerHeight||0});}"
                + "return '';})()";
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
            int attempt
    ) {
        if (overlay == null || webView == null || prompt == null || prompt.isRawFallbackShown()) return;
        stageNativeAgeOverlay(overlay, webView);
        try {
            webView.evaluateJavascript(ageGateNativeTapTargetScript(), encoded -> {
                if (prompt.isRawFallbackShown() || webView.getParent() == null) return;
                AgeTapTarget target = decodeAgeTapTarget(webView, encoded);
                if (target != null) {
                    alignPhysicalAgeTarget(overlay, webView, prompt, target);
                    return;
                }
                if (attempt < 3) {
                    webView.postDelayed(
                            () -> preparePhysicalAgeBridge(overlay, webView, prompt, attempt + 1),
                            250L
                    );
                    return;
                }
                Log.i(TAG, "nativeAge physical bridge target unavailable");
                prompt.showRawFallback();
                revealRawAgeFallback(overlay, webView);
            });
        } catch (Exception ignored) {
            prompt.showRawFallback();
            revealRawAgeFallback(overlay, webView);
        }
    }

    private static XMegaDriveAgePrompt agePrompt'''
text, count = bridge_pattern.subn(bridge_replacement, text, count=1)
if count != 1:
    raise SystemExit(f"Expected one native age bridge block, replaced {count}")

show_pattern = re.compile(
    r"    private static XMegaDriveAgePrompt showNativeAgePrompt\(.*?\n    private static void dismissAgePrompt",
    re.S,
)
show_replacement = r'''    private static XMegaDriveAgePrompt showNativeAgePrompt(
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
        preparePhysicalAgeBridge(overlay, webView, created, 1);
        return created;
    }

    private static void dismissAgePrompt'''
text, count = show_pattern.subn(show_replacement, text, count=1)
if count != 1:
    raise SystemExit(f"Expected one showNativeAgePrompt block, replaced {count}")

old_call = "prompt = showNativeAgePrompt(webView, completed, error, finished);"
if old_call not in text:
    raise SystemExit("Expected old showNativeAgePrompt call")
text = text.replace(
    old_call,
    "prompt = showNativeAgePrompt(webView, overlay, completed, error, finished);",
    1,
)

reveal_pattern = re.compile(
    r"    private static void reveal\(FrameLayout overlay, WebView webView\) \{.*?\n    \}\n\n    private static boolean permanentlyBlocked",
    re.S,
)
reveal_replacement = r'''    private static void reveal(FrameLayout overlay, WebView webView) {
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

    private static boolean permanentlyBlocked'''
text, count = reveal_pattern.subn(reveal_replacement, text, count=1)
if count != 1:
    raise SystemExit(f"Expected one reveal block, replaced {count}")

if "dispatchNativeTap(" in text or "MotionEvent.obtain(" in text:
    raise SystemExit("Synthetic Android touch bridge is still present")
if "triggerNativeAgeContinue(" in text:
    raise SystemExit("Old native age trigger is still present")

path.write_text(text)

test_path = Path("app/src/test/java/com/webapp/crazyshit/XMegaDriveAgeGateSafetyTest.java")
test_path.write_text(r'''package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XMegaDriveAgeGateSafetyTest {
    @Test public void visibleFallbackHidesAndBlocksDeclineControlsWithoutSyntheticAccept() {
        String script = XMegaDriveBrowserFetch.ageGateSafetyScript();
        assertTrue(script.contains("ageDecline"));
        assertTrue(script.contains("style.display='none'"));
        assertTrue(script.contains("aria-hidden"));
        assertTrue(script.contains("tabindex"));
        assertTrue(script.contains("disabled"));
        assertTrue(script.contains("document.addEventListener('click'"));
        assertTrue(script.contains("stopImmediatePropagation"));
        assertTrue(script.contains("\\b(exit|leave|decline)\\b"));
        assertFalse(script.contains("el.click()"));
        assertFalse(script.contains("ageAccept"));
    }

    @Test public void nativePromptLocatesAndSizesRealAcceptControlWithoutSyntheticClick() {
        String script = XMegaDriveBrowserFetch.ageGateNativeTapTargetScript();
        assertTrue(script.contains("style.display='none'"));
        assertTrue(script.contains("aria-hidden"));
        assertTrue(script.contains("disabled"));
        assertTrue(script.contains("scrollIntoView"));
        assertTrue(script.contains("getBoundingClientRect"));
        assertTrue(script.contains("w:r.width"));
        assertTrue(script.contains("h:r.height"));
        assertTrue(script.contains("window.innerWidth"));
        assertTrue(script.contains("window.innerHeight"));
        assertTrue(script.contains("JSON.stringify"));
        assertTrue(script.contains("\\b(exit|leave|decline)\\b"));
        assertTrue(script.contains("i(?:'m| am|m) 18"));
        assertFalse(script.contains("el.click()"));
        assertFalse(script.contains("document.activeElement"));
    }
}
''')

print("patched physical gesture bridge")
