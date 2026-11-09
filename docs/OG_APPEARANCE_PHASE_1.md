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

## Compose remodel phase 1

- Appearance is hosted in `androidx.activity.ComponentActivity` and rendered by `AppearanceComposeScreen.kt`.
- The Java View tree for Appearance was replaced with Compose Material 3 surfaces, native Compose sliders, switches, reset dialog, and accent-color swatches.
- Values still read/write the existing `AppearanceTheme` preferences. The native glass engine and saved content are unchanged.
- The preview is a simple visual approximation, not a background-sampling lens. The navbar/header use the real OG glass renderer.
- Settings is now rendered by `SettingsComposeScreen.kt` with the same Compose OLED cards, controls, accent, and section layout as Appearance.
- The existing Java `SettingsActivity` retains all notification, privacy, backup, history, content, source-config and app-update handlers. It now creates a Compose section/row model, and live status updates still flow through that model.
- Settings and Appearance both use `ComponentActivity` for safe Compose lifecycle integration. Existing callback and preference keys stay unchanged.
- Next visual work after phone validation: shared menus, Search contrast, and leftover fixed-cyan controls.

## Settings Compose validation checklist

- Open Settings, use Back, scroll between all seven groups and reopen it.
- Verify notification switches trigger their existing scheduling and permission behavior.
- Check check-frequency, creator-update and app-update actions.
- Test content preference, ShitTok preloading and blocked creators.
- Verify Appearance opens correctly and accent remains active on return.
- Verify Library navigation, backup export/import, history deletion confirmations, site-data confirmation, performance details, source-config status/refresh and test notification.
- For backup, history clearing, site data and source refresh: confirm only the dialog or flow opens; do not destructively delete data to test styling.

## Current limits

- Some older components still use hardcoded accent colors and will be covered by later **targeted** UI polish.
- Popup menus use theme-aware translucent paint, not the exact live refraction path of the navbar.
- Do not merge before a signed build and physical test on OnePlus Android 16. This draft is not a public release.

## Validation

Previous preview-3 build was successful at `b2ab28c` with focused AppearanceTheme and HeaderGlassMaterial tests and a signed release artifact. The OLED-only accent-preserving revision was followed by the new Compose Appearance phase. Focused compilation and device validation are pending.
