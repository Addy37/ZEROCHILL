# ZEROCHILL Android installer: production preparation (HOLD)

## Scope

Feature branch: `feature/zerochill-safe-installer-beta`
Draft PR: #453 against the approved ZEROCHILL 7.2.0 main lineage.
Status: **Keep unmerged and do not publish a new stable release until the product owner explicitly approves.** The user wants to consider additional improvements tomorrow, November 18, 2026.

## What is ready

- Keep the current GitHub stable release lookup, update popup, What's New summary and Android DownloadManager transfer/progress.
- On completed download, display **Install update**. On user tap, validate the downloaded APK's size, package identity and version code, then stage it through Android's PackageInstaller.
- Android shows its normal user confirmation. If an installation permission is needed, request it from Settings after the user's tap. No silent installs.
- Preserve **Open Downloads** as the fallback for download or installer errors and user-denied permissions.
- Keep the original application ID `com.addy37.crazyshitunofficial`, original signing identity, New Zerochill backend, persisted data and content-source behavior.
- Remove the temporary private-prerelease update test, Settings row, fixed tag/asset test constants, and the one-shot remote E2E workflow. Keep focused production-relevant unit tests.

## Verification already completed

The owner tested a production-signed installer beta on their phone and Play Protect reported that it looked safe. The earlier local-file PackageInstaller test upgraded 7002002 to 7002003. The final opt-in end-to-end test updated a 7002004 harness to `7.2.1-installer-e2e.5` (7002005) using the exact tagged GitHub prerelease, **Download update**, system DownloadManager, **Install update**, and Android confirmation. The owner confirmed the app launched, Play Protect marked it safe, and favorites/history/settings and other saved data survived.

GitHub E2E test prerelease `v7.2.1-installer-e2e.5` was publicly visible as a **prerelease**; it was not the stable latest release. Keep the historical run and test asset as evidence unless a separate deletion decision is made.

The last remote test workflow run #44170183655 failed **at its final reporting command** because `gh release view --json` does not recognize `isLatest`. Earlier focused tests, signing/build, upload and publication checks passed. Do not call the entire job green, and do not rerun the old expensive workflow.

## Before any later public release

1. Collect and implement the owner's remaining changes on a reviewed feature branch or PR, without resetting the verified installer behavior.
2. **Use versionCode strictly greater than 7002005** because the owner's installed test app is 7002005; consider `7.2.2` or newer for versionName so the current numeric version-name comparator also sees a newer update than `7.2.1-installer-e2e.5`.
3. Align bundled What's New, release notes and the update-dialog summary with the final approved change list, and confirm their text is scrollable.
4. Run focused installer and update-card tests, one final signed release candidate build, and verification against the stable production package/certificate. Repeat Play Protect/device upgrade checks on the **actual final candidate**.
5. Confirm the final PR contains no test-only GitHub prerelease endpoints, one-off test Settings rows or remote-E2E workflow, and no source/backend/config regressions.
6. Merge, tag or publish **only after explicit release authorization**. Preserve normal installed-upgrade compatibility and existing user data.

No Supabase/schema migration, remote-source configuration, content source, public release, or application ID changes are part of this cleanup.
