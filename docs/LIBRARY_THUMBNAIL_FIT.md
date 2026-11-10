# PR 412 / Preview 22: proportionate thumbnail fitting

The user physically approved Preview 21's Library header correction at
`480e3d4104194360af3d611e80970d8ebd90bb59`, then requested thumbnail fitting and
an intelligent Library visual check. Continue that branch and retain the header
and inset correction, original gallery arrangement, controls and palette.

## Findings and change

Library home already uses CENTER_CROP. FavoritesActivity (History, Favorites,
Watch Later and Continue), Downloads and Liked Videos used FIT_CENTER inside
fixed 16:9 / 9:16 tiles. Non-matching source proportions therefore produced black
bands despite the media slot filling its card. Destination photo ImageViews now
use CENTER_CROP: fill the tile with equal scale on both axes, cropping excess at
the center. This can crop image edges; full media remains available on opening.
The portrait/landscape detection still receives the untransformed decoded image.
Glide requests, decode bounds, fades and caching are unchanged.

Downloads no longer nests a second, smaller-radius MaterialCardView inside its
outer media card. The outer card owns corner shape and outline. Missing-artwork
icons stay CENTER_INSIDE, including recycled Liked Videos rows with no image.

Review scope: preserve existing cyan/accent surfaces, readable title gradients,
metadata hierarchy, 48dp actions and mixed gallery order/ratios. Library home
already fills its thumbnails and creator avatars retain their custom framing.
Avoid adding decorative effects or changing the design the user already likes.
No header/window/inset edits, data changes, playback/menu/Back changes, preference
changes, performance-policy changes, Supabase or remote-config changes.

## Validation and candidate

LibraryThumbnailFitTest uses actual destination thumbnail factories and verifies
matrix geometry with square, 4:3, 2:3 and ultrawide artwork in narrow/wide portrait
and landscape slots: uniform scale, no exposed image-slot edges. It checks the
missing Downloads artwork icon and saves native synthetic gallery renders at
320px and 600px for visual inspection. These fixtures test fitting and rounding,
not source photography or physical OxygenOS rendering.

Run the focused Library suite, including the API 35/36 delayed-fitment regression,
menu, mixed-grid and appearance tests. Produce one signed release APK in the
existing scoped Actions workflow: Preview 22 / code 6000024. Preserve production
application ID and signing identity, compare with stable and require an upgrade
version above Preview 21. No merge, tag or publication; physical acceptance of the
thumbnail crop remains pending. See PR 412's latest candidate record for results.
