# ZEROCHILL session-installer beta

Source: current approved 7.2.0 main lineage; Android production package/signing identity retained.

## What changed

- Keep the existing GitHub release check, DownloadManager download, system notification, and OLED update card.
- After DownloadManager confirms success, **Install update** stages that exact download into a user-initiated Android PackageInstaller session and asks Android to display its confirmation.
- Request Android's per-app "Install unknown apps" approval only after the user taps Install, and resume the same download when permission is granted.
- Show **Open Downloads** as an always-visible alternative; offer it if permission is denied or staging fails.
- Never silently install, request root/Shizuku/device-owner privileges, alter content sources, change backend endpoints, or publish an update.
- Keep the downloaded APK in Downloads so the fallback survives app restarts. Android independently checks package identity and update signing before replacing an installed app.
- Validate that the downloaded bytes are nonempty and match DownloadManager's declared size when available.

## Beta test plan

1. Install the one-shot production-signed beta over 7.2.0. Confirm Play Protect does not flag it and all app data is still present.
2. The real installer path needs a later compatible release or a controlled test fixture. This signed APK alone verifies installation compatibility but **does not** prove that a session update succeeds.
3. With an installable later candidate, tap update, download, then **Install update**. Confirm Android's system installation screen appears and an ordinary upgrade completes.
4. On a device where "Install unknown apps" is disabled, grant permission from the system settings after tapping Install; confirm the same download is used.
5. Decline that permission and verify **Open Downloads** remains available; cancel the Android install and confirm the current installation and data remain untouched.
6. Verify retry, cancel, Keep browsing, Android 12 and Android 16, APK signing, downgrade rejection, stable/beta update discovery and Play Protect with scanning enabled.

## Risk

The new manifest install permission could cause Play Protect to classify ZEROCHILL differently. A passing single-device beta scan is not a guarantee for subsequent releases. Nothing changes for users until a separately approved stable release.

No Supabase, remote source config, schema, signing, app ID or data migration changes.

## Private on-phone test results and production cleanup

The user installed the production-signed installer beta 7002001 on their OnePlus. Google Play Protect displayed "This app looks safe" and the app opened normally.

The user subsequently installed the signed 7002002 harness and used its temporary **Test APK installer** action to select the 7002003 update APK. Android installed the newer version successfully. This validates the Android confirmation and update-installation path, but the user still had to choose the APK manually, as intended by the temporary harness. No claim is made about a newer Play Protect scan unless the user confirms it.

The private file-picker code and both one-off Actions workflows have now been removed from the proposed production change. Their historical signed run [#44162759348](https://github.com/Addy37/ZEROCHILL/actions/runs/44162759348) succeeded and still records the test evidence.

## Remaining work before a public release

- A real newly published GitHub release must still exercise the full automated update discovery, DownloadManager transfer and **Install update** handoff. That flow is coded, but was not exercised by the local-file test.
- From a normal installed ZEROCHILL release, the app fetches the latest public stable GitHub release and offers it only if newer. The user taps **Download update** and later **Install update**; there is no APK file picker in the production changes.
- Do not merge solely on the strength of the manual file-picker test. Retain the existing Downloads fallback.
- Any future production APK intended to upgrade the user's 7002003 testing installation needs a versionCode **greater than 7002003**, with the production signing certificate unchanged.
- Play Protect assessment can vary for each new build and must be reviewed on the final release candidate.

No GitHub release, Supabase, remote-config, signing, app ID or persisted-data change was made as part of the private test.

## Isolated GitHub download-to-install acceptance test

The previous private APK picker test confirmed the Android installer session but not the download handoff. The user approved one dedicated **end-to-end** test.

- One private Actions workflow builds **two** production-signed APKs from the same source, each with the normal `com.addy37.crazyshitunofficial` application ID.
- Harness `7.2.1-installer-e2e.4`, versionCode `7002004`, upgrades the user's installed `7002003`.
- Fixture `7.2.1-installer-e2e.5`, versionCode `7002005`, is uploaded as `ZeroChill-installer-e2e-test.apk` to the **public GitHub prerelease** `v7.2.1-installer-e2e.5`. Public prerelease assets are accessible to anyone browsing the release page; they are **excluded from normal stable latest-release API checks** and will not trigger updates to regular ZEROCHILL users. The prerelease is explicitly marked `latest=false`.
- Only the exact harness version shows **Settings > Notifications & updates > Check installer test update**. That control fetches the exact tagged prerelease, validates its tag and expected asset URL, then uses the **unchanged** update card, DownloadManager URL download, download progress, Android PackageInstaller session and system confirmation.
- The test update asset must be available and verified before asking the user to install the harness.
- Phone test: install harness once, tap **Check installer test update**, tap **Download update**, let it finish, then tap **Install update**. No file picker. Confirm code 7002005, favorites/history/accounts/settings retained, and Play Protect behavior with scanning enabled.
- Do not merge this test-only Settings row or test endpoint into production. Remove both once accepted, and run scoped regression checks on the clean production candidate.
- Do not tag/merge/publish a stable release for this test. No Supabase, remote-config, app ID, signing, or migration changes.
