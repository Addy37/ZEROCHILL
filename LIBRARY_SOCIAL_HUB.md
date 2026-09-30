# Library social hub

The primary Library tab keeps its existing saved-media rails and adds two compact communication cards above them. Messages and Notifications have separate labels, previews, and nonzero unread badges, with dark smoked surfaces, electric blue accents, circular avatars, and bounded copy. No new dependencies or replacement social system are introduced.

## Messages

The card and recent-conversation preview open the existing DM inbox, which continues into the existing thread flow. Conversation and profile information comes from `ZeroChillSocialRepository.loadInbox`; avatars use the existing account avatar URLs and Glide cache. An active Library refreshes periodically and on resume. It stops refreshing when inactive. Account changes clear previews and stale callbacks cannot publish another account's data.

The current repository query reads the latest 500 message rows, so unread counts and recent-conversation coverage retain that existing limit. This pass does not introduce realtime subscriptions, push messaging, delivery receipts, or DM schema changes.

## Notifications

Notifications reuse `UpdateInboxStore` and `UpdateInboxActivity`, now titled Notifications. The existing persistent inbox keeps up to 200 entries, newest first, with individual read state and Read all. Tapping a creator entry opens its gallery with fresh-content markers. Tapping an app update opens the existing update check.

Supported events remain favorite OnlyFap creator activity from the existing content checker and app release updates. No fake events, comment/reply/like alerts, or duplicate DM notifications are added. Creator and app updates are local to the installation, available signed out, and are not cloud-synced. Existing favorites determine which creator events remain in history. Android notification permission does not gate the in-app history.

The center listens for local history changes while resumed, with short row/read-state transitions and press feedback. Its old activity name and More entry remain compatible.

## Compatibility

No database migrations, backend deployments, policy changes, remote source configuration, package changes, signing changes, data-store renames, or media-store changes. The beta uses the existing `.dev` application ID and persistent signing key, alongside stable installs. Device verification should cover narrow screens, large font settings, avatars, account switching, new messages, notification read state, media rails, and collapsing navigation.
