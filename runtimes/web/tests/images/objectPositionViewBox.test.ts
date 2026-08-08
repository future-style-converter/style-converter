// objectPositionViewBox.test.ts — wave-36 lane M1: the two IMAGES
// extractors that could not read their own IR shape.
//
// WHAT WAS BROKEN. `ObjectPosition`'s IR value is a Position CAPSULE,
// `{x: <axis>, y: <axis>}` (converter/.../images/ObjectPositionProperty.kt),
// and `ObjectViewBox`'s is a basic shape, `{type:'inset', top, right,
// bottom, left}`. Both extractors folded through the shared `keywordOrRaw`,
// which looks for `.keyword`/`.value`/`.raw` on the TOP object, found none,
// and returned undefined — so every object-position and every inset view box
// in the corpus was silently dropped and the browser painted the `50% 50%` /
// `none` initials. In the css-images `object-fit-*` family that collapsed
// six of the seven boxes in every row onto one another, which no amount of
// correct object-fit could recover.
//
// The payload shapes below are BYTE-COPIES from the live wave-35 section IR
// (tools/titan/runs/wave35-webmap/sections/css-images/out/tmpOutput.json),
// not invented — including the two length spellings the wire really uses
// (`{px:N}` for absolutized lengths, `{original:{v,u:'PERCENT'}}` for
// percentages, which resolve against box − content size at paint time).
import { describe, it, expect } from 'vitest';
import { extractObjectPosition } from '../../src/engine/images/ObjectPositionExtractor';
import { extractObjectViewBox } from '../../src/engine/images/ObjectViewBoxExtractor';

const pos = (data: unknown) => extractObjectPosition([{ type: 'ObjectPosition', data }]).value;
const vb = (data: unknown) => extractObjectViewBox([{ type: 'ObjectViewBox', data }]).value;

describe('ObjectPositionExtractor — the {x, y} capsule', () => {
  it('keyword axes emit both halves in x-then-y CSS order', () => {
    // `object-position: top right` — the converter now assigns x=RIGHT,
    // y=TOP (css-values-4 <position> third arm is unordered), and this side
    // must emit them in the CSS order, which is horizontal first.
    expect(pos({
      x: { type: 'keyword', value: 'RIGHT' },
      y: { type: 'keyword', value: 'TOP' },
    })).toBe('right top');
  });

  it('keyword-offset axes emit `<keyword> <length>` per axis', () => {
    // `object-position: bottom 1px right 2px`.
    expect(pos({
      x: { type: 'keyword-offset', keyword: 'RIGHT', offset: { px: 2 } },
      y: { type: 'keyword-offset', keyword: 'BOTTOM', offset: { px: 1 } },
    })).toBe('right 2px bottom 1px');
  });

  it('percentage offsets keep their unit (they resolve at paint time)', () => {
    // `object-position: top 25% left 25%` — percentages stay symbolic
    // because §5.2 resolves them against (box − content), which only the
    // rendering surface knows.
    expect(pos({
      x: { type: 'keyword-offset', keyword: 'LEFT', offset: { original: { v: 25, u: 'PERCENT' } } },
      y: { type: 'keyword-offset', keyword: 'TOP', offset: { original: { v: 25, u: 'PERCENT' } } },
    })).toBe('left 25% top 25%');
  });

  it('bare percentage and bare length axes', () => {
    expect(pos({
      x: { type: 'percentage', percentage: 50 },
      y: { type: 'percentage', percentage: -7 },
    })).toBe('50% -7%');
    expect(pos({
      x: { type: 'length', px: 25 },
      y: { type: 'length', px: 125 },
    })).toBe('25px 125px');
  });

  it('global and raw arms emit ONCE, not doubled', () => {
    // The parser stores the same object on BOTH axes for these whole-value
    // arms, so a naive join would emit `inherit inherit` — which the browser
    // drops, silently reverting to the initial.
    expect(pos({
      x: { type: 'global', value: 'inherit' },
      y: { type: 'global', value: 'inherit' },
    })).toBe('inherit');
    expect(pos({
      x: { type: 'raw', value: 'var(--p)' },
      y: { type: 'raw', value: 'var(--p)' },
    })).toBe('var(--p)');
  });

  it('a half-readable capsule emits NOTHING rather than half a position', () => {
    // One axis alone would silently re-centre the other — a different wrong
    // answer, not a partial right one.
    expect(pos({ x: { type: 'keyword', value: 'RIGHT' }, y: { type: 'mystery' } }))
      .toBeUndefined();
    expect(pos('nonsense')).toBeUndefined();
    expect(pos(null)).toBeUndefined();
  });
});

describe('ObjectViewBoxExtractor — the inset() basic shape', () => {
  it('inset emits all four edges in CSS clockwise order', () => {
    // `object-view-box: inset(50px 0px 0px 25px)`.
    expect(vb({
      type: 'inset',
      top: { px: 50 }, right: { px: 0 }, bottom: { px: 0 }, left: { px: 25 },
    })).toBe('inset(50px 0px 0px 25px)');
  });

  it('a missing edge drops the whole declaration', () => {
    // A three-edge emission would move the view box somewhere the author
    // never wrote; declining leaves the browser on the `none` initial.
    expect(vb({ type: 'inset', top: { px: 50 }, right: { px: 0 }, bottom: { px: 0 } }))
      .toBeUndefined();
  });

  it('the none arm still passes through the shared keyword path', () => {
    expect(vb({ type: 'none' })).toBe('none');
    expect(vb('NONE')).toBe('none');
  });

  // ── wave-37 lane W2: the other two <basic-shape-rect> spellings ────────
  it('rect() emits its four edges', () => {
    // `object-view-box: rect(50px 50px 100px 25px)` (WPT object-view-box-rect).
    expect(vb({
      type: 'rect',
      top: { px: 50 }, right: { px: 50 }, bottom: { px: 100 }, left: { px: 25 },
    })).toBe('rect(50px 50px 100px 25px)');
  });

  it('xywh() emits origin then size, not edges', () => {
    // `object-view-box: xywh(25px 50px 25px 50px)` (WPT object-view-box-xywh).
    expect(vb({
      type: 'xywh',
      x: { px: 25 }, y: { px: 50 }, width: { px: 25 }, height: { px: 50 },
    })).toBe('xywh(25px 50px 25px 50px)');
  });

  it('percentage arguments arrive as bare numbers and regain their unit', () => {
    // IRLengthPercentage.Percentage serializes as a RAW NUMBER; emitting a
    // unitless `50` would invalidate the whole function in the browser.
    expect(vb({ type: 'xywh', x: 50, y: 50, width: 50, height: 50 }))
      .toBe('xywh(50% 50% 50% 50%)');
    expect(vb({ type: 'rect', top: 50, right: 100, bottom: 100, left: 50 }))
      .toBe('rect(50% 100% 100% 50%)');
  });

  it('a missing rect/xywh argument drops the declaration like inset', () => {
    expect(vb({ type: 'xywh', x: { px: 25 }, y: { px: 50 }, width: { px: 25 } }))
      .toBeUndefined();
  });
});
