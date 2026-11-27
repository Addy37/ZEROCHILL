# ZEROCHILL 7.5 release preparation

The release preparation branch starts at verified remote `main`,
`23e3229adb7b997d66f9298988f82a4277351e7f`. It contains the exact Beta 14
source `873129316cc37c6b204a0a1657fbc8852e37df1d` through merged PR #499.
The Beta 14 signed workflow and lineage check passed. That workflow produced
the side-by-side `.dev` build, version code 7005080. The production
candidate uses 7.5.0, code 7005081.

- [Consolidation PR #499](https://github.com/Addy37/ZEROCHILL/pull/499)
- [Beta 14 CI](https://github.com/Addy37/ZEROCHILL/actions/runs/45923649467)
- [Source comparison from public 7.4.0](https://github.com/Addy37/ZEROCHILL/compare/v7.4.0...23e3229adb7b997d66f9298988f82a4277351e7f)

## Outstanding pull requests

| Disposition | Evidence |
| --- | --- |
| Superseded or consolidated | #471 in #474, the approved part of #472 in #474, #483–486 in #487 and the later list-only Spotlight consolidation, the approved Pawchive discovery scope of #477 in #478, and #405 by the existing What's New implementation. |
| Experiments or previews | #473 was explicitly excluded by #474. #477’s subsequent GIF experiments were explicitly excluded by #478. #480–481 were previews or fixtures ahead of the native Erome integration. Older installer diagnostics #382, #386–395, UI experiments #285, #320, #331 and provider probe #279 supply no approval evidence for this release. The remaining pre-#249 PRs are historical diagnostics, app versions, provider prototypes and admin work without evidence of missing 7.5 scope. |
| Outside the approved app scope | #249, #358, #400, #418–421, #424, #428–430, #453–456. These include admin, staging, account-migration and historical installer/playback branches. No missing owner-approved 7.5 behavior was identified in their descriptions and the consolidated lineage. |

## Stability findings

| Severity | Status and evidence |
| --- | --- |
| Critical | None confirmed. |
| High | Fixed an Android 14 application-start crash recovered from public 7.4.0 diagnostics. The stack reached `FirebaseApp.getInstance()` from push registration in `Application.onCreate`. Startup now attempts standard Firebase initialization and tolerates unavailability without failing the process. Logout cleanup also tolerates an unavailable Firebase instance. Live notification delivery remains a device check. |
| High | Fixed ShitTok landscape fullscreen. Portrait touch handlers and pager restoration could change the current clip, and the persistent progress rule kept a separate seek row visible. The dedicated fullscreen surface has its own shared tap-to-seek handling and one overlay for every control. It retains the selected player lease and suppresses feed paging and auto-advance until exit. |
| High | Fixed the oldest-first limit on DM threads. A thread over 500 messages omitted newly received messages after reloading. The same bounded query now selects the latest 500, with a stable tie-breaker, and displays them in chronological order. The existing visibility RPC and cleanup cutoff still apply. Messages outside the recent window remain on the server. |
| High | Unconfirmed historical Samsung OOM and vivo/Android layout crash. The reports lack a heap or reproducible app frame. Bounded ShitTok player leases, gallery worker queues and activity teardown are in source. Physical low-memory, rotation and long-session checks remain necessary. |
| Medium | Fixed the previous-version What's New copy and the launch marker dropped during one-time XMegaDrive access repair. Ordinary upgrades still skip the wizard. A normal launcher handoff after repair now remains eligible for the popup. |
| Medium | Added validation for optional source URLs in backup items before restore can apply changes. Collection membership roundtrip and legacy format are covered by the release regression tests. |
| Medium | Production push registration returned 362 HTTP 401s in 465 POSTs over the measured day. Logs do not establish the client version or cause. Live delivery and account switching remain release acceptance items. |
| Medium | Active DM threads poll every 2.5 seconds while resumed. Each refresh can read cutoff state as well as messages. This traffic warrants follow-up after physical delivery and latency checks. The existing cadence, authorization and failure backoff are preserved. |
| Medium | Provider availability varies. PR #500’s source audit found 403s on Balbums discovery and search and no canonical eFukt links from CI. Bunkr’s active index search, album links and signed/direct resolution passed. Bundled fallback and remote provider configuration are unchanged. |
| Low | Foreground download timeout pauses workers and stops the service while workers finish cleanup. Normal cancellation disconnects active HTTP and closes the service after the last job. A timeout/cancel race was not reproduced. |
| Low | Spotlight cancels work on close and discards stale results. Blocking provider fetches may continue until their configured timeout if interruption is unsupported. The currently bounded work and known-good fallback remain. |

Previously documented WorkManager initialization, OnlyHaven response-read and
Bunkr gallery null-result guards are present in current source. The navigation
review included player release on ShitTok teardown, detail-player pause on stop,
gallery cancellation/lifecycle guards, retained feed state and the post-rotation
fullscreen path. This is source evidence. It does not establish physical
memory, ANR, CPU or battery performance.

## Upgrade and signed build gates

The actual public 7.4.0 APK reports production package
`com.addy37.crazyshitunofficial`, code `7004000` and signing certificate SHA-256
`04abf84b3d468cbb1a562dea83b0007e686b2e56018cce911223963faea51356`.
Its APK SHA-256 is
`eb784bf5d393955f99ce8dbbf1ffd4012c2847487286b90f6067b644c67c4173`.
Stable 7.3.0 is the second representative supported 7.x baseline.

The phone-candidate workflow runs focused release unit tests and assembles one
signed production APK. The full debug and release suites are deferred until
production validation. It checks package, certificate, version progression,
source commit, production routing, disabled legacy analytics and the packaged
What's New resource. The offline Android 15 gate installs both public baselines
with representative existing data, replaces each with the signed APK, checks
UID, first install, local media and settings, and exercises upgrade startup,
What’s New acknowledgement, downloads and secondary navigation. A fresh install
must open the cinematic wizard. The statically verified APK and SHA-256 file are uploaded to Actions before
the emulator runs. The signed source was `7a28dd47ac0b0342bfba989c738211007ffe5664`.
The [signed run](https://github.com/Addy37/ZEROCHILL/actions/runs/45928943983)
passed 144 focused release tests. The [Android 15 upgrade run](https://github.com/Addy37/ZEROCHILL/actions/runs/45931003615)
reused that APK and passed both public baselines and fresh wizard launch.
The APK SHA-256 is
`a6f5d5c9188df32204b56d520901f59ff08f58b686416f16ec2de01fb7423b35`.

For a 7.4 to 7.5 upgrade, the package, Keystore alias, secure-session store,
New Zerochill routing and local data store identities are carried forward. A
legacy 6.x account token issued by the prior project follows the established
7.x sign-in requirement. The offline fixture cannot prove live token validity,
account-backed favorites and blocks, push delivery, or cross-device state. 7.4
had no custom collections. Membership is checked separately through 7.5 backup
export and restore, including import of a version 1 backup without that field.

## Supabase

A read-only aggregate of production New Zerochill logs for
2026-11-26 01:52 UTC to 2026-11-27 01:52 UTC recorded 19,140 edge log events with a request path.
The video social summary RPC accounted for 10,790 successful calls and the
visible-direct-messages RPC for 2,594. Source config version reads accounted for
1,393 events, notifications preferences 450, profiles 140, and Auth token calls
282 (242 HTTP 200, 40 HTTP 400). Push registrations returned 103 successful
POSTs and 362 HTTP 401s. These project-wide totals include production clients
and server activity. They are neither a 7.5 traffic replay nor a monthly forecast.

Response headers report at least 1,715,848 storage response bytes across 16
events. Many responses omit a size, and cached or compressed transfer changes
the billed total. No billed egress or free-tier runway estimate can be inferred
from this sample. The release route uses the established low-traffic flags:
coalesced social summaries with a bounded cache, five-minute social recovery
polling, notification preference caching, a conditional two-hour configuration
refresh and disabled legacy analytics. DM polling is removed on pause and
requests carry account and cleanup revision guards. No Android Realtime
subscription was identified in the audited path. No backend deployment, policy
change or remote provider update is part of this branch.

## Phone acceptance

1. Keep the existing public installation. Record the account, favorite creators,
   saved items, Watch Later, history and playback timestamp, downloaded files,
   blocked creators/users, accent, notification settings and playback settings.
2. Install the candidate over that installation and open from its normal icon.
   Confirm the startup wizard stays dismissed and the 7.5 What’s New appears once.
   Scroll to the end, dismiss, relaunch, then reopen What’s New from Settings.
3. Compare recorded data and open an existing download. Resume a Shows item,
   create a collection, assign favorite media, and verify backup/restore of its
   membership. Restore a 7.4 backup on a disposable install if available.
4. Switch all five ShitTok feeds, then return to each. Confirm position,
   timestamp, pause/mute, buffering and ownership after Library, gallery,
   rotation, fullscreen, gesture back and background transitions. In fullscreen,
   tap once to toggle the complete overlay, double-tap both sides to seek, drag
   to a new timestamp, try the speed menu, and confirm that vertical and horizontal
   drags stay on the clip. Exit with both Back controls and try a rapid re-entry.
5. Search across Spotlight providers, switch Erome filters during a load, scroll
   Erome/Bunkr pagination, open a Pawchive/creator gallery, and drag the Favorite
   Creators alphabet. Check slow/offline behavior and that stale search results
   never replace the current provider.
6. Start a download, pause, resume and cancel. Leave the app during a download
   and confirm the notification and service finish with the last job.
7. With two test accounts, check session continuity, message sending/receiving,
   long-thread newest messages, account switching, blocks, reconnect and push
   delivery on a device using the production package.
8. On a separate clean install, complete all four wizard steps. Check full mascot
   animation and reduced motion. Confirm the splash follows the saved accent
   and the launcher uses default cyan or system theming. Verify status and
   navigation bars on narrow, tablet, landscape and gesture-navigation layouts.
9. On a lower-end device, repeat gallery and ShitTok navigation for an extended
   session and record stalls, background audio, OS pressure and crashes.

Publication readiness stays **NO-GO** until the exact candidate passes the
required tests and phone acceptance. Publication requires the owner's explicit
authorization.
