# Library social hub

The primary Library tab keeps its existing saved-media rails and adds two compact communication cards above them. Messages and Notifications have separate labels, previews, and nonzero unread badges, with dark smoked surfaces, electric blue accents, circular avatars, and bounded copy. No new dependencies or replacement social system are introduced.

## Messages

The card and recent-conversation preview open the existing DM inbox, which continues into the existing thread flow. Conversation and profile information comes from `ZeroChillSocialRepository.loadInbox`; avatars use the existing account avatar URLs and Glide cache. An active Library refreshes periodically and on resume. It stops refreshing when inactive. Account changes clear previews and stale callbacks cannot publish another account's data.

The current repository query reads the latest 500 message rows, so unread counts and recent-conversation coverage retain that existing limit. This pass does not introduce realtime subscriptions, push messaging, delivery receipts, or DM schema changes.

## Notifications

Notifications reuse `UpdateInboxStore` and `UpdateInboxActivity`, now titled Notifications. The existing persistent inbox keeps up to 200 entries, newest first, with individual read state and Read all. Tapping a creator entry opens its gallery with fresh-content markers. Tapping an app update opens the existing update check.

Supported events include favorite OnlyFap creator activity, ZeroChill app releases, replies to the signed-in user's comments, and likes on the signed-in user's comments. Social activity reuses the existing comments, comment likes, profiles, and account session APIs without a new backend table. The client reads activity from the last 30 days across the user's 200 most recent comments, dedupes repeated polling results, and keeps stored social rows scoped to the account that received them.

Creator and app updates remain available signed out. Social activity is only visible while its matching ZeroChill account is signed in. Notification history remains local to the installation and is not cloud-synced. Existing favorites determine which creator events remain in history. Android notification permission does not gate the in-app history.

The notification center includes a Social filter. Social rows use a compact activity-feed layout with a 48dp circular avatar, bold actor name, compact inline timestamp, reply/comment preview, small electric-blue unread dot, and direct View/Reply actions. Creator entries reuse existing content art as a right-side thumbnail when one is already available. The filter tabs are intentionally shorter and flatter than the original hub design.

Tapping a like or reply opens the existing ZeroChill comment sheet for the original video and briefly highlights the matching comment or reply. Reply opens the same thread with the composer targeted at that reply. No Android push notifications, realtime subscriptions, mention alerts, or duplicate DM notifications are added.

The center listens for local history changes while resumed, with short row/read-state transitions and press feedback. Its old activity name and More entry remain compatible.

## Comment ownership

Signed-in users can edit or delete only comments they own. Editing reuses the existing comment composer and records edited_at; the UI shows an Edited label. Delete is a soft delete: the body becomes `Comment deleted` and deleted_at is set. The tombstone remains in the thread so replies and existing notification links do not lose their parent position. Deleted comments no longer expose like/reply controls in the updated client, and new likes are rejected by backend policy.

## Compatibility

This pass adds one backward-compatible Supabase migration for owner-only comment updates and sanitized deleted-comment tombstones. It does not change DM schema or policies, remote source configuration, package identity, signing, data-store names, favorites, history, downloads, or media stores. Existing clients continue to read active comments normally. The beta uses the existing `.dev` application ID and persistent signing key, alongside stable installs. Device verification should cover comment edit/delete, nested replies after deletion, notification View/Reply actions, narrow screens, large font settings, avatars, account switching, notification read state, media rails, and collapsing navigation.
