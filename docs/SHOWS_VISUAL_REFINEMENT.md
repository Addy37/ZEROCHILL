# Shows visual refinement

## Source and design boundary

This feature starts from explicitly requested `rebrand/zerochill` at
`2f9a489ac6855926978630b7495965c404fe9e38`, a descendant of approved baseline
`ec1dda86`. The legacy CrazyShitAndroid repository redirects to Addy37/ZEROCHILL.
Reference V2 main was verified at `9ce121f923cee5023283ada230062a2c7298e55e`.

The requested source branch is older than the local newer OG development
checkout. In particular, it has no ten-episode range UI, custom-accent settings,
or Shows watch-morph host. This change keeps the requested branch and ports the
small source-independent ten-range helper from newer OG. It does not merge the
newer product lineage, replace the episode hero, or import V2's player.
The existing ShowDetailsHeader is unchanged, including its artwork, metadata,
gradient and Browse Videos action. That older branch's hero differs from the
newer Play-button hero in the supplied OG screenshot.

## Changes

- ShowsHubView retains OG hero candidates and shelf ordering. The homepage gets
  V2 proportions, compact two-row rails, Continue Watching tiles, selectable
  dots with an accent active pill, six-second rotation, and a circular reveal
  after the next artwork loads. Outer hero and artwork corners remain sharp.
  Hero art prefers full remote artwork with bundled error fallback. Snapshot
  allocation is bounded and cancelled on replacement/detach. Shelf title areas
  grow with accessibility text size while keeping the compact default geometry.
- NativeFeedAdapter has a Shows-only compact presentation: 18dp card corners,
  132x80dp thumbnail slots (smaller on narrow displays), accent EPISODE 01
  numbering, and up to three title lines. Entire cards remain tappable and
  long-press actions, thumbnail recovery and playback tracking remain available.
- NativeFeedBrowserActivity retains original provider pagination, sequential
  request guard, current source page, cache and ordered full snapshot. Only the
  visible ten-item range is bound. Cached range navigation performs no source
  request, resets list position and retains the header offset. Appended unseen
  episodes do not rebind an unchanged range. A compact inline footer navigates
  ranges or explicitly requests more at the loaded tail. Range state is saved
  with the existing browser instance state. Source titles are untouched.
- ShowsAppearance reads the established newer OG appearance_accent values from
  app_prefs, defaults to this branch's cyan, and respects the glass-off preset
  for the compact footer. It writes no preferences. This source branch does not
  expose the newer Appearance controls; no settings redesign is included.
- ShowsWatchActivity subclasses the existing VideoDetailActivity. A bounded
  captured thumbnail and weak live source identity provide a 320ms opening and
  280ms reverse, with a 70ms video-to-poster bridge. The original player stays
  attached. The underlying browsing Activity remains present. Related-video
  history and player fullscreen/gesture paths retain their existing ownership.
  Recycled/detached origins, changed videos, rotation and reduced motion use a
  fade/direct fallback. No artwork request is made by the transition. Android
  8.0/API26 uses an opaque fade to avoid its orientation/translucency restriction;
  API27+ uses the transparent host. Global OLED and window/predictive-back polish
  skip only this host so they cannot paint over or override its transition.

## Compatibility and validation limits

No application ID, signing configuration, provider/parser, backend, remote
configuration, database schema, settings writes, favorites, downloads, backups,
or existing persisted history format changes. New transient browser state only
stores the selected range. No dependency or playback engine change.

Local JVM/Robolectric tests cover hero geometry/selection/state, episode layout,
numbering, cached page switching, duplicate-free appends without visible rebinds,
source identity and thumbnail pixels, reduced-motion fallback, geometry, and
API26/27 themes. Existing Shows controls/orientation routing tests remain in the
suite. Record actual final build/test counts in the PR.

No emulator or physical device was available (adb lists no devices; /dev/kvm is
absent). Compilation and host tests do not establish physical motion parity,
GPU frame pacing, real-provider playback, rotation behavior, or memory cost on
OnePlus/Android16. The captured image is bounded to 512px for a player handoff,
so a brief scaling softness is possible. Android8.0 and unavailable origins
intentionally use a fade. Physical approval remains pending.

## Device acceptance

1. Browse every Shows rail; compare sizing/crops/text with V2 references and
   verify hero edges remain square. Swipe/select dots, wait for rotation, and
   confirm artwork reveals without blank frames.
2. Open a show and confirm its existing OG hero is retained. Browse 1–10,
   11–20 and the partial tail; verify numbering, titles and missing/portrait art.
3. Switch cached pages repeatedly; verify immediate cards and list start.
   Background source appends must not replace visible loaded thumbnails.
4. Open episodes, This Week and Continue Watching, including partly clipped
   cards. Back must return to the same source/scroll position. Try fast Back,
   changed source artwork, related videos and missing thumbnails.
5. Verify original controls, scrubber, buffering/error flow, progress/history,
   portrait video, wide video, manual fullscreen, rotation lock and auto-rotate.
6. Test reduced motion, background/resume, recreation and saved appearance
   preferences. On API26 verify the fade fallback and fullscreen do not crash.
