// performancePhase10.test.ts — Phase-10 long-tail smoke tests.
import { describe, it, expect } from 'vitest';
import { applyPerformancePhase10 } from '../../src/engine/performance/_dispatch';

describe('applyPerformancePhase10', () => {
  it('empty input → empty output', () => {
    expect(applyPerformancePhase10([])).toEqual({});
  });
  it('Contain → strict', () => {
    expect(applyPerformancePhase10([{ type: 'Contain', data: 'STRICT' }]))
      .toEqual({ contain: 'strict' });
  });
});

// ── wave-40 lane T1: the will-change LIST wire ───────────────────────────────
// WillChangeProperty.kt is `List<WillChangeValue>`, serialised as
// `[{type:'property-name', name:'transform'}, …]` with tag-only variants
// `{type:'auto'|'scroll-position'|'contents'}`. The extractor folded through
// `keywordOrRaw`, which reads SCALAR wires only, so an Array matched no branch
// and EVERY `will-change` declaration was dropped on web.
//
// Not cosmetic: measured on the pipeline's own Chromium (_diag40/T1/probe3.mjs),
// a compositing-promoted child of `backface-visibility:hidden` +
// `transform-style:preserve-3d` renders (10 000 green px) and an unpromoted one
// does not (0) — wave39-final's
// css-transforms/composited-under-rotateY-180deg-preserve-3d captured BLANK
// (ink 0.00 % vs the ref's 4.27 %).
describe('will-change list wire (wave-40 T1)', () => {
  const wc = (data: unknown) => applyPerformancePhase10([{ type: 'WillChange', data }]);

  it('single property-name entry', () => {
    expect(wc([{ type: 'property-name', name: 'transform' }]))
      .toEqual({ willChange: 'transform' });
  });
  it('multiple entries join with the spec comma', () => {
    // css-will-change-1 §3: `<animateable-feature>#`.
    expect(wc([
      { type: 'property-name', name: 'transform' },
      { type: 'property-name', name: 'opacity' },
    ])).toEqual({ willChange: 'transform, opacity' });
  });
  it('tag-only features (auto / scroll-position / contents)', () => {
    expect(wc([{ type: 'auto' }])).toEqual({ willChange: 'auto' });
    expect(wc([{ type: 'scroll-position' }])).toEqual({ willChange: 'scroll-position' });
    expect(wc([{ type: 'contents' }])).toEqual({ willChange: 'contents' });
  });
  it('mixed tag-only + property-name keeps source order', () => {
    expect(wc([{ type: 'scroll-position' }, { type: 'property-name', name: 'transform' }]))
      .toEqual({ willChange: 'scroll-position, transform' });
  });
  it('an unreadable entry is dropped, not emitted as an invalid token', () => {
    // One bad entry would invalidate the WHOLE comma list in a browser, so
    // the unknown shape is skipped and the legible ones still land.
    expect(wc([{ type: 'nonsense' }, { type: 'property-name', name: 'transform' }]))
      .toEqual({ willChange: 'transform' });
    // Nothing legible at all ⇒ no declaration (never `willChange: ''`).
    expect(wc([{ type: 'nonsense' }])).toEqual({});
    expect(wc([])).toEqual({});
  });
  it('still tolerates the scalar wire a hand-authored fixture may carry', () => {
    expect(wc('AUTO')).toEqual({ willChange: 'auto' });
    expect(wc({ type: 'property-name', name: 'transform' })).toEqual({ willChange: 'transform' });
  });
});

// ── wave-48 lane W5: the contain-intrinsic-* LENGTH wire ─────────────────────
// ContainIntrinsicValue (css-sizing-4 §4.4) serialises its length arms as
// {"type":"length","px":N} / {"type":"auto-length","px":N}, which the generic
// keywordOrRaw fold read as "no value" — every contain-intrinsic declaration
// with a length was dropped. Measured on wave48-cal css-contain/
// contain-inline-size-intrinsic: with the intrinsic size gone, a
// contain:inline-size box's fit-content resolves to 0 and the whole capture
// has zero green ink (web 0.9549 coverage-vetoed; both natives pass 0.969).
describe('contain-intrinsic length wire (wave-48 W5)', () => {
  const one = (type: string, data: unknown) => applyPerformancePhase10([{ type, data }]);

  it('VERBATIM contain-inline-size-intrinsic wires reach the browser', () => {
    // The two divs of the WPT test (wave48-cal per-test-ir).
    expect(one('ContainIntrinsicInlineSize', { type: 'length', px: 100 }))
      .toEqual({ containIntrinsicInlineSize: '100px' });
    expect(one('ContainIntrinsicInlineSize', { type: 'length', px: 50 }))
      .toEqual({ containIntrinsicInlineSize: '50px' });
  });

  it('auto-length carries both tokens (§4.4 last-remembered-size form)', () => {
    expect(one('ContainIntrinsicWidth', { type: 'auto-length', px: 300 }))
      .toEqual({ containIntrinsicWidth: 'auto 300px' });
  });

  it('keyword variants keep their pre-fix output byte-identically', () => {
    expect(one('ContainIntrinsicBlockSize', { type: 'none' }))
      .toEqual({ containIntrinsicBlockSize: 'none' });
    expect(one('ContainIntrinsicHeight', { type: 'auto' }))
      .toEqual({ containIntrinsicHeight: 'auto' });
  });

  it('two-axis contain-intrinsic-size wire and its flattened single-axis twin', () => {
    // {"width","height"} — the css-sizing abspos-014 shape…
    expect(one('ContainIntrinsicSize', {
      width: { type: 'length', px: 500 }, height: { type: 'length', px: 50 },
    })).toEqual({ containIntrinsicSize: '500px 50px' });
    // …and the deepFlatten-inlined lone-width shape the wave48-cal
    // css-view-transitions content-visibility IR carries.
    expect(one('ContainIntrinsicSize', { type: 'length', px: 500 }))
      .toEqual({ containIntrinsicSize: '500px' });
  });
});
