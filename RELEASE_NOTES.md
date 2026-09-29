# ZeroChill 4.2.0

ZeroChill 4.2.0 improves first-run onboarding, OnlyFap discovery and gallery motion, ShitTok responsiveness, feedback conversations, and Shows browsing.

## Startup experience

- New users now get a full-screen ZEROCHILL startup wizard.
- The wizard introduces ShitTok, Shows, OnlyFap, and Library.
- The existing 18+ access confirmation is integrated into onboarding.
- New users can choose Favorite Creator alerts, app update alerts, and haptic feedback before entering the app.
- Android notification permission is requested only after onboarding when alerts are enabled.
- Existing installs skip the wizard automatically.
- Settings includes Startup tour so onboarding can be replayed without clearing app data.

## OnlyFap

- Replaced Trending with Discover.
- Discover shows 16 randomized creators from deeper Fapello listings and avoids creators already visible in New, Hot, and Popular.
- Tapping a creator-gallery video now starts the selected video automatically in fullscreen.
- Added a holographic grid morph when changing creator-gallery density with pinch gestures.
- Gallery thumbnails now resize and reposition smoothly instead of snapping between layouts.
- Added subtle electric-blue feedback when a new grid density locks in.
- Source, play, and new-content indicators scale down cleanly in denser grids.
- Fixed saved custom Favorite Creator avatar framing on compact Library and OnlyFap shelves.

## ShitTok

- Fixed the remaining occasional swipe-start hitch seen on long-running installs.
- Playback-history and recent-video persistence now run off the active swipe path.
- Simplified touch handling for faster initial swipe response.
- Removed hold-for-2x playback and pinch-to-hide from the ShitTok touch path.
- Normal playback stays visually clean without a persistent progress bar.
- Tapping to pause shows the progress bar and centered play indicator.
- Existing history, Continue Watching, source mix, and anti-repeat behavior remain compatible.

## Feedback

- Feedback now supports persistent two-way conversation threads.
- Users can reply repeatedly inside an existing feedback conversation.
- My Feedback shows unread developer replies and latest-message previews.
- Messages support read receipts.
- Existing feedback from older app versions remains compatible.

## Shows

- This Week, CrazyShit Shows, EFukt Series, CrazyShit Categories, and Kaotic Categories now use more compact portrait cards.
- More titles fit on screen with less horizontal scrolling.
- Continue Watching keeps its existing landscape layout.

## Performance and stability

- Reduced work performed on the UI thread during ShitTok paging.
- Playback history is cached in memory and serialized in the background.
- Recent ShitTok anti-repeat state is also persisted in the background.
- Added regression coverage for startup onboarding, gallery motion, playback history, gestures, and creator discovery.

## Compatibility

Existing installs keep the same application ID and signing identity. Favorites, creator merges, custom avatars, Library data, watch history, Watch Later, downloads, backups, feedback history, and persisted settings remain intact.

This release supports an in-place upgrade from ZeroChill 4.1.1.
