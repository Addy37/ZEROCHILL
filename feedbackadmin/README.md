# ZeroChill Admin console

The private admin app uses Kotlin and Jetpack Compose for presentation. The consumer app is outside this change. Version 2.1.0 / 12 keeps `com.addy37.crazyshitadmin`, the existing token keystore alias and preference keys, private token authentication, notification worker, and Java `AdminRepository`.

## Presentation architecture

- `MainActivity`: pairing, five primary destinations, shared admin menu, notification permission, legacy destination intents, and foreground-only live counter polling on Home and Analytics.
- `ConsoleState`: lifecycle-owned UI state, independent background requests, cancellation on lock, saved navigation, reply drafts, source candidate, and read-only Community search/filter state. Credentials never enter saved state. Process restoration intentionally revokes validation permission. Lightweight active-install counts are stored separately from the full analytics snapshot so fast refreshes cannot replace rankings or charts.
- `AdminGateway`: small test seam that delegates to the existing Java repositories in production.
- `ConsoleTheme`: OLED/electric-blue palette using the ZEROCHILL primary accent `#0892D0`, typography, reusable panels, tags, placeholders, navigation, and short animations. Compose animations follow Android's animator duration scale.
- `HomeScreen`, `InboxScreen`, `CommunityScreen`, `AnalyticsScreen`: operational snapshot, feedback search/filters, read-only account/social/report visibility, and aggregate rankings. Home and the Analytics summary show live Today/Week/Month counts refreshed every five seconds while the admin app is foregrounded on those screens. No invented time series or runtime source health.
- `CommunityRepository`: read-only account and social access through the existing private `feedback-admin` endpoint. Account details include auth/profile metadata, social totals, recent comments/likes, message metadata, reports, and block counts. The separate private DMs reader can load full messages; account details still show metadata only.
- `FeedbackThreadActivity`: preserved notification entry point plus Compose conversation, confirmed status, sticky reply composer, read receipts, details, and confirmed permanent deletion.
- `SourceConfigEditor`: providers and fields derived from the loaded document, guided scalar/list edits, advanced JSON, exact-candidate validation, explicit publish review, and existing rollback-as-new-version semantics. Every edit revokes validation. Refresh retains unsaved edits.

The bright ZEROCHILL accent is electric blue `#0892D0`. Newly branded admin surfaces should use that exact token.

The `feedback-admin` Edge Function keeps the existing admin-token boundary and now exposes additive read-only Community actions: `community_overview`, `accounts`, `account_detail`, `social_activity`, and `reports`, alongside the existing feedback, analytics, and `active_counts` actions. Service-role credentials remain backend-only. The Community endpoints read the existing Auth, profile, comment, like, direct-message metadata, report, and block tables. No schema migration, analytics-ingest change, consumer-app change, authorization-boundary change, or remote-config change is required.

The Community section has no suspend, ban, delete-account, remove-comment, restore-content, or report-resolution controls. Social activity and account details remain metadata-only; private DMs are readable in the dedicated owner-only section.

Compose BOM 2026.06.01 matches the existing consumer app's compatible stable stack; Kotlin/compiler plugins reuse the root's 2.2.10. Activity Compose 1.10.1 and Lifecycle Compose 2.9.2 support the thin state layer. Material 3 and native Canvas charts avoid a chart dependency. Compile SDK 36, minimum/target SDK unchanged at 26/34.

## Verification

```sh
gradle --no-daemon :feedbackadmin:testDebugUnitTest :feedbackadmin:testReleaseUnitTest :feedbackadmin:assembleDebug :feedbackadmin:assembleRelease :feedbackadmin:assembleDebugAndroidTest
gradle --no-daemon :feedbackadmin:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.addy37.crazyshitadmin.ConsoleFlowTest
```

`ConsoleLogicTest` covers candidate identity/invalidation, version assignment, preserving unknown config fields, semantic diffs, combined inbox filters/sorting, status labels and device filtering. `CommunityRepositoryTest` covers Community payload parsing and the message-metadata-only contract.

`ConsoleFlowTest` uses test-only fixtures through the gateway, never real hosted feedback or a real remote publish. It captures the pairing, Home, Community accounts/account detail/social/reports, inbox, conversation, details, Sources, validated candidate, history and Analytics screens. It checks failed status saves, expired authentication, saved candidate/draft restoration and lock. Screenshots contain synthetic data.

CI also builds the PR base's release APK with the same signing bundle, seeds a synthetic token using `TokenUpgradeTest#seed`, installs the new release with `adb install -r`, and checks decryption using `TokenUpgradeTest#verify`. These methods run explicitly, separately from the fixture suite. Debug/test APKs use the persistent signing configuration when supplied by CI; local builds keep the normal local debug key.

The existing admin workflow produces an identifiable signed APK and test/visual artifacts. Hosted production replies, deletion, publishing and rollback still require a real admin session and device review. A fixture test is not physical testing or a production operation.
    
## 7.0.1 owner DM reader candidate

Community includes a read-only DMs section showing all direct-message bodies,
participants, read state and dates. It pages 50 messages at a time without
loading or storing an entire archive. Private photo/GIF attachments use
short-lived signed URLs. No admin send, delete, or read-receipt mutations
are added. The matching New Zerochill migration and Edge Function must be
deployed together, after privacy disclosure and access-control review.


## 7.0.2 threaded conversation upgrade

Community > DMs returns to the older Admin 2.2 conversation layout. It shows
one card per participant pair, sorted by the latest message, including last
message preview, total/unread counts, and report markers. Search matches people
and message text. The Reported filter applies to whole conversations.

Tap a card to read the complete history in chronological order. Older messages
load in pages of 50; photo/GIF links are signed for five minutes. The backend
retains the Admin 7.0.1 flat endpoint for existing private installations and
requires the established admin token for every request. No write operations are
added, and user read receipts are unaffected.

New Zerochill `feedback-admin` version 5 was deployed on 2026-11-19.
The live backend test returned 3 conversation groups covering all 219
direct messages, rejected requests without an admin token, and successfully
paged one thread through two distinct 50-message pages. Device validation
of the new UI is separate from the backend tests.
