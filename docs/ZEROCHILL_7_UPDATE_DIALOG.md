# ZEROCHILL 7 update prompt redesign

## Scope
Restyle the **existing update prompt** in `UpdateCardController` using the approved `WhatsNewDialog` as its visual reference. This is a UI-only change. The feature branch starts from `fix/zerochill-7-legacy-crash-regressions`, retaining its targeted startup/OnlyHaven/Bunkr fixes and New Zerochill 7.0 app routing.

## Behavior
- On automatic or manual update availability, show a centered modal-style panel over a dimmed app, replacing the previous compact top banner.
- Reuse What's New's OLED black, user-selected accent border, white `ZERO` and accent `CHILL` wordmark, large headline, accent version text, and readable multiline release notes.
- Keep a full-width accent **Download update** button and explicit **Later** button. Later still snoozes that release for 12 hours through the existing `AppUpdater` preferences.
- Preserve existing progress, cancel, retry, downloaded, preview-ready, and Open Downloads states using the **same** update card controller.
- Never call Android install directly, download silently to another location, change APK URLs, or bypass the existing download manager.
- The release summary stays bounded by the current `ReleaseNotesSummary` parser. When it returns two items separated by its middle-dot delimiter, present them as bullet lines. Fallback/paragraph text stays unmodified.
- Preserve accessibility-readable text and 48dp or larger button touch targets. Motion obeys the existing `ZeroChillMotion` setting. The panel is removed when the Activity/updater closes.
- Show the updated design only in the *newly installed APK*. Previously installed releases retain their own UI.

## Source and compatibility
Only `UpdateCardController.java`, its focused test, documentation, and the private test workflow change. Do not modify backend endpoints, source config, authentication, app ID, signing identity, file destinations, local data or persisted settings. All changes are UI-only.

## Validation
Run `UpdateCardControllerTest` and `ReleaseNotesSummaryTest`, compile release APK, verify production signing and same application ID, source lineage, version `7.0.2` (code `7000002`) over `7.0.1`, and New Zerochill-only backend. Use a private one-shot workflow and no public GitHub release.

The UI must still be smoke-tested on a real phone, including update prompt, Later, download progress, Cancel, retry and Open Downloads. A green build alone does not establish visual acceptance or successful download of a future release.
