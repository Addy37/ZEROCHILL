# ZEROCHILL 7.0.5

## What's new

- Restored native Bunkr album search through the independent Bunkr Index service, with the existing Balbums and browser fallbacks retained. Search queries are sent directly to that independent service.
- Added the animated OLED **Support ZEROCHILL** dialog with GitHub Sponsors and Cash App links.
- Made **What's New** and **Update Available** scroll when needed so long notes and large text remain readable.
- Fixed a Downloads action-menu crash when a scrolling list detaches the menu anchor, and reject menus during activity teardown.
- Kept Bunkr response-read failures in the existing fallback path.

## Known source limitations

Balbums may require browser verification. OnlyHaven's creator/category APIs can be intermittently unavailable; existing fallbacks remain available but cannot guarantee results during upstream errors. EFukt availability varies by region. These sources remain enabled.

## Updating

Install **ZeroChill.apk** over your existing ZEROCHILL app. **Do not uninstall or clear app data** before updating. Your on-device favorites, history, downloads, backups, and settings are retained during a normal in-place update.

The application ID, signing identity, New Zerochill backend and account behavior are unchanged. Older accounts from the retired social backend still require a new ZEROCHILL account; browsing does not require signing in. This candidate does not claim to resolve the unconfirmed Android/OEM main-screen memory and layout crash reports.
