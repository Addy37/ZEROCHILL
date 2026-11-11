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
