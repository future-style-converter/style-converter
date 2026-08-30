// tools/titan/novel-ink.mjs
//
// ── wave-49 NOVEL-INK: the area-normalized wrong-answer detector ─────────────
//
// WHY THIS MODULE EXISTS
// The corpus scorer's colour veto (inject-wpt-block.mjs computeColorFailed)
// is armed by `colorDivergent`, which is a per-channel WHOLE-CANVAS histogram
// KL test. Histogram KL is coverage-weighted by construction: a colour swap
// confined to a small patch moves the whole-canvas distribution by an amount
// proportional to that patch's area, so it cannot clear a fixed KL bar no
// matter how total the swap is. Measured on wave48-final,
// css-view-transitions/hit-test-unrelated-element: the reference paints a
// 100x100 GREEN square, all three captures paint the SAME square in pure red,
// and the pair scores ssim 1.0000 / histogramKL {r:0.0141,g:0.0591,b:0} —
// under the 0.1 bar — so colorDivergent is false and the pair is a PASS.
// 10000 pixels at a full 255-per-channel delta, recorded as perfect.
//
// THE FIX, STATED AS A MEASUREMENT
// Normalize on the DIVERGENT REGION, not on the canvas. Ask a colour-agnostic
// question: *of the pixels where the two images disagree, how much of the
// capture's paint is in a colour the reference never uses?* A displacement
// (text shifted 2px, a box 3px wider) fills the divergent region with colours
// that ARE in the reference's palette, so it scores ~0. A hue swap fills it
// with a colour the reference never painted, so it scores ~1.
//
// COLOUR-AGNOSTIC BY CONSTRUCTION
// Nothing below names a hue, a channel, or a WPT prose string. The reference's
// own palette is the only vocabulary; "novel" is defined purely as perceptual
// distance from that palette. Red-vs-green is merely this corpus's dominant
// instance of the class — and not the only one it catches in practice: the
// measured true positive selectors/invalidation/class-id-attr (web) is a
// BLACK-for-green text swap, novel class (0,0,0) at CIEDE2000 43.1, pinned in
// novel-ink.test.mjs precisely so this claim is checkable rather than asserted.
//
// WHICH DISAGREEMENT? — the denominator, and why it is the capture's INK
// The ratio is normalized on the divergent pixels where the CAPTURE PAINTED
// SOMETHING, not on all divergent pixels. Divergent pixels where the capture
// left blank canvas are a MISSING-ink defect, already owned by the presence
// and coverage-ratio vetoes; folding them in here would halve the ratio for
// every "wrong colour AND wrong place" render. Measured on wave48-final: with
// the all-divergent denominator, 64 of the 179 known wrong-colour passes land
// in a tight 0.36–0.49 band — exactly the ~½ a disjoint moved-and-recoloured
// square produces (novel square + equal-area blank-where-ref-inked region).
// The bars below were NOT changed to accommodate that; the denominator was
// corrected to measure the intended quantity.
//
// ANTIALIASING DEFENCE — palette closure under blending
// AA, subpixel coverage and image resampling all produce colours that lie on a
// LINE SEGMENT between two colours the source actually paints (compositing is
// a convex blend, and browser/Skia text AA composites in non-linear sRGB —
// hence segments are sampled in sRGB below, not in linear light). A hue swap
// lies on no such segment. So the reference "palette" is expanded to its
// pairwise blend set before distance is measured; a grey halo between the
// ref's black text and its white canvas is therefore NOT novel ink, while a
// red square against a white/blue/green reference is (its nearest blend point
// is ~255 units away in sRGB).

// The shared "is this pixel the same as that pixel" per-channel slack (8/255)
// every other presence/overflow metric in this pipeline already uses. Reusing
// it means "divergent pixel" here and "ink pixel" in the presence gate agree.
import { SEMANTIC_PRESENCE_TOLERANCE } from '../visual/compare-screenshots-metrics.mjs';
// Colour vocabulary: quantisation, bin→representative colour, palette blend
// closure and the perceptual distance query. See that file's banner for why
// blend closure is the antialiasing defence.
import { binner, accumulate, meanRgb, blendSetLab, minDeltaE } from './novel-ink-palette.mjs';

// ── Pre-registered constants (wave-49; fixed BEFORE any measurement) ─────────

// Per-channel slack for calling a pixel "divergent". Same 8/255 as above.
export const NOVEL_INK_TOLERANCE = SEMANTIC_PRESENCE_TOLERANCE;
// Colour quantisation: 5 bits/channel → 32 levels → bin width 8, deliberately
// equal to NOVEL_INK_TOLERANCE so two colours in one bin are, by the
// pipeline's own definition, the same colour.
export const NOVEL_INK_BIN_BITS = 5;
// A colour counts as "used by the reference" once it covers this % of the
// reference. Below it the colour is stray AA speckle, not part of the palette.
export const NOVEL_INK_REF_PALETTE_MIN_PCT = 0.01;
// Cap on palette size: the blend set is O(K^2), and the top-mass colours carry
// essentially all of a reftest reference's ink. Bounds the per-pair cost.
export const NOVEL_INK_PALETTE_MAX = 24;
// VALIDITY PRECONDITION, not a sensitivity knob. "This colour is absent from
// the reference" is only a sound claim if we actually enumerated the
// reference's colours; the floor + cap above can truncate a colour-rich
// reference (a smooth hue sweep has thousands of occupied bins, and a hue
// sweep is NOT a linear sRGB blend of its endpoints, so blend closure does not
// recover it). When the retained palette explains less than this share of the
// reference's pixels the pair is UNJUDGEABLE and the veto stays silent.
// Refuted the alternative empirically: with no such guard,
// css-images/gradient/gradient-decreasing-hue-hsl (android, wave48-final)
// fires at novelFraction 1.0 on 275 divergent pixels of gradient
// interpolation noise, against a reference whose top-24 bins cover a small
// fraction of its rainbow. Opening the two PNGs shows the render is correct.
export const NOVEL_INK_PALETTE_COVERAGE_MIN = 0.95;
// Samples taken along each palette-pair segment (endpoints included).
export const NOVEL_INK_BLEND_SAMPLES = 9;
// A capture colour is NOVEL when its CIEDE2000 distance to the whole blend set
// is at least this. 20 is ~8.7x the just-noticeable-difference (2.3, the value
// computeColorFailed already uses) and 2x the conventional "these are clearly
// different colours" bar of 10 — deliberately conservative, so a merely
// mis-tinted render is not novel ink, only an unmistakably different colour.
export const NOVEL_INK_DELTA_E_MIN = 20;
// A novel colour must be a coherent MARK, not scatter: a colour class is only
// counted when it holds at least this fraction of the divergent region.
export const NOVEL_INK_CLASS_MIN_FRACTION = 0.005;
// Veto bar 1 — novel ink must be the MAJORITY of the capture's paint inside
// the disagreement. Below this the pair's divergence is mostly displacement or
// shape (paint in colours the reference does use), not a wrong answer.
export const NOVEL_INK_FRACTION_MIN = 0.5;
// Veto bar 2 — the novel ink must be a visible mark: at least this % of the
// frame, i.e. 5x the presence gate's 0.02% "the reference visibly has ink"
// floor, so an AA fringe can never arm the veto on its own.
export const NOVEL_INK_ABS_MIN_PCT = 0.1;

// The capture canvas both sides are padded onto — WHITE, the corpus-v4
// boundary (inject-wpt-block's WPT_CANVAS_BG / padToCanvas fill / every
// platform's WPT capture mode). Used ONLY to tell "the capture painted here"
// from "the capture left the page blank here"; it names a canvas, not a hue,
// so the check stays colour-agnostic about what gets painted ON it.
export const NOVEL_INK_CANVAS_BG = Object.freeze({ r: 0xFF, g: 0xFF, b: 0xFF });
const CANVAS = NOVEL_INK_CANVAS_BG;

// Quantiser + colour-vocabulary helpers live in novel-ink-palette.mjs (split
// at the per-file size rule). binOf is built here so the bin width stays tied
// to NOVEL_INK_BIN_BITS, the one constant that defines it.
const binOf = binner(NOVEL_INK_BIN_BITS);

/**
 * Compute the novel-ink block for a (capture, reference) pair.
 *
 * `a` = the platform CAPTURE, `b` = the browser REFERENCE — the same argument
 * order diffWebVsRef uses, both already padded to one canvas by padToCanvas.
 * Returns `null` when the pair cannot be judged (shape mismatch, or a
 * reference with no palette at all), which callers must read as UNKNOWN, not
 * as failed — the same "unknown ≠ divergent" stance every other gate here
 * takes.
 */
export function computeNovelInk(a, b) {
  // Shape guard: the metric compares pixel i to pixel i, so differing buffer
  // lengths mean the caller did not pad — refuse rather than guess.
  if (!a?.data || !b?.data || a.data.length !== b.data.length) return null;
  const totalPx = a.data.length / 4;
  if (totalPx === 0) return null;

  const refBins = new Map();   // reference palette histogram (whole canvas)
  const capBins = new Map();   // capture histogram, DIVERGENT INK PIXELS ONLY
  let divergentPx = 0;         // every disagreeing pixel
  let divergentInkPx = 0;      // …of which the CAPTURE actually painted something
  for (let i = 0; i < a.data.length; i += 4) {
    // Fully transparent on either side → the colour underneath is undefined;
    // skipped exactly as computeLabDeltaE skips it.
    if (a.data[i + 3] === 0 || b.data[i + 3] === 0) continue;
    const br = b.data[i], bg = b.data[i + 1], bb = b.data[i + 2];
    accumulate(refBins, binOf, br, bg, bb);
    const ar = a.data[i], ag = a.data[i + 1], ab = a.data[i + 2];
    // Divergent = any channel differs by more than the shared tolerance.
    // Same per-channel max form as countOverflowInk's ink test.
    if (Math.abs(ar - br) <= NOVEL_INK_TOLERANCE
      && Math.abs(ag - bg) <= NOVEL_INK_TOLERANCE
      && Math.abs(ab - bb) <= NOVEL_INK_TOLERANCE) continue;
    divergentPx++;
    // DENOMINATOR CHOICE (see the banner's "which disagreement?" note): a
    // divergent pixel where the capture painted BLANK CANVAS is a MISSING-ink
    // defect, which the presence + coverage-ratio vetoes already own. This
    // check asks only about ink the capture DID lay down, so blank-capture
    // divergence is excluded from both numerator and denominator.
    if (Math.abs(ar - CANVAS.r) <= NOVEL_INK_TOLERANCE
      && Math.abs(ag - CANVAS.g) <= NOVEL_INK_TOLERANCE
      && Math.abs(ab - CANVAS.b) <= NOVEL_INK_TOLERANCE) continue;
    divergentInkPx++;
    accumulate(capBins, binOf, ar, ag, ab);
  }

  // Nothing disagrees (or the capture painted no ink where it disagrees) →
  // nothing can be novel. Report the zero honestly rather than returning null:
  // this is a KNOWN answer, not an unknown one.
  if (divergentInkPx === 0) {
    return { divergentPx, divergentInkPx: 0, novelPx: 0, novelFractionOfDivergentInk: 0,
      novelFractionOfDivergent: 0, novelPct: 0, refPaletteSize: refBins.size,
      paletteCoveragePct: 1, novelClasses: [] };
  }

  // Palette = the reference's own colours above the mass floor, richest first,
  // capped. Mass floor is a % of the canvas, matching how every other coverage
  // number in this pipeline is expressed.
  const paletteFloorPx = (NOVEL_INK_REF_PALETTE_MIN_PCT / 100) * totalPx;
  const kept = [...refBins.values()]
    .filter((e) => e.px >= paletteFloorPx)
    .sort((x, y) => y.px - x.px)
    .slice(0, NOVEL_INK_PALETTE_MAX);
  const palette = kept.map(meanRgb);
  // A reference with no colour above the floor is degenerate (or empty) — we
  // have no vocabulary to call anything novel against. UNKNOWN, not failed.
  if (palette.length === 0) return null;
  // How much of the reference the retained palette actually explains. Read by
  // the predicate as the validity precondition (see the constant's banner).
  let refCounted = 0;
  for (const e of refBins.values()) refCounted += e.px;
  let paletteCovered = 0;
  for (const e of kept) paletteCovered += e.px;
  const paletteCoveragePct = refCounted > 0 ? paletteCovered / refCounted : 0;
  const blendLab = blendSetLab(palette, NOVEL_INK_BLEND_SAMPLES);

  // Score each coherent capture colour class in the divergent region.
  const classFloorPx = NOVEL_INK_CLASS_MIN_FRACTION * divergentInkPx;
  let novelPx = 0;
  const novelClasses = [];
  for (const e of capBins.values()) {
    if (e.px < classFloorPx) continue;          // scatter, not a mark
    const rgb = meanRgb(e);
    const d = minDeltaE(rgb, blendLab);
    if (d < NOVEL_INK_DELTA_E_MIN) continue;    // a colour the ref uses (or blends to)
    novelPx += e.px;
    novelClasses.push({ rgb: rgb.map((v) => Math.round(v)), px: e.px, deltaE: +d.toFixed(2) });
  }
  // Diagnostics first (biggest novel class first) so a manifest row explains
  // itself without re-reading the captures.
  novelClasses.sort((x, y) => y.px - x.px);
  return {
    divergentPx,
    divergentInkPx,
    novelPx,
    // THE GATING RATIO — novel paint as a share of the capture's paint inside
    // the disagreement.
    novelFractionOfDivergentInk: +(novelPx / divergentInkPx).toFixed(4),
    // Kept for triage/comparability with the wave-49 v1 measurement: the same
    // numerator over ALL divergent pixels (blank-capture ones included).
    novelFractionOfDivergent: +(novelPx / divergentPx).toFixed(4),
    novelPct: +((novelPx / totalPx) * 100).toFixed(4),
    refPaletteSize: palette.length,
    // Share of the reference explained by that palette — the veto's validity
    // precondition, kept in the block so a manifest row shows WHY it abstained.
    paletteCoveragePct: +paletteCoveragePct.toFixed(4),
    novelClasses: novelClasses.slice(0, 3),
  };
}

/** The veto predicate: true when the capture's disagreement with the reference
 *  is MOSTLY paint in a colour the reference never uses, and that paint is a
 *  visible mark rather than a fringe. Pure + exported for the unit pins.
 *  `null`/absent block → false (UNKNOWN ≠ FAILED). */
export function computeNovelInkFailed(novelInk) {
  if (!novelInk || typeof novelInk !== 'object') return false;
  const frac = novelInk.novelFractionOfDivergentInk;
  const pct = novelInk.novelPct;
  const cov = novelInk.paletteCoveragePct;
  // Non-numeric fields (defensive: hand-edited manifests, or a block predating
  // the coverage precondition) → unknown → false.
  if (typeof frac !== 'number' || typeof pct !== 'number' || typeof cov !== 'number') return false;
  // Validity first: we may only call a colour absent from a reference whose
  // palette we actually enumerated.
  if (cov < NOVEL_INK_PALETTE_COVERAGE_MIN) return false;
  return frac >= NOVEL_INK_FRACTION_MIN && pct >= NOVEL_INK_ABS_MIN_PCT;
}
