// tools/titan/view-transition-bake.test.mjs — unit pins for the wave-38
// VIEW-TRANSITION BAKE (view-transition-bake.mjs; the static trigger it gates
// on lives in extract-fixture.mjs and is pinned in section 2 below).
//
// Coverage map (mirrors the module's contract sections):
//   1. tunables + the lossy vocabulary (the exact epsilons, budgets, the
//      contained-object-fit set, the fresh lossy-reason string) and the
//      `viewTransitionBaked` provenance channel, fixture → keyMap → manifest;
//   2. the STATIC TRIGGER — all four signals, comment stripping, and the
//      over-approximation the browser is expected to narrow;
//   3. the computed-style predicates (groupPseudoExists, leafEffectiveOpacity,
//      leafPainted) — the phantom-group discriminator and the decoy-group
//      idiom this section is built from;
//   4. geometry (parseUsedMatrix, transformedBounds, boundsOverlap,
//      isolationWindow) — including the union window the twelve
//      `pseudo-with-classes-*` tests forced;
//   5. the alpha solve (solveSnapshot) on synthesized composites: opaque,
//      semi-transparent, WHITE-vs-TRANSPARENT (the ambiguity two backdrops
//      exist to break), the empty snapshot, non-uniformity, size drift;
//  5c. the wave-40 TWO-COLOUR fit — the shapes it accepts (split band, nested
//      rect, frame-around-fill, transparent hole), the non-overlapping tiling
//      that keeps a translucent colour composited once, the location test that
//      stops a third region hiding in the dominance floor, and the shapes it
//      must keep refusing (gradient, diagonal, three regions, L-shape, ring);
//   6. settle stability (firstDifference) — the dotted path, and the
//      one-8-bit-step opacity tolerance that must not leak to other keys;
//   7. planViewTransitionBake — paint order, every bail in the SCOPE
//      BOUNDARY banner, and the opacity folding;
//   8. applyViewTransitionBakePlan — the retire-the-document rule, the id
//      shape, paint order in the children map, the honesty stamps;
//   9. isolationCss / snapshotColorCss / hexToComputedRgb string shapes;
//  10. source-wiring pins for the extract-fixture.mjs CLI integration —
//      the bail-to-static guarantee on a browser fault, the wedged-browser
//      recycle, and a doc-drift pin holding the module header and
//      section-runner.sh to the same story about whether VT_BAKE is pinned.

import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';

import {
  VT_SETTLE_TIMEOUT_MS, VT_STABILITY_DELAY_MS, VT_STABILITY_OPACITY_EPS,
  VT_PAINT_EPSILON, VT_COLOR_TOLERANCE, VT_UNIFORM_DOMINANCE_PCT,
  VT_MAX_SNAPSHOT_PX, MAX_VT_GROUPS, VT_BAKE_LOSSY_REASON,
  VT_ISOLATION_BACKDROPS, VT_ISOLATION_STYLE_ID, VT_CONTAINED_OBJECT_FITS,
  r2, px, groupPseudoExists, leafEffectiveOpacity, leafPainted,
  parseUsedMatrix, transformedBounds, boundsOverlap, isolationWindow,
  solveSnapshot, firstDifference, hexToComputedRgb, snapshotColorCss,
  planViewTransitionBake, applyViewTransitionBakePlan, isolationCss,
  VT_XFADE_SUM_EPS, allDifferences, isComplementaryCrossFade,
  crossFadeChainOpacity, classifyStabilityDrift, REFTEST_WAIT_SHIM,
  VT_OVERFLOW_INK_TOLERANCE_PX, cropComposite, probeSnapshotOverflow,
  VT_TWO_COLOUR_MIN_AREA_PX, VT_TWO_COLOUR_EDGE_SLACK_PX,
  twoColourKind, rectComplement, snapshotBoxes, frameRingColor,
} from './view-transition-bake.mjs';
import {
  viewTransitionBakeTrigger,
  VT_START_API_RX, VT_NAME_PROPERTY_RX, VT_PSEUDO_SELECTOR_RX,
  VT_ACTIVE_SELECTOR_RX,
} from './extract-fixture.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));

// ── Builders ────────────────────────────────────────────────────────────────
// A group as inPageVtWalker reports it. Defaults describe the section's
// commonest shape: a 100×100 group at the origin, identity transform, both
// leaves present and fully opaque.
function grp(over = {}) {
  const leaf = (o = {}) => ({
    width: '100px', height: '100px', left: '0px', top: '0px',
    opacity: '1', visibility: 'visible', mixBlendMode: 'normal',
    objectFit: 'fill', ...o,
  });
  return {
    name: 'root', documentIndex: 0,
    width: '100px', height: '100px', left: '0px', top: '0px',
    transform: 'none', transformOrigin: '50px 50px', opacity: '1',
    imagePair: { opacity: '1', visibility: 'visible' },
    old: leaf(), new: leaf(),
    ...over,
    ...(over.old ? { old: leaf(over.old) } : {}),
    ...(over.new ? { new: leaf(over.new) } : {}),
    ...(over.imagePair ? { imagePair: { opacity: '1', visibility: 'visible', ...over.imagePair } } : {}),
  };
}

function walkOf(groups, over = {}) {
  return {
    active: true, rootCaptureName: 'root',
    scb: { width: 358, height: 568 },
    viewTransitionBackground: null, canvasBackground: null,
    groups, ...over,
  };
}

// A solved snapshot: flat colour filling the whole 100×100 leaf.
function solvedFlat(over = {}) {
  return {
    rect: { x: 0, y: 0, w: 100, h: 100 },
    rgba: { r: 0, g: 128, b: 0, a: 1 },
    uniform: true, distinct: 1, coverage: 100, ...over,
  };
}

/** Synthesize the two isolation composites solveSnapshot consumes: a `paint`
 *  callback answers `{ r, g, b, a }` (a = 0 for an unpainted pixel) per pixel,
 *  and this composites it over black and over white exactly as the browser
 *  would, so the solve is exercised against real 8-bit rounding. */
function composites(w, h, paint) {
  const mk = (bg) => {
    const png = new PNG({ width: w, height: h });
    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) {
        const { r, g, b, a } = paint(x, y);
        const o = (y * w + x) * 4;
        png.data[o] = Math.round(a * r + (1 - a) * bg);
        png.data[o + 1] = Math.round(a * g + (1 - a) * bg);
        png.data[o + 2] = Math.round(a * b + (1 - a) * bg);
        png.data[o + 3] = 255;
      }
    }
    return PNG.sync.write(png);
  };
  return [mk(0), mk(255)];
}

// ── 1. Tunables + vocabulary ────────────────────────────────────────────────

test('VT: the tunables are the measured constants, not round numbers of convenience', () => {
  assert.equal(VT_SETTLE_TIMEOUT_MS, 4000);
  assert.equal(VT_STABILITY_DELAY_MS, 100);
  // One 8-bit step — the same floor the paint test uses, so "invisible" and
  // "unchanged" cannot disagree.
  assert.equal(VT_STABILITY_OPACITY_EPS, 1 / 255);
  assert.equal(VT_PAINT_EPSILON, 1 / 255);
  assert.equal(VT_COLOR_TOLERANCE, 2);
  assert.equal(VT_UNIFORM_DOMINANCE_PCT, 99.5);
  assert.equal(VT_MAX_SNAPSHOT_PX, 4000);
  assert.equal(MAX_VT_GROUPS, 40);
});

test('VT: the lossy reason is fresh, so it can never collide with a score-exclusion tag', () => {
  assert.equal(VT_BAKE_LOSSY_REASON, 'baked-view-transition-tree');
  // A SCORE_EXCLUDED tag is a wpt-not-applicable tag name; this reason must
  // not be one, or a baked row could quietly leave the scored population.
  const na = readFileSync(join(__dirname, 'wpt-not-applicable.mjs'), 'utf8');
  assert.ok(!na.includes(VT_BAKE_LOSSY_REASON),
    'the lossy reason must not double as a not-applicable tag');
});

test('VT provenance: the `viewTransitionBaked` stamp is threaded fixture → keyMap → manifest row', () => {
  // The module header promises this channel; without both halves the stamp
  // the bake writes onto `_wpt` dies at the combined-fixture boundary and no
  // investigator can tell a baked row from a static one.
  const build = readFileSync(join(__dirname, 'build-combined-fixture.mjs'), 'utf8');
  assert.match(build, /viewTransitionBaked: fixture\._wpt\?\.viewTransitionBaked === true/);
  // …and a MISSING fixture must say false explicitly, never leave it absent.
  assert.match(build, /viewTransitionBaked: false/);
  const inject = readFileSync(join(__dirname, 'inject-wpt-block.mjs'), 'utf8');
  assert.match(inject, /viewTransitionBaked: meta\.viewTransitionBaked === true/);
});

test('VT: two backdrops, black and white — the pair that breaks the white/transparent tie', () => {
  assert.deepEqual(VT_ISOLATION_BACKDROPS, ['#000000', '#FFFFFF']);
  assert.equal(VT_ISOLATION_STYLE_ID, 'sc-vt-isolation');
});

test('VT: only object-fits that keep ink inside the box may be measured', () => {
  assert.deepEqual(VT_CONTAINED_OBJECT_FITS, ['fill', 'contain', 'scale-down']);
  // `cover` and `none` can paint outside the leaf box, so the measured rect
  // would be a cropped lie — they must NOT be in the set.
  assert.ok(!VT_CONTAINED_OBJECT_FITS.includes('cover'));
  assert.ok(!VT_CONTAINED_OBJECT_FITS.includes('none'));
});

test('VT: r2/px quantise to the 2dp the other bakes use', () => {
  assert.equal(r2(100 / 3), 33.33);
  assert.equal(r2(1.006), 1.01);
  assert.equal(r2(-0.004), -0);
  assert.equal(px(12.3456), '12.35px');
  assert.equal(px(0), '0px');
  // Pinned as the KNOWN limit, not hidden: `Math.round(x * 100) / 100` is a
  // binary-float rounder, so an exact-half decimal whose ×100 lands just
  // under (1.005 * 100 === 100.49999999999999) rounds DOWN. Shared verbatim
  // with bidi-bake/post-load so all the bakes' numbers stay diff-comparable;
  // a half-ULP disagreement is far below the 1-px geometry this feeds.
  assert.equal(r2(1.005), 1);
});

// ── 2. The static trigger ───────────────────────────────────────────────────

test('VT trigger: the JS entry point fires on any receiver', () => {
  assert.equal(viewTransitionBakeTrigger('<script>document.startViewTransition(f)</script>'),
    'start-view-transition-api');
  // css-view-transitions-2 scoped transitions start on an ELEMENT.
  assert.equal(viewTransitionBakeTrigger('<script>el.startViewTransition(()=>{})</script>'),
    'start-view-transition-api');
  // Not a false positive on a lookalike identifier.
  assert.equal(viewTransitionBakeTrigger('<script>myStartViewTransitionHelper()</script>'), null);
  assert.equal(viewTransitionBakeTrigger('<script>x.startViewTransitionLater()</script>'), null);
});

test('VT trigger: the naming properties and the pseudo/state selectors each fire alone', () => {
  assert.equal(viewTransitionBakeTrigger('<style>.a{view-transition-name: x}</style>'),
    'view-transition-name');
  assert.equal(viewTransitionBakeTrigger('<style>.a{view-transition-class: c}</style>'),
    'view-transition-name');
  assert.equal(viewTransitionBakeTrigger('<style>::view-transition{background:pink}</style>'),
    'view-transition-pseudo');
  assert.equal(viewTransitionBakeTrigger('<style>::view-transition-old(x){opacity:0}</style>'),
    'view-transition-pseudo');
  assert.equal(viewTransitionBakeTrigger('<style>:active-view-transition{color:red}</style>'),
    'active-view-transition-selector');
  assert.equal(viewTransitionBakeTrigger('<style>:active-view-transition-type(a){color:red}</style>'),
    'active-view-transition-selector');
});

test('VT trigger: the API wins over the styling hints — it is the signal a transition RUNS', () => {
  const both = '<style>.a{view-transition-name:x}</style><script>document.startViewTransition(f)</script>';
  assert.equal(viewTransitionBakeTrigger(both), 'start-view-transition-api');
});

test('VT trigger: a commented-out demo cannot arm a browser launch', () => {
  assert.equal(viewTransitionBakeTrigger('<style>/* ::view-transition {} */</style>'), null);
  assert.equal(viewTransitionBakeTrigger('<script>/* document.startViewTransition(f) */</script>'), null);
});

test('VT trigger: an ordinary document is silent (the pure-static batch path pays one regex sweep)', () => {
  assert.equal(viewTransitionBakeTrigger('<div style="color:red">hi</div>'), null);
  assert.equal(viewTransitionBakeTrigger(''), null);
  assert.equal(viewTransitionBakeTrigger(null), null);
});

test('VT trigger: the four regexes are exported so the taxonomy is auditable', () => {
  for (const rx of [VT_START_API_RX, VT_NAME_PROPERTY_RX, VT_PSEUDO_SELECTOR_RX, VT_ACTIVE_SELECTOR_RX]) {
    assert.ok(rx instanceof RegExp);
    assert.ok(!rx.global, 'a global regex carries lastIndex state between tests');
  }
});

// ── 3. Computed-style predicates ────────────────────────────────────────────

test('VT: a phantom group answers `auto` — that is the whole existence discriminator', () => {
  assert.equal(groupPseudoExists(grp()), true);
  assert.equal(groupPseudoExists(grp({ width: 'auto', height: 'auto' })), false);
  // A half-auto answer is not a real group either.
  assert.equal(groupPseudoExists(grp({ width: 'auto' })), false);
  assert.equal(groupPseudoExists(null), false);
});

test('VT: leaf opacity is the product down the pseudo chain', () => {
  const g = grp({ opacity: '0.5', imagePair: { opacity: '0.5' }, old: { opacity: '0.5' } });
  assert.equal(leafEffectiveOpacity(g, g.old), 0.125);
  // A non-numeric answer degrades to 1 rather than poisoning the product.
  const bad = grp({ opacity: 'weird' });
  assert.equal(leafEffectiveOpacity(bad, bad.old), 1);
});

test('VT: the decoy-group idiom (hidden image-pair) reads as unpainted off the LEAF alone', () => {
  // `::view-transition-image-pair(x){visibility:hidden}` inherits down, so
  // the leaf's own computed value already says hidden — no chain walk needed.
  const g = grp({ old: { visibility: 'hidden' } });
  assert.equal(leafPainted(g, g.old), false);
  // Opacity does NOT inherit, hence the explicit product.
  const faded = grp({ imagePair: { opacity: '0' } });
  assert.equal(leafPainted(faded, faded.old), false);
  assert.equal(leafPainted(grp(), grp().old), true);
  assert.equal(leafPainted(grp(), null), false);
});

test('VT: the paint floor is one 8-bit step, not zero', () => {
  const almost = grp({ old: { opacity: String(0.9 / 255) } });
  assert.equal(leafPainted(almost, almost.old), false);
  const just = grp({ old: { opacity: String(1.1 / 255) } });
  assert.equal(leafPainted(just, just.old), true);
});

// ── 4. Geometry ─────────────────────────────────────────────────────────────

test('VT: parseUsedMatrix reads 2-D, projects 3-D, and refuses anything else', () => {
  assert.deepEqual(parseUsedMatrix('none'), { a: 1, b: 0, c: 0, d: 1, e: 0, f: 0 });
  assert.deepEqual(parseUsedMatrix(''), { a: 1, b: 0, c: 0, d: 1, e: 0, f: 0 });
  assert.deepEqual(parseUsedMatrix('matrix(1, 0, 0, 1, 10, 20)'),
    { a: 1, b: 0, c: 0, d: 1, e: 10, f: 20 });
  // matrix3d → its 2-D block (indices 0,1,4,5,12,13).
  const m3 = 'matrix3d(2, 0, 0, 0, 0, 3, 0, 0, 0, 0, 1, 0, 7, 9, 0, 1)';
  assert.deepEqual(parseUsedMatrix(m3), { a: 2, b: 0, c: 0, d: 3, e: 7, f: 9 });
  // Wrong arity / garbage → null, which the planner turns into a loud bail.
  assert.equal(parseUsedMatrix('matrix(1, 0, 0)'), null);
  assert.equal(parseUsedMatrix('rotate(45deg)'), null);
  assert.equal(parseUsedMatrix('matrix(a, b, c, d, e, f)'), null);
});

test('VT: transformedBounds bounds the four transformed CORNERS, so rotation cannot hide overlap', () => {
  const id = { a: 1, b: 0, c: 0, d: 1, e: 0, f: 0 };
  assert.deepEqual(transformedBounds(10, 20, 100, 50, id), { x: 10, y: 20, w: 100, h: 50 });
  // 90° rotation: a 100×50 box becomes a 50×100 bound.
  const rot90 = { a: 0, b: 1, c: -1, d: 0, e: 0, f: 0 };
  const b = transformedBounds(0, 0, 100, 50, rot90);
  assert.equal(r2(b.w), 50);
  assert.equal(r2(b.h), 100);
});

test('VT: touching edges are not an overlap', () => {
  const a = { x: 0, y: 0, w: 10, h: 10 };
  assert.equal(boundsOverlap(a, { x: 10, y: 0, w: 10, h: 10 }), false);
  assert.equal(boundsOverlap(a, { x: 9.99, y: 0, w: 10, h: 10 }), true);
  assert.equal(boundsOverlap(a, { x: 0, y: 20, w: 10, h: 10 }), false);
});

test('VT: the isolation window is the UNION of group and leaf boxes', () => {
  // The plain case: leaf inside its group → window is the group.
  const g = grp();
  assert.deepEqual(isolationWindow(g, g.old), { offset: { x: 0, y: 0 }, width: 100, height: 100 });
  // The `pseudo-with-classes-*` case: `::view-transition-old(target.cls){left:100px}`
  // puts the snapshot clean outside the group. Clipping to the group reported
  // "nothing painted"; the union keeps it measurable.
  const moved = grp({ old: { left: '100px' } });
  assert.deepEqual(isolationWindow(moved, moved.old),
    { offset: { x: 0, y: 0 }, width: 200, height: 100 });
  // A NEGATIVE leaf offset shifts the window origin, and `offset` is what the
  // solved rect is re-based by so the emitted left/top stay in group space.
  const back = grp({ old: { left: '-40px', top: '-10px' } });
  assert.deepEqual(isolationWindow(back, back.old),
    { offset: { x: 40, y: 10 }, width: 140, height: 110 });
});

test('VT: a fractional union window is ceiled — a screenshot clip is whole pixels', () => {
  const g = grp({ old: { left: '10.4px', width: '100.2px' } });
  assert.equal(isolationWindow(g, g.old).width, 111);
});

// ── 5. The alpha solve ──────────────────────────────────────────────────────

test('VT solve: an opaque flat leaf recovers its exact colour, alpha and rect', () => {
  const [b, w] = composites(20, 20, (x, y) =>
    (x >= 5 && x < 15 && y >= 4 && y < 16)
      ? { r: 0, g: 128, b: 0, a: 1 } : { r: 0, g: 0, b: 0, a: 0 });
  const s = solveSnapshot(b, w);
  assert.deepEqual(s.rect, { x: 5, y: 4, w: 10, h: 12 });
  assert.deepEqual(s.rgba, { r: 0, g: 128, b: 0, a: 1 });
  assert.equal(s.uniform, true);
  assert.equal(s.coverage, 100);
});

test('VT solve: a WHITE opaque leaf is not mistaken for transparency — the whole reason for two backdrops', () => {
  const [b, w] = composites(8, 8, () => ({ r: 255, g: 255, b: 255, a: 1 }));
  const s = solveSnapshot(b, w);
  assert.deepEqual(s.rgba, { r: 255, g: 255, b: 255, a: 1 });
  assert.deepEqual(s.rect, { x: 0, y: 0, w: 8, h: 8 });
  // Read against WHITE alone this is indistinguishable from nothing at all.
  const [b2, w2] = composites(8, 8, () => ({ r: 0, g: 0, b: 0, a: 0 }));
  assert.equal(solveSnapshot(b2, w2).rect, null);
});

test('VT solve: a semi-transparent leaf recovers both alpha and the underlying colour', () => {
  const [b, w] = composites(8, 8, () => ({ r: 200, g: 100, b: 50, a: 0.5 }));
  const s = solveSnapshot(b, w);
  assert.equal(s.rgba.a, 0.5);
  // 8-bit compositing then dividing by alpha costs at most a step or two.
  assert.ok(Math.abs(s.rgba.r - 200) <= VT_COLOR_TOLERANCE, `r=${s.rgba.r}`);
  assert.ok(Math.abs(s.rgba.g - 100) <= VT_COLOR_TOLERANCE, `g=${s.rgba.g}`);
});

test('VT solve: an empty snapshot is a legitimate outcome, not an error', () => {
  const [b, w] = composites(8, 8, () => ({ r: 0, g: 0, b: 0, a: 0 }));
  const s = solveSnapshot(b, w);
  assert.deepEqual(s, { rect: null, rgba: null, uniform: true, distinct: 0, coverage: 0 });
});

test('VT solve: a two-colour snapshot is NOT uniform — `uniform` still means ONE colour', () => {
  const [b, w] = composites(20, 20, (x) =>
    x < 10 ? { r: 255, g: 0, b: 0, a: 1 } : { r: 0, g: 0, b: 255, a: 1 });
  const s = solveSnapshot(b, w);
  assert.equal(s.uniform, false);
  assert.ok(s.coverage <= 51, `half-and-half must report ~50% flat, got ${s.coverage}`);
  assert.ok(s.distinct >= 2);
  // …and wave-40's fit picks it up from there (section 5c). `uniform` is
  // untouched precisely so the two questions stay separable.
  assert.equal(s.two.kind, 'band-v');
});

test('VT solve: a HOLE inside the rect counts against uniformity — a hole is not flat colour', () => {
  const [b, w] = composites(20, 20, (x, y) =>
    (x === 10 && y === 10) ? { r: 0, g: 0, b: 0, a: 0 } : { r: 0, g: 128, b: 0, a: 1 });
  const s = solveSnapshot(b, w);
  // 1 unpainted pixel of 400 = 99.75% — above the 99.5% floor, so still flat…
  assert.equal(s.uniform, true);
  assert.ok(s.coverage < 100);
});

test('VT solve: mismatched composite sizes are a loud error, never a silent crop', () => {
  const [b] = composites(10, 10, () => ({ r: 0, g: 0, b: 0, a: 1 }));
  const [, w] = composites(10, 12, () => ({ r: 0, g: 0, b: 0, a: 1 }));
  const s = solveSnapshot(b, w);
  assert.match(s.error, /isolation size drift 10x10 vs 10x12/);
});

// ── 6. Settle stability ─────────────────────────────────────────────────────

// ── 5b. The crop probe (wave-39 A4) ─────────────────────────────────────────

test('VT overflow: the ink tolerance is a sub-visible antialiasing allowance, not a fudge', () => {
  assert.equal(VT_OVERFLOW_INK_TOLERANCE_PX, 4);
});

test('VT overflow: the crop is byte-identical to the pixels the old clip produced', () => {
  const [black, white] = composites(20, 12, (x, y) =>
    ({ r: x * 10, g: y * 10, b: 7, a: 1 }));
  const crop = PNG.sync.read(cropComposite(black, 8, 5));
  const full = PNG.sync.read(black);
  assert.equal(crop.width, 8);
  assert.equal(crop.height, 5);
  for (let y = 0; y < 5; y++) {
    for (let x = 0; x < 8; x++) {
      for (let c = 0; c < 4; c++) {
        assert.equal(crop.data[(y * 8 + x) * 4 + c], full.data[(y * 20 + x) * 4 + c]);
      }
    }
  }
  // …and the white composite crops the same way, or the solve pairs two
  // different regions.
  assert.equal(PNG.sync.read(cropComposite(white, 8, 5)).width, 8);
});

test('VT overflow: a snapshot confined to its window reports NO outside ink', () => {
  // Painted only inside the 10×10 window; the rest of the 40×40 viewport is
  // the transparent surround.
  const [b, w] = composites(40, 40, (x, y) =>
    (x < 10 && y < 10 ? { r: 0, g: 128, b: 0, a: 1 } : { r: 0, g: 0, b: 0, a: 0 }));
  assert.deepEqual(probeSnapshotOverflow(b, w, { width: 10, height: 10 }), { outside: 0 });
});

test('VT overflow: ink outside the window is COUNTED — the cropped-lie detector', () => {
  // The measured defect's shape: a 10×10 box whose children paint well past it.
  const [b, w] = composites(40, 40, (x, y) =>
    ((x < 10 && y < 10) || (x >= 20 && x < 25 && y >= 20 && y < 30)
      ? { r: 0, g: 128, b: 0, a: 1 }
      : { r: 0, g: 0, b: 0, a: 0 }));
  assert.equal(probeSnapshotOverflow(b, w, { width: 10, height: 10 }).outside, 50);
});

test('VT overflow: a window that IS the viewport has an empty complement — the root case', () => {
  const [b, w] = composites(24, 24, () => ({ r: 255, g: 255, b: 255, a: 1 }));
  assert.deepEqual(probeSnapshotOverflow(b, w, { width: 24, height: 24 }), { outside: 0 });
});

// ── 5c. The TWO-COLOUR fit (wave-40 T4) ─────────────────────────────────────
//
// The narrowing of the raster refusal: a snapshot that is one flat colour with
// one flat RECTANGLE in it is two boxes, not a bitmap. Everything here is
// driven through solveSnapshot on synthesized composites, so the fit is
// exercised against the same 8-bit rounding the browser produces.

test('VT two-colour: the minimum region area is a noise floor, not a fudge', () => {
  assert.equal(VT_TWO_COLOUR_MIN_AREA_PX, 4);
});

test('VT two-colour: a SPLIT BAND solves to the two exact rects and colours', () => {
  const [b, w] = composites(20, 20, (x) =>
    x < 10 ? { r: 255, g: 0, b: 0, a: 1 } : { r: 0, g: 0, b: 255, a: 1 });
  const s = solveSnapshot(b, w);
  assert.equal(s.two.kind, 'band-v');
  assert.deepEqual(s.two.rect, { x: 10, y: 0, w: 10, h: 20 });
  assert.equal(s.two.coverage, 100);
  // A band splits the dominant into exactly ONE complement piece, so the pair
  // the lane is named for really is two boxes.
  const boxes = snapshotBoxes(s);
  assert.equal(boxes.length, 2);
  assert.deepEqual(boxes[0], { rect: { x: 0, y: 0, w: 10, h: 20 }, rgba: { r: 255, g: 0, b: 0, a: 1 } });
  assert.deepEqual(boxes[1], { rect: { x: 10, y: 0, w: 10, h: 20 }, rgba: { r: 0, g: 0, b: 255, a: 1 } });
});

test('VT two-colour: a NESTED rect decomposes into four surrounding pieces plus the inner box', () => {
  const [b, w] = composites(40, 40, (x, y) =>
    (x >= 10 && x < 20 && y >= 15 && y < 25)
      ? { r: 0, g: 0, b: 255, a: 1 } : { r: 0, g: 128, b: 0, a: 1 });
  const s = solveSnapshot(b, w);
  assert.equal(s.two.kind, 'nested');
  assert.deepEqual(s.two.rect, { x: 10, y: 15, w: 10, h: 10 });
  const boxes = snapshotBoxes(s);
  assert.equal(boxes.length, 5);
  // THE PROOF THAT MATTERS: the pieces tile the rect exactly once. Overlapping
  // boxes would composite a translucent colour twice.
  const seen = new Uint8Array(40 * 40);
  for (const { rect } of boxes) {
    for (let y = rect.y; y < rect.y + rect.h; y++) {
      for (let x = rect.x; x < rect.x + rect.w; x++) seen[y * 40 + x]++;
    }
  }
  assert.ok(seen.every((n) => n === 1), 'every pixel of the rect is covered exactly once');
});

test('VT two-colour: a FRAME around a fill is the same shape with the roles swapped', () => {
  // The border families' shape. Orientation 1 cannot describe it — the frame
  // colour s bbox IS the whole rect — so the fit swaps: the frame becomes the
  // BASE and the fill becomes the sub-rectangle.
  const [b, w] = composites(100, 100, (x, y) =>
    (x < 5 || y < 5 || x >= 95 || y >= 95)
      ? { r: 0, g: 0, b: 255, a: 1 } : { r: 0, g: 128, b: 0, a: 1 });
  const s = solveSnapshot(b, w);
  // `rgba` is still the DOMINANT (the fill) — the solve's own contract — and
  // the background override says the surround is the other colour.
  assert.deepEqual(s.rgba, { r: 0, g: 128, b: 0, a: 1 });
  assert.deepEqual(s.two.base, { r: 0, g: 0, b: 255, a: 1 });
  assert.deepEqual(s.two.rect, { x: 5, y: 5, w: 90, h: 90 });
  const boxes = snapshotBoxes(s);
  assert.equal(boxes.length, 5);
  // Four frame pieces in the border colour, then the fill.
  for (const bx of boxes.slice(0, 4)) assert.deepEqual(bx.rgba, { r: 0, g: 0, b: 255, a: 1 });
  assert.deepEqual(boxes[4], { rect: { x: 5, y: 5, w: 90, h: 90 }, rgba: { r: 0, g: 128, b: 0, a: 1 } });
});

test('VT two-colour: a RING of a third colour is not a frame — both orientations fail', () => {
  // Frame, fill, and a second frame inside it: three classes, and the swap
  // must not rescue it.
  const [b, w] = composites(100, 100, (x, y) => {
    const d = Math.min(x, y, 99 - x, 99 - y);
    if (d < 5) return { r: 0, g: 0, b: 255, a: 1 };
    if (d < 10) return { r: 255, g: 0, b: 0, a: 1 };
    return { r: 0, g: 128, b: 0, a: 1 };
  });
  assert.equal(solveSnapshot(b, w).two, null);
});

test('VT two-colour: a HOLE is the second region, and it emits NO box', () => {
  const [b, w] = composites(40, 40, (x, y) =>
    (x >= 10 && x < 20 && y >= 15 && y < 25)
      ? { r: 0, g: 0, b: 0, a: 0 } : { r: 0, g: 128, b: 0, a: 1 });
  const s = solveSnapshot(b, w);
  assert.equal(s.two.rgba, null, 'a transparent second region has no colour');
  assert.deepEqual(s.two.rect, { x: 10, y: 15, w: 10, h: 10 });
  // Four surrounding pieces and nothing in the middle — the bite stays a bite.
  assert.equal(snapshotBoxes(s).length, 4);
});

test('VT two-colour: a TRANSLUCENT second colour keeps its own alpha, composited once', () => {
  const [b, w] = composites(20, 20, (x) =>
    x < 10 ? { r: 0, g: 128, b: 0, a: 1 } : { r: 0, g: 0, b: 255, a: 0.5 });
  const s = solveSnapshot(b, w);
  assert.equal(s.two.rgba.a, 0.5);
  const boxes = snapshotBoxes(s);
  assert.equal(boxes.length, 2);
  // The green piece stops at x=10: it must NOT run under the translucent blue,
  // or the blue would composite over green instead of over the backdrop.
  assert.equal(boxes[0].rect.x + boxes[0].rect.w, 10);
});

test('VT two-colour: a GRADIENT is still a raster — the refusal holds where it was written', () => {
  const [b, w] = composites(40, 40, (x) => ({ r: x * 6, g: 0, b: 0, a: 1 }));
  const s = solveSnapshot(b, w);
  assert.equal(s.uniform, false);
  assert.equal(s.two, null);
});

test('VT two-colour: a DIAGONAL split has a rectangular bbox but is not a rectangle', () => {
  // The trap this fit must not fall into: the minority colour's bounding box
  // is the whole rect, and it is FULL of dominant-coloured pixels.
  const [b, w] = composites(40, 40, (x, y) =>
    x > y ? { r: 255, g: 0, b: 0, a: 1 } : { r: 0, g: 0, b: 255, a: 1 });
  assert.equal(solveSnapshot(b, w).two, null);
});

test('VT two-colour: THREE regions are two too many', () => {
  const [b, w] = composites(30, 30, (x) =>
    x < 10 ? { r: 255, g: 0, b: 0, a: 1 }
      : x < 20 ? { r: 0, g: 255, b: 0, a: 1 }
        : { r: 0, g: 0, b: 255, a: 1 });
  assert.equal(solveSnapshot(b, w).two, null);
});

test('VT two-colour: a THIRD region cannot hide in the dominance floor s slack', () => {
  // 24×24 = 576 px of a 358×568 rect is 0.28 % — comfortably under the 0.5 %
  // the floor forgives, so a count-only test would DELETE it. The location
  // test refuses: those pixels are nowhere near either region's boundary.
  const [b, w] = composites(358, 568, (x, y) => {
    if (x >= 100 && x < 124 && y >= 300 && y < 324) return { r: 255, g: 0, b: 0, a: 1 };
    return y < 284 ? { r: 255, g: 255, b: 255, a: 1 } : { r: 0, g: 128, b: 0, a: 1 };
  });
  const s = solveSnapshot(b, w);
  assert.equal(s.uniform, false);
  assert.equal(s.two, null, 'a third region must bail, not be forgiven as edge noise');
  // …and the same snapshot WITHOUT the blob is the two-colour band it looks
  // like, so the refusal above is about the blob and nothing else.
  const [b2, w2] = composites(358, 568, (x, y) =>
    (y < 284 ? { r: 255, g: 255, b: 255, a: 1 } : { r: 0, g: 128, b: 0, a: 1 }));
  assert.equal(solveSnapshot(b2, w2).two.kind, 'band-h');
});

test('VT two-colour: the edge slack is one pixel — the width of an 8-bit AA boundary', () => {
  assert.equal(VT_TWO_COLOUR_EDGE_SLACK_PX, 1);
  // An antialiased boundary between the two fills is forgiven: one blended
  // column at the seam is exactly what a mid-pixel box edge produces, and the
  // location test is what tells it apart from a shape. (300 px of a 300×300
  // rect is 0.33 %, inside the dominance floor — at the section's real
  // snapshot sizes a one-px seam always is; a 40×40 toy would fail on the
  // COUNT before the location test ever ran.)
  const [b, w] = composites(300, 300, (x) =>
    x < 150 ? { r: 0, g: 128, b: 0, a: 1 }
      : x === 150 ? { r: 0, g: 64, b: 128, a: 1 }
        : { r: 0, g: 0, b: 255, a: 1 });
  const s = solveSnapshot(b, w);
  assert.ok(s.two, 'a one-pixel seam must not defeat the fit');
  assert.equal(s.two.kind, 'band-v');
  assert.deepEqual(s.two.rect, { x: 151, y: 0, w: 149, h: 300 });
});

test('VT two-colour: an L-SHAPED second region fails the rectangle proof', () => {
  const [b, w] = composites(40, 40, (x, y) => {
    const inL = (x >= 10 && x < 30 && y >= 10 && y < 16) || (x >= 10 && x < 16 && y >= 10 && y < 30);
    return inL ? { r: 0, g: 0, b: 255, a: 1 } : { r: 0, g: 128, b: 0, a: 1 };
  });
  assert.equal(solveSnapshot(b, w).two, null);
});

test('VT two-colour: a SPECK below the area floor is noise, never a box', () => {
  // 1 px of a second colour in 400: the dominance floor alone would forgive it
  // (99.75 % > 99.5 %), so `uniform` is already true and the fit is not even
  // asked. This pins that a sub-floor region can never become a hairline box.
  const [b, w] = composites(20, 20, (x, y) =>
    (x === 5 && y === 5) ? { r: 0, g: 0, b: 255, a: 1 } : { r: 0, g: 128, b: 0, a: 1 });
  const s = solveSnapshot(b, w);
  assert.equal(s.uniform, true);
  assert.equal(s.two, undefined);
  assert.equal(snapshotBoxes(s).length, 1);
});

test('VT two-colour: a uniform solve ships the wave-38 single box, byte-for-byte', () => {
  const [b, w] = composites(20, 20, () => ({ r: 0, g: 128, b: 0, a: 1 }));
  const s = solveSnapshot(b, w);
  assert.deepEqual(snapshotBoxes(s), [{ rect: s.rect, rgba: s.rgba }]);
  // …and an empty snapshot ships nothing at all.
  assert.deepEqual(snapshotBoxes({ rect: null, rgba: null }), []);
  assert.deepEqual(snapshotBoxes(undefined), []);
});

test('VT two-colour: rectComplement tiles outer-minus-inner without overlap', () => {
  assert.deepEqual(rectComplement({ x: 0, y: 0, w: 10, h: 10 }, { x: 2, y: 2, w: 3, h: 3 }), [
    { x: 0, y: 0, w: 10, h: 2 },    // top band
    { x: 0, y: 5, w: 10, h: 5 },    // bottom band
    { x: 0, y: 2, w: 2, h: 3 },     // left of the middle strip
    { x: 5, y: 2, w: 5, h: 3 },     // right of it
  ]);
  // A full-width inner rect collapses to the bands only — the split-band case.
  assert.deepEqual(rectComplement({ x: 0, y: 0, w: 10, h: 10 }, { x: 0, y: 0, w: 10, h: 4 }),
    [{ x: 0, y: 4, w: 10, h: 6 }]);
  // No overlap at all → the outer survives whole.
  assert.deepEqual(rectComplement({ x: 0, y: 0, w: 4, h: 4 }, { x: 9, y: 9, w: 2, h: 2 }),
    [{ x: 0, y: 0, w: 4, h: 4 }]);
  // Fully covered → nothing left.
  assert.deepEqual(rectComplement({ x: 1, y: 1, w: 4, h: 4 }, { x: 0, y: 0, w: 9, h: 9 }), []);
});

test('VT two-colour: twoColourKind names the shape the fit measured', () => {
  const outer = { x: 0, y: 0, w: 10, h: 10 };
  assert.equal(twoColourKind(outer, { x: 0, y: 0, w: 10, h: 3 }), 'band-h');
  assert.equal(twoColourKind(outer, { x: 7, y: 0, w: 3, h: 10 }), 'band-v');
  assert.equal(twoColourKind(outer, { x: 3, y: 3, w: 4, h: 4 }), 'nested');
  assert.equal(twoColourKind(outer, { x: 0, y: 0, w: 4, h: 4 }), 'corner');
  // The degenerate shape the fit itself rejects — no dominant region left.
  assert.equal(twoColourKind(outer, outer), 'full');
});

test('VT overflow: mismatched composite sizes are a loud error, never a silent pass', () => {
  const [b] = composites(8, 8, () => ({ r: 0, g: 0, b: 0, a: 1 }));
  const [w] = composites(8, 9, () => ({ r: 0, g: 0, b: 0, a: 1 }));
  assert.match(probeSnapshotOverflow(b, w, { width: 8, height: 8 }).error,
    /overflow-probe size drift 8x8 vs 8x9/);
});

test('VT stability: firstDifference reports the dotted PATH, so a batch log says what moved', () => {
  const a = { groups: [{ name: 'root', old: { opacity: '1' } }] };
  const b = { groups: [{ name: 'root', old: { opacity: '0.5' } }] };
  assert.deepEqual(firstDifference(a, b), { path: 'groups.0.old.opacity', a: '1', b: '0.5' });
  assert.equal(firstDifference(a, structuredClone(a)), null);
});

test('VT stability: the opacity tolerance is one 8-bit step and applies ONLY to opacity keys', () => {
  const mk = (v) => ({ g: { opacity: String(v) } });
  assert.equal(firstDifference(mk(0.5), mk(0.5 + 0.5 / 255)), null);
  assert.ok(firstDifference(mk(0.5), mk(0.5 + 2 / 255)));
  // A same-magnitude drift on any OTHER key is a real difference — the key
  // name is the discriminator so nothing inherits the tolerance by accident.
  const wa = { g: { width: '100' } }, wb = { g: { width: String(100 + 0.5 / 255) } };
  assert.ok(firstDifference(wa, wb));
});

test('VT stability: a type change and a new key both surface', () => {
  assert.deepEqual(firstDifference({ a: 1 }, { a: '1' }), { path: 'a', a: 1, b: '1' });
  assert.deepEqual(firstDifference({}, { a: 1 }), { path: 'a', a: undefined, b: 1 });
});

test('VT stability: firstDifference is the one-answer front end for allDifferences', () => {
  const a = { p: { x: '1', y: '2' } }, b = { p: { x: '9', y: '8' } };
  const all = allDifferences(a, b);
  assert.equal(all.length, 2);
  assert.deepEqual(firstDifference(a, b), all[0]);
  // The tolerance lives in ONE traversal, so the two can never disagree.
  const o = (v) => ({ groups: [{ old: { opacity: String(v) } }] });
  assert.deepEqual(allDifferences(o(0.5), o(0.5 + 0.5 / 255)), []);
  assert.equal(firstDifference(o(0.5), o(0.5 + 0.5 / 255)), null);
});

// ── 6b. The invariant cross-fade (wave-39 A4) ───────────────────────────────

/** The section's real shape: a UA cross-fade caught mid-flight — both leaves
 *  plus-lighter, opacities complementary. */
function xfadeGroup(p, over = {}) {
  return grp({
    old: { opacity: String(1 - p), mixBlendMode: 'plus-lighter' },
    new: { opacity: String(p), mixBlendMode: 'plus-lighter' },
    ...over,
  });
}

test('VT cross-fade: the sum tolerance is one 8-bit step, like every other tolerance here', () => {
  assert.equal(VT_XFADE_SUM_EPS, 1 / 255);
});

test('VT cross-fade: the UA pair is recognised at any animation position', () => {
  for (const p of [0, 0.001, 0.5, 0.9994, 1]) {
    assert.equal(isComplementaryCrossFade(xfadeGroup(p)), true, `p=${p}`);
  }
});

test('VT cross-fade: `normal` blending is refused — source-over is NOT position-independent', () => {
  // Co = αp + α(1−p)(1−αp) depends on p, so the collapse to α·C does not hold.
  assert.equal(isComplementaryCrossFade(grp({
    old: { opacity: '0.4' }, new: { opacity: '0.6' },   // default blend: normal
  })), false);
});

test('VT cross-fade: two opacities that do not sum to 1 are two animations, not one fade', () => {
  const g = grp({
    old: { opacity: '0.4', mixBlendMode: 'plus-lighter' },
    new: { opacity: '0.4', mixBlendMode: 'plus-lighter' },
  });
  assert.equal(isComplementaryCrossFade(g), false);
  // …and the boundary is the 8-bit step, measured on both sides of it.
  assert.equal(isComplementaryCrossFade(xfadeGroup(0.5, {
    new: { opacity: String(0.5 + 0.5 / 255), mixBlendMode: 'plus-lighter' },
  })), true);
  assert.equal(isComplementaryCrossFade(xfadeGroup(0.5, {
    new: { opacity: String(0.5 + 2 / 255), mixBlendMode: 'plus-lighter' },
  })), false);
});

test('VT cross-fade: the decoy-group idiom (hidden image-pair) is not a cross-fade', () => {
  assert.equal(isComplementaryCrossFade(xfadeGroup(0.5, {
    imagePair: { visibility: 'hidden' }, old: { visibility: 'hidden', mixBlendMode: 'plus-lighter', opacity: '0.5' },
  })), false);
  // …nor is a pair whose whole chain above them has been faded out.
  assert.equal(isComplementaryCrossFade(xfadeGroup(0.5, { opacity: '0' })), false);
});

test('VT cross-fade: the chain opacity leaves the (summing-to-1) leaves out', () => {
  assert.equal(crossFadeChainOpacity(xfadeGroup(0.3, {
    opacity: '0.5', imagePair: { opacity: '0.5' },
  })), 0.25);
});

test('VT cross-fade: classify defers ONLY a leaf-opacity pair, and names the group', () => {
  const a = walkOf([xfadeGroup(0.1)]), b = walkOf([xfadeGroup(0.9)]);
  assert.deepEqual(classifyStabilityDrift(a, b), { hard: null, crossFade: ['root'] });
});

test('VT cross-fade: any OTHER drift stays a hard transition-not-frozen bail', () => {
  const a = walkOf([xfadeGroup(0.1)]);
  const b = walkOf([xfadeGroup(0.9, { transform: 'matrix(1, 0, 0, 1, 5, 0)' })]);
  const { hard } = classifyStabilityDrift(a, b);
  assert.equal(hard.path, 'groups.0.transform');
  // …and an unstable NAME order is a walker artifact, never a cross-fade.
  const c = walkOf([xfadeGroup(0.9, { name: 'other' })]);
  assert.ok(classifyStabilityDrift(a, c).hard);
});

test('VT cross-fade: a leaf-opacity drift on a NON-complementary pair is still hard', () => {
  const mk = (o, n) => walkOf([grp({
    old: { opacity: String(o), mixBlendMode: 'plus-lighter' },
    new: { opacity: String(n), mixBlendMode: 'plus-lighter' },
  })]);
  const { hard, crossFade } = classifyStabilityDrift(mk(1, 1), mk(0.5, 1));
  assert.equal(hard.path, 'groups.0.old.opacity');
  assert.deepEqual(crossFade, []);
});

test('VT cross-fade plan: two IDENTICAL snapshots collapse to ONE box', () => {
  const w = walkOf([xfadeGroup(0.3)]);
  const solved = { 'root|old': solvedFlat(), 'root|new': solvedFlat() };
  const { bail, plan } = planViewTransitionBake(w, solved, ['root']);
  assert.equal(bail, undefined);
  assert.equal(plan.boxes[0].leaves.length, 1);
  assert.equal(plan.boxes[0].leaves[0].which, 'cross-fade');
  // The pair's own opacities sum to 1, so NO leaf opacity is emitted…
  assert.equal(plan.boxes[0].leaves[0].props.opacity, undefined);
  assert.equal(plan.boxes[0].leaves[0].props['background-color'], 'rgb(0, 128, 0)');
});

test('VT cross-fade plan: the chain ABOVE the pair is still folded onto the box', () => {
  const w = walkOf([xfadeGroup(0.3, { imagePair: { opacity: '0.5' } })]);
  const solved = { 'root|old': solvedFlat(), 'root|new': solvedFlat() };
  const { plan } = planViewTransitionBake(w, solved, ['root']);
  assert.equal(plan.boxes[0].leaves[0].props.opacity, '0.5');
});

test('VT cross-fade plan bail: a pair whose snapshots DIFFER has no frozen state', () => {
  const w = walkOf([xfadeGroup(0.3)]);
  const solved = {
    'root|old': solvedFlat(),
    'root|new': solvedFlat({ rgba: { r: 255, g: 0, b: 0, a: 1 } }),
  };
  assert.match(planViewTransitionBake(w, solved, ['root']).bail,
    /^cross-fade-not-invariant 'root'/);
  // A rect difference is just as disqualifying as a colour one.
  const solved2 = {
    'root|old': solvedFlat(),
    'root|new': solvedFlat({ rect: { x: 0, y: 0, w: 100, h: 99 } }),
  };
  assert.match(planViewTransitionBake(w, solved2, ['root']).bail,
    /^cross-fade-not-invariant 'root'/);
});

test('VT cross-fade plan: the raster refusal and the missing-solve bail still apply to the pair', () => {
  const w = walkOf([xfadeGroup(0.3)]);
  assert.match(planViewTransitionBake(w, { 'root|old': solvedFlat() }, ['root']).bail,
    /missing snapshot solve for root\/new/);
  assert.match(planViewTransitionBake(w, {
    'root|old': solvedFlat({ uniform: false, distinct: 4, coverage: 61 }),
    'root|new': solvedFlat(),
  }, ['root']).bail, /non-uniform-snapshot root\/old \(4 colours, 61% flat\)/);
});

test('VT cross-fade plan: an un-flagged group keeps the wave-38 both-leaves-painted bail', () => {
  // The default third argument is empty, so every pre-existing caller and
  // every future one that does not opt in decides exactly as wave 38 did.
  const w = walkOf([xfadeGroup(0.3)]);
  const solved = { 'root|old': solvedFlat(), 'root|new': solvedFlat() };
  assert.match(planViewTransitionBake(w, solved).bail,
    /mix-blend-mode 'plus-lighter' with both leaves painted on 'root'/);
});

// ── 7. planViewTransitionBake ───────────────────────────────────────────────

const SOLVED_ROOT_OLD = { 'root|old': solvedFlat() };

function planOneRoot(over = {}, solved = null) {
  const g = grp({ new: { visibility: 'hidden' }, ...over });
  return planViewTransitionBake(walkOf([g]), solved ?? SOLVED_ROOT_OLD);
}

test('VT plan: the happy path emits the root group with its used matrix carried VERBATIM', () => {
  const { bail, plan } = planOneRoot({ transform: 'matrix(1, 0, 0, 1, 12, 34)' });
  assert.equal(bail, undefined);
  assert.equal(plan.boxes.length, 1);
  assert.equal(plan.boxes[0].props.transform, 'matrix(1, 0, 0, 1, 12, 34)');
  assert.equal(plan.boxes[0].props['transform-origin'], '50px 50px');
  assert.equal(plan.boxes[0].leaves.length, 1);
  assert.equal(plan.boxes[0].leaves[0].props['background-color'], 'rgb(0, 128, 0)');
  assert.deepEqual(plan.root, {
    width: 358, height: 568, background: null, canvasBackground: null,
  });
});

test('VT plan: paint order is root first, then document order, orphans last', () => {
  const groups = [
    grp({ name: 'c', documentIndex: 9, new: { visibility: 'hidden' } }),
    grp({ name: 'orphan', documentIndex: -1, new: { visibility: 'hidden' },
      left: '300px', top: '400px' }),
    grp({ name: 'root', documentIndex: 4, new: { visibility: 'hidden' } }),
    grp({ name: 'b', documentIndex: 1, new: { visibility: 'hidden' } }),
  ];
  const solved = Object.fromEntries(
    ['root', 'b', 'c', 'orphan'].map((n) => [`${n}|old`, solvedFlat()]));
  const { bail, plan } = planViewTransitionBake(walkOf(groups), solved);
  assert.equal(bail, undefined);
  assert.deepEqual(plan.boxes.map((b) => b.name), ['root', 'b', 'c', 'orphan']);
  assert.equal(plan.boxes[3].orphan, true);
});

test('VT plan bail: no active transition (the `view-transition-name: auto` family)', () => {
  assert.equal(planViewTransitionBake(walkOf([grp()], { active: false }), {}).bail,
    'no-active-transition');
});

test('VT plan bail: every group is a phantom', () => {
  const w = walkOf([grp({ width: 'auto', height: 'auto' })]);
  assert.equal(planViewTransitionBake(w, {}).bail, 'no-view-transition-groups');
});

test('VT plan bail: the root is not captured — the page keeps painting in place', () => {
  const w = walkOf([grp({ name: 'x', new: { visibility: 'hidden' } })], { rootCaptureName: null });
  assert.match(planViewTransitionBake(w, {}).bail, /^root-not-captured/);
  // …and the half where a root NAME exists but has no real group.
  const w2 = walkOf([grp({ name: 'x', new: { visibility: 'hidden' } })], { rootCaptureName: 'root' });
  assert.match(planViewTransitionBake(w2, {}).bail, /no group for the root capture name 'root'/);
});

test('VT plan bail: the group budget', () => {
  const groups = Array.from({ length: MAX_VT_GROUPS + 1 }, (_, i) =>
    grp({ name: i === 0 ? 'root' : `g${i}`, documentIndex: i }));
  assert.match(planViewTransitionBake(walkOf(groups), {}).bail,
    new RegExp(`group budget exceeded \\(${MAX_VT_GROUPS + 1} > ${MAX_VT_GROUPS}\\)`));
});

test('VT plan bail: oversize geometry and an unreadable transform', () => {
  assert.match(planOneRoot({ width: `${VT_MAX_SNAPSHOT_PX + 1}px` }).bail,
    /exceeds 4000px/);
  assert.match(planOneRoot({ transform: 'rotate(30deg)' }).bail,
    /unreadable transform for 'root' \(rotate\(30deg\)\)/);
});

test('VT plan bail: mix-blend-mode with BOTH leaves painted (a flat box cannot carry it)', () => {
  const g = grp({ old: { mixBlendMode: 'plus-lighter' }, new: { mixBlendMode: 'plus-lighter' } });
  const solved = { 'root|old': solvedFlat(), 'root|new': solvedFlat() };
  assert.match(planViewTransitionBake(walkOf([g]), solved).bail,
    /mix-blend-mode 'plus-lighter' with both leaves painted on 'root'/);
});

test('VT plan: a SINGLE painted leaf keeps plus-lighter — the compositing algebra collapses it', () => {
  // `isolation: isolate` on the image-pair makes the backdrop transparent
  // black, and Co = αs·Cs for every separable blend when αb = 0.
  const { bail, plan } = planOneRoot({ old: { mixBlendMode: 'plus-lighter' } });
  assert.equal(bail, undefined);
  assert.equal(plan.boxes[0].leaves.length, 1);
});

test('VT plan bail: a non-uniform snapshot the fit could NOT describe — THE RASTER REFUSAL, with its population stated', () => {
  // `two` absent (or null) is what "the fit had no answer" looks like, and it
  // is still the whole test going back to the static fixture.
  const solved = { 'root|old': solvedFlat({ uniform: false, distinct: 9, coverage: 88.7 }) };
  assert.match(planOneRoot({}, solved).bail,
    /non-uniform-snapshot root\/old \(9 colours, 88.7% flat\)/);
  const solvedNull = { 'root|old': solvedFlat({ uniform: false, distinct: 9, coverage: 88.7, two: null }) };
  assert.match(planOneRoot({}, solvedNull).bail,
    /non-uniform-snapshot root\/old \(9 colours, 88.7% flat\)/);
});

// ── 7b. The two-colour path through the plan (wave-40 T4) ───────────────────

/** A solve the fit DID describe: green with a blue band across the bottom. */
function solvedTwo(over = {}) {
  return solvedFlat({
    uniform: false, distinct: 2, coverage: 50,
    two: { kind: 'band-h', rect: { x: 0, y: 60, w: 100, h: 40 }, rgba: { r: 0, g: 0, b: 255, a: 1 }, coverage: 100 },
    ...over,
  });
}

test('VT plan: a two-colour solve emits its measured boxes instead of bailing', () => {
  const { bail, plan } = planOneRoot({}, { 'root|old': solvedTwo() });
  assert.equal(bail, undefined);
  const leaves = plan.boxes[0].leaves;
  assert.equal(leaves.length, 2);
  // The dominant's complement first, then the sub-rect — deterministic order,
  // and the two never overlap.
  assert.equal(leaves[0].props['background-color'], 'rgb(0, 128, 0)');
  assert.equal(leaves[0].props.top, '0px');
  assert.equal(leaves[0].props.height, '60px');
  assert.equal(leaves[1].props['background-color'], 'rgb(0, 0, 255)');
  assert.equal(leaves[1].props.top, '60px');
  assert.equal(leaves[1].props.height, '40px');
  // Every emitted box keeps the wave-38 declaration block, in the same order.
  assert.deepEqual(Object.keys(leaves[0].props), [
    'position', 'left', 'top', 'width', 'height', 'box-sizing', 'background-color',
  ]);
  // …and each carries the SHAPE the fit measured, for the batch log.
  assert.equal(leaves[0].shape, 'band-h');
  assert.equal(leaves[1].shape, 'band-h');
});

test('VT plan: the chain opacity folds onto EVERY box of a two-colour leaf, not just the first', () => {
  const { plan } = planOneRoot(
    { imagePair: { opacity: '0.4' }, new: { visibility: 'hidden' } },
    { 'root|old': solvedTwo() });
  for (const leaf of plan.boxes[0].leaves) assert.equal(leaf.props.opacity, '0.4');
});

test('VT plan: a transparent second region emits NO box for the hole', () => {
  const { plan } = planOneRoot({}, {
    'root|old': solvedTwo({ two: { kind: 'nested', rect: { x: 20, y: 20, w: 20, h: 20 }, rgba: null, coverage: 100 } }),
  });
  const leaves = plan.boxes[0].leaves;
  assert.equal(leaves.length, 4);                    // the four surrounding pieces
  for (const l of leaves) assert.equal(l.props['background-color'], 'rgb(0, 128, 0)');
});

test('VT cross-fade plan: a two-colour PAIR must agree box for box, sub-rect included', () => {
  const w = walkOf([xfadeGroup(0.3)]);
  // Same dominant, same band → invariant, and the pair ships as two boxes.
  const same = planViewTransitionBake(w, { 'root|old': solvedTwo(), 'root|new': solvedTwo() }, ['root']);
  assert.equal(same.bail, undefined);
  assert.equal(same.plan.boxes[0].leaves.length, 2);
  assert.equal(same.plan.boxes[0].leaves[0].which, 'cross-fade');
  // A sub-rect that MOVED between the two reads is a live cross-fade, and the
  // wave-39 invariance bail must still catch it — the pixel half of that proof
  // is exactly "the two snapshots are the same image".
  const moved = planViewTransitionBake(w, {
    'root|old': solvedTwo(),
    'root|new': solvedTwo({ two: { kind: 'band-h', rect: { x: 0, y: 50, w: 100, h: 50 }, rgba: { r: 0, g: 0, b: 255, a: 1 }, coverage: 100 } }),
  }, ['root']);
  assert.match(moved.bail, /^cross-fade-not-invariant 'root'/);
});

test('VT plan bail: a missing or errored snapshot solve is never silently skipped', () => {
  assert.match(planOneRoot({}, {}).bail, /missing snapshot solve for root\/old/);
  assert.match(planOneRoot({}, { 'root|old': { error: 'isolation window too big' } }).bail,
    /snapshot solve failed for root\/old: isolation window too big/);
});

test('VT plan bail: an orphan that OVERLAPS another painted group has unknowable z-order', () => {
  const groups = [
    grp({ name: 'root', documentIndex: 0, new: { visibility: 'hidden' } }),
    grp({ name: 'ghost', documentIndex: -1, new: { visibility: 'hidden' } }),
  ];
  const solved = { 'root|old': solvedFlat(), 'ghost|old': solvedFlat() };
  assert.match(planViewTransitionBake(walkOf(groups), solved).bail,
    /ambiguous paint order: 'ghost' has no document position and overlaps 'root'/);
});

test('VT plan: an orphan that does NOT overlap is fine — only 3 of 53 bakeable tests overlap at all', () => {
  const groups = [
    grp({ name: 'root', documentIndex: 0, new: { visibility: 'hidden' } }),
    grp({ name: 'ghost', documentIndex: -1, left: '200px', top: '200px',
      new: { visibility: 'hidden' } }),
  ];
  const solved = { 'root|old': solvedFlat(), 'ghost|old': solvedFlat() };
  assert.equal(planViewTransitionBake(walkOf(groups), solved).bail, undefined);
});

test('VT plan bail: an entirely invisible tree emits nothing rather than an empty subtree', () => {
  const g = grp({ old: { visibility: 'hidden' }, new: { visibility: 'hidden' } });
  assert.equal(planViewTransitionBake(walkOf([g]), {}).bail, 'no painted view-transition content');
});

test('VT plan: a leaf whose snapshot painted nothing emits no box', () => {
  const solved = { 'root|old': { rect: null, rgba: null, uniform: true, distinct: 0, coverage: 0 } };
  // The only group's only painted leaf has no ink → no boxes at all.
  assert.equal(planOneRoot({}, solved).bail, 'no painted view-transition content');
});

test('VT plan: group opacity rides the GROUP, image-pair×leaf opacity rides the LEAF', () => {
  const withGroupOpacity = planOneRoot({ opacity: '0.4' });
  assert.equal(withGroupOpacity.plan.boxes[0].props.opacity, '0.4');
  // …and it is NOT double-counted onto the leaf.
  assert.equal(withGroupOpacity.plan.boxes[0].leaves[0].props.opacity, undefined);
  const withLeafOpacity = planOneRoot({ imagePair: { opacity: '0.25' } });
  assert.equal(withLeafOpacity.plan.boxes[0].props.opacity, undefined);
  assert.equal(withLeafOpacity.plan.boxes[0].leaves[0].props.opacity, '0.25');
});

test('VT plan: a semi-transparent snapshot colour emits rgba(), an opaque one rgb()', () => {
  const solved = { 'root|old': solvedFlat({ rgba: { r: 1, g: 2, b: 3, a: 0.5 } }) };
  assert.equal(planOneRoot({}, solved).plan.boxes[0].leaves[0].props['background-color'],
    'rgba(1, 2, 3, 0.5)');
});

// ── 8. applyViewTransitionBakePlan ──────────────────────────────────────────

function fixtureWith(n) {
  const components = {};
  for (let i = 0; i < n; i++) components[`t__${i}`] = { id: `t__${i}`, properties: { color: 'red' } };
  return { components, _wpt: { lossy: false, lossyReasons: [] } };
}

test('VT apply: the live document is RETIRED with display:none, all of it', () => {
  const fx = fixtureWith(3);
  const { plan } = planOneRoot();
  applyViewTransitionBakePlan(fx, 't', plan);
  for (const id of ['t__0', 't__1', 't__2']) {
    assert.equal(fx.components[id].properties.display, 'none', id);
    // The original declaration survives underneath — the bake overrides the
    // paint, it does not rewrite the component.
    assert.equal(fx.components[id].properties.color, 'red');
  }
});

test('VT apply: the pseudo tree is ONE positioned, clipped subtree at the canvas origin', () => {
  const fx = fixtureWith(1);
  const { plan } = planOneRoot();
  applyViewTransitionBakePlan(fx, 't', plan);
  const root = fx.components.t__vt;
  assert.ok(root, 'the subtree is keyed <stem>__vt');
  assert.equal(root.properties.position, 'relative');
  assert.equal(root.properties.width, '358px');
  assert.equal(root.properties.height, '568px');
  // A group transformed off-viewport must not grow the composed canvas.
  assert.equal(root.properties.overflow, 'hidden');
  assert.equal(root._lossy, true);
  assert.deepEqual(root._lossyReasons, [VT_BAKE_LOSSY_REASON]);
});

test('VT apply: the ::view-transition backdrop is the FIRST child, so it paints under every group', () => {
  const fx = fixtureWith(1);
  const { plan } = planOneRoot();
  plan.root.background = 'rgb(255, 192, 203)';
  applyViewTransitionBakePlan(fx, 't', plan);
  const kids = Object.keys(fx.components.t__vt.children);
  assert.deepEqual(kids, ['t__vt__0', 't__vt__1']);
  assert.equal(fx.components.t__vt.children.t__vt__0.properties['background-color'],
    'rgb(255, 192, 203)');
  // …and the group follows it.
  assert.ok(fx.components.t__vt.children.t__vt__1.children);
});

test('VT apply: with no ::view-transition background the group takes index 0 (no empty box)', () => {
  const fx = fixtureWith(1);
  const { plan } = planOneRoot();
  applyViewTransitionBakePlan(fx, 't', plan);
  assert.deepEqual(Object.keys(fx.components.t__vt.children), ['t__vt__0']);
});

test('VT apply: the document canvas background is restated — it survives the transition', () => {
  const fx = fixtureWith(1);
  const { plan } = planOneRoot();
  plan.root.canvasBackground = 'rgb(0, 0, 255)';
  applyViewTransitionBakePlan(fx, 't', plan);
  assert.equal(fx.components.t__vt.properties['background-color'], 'rgb(0, 0, 255)');
});

test('VT apply: leaf ids nest under their group, in paint order', () => {
  const fx = fixtureWith(1);
  const g = grp();
  const solved = { 'root|old': solvedFlat(), 'root|new': solvedFlat() };
  const { plan } = planViewTransitionBake(walkOf([g]), solved);
  applyViewTransitionBakePlan(fx, 't', plan);
  const group = fx.components.t__vt.children.t__vt__0;
  assert.deepEqual(Object.keys(group.children), ['t__vt__0__0', 't__vt__0__1']);
});

test('VT apply: the honesty stamps land on the fixture and the roll-up', () => {
  const fx = fixtureWith(1);
  const { plan } = planOneRoot();
  applyViewTransitionBakePlan(fx, 't', plan);
  assert.equal(fx._wpt.viewTransitionBaked, true);
  assert.equal(fx._wpt.lossy, true);
  assert.deepEqual(fx._wpt.lossyReasons, [VT_BAKE_LOSSY_REASON]);
});

test('VT apply: the roll-up does not INVENT a lossy key on a fixture that has none', () => {
  const fx = { components: {}, _wpt: {} };
  const { plan } = planOneRoot();
  applyViewTransitionBakePlan(fx, 't', plan);
  assert.equal(fx._wpt.viewTransitionBaked, true);
  assert.equal('lossy' in fx._wpt, false);
});

test('VT apply: the written count covers hidden components, groups and leaves', () => {
  const fx = fixtureWith(3);
  const { plan } = planOneRoot();
  // 3 retired + 1 group + 1 leaf.
  assert.equal(applyViewTransitionBakePlan(fx, 't', plan), 5);
});

// ── 7b. css-view-transitions-2 guards (wave-41) ─────────────────────────────
//
// Measured defect: nested/group-children-sizing baked "1 group, 1 leaf" while
// Chromium's settled state paints five `match-element` bars — the walker
// enumerates the KEYWORD (whose group pseudo answers `auto`), the existence
// filter drops it silently, and the bake ships a tree with the bars deleted
// (0.8744 static → 0.7910 baked). Both guards fire BEFORE that filter.

test('VT plan: a `match-element` keyword name bails the whole test — its real groups are unaddressable', () => {
  const g = grp();
  // The walker reports the keyword as a name whose own group does not exist
  // (auto-sized) — exactly what nested/group-children-sizing produces.
  const kw = grp({ name: 'match-element', width: 'auto', height: 'auto' });
  const out = planViewTransitionBake(walkOf([g, kw]), { 'root|old': solvedFlat(), 'root|new': solvedFlat() });
  assert.match(out.bail, /generated-names.*match-element/);
});

test('VT plan: a `view-transition-name: auto` name bails the same way', () => {
  const g = grp();
  const kw = grp({ name: 'auto', width: 'auto', height: 'auto' });
  const out = planViewTransitionBake(walkOf([g, kw]), { 'root|old': solvedFlat(), 'root|new': solvedFlat() });
  assert.match(out.bail, /generated-names.*auto/);
});

test('VT plan: an existing ::view-transition-group-children pseudo bails — nested children are invisible to the isolation', () => {
  // The nesting container as the walker reports it: real px on the
  // group-children probe (measured 200px×200px on `clipper`).
  const g = grp({ groupChildren: { width: '200px', height: '200px' } });
  const out = planViewTransitionBake(walkOf([g]), { 'root|old': solvedFlat(), 'root|new': solvedFlat() });
  assert.match(out.bail, /nested-group-tree.*'root'/);
});

test('VT plan: a flat tree with the group-children probe answering `auto` does NOT bail', () => {
  // The wave-38/39/40 walks carried no groupChildren field at all; the new
  // field with `auto` must be equally inert — both spellings of "flat".
  const g = grp({ groupChildren: { width: 'auto', height: 'auto' } });
  const out = planViewTransitionBake(walkOf([g]), { 'root|old': solvedFlat(), 'root|new': solvedFlat() });
  assert.equal(out.bail, undefined);
  assert.ok(out.plan);
});

// ── 8b. The frame-ring stamp (wave-41) ──────────────────────────────────────
//
// The measured defect: 15 baked tests failing in a cluster at SSIM ≈0.9172
// whose diff is 100% the 16px frame ring (interior 0 differing px of
// 203 344 — new-content-is-empty-div et al.), because the author's
// `::view-transition { background }` reaches the viewport edge in the settled
// state, the ref pipeline's padColorFor frames the REF in that colour, and
// the composed canvases paint their frame from the ONE channel the bake never
// wrote: the body-root component's background.

/** A solid W×H PNG buffer, with an optional per-pixel override — the settled
 *  screenshot stand-in the ring sampler reads. */
function solidPng(w, h, [r, g, b], override = null) {
  const png = new PNG({ width: w, height: h });
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const o = (y * w + x) * 4;
      const c = override?.(x, y) ?? [r, g, b];
      png.data[o] = c[0]; png.data[o + 1] = c[1]; png.data[o + 2] = c[2];
      png.data[o + 3] = 255;
    }
  }
  return PNG.sync.write(png);
}

test('VT frame-ring: a uniform non-white ring answers its rgb() string', () => {
  // The measured cluster colour: lightpink, the section’s commonest backdrop.
  assert.equal(frameRingColor(solidPng(20, 20, [255, 182, 193])), 'rgb(255, 182, 193)');
});

test('VT frame-ring: a WHITE ring answers null — the pipeline default already matches', () => {
  // Stamping white would churn fixture bytes for zero pixel change; every
  // currently-passing baked test is this case, so null is the no-regression
  // guarantee, pinned.
  assert.equal(frameRingColor(solidPng(20, 20, [255, 255, 255])), null);
});

test('VT frame-ring: a non-uniform ring answers null — mirroring the ref pad fallback', () => {
  // One corner differing breaks padColorFor’s 8-point uniformity, which
  // falls back to CANVAS_BG — exactly what the ref side does, so the two
  // pads agree by construction.
  const buf = solidPng(20, 20, [255, 182, 193], (x, y) => (x === 0 && y === 0 ? [0, 128, 0] : null));
  assert.equal(frameRingColor(buf), null);
});

test('VT apply: the frame ring lands on an EXISTING body-root’s background, nothing else moves', () => {
  const fx = fixtureWith(1);
  // The extractor’s own body-root shape: role marker + a property bag.
  fx.components.t__body = { properties: { 'font-size': '20px', 'background-color': 'rgb(1, 2, 3)' }, _role: 'body-root' };
  const { plan } = planOneRoot();
  applyViewTransitionBakePlan(fx, 't', plan, 'rgb(255, 182, 193)');
  const br = fx.components.t__body;
  // Overridden, not merged: the ring IS the settled state’s visible canvas.
  assert.equal(br.properties['background-color'], 'rgb(255, 182, 193)');
  // The rest of the bag survives (the bake overrides paint, never rewrites).
  assert.equal(br.properties['font-size'], '20px');
  // Retired like every live component (step 1 ran over it too).
  assert.equal(br.properties.display, 'none');
  // No SECOND body-root was minted.
  assert.equal(Object.values(fx.components).filter((c) => c._role === 'body-root').length, 1);
});

test('VT apply: with NO body-root the stamp mints the minimal one, provenance-marked', () => {
  const fx = fixtureWith(1);
  const { plan } = planOneRoot();
  applyViewTransitionBakePlan(fx, 't', plan, 'rgb(255, 192, 203)');
  const br = fx.components.t__body;
  assert.ok(br, 'minted at the extractor’s own id shape, <stem>__body');
  // The canvases’ lookup key is the ROLE, so the mint must carry it.
  assert.equal(br._role, 'body-root');
  assert.equal(br.properties['background-color'], 'rgb(255, 192, 203)');
  // Paints nothing itself — the ring is delivered through the canvas
  // resolver’s PROPERTY read, exactly like the display:none’d live boxes.
  assert.equal(br.properties.display, 'none');
  // Same honesty stamp the wrapper subtree carries.
  assert.equal(br._lossy, true);
  assert.deepEqual(br._lossyReasons, [VT_BAKE_LOSSY_REASON]);
});

test('VT apply: the stamp counts as one written component', () => {
  const fx = fixtureWith(3);
  const { plan } = planOneRoot();
  // 3 retired + 1 group + 1 leaf + 1 body-root mint.
  assert.equal(applyViewTransitionBakePlan(fx, 't', plan, 'rgb(255, 182, 193)'), 6);
});

test('VT apply: NO frameRing argument keeps the fixture byte-identical to the wave-40 shape', () => {
  // The default-argument path is the one every existing caller and golden
  // exercises; a body-root mint appearing without a measured ring would be
  // the silent fallthrough this repo forbids.
  const fx = fixtureWith(1);
  const { plan } = planOneRoot();
  applyViewTransitionBakePlan(fx, 't', plan);
  assert.equal('t__body' in fx.components, false);
});

// ── 9. String shapes ────────────────────────────────────────────────────────

test('VT: hexToComputedRgb answers getComputedStyle\'s own spelling, and refuses garbage loudly', () => {
  assert.equal(hexToComputedRgb('#FFFFFF'), 'rgb(255, 255, 255)');
  assert.equal(hexToComputedRgb('#0a0B0c'), 'rgb(10, 11, 12)');
  assert.throws(() => hexToComputedRgb('white'), /not a #RRGGBB colour/);
  assert.throws(() => hexToComputedRgb('#fff'), /not a #RRGGBB colour/);
});

test('VT: snapshotColorCss keeps the common case the simplest string', () => {
  assert.equal(snapshotColorCss({ r: 1, g: 2, b: 3, a: 1 }), 'rgb(1, 2, 3)');
  assert.equal(snapshotColorCss({ r: 1, g: 2, b: 3, a: 0.25 }), 'rgba(1, 2, 3, 0.25)');
});

// ── 9b. The /common/ shim's surface (wave-39 A4) ────────────────────────────

test('VT shim: every global the corpus calls is supplied, and each is on `window`', () => {
  // Enumerated from the corpus, not guessed — the census behind the module's
  // banner. `waitForCompositorReady` is the biggest single one (28 calls) and
  // the sole reason 26 of the 30 never-settled tests never settled.
  for (const fn of [
    'takeScreenshot', 'takeScreenshotDelayed', 'takeScreenshotOnAnimationsReady',
    'failIfNot', 'waitForAtLeastOneFrame', 'waitForCompositorReady',
  ]) {
    assert.match(REFTEST_WAIT_SHIM, new RegExp(`function ${fn}\\(`), `defines ${fn}`);
    assert.match(REFTEST_WAIT_SHIM, new RegExp(`window\\.${fn} = ${fn};`), `exports ${fn}`);
  }
});

test('VT shim: the settle signal is the reftest-wait class removal, and nothing can hang', () => {
  assert.match(REFTEST_WAIT_SHIM, /classList\.remove\('reftest-wait'\)/);
  // takeScreenshotOnAnimationsReady must shoot on REJECTION too: a cancelled
  // animation would otherwise hold the page until VT_SETTLE_TIMEOUT_MS.
  assert.match(REFTEST_WAIT_SHIM, /Promise\.all\(ready\)\.then\(takeScreenshot, takeScreenshot\)/);
});

test('VT shim: waitForCompositorReady promotes NO layer — the CPU-raster contract', () => {
  // WPT's original animates document.body, which forces a compositing layer
  // and would change the pixels the alpha solve measures under
  // --disable-gpu-rasterization. The shim is a frame barrier instead.
  assert.doesNotMatch(REFTEST_WAIT_SHIM, /\.animate\(/);
  assert.match(REFTEST_WAIT_SHIM,
    /function waitForCompositorReady\(\)\{ return waitForAtLeastOneFrame\(\); \}/);
});

test('VT: isolationCss silences every other group and pins the measured one at the origin', () => {
  const css = isolationCss('target', 'old', '#000000', { x: 12.345, y: -6 });
  assert.match(css, /::view-transition-group\(\*\) \{ opacity: 0 !important; \}/);
  assert.match(css, /::view-transition-group\(target\)/);
  assert.match(css, /transform: translate\(12\.35px, -6px\) !important/);
  assert.match(css, /left: 0 !important; top: 0 !important/);
  // The fixed ::view-transition box IS the backdrop — it is `inset: 0` on the
  // snapshot containing block and the caller refuses a window bigger than the
  // viewport, so one opaque rule covers every pixel the clip can read.
  assert.match(css, /::view-transition \{ background: #000000 !important; \}/);
  // …and the PAGE's own background is never touched (wave-39 A4). The wave-38
  // `:where(html, body)` belt-and-braces rule repainted a LIVE
  // `::view-transition-new` leaf with the backdrop, so its alpha solve read
  // "nothing painted" and the whole new-content half of the tree was dropped.
  // This is the regression pin for that deletion.
  assert.doesNotMatch(css, /html/);
  assert.doesNotMatch(css, /\bbody\b/);
  // The measured leaf is the ONLY one left painting, with blending disabled.
  assert.match(css, /::view-transition-old\(\*\), ::view-transition-new\(\*\) \{ opacity: 0 !important; \}/);
  assert.match(css, /::view-transition-old\(target\)/);
  assert.match(css, /mix-blend-mode: normal !important/);
});

// ── 10. CLI wiring ──────────────────────────────────────────────────────────

const EXTRACT_SRC = readFileSync(join(__dirname, 'extract-fixture.mjs'), 'utf8');

test('VT wiring: the bake is opt-in by flag OR env, and the flag is stripped from the inputs', () => {
  assert.match(EXTRACT_SRC, /process\.argv\.includes\('--vt-bake'\)/);
  assert.match(EXTRACT_SRC, /process\.env\.VT_BAKE === '1'/);
  assert.match(EXTRACT_SRC, /a !== '--post-load' && a !== '--bidi-bake' && a !== '--vt-bake'/);
});

test('VT wiring: the module is imported LAZILY so the static path never pays puppeteer+pngjs', () => {
  assert.match(EXTRACT_SRC, /vtBakeEnabled \? await import\('\.\/view-transition-bake\.mjs'\) : null/);
});

test('VT wiring: the shared browser is closed in a finally, so a throw cannot hang the process', () => {
  assert.match(EXTRACT_SRC, /if \(vtBake\) await vtBake\.closeViewTransitionBakeBrowser\(\)/);
});

test('VT wiring: a browser fault still WRITES the static fixture — the bail-to-static contract', () => {
  // The regression this pins: an uncaught throw from the bake skipped
  // writeFixturePair entirely, so a section run silently LOST tests the pure
  // static path handles fine (7 such faults in one 53-test batch —
  // _diag38/N5/bake3.log). The catch must sit around the bake call only, and
  // must not borrow a word any successful outcome uses.
  const region = EXTRACT_SRC.slice(EXTRACT_SRC.indexOf('let vtNote'));
  assert.match(region, /catch \(vtErr\) \{\s*\n\s*vtNote = ` \[vt-bake: errored/);
  const writeAt = region.indexOf('await writeFixturePair(result)');
  const catchAt = region.indexOf('catch (vtErr)');
  assert.ok(catchAt !== -1 && writeAt !== -1 && catchAt < writeAt,
    'the vt-bake catch must precede the fixture write');
});

test('VT wiring: the bake runs LAST, after post-load, bidi and the counter bake', () => {
  const at = (needle) => EXTRACT_SRC.indexOf(needle);
  assert.ok(at('postLoad.postLoadAugmentFixture') < at('bidiBake.bidiBakeFixture'));
  assert.ok(at('bidiBake.bidiBakeFixture') < at('counterBake.bakeCounterStyles'));
  assert.ok(at('counterBake.bakeCounterStyles') < at('vtBake.viewTransitionBakeFixture'));
});

test('VT wiring: the module recycles a WEDGED browser so one sick renderer cannot condemn the batch', () => {
  const src = readFileSync(join(__dirname, 'view-transition-bake.mjs'), 'utf8');
  assert.match(src, /function discardWedgedBrowser\(\)/);
  // Handle dropped FIRST, close fired unawaited: a wedged browser is exactly
  // the one whose close() can itself hang.
  assert.match(src, /_browser = null;\s*\n\s*if \(dead\) dead\.close\(\)\.catch\(\(\) => \{\}\);/);
  // Both the open path and the mid-bake path recycle.
  assert.ok(src.split('discardWedgedBrowser();').length - 1 >= 2,
    'both the page-open and mid-bake failure paths must recycle');
});

test('VT wiring: both batch logs report the two-colour SHAPE, not just a bigger leaf count', () => {
  // The fit turns one bail into up to five boxes. Without the shape in the
  // log, the only visible trace is "leaves went up", which is exactly the kind
  // of silent widening this module's bail taxonomy exists to prevent.
  const src = readFileSync(join(__dirname, 'view-transition-bake.mjs'), 'utf8');
  assert.match(src, /shapes: plan\.boxes\.flatMap/);
  assert.match(src, /two-colour \$\{\[\.\.\.new Set\(outcome\.shapes\)\]\.sort\(\)\.join\('\/'\)\}/);
  assert.match(EXTRACT_SRC, /two-colour \$\{\[\.\.\.new Set\(outcome\.shapes\)\]\.sort\(\)\.join\('\/'\)\}/);
});

test('VT wiring: the two-colour sub-rect is re-based by the SAME window offset as the outer rect', () => {
  // The driver is the only place the isolation translate is undone, and it is
  // not unit-drivable without a browser — so this is a source pin. A sub-rect
  // left in window coordinates would place the second region's box at the
  // wrong offset on every non-zero-offset window (the twelve
  // `pseudo-with-classes-*` shapes are exactly those).
  const src = readFileSync(join(__dirname, 'view-transition-bake.mjs'), 'utf8');
  assert.match(src, /if \(s\.rect\) s\.rect = \{ \.\.\.s\.rect, x: s\.rect\.x - win\.offset\.x, y: s\.rect\.y - win\.offset\.y \};/);
  assert.match(src, /x: s\.two\.rect\.x - win\.offset\.x,\s*\n\s*y: s\.two\.rect\.y - win\.offset\.y,/);
});

test('VT wiring: the header and section-runner.sh AGREE about whether VT_BAKE is pinned', () => {
  // Doc-drift pin. The module header shipped claiming section-runner pinned
  // VT_BAKE=1 while the script did not, which is the kind of claim that gets
  // believed instead of checked. Whichever way a future wave settles it, both
  // sides must move together.
  const src = readFileSync(join(__dirname, 'view-transition-bake.mjs'), 'utf8');
  const runner = readFileSync(join(__dirname, 'section-runner.sh'), 'utf8');
  const runnerPins = /VT_BAKE=1/.test(runner);
  const headerClaimsPinned = /section-runner\.sh pins VT_BAKE=1/.test(src);
  const headerClaimsNotPinned = /section-runner\.sh does NOT yet pin VT_BAKE=1/.test(src);
  assert.ok(headerClaimsPinned || headerClaimsNotPinned,
    'the header must state one way or the other');
  assert.equal(headerClaimsPinned, runnerPins,
    runnerPins
      ? 'section-runner.sh pins VT_BAKE=1 — the header must say so'
      : 'section-runner.sh does NOT pin VT_BAKE=1 — the header must not claim it does');
  // The two pinned bakes are still pinned — this test must not pass by the
  // whole activation block having been deleted.
  assert.match(runner, /POST_LOAD_EXTRACT=1 BIDI_BAKE=1/);
});
