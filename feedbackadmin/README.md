# ZeroChill Admin console

The private admin app uses Kotlin and Jetpack Compose for presentation. The consumer app is outside this change. Version 2.2.0 / 13 keeps `com.addy37.crazyshitadmin`, the existing token keystore alias and preference keys, private token authentication, notification worker, and Java `AdminRepository`.

## Presentation architecture

- `MainActivity`: pairing, five primary destinations, shared admin menu, notification permission, legacy destination intents, and foreground-only live counter polling on Home and Analytics.
- `ConsoleState`: lifecycle-owned UI state, independent background requests, cancellation on lock, saved navigation, reply drafts, source candidate, and read-only Community search/filter state. Credentials never enter saved state. Process restoration intentionally revokes validation permission. Lightweight active-install counts are stored separately from the full analytics snapshot so fast refreshes cannot replace rankings or charts.
- `AdminGateway`: small test seam that delegates to the existing Java repositories in production.
- `ConsoleTheme`: OLED/electric-blue palette using the ZEROCHILL primary accent `#0892D0`, typography, reusable panels, tags, placeholders, navigation, and short animations. Compose animations follow Android's animator duration scale.
- `HomeScreen`, `InboxScreen`, `CommunityScreen`, `AnalyticsScreen`: operational snapshot, feedback search/filters, read-only account/social/message/report visibility, and aggregate rankings. Home and the Analytics summary show live Today/Week/Month counts refreshed every five seconds while the admin app is foregrounded on those screens. No invented time series or runtime source health.
- `CommunityRepository`: read-only account and social access through the existing private `feedback-admin` endpoint plus direct-message moderation through the private `dm-moderation` endpoint. Message moderation exposes sender, recipient, timestamp, read state, message body, message ID, and report markers. Both endpoints use the existing private admin token boundary. Supabase service-role credentials stay backend-only.
- `FeedbackThreadActivity`: preserved notification entry point plus Compose conversation, confirmed status, sticky reply composer, read receipts, details, and confirmed permanent deletion.
- `SourceConfigEditor`: providers and fields derived from the loaded document, guided scalar/list edits, advanced JSON, exact-candidate validation, explicit publish review, and existing rollback-as-new-version semantics. Every edit revokes validation. Refresh retains unsaved edits.

The bright ZEROCHILL accent is electric blue `#0892D0`. Newly branded admin surfaces should use that exact token.

The existing `feedback-admin` Edge Function keeps the current admin-token boundary and read-only Community actions: `community_overview`, `accounts`, `account_detail`, `social_activity`, and `reports`, alongside the existing feedback, analytics, and `active_counts` actions. The `dm-moderation` Edge Function uses the same admin-token hash and backend-only service-role credential to return the latest direct-message moderation rows. No database schema migration, analytics-ingest change, consumer-app change, public authorization-boundary change, or remote-config change is required.

Community remains read-only. There are no suspend, ban, delete-account, remove-comment, restore-content, report-resolution, message-edit, or message-delete controls. The dedicated Messages section can search sender/recipient identity, message text, or message ID and can filter to messages tied to a report. Account detail continues to show message metadata only.

Compose BOM 2026.06.01 matches the existing consumer app's compatible stable stack; Kotlin/compiler plugins reuse the root's 2.2.10. Activity Compose 1.10.1 and Lifecycle Compose 2.9.2 support the thin state layer. Material 3 and native Canvas charts avoid a chart dependency. Compile SDK 36, minimum/target SDK unchanged at 26/34.

## Verification

```sh
gradle --no-daemon :feedbackadmin:testDebugUnitTest :feedbackadmin:testReleaseUnitTest :feedbackadmin:assembleDebug :feedbackadmin:assembleRelease :feedbackadmin:assembleDebugAndroidTest
gradle --no-daemon :feedbackadmin:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.addy37.crazyshitadmin.ConsoleFlowTest,com.addy37.crazyshitadmin.CommunityMessagesFlowTest
```

`ConsoleLogicTest` covers candidate identity/invalidation, version assignment, preserving unknown config fields, semantic diffs, combined inbox filters/sorting, status labels and device filtering. `CommunityRepositoryTest` covers Community payload parsing, account-detail message-metadata behavior, and the dedicated DM moderation body/report payload.

`ConsoleFlowTest` uses test-only fixtures through the gateway, never real hosted feedback, moderation data, or a real remote publish. It captures the pairing, Home, Community accounts/account detail/social/reports, inbox, conversation, details, Sources, validated candidate, history and Analytics screens. It checks failed status saves, expired authentication, saved candidate/draft restoration and lock. `CommunityMessagesFlowTest` separately opens the Messages moderation section with synthetic data and verifies participant labels, message body, report badge, message ID, and the Reported filter. Screenshots and fixtures contain synthetic data only.

CI also builds the PR base's release APK with the same signing bundle, seeds a synthetic token using `TokenUpgradeTest#seed`, installs the new release with `adb install -r`, and checks decryption using `TokenUpgradeTest#verify`. These methods run explicitly, separately from the fixture suite. Debug/test APKs use the persistent signing configuration when supplied by CI; local builds keep the normal local debug key.

The existing admin workflow produces an identifiable signed APK and test/visual artifacts. Hosted production replies, deletion, publishing, rollback, and direct-message moderation access still require a real admin session and device review. A fixture test is not physical testing or a production operation.
