// BorderRadius — coverage for the 4 physical + 4 logical corner triplets.
// Fixtures: examples/properties/borders/border-radius-physical.json and
// border-radius-logical.json.
import { describe, it, expect } from 'vitest';
import { extractBorderTopLeftRadius }     from '../../../../src/style/engine/borders/radius/BorderTopLeftRadiusExtractor';
import { applyBorderTopLeftRadius }       from '../../../../src/style/engine/borders/radius/BorderTopLeftRadiusApplier';
import { extractBorderBottomRightRadius } from '../../../../src/style/engine/borders/radius/BorderBottomRightRadiusExtractor';
import { applyBorderBottomRightRadius }   from '../../../../src/style/engine/borders/radius/BorderBottomRightRadiusApplier';
import { extractBorderStartStartRadius }  from '../../../../src/style/engine/borders/radius/BorderStartStartRadiusExtractor';
import { applyBorderStartStartRadius }    from '../../../../src/style/engine/borders/radius/BorderStartStartRadiusApplier';
import { extractBorderEndEndRadius }      from '../../../../src/style/engine/borders/radius/BorderEndEndRadiusExtractor';
import { applyBorderEndEndRadius }        from '../../../../src/style/engine/borders/radius/BorderEndEndRadiusApplier';

const p = (type: string, data: unknown) => ({ type, data });

describe('Border*Radius — physical', () => {
  it('plain px', () => {
    expect(applyBorderTopLeftRadius(extractBorderTopLeftRadius([
      p('BorderTopLeftRadius', { px: 16 }),
    ]))).toEqual({ borderTopLeftRadius: '16px' });
  });
  it('zero', () => {
    expect(applyBorderTopLeftRadius(extractBorderTopLeftRadius([
      p('BorderTopLeftRadius', { px: 0 }),
    ]))).toEqual({ borderTopLeftRadius: '0px' });
  });
  it('percentage', () => {
    expect(applyBorderTopLeftRadius(extractBorderTopLeftRadius([
      p('BorderTopLeftRadius', { original: { v: 50, u: 'PERCENT' } }),
    ]))).toEqual({ borderTopLeftRadius: '50%' });
  });
  it('rem', () => {
    expect(applyBorderTopLeftRadius(extractBorderTopLeftRadius([
      p('BorderTopLeftRadius', { original: { v: 1, u: 'REM' } }),
    ]))).toEqual({ borderTopLeftRadius: '1rem' });
  });
  it('elliptical px pair', () => {
    expect(applyBorderTopLeftRadius(extractBorderTopLeftRadius([
      p('BorderTopLeftRadius', { horizontal: { px: 40 }, vertical: { px: 20 } }),
    ]))).toEqual({ borderTopLeftRadius: '40px 20px' });
  });
  it('elliptical px/percent mix', () => {
    expect(applyBorderTopLeftRadius(extractBorderTopLeftRadius([
      p('BorderTopLeftRadius', {
        horizontal: { px: 40 },
        vertical: { original: { v: 50, u: 'PERCENT' } },
      }),
    ]))).toEqual({ borderTopLeftRadius: '40px 50%' });
  });
  it('per-corner emits independently', () => {
    expect(applyBorderBottomRightRadius(extractBorderBottomRightRadius([
      p('BorderBottomRightRadius', { px: 32 }),
    ]))).toEqual({ borderBottomRightRadius: '32px' });
  });
  it('empty when unset', () => {
    expect(applyBorderTopLeftRadius({})).toEqual({});
  });
});

describe('Border*Radius — logical', () => {
  it('emits native borderStartStartRadius', () => {
    expect(applyBorderStartStartRadius(extractBorderStartStartRadius([
      p('BorderStartStartRadius', { px: 32 }),
    ]))).toEqual({ borderStartStartRadius: '32px' });
  });
  it('emits native borderEndEndRadius with elliptical pair', () => {
    expect(applyBorderEndEndRadius(extractBorderEndEndRadius([
      p('BorderEndEndRadius', { horizontal: { px: 40 }, vertical: { px: 20 } }),
    ]))).toEqual({ borderEndEndRadius: '40px 20px' });
  });
  it('percentage for logical corner', () => {
    expect(applyBorderStartStartRadius(extractBorderStartStartRadius([
      p('BorderStartStartRadius', { original: { v: 50, u: 'PERCENT' } }),
    ]))).toEqual({ borderStartStartRadius: '50%' });
  });
});
