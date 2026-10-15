# ZEROCHILL 5.0 visual candidate

**Physical review rejected the initial candidate at `13a32ab9`: the visible change
was too subtle.** The KEEP/FLATTEN decisions below describe that initial pass, not
acceptance of its visual direction. The correction implementation and current
material decisions are tracked in [ZEROCHILL_5_LIQUID_GLASS.md](ZEROCHILL_5_LIQUID_GLASS.md).

## Baseline and scope

Audited before implementation on `rebrand/zerochill` at
`91afd759bf32790cf43dc39ca97e06f98bd178ba` (including approved PR #294).
The integration branch still pointed at `eed95bde`; this candidate starts from
the newer complete source-of-truth branch. Approved lineage and release metadata
are not advanced by this unapproved visual candidate.

This is a source-backed audit. Render checks and device judgments are recorded
separately; source inspection cannot establish a visual quality score.

## Prioritized audit

| Category | Surfaces / evidence | Decision |
|---|---|---|
| Already excellent, protect | ShitTok playback, swipe, pinch, recent buffer/orientation work; `ChaosFeedView`, player pool | No playback or gesture rewrite, no new media chrome |
| Already excellent, protect | Primary-tab work queue and retained pages; `MainPagerAdapter`, `PrimaryTabWorkQueue` | Preserve immediate selection and deferred work |
| Already excellent, protect | Shows artwork framing, OnlyFap hero/gallery density, native gallery handoff | Preserve media geometry and loading pipeline |
| Already excellent, protect | Floating nav inset geometry and collapsed icon alignment | Preserve geometry and gesture/3-button behavior |
| Already excellent, protect | Static gallery skeleton, bounded scroll-aware `ThumbnailFades` | Keep cache-first and scroll-aware behavior |
| Already excellent, protect | Settings grouped rows; comments adjacency; Search partial-result recovery | Reuse restrained grouping; retain semantics |
| Major visual opportunity | Shared `zc_edge` is cyan; ordinary cards, top bars, sheets and buttons inherit glass | Distinct solid/raised/floating roles, neutral structure |
| Major visual opportunity | Library Connect cyan heading, icon wells, borders and unread markers compete | Neutral communication group, cyan unread only; consistent section rhythm |
| Noticeable weakness | Account/public profile duplicate gradient cards and CTAs; account ambient infinite glow | Flat utility cards, one primary CTA, remove ambient ornament |
| Noticeable weakness | Message bubble white 14sp text on #0892D0, approximately 3.48:1 | Dark outgoing surface, white body; cyan Send |
| Noticeable weakness | Message thread creates progress but does not mount it | Mount loading UI; make first-load failure recoverable |
| Noticeable weakness | Small update/comment actions; tiny supporting copy | Larger touch bounds and readable role-based text |
| Inconsistent with 5.0 | Chip selection scales to 1.04; press scale conflicts with selected scale | Stable selection geometry; bounded press feedback |
| Inconsistent with 5.0 | `OledThemeController` repaints cards after 140/520ms; `UiPolishController` clamps radii | Protect explicitly styled components from legacy overrides |
| Inconsistent with 5.0 | Duplicated near-blacks, grays, icon accents and utility headings | Shared palette/typography roles, migrate affected helpers |
| Inconsistent with 5.0 | Skeleton pulse ignores reduced motion and detach; loading completion bypasses app motion preference | Static skeleton, unified motion gate and lifecycle cleanup |
| Minor polish | Creator rank/merged badges use cyan despite being passive metadata | Neutral metadata; favorite/new states keep cyan |
| Minor polish | Utility popup uses total item count despite hidden items | Size visible rows, native dismissal/focus preserved |

## Coverage and glass decisions

| Surface | Glass decision | Treatment / protected behavior |
|---|---|---|
| ShitTok/fullscreen controls | KEEP | Existing video focus, scrims and controls |
| Shows/OnlyFap heroes | KEEP | Imagery scrims; quiet titles/actions, no extra panels |
| Floating bottom navigation | KEEP | Existing bounded reflection, geometry, inset handling |
| Source/filter rails | FLATTEN | Neutral rail; selected cyan only |
| Creator search/gallery/favorites | REDUCE | Media cards and favorite state; neutral rank/merge metadata |
| Library media rails | KEEP | Artwork scrims, retained cards and scroll positions |
| Library communication cards | FLATTEN | Shared neutral surfaces, unread hierarchy |
| Search/favorites/history/Watch Later/downloads | FLATTEN | Shared field/action/card roles, readable metadata |
| Updates/notifications/messages/comments | REDUCE | Opaque dark base, cyan unread/action/progress |
| Personal/public profile/account/security | FLATTEN | Identity through avatar/name, neutral utility groups |
| Account ambient glow | REMOVE | No decorative continuously moving backdrop |
| Settings/backup/restore | FLATTEN | Existing grouped rows; Android document picker retained |
| Setup/onboarding | REDUCE | Mascot/wordmark retained; neutral information cards |
| Update card/feedback | REDUCE | Readable copy, quiet structure, semantic actions |
| Menus/dialogs/sheets | FLATTEN | Opaque floating surface, neutral edge, native callbacks |
| Inputs/switches/checkboxes | REDUCE | Cyan focus/checked state; Android editing/accessibility |
| Loading/empty/error/transient | REDUCE | Stable placeholders, small indicators, readable recovery copy |
| Portrait/landscape/system bars | KEEP | Existing rotation, insets, keyboard, Back ownership |

## 5.0 rules

- OLED background; cool neutral raised surfaces; opaque floating utility surfaces.
- Electric cyan #0892D0 remains the brand/action color. White and cool gray own
  ordinary typography and icons. Near-black text on solid cyan buttons.
- Cyan means selected, focused, active, progress, unread/new, or primary action.
  Rank, section labels, borders and secondary actions are not primary actions.
- Typography roles: display 30sp, title 24sp, section 20sp, content 16sp,
  body 14sp, support 13sp, metadata 12sp, micro 11sp. Existing media title fits
  can stay deliberate exceptions; never shrink type to make a utility row fit.
- Spacing family 4/8/12/16/24/32dp. Shapes 12dp controls, 16dp cards,
  24dp floating panels, pills/circles only for appropriate controls/avatars.
- Keep touch areas at least 48dp where layout allows; visible glyphs need not grow.
- Motion roles: press 80ms, quick 140ms, standard 190ms, media 250ms.
  No new scroll-path blur, looping ornament, or blocking navigation transition.
- Loading completion reveals available content immediately. Stable skeletons
  do not pulse indefinitely. Reduced motion reaches the same final visual state.
- Existing application ID, signing, preference keys, data, content sources,
  backend boundaries, remote configuration and release version stay unchanged.

## Separate issues found, not justification for unrelated rewrites

- Feedback composer draft is rebuilt when switching its internal tabs.
- Field validation uses transient error text; a persistent supporting-text
  container would require a separate field-layout/state change.
- Backup/restore lacks an attached progress state.
- Broad process-recreation improvements are not part of a visual relaunch.

These must remain visible as limitations if not resolved in this candidate.
Physical review must include large fonts, narrow screens, both navigation modes,
IME, rotation/fullscreen, loading/offline states, account switching, and real media.
