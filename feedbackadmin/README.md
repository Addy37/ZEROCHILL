# ZeroChill Admin console

The private admin app uses Kotlin and Jetpack Compose for presentation. The consumer app is outside this change. Version 2.0.0 / 11 keeps `com.addy37.crazyshitadmin`, the existing token keystore alias and preference keys, private token authentication, notification worker, and Java `AdminRepository`.

## Presentation architecture

- `MainActivity`: pairing, four primary destinations, shared admin menu, notification permission, and legacy destination intents.
- `ConsoleState`: lifecycle-owned UI state, independent background requests, cancellation on lock, saved navigation, reply drafts and source candidate. Credentials never enter saved state. Process restoration intentionally revokes validation permission.
- `AdminGateway`: small test seam that delegates to the unchanged Java repository in production.
- `ConsoleTheme`: OLED/cyan palette, typography, reusable panels, tags, placeholders, navigation, and short animations. Compose animations follow Android's animator duration scale.
- `HomeScreen`, `InboxScreen`, `AnalyticsScreen`: operational snapshot, local search/filters over the server's latest 200 tickets, and aggregate rankings. No invented time series or runtime source health.
- `FeedbackThreadActivity`: preserved notification entry point plus Compose conversation, confirmed status, sticky reply composer, read receipts, details, and confirmed permanent deletion.
- `SourceConfigEditor`: providers and fields derived from the loaded document, guided scalar/list edits, advanced JSON, exact-candidate validation, explicit publish review, and existing rollback-as-new-version semantics. Every edit revokes validation. Refresh retains unsaved edits.

No backend, schema, authorization, analytics-collection, or remote-config contract changes are required. Compose BOM 2026.06.01 matches the existing consumer app's compatible stable stack; Kotlin/compiler plugins reuse the root's 2.2.10. Activity Compose 1.10.1 and Lifecycle Compose 2.9.2 support the thin state layer. Material 3 and native Canvas charts avoid a chart dependency. Compile SDK 36, minimum/target SDK unchanged at 26/34.

## Verification

```sh
gradle --no-daemon :feedbackadmin:testDebugUnitTest :feedbackadmin:testReleaseUnitTest :feedbackadmin:assembleDebug :feedbackadmin:assembleRelease :feedbackadmin:assembleDebugAndroidTest
gradle --no-daemon :feedbackadmin:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.addy37.crazyshitadmin.ConsoleFlowTest
```

`ConsoleLogicTest` covers candidate identity/invalidation, version assignment, preserving unknown config fields, semantic diffs, combined inbox filters/sorting, status labels and device filtering.

`ConsoleFlowTest` uses test-only fixtures through the gateway, never real hosted feedback or a real remote publish. It captures the pairing, Home, inbox, conversation, details, Sources, validated candidate, history and Analytics screens. It checks failed status saves, expired authentication, saved candidate/draft restoration and lock. Screenshots contain synthetic data.

CI also builds the PR base's release APK with the same signing bundle, seeds a synthetic token using `TokenUpgradeTest#seed`, installs the new release with `adb install -r`, and checks decryption using `TokenUpgradeTest#verify`. These methods run explicitly, separately from the fixture suite. Debug/test APKs use the persistent signing configuration when supplied by CI; local builds keep the normal local debug key.

The existing admin workflow produces an identifiable signed APK and test/visual artifacts. Hosted production replies, deletion, publishing and rollback still require a real admin session and device review. A fixture test is not physical testing or a production operation.
