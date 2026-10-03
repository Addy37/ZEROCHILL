# Profile and social cleanup

Base: current `rebrand/zerochill` at `5063cbea`, descendant of the approved v4.3.2 lineage. No package, signing, media/favorite/history/download/backup, remote source config, or content-source changes.

## Avatars

Account image selection opens the existing CreatorAvatarCropActivity circular drag/pinch/reset editor in local-URI mode. Glide applies orientation and bounds the decoded image to 2048px. Crop state survives recreation. Cancel never uploads. Save renders the exact square beneath the circle to a 512px JPEG at quality 84, below the existing 512KiB upload limit.

New objects use `<owner>/avatar-<UUID>.jpg`. The existing public `avatar_path` column carries this new identity through account, public profile, comment/reply view, inbox/thread/header, Library, blocked-user list, social banner, and notification consumers. Already displayed account avatars rebind after a successful local update. Notifications persist actor IDs, refresh avatars during repeated event polls, and reload known actors' public profiles when reopened. Older notification records recover actor IDs from known account-avatar URLs. Very old events with no stored actor identity and no avatar URL can only gain an identity if the event is still returned by polling.

Upload stages an immutable new object, then compare-and-sets the previous profile path. Failure keeps the prior image. An ambiguous PATCH response is reconciled against the server before any deletion. Durable account-specific cleanup debt is recorded before upload and retried before another upload, preventing repeated deletion failures from accumulating staged files. Failed cleanup after a successful profile swap does not report a failed avatar change. The one legacy `avatar.jpg` object is retained for old clients that can still reuse that filename; new obsolete immutable files are removed through Storage API and existing owner RLS. Old immutable orphan objects older than 24 hours (measured against the HTTPS Storage response Date, never the phone clock) are also recovered from the owner folder before uploading, even if local app data was removed. Recent staging files remain protected. No Storage policy or profile schema change.

## Messages

Long-press a conversation and confirm Remove. Only the current account's inbox/thread view is cleared, including unread counts and Library previews. No shared message row or other participant's read/history state is removed. Any later message in either direction restores the conversation, showing only messages strictly after that user's clear timestamp.

Migration `20261003171153_conversation_cleanup.sql` adds `conversation_clear_state` with owner-only SELECT RLS. Clients cannot directly write the table. The private authenticated clear RPC derives the owner and server timestamp, validates a live session and existing conversation, and has a public invoker wrapper. The visible-message RPC is a security invoker and applies cleanup before the existing 500-row query limit. Existing direct_messages RLS/grants remain unchanged. Older apps can still read their old view of all messages; they do not honor the new clear preference.

Rollback: disable new clients' cleanup calls, then drop the three new functions and cleanup table if needed. Dropping the table loses only cleanup preferences. Message rows remain intact. Do not restore older broader grants.

## Notifications

Swipe left to remove an individual notification, or long-press for a confirmed Delete action. Clear All confirms and clears local history for the current account plus installation creator/app entries. Other accounts' social entries remain. Read all remains separate. Persistent bounded deletion fingerprints stop polling/backfill from restoring cleared entries; new event identities remain eligible. Monitoring, Android permissions, comments/likes, and update checks do not change.

## Validation

New focused tests cover crop output/recreation/cancel, avatar replacement/failure/CAS/lost response/cleanup debt, conversation confirmations and account isolation, Library refresh, notification delete/Clear All/cancel/read/count/scoping/replay and actor avatar updates. Android CI runs focused regressions before full debug/release suites and builds, plus lint audit. SQL rollback-only fixture tests strict cutoff, new incoming/outgoing messages, other-party history/privacy, live-session checks, grants, and denial of shared message deletes.

Device testing remains required for the real photo picker, pinch/drag/save, repeated avatar uploads and two-account views, conversation send/receive/clear, notification gestures, and install compatibility. No merge until device approval.

Abandoned staging recovery skips deletion when Storage provides no server Date. An upload suspended for 23 hours expires before profile replacement, leaving margin before the 24-hour recovery window.
