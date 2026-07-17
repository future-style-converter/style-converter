// BorderImage — coverage for the 5 BorderImage* triplets.
// Fixture: examples/properties/borders/border-image.json.
import { describe, it, expect } from 'vitest';
import { extractBorderImageSource } from '../../../src/engine/borders/image/BorderImageSourceExtractor';
import { applyBorderImageSource }   from '../../../src/engine/borders/image/BorderImageSourceApplier';
import { extractBorderImageSlice }  from '../../../src/engine/borders/image/BorderImageSliceExtractor';
import { applyBorderImageSlice }    from '../../../src/engine/borders/image/BorderImageSliceApplier';
import { extractBorderImageWidth }  from '../../../src/engine/borders/image/BorderImageWidthExtractor';
import { applyBorderImageWidth }    from '../../../src/engine/borders/image/BorderImageWidthApplier';
import { extractBorderImageOutset } from '../../../src/engine/borders/image/BorderImageOutsetExtractor';
import { applyBorderImageOutset }   from '../../../src/engine/borders/image/BorderImageOutsetApplier';
import { extractBorderImageRepeat } from '../../../src/engine/borders/image/BorderImageRepeatExtractor';
import { applyBorderImageRepeat }   from '../../../src/engine/borders/image/BorderImageRepeatApplier';

const p = (type: string, data: unknown) => ({ type, data });

describe('BorderImageSource', () => {
  it('none keyword', () => {
    expect(applyBorderImageSource(extractBorderImageSource([
      p('BorderImageSource', { type: 'none' }),
    ]))).toEqual({ borderImageSource: 'none' });
  });
  it('url reference is always emitted quoted', () => {
    // Quoted form — an unquoted url() token cannot carry whitespace or
    // quotes (css-values-4 §4.5), so the serialiser always quotes.
    expect(applyBorderImageSource(extractBorderImageSource([
      p('BorderImageSource', { type: 'url', url: 'border.png' }),
    ]))).toEqual({ borderImageSource: 'url("border.png")' });
  });
  it('quote-and-space-laden SVG data URI survives as a valid declaration', () => {
    // The border-image fixture shape: an inline SVG data URI whose XML
    // attributes carry double quotes AND spaces. Unquoted, this produced
    // an invalid url() token the browser dropped wholesale — no platform
    // painted the border image. Double quotes must be backslash-escaped
    // inside the CSS <string> (css-values-4 §4.3); spaces need no escape.
    const uri = 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10">'
      + '<rect fill="red" width="10" height="10"/></svg>';
    expect(applyBorderImageSource(extractBorderImageSource([
      p('BorderImageSource', { type: 'url', url: uri }),
    ]))).toEqual({ borderImageSource: `url("${uri.replace(/"/g, '\\"')}")` });
  });
  it('backslashes and newlines are CSS-string escaped', () => {
    // Backslash doubles (it is the escape char); a raw newline is
    // invalid inside a CSS <string> and becomes the `\a ` hex escape.
    expect(applyBorderImageSource(extractBorderImageSource([
      p('BorderImageSource', { type: 'url', url: 'a\\b\n c' }),
    ]))).toEqual({ borderImageSource: 'url("a\\\\b\\a  c")' });
  });
  it('linear-gradient passthrough', () => {
    expect(applyBorderImageSource(extractBorderImageSource([
      p('BorderImageSource', { type: 'gradient', gradient: 'linear-gradient(45deg, red, blue)' }),
    ]))).toEqual({ borderImageSource: 'linear-gradient(45deg, red, blue)' });
  });
  it('radial-gradient passthrough', () => {
    expect(applyBorderImageSource(extractBorderImageSource([
      p('BorderImageSource', { type: 'gradient', gradient: 'radial-gradient(circle, red, yellow, blue)' }),
    ]))).toEqual({ borderImageSource: 'radial-gradient(circle, red, yellow, blue)' });
  });
});

describe('BorderImageSlice', () => {
  it('number quad (single value 1 1 1 1)', () => {
    expect(applyBorderImageSlice(extractBorderImageSlice([
      p('BorderImageSlice', {
        top: { type: 'number', value: 1 }, right: { type: 'number', value: 1 },
        bottom: { type: 'number', value: 1 }, left: { type: 'number', value: 1 },
      }),
    ]))).toEqual({ borderImageSlice: '1 1 1 1' });
  });
  it('percent quad', () => {
    expect(applyBorderImageSlice(extractBorderImageSlice([
      p('BorderImageSlice', {
        top: { type: 'percentage', value: 25 }, right: { type: 'percentage', value: 25 },
        bottom: { type: 'percentage', value: 25 }, left: { type: 'percentage', value: 25 },
      }),
    ]))).toEqual({ borderImageSlice: '25% 25% 25% 25%' });
  });
  it('four-value mix', () => {
    expect(applyBorderImageSlice(extractBorderImageSlice([
      p('BorderImageSlice', {
        top: { type: 'number', value: 10 }, right: { type: 'number', value: 20 },
        bottom: { type: 'number', value: 30 }, left: { type: 'number', value: 40 },
      }),
    ]))).toEqual({ borderImageSlice: '10 20 30 40' });
  });
});

describe('BorderImageWidth', () => {
  it('length quad', () => {
    expect(applyBorderImageWidth(extractBorderImageWidth([
      p('BorderImageWidth', {
        top: { type: 'length', px: 10 }, right: { type: 'length', px: 10 },
        bottom: { type: 'length', px: 10 }, left: { type: 'length', px: 10 },
      }),
    ]))).toEqual({ borderImageWidth: '10px 10px 10px 10px' });
  });
  it('auto quad', () => {
    expect(applyBorderImageWidth(extractBorderImageWidth([
      p('BorderImageWidth', {
        top: { type: 'auto' }, right: { type: 'auto' },
        bottom: { type: 'auto' }, left: { type: 'auto' },
      }),
    ]))).toEqual({ borderImageWidth: 'auto auto auto auto' });
  });
  it('number multiplier', () => {
    expect(applyBorderImageWidth(extractBorderImageWidth([
      p('BorderImageWidth', {
        top: { type: 'number', value: 2 }, right: { type: 'number', value: 2 },
        bottom: { type: 'number', value: 2 }, left: { type: 'number', value: 2 },
      }),
    ]))).toEqual({ borderImageWidth: '2 2 2 2' });
  });
  it('four-value mix', () => {
    expect(applyBorderImageWidth(extractBorderImageWidth([
      p('BorderImageWidth', {
        top: { type: 'length', px: 10 }, right: { type: 'length', px: 20 },
        bottom: { type: 'length', px: 30 }, left: { type: 'length', px: 40 },
      }),
    ]))).toEqual({ borderImageWidth: '10px 20px 30px 40px' });
  });
});

describe('BorderImageOutset', () => {
  it('length quad', () => {
    expect(applyBorderImageOutset(extractBorderImageOutset([
      p('BorderImageOutset', {
        top: { type: 'length', px: 10 }, right: { type: 'length', px: 10 },
        bottom: { type: 'length', px: 10 }, left: { type: 'length', px: 10 },
      }),
    ]))).toEqual({ borderImageOutset: '10px 10px 10px 10px' });
  });
  it('number quad', () => {
    expect(applyBorderImageOutset(extractBorderImageOutset([
      p('BorderImageOutset', {
        top: { type: 'number', value: 2 }, right: { type: 'number', value: 2 },
        bottom: { type: 'number', value: 2 }, left: { type: 'number', value: 2 },
      }),
    ]))).toEqual({ borderImageOutset: '2 2 2 2' });
  });
});

describe('BorderImageRepeat', () => {
  it.each(['stretch', 'repeat', 'round', 'space'])('single keyword %s', (kw) => {
    expect(applyBorderImageRepeat(extractBorderImageRepeat([p('BorderImageRepeat', kw.toUpperCase())])))
      .toEqual({ borderImageRepeat: kw });
  });
  it('two-value form', () => {
    expect(applyBorderImageRepeat(extractBorderImageRepeat([
      p('BorderImageRepeat', { horizontal: 'REPEAT', vertical: 'STRETCH' }),
    ]))).toEqual({ borderImageRepeat: 'repeat stretch' });
  });
  it('two-value equals collapses', () => {
    expect(applyBorderImageRepeat(extractBorderImageRepeat([
      p('BorderImageRepeat', { horizontal: 'ROUND', vertical: 'ROUND' }),
    ]))).toEqual({ borderImageRepeat: 'round' });
  });
});
