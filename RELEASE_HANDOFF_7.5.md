# ZEROCHILL 7.5 release handoff

## Final copy and publication authorization: 7005085

Owner approved the 7005084 phone candidate and both condensed release summaries and explicitly authorized 7.5.0 publication. Candidate signed build, focused tests, and Android upgrade checks passed in run 45939140399. The final APK increments the version code to 7005085 so it updates the approved phone candidate and changes only the bundled eight highlights, update summary, and related validation/metadata. Full release validation must pass before publication. Existing eFukt and Balbums provider-audit failures remain documented and are not modified. No app ID, signing, storage, backend, source config or UI layout changes.

## Candidate 7005084: More menu redesign

Owner explicitly approved the ShitTok and Shows Save To update and requested the compact More menu mockup. Incorporates PR #501's two-column grid, theme accents, active-download and unread badges, secondary actions, and anchored panel. Existing callbacks and player save behavior preserved. Production package, signing, backend, and data unchanged. Added MoreMenuLayoutTest and retained SupportZeroChillDialogTest in focused CI. Previous 7005083 signed artifact was built but its emulator upgrade test failed. New signed candidate and physical testing pending; no production merge or publication authorized.

## Candidate 7005083: save sheet polish

Owner confirmed build 7005082's save functionality and requested a dedicated visual treatment. The new Save to sheet uses two natural-height rows, 48dp icon tiles, an accent-colored collection action, saved checkmark, and a portrait bottom sheet or landscape side panel. It retains the existing callbacks, picker, and storage. This supersedes the old APK for acceptance. The 7005082 focused tests, signing and artifact upload passed; its emulator upgrade job failed and must not be counted as passed. Build 7005083 requires fresh signed verification and phone acceptance. No publication authorized.

## Superseding candidate: 7005082

Owner passed all nine phone checks on build 7005081, then approved a direct Save to menu for ShitTok and Shows. Commit `17b9310` adds one-tap Watch Later and Add to collection actions, including fullscreen, using the existing collection picker and local media-favorite storage. The previous phone-tested APK and emulator results below are historical, not approval of build 7005082. No app ID, signing, backend, schema or provider changes. The existing candidate workflow must generate and verify a new signed artifact and repeat upgrade checks. Focused `PlayerSaveMenuTest` covers explicit selection and collection membership without Watch Later; results are pending. Re-test both players, rotation, playback, existing collection membership, Watch Later independence, new collection creation, and cancel. Full production suites remain pending. Do not publish or merge without owner approval.

**Phone testing is ready. Publication status — NO-GO.** The signed APK and
both offline upgrade scenarios passed their gates. Keep the PR draft until
the phone checks and full production suites have been reviewed. The owner
must explicitly authorize publication.

- Branch [release/zerochill-7.5.0-preparation](https://github.com/Addy37/ZEROCHILL/tree/release/zerochill-7.5.0-preparation)
- Draft [PR #500](https://github.com/Addy37/ZEROCHILL/pull/500)
- Last app implementation checkpoint [e0adb7435d284ef2170ab74ef865cac16fa7c62f](https://github.com/Addy37/ZEROCHILL/commit/e0adb7435d284ef2170ab74ef865cac16fa7c62f). The branch tip includes the handoff and the follow-up for emulator-only validation. Use the APK run's full source SHA as the artifact identity.
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
- [Signed phone-test APK](https://github.com/Addy37/ZEROCHILL/actions/runs/45928943983/artifacts/15168070843), source `7a28dd47ac0b0342bfba989c738211007ffe5664`, APK SHA-256
  `a6f5d5c9188df32204b56d520901f59ff08f58b686416f16ec2de01fb7423b35`.
  The certificate, package, version, bundled notes, production routing and
  embedded commit checks passed. The [focused release evidence](https://github.com/Addy37/ZEROCHILL/actions/runs/45928943983/artifacts/15168455434)
  records **144 tests passed** across 18 test classes, with zero failures,
  errors and skips.
- The first 7.4.0 emulator upgrade installed in place, preserved UID and
  checked seed bytes before launch. The post-launch UI check was blocked by
  an unrelated **Pixel Launcher isn't responding** dialog in the captured UI
  tree. This is an incomplete upgrade run. The emulator-only follow-up
  disables that launcher and reuses the exact verified APK, checking its
  checksum and source and requiring an unchanged app tree.
  [First emulator-only retry](https://github.com/Addy37/ZEROCHILL/actions/runs/45930148966)
  reused the APK without rebuilding and again passed installation checks.
  UIAutomator returned a null root after the launcher was disabled. The
  follow-up wakes/unlocks the emulator, retries the UI lookup, saves per-stage
  evidence and continues to the remaining scenarios if an earlier one fails.
  [Upgrade run with diagnostics](https://github.com/Addy37/ZEROCHILL/actions/runs/45930543221/artifacts/15168991101)
  fully passed 7.4.0 data retention, existing download visibility, upgraded
  What's New acknowledgement and fresh install wizard launch. 7.3.0
  passed in-place installation and seeded byte retention before launch.
  The second emulator installation got a different UID and inherited stale
  SELinux labels on manually pushed preferences. Logs show EACCES on those
  fixtures before the wizard appeared. The harness now assigns and verifies
  the fresh app-data label before rerunning the two scenarios with the same APK.
- [Final Android 15 upgrade run](https://github.com/Addy37/ZEROCHILL/actions/runs/45931003615)
  completed successfully at `1bfa643d26c7806f6ad51dfa0b8af795579be766`
  using the same signed APK. [Upgrade evidence](https://github.com/Addy37/ZEROCHILL/actions/runs/45931003615/artifacts/15168862758)
  includes passing 7.4.0 and 7.3.0 result files and the fresh-install wizard
  result. Both in-place installs retained first-install time, UID, seeded
  local media/settings and visible downloads. What’s New appeared and was
  acknowledged in both upgrade cases.
- Follow the [latest branch run](https://github.com/Addy37/ZEROCHILL/actions/workflows/zerochill-7-5-0-candidate.yml?query=branch%3Arelease%2Fzerochill-7.5.0-preparation).
  The signed artifact contains one APK, `SHA256SUMS.txt`,
  `BUILD_LINEAGE.txt` and `VALIDATION.json`. A later emulator-only run uses the
  same signed APK and may finish after the build run.
- The emulator step attempts offline Android 15 upgrades from public 7.4.0 and
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

1. Phone-test the linked APK. Start with a normal 7.4.0 upgrade and the
   ShitTok fullscreen checklist, then verify authenticated state and real
   push delivery. Validate landscape insets and reduced motion on physical
   hardware. Keep the recorded APK checksum and full source SHA.
2. If acceptance finds a problem, fix it on PR #500's branch and run the
   targeted regressions before pushing. A code change requires a fresh
   signed artifact. Workflow-only reruns can reuse the exact APK when the
   verified source comparison and checksum pass.
3. Before publication, complete full debug and release test suites and the
   remaining device checks, record results, and obtain explicit owner approval.
   The standing publication workflow is manual. Keep publication, tags,
   provider changes and the updater gated during phone acceptance.
