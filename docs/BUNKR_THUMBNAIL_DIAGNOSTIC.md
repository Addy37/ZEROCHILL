# Bunkr thumbnail diagnostic, 2026-10-28

Inspected current main `576024c7`, including merged PR #353 and its artwork,
signing, cache-expiry and MP4 AAC-metadata fixes. This investigation is separate
from ShitTok PR #361.

## Reproduced failure

Read-only probes used the bundled configured User-Agent, request Accept and
page-origin Referer. No cookies or credentials were logged.

- `https://bunkr.cr/a/WlA5Bbyp?page=1` and
  `https://bunkr.cr/f/Fuyd9dOX3ZbhP` returned HTTP 200, `text/html`,
  195 bytes, with title `Site Unavailable` and body `Unable to access this site.`
- The same album path on configured `bunkr.fi`, `bunkr.site` and `bunkr.ph`
  returned album HTML, approximately 34 KB. The `.fi` file page returned 24,591
  bytes with social artwork and a video grid cover.
- Both actual album raster URLs on `static.scdn.st` returned HTTP 200,
  `image/png`, valid PNG magic bytes, and no redirect. Their basenames end in
  `p744ZWo1.png` (46,721 bytes) and `s0n7xwKB.png` (45,013 bytes), under the
  album's `c7a9b5d3-1e4f-4c82-a6d8-3b7e9f0c2a1d/thumbs/` path.

These are responses observed from this execution environment, not proof that
all devices or networks receive the same host response. No failing physical-device
item IDs were supplied, so this does not establish the cause of every reported
missing thumbnail or identify a failing raster CDN response.

## Application path

`BunkrRepository.fetchBunkrDocument` tries the item's canonical page host before
the configured origins. `isBlockedPage` rejects several challenge/error titles
but did not reject `Site Unavailable`. An HTTP-200 unavailable document was
therefore accepted and prevented fallback to the working configured hosts.
`fetchFileArtwork` returned an empty result, and `BunkrArtworkResolver` cached
that result for five minutes. The grid repair callback ignores empty results,
leaving the dark placeholder until another bind/request. Expiry alone does not
schedule a visible-tile retry.

This is a page-response classification/fallback defect in artwork repair,
with negative caching prolonging the symptom. It is not a demonstrated PNG
extension, signed-thumbnail expiry, redirect, gallery-pagination race, or
missing raster metadata problem in the two working samples.

## Targeted follow-up

Reject the observed unavailable title using the existing blocked-page classifier,
then allow the existing configured-origin fallback to run. Add offline fixtures
for unavailable canonical host plus working fallback, and working canonical host
without fallback. No domain, CDN, route, header, timeout, retry count, persistence,
application ID, signing, backend or remote-config change.

Removing `.cr` from remote origins alone cannot reliably repair old `.cr` items:
the code always tries their requested origin first. This classifier defect needs
an app change. A config publication is not required for the targeted fix.

Regression risk is limited to pages bearing an unavailable-site title. Working
canonical pages retain their current path; all fallback origins remain configured.
The shared page fetcher also benefits playback resolution when it sees this host
response. That is separate from the confirmed malformed AAC descriptor repaired
in PR #353. Working thumbnails do not establish video playback success.

## Other findings, not changed

- The gallery grid uses its compiled User-Agent/Referer and does not apply Bunkr
  remote header overrides. No observed raster response established that headers
  caused these samples to fail.
- Positive artwork metadata caches for one hour without testing image success;
  a failed repaired URL gets no additional repair attempt on that bind.
- An empty repair has no explicit unavailable-image UI or automatic retry after
  its negative cache expires. Do not label this an ongoing load indefinitely.
- Existing live-contract coverage checks URL shape for album artwork, not an
  HTTP/decode result for every thumbnail. Extend device diagnostics with the
  failing item's page URL and image response status/content type if symptoms
  persist after this narrow fix.

Validation results belong in the follow-up PR. No live source settings changed.
