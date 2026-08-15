// CalcSizeValue.test.ts — wave 42 (lane W3) css-values-5 calc-size() typing.
//
// The wire payloads below are VERBATIM converter output (checked against
// `:converter:run` on fixtures/wpt/css-values/calc-size__calc-size-flex-002:
//   {"type":"MinWidth","data":{"type":"calc-size","basis":"auto","factor":1,
//    "offsetPx":40,"original":"calc-size(auto, size + 40px)"}}
// ). Web's contract for calc-size() is DELIVERY: the browser implements the
// function natively, and the 15 css-values/calc-size WPT cells pass because
// the declaration reaches Chromium — previously via the Generic envelope,
// now via this typed path. The pinned invariants:
//   1. the typed wire decodes and routes into SizeConfig.calcSize;
//   2. the applier re-emits the VERBATIM original declaration;
//   3. reconstruction (no `original`) produces an equivalent calc-size();
//   4. non-calc-size shapes are untouched (additive decode only).

import { describe, expect, it } from 'vitest';
import { extractCalcSize, toCalcSizeCss } from '../../src/engine/sizing/CalcSizeValue';
import { extractSize } from '../../src/engine/sizing/SizeExtractor';
import { applySize } from '../../src/engine/sizing/SizeApplier';

// Verbatim converter wire for calc-size-flex-002's min-width declaration.
const FLEX_002_MIN_WIDTH = {
  type: 'calc-size', basis: 'auto', factor: 1, offsetPx: 40,
  original: 'calc-size(auto, size + 40px)',
};

// Verbatim converter wire for calc-size-min-max-sizes-004's width declaration.
const MIN_MAX_004_WIDTH = {
  type: 'calc-size', basis: 'fit-content', factor: 1, offsetPx: 80,
  original: 'calc-size(fit-content, size + 80px)',
};

describe('extractCalcSize', () => {
  it('decodes the typed converter wire field-for-field', () => {
    expect(extractCalcSize(FLEX_002_MIN_WIDTH)).toEqual({
      basis: 'auto', factor: 1, offsetPx: 40,
      original: 'calc-size(auto, size + 40px)',
    });
  });

  it('returns null for every non-calc-size shape (additive decode)', () => {
    // The neighbouring sizing shapes must keep their existing routes.
    expect(extractCalcSize({ type: 'length', px: 100 })).toBeNull();
    expect(extractCalcSize('auto')).toBeNull();
    expect(extractCalcSize({ type: 'percentage', value: 50 })).toBeNull();
    expect(extractCalcSize(null)).toBeNull();
    // Discriminator without a basis is malformed — refuse, don't guess.
    expect(extractCalcSize({ type: 'calc-size' })).toBeNull();
  });
});

describe('toCalcSizeCss', () => {
  it('replays the verbatim original byte-for-byte', () => {
    // Fidelity contract: what the author wrote is what the browser gets.
    expect(toCalcSizeCss(extractCalcSize(FLEX_002_MIN_WIDTH)!))
      .toBe('calc-size(auto, size + 40px)');
  });

  it('reconstructs an equivalent declaration when original is absent', () => {
    // factor 1 + positive offset → `size + Npx`.
    expect(toCalcSizeCss({ basis: 'auto', factor: 1, offsetPx: 20 }))
      .toBe('calc-size(auto, size + 20px)');
    // negative offset folds into the subtraction operator.
    expect(toCalcSizeCss({ basis: 'auto', factor: 1, offsetPx: -50 }))
      .toBe('calc-size(auto, size - 50px)');
    // non-unit factor scales the size term; zero offset drops the addend.
    expect(toCalcSizeCss({ basis: 'min-content', factor: 2, offsetPx: 0 }))
      .toBe('calc-size(min-content, size * 2)');
  });
});

describe('SizeExtractor + SizeApplier calc-size routing', () => {
  it('routes a typed MinWidth into calcSize and emits the verbatim CSS', () => {
    // calc-size-flex-002's exact property list shape (IR type + data).
    const cfg = extractSize([{ type: 'MinWidth', data: FLEX_002_MIN_WIDTH }]);
    // Routed to the side-table, NOT the LengthValue slot (exclusive routes).
    expect(cfg.minWidth).toBeUndefined();
    expect(cfg.calcSize?.minWidth?.basis).toBe('auto');
    // The applier hands the browser the byte-exact declaration.
    expect(applySize(cfg).minWidth).toBe('calc-size(auto, size + 40px)');
  });

  it('routes a typed Width and keeps sibling length slots intact', () => {
    const cfg = extractSize([
      { type: 'Width', data: MIN_MAX_004_WIDTH },
      // A plain height alongside must keep its normal length route.
      { type: 'Height', data: { type: 'length', px: 100 } },
    ]);
    const out = applySize(cfg);
    expect(out.width).toBe('calc-size(fit-content, size + 80px)');
    expect(out.height).toBe('100px');
  });

  it('emits calc-size on the max slots too (grid-repeat shape)', () => {
    // calc-size-grid-repeat's `max-width: calc-size(min-content, size * 1000)`.
    const cfg = extractSize([{
      type: 'MaxWidth',
      data: {
        type: 'calc-size', basis: 'min-content', factor: 1000, offsetPx: 0,
        original: 'calc-size(min-content, size * 1000)',
      },
    }]);
    expect(applySize(cfg).maxWidth).toBe('calc-size(min-content, size * 1000)');
  });
});
