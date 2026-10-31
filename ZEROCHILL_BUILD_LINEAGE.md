# ZEROCHILL build lineage

This file identifies the current approved ZEROCHILL product baseline for test APKs and future feature work.

## Unreleased filtered ShitTok hotfix candidate

GitHub `main` was verified at `1fa925f4806d21a1cfefc6d98158ca2cb7f14a51`
before starting `hotfix/5.6.1-filtered-shittok`. This is a descendant of the user's
approved 5.6.0 baseline, not the older release preparations below. Stable release
metadata stays unchanged. The existing signed-candidate workflow produces a
branch/SHA-named Beta and a release-package upgrade candidate for testing only.
See `docs/SHITTOK_FILTERED_HOTFIX.md` for architecture and compatibility.
No merge, 5.6.1 publication, backend deployment or source-config change is authorized.
Physical approval on the user's OnePlus CPH2655 is pending.
The physical-test rescue continues PR #376 from verified head
`89f2563c1457f462858bdcdd4448cd457c053a5b`; it adds the requested All/OnlyFap
candidate restriction and targeted refill/harvest/metadata scheduling fixes. Build
and artifact records on the draft PR identify the final source SHA. Do not merge
or publish 5.6.1 before the new physical Beta is approved.
The user physically rejected `33f2a3eec385516946fbf08d422a5e1ab932ac5c`
on October 31. Its targeted follow-up continues that exact PR head; passing unit or
emulator checks do not supersede the failed device acceptance. See the hotfix document.

## Current release preparation: 5.2.1

- Source-of-truth branch: `main`
- Approved application baseline: `779e3752a4b2c76c0b2e8dc60aebd2a1a66eb85c` (merged PR #339)
- Release preparation branch: `release/zerochill-5.2.1`
- Previous stable release: `v5.2.0` (`5002000`)
- Release target: `5.2.1` (`5002001`)
- User publication authorization: 2026-10-24

The user approved and merged the Search visual candidate from
`bb7d1387f68c301acaaa6b47923560997d6086d6`, then authorized public release.
Current main carries the same application tree. This release also includes the
approved XMegaDrive integration/lifecycle/quality work and scoped Search/playback
changes from PRs #333 and #335 through #339 since stable 5.2.0.

Signed Upgrade Candidate #987 passed focused Search/material/geometry/scope/overlay
regressions, full debug and release unit tests, both APK builds, production signing
checks, and an Android 15 in-place upgrade from stable 5.2.0 with its first-install
record preserved. The directly inspected signed Beta matches the final source SHA,
CI checksum, production certificate, and prior approved Beta package/version.
The separate automatic build and performance workflows were still running when
publication was requested; no quantitative performance result is claimed.

This preparation changes only release version, notes, and this lineage record.
The existing production release workflow rebuilds and tests the approved code,
verifies package `com.addy37.crazyshitunofficial`, the stable certificate, and a
higher version code, then publishes `ZeroChill.apk` and `SHA256SUMS.txt` as `v5.2.1`.
Use the successful publication run, tag, and checksum as the final release record.
No data migration, backend deployment, Supabase, or remote-config change is needed.

## Prior release preparation: 5.0.1

- Source-of-truth branch: `main`
- Integration branch: `main`
- Approved product baseline commit: `c1a9e486988c99c67839670c9da120fae745c897`
- Release preparation branch: `release/zerochill-5.0.1`
- Latest approved feature PRs: #314 (creator/gallery handoff), #315 (OnlyFap Library creator context), #316 (BaddieHub titles), and #317 (search results and recent searches)
- Approval and publication authorization date: 2026-10-19
- Previous stable release: `v5.0.0`
- Release target: `5.0.1` (`5000001`)

The user physically approved the search candidate at `0860f0e4574e79e072526075e4059852843d9c26` and approved its merge through PR #317. Main at `c1a9e486` has the identical application tree and includes all approved work since 5.0.0. The candidate passed 44 focused search/source/geometry checks, 871 tests per debug/release variant (zero failures/errors, one skipped each), both APK builds, production signing checks, and an Android 15 in-place upgrade from stable 5.0.0. Physical testing and publication are authorized. The existing non-blocking lint audit reports 321 errors and 418 warnings.

The 5.0.1 preparation changes release metadata and this lineage record only. The production release workflow rebuilds and tests the approved application code, verifies the package and stable signing certificate, checks that version code `5000001` upgrades from 5.0.0 (`5000000`), and publishes `ZeroChill.apk` plus `SHA256SUMS.txt`. Use the successful production workflow, final `v5.0.1` tag, and its published checksum as the authoritative release record. No existing data migration, backend deployment, or remote-config change is required; recent searches use a new optional local preference file.

ZeroChill v5.0.0 was published on 2026-10-18 with production APK SHA-256 `bc8e6d481b9e7e4e0c67a66cddbb0bb3664b241b0260c29e155d7c638acd520b`.

## Unreleased native 5.1.0 development

`feature/zerochill-5.1.0` / draft PR #321 starts from approved main
`da9b00588e6ecc370327cad75209e757475c2370` (the released 5.0.1 merge).
The user abandoned the 6.0 Compose migration experiment; PR #320 and its feature
branch are not a source for this work. The approved product baseline remains 5.0.1.
See `ZEROCHILL_5_1_NATIVE.md` for native feature behavior and validation scope.
Signed testing uses the existing Beta artifact convention, with branch and source
SHA in the APK filename. No production release, tag or merge is authorized.

## Prior release preparation: 5.0.0

The 5.0.0 candidate preserves the physically approved application tree at `2f9a489a`. PRs #311 and #312 were closed CI-only drafts and are excluded from release lineage. Main-only history changes README and historical lineage documentation; the README already matches the approved branch, and obsolete 3.1.0 lineage instructions are superseded by this current record. The release branch merges main history without changing approved application code. No backend, remote-config, schema, or data migration is introduced. Signed candidate validation and artifact checks are recorded in the release PR. This is preparation only: no final tag or GitHub release has been created. `rebrand/zerochill` remains the source of truth until the approved integration into main.

ZeroChill v4.3.8 contains the device-approved temporary ShitTok pinch from PR #291, primary-tab activation cleanup from PR #292, and notification-entry fix from PR #293. The complete approved functional baseline is `ec1dda86`; release metadata adds version 4.3.8 and plain-language notes without changing the approved application code. The final device-tested candidate `2fd5f0c2` passed 576 debug and 576 release tests with one skipped in each, both APK builds, production signing/package verification, and an Android 15 in-place upgrade from stable 4.3.7 to preview 4.3.8. The user approved publication on October 13. The release workflow rebuilds, verifies, and publishes the stable APK with version code `4003008`, the existing application ID and signing identity, and a checksum. Use tag `v4.3.8` and its successful publication run as the authoritative stable release source and checksum. No data migration, backend, Supabase, or remote-config change is required. Lint remains an audit with 291 errors and 385 warnings; the new warning concerns translating the loading text. No quantitative performance improvement is claimed from the failed emulator comparisons. Keep `integration/zerochill-current` synchronized with the stable release source after successful publication.

Previous stable release: v4.3.7, source `d46516d8b9319d32a926bff9e13332841e0d094f`, published October 12 in the user's timezone. Its production APK checksum is `7750e56e57c93aaa5cb58090ed88ca0ed326a657b3841444b9fc47f97aed8799`.

ZeroChill v4.3.4 was published from `02215d6` after release PR #277. This release includes the device-approved app-wide ZEROCHILL UI consistency polish from PR #271, optional ShitTok Auto Scroll and cleaner fullscreen controls from PR #272, the landscape timed scrubber and fullscreen spacing/readability polish from PR #273, Search no-results/fallback cleanup from PR #274, Shows fullscreen parity with ShitTok from PR #275, and the final performance/stability pass from PR #276. The ShitTok optimization removed duplicate selected-video preparation, reducing the controlled cached-selection dispatch count from 200 to 100 per 100 selections without changing persisted playback behavior. Build Android APK #1811 and Signed Upgrade Candidate #683 passed before publication. The production release workflow #119 ran `:app:testReleaseUnitTest :app:assembleRelease`, rebuilt and signed `ZeroChill.apk`, verified package `com.addy37.crazyshitunofficial`, production signing certificate SHA-256 `04abf84b3d468cbb1a562dea83b0007e686b2e56018cce911223963faea51356`, version code `4003004`, and upgrade compatibility from v4.3.3, then published tag `v4.3.4` with `SHA256SUMS.txt`. The published APK SHA-256 is `d86d81c8ba2588f8dc9c05a963187ebd5f8f7782af1d8dbf03630c28c8d20938`. The final long ShitTok benchmark passed and nine of ten final performance/profile jobs passed; `shows-scroll-motion` lost its CI emulator rather than recording an app crash or ANR. The release was physically approved on the OnePlus 13 / Android 16. No Supabase migration, backend security-boundary change, or remote-config/content-source change was required for this release. The previous v4.3.3 release source is preserved at `checkpoint/zerochill-approved-323cc13`.

ZeroChill v4.3.3 was published from `323cc13` after release PR #269. This account/social polish release includes CI analytics isolation from PR #265, the device-approved profile avatar crop/upload/refresh and Messages/Notifications cleanup from PR #266, merged-creator identification from PR #267, and the signed-in header avatar, personal social hub, and multi-select conversation cleanup from PR #268. The production `conversation_cleanup` Supabase migration was already applied before release. The production workflow rebuilt and signed `ZeroChill.apk`, verified package `com.addy37.crazyshitunofficial`, production signing identity, version code `4003003`, and upgrade compatibility from v4.3.2, then published tag `v4.3.3` with `SHA256SUMS.txt`. The published APK SHA-256 is `0460614ab0353e5a28091c74dc61d26eae7ab19757110dffca176a7012d05da8`. The final device-tested beta came from `0c1bbfd`; the release PR changed only release metadata. Build Android APK #1766 and Signed Upgrade Candidate #647 passed before publication, and production release workflow #118 completed successfully. Two PR #268 performance jobs lost their emulators and produced no usable result; the related release behavior was approved on the OnePlus 13 and does not change those playback paths. The previous v4.3.2 release source is preserved at `checkpoint/zerochill-approved-fde995d`.

ZeroChill v4.3.2 was published from `fde995d` after release PR #263. This maintenance polish release carries the device-approved floating-navbar inset/alignment fixes from PRs #257 and #258, the OnlyFap gallery expansion, Android rotation-policy, blurred-preview/media-fitting, and electric-blue video-loading work from PRs #259 through #261, and the app-wide scroll-aware thumbnail/avatar fade from PR #262. The production release workflow rebuilt and signed `ZeroChill.apk`, verified package `com.addy37.crazyshitunofficial`, production signing identity, version code `4003002`, and upgrade compatibility from v4.3.1, then published tag `v4.3.2` with `SHA256SUMS.txt`. The published APK SHA-256 is `3a2bfeca1b28b48566b3b71efdb24c98d8ca3530aa536f15bed8fdd7fc28cb0b`. The final device-tested functional tree came from `c1ec6ca`; the release PR changed only release metadata. Build Android APK #1739 and Signed Upgrade Candidate #631 passed before publication, and production release workflow #117 completed successfully. Earlier gallery/animation performance jobs that lost their emulator remain documented as validation limitations; physical-device tests were approved before merge. The previous v4.3.1 release source is preserved at `checkpoint/zerochill-approved-afae804`.

ZeroChill v4.3.1 was published from `afae804` after release PR #255. This maintenance release adds ShitTok comment counts, the compact in-app update experience, the merged OnlyFap favorite-gallery fix, and the device-approved Shows manual fullscreen/landscape behavior from PRs #250 through #254. The production release workflow rebuilt and signed `ZeroChill.apk`, verified package `com.addy37.crazyshitunofficial`, production signing identity, version code `4003001`, and upgrade compatibility from v4.3.0, then published tag `v4.3.1` with `SHA256SUMS.txt`. The published APK SHA-256 is `b73c9313e69f0229b222b41ce8cab826109424098f28efc56598a70d3c2a5142`. The final functional device-tested tree came from `64f25ea`; the release PR then changed only release metadata. Build Android APK #1667 and Signed Upgrade Candidate #568 passed before publication, and production release workflow #116 completed successfully. The previous v4.3.0 release source is preserved at `checkpoint/zerochill-approved-794b5d7`.

ZeroChill v4.3.0 was published from `794b5d7` after device-approved release PR #247. This major account and social release adds ZEROCHILL ID account creation/sign-in, public profiles, avatars and bios, account-scoped creator favorites, comment replies and likes, video likes, direct messages with unread/read state, the Library social hub, native social notifications with deep return into content, Profile + Account 2.0 security controls, Shared Creators, the optional startup-wizard account entry, and the one-time ZEROCHILL ID announcement for existing users upgrading into 4.3. Branded account/security email delivery is live through the authenticated ZEROCHILL mail domain, while the deployed Profile + Account 2.0 Supabase migrations and account-delete Edge Function remain the backend baseline. The production workflow rebuilt and signed `ZeroChill.apk`, verified package `com.addy37.crazyshitunofficial`, production signing identity, version code `4003000`, and upgrade compatibility from v4.2.1, then published tag `v4.3.0` with `SHA256SUMS.txt`. The published APK SHA-256 is `ad07c8962368df443ce5b2c16f2263d0e34f5328869a6f96b73c74bb698a4e63`. The signed device-tested candidate came from `95da876`, passed 349 debug and 349 release unit tests with one skipped in each suite and no failures/errors, all six performance jobs, and Android 15 in-place upgrade verification. The previous v4.2.1 release source is preserved at `checkpoint/zerochill-approved-48c14a8`.

ZeroChill v4.2.1 was published from `48c14a8` after release PR #231. It includes the device-approved OnlyFap Discover lifecycle/refill fix from PR #230 and the cleared-storage startup-wizard routing and no-blink transitions from PR #229. Discover now waits for final regular-shelf results, refills shortages from deeper configured Fapello listings, and publishes once while retaining randomized selection, duplicate protection, and cached fallback. The production workflow passed release unit tests, rebuilt and signed `ZeroChill.apk`, verified the package and production certificate, confirmed version code `4002001` upgrades from v4.2.0, and published tag `v4.2.1` with `SHA256SUMS.txt`. The device-tested candidate `08c8e02` also passed full debug/release unit tests and APK builds, all six performance CI jobs, and Android 15 in-place upgrade verification. Lint diagnostics match the prior approved baseline with no added findings. The previous v4.2.0 release source is preserved at `checkpoint/zerochill-approved-fc9c01e`.

ZeroChill v4.2.0 was published from `fc9c01e` after release PR #227. This release adds the device-approved full-screen startup wizard for new installs, the holographic OnlyFap creator-gallery grid morph, Fapello-backed Discover with deeper duplicate-free creator selection, threaded feedback conversations with read receipts, compact Shows portrait shelves, tapped-video autoplay in creator galleries, corrected custom avatar framing on compact shelves, and the approved ShitTok swipe-start fixes that move history and anti-repeat persistence off the active swipe path. The production workflow rebuilt and signed `ZeroChill.apk`, verified the package and production signing certificate, confirmed a higher version code and upgrade compatibility from v4.1.1, and published tag `v4.2.0` with `SHA256SUMS.txt`. The previous v4.1.1 rollback baseline is preserved at `checkpoint/zerochill-approved-024faa0`.

ZeroChill v4.1.1 was published from `024faa0` after release PR #211. This focused ShitTok update preserves the current clip position across tab switches, rebalances the warm feed toward Kaotic, Shit Show, and OnlyFap, reduces EFukt presence, removes unstable Bunkr content from ShitTok, and carries the device-approved swipe-smoothness work from PRs #209 and #210. Player preparation and cleanup are moved out of the active swipe path, RecyclerView no longer synchronously destroys players while paging, decoder cleanup is staggered while idle, and warm players are prepared one at a time. The production workflow built and signed `ZeroChill.apk`, verified the package and signing certificate, confirmed a higher Android version code and upgrade compatibility from v4.1.0, and published tag `v4.1.1` with `SHA256SUMS.txt`. The previous v4.1.0 rollback baseline is preserved at `checkpoint/zerochill-approved-e905d77`.

ZeroChill v4.1.0 was published from `e905d77` after release PR #205. This release adds reviewed and manual creator grouping, merged creator galleries, custom creator avatars with persistent pinch/drag crop framing, Favorite Creator unread-content count badges, adaptive ShitTok portrait-first ordering and creator-flow polish, OnlyFap search/gallery performance improvements, direct gallery downloads, and the approved swipeable full-width Shows hero with protected low-resolution artwork and mirrored top/bottom fades. The production workflow built and signed `ZeroChill.apk`, verified the package and signing certificate, confirmed a higher Android version code and upgrade compatibility from v4.0.0, and published tag `v4.1.0` with `SHA256SUMS.txt`. The previous v4.0.0 rollback baseline is preserved at `checkpoint/zerochill-approved-eb30d10`.

ZeroChill v4.0.0 was published from `eb30d10` after release PR #183. This major release promotes Library to primary navigation, keeps ShitTok as the main landing tab, rebuilds Shows and OnlyFap as cinematic media hubs, adds the collapsing glass bottom navigation, refreshes Library search and playback, simplifies More and Settings, standardizes section branding, and includes the accumulated device-approved playback, loading, thumbnail, and navigation polish through PR #182. The production workflow built and signed `ZeroChill.apk`, verified the package and signing certificate, confirmed a higher Android version code and upgrade compatibility from v3.1.6, and published tag `v4.0.0`. The v3.1.6 rollback baseline is preserved at `checkpoint/zerochill-approved-afd270e`.

The approved baseline contains the complete current ZEROCHILL stack, including Home/Collections glass UI, OnlyFap and OnlyHaven work, ShitTok all-source/fullscreen and preload work, creator search and bundled catalog, release orientation and action-sheet cleanup, Account Coming Soon, Memes removal from the release UI, product-level notification consolidation, aggregate device and Android adoption analytics, and the approved OnlyFap creator-gallery optimizations from PR #115.

PR #137 was device-approved and merged as `b1e48ed`. The current approved product baseline now includes the in-app More → Updates inbox, favorite-creator-only OnlyFap tracking, direct creator-gallery routing, compact cyan fresh-content markers, the approved animated loading mascot rollout from PR #135, and transition-safe update endpoints that prefer `Addy37/ZEROCHILL` while retaining the legacy `Addy37/CrazyShitAndroid` fallback for installed versions.

The approved gallery behavior includes progressive creator results, ZEROCHILL loading treatment and skeletons, fluid pinch density changes, refresh suppression during pinch, full-screen media resolution five items ahead and one behind, image warm-up, and bounded video preloading.

ZeroChill v3.1.6 was published from `afd270e` after PR #148. It includes the device-approved OnlyFap maximum-speed work from PR #146, including race-to-first-media loading, persistent hot-gallery sessions, smarter creator prewarming, and the fullscreen gallery status-bar fix. It also includes the compact Settings redesign and final status-bar fade treatment from PR #147. The signed candidate and Android 15 in-place upgrade checks passed before release, and production tag `v3.1.6` points to `afd270e`.

ZeroChill v3.1.5 was published from `e45402a` after PR #143. It includes the device-approved OnlyFap creator hero/collapsing-header work from PR #141 and the bounded creator-gallery prewarming from PR #142 across OnlyFap shelves, creator search, favorites, global search, and ShitTok creator links. The production release workflow verified the signed package and certificate, confirmed upgrade compatibility from v3.1.4, and published only `ZeroChill.apk` plus `SHA256SUMS.txt`.

ZeroChill v3.1.4 was published from `65bddfb` after PR #138 under the renamed public repository `Addy37/ZEROCHILL`. It includes the device-approved favorite-creator Updates inbox from PR #137, direct creator-gallery routing with cyan fresh-content markers, the approved animated loading mascot rollout, and transition-safe update endpoints that prefer `Addy37/ZEROCHILL` while retaining the legacy `Addy37/CrazyShitAndroid` fallback for installed versions. The release workflow verified the signed package, certificate, and upgrade path from v3.1.3 and published only `ZeroChill.apk` plus `SHA256SUMS.txt`.

ZeroChill v3.1.3 was published from `0a061a0` after PR #134. It includes PR #133's ShitTok speed and preload pass, OnlyFap creator-name/gallery warmup, pinch-to-clear-display gesture, and the phone fullscreen orientation restore fix. The production release workflow verified the signed package, certificate, and upgrade path from v3.1.2. New releases now publish one public install APK, `ZeroChill.apk`, plus `SHA256SUMS.txt` instead of duplicate APK aliases.

ZeroChill v3.1.2 was published from `ca95579e` after PR #132. Compared with v3.1.1, the main app adds fast source failover for Home and ShitTok from PR #131. The repository also contains the distinct ZeroChill Admin launcher icon from PR #130. The v3.1.2 release workflow built and signed the stable APK, verified the package and certificate, and verified upgrade compatibility from the previous stable release.

The previous gallery and release baseline was built from `b1a6107` and merged through PR #119 as `ed07f23`.

The approved portrait navigation icons are Home, Shows, a phone and downward feed arrow for ShitTok, a seated devil woman silhouette for OnlyFap, and the ZEROCHILL mascot for More. The device-tested signed APK was built at `02d7a5a`. PR #122 merged those icons into `rebrand/zerochill` as `95d8fa4`.

The approved devil-head launcher and themed icons and DEVIL WAKE splash came from the signed test APK built at `fcc454d`. PR #123 merged that product tree into `rebrand/zerochill` as `5035f48`. The previous approved product tree remains on the checkpoint branch for rollback.

## APK rule

Before producing or sharing a ZEROCHILL test APK:

1. Verify that its source commit is the approved product baseline or a descendant of it.
2. Use `main` as the complete approved lineage unless the user specifies another active branch.
3. Start new feature work from current `main`, rather than an older feature branch.
4. Include the source branch and short commit SHA in a test APK filename.
5. Preserve the application ID, production signing identity, existing installs, and saved data.
6. Record subsequent approved baselines and production release evidence here without replacing historical release records.
