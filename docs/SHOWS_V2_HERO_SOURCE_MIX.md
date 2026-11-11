# Shows V2 hero source mix follow-up

This branch extends draft PR #416 and is based on its signed-test candidate.
Do not merge it independently into main before #416 is approved.

## Scope

- Populate the existing square OG Shows homepage hero with **loaded**
  CrazyShit series/categories, Kaotic categories, EFukt series,
  XMegaDrive categories and BaddieHub categories, using the five-source
  round-robin pool from V2. Use a per-instance stable shuffle to avoid
  advertising only the first provider items.
- Maintain the existing eight-dot maximum, six-second rotation, drag selection,
  source identity and exact collection routes. Keep OG's episode detail hero,
  inline paging, video player and thumbnail-to-player transitions untouched.
- Reject known placeholder URLs and duplicates before requesting hero artwork.
  Check decoded remote artwork dimensions using V2's landscape/portrait limits.
  Decode with a bounded full-artwork target before display fitting so narrow
  portrait covers are not mistakenly rejected due to thumbnail scaling.
  If a remote image fails or is too small, exclude that image for this view's
  lifetime and automatically select a remaining candidate.
- When all remote candidates fail, try the existing locally embedded artwork
  instead of repeatedly presenting black hero slides. If no image is available,
  retain a neutral Featured Shows placeholder with no dead View Show button.
- Continue using Glide's existing disk cache, existing image request headers,
  the next-hero preload and the source-specific XMegaDrive session headers.
  Do not fetch extra provider catalogs or add image validation network probes.
- Reduce Search and More icon drawings to 18dp inside their existing 48dp
  accessible controls. Keep the OLED background, square hero corners and
  previous destinations.

## Validation

- `ShowsHeroPicksTest` covers five-source mixing, fairness, deduplication,
  placeholder/rejected-image filtering, deterministic seed and image dimensions.
- `ShowsHeroDesignTest` keeps the 48dp touch targets and checks icon padding.
- Check the candidate on the OnePlus Android 16: rotate through each provider;
  verify bad/missing images are skipped rather than remaining black; verify the
  displayed title and View Show open the same collection; verify that search,
  More, navigation and the episode player still work.
- Check no extra login/age challenge occurs when loading XMegaDrive hero picks.
  The existing visible Shows category loader remains responsible for access.

No changed providers, parsers, content-source domains or remote configuration.
No changes to Supabase, app package, stable signing identity, persisted settings,
favorites, history, downloads, backups, source feeds, player or dependencies.
Physical acceptance and installation-over the current signed preview are pending.

## Focused visual follow-up (preview 42)

Continues PR #417 at `8daa14c9495a4ab7db38313a90803e89c2005995`.
Only the homepage artwork-to-bottom scrim, landscape thumbnail captions and
View Show pill change. The scrim uses V2's 30/53/77/100 percent stops over the
artwork height so its final color meets the existing bottom bar. OG hero corners
remain square. Landscape cards lose the dark surface beneath captions, with
12sp medium type, 16sp lines and a 6dp thumbnail gap; allocated card/rail sizes
remain unchanged. Portrait overlays stay intact. View Show uses a filled,
accent-glazed glass appearance, 17dp corners, 44dp height, 19dp play icon and
12sp bold text. It retains the existing openHero listener and 128dp width.
V2's native Compose sources are the styling reference; no screenshot attachments
were available in this continuation. No backdrop-capture or animation is added.

The existing signed-candidate workflow produces preview 42 (`6000044`), above
preview 41 (`6000043`), with unchanged production application ID and signing key.
Package, certificate and version gates run against the latest stable APK.
No changes to hero picks, artwork requests, dots, navigation, rails, episode
pages, player, animations, stored data, backend, remote config or dependencies.
Physical installation and visual acceptance remain for the user; successful
host tests/build/signature gates do not establish physical device acceptance.
