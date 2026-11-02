# Social keyboard GIF input

Source: `feature/social-keyboard-gifs`, based on verified GitHub `main`
`46cbf4c32ca3d6c5ddf3fd6d5b142fde7907ec67`, a descendant of merged PR #397
(`9ecb92975137d16f0835b76a850146365f5002ab`). No merge or release is authorized.

`SocialKeyboardContent` installs AndroidX receive-content on the existing comment
sheet and DM composers only. AppCompat supplies the IME input-connection bridge on
older Android versions; Android 12+ uses platform receive-content. It accepts GIF,
JPEG, PNG and WebP content URIs, retains the payload during background preparation,
and consumes those items without inserting a URI or placeholder. Shared
`ZeroChillEditText` behavior is unchanged on all other screens.

Keyboards that insert text instead can supply a direct HTTPS `.gif` URL from
`static.klipy.com` (including a query string). Only that exact host, HTTPS and the
default/443 port are accepted; credentials and fragments are rejected. The
download uses the existing background media executor and HttpURLConnection pattern,
bounded timeouts and a streaming 10 MiB limit. All redirects are rejected before
reading the response. No account headers, cookies or arbitrary URL fetcher are added.
The response must pass `SocialMedia.prepareBytes` with the GIF signature and limit.

The current one-attachment slot, animated preview, remove control, Send readiness,
comment/reply submission and DM retry paths remain in use. The picker and keyboard
share selection rendering. During preparation, Send and attachment changes are
disabled. A successful URL conversion removes only the exact tracked URL from the
same editable draft, preserving surrounding captions and concurrent caption edits.
Failure leaves the URL and prior selection intact and displays an error. Mode
changes, cancellation, detachment and account changes discard stale results.
Comment edits remain text-only, following the existing edit contract.

No backend, Supabase migration/deployment, storage bucket, database contract,
dependency, application ID, signing, session or remote source-config change is made.
Both input paths produce the same `SocialMedia.Prepared` used by PR #397 and upload
through its existing `social-media-upload` endpoint and private buckets. GIF bytes
are preserved unchanged. Older clients keep their existing `GIF`/`Photo` fallback.

## Validation

`SocialKeyboardContentTest` covers actual composer MIME registration, native IME
commitContent, Android 12+ delivery, comment/reply and DM media submission, failed
send retention, replacement/removal, deferred URL removal, caption edits, stale
drafts/accounts, failure retention, allowlisting, redirects, invalid bytes, declared
and streamed oversize responses, and fragmented content-provider headers.
Existing `SocialMediaTest`, `InlineCommentsUiTest` and
`ZeroChillMessageThreadUiTest` cover animation, preparation and surrounding UI flows.
The PR records the test/build results actually obtained.

## Physical acceptance (pending)

On the Android device with the KLIPY keyboard panel:

1. Insert a GIF-only new comment and a caption + GIF reply. Check animated preview,
   empty URL text, successful send, correct reply parent and inline animation.
2. Repeat in DMs. Check grouping, seen state, long-press reactions and keyboard-open
   composer size; verify an offline send retains its draft and retries correctly.
3. Replace an existing picker attachment with a keyboard GIF, then remove it.
4. Paste a direct KLIPY GIF URL between caption words; check that only the URL is
   removed after preview success. Ordinary URLs must remain text.
5. With network unavailable, verify that a KLIPY URL stays in the composer with an
   error and the previous attachment remains selected.
6. Check comment scrolling, replies/likes/edit/delete and DM report/block controls.

The fallback currently supports only direct KLIPY GIF URLs, not other providers,
HTML sharing pages, video/sticker formats, redirected URLs or multi-attachment
messages. A single completed URL is converted per insertion. Unsent attachments
remain process-local, matching the existing picker; process death discards them.
Automated tests do not establish physical keyboard acceptance.
