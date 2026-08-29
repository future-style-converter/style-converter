// spec-oracle.mjs — assert each platform against SPEC-DERIVED expected values.
//
// ## Why this exists
//
// The comparison system compares three runtimes pairwise, and 2026-08-28
// proved they can all be wrong TOGETHER: filter blur/invert was wrong on both
// natives while every pairwise gate passed, and every real bug found that day
// came from comparing against the CSS SPEC, not against a sibling platform
// (docs/STATUS.md, the 2026-08-28 sections). Three renderers agreeing is
// evidence of consistency, not correctness. This module closes that class: a
// fixture component may declare what the spec says the render MUST look like,
// and each platform is judged ALONE — so the system can fail even when all
// three platforms agree.
//
// ## Fixture schema (v1 — deliberately small)
//
//     "_expect": {
//       "fill": [r, g, b],       // dominant non-page-bg colour, 0-255 ints
//       "fillTolerance": 2,      // optional, per-channel, default 2
//       "box": [w, h],           // optional: bbox of fill-coloured pixels, px
//       "boxTolerance": 1,       // optional, per-axis, default 1
//       "note": "spec citation",  // optional, printed on failure
//       "waive": {                 // optional, per-platform known-divergence
//         "iOS": "reason"          //   excuse, mirroring the cross-platform
//       }                          //   ledger's shape: excused, never silent
//     }
//
// `waive` exists for exactly one situation: a platform whose divergence from
// the spec is DOCUMENTED and accepted (e.g. the iOS sepia src-over alpha
// trade-off, ledgered 2026-08-28). A waived violation is printed as a
// warning with its reason; a waived check that PASSES is reported STALE and
// fails the run — the same two-sided discipline the cross-platform ledger
// earned after the noise-floor study, and for the same reason: the floor is
// zero, so "waived but passing" always means the waiver has outlived the
// divergence it excused.
//
// `fill` is the MOST FREQUENT colour excluding the page background — NOT
// "all non-background pixels", because the harness may render a text label
// (the component name, or `_text`); the mode statistic ignores it since the
// label is far smaller than the component fill. `box` is the bbox of pixels
// within `fillTolerance` of the DECLARED fill (not of all non-bg pixels —
// label text would pollute that). `_expect` is OPTIONAL and inert: the
// converter reads fixture keys by explicit lookup (CssParsing.parseComponent),
// so unknown `_`-prefixed keys never reach the IR, and fixtures without it
// behave byte-for-byte as before this module existed.
//
// AUTHORING HAZARD (adversarial-review note): the measurement has a
// page-background guard but NO label guard — a declared fill within
// fillTolerance of WHITE will merge the rendered white text label into the
// box bbox and fail `box` spuriously. Keep component fills ≥ ~3/channel
// away from rgb(255,255,255), or omit `box` for near-white components.
// (Nearest shipped case: Sepia_White at (255,255,239), clear by Δ16.)
//
// Near-exact assertions are legitimate here: the harness's A/A noise floor
// is exactly ZERO (noise-floor.sh — 423 captures bit-identical across
// independent runs), so the tolerances exist only for cross-platform
// rounding (three float→byte roundings may disagree by 1) and edge AA.

import { componentKey } from './cross-platform-gate.mjs';
// Single source of truth for the capture page background (#1A1A2E). The
// capture pipeline writes it exactly (see the semantic-presence rationale in
// compare-screenshots-metrics.mjs) — duplicating the constant here would be
// the drift bug waiting to happen.
import { CANONICAL_BG } from './compare-screenshots-metrics.mjs';

/** Exit code for "a render disagrees with a spec-derived _expect". Distinct
 *  from 4 (runtimes disagree with EACH OTHER) and 5 (stale ledger): this one
 *  means wrong versus the CSS spec, which no cross-platform agreement excuses. */
export const EXIT_SPEC_ORACLE_VIOLATION = 6;

/** Default per-channel fill tolerance. 1 covers a float→byte rounding
 *  disagreement; 2 adds one LSB for per-layer 8-bit quantisation in composited
 *  filter paths (measured on the iOS sepia composite — docs/STATUS.md). */
export const DEFAULT_FILL_TOLERANCE = 2;

/** Default per-axis box tolerance: rects are integer CSS px and captures 1:1
 *  device px, so 1px absorbs an AA edge row without letting a 2×-scale or
 *  clipped render through. */
export const DEFAULT_BOX_TOLERANCE = 1;

/** Per-channel band around the page background inside which a pixel is "page
 *  ground", excluded from the fill histogram. Mirrors SEMANTIC_PRESENCE_TOLERANCE
 *  (compare-screenshots-metrics.mjs): the ground is written at exactly
 *  (26,26,46), so only compositor rounding / AA bleed lands inside it. */
export const PAGE_BG_EXCLUSION_TOLERANCE = 8;

/** Is (r,g,b) inside the page-ground exclusion band around `bg`?
 *  Max-channel metric, same shape as the semantic-presence foreground test. */
function isPageBg(r, g, b, bg, tol) {
  return Math.abs(r - bg.r) <= tol && Math.abs(g - bg.g) <= tol && Math.abs(b - bg.b) <= tol;
}

/** Uniformly-prefixed authoring error — every parse failure names the
 *  component so the fix is a one-line edit, not a hunt. */
function authoringError(name, msg) {
  throw new Error(`_expect on "${name}": ${msg}`);
}

/**
 * Walk a fixture document and collect every component's `_expect` into a Map
 * keyed by component NAME — index-free, because capture filenames carry a
 * positional `{NNN}_` prefix that renumbers when the corpus shifts (see
 * componentKey for the measured failure that rule comes from). Children are
 * walked too: they become captures of their own in the flattened list. A
 * NAME collision throws (same rationale as the ledger's collision guard —
 * silent last-writer-wins would make one expectation vanish). Authoring
 * errors throw with the component named.
 *
 * @param {object} fixtureDoc parsed input-fixture JSON ({ components: {...} })
 * @param {{pageBg?: {r,g,b}}} [opts]
 * @returns {Map<string, {fill:number[], fillTolerance:number, box:number[]|null, boxTolerance:number, note:string|null}>}
 */
export function parseExpectations(fixtureDoc, { pageBg = CANONICAL_BG } = {}) {
  const out = new Map();
  const walk = (components) => {                    // recursive: components + nested children
    for (const [name, comp] of Object.entries(components ?? {})) {
      if (comp && typeof comp === 'object' && comp._expect !== undefined) {
        if (out.has(name)) {
          authoringError(name, 'duplicate component name — expectations key on the name, so rename one');
        }
        out.set(name, normalizeExpect(name, comp._expect, pageBg));
      }
      if (comp && typeof comp === 'object' && comp.children) walk(comp.children);
    }
  };
  walk(fixtureDoc?.components);
  return out;
}

/** Validate + default one raw `_expect`. Reads top-to-bottom as the schema. */
function normalizeExpect(name, raw, pageBg) {
  // Shape gate first — everything below indexes into it.
  if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) {
    authoringError(name, `must be an object, got ${JSON.stringify(raw)}`);
  }
  // `fill` is REQUIRED: it anchors both checks (the box is measured against
  // the declared fill, so an expectation without one asserts nothing).
  const fill = raw.fill;
  if (!Array.isArray(fill) || fill.length !== 3 ||
      !fill.every((c) => Number.isInteger(c) && c >= 0 && c <= 255)) {
    authoringError(name, `fill must be [r,g,b] with integer 0-255 channels, got ${JSON.stringify(fill)}`);
  }
  // Tolerances: non-negative finite numbers, defaulted per the constants above.
  const fillTolerance = raw.fillTolerance ?? DEFAULT_FILL_TOLERANCE;
  if (typeof fillTolerance !== 'number' || !Number.isFinite(fillTolerance) || fillTolerance < 0) {
    authoringError(name, `fillTolerance must be a non-negative number, got ${JSON.stringify(raw.fillTolerance)}`);
  }
  const boxTolerance = raw.boxTolerance ?? DEFAULT_BOX_TOLERANCE;
  if (typeof boxTolerance !== 'number' || !Number.isFinite(boxTolerance) || boxTolerance < 0) {
    authoringError(name, `boxTolerance must be a non-negative number, got ${JSON.stringify(raw.boxTolerance)}`);
  }
  // `box` is optional — colour-only expectations are legitimate (a gradient
  // midpoint probe has no meaningful fill bbox).
  const box = raw.box ?? null;
  if (box !== null && (!Array.isArray(box) || box.length !== 2 ||
      !box.every((d) => Number.isInteger(d) && d > 0))) {
    authoringError(name, `box must be [w,h] with positive integer px, got ${JSON.stringify(raw.box)}`);
  }
  // The unfailable-check guard: a declared fill inside the page-ground band
  // is invisible to measureCapture (its pixels are excluded from the
  // histogram BY CONSTRUCTION), so the assertion could never fire. A check
  // that cannot fail is worse than none — refuse at parse time.
  if (isPageBg(fill[0], fill[1], fill[2], pageBg, PAGE_BG_EXCLUSION_TOLERANCE)) {
    authoringError(name,
      `fill ${JSON.stringify(fill)} is within ±${PAGE_BG_EXCLUSION_TOLERANCE}/channel of the page background ` +
      `rgb(${pageBg.r},${pageBg.g},${pageBg.b}) — the measurement excludes page-ground pixels, so this ` +
      `expectation could never fail. Give the component a fill distinguishable from the page.`);
  }
  // `waive`: optional per-platform excuse map. Platforms must be real, and
  // every reason must be a non-empty string — a waiver without a reason is
  // indistinguishable from a shrug, and the ledger's whole value is that
  // the excuse travels with the entry.
  const waive = raw.waive ?? null;
  if (waive !== null) {
    if (typeof waive !== 'object' || Array.isArray(waive)) {
      authoringError(name, `waive must be {platform: reason}, got ${JSON.stringify(waive)}`);
    }
    for (const [plat, reason] of Object.entries(waive)) {
      if (!['iOS', 'Android', 'web'].includes(plat)) {
        authoringError(name, `waive names unknown platform "${plat}" (iOS|Android|web)`);
      }
      if (typeof reason !== 'string' || reason.trim().length === 0) {
        authoringError(name, `waive.${plat} must carry a non-empty reason string`);
      }
    }
  }
  // `note` carries the spec citation, printed verbatim on failure so a red
  // line is diagnosable (and re-derivable) from the log alone.
  const note = raw.note ?? null;
  if (note !== null && typeof note !== 'string') {
    authoringError(name, `note must be a string, got ${JSON.stringify(raw.note)}`);
  }
  return { fill, fillTolerance, box, boxTolerance, note, waive };
}

/**
 * Measure one capture against one expectation. Pure over the decoded PNG —
 * no IO, so tests build inputs with pngjs directly.
 *
 * fill — the mode (most frequent exact RGB triple) over pixels OUTSIDE the
 *   page-ground band. Mode, not mean: a solid component's interior is
 *   byte-exact, and the mode ignores both AA blend pixels and the label text
 *   (each far less frequent than the fill). Ties break to the numerically
 *   smallest packed RGB so the result is deterministic. `null` when zero
 *   non-ground pixels exist (an empty render).
 * box — bounding box [w,h] of pixels within `expect.fillTolerance` per
 *   channel of the DECLARED fill. Measured against the declaration (not the
 *   observed mode) so a wrong-colour render reports box [0,0] alongside its
 *   fill violation instead of measuring garbage. Alpha is ignored — captures
 *   are opaque by contract (padToCanvas + platform writers emit alpha 255).
 *
 * @param {import('pngjs').PNG} png decoded capture (RGBA buffer)
 * @param {{fill:number[], fillTolerance:number}} expect normalized expectation
 * @param {{r,g,b}} [pageBg] capture ground colour
 * @returns {{fill:number[]|null, fillCount:number, box:number[], boxPixels:number}}
 */
export function measureCapture(png, expect, pageBg = CANONICAL_BG) {
  const { data, width, height } = png;
  const [ef, eg, eb] = expect.fill;                 // declared fill channels
  const tol = expect.fillTolerance;                 // shared fill/box-membership knob (schema v1)
  const counts = new Map();                         // packed RGB → pixel count
  let minX = Infinity, minY = Infinity, maxX = -1, maxY = -1; // fill-match bbox
  let boxPixels = 0;                                // pixels matching the declared fill
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const i = (y * width + x) * 4;                // RGBA stride
      const r = data[i], g = data[i + 1], b = data[i + 2];
      // Histogram: only "ink on the page" participates in the mode.
      if (!isPageBg(r, g, b, pageBg, PAGE_BG_EXCLUSION_TOLERANCE)) {
        const packed = (r << 16) | (g << 8) | b;
        counts.set(packed, (counts.get(packed) ?? 0) + 1);
      }
      // Bbox membership: within tolerance of the DECLARED fill on every channel.
      if (Math.abs(r - ef) <= tol && Math.abs(g - eg) <= tol && Math.abs(b - eb) <= tol) {
        boxPixels++;
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
      }
    }
  }
  // Mode extraction with the deterministic tie-break documented above.
  let bestPacked = -1, bestCount = 0;
  for (const [packed, count] of counts) {
    if (count > bestCount || (count === bestCount && packed < bestPacked)) {
      bestPacked = packed;
      bestCount = count;
    }
  }
  const fill = bestPacked < 0 ? null
    : [(bestPacked >> 16) & 0xff, (bestPacked >> 8) & 0xff, bestPacked & 0xff];
  // Zero matches → [0,0], which reads as "no such colour anywhere" in the log.
  const box = boxPixels === 0 ? [0, 0] : [maxX - minX + 1, maxY - minY + 1];
  return { fill, fillCount: bestCount, box, boxPixels };
}

/**
 * Evaluate every expectation against every platform's capture — EACH
 * PLATFORM ALONE. That is the whole point: two platforms agreeing on the
 * wrong value must BOTH appear as violations, which no pairwise comparison
 * can produce. Pure given `opts.getPng`: the comparator injects a disk
 * reader; tests inject an in-memory lookup.
 *
 * @param {object[]} rows comparator rows ({name, platforms:{p:{present,...}}})
 * @param {Map} expectations parseExpectations() output
 * @param {{getPng:(platform:string,captureName:string)=>object|null,
 *          pageBg?:{r,g,b}, platforms?:string[]}} opts
 * @returns {{skipped:false, checked:number, violations:object[], missing:object[]}}
 */
export function evaluateOracle(rows, expectations, opts) {
  const { getPng, pageBg = CANONICAL_BG, platforms = ['iOS', 'Android', 'web'] } = opts;
  const violations = [];                            // {platform, component, field, expected, actual, delta, tolerance, note}
  const waived = [];                                // violations excused by _expect.waive — warned, not fatal
  const stale = [];                                 // waivers whose platform now PASSES — fatal (two-sided)
  const missing = [];                               // {component, platform|null, reason} — reported, never crashed on
  const matched = new Set();                        // expectation names that found a row
  let checked = 0;                                  // platform×component measurements performed
  for (const row of rows) {
    // Capture names are `{NNN}_{Name}.png`; expectations key on the bare name.
    const base = componentKey(row.name).replace(/\.png$/, '');
    const expect = expectations.get(base);
    if (!expect) continue;                          // no expectation → not our business
    matched.add(base);
    for (const p of platforms) {
      const info = row.platforms?.[p];
      // A platform that captured nothing (SKIP_* run, capture failure,
      // decode error) is REPORTED as missing, not treated as a violation —
      // the capture-count and decode guards own that failure class.
      if (!info || info.present === false) {
        missing.push({ component: base, platform: p, reason: info?.error ? `decode error: ${info.error}` : 'no capture' });
        continue;
      }
      // Injected reader; a throw here is a broken file, same bucket as absent.
      let png = null;
      try { png = getPng(p, row.name); } catch (e) { missing.push({ component: base, platform: p, reason: `unreadable: ${e.message ?? e}` }); continue; }
      if (!png) { missing.push({ component: base, platform: p, reason: 'no capture' }); continue; }
      checked += 1;
      const m = measureCapture(png, expect, pageBg);
      // Per-platform waiver: violations on this platform are excused (with
      // the reason attached) instead of fatal — and if the platform passes
      // BOTH checks, the waiver itself is stale and reported below.
      const waiveReason = expect.waive?.[p] ?? null;
      const sink = waiveReason ? waived : violations;
      const violationsBefore = sink.length;
      // ── fill check — two-sided per-channel band around the declared value.
      if (m.fill === null) {
        // An empty render is a violation, not a missing: the platform DID
        // produce a capture, and it contains no component at all.
        sink.push({ platform: p, component: base, field: 'fill', expected: expect.fill, actual: null, delta: null, tolerance: expect.fillTolerance, note: expect.note });
      } else {
        // Δ = worst channel; > tolerance fails in either direction.
        const delta = Math.max(...expect.fill.map((c, i) => Math.abs(m.fill[i] - c)));
        if (delta > expect.fillTolerance) {
          sink.push({ platform: p, component: base, field: 'fill', expected: expect.fill, actual: m.fill, delta, tolerance: expect.fillTolerance, note: expect.note });
        }
      }
      // ── box check — only when declared; per-axis two-sided band.
      if (expect.box) {
        const delta = [Math.abs(m.box[0] - expect.box[0]), Math.abs(m.box[1] - expect.box[1])];
        if (delta[0] > expect.boxTolerance || delta[1] > expect.boxTolerance) {
          sink.push({ platform: p, component: base, field: 'box', expected: expect.box, actual: m.box, delta, tolerance: expect.boxTolerance, note: expect.note });
        }
      }
      if (waiveReason) {
        if (sink.length === violationsBefore) {
          // The waived platform passed everything: the waiver has outlived
          // the divergence it excused. Two-sided, like the gate's ledger.
          stale.push({ platform: p, component: base, reason: waiveReason });
        } else {
          // Attach the reason to each excused record so the warning line
          // explains ITSELF — an excuse that has to be looked up elsewhere
          // decays into folklore.
          for (let i = violationsBefore; i < sink.length; i++) sink[i].waiveReason = waiveReason;
        }
      }
    }
  }
  // An expectation that matched no row is the oracle silently checking
  // nothing — surfaced per entry; the comparator additionally hard-fails
  // when checked === 0, so a fully-unbound oracle can never read as green.
  for (const name of expectations.keys()) {
    if (!matched.has(name)) missing.push({ component: name, platform: null, reason: 'no capture row matched this expectation (renamed or dropped component?)' });
  }
  return { skipped: false, checked, violations, waived, stale, missing };
}

/** One diagnosable line per violation: expected vs actual vs delta plus the
 *  spec note — a failure must be actionable from the log alone. */
export function formatViolation(v) {
  const note = v.note ? ` — ${v.note}` : '';
  if (v.field === 'fill') {
    const actual = v.actual === null ? 'NOTHING (no non-background pixels)' : `rgb(${v.actual.join(',')})`;
    const delta = v.delta === null ? '' : ` · Δmax ${v.delta}`;
    return `${v.platform} · ${v.component} · fill — expected rgb(${v.expected.join(',')}) ±${v.tolerance}/channel · measured ${actual}${delta}${note}`;
  }
  // box: per-axis deltas so "shaved 1px" and "rendered at 2×" read differently.
  return `${v.platform} · ${v.component} · box — expected ${v.expected[0]}×${v.expected[1]} ±${v.tolerance}px · measured ${v.actual[0]}×${v.actual[1]} · Δ ${v.delta[0]}×${v.delta[1]}${note}`;
}

/** One line per missing measurement, so an oracle that skipped a platform
 *  says so instead of silently narrowing its own coverage. */
export function formatMissing(m) {
  return m.platform
    ? `${m.component} · ${m.platform} — ${m.reason}`
    : `${m.component} — ${m.reason}`;
}
