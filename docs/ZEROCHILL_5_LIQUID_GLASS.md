# ZEROCHILL 5.0 correction candidate

## Acceptance

The physical-device review of PR #295 at `13a32ab9` failed: the user could barely
see a difference. Its palette, typography, readable messages, retained refresh
content, retry handling, static skeletons, motion gates and legacy-override
protections remain useful and are retained. Its deliberately unchanged navigation,
flat identity panels and opaque shared dialog treatment are replaced here.

The visual gate precedes full validation. Software fixture renders show layout,
material finishing and hierarchy; they do not exercise the hardware RenderEffect
chain. The dedicated `Glass renderer visual probe` workflow renders the same fixture
through both the approved 4.x renderer and candidate on API 35, with moving and
static backgrounds and both system navigation modes. Its logs must demonstrate
that the refractive tier actually ran. Synthetic frames do not establish playback
performance or physical-device smoothness.

## References and licensing

| Reference | Inspected revision | Used |
| --- | --- | --- |
| [Lelonio/Square](https://github.com/Lelonio/Square) | `5189ba37500b3985d53a1f6f985d01053c536338` | Read `ui/glass`, `LiquidBottomTab`, floating/shared-bounds navigation, geometric fold, capture, configurable effects and lower-cost modes. Engineering reference only. No Square-authored GPL code copied. |
| [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) | `65ab177e90e5c1d8c62e70cf7755841982da65f6` | Apache-2.0 SDF/refraction shader adapted from `backdrop/internal/Shaders.kt`. Read layer capture, lens pipeline, tab catalog, moving selection, highlights and press interaction. Full license retained under `licenses/`, copyright/change notice in the Java source, attribution in NOTICE.md and license/notice copies packaged in APK assets. |
| [elyesmansour/compose-floating-tab-bar](https://github.com/elyesmansour/compose-floating-tab-bar) | `88ad99a98e2c44f42da69e0c4f9c16fd381e59ca` | Apache-2.0 shared-bounds/inline-expanded transition reference. No source copied. Existing ZEROCHILL collapse geometry remains the implementation. |

No Compose dependency or bridge is added. The reusable primitive is a native
cropped backdrop renderer plus shared optical finishing, not duplicated fake
blur drawables. Ordinary information cards stay solid.

## Rendering

- The existing parent records content once as a RenderNode display list, excluding
  the floating overlay. This includes the existing TextureView media hierarchy;
  no player surface, ownership, warmup, handoff or lifecycle code changes.
- Effects rasterize only a padded crop around the visible floating surface. There
  are no CPU screenshots or full-screen blur bitmaps on the scroll path.
- API 33+ uses a 6dp blur and the adapted boundary-displacement shader. The outer
  capsule and selected lens share one content sample. The lens adds optical scaling
  and stronger edge displacement under pressure. No chromatic fringing.
- Uniform/effect state is cached while geometry and pressure stay unchanged.
  RenderEffect snapshots uniforms, so geometry changes rebuild the effect; normal
  scrolling reuses it. RenderNodes, paths and rectangles are reused. There is no
  continuous animation loop in production.
- API 31/32 and low-RAM devices use the cropped blur path; older devices/software
  canvases use the same neutral optical finishing without live refraction.
  Unsupported shader/render failures log and degrade without breaking navigation.
- Hidden/zero-alpha overlays skip capture/effects, including ShitTok pinch clear-display. Detached hosts discard display lists.
- Selected destination updates immediately. A separate 240ms visual lens follows
  taps; pager drag follows directly. Reduced motion snaps to the same final state.
  Press feedback deforms the material, keeping glyph geometry steady.
- Shows and OnlyFap share the same cropped renderer for their floating hero header.
  Their content geometry and data/hero/gallery scheduling remain intact.
- Bounded AlertDialogs use compositor background blur when supported/enabled and
  the same shared optical finish, with opaque fallback. Listener registration
  follows decor attachment. Menus/action sheets share the finish but do not claim
  refractive capture. Native dialog callbacks, focus, Back and IME remain owners.

## Visual changes

- Signature neutral liquid navigation with a moving embedded lens, replacing the
  dark cyan-outlined container/selected capsule. Cyan remains selected ink.
- Floating editorial headers over Shows and OnlyFap media, stronger solid section
  hierarchy, no media tile resize or player change.
- Branded personal Library masthead and distinct solid communication groups.
- Cut near-black identity covers with a stationary etched existing mascot for
  account/profile, rather than flat utility cards or ambient cyan glow.
- Consistent dimensional message/notification headers and shared floating dialog
  material, retaining PR #295's message legibility and state fixes.

## Compatibility boundaries

No application ID, package migration, signing identity, persisted key, database,
account/authentication, download/history/favorite/backup, source/parser, backend,
Supabase or remote-config changes. Release metadata is not advanced. Signed APKs
come from the existing upgrade-candidate workflow. Debug renderer fixtures are
excluded from release APKs. PR #295 stays draft and unmerged.

## Validation

Results and limitations are appended after the visual gate and final builds.

## Local validation and review status (October 15)

- The broad suite passed on `81918397`: 630 tests per debug/release variant,
  zero failures/errors, one skipped per variant. Final material-only adjustments
  use separate focused navigation, shader, contrast and native-render checks.
- The default test worker exhausted its Java heap. The app module now allocates
  1 GiB only to Gradle test JVMs. This is not an application heap change or a
  device-memory clearance. Existing offline test isolation was used locally.
- Old assertions tied to the retired XML material were updated. Complete vector
  glyph pixels, screenshot-calibrated geometry, inset/collapse bounds and native
  layout checks remain. The old 4.x full-material PNG fingerprint was removed
  because the user explicitly required replacing that material.
- An initial DEX assembly reported duplicate generated `coordinatorlayout.R`
  output. A clean rebuild of identical source succeeded for both APK variants;
  no dependency was changed to mask the stale build output.
- A bright-backdrop check found that primary cyan and a white lens wash could
  reduce control legibility. Reflected selected/inactive ink roles and a darker
  neutral lens replace that combination. The native contrast test checks at
  least 4.5:1 through glyph areas over white, black, gray and colored backgrounds.
  Primary brand cyan remains #0892D0 elsewhere.
- Local debug/release builds use preview version code `4003009` and name
  `4.3.9-zerochill-preview` (debug adds `-dev`). They use the local test signing
  key. They are not production-signed upgrade candidates and are not offered for
  installation over stable. Embedded source metadata and in-place upgrade
  verification remain the responsibility of the existing signed workflow.
- Production glass over TextureView video, actual frame pacing/GPU workload,
  device memory/allocations, physical Back/IME/rotation, real system navigation,
  large text and physical acceptance remain pending. No 60 fps or regression
  clearance is claimed. Full lintDebug was not rerun.
- Automatic approval review rejected the feature-branch push as publishing under
  the user's restriction. Commits remain local. Remote PR #295 stays draft at
  `13a32ab9`; no merge, tag, release or publication occurred. Approval is needed
  to push the existing branch and run the hardware/signed-upgrade workflows.

Final source/render results are included in the accompanying correction review.

Final runtime source: `70c45ba0` (later checkpoint changes are documentation/CI only).
The final focused debug/release runs each passed 27 tests with zero failures,
errors or skips. Both APK variants assembled successfully; a separate final
assembly confirmation also passed. Package/version and certificate were inspected
in the release APK. The certificate is the local Android Debug certificate,
not the production key. Packaged Apache license/notice assets were verified and
the debug-only renderer probe was confirmed absent from release DEX files.
