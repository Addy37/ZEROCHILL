# ZEROCHILL 7.0 Android traffic reduction (draft)

Branch: `feature/zerochill-7-social-traffic-reduction`, based on the approved current `main` lineage. Date: 2026-11-12.

## Goal

Lower avoidable Supabase requests while preserving existing social features, notifications, viewing, favorites, history, downloads, installed settings and upgrade identity. The new endpoint is `https://aavjpykssueavbseqdlf.supabase.co`. The old project's users and already-installed app remain on the old backend.

## Code changes

- `app/build.gradle.kts`: adds opt-in build flag `ZEROCHILL_V7_TRAFFIC=true`, requiring the new-project Supabase URL and publishable client key. It prevents pointing the optional source-config/feedback overrides at the old project and forces both legacy analytics endpoint fields empty in this mode. Without the flag, existing defaults and package/signing are preserved. **No signing workflow was changed.**
- `SocialActivityCoordinator.java`: changes the **opt-in** background social recovery interval from 12 seconds to 5 minutes; rechecks after a sustained 60-second background period, preserves explicit refresh, and backs off failed requests. Permanent authentication/missing-route failures stop automatic retries until explicit manual refresh or account change. Original behavior remains for non-7.0 builds.
- `ZeroChillNotificationPreferences.java`: in 7.0 mode, caches account notification settings for 30 minutes rather than 60 seconds, and clears the refresh stamp on sign-out.
- `ZeroChillSocialRepository.java`: in 7.0 mode, uses the already-staged new-project `zerochill_video_social_summary` RPC to obtain like count, visible comment count, and account-scoped own-like state together. Coalesces concurrent requests for the same account/content key, keeps entries for 2 minutes (max 120), validates response data, and invalidates on local like/comment writes. The original full-comment retrieval remains for actual open threads. Legacy mode keeps old REST behavior.
- `ChaosFeedView.java` and `VideoDetailActivity.java`: use the comment-count method instead of full-comment fetching for the rail labels when the 7.0 mode is enabled, with unchanged current view behavior and legacy fallback.
- `ZeroChillPushRegistration.java`: in 7.0 mode, caches successful identical Firebase registration writes for 24 hours and failed attempts for 30 minutes, keyed by a locally hashed account/install/app/Firebase identity. Retains invalid-token rotation on HTTP 409, separate account registration, and removal on logout. Existing builds use their prior registration behavior.

## Tests added, but **not run**

- `SocialActivityCoordinatorTest.java`: 12-second legacy / 5-minute opt-in recovery / foreground thresholds and 60-second versus 30-minute settings cache.
- `ZeroChillPushRegistrationTest.java`: duplicate registration acceptance/expiry/key changes.
- `VideoSocialSummaryTest.java`: combined counts/liked state parsing, missing fields, no rows and permanent versus retriable HTTP statuses.

## Verification performed

- Examined current `main`, `AGENTS.md`, `ZEROCHILL_BUILD_LINEAGE.md`, repository social coordinator, networking, preference, push, and player UI code before editing.
- Compared branch to `main` after edits: only targeted Android build configuration, source, and tests changed.
- Ran a brace/quote/comment balance inspection over the changed Java files. **This is not compilation or a Gradle test.**
- The new-project RPC itself was separately tested in DB transactions for anonymous and authenticated scenarios under draft backend PR #421. That does not validate the Android integration.

## Important limitations and next steps

1. **Build and run focused Android tests** in a real Android/Gradle environment. This chat/container did not have Gradle or an Android SDK; no APK, unit tests, emulator or physical tests have been run. Inspect and fix failures before declaring implementation complete.
2. Verify real Auth, Firebase push registration and delivery, DM/reply/like notifications, permission denied, offline resume, account changes and expired sessions. New backend social push needs a dedicated test plan and complete `push_device_tokens` setup before enabling live use.
3. Check complete 7.0 workflow injection of **new** public key, source-config URL, feedback URL, update backend and Firebase app identity; reject accidental old backend fallback. Do not embed service-role/admin credentials. Keep the release application ID/signing identity.
4. Test ShitTok/Shows like and comment counts (including local writes, immediate own-like feedback, 120-second cached counts, account changes and failure fallback) against the new backend. Existing content players and provider behavior must remain working.
5. Run a bounded soak test with request counters on an explicitly test-only APK before any production cutover. The 8k–12k requests/day target is a **planning goal**, not verified savings. Counts are not billing/egress.
6. Review the app's other active REST endpoints and notifications for request spikes. No old analytics function should be deployed to New Zerochill.
7. This PR targets `main` but **depends on staged Supabase backend RPC in PR #421**. Backend PRs #419, #420, #421 remain draft. Keep this PR draft until the real Android tests pass.

No existing old-project account, social row, APK, app ID, signing setup, or user data was changed. No new backend migration or function was applied in this Android branch.
