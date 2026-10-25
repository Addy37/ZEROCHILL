from pathlib import Path
import re

browser = Path('app/src/main/java/com/webapp/crazyshit/XMegaDriveBrowserFetch.java')
text = browser.read_text()

old = '    private static final long NATIVE_AGE_FALLBACK_GRACE_MS = 6500L;\n'
new = old + '    private static final long NATIVE_AGE_TARGET_DISCOVERY_TIMEOUT_MS = 12000L;\n' + '    private static final long NATIVE_AGE_TARGET_RETRY_MS = 300L;\n'
if old not in text:
    raise SystemExit('missing fallback grace constant')
text = text.replace(old, new, 1)

script_pattern = re.compile(r'    static String ageGateNativeTapTargetScript\(\) \{.*?\n    \}\n\n    private static final class AgeTapTarget', re.S)
script_replacement = r'''    static String ageGateNativeTapTargetScript() {
        return "(() => {"
                + "const norm=s=>String(s||'').replace(String.fromCharCode(8217),\"'\").replace(/\\s+/g,' ').trim().toLowerCase();"
                + "const decline=s=>/\\b(exit|leave|decline)\\b/.test(s)||/under\\s*18/.test(s)"
                + "||/\\bi(?:'m| am|m) not\\b/.test(s)||s==='no'||s==='no thanks'||s==='not 18';"
                + "const accept=s=>!!s&&!decline(s)&&(/\\bcontinue\\b/.test(s)||/\\benter\\b/.test(s)"
                + "||s==='yes'||/\\bi(?:'m| am|m) 18\\b/.test(s));"
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

    private static final class AgeTapTarget'''
text, count = script_pattern.subn(script_replacement, text, count=1)
if count != 1:
    raise SystemExit(f'failed replacing native target script: {count}')

prepare_pattern = re.compile(r'    private static void preparePhysicalAgeBridge\(.*?\n    \}\n\n    private static XMegaDriveAgePrompt agePrompt', re.S)
prepare_replacement = r'''    private static void preparePhysicalAgeBridge(
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

    private static XMegaDriveAgePrompt agePrompt'''
text, count = prepare_pattern.subn(prepare_replacement, text, count=1)
if count != 1:
    raise SystemExit(f'failed replacing prepare bridge: {count}')

old_call = '        preparePhysicalAgeBridge(overlay, webView, created, 1);'
new_call = '        preparePhysicalAgeBridge(overlay, webView, created, android.os.SystemClock.elapsedRealtime());'
if old_call not in text:
    raise SystemExit('missing preparePhysicalAgeBridge call')
text = text.replace(old_call, new_call, 1)

if 'attempt < 3' in text:
    raise SystemExit('old three-attempt discovery remains')
if 'MotionEvent.obtain' in text or 'dispatchNativeTap' in text or 'el.click()' in text:
    raise SystemExit('synthetic activation unexpectedly present')

browser.write_text(text)

test = Path('app/src/test/java/com/webapp/crazyshit/XMegaDriveAgeGateSafetyTest.java')
t = test.read_text()
needle = '''        assertTrue(script.contains("JSON.stringify"));\n        assertFalse(script.contains("el.click()"));'''
replacement = '''        assertTrue(script.contains("JSON.stringify"));\n        assertTrue(script.contains("shadowRoot"));\n        assertTrue(script.contains("contentDocument"));\n        assertTrue(script.contains("depth>5"));\n        assertFalse(script.contains("el.click()"));'''
if needle not in t:
    raise SystemExit('missing target test insertion point')
t = t.replace(needle, replacement, 1)
test.write_text(t)

print('patched XMegaDrive target discovery')
