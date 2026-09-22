// @vitest-environment jsdom
// @vitest-environment-options { "url": "http://localhost:3000/?mode=capture&width=250" }
//
// LabelChrome.width250.test.tsx — the FRAME-width truncation pin for the
// harness label chrome (wave 51 PR (A); docs/DYNAMIC_CAPTURE.md "Harness
// label chrome"). The environment URL carries `?width=250`, so
// CaptureGallery's read-once CANVAS_WIDTH_PX resolves to 250 exactly the way
// a CAPTURE_WIDTH=250 run boots (capture-screenshots.mjs → &width=250) —
// the two-width media recipe, docs/DYNAMIC_CAPTURE.md §2.
//
// Shared rule: largest n with 8 + n*6 <= frameWidth - 8 → 39 glyphs at 250
// (62 at 390). The chrome must truncate against THIS number — never a
// component width (the WEB-LABEL-SPILL mechanism) and never Infinity.
//
// MUTATION RECORD (executed 2026-09-16 on this tree, source restored
// byte-exact afterwards): `Infinity` passed as `frameWidth` in
// CaptureGallery.CaptureCanvas instead of CANVAS_WIDTH_PX → "39 glyphs at
// 250" red: width attribute "599" (100 glyphs) against the expected "233".
// The FixtureCanvas pin below stayed green under that mutation (its own
// literal 390 was not the mutated value), which is exactly the
// independence it asserts.
import { describe, it, expect, afterEach } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { CaptureGallery } from '../../src/ui/CaptureGallery';
import { FixtureCanvas } from '../../src/ui/FixtureCanvas';
import type { IRComponent, IRDocument } from '@style-converter/web/core/ir/IRModels';

/** Minimal decoded v2 document. */
function doc(components: IRComponent[]): IRDocument {
  return { irVersion: 2, minReaderVersion: 2, components } as IRDocument;
}
/** Count distinct 6-px glyph cells the svg's rects occupy (block-local x). */
function glyphColumns(svg: Element): number {
  const cells = new Set<number>();
  svg.querySelectorAll('rect').forEach((r) => cells.add(Math.floor(Number(r.getAttribute('x')) / 6)));
  return cells.size;
}
// A 100-char name: far longer than either frame admits.
const LONG = { id: 'long', name: 'A'.repeat(100), properties: [] } as IRComponent;

afterEach(() => { document.body.innerHTML = ''; });

describe('LabelChrome — truncation against the capture FRAME width (?width=250)', () => {
  it('a 100-char name keeps 39 glyphs on a 250 frame: width attribute 38*6+5', () => {
    document.body.innerHTML = renderToStaticMarkup(<CaptureGallery document={doc([LONG])} />);
    const canvas = document.querySelector<HTMLElement>('[data-capture-canvas]')!;
    // Sanity: the URL override reached the canvas — the frame IS 250 wide.
    expect(canvas.getAttribute('style')).toContain('width:250px');
    const svg = canvas.querySelector('svg[data-label-chrome]')!;
    // DOM `SVGSVGElement.width` is an SVGAnimatedLength (undefined in
    // JSDOM) — the pin reads the ATTRIBUTE the harness sets from
    // layout.width (r3 correction). 8 + 39*6 = 242 <= 242; 40 would not fit.
    expect(svg.getAttribute('width')).toBe(String(38 * 6 + 5));
    expect(svg.getAttribute('height')).toBe('7');
    // 39 glyph columns — every 'A' cell has set bits, so cells == glyphs.
    expect(glyphColumns(svg)).toBe(39);
  });

  it('FixtureCanvas (Tier 5) keeps its literal 390 frame: 62 glyphs, independent of ?width=', () => {
    document.body.innerHTML = renderToStaticMarkup(<FixtureCanvas document={doc([LONG])} fixtureName="long" />);
    const wrapper = document.querySelector<HTMLElement>('[data-testid="long"]')!;
    // The Tier-5 canvas is always 390 wide (canvasStyle) — a `?width=250`
    // URL must not truncate its label to 39 glyphs on a 390 frame.
    expect(wrapper.getAttribute('style')).toContain('width:390px');
    const svg = wrapper.querySelector('svg[data-label-chrome]')!;
    expect(svg.getAttribute('width')).toBe(String(61 * 6 + 5));
    expect(glyphColumns(svg)).toBe(62);
  });
});
