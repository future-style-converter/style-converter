// Gradient reconstruction — covers linear/radial/conic, repeating, multi-stop,
// shape keywords stuffed into stops, and multi-layer composition.
import { describe, it, expect } from 'vitest';
import { extractBackgroundImage } from '../../../src/style/engine/background/BackgroundImageExtractor';
import { applyBackgroundImage } from '../../../src/style/engine/background/BackgroundImageApplier';

const p = (type: string, data: unknown) => ({ type, data });

// Helper to build a two-stop red->blue array.
const redBlueStops = [
  { color: { srgb: { r: 1, g: 0, b: 0 }, original: 'red' }, position: null },
  { color: { srgb: { r: 0, g: 0, b: 1 }, original: 'blue' }, position: null },
];

describe('BackgroundImage', () => {
  it('reconstructs basic linear gradient with no angle (default 180deg)', () => {
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{ type: 'linear-gradient', stops: redBlueStops }]),
    ]);
    const out = applyBackgroundImage(cfg);
    expect(out.backgroundImage).toBe('linear-gradient(180deg, rgba(255, 0, 0, 1), rgba(0, 0, 255, 1))');
  });

  it('reconstructs linear gradient with 45deg angle', () => {
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{
        type: 'linear-gradient', angle: { deg: 45 }, stops: redBlueStops,
      }]),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage).toContain('linear-gradient(45deg,');
  });

  it('emits stop positions as percentages', () => {
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{
        type: 'linear-gradient', angle: { deg: 90 },
        stops: [
          { color: { srgb: { r: 1, g: 0, b: 0 } }, position: 0 },
          { color: { srgb: { r: 0, g: 0.5, b: 0 } }, position: 50 },
          { color: { srgb: { r: 0, g: 0, b: 1 } }, position: 100 },
        ],
      }]),
    ]);
    const css = applyBackgroundImage(cfg).backgroundImage!;
    expect(css).toContain('0%');
    expect(css).toContain('50%');
    expect(css).toContain('100%');
  });

  it('reconstructs radial gradient with shape keyword stuffed in stops', () => {
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{
        type: 'radial-gradient',
        stops: [{ color: { original: 'circle' }, position: null }, ...redBlueStops],
      }]),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage).toBe(
      'radial-gradient(circle, rgba(255, 0, 0, 1), rgba(0, 0, 255, 1))',
    );
  });

  it('reconstructs conic gradient with from-angle', () => {
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{
        type: 'conic-gradient', angle: { deg: 30 }, stops: redBlueStops,
      }]),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage).toContain('conic-gradient(from 30deg,');
  });

  it('applies repeating- prefix', () => {
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{
        type: 'repeating-linear-gradient', angle: { deg: 45 }, stops: redBlueStops,
      }]),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage).toContain('repeating-linear-gradient(');
  });

  it('passes through url()', () => {
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{ url: 'data:image/png;base64,abc', data: true }]),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage).toBe('url(data:image/png;base64,abc)');
  });

  it('emits none for bare "none"', () => {
    const cfg = extractBackgroundImage([p('BackgroundImage', ['none'])]);
    expect(applyBackgroundImage(cfg).backgroundImage).toBe('none');
  });

  it('comma-joins multiple layers', () => {
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [
        { type: 'linear-gradient', angle: { deg: 90 }, stops: redBlueStops },
        { url: 'x.png' },
      ]),
    ]);
    const css = applyBackgroundImage(cfg).backgroundImage!;
    expect(css.startsWith('linear-gradient(')).toBe(true);
    expect(css).toContain(', url(x.png)');
  });

  it('unset -> empty styles', () => {
    expect(applyBackgroundImage(extractBackgroundImage([]))).toEqual({});
  });

  // Bug 5 (swarm-003 / swarm-002 css-images__image-orientation-background-position)
  //
  // IRUrlSerializer (src/main/kotlin/app/irmodels/ValueTypes.kt) encodes
  // non-data URLs as bare JSON string primitives. Previously layerCss()
  // silently dropped any bare string except 'none', so URLs like
  // `support/exif-orientation-2-ur.jpg` produced no background-image
  // declaration at all. The fix wraps non-'none' bare strings as
  // `url(...)` layers.

  it('wraps a non-"none" bare-string URL as url(...) [Bug 5]', () => {
    const cfg = extractBackgroundImage([
      p('BackgroundImage', ['support/exif-orientation-2-ur.jpg']),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage)
      .toBe('url(support/exif-orientation-2-ur.jpg)');
  });

  it('still emits "none" for the bare-"none" sentinel (legacy behaviour) [Bug 5]', () => {
    // Regression pin: the 'none' branch must NOT get rewritten to
    // `url(none)`. CSS `background-image: none` is the explicit
    // "no image" sentinel; rewriting would change semantics.
    const cfg = extractBackgroundImage([p('BackgroundImage', ['none'])]);
    expect(applyBackgroundImage(cfg).backgroundImage).toBe('none');
  });

  it('wraps multiple bare-string URLs as comma-joined url() layers [Bug 5]', () => {
    // The image-orientation test fixture in particular emits two
    // adjacent absolutely-positioned divs each with a single bare-string
    // URL; this test pins the multi-layer happy path.
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [
        'support/exif-orientation-2-ur.jpg',
        'support/exif-orientation-6-ru.jpg',
      ]),
    ]);
    const css = applyBackgroundImage(cfg).backgroundImage!;
    expect(css).toContain('url(support/exif-orientation-2-ur.jpg)');
    expect(css).toContain('url(support/exif-orientation-6-ru.jpg)');
  });
});
