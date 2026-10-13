# ZeroChill 4.3.7

- Rebuilt ShitTok playback around AndroidX Media3 1.11.1 PlayerPool with a bounded three-player architecture for much smoother vertical swiping and faster player handoff.
- Fixed stray and overlapping ShitTok audio by centralizing playback ownership and allowing audio only for the selected, settled, visible player.
- Deferred refill, adapter, social-counter, and related UI work until scrolling is idle to reduce swipe hitching.
- Removed automatic creator-gallery warming from the ShitTok queue. Creator galleries now warm only after explicit creator interaction.
- Improved Favorite Creator update handling with grouped creator cards, exact unread items, individual read state, and merged-creator support.
- Preserved the existing application ID, production signing identity, user data, favorites, history, downloads, source routing, remote config, Supabase schema, and account security boundaries.
