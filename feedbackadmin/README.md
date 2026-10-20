# ZeroChill Admin console

The private admin app uses Kotlin and Jetpack Compose for presentation. The consumer app is outside this change. Version 2.0.0 / 11 keeps `com.addy37.crazyshitadmin`, the existing token keystore alias and preference keys, private token authentication, notification worker, and Java `AdminRepository`.

## Presentation architecture

- `MainActivity`: pairing, four primary destinations, shared admin menu, notification permission, legacy destination intents, and foreground-only live counter polling on Home and Analytics.
- `ConsoleState`: lifecycle-owned UI state, independent background requests, cancellation on lock, saved navigation, reply drafts and source candidate. Credentials never enter saved state. Process restoration intentionally revokes validation permission. Lightweight active-install counts are stored separately from the full analytics snapshot so fast refreshes cannot replace rankings or charts.
- `AdminGateway`: small test seam that delegates to the existing Java repository in production.
- `ConsoleTheme`: OLED/electric-blue palette using the ZEROCHILL primary accent `#0892D0`, typography, reusable panels, tags, placeholders, navigation, and short animations. Compose animations follow Android's animator duration scale.
- `HomeScreen`, `InboxScreen`, `AnalyticsScreen`: operational snapshot, local search/filters over the server's latest 200 tickets, and aggregate rankings. Home and the Analytics summary show live Today/Week/Month counts refreshed every five seconds while the admin app is foregrounded on those screens. No invented time series or runtime source health.
- `FeedbackThreadActivity`: preserved notification entry point plus Compose conversation, confirmed status, sticky reply composer, read receipts, details, and confirmed permanent deletion.
- `SourceConfigEditor`: providers and fields derived from the loaded document, guided scalar/list edits, advanced JSON, exact-candidate validation, explicit publish review, and existing rollback-as-new-version semantics. Every edit revokes validation. Refresh retains unsaved edits.

The bright ZEROCHILL accent is electric blue `#0892D0`. Newly branded admin surfaces should use that exact token.

The `feedback-admin` Edge Function now exposes an additive, admin-token-protected `active_counts` action. It performs three indexed count queries against the existing `analytics_uniques` table for the current UTC day, week, and month. The full `analytics_dashboard()` RPC remains unchanged and continues to provide rankings and charts. No schema migration, analytics-ingest change, consumer-app change, authorization-boundary change, or remote-config change is required.

Compose BOM 2026.06.01 matches the existing consumer app's compatible stable stack; Kotlin/compiler plugins reuse the root's 2.2.10. Activity Compose 1.10.1 and Lifecycle Compose 2.9.2 support the thin state layer. Material 3 and native Canvas charts avoid a chart dependency. Compile SDK 36, minimum/target SDK unchanged at 26/34.

## Verification

```sh
gradle --no-daemon :feedbackadmin:testDebugUnitTest :feedbackadmin:testReleaseUnitTest :feedbackadmin:assembleDebug :feedbackadmin:assembleRelease :feedbackadmin:assembleDebugAndroidTest
gradle --no-daemon :feedbackadmin:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.addy37.crazyshitadmin.ConsoleFlowTest
```

`ConsoleLogicTest` covers candidate identity/invalidation, version assignment, preserving unknown config fields, semantic diffs, combined inbox filters/sorting, status labels and device filtering.

`ConsoleFlowTest` uses test-only fixtures through the gateway, never real hosted feedback or a real remote publish. It captures the pairing, Home, inbox, conversation, details, Sources, validated candidate, history and Analytics screens. It checks failed status saves, expired authentication, saved candidate/draft restoration and lock. Screenshots contain synthetic data.

CI also builds the PR base's release APK with the same signing bundle, seeds a synthetic token using `TokenUpgradeTest#seed`, installs the new release with `adb install -r`, and checks decryption using `TokenUpgradeTest#verify`. These methods run explicitly, separately from the fixture suite. Debug/test APKs use the persistent signing configuration when supplied by CI; local builds keep the normal local debug key.

The existing admin workflow produces an identifiable signed APK and test/visual artifacts. Hosted production replies, deletion, publishing and rollback still require a real admin session and device review. A fixture test is not physical testing or a production operation.
