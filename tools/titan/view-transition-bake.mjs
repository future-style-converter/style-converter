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
//      sees only the pre-script DOM. REFTEST_WAIT_SHIM below supplies the four
//      helpers at document-start; 165 of 195 then settle inside
//      VT_SETTLE_TIMEOUT_MS and 153 hold an `:active-view-transition`.
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
//   - NO ROOT GROUP. When `:root` is NOT captured the page's own boxes keep
//     painting in place and only the NAMED elements drop out, so a faithful
//     bake would have to hide exactly those components — an element↔component
//     mapping that is guaranteed to have drifted, because the transition's own
//     callback mutated the DOM this walk is reading. 16 tests; refused rather
//     than approximated. (`escaped-name`, one of them, passes today at SSIM
//     1.0000 — bailing keeps it byte-identical and passing.)
//   - NON-UNIFORM SNAPSHOT — the raster refusal above.
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
import {
  BROWSER_LAUNCH_ARGS, canvasFrameCss, CANVAS_BG,
  REF_RENDER_WIDTH, REF_RENDER_MIN_HEIGHT,
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
// The four functions are the complete set the 195 tests call
// (`failIfNot` 172×, `waitForAtLeastOneFrame` 13×, `takeScreenshot`,
// `takeScreenshotDelayed`) and each is the WPT semantic verbatim:
// `takeScreenshot` removes `reftest-wait` from the root — that class removal
// IS this module's settle signal, exactly as it is the WPT runner's.
//
// `failIfNot` diverges in ONE documented way: the WPT harness marks the test
// failed and stops; here it clears the wait class so the drive terminates
// promptly instead of burning VT_SETTLE_TIMEOUT_MS. The state it leaves
// behind still has to survive the `:active-view-transition` gate below, so a
// genuinely unsupported feature bails rather than baking a half-page.
export const REFTEST_WAIT_SHIM = `(function(){
  function takeScreenshot(){ document.documentElement.classList.remove('reftest-wait'); }
  function takeScreenshotDelayed(t){ setTimeout(takeScreenshot, t || 0); }
  function failIfNot(cond){ if (!cond) takeScreenshot(); }
  function waitForAtLeastOneFrame(){
    return new Promise(function(r){ requestAnimationFrame(function(){ requestAnimationFrame(r); }); });
  }
  window.takeScreenshot = takeScreenshot;
  window.takeScreenshotDelayed = takeScreenshotDelayed;
  window.failIfNot = failIfNot;
  window.waitForAtLeastOneFrame = waitForAtLeastOneFrame;
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
    const key = `${rgb[i * 3] >> 1},${rgb[i * 3 + 1] >> 1},${rgb[i * 3 + 2] >> 1},${Math.round(a * 64)}`;
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }
  if (!painted) return { rect: null, rgba: null, uniform: true, distinct: 0, coverage: 0 };
  // The dominant bucket, then its exact representative (first pixel in it).
  let topKey = null, topN = 0;
  for (const [k, c] of counts) if (c > topN) { topN = c; topKey = k; }
  let rep = null;
  for (let i = 0; i < W * H && !rep; i++) {
    if (!alpha[i]) continue;
    const key = `${rgb[i * 3] >> 1},${rgb[i * 3 + 1] >> 1},${rgb[i * 3 + 2] >> 1},${Math.round(alpha[i] * 64)}`;
    if (key === topKey) rep = { r: rgb[i * 3], g: rgb[i * 3 + 1], b: rgb[i * 3 + 2], a: alpha[i] };
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
      if (Math.abs(rgb[i * 3] - rep.r) <= VT_COLOR_TOLERANCE
       && Math.abs(rgb[i * 3 + 1] - rep.g) <= VT_COLOR_TOLERANCE
       && Math.abs(rgb[i * 3 + 2] - rep.b) <= VT_COLOR_TOLERANCE
       && Math.abs(alpha[i] - rep.a) <= 2 / 255) match++;
    }
  }
  return {
    rect,
    rgba: { r: rep.r, g: rep.g, b: rep.b, a: r2(rep.a) },
    uniform: (100 * match / area) >= VT_UNIFORM_DOMINANCE_PCT,
    distinct: counts.size,
    coverage: r2(100 * match / area),
  };
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
 */
export function firstDifference(a, b, path = '') {
  if (a === b) return null;
  const ta = a === null ? 'null' : Array.isArray(a) ? 'array' : typeof a;
  const tb = b === null ? 'null' : Array.isArray(b) ? 'array' : typeof b;
  if (ta !== tb) return { path: path || '<root>', a, b };
  if (ta === 'object' || ta === 'array') {
    const keys = [...new Set([...Object.keys(a), ...Object.keys(b)])];
    for (const k of keys) {
      const d = firstDifference(a[k], b[k], path ? `${path}.${k}` : k);
      if (d) return d;
    }
    return null;
  }
  // The single tolerance (see VT_STABILITY_OPACITY_EPS): an opacity that moved
  // by less than one 8-bit step is the same rendered state. The key NAME is
  // the discriminator, so nothing else can accidentally inherit it.
  if (path.endsWith('opacity')) {
    const na = Number.parseFloat(a), nb = Number.parseFloat(b);
    if (Number.isFinite(na) && Number.isFinite(nb)
        && Math.abs(na - nb) < VT_STABILITY_OPACITY_EPS) return null;
  }
  return { path: path || '<root>', a, b };
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

// ── Pure planning: walk → bake plan ─────────────────────────────────────────

/**
 * Turn the browser walk (+ the per-leaf solved snapshots) into the boxes the
 * fixture will carry, or a bail string.
 *
 * `walk` is what inPageVtWalker returns; `solved` maps `"<name>|<leaf>"` to a
 * solveSnapshot result. Both are plain data, so the whole decision surface is
 * unit-testable without a browser.
 */
export function planViewTransitionBake(walk, solved) {
  if (!walk.active) return { bail: 'no-active-transition' };
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

    // Which leaves actually put ink on the canvas.
    const painted = ['old', 'new'].filter((which) => leafPainted(g, g[which]));
    const leaves = [];
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
      if (!s.uniform) {
        // THE RASTER REFUSAL — see the module banner.
        return { bail: `non-uniform-snapshot ${g.name}/${which} (${s.distinct} colours, ${s.coverage}% flat)` };
      }
      if (!s.rect) continue;                 // nothing painted — emit no box
      leaves.push({
        which,
        props: {
          position: 'absolute',
          left:   px(s.rect.x),
          top:    px(s.rect.y),
          width:  px(s.rect.w),
          height: px(s.rect.h),
          'box-sizing': 'border-box',
          'background-color': snapshotColorCss(s.rgba),
          // The chain's opacity, folded here (see leafEffectiveOpacity). The
          // group carries its own below, so this is the image-pair × leaf half.
          ...(leafEffectiveOpacity(g, leaf) < 1 && Number.parseFloat(g.opacity) === 1
            ? { opacity: String(r2(leafEffectiveOpacity(g, leaf))) }
            : {}),
        },
      });
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
 */
export function applyViewTransitionBakePlan(fixture, stem, plan) {
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
    groups.push({
      name,
      documentIndex: documentIndex.has(name) ? documentIndex.get(name) : -1,
      width: g.width, height: g.height, left: g.left, top: g.top,
      transform: g.transform, transformOrigin: g.transformOrigin,
      opacity: g.opacity, visibility: g.visibility,
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
 *  none), so the "hide all, then re-show one" pair resolves the right way. */
export function isolationCss(name, leaf, backdrop, offset = { x: 0, y: 0 }) {
  return `
    /* The canvas gets the same backdrop as the top-layer box: the fixed
       ::view-transition covers the viewport, but a window whose translate
       has pushed part of the group past its edge would otherwise read the
       page - which solves to "opaque" and floods the measurement. */
    :where(html, body) { background: ${backdrop} !important; }
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
 */
export async function viewTransitionBakeFixture(fixture, testRel) {
  const testAbs = join(WPT_DIR, testRel);
  const html = await fs.readFile(testAbs, 'utf8');
  // Cheap static gates first — neither needs a browser.
  const trigger = viewTransitionBakeTrigger(html);
  if (!trigger) return { status: 'skipped', reason: 'no view-transition trigger' };
  const decline = postLoadDecline(html);
  if (decline) return { status: 'declined', reason: decline };

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
    const drift = firstDifference(first, walk);
    if (drift) {
      return {
        status: 'bailed',
        reason: `transition-not-frozen (${drift.path}: ${JSON.stringify(drift.a)} → ${JSON.stringify(drift.b)})`,
      };
    }
    if (!walk.active) return { status: 'bailed', reason: 'no-active-transition' };

    // Which leaves need a snapshot solve? Only the painted ones — an isolation
    // pair costs two screenshots, and a hidden decoy group (this section's
    // commonest idiom) must not pay for them.
    const wanted = [];
    for (const g of walk.groups) {
      if (!groupPseudoExists(g)) continue;
      const w = Number.parseFloat(g.width), h = Number.parseFloat(g.height);
      if (!(w > 0 && h > 0) || w > VT_MAX_SNAPSHOT_PX || h > VT_MAX_SNAPSHOT_PX) continue;
      for (const which of ['old', 'new']) {
        if (leafPainted(g, g[which])) {
          wanted.push({
            name: g.name, which, fit: g[which].objectFit,
            window: isolationWindow(g, g[which]),
          });
        }
      }
    }
    const viewport = page.viewport();
    const solved = {};
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
          // The isolation pins the group at `left/top: 0` and translates it by
          // `win.offset`, so the window starts at the viewport origin.
          clip: { x: 0, y: 0, width: win.width, height: win.height },
        }));
      }
      const s = solveSnapshot(shots[0], shots[1]);
      // Re-base the measured rect into the GROUP's own coordinates (undo the
      // isolation translate).
      if (s.rect) s.rect = { ...s.rect, x: s.rect.x - win.offset.x, y: s.rect.y - win.offset.y };
      solved[key] = s;
    }
    // Remove the instrument before anything else reads the page.
    await page.evaluate((id) => { const s = document.getElementById(id); if (s) s.remove(); },
      VT_ISOLATION_STYLE_ID);

    const { bail, plan } = planViewTransitionBake(walk, solved);
    if (bail) return { status: 'bailed', reason: bail };
    const stem = fixtureStem(testRel);
    const written = applyViewTransitionBakePlan(fixture, stem, plan);
    return {
      status: 'baked', trigger,
      groups: plan.boxes.length,
      leaves: plan.boxes.reduce((n, b) => n + b.leaves.length, 0),
      written,
    };
  } catch (err) {
    // Same recycle discipline for a mid-bake protocol failure (the measured
    // cascade). Deliberately NOT converted into a `bailed` status: a bail is a
    // documented scope boundary this module chose, and dressing a browser
    // fault up as one would hide a broken environment behind 195 plausible
    // declines. It stays an error; only the CASCADE is fixed.
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
            ? ` (${outcome.groups} groups, ${outcome.leaves} leaves — ${outcome.trigger})`
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
