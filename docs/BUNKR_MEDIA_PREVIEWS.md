# Bunkr media previews

Some source files have no real thumbnail. The grid first uses supplied artwork and
its existing file-page artwork repair. If artwork is absent or fails, a visible
Bunkr tile requests an original-media preview. Other providers keep their current
behavior. Playback models and resolved playback caches remain unchanged.

- Images: resolve the original through BunkrRepository, accept an image response
  up to 12 MiB, decode with sampling and store a JPEG no larger than 384 pixels.
- Videos: resolve through the same repository, extract a scaled keyframe near one
  second, then try zero only if extraction returns no frame. This creates no player.
- Android 8.0 uses frame extraction followed by scaling; Android 8.1+ uses scaled
  extraction directly. Minimum SDK stays 26.
- One global worker and at most 24 queued jobs. Retained filter pages outside
  the clipped viewport do not start preview work. Detached/recycled or offscreen tiles cancel
  queued and running preview tasks, and stale results cannot replace new bindings.
  The browser pauses preview jobs before opening a viewer or entering the background,
  so extraction does not compete with viewer playback.
- Video reads use 256 KiB HTTP ranges, a four-block memory cache, an 8 MiB transfer
  budget and at most 48 range requests. Large responses that ignore ranges fail
  without downloading the full video. The configured Bunkr request timeout bounds
  the media-read deadline. Metadata resolution retains existing repository behavior.
- Network work uses the current Bunkr User-Agent, Referer override and approved
  request headers. No domain, header value, timeout or remote configuration changes.
- Successful previews use a versioned media-kind/file-page cache identity rather
  than expiring CDN URLs. A dedicated 32 MiB disk cache evicts oldest JPEGs.
- Failures are remembered in memory for five minutes with a 256-entry bound.
  Failed previews show "Preview unavailable" and preserve the video play badge.
  No permanent loading skeleton or automatic retry loop is introduced.

Limits: source files still need to resolve and load. Range-unsupported videos,
large image originals, frame decoding failures, and reads exceeding the budget
keep the placeholder. Native decoding and CDN behavior need physical-device
validation. There is no claimed guarantee that every source video yields a frame.

PR #361's Focus Mode at 17ebeb1a8e77dd7210b561669f87aa051f7672fe was approved
on device by the user on October 28, 2026. Keep it unchanged and unmerged. A test
candidate for this feature must include that approved baseline as an ancestor.
PR #362's unavailable-host classifier is included as a prerequisite, with its
existing fixtures; it remains separate and unmerged.
