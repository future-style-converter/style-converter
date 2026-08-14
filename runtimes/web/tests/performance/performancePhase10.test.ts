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
