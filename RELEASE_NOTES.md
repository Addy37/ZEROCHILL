# ZEROCHILL 7.0.5

Bunkr search is back, Downloads is more stable, and there's a new way to support ZEROCHILL. Plus, longer update notes are easier to read.

## What's new

- **Bunkr search is back!** Finding albums should be easier again when the usual search isn't working.
- **Support ZEROCHILL** from the More tab with a choice of GitHub Sponsors or Cash App.
- **Read every update.** What's New and update messages scroll, even with larger text.
- **Fewer interruptions.** Fixed a crash when opening Downloads options and improved Bunkr's handling of incomplete responses.

## Technical notes and source availability

Bunkr album search uses the independent Bunkr Index service. Search queries go directly to that service, and existing fallbacks remain available. The Index service has separate privacy, rate-limit, and availability policies that ZEROCHILL does not control.

Balbums may require browser verification. OnlyHaven's creator and category services may be intermittently unavailable. EFukt availability varies by region. These sources remain enabled, and an upstream service failing does not mean ZEROCHILL has removed it.

The Downloads action-menu fix covers a disappearing or detached row during menu placement and protects shutdown handling. The release also includes checked fallback for interrupted Bunkr responses.

## Updating

Install **ZeroChill.apk** over your existing ZEROCHILL app. **Do not uninstall or clear app data** before updating. Your on-device favorites, history, downloads, backups, and settings remain available during a normal in-place update.

The application ID, signing identity, New Zerochill backend, and account behavior are unchanged. Older accounts from the retired social system still require a new ZEROCHILL account for social features, but you can browse without signing in.

The unconfirmed Samsung memory and vivo layout reports were not reproduced or fixed in this update. They will be revisited if additional reports come in.
