// EasingReference.test.ts — web side of the cross-runtime easing contract (Wave 2).
//
// ## Why this arm is different in kind
//
// Compose and SwiftUI each own an easing EVALUATOR: they compute output
// progress from input progress themselves, so their suites assert numbers
// against schema/conformance/easing/easing-reference.json directly.
//
// The web runtime deliberately owns no evaluator. Its product is a CSS
// declaration, and the BROWSER evaluates the curve — which is the correct
// architecture, because the browser is the reference the other two runtimes
// are judged against. Reimplementing bezier arithmetic here would make the
// three-way comparison circular: web would agree with itself.
//
// So what web must prove is the other half of the contract: that for every
// timing function in the reference table it emits the CSS string that
// denotes that function. If the string is right, the browser's numbers are
// right by construction. If it is wrong — a keyword mapped to the wrong
// curve, a step position dropped, a linear() stop losing its percentage —
// the browser faithfully renders the wrong animation and no pixel metric
// attributes it to the timing function.
//
// The table already earned its keep on the two evaluator arms: it caught
// Compose clamping steps(n, jump-both) to the step count instead of to
// `jumps` (so those animations never reached their end state), and SwiftUI
// clamping cubic-bezier OUTPUT progress to [0,1] (which flattened every
// back-ease overshoot, and differed from Compose on identical CSS).

import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { timingFunctionToCss, timingFunctionListToCss } from '../../src/engine/animations/_shared';

const TABLE_PATH = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '../../../../schema/conformance/easing/easing-reference.json',
);

interface Sample { t: number; expected: number }
interface Case {
  id: string;
  kind: 'cubic-bezier' | 'steps' | 'linear';
  css: string;
  note: string;
  cubicBezier?: { x1: number; y1: number; x2: number; y2: number };
  steps?: { count: number; position: string };
  linearStops?: Array<{ value: number; position?: number }>;
  samples: Sample[];
}

const table = JSON.parse(readFileSync(TABLE_PATH, 'utf8')) as {
  version: number; caseCount: number; sampleCount: number; cases: Case[];
};

/**
 * Build the IR record shape the converter emits for a timing function, as
 * documented by `timingFunctionToCss` itself: `cb` for a bezier tuple,
 * `steps: {n, pos}`, `linear: [{v, p}]`, plus the `original` keyword hint
 * when the author wrote a keyword.
 */
function irRecordFor(c: Case): unknown {
  const isKeyword = c.id.startsWith('keyword-');
  const original = isKeyword ? c.css : undefined;
  switch (c.kind) {
    case 'cubic-bezier':
      return { original, cb: [c.cubicBezier!.x1, c.cubicBezier!.y1, c.cubicBezier!.x2, c.cubicBezier!.y2] };
    case 'steps':
      return { original, steps: { n: c.steps!.count, pos: c.steps!.position } };
    case 'linear':
      return {
        original,
        // The table stores positions as 0–1 (the spec's progress domain);
        // the IR carries a percent. Converting here keeps the table
        // platform-neutral rather than encoding a web detail into it.
        linear: c.linearStops!.map((s) => (s.position != null ? { v: s.value, p: s.position * 100 } : { v: s.value })),
      };
  }
}

describe('easing reference table', () => {
  it('loads and is non-trivial', () => {
    expect(table.version).toBe(1);
    expect(table.cases.length).toBeGreaterThanOrEqual(30);
    expect(table.cases.reduce((n, c) => n + c.samples.length, 0)).toBe(table.sampleCount);
  });

  it('keyword control points match the CSS spec constants', () => {
    // css-easing-1 §2.2. Web never evaluates these, but the converter's
    // normalisation feeds all three runtimes — a drift here is wrong
    // everywhere at once, which is precisely the correlated failure that
    // three-way agreement cannot detect.
    const expected: Record<string, [number, number, number, number]> = {
      'keyword-linear': [0, 0, 1, 1],
      'keyword-ease': [0.25, 0.1, 0.25, 1],
      'keyword-ease-in': [0.42, 0, 1, 1],
      'keyword-ease-out': [0, 0, 0.58, 1],
      'keyword-ease-in-out': [0.42, 0, 0.58, 1],
    };
    for (const [id, want] of Object.entries(expected)) {
      const c = table.cases.find((x) => x.id === id)!;
      expect(c, id).toBeDefined();
      expect([c.cubicBezier!.x1, c.cubicBezier!.y1, c.cubicBezier!.x2, c.cubicBezier!.y2], id).toEqual(want);
    }
  });
});

describe('timingFunctionToCss emits the CSS each reference case denotes', () => {
  for (const c of table.cases) {
    it(`${c.id} → ${c.css}`, () => {
      expect(timingFunctionToCss(irRecordFor(c))).toBe(c.css);
    });
  }
});

describe('serialisation properties that would silently mis-animate', () => {
  it('every step position survives round-trip, not just the default', () => {
    // A serializer that dropped `pos` would emit steps(n) — legal CSS
    // meaning steps(n, end) — so jump-start / jump-both / jump-none would
    // all silently become jump-end. The animation still runs; it just runs
    // the wrong one.
    for (const pos of ['jump-start', 'jump-end', 'jump-none', 'jump-both', 'start', 'end']) {
      expect(timingFunctionToCss({ steps: { n: 3, pos } })).toBe(`steps(3, ${pos})`);
    }
  });

  it('linear() keeps explicit stop percentages', () => {
    // Dropping `p` turns linear(0, 0.8 20%, 1) into an evenly-spread curve —
    // same stops, different timing, no error anywhere.
    expect(timingFunctionToCss({ linear: [{ v: 0 }, { v: 0.8, p: 20 }, { v: 1 }] }))
      .toBe('linear(0, 0.8 20%, 1)');
  });

  it('an overshoot bezier is emitted verbatim, not clamped', () => {
    // The exact curve SwiftUI was flattening. Web must hand the browser the
    // real control points; clamping here would reintroduce the bug the
    // reference table just removed from the native side.
    expect(timingFunctionToCss({ cb: [0.68, -0.55, 0.265, 1.55] }))
      .toBe('cubic-bezier(0.68, -0.55, 0.265, 1.55)');
  });

  it('a comma-separated list round-trips per-entry', () => {
    // animation-timing-function is a list property; a serializer that only
    // handled the first entry would silently apply one curve to every
    // animation on the element.
    expect(timingFunctionListToCss([
      { original: 'ease-in' },
      { steps: { n: 2, pos: 'jump-both' } },
      { cb: [0.1, 0.2, 0.3, 0.4] },
    ])).toBe('ease-in, steps(2, jump-both), cubic-bezier(0.1, 0.2, 0.3, 0.4)');
  });

  it('an unrecognised shape yields undefined rather than a bogus declaration', () => {
    // No silent fallthrough: emitting a wrong-but-parseable value would be
    // worse than emitting nothing, because nothing is visible in the report.
    expect(timingFunctionToCss({})).toBeUndefined();
    expect(timingFunctionToCss(null)).toBeUndefined();
    expect(timingFunctionToCss({ cb: [0, 0, 1] })).toBeUndefined();
  });
});
