# ZEROCHILL 5.6.2 Play Protect-safe updater

## Decision

Use Android DownloadManager for user-initiated APK downloads and remove ZEROCHILL's native APK-install capability.

Physical testing on the production package and production signing identity established this behavior:

- The legacy updater with native APK-install capability was blocked by Google Play Protect.
- A PackageInstaller.Session diagnostic that retained native install capability was also blocked.
- A browser-owned diagnostic with no native install capability was reported safe.
- The DownloadManager-only diagnostic in PR #393, with no native install capability, was reported safe.

The DownloadManager-only result preserves the native ZEROCHILL update card and progress while avoiding a browser handoff during download.

## Final update flow

1. ZEROCHILL checks GitHub release metadata using the existing updater endpoints.
2. The user explicitly taps `Download update`.
3. Android DownloadManager downloads the selected APK into the public Downloads directory.
4. ZEROCHILL mirrors DownloadManager progress in the existing update card.
5. After completion, the card tells the user to open Downloads.
6. The user taps the downloaded ZEROCHILL APK in Android Downloads.
7. Android owns the package-install confirmation and upgrade flow.

## Removed installer capability

The final path must not restore any of the following:

- `android.permission.REQUEST_INSTALL_PACKAGES`
- updater APK FileProvider configuration
- unknown-sources settings flow
- `PackageInstaller.Session` use by ZEROCHILL
- app-owned APK URI installer intents

The app may still check for updates, present notifications/cards, start a user-requested DownloadManager download, mirror download progress, and open Android's Downloads UI.

## Compatibility

- Application ID remains `com.addy37.crazyshitunofficial`.
- Production signing identity remains unchanged.
- Existing installs upgrade in place.
- No persisted data or schema migration is required.
- No favorites, history, downloads, backups, account data, or settings format changes are introduced.

## Backend and remote configuration

No Supabase, backend, content-source, or remote-config changes are required.

## Release validation

Before 5.6.2 publication:

- Run the signed upgrade-candidate workflow from the final hotfix head.
- Verify upgrade compatibility against the current stable release.
- Run the Android 15 in-place upgrade check.
- Verify the built production APK still omits `REQUEST_INSTALL_PACKAGES`.
- Physically scan the exact final production-signed candidate with Play Protect before merge/publication.

Do not treat CI success as a substitute for the final physical Play Protect scan.