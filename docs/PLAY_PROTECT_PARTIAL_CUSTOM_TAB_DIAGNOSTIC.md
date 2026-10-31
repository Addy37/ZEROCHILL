# Play Protect partial Custom Tab diagnostic

This branch keeps the Play Protect-safe updater architecture while testing a more polished update handoff.

- No `REQUEST_INSTALL_PACKAGES` permission.
- No app-managed APK download, staging, or install flow.
- Update detection, notifications, release notes, snooze behavior, and update card remain intact.
- Selecting the update opens the official GitHub release page in a dark partial Custom Tab when supported by the user's browser.
- The browser remains responsible for any APK download.
- Full browser `ACTION_VIEW` remains the fallback when Custom Tabs are unavailable.

This branch is diagnostic only. Do not merge or release until physical Play Protect and UX testing are approved.
