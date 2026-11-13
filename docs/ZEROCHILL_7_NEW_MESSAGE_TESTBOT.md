# ZEROCHILL 7.0: New Message entry and TestBot

## Android behavior

- Library > Messages now provides **+ New**. Existing conversations, selection, and bulk removal are unchanged.
- Enter an account username (optional leading `@`) and explicitly tap **Search** or the keyboard Search action.
- Search is bounded to a 2-20 character case-normalized username prefix and up to 20 results. There is no query-on-keystroke, background user scanning, or automatic polling.
- Search uses existing indexed `profiles.username_key` and its existing public read policy. The current account is excluded. Account switching invalidates pending results.
- Selecting a result opens the existing `ZeroChillMessageActivity`. The recipient's current blocking, send, attachment, read, and notification rules still apply. Opening a draft alone does not insert a DM.
- No new backend SQL, keys, migrations, source changes, permissions, or dependencies.

## Administrator TestBot

- New Zerochill Supabase now has a normal `@TESTBOT` profile created by the owner. Its password is not required for administrator-authored test DMs.
- One administrator-authored test DM from `@TESTBOT` to `@ZEROCHILL` was committed on November 12 (US Central time). The normal `direct_messages` INSERT trigger invokes the existing push dispatch webhook. Receiving in the Android Beta and actual push delivery have **not** been validated yet.
- Legacy ZEROCHILL/TestBot messages have not been migrated.
- No broadcast has been sent in the new project. A future broadcast is a separate, explicitly approved operation with final message, recipients, dry-run count, bot exclusion, duplicate checks, and verification of deliveries. Do not schedule repeated broadcasts or put privileged credentials in the APK.

## Validation and acceptance

Verified: account profiles, `direct_messages` row/trigger, public profile-column privileges, and username-key index in the **new** Supabase project. The one test DM was confirmed in the database as sent from TestBot to the user's test account.

Pending: Android compilation, device tests, keyboard/layout behavior, empty/no-result searches, blocked users, two accounts with matching prefix, no self-result, account-switch race, outgoing DM, unread counts, and actual push delivery.

This work is staged on the ZEROCHILL 7.0 traffic candidate branch lineage and must **not** be merged or released before signed APK and device acceptance.
