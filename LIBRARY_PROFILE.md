# ZEROCHILL Library profile

Library reads the existing retained, deferred LibraryHubView shell. LibraryProfileHeader uses the matching AccountProfileCache identity and avatar, with a signed-out fallback linking to the existing account screen. Messages, Notifications and Settings are compact, accessible actions. Unread information comes from existing local badge/inbox state. Header rendering adds no account/inbox polling or profile query. The redundant profile subtitle is omitted. The toolbar avatar is suppressed only while Library is selected, including across profile refresh/resume; other tabs retain their existing account shortcut.

The content order is Favorite Creators, Liked Videos, Downloads, Watch Later, History. Collection previews retain their existing bounded rails and reuse unchanged views across refreshes. Favorite Creators uses two rows of the original 72 dp circular avatars, with the existing custom crop support. Each media collection has separate independently scrolling portrait (104 x 185 dp) and landscape (176 x 99 dp) thumbnail rows, preserving collection order within each row. Empty rows are hidden. Orientation comes from the actual decoded thumbnail, with no new probes, source requests, persisted fields or guesses from the source name. Unknown artwork initially uses the landscape placeholder; confirmed portrait thumbnails move to their row in place. The image decode preserves aspect ratio and is bounded to 256 dp, with the original ImageView crop presentation. Orientation and row positions remain cached for retained views. History progress measures the current card width. Empty collections are omitted; the likes collection retains a compact shortcut so older likes can be loaded explicitly. No decorative counters are queried. Section subtitles are omitted and section spacing is 32 dp. Video previews share a 13 sp, two-line title area over a restrained bottom gradient. Generated OnlyFap numeric IDs are omitted from preview text only; accessibility descriptions, persisted titles and playback metadata retain the originals. Completed downloads show a small cyan download/check icon; active/error status text and full accessible status remain available.

History replaces the duplicated Continue Watching presentation. Recent-first ordering and completed state come from PlaybackHistoryStore. Unfinished cards expose progress and remaining time or Resume, and route through the original saved-position listener. Completed items replay from zero through the existing resume policy. The full History destination also shows progress for unfinished entries. Internal Continue Watching APIs and other screens remain intact.

The first unresolved snapshot displays a restrained loading placeholder, never the true empty state. Normal refreshes retain content, artwork and rail positions. Account ownership changes clear the previous media immediately before the next background snapshot. Bottom content padding includes the floating navigation height plus spacing; the host keeps its existing system/cutout insets.

## Likes and compatibility

The existing server `video_likes` table remains the only authority for likes. It contains account IDs, hashed content keys and timestamps, without video URLs, titles or artwork. Device discovery exclusions are deliberately not treated as current account likes: they are device-wide, may include unliked items and contain non-reversible aliases.

SocialContentContextStore now also holds a derived, account-scoped cache of confirmed likes, alongside its existing native video metadata. Successful existing like reads/toggles update this cache; failed mutations do not. ShitTok and detail like calls pass their already available metadata to that same repository, on its worker, without changing playback or source behavior. Confirmed unlikes remove cached entries. Cache writes and reads reject mismatched accounts. Existing watch history and Watch Later metadata match older server keys without source scraping. The cache retains metadata/URLs independently of later local history deletion, with up to 2,000 confirmed rows per account.

Library opens locally. LikedVideosActivity is a recycled list, with explicit Refresh and 100-row Load more actions against the existing authenticated API. Unknown historical hashes are reported as older likes without video details, not replaced by fake content or guessed URLs. The full view can page beyond the local cache limit in memory. Rotation retains loaded rows, paging and scroll; process recreation reloads the bounded local cache. Refresh starts a new server snapshot, and failures retain displayed content. Other-device unlike changes are reconciled on explicit Refresh, rather than adding Library background traffic.

No schema migration, duplicate authoritative likes database, source configuration, backend deployment, dependency, package/signing identity, existing store rename, download location, backup format or auth behavior change. Existing saves, favorites, downloads and history remain owned by their original stores.

Physical-device checks remain necessary for uploaded artwork, live authenticated sync/pagination, playback resume and portrait/landscape gestures. Native fixture renders use synthetic metadata and fallback artwork.


## Startup and navigation lifecycle follow-up

The Library shell now creates its profile controls and loading placeholder before
activation, without reading account/inbox state or collection stores. Profile/badge
reads share the Library background reader; inactive/account-stale results cannot
replace the visible identity. Snapshot completion releases the loading flag even
when Library has become inactive, and does not build off-screen media views.
A local-read failure retains content or presents a retry message on the shell.

PrimaryTabWorkQueue still starts after a settled frame and prefers idle work, with
a 32ms fallback when a busy message queue does not become idle. It runs one task
per frame and cancels idle/fallback dispatch during motion, pause and close.
Library pauses its thumbnail repair resolvers during exit rather than allowing
hidden WebViews to start more work. Pausing cancels/requeues the current request;
generation checks reject old static, WebView/JavaScript and video-frame callbacks,
including a resumed request for the same URL. Other resolver callers remain active
by default. Existing caches, source configuration, store formats and routes remain.

Focused regressions and the signed workflow's Library lifecycle fixture cover
first-frame shell visibility while a local reader is blocked, exit/return, finite
publication, resolver suspension, stale completion and queue cancellation. The
fixture uses production Library views/queue and synthetic local conditions; it is
not a physical-device performance benchmark. No timing improvement is claimed
until the supplied recordings are checked against the new Beta on the user's device.
