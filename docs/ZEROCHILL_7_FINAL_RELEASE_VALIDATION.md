# ZEROCHILL 7 final integration and release checks

This document records the November 13, 2026 stabilization decisions. The user declared the product feature-complete and authorized **merging the approved work into main and verifying the final install/update experience**, not a public release.

## Approved code now in main

- PR #431: New Zerochill-only routing and 7.x fail-closed build checks
- PR #432: private New Zerochill Admin 7.0 application; private read-only social metadata migration documented
- PR #433: targeted inherited 6.0.2 crash fixes, signed release-candidate workflow, and the approved What's New-like update panel from merged PR #434

All were merged into `main` with original compatibility-sensitive application IDs, signing configurations, favorites, downloads, history, backups and local settings intact.

## Verified tests and package

- The signed 7.0.2 release **candidate** was built from commit `24cdc81df3c42c145d870bad6cf711ae425952eb`, GitHub Actions run `43532568960`. Focused release-unit tests, `:app:assembleRelease`, package/version/signing comparison against published 6.0.2, source SHA check, and New-only Supabase scan **passed**.
- The candidate APK is `ZeroChill-7.0.2-update-dialog-24cdc81.apk`, SHA-256 `b416ef97949c02e4857eb6a572b5bd091cfc1f62fd75e3a24bf43115e392a659` as recorded in the downloaded signed artifact's `SHA256SUMS.txt`.
- The Android production application ID is `com.addy37.crazyshitunofficial`. Candidate versionName is `7.0.2`, versionCode `7000002`.
- The merged main source for `UpdateCardController`, `NotificationCoordinator`, `OnlyHavenRepository`, `ZeroChillSessionStore` and `app/build.gradle.kts` matches the tested candidate's GitHub file blob IDs. The merged Admin app was separately built and accepted by the user.
- Neither an Android emulator upgrade nor a physical-device upgrade was run by ChatGPT. The checks above verify technical APK identity and upgrade eligibility, **not that installed on-device data actually survived an update**.

## Required phone acceptance

Do **not** uninstall or clear app data. Install `ZeroChill-7.0.2-update-dialog-24cdc81.apk` directly over an existing signed 6.0.2, 7.0.0 or 7.0.1 installation as applicable, then check:
1. Android accepts the install as an **update** rather than requiring uninstall.
2. Saved Favorites, Downloads, History, Watch Later, local settings and theme remain.
3. Main screen, Shows, ShitTok, OnlyFap/creator gallery, messaging, and Admin can still be opened.
4. The user accepts the new update-screen appearance when a newer version is actually available.

The redesigned update prompt only appears when the installed app detects a **newer published update**. Installing 7.0.2 does **not** display it immediately. No newer public version is being created just to force a prompt. This visual check remains pending until a controlled future release or a separately authorized private preview.

If users installed 7.0.2 candidates, a public v7.0.2 has the same versionCode and will **not** upgrade those installs. Before public release, choose a code strictly above `7000002` (for example 7.0.3) if the intention is to update all candidate installs.

## Stable release publishing safety

The previous `release-apk.yml` used an old Supabase source-config URL and legacy feedback secrets. It has been scoped to **manual main-branch dispatch only** and hard-wired to New Zerochill public client configuration, with `ZEROCHILL_V7_TRAFFIC=true`, empty legacy analytics endpoints and an APK scan that refuses any old backend marker. It refuses versions earlier than 7.0.2. Its private signing key continues to come only from GitHub Actions secrets.

Changing this workflow does **not** publish an APK. Do not invoke `workflow_dispatch` until the user separately approves a version and public release. The shipping release notes should reflect confirmed 7.x changes; do not rely on 6.x notes.

## Unresolved

- Actual phone upgrade/data-persistence acceptance and first live update-popup appearance remain user-dependent.
- Android framework-only layout crashes without identifying app frames remain documented rather than receiving speculative changes.
- Production tag, release publishing and disabling the old Supabase project are intentionally out of scope.
