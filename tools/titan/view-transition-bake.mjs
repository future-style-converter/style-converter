#!/usr/bin/env node
//
// tools/titan/view-transition-bake.mjs — wave-38 lane N5, THE VIEW-TRANSITION
// CAPTURE MODE. The sixth bake.
//
// THE WALL, as wave-37 lane W5 measured and then REFUSED to hide.
// ---------------------------------------------------------------
// css-view-transitions is the web map's worst big section: 195 bucket-A
// tests, 192 scored, 54 passing (28.1 % — re-measured at this wave's HEAD,
// run-id wave38-N5-before, reproducing W5's 54/192 exactly). The cause is
// structural and W5 stated it plainly in
// tools/titan/results/wave37-W5-decisions.json:
//
//     `::view-transition`, `::view-transition-group/-image-pair/-old/-new`
//     are UA-generated boxes in the TOP LAYER holding SNAPSHOT IMAGES of
//     elements. They are not in the DOM, so neither the static extractor nor
//     post-load-extract.mjs (which walks real elements for computed styles)
//     can ever serialize them. 178 of 195 tests DID bake post-load and it
//     changed nothing about the pseudo tree.
//
// W5 refused the exclusion anyway (precision 0.719 against a 1.0 bar — see
// inject-wpt-block.mjs's REFUSED_EXCLUSION_TAGS banner) and named the closing
// move exactly:
//
//     "a view-transition CAPTURE mode — drive the transition in the ref
//      browser and serialize the settled pseudo tree as ordinary boxes —
//      not an exclusion"
//
// This module is that mode.
//
// WHAT IS ACTUALLY REACHABLE — measured, not assumed.
// ---------------------------------------------------
// Three facts, each probed on all 195 tests before a line of this file was
// written (probe artifacts under _diag38/N5/):
//
//   1. THE FROZEN STATE IS DRIVABLE. 189 of the 195 carry `class=reftest-wait`
//      on <html> and `<script src="/common/reftest-wait.js">`, and they freeze
//      the transition themselves (`animation-delay: 300s`,
//      `animation-play-state: paused`, `animation-duration: 300s`) before
//      calling `takeScreenshot()`, which removes the class. The corpus mirror's
//      sparse checkout has no `common/`, so that script 404s and the page's
//      inline `failIfNot(...)` throws — which is WHY today's post-load pass
//      sees only the pre-script DOM. REFTEST_WAIT_SHIM below supplies the
//      helpers at document-start; 165 of 195 then settled inside
//      VT_SETTLE_TIMEOUT_MS and 153 held an `:active-view-transition`.
//      WAVE-39 lane A4 re-counted the 30 that did NOT settle and found the
//      shim was three helpers short, not zero: `waitForCompositorReady` (28
//      calls, from /dom/events/scrolling/scroll_support.js — the entire
//      `scoped/` and `nested/` families gate their `startViewTransition()` on
//      it) and `takeScreenshotOnAnimationsReady` (4 calls, from reftest-wait.js
//      itself). With those supplied the family collapses from 30 to 1 — the
//      survivor is `hit-test-unrelated-element`, which needs testdriver INPUT
//      injection and is out of scope for any shim.
//
//   2. THE PSEUDO TREE IS FULLY INTROSPECTABLE. `getComputedStyle(
//      document.documentElement, '::view-transition-group(name)')` returns
//      REAL used values for the UA pseudos — width/height in px, the used
//      `transform` matrix (2-D or matrix3d), transform-origin, opacity,
//      visibility — and the same for `-image-pair`, `-old` and `-new`. A
//      pseudo that does not exist answers `width: auto`, which is the
//      existence discriminator this module uses.
//
//   3. THE SNAPSHOTS ARE MOSTLY FLAT COLOUR. The one thing NO API exposes is
//      the snapshot bitmap. But 506 of the 750 (group, leaf) cells in the
//      section are a single uniform colour — the corpus is built from
//      solid-background `<div>`s. Measured per leaf by isolating it (below)
//      over TWO backdrops and solving for alpha, which recovers the snapshot's
//      own colour, its own alpha and its own painted RECT exactly.
//
// So the bake ships REAL IR, not a raster: a `::view-transition` backdrop box,
// one absolutely positioned box per painted group carrying the used
// `transform`/`transform-origin`/`width`/`height`/`opacity`, and inside it one
// box per painted leaf carrying the measured rect and `background-color`. Each
// of the three runtimes must still position, size, transform, fade and fill
// those boxes itself — the same division of labour every other bake keeps
// (bidi-bake delivers Chromium's text GEOMETRY and each platform still shapes
// the glyphs; counter-bake delivers the marker STRING and each platform still
// paints it).
//
// THE RASTER IS DELIBERATELY NOT IMPLEMENTED. Lane N5's brief allowed the
// snapshot to ride the harness's `/wpt-image/` channel as an image asset. It
// is refused here, loudly, and the refusal is the honest half of this lane: an
// image asset would bake the browser's OUTPUT, not its resolution of an
// input the IR cannot carry, and every one of the 82 non-uniform tests would
// then "pass" by the harness re-displaying pixels Chromium rasterised from the
// page under test. That is a different signal wearing this section's name. A
// leaf whose snapshot is not flat colour BAILS the whole test (bail reason
// `non-uniform-snapshot`), the fixture stays byte-identical, and the failure
// stays visible as what it is. The population is stated in the log so a
// future wave can weigh a real snapshot channel with the numbers in hand.
//
// WAVE-40 lane T4 took that stated population and moved the part of it that
// is not a raster at all. Wave-39 A4's mining of the 40 `non-uniform-snapshot`
// bails found 28 whose settled snapshot solves to exactly TWO quantised
// colours in a trivially rectangular arrangement — an element that is half its
// old colour and half its new one, or a child box sitting inside its parent's
// snapshot. Those are not bitmaps the IR cannot carry; they are two boxes.
// fitTwoColourSnapshot measures the second region and PROVES it rectangular
// (every pixel inside its bbox is one colour, every pixel outside it the
// other, at the same 99.5 % floor the single-colour path uses PLUS a location
// test so a third region cannot hide in that floor's slack), and snapshotBoxes
// emits the background colour's COMPLEMENT pieces plus the sub-rect —
// non-overlapping, so a translucent snapshot colour composites exactly once,
// exactly as its own pixel did. Both orientations are tried, because which
// colour is the background is not decided by area: a box on a background makes
// the box the minority, a FRAME around a fill makes the frame the minority and
// the FILL the rectangle. Anything with a gradient, a glyph, a diagonal, three
// regions or a non-rectangular second region still fails that proof and still
// bails. The refusal is unchanged in kind: this ships measured geometry, never
// pixels.
//
// SCOPE BOUNDARY (documented, enforced, never silent). Every one of these is
// a loud bail that leaves the fixture byte-identical — the bail-to-static
// contract the other five bakes share:
//   - NO ACTIVE TRANSITION AT SETTLE (`:active-view-transition` false). 42 of
//     195, dominated by the `view-transition-name: auto` family (css-view-
//     transitions-2), which THIS Chromium does not implement: the names never
//     resolve, nothing is captured and the transition ends instantly. There is
//     no pseudo tree to serialize and the ref is unreachable — a browser-ref
//     divergence, not a harness gap.
//   - NOT FROZEN. Two pseudo-tree snapshots VT_STABILITY_DELAY_MS apart must
//     be IDENTICAL. A still-running transition has no state to serialize, and
//     baking one frame of it would encode this machine's scheduler. Same
//     settle-stability discipline as post-load-extract.mjs.
//     ONE drift is deferred rather than refused (wave-39 lane A4): the UA's
//     COMPLEMENTARY plus-lighter cross-fade, whose composite is provably
//     independent of the animation position when the two snapshots are the
//     same image. The proof is split — the algebra in isComplementaryCrossFade
//     / classifyStabilityDrift, the "same image" clause in pixels inside
//     planViewTransitionBake — and a pair that fails the pixel half bails
//     `cross-fade-not-invariant`. Nothing samples `currentTime`, so no baked
//     byte depends on where in its 250 ms the transition was caught.
//   - NO ROOT GROUP. When `:root` is NOT captured the page's own boxes keep
//     painting in place and only the NAMED elements drop out, so a faithful
//     bake would have to hide exactly those components — an element↔component
//     mapping that is guaranteed to have drifted, because the transition's own
//     callback mutated the DOM this walk is reading. 16 tests; refused rather
//     than approximated. (`escaped-name`, one of them, passes today at SSIM
//     1.0000 — bailing keeps it byte-identical and passing.)
//   - NON-UNIFORM SNAPSHOT that the TWO-COLOUR fit cannot describe — the
//     raster refusal above, narrowed by wave-40 lane T4 to exactly the
//     snapshots that are genuinely rasters. A snapshot which is one flat
//     colour with one flat rectangle in it ships as measured boxes; three
//     regions, a gradient, a glyph or a non-rectangular second region still
//     takes the whole test back to the static fixture.
//   - MIX-BLEND-MODE on a painted leaf: the image-pair is an isolation group
//     and a blended leaf's result depends on the pixels underneath it, which a
//     flat box cannot reproduce.
//   - AMBIGUOUS PAINT ORDER: `root` is first (it is the first element in tree
//     order) and the rest follow their named elements' document order; a name
//     whose element the callback removed has no document position, so if its
//     box OVERLAPS another painted group the stacking is unknowable and the
//     test bails. (Measured: only 3 of 53 bakeable tests have ANY overlapping
//     non-root pair at all.)
//   - OVERSIZE / DEGENERATE geometry: a group past VT_MAX_SNAPSHOT_PX on
//     either axis, or a used transform this module cannot read back.
//   - GENERATED NAMES / NESTED GROUP TREES (wave-41 lane T1). css-view-
//     transitions-2's `view-transition-name: auto|match-element` mints
//     per-element generated names the walker cannot enumerate (the computed
//     value is the keyword; its own group pseudo answers `auto`), and a name
//     whose `::view-transition-group-children` pseudo exists nests its child
//     groups inside it, where the single-group isolation instrument hides
//     them. Both shipped ink-deleting bakes when the two-colour fit let the
//     `nested/group-children-sizing*` pair through (five match-element bars
//     deleted, SSIM 0.8744→0.7910); both now bail before the existence
//     filter can drop a group silently.
//   - SNAPSHOT OVERFLOWS ITS BOX (wave-39 lane A4). A captured element's
//     snapshot carries its INK OVERFLOW; the isolation window is the union of
//     the group and leaf BOXES, which is smaller. Measuring inside that window
//     and calling the result flat would ship a cropped lie —
//     `content-with-child-with-transparent-background` did exactly that, baking
//     a 50x50 grey box and deleting the two children painting 75 px outside it
//     (ref ink coverage 2.137 %, cropped bake 1.068 %). probeSnapshotOverflow
//     now reads the complement of the window off the same composites and bails.
//
// ACTIVATION (opt-in, the same shape as the wave-16 post-load mode and the
// wave-23 bidi bake):
//   VT_BAKE=1 node tools/titan/extract-fixture.mjs <paths>...
//   node tools/titan/extract-fixture.mjs --vt-bake <paths>...
//   node tools/titan/view-transition-bake.mjs <paths>...   (this CLI, e2e)
// section-runner.sh pins VT_BAKE=1 next to POST_LOAD_EXTRACT / BIDI_BAKE
// (wave-19 RC-A5a, wave-30 fix-T3 precedent: engagement must never depend on
// ambient shell state, pinned only AFTER a scored section re-run moved the
// number). This bake CLEARED that bar at the wave-38 ship: the scored
// before/after on css-view-transitions (run-id wave38-vt, web-only, full cap)
// measured 50/192 -> 70/192 web passes — 21 gained, 1 lost
// (new-and-old-sizes-match, which previously passed on an unbaked fallback
// render; its baked tree diverges honestly and the loss is accepted).
// Delivery taxonomy from the same tree: 38 of 195 bake, 153 bail loudly,
// 2 decline, 0 errors, 0 fixtures lost.
//
// WAVE-39 lane A4 mined those 153 bails and moved three of the families
// (the shim gap, the invariant cross-fade, and the isolation instrument's
// live-`new` bug), then found and fixed a fourth thing on the way out — the
// crop this module had been shipping silently, see probeSnapshotOverflow.
// Re-scored twice on the same section, web-only, full cap. Run-id
// wave39-A4-after carries the first three changes alone: 70/192, 2 gained,
// 2 lost — and one of those losses,
// `content-with-child-with-transparent-background`, is what exposed the crop
// (it baked a 50x50 grey box and deleted two children painting 75 px outside
// it). Run-id wave39-A4-probe is the SHIPPED state, all four:
//
//     70/192 HOLDS, and the composition is strictly more honest.
//     GAINED  fragmented-at-start-ignored           (invariant cross-fade)
//             transform-origin-view-transition-group (invariant cross-fade)
//             new-and-old-sizes-match — wave 38's ACCEPTED LOSS, recovered:
//               its "honest divergence" was a cropped snapshot (1 131 px of
//               ink outside the measured box), and the crop bail restores the
//               byte-identical static fixture that passed before the bake.
//     LOST    capture-with-visibility-mixed-descendants
//             clip-path-larger-than-border-box-on-child-of-named-element
//             element-is-grouping-during-animation
//               — all three were passing on a CROPPED bake (the first drops a
//               10x10 green square 200 px outside the box; the third drops
//               193 344 px of ink). A pass earned by deleting visible ink is
//               the vacuous pass this campaign exists to retire, so the three
//               go back to static and fail honestly.
//
// Delivery taxonomy from the shipped tree: 34 of 195 bake (3 carrying a proved
// invariant cross-fade), 157 bail, 2 decline, 0 errors, 0 fixtures lost.
// The bail families moved like this. Two SHRANK because the module got
// better: `reftest-wait never cleared` 30 -> 2 (the shim gap; the survivors
// need testdriver INPUT injection, out of scope for any shim) and
// `transition-not-frozen` 40 -> 13 (the cross-fade classifier). The rest GREW
// because tests that used to bail early now reach a later, more specific
// refusal: `snapshot solve failed` 7 -> 52 (30 of them the new crop probe, 12
// the `massive-element-*` family's `object-fit: none`), `no-active-transition`
// 7 -> 27 (the newly settling `scoped/` family is css-view-transitions-2,
// which THIS Chromium does not run — a browser-ref divergence, not a harness
// gap), `root-not-captured` 22 -> 28. `non-uniform-snapshot` fell 40 -> 29
// only because the crop probe now fires first on many of them; the raster
// refusal itself is unchanged in kind.
//
// WAVE-45 lane X5 hardened the drive against its ONE recurring flake — the
// per-gate web −1 (wave-41: pseudo-with-classes-match-wildcard's crop probe
// read the whole window complement as transient "ink"; wave-44:
// fractional-box-old's one-off protocol timeout). viewTransitionBakeFixture
// now spends at most VT_MAX_REDRIVES fresh drives when a drive hits the
// DIRTY-PROBE signature (isDirtyOverflowProbe) or throws, then lets the
// outcome stand. Clean drives and genuine bails are byte-identical to
// wave-44 behaviour; see the function's doc block for the evidence.
//
// HONESTY STAMPS: `_wpt.viewTransitionBaked: true` on the fixture, plus
// `_lossy` + VT_BAKE_LOSSY_REASON in `_lossyReasons` on the emitted subtree
// AND in the fixture-level roll-up. The reason string is fresh, so it can
// never collide with a SCORE_EXCLUDED_TAG; inject-wpt-block.mjs reads it off
// the keyMap's `lossyReasons` to stamp `viewTransitionBaked` on the manifest
// row (`requires-view-transitions` is a REFUSED exclusion — it stays SCORED —
// so this is provenance, not re-admission).
//
// Exit codes: 0 — every input handled (baked, skipped or an expected bail);
// 1 — at least one hard error (browser / IO failure).

import puppeteer from 'puppeteer';
import { promises as fs } from 'node:fs';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';

// The static extractor's own building blocks — reused, never copied, so the
// trigger the CLI re-checks is the same one extract-fixture.mjs gates on.
import {
  viewTransitionBakeTrigger, extractFixture, writeFixturePair,
} from './extract-fixture.mjs';
// The shared decline surface (top-layer promotion) — a popover/dialog test is
// unrepresentable for exactly the reason recorded there, and a view transition
// does not make it representable.
import { postLoadDecline, CANVAS_FRAME_STYLE_ID } from './post-load-extract.mjs';
// The ONE canonical fixture-stem derivation: component ids are `<stem>__N…`,
// so anything appended to the tree must rebuild them the same way.
import { fixtureStem } from './safe-name.mjs';
// The browser-ref rendering contract: same launch flags, same 358×568 UNPADDED
// white canvas, same embedded Inter faces + line-height pin. Geometry read
// under any other environment would carry a systematic offset.
// padColorFor is the REF pipeline's own image-space frame rule (8-point border
// ring sample); the wave-41 frame-ring stamp below reuses it VERBATIM so the
// two sides of the comparison can never disagree about what a ring "is".
import {
  BROWSER_LAUNCH_ARGS, canvasFrameCss, CANVAS_BG,
  REF_RENDER_WIDTH, REF_RENDER_MIN_HEIGHT, padColorFor, parseHexRgb,
} from './capture-browser-ref.mjs';

const __filename = fileURLToPath(import.meta.url);
const __dirname  = dirname(__filename);
const REPO_ROOT  = resolve(__dirname, '..', '..');
// Same corpus-root override every other titan tool honours.
const WPT_DIR    = process.env.WPT_DIR ?? join(REPO_ROOT, 'tools', 'wpt');

// ── Tunables (exported so the unit pins assert the exact numbers) ────────────

/** How long to wait for the page to clear `class=reftest-wait`. The tests
 *  freeze their own transition and then call `takeScreenshot()` from a
 *  `.ready`/rAF continuation, so a settle is two frames of work — 4 s is two
 *  orders of magnitude of headroom, and a test that misses it is one whose
 *  settle depends on something the shim does not provide (testdriver input,
 *  a scroll helper). Measured: 165 of 195 settle, 30 time out. */
export const VT_SETTLE_TIMEOUT_MS = 4000;

/** Gap between the two pseudo-tree reads whose equality proves the transition
 *  is FROZEN. Same number, same purpose as post-load-extract's stability
 *  probe: one animation frame is 16.7 ms, so 100 ms is six frames of a
 *  still-running animation — far more than enough to move any interpolated
 *  transform, opacity or size. */
export const VT_STABILITY_DELAY_MS = 100;

/** The ONE tolerance the settle-stability comparison grants, and only on
 *  `opacity`.
 *
 *  WHY IT EXISTS, measured. This section freezes a transition by giving the
 *  UA keyframes a 300 s duration or delay, which is a freeze in every sense
 *  that matters but is not literally a stopped clock: over the 100 ms probe
 *  window the root cross-fade's opacity moves by ~2e-4 (`scroller`: 0.999978 →
 *  0.999799, `writing-mode-container-resize`: 1 → 0.99992). An 8-bit capture
 *  cannot express a change below 1/255, so those samples ARE the frozen state.
 *  A genuinely live transition is three orders of magnitude away and is still
 *  rejected: the 250 ms default cross-fade moves 0.41 in the same window
 *  (`span-with-overflowing-text-hidden`: 0.424375 → 0.0124518). There is no
 *  ambiguous middle in this corpus, so the boundary is a measured one.
 *
 *  Nothing else gets a tolerance: a length, a matrix component or any
 *  non-numeric value must be EXACTLY equal across the two reads. */
export const VT_STABILITY_OPACITY_EPS = 1 / 255;

/** Effective-opacity floor below which a leaf is "not painted". 1/255 is the
 *  smallest alpha an 8-bit capture can express; anything under it cannot put
 *  ink on either side of the diff. */
export const VT_PAINT_EPSILON = 1 / 255;

/** How far `old.opacity + new.opacity` may sit from 1 and still be recognised
 *  as the UA's COMPLEMENTARY cross-fade pair (see isComplementaryCrossFade).
 *
 *  Same 8-bit argument as VT_STABILITY_OPACITY_EPS: the two UA keyframes
 *  (`-ua-view-transition-fade-out` 1→0 and `-ua-view-transition-fade-in` 0→1)
 *  share one timing function, so the used pair is exactly (1−p, p) and the sum
 *  is exactly 1 up to the browser's own float rounding. Measured across the 29
 *  candidate tests (_diag39/A4/walk-tnf.json): every painted pair summed to
 *  1 or 0.999999 on BOTH probe reads. A sum that misses by a whole 8-bit step
 *  is not this pair and must not inherit its algebra. */
export const VT_XFADE_SUM_EPS = 1 / 255;

/** Per-channel 8-bit tolerance for "these two pixels are the same colour".
 *  The isolation reads are two flat composites of the SAME snapshot, so the
 *  only spread is the browser's own rounding on the alpha solve; 2/255 keeps
 *  a real gradient (which must bail) from ever reading as flat. */
export const VT_COLOR_TOLERANCE = 2;

/** Fraction of the painted rect that must carry the dominant colour for the
 *  snapshot to count as flat. Not 100 % because a snapshot's OWN edge can be
 *  antialiased against its transparent surround (a fractional-px box); the
 *  remainder is bounded to the rect's perimeter by the rect test itself. */
export const VT_UNIFORM_DOMINANCE_PCT = 99.5;

/** Hard cap on a group's own box, per axis, in CSS px. Past this the
 *  isolation screenshot stops being a cheap measurement (the `massive-element-*`
 *  family declares 10 000 px boxes) and the test bails honestly. */
export const VT_MAX_SNAPSHOT_PX = 4000;

/** Hard cap on baked group boxes per fixture — the same guard-rail
 *  MAX_BIDI_RUNS is: a pathological tree would stop being a meaningful
 *  cross-platform comparison. */
export const MAX_VT_GROUPS = 40;

/** The LOUD marker the emitted subtree (and the fixture roll-up) carries.
 *  Never consulted by applyNaScoreGate — SCORE_EXCLUDED_TAGS holds no such
 *  string — so it is informational provenance, exactly like
 *  'baked-bidi-visual-order'. */
export const VT_BAKE_LOSSY_REASON = 'baked-view-transition-tree';

/** The two backdrops the alpha solve uses. Pure black and pure white are the
 *  only pair that makes the solve exact in 8-bit: over black a pixel reads
 *  `α·s`, over white `α·s + (1−α)·255`, so α and s fall straight out with no
 *  division by a channel that might be zero on both sides. */
export const VT_ISOLATION_BACKDROPS = ['#000000', '#FFFFFF'];

/** The id of the isolation stylesheet. Distinct from CANVAS_FRAME_STYLE_ID —
 *  the canvas frame is the rendering CONTRACT and stays for the whole page
 *  life; this sheet is a measurement instrument, rewritten per leaf. */
export const VT_ISOLATION_STYLE_ID = 'sc-vt-isolation';

/** `object-fit` values whose painted content is guaranteed to stay INSIDE the
 *  replaced element's own content box, so the isolation window (the union of
 *  the group and leaf boxes) cannot truncate it.
 *
 *  `none` and `cover` are absent on purpose: both can scale the snapshot
 *  larger than its box, and CSS Images 3 §5.5 does not clip the overflow. A
 *  leaf using either bails rather than shipping a rect the window cut down.
 *  (Measured on this section: every leaf is `fill`, the initial value — the
 *  `*-object-view-box` pair reshapes the source box, not the fit.) */
export const VT_CONTAINED_OBJECT_FITS = ['fill', 'contain', 'scale-down'];

/** How much ink may sit OUTSIDE the isolation window before the measurement is
 *  declared cropped (see probeSnapshotOverflow).
 *
 *  WHY IT IS NOT ZERO. The window edge is a whole-pixel ceil of a possibly
 *  fractional box, so a snapshot whose own edge lands mid-pixel can leak one
 *  antialiased row or column of near-transparent ink past it. Four pixels is
 *  below anything a diff can see and orders of magnitude below a real overflow
 *  — the defect this probe was written for (a captured element whose children
 *  paint 75 px outside its border box) leaks 2 500.
 *
 *  WHY THE PROBE EXISTS AT ALL, measured. `content-with-child-with-transparent-
 *  background` baked a 50x50 grey box and SILENTLY DROPPED the two 25x50
 *  children painting outside it: the ref carries 2.137 % ink coverage, the
 *  wave-38-instrument bake carried 1.068 %. A captured element's snapshot
 *  includes its ink overflow; the isolation window is the union of the group
 *  and leaf BOXES, which is smaller. Cropping and calling the result "uniform"
 *  is precisely the silent fallthrough this repo forbids. */
export const VT_OVERFLOW_INK_TOLERANCE_PX = 4;

/** Fraction of the isolation window's COMPLEMENT (viewport area minus window
 *  area, in px²) at or above which an overflow-probe reading stops meaning
 *  "this snapshot has ink overflow" and starts meaning "this composite was
 *  never isolated at all" — the DIRTY-SETTLE signature the bounded re-drive
 *  in viewTransitionBakeFixture fires on (wave-45 lane X5).
 *
 *  WHY THE TWO POPULATIONS CANNOT COLLIDE, measured. A genuine ink overflow
 *  is the snapshot's own painted excess past the union window and is small by
 *  construction: fractional-box-with-overflow-children reads 225 px outside
 *  its 101x51 window (0.11 % of the 198 193 px² complement),
 *  fractional-box-with-shadow reads 624 px (0.31 %), and the founding case —
 *  content-with-child-with-transparent-background's children painting 75 px
 *  past the box — leaks ~2 500 px (1.3 %). The one dirty settle ever caught
 *  in a gate (wave-41 fullcap2: pseudo-with-classes-match-wildcard, `snapshot
 *  overflows its 200x100 box (183344px of ink outside it)`) read EXACTLY the
 *  full complement of its window in the 358×568 viewport (203 344 − 20 000 =
 *  183 344): every single pixel outside the window solved as ink, which no
 *  element's overflow can produce and which is the arithmetic fingerprint of
 *  one composite showing the UN-isolated page — a backdrop pixel then reads
 *  α = 1 − (w−b)/255 ≈ 1 instead of 0, because the "black" composite was
 *  never black. Half the complement sits two orders of magnitude above every
 *  genuine overflow ever measured and a factor of two below the one observed
 *  dirty read, so the boundary is a measured one with no ambiguous middle —
 *  the same shape of argument as VT_STABILITY_OPACITY_EPS. */
export const VT_DIRTY_PROBE_COMPLEMENT_FRACTION = 0.5;

/** How many RE-DRIVES one test may spend recovering from a transient dirty
 *  settle (or a transient drive fault) before the drive's own outcome stands.
 *  ONE, not more: both flakes this bound exists for are one-offs that
 *  re-drive clean on the first try (wave-41: 4/4 clean re-drives after the
 *  dirty crop probe; wave-44's fractional-box-old protocol timeout: baked
 *  clean on every re-drive; wave-45 X5: 12/12 clean, byte-identical drives of
 *  the same tests), so a second retry could only ever mask a REPRODUCIBLE
 *  fault — which must stay visible as what it is (a bail or an error), never
 *  be retried into silence. */
export const VT_MAX_REDRIVES = 1;

/** Smallest sub-rectangle, in px², the TWO-COLOUR fit will call a second
 *  region rather than noise (see fitTwoColourSnapshot).
 *
 *  WHY IT IS NOT 1. The minority class is whatever did not match the dominant
 *  colour, and on a snapshot whose own edge lands mid-pixel that set can be a
 *  handful of antialiased pixels. A 2-px "region" is not a shape the bake
 *  learned anything from — it is the residue the 99.5 % dominance floor already
 *  forgives on the single-colour path, and promoting it to a box would emit a
 *  hairline nobody rendered. Four px² is the same allowance
 *  VT_OVERFLOW_INK_TOLERANCE_PX makes for the same cause (one antialiased row
 *  or column of a fractional-px edge), and it is deliberately the WEAKER of
 *  the two gates: the rectangle proof below is what actually decides, and a
 *  region small enough to matter here has already been forgiven upstream by
 *  `uniform`. This exists so the fit can never answer with a speck. */
export const VT_TWO_COLOUR_MIN_AREA_PX = 4;

/** How far from a region BOUNDARY a mismatched pixel may sit, in px, before
 *  the two-colour fit refuses the snapshot outright.
 *
 *  WHY A LOCATION TEST AND NOT JUST A COUNT. VT_UNIFORM_DOMINANCE_PCT forgives
 *  0.5 % of the rect, which on a root-sized snapshot (358×568) is a thousand
 *  pixels — enough for a whole THIRD region to hide in the slack and be
 *  deleted silently. The 0.5 % exists for one measured reason only: a
 *  snapshot's own edge can be antialiased where the box lands mid-pixel, and
 *  those pixels are by construction ON an edge. So the allowance is spent
 *  where its justification lives — within one pixel of the painted rect's
 *  perimeter or of the second region's — and a mismatch anywhere else is a
 *  shape this fit did not measure, which bails. One px, because an 8-bit AA
 *  boundary between two flat fills is one pixel wide. */
export const VT_TWO_COLOUR_EDGE_SLACK_PX = 1;

// ── The /common/ shim ───────────────────────────────────────────────────────
//
// WHY THIS EXISTS AND WHY IT IS NOT A NETWORK FETCH. fetch-wpt.sh's sparse
// checkout (TITAN_ARCHITECTURE §3.3) materialises `css/`, `fonts/` and
// `resources/` — not `common/`. Under `file://` the tests' server-absolute
// `<script src="/common/reftest-wait.js">` resolves to `file:///common/...`
// and 404s no matter what the checkout holds, so the helpers have to come
// from the harness either way. They are re-stated here rather than fetched
// because a bake must be reproducible offline from the pinned corpus alone.
//
// The functions are the complete set of MISSING globals the 195 tests call,
// enumerated from the corpus rather than guessed (wave-39 lane A4 re-counted
// them across the 30 `reftest-wait never cleared` bails —
// `_diag39/A4/settle8.json` and the `<script src>` census beside it):
//
//   from /common/reftest-wait.js — `failIfNot` 172×, `takeScreenshot`,
//     `takeScreenshotDelayed`, `takeScreenshotOnAnimationsReady` 4×;
//   from /dom/events/scrolling/scroll_support.js — `waitForCompositorReady`
//     28×, the SINGLE largest cause of the never-settled family (8 tests in
//     `nested/`, 18 in `scoped/`, plus `nothing-captured` and
//     `empty-render-target-capture`);
//   from the same reftest-wait.js — `waitForAtLeastOneFrame` 13×.
//
// `takeScreenshot` removes `reftest-wait` from the root — that class removal
// IS this module's settle signal, exactly as it is the WPT runner's.
//
// TWO documented divergences, both of them shaped so a genuinely unsupported
// feature still bails rather than baking a half-page:
//
//   - `failIfNot`: the WPT harness marks the test failed and stops; here it
//     clears the wait class so the drive terminates promptly instead of
//     burning VT_SETTLE_TIMEOUT_MS. The state it leaves behind still has to
//     survive the `:active-view-transition` gate below.
//   - `waitForCompositorReady`: WPT's version starts a throwaway 1 ms
//     animation on `document.body` and awaits its `ready`, which resolves
//     once the compositor has accepted the animation. Starting an opacity
//     animation on the body forces a compositing layer, and this pipeline's
//     whole raster contract is `--disable-gpu-rasterization` CPU raster
//     (BROWSER_LAUNCH_ARGS) — promoting a layer here would change the pixels
//     the alpha solve then measures. The shim delivers the same GUARANTEE the
//     callers actually depend on — "at least one frame has been produced
//     before I start the transition" — with the double-rAF the corpus's own
//     `waitForAtLeastOneFrame` uses, and touches no style. Callers use it
//     only as a scheduling barrier before `startViewTransition()`.
export const REFTEST_WAIT_SHIM = `(function(){
  function takeScreenshot(){ document.documentElement.classList.remove('reftest-wait'); }
  function takeScreenshotDelayed(t){ setTimeout(takeScreenshot, t || 0); }
  function failIfNot(cond){ if (!cond) takeScreenshot(); }
  function waitForAtLeastOneFrame(){
    return new Promise(function(r){ requestAnimationFrame(function(){ requestAnimationFrame(r); }); });
  }
  /* WPT semantic verbatim: wait for every running animation to be READY (the
     UA view-transition keyframes included), then shoot. A rejected ready
     promise still shoots — the WPT original has no rejection path because a
     cancelled animation cannot happen there, and hanging is the one outcome
     this drive must never produce. */
  function takeScreenshotOnAnimationsReady(){
    var ready = document.getAnimations().map(function(a){ return a.ready; });
    return Promise.all(ready).then(takeScreenshot, takeScreenshot);
  }
  /* See the divergence note above: a frame barrier, not a layer promotion. */
  function waitForCompositorReady(){ return waitForAtLeastOneFrame(); }
  window.takeScreenshot = takeScreenshot;
  window.takeScreenshotDelayed = takeScreenshotDelayed;
  window.takeScreenshotOnAnimationsReady = takeScreenshotOnAnimationsReady;
  window.failIfNot = failIfNot;
  window.waitForAtLeastOneFrame = waitForAtLeastOneFrame;
  window.waitForCompositorReady = waitForCompositorReady;
})();`;

/**
 * The canvas frame, installed at DOCUMENT-START rather than after `load`.
 *
 * THE RACE THIS FIXES, measured. Every other bake injects the frame with
 * `page.evaluate` after `page.goto(waitUntil:'load')`, which is correct for
 * them: they READ geometry afterwards. A view transition is different — the
 * page's own script calls `startViewTransition()` from `onload` + two rAFs,
 * and that call is what SNAPSHOTS the geometry. Injecting the frame after
 * `load` therefore lands on either side of the snapshot depending on
 * scheduling: the same test (3d-transform-incoming) read its `hidden` group at
 * `matrix(1,0,0,1,8,108)` on one probe run and `matrix(1,0,0,1,0,100)` on the
 * next — the 8 px is the UA body margin the frame removes. Installing at
 * document-start makes the frame unconditionally older than the snapshot.
 *
 * `document.head` does not exist yet at document-start, so the style is
 * appended to whichever root node is available and re-homed on
 * `readystatechange` if it was not the head. A `<style>` is honoured from
 * either position (HTML §4.2.6 — the element applies wherever it sits), so
 * this is a tidiness re-home, not a correctness one.
 */
export function canvasFrameInstallerScript(styleId, css) {
  return `(function(){
    var ID = ${JSON.stringify(styleId)}, CSS = ${JSON.stringify(css)};
    function install(){
      if (document.getElementById(ID)) return true;
      var root = document.head || document.documentElement;
      if (!root) return false;
      var s = document.createElement('style');
      s.id = ID; s.textContent = CSS;
      root.appendChild(s);
      return true;
    }
    if (!install()) {
      document.addEventListener('readystatechange', install, true);
    }
  })();`;
}

// ── Pure helpers: computed-style reading ────────────────────────────────────

/** Round to 2 dp — the precision every rect in this module is quantised to,
 *  matching bidi-bake/post-load so the bakes' numbers are diff-comparable. */
export function r2(n) { return Math.round(n * 100) / 100; }

/** Format a px length for the fixture's CSS-shaped property map. */
export function px(n) { return `${r2(n)}px`; }

/**
 * Does this `::view-transition-group(<name>)` pseudo actually EXIST?
 *
 * There is no `document.querySelector('::view-transition-group(x)')`, and
 * `getComputedStyle` never throws for a name that was not captured — it
 * answers the pseudo's INITIAL values. The UA stylesheet gives every real
 * group a used `width`/`height` in px (the captured element's border-box
 * size), while a phantom answers the `auto` initial. That is the whole
 * discriminator, and it is exact: a real group can never be `auto`-sized
 * because the UA sheet sets both from the capture.
 */
export function groupPseudoExists(g) {
  return !!g && /px$/.test(String(g.width ?? '')) && /px$/.test(String(g.height ?? ''));
}

/**
 * Effective opacity of one leaf — the product down the pseudo chain. The
 * `image-pair` box exists to isolate a blend between old and new; with a
 * single painted leaf its only remaining contribution is its own opacity,
 * which is why this folds it into the leaf rather than emitting a third box.
 */
export function leafEffectiveOpacity(group, leaf) {
  const n = (v) => { const f = Number.parseFloat(v); return Number.isFinite(f) ? f : 1; };
  return n(group.opacity) * n(group.imagePair.opacity) * n(leaf.opacity);
}

/**
 * Is this leaf PAINTED in the frozen state?
 *
 * `visibility` is read off the LEAF's own computed style, which already
 * carries inheritance — `::view-transition-image-pair(hidden){visibility:
 * hidden}` (the idiom half this section uses to park a decoy group) makes the
 * leaf's own computed value `hidden` without the caller having to walk the
 * chain. Opacity does not inherit, hence the explicit product above.
 */
export function leafPainted(group, leaf) {
  if (!leaf || leaf.visibility === 'hidden') return false;
  return leafEffectiveOpacity(group, leaf) > VT_PAINT_EPSILON;
}

/**
 * Read a used `transform` string back as its 2-D affine components, or null
 * when it is a shape this module will not reason about.
 *
 * Only used for the OVERLAP test that decides whether an unknowable paint
 * order matters — the emitted wire carries the matrix string VERBATIM, so a
 * 3-D transform is passed through to the runtimes untouched (they all parse
 * `matrix3d`; see runtimes/web/src/engine/transforms/TransformExtractor.ts and
 * its two twins). `matrix3d` is projected to its 2-D block here, which is an
 * over-approximation of the painted quad and therefore conservative in the
 * only direction that matters: it can only ever declare an overlap that a
 * perspective divide would have avoided.
 */
export function parseUsedMatrix(tr) {
  const s = String(tr ?? '').trim();
  if (!s || s === 'none') return { a: 1, b: 0, c: 0, d: 1, e: 0, f: 0 };
  let m = /^matrix\(([^)]+)\)$/.exec(s);
  if (m) {
    const v = m[1].split(',').map((x) => Number.parseFloat(x));
    if (v.length !== 6 || v.some((x) => !Number.isFinite(x))) return null;
    return { a: v[0], b: v[1], c: v[2], d: v[3], e: v[4], f: v[5] };
  }
  m = /^matrix3d\(([^)]+)\)$/.exec(s);
  if (m) {
    const v = m[1].split(',').map((x) => Number.parseFloat(x));
    if (v.length !== 16 || v.some((x) => !Number.isFinite(x))) return null;
    return { a: v[0], b: v[1], c: v[4], d: v[5], e: v[12], f: v[13] };
  }
  return null;
}

/** Axis-aligned bound of the group's own box after its used transform — the
 *  input to the overlap test. Deliberately the bound of the four transformed
 *  corners (not the box), so a rotation cannot hide an overlap. */
export function transformedBounds(left, top, width, height, matrix) {
  const pts = [[0, 0], [width, 0], [0, height], [width, height]].map(([x, y]) => ({
    x: matrix.a * x + matrix.c * y + matrix.e + left,
    y: matrix.b * x + matrix.d * y + matrix.f + top,
  }));
  const xs = pts.map((p) => p.x), ys = pts.map((p) => p.y);
  return { x: Math.min(...xs), y: Math.min(...ys), w: Math.max(...xs) - Math.min(...xs), h: Math.max(...ys) - Math.min(...ys) };
}

/** Do two axis-aligned bounds share any area? Touching edges do not count. */
export function boundsOverlap(a, b) {
  return a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;
}

// ── Pure pixels: two composites → the snapshot's own colour, alpha and rect ──

/**
 * Solve one leaf's snapshot out of its two isolation composites.
 *
 * The leaf is rendered twice, alone, at opacity 1 over an opaque backdrop —
 * once black, once white. For each pixel the compositing equation gives
 *
 *     over black: cb = α·s
 *     over white: cw = α·s + (1−α)·255
 *
 * so α = 1 − (cw − cb)/255 and s = cb/α. Both are exact in 8-bit up to the
 * browser's own rounding, and the pair also SEPARATES a white snapshot from a
 * transparent one — the single ambiguity a one-backdrop read cannot resolve,
 * and the one that would otherwise paint an opaque white box over a coloured
 * backdrop.
 *
 * Returns `{ rect, rgba, uniform, distinct, coverage }`:
 *   - `rect` — the bounding box of the pixels with α above the paint floor,
 *     in the group's own coordinates. This is what makes `object-fit`
 *     letterboxing and `object-view-box` cropping come out right without
 *     modelling either: the painted rect is MEASURED, not derived.
 *   - `rgba` — the dominant (r,g,b) with the dominant α as a 0–1 float.
 *   - `uniform` — every pixel inside `rect` is painted and matches the
 *     dominant within VT_COLOR_TOLERANCE, at ≥ VT_UNIFORM_DOMINANCE_PCT.
 *   - `two` — present ONLY when `uniform` is false: fitTwoColourSnapshot's
 *     answer (`null` when the snapshot is genuinely a raster). See that
 *     function for the whole decision; the short version is that a
 *     `{ kind, rect, rgba, coverage }` here means the snapshot is `two.base ??
 *     rgba` everywhere in `rect` EXCEPT the sub-rectangle `two.rect`, which is
 *     `two.rgba` (or transparent when that is null).
 *
 * A leaf with no painted pixel at all returns `rect: null` — a legitimate
 * outcome (an empty `<div>`'s snapshot) that emits no box.
 */
export function solveSnapshot(blackPng, whitePng) {
  const b = PNG.sync.read(blackPng);
  const w = PNG.sync.read(whitePng);
  if (b.width !== w.width || b.height !== w.height) {
    return { error: `isolation size drift ${b.width}x${b.height} vs ${w.width}x${w.height}` };
  }
  const W = b.width, H = b.height;
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
  let painted = 0;
  const counts = new Map();
  // First pass: alpha + colour per pixel, painted bbox, colour histogram.
  const alpha = new Float32Array(W * H);
  const rgb = new Uint8Array(W * H * 3);
  for (let i = 0; i < W * H; i++) {
    const o = i * 4;
    // Green channel drives the alpha solve — it carries the most luminance
    // precision of the three and the equation is channel-independent, so
    // picking one avoids averaging three roundings into a fourth.
    const a = 1 - (w.data[o + 1] - b.data[o + 1]) / 255;
    if (!(a > VT_PAINT_EPSILON)) { alpha[i] = 0; continue; }
    alpha[i] = a;
    painted++;
    const x = i % W, y = (i - x) / W;
    if (x < minX) minX = x; if (x > maxX) maxX = x;
    if (y < minY) minY = y; if (y > maxY) maxY = y;
    for (let ch = 0; ch < 3; ch++) rgb[i * 3 + ch] = Math.max(0, Math.min(255, Math.round(b.data[o + ch] / a)));
    // Quantise for the dominance histogram: the exact match test below uses
    // the tolerance, this bucket only has to FIND the dominant candidate.
    const key = quantKey(rgb, alpha, i);
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }
  if (!painted) return { rect: null, rgba: null, uniform: true, distinct: 0, coverage: 0 };
  // The dominant bucket, then its exact representative (first pixel in it).
  let topKey = null, topN = 0;
  for (const [k, c] of counts) if (c > topN) { topN = c; topKey = k; }
  let rep = null;
  for (let i = 0; i < W * H && !rep; i++) {
    if (!alpha[i]) continue;
    if (quantKey(rgb, alpha, i) === topKey) rep = pixelRep(rgb, alpha, i);
  }
  const rect = { x: minX, y: minY, w: maxX - minX + 1, h: maxY - minY + 1 };
  // Second pass: how much of the RECT matches the representative? Unpainted
  // pixels inside the rect count as mismatches — a hole is not flat colour.
  let match = 0;
  const area = rect.w * rect.h;
  for (let y = rect.y; y <= maxY; y++) {
    for (let x = rect.x; x <= maxX; x++) {
      const i = y * W + x;
      if (!alpha[i]) continue;
      if (pixelMatchesRep(rgb, alpha, i, rep)) match++;
    }
  }
  const uniform = (100 * match / area) >= VT_UNIFORM_DOMINANCE_PCT;
  return {
    rect,
    rgba: { r: rep.r, g: rep.g, b: rep.b, a: r2(rep.a) },
    uniform,
    distinct: counts.size,
    coverage: r2(100 * match / area),
    // Only a NON-uniform snapshot is asked the two-colour question; a uniform
    // one already has its answer and must keep the wave-38/39 result shape
    // byte-for-byte (an added key would break every deepEqual pin on it).
    ...(uniform ? {} : { two: fitTwoColourSnapshot({ W, alpha, rgb, rect, rep }) }),
  };
}

// ── The TWO-COLOUR fit (wave-40 lane T4) ────────────────────────────────────

/** The dominance-histogram bucket for one pixel — quantised rgb (1 bit off
 *  each channel) plus alpha in 1/64ths. Extracted so the dominant search and
 *  the SECOND-colour search below bucket identically; the exact per-pixel
 *  verdict is always pixelMatchesRep's, never a bucket comparison. */
function quantKey(rgb, alpha, i) {
  return `${rgb[i * 3] >> 1},${rgb[i * 3 + 1] >> 1},${rgb[i * 3 + 2] >> 1},${Math.round(alpha[i] * 64)}`;
}

/** One pixel as an exact representative colour (unrounded alpha — r2 is
 *  applied only where the value leaves this module). */
function pixelRep(rgb, alpha, i) {
  return { r: rgb[i * 3], g: rgb[i * 3 + 1], b: rgb[i * 3 + 2], a: alpha[i] };
}

/** Is this pixel the same colour as `rep`? Per-channel VT_COLOR_TOLERANCE plus
 *  the same 2/255 alpha window the wave-38 solve used — moved out of
 *  solveSnapshot verbatim so the two searches cannot drift apart. */
function pixelMatchesRep(rgb, alpha, i, rep) {
  return Math.abs(rgb[i * 3] - rep.r) <= VT_COLOR_TOLERANCE
      && Math.abs(rgb[i * 3 + 1] - rep.g) <= VT_COLOR_TOLERANCE
      && Math.abs(rgb[i * 3 + 2] - rep.b) <= VT_COLOR_TOLERANCE
      && Math.abs(alpha[i] - rep.a) <= 2 / 255;
}

/**
 * Can this non-uniform snapshot be described as ONE flat colour with ONE flat
 * sub-rectangle in it? Returns `{ kind, rect, rgba, coverage, base? }` or null
 * — `rect`/`rgba` are the sub-rectangle's, and `base` appears only when the
 * background is NOT the dominant colour (the frame orientation below).
 *
 * WHY THIS IS NOT A CRACK IN THE RASTER REFUSAL. The refusal (module banner)
 * is against shipping the browser's OUTPUT PIXELS as an image asset, which
 * would let the harness re-display a raster it never resolved. This fit ships
 * no pixels: it ships the same kind of typed IR the single-colour path already
 * ships — positioned boxes with a `background-color` — and it only ships them
 * when the measurement PROVES the snapshot is exactly that geometry. Anything
 * with a gradient, a glyph, a diagonal, three regions or a non-rectangular
 * second region fails the proof below and bails as before — measured on this
 * section, `animating-new-content` (117 colours) and
 * `content-with-transform-old-image` (139) still bail, which is what tells you
 * the proof is doing work. The population this WAS written for (wave-39 lane
 * A4's mining of the 40 `non-uniform-snapshot` bails) is 28 snapshots that
 * solve to exactly two quantised colours.
 *
 * THE DECOMPOSITION IS NON-OVERLAPPING, which is what makes it exact for any
 * alpha. A "colour B box drawn over a colour A box" would composite B over A
 * wherever B is translucent, and a snapshot pixel's α is its own, not a
 * stack's. So the base colour is emitted as the COMPLEMENT of the sub-rect
 * (rectComplement — up to four boxes; exactly one for a split band), and the
 * sub-rect is emitted once. Every emitted box then paints over the same thing
 * the snapshot pixel did: the group's transparent backdrop.
 *
 * THE SECOND REGION MAY BE TRANSPARENT. `rgba: null` means the sub-rect is a
 * HOLE — the snapshot is colour A with a rectangular bite out of it — and
 * snapshotBoxes emits nothing there. Same proof, one fewer box.
 *
 * `state` is solveSnapshot's own pixel state (`W`, `alpha`, `rgb`, `rect`,
 * `rep`); this stays a private helper because reproducing that state is the
 * job of solveSnapshot, and the unit pins drive it through the same PNG pair
 * the browser produces.
 */
function fitTwoColourSnapshot({ W, alpha, rgb, rect, rep }) {
  const x1 = rect.x + rect.w, y1 = rect.y + rect.h;
  // Pass 1 — the two candidate minority populations inside the rect: pixels
  // that are painted but are not the dominant colour, and pixels that are not
  // painted at all (a hole). They are counted separately because they need
  // different representatives, and the LARGER one is the region under test:
  // if both are substantial the fit will fail dominance below, which is the
  // correct answer (three classes is not two).
  let nOther = 0, nHole = 0;
  const counts2 = new Map();
  for (let y = rect.y; y < y1; y++) {
    for (let x = rect.x; x < x1; x++) {
      const i = y * W + x;
      if (!alpha[i]) { nHole++; continue; }
      if (pixelMatchesRep(rgb, alpha, i, rep)) continue;
      nOther++;
      const k = quantKey(rgb, alpha, i);
      counts2.set(k, (counts2.get(k) ?? 0) + 1);
    }
  }
  const holeIsMinority = nHole > nOther;
  if (!(holeIsMinority ? nHole : nOther)) return null;   // nothing to fit
  // Pass 2 — the minority representative (null for a hole) and its bbox.
  let rep2 = null;
  if (!holeIsMinority) {
    let topKey = null, topN = 0;
    for (const [k, c] of counts2) if (c > topN) { topN = c; topKey = k; }
    for (let y = rect.y; y < y1 && !rep2; y++) {
      for (let x = rect.x; x < x1 && !rep2; x++) {
        const i = y * W + x;
        if (alpha[i] && quantKey(rgb, alpha, i) === topKey) rep2 = pixelRep(rgb, alpha, i);
      }
    }
    if (!rep2) return null;                              // defensive: no bucket
  }
  const isDominant = (i) => !!alpha[i] && pixelMatchesRep(rgb, alpha, i, rep);
  const isMinority = (i) => (holeIsMinority
    ? !alpha[i]
    : (!!alpha[i] && pixelMatchesRep(rgb, alpha, i, rep2)));
  const asRgba = (r) => (r ? { r: r.r, g: r.g, b: r.b, a: r2(r.a) } : null);
  // Pass 3 — the rectangle proof, run for each ORIENTATION. Which of the two
  // colours is the background and which is the inner rectangle is not decided
  // by area:
  //   - a BOX ON A BACKGROUND makes the box the minority (orientation 1);
  //   - a FRAME AROUND A FILL makes the frame the minority, and the frame is
  //     not a rectangle — the FILL is (orientation 2). A thin border is as
  //     representable as orientation 1: same two colours, same two
  //     non-overlapping regions, roles swapped. Measured on this section:
  //     `content-visibility-auto-shared-element` is exactly this shape (a 1 px
  //     black border round a 98×498 green fill) and bailed before wave 40.
  // Orientation 2 is skipped when the minority is a HOLE: a transparent base
  // would have put the painted bbox somewhere else, so the shape cannot arise.
  const one = proveTwoRegions({ W, alpha, rect }, isDominant, isMinority);
  if (one) return { ...one, rgba: asRgba(rep2) };
  if (holeIsMinority) return null;
  const two = proveTwoRegions({ W, alpha, rect }, isMinority, isDominant);
  // The BASE colour moves with the roles: here the surround is the minority
  // and the sub-rect is the dominant, so snapshotBoxes must not reach for
  // `s.rgba` (which is always the dominant) as the background.
  return two ? { ...two, rgba: asRgba(rep), base: asRgba(rep2) } : null;
}

/**
 * The rectangle PROOF, for one assignment of the two colour classes.
 *
 * `isBase` and `isSub` are per-pixel class predicates. The claim under test is
 * "everything in `rect` is the base class except one solid sub-RECTANGLE of
 * the sub class", and the sub-rectangle is not guessed — it is the bounding
 * box of the sub class, so a shape that is not a rectangle (an L, a diagonal,
 * a ring, a second scattered region) puts base-class pixels inside its own
 * bbox and fails here.
 *
 * TWO tests decide, and both must pass:
 *   - the same VT_UNIFORM_DOMINANCE_PCT floor the single-colour path uses;
 *   - the LOCATION test (VT_TWO_COLOUR_EDGE_SLACK_PX): every mismatch must sit
 *     within a pixel of the painted rect's perimeter or of the sub-rect's.
 *     Without it, 0.5 % of a root-sized snapshot is a thousand pixels — room
 *     for a whole third region to be deleted silently.
 *
 * Returns `{ kind, rect, coverage }` (the caller attaches the colours) or null.
 */
function proveTwoRegions({ W, alpha, rect }, isBase, isSub) {
  const x1 = rect.x + rect.w, y1 = rect.y + rect.h;
  let mnX = Infinity, mnY = Infinity, mxX = -Infinity, mxY = -Infinity;
  for (let y = rect.y; y < y1; y++) {
    for (let x = rect.x; x < x1; x++) {
      if (!isSub(y * W + x)) continue;
      if (x < mnX) mnX = x; if (x > mxX) mxX = x;
      if (y < mnY) mnY = y; if (y > mxY) mxY = y;
    }
  }
  if (!Number.isFinite(mnX)) return null;
  const sub = { x: mnX, y: mnY, w: mxX - mnX + 1, h: mxY - mnY + 1 };
  // A sub-rect that is the WHOLE rect leaves the base colour nowhere to paint
  // — that is not a two-region shape, it is a failed class split.
  if (sub.w >= rect.w && sub.h >= rect.h) return null;
  if (sub.w * sub.h < VT_TWO_COLOUR_MIN_AREA_PX) return null;
  const e = VT_TWO_COLOUR_EDGE_SLACK_PX;
  const nearOuterEdge = (x, y) =>
    x - rect.x <= e || x1 - 1 - x <= e || y - rect.y <= e || y1 - 1 - y <= e;
  const nearSubEdge = (x, y) =>
    x >= sub.x - e && x < sub.x + sub.w + e && y >= sub.y - e && y < sub.y + sub.h + e
    && !(x >= sub.x + e && x < sub.x + sub.w - e && y >= sub.y + e && y < sub.y + sub.h - e);
  let match = 0;
  for (let y = rect.y; y < y1; y++) {
    for (let x = rect.x; x < x1; x++) {
      const i = y * W + x;
      const inSub = x >= sub.x && x < sub.x + sub.w && y >= sub.y && y < sub.y + sub.h;
      if (inSub ? isSub(i) : isBase(i)) { match++; continue; }
      // A mismatch away from every boundary is a shape, not an edge artifact.
      if (!nearOuterEdge(x, y) && !nearSubEdge(x, y)) return null;
    }
  }
  const coverage = 100 * match / (rect.w * rect.h);
  if (coverage < VT_UNIFORM_DOMINANCE_PCT) return null;
  return { kind: twoColourKind(rect, sub), rect: sub, coverage: r2(coverage) };
}

/**
 * Name the two-region SHAPE, from the two rects alone.
 *
 * This is the "simple geometry" the lane brief asks the fit to DETECT, and it
 * is reported (in the bake log and on the plan's leaf boxes) rather than used
 * as a gate: every one of these shapes decomposes into ≤ 4 non-overlapping
 * boxes by the same rectComplement, so accepting one and refusing another
 * would be taste, not measurement. The names are what make a batch log
 * readable — `band-h` (the section's commonest: an element half old-colour,
 * half new-colour), `nested` (a child box inside its parent's snapshot),
 * `corner`, and the degenerate `full` the fit itself rejects.
 */
export function twoColourKind(outer, inner) {
  const spansW = inner.x <= outer.x && inner.x + inner.w >= outer.x + outer.w;
  const spansH = inner.y <= outer.y && inner.y + inner.h >= outer.y + outer.h;
  if (spansW && spansH) return 'full';
  if (spansW) return 'band-h';       // full-width band → splits top/bottom
  if (spansH) return 'band-v';       // full-height band → splits left/right
  const edges = (inner.x <= outer.x) + (inner.y <= outer.y)
              + (inner.x + inner.w >= outer.x + outer.w)
              + (inner.y + inner.h >= outer.y + outer.h);
  return edges ? 'corner' : 'nested';
}

/**
 * `outer` minus `inner`, as up to four NON-OVERLAPPING rectangles.
 *
 * Top band, bottom band, then the left and right pieces of the middle strip —
 * the standard four-way split, in that order so the emitted box list is
 * deterministic. An `inner` that misses `outer` entirely yields `outer`
 * unchanged; an `inner` that covers it yields nothing.
 *
 * Non-overlapping is the whole point (see fitTwoColourSnapshot): each piece
 * paints directly onto the group's backdrop, so a translucent snapshot colour
 * composites exactly once, exactly as its own pixel did.
 */
export function rectComplement(outer, inner) {
  const x0 = Math.max(outer.x, inner.x), y0 = Math.max(outer.y, inner.y);
  const x1 = Math.min(outer.x + outer.w, inner.x + inner.w);
  const y1 = Math.min(outer.y + outer.h, inner.y + inner.h);
  if (!(x1 > x0 && y1 > y0)) return [{ ...outer }];
  const out = [];
  const oy1 = outer.y + outer.h, ox1 = outer.x + outer.w;
  if (y0 > outer.y) out.push({ x: outer.x, y: outer.y, w: outer.w, h: y0 - outer.y });
  if (y1 < oy1)     out.push({ x: outer.x, y: y1, w: outer.w, h: oy1 - y1 });
  if (x0 > outer.x) out.push({ x: outer.x, y: y0, w: x0 - outer.x, h: y1 - y0 });
  if (x1 < ox1)     out.push({ x: x1, y: y0, w: ox1 - x1, h: y1 - y0 });
  return out;
}

/**
 * The BOXES one solved snapshot ships, as `{ rect, rgba }` in the leaf's own
 * coordinates — the single place that knows how a solve becomes geometry.
 *
 * Three cases, one function so the plan and the cross-fade invariance check
 * can never disagree about what a solve means:
 *   - nothing painted → no boxes;
 *   - uniform → one box, exactly the wave-38 shape;
 *   - two-colour → the dominant's complement pieces first, then the sub-rect
 *     (omitted when it is a transparent hole).
 * Order is paint order, but the pieces never overlap, so it is only
 * determinism, not stacking.
 */
export function snapshotBoxes(s) {
  if (!s || !s.rect) return [];
  if (!s.two) return [{ rect: s.rect, rgba: s.rgba }];
  // `two.base` is present only for the FRAME orientation, where the background
  // is the minority colour rather than the dominant one; everywhere else the
  // base IS `s.rgba` and the override is absent.
  const base = s.two.base ?? s.rgba;
  return [
    ...rectComplement(s.rect, s.two.rect).map((rect) => ({ rect, rgba: base })),
    ...(s.two.rgba ? [{ rect: s.two.rect, rgba: s.two.rgba }] : []),
  ];
}

/**
 * Crop the top-left `w`x`h` of an isolation composite, as a PNG buffer.
 *
 * The composites are now taken at the FULL viewport (see the driver) so that
 * probeSnapshotOverflow can see the region OUTSIDE the isolation window. The
 * solve itself must still run on exactly the window it always did, and this is
 * that crop — the same pixels the wave-38 `page.screenshot({ clip })` produced,
 * because it is the same render.
 */
export function cropComposite(pngBuf, w, h) {
  const src = PNG.sync.read(pngBuf);
  const out = new PNG({ width: w, height: h });
  for (let y = 0; y < h; y++) {
    src.data.copy(out.data, y * w * 4, y * src.width * 4, y * src.width * 4 + w * 4);
  }
  return PNG.sync.write(out);
}

/**
 * How many painted pixels of this leaf's snapshot fall OUTSIDE the isolation
 * window — i.e. how much ink the window would have cropped.
 *
 * Same alpha solve as solveSnapshot (green channel, two backdrops), applied to
 * the complement of the window rather than to the window. A non-zero answer
 * means the leaf's snapshot is bigger than the union of its group and leaf
 * boxes, which happens whenever the captured element has INK OVERFLOW: the
 * snapshot carries the overflowing descendants, the boxes do not.
 *
 * The root capture needs no special case: its group box IS the snapshot
 * containing block, so its window is the whole viewport and the complement is
 * empty by construction.
 */
export function probeSnapshotOverflow(blackPng, whitePng, win) {
  const b = PNG.sync.read(blackPng);
  const w = PNG.sync.read(whitePng);
  if (b.width !== w.width || b.height !== w.height) {
    return { error: `overflow-probe size drift ${b.width}x${b.height} vs ${w.width}x${w.height}` };
  }
  let outside = 0;
  for (let y = 0; y < b.height; y++) {
    for (let x = 0; x < b.width; x++) {
      if (x < win.width && y < win.height) continue;   // inside the window
      const o = (y * b.width + x) * 4;
      const a = 1 - (w.data[o + 1] - b.data[o + 1]) / 255;
      if (a > VT_PAINT_EPSILON) outside++;
    }
  }
  return { outside };
}

/**
 * Is this overflow-probe reading the DIRTY-SETTLE signature rather than real
 * ink overflow? (wave-45 lane X5)
 *
 * The distinction decides RETRY-vs-BAIL in viewTransitionBakeFixture: a
 * genuine overflow — a snapshot painting past the union of its group and leaf
 * boxes — is a stable property of the page and must BAIL, because re-driving
 * it would measure the same ink again. A dirty read — the whole window
 * complement solving as ink because one composite raced the isolation
 * restyle and shows the un-isolated page — is a property of one bad
 * measurement session, and the ONE bounded re-drive is exactly the remedy the
 * wave-41 evidence supports (4/4 clean re-drives of the identical test). See
 * VT_DIRTY_PROBE_COMPLEMENT_FRACTION for the measured gap between the two
 * populations (≤1.3 % of the complement for every genuine overflow ever
 * observed vs 100 % for the one observed dirty read).
 *
 * The retry can never change a correct outcome: whatever the SECOND drive
 * measures stands, so a pathological page whose real ink genuinely covers
 * half the complement re-measures the same overflow and bails exactly as
 * before — it only pays one extra drive.
 *
 * A window that fills the whole viewport has an EMPTY complement (the root
 * group's usual case): nothing outside it can ever read as ink, so the answer
 * is false by construction rather than a 0-of-0 ambiguity.
 */
export function isDirtyOverflowProbe(outsidePx, win, viewport) {
  // The region the probe actually scanned: every viewport pixel that is not
  // in the top-left window (probeSnapshotOverflow skips x<w && y<h).
  const complement = viewport.width * viewport.height - win.width * win.height;
  // ≥, not >: the observed dirty read sits AT 100 % of the complement, and a
  // reading at half of it is already two orders of magnitude past every
  // genuine overflow measured — there is nothing between to protect.
  return complement > 0 && outsidePx >= VT_DIRTY_PROBE_COMPLEMENT_FRACTION * complement;
}

/**
 * First structural difference between two pseudo-tree reads, as a dotted
 * path + the two values — or null when they are identical.
 *
 * The settle-stability bail is only useful if it says WHAT moved: "the tree
 * moved" cannot distinguish a still-interpolating opacity (a genuinely
 * unfrozen transition) from a walker artifact such as an unstable name order.
 * Both are bails, but only one is a bug in this module, and the reason string
 * is the only place that distinction ever surfaces in a batch log.
 *
 * The single tolerance the walk grants (see VT_STABILITY_OPACITY_EPS) lives in
 * allDifferences, which this is the one-answer front end for: one traversal
 * implementation, so the classifier below and the log line here can never
 * disagree about what counts as a difference.
 */
export function firstDifference(a, b, path = '') {
  return allDifferences(a, b, path)[0] ?? null;
}

/**
 * EVERY structural difference between two pseudo-tree reads, not just the
 * first. Same walk, same tolerance rule as firstDifference (which stays the
 * single-answer front end for the log line and the unit pins); this one exists
 * because the drift CLASSIFIER below has to see the whole set — a tree whose
 * only movement is one complementary cross-fade is a different animal from one
 * that also moved a matrix, and "the first difference" cannot tell them apart.
 */
export function allDifferences(a, b, path = '', out = []) {
  if (a === b) return out;
  const ta = a === null ? 'null' : Array.isArray(a) ? 'array' : typeof a;
  const tb = b === null ? 'null' : Array.isArray(b) ? 'array' : typeof b;
  if (ta !== tb) { out.push({ path: path || '<root>', a, b }); return out; }
  if (ta === 'object' || ta === 'array') {
    for (const k of [...new Set([...Object.keys(a), ...Object.keys(b)])]) {
      allDifferences(a[k], b[k], path ? `${path}.${k}` : k, out);
    }
    return out;
  }
  if (path.endsWith('opacity')) {
    const na = Number.parseFloat(a), nb = Number.parseFloat(b);
    if (Number.isFinite(na) && Number.isFinite(nb)
        && Math.abs(na - nb) < VT_STABILITY_OPACITY_EPS) return out;
  }
  out.push({ path: path || '<root>', a, b });
  return out;
}

/**
 * Is this group's leaf pair the UA's COMPLEMENTARY plus-lighter cross-fade?
 *
 * WHY THE QUESTION EARNS AN ANSWER. 29 of the 40 `transition-not-frozen` bails
 * (wave-38's single largest bail family, re-measured in
 * _diag39/A4/walk-tnf.json) drift ONLY in leaf opacity, and in every one of
 * them the two leaves' used opacities sum to 1 on both probe reads. That is
 * the signature of the UA cross-fade running with its default 250 ms timing
 * while the test froze only the groups it cares about — and a cross-fade
 * between two IDENTICAL snapshots is TIME-INVARIANT, so there is a frozen
 * rendered state to serialize even though the tree is still moving.
 *
 * The algebra, stated so the gate is provable rather than hopeful.
 * `::view-transition-image-pair` carries `isolation: isolate` (UA sheet), so
 * the pair's backdrop starts at transparent black and PLUS-LIGHTER reduces to
 * a premultiplied SUM of the two leaves:
 *
 *     Co·αo = α·(1−p)·C  +  α·p·C  =  α·C
 *     αo    = α·(1−p)    +  α·p    =  α
 *
 * for a snapshot of colour C and own alpha α — independent of p, the animation
 * position. Three things have to hold for that collapse, and each is a
 * separate clause below:
 *   1. BOTH leaves blend with `plus-lighter`. Under `normal` the composite is
 *      source-over, `αo = αp + α(1−p)(1−αp)`, which is NOT independent of p.
 *   2. The used opacities are complementary — (1−p, p), i.e. they sum to 1
 *      within VT_XFADE_SUM_EPS. Two independently animated opacities are not
 *      one cross-fade.
 *   3. The pair is actually painted at all: neither leaf `visibility: hidden`
 *      (the decoy-group idiom parks a group exactly this way) and the group ×
 *      image-pair chain above them is not itself faded out.
 *
 * The FOURTH condition — that the two snapshots really are identical — cannot
 * be answered from computed style. It is answered in pixels, after the solve,
 * inside planViewTransitionBake; a pair that fails it bails
 * `cross-fade-not-invariant` and the fixture stays byte-identical.
 *
 * Note what this deliberately does NOT do: it never samples the animation at a
 * chosen time. Nothing here reads or writes `currentTime`, so no baked byte
 * depends on where in its 250 ms this machine's scheduler happened to catch
 * the transition — which is the whole reason the settle-stability contract
 * exists.
 */
export function isComplementaryCrossFade(group) {
  const old = group?.old, nw = group?.new;
  if (!old || !nw) return false;
  // (1) the UA blend that makes the sum exact.
  if (old.mixBlendMode !== 'plus-lighter' || nw.mixBlendMode !== 'plus-lighter') return false;
  // (3a) a hidden leaf paints nothing, so there is no cross-fade to collapse.
  if (old.visibility === 'hidden' || nw.visibility === 'hidden') return false;
  // (2) complementary — the pair is (1−p, p).
  const o = Number.parseFloat(old.opacity), n = Number.parseFloat(nw.opacity);
  if (!Number.isFinite(o) || !Number.isFinite(n)) return false;
  if (Math.abs(o + n - 1) > VT_XFADE_SUM_EPS) return false;
  // (3b) the chain ABOVE the leaves must still put ink on the canvas. Leaf
  //      opacity is excluded on purpose: the whole point is that one of the
  //      two is momentarily near zero while the other carries the ink.
  return crossFadeChainOpacity(group) > VT_PAINT_EPSILON;
}

/** The opacity the group contributes ABOVE an invariant cross-fade pair: the
 *  chain product with the leaves' own (summing-to-1) contribution left out.
 *  Mirrors leafEffectiveOpacity, which folds the leaf in for the single-leaf
 *  case. */
export function crossFadeChainOpacity(group) {
  const n = (v) => { const f = Number.parseFloat(v); return Number.isFinite(f) ? f : 1; };
  return n(group.opacity) * n(group.imagePair?.opacity);
}

/**
 * Split the settle-stability drift into the part that FORBIDS a bake and the
 * part that merely defers the decision to the pixels.
 *
 * Returns `{ hard, crossFade }`:
 *   - `hard` — the first difference that is not a recognised complementary
 *     cross-fade, in firstDifference's `{ path, a, b }` shape, or null. A
 *     non-null `hard` is the `transition-not-frozen` bail, unchanged.
 *   - `crossFade` — the sorted group NAMES whose only drift was such a pair.
 *     planViewTransitionBake must then prove, in pixels, that each one's two
 *     snapshots are identical.
 *
 * A group only qualifies when it reads as a complementary cross-fade in BOTH
 * probe reads and keeps the same name at the same index across them — an
 * unstable name ORDER is a walker artifact and must stay a hard bail, which is
 * exactly the distinction firstDifference's doc comment says the reason string
 * exists to preserve.
 */
export function classifyStabilityDrift(first, second) {
  const crossFade = new Set();
  for (const d of allDifferences(first, second)) {
    const m = /^groups\.(\d+)\.(old|new)\.opacity$/.exec(d.path);
    if (!m) return { hard: d, crossFade: [...crossFade].sort() };
    const i = Number(m[1]);
    const ga = first?.groups?.[i], gb = second?.groups?.[i];
    if (!ga || !gb || ga.name !== gb.name) return { hard: d, crossFade: [...crossFade].sort() };
    if (!isComplementaryCrossFade(ga) || !isComplementaryCrossFade(gb)) {
      return { hard: d, crossFade: [...crossFade].sort() };
    }
    crossFade.add(ga.name);
  }
  return { hard: null, crossFade: [...crossFade].sort() };
}

/**
 * The screenshot window one leaf must be measured in: the UNION of the
 * group's own box and the leaf's own box, expressed as a viewport-origin clip
 * plus the translate the isolation applies to bring that union to (0, 0).
 *
 * WHY THE UNION AND NOT THE GROUP BOX. A leaf is not confined to its group:
 * `::view-transition-old(target.cls){ left: 100px }` — the twelve
 * `pseudo-with-classes-*` tests — moves the snapshot clean outside a 100×100
 * group. Clipping to the group reported "nothing painted" and silently
 * dropped the whole group, which is exactly the silent fallthrough this repo
 * forbids.
 *
 * WHY NO SLACK MARGIN. The window has to fit inside the viewport: the
 * `::view-transition` backdrop the alpha solve reads against is
 * `position: fixed; inset: 0`, so there is no defined backdrop outside it and
 * a wider clip would read the page instead — which solves to "opaque",
 * flooding the measurement. Containment is guaranteed structurally instead,
 * by the VT_CONTAINED_OBJECT_FITS gate: with a fit that keeps content inside
 * its box, the union IS the complete extent of the ink.
 *
 * `offset` is what the solved rect is later re-based by, so the emitted
 * `left`/`top` stay in the GROUP's coordinates.
 */
export function isolationWindow(group, leaf) {
  const n = (v, fallback) => { const f = Number.parseFloat(v); return Number.isFinite(f) ? f : fallback; };
  const gw = n(group.width, 0), gh = n(group.height, 0);
  const lx = n(leaf.left, 0), ly = n(leaf.top, 0);
  const lw = n(leaf.width, gw), lh = n(leaf.height, gh);
  const x0 = Math.min(0, lx), y0 = Math.min(0, ly);
  const x1 = Math.max(gw, lx + lw), y1 = Math.max(gh, ly + lh);
  return {
    // `+ 0` normalises the `-0` that `-x0` yields for the overwhelmingly
    // common x0 === 0 case. It reaches a CSS `translate()` and a rect
    // re-base, where -0 is harmless, but it also reaches every equality
    // check in this module's tests and any future JSON diff — and `-0` is a
    // distinct value to Object.is/deepStrictEqual. Costs nothing, removes a
    // whole class of spurious "changed" reads.
    offset: { x: -x0 + 0, y: -y0 + 0 },
    width:  Math.ceil(x1 - x0),
    height: Math.ceil(y1 - y0),
  };
}

/** `#RRGGBB` → the `rgb(r, g, b)` spelling getComputedStyle answers with.
 *  Used only to recognise the canvas frame's own injected background, so a
 *  malformed input must fail loudly rather than silently never matching. */
export function hexToComputedRgb(hex) {
  const m = /^#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i.exec(String(hex).trim());
  if (!m) throw new Error(`view-transition-bake: not a #RRGGBB colour: ${hex}`);
  return `rgb(${parseInt(m[1], 16)}, ${parseInt(m[2], 16)}, ${parseInt(m[3], 16)})`;
}

/** CSS colour literal for a solved snapshot. An α of 1 emits `rgb()` so the
 *  overwhelmingly common case stays the simplest string every parser on the
 *  three platforms handles first. */
export function snapshotColorCss(rgba) {
  return rgba.a >= 1
    ? `rgb(${rgba.r}, ${rgba.g}, ${rgba.b})`
    : `rgba(${rgba.r}, ${rgba.g}, ${rgba.b}, ${rgba.a})`;
}

/**
 * The composed-canvas FRAME colour one settled page implies, or null when the
 * pipeline default (CANVAS_BG) already matches — the wave-41 frame-ring stamp.
 *
 * THE DEFECT THIS CLOSES, measured at the wave-41 full-cap re-score. A test
 * whose author paints `::view-transition { background: lightpink }` shows that
 * backdrop to the viewport EDGE whenever no snapshot covers it (fixed inset 0,
 * top layer). The REF pipeline frames its render in IMAGE space with
 * padColorFor — a uniform border ring becomes the 16px pad — so the frozen
 * ref carries a PINK pad. The composed harnesses paint their pad from
 * resolveCanvasBackground, which reads ONE channel: the `meta.role:
 * 'body-root'` component's background (apps/web-harness/src/ui/
 * ComposedCaptureGallery.tsx and its two native twins). The bake never wrote
 * that channel, so every such capture wore a WHITE ring against a pink-padded
 * ref: 15 baked tests failing in a cluster at SSIM ≈0.9172 whose diffs are
 * 100% ring (interior 0 differing pixels of 203 344 — e.g.
 * new-content-is-empty-div, ring 30 656/30 656 differing).
 *
 * THE RULE IS THE REF'S OWN RULE, run on the test side: sample the SETTLED
 * page's border ring with the same 8-point predicate the ref pad uses
 * (padColorFor, reused not copied). Three outcomes:
 *   - uniform non-white ring → that colour, as the `rgb()` string the fixture
 *     property bag carries (the stamp target is a CSS declaration);
 *   - uniform WHITE ring → null: the pipeline default already matches, and
 *     stamping white would churn fixture bytes for zero pixel change;
 *   - non-uniform ring → padColorFor answers its CANVAS_BG fallback → null,
 *     which mirrors the ref side exactly (its pad falls back to white too).
 * Because every currently-PASSING baked test has a white ring on both sides
 * (a non-white settled ring against today's white frame cannot score ≥0.95),
 * the null path keeps each of them byte-identical — measured, not hoped.
 */
export function frameRingColor(pngBuf) {
  // The ref pipeline's own 8-point ring rule, byte-for-byte (same function).
  const ring = padColorFor(PNG.sync.read(pngBuf));
  // CANVAS_BG parsed through the same helper the ref uses, so a future
  // contract change cannot desynchronise the two comparisons.
  const bg = parseHexRgb(CANVAS_BG);
  // Fallback OR genuinely-white ring: the composed default already matches.
  if (ring.r === bg.r && ring.g === bg.g && ring.b === bg.b && ring.a === bg.a) return null;
  // The ring is opaque by construction (screenshots have no alpha), so the
  // plain `rgb()` spelling is exact — same shape snapshotColorCss emits.
  return `rgb(${ring.r}, ${ring.g}, ${ring.b})`;
}

// ── Pure planning: walk → bake plan ─────────────────────────────────────────

/** The declaration block for ONE emitted snapshot box. Extracted so the
 *  single-leaf path, the cross-fade path and the two-colour pieces all emit
 *  the SAME keys in the SAME order — the fixture's bytes depend on it, and a
 *  fourth hand-rolled copy is how that silently drifts. `opacity` is the
 *  already-folded chain value; 1 emits no declaration at all (the wave-38
 *  shape). */
function leafBoxProps(box, opacity) {
  return {
    position: 'absolute',
    left:   px(box.rect.x),
    top:    px(box.rect.y),
    width:  px(box.rect.w),
    height: px(box.rect.h),
    'box-sizing': 'border-box',
    'background-color': snapshotColorCss(box.rgba),
    ...(opacity < 1 ? { opacity: String(r2(opacity)) } : {}),
  };
}

/**
 * Turn the browser walk (+ the per-leaf solved snapshots) into the boxes the
 * fixture will carry, or a bail string.
 *
 * `walk` is what inPageVtWalker returns; `solved` maps `"<name>|<leaf>"` to a
 * solveSnapshot result. Both are plain data, so the whole decision surface is
 * unit-testable without a browser.
 *
 * `crossFadeNames` is classifyStabilityDrift's deferred list — group names
 * whose leaf pair is a complementary plus-lighter cross-fade and whose
 * time-invariance still has to be PROVED in pixels here. Defaults to empty, so
 * every caller that does not opt in keeps the wave-38 decision surface
 * byte-for-byte.
 */
export function planViewTransitionBake(walk, solved, crossFadeNames = []) {
  const crossFade = new Set(crossFadeNames);
  if (!walk.active) return { bail: 'no-active-transition' };
  // ── css-view-transitions-2 guards (wave-41), BEFORE the existence filter ──
  // GENERATED NAMES: `view-transition-name: auto | match-element` gives every
  // captured element a UNIQUE generated name; the computed value the walker
  // enumerates is the KEYWORD, whose own group pseudo answers `auto` — so the
  // real groups are unaddressable and would be dropped SILENTLY by the filter
  // below. Measured: nested/group-children-sizing baked "1 group" while
  // Chromium's settled state paints five `match-element` bars (the bake shipped
  // a green frame and deleted every bar — the exact ink-deletion vacuity the
  // wave-39 crop probe retired for leaves). A keyword name in the walk means
  // the tree has groups this module cannot see, so the whole test bails.
  const kw = walk.groups.find((g) => g.name === 'auto' || g.name === 'match-element');
  if (kw) {
    return { bail: `generated-names (view-transition-name: ${kw.name} mints unaddressable groups)` };
  }
  // NESTED GROUP TREES: a name whose `::view-transition-group-children`
  // pseudo EXISTS (real px, same discriminator as groupPseudoExists) is a
  // nesting container — its children's boxes resolve against IT, and the
  // isolation instrument hides it (`::view-transition-group(*){opacity:0}`),
  // so every nested child solves to "nothing painted" and vanishes. Bail
  // until the instrument can isolate a chain, not a single group.
  const nested = walk.groups.find((g) => groupPseudoExists(g.groupChildren));
  if (nested) {
    return { bail: `nested-group-tree ('${nested.name}' has ::view-transition-group-children)` };
  }
  const groups = walk.groups.filter((g) => groupPseudoExists(g));
  if (!groups.length) return { bail: 'no-view-transition-groups' };
  // See the SCOPE BOUNDARY banner: without a LIVE root capture the page keeps
  // painting and only the named elements drop out — a per-element hide this
  // bake deliberately does not attempt. Both halves are checked: the root
  // element must still hold a name AND that name must have a real group.
  if (!walk.rootCaptureName) {
    return { bail: 'root-not-captured (page content paints in place)' };
  }
  if (!groups.some((g) => g.name === walk.rootCaptureName)) {
    return { bail: `no group for the root capture name '${walk.rootCaptureName}'` };
  }
  if (groups.length > MAX_VT_GROUPS) {
    return { bail: `group budget exceeded (${groups.length} > ${MAX_VT_GROUPS})` };
  }

  // Paint order: `root` first — `:root` is the first element in tree order and
  // therefore the first captured — then the rest in their named elements'
  // document order. `documentIndex` is -1 for a name whose element the
  // callback removed; those sort last and are flagged for the overlap test.
  const ordered = [...groups].sort((x, y) => {
    if (x.name === walk.rootCaptureName) return -1;
    if (y.name === walk.rootCaptureName) return 1;
    const xi = x.documentIndex < 0 ? Number.MAX_SAFE_INTEGER : x.documentIndex;
    const yi = y.documentIndex < 0 ? Number.MAX_SAFE_INTEGER : y.documentIndex;
    return xi - yi;
  });

  const boxes = [];
  for (const g of ordered) {
    const width = Number.parseFloat(g.width), height = Number.parseFloat(g.height);
    const left = Number.parseFloat(g.left) || 0, top = Number.parseFloat(g.top) || 0;
    if (!Number.isFinite(width) || !Number.isFinite(height)) {
      return { bail: `unreadable group box for '${g.name}'` };
    }
    if (width > VT_MAX_SNAPSHOT_PX || height > VT_MAX_SNAPSHOT_PX) {
      return { bail: `group '${g.name}' exceeds ${VT_MAX_SNAPSHOT_PX}px (${width}x${height})` };
    }
    const matrix = parseUsedMatrix(g.transform);
    if (!matrix) return { bail: `unreadable transform for '${g.name}' (${g.transform})` };

    const leaves = [];
    // ── The INVARIANT CROSS-FADE branch (wave-39 lane A4) ────────────────
    // classifyStabilityDrift proved from computed style that this group's
    // leaves are the UA's complementary plus-lighter pair; its doc comment
    // carries the algebra that collapses the pair to `α·C`, independent of the
    // animation position. What is left is the one clause pixels alone can
    // answer: the two snapshots must actually BE the same image. Both are
    // measured with opacity forced to 1 by the isolation sheet, so the solves
    // are of the two BITMAPS and are not themselves time-dependent.
    if (crossFade.has(g.name)) {
      const so = solved[`${g.name}|old`], sn = solved[`${g.name}|new`];
      for (const [which, s] of [['old', so], ['new', sn]]) {
        if (!s) return { bail: `missing snapshot solve for ${g.name}/${which}` };
        if (s.error) return { bail: `snapshot solve failed for ${g.name}/${which}: ${s.error}` };
        if (!s.uniform && !s.two) {
          // THE RASTER REFUSAL, same boundary as the single-leaf path.
          return { bail: `non-uniform-snapshot ${g.name}/${which} (${s.distinct} colours, ${s.coverage}% flat)` };
        }
      }
      // Identical means the two solves ship the same BOXES — rect, colour and
      // alpha, including a two-colour sub-rect when there is one. A pair that
      // differs anywhere is a genuine mid-flight cross-fade whose composite
      // moves with p, and there is no frozen state to ship.
      const oldBoxes = snapshotBoxes(so), newBoxes = snapshotBoxes(sn);
      if (JSON.stringify(oldBoxes) !== JSON.stringify(newBoxes)) {
        return { bail: `cross-fade-not-invariant '${g.name}' (old ${JSON.stringify(oldBoxes)} vs new ${JSON.stringify(newBoxes)})` };
      }
      // ONE box per solved region for the pair: the sum of the two
      // premultiplied layers is exactly the snapshot, so the leaves' own
      // opacities are already accounted for and only the chain ABOVE them is
      // folded in.
      const chain = crossFadeChainOpacity(g);
      const chainFold = chain < 1 && Number.parseFloat(g.opacity) === 1 ? chain : 1;
      for (const box of oldBoxes) {
        leaves.push({
          which: 'cross-fade',
          // Provenance for the batch log only (see the `shape` roll-up in
          // viewTransitionBakeFixture); absent when the solve was uniform, so
          // the wave-38/39 leaf shape is untouched on that path.
          ...(so.two ? { shape: so.two.kind } : {}),
          props: leafBoxProps(box, chainFold),
        });
      }
    }

    // Which leaves actually put ink on the canvas. A group already handled by
    // the cross-fade branch is skipped here — its pair is one box, not two.
    const painted = crossFade.has(g.name)
      ? []
      : ['old', 'new'].filter((which) => leafPainted(g, g[which]));
    for (const which of painted) {
      const leaf = g[which];
      // ── The plus-lighter question, answered from the compositing algebra ──
      // The UA sheet gives BOTH leaves `mix-blend-mode: plus-lighter` so the
      // cross-fade sums to 1 instead of dipping; measured, it is on ~every
      // leaf in this section. It is NOT an obstacle when the leaf is alone in
      // its image-pair, and that is provable rather than hopeful:
      // `::view-transition-image-pair` carries `isolation: isolate` (UA
      // sheet — confirmed on the live tree), so the group's backdrop starts
      // at TRANSPARENT BLACK, and the CSS Compositing §"simple alpha
      // compositing" formula
      //     Co = (1−αb)·αs·Cs + αb·αs·B(Cb,Cs) + (1−αs)·αb·Cb
      // collapses to `Co = αs·Cs` for EVERY separable blend function when
      // αb = 0. One painted leaf therefore renders identically to `normal`,
      // whatever B is.
      // With BOTH leaves painted the second one blends against the first and
      // the result is a genuine per-pixel function of two layers — a flat box
      // cannot carry it, so the whole test bails.
      if (leaf.mixBlendMode && leaf.mixBlendMode !== 'normal' && painted.length > 1) {
        return { bail: `mix-blend-mode '${leaf.mixBlendMode}' with both leaves painted on '${g.name}'` };
      }
      const s = solved[`${g.name}|${which}`];
      if (!s) return { bail: `missing snapshot solve for ${g.name}/${which}` };
      if (s.error) return { bail: `snapshot solve failed for ${g.name}/${which}: ${s.error}` };
      if (!s.uniform && !s.two) {
        // THE RASTER REFUSAL — see the module banner. A snapshot the
        // TWO-COLOUR fit could describe (`s.two`) is not a raster and is
        // emitted below as its measured rectangles; everything else still
        // takes the whole test back to the static fixture.
        return { bail: `non-uniform-snapshot ${g.name}/${which} (${s.distinct} colours, ${s.coverage}% flat)` };
      }
      if (!s.rect) continue;                 // nothing painted — emit no box
      // The chain's opacity, folded onto every emitted box (see
      // leafEffectiveOpacity). The group carries its own below, so this is the
      // image-pair × leaf half.
      const eff = leafEffectiveOpacity(g, leaf);
      const fold = eff < 1 && Number.parseFloat(g.opacity) === 1 ? eff : 1;
      // ONE box for a uniform snapshot (the wave-38 shape, byte-for-byte);
      // the dominant colour's complement pieces plus the sub-rect for a
      // two-colour one. snapshotBoxes is the single place that decides.
      for (const box of snapshotBoxes(s)) {
        leaves.push({
          which,
          ...(s.two ? { shape: s.two.kind } : {}),
          props: leafBoxProps(box, fold),
        });
      }
    }
    if (!leaves.length) continue;            // an entirely invisible group

    boxes.push({
      name: g.name,
      bounds: transformedBounds(left, top, width, height, matrix),
      orphan: g.documentIndex < 0,
      props: {
        position: 'absolute',
        left:   px(left),
        top:    px(top),
        width:  px(width),
        height: px(height),
        'box-sizing': 'border-box',
        // The used matrix, verbatim. This is the value the whole bake exists
        // to deliver — it is the ONE number no static parse of the source can
        // produce, because it is the transition's own interpolation between
        // the old and new snapshot geometries.
        transform: g.transform,
        'transform-origin': g.transformOrigin,
        ...(Number.parseFloat(g.opacity) < 1 ? { opacity: String(r2(Number.parseFloat(g.opacity))) } : {}),
      },
      leaves,
    });
  }
  if (!boxes.length) return { bail: 'no painted view-transition content' };

  // Ambiguous stacking: an orphan name (its element is gone) that overlaps
  // any other painted group has an unknowable z-order. See SCOPE BOUNDARY.
  for (let i = 0; i < boxes.length; i++) {
    if (!boxes[i].orphan) continue;
    for (let j = 0; j < boxes.length; j++) {
      if (i === j) continue;
      if (boundsOverlap(boxes[i].bounds, boxes[j].bounds)) {
        return { bail: `ambiguous paint order: '${boxes[i].name}' has no document position and overlaps '${boxes[j].name}'` };
      }
    }
  }

  return {
    plan: {
      // The `::view-transition` backdrop: `position: fixed; inset: 0` in the
      // UA sheet, i.e. the snapshot containing block. Its own background is
      // author-settable (`::view-transition { background: pink }` is this
      // section's commonest single idiom) and paints UNDER every group.
      root: {
        width: walk.scb.width, height: walk.scb.height,
        background: walk.viewTransitionBackground,
        canvasBackground: walk.canvasBackground,
      },
      boxes,
    },
  };
}

// ── Pure merge: bake plan → fixture ─────────────────────────────────────────

/**
 * Apply a plan to a fixture in place; returns the number of components
 * written.
 *
 * THE PAGE'S OWN BOXES ARE HIDDEN, all of them. That is not a shortcut — it
 * is what the spec says happens: with `:root` captured (the plan requires a
 * root group), the whole document is painted INTO the root snapshot and the
 * live boxes are not painted at all (css-view-transitions-1 §"the captured
 * element is not painted"). Anything the page still shows comes back through
 * the root group's own leaf box, which this plan carries.
 *
 * `frameRing` (optional, wave-41) is frameRingColor's answer for the settled
 * page: a CSS colour the composed canvas FRAME must show, or null/absent for
 * the default. Non-null stamps it on the `_role: 'body-root'` component —
 * the ONE channel all three composed canvases read their background from —
 * creating that component when the static extraction minted none (a page
 * with no body/html-scoped CSS has no body-root, yet its `::view-transition`
 * backdrop still reaches the viewport edge). Absent keeps every existing
 * caller and fixture byte-identical.
 */
export function applyViewTransitionBakePlan(fixture, stem, plan, frameRing = null) {
  let written = 0;
  // 1. Retire the live document. `display: none` (not `visibility: hidden`)
  //    so the hidden boxes also stop contributing height to the composed
  //    canvas — the pseudo tree is anchored at the canvas origin below and
  //    must not be pushed down by a box the browser is not painting either.
  for (const cmp of Object.values(fixture.components ?? {})) {
    if (!cmp || typeof cmp !== 'object') continue;
    (cmp.properties ??= {}).display = 'none';
    written++;
  }
  // 2. The pseudo tree, as ONE positioned subtree at the canvas origin. A
  //    single wrapper (rather than N top-level components) is what gives the
  //    absolutely positioned groups a containing block whose origin is the
  //    snapshot containing block's, on every runtime, without depending on
  //    how each harness lays out a top-level component list.
  const rootId = `${stem}__vt`;
  const rootProps = {
    position: 'relative',
    left: '0px', top: '0px',
    width:  px(plan.root.width),
    height: px(plan.root.height),
    'box-sizing': 'border-box',
    // The pseudo tree is clipped to the snapshot containing block: a group
    // transformed off-viewport must not grow the composed canvas.
    overflow: 'hidden',
  };
  // The document CANVAS background (root-element background, or the body's
  // when the root's is transparent — CSS Backgrounds §2.11.2). It paints
  // BELOW the top layer, so it survives the transition and must be restated
  // here now that every live box is `display: none`.
  if (plan.root.canvasBackground) rootProps['background-color'] = plan.root.canvasBackground;
  const vtRoot = {
    id: rootId,
    properties: rootProps,
    children: {},
    _lossy: true,
    _lossyReasons: [VT_BAKE_LOSSY_REASON],
  };
  let i = 0;
  // 2a. The ::view-transition backdrop box, first child so it paints under
  //     every group (the runtimes paint a children map in insertion order).
  if (plan.root.background) {
    const id = `${rootId}__${i++}`;
    vtRoot.children[id] = {
      id,
      properties: {
        position: 'absolute', left: '0px', top: '0px',
        width: px(plan.root.width), height: px(plan.root.height),
        'box-sizing': 'border-box',
        'background-color': plan.root.background,
      },
    };
    written++;
  }
  // 2b. One box per painted group, in paint order, each holding its painted
  //     leaves.
  for (const box of plan.boxes) {
    const id = `${rootId}__${i++}`;
    const node = { id, properties: box.props, children: {} };
    let j = 0;
    for (const leaf of box.leaves) {
      const lid = `${id}__${j++}`;
      node.children[lid] = { id: lid, properties: leaf.props };
      written++;
    }
    vtRoot.children[id] = node;
    written++;
  }
  (fixture.components ??= {})[rootId] = vtRoot;

  // 2c. The frame-ring stamp (wave-41; see frameRingColor for the measured
  //     defect). The composed canvases paint their 16px frame from the
  //     body-root component's background — a PROPERTY read that step 1's
  //     `display: none` deliberately does not disturb — so the settled ring
  //     colour is delivered by writing exactly that property.
  if (frameRing) {
    // The static extraction mints at most one body-root (`<stem>__body`,
    // extract-fixture.mjs); find it by ROLE, not id, because the role is the
    // contract the canvases resolve by.
    const bodyRoot = Object.values(fixture.components)
      .find((c) => c && typeof c === 'object' && c._role === 'body-root');
    if (bodyRoot) {
      // Override, never merge: the ring IS the visible canvas colour of the
      // settled state (the author's own body background sits UNDER the
      // top-layer backdrop whenever the two differ, so the ring wins).
      (bodyRoot.properties ??= {})['background-color'] = frameRing;
    } else {
      // No body-root minted — the page had no body/html-scoped CSS. Create
      // the minimal one: role marker (the canvases' lookup key), the ring as
      // its background, and `display: none` so the component paints nothing
      // itself, exactly like every step-1-retired box. Same id shape the
      // extractor uses, which cannot collide (only the extractor mints it,
      // and it did not).
      fixture.components[`${stem}__body`] = {
        properties: { display: 'none', 'background-color': frameRing },
        _role: 'body-root',
        // Honesty stamp: this component exists only because the bake
        // measured the ring — same provenance the wrapper subtree carries.
        _lossy: true,
        _lossyReasons: [VT_BAKE_LOSSY_REASON],
      };
    }
    written++;
  }

  // 3. Honesty stamps: the delivery record plus the fixture-level roll-up.
  fixture._wpt ??= {};
  fixture._wpt.viewTransitionBaked = true;
  if ('lossy' in fixture._wpt) {
    fixture._wpt.lossy = true;
    fixture._wpt.lossyReasons =
      [...new Set([...(fixture._wpt.lossyReasons ?? []), VT_BAKE_LOSSY_REASON])];
  }
  return written;
}

// ── In-page walker ──────────────────────────────────────────────────────────
//
// Runs inside page.evaluate — dependency-free, everything arrives through the
// serialized params object. Unlike post-load/bidi this walker does NOT need
// the static traversal identity: the bake hides every live component wholesale
// and writes an entirely synthetic subtree, so there is no per-element overlay
// to mis-align. (That is also why it can run on a DOM the transition's own
// callback has already mutated — the one condition that makes the mapping
// cross-check those bakes rely on unusable here.)
export function inPageVtWalker(params) {
  const { pseudoKinds } = params;
  const de = document.documentElement;
  const TRANSPARENT = 'rgba(0, 0, 0, 0)';

  // ── Name enumeration, from three independent sources ──────────────────────
  const names = new Set(['root']);
  // (a) the UA group animations — the authoritative list while they exist.
  for (const a of document.getAnimations()) {
    const p = a.effect && a.effect.pseudoElement;
    if (!p) continue;
    const m = /^::view-transition-[a-z-]+\((.+)\)$/.exec(p);
    if (m && m[1] !== '*') names.add(m[1]);
  }
  // (b) every element's used `view-transition-name` — catches a test that
  //     killed the UA animations with `animation: unset`.
  const documentIndex = new Map();
  let idx = 0;
  for (const el of document.querySelectorAll('*')) {
    const n = getComputedStyle(el).viewTransitionName;
    if (n && n !== 'none') { names.add(n); if (!documentIndex.has(n)) documentIndex.set(n, idx); }
    idx++;
  }
  // (c) names written into the document's own `::view-transition-*(NAME)`
  //     selectors — catches an OLD-only name whose element the callback
  //     removed and whose animation is `unset`.
  for (const sheet of Array.from(document.styleSheets)) {
    let rules;
    try { rules = sheet.cssRules; } catch { continue; }  // cross-origin sheet
    for (const rule of Array.from(rules ?? [])) {
      const sel = rule.selectorText;
      if (!sel) continue;
      // The argument is `<pt-name-selector> <pt-class-selector>*` (css-view-
      // transitions-2 §"Selecting a pseudo"), e.g. `target.cls` or `*.cls`.
      // Only the NAME part identifies a group, so the class suffix is
      // stripped and a `*` wildcard contributes nothing.
      for (const m of sel.matchAll(/::view-transition-[a-z-]+\(([^)]+)\)/g)) {
        const name = m[1].trim().split('.')[0].trim();
        if (name && name !== '*') names.add(name);
      }
    }
  }

  const read = (sel) => {
    const cs = getComputedStyle(de, sel);
    return {
      width: cs.width, height: cs.height, left: cs.left, top: cs.top,
      transform: cs.transform, transformOrigin: cs.transformOrigin,
      opacity: cs.opacity, visibility: cs.visibility,
      objectFit: cs.objectFit, mixBlendMode: cs.mixBlendMode,
      backgroundColor: cs.backgroundColor,
    };
  };

  const groups = [];
  for (const name of names) {
    const g = read(`::view-transition-${pseudoKinds.group}(${name})`);
    // css-view-transitions-2 NESTED-TREE probe (wave-41): a name whose
    // `::view-transition-group-children` pseudo has REAL used px dimensions
    // is a nesting CONTAINER — its child groups live inside it, not against
    // the snapshot containing block. Measured in this Chromium: the pseudo
    // answers `auto` for every flat-tree name (root of group-children-sizing)
    // and `200px` for the nesting `clipper`, so the same px-discriminator
    // groupPseudoExists uses works unchanged. Width/height only — existence
    // is the whole question.
    const gc = getComputedStyle(de, `::view-transition-group-children(${name})`);
    groups.push({
      name,
      documentIndex: documentIndex.has(name) ? documentIndex.get(name) : -1,
      width: g.width, height: g.height, left: g.left, top: g.top,
      transform: g.transform, transformOrigin: g.transformOrigin,
      opacity: g.opacity, visibility: g.visibility,
      groupChildren: { width: gc.width, height: gc.height },
      imagePair: read(`::view-transition-${pseudoKinds.imagePair}(${name})`),
      old: read(`::view-transition-${pseudoKinds.old}(${name})`),
      new: read(`::view-transition-${pseudoKinds.new}(${name})`),
    });
  }

  const vt = getComputedStyle(de, '::view-transition');
  // Canvas background: the root element's, else the body's (CSS Backgrounds
  // §2.11.2 propagation). The canvas paints below the top layer, so it is the
  // one piece of the live document that survives a full capture.
  // The canvas frame this pipeline injects declares `background: CANVAS_BG`
  // at ZERO specificity, so a test that declares none reads back the frame's
  // own white. That value belongs to the CAPTURE pipeline, never to a
  // fixture (the same rule CANVAS_FRAME_STYLE_ID exists to enforce on the
  // structure serializer), so it is filtered out here by comparing against
  // the frame's colour rather than by hoping no test paints white.
  const htmlBg = getComputedStyle(de).backgroundColor;
  const bodyBg = document.body ? getComputedStyle(document.body).backgroundColor : TRANSPARENT;
  const canvasBg = htmlBg !== TRANSPARENT ? htmlBg : bodyBg;
  const frameBg = params.canvasFrameBackground;

  // Is the ROOT ELEMENT captured RIGHT NOW? The UA sheet gives `:root` the
  // name `root`, and while it holds one the whole document is painted into
  // that snapshot instead of in place — which is the premise the bake's
  // "retire every live component" step rests on. A test that clears it
  // mid-transition (`documentElement.style.viewTransitionName = 'none'`,
  // root-to-shared-animation-end) keeps a root GROUP from the old capture
  // while the live document paints again, and that combination is not
  // serializable here.
  const rootName = getComputedStyle(de).viewTransitionName;

  return {
    active: de.matches(':active-view-transition'),
    rootCaptureName: (rootName && rootName !== 'none') ? rootName : null,
    groups,
    viewTransitionBackground: vt.backgroundColor === TRANSPARENT ? null : vt.backgroundColor,
    canvasBackground: (canvasBg === TRANSPARENT || canvasBg === frameBg) ? null : canvasBg,
    // The snapshot containing block — the viewport, which under this
    // pipeline's canvas contract is exactly REF_RENDER_WIDTH wide.
    scb: { width: window.innerWidth, height: window.innerHeight },
  };
}

/** The isolation stylesheet for ONE leaf: everything else in the tree is
 *  removed from the paint, the surviving chain is forced fully visible and
 *  untransformed at the origin, and `::view-transition` becomes the opaque
 *  backdrop the alpha solve reads against.
 *
 *  Author `!important` beats an animation's declaration (CSS Cascade §6.2 —
 *  important author rules sit ABOVE animations in the cascade order), which is
 *  what lets a paused UA keyframe be overridden here without touching it.
 *  Among the important rules the named selector out-specifies the `(*)` one
 *  (a view-transition name argument has type-selector specificity, `*` has
 *  none), so the "hide all, then re-show one" pair resolves the right way.
 *
 *  THE PAGE'S OWN BACKGROUND IS DELIBERATELY NOT TOUCHED (wave-39 lane A4).
 *  Through wave 38 this sheet opened with
 *      :where(html, body) { background: <backdrop> !important; }
 *  as belt-and-braces against a window that had slid past the fixed
 *  `::view-transition` box. It was unnecessary AND it silently destroyed a
 *  whole class of measurement:
 *
 *  UNNECESSARY — `::view-transition` is `position: fixed; inset: 0` on the
 *  snapshot containing block, i.e. the whole viewport; the caller refuses any
 *  isolation window larger than the viewport, and the rules below pin the
 *  group at `left/top: 0` and translate it by a NON-negative offset. The clip
 *  therefore always lies inside an opaque backdrop box.
 *
 *  HARMFUL — `::view-transition-new(<name>)` is a LIVE representation of the
 *  captured element, not a frozen bitmap (css-view-transitions-1 §"the new
 *  content is a live representation"). Repainting html/body with the backdrop
 *  repainted the leaf ITSELF, so the two composites came back identical and
 *  the alpha solve read α = 0 — "nothing painted" — for every live `new` leaf.
 *  Measured on three tests both ways (_diag39/A4/iso-current.json vs
 *  iso-noPageBg.json): with the rule, `root/new` solved to `rect: null`;
 *  without it, to the same 358×568 opaque white the frozen `root/old` leaf
 *  already solved to. A leaf that solves to nothing emits no box (see
 *  planViewTransitionBake), so the rule was DELETING the new-content half of
 *  the pseudo tree from every baked fixture that had one. */
export function isolationCss(name, leaf, backdrop, offset = { x: 0, y: 0 }) {
  return `
    /* The opaque backdrop the alpha solve reads against. The PAGE's own
       background is deliberately left alone - see the banner above. */
    ::view-transition { background: ${backdrop} !important; }
    ::view-transition-group(*) { opacity: 0 !important; }
    ::view-transition-group(${name}) {
      opacity: 1 !important; visibility: visible !important;
      transform: translate(${r2(offset.x)}px, ${r2(offset.y)}px) !important;
      left: 0 !important; top: 0 !important;
    }
    ::view-transition-image-pair(${name}) {
      visibility: visible !important; opacity: 1 !important;
      mix-blend-mode: normal !important;
    }
    ::view-transition-old(*), ::view-transition-new(*) { opacity: 0 !important; }
    ::view-transition-${leaf}(${name}) {
      opacity: 1 !important; visibility: visible !important;
      mix-blend-mode: normal !important;
    }
  `;
}

// ── Browser plumbing ────────────────────────────────────────────────────────

// One shared browser per process (a launch per test would dominate wall time).
// Lazily created; callers MUST closeViewTransitionBakeBrowser() before exit.
let _browser = null;
async function getBrowser() {
  if (_browser) return _browser;
  _browser = await puppeteer.launch({
    headless: 'new',                 // same mode as browser-ref / post-load
    args: BROWSER_LAUNCH_ARGS,       // the shared flag contract
    protocolTimeout: 300_000,        // long-run safety margin
  });
  return _browser;
}

/** Close the shared browser (no-op when never launched). */
export async function closeViewTransitionBakeBrowser() {
  if (_browser) { await _browser.close(); _browser = null; }
}

/**
 * Drop the shared browser after it has WEDGED, so the next test starts from a
 * fresh one. MEASURED need (_diag38/N5/bake3.log): once one page provoked
 * `Runtime.callFunctionOn timed out`, every remaining test in that batch threw
 * the same error — 7 consecutive hard failures from a single sick renderer.
 * A wedged browser is exactly the one whose `close()` can itself hang, so the
 * handle is dropped FIRST (making the next getBrowser() launch a new one) and
 * the close is fired off unawaited with its rejection swallowed; a leaked
 * Chromium is a bounded cost, a hung batch is not.
 */
function discardWedgedBrowser() {
  const dead = _browser;
  _browser = null;
  if (dead) dead.close().catch(() => {});
}

/** Drive one test page to its frozen transition state under the ref canvas
 *  contract. Returns the open page (caller closes) or throws. */
async function openFrozenPage(browser, testAbs) {
  const page = await browser.newPage();
  try {
    // Viewport BEFORE goto so the page lays out at the pipeline's canvas —
    // the ref's CONTENT space (358×568), not puppeteer's 800×600 default and
    // not the 390×600 outer image (the 16 px frame is applied in image space).
    await page.setViewport({
      width: REF_RENDER_WIDTH, height: REF_RENDER_MIN_HEIGHT, deviceScaleFactor: 1,
    });
    // Document-start installs, in order: the /common/ helpers the corpus
    // mirror does not carry, then the canvas frame (see
    // canvasFrameInstallerScript for why it cannot wait for `load` here).
    await page.evaluateOnNewDocument(REFTEST_WAIT_SHIM);
    await page.evaluateOnNewDocument(
      canvasFrameInstallerScript(CANVAS_FRAME_STYLE_ID, await canvasFrameCss()));
    await page.goto('file://' + encodeURI(testAbs), { waitUntil: 'load', timeout: 30_000 });
    // fonts.ready + double-rAF: the data-URI Inter faces load async and a
    // geometry read before the relayout with the loaded face would encode
    // fallback-font advances (the corpus-v4.1 font-pin lesson).
    await page.evaluate(() => document.fonts.ready.then(
      () => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))),
    ));
    return page;
  } catch (err) {
    await page.close().catch(() => {});
    throw err;
  }
}

/** Wait for the page's own `takeScreenshot()` to clear `reftest-wait`. A page
 *  that never carried the class is settled by definition. */
async function waitForReftestSettle(page, timeoutMs) {
  return page.evaluate(async (deadlineMs) => {
    const de = document.documentElement;
    if (!de.classList.contains('reftest-wait')) return true;
    const end = Date.now() + deadlineMs;
    while (de.classList.contains('reftest-wait') && Date.now() < end) {
      await new Promise((r) => setTimeout(r, 25));
    }
    return !de.classList.contains('reftest-wait');
  }, timeoutMs);
}

/**
 * Run the view-transition bake for one test and, on success, bake the result
 * into `fixture` in place. Returns `{ status, reason?, groups?, leaves? }`
 * with status ∈ 'baked' | 'skipped' | 'declined' | 'bailed':
 *   - 'skipped'  — the static trigger did not fire; no browser, no edit;
 *   - 'declined' — the shared top-layer pre-check says the rendered state is
 *                  unrepresentable; no browser was launched;
 *   - 'bailed'   — a scope boundary fired (see the module banner); the fixture
 *                  is left byte-identical;
 *   - 'baked'    — the settled pseudo tree was delivered, fixture stamped.
 *
 * THE BOUNDED RE-DRIVE (wave-45 lane X5). Two gates in a row lost exactly one
 * web pass each to a transient, non-reproducible drive fault in THIS module —
 * the recurring web −1:
 *   - wave-41 fullcap2: pseudo-with-classes-match-wildcard bailed `snapshot
 *     overflows its 200x100 box (183344px of ink outside it)` — the crop
 *     probe read the ENTIRE window complement as ink because one isolation
 *     composite raced the restyle and showed the un-isolated page; 4/4
 *     re-drives baked clean (fullcap ran the identical tree and baked it).
 *   - wave-44 final: fractional-box-old errored `Runtime.callFunctionOn timed
 *     out` (extract.log:36) — a one-off protocol wedge; the recycled browser
 *     baked the very next test, and every re-drive of fractional-box-old
 *     bakes clean (wave-45 X5: 12/12 clean, byte-identical fixtures).
 * Both flakes cost the bake (bail-to-static → web-ref 1.0 → ~0.975 → a lost
 * pass) and both re-drive clean, so this wrapper spends at most
 * VT_MAX_REDRIVES fresh page drives before letting the outcome stand:
 *   - a drive whose crop probe read the DIRTY signature (see
 *     isDirtyOverflowProbe — the whole-complement reading no real ink can
 *     produce) is re-driven once, loudly;
 *   - a drive that THREW is re-driven once, loudly, on a fresh browser (the
 *     throwing drive already recycled the wedged one); an error that repeats
 *     stays an error — the caller's tally and bail-to-static contract are
 *     unchanged.
 * CLEAN DRIVES ARE UNTOUCHED: no dirty signature, no throw → the first
 * drive's result returns straight through this loop, byte-for-byte the
 * pre-wave-45 behaviour (verified: 12/12 repeated drives of
 * fractional-box-old + pseudo-with-classes-match-wildcard hash-identical
 * before and after this change). Genuine bails are also untouched: their
 * probe readings sit orders of magnitude below the dirty signature, and even
 * a pathological page that trips it simply re-measures the same state and
 * bails identically one drive later.
 */
export async function viewTransitionBakeFixture(fixture, testRel) {
  const testAbs = join(WPT_DIR, testRel);
  const html = await fs.readFile(testAbs, 'utf8');
  // Cheap static gates first — neither needs a browser.
  const trigger = viewTransitionBakeTrigger(html);
  if (!trigger) return { status: 'skipped', reason: 'no view-transition trigger' };
  const decline = postLoadDecline(html);
  if (decline) return { status: 'declined', reason: decline };

  // The re-drive loop proper. Unbounded `for` with an in-loop bound, so the
  // final iteration's outcome (or error) ALWAYS escapes — there is no path
  // that retries forever and none that swallows the last result.
  for (let drive = 0; ; drive++) {
    let outcome;
    try {
      outcome = await driveViewTransitionBake(fixture, testRel, testAbs, trigger);
    } catch (err) {
      // TRANSIENT DRIVE FAULT — the wave-44 signature. The drive already
      // recycled the wedged browser (see its catch), so the retry runs on a
      // freshly launched one. Logged to stderr so a gate's extract.log shows
      // every retry next to the test it saved (or failed to).
      if (drive < VT_MAX_REDRIVES) {
        console.warn(`view-transition-bake: re-drive ${drive + 1}/${VT_MAX_REDRIVES} for ${testRel} — ` +
          `transient drive fault (${err.message ?? err}); browser recycled ` +
          `(wave-44 fractional-box-old precedent: one-off wedge, clean on re-drive)`);
        continue;
      }
      // Reproducible → stays an error, exactly as before wave-45: the CLI
      // tallies it and extract-fixture.mjs's batch path writes the static
      // pair (the bail-to-static contract pinned in the unit tests).
      throw err;
    }
    // DIRTY CROP PROBE — the wave-41 signature. Non-null only when a solve's
    // overflow reading was classified as "un-isolated composite" rather than
    // ink (isDirtyOverflowProbe); such a drive always bails, so retrying can
    // only ever trade a flake-bail for the truth (never overwrite a bake).
    if (outcome.dirtyProbe && drive < VT_MAX_REDRIVES) {
      console.warn(`view-transition-bake: re-drive ${drive + 1}/${VT_MAX_REDRIVES} for ${testRel} — ` +
        `dirty crop probe (${outcome.dirtyProbe}): a whole-complement reading is a ` +
        `transient un-isolated composite (wave-41 fullcap2 precedent), not ink overflow`);
      continue;
    }
    // Strip the internal dirty-probe channel before the result reaches the
    // CLI / extract-fixture.mjs — the retry is THIS function's concern, and
    // the callers' outcome vocabulary must not grow a key they never read.
    const { dirtyProbe, ...result } = outcome;
    return result;
  }
}

/**
 * ONE full drive of the bake — the pre-wave-45 viewTransitionBakeFixture
 * body, verbatim, plus the dirty-probe channel: opens the page, settles,
 * walks, solves, plans, and on success applies the plan to `fixture` in
 * place. The fixture is mutated ONLY on the 'baked' return (the plan apply is
 * the final synchronous step), so a re-drive of any bailed or thrown drive
 * always starts from an unmutated fixture.
 */
async function driveViewTransitionBake(fixture, testRel, testAbs, trigger) {
  const browser = await getBrowser();
  let page;
  try {
    page = await openFrozenPage(browser, testAbs);
  } catch (err) {
    // A failure to even open the page is a browser-health signal, not a scope
    // boundary. Recycle so the NEXT test is not condemned by this one, then
    // rethrow — an error must stay an error (the CLI tallies it, and
    // extract-fixture.mjs's batch path still writes the static fixture).
    discardWedgedBrowser();
    throw err;
  }
  try {
    const settled = await waitForReftestSettle(page, VT_SETTLE_TIMEOUT_MS);
    if (!settled) return { status: 'bailed', reason: 'reftest-wait never cleared' };

    const walkParams = {
      pseudoKinds: { group: 'group', imagePair: 'image-pair', old: 'old', new: 'new' },
      // The frame's own canvas colour, so the walk can tell an AUTHOR
      // background from the one this pipeline injected. `rgb()` because that
      // is the shape getComputedStyle answers with, whatever the source
      // literal was.
      canvasFrameBackground: hexToComputedRgb(CANVAS_BG),
    };
    // SETTLE STABILITY — two reads VT_STABILITY_DELAY_MS apart must be
    // IDENTICAL. A transition still interpolating has no state to serialize.
    const first = await page.evaluate(inPageVtWalker, walkParams);
    await page.evaluate((ms) => new Promise((r) => setTimeout(r, ms)), VT_STABILITY_DELAY_MS);
    const walk = await page.evaluate(inPageVtWalker, walkParams);
    // The drift is CLASSIFIED, not merely detected: a tree whose only movement
    // is the UA's complementary cross-fade still has a frozen rendered state
    // (see classifyStabilityDrift / isComplementaryCrossFade). Everything else
    // is the wave-38 bail, byte-for-byte.
    const { hard: drift, crossFade } = classifyStabilityDrift(first, walk);
    if (drift) {
      return {
        status: 'bailed',
        reason: `transition-not-frozen (${drift.path}: ${JSON.stringify(drift.a)} → ${JSON.stringify(drift.b)})`,
      };
    }
    if (!walk.active) return { status: 'bailed', reason: 'no-active-transition' };
    const crossFadeSet = new Set(crossFade);

    // Which leaves need a snapshot solve? Only the painted ones — an isolation
    // pair costs two screenshots, and a hidden decoy group (this section's
    // commonest idiom) must not pay for them.
    const wanted = [];
    for (const g of walk.groups) {
      if (!groupPseudoExists(g)) continue;
      const w = Number.parseFloat(g.width), h = Number.parseFloat(g.height);
      if (!(w > 0 && h > 0) || w > VT_MAX_SNAPSHOT_PX || h > VT_MAX_SNAPSHOT_PX) continue;
      for (const which of ['old', 'new']) {
        // A cross-fade pair needs BOTH solves even when one side's animated
        // opacity has momentarily dipped below the paint floor: the isolation
        // sheet forces opacity 1, so the solve measures the BITMAP, and it is
        // the two bitmaps' equality that the invariance proof rests on.
        if (crossFadeSet.has(g.name) || leafPainted(g, g[which])) {
          wanted.push({
            name: g.name, which, fit: g[which].objectFit,
            window: isolationWindow(g, g[which]),
          });
        }
      }
    }
    const viewport = page.viewport();
    const solved = {};
    // Dirty-probe channel (wave-45 X5): overflow readings the classifier
    // calls "un-isolated composite" rather than ink. The first one is enough
    // — one dirty read already proves the measurement session raced the
    // restyle — but the push is per-leaf so the log names the leaf that read
    // dirty, not just the test.
    const dirtyProbes = [];
    for (const item of wanted) {
      const win = item.window;
      const key = `${item.name}|${item.which}`;
      if (!VT_CONTAINED_OBJECT_FITS.includes(String(item.fit))) {
        // See VT_CONTAINED_OBJECT_FITS: the snapshot may paint outside the box
        // the window measures, so the rect would be a cropped lie.
        solved[key] = { error: `object-fit '${item.fit}' may overflow the leaf box` };
        continue;
      }
      if (win.width > viewport.width || win.height > viewport.height) {
        // See isolationWindow: outside the fixed `::view-transition` box there
        // is no backdrop to solve against.
        solved[key] = { error: `isolation window ${win.width}x${win.height} exceeds the ${viewport.width}x${viewport.height} viewport` };
        continue;
      }
      const shots = [];
      for (const backdrop of VT_ISOLATION_BACKDROPS) {
        await page.evaluate(({ id, css }) => {
          let s = document.getElementById(id);
          if (!s) { s = document.createElement('style'); s.id = id; document.head.appendChild(s); }
          s.textContent = css;
        }, {
          id: VT_ISOLATION_STYLE_ID,
          css: isolationCss(item.name, item.which, backdrop, win.offset),
        });
        // One frame for the restyle to paint before the clip screenshot.
        await page.evaluate(() => new Promise((r) => requestAnimationFrame(
          () => requestAnimationFrame(r))));
        shots.push(await page.screenshot({
          type: 'png',
          // The FULL viewport, not the window. The isolation pins the group at
          // `left/top: 0` and translates it by `win.offset`, so the window is
          // the top-left `win.width`x`win.height` of this image — and the REST
          // of the image is what probeSnapshotOverflow needs to see. Same
          // render either way, so the window's pixels are byte-identical to
          // the wave-38 `clip`.
          clip: { x: 0, y: 0, width: viewport.width, height: viewport.height },
        }));
      }
      // CROP DETECTION before the solve: a snapshot that paints past the union
      // of its group and leaf boxes (a captured element with ink overflow)
      // would otherwise be measured as a smaller, "uniform" box and ship with
      // the overflowing ink silently deleted.
      const over = probeSnapshotOverflow(shots[0], shots[1], win);
      if (over.error) { solved[key] = { error: over.error }; continue; }
      if (over.outside > VT_OVERFLOW_INK_TOLERANCE_PX) {
        // DIRTY-vs-GENUINE (wave-45 X5): a reading at ≥half the window's
        // complement is the wave-41 transient-settle signature (an
        // un-isolated composite reads its ENTIRE complement as ink), not a
        // property of the page — record it so the outer loop can spend its
        // one re-drive. The solve error below is recorded EITHER WAY: this
        // drive's own outcome must stay the honest bail, so a second dirty
        // read on the re-drive bails exactly as wave-41 did.
        if (isDirtyOverflowProbe(over.outside, win, viewport)) {
          dirtyProbes.push(`${key} read ${over.outside}px outside its ${win.width}x${win.height} window`);
        }
        solved[key] = {
          error: `snapshot overflows its ${win.width}x${win.height} box ` +
                 `(${over.outside}px of ink outside it)`,
        };
        continue;
      }
      const s = solveSnapshot(
        cropComposite(shots[0], win.width, win.height),
        cropComposite(shots[1], win.width, win.height));
      // Re-base the measured rect into the GROUP's own coordinates (undo the
      // isolation translate). The two-colour sub-rect is measured in the same
      // window and must move with it — a rebase that skipped it would put the
      // second region's box at the wrong offset on every non-zero-offset
      // window (the twelve `pseudo-with-classes-*` shapes).
      if (s.rect) s.rect = { ...s.rect, x: s.rect.x - win.offset.x, y: s.rect.y - win.offset.y };
      if (s.two) {
        s.two.rect = {
          ...s.two.rect,
          x: s.two.rect.x - win.offset.x,
          y: s.two.rect.y - win.offset.y,
        };
      }
      solved[key] = s;
    }
    // Remove the instrument before anything else reads the page.
    await page.evaluate((id) => { const s = document.getElementById(id); if (s) s.remove(); },
      VT_ISOLATION_STYLE_ID);

    const { bail, plan } = planViewTransitionBake(walk, solved, crossFade);
    // The dirty-probe channel rides the BAIL return only. It cannot ride the
    // baked one: a dirty leaf's solve is an error, `wanted` and the plan
    // consult the same painted/cross-fade predicates over the same walk, so
    // every wanted leaf IS consulted and an error among them always bails —
    // the retry can therefore never re-drive over an applied (mutated)
    // fixture.
    if (bail) return { status: 'bailed', reason: bail, dirtyProbe: dirtyProbes[0] ?? null };
    // The frame-ring sample (wave-41; see frameRingColor). Taken AFTER the
    // plan is accepted so bailed tests never pay the screenshot, and AFTER
    // the isolation sheet was removed above so this is the SETTLED page —
    // the same pixels the ref pipeline's padColorFor frames the ref against.
    const ringShot = await page.screenshot({
      type: 'png',
      // The 358×568 ref-contract viewport, exactly what the ref render pads.
      clip: { x: 0, y: 0, width: viewport.width, height: viewport.height },
    });
    const frameRing = frameRingColor(ringShot);
    const stem = fixtureStem(testRel);
    const written = applyViewTransitionBakePlan(fixture, stem, plan, frameRing);
    return {
      status: 'baked', trigger,
      groups: plan.boxes.length,
      leaves: plan.boxes.reduce((n, b) => n + b.leaves.length, 0),
      // Provenance for the batch log: which groups shipped as a proved
      // time-invariant cross-fade rather than as a single frozen leaf.
      crossFade: crossFade.length,
      // …and which leaves needed the TWO-COLOUR fit, by shape, so a batch log
      // says WHICH geometries the corpus actually contains rather than only
      // how many bails it avoided.
      shapes: plan.boxes.flatMap((b) => b.leaves.map((l) => l.shape).filter(Boolean)),
      // …and the frame-ring stamp when one was delivered (wave-41), so the
      // batch log names the tests whose composed-canvas frame the bake set.
      ...(frameRing ? { frameRing } : {}),
      written,
    };
  } catch (err) {
    // Same recycle discipline for a mid-bake protocol failure (the measured
    // cascade). Deliberately NOT converted into a `bailed` status: a bail is a
    // documented scope boundary this module chose, and dressing a browser
    // fault up as one would hide a broken environment behind 195 plausible
    // declines. It stays an error; only the CASCADE is fixed — though the
    // outer loop in viewTransitionBakeFixture may spend its ONE re-drive on
    // it first (wave-45 X5; the wave-44 gate lost fractional-box-old to
    // exactly one such transient), on the fresh browser this recycle makes.
    discardWedgedBrowser();
    throw err;
  } finally {
    // The page belongs to a browser that may already be discarded above, in
    // which case closing it is both pointless and a second hang risk.
    await page.close().catch(() => {});  // one page per test; the browser is shared
  }
}

// ── CLI ─────────────────────────────────────────────────────────────────────
//
// End-to-end per test: static extraction → view-transition bake →
// writeFixturePair. Skips/declines/bails still WRITE the static pair (that IS
// the bail-to-static contract) and are reported per test.
async function main() {
  const inputs = process.argv.slice(2).filter((a) => !a.startsWith('--'));
  if (inputs.length === 0) {
    console.error('usage: view-transition-bake.mjs <relative-test-path>...');
    console.error('       (paths are repo-relative, e.g. "css/css-view-transitions/auto-name.html")');
    process.exit(1);
  }
  let hardFail = 0;
  const tally = { baked: 0, skipped: 0, declined: 0, bailed: 0 };
  const bails = new Map();
  try {
    for (const rel of inputs) {
      try {
        const result = await extractFixture(rel);            // static pass
        const outcome = await viewTransitionBakeFixture(result.fixture, rel);
        await writeFixturePair(result);                      // write either way
        tally[outcome.status]++;
        if (outcome.status === 'bailed') {
          // Taxonomy key: the reason's leading token, so a batch log rolls up
          // by CLASS rather than by per-test detail.
          const key = String(outcome.reason).split(/[\s(]/)[0];
          bails.set(key, (bails.get(key) ?? 0) + 1);
        }
        console.log(`${outcome.status.padEnd(8)} ${rel}` +
          (outcome.status === 'baked'
            ? ` (${outcome.groups} groups, ${outcome.leaves} leaves` +
              `${outcome.crossFade ? `, ${outcome.crossFade} invariant cross-fade` : ''}` +
              `${outcome.shapes?.length ? `, two-colour ${[...new Set(outcome.shapes)].sort().join('/')}` : ''}` +
              `${outcome.frameRing ? `, frame-ring ${outcome.frameRing}` : ''}` +
              ` — ${outcome.trigger})`
            : ` (${outcome.reason})`));
      } catch (err) {
        hardFail++;
        console.error(`ERROR    ${rel}: ${err.message ?? err}`);
      }
    }
  } finally {
    await closeViewTransitionBakeBrowser();  // never leak the shared browser
  }
  console.log(`view-transition-bake: baked=${tally.baked} skipped=${tally.skipped} ` +
              `declined=${tally.declined} bailed=${tally.bailed} errors=${hardFail}`);
  if (bails.size) {
    console.log('bail taxonomy: ' +
      [...bails.entries()].sort((a, b) => b[1] - a[1]).map(([k, n]) => `${k}=${n}`).join(' '));
  }
  process.exit(hardFail > 0 ? 1 : 0);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((err) => {
    console.error('view-transition-bake: fatal:', err);
    process.exit(1);
  });
}
