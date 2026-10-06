// AlignSelfBaselinePosition.test.ts — wave 52 (lane L7, static-position T3).
//
// The converter now types css-align-3 §4.2 <baseline-position> values: the
// verbatim wave51-fix rawValue "last baseline" (12 css-grid/abspos/*-last-
// baseline-* docs, plus the in-flow grid item of css-contain/contain-inline-
// size-grid-indefinite-height-min-height-flex-row) arrives as
// {"type":"AlignSelf","data":"LAST_BASELINE"} (converter pin P3f). The web
// runtime must hand the browser the SPACE-separated CSS value: kebab() would
// emit `last-baseline`, invalid CSS the browser drops — today's picture (mark
// 14 px high on grid-abspos-staticpos-align-self-rtl-last-baseline-002 web).
//
// MUTATION EXECUTED (tools/titan/results/wave52-static-position/mutations.log):
//   M-W1 route LAST_BASELINE through kebab() (delete the SPACED_KEYWORDS hit)
//        → W1/W2/W3/W4 fail ('last-baseline' / 'first-baseline').

import { describe, it, expect } from 'vitest';
import { extractAlignSelf, alignSelfKeyword } from '../../src/engine/layout/flexbox/AlignSelfExtractor';
import { applyAlignSelf } from '../../src/engine/layout/flexbox/AlignSelfApplier';
import { applyLayoutPhase7 } from '../../src/engine/layout/_dispatch';

// The post-T3 converter wire for `align-self: last baseline` (pin P3f).
const LAST_BASELINE = { type: 'AlignSelf', data: 'LAST_BASELINE' };

describe('align-self <baseline-position> on the web runtime', () => {
  it('W1 extracts LAST_BASELINE as the spaced CSS keyword', () => {
    // Never the kebab form: `last-baseline` is not CSS.
    expect(extractAlignSelf([LAST_BASELINE])).toEqual({ value: 'last baseline' });
    expect(alignSelfKeyword('LAST_BASELINE')).not.toBe('last-baseline');
  });

  it('W2 the applier emits it verbatim', () => {
    // css-align-3 §4.2 spelling reaches React's style object unchanged.
    expect(applyAlignSelf(extractAlignSelf([LAST_BASELINE]))).toEqual({ alignSelf: 'last baseline' });
  });

  it('W3 the Phase-7 dispatch (the StyleBuilder path) carries it end to end', () => {
    // A verbatim abspos child of grid-abspos-staticpos-align-self-rtl-last-
    // baseline-002 after T3 (Position + the typed align-self).
    const out = applyLayoutPhase7([{ type: 'Position', data: 'ABSOLUTE' }, LAST_BASELINE]);
    expect(out.alignSelf).toBe('last baseline');
  });

  it('W4 the other T3 keywords keep the single-token kebab path', () => {
    // `normal` (12 docs) and `first baseline` → BASELINE (§4.2 same value).
    expect(extractAlignSelf([{ type: 'AlignSelf', data: 'NORMAL' }])).toEqual({ value: 'normal' });
    expect(extractAlignSelf([{ type: 'AlignSelf', data: 'BASELINE' }])).toEqual({ value: 'baseline' });
    // Pre-wave-52 keywords are untouched.
    expect(extractAlignSelf([{ type: 'AlignSelf', data: 'FLEX_END' }])).toEqual({ value: 'flex-end' });
    // Hand-written FIRST_BASELINE is tolerated with its space.
    expect(alignSelfKeyword('FIRST_BASELINE')).toBe('first baseline');
  });
});
