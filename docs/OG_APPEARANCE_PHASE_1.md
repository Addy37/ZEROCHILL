# ZEROCHILL 7.0 Appearance | OLED-only direction

Source: current approved OG main lineage. Development branch: `feature/og-appearance-foundation-7`.

## User decision (2026-11-09)

ZEROCHILL keeps OLED black as its **only** app theme. The user explicitly wants to retain the **seven accent color choices** (cyan default, purple, blue, green, red, pink, white). Do not add Light, Dark, Follow System, or alternate app-background themes. Next phase is general UI polish, not more theme modes.

## What the draft includes

- Appearance screen with accent-color swatches, advanced liquid-glass controls, and motion.
- V2-style glass options through OG rendering: Off, Fast, Balanced, Quality, transparency, blur, refraction, vibrancy, rim highlights, optical strength, menu tint and Android 13+ color splitting.
- Dynamic accent propagation into shared UI, bottom Compose navigation, headers, switches, selected native surfaces, account, profile, inbox and messages.
- The default remains OG's OLED black background, white/gray text and electric cyan accents. Other accent choices recolor highlights, **not** the background.
- Previous preview Light/Dark preferences are treated as legacy only. The app reports OLED and returns the compatibility `oled_black_enabled` flag to true without clearing account or user data.
- Pre-existing preview backups with older theme keys remain readable. Saved accent/glass preferences stay in the existing backup format and app_prefs store.
- Media players, content sources, Supabase, downloads, favorites, history, update compatibility, application ID and signing remain unchanged.

## Current limits

- Some older components still use hardcoded accent colors and will be covered by later **targeted** UI polish.
- Popup menus use theme-aware translucent paint, not the exact live refraction path of the navbar.
- Do not merge before a signed build and physical test on OnePlus Android 16. This draft is not a public release.

## Validation

Previous preview-3 build was successful at `b2ab28c` with focused AppearanceTheme and HeaderGlassMaterial tests and a signed release artifact. The OLED-only accent-preserving revision is newer and **has not been built or tested yet**.
