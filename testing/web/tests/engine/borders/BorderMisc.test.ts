// BorderMisc — coverage for BoxDecorationBreak, CornerShape, BorderBoundary.
// Fixtures: examples/properties/borders/{box-decoration-break,corner-shape}.json.
import { describe, it, expect } from 'vitest';
import { extractBoxDecorationBreak } from '../../../src/style/engine/borders/BoxDecorationBreakExtractor';
import { applyBoxDecorationBreak }   from '../../../src/style/engine/borders/BoxDecorationBreakApplier';
import { extractCornerShape }        from '../../../src/style/engine/borders/CornerShapeExtractor';
import { applyCornerShape }          from '../../../src/style/engine/borders/CornerShapeApplier';
import { extractBorderBoundary }     from '../../../src/style/engine/borders/BorderBoundaryExtractor';
import { applyBorderBoundary }       from '../../../src/style/engine/borders/BorderBoundaryApplier';

const p = (type: string, data: unknown) => ({ type, data });

describe('BoxDecorationBreak', () => {
  it('slice', () => {
    expect(applyBoxDecorationBreak(extractBoxDecorationBreak([p('BoxDecorationBreak', 'SLICE')])))
      .toEqual({ boxDecorationBreak: 'slice', WebkitBoxDecorationBreak: 'slice' });
  });
  it('clone', () => {
    expect(applyBoxDecorationBreak(extractBoxDecorationBreak([p('BoxDecorationBreak', 'CLONE')])))
      .toEqual({ boxDecorationBreak: 'clone', WebkitBoxDecorationBreak: 'clone' });
  });
  it('rejects unknown keywords', () => {
    expect(applyBoxDecorationBreak(extractBoxDecorationBreak([p('BoxDecorationBreak', 'WAVY')])))
      .toEqual({});
  });
});

describe('CornerShape', () => {
  it.each(['round', 'angle', 'notch', 'bevel', 'scoop', 'squircle'])(
    'accepts %s',
    (kw) => {
      const out = applyCornerShape(extractCornerShape([p('CornerShape', kw.toUpperCase())]));
      expect(out.cornerShape).toBe(kw);
      expect(out.WebkitCornerShape).toBe(kw);
    },
  );
  it('rejects unknown', () => {
    expect(applyCornerShape(extractCornerShape([p('CornerShape', 'WOBBLE')]))).toEqual({});
  });
});

describe('BorderBoundary', () => {
  it.each(['none', 'parent', 'display'])('accepts %s', (kw) => {
    expect(applyBorderBoundary(extractBorderBoundary([p('BorderBoundary', kw.toUpperCase())])))
      .toEqual({ borderBoundary: kw });
  });
  it('rejects unknown', () => {
    expect(applyBorderBoundary(extractBorderBoundary([p('BorderBoundary', 'WINDOW')]))).toEqual({});
  });
});
