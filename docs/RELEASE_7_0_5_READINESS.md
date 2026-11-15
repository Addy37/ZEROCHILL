# ZEROCHILL 7.0.5 release gate — issue #446

Prepared from freshly fetched `main` at `3d69cfa1d67b20cce31b2563c80e2cf775e0a651`.
Latest public release remains `v7.0.4`, source `c4b9ad45fdacdb28347eb6ba7c369870006b3b23`.
This is a candidate only. No merge, tag, release, remote-config publication or backend deployment is authorized.

## Crash investigation

Read the three recent New Zerochill `app_feedback` crash reports and their complete
available `feedback_messages` diagnostics on November 15. No feedback records were changed.

| Report | Evidence and disposition |
| --- | --- |
| 7.0.3, Samsung SM-A166U, Android 17, Downloads, November 14 21:21 UTC | Confirmed application reentrancy defect. `showAsDropDown` can synchronously scroll a parent that recycles/detaches the anchor. The app's detach listener dismisses the popup while Android is still positioning it; `invokePopup` then dereferences its cleared decor. A recycling-parent regression on Android 15 reproduces the same framework NPE before the fix. An anchored-window subclass defers dismissal until positioning exits, including exceptional exits. Existing placement, scrolling, menus, callbacks and glass composition are retained. Also reject attached anchors whose activity is finishing/destroyed and clean up failed native/Compose shows. Both pre-fix regression cases failed and pass after the fix. This is a reproduced failure mechanism consistent with the reported stack; no Samsung Android 17 device reproduction is claimed. |
| 7.0.4, Samsung SM-S936U, Android 17, main, November 15 02:18 UTC | Unconfirmed, potentially high severity. The OOM occurs allocating a framework diagnostic stack in `View.setRequestedFrameRate` during Compose drawing. No heap dump, allocation history or retained-object chain is supplied. Inspected feed queues (24 ahead), stream cache (32), playback pool/allocator, player retirement, activity/adapter/feed close paths and recorded-page RenderNode release. Three player leases and bounded preloads are managed by `ShitTokPlaybackOwner`; this does not establish total heap usage. No frame-rate suppression, broad OOM catch, bitmap rewrite or claimed leak fix is justified. Emulator memory snapshots are observational only and do not reproduce the OEM report or long-session live-media load. |
| 7.0.4, vivo I2214, Android 16, main, November 15 01:20 UTC | Unconfirmed, potentially high severity. Stack contains only `FrameLayout.layoutChildren`/framework traversal; diagnostic session was leaving ShitTok. Inspected main search/overlay removal, retained pager/host pause/resume/close, insets, popup detachment, glass host attachment and owner disposal. No specific app view or reentrant layout callback is established by the trace. Preserve working navigation/animations rather than suppressing framework NPEs. Requires affected-device transition logs or reproduction. |

No crash report is attributed to an OEM defect merely because its top frame is framework code.
The popup fix must not be represented as a fix for the independent main-screen layout crash.

## Test isolation

Historical full Release failure: `ShitTokSpeedMuteTest.multiTouchAndRealPinchCancelHold`,
`FileSystemAlreadyExistsException` during Robolectric native-font initialization, before
product assertions. Eight isolated cases previously passed. Inspected Robolectric 4.16's
`DefaultNativeRuntimeLoader`: font extraction opens a JVM-global ZIP filesystem by URI;
its loader and interruption lifetime can cross sandbox/class boundaries. 4.16.1 and
4.17 retain that extraction mechanism, so an unrelated dependency upgrade is not justified.

Each test class now gets its own bounded JVM (`forkEvery=1`, one worker, 1 GiB heap),
replacing batches of ten classes. Native font resources and interrupted background loaders
cannot leak into a subsequent class. Native rendering, all assertions, all SDK annotations,
and every test remain enabled. Full Debug/Release results, including the original eight
speed/mute cases and existing opt-in skips, must be reviewed before publication.

## Sources and recently merged changes

Read-only investigation: [run 43729981154](https://github.com/Addy37/ZEROCHILL/actions/runs/43729981154),
November 15 15:27 UTC, Azure **centralus** (runner setup log). The workspace itself
has a restricted proxy and does not expose a trustworthy region; its CONNECT denials
are not provider responses. No proxies, CAPTCHA solving or restriction bypasses were used.

| Source/path | Observation | Application disposition |
| --- | --- | --- |
| Bunkr Index active search | Both JSON pages passed canonical metadata/pagination checks | Preserve user-approved path; fix checked response reading so interrupted reads reach the existing Balbums fallback instead of escaping as `UncheckedIOException`. One bounded index request per requested page, 512 KiB response cap, timeout retained. |
| Bunkr album/file/signer | Album thumbnails/media links and signed-or-direct metadata contracts passed | Preserve native gallery, cookie scoping, referer cache and recovery work from #442/#443. Metadata success does not establish Android first frame, seek or signature expiry. |
| Balbums discovery and fallback search | Both HTTP 403 | Keep failure visible and retain user-initiated browser action. Do not call this path healthy because primary search works. |
| EFukt | All four listing, detail/artwork, search and catalog contracts passed in centralus | Preserve compiled repository and home-feed series fallback. Earlier failures from another runner do not establish global breakage. Availability remains region/session dependent. |
| OnlyHaven | Eight creator/category API requests returned HTTP 500; dependent profile/posts check could not run. HTML cq page contract passed (may be a JS shell). | External outage; usable rendered results/profile playback remain unverified. Existing JSON → HTML → rendered fallback stays intact; category membership is never guessed. Added bundled-pattern/HTML-parser coverage, including rejection of foreign hosts and encoded traversal. Active v13 and bundled creator/media patterns have identical lengths and expected behavior; no verified route drift supports a remote change. |

Bunkr Index's public [About](https://bunkrsearch.link/about) advertises a JSON API;
[Terms](https://bunkrsearch.link/terms) state browsing needs no account/payment and
search-request access logs are disabled. Inspected pages provide no integration-specific
permission, published numeric rate limit or availability guarantee. Operator approval is
not claimed. Queries and source IP reach that independent operator. File-hit pages are
grouped/deduplicated by album within each page, with canonical Bunkr links; this is not
Balbums album ranking and does not guarantee cross-page album uniqueness. The current
search UI requests page one; no speculative pagination redesign is included.

The audit now exercises this actual primary path while retaining both failing Balbums
checks. It records response status/type/final URL and bounded page titles, with no media
body downloads or signing tokens. Offline audit regressions verify primary-path coverage,
malformed/repeated page failures and visibility of legacy failures.

Reviewed every application/test change since v7.0.4, including Support ZEROCHILL
(destination links, dismiss behavior, OLED surface/motion) and #445 What's New/update
scroll bounds, natural compact card height, large-text behavior and retained acknowledgement.
Updated notes have six visible highlights, including source limitations; the content test
now checks the new authored count and key release/limitation text. No public old branding
was reintroduced.

## Identity and compatibility

Target `7.0.5` / `7000005` exceeds stable `7.0.4` / `7000004`. The separate `.dev`
betas with higher codes are not production upgrade targets. Keep application ID
`com.addy37.crazyshitunofficial`, certificate SHA-256
`04abf84b3d468cbb1a562dea83b0007e686b2e56018cce911223963faea51356`, New Zerochill
`aavjpykssueavbseqdlf`, and empty legacy analytics endpoints.

No database, preference, download, favorite, history, backup, account or remote-config
format changes. No backend writes/deployment or source configuration publication.
Candidate tooling verifies actual APK identity/signature/DEX backend markers/source SHA
and checksum against the published 7.0.4 artifact. A disposable offline Android 15
emulator check installs over 7.0.4, checks first-install identity and seeded saved stores,
backup/download bytes, startup and repeated Downloads/rotation navigation. Synthetic
emulator data is not physical-phone or live-source acceptance.

## Validation and publishing disposition

Validation results and artifact identity are recorded below after the candidate run.
Until then the release recommendation is **HOLD**. External source outages and the two
unconfirmed main-screen crashes remain explicit limitations, regardless of passing unit tests.
