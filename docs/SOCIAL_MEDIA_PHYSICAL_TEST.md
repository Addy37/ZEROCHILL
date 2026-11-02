# 5.7.0-beta.1 physical acceptance — pending

Device: Addy's OnePlus CPH2655 / Android 16.
Branch: `feature/social-media-reactions-analytics-v2`, descendant of approved
`4454d965ea3285800b98592d2dcca15c071fa304`. Use the exact SHA and signed, branch/SHA-named
Beta artifact recorded in the PR. No stable publication or PR merge.

The social production migration is already deployed and `social-media-upload` v1
is ACTIVE with JWT verification enabled; social media testing is possible now.
Analytics v2 remains undeployed. CI Beta
analytics is disabled; do not interpret an absence of v2 traffic as a failure.
Use two disposable signed-in accounts and a third unrelated account for access checks.

- [ ] Photo-only and text + photo comments, JPEG/PNG/WebP selections.
- [ ] Animated GIF-only and text + GIF comments; animation continues after scrolling.
- [ ] Reply with photo; reply with GIF; existing thread order, likes and counts.
- [ ] Preview then remove before posting, including canceling Photo Picker.
- [ ] Tap comment media to enlarge; profile links and timestamps remain usable.
- [ ] Reject a static source over 20 MB and GIF over 10 MB with clear text.
- [ ] Send photo and GIF DMs; receive both on the other account, including attachment-only.
- [ ] Open larger DM media; GIF keeps animating through normal thread refreshes.
- [ ] Airplane mode/upload failure preserves the draft; retry creates one message.
- [ ] Long press text/photo/GIF DM; add, change and remove the same reaction.
- [ ] Both users react with the same emoji; grouped count is two and own reaction is marked.
- [ ] Reopen thread and verify media/reaction persistence and Seen/unread behavior.
- [ ] Clear conversation; later messages still arrive. Block/report behavior remains intact.
- [ ] Unrelated account cannot download a known DM object through authenticated Storage.
- [ ] Older client displays Photo/GIF fallback without crashing; legacy text remains normal.
- [ ] Update notification preferences and updater handoff remain unchanged.
- [ ] Normal ShitTok playback, pause/resume, tab switches and fullscreen still work.
- [ ] Analytics is invisible to the user and never delays interaction or playback.
- [ ] Rotate/recreate the screen while choosing media; canceled drafts do not post silently.

An automated container, SQL fixture or emulator check does not count as physical acceptance.

## Follow-up acceptance: comment gestures and compact UI

- [ ] Repeated fast swipes down and back up through a long thread; start on text,
      media, whitespace, and the list edge above the pinned composer.
- [ ] List consumes scrolling while it can scroll; at the top, pull downward to
      drag/dismiss the sheet. The drag handle/header still move the sheet.
- [ ] Open/close keyboard repeatedly, including after scrolling; composer remains
      pinned and only the comments list scrolls.
- [ ] Centered count/compact header and TikTok-like density, with ZEROCHILL colors.
- [ ] Landscape/portrait images retain aspect ratio at about 150dp maximum height;
      GIF animates, tap opens viewer, reply/photo/GIF/like/edit/delete still work.
- [ ] Avatar, compact text field, integrated media action and active/inactive send;
      preview/remove, reply/edit context, sign-in and upload retry preserve drafts.

## Follow-up acceptance: WhatsApp-style DMs

- [ ] Compact identity header and overflow retain profile, block/unblock and report.
- [ ] Natural-width compact bubbles group consecutive messages closely; sender
      changes and gaps separate them; dates and long gaps get centered separators.
- [ ] Long press text/photo/GIF: highlight stays associated with floating six-emoji
      strip. Strip stays on screen near top/bottom edges and with keyboard open.
- [ ] Context header offers Copy only for actual text, Report only for incoming
      messages; no unsupported reply/edit/forward/delete actions.
- [ ] Outside tap, Back, scrolling, backgrounding and reopening exit selection.
- [ ] Add/change/remove reaction and grouped own/count presentation persist after
      refresh/reopen. Blocked users cannot react/send.
- [ ] Photo/GIF aspect ratio, animation, viewer, compact composer, preview/removal,
      send readiness, failed-send draft/retry, keyboard and newest-message scrolling.
- [ ] Seen/read/unread and conversation clearing still work with grouped media.

## Follow-up acceptance: first-party automatic crashes

The controlled trigger exists only in debug/Beta source sets. Launch explicitly:

```sh
adb shell am start -n com.addy37.crazyshitunofficial.dev/com.webapp.crazyshit.CrashProbeActivity
```

Tap **Trigger controlled ZEROCHILL Beta crash**, then reopen ZEROCHILL normally.
Do not expect any success/failure toast. Submission starts in the background after
10 seconds on a successful launch; an offline failure is retried on a later launch.

- [ ] App terminates normally and next launch starts normally.
- [ ] Feedback Admin shows Bug report / Crash with clear summary and diagnostics
      underneath in at most three diagnostic replies (each below 2,000 characters).
- [ ] App/version code/source revision/Android/device/time/thread/screen are correct;
      useful stack frames, recent events and performance summary are present.
- [ ] No access tokens, keys, cookies, emails, full content URLs, private DM bodies,
      or comment drafts appear. Free-form exception messages are omitted for privacy.
- [ ] Repeating the same crash increments its local count and reuses the thread;
      delivered repeat updates have a one-hour cooldown. New automatic threads are
      capped at one per 24 hours, preserving ordinary Feedback capacity.
- [ ] Offline crash survives reopen/failure; later connected launch submits it.
- [ ] Normal manual Feedback and replies still work. Email alerts are not implemented.

Debug/Beta and stable use separate private app storage. To validate captured version
metadata across an upgrade, upgrade the same package without clearing app data.
