// Background longhands: size, position, repeat, clip, origin, attachment.
import { describe, it, expect } from 'vitest';
import { extractBackgroundSize } from '../../../src/style/engine/background/BackgroundSizeExtractor';
import { applyBackgroundSize } from '../../../src/style/engine/background/BackgroundSizeApplier';
import { extractBackgroundPosition } from '../../../src/style/engine/background/BackgroundPositionExtractor';
import { applyBackgroundPosition } from '../../../src/style/engine/background/BackgroundPositionApplier';
import { extractBackgroundRepeat } from '../../../src/style/engine/background/BackgroundRepeatExtractor';
import { applyBackgroundRepeat } from '../../../src/style/engine/background/BackgroundRepeatApplier';
import { extractBackgroundClip } from '../../../src/style/engine/background/BackgroundClipExtractor';
import { applyBackgroundClip } from '../../../src/style/engine/background/BackgroundClipApplier';
import { extractBackgroundOrigin } from '../../../src/style/engine/background/BackgroundOriginExtractor';
import { applyBackgroundOrigin } from '../../../src/style/engine/background/BackgroundOriginApplier';
import { extractBackgroundAttachment } from '../../../src/style/engine/background/BackgroundAttachmentExtractor';
import { applyBackgroundAttachment } from '../../../src/style/engine/background/BackgroundAttachmentApplier';

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
