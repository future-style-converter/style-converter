// threshold-derivation.test.mjs — pins the DERIVATION MATHS of the
// threshold study, not the captures. Every assertion is a hand-computable
// known value on synthetic images, so each test can fail three ways: the
// helper regresses, the pixelmatch option sets drift, or the shipping
// modules the study leans on (padToCanvas, computeLabDeltaE) change
// behaviour underneath it. Re-scoring the 399 committed baselines belongs
// to the study script itself (threshold-derivation.mjs), not here — a test
// that restates a distribution merely pins today's PNG bytes.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { PNG } from 'pngjs';
import pixelmatchDefault from 'pixelmatch';
import {
  SHIPPING_PIXELMATCH_OPTS, STRICT_PIXELMATCH_OPTS, EDGE_GRADIENT,
  isEdgePixel, scorePair, percentile, pairFails, CANDIDATES, CALIBRATION,
  verifyAgainstRecorded, runControls,
} from './threshold-derivation.mjs';

// Same CJS interop as the module under test.
const pixelmatch = pixelmatchDefault.default ?? pixelmatchDefault;

// Build a W×H image filled with one opaque colour. Opaque matters: with
// alpha 0 pixelmatch scores EVERY colour pair as identical (the STATUS
// 2026-08-28 correction traced the wrong 132 blind-spot figure to exactly
// that trap), so a transparent fixture here would make every test vacuous.
function fill(W, H, [r, g, b]) {
  const img = new PNG({ width: W, height: H });
  for (let i = 0; i < img.data.length; i += 4) {
    img.data[i] = r; img.data[i + 1] = g; img.data[i + 2] = b; img.data[i + 3] = 255;
  }
  return img;
}

// Paint an axis-aligned rectangle of one colour into an image, in place.
function rect(img, x0, y0, w, h, [r, g, b]) {
  for (let y = y0; y < y0 + h; y++) {
    for (let x = x0; x < x0 + w; x++) {
      const i = (y * img.width + x) * 4;
      img.data[i] = r; img.data[i + 1] = g; img.data[i + 2] = b; img.data[i + 3] = 255;
    }
  }
  return img;
}

// ── The two pixelmatch option sets ──────────────────────────────────────────

test('shipping opts keep the measured 66/255 uniform blind spot; strict opts close it', () => {
  // Uniform grey shifts on an 8×8 opaque field: the YIQ budget at threshold
  // 0.25 works out to d > 66.0 (chroma rows cancel; 0.5053·d² > 2200.94).
  const count = (d, opts) => pixelmatch(fill(8, 8, [0, 0, 0]).data, fill(8, 8, [d, d, d]).data, null, 8, 8, opts);
  assert.equal(count(65, SHIPPING_PIXELMATCH_OPTS), 0, 'shipping must be silent at 65 — boundary moved');
  assert.equal(count(66, SHIPPING_PIXELMATCH_OPTS), 64, 'shipping must fire at 66 — boundary moved');
  // Strict budget: 35215·0.02² = 14.09 → uniform d > 5.28. 5 silent, 6 fires.
  assert.equal(count(5, STRICT_PIXELMATCH_OPTS), 0, 'strict must be silent at 5/255');
  assert.equal(count(6, STRICT_PIXELMATCH_OPTS), 64, 'strict must fire at 6/255');
});

test('strict opts run the AA detector, shipping opts disable it', () => {
  // pixelmatch's includeAA flag means "SKIP AA detection" — the inversion
  // the corrected comment in compare-screenshots.mjs documents. Pin the
  // wiring on both sides so a flag flip cannot pass silently.
  assert.equal(SHIPPING_PIXELMATCH_OPTS.includeAA, true, 'shipping: AA detection must stay OFF (numbers recorded that way)');
  assert.equal(STRICT_PIXELMATCH_OPTS.includeAA, false, 'strict: AA detection must stay ON — its whole point');
  // Behavioural half: an antialiased-shaped pixel (grey step between a dark
  // and a bright region, differing between the images) is counted under
  // shipping-with-0.02 but excused once the detector runs. Geometry: a
  // vertical black/white edge whose boundary column carries the mid-grey on
  // one side only — pixelmatch's antialiased() sees a pixel with both
  // darker and brighter 8-neighbours and ≥3 equal neighbours, its
  // definition of AA (Vysniauskas 2009).
  const W = 9, H = 9;
  const a = fill(W, H, [255, 255, 255]); rect(a, 0, 0, 4, H, [0, 0, 0]);
  const b = fill(W, H, [255, 255, 255]); rect(b, 0, 0, 4, H, [0, 0, 0]);
  rect(b, 4, 0, 1, H, [128, 128, 128]);                      // AA-like column on b only
  const noAA = pixelmatch(a.data, b.data, null, W, H, { threshold: 0.02, includeAA: true });
  const withAA = pixelmatch(a.data, b.data, null, W, H, { threshold: 0.02, includeAA: false });
  assert.ok(noAA > 0, 'without the detector the AA column must count');
  assert.ok(withAA < noAA, 'the detector must excuse at least part of the AA column');
});

// ── Edge/flat classification ────────────────────────────────────────────────

test('isEdgePixel: flat interiors are not edges, hard boundaries are', () => {
  const img = fill(10, 10, [30, 30, 30]); rect(img, 3, 3, 4, 4, [200, 200, 200]);
  assert.equal(isEdgePixel(img, 0, 0, 10, 10), false, 'background interior is flat');
  assert.equal(isEdgePixel(img, 4, 4, 10, 10), false, 'box interior is flat');
  assert.equal(isEdgePixel(img, 3, 4, 10, 10), true, 'box boundary column is an edge');
  assert.equal(isEdgePixel(img, 2, 4, 10, 10), true, 'pixel touching the boundary from outside is an edge');
  // The gradient constant is load-bearing: a step of exactly EDGE_GRADIENT
  // must NOT count (strictly-greater comparison, mirroring the module).
  const soft = fill(10, 10, [100, 100, 100]); rect(soft, 5, 0, 5, 10, [100 + EDGE_GRADIENT, 100, 100]);
  assert.equal(isEdgePixel(soft, 4, 5, 10, 10), false, 'a step of exactly EDGE_GRADIENT is sub-edge');
});

test('scorePair classifies a flat recolour vs a 1px shift correctly', async () => {
  // Flat recolour: same 10×10 box, interior colour moved 40/255 — every
  // strict-diff pixel is interior, so edgeShare must be ~0 and dE95 must
  // fire while the SHIPPING pixel metric (blind below 66) reads 0.
  const bg = [26, 26, 46];                                    // canonical capture ground
  const a1 = fill(40, 40, bg); rect(a1, 10, 10, 20, 20, [200, 60, 60]);
  const b1 = fill(40, 40, bg); rect(b1, 10, 10, 20, 20, [160, 60, 60]);
  const flatRow = await scorePair(a1, b1);
  assert.equal(flatRow.dpxCur, 0, 'a 40/255 recolour must be invisible to the shipping pixel metric');
  assert.ok(flatRow.dpxStrict >= 20, 'the strict metric must count the whole box (25% of canvas)');
  assert.ok(flatRow.edgeShare <= 0.2, `recolour must classify flat, got edgeShare ${flatRow.edgeShare}`);
  assert.ok(flatRow.dE95 > 5, `a 40-point red shift on 25% of pixels must move dE95, got ${flatRow.dE95}`);
  // 1px shift: same box drawn one column over — the diff is two boundary
  // strips, everything about it is edge-class.
  const a2 = fill(40, 40, bg); rect(a2, 10, 10, 20, 20, [200, 60, 60]);
  const b2 = fill(40, 40, bg); rect(b2, 11, 10, 20, 20, [200, 60, 60]);
  const edgeRow = await scorePair(a2, b2);
  assert.ok(edgeRow.edgeShare >= 0.8, `1px shift must classify edge, got edgeShare ${edgeRow.edgeShare}`);
  assert.equal(edgeRow.dE95, 0, 'a 2-column diff (5% of pixels) sits under the p95 support floor by construction');
});

test('scorePair pads size mismatches with the sentinel (and the await held)', async () => {
  // A half-height capture against a full one: the padded region is magenta
  // vs content, so the diff must be huge. If padToCanvas were not awaited
  // this throws (no .data on a Promise); if the pad used the background
  // colour this would score ~0 — the exact under-size blindness the
  // sentinel exists to kill. 32px so ssim.js's 11×11 fast window fits.
  const short = fill(32, 32, [26, 26, 46]);
  const tall = fill(32, 64, [26, 26, 46]);
  const row = await scorePair(short, tall);
  assert.ok(row.dpxCur >= 45, `padded half must diff, got dpxCur ${row.dpxCur}%`);
});

// ── Candidate evaluation maths ──────────────────────────────────────────────

test('pairFails: boundary semantics and null-handling mirror pairRegressed', () => {
  const cand = { ssim: 0.95, dpxCur: 1, dE95: 2, dpxStrict: 3.5 };
  const pass = { ssim: 0.95, dpxCur: 1, dE95: 2, dpxStrict: 3.5 };
  assert.equal(pairFails(pass, cand), false, 'sitting exactly ON every boundary passes (ssim <, others >)');
  assert.equal(pairFails({ ...pass, ssim: 0.9499 }, cand), true, 'ssim strictly below fails');
  assert.equal(pairFails({ ...pass, dpxCur: 1.001 }, cand), true, 'dpxCur strictly above fails');
  assert.equal(pairFails({ ...pass, dE95: 2.001 }, cand), true, 'dE95 strictly above fails');
  assert.equal(pairFails({ ...pass, dpxStrict: 3.501 }, cand), true, 'dpxStrict strictly above fails');
  // A missing metric must never manufacture a failure on its own — the same
  // rule pairRegressed applies so an old manifest cannot fail retroactively.
  assert.equal(pairFails({ ssim: null, dpxCur: null, dE95: null, dpxStrict: null }, cand), false, 'all-null row passes');
  // A candidate that does not gate a metric ignores even a terrible value.
  assert.equal(pairFails({ ...pass, dpxStrict: 99 }, { ...cand, dpxStrict: null }, ), false, 'ungated metric is ignored');
});

test('the candidate table gates what it claims to gate', () => {
  assert.equal(new Set(CANDIDATES.map((c) => c.id)).size, CANDIDATES.length, 'candidate ids must be unique');
  const current = CANDIDATES.find((c) => c.id === 'C0-current');
  // C0 must stay the shipped gate verbatim — it is the control row of the
  // catch/cost table; if the shipped thresholds change, change C0 WITH them.
  assert.deepEqual({ ssim: current.ssim, dpxCur: current.dpxCur, dE95: current.dE95, dpxStrict: current.dpxStrict }, { ssim: 0.95, dpxCur: 2, dE95: 5, dpxStrict: null });
  // The blur acid test, pinned as maths: blur's weakest live pair read dE95
  // 2.287 (recomputed from 215b377b^). The current gate must MISS it and
  // every dE-tightened candidate must CATCH it — that asymmetry is the
  // entire reason the study exists.
  const blurWeakest = { ssim: 0.975, dpxCur: 0.032, dE95: 2.287, dpxStrict: 5.253 };
  assert.equal(pairFails(blurWeakest, current), false, 'C0 passing blur is the finding, not a bug in the test');
  for (const id of ['C1-dE2', 'C3-dE2-px1', 'C4-proposal']) {
    assert.equal(pairFails(blurWeakest, CANDIDATES.find((c) => c.id === id)), true, `${id} must catch the weakest blur pair`);
  }
});

test('percentile matches computeLabDeltaE p95 index formula', () => {
  const v = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
  assert.equal(percentile(v, 0.95), 9, 'floor(0.95·9)=8 → value 9, NOT interpolated 9.55');
  assert.equal(percentile(v, 0), 1); assert.equal(percentile(v, 1), 10);
  assert.equal(percentile([], 0.95), null, 'empty population has no percentile');
});

// ── Calibration bookkeeping ─────────────────────────────────────────────────

test('verifyAgainstRecorded flags drift beyond tolerance and accepts within', () => {
  const rec = { dE95: 23.35, ssim: 0.9573 };
  assert.deepEqual(verifyAgainstRecorded({ dE95: 23.352, ssim: 0.9573 }, rec, { dE95: 0.005, ssim: 0.0005 }), [], 'within tolerance verifies');
  assert.equal(verifyAgainstRecorded({ dE95: 23.5, ssim: 0.9573 }, rec, { dE95: 0.005, ssim: 0.0005 }).length, 1, 'a drifted dE must be flagged');
  assert.equal(verifyAgainstRecorded({ dE95: null, ssim: 0.9573 }, rec, { dE95: 0.005 }).length, 1, 'a metric that bailed cannot silently verify');
});

test('every calibration entry is recomputable-or-pinned, never neither', () => {
  for (const cal of CALIBRATION) {
    if (cal.pinnedOnly) {
      // Pinned entries must carry the readings they claim, for every pair.
      for (const p of cal.pairs) assert.ok(cal.pinned?.[p], `${cal.bug}: pinned entry missing readings for ${p}`);
      assert.equal(cal.rev, null, `${cal.bug}: pinned entries have no rev to recompute from`);
    } else {
      // Recomputable entries name a source: a git rev (pre-fix bytes) or
      // live: true (current baselines, for bugs still in the tree).
      assert.ok(cal.rev !== undefined, `${cal.bug}: recomputable entry must state its rev (null = working tree)`);
      assert.ok(cal.rev !== null || cal.live, `${cal.bug}: rev null without live:true would silently score FIXED bytes as the bug`);
    }
  }
});

test('the controls themselves pass on the real pipeline', async () => {
  // runControls() is the study's own broken-pipeline detector; running it
  // here means `node --test` fails the moment padToCanvas stops being
  // awaited, the sentinel changes colour, or a pixelmatch upgrade moves the
  // blind-spot boundary — without waiting for someone to rerun the study.
  assert.deepEqual(await runControls(), [], 'known-value controls must hold');
});
