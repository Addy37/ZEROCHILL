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
- The next visual phase converts shared anchored action menus to Compose using the existing native MenuItem callbacks, with preference-glass optics underneath and an opaque-enough film for readability.

## Settings Compose validation checklist

- Open Settings, use Back, scroll between all seven groups and reopen it.
- Verify notification switches trigger their existing scheduling and permission behavior.
- Check check-frequency, creator-update and app-update actions.
- Test content preference, ShitTok preloading and blocked creators.
- Verify Appearance opens correctly and accent remains active on return.
- Verify Library navigation, backup export/import, history deletion confirmations, site-data confirmation, performance details, source-config status/refresh and test notification.
- For backup, history clearing, site data and source refresh: confirm only the dialog or flow opens; do not destructively delete data to test styling.

## Compose glass menus, phase 3

- ZeroChillMenu now prefers a Compose UI inside the existing anchored PopupWindow. The original Java menu dispatcher still owns item IDs, visibility, disabled items and action callbacks, with a native fallback outside Activity contexts.
- The Compose menu is placed inside the existing PreferenceGlassPanel. On capable Android devices it samples the underlying app and renders the OG blur/refraction optics, using screen-aligned recording.
- The dark visual wash is stronger, so text remains legible above faces, previews and moving artwork. Existing menu tint controls still apply. On unsupported devices, a dark non-refracting fallback remains readable.
- The color-splitting preference adds a subtle multi-colored reflective rim to Compose menus, not a new content shader.
- Menu opening/closing uses short native window animations and respects the existing `immersive_motion_enabled` preference.
- Content-preference choices retain the existing native picker, but its selected row and confirmation accents now follow the chosen accent color. Its preference glass inherits the stronger readable wash.
- Targeted regression tests cover menu filtering, item identity, and the original action callback contract.

## Menu acceptance checklist

- Open a creator overflow menu in Library, a video menu in Shows/OnlyFap galleries, and an options menu in Messages.
- Confirm each action still works, disabled choices remain disabled, and touch-outside/back dismisses the menu.
- Check menus against bright and dark thumbnails, on your selected accent and default cyan.
- Open the content-preference picker over a busy ShitTok hero and verify its text is readable and every option is reachable.
- Return from a menu without changing the selected tab, playback position, or data.

## Current limits

- Some older components still use hardcoded accent colors and will be covered by later **targeted** UI polish.
- The shared Compose menu now requests the OG page-sampled lens; some other screen-specific menus remain native and need a later targeted visual pass.
- Do not merge before a signed build and physical test on OnePlus Android 16. This draft is not a public release.

## Validation

Previous preview-3 build was successful at `b2ab28c` with focused AppearanceTheme and HeaderGlassMaterial tests and a signed release artifact. The OLED-only accent-preserving revision was followed by the new Compose Appearance phase. Focused compilation and device validation are pending.
