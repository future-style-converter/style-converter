// Tests for SizeExtractor — covers every IR shape observed across the six
// examples/properties/sizing/*.json fixtures after the Kotlin converter.
import { describe, it, expect } from 'vitest';
import { extractSize } from '../../../src/style/engine/sizing/SizeExtractor';

// Helper: build a minimal IR property record for the extractor input.
const p = (type: string, data: unknown) => ({ type, data });

describe('extractSize — WidthValue shapes (Width/Height/Min*/Max*)', () => {
  it('parses {type:"length",px:N} absolute width', () => {
    // Confirmed shape across width-absolute fixture.
    const cfg = extractSize([p('Width', { type: 'length', px: 200 })]);
    expect(cfg.width).toEqual({ kind: 'exact', px: 200 });
  });

  it('parses the full Width_* complement (height/min/max)', () => {
    // All emit the same {type:"length",px:N} envelope — one case per key.
    const cfg = extractSize([
      p('Height',    { type: 'length', px: 60 }),
      p('MinWidth',  { type: 'length', px: 100 }),
      p('MaxWidth',  { type: 'length', px: 300 }),
      p('MinHeight', { type: 'length', px: 40 }),
      p('MaxHeight', { type: 'length', px: 120 }),
    ]);
    expect(cfg.height).toEqual({ kind: 'exact', px: 60 });
    expect(cfg.minWidth).toEqual({ kind: 'exact', px: 100 });
    expect(cfg.maxWidth).toEqual({ kind: 'exact', px: 300 });
    expect(cfg.minHeight).toEqual({ kind: 'exact', px: 40 });
    expect(cfg.maxHeight).toEqual({ kind: 'exact', px: 120 });
  });

  it('parses bare "auto" (width-intrinsic: Width_Auto)', () => {
    const cfg = extractSize([p('Width', 'auto')]);
    expect(cfg.width).toEqual({ kind: 'auto' });
  });

  it('parses bare intrinsic keywords (min/max-content)', () => {
    const cfg = extractSize([
      p('Width',  'min-content'),
      p('Height', 'max-content'),
    ]);
    expect(cfg.width).toEqual({ kind: 'intrinsic', intrinsicKind: 'min-content' });
    expect(cfg.height).toEqual({ kind: 'intrinsic', intrinsicKind: 'max-content' });
  });

  it('parses bounded fit-content: {"fit-content":{px:200}}', () => {
    // Width_FitContent_Bounded_200px fixture.
    const cfg = extractSize([p('Width', { 'fit-content': { px: 200 } })]);
    expect(cfg.width).toEqual({
      kind: 'intrinsic', intrinsicKind: 'fit-content',
      bound: { kind: 'exact', px: 200 },
    });
  });

  it('parses {type:"none"} on MaxWidth/MaxHeight', () => {
    // MaxWidth_None / MaxHeight_None fixtures.
    const cfg = extractSize([
      p('MaxWidth',  { type: 'none' }),
      p('MaxHeight', { type: 'none' }),
    ]);
    expect(cfg.maxWidth).toEqual({ kind: 'none' });
    expect(cfg.maxHeight).toEqual({ kind: 'none' });
  });

  it('parses em / percent units (width-units fixture)', () => {
    // Width_Em_10_ParentFont16 and Width_Percent_50_In_300pxParent.
    const cfg = extractSize([
      p('Width',  { type: 'length', original: { v: 10, u: 'EM' } }),
      p('Height', { type: 'percentage', value: 50 }),
    ]);
    expect(cfg.width).toEqual({ kind: 'relative', value: 10, unit: 'em' });
    expect(cfg.height).toEqual({ kind: 'relative', value: 50, unit: 'percent' });
  });
});

describe('extractSize — SizeValue shapes (BlockSize/InlineSize/Min*/Max*)', () => {
  it('parses raw {px:N} with no "type":"length" wrapper', () => {
    // BlockSize_100px fixture emits {"data":{"px":100.0},"type":"BlockSize"}.
    const cfg = extractSize([
      p('BlockSize',     { px: 100 }),
      p('InlineSize',    { px: 250 }),
      p('MinBlockSize',  { px: 80 }),
      p('MaxBlockSize',  { px: 50 }),
      p('MinInlineSize', { px: 300 }),
      p('MaxInlineSize', { px: 150 }),
    ]);
    expect(cfg.blockSize).toEqual({ kind: 'exact', px: 100 });
    expect(cfg.inlineSize).toEqual({ kind: 'exact', px: 250 });
    expect(cfg.minBlockSize).toEqual({ kind: 'exact', px: 80 });
    expect(cfg.maxBlockSize).toEqual({ kind: 'exact', px: 50 });
    expect(cfg.minInlineSize).toEqual({ kind: 'exact', px: 300 });
    expect(cfg.maxInlineSize).toEqual({ kind: 'exact', px: 150 });
  });

  it('parses bare-number percent (InlineSize_Percent_50 -> data:50.0)', () => {
    // Confirmed: SizeValue emits a bare number for percentages.
    const cfg = extractSize([p('InlineSize', 50)]);
    expect(cfg.inlineSize).toEqual({ kind: 'relative', value: 50, unit: 'percent' });
  });

  it('parses "auto" on BlockSize', () => {
    const cfg = extractSize([p('BlockSize', 'auto')]);
    expect(cfg.blockSize).toEqual({ kind: 'auto' });
  });
});

describe('extractSize — AspectRatio + mixed input', () => {
  it('stores AspectRatio separately from lengths', () => {
    const cfg = extractSize([
      p('Width', { type: 'length', px: 200 }),
      p('AspectRatio', { ratio: { w: 16, h: 9 }, normalizedRatio: 16 / 9 }),
    ]);
    expect(cfg.width).toEqual({ kind: 'exact', px: 200 });
    expect(cfg.aspectRatio).toEqual({ ratio: 16 / 9, isAuto: false });
  });

  it('skips unknown AspectRatio shapes without crashing', () => {
    // Extractor returns null for garbage -> no aspectRatio field emitted.
    const cfg = extractSize([p('AspectRatio', { ratio: {} })]);
    expect(cfg.aspectRatio).toBeUndefined();
  });

  it('ignores non-sizing properties', () => {
    const cfg = extractSize([
      p('PaddingTop',      { px: 10 }),
      p('BackgroundColor', '#fff'),
    ]);
    expect(cfg).toEqual({});
  });

  it('returns empty config when no sizing properties are present', () => {
    expect(extractSize([])).toEqual({});
  });
});
