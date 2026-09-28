# ZEROCHILL build lineage

This file identifies the current approved ZEROCHILL product baseline for test APKs and future feature work.

- Source-of-truth branch: `rebrand/zerochill`
- Integration branch: `integration/zerochill-current`
- Approved product baseline commit: `e905d77604c9d9797b64e085e7afcf3689c4ee2b`
- Latest production release source commit: `e905d77604c9d9797b64e085e7afcf3689c4ee2b`
- Device-tested APK source commit: `e26b34c3f763948a1ed20551bd867ebd817cde49`
- Latest release PR: #205
- Approval date: 2026-09-28

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

1. Verify the candidate commit is the approved product baseline commit or a descendant of it.
2. Treat `rebrand/zerochill` as the current ZEROCHILL source of truth until it is merged into `main`.
3. Keep `integration/zerochill-current` synchronized with the latest approved complete ZEROCHILL lineage.
4. Start new feature work from the current complete integration lineage, not from an older feature branch.
5. Do not build from an older feature branch when a newer compatible approved lineage exists.
6. Include the source branch and short commit SHA in the APK filename.

If a newer complete build replaces this baseline, advance both current branches as appropriate, preserve a rollback checkpoint for the last approved build, and update the approved product baseline commit here.
