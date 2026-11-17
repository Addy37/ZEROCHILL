# Flat comments header and search entrance test APK

PR #451, branch `fix/flat-comments-header`, based on phone-approved PR #450 merged
as main `66810a58eb4e53aeaa7b301bf201813d1ff202df`. Both tweaks are present in app
source `f23693c9ff40c9de83bfd09bbeb7171f129c99ce`; subsequent build preparation
changes only documentation and the scoped candidate workflow.

## Included tweaks

- The Comments title/count and close button blend into the sheet without the
  rounded glass pill. Keep the drag handle and divider. The bottom composer,
  keyboard positioning and sheet geometry are unchanged.
- Search results fade and settle upward by 8dp over 190ms, with at most 60ms of
  stagger. Start the effect when each row reaches the screen rather than during
  prefetch binding. Each result enters once per query; refreshes and scrolling
  back do not replay it. Detached/recycled rows clear animation state. Existing
  reduced-motion settings are respected. No delay is added to search requests,
  debounce, cached data, result delivery or interaction.

All previously merged comments, Shows rotation, episodic Bunkr, DM and ShitTok
fixes and polish remain included.

## Focused evidence

Already passed locally on API 35:

- SearchResultsPolishTest: 10 tests.
- SearchStateTest: 3 tests.
- SearchScopeTest: 14 tests.
- InlineCommentsSheetTest.populatedSheetRendersInPortraitAndLandscape: one test
  rendering both orientations. The portrait preview was visually inspected.
- git diff --check.

No app code changes are needed for this artifact request. The one candidate
Actions job runs SearchResultsPolishTest and the comments portrait/landscape
render test (comments on APIs 35 and 36), builds one release APK, and verifies
package/certificate/upgrade compatibility. Its actual results, source SHA and
comments previews are included in the artifact. No full suite or debug APK build.

## Upgrade identity and scope

Version name `7.1.1-test.3-header-search`, versionCode `7001003`, application ID
`com.addy37.crazyshitunofficial`. The existing encrypted Actions signing bundle is
reused unchanged. The workflow compares package/certificate against published
7.1.0 and requires a version above the previous test APK's `7001002`.

No new dependency, data migration, source networking/configuration, authentication,
Supabase/backend, updater metadata, release, tag or merge changes. The existing
New Zerochill routing remains unchanged. PR #451 stays draft pending phone testing.
