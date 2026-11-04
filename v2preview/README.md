# ZEROCHILL V2 visual skeleton

This is a separate, installable Kotlin + Jetpack Compose Android preview.
It does not replace the existing ZEROCHILL application and it is not a production upgrade.

- Four main tabs in the current approved order: Shows, ShitTok, OnlyFap, Library.
- More and Settings placeholders inside Library.
- Original ZEROCHILL mascot, electric cyan, OLED black, wordmark and tagline.
- Static sample cards and video/feed preview layouts. No real media.
- No INTERNET permission, providers, Supabase, analytics, authentication, messaging, downloads or background activity.
- Simple translucent glass-style surfaces only. Real glass/refraction effects and theme customization are later design work.
- Library placeholder detail pages and tab navigation work locally.

App package: `com.addy37.zerochill.v2preview`, intentionally different from production.
Production package: `com.addy37.crazyshitunofficial`, unchanged in `:app`.

Build: `./gradlew :v2preview:assembleDebug`.
APK: `v2preview/build/outputs/apk/debug/v2preview-debug.apk`.

Do not merge, release, change production package IDs, or claim upgrade compatibility for this preview.
The future V2 release needs a separate migration/upgrade review and physical testing.
