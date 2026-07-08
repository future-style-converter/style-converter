// Tests for SizeApplier — SizeConfig -> partial CSSProperties serialisation.
import { describe, it, expect } from 'vitest';
import { applySize } from '../../../src/style/engine/sizing/SizeApplier';

describe('applySize', () => {
  it('returns an empty object when nothing is configured', () => {
    // Baseline — no keys should appear unless the caller populated them.
    expect(applySize({})).toEqual({});
  });

  it('emits px widths/heights', () => {
    // Most common path — absolute pixels round-trip as `Npx` strings.
    const out = applySize({
      width:  { kind: 'exact', px: 200 },
      height: { kind: 'exact', px: 60 },
    });
    expect(out).toEqual({ width: '200px', height: '60px' });
  });

  it('emits min/max on both axes', () => {
    // Confirms each min-*/max-* field maps to its own CSS key.
    const out = applySize({
      minWidth:  { kind: 'exact', px: 100 },
      maxWidth:  { kind: 'exact', px: 300 },
      minHeight: { kind: 'exact', px: 40 },
      maxHeight: { kind: 'exact', px: 120 },
    });
    expect(out).toEqual({
      minWidth: '100px', maxWidth: '300px',
      minHeight: '40px', maxHeight: '120px',
    });
  });

  it('emits "none" for max-width / max-height', () => {
    // {kind:"none"} -> CSS 'none' keyword (legal on max-*, disallowed on min-*).
    const out = applySize({
      maxWidth:  { kind: 'none' },
      maxHeight: { kind: 'none' },
    });
    expect(out).toEqual({ maxWidth: 'none', maxHeight: 'none' });
  });

  it('emits intrinsic keywords and bounded fit-content', () => {
    // Width keyword family: auto / min-content / max-content / fit-content(<len>).
    const out = applySize({
      width:    { kind: 'auto' },
      minWidth: { kind: 'intrinsic', intrinsicKind: 'min-content' },
      maxWidth: { kind: 'intrinsic', intrinsicKind: 'max-content' },
      height:   { kind: 'intrinsic', intrinsicKind: 'fit-content', bound: { kind: 'exact', px: 200 } },
    });
    expect(out).toEqual({
      width: 'auto',
      minWidth: 'min-content',
      maxWidth: 'max-content',
      height: 'fit-content(200px)',
    });
  });

  it('emits logical sizing keys as camelCase (blockSize / inlineSize ...)', () => {
    // Browser natively resolves block/inline against writing-mode/direction.
    const out = applySize({
      blockSize:      { kind: 'exact', px: 100 },
      inlineSize:     { kind: 'relative', value: 50, unit: 'percent' },
      minBlockSize:   { kind: 'exact', px: 80 },
      maxBlockSize:   { kind: 'exact', px: 50 },
      minInlineSize:  { kind: 'exact', px: 300 },
      maxInlineSize:  { kind: 'exact', px: 150 },
    });
    expect(out).toEqual({
      blockSize: '100px',
      inlineSize: '50%',
      minBlockSize: '80px',
      maxBlockSize: '50px',
      minInlineSize: '300px',
      maxInlineSize: '150px',
    });
  });

  it('emits aspectRatio as a numeric string when not auto', () => {
    // CSS accepts `aspect-ratio: 1.777...` — we pass the normalized value through.
    const out = applySize({ aspectRatio: { ratio: 16 / 9, isAuto: false } });
    expect(out.aspectRatio).toBe(String(16 / 9));
  });

  it('emits aspectRatio:"auto" for the auto keyword', () => {
    // Ratio_Auto fixture -> {isAuto:true} -> 'auto' keyword round-trip.
    expect(applySize({ aspectRatio: { ratio: 0, isAuto: true } }))
      .toEqual({ aspectRatio: 'auto' });
  });

  it('skips fields that are not set (no undefined keys)', () => {
    // We only want keys the caller populated — absent fields must stay absent.
    const out = applySize({ width: { kind: 'exact', px: 10 } });
    expect(Object.keys(out)).toEqual(['width']);
  });
});
