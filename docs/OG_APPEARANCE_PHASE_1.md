# ZEROCHILL 7.0 Appearance, first stage

Source: current OG main, not historical rebrand/zerochill.

## Included in the first draft

- Compact Appearance activity from Settings, organized as Colors, Glass and Motion.
- Four theme modes, seven accent choices, and immediate persistence in existing app_prefs.
- OLED black and electric cyan defaults, with legacy oled_black_enabled fallback and compatibility.
- Shared semantic colors used by ZeroChillUi, Settings, dialog text, and the bottom Compose navigation.
- Navigation glass-intensity control using the existing OG glass engine, not V2's dependency.
- Glass follow-up: at maximum intensity, strengthen lens and rim instead of darkening the film; allow shared headers and native preference panels to follow intensity.
- V2-style optics added: Off/Fast/Balanced/Quality, transparency, blur, refraction, vibrancy, edge highlights, and Android 13+ color splitting (using OG's native shader parameters).
- Native Light/Dark Material themes and accent overlays are applied before supported activities inflate views. Main/Settings rebuild once when returning from theme changes; main preserves the selected page using its existing save/restore.
- Account, public profile, inbox, message and light dialogs now use dynamic semantic colors. Other older visual surfaces still need explicit conversion; no promise of full light-theme parity yet.
- Main library, Shows, OnlyFap section chrome, native cards/menus and social settings now use theme-aware colors. Media artwork overlays remain white/dark as needed.
- Appearance preview now reacts to glass intensity and tint changes without pretending to show real backdrop refraction.
- Theme-aware native popup tint as a readable fallback. No background capture in PopupWindow.
- Existing immersive_motion_enabled remains authoritative, including Android animation preference.
- The existing validated backup format now retains the two appearance strings and two glass floats.
- Focused migration and persistence unit tests.

## Known limits, must not ship as a completed global theme

Many OG native surfaces use hardcoded UiPalette constants, drawable resources or inline black/white.
The screen is an operational foundation but Light/Follow System is not yet uniformly readable
throughout every tab, photo/video viewer, dialog and account surface. Fullscreen media canvases
must remain black independently of the selected app chrome. A later, scoped pass must connect
remaining theme consumers before calling the system app-wide or shipping as a completed 7.0 theme.

Native PopupWindow menus cannot sample the Activity backdrop as the existing Compose navbar
can. The initial menu tint control affects translucent menu paint only; V2-like real refraction
for menus requires a tested in-window overlay migration that preserves anchors and actions.

This draft does not change app identifiers, signing, provider traffic, Supabase, content,
downloads, user database, or existing backups. Do not merge before tests and phone validation.
