# Phase 4 visual consistency candidate

## Baseline and scope

Base: `feature/zerochill-7-shared-chrome-phase4` at `3835f159`, the merge
of physically approved navigation PR #409. Candidate branch:
`fix/zerochill-phase4-visual-consistency`. This is an unmerged test candidate.
OLED black and cyan remain the defaults, with all seven existing accent choices.

The existing AppearanceTheme/preferences and native/Compose renderers remain the
owners. No navigation, messaging, source/provider, backend, persisted key/format,
dependency, package or signing-identity migration is introduced.

## Changed surfaces

- Existing color-only native theme overlays now reach every Activity, including
  media viewers, without replacing their window/transition theme. An explicit
  cyan overlay restores the default when changing from another accent.
- Shared input focus borders, text cursors, checkbox/radio selection, dialog list
  highlights and player scrubbers resolve the selected accent. Checked marks use
  an on-accent foreground, including the white palette.
- Native browse/search favorites, creator headers/cards/merge sheet, gallery
  favorite menus, saved/download indicators, feedback/rating controls, account
  security action text, onboarding controls and progress indicators use the
  existing AppearanceTheme palette instead of fixed UiPalette cyan.
- ShitTok favorites/likes/seek controls, Shows fullscreen favorites/likes and
  timeline, detail/mini-player progress and media composer actions follow accent.
  Message/inbox bubbles and actions already used the existing theme; the working
  message logic is unchanged.
- PreferenceGlassPanel and shared Compose menu rims respect edge highlights.
  Popup glass applies vibrancy and skips backdrop sampling for Off/Fast. Zero blur
  and refraction avoid unnecessary effects; effect caching tracks individual
  settings. Popup sizing, placement, item identity, callbacks and dismissal remain
  intact. Transparency, tint, motion, preset keys and advanced ranges are retained.
- UiPolishController no longer overwrites themed spinners with fixed cyan.

Brand wordmarks/mascot artwork and Android system notification/channel colors
remain intentionally cyan. Semantic warning, destructive and success colors
retain their meaning. Static fallback panels retain their established readable
surfaces; no new backdrop capture is added to scrolling rows.

## Validation and evidence

New coverage: AccentCoverageTest checks seven palettes on native host and dialog
contexts, shared controls, cyan restoration, OLED background and re-created host
preference retention. PreferenceGlassSettingsTest checks Off/Fast optical bypass,
quality restore and custom edge settings. Activity recreation tests are not a
claim of physical process-restart testing.

The isolated `phase4-visual-consistency-apk.yml` workflow runs focused appearance,
menu, media, messaging and #409 navigation/work-bound regressions, then one
`:app:assembleRelease` using existing production signing. Preview 12 version code
`6000014` exceeds Preview 11 (`6000013`); the workflow verifies the stable package,
production certificate and client configuration and exports a branch/SHA-named
APK with SHA-256. It does not publish a release or merge a PR. Executed results
and artifact links are recorded in the PR.

## Physical acceptance still required

No device/emulator is attached in this task. Unit tests/builds cannot establish
real glass appearance, popup positioning over moving content, hardware shader
compatibility, scrolling/gesture smoothness, or frame-time/battery performance.
Before APK approval, check:

1. All primary tabs, search fields/results, Library, account, feedback, dialogs,
   gallery menus and Messages with cyan, a saturated accent and white; cycle all
   seven accents and restore cyan.
2. Change accent/advanced glass, force-stop and restart; verify settings remain.
3. Off/Fast/Balanced/Quality and custom intensity/transparency/tint/blur/
   refraction/vibrancy/edge/split; reduced motion; busy/bright popup backdrops.
4. Rapid primary-tab taps and Back, navbar drag, retained list scroll, Search
   IME-first Back, fullscreen exit and return from Settings/Messages/Notifications.
   Confirm #409's approved responsiveness and playback remain intact.

Do not merge until the user approves the signed APK.
