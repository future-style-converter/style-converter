// tools/titan/novel-ink-palette.mjs
//
// ── wave-49 NOVEL-INK: the colour-space half ─────────────────────────────────
//
// Split out of novel-ink.mjs at the per-file size rule (CLAUDE.md: target ≤200
// lines, split past ~300). novel-ink.mjs owns the MEASUREMENT — which pixels
// disagree, what fraction of the capture's paint there is novel, and the two
// bars. This file owns the COLOUR VOCABULARY it measures against: quantisation,
// bin→representative-colour, closure of a palette under blending, and the
// perceptual distance query. Nothing here knows what a capture or a reference
// is, and nothing here names a hue.
//
// The one non-obvious idea is blend closure. Antialiasing, subpixel coverage
// and image resampling all produce colours that lie on a LINE SEGMENT between
// two colours the source paints, because compositing is a convex blend (and
// browser/Skia text AA composites in NON-LINEAR sRGB — hence the segments are
// sampled in sRGB byte space, not in linear light). Measuring distance to the
// palette's blend set rather than to its points is what stops a grey halo
// between black text and a white page from reading as an invented colour.

import { converter, differenceCiede2000 } from 'culori';

// One converter/difference pair per module load (culori's factories allocate).
// lab65 + CIEDE2000 is exactly what compare-screenshots-metrics' computeLabDeltaE
// uses, so every ΔE printed by this family is on one scale.
const toLab = converter('lab65');
const deltaE = differenceCiede2000();

/** sRGB byte triple → culori lab65 colour. */
export const labOf = ([r, g, b]) => toLab({ mode: 'rgb', r: r / 255, g: g / 255, b: b / 255 });

/** Build the bin arithmetic for a given quantisation width. `bits` bits per
 *  channel → (1<<bits) levels per axis; the caller picks the width so that one
 *  bin is exactly the pipeline's "same colour" tolerance. Returned as a closure
 *  rather than a module constant so the width stays a single caller-owned
 *  number instead of being duplicated here. */
export function binner(bits) {
  const shift = 8 - bits;          // channel byte → bin index on one axis
  const axis = 1 << bits;          // bins per axis
  return (r, g, b) => ((r >> shift) * axis + (g >> shift)) * axis + (b >> shift);
}

/** Accumulate a colour histogram: bin id → { px, r, g, b } running sums, so a
 *  bin's representative colour can be the MEAN of the pixels that fell in it —
 *  more faithful than the bin centre when a bin straddles two real colours. */
export function accumulate(map, binOf, r, g, b) {
  const key = binOf(r, g, b);
  const e = map.get(key);
  // Hot path: one Map lookup, no allocation after a bin's first pixel.
  if (e) { e.px++; e.r += r; e.g += g; e.b += b; return; }
  map.set(key, { px: 1, r, g, b });
}

/** Histogram entry → mean sRGB triple (the bin's representative colour). */
export const meanRgb = (e) => [e.r / e.px, e.g / e.px, e.b / e.px];

/** Close a palette under pairwise blending and return the result in Lab:
 *  every palette colour, plus `samples`-many points along every pairwise sRGB
 *  segment. Sampled rather than solved because the distance below is
 *  CIEDE2000 — a non-Euclidean metric with no closed-form point-to-segment
 *  projection. Cost is O(K² · samples) Lab conversions, which is why the
 *  caller caps K. */
export function blendSetLab(paletteRgb, samples) {
  const out = paletteRgb.map(labOf);
  for (let i = 0; i < paletteRgb.length; i++) {
    for (let j = i + 1; j < paletteRgb.length; j++) {
      const [ar, ag, ab] = paletteRgb[i];
      const [br, bg, bb] = paletteRgb[j];
      // Interior samples only — t=0 and t=1 are the endpoints, already added.
      for (let s = 1; s < samples - 1; s++) {
        const t = s / (samples - 1);
        out.push(labOf([ar + (br - ar) * t, ag + (bg - ag) * t, ab + (bb - ab) * t]));
      }
    }
  }
  return out;
}

/** Minimum CIEDE2000 distance from one sRGB colour to a whole Lab point set.
 *  The answer to "how far is this colour from anything the reference paints,
 *  or from any blend of two things it paints?". */
export function minDeltaE(rgb, blendLab) {
  const c = labOf(rgb);
  let best = Infinity;
  for (const p of blendLab) {
    const d = deltaE(c, p);
    if (d < best) best = d;
  }
  return best;
}
