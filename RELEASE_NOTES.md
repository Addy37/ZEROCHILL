# ZeroChill 4.3.0

ZeroChill 4.3.0 is a major account and social update built on the device-approved 4.2.1 baseline.

## ZEROCHILL ID and profiles

- Adds first-party ZEROCHILL account creation, sign-in, email confirmation, avatars, display names, and bios.
- Adds public profiles with native profile navigation and shared creator discovery.
- Adds account security controls for email and password changes, blocked users, and permanent account deletion.
- Keeps creator favorites separated by signed-in account while preserving the signed-out collection.
- Adds account-synced notification preferences for supported in-app activity.
- Adds an optional Create Account / Sign In entry to the final startup-wizard page without blocking app use.

## Social

- Adds ZEROCHILL-owned comments, replies, comment likes, and video likes across supported content.
- Adds one-to-one direct messages with unread state, grouped chat bubbles, send state, and read receipts.
- Adds Messages and Notifications to Library as the social hub.
- Adds reply and comment-like activity notifications with direct return to the originating video and conversation.
- Adds native social actions and thumbnails to notification rows and keeps activity scoped to the signed-in account.
- Polishes comment, notification, public-profile, and shared-creator presentation while preserving existing navigation and playback behavior.

## Video details and playback

- Restores the native ZEROCHILL video-detail experience with title, likes, comments, Watch Later, Share, seeking, and related videos.
- Simplifies player controls to reduce duplicate chrome and keep portrait video unobstructed.
- Keeps portrait fullscreen in portrait and preserves existing landscape fullscreen behavior.
- Aligns detail-page Like, Comments, Watch Later, and Share controls with ShitTok iconography.

## Account email security

- Account emails now use ZEROCHILL-branded transactional delivery from `noreply@zerochill.online`.
- Signup confirmation, password reset, email change, reauthentication, invitation, and magic-link templates use the ZEROCHILL visual system.
- Password-changed and email-changed security notifications are enabled and branded.
- Email confirmation continues to return directly to the native ZEROCHILL account flow.

## Compatibility

The update keeps the same application ID and production signing identity. Existing favorites, creator merges, custom avatars, history, downloads, backups, playback state, and saved settings remain compatible.

The Profile + Account 2.0 Supabase migrations and account-delete Edge Function are already deployed. This release does not change content-source routes or remote source configuration.

Supports an in-place upgrade from ZeroChill 4.2.1.
