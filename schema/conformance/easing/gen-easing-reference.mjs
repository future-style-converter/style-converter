#!/usr/bin/env node
//
// gen-easing-reference.mjs — generate the cross-runtime easing reference table.
//
// ## What this is for
//
// An animation is a function from time to a value. Most of what can go wrong
// in that function — a wrong easing curve, a wrong step boundary, a wrong
// endpoint — is pure arithmetic, and arithmetic can be checked WITHOUT a
// device, an emulator, a simulator, a browser, or a screenshot.
//
// This file computes one table of `(easing, t) → output progress` from the
// SPEC, and the three runtimes each assert their own evaluator against it in
// their own language's unit-test suite. Any divergence is then attributable
// to a named easing function at a named input, which is what a fix needs.
//
// Precedent: this is deliberately the same shape as schema/conformance/ —
// one artifact, decoded by every codebase — because it is the same idea
// applied to behaviour instead of wire format.
//
// ## The reference is derived from the spec, not from a runtime
//
// It would be worthless to generate expectations by running one of the three
// implementations: the table would then encode that implementation's bugs as
// the standard, and the other two would be "fixed" to match it. Every value
// below comes from css-easing-1, with the section cited at each step.
//
// Usage:
//     node schema/conformance/easing/gen-easing-reference.mjs > easing-reference.json
//
// Regenerate and commit whenever a case is added. The committed JSON is the
// contract; this script is how it is reproduced and reviewed.

// ── cubic-bezier ────────────────────────────────────────────────────────────
//
// css-easing-1 §2.1: the curve is defined by control points P0=(0,0),
// P1=(x1,y1), P2=(x2,y2), P3=(1,1), and the output for an input progress x is
// the y of the point on the curve whose x equals that input. So evaluation is
// two steps: invert x(u) to find the curve parameter u, then evaluate y(u).
//
// x1 and x2 are required to be in [0,1] (§2.1), which makes x(u) monotonic
// and the inversion well-posed. y is unconstrained — that is what allows
// overshoot curves like cubic-bezier(0.68, -0.55, 0.265, 1.55).

/** Polynomial coefficients for one axis of the unit cubic Bézier. */
function coefficients(p1, p2) {
  // Standard expansion of B(u) with P0=0 and P3=1:
  //   B(u) = 3(1-u)²u·p1 + 3(1-u)u²·p2 + u³
  //        = c·u + b·u² + a·u³
  const c = 3 * p1;
  const b = 3 * (p2 - p1) - c;
  const a = 1 - c - b;
  return { a, b, c };
}

const sample = ({ a, b, c }, u) => ((a * u + b) * u + c) * u;
const sampleDerivative = ({ a, b, c }, u) => (3 * a * u + 2 * b) * u + c;

/**
 * Invert x(u) = target. Newton-Raphson first (quadratic convergence where the
 * derivative is healthy), bisection as the guaranteed fallback where it is
 * not — the flat regions of curves like ease-in have derivatives near zero
 * and Newton alone can wander out of [0,1] there.
 *
 * The iteration counts are deliberately far beyond what any runtime uses:
 * this is the reference, so it should be limited by double precision rather
 * than by a performance budget.
 */
function solveForU(cx, x) {
  let u = x;                                     // x is a good first guess since x(u) ≈ u
  for (let i = 0; i < 32; i += 1) {
    const err = sample(cx, u) - x;
    if (Math.abs(err) < 1e-15) return u;
    const d = sampleDerivative(cx, u);
    if (Math.abs(d) < 1e-12) break;              // derivative too flat — hand over to bisection
    u -= err / d;
    if (u < 0 || u > 1) break;                   // left the domain — hand over to bisection
  }
  let lo = 0, hi = 1;
  u = x;
  for (let i = 0; i < 200; i += 1) {             // 200 halvings ≫ double precision
    const xu = sample(cx, u);
    if (Math.abs(xu - x) < 1e-15) return u;
    if (xu < x) lo = u; else hi = u;
    u = (lo + hi) / 2;
  }
  return u;
}

function cubicBezier(x, x1, y1, x2, y2) {
  // css-easing-1 §2.1: outside [0,1] the curve is extended by its endpoint
  // tangents. Every runtime here clamps input progress to [0,1] before
  // easing, so the extension is unreachable; the endpoints are exact by
  // definition and are returned without going through the solver.
  if (x <= 0) return 0;
  if (x >= 1) return 1;
  return sample(coefficients(y1, y2), solveForU(coefficients(x1, x2), x));
}

// ── steps() ─────────────────────────────────────────────────────────────────
//
// css-easing-1 §2.3.1, transcribed literally. The `before flag` step is omitted
// because it only applies when an animation is running in reverse at exactly
// a step boundary; none of the three runtimes model it, and the harness never
// samples in that state.

function steps(x, count, position) {
  // "Calculate the current step as floor(input progress × steps)."
  let current = Math.floor(x * count);

  // "If the step position property is one of start, jump-start, or
  //  jump-both, increment current step by one."
  if (position === 'start' || position === 'jump-start' || position === 'jump-both') {
    current += 1;
  }

  // "If both of the following conditions are true: the before flag is set,
  //  and input progress ≥ 0 … " — omitted (see above). But the spec's floor
  //  of zero is kept: a negative current step is clamped up.
  if (x >= 0 && current < 0) current = 0;

  // "Calculate jumps based on the step position." This is the line the two
  // native runtimes disagree about, and the reason this table exists.
  let jumps;
  if (position === 'jump-none') jumps = count - 1;
  else if (position === 'jump-both') jumps = count + 1;
  else jumps = count;                            // start · end · jump-start · jump-end

  // "If input progress value ≤ 1 and current step > jumps, decrement current
  //  step by one." Note it clamps to JUMPS, not to the step count — for
  //  jump-both those differ (jumps = n+1), which is exactly where an
  //  implementation that clamps to n produces n/(n+1) at input 1 instead of 1.
  if (x <= 1 && current > jumps) current = jumps;

  // "The output progress value is current step / jumps."
  return current / jumps;
}

// ── linear() ────────────────────────────────────────────────────────────────
//
// css-easing-1 §2.2. Stops without an explicit position are filled in: the
// first defaults to 0%, the last to 100%, and a run of unpositioned interior
// stops is spread evenly between its nearest positioned neighbours. Positions
// are also made non-decreasing (a stop may not precede the one before it).

function linearEasing(x, stops) {
  const n = stops.length;
  if (n === 0) return x;
  if (n === 1) return stops[0].value;

  const pos = new Array(n).fill(null);
  if (stops[0].position != null) pos[0] = stops[0].position; else pos[0] = 0;
  if (stops[n - 1].position != null) pos[n - 1] = stops[n - 1].position; else pos[n - 1] = 1;
  for (let i = 1; i < n - 1; i += 1) if (stops[i].position != null) pos[i] = stops[i].position;

  // Even spread across each unpositioned run, between its known neighbours.
  let i = 0;
  while (i < n) {
    if (pos[i] != null) { i += 1; continue; }
    let j = i;
    while (j < n && pos[j] == null) j += 1;      // [i, j) is the unpositioned run
    const before = pos[i - 1], after = pos[j];
    const gap = j - i + 1;
    for (let k = i; k < j; k += 1) pos[k] = before + ((after - before) * (k - i + 1)) / gap;
    i = j;
  }
  // Enforce non-decreasing positions (§2.2).
  for (let k = 1; k < n; k += 1) if (pos[k] < pos[k - 1]) pos[k] = pos[k - 1];

  if (x <= pos[0]) return stops[0].value;
  if (x >= pos[n - 1]) return stops[n - 1].value;
  for (let k = 1; k < n; k += 1) {
    if (x <= pos[k]) {
      const span = pos[k] - pos[k - 1];
      if (span === 0) return stops[k].value;     // coincident stops → the later value wins
      const f = (x - pos[k - 1]) / span;
      return stops[k - 1].value + f * (stops[k].value - stops[k - 1].value);
    }
  }
  return stops[n - 1].value;
}

// ── Cases ───────────────────────────────────────────────────────────────────
//
// Keyword→bezier mappings are css-easing-1 §2.2 and must match the converter's
// AnimationTimingFunctionProperty companion constants exactly.

const KEYWORD_BEZIERS = {
  linear:        [0, 0, 1, 1],
  ease:          [0.25, 0.1, 0.25, 1],
  'ease-in':     [0.42, 0, 1, 1],
  'ease-out':    [0, 0, 0.58, 1],
  'ease-in-out': [0.42, 0, 0.58, 1],
};

/** Sample points for a continuous curve — dense near the endpoints, where
 *  a solver that mishandles the domain edges goes wrong first. */
const CURVE_SAMPLES = [
  0, 0.0001, 0.001, 0.01, 0.05, 0.1, 0.2, 0.25, 1 / 3, 0.4, 0.5,
  0.6, 2 / 3, 0.75, 0.8, 0.9, 0.95, 0.99, 0.999, 0.9999, 1,
];

/**
 * Sample points for a step function: every boundary, plus a hair either side.
 * The boundaries ARE the contract — sampling only the flat middles would let
 * an off-by-one in the jump count pass. 1e-6 is wide enough that it probes
 * the branch rather than the platform's float representation of k/n.
 */
function stepSamples(count) {
  const out = new Set([0, 0.5, 1]);
  for (let k = 0; k <= count; k += 1) {
    const b = k / count;
    out.add(Number(b.toFixed(12)));
    if (b - 1e-6 > 0) out.add(Number((b - 1e-6).toFixed(12)));
    if (b + 1e-6 < 1) out.add(Number((b + 1e-6).toFixed(12)));
  }
  return [...out].sort((a, b) => a - b);
}

const cases = [];

for (const [keyword, cb] of Object.entries(KEYWORD_BEZIERS)) {
  cases.push({
    id: `keyword-${keyword}`,
    kind: 'cubic-bezier',
    css: keyword,
    note: `CSS keyword "${keyword}" normalises to cubic-bezier(${cb.join(', ')}) — css-easing-1 §2.2.`,
    cubicBezier: { x1: cb[0], y1: cb[1], x2: cb[2], y2: cb[3] },
    samples: CURVE_SAMPLES.map((t) => ({ t, expected: cubicBezier(t, ...cb) })),
  });
}

// Custom curves: one ordinary, one with overshoot on both ends. Overshoot is
// worth pinning because y is unconstrained while x is not — an implementation
// that clamps the OUTPUT to [0,1] silently destroys these.
for (const [id, cb, note] of [
  ['custom-symmetric', [0.5, 0, 0.5, 1], 'Ordinary symmetric ease-in-out-ish curve.'],
  ['custom-overshoot', [0.68, -0.55, 0.265, 1.55],
    'Back-ease. y1 < 0 and y2 > 1, so output leaves [0,1] mid-curve — an implementation that clamps output progress fails here.'],
]) {
  cases.push({
    id, kind: 'cubic-bezier', css: `cubic-bezier(${cb.join(', ')})`, note,
    cubicBezier: { x1: cb[0], y1: cb[1], x2: cb[2], y2: cb[3] },
    samples: CURVE_SAMPLES.map((t) => ({ t, expected: cubicBezier(t, ...cb) })),
  });
}

// steps(): every position × a spread of counts. jump-none is skipped at n=1
// because css-easing-1 §2.3 makes steps(1, jump-none) invalid — jumps would
// be 0 and the output undefined. Testing it would pin a divergence that the
// spec does not adjudicate.
for (const position of ['jump-start', 'jump-end', 'jump-none', 'jump-both', 'start', 'end']) {
  for (const count of [1, 2, 3, 5]) {
    if (position === 'jump-none' && count < 2) continue;
    cases.push({
      id: `steps-${count}-${position}`,
      kind: 'steps',
      css: `steps(${count}, ${position})`,
      note: position === 'jump-both'
        ? `jumps = count + 1 = ${count + 1}. The spec clamps current step to JUMPS, not to the step count — clamping to ${count} yields ${count}/${count + 1} at input 1 instead of 1.`
        : position === 'jump-none'
          ? `jumps = count - 1 = ${count - 1}. Endpoints are held, so there are ${count - 1} interior jumps.`
          : `jumps = count = ${count}.`,
      steps: { count, position },
      samples: stepSamples(count).map((t) => ({ t, expected: steps(t, count, position) })),
    });
  }
}

// step-start / step-end keywords — css-easing-1 §2.3 aliases.
for (const [keyword, count, position] of [['step-start', 1, 'start'], ['step-end', 1, 'end']]) {
  cases.push({
    id: `keyword-${keyword}`, kind: 'steps', css: keyword,
    note: `CSS keyword "${keyword}" is steps(${count}, ${position}) — css-easing-1 §2.3.`,
    steps: { count, position },
    samples: stepSamples(count).map((t) => ({ t, expected: steps(t, count, position) })),
  });
}

// linear() — including the unpositioned-interior fill, which is the part
// implementations most often approximate.
for (const [id, stops, note] of [
  ['linear-identity', [{ value: 0 }, { value: 1 }], 'The two-stop identity — must equal input exactly.'],
  ['linear-three-even', [{ value: 0 }, { value: 0.25 }, { value: 1 }],
    'Interior stop with no position: fills to 50%, NOT to its value. A runtime that spreads by index over n-1 gets this right; one that uses the value as the position does not.'],
  ['linear-positioned', [{ value: 0, position: 0 }, { value: 0.8, position: 0.2 }, { value: 1, position: 1 }],
    'Explicit positions — a steep first segment then a shallow one.'],
  ['linear-nonmonotonic', [{ value: 0 }, { value: 1 }, { value: 0.5 }],
    'Output may decrease; linear() constrains positions, not values.'],
]) {
  cases.push({
    id, kind: 'linear', css: `linear(${stops.map((s) => s.position != null ? `${s.value} ${s.position * 100}%` : `${s.value}`).join(', ')})`,
    note, linearStops: stops,
    samples: CURVE_SAMPLES.map((t) => ({ t, expected: linearEasing(t, stops) })),
  });
}

// ── Emit ────────────────────────────────────────────────────────────────────

const round = (v) => Number(v.toFixed(12));      // kill double-printing noise; well inside any tolerance
for (const c of cases) for (const s of c.samples) { s.t = round(s.t); s.expected = round(s.expected); }

process.stdout.write(JSON.stringify({
  version: 1,
  generator: 'schema/conformance/easing/gen-easing-reference.mjs',
  spec: 'https://www.w3.org/TR/css-easing-1/',
  note:
    'Cross-runtime easing reference. Every expected value is computed FROM THE SPEC by the ' +
    'generator, never by running a runtime — generating it from an implementation would encode ' +
    "that implementation's bugs as the standard. Compose, SwiftUI and web each assert their own " +
    'evaluator against this table in their own unit-test suite, so a divergence is attributable ' +
    'to a named easing function at a named input without a device, an emulator or a screenshot.',
  tolerance: {
    cubicBezier: 1e-4,
    note:
      'cubic-bezier is compared with an absolute tolerance because implementations legitimately ' +
      'differ in solver iteration count and float width (Compose evaluates in Float via androidx ' +
      'CubicBezierEasing). 1e-4 is WPT assert_matrix_equals\'s epsilon and is orders of magnitude ' +
      'below anything renderable. steps() and linear() are exact rational arithmetic and are ' +
      'compared with a much tighter 1e-9 — a difference there is a logic bug, not float noise.',
    stepsAndLinear: 1e-9,
  },
  caseCount: cases.length,
  sampleCount: cases.reduce((n, c) => n + c.samples.length, 0),
  cases,
}, null, 2) + '\n');
