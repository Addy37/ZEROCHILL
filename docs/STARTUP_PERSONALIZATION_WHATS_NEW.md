# ZEROCHILL 7.0 startup personalization and What's New

## Lineage and scope

`feature/zerochill-7-startup-personalization` starts at PR #423 head
`b3b7973247dd2124f5443a2e18747aa4025792c8`, continuing PR #422 and approved
main `88b60e38a9516bfa52b277e7f079d2eda9337780`. The feature PR targets
`feature/zerochill-7-new-message-compose` so its diff contains this work only.
PR #423 stays an unchanged, unmerged draft. No workflow, backend, endpoint,
release metadata, application ID, signing configuration or launcher icon changes.
No Actions, APK build, merge, publish or release is authorized for this task.

## Personalization

The wizard's final page reads the existing `AppearanceTheme.Accent` value and
shows seven circular, named, accessible swatches. A tap writes only the existing
`appearance_accent` key in `app_prefs`; simply visiting onboarding does not write
or reset the accent. The mascot, CHILL wordmark, preview control, primary action
and progress indicator interpolate the chosen color. Existing account controls,
notification options, age confirmation and XMegaDrive setup stay in place.
The page and unsaved toggle choices survive recreation. Appearance remains the
place to change accents later. StartupWizardPolicy eligibility is unchanged.

Splash reads the same local preference once after inflation. Accent filtering
preserves the horns' dark fill, the black face and the original eyes/teeth.
The system splash and launcher icon are unchanged. The existing reveal schedule,
960ms handler deadline, preloader and destination selection remain intact.
Motion obeys both the Android animator setting and `immersive_motion_enabled`.

## What's New behavior

- `whats_new_state` holds the last acknowledged numeric version code and first
  install timestamp. It is outside `app_prefs` and the manual user-backup schema;
  Android backup is already disabled in the manifest.
- A genuinely fresh install baselines the current version without a popup.
  Completing first-install onboarding also baselines it, including an update
  received while onboarding was unfinished. A restored user's preferences do not
  make a new installation look like an upgrade.
- An existing installation without tracking state is eligible when package first
  install and last update times establish an upgrade. Unknown dates suppress the
  automatic popup. A changed install timestamp discards transplanted tracking.
- Acknowledgement uses monotonic version codes, so relaunches and downgrades do
  not repeat highlights. Back also acknowledges. Rotation, process recreation,
  backgrounding and navigation deferral do not acknowledge an open popup.
- Automatic presentation requires an ordinary ACTION_MAIN/CATEGORY_LAUNCHER
  entry or its internal Splash handoff marker, with no routing data/extras.
  Shortcuts, generic notifications and deep links defer it until a normal launch.
  A new navigation intent dismisses an automatic popup without acknowledging it.
- Presentation happens only after main content startup and foreground resume.
  Existing account/notification/rating prompts continue after acknowledgement;
  Customize appearance postpones that continuation until return. Rating prompts
  are blocked while What's New is pending or visible.
- Settings > Notifications & updates > What's New opens the installed notes at
  any time, regardless of prior acknowledgement.

The native DialogFragment restores its version arguments and open state. Its
black scrollable layout is capped at 85% of the screen height, and its short
scale/fade animations use the existing motion policy.

## Offline release notes

`res/raw/whats_new_7_0.md` is the versioned source bundle for
`7.0-personalization-v1`. It lists only features implemented in this change.
It is associated with this binary's source revision rather than a version-name
prefix: local/custom Beta packaging may use a different label. The dialog displays
the actual BuildConfig version name, and tracking uses BuildConfig version code.
Release owners must update the bundled notes/revision for future product releases.
No release/version metadata is bumped by this feature.

The bundle reuses ReleaseNotesSummary's Markdown cleanup. The GitHub updater,
its fetch/check cadence and release-summary behavior remain unchanged. The popup
has no network client, remote fetch, new background task or Supabase dependency.

## Validation

Focused local Robolectric/JUnit validation passed on 2026-11-13: **55 debug +
55 release tests**, no failures or skips, using the existing local Android SDK,
JDK/Gradle caches and offline Gradle. `git diff --check` also passed.

Both `:app:testDebugUnitTest` and `:app:testReleaseUnitTest` ran with these filters:
`*WhatsNew*`, `*StartupWizard*`, `*AppearanceThemeTest`, `*ReleaseNotesSummaryTest`,
`*Splash*`, `*CreatorBackupTest`, `*ZeroChillIdUpgradeAnnouncementTest`.
Packaging labels for this local validation were `APP_VERSION_NAME=7.0-personalization`
and `APP_VERSION_CODE=6000048`; no release metadata file changed.
The first iterations exposed a splash test fixture clock offset and a test's
asynchronous dismissal assertion; both were corrected before the passing run.
No APK was produced. Device acceptance remains necessary for visual
motion quality, system permission UI, real notification delivery and process death
on the target OnePlus/Android 16 device. Tests exercise the routing/handoff
contract with local intents, rather than sending live notifications.
