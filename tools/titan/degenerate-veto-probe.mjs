#!/usr/bin/env node
//
// tools/titan/degenerate-veto-probe.mjs — the DISPLACEMENT-AWARE successor to
// the disarmed novel-ink veto (BACKLOG obligation #4), shipped DISARMED.
//
// WHY A SUCCESSOR. novel-ink.mjs asks "of the capture's paint inside the
// disagreement, how much is in a colour the reference never uses?" and divides
// by ALL of that paint. Its two measured miss mechanisms are (a) renders that
// are wrong-colour AND displaced — the divergent region then fills with the
// reference's OWN palette, so the numerator collapses — and (b) small marks
// that clear the fraction bar but not the mass bar. BACKLOG obligation #4
// names the fix for (a): a DISPLACEMENT-AWARE DENOMINATOR — exclude the
// divergent pixels that a rigid translation of reference ink already explains,
// then measure what is left. This file is that measurement.
//
// WHAT IT ASKS, IN ONE SENTENCE. Of the pixels where the two images disagree,
// how much paint is left once every pixel that a small rigid translation of
// the reference explains has been removed — in BOTH directions (paint the
// capture added, and reference ink the capture dropped)?
//
// COLOUR-AGNOSTIC AND TEST-AGNOSTIC BY CONSTRUCTION. Nothing below names a
// hue, a channel, a WPT prose string or a test id. The "canvas" is derived as
// the reference's own modal colour, never asserted; "explained" is defined
// only against the reference's own paint. The probe therefore says nothing
// about what a correct render looks like — only whether the capture's
// disagreement with its reference is explicable as a small displacement.
//
// STATUS: DISARMED. `degenerateVetoFailed()` is pure and always computable;
// `isArmed()` gates whether a caller should act on it, and returns true only
// under TITAN_DEGENERATE_VETO=1. The measured result of the pre-registered
// rule is in tools/titan/results/wave50-B12/README.md — it MISSED its recall
// bar, so arming it would trade one dishonest signal for another.
//
// Usage:
//   node tools/titan/degenerate-veto-probe.mjs <runId|runDir> [--json out.json]
//                                              [--section <sec>] [--cells cells.json]
// `--cells` takes a file with {sample:[{section,test,platform,capture,ref}]}
// (the shape tools/titan/results/wave50-B12/draw-sample.mjs emits) and probes
// exactly those cells instead of walking every manifest.

import { readFileSync, readdirSync, existsSync, writeFileSync } from 'node:fs';
import { resolve, join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';
// The ONE canonical stem derivation — composed captures are named
// `wpt__<section>__<fixtureStem>.png` (split-combined-ir.mjs / composedPngName).
import { safe, fixtureStem } from './safe-name.mjs';
// The shared "is this pixel the same as that pixel" per-channel slack (8/255)
// every presence/overflow/novel-ink metric in this pipeline already uses.
// Reusing it means "divergent" here and "divergent" there are the same word.
import { SEMANTIC_PRESENCE_TOLERANCE } from '../visual/compare-screenshots-metrics.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(__dirname, '..', '..');

// ── Pre-registered constants (wave 50; fixed BEFORE any measurement) ─────────

// Per-channel slack for calling a pixel divergent, and for calling two colours
// the same when testing whether a translation explains one by the other.
export const DEGENERATE_TOLERANCE = SEMANTIC_PRESENCE_TOLERANCE;
// The rigid-translation slack, in pixels (Chebyshev). A divergent pixel is
// "explained" when the reference carries the same colour within this radius —
// i.e. when a translation of at most R pixels moves reference ink onto it.
// R = 4 is four times the largest rasteriser jitter measured across the three
// byte-identical-picture groups of the wave50-B12 hand sample (<= 1 px of row
// extent), so nothing the model excuses is larger than a displacement a reader
// would call a defect. Raising it excuses real defects; lowering it re-admits
// rasteriser noise. It is a model parameter, not a sensitivity knob.
export const DEGENERATE_SHIFT_RADIUS = 4;
// Veto bar 1 — the unexplained paint must be the MAJORITY of the disagreement.
// 0.50 is novel-ink's own NOVEL_INK_FRACTION_MIN, reused verbatim so the
// successor is comparable to the instrument it replaces and is not tuned.
export const DEGENERATE_FRACTION_MIN = 0.5;
// Veto bar 2 — the unexplained paint must be a visible mark, not a fringe.
// 0.10 % of the frame is novel-ink's own NOVEL_INK_ABS_MIN_PCT, likewise reused
// verbatim. (This bar is the known miss mechanism (b); it is kept anyway,
// because lowering it is exactly the move obligation #4 forbids.)
export const DEGENERATE_ABS_MIN_PCT = 0.1;
// Colour quantisation used ONLY to find the reference's modal colour (the
// canvas). 5 bits/channel gives a bin width of 8 — deliberately equal to the
// tolerance above, so two colours in one bin are, by this pipeline's own
// definition, the same colour.
const CANVAS_BIN_BITS = 5;

/** The scorer's eligibility idiom, verbatim (BACKLOG "scorer idiom"): a cell
 *  counts only when it carries a numeric ssim and is not score-excluded. */
export function isScored(d) { return !!d && typeof d.ssim === 'number' && !d.scoreExcluded; }

/** Composed capture filename for a manifest test key. */
export function captureName(testRel) {
  const section = testRel.split('/')[1];                       // css/<section>/...
  return `${safe(`wpt__${section}__${fixtureStem(testRel)}`)}.png`;
}

/** The reference's modal colour, as an {r,g,b}. This is the probe's "canvas":
 *  derived from the image in front of us, never asserted, so the check stays
 *  colour-agnostic about what the corpus happens to pad onto. */
export function modalColour(png) {
  const bins = new Map();                                      // quantised bin → {n, r, g, b}
  const d = png.data;
  for (let i = 0; i < d.length; i += 4) {                      // whole frame, alpha ignored (opaque composites)
    const k = ((d[i] >> (8 - CANVAS_BIN_BITS)) << (2 * CANVAS_BIN_BITS))
      | ((d[i + 1] >> (8 - CANVAS_BIN_BITS)) << CANVAS_BIN_BITS)
      | (d[i + 2] >> (8 - CANVAS_BIN_BITS));
    const e = bins.get(k) || { n: 0, r: 0, g: 0, b: 0 };
    e.n++; e.r += d[i]; e.g += d[i + 1]; e.b += d[i + 2]; bins.set(k, e);
  }
  let best = null;                                             // richest bin wins
  for (const e of bins.values()) if (!best || e.n > best.n) best = e;
  return best ? { r: best.r / best.n, g: best.g / best.n, b: best.b / best.n } : null;
}

/**
 * Compute the displacement-aware block for a (capture, reference) pair.
 * `a` = the platform CAPTURE, `b` = the browser REFERENCE — diffWebVsRef's
 * argument order. Returns `null` when the pair cannot be judged (no decode, no
 * common area, a reference with no ink at all), which callers read as UNKNOWN,
 * never as failed — the same stance every other gate in this pipeline takes.
 */
export function computeDegenerate(a, b, { tol = DEGENERATE_TOLERANCE, radius = DEGENERATE_SHIFT_RADIUS } = {}) {
  if (!a?.data || !b?.data) return null;                       // undecodable → unknown
  const W = Math.min(a.width, b.width), H = Math.min(a.height, b.height);
  if (W <= 0 || H <= 0) return null;                           // no common area → unknown
  const canvas = modalColour(b);
  if (!canvas) return null;                                    // an empty reference has no canvas → unknown
  const px = (p, x, y) => (y * p.width + x) * 4;               // row-major RGBA index
  const near = (p, i, r, g, bl) => Math.abs(p.data[i] - r) <= tol
    && Math.abs(p.data[i + 1] - g) <= tol && Math.abs(p.data[i + 2] - bl) <= tol;
  const isInk = (p, i) => !near(p, i, canvas.r, canvas.g, canvas.b); // "painted something"
  // `explained(src, dst, x, y)` — does some translation of at most `radius`
  // move INK of `src` onto the colour `dst` carries at (x,y)? This is the
  // rigid-translation model, applied locally so a per-element displacement is
  // explained as readily as a whole-frame one.
  const explained = (src, dst, x, y) => {
    const di = px(dst, x, y), r = dst.data[di], g = dst.data[di + 1], bl = dst.data[di + 2];
    for (let dy = -radius; dy <= radius; dy++) {
      const yy = y + dy; if (yy < 0 || yy >= H) continue;      // stay inside the common area
      for (let dx = -radius; dx <= radius; dx++) {
        const xx = x + dx; if (xx < 0 || xx >= W) continue;
        const si = px(src, xx, yy);
        if (isInk(src, si) && near(src, si, r, g, bl)) return true; // same paint, small offset
      }
    }
    return false;
  };
  let divergentPx = 0, divergentInkPx = 0, unexplainedInkPx = 0; // capture-side counters
  let divergentRefInkPx = 0, refDroppedPx = 0;                  // reference-side counters
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const ai = px(a, x, y), bi = px(b, x, y);
    if (near(a, ai, b.data[bi], b.data[bi + 1], b.data[bi + 2])) continue; // agrees → nothing to explain
    divergentPx++;
    if (isInk(a, ai)) {                                        // the capture painted here
      divergentInkPx++;
      // DENOMINATOR CHOICE: a divergent pixel the reference explains by a small
      // translation of its own ink leaves BOTH numerator and denominator.
      if (!explained(b, a, x, y)) unexplainedInkPx++;
    }
    if (isInk(b, bi)) {                                        // the reference painted here
      divergentRefInkPx++;
      // The other half of the same question: reference ink the capture did not
      // reproduce anywhere nearby is DROPPED content, not a displacement.
      if (!explained(a, b, x, y)) refDroppedPx++;
    }
  }
  const total = W * H;                                          // common-area denominator for the % bars
  const frac = (n, d) => (d > 0 ? +(n / d).toFixed(4) : 0);     // 0/0 is a known zero, not an unknown
  return {
    frameW: W, frameH: H,
    frameMismatch: a.width !== b.width || a.height !== b.height, // recorded, never silently cropped away
    shiftRadius: radius,
    divergentPx, divergentInkPx, divergentRefInkPx,
    // THE DISPLACEMENT-AWARE NUMERATOR/DENOMINATOR PAIR, capture side.
    unexplainedInkPx, unexplainedInkFraction: frac(unexplainedInkPx, divergentInkPx),
    unexplainedInkPct: +((unexplainedInkPx / total) * 100).toFixed(4),
    // …and reference side: content the capture dropped rather than moved.
    refDroppedPx, refDroppedFraction: frac(refDroppedPx, divergentRefInkPx),
    refDroppedPct: +((refDroppedPx / total) * 100).toFixed(4),
  };
}

/** The PRE-REGISTERED predicate (wave 50, fixed before measurement; see
 *  tools/titan/results/wave50-B12/README.md §"Pre-registration"). True when the
 *  capture's disagreement with its reference is MOSTLY paint no small rigid
 *  translation explains, in either direction, and that paint is a visible mark.
 *  `null`/absent block → false (UNKNOWN is never FAILED). */
export function degenerateVetoFailed(block) {
  if (!block || typeof block !== 'object') return false;        // unknown → false
  const uf = block.unexplainedInkFraction, up = block.unexplainedInkPct;
  const rf = block.refDroppedFraction, rp = block.refDroppedPct;
  if ([uf, up, rf, rp].some((v) => typeof v !== 'number')) return false; // hand-edited/older block → unknown
  const added = uf >= DEGENERATE_FRACTION_MIN && up >= DEGENERATE_ABS_MIN_PCT;   // paint that is not a move
  const dropped = rf >= DEGENERATE_FRACTION_MIN && rp >= DEGENERATE_ABS_MIN_PCT; // ink that went nowhere
  return added || dropped;
}

/** Arming gate. The probe is SHIPPED DISARMED: it measures and stamps, and a
 *  caller may act on it only under TITAN_DEGENERATE_VETO=1. Kept as a function
 *  (not a constant) so a test can flip the variable and observe both answers. */
export function isArmed(env = process.env) { return env.TITAN_DEGENERATE_VETO === '1'; }

/** Probe one explicit cell list (the `--cells` shape). Returns one row per cell
 *  with its block and the predicate's answer, so a caller can join it against
 *  hand verdicts without re-deriving anything. */
export function probeCells(cells, { repoRoot = REPO_ROOT } = {}) {
  return cells.map((c) => {
    const capPath = resolve(repoRoot, c.capture), refPath = resolve(repoRoot, c.ref);
    if (!existsSync(capPath) || !existsSync(refPath)) {         // a missing PNG is unknown, never a fire
      return { ...pick(c), block: null, fired: false, missing: true };
    }
    const block = computeDegenerate(PNG.sync.read(readFileSync(capPath)), PNG.sync.read(readFileSync(refPath)));
    return { ...pick(c), block, fired: degenerateVetoFailed(block), missing: false };
  });
}
/** The identifying fields carried through to the report (no PNG bytes). */
const pick = (c) => ({ section: c.section, test: c.test, platform: c.platform, ssim: c.ssim, n: c.n });

/** Walk a run dir's manifests and probe every scored cell. */
export function probeRun(runDir, { sections = null, repoRoot = REPO_ROOT } = {}) {
  const base = join(runDir, 'sections');
  const cells = [];
  const DIRS = { web: 'screenshots', ios: 'ios-screenshots', android: 'android-screenshots' };
  for (const sec of readdirSync(base).sort()) {
    if (sections && !sections.includes(sec)) continue;
    const mp = join(base, sec, 'manifest.json');
    if (!existsSync(mp)) continue;                              // no manifest → no cells
    const results = JSON.parse(readFileSync(mp, 'utf8')).wpt?.results ?? {};
    for (const [test, r] of Object.entries(results)) {
      const br = r.browserRef ?? {};
      if (!br.path) continue;                                   // no frozen reference → nothing to compare
      for (const [platform, dir] of Object.entries(DIRS)) {
        const d = br.diffs?.[`${platform}-ref`];
        if (!isScored(d)) continue;
        cells.push({ section: sec, test, platform, ssim: d.ssim, wptPass: d.wptPass === true,
          capture: join(base, sec, dir, captureName(test)), ref: resolve(repoRoot, br.path) });
      }
    }
  }
  const rows = probeCells(cells, { repoRoot });
  rows.forEach((row, i) => { row.wptPass = cells[i].wptPass; });  // keep the scorer's verdict alongside
  return rows;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  const arg = (n) => { const i = args.indexOf(n); return i >= 0 ? args[i + 1] : null; };
  const cellsFile = arg('--cells'), out = arg('--json');
  let rows;
  if (cellsFile) {
    // Accept either the sampler's `{sample:[…]}` or a verdict file's
    // `{cells:[…]}` — both carry {section,test,platform,capture,ref}, so the
    // committed wave50-B12 artifacts are re-runnable without a run dir.
    const doc = JSON.parse(readFileSync(cellsFile, 'utf8'));
    rows = probeCells(doc.sample ?? doc.cells ?? []);
  } else {
    const target = args.find((a) => !a.startsWith('--') && a !== out && a !== arg('--section'));
    if (!target) { console.error('usage: degenerate-veto-probe.mjs <runId|runDir> [--json out] [--section s] [--cells f]'); process.exit(2); }
    const runDir = existsSync(join(target, 'sections')) ? resolve(target) : join(__dirname, 'runs', target);
    if (!existsSync(join(runDir, 'sections'))) { console.error(`degenerate-veto-probe: no sections/ under ${runDir}`); process.exit(2); }
    rows = probeRun(runDir, { sections: arg('--section') ? [arg('--section')] : null });
  }
  const fired = rows.filter((r) => r.fired).length;
  console.log(`degenerate-veto probe: ${rows.length} cells | would-fire ${fired} | armed=${isArmed()} ` +
    `| radius ${DEGENERATE_SHIFT_RADIUS}px, bars ${DEGENERATE_FRACTION_MIN}/${DEGENERATE_ABS_MIN_PCT}%`);
  if (out) writeFileSync(out, JSON.stringify({ radius: DEGENERATE_SHIFT_RADIUS,
    fractionMin: DEGENERATE_FRACTION_MIN, absMinPct: DEGENERATE_ABS_MIN_PCT, rows }, null, 1));
}
