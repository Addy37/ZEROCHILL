# Smart ShitTok v1

Baseline: current approved `main` after PR #360.

## Product behavior

ShitTok keeps its existing freshness, anti-repeat, portrait/square admission, playback continuity, source availability and manual content-preference rules. Smart ranking only reorders candidates that already passed those gates.

The v1 score combines two signals:

- **Community popularity:** confirmed ZEROCHILL video likes boost matching candidate hashes. Recent momentum is worth more than lifetime likes and logarithmic scaling prevents an old hit from dominating forever.
- **Fast skips:** when a user leaves a settled ShitTok clip within 2.5 seconds, ZEROCHILL records a small source penalty and a stronger creator penalty when creator identity is known. These signals stay on-device and decay with a seven-day half-life.

Diversity remains a hard guardrail. Candidates stay grouped by source and ShitTok takes one item from every available source before any source gets another turn. Within each source, high-scoring items normally lead, while roughly one in four positions is deliberately exploratory. Source order itself is also exploratory one round in four.

## Community data

`public.shittok_video_popularity` is a read-only `security_invoker` view over the existing authoritative `video_likes` table. It exposes only:

- `content_key`
- total `likes`
- `recent_24h`
- `recent_7d`

No user IDs are exposed by the view. The Android app refreshes at most 512 top aggregate rows in the background, caches them in memory for 15 minutes, and never blocks first feed playback on the request. A failed or unconfigured request simply leaves community boost at zero.

This first version can boost a popular video when that video is already discovered by the existing source mixers. The likes table intentionally stores hashes rather than canonical video URLs, so v1 does not inject arbitrary globally popular videos that were not otherwise fetched as candidates.

## Compatibility

- No application ID, signing or Android minimum-version change.
- No change to existing likes, favorites, history, downloads, backups or notification data.
- Fast-skip preferences use a new optional device-local preference file: `shittok_smart_ranker_v1`.
- No service-role or admin credential is placed in the Android app.
- No content-source domain, selector, header, retry, timeout or remote-config change.
- Existing Focus Mode, player ownership, source fetch budgets and playback continuity are unchanged.

## Backend

Migration: `20261028175238_shittok_community_popularity.sql`.

It adds a supporting `video_likes(created_at desc, content_key)` index and the read-only aggregate view. The migration is additive and compatible with installed versions that know nothing about the view.
