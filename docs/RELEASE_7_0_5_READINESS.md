# ZEROCHILL 7.0.5 release gate — issue #446

Prepared from freshly fetched `main` at `3d69cfa1d67b20cce31b2563c80e2cf775e0a651`.
Latest public release remains `v7.0.4`, source `c4b9ad45fdacdb28347eb6ba7c369870006b3b23`.
This is a candidate only. No merge, tag, release, remote-config publication or backend deployment is authorized.

## Crash investigation

Read the three recent New Zerochill `app_feedback` crash reports and their complete
available `feedback_messages` diagnostics on November 15. The Downloads report names
`0651dd726c04a6438496f0600722857660fe1671` (tag v7.0.3); both main-screen
reports name `c4b9ad45fdacdb28347eb6ba7c369870006b3b23` (tag v7.0.4). No feedback records were changed.

| Report | Evidence and disposition |
| --- | --- |
| 7.0.3, Samsung SM-A166U, Android 17, Downloads, November 14 21:21 UTC | Confirmed application reentrancy defect. `showAsDropDown` can synchronously scroll a parent that recycles/detaches the anchor. The app's detach listener dismisses the popup while Android is still positioning it; `invokePopup` then dereferences its cleared decor. A recycling-parent regression on Android 15 reproduces the same framework NPE before the fix. An anchored-window subclass defers dismissal until positioning exits, including exceptional exits. Existing placement, scrolling, menus, callbacks and glass composition are retained. Also reject attached anchors whose activity is finishing/destroyed and clean up failed native/Compose shows. Both pre-fix regression cases failed and pass after the fix. A further injected expired-token/detach regression found two dismissal notifications across Compose/native fallback; one shared per-show notification guard fixes this edge as well. This is a reproduced failure mechanism consistent with the reported stack; no Samsung Android 17 device reproduction is claimed. |
| 7.0.4, Samsung SM-S936U, Android 17, main, November 15 02:18 UTC | Unconfirmed, potentially high severity. The OOM occurs allocating a framework diagnostic stack in `View.setRequestedFrameRate` during Compose drawing. No heap dump, allocation history or retained-object chain is supplied. Inspected feed queues (24 ahead), stream cache (32), playback pool/allocator, player retirement (stop, clearMediaItems and pool yield), activity/adapter/feed close paths and recorded-page RenderNode release. Three player leases and bounded preloads are managed by `ShitTokPlaybackOwner`; this does not establish total heap usage. No frame-rate suppression, broad OOM catch, bitmap rewrite or claimed leak fix is justified. Emulator memory snapshots are observational only and do not reproduce the OEM report or long-session live-media load. |
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

Each test class now gets its own bounded JVM (`forkEvery=1`, at most two workers, 1 GiB heap each),
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
| OnlyHaven | Eight creator/category API requests returned HTTP 500; dependent profile/posts check could not run. HTML cq page contract passed (may be a JS shell). | External outage; usable rendered results/profile playback remain unverified. Existing JSON → HTML → rendered fallback stays intact; category membership is never guessed. Added bundled-pattern/HTML-parser coverage, including rejection of foreign hosts and encoded traversal. Read back active v13: all seven complete source sections equal bundled defaults. No verified route drift supports a remote change. The official creator page also displays “Failed to load creators” ([read-only website observation](https://cum.st/creators)), corroborating an upstream failure rather than a confirmed Android parser regression. |

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
A short-display/200% text compatibility test also passes against untouched main. An initial global-window visibility assertion was a Robolectric harness error, not evidence of a product defect; the final test uses the actual dialog width, the supported font-scale API, nonzero/bounded measurements and action bounds in the viewport. The approved Support presentation is preserved. The modified dialog helper now explicitly opts into the Media3 API contract it already uses, addressing its lint opt-in errors without suppressing diagnostics. Updated notes have six visible highlights, including source limitations; the content test
now checks the new authored count and key release/limitation text. No public old branding
was reintroduced.

A later full source audit [run 43733638580](https://github.com/Addy37/ZEROCHILL/actions/runs/43733638580)
at 16:07 UTC passed all ten OnlyHaven contracts from **westcentralus**, without an app or
backend change. This establishes working metadata/search/category/profile paths in that
runner at that time; it does not prove global recovery from the earlier centralus 500s.
Classify OnlyHaven as **intermittent / runner-dependent upstream availability**, not a
confirmed app regression. EFukt again passed all four contracts from **centralus**;
regions without accessible responses remain **region-limited / not verifiable from runner**,
not evidence to disable EFukt. Bunkr from **eastus2** passed the active index, album and
signing checks; both Balbums 403 failures remained visible. The other six providers and
schema validation passed (31 backend schema/handler tests, zero failures; Deno checks passed).

## Identity and compatibility

Target `7.0.5` / `7000005` exceeds stable `7.0.4` / `7000004`. The separate `.dev`
betas with higher codes are not production upgrade targets. Keep application ID
`com.addy37.crazyshitunofficial`, certificate SHA-256
`04abf84b3d468cbb1a562dea83b0007e686b2e56018cce911223963faea51356`, New Zerochill
`aavjpykssueavbseqdlf`, and empty legacy analytics endpoints.

No database, preference, download, favorite, history, backup, account or remote-config
format changes. No backend writes/deployment or source configuration publication.
The New Supabase management API also confirmed the expected project URL and that the compiled publishable client key is present and enabled. Candidate tooling verifies actual APK identity/signature/DEX backend markers/source SHA
and checksum against the published 7.0.4 artifact. A disposable offline Android 15
emulator check installs over 7.0.4, checks first-install identity and seeded saved stores,
backup/download bytes, startup and repeated Downloads/rotation navigation. Synthetic
emulator data is not physical-phone or live-source acceptance.

The final gate tests/signs `ea55674972c047efc2c42aaffc0e60e566c7203e`, including
stable wording for OnlyHaven's intermittent availability. Its first step cancelled only
superseded runs `43731496109` and `43733538152` after checking their exact branch/SHA.
The cancellation helper had Actions write permission solely for those pinned cancellations;
repository contents permission remained read-only and no release/deploy step exists.
The superseded first run completed 1,811 Debug tests (zero failures/errors, four existing
skips; all eight speed/mute cases passed) but its Release task was cancelled. It is **not**
used as a completed Release gate or the delivered candidate.

## Validation and publishing disposition

The final gate and independent downloaded-artifact review passed. The publishing
recommendation remains **HOLD**: the two fresh, unconfirmed main-screen crashes need
affected-device evidence/acceptance or an explicit product risk decision. This is not
a HOLD solely because an EFukt probe is unavailable in one region. Lint debt, variable
upstream availability and unverified live/OEM behavior remain explicit limitations.

### Local validation before final artifact

- Final focused Debug regressions: **35 passed, zero failures/errors/skips**.
- Focused Release regressions on final application source: **43 passed, zero failures/errors/skips**, including all eight speed/mute cases.
- Separate source/config/OnlyHaven regressions: **17 passed, zero failures/errors/skips**.
- Offline audit regressions: **3 passed**.
- Local Debug and Release APK assembly passed. These local builds use the local default
  signing configuration and are not the production signed deliverable.
- Main baseline lint: **361 errors, 488 warnings**. Final code lint: **348 errors,
  488 warnings**. Compared all findings by detector/severity/message/file: **no new
  findings; 13 existing Media3 opt-in findings resolved**. Lint still exits nonzero;
  its remaining inherited debt is not waived or hidden.
- Initial local SDK-36 test attempt failed to download the official Android 16 fixture
  because Maven redirects to a host outside the workspace's allowlist. Full suites
  therefore run in Actions with both Java 17 and 21; no SDK annotation/test was removed.

The same source audit repeated at 16:23 UTC ([run 43735175273](https://github.com/Addy37/ZEROCHILL/actions/runs/43735175273)):
Bunkr active paths passed in eastus2 with two separately failing Balbums 403s; OnlyHaven
passed all ten contracts in westus3; EFukt passed all four in centralus; all other
provider jobs and schema validation passed. No source was disabled and no backend
change preceded these recoveries.

## Final gate and delivered APK

[Complete gate 43735170508](https://github.com/Addy37/ZEROCHILL/actions/runs/43735170508)
completed successfully on November 15 at 17:20 UTC. Its full Gradle test/build command
ran for 51m 58s; no failed unit test was ignored. Every test class ran in a fresh JVM.

| Check | Actual result |
| --- | --- |
| Full Debug unit suite | 1,813 tests across 287 classes; 0 failures, 0 errors, 4 existing opt-in skips |
| Full Release unit suite | 1,813 tests across 287 classes; 0 failures, 0 errors, the same 4 skips |
| Historical speed/mute class | All 8 cases passed in both complete variants, including `multiTouchAndRealPinchCancelHold` |
| Debug and Release APK builds | Both passed |
| CI Debug lint audit | Exited nonzero: 348 errors, 493 warnings, 1 informational item. Retained in full; not claimed clean |
| Actual APK signature/package/version/backend/source check | Passed against published 7.0.4 |
| Android 15 in-place upgrade | `adb install -r` passed; package first-install time preserved |
| Representative data and offline UI | Favorite/Watch Later, history, custom-download metadata and valid local MP4, representative backup file and public download bytes preserved; existing Downloads row/menu, Favorites, account/inbox/settings navigation passed |
| Captured runtime fatal markers | None: no Java fatal, native fatal signal, ANR marker or OOM in the captured offline session |

The four unchanged opt-in skips in **each** variant were:

- `FapelloLiveSmokeTest.liveSearchCreatorPaginationImagesAndVideos`
- `RemoteSourceConfigManagerTest.publishedProductionDocumentActivatesInCurrentClient`
- `SourceExpansionTest.currentClientParsesLiveOnlyHavenSearchAndMenCategory`
- `BunkrLiveContractTest.recordedFileResolvesToRealMp4BytesAndItsAlbumHasRasterArtwork`

They were not newly disabled. Separate live metadata audits exercised all nine providers,
including the active Bunkr Index path. These do not replace native live video decoding,
seek/expiry, live authenticated social flows or physical-device acceptance.

One Robolectric JVM shutdown emitted `DirectoryNotEmptyException` while destroying a
native-runtime `hyphen-data` temp directory, alongside fixture decoder/Glide shutdown
messages. This is a remaining **low-severity test-harness cleanup warning**, not a failed
product assertion or a production Android crash. The original `FileSystemAlreadyExistsException`
unit setup failure did not recur in either full suite. No exception/assertion was muted.

[Independent artifact review 43740983736](https://github.com/Addy37/ZEROCHILL/actions/runs/43740983736)
downloaded the completed artifact, checked the archive hash against GitHub's recorded
digest, extracted exactly one APK, verified its bytes/checksum, reran `apksigner` and
`aapt2`, and inspected the complete lint/runtime/memory reports without rebuilding.
The five net extra CI warnings versus the local baseline are online `GradleDependency`
update notices on unchanged Compose, Firebase, Material, Browser and WebKit declarations;
the Core update notice also changed its suggested version. One KTX informational suggestion
is CI-only. No new code-behavior lint finding was identified. Dependency versions were
preserved rather than introducing unrelated library upgrades into this candidate.

Offline PSS samples were 86,670 KiB initially and 116,032 KiB after navigation; the latter
also had an extra activity and a WebView. This short, cold/offline sample establishes
neither a long-session leak nor a memory improvement and does not reproduce the Samsung OOM.

### APK verification record

[Download the signed RC artifact](https://github.com/Addy37/ZEROCHILL/actions/runs/43735170508/artifacts/14172520587)
(one APK plus complete reports; expires December 15, 2026).

| Field | Verified value |
| --- | --- |
| APK | `ZeroChill-7.0.5-release-zerochill-7.0.5-ea55674.apk` |
| Source branch | `release/zerochill-7.0.5` |
| Embedded source commit | `ea55674972c047efc2c42aaffc0e60e566c7203e` |
| Application ID | `com.addy37.crazyshitunofficial` |
| Version | `7.0.5` / `7000005`, above 7.0.4 / `7000004` |
| Certificate SHA-256 | `04abf84b3d468cbb1a562dea83b0007e686b2e56018cce911223963faea51356` — matches 7.0.4 |
| Signature | APK Signature Scheme v2 verification passed; valid for the unchanged min SDK 26 |
| APK size | 23,203,752 bytes |
| APK SHA-256 | `8e043cb79545fe40d5b6aa942c5a01af59edb631a43bff89af1d9ace0ab06a01` |
| Artifact ZIP SHA-256 | `a531e3b92740b7afd5ce08fb5203bb56bb05573aed0a94218fec03f598676124` |
| Backend | `https://aavjpykssueavbseqdlf.supabase.co`; matching New feedback/config endpoints and enabled public client key |
| Legacy analytics | Both endpoints empty; V7 traffic enabled |
| APK byte scan | No retired/test project reference or secret-key marker; expected New URL/endpoints and exact source revision present |

Final PR-only report/workflow-cleanup commits do not change the application tree from
this tested/signed source. The production signing bundle remains private in Actions;
no signing material is committed or included in the artifact. No merge, tag, release,
source-config publish or backend deployment was performed.

## Remaining issues and disposition

- **Potentially high:** unconfirmed Samsung Android 17 OOM and vivo Android 16 framework
  layout NPE. Need affected-device reproduction/heap/transition evidence; no speculative
  application or OEM root-cause claim. The separately reproduced Downloads popup path is fixed.
- **Medium:** inherited lint debt (348 errors), live authenticated social/playback/OEM
  acceptance not established by synthetic offline tests, and independent Bunkr Index
  availability/integration-specific permission/rate-limit/SLA unknowns.
- **Variable external availability:** Balbums discovery/search remain HTTP 403 even when
  the active Bunkr Index/album/signer paths work. OnlyHaven initially returned 500, then
  passed all ten contracts in westcentralus, westus3 and eastus without an app change.
- **Region-limited / not verifiable from runner:** EFukt passed 4/4 in centralus multiple
  times, then returned no canonical metadata in southcentralus at 17:25 UTC
  ([audit 43740989772](https://github.com/Addy37/ZEROCHILL/actions/runs/43740989772)).
  The latter contracts fail visibly; they do not establish a global outage or a patch
  regression. Preserve its integration and known-good fallback. No restriction bypass.
- **Low:** remaining Robolectric temp-directory cleanup warning during JVM shutdown.

**Publishing: HOLD.** The signed production-identity RC is technically verified and
ready for physical review. The unresolved crash risks and acceptance limits above are
not erased by the green test/build/upgrade gate. Do not merge, tag, publish or deploy
as part of this pass.
