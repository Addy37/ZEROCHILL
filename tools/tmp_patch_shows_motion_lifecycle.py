from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "app/src/main/java/com/webapp/crazyshit/VideoDetailActivity.java"
text = PATH.read_text()

old = '''                buildPlayer(requestedStartPosition);\n                if (showsSession != null && showsSession.related(pageUrl).isEmpty()) {\n                    String scheme = Uri.parse(mediaUrl).getScheme();\n                    if (!"content".equalsIgnoreCase(scheme) && !"file".equalsIgnoreCase(scheme)) {\n                        loadRelated();\n                    }\n                }\n'''
new = '''                startResolvedDeferredShowsPlaybackIfResumed();\n'''
if text.count(old) != 1:
    raise RuntimeError(f"resolver playback block count={text.count(old)}")
text = text.replace(old, new, 1)

marker = '''    private void buildPlayer(long startPosition) {\n'''
helper = '''    private void startResolvedDeferredShowsPlaybackIfResumed() {\n        if (!showsOrigin || deferredShowsResolve || !recoveryResumed || player != null ||\n                mediaUrl == null || mediaUrl.trim().isEmpty()) return;\n        buildPlayer(requestedStartPosition);\n        if (showsSession != null && showsSession.related(pageUrl).isEmpty()) {\n            String scheme = Uri.parse(mediaUrl).getScheme();\n            if (!"content".equalsIgnoreCase(scheme) && !"file".equalsIgnoreCase(scheme)) {\n                loadRelated();\n            }\n        }\n    }\n\n'''
if text.count(marker) != 1:
    raise RuntimeError(f"buildPlayer marker count={text.count(marker)}")
text = text.replace(marker, helper + marker, 1)

old = '''        super.onResume();\n        recoveryResumed = true;\n        if (showsOrigin && fullscreenChromeActive && fullscreenControls != null)\n'''
new = '''        super.onResume();\n        recoveryResumed = true;\n        startResolvedDeferredShowsPlaybackIfResumed();\n        if (showsOrigin && fullscreenChromeActive && fullscreenControls != null)\n'''
if text.count(old) != 1:
    raise RuntimeError(f"onResume marker count={text.count(old)}")
text = text.replace(old, new, 1)

PATH.write_text(text)
print("Shows deferred playback lifecycle fix applied")
