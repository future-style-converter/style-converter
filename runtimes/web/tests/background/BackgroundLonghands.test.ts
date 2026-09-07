// Background longhands: size, position, repeat, clip, origin, attachment.
import { describe, it, expect } from 'vitest';
import { extractBackgroundSize } from '../../src/engine/background/BackgroundSizeExtractor';
import { applyBackgroundSize } from '../../src/engine/background/BackgroundSizeApplier';
import { extractBackgroundPosition } from '../../src/engine/background/BackgroundPositionExtractor';
import { applyBackgroundPosition } from '../../src/engine/background/BackgroundPositionApplier';
import { extractBackgroundRepeat } from '../../src/engine/background/BackgroundRepeatExtractor';
import { applyBackgroundRepeat } from '../../src/engine/background/BackgroundRepeatApplier';
import { extractBackgroundClip } from '../../src/engine/background/BackgroundClipExtractor';
import { applyBackgroundClip } from '../../src/engine/background/BackgroundClipApplier';
import { extractBackgroundOrigin } from '../../src/engine/background/BackgroundOriginExtractor';
import { applyBackgroundOrigin } from '../../src/engine/background/BackgroundOriginApplier';
import { extractBackgroundAttachment } from '../../src/engine/background/BackgroundAttachmentExtractor';
import { applyBackgroundAttachment } from '../../src/engine/background/BackgroundAttachmentApplier';

const p = (type: string, data: unknown) => ({ type, data });

describe('BackgroundSize', () => {
  it('handles cover / contain / auto keywords', () => {
    expect(applyBackgroundSize(extractBackgroundSize([p('BackgroundSize', ['cover'])])).backgroundSize).toBe('cover');
    expect(applyBackgroundSize(extractBackgroundSize([p('BackgroundSize', ['contain'])])).backgroundSize).toBe('contain');
    expect(applyBackgroundSize(extractBackgroundSize([p('BackgroundSize', ['auto'])])).backgroundSize).toBe('auto');
  });

  it('handles {w:{px:N}} single-value', () => {
    const cfg = extractBackgroundSize([p('BackgroundSize', [{ w: { px: 100 } }])]);
    expect(applyBackgroundSize(cfg).backgroundSize).toBe('100px');
  });

  it('handles bare number w as percentage', () => {
    const cfg = extractBackgroundSize([p('BackgroundSize', [{ w: 50 }])]);
    expect(applyBackgroundSize(cfg).backgroundSize).toBe('50%');
  });

  it('handles {w, h} bare-number as percentages (edge case)', () => {
    const cfg = extractBackgroundSize([p('BackgroundSize', [{ w: 50.0, h: 100.0 }])]);
    expect(applyBackgroundSize(cfg).backgroundSize).toBe('50% 100%');
  });

  it('handles {w:{px}, h:{px}}', () => {
    const cfg = extractBackgroundSize([p('BackgroundSize', [{ w: { px: 100 }, h: { px: 50 } }])]);
    expect(applyBackgroundSize(cfg).backgroundSize).toBe('100px 50px');
  });
});

describe('BackgroundPosition', () => {
  it('combines X + Y keyword', () => {
    const cfg = extractBackgroundPosition([
      p('BackgroundPositionX', { type: 'keyword', value: 'LEFT' }),
      p('BackgroundPositionY', { type: 'keyword', value: 'TOP' }),
    ]);
    expect(applyBackgroundPosition(cfg).backgroundPosition).toBe('left top');
  });

  it('percentage + length mix', () => {
    const cfg = extractBackgroundPosition([
      p('BackgroundPositionX', { type: 'length', px: 20 }),
      p('BackgroundPositionY', { type: 'percentage', percentage: 30 }),
    ]);
    expect(applyBackgroundPosition(cfg).backgroundPosition).toBe('20px 30%');
  });

  it('emits per-axis longhand when only X is set', () => {
    const cfg = extractBackgroundPosition([
      p('BackgroundPositionX', { type: 'keyword', value: 'RIGHT' }),
    ]);
    const out = applyBackgroundPosition(cfg);
    expect(out.backgroundPositionX).toBe('right');
    expect(out.backgroundPosition).toBeUndefined();
  });

  // ---- SHORTHAND wire — the `background` shorthand emits a single
  // `BackgroundPosition` whose data is a tagged PositionValue LIST.  All
  // payloads below are pinned byte-for-byte against live converter output
  // (JDK 21, `--to ir`) of the CSS noted on each test.

  it('shorthand wire: keyword pair (background: red url(x) right bottom)', () => {
    // Live wire: {"type":"BackgroundPosition","data":[{"type":"two-value",
    //   "x":{"type":"right"},"y":{"type":"bottom"}}]}
    const cfg = extractBackgroundPosition([
      p('BackgroundPosition', [
        { type: 'two-value', x: { type: 'right' }, y: { type: 'bottom' } },
      ]),
    ]);
    expect(applyBackgroundPosition(cfg).backgroundPosition).toBe('right bottom');
  });

  it('shorthand wire: single center (background: blue url(x) center)', () => {
    // Live wire: "data":[{"type":"center"}] — one-value center fills both axes.
    const cfg = extractBackgroundPosition([
      p('BackgroundPosition', [{ type: 'center' }]),
    ]);
    expect(applyBackgroundPosition(cfg).backgroundPosition).toBe('center center');
  });

  it('shorthand wire: length pair (background: green url(x) 10px 20px)', () => {
    // Live wire: "data":[{"type":"two-value","x":{"type":"length","px":10.0},
    //   "y":{"type":"length","px":20.0}}]
    const cfg = extractBackgroundPosition([
      p('BackgroundPosition', [
        {
          type: 'two-value',
          x: { type: 'length', px: 10 },
          y: { type: 'length', px: 20 },
        },
      ]),
    ]);
    expect(applyBackgroundPosition(cfg).backgroundPosition).toBe('10px 20px');
  });

  it('shorthand wire: percentage pair (background: green url(x) 25% 75%)', () => {
    // Live wire: "data":[{"type":"two-value","x":{"type":"percentage",
    //   "percentage":25.0},"y":{"type":"percentage","percentage":75.0}}]
    const cfg = extractBackgroundPosition([
      p('BackgroundPosition', [
        {
          type: 'two-value',
          x: { type: 'percentage', percentage: 25 },
          y: { type: 'percentage', percentage: 75 },
        },
      ]),
    ]);
    expect(applyBackgroundPosition(cfg).backgroundPosition).toBe('25% 75%');
  });

  it('shorthand wire: one-value keyword folds the missing axis to center', () => {
    // Model variant PositionValue.Keyword ({"type":"keyword","keyword":"top"});
    // css-backgrounds-3 §2.6: single `top` ≡ `center top`.
    const cfg = extractBackgroundPosition([
      p('BackgroundPosition', [{ type: 'keyword', keyword: 'top' }]),
    ]);
    expect(applyBackgroundPosition(cfg).backgroundPosition).toBe('center top');
  });

  it('shorthand wire: raw variant is dropped, not misrendered', () => {
    // PositionValue.Raw carries unresolvable text (var()/calc()) — no per-axis
    // decomposition exists, so the extractor must leave the config empty.
    const cfg = extractBackgroundPosition([
      p('BackgroundPosition', [{ type: 'raw', value: 'var(--pos)' }]),
    ]);
    expect(applyBackgroundPosition(cfg)).toEqual({});
  });

  it('longhand path unchanged: background-position: 25% 75%', () => {
    // Live wire for the LONGHAND declaration — still split into X/Y:
    //   {"type":"BackgroundPositionX","data":{"type":"percentage","percentage":25.0}}
    //   {"type":"BackgroundPositionY","data":{"type":"percentage","percentage":75.0}}
    const cfg = extractBackgroundPosition([
      p('BackgroundPositionX', { type: 'percentage', percentage: 25 }),
      p('BackgroundPositionY', { type: 'percentage', percentage: 75 }),
    ]);
    expect(applyBackgroundPosition(cfg).backgroundPosition).toBe('25% 75%');
  });
});

describe('BackgroundRepeat', () => {
  it('handles bare keywords', () => {
    expect(applyBackgroundRepeat(extractBackgroundRepeat([p('BackgroundRepeat', ['no-repeat'])])).backgroundRepeat)
      .toBe('no-repeat');
    expect(applyBackgroundRepeat(extractBackgroundRepeat([p('BackgroundRepeat', ['space'])])).backgroundRepeat)
      .toBe('space');
  });

  it('collapses { x:"repeat", y:"no-repeat" } to "repeat no-repeat"', () => {
    const cfg = extractBackgroundRepeat([p('BackgroundRepeat', [{ x: 'repeat', y: 'no-repeat' }])]);
    expect(applyBackgroundRepeat(cfg).backgroundRepeat).toBe('repeat no-repeat');
  });
});

describe('BackgroundClip', () => {
  it('lowercases uppercase enum strings', () => {
    expect(applyBackgroundClip(extractBackgroundClip([p('BackgroundClip', ['BORDER_BOX'])])).backgroundClip)
      .toBe('border-box');
    expect(applyBackgroundClip(extractBackgroundClip([p('BackgroundClip', ['PADDING_BOX'])])).backgroundClip)
      .toBe('padding-box');
    expect(applyBackgroundClip(extractBackgroundClip([p('BackgroundClip', ['CONTENT_BOX'])])).backgroundClip)
      .toBe('content-box');
    expect(applyBackgroundClip(extractBackgroundClip([p('BackgroundClip', ['TEXT'])])).backgroundClip)
      .toBe('text');
  });
});

describe('BackgroundOrigin', () => {
  it('reads {type:"border-box"} etc.', () => {
    expect(applyBackgroundOrigin(extractBackgroundOrigin([p('BackgroundOrigin', [{ type: 'border-box' }])]))
      .backgroundOrigin).toBe('border-box');
    expect(applyBackgroundOrigin(extractBackgroundOrigin([p('BackgroundOrigin', [{ type: 'padding-box' }])]))
      .backgroundOrigin).toBe('padding-box');
  });
});

describe('BackgroundAttachment', () => {
  it('reads {type:"scroll"|"fixed"|"local"}', () => {
    expect(applyBackgroundAttachment(extractBackgroundAttachment([p('BackgroundAttachment', [{ type: 'scroll' }])]))
      .backgroundAttachment).toBe('scroll');
    expect(applyBackgroundAttachment(extractBackgroundAttachment([p('BackgroundAttachment', [{ type: 'fixed' }])]))
      .backgroundAttachment).toBe('fixed');
    expect(applyBackgroundAttachment(extractBackgroundAttachment([p('BackgroundAttachment', [{ type: 'local' }])]))
      .backgroundAttachment).toBe('local');
  });
});
