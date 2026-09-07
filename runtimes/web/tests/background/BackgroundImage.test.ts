// Gradient reconstruction — covers linear/radial/conic, repeating, multi-stop,
// shape keywords stuffed into stops, and multi-layer composition.
import { describe, it, expect } from 'vitest';
import { extractBackgroundImage } from '../../src/engine/background/BackgroundImageExtractor';
import { applyBackgroundImage } from '../../src/engine/background/BackgroundImageApplier';

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

  it('passes through url() as an always-QUOTED token', () => {
    // cssUrl quoting (css-values-4 §4.5) — shared with border-image.
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{ url: 'data:image/png;base64,abc', data: true }]),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage).toBe('url("data:image/png;base64,abc")');
  });

  it('round-trips a data URI with spaces and quotes [quoting hole]', () => {
    // An unquoted url() token cannot contain whitespace or quotes
    // (css-values-4 §4.5) — the old `url(${entry})` emitted an invalid
    // declaration the browser dropped WHOLESALE for exactly this input
    // (SVG data URIs are full of spaces + double quotes). cssUrl must
    // quote the token and escape the embedded quotes per §4.3.
    const svg = 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"/>';
    const cfg = extractBackgroundImage([p('BackgroundImage', [{ url: svg, data: true }])]);
    expect(applyBackgroundImage(cfg).backgroundImage).toBe(
      'url("data:image/svg+xml,<svg xmlns=\\"http://www.w3.org/2000/svg\\" width=\\"10\\" height=\\"10\\"/>")',
    );
  });

  it('quotes bare-string URLs with spaces too [quoting hole]', () => {
    // The bare-string branch (Bug 5) shares the same cssUrl serialiser.
    const cfg = extractBackgroundImage([p('BackgroundImage', ['my picture.png'])]);
    expect(applyBackgroundImage(cfg).backgroundImage).toBe('url("my picture.png")');
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
    expect(css).toContain(', url("x.png")');
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
      .toBe('url("support/exif-orientation-2-ur.jpg")');
  });

  it('still emits "none" for the bare-"none" sentinel (legacy behaviour) [Bug 5]', () => {
    // Regression pin: the 'none' branch must NOT get rewritten to
    // `url(none)`. CSS `background-image: none` is the explicit
    // "no image" sentinel; rewriting would change semantics.
    const cfg = extractBackgroundImage([p('BackgroundImage', ['none'])]);
    expect(applyBackgroundImage(cfg).backgroundImage).toBe('none');
  });

  // ── wave-37 lane W2: the <color-interpolation-method> carry ────────────
  it('re-emits the authored interpolation method inside the syntax prefix', () => {
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{
        type: 'linear-gradient', angle: { deg: 90 }, stops: redBlueStops,
        interp: 'in hsl increasing hue',
      }]),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage)
      .toBe('linear-gradient(90deg in hsl increasing hue, rgba(255, 0, 0, 1), rgba(0, 0, 255, 1))');
  });

  it('carries the method on radial and conic too', () => {
    const r = extractBackgroundImage([
      p('BackgroundImage', [{ type: 'radial-gradient', shape: 'circle', stops: redBlueStops, interp: 'in oklch' }]),
    ]);
    expect(applyBackgroundImage(r).backgroundImage).toContain('radial-gradient(circle in oklch,');
    const c = extractBackgroundImage([
      p('BackgroundImage', [{ type: 'conic-gradient', angle: { deg: 45 }, stops: redBlueStops, interp: 'in lch longer hue' }]),
    ]);
    expect(applyBackgroundImage(c).backgroundImage).toContain('conic-gradient(from 45deg in lch longer hue,');
  });

  it('emits a method-only radial prefix without a stray leading comma', () => {
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{ type: 'radial-gradient', stops: redBlueStops, interp: 'in oklab' }]),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage).toContain('radial-gradient(in oklab,');
  });

  it('drops an interp value outside the closed grammar rather than emitting it', () => {
    // Wire text is never trusted into a declaration: an unknown colour space
    // would invalidate the whole gradient in the browser.
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{ type: 'linear-gradient', angle: { deg: 90 }, stops: redBlueStops, interp: 'in nonesuch' }]),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage).toBe(
      'linear-gradient(90deg, rgba(255, 0, 0, 1), rgba(0, 0, 255, 1))');
  });

  it('drops a hue method riding a RECTANGULAR space (css-color-4 §13.2)', () => {
    // `in oklab longer hue` does not parse — re-emitting it would delete the
    // whole declaration, so the clause is dropped and the ramp survives.
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{ type: 'linear-gradient', angle: { deg: 90 }, stops: redBlueStops, interp: 'in oklab longer hue' }]),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage).toBe(
      'linear-gradient(90deg, rgba(255, 0, 0, 1), rgba(0, 0, 255, 1))');
  });

  it('keeps a bare rectangular space', () => {
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{ type: 'linear-gradient', angle: { deg: 90 }, stops: redBlueStops, interp: 'in oklab' }]),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage).toContain('linear-gradient(90deg in oklab,');
  });

  it('a gradient with no interp key is byte-identical to before', () => {
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{ type: 'linear-gradient', angle: { deg: 90 }, stops: redBlueStops }]),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage).toBe(
      'linear-gradient(90deg, rgba(255, 0, 0, 1), rgba(0, 0, 255, 1))');
  });

  // ── wave-37 lane W2: the {raw:…} <image> passthrough ────────────────────
  it('passes a single-layer image-set() through verbatim', () => {
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{ raw: 'image-set(url("/images/green.png") 1x)' }]),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage).toBe('image-set(url("/images/green.png") 1x)');
  });

  it('passes a raw gradient whose stops the converter could not type', () => {
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{ raw: 'linear-gradient(to right in lch increasing hue, lch(50% 100% 0deg), lch(50% 100% 80deg))' }]),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage).toContain('lch(50% 100% 0deg)');
  });

  it('refuses a raw value that is not an <image> function', () => {
    const cfg = extractBackgroundImage([p('BackgroundImage', [{ raw: 'var(--bg)' }])]);
    expect(applyBackgroundImage(cfg).backgroundImage).toBeUndefined();
  });

  it('refuses raw values that could escape the declaration or are unbalanced', () => {
    for (const raw of [
      'image-set(url(a.png) 1x); color: red',   // ';' would end the declaration
      'image-set(url(a.png) 1x',                // unbalanced
      'image-set(url(a.png) 1x) junk',          // trailing junk after the function
      'linear-gradient(red, blue) } body {',    // '}' escape attempt
    ]) {
      const cfg = extractBackgroundImage([p('BackgroundImage', [{ raw }])]);
      expect(applyBackgroundImage(cfg).backgroundImage).toBeUndefined();
    }
  });

  it('never lets a raw layer into a MULTI-layer declaration', () => {
    // One invalid layer would invalidate the comma-joined value and take the
    // valid siblings down with it — the only regression this feature could
    // cause, so multi-layer raws stay dropped.
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [
        { type: 'linear-gradient', angle: { deg: 90 }, stops: redBlueStops },
        { raw: 'image-set(url(a.png) 1x)' },
      ]),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage).toBe(
      'linear-gradient(90deg, rgba(255, 0, 0, 1), rgba(0, 0, 255, 1))');
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
    expect(css).toContain('url("support/exif-orientation-2-ur.jpg")');
    expect(css).toContain('url("support/exif-orientation-6-ru.jpg")');
  });

  // ---- wave-40 T6: the <length> arm of a stop position ---------------
  // css-images-4 §3.5.3 types a stop position as a <length-percentage>. The
  // percentage arm has always been the raw-number `position` key; the length
  // arm arrives as the additive `positionLength` key (an IRLength object).

  it('emits an absolute-length stop position in px', () => {
    // The WPT gradient-border-box declaration's wire form: the 30px stop is
    // the REPEAT PERIOD, and dropping it painted one full-box ramp (0.6458).
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{
        type: 'repeating-linear-gradient', angle: { deg: 135 },
        stops: [
          { color: { srgb: { r: 1, g: 1, b: 1 } }, position: null },
          { color: { srgb: { r: 0, g: 0, b: 0 } }, position: null },
          { color: { srgb: { r: 1, g: 1, b: 1 } }, position: null, positionLength: { px: 30 } },
        ],
      }]),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage).toBe(
      'repeating-linear-gradient(135deg, rgba(255, 255, 255, 1), rgba(0, 0, 0, 1), rgba(255, 255, 255, 1) 30px)');
  });

  it('re-emits a runtime-dependent stop unit verbatim for the browser to resolve', () => {
    // Same rule as the gradient centre's positionAxisCss: px is absent when
    // the unit cannot be pre-resolved, so the authored value+unit rides out.
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{
        type: 'linear-gradient', angle: { deg: 90 },
        stops: [
          { color: { srgb: { r: 1, g: 0, b: 0 } }, position: null },
          { color: { srgb: { r: 0, g: 0, b: 1 } }, position: null, positionLength: { original: { v: 2, u: 'EM' } } },
        ],
      }]),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage).toContain('rgba(0, 0, 255, 1) 2em');
  });

  it('lets the percentage arm win when both keys somehow arrive', () => {
    // Defensive: `position` is the frozen key, so it stays authoritative and
    // a stray length can never produce two positions on one stop.
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{
        type: 'linear-gradient', angle: { deg: 90 },
        stops: [
          { color: { srgb: { r: 1, g: 0, b: 0 } }, position: 25, positionLength: { px: 30 } },
          { color: { srgb: { r: 0, g: 0, b: 1 } }, position: null },
        ],
      }]),
    ]);
    const css = applyBackgroundImage(cfg).backgroundImage!;
    expect(css).toContain('rgba(255, 0, 0, 1) 25%');
    expect(css).not.toContain('30px');
  });

  it('ignores a malformed positionLength instead of emitting junk', () => {
    for (const positionLength of [null, 'thirty', 30, {}, { px: 'x' }]) {
      const cfg = extractBackgroundImage([
        p('BackgroundImage', [{
          type: 'linear-gradient', angle: { deg: 90 },
          stops: [
            { color: { srgb: { r: 1, g: 0, b: 0 } }, position: null, positionLength },
            { color: { srgb: { r: 0, g: 0, b: 1 } }, position: null },
          ],
        }]),
      ]);
      expect(applyBackgroundImage(cfg).backgroundImage).toBe(
        'linear-gradient(90deg, rgba(255, 0, 0, 1), rgba(0, 0, 255, 1))');
    }
  });

  // ---- wave-40 T6: display-p3-linear joins the rectangular table -----

  it('emits `in display-p3-linear` (the capture browser accepts it)', () => {
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{
        type: 'linear-gradient', angle: { deg: 90 },
        stops: redBlueStops, interp: 'in display-p3-linear',
      }]),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage).toContain('linear-gradient(90deg in display-p3-linear,');
  });

  it('still refuses the linear-light spaces the capture browser rejects', () => {
    // Measured on Chrome 151: CSS.supports rejects every one of these, and an
    // unparsed gradient takes the WHOLE declaration with it — so the clause is
    // dropped and the ramp survives, exactly as for any unknown space.
    for (const interp of ['in a98-rgb-linear', 'in prophoto-rgb-linear', 'in rec2020-linear', 'in rec2100-pq']) {
      const cfg = extractBackgroundImage([
        p('BackgroundImage', [{ type: 'linear-gradient', angle: { deg: 90 }, stops: redBlueStops, interp }]),
      ]);
      expect(applyBackgroundImage(cfg).backgroundImage).toBe(
        'linear-gradient(90deg, rgba(255, 0, 0, 1), rgba(0, 0, 255, 1))');
    }
  });

  // ---- wave-48 W5: typed lab-family stop originals re-emit verbatim ----

  it('re-emits an lch stop from its typed original, not the clipped rgba', () => {
    // VERBATIM wave48-cal wire shape for a WPT gradient-decreasing-hue-lch
    // stop after the chroma-% parser fix: chroma 150 is far outside srgb, so
    // the srgb triple is a simple clip; the browser must get the author's
    // own colour so its per-sample gamut mapping matches the reference.
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{
        type: 'linear-gradient', angle: { deg: 90 }, interp: 'in lch decreasing hue',
        stops: [
          { color: { srgb: { r: 1, g: 0, b: 0.4 }, original: { type: 'lch', l: 50, c: 150, h: 0 } }, position: null },
          { color: { srgb: { r: 0.9, g: 0.2, b: 0 }, original: { type: 'lch', l: 50, c: 150, h: 80 } }, position: null },
        ],
      }]),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage).toBe(
      'linear-gradient(90deg in lch decreasing hue, lch(50 150 0), lch(50 150 80))');
  });

  it('carries the typed original alpha through (powerless-hue wire shape)', () => {
    // VERBATIM wave48-cal per-test-ir stop of gradient-powerless-hue-oklch
    // (post-fix canonical L: 86.64…% → 0.8664…). alpha rides after a slash.
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{
        type: 'linear-gradient', angle: { deg: 90 }, interp: 'in oklch',
        stops: [
          { color: { srgb: { r: 1, g: 0, b: 0 }, original: 'red' }, position: null },
          { color: { srgb: { r: 0, g: 1, b: 0, a: 0 }, original: { type: 'oklch', l: 0.8664396175234369, c: 0.295, h: 142.4953450414439, alpha: 0 } }, position: null },
        ],
      }]),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage).toBe(
      'linear-gradient(90deg in oklch, rgba(255, 0, 0, 1), oklch(0.8664396175234369 0.295 142.4953450414439 / 0))');
  });

  it('guards a legacy 0..100 ok-space lightness back onto the srgb path', () => {
    // VERBATIM wave48-cal per-test-ir stop of gradient-powerless-hue-oklch
    // as emitted BEFORE the wave-48 ColorParser %-scaling fix: `l` carries
    // 86.64… (the legacy 0..100 convention for oklch(86.64% …)). Re-emitted
    // verbatim this would become oklch(86.64… …), which the browser clamps
    // to WHITE (S6 must-fix 3, executed repro on a cell scoring 0.9994 P) —
    // so the OK-SPACE LIGHTNESS GUARD must refuse the typed path and fall
    // through to the srgb rgba() serialisation instead.
    const legacy = extractBackgroundImage([
      p('BackgroundImage', [{
        type: 'linear-gradient', angle: { deg: 90 }, interp: 'in oklch',
        stops: [
          { color: { srgb: { r: 1, g: 0, b: 0 }, original: 'red' }, position: null },
          { color: { srgb: { r: 0, g: 1, b: 0, a: 0 }, original: { type: 'oklch', l: 86.64396175234369, c: 0.295, h: 142.4953450414439, alpha: 0 } }, position: null },
        ],
      }]),
    ]);
    expect(applyBackgroundImage(legacy).backgroundImage).toBe(
      'linear-gradient(90deg in oklch, rgba(255, 0, 0, 1), rgba(0, 255, 0, 0))');
    // The canonical 0..1 twin of the SAME stop (the post-fix wire) must
    // keep the typed path — the author fidelity the guard must not cost.
    // (The alpha-carry pin above asserts this shape independently; paired
    // here so guard and fidelity are proven against each other.)
    const canonical = extractBackgroundImage([
      p('BackgroundImage', [{
        type: 'linear-gradient', angle: { deg: 90 }, interp: 'in oklch',
        stops: [
          { color: { srgb: { r: 1, g: 0, b: 0 }, original: 'red' }, position: null },
          { color: { srgb: { r: 0, g: 1, b: 0, a: 0 }, original: { type: 'oklch', l: 0.8664396175234369, c: 0.295, h: 142.4953450414439, alpha: 0 } }, position: null },
        ],
      }]),
    ]);
    expect(applyBackgroundImage(canonical).backgroundImage).toBe(
      'linear-gradient(90deg in oklch, rgba(255, 0, 0, 1), oklch(0.8664396175234369 0.295 142.4953450414439 / 0))');
  });

  it('string originals and plain srgb stops keep the rgba path byte-identically', () => {
    // The guard: hwb/color()/named stops are NOT typed-re-emitted (see the
    // TYPED_LAB_FAMILIES note) — nothing outside the lab family moves.
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{
        type: 'linear-gradient', angle: { deg: 90 },
        stops: [
          { color: { srgb: { r: 1, g: 0, b: 0 }, original: 'red' }, position: null },
          { color: { srgb: { r: 0, g: 1, b: 0 }, original: { type: 'hwb', h: 120, w: 0, b: 0 } }, position: null },
        ],
      }]),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage).toBe(
      'linear-gradient(90deg, rgba(255, 0, 0, 1), rgba(0, 255, 0, 1))');
  });

  // ---- wave-48 W5: image() notation lowering -------------------------

  it('lowers image(src, fallback) to a url layer over a solid-colour underlay', () => {
    // Wire of WPT css-image-fallbacks-and-annotations 001 (`background:
    // image("green.png", green)`): the url 404s in the capture browser and
    // the solid green beneath it shows — §2.1's fallback outcome.
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{
        type: 'image', srcs: ['green.png'],
        color: { srgb: { r: 0, g: 0.5019607843137255, b: 0 }, original: 'green' },
      }]),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage).toBe(
      'url("green.png"), linear-gradient(rgba(0, 128, 0, 1), rgba(0, 128, 0, 1))');
  });

  it('stacks legacy multi-src candidates in author try-order', () => {
    // Wire of 003/004: first candidate missing on disk → paints nothing →
    // the next one shows through (first-that-loads for opaque images).
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{
        type: 'image', srcs: ['1x1-green.svg', 'support/1x1-green.png', 'support/1x1-green.gif'],
      }]),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage).toBe(
      'url("1x1-green.svg"), url("support/1x1-green.png"), url("support/1x1-green.gif")');
  });

  it('renders a src-less image(color) as the solid-colour image', () => {
    // Wire of 005 layer 1 — image(rgba(0,0,255,0.5)) composited over the
    // second (url) layer by the browser.
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [
        { type: 'image', srcs: [], color: { srgb: { r: 0, g: 0, b: 1, a: 0.5 }, original: { r: 0, g: 0, b: 255, a: 0.5 } } },
        'support/1x1-green.png',
      ]),
    ]);
    expect(applyBackgroundImage(cfg).backgroundImage).toBe(
      'linear-gradient(rgba(0, 0, 255, 0.5), rgba(0, 0, 255, 0.5)), url("support/1x1-green.png")');
  });

  // ---- wave-49 lane A3: the wire shape the corpus now DELIVERS ---------
  //
  // The three tests above pin the wire as it stood when the lowering shipped:
  // candidates as bare author-relative strings. That is no longer what web
  // receives. tools/titan/extract-fixture.mjs now lowers every inlinable
  // bare-string <image-src> to `url(data:…)` before the converter sees it —
  // the delivery channel the NATIVES need (a device cannot reach the host's
  // corpus), and the converter's UrlParser turns each one into the OBJECT
  // form of IRUrl, `{url, data:true}`, rather than a bare string.
  //
  // So this is a NEW live shape on the web path, and web is the platform
  // currently scoring 1.0000 on these tests: the pin exists so a change that
  // improves the natives cannot silently regress web on the same wire.
  //
  // PAYLOAD PROVENANCE — verbatim, not invented: the exact
  // `converter/build` output of
  // fixtures/wpt/css-images/css-image-fallbacks-and-annotations003.json
  // (`:converter:run --from css --to ir`), truncated here ONLY in the middle
  // of the two percent-encoded bodies, which this lowering never inspects.
  it('stacks object-form data: candidates from the inlined corpus wire', () => {
    const PNG = 'data:image/png,%89%50%4e%47%0d%0a%1a%0a%00%00%00%0d%49%48%44%52';
    const GIF = 'data:image/gif,%47%49%46%38%39%61%01%00%01%00%80%00%00%00%7f';
    const cfg = extractBackgroundImage([
      p('BackgroundImage', [{
        type: 'image',
        // Candidate 1 stayed a bare string: `1x1-green.svg` is not on disk
        // beside the test (which is precisely what 003 asserts) and SVG is
        // not in the inliner's raster table, so nothing rewrote it.
        srcs: ['1x1-green.svg', { url: PNG, data: true }, { url: GIF, data: true }],
      }]),
    ]);
    // Author order preserved, object form unwrapped to the same url() token
    // a bare string produces: §2.1's try-order is the CSS layer order, and
    // the browser paints the first candidate that loads.
    expect(applyBackgroundImage(cfg).backgroundImage).toBe(
      `url("1x1-green.svg"), url("${PNG}"), url("${GIF}")`);
  });
});
