# ZEROCHILL 7.5 release handoff

**Publication status — NO-GO.** This branch is for a signed phone-test APK. The
owner must explicitly authorize publication. Keep the PR draft until the
current build and phone checks have been reviewed.

- Branch [release/zerochill-7.5.0-preparation](https://github.com/Addy37/ZEROCHILL/tree/release/zerochill-7.5.0-preparation)
- Draft [PR #500](https://github.com/Addy37/ZEROCHILL/pull/500)
- Last app implementation checkpoint [e0adb7435d284ef2170ab74ef865cac16fa7c62f](https://github.com/Addy37/ZEROCHILL/commit/e0adb7435d284ef2170ab74ef865cac16fa7c62f). The current branch tip adds this handoff and narrows the phone-build workflow. Use the Actions run's full source SHA as the APK identity.
- Verified original base [23e3229adb7b997d66f9298988f82a4277351e7f](https://github.com/Addy37/ZEROCHILL/commit/23e3229adb7b997d66f9298988f82a4277351e7f) from merged PR #499. The app tree at that base matches the owner-approved Beta 14 source `873129316cc37c6b204a0a1657fbc8852e37df1d`.
- [Current phone-candidate runs for this branch](https://github.com/Addy37/ZEROCHILL/actions/workflows/zerochill-7-5-0-candidate.yml?query=branch%3Arelease%2Fzerochill-7.5.0-preparation)

## Objective and scope

Prepare ZEROCHILL 7.5: The Experience Update from approved Beta 14. Audit
stability, preserve public-install data and production signing, coordinate
release messaging, and provide one Actions artifact for phone acceptance.
The dedicated landscape ShitTok correction is included. Keep approved
portrait and Shows behavior, providers, fallbacks and production backend.
With remaining allowance limited, focused regressions and the phone-test APK
take precedence over comprehensive validation.

## Confirmed defects and implementation

| Severity | Defect and correction | Source |
| --- | --- | --- |
| Critical | None confirmed. | |
| High | Landscape ShitTok inherited the portrait gesture route and the pager could be re-enabled by other callbacks. The progress line was governed separately from landscape controls. A dedicated fullscreen touch surface uses the shared Shows tap and accumulated seek implementation. A single overlay owns the title, creator, controls, gradients and timeline. Pager input and auto-advance are gated, and Back restores the feed and adaptive shell. The same selected player, position and speed are retained. | `ChaosFeedView.java` (fullscreen entry/exit, shared seek bar, control visibility and pager gate), `FullscreenVideoGestures.java`, `NativeMainActivity.java`, `MainPagerAdapter.java`, `LandscapeUiController.java`, `ShitTokShareSheet.java` |
| High | A 7.4.0 production Android 14 launch crashed when push registration accessed an unavailable default Firebase app. The client now attempts standard initialization and safely handles missing Firebase in registration and logout cleanup. | [ad1e1331](https://github.com/Addy37/ZEROCHILL/commit/ad1e13312984f9c480fe6b294c6c0543c16b45c7), `ZeroChillPushRegistration.java` |
| High | A DM thread over 500 entries selected its oldest messages and omitted new ones after refresh. It now loads the newest 500 deterministically and presents them in chronological order. Older messages remain on the server. | `ZeroChillSocialRepository.recentDirectMessagesPath` and `chronologicalThread` |
| Medium | The packaged copy still described 7.4.0. A one-time source access repair could also drop the ordinary launcher marker and postpone What’s New. Updated the release copy and preserved eligibility through the repair. | `XMegaDriveAccessSetupActivity.completeLaunch`, `RELEASE_NOTES.md`, `app/src/main/res/raw/whats_new_7_0.md` |
| Medium | Backup imports did not validate two optional source URL fields. Validation now happens before restore writes. Regression coverage verifies collections and imports where the version 1 collection key is absent. | `BackupDocument.validate`, `CreatorBackupTest` |

See [the audit and phone checklist](docs/ZEROCHILL_7_5_RELEASE_PREPARATION.md).
Historical WorkManager initialization, OnlyHaven checked response and Bunkr
resolver null guards are present. Historical OEM-only layout and memory
reports remain unresolved without a reproducible device trace. There is no
confirmed fullscreen defect left open at the source checkpoint.

## Actual tests and failed attempts

- Local focused debug run after the Firebase correction: **66 tests passed**.
  Coverage included Firebase, DMs, backup/collections, launch and notes, seek
  ownership, gallery preparation, and Spotlight. Local Robolectric needed
  a writable test home because the default dependency-resolver lock path
  was read-only. This was an external test invocation change.
- After fullscreen: **79 Android 35 focused debug tests passed** with zero
  failures, errors or skips. They exercised tap and overlay visibility,
  playback buttons, accumulated left/right seeking, swipe suppression,
  timeline drag, disabling unknown duration, delayed rotation after Back,
  speed menu, portrait restoration, existing Shows controls, accent and
  reduced motion. The whole debug and release suites are still required for
  production validation.
- Four portrait Focus Mode assertions also failed on the pre-fullscreen
  baseline. The fake clip identities had no accepted Chaos provider; the
  genuine paging gate rejected them. Shared offline fixtures now label
  them as Kaotic while using the same offline URLs and preserving the
  assertions. Early fullscreen touch tests also needed their window
  configuration to settle before sending events. The final focused run
  passed the corrected fixtures.
- An initial offline Gradle check could not resolve the uncached LibVLC
  archive. Online dependency resolution recovered. A first fullscreen
  compile caught locally fixed Java name conflicts.
- Notes validation, YAML parsing, upgrade-script syntax and Git diff checks
  passed. Physical phone and tablet rendering, live push/session validation,
  and lower-end long-session profiling are pending.
- [Provider contract audit](https://github.com/Addy37/ZEROCHILL/actions/runs/45926075929)
  had two failing probes. Balbums discovery and search returned HTTP 403.
  eFukt CI responses had no canonical video links. Bunkr active index,
  album/media links and signed or direct resolution passed. Keep working
  fallbacks and verify failing paths on the phone.

## Signing, upgrade and build links

- Production target `7.5.0` / `7005081`, package
  `com.addy37.crazyshitunofficial`. The production certificate expected by
  CI is SHA-256
  `04abf84b3d468cbb1a562dea83b0007e686b2e56018cce911223963faea51356`.
- Actual public 7.4.0 and 7.3.0 APKs were inspected. Their version codes are
  `7004000` and `7003000`, and both use that package and certificate.
  The owner-approved 7005080 Beta 14 uses a separate `.dev` package.
- [Beta 14 signed run](https://github.com/Addy37/ZEROCHILL/actions/runs/45923649467)
  passed. [Earlier candidate run](https://github.com/Addy37/ZEROCHILL/actions/runs/45926075862)
  predates the fullscreen change and must not supply the test APK.
  [Original fullscreen source run](https://github.com/Addy37/ZEROCHILL/actions/runs/45928763769)
  is superseded by the focused phone-candidate workflow.
- Follow the [latest branch run](https://github.com/Addy37/ZEROCHILL/actions/workflows/zerochill-7-5-0-candidate.yml?query=branch%3Arelease%2Fzerochill-7.5.0-preparation).
  Download the signed artifact only from the run whose source is the
  handoff/workflow checkpoint or a later reviewed source. The signed artifact
  contains one APK, `SHA256SUMS.txt`, `BUILD_LINEAGE.txt` and `VALIDATION.json`.
  At this checkpoint, production signing and emulator results for that
  candidate are pending.
- The same run attempts offline Android 15 upgrades from public 7.4.0 and
  7.3.0 after APK verification. Check separately uploaded evidence and
  `result.json` files. The emulator fixture checks app UID, files, downloads,
  saved media, history and settings, upgrade launch, and fresh onboarding.
  It does not establish live account session or server-side favorite/block
  retention. Public 7.4 also predates custom collections.

## Supabase and release copy

Production logs measured 10,790 successful video-summary calls, 2,594
visible DM calls, and 362 HTTP 401 responses across 465 push registration
POSTs in 24 hours. The push response pattern has no proven cause. Existing
coalescing, backoff, notification delivery and account routing are retained.
The Android 14 startup crash traceback is separate evidence from the 401
responses. No project switch, migration, deploy or private credential change
is included. Recheck real push delivery before deciding whether to change
retry or authentication behavior.

[GitHub changelog and updater summary](RELEASE_NOTES.md) and
[bundled What’s New](app/src/main/res/raw/whats_new_7_0.md) are prepared.
The updater uses the opening paragraph and the installed dialog has 15
highlights. The public updater continues to point to the published release
until publication is explicitly authorized.

## Next steps for ChatGPT

1. Open PR #500 and inspect the latest **ZEROCHILL 7.5.0 Signed Release
   Candidate** Actions run. Verify its head SHA, branch, APK identity and
   checksum from `VALIDATION.json` before sharing the direct artifact URL.
2. If the run fails, inspect the exact failing step. Fix legitimate code or
   fixture failures in the existing branch and push one coherent correction.
   The normal `pull_request` synchronize trigger runs again. Keep the package,
   signing gate, source gate and tested assertions intact.
3. Check the seeded emulator upgrade result separately. If it is incomplete,
   rerun it or reproduce the specific step before claiming retained data.
   Use the audit's phone checklist with the exact APK. Prioritize fullscreen
   behavior, existing session and data, and live push on the production package.
4. Before publication, complete full debug and release test suites and the
   remaining device checks, record results, and obtain explicit owner approval.
   The standing publication workflow is manual. Do not merge, tag, dispatch
   publication, change remote provider configuration or activate the updater
   during phone acceptance.
