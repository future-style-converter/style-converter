// BoxShadow — coverage for the engine/effects/shadow/BoxShadow triplet.
// Fixture: examples/properties/borders/box-shadow.json.
import { describe, it, expect } from 'vitest';
import { extractBoxShadow } from '../../../../src/style/engine/effects/shadow/BoxShadowExtractor';
import { applyBoxShadow }   from '../../../../src/style/engine/effects/shadow/BoxShadowApplier';

const p = (type: string, data: unknown) => ({ type, data });

describe('BoxShadow', () => {
  it('empty array → none (parser gap)', () => {
    expect(applyBoxShadow(extractBoxShadow([p('BoxShadow', [])])))
      .toEqual({ boxShadow: 'none' });
  });
  it('offset-only layer', () => {
    expect(applyBoxShadow(extractBoxShadow([p('BoxShadow', [
      { x: { px: 8 }, y: { px: 8 }, c: { srgb: { r: 0.07, g: 0.07, b: 0.07 } } },
    ])]))).toEqual({ boxShadow: '8px 8px rgba(18, 18, 18, 1)' });
  });
  it('offset + blur', () => {
    expect(applyBoxShadow(extractBoxShadow([p('BoxShadow', [
      { x: { px: 4 }, y: { px: 4 }, blur: { px: 12 }, c: { srgb: { r: 0, g: 0, b: 0 } } },
    ])]))).toEqual({ boxShadow: '4px 4px 12px rgba(0, 0, 0, 1)' });
  });
  it('offset + blur + spread', () => {
    expect(applyBoxShadow(extractBoxShadow([p('BoxShadow', [
      { x: { px: 4 }, y: { px: 4 }, blur: { px: 12 }, spread: { px: 4 },
        c: { srgb: { r: 0, g: 0, b: 0 } } },
    ])]))).toEqual({ boxShadow: '4px 4px 12px 4px rgba(0, 0, 0, 1)' });
  });
  it('inset shadow emits inset first', () => {
    expect(applyBoxShadow(extractBoxShadow([p('BoxShadow', [
      { x: { px: 4 }, y: { px: 4 }, blur: { px: 8 }, inset: true,
        c: { srgb: { r: 0, g: 0, b: 0 } } },
    ])]))).toEqual({ boxShadow: 'inset 4px 4px 8px rgba(0, 0, 0, 1)' });
  });
  it('negative offsets', () => {
    expect(applyBoxShadow(extractBoxShadow([p('BoxShadow', [
      { x: { px: -8 }, y: { px: -8 }, blur: { px: 12 },
        c: { srgb: { r: 0, g: 0, b: 0 } } },
    ])]))).toEqual({ boxShadow: '-8px -8px 12px rgba(0, 0, 0, 1)' });
  });
  it('multiple layers join with ", "', () => {
    const out = applyBoxShadow(extractBoxShadow([p('BoxShadow', [
      { x: { px: 4 }, y: { px: 4 }, blur: { px: 8 }, c: { srgb: { r: 0.9, g: 0.3, b: 0.2 } } },
      { x: { px: -4 }, y: { px: -4 }, blur: { px: 8 }, c: { srgb: { r: 0.2, g: 0.6, b: 0.86 } } },
    ])]));
    expect(out.boxShadow).toContain(', ');
    expect(out.boxShadow).toContain('4px 4px 8px');
    expect(out.boxShadow).toContain('-4px -4px 8px');
  });
  it('inset mixed with non-inset in multi-layer', () => {
    const out = applyBoxShadow(extractBoxShadow([p('BoxShadow', [
      { x: { px: 0 }, y: { px: 0 }, blur: { px: 8 }, inset: true,
        c: { srgb: { r: 0.07, g: 0.07, b: 0.07 } } },
      { x: { px: 0 }, y: { px: 8 }, blur: { px: 16 },
        c: { srgb: { r: 0, g: 0, b: 0, a: 0.3 } } },
    ])]));
    expect(out.boxShadow).toMatch(/^inset /);
    expect(out.boxShadow).toContain('rgba(0, 0, 0, 0.3)');
  });
  it('raw calc() string preserves CSS', () => {
    expect(applyBoxShadow(extractBoxShadow([
      p('BoxShadow', 'calc(2px + 2px) calc(2px + 2px) 8px #111'),
    ]))).toEqual({ boxShadow: 'calc(2px + 2px) calc(2px + 2px) 8px #111' });
  });
  it('empty when property not present', () => {
    expect(applyBoxShadow(extractBoxShadow([]))).toEqual({});
  });
});
