// Mask.test.ts — Phase-8 mask-* + mask-border-* coverage.

import { describe, it, expect } from 'vitest';
import { applyEffectsPhase8 } from '../../../../src/style/engine/effects/_dispatch';
import { extractMaskImage } from '../../../../src/style/engine/effects/mask/MaskImageExtractor';
import { applyMaskImage } from '../../../../src/style/engine/effects/mask/MaskImageApplier';
import { extractMaskMode } from '../../../../src/style/engine/effects/mask/MaskModeExtractor';
import { applyMaskMode } from '../../../../src/style/engine/effects/mask/MaskModeApplier';
import { extractMaskRepeat } from '../../../../src/style/engine/effects/mask/MaskRepeatExtractor';
import { applyMaskRepeat } from '../../../../src/style/engine/effects/mask/MaskRepeatApplier';
import { extractMaskSize } from '../../../../src/style/engine/effects/mask/MaskSizeExtractor';
import { applyMaskSize } from '../../../../src/style/engine/effects/mask/MaskSizeApplier';
import { extractMaskComposite } from '../../../../src/style/engine/effects/mask/MaskCompositeExtractor';
import { applyMaskComposite } from '../../../../src/style/engine/effects/mask/MaskCompositeApplier';
import { extractMaskBorderSlice } from '../../../../src/style/engine/effects/mask/MaskBorderSliceExtractor';
import { applyMaskBorderSlice } from '../../../../src/style/engine/effects/mask/MaskBorderSliceApplier';
import { extractMaskBorderRepeat } from '../../../../src/style/engine/effects/mask/MaskBorderRepeatExtractor';
import { applyMaskBorderRepeat } from '../../../../src/style/engine/effects/mask/MaskBorderRepeatApplier';
import { extractMaskBorderWidth } from '../../../../src/style/engine/effects/mask/MaskBorderWidthExtractor';
import { applyMaskBorderWidth } from '../../../../src/style/engine/effects/mask/MaskBorderWidthApplier';

const p = (type: string, data: unknown) => ({ type, data });

describe('MaskImage', () => {
  it('bare filename → url()', () => {
    expect(applyMaskImage(extractMaskImage([p('MaskImage', ['mask.png'])])))
      .toEqual({ maskImage: 'url("mask.png")', WebkitMaskImage: 'url("mask.png")' });
  });
  it("'none' passthrough", () => {
    expect(applyMaskImage(extractMaskImage([p('MaskImage', ['none'])])))
      .toEqual({ maskImage: 'none', WebkitMaskImage: 'none' });
  });
  it('linear-gradient layer', () => {
    const r = applyMaskImage(extractMaskImage([p('MaskImage', [{
      type: 'linear-gradient', angle: { deg: 45 },
      stops: [
        { color: { srgb: { r: 0, g: 0, b: 0 }, original: 'black' }, position: null },
        { color: { srgb: { r: 0, g: 0, b: 0, a: 0 }, original: 'transparent' }, position: null },
      ],
    }])]));
    expect(r.maskImage).toContain('linear-gradient(45deg');
  });
});

describe('MaskMode', () => {
  it('FQCN Alpha → alpha', () => {
    expect(applyMaskMode(extractMaskMode([p('MaskMode',
      { type: 'app.irmodels.properties.effects.MaskModeValue.Alpha' })])))
      .toEqual({ maskMode: 'alpha' });
  });
  it('FQCN MatchSource → match-source', () => {
    expect(applyMaskMode(extractMaskMode([p('MaskMode',
      { type: 'app.irmodels.properties.effects.MaskModeValue.MatchSource' })])))
      .toEqual({ maskMode: 'match-source' });
  });
});

describe('MaskRepeat', () => {
  it('FQCN NoRepeat → no-repeat (standard + webkit)', () => {
    expect(applyMaskRepeat(extractMaskRepeat([p('MaskRepeat',
      { type: 'app.irmodels.properties.effects.MaskRepeatValue.NoRepeat' })])))
      .toEqual({ maskRepeat: 'no-repeat', WebkitMaskRepeat: 'no-repeat' });
  });
});

describe('MaskSize', () => {
  it('cover collapses', () => {
    expect(applyMaskSize(extractMaskSize([p('MaskSize',
      { width: { type: 'cover' }, height: { type: 'cover' } })])))
      .toEqual({ maskSize: 'cover', WebkitMaskSize: 'cover' });
  });
  it('two lengths', () => {
    expect(applyMaskSize(extractMaskSize([p('MaskSize',
      { width: { type: 'length', px: 80 }, height: { type: 'length', px: 40 } })])))
      .toEqual({ maskSize: '80px 40px', WebkitMaskSize: '80px 40px' });
  });
  it('auto + length', () => {
    expect(applyMaskSize(extractMaskSize([p('MaskSize',
      { width: { type: 'length', px: 100 }, height: { type: 'auto' } })])))
      .toEqual({ maskSize: '100px auto', WebkitMaskSize: '100px auto' });
  });
});

describe('MaskComposite', () => {
  it('FQCN Add → add', () => {
    expect(applyMaskComposite(extractMaskComposite([p('MaskComposite',
      { type: 'app.irmodels.properties.effects.MaskCompositeValue.Add' })])).maskComposite)
      .toBe('add');
  });
});

describe('MaskBorderSlice', () => {
  it('uniform number', () => {
    const r = applyMaskBorderSlice(extractMaskBorderSlice([p('MaskBorderSlice', 30)]));
    expect(r.maskBorderSlice).toBe('30');
    expect(r.WebkitMaskBoxImageSlice).toBe('30');
  });
  it('TRBL with fill', () => {
    const r = applyMaskBorderSlice(extractMaskBorderSlice([p('MaskBorderSlice',
      { top: { pct: 25 }, right: { pct: 25 }, bottom: { pct: 25 }, left: { pct: 25 }, fill: true })]));
    expect(r.maskBorderSlice).toBe('25% 25% 25% 25% fill');
  });
});

describe('MaskBorderRepeat', () => {
  it('single keyword', () => {
    expect(applyMaskBorderRepeat(extractMaskBorderRepeat([p('MaskBorderRepeat',
      { type: 'round' })])).maskBorderRepeat).toBe('round');
  });
  it('two-value form', () => {
    expect(applyMaskBorderRepeat(extractMaskBorderRepeat([p('MaskBorderRepeat',
      { type: 'two-value', horizontal: 'round', vertical: 'space' })])).maskBorderRepeat)
      .toBe('round space');
  });
});

describe('MaskBorderWidth', () => {
  it('auto', () => {
    expect(applyMaskBorderWidth(extractMaskBorderWidth([p('MaskBorderWidth',
      { type: 'auto' })])).maskBorderWidth).toBe('auto');
  });
  it('number (unitless)', () => {
    expect(applyMaskBorderWidth(extractMaskBorderWidth([p('MaskBorderWidth',
      { type: 'number', value: 2 })])).maskBorderWidth).toBe('2');
  });
  it('multi TRBL', () => {
    expect(applyMaskBorderWidth(extractMaskBorderWidth([p('MaskBorderWidth',
      { type: 'multi', top: '10px', right: '20px', bottom: '10px', left: '20px' })])).maskBorderWidth)
      .toBe('10px 20px 10px 20px');
  });
});

describe('applyEffectsPhase8 mask integration', () => {
  it('merges multiple mask properties', () => {
    const r = applyEffectsPhase8([
      p('MaskImage', ['mask.png']),
      p('MaskMode', { type: 'app.irmodels.properties.effects.MaskModeValue.Alpha' }),
      p('MaskOrigin', 'BORDER_BOX'),
      p('MaskType', 'LUMINANCE'),
    ]);
    expect(r.maskImage).toBe('url("mask.png")');
    expect(r.maskMode).toBe('alpha');
    expect(r.maskOrigin).toBe('border-box');
    expect(r.maskType).toBe('luminance');
  });
});
