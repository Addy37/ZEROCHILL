# 5.7.0-beta.1 physical acceptance — pending

Device: Addy's OnePlus CPH2655 / Android 16.
Branch: `feature/social-media-reactions-analytics-v2`, descendant of approved
`4454d965ea3285800b98592d2dcca15c071fa304`. Use the exact SHA and signed, branch/SHA-named
Beta artifact recorded in the PR. No stable publication or PR merge.

Apply the documented additive backend rollout before feature testing. CI Beta
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
