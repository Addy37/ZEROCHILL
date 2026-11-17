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

## Private two-APK confirmation test

PR #453 adds a **test-only** action under Settings > Notifications & updates > Test APK installer. It appears only in signed builds with the version name prefix `7.2.1-installer-beta.` and must be removed before the eventual production merge.

The one-shot GitHub Actions artifact contains:
- **Harness**: version code 7002002. Install this APK over the existing signed installer beta (7002001) with Play Protect enabled.
- **Fixture**: version code 7002003. Download it to the phone but **do not install by tapping the APK**.
- On the harness, tap Settings > Test APK installer and choose the fixture via Android Files. The app validates package identity and a higher version, stages it through the **same PackageInstaller session code** as a real DownloadManager update, and Android requests installation confirmation.
- Accept the prompt and verify version 7002003 plus intact sign-in, history, favorites, downloaded content, preferences and backups.
- Retest installation permission denied and installer cancellation if convenient.

### What this tests and what remains

This privately tests the real Android PackageInstaller handoff, permission prompt, APK identity/version guards, Android's signing check, and upgrade preservation without publishing any release. It **does not** test private remote downloading or update auto-detection. Production still obtains APKs through the unchanged, previously working DownloadManager path; a future public update or controlled opt-in HTTPS endpoint is needed to validate that integration end to end.

**No public release, Supabase changes, remote config changes, app ID change, signing change, or migrations.**
