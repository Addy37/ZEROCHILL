# DM / shared social attachment lifecycle fix

Scope: SocialMedia rendering, viewer and DM row identity only. No Pixel gallery,
creator/gallery, UI redesign, backend, Storage policy or schema changes. The
existing 6.0.1 crash-hotfix branch/PR remains separate and untouched.

## Observed and reproduced mechanisms

The supplied physical recording shows an attachment viewer reduced to its Close
button area. The released viewer installs an unloaded ImageView directly as
AlertDialog custom content. AlertDialog replaces that root's LayoutParams with
match-parent inside wrap-content panels, discarding the requested 72%-screen
height. With no drawable, the content measures to zero. Glide waits for a valid
target size, so the drawable that could give it height never arrives. A negative
control using the released bind/viewer methods reproduces zero height before a
controlled remote response. This is a layout/request handoff defect, not a
manufacturer-specific rendering problem.

DM polling, reaction, Seen and adjacent-group updates call notifyDataSetChanged.
The adapter previously had no stable row IDs, so full invalidation could discard
holders even for the same message. The old same-string-tag shortcut only helped
the *same view*: a replacement/recycled holder cleared its image and started
another token preparation/download. Both Glide caches were disabled, so returning
to an attachment always needed another fetch. Slow requests expose the empty
interval as flicker/reloading. The actual RecyclerView regression checks holder,
drawable and request retention through a full refresh and repeated scrolling.
The released adapter/loading comparison exceeds three source fetches while
scrolling the same three images; the fixed path stays within those three fetches,
including conversation switching/return. The negative control uses the original
generic tag format so it measures reload behavior, not the new tag representation.

The old string tag also accepted stale A work after A -> B -> A rebinding and
never retried a failed token preparation for an unchanged tag. There was no
recycler cancellation or viewer-dismiss cleanup for pending header work.

## Implementation

- MessageAdapter uses unique adapter-local stable IDs for message UUIDs. The ID
  table lasts for the conversation activity; it holds strings/longs, not media.
  Existing polling, grouping, scrolling and item animations are unchanged.
- SocialMedia uses a dedicated keyed binding tag, with a new identity per real
  bind. Identical preparing/running/completed requests are retained. Failed or
  cleared requests may retry. Recycling clears the Glide request, cancels token
  preparation and removes stale attachment click listeners.
- Posted header work and Glide delivery prove both binding and session identity.
  Header results use the main Handler rather than a detached view's post queue;
  starting a request no longer depends on the view's first/repeated attachment.
  Rejecting a delivery never clears/restarts Glide reentrantly inside a listener.
  Activity destruction and viewer dismissal cancel outstanding preparation and
  clear owned targets. Weak view tracking does not retain activities.
- Viewer loads after dialog.show, inside a bounded child viewport retaining the
  original intended 72%-screen height and FIT_CENTER presentation. Inline and
  viewer decodes use bounded target dimensions instead of waiting on drawable
  dependent layout. No borrowed drawable or bitmap copy is used.
- Remote loading remains as Drawable, including GifDrawable. No asBitmap or
  dontAnimate option is used; Glide owns GIF start/stop on targets and lifecycle.
  Existing inline aspect-ratio measurement, corners and interaction remain.

## Private media and caching

DiskCacheStrategy.NONE remains explicit. Normal bounded Glide memory caching is
enabled only with a per-process random session signature. The partition identity
includes the user and Supabase session_id; ordinary access-token refresh keeps
the same partition, while a new login or logout retires it. Legacy sessions
without session_id conservatively partition by token. Logout is observed even
if the same account logs back in before the posted preference apply executes.

Session change clears tracked displayed images and pending work. Retired cache
keys cannot satisfy new-session requests, even with the same URL/token; their
resources leave memory through Glide's bounded LRU. No global memory-cache clear
or cache policy change affects other image systems. No private pixels are saved
to disk by this path. The synthetic test also checks the Glide disk cache stays
empty.

Cache partitioning is not authorization. Requests still use the authenticated
Storage object endpoint, apikey and the current user's bearer JWT. The existing
anonymous fallback remains subject to server RLS. No public URL, signed URL,
service key or policy bypass is introduced. A new user's Storage denial cannot
be satisfied from the previous user's decoded image cache. Header preparation
failure now shows the existing error icon and can retry instead of silently
leaving an unchanged-tag view blank forever.

## Regression evidence

SocialMediaLifecycleTest exercises actual Glide decoding and targets, actual
dialogs, and actual MessageAdapter/RecyclerView handoffs with synthetic media
and delayed authentication/fetch callbacks. Cases include JPEG, PNG, WebP,
animated GIF, repeated same binding, full DM refresh, rapid scrolling, recycle,
conversation return, cached/uncached viewers, repeated open/close and different
attachments, pending dismissal/destruction, stale A/B/A preparation, token
refresh, user change, same-user logout/login and denied Storage access/retry.
Comment image/GIF rendering uses the same path and is checked without redesign.

Default native-graphics matrix uses Android 13/14/15 on repository JDK17.
Android16 requires JDK21; the identical fixture is also run with a temporary
SDK36 annotation and test-launcher override, leaving repository toolchains intact.
Existing SocialMediaTest, MessageThread UI, keyboard, comments and social tests
remain relevant. Final actual counts, failures, builds and Android16 results
are recorded in the PR/handoff; no physical-device validation is claimed.
