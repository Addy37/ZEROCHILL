# ZEROCHILL 7.0 Android traffic test candidate (PR #422)

Branch: `feature/zerochill-7-social-traffic-reduction`. Selected baseline:
`88b60e38a9516bfa52b277e7f079d2eda9337780` (main). Candidate commits must descend
from this baseline. The signed artifact's `VALIDATION.json` records its exact SHA.

## Backend and installation boundaries

- Tests and the candidate use only New Zerochill, project `aavjpykssueavbseqdlf`.
- The existing New project public client key is used. No service-role/admin key is embedded.
- Both analytics endpoints are forced empty in traffic mode.
- Account/social/storage requests use the New project. Source config uses its `app-config`
  function; feedback uses its existing `dynamic-api` function. Public read-only probes
  returned HTTP 200 for app-config, summary RPC and empty feedback list.
- No backend deployment, migration, account/data migration or merge was performed.
  Backend PRs #419, #420 and #421 remain outside this approval.
- The test APK uses `com.addy37.crazyshitunofficial.dev` (ZeroChill Beta), alongside stable.
  It can replace an existing Beta install; stable's private data is separate. Use New
  project test accounts; old-project accounts are not migrated or usable here.
- Stable `com.addy37.crazyshitunofficial`, signing configuration and release versions
  remain unchanged. The candidate requires the established production certificate,
  SHA-256 `04abf84b3d468cbb1a562dea83b0007e686b2e56018cce911223963faea51356`.

## Implementation and review fixes

- Opt-in `ZEROCHILL_V7_TRAFFIC=true` requires the exact New project URL and a public
  client key, rejects source/feedback overrides to other backends, and disables analytics.
  Legacy builds retain their selected backend and 12-second polling behavior.
- Social recovery polling runs every five minutes, with foreground catch-up after a
  minute away. Transient failures back off to at most 30 minutes; permanent HTTP
  401/403/404 errors stop automatic polling. Manual refresh/account changes can recover.
  Foreground navigation cannot bypass failure backoff.
- Active DM refresh retains its normal interval when healthy. Failed thread reads back off
  from five seconds to five minutes; permanent HTTP errors stop automatic thread reads
  until explicit Retry / More → Refresh messages. Queued polls cannot immediately loop failed reads. Successful
  sends/manual recovery restore normal refresh.
- Notification preferences cache successful reads for 30 minutes. Failed reads use a
  shorter one-minute retry interval, bounded by actual refresh triggers.
- Likes and comment counts share one account/content-scoped summary RPC, a 120-second
  TTL, in-flight coalescing and a 120-entry LRU bound. Opening comments still loads the
  complete thread through the existing comments API. Comment/like writes invalidate summaries.
- Queued cache hits, network results and final UI applications reject account changes.
  Changed-account UI clears prior heart/count state before refreshing, including standalone
  fullscreen playback. Superseded reads retry once; network failures never self-retry.
- Traffic-mode hearts update optimistically and revert failed writes. A successful write
  followed by a failed summary read retains the confirmed heart and optimistic count;
  the count refreshes on the next successful read. It is not falsely rolled back.
- Push registration deduplicates identical successful writes for 24 hours and failures
  for 30 minutes. Its persisted timestamps now use wall time rather than uptime, with
  backward-clock handling. Logout cleanup and Firebase installation rotation remain.
- App bytecode targets Java 17. Robolectric tests use Java 21 because Android 16 fixtures
  require it; this fixes the Java-17-only test runtime failure. Native fixtures recycle
  workers every ten classes to bound temporary disk use. The stale navigation test now
  expects the approved OnlyFapGalleryActivity route; navigation implementation is unchanged.

## Validation evidence

- Focused local run: **58 tests, zero failures/errors/skips**: coordinator, summary parsing,
  cache races/expiry/coalescing, push registration, full comments UI and DM thread UI.
- Full debug/release suites and both APK builds: final result recorded in PR #422.
- Initial local attempts exposed read-only Robolectric cache paths and the Android 16
  Java runtime requirement. Workspace-local caches and Java 21 resolve those setup failures.
- The signed workflow is reached through the existing manual Build Android APK workflow
  on this branch. It bypasses the old-backend build, runs both unit suites, and builds only
  the signed side-by-side APK. A `[skip ci]` source push prevents incidental PR workflows.
- Artifact gates verify ancestry, `.dev` package/version, production certificate, source SHA,
  disabled analytics, New project presence and absence of the old project marker in every
  packaged APK entry (UTF-8 and UTF-16). These are static artifact checks, not device packet capture.

## Controlled request comparison

- The coalescing/cache test makes three summary consumers invoke the fake network loader
  once. Expiry causes a fresh invocation; failures cause one invocation without a retry loop.
- The coordinator test counts recovery loads over ten foreground minutes, including the
  initial load: 3 in traffic mode versus 51 in legacy mode. Per-hour steady polling frequency
  is 12 versus 300 (96% fewer scheduled polls), excluding foreground/manual refresh and backoff.
- These are controlled loader/scheduler counts, **not measured live HTTP or billing savings**.
  Combined summary replaces the previous like read plus full-comment-count read, but no
  physical scrolling/network soak comparison has run. No daily-request target is claimed.

## Physical acceptance still required

1. Install the Beta APK alongside stable on OnePlus Android 16. Confirm stable's favorites,
   history, downloads, login and playback remain intact. If an older Beta is installed,
   sign it out and use a New project account; its data is separate from stable.
   Both installs retain the existing account callback scheme; if Android shows an email
   confirmation-link chooser, select ZeroChill Beta for New project links.
2. With two New project test accounts, like/unlike from feed, detail and fullscreen player;
   verify immediate heart changes, correct eventual counts, and a failed write rollback.
   Interrupt connectivity after a successful write and confirm the heart stays committed.
3. Check cached comment counts, then open the complete thread, post/reply/delete, and
   confirm the updated count. Check blocked-user visibility and cache expiry.
4. Send/receive/refresh DMs, attachments, unread/read state and reactions. Test app background,
   foreground, manual refresh and offline recovery. Test push registration and real Firebase
   delivery for each account and after logout/login, notification choices and permission denial.
5. Switch A → B → logout → A with reads/writes in flight. No prior account's heart, comment
   visibility, messages, notifications or profile data should appear for the next account.
6. Observe five-minute recovery polling, bounded failed requests and restored manual refresh.
   Capture HTTP counts during comparable feed/thread/message sessions on old and new builds.
7. Smoke-test favorites, history, downloads/resume, playback/rotation, navigation and updater.

Live Auth, actual DM send/receive, Firebase delivery, physical compatibility, installed
Beta account switching, and live traffic savings are **not established by unit tests**.
Do not merge, publish, tag or cut over installed users before physical candidate approval.
