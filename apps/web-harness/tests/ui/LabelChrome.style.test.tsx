// @vitest-environment jsdom
// @vitest-environment-options { "url": "http://localhost:3000/?mode=capture" }
//
// LabelChrome.style.test.tsx — the svg's OWN attribute pins for the harness
// label chrome (wave 51 PR (A); docs/DYNAMIC_CAPTURE.md "Harness label
// chrome"), split out of LabelChrome.test.tsx on 2026-09-22 (which keeps the
// width-independent structural pins — mount site, gating, one per capture).
// Pinned here: the (8,6) frame-origin style tokens, the stacking / pointer /
// display tokens, crispEdges, the fill SPELLING, and the 390-frame truncation
// geometry (62 glyphs, tight width 371, one cell tall, untruncated aria name).
//
// Same read-once boot as the structural file: `?mode=capture`, no `wpt`, no
// `width` → 390 px frame, legacy flatten() flow. One URL per file.
//
// The fill pin is a STRING pin and deliberately NOT the colour gate: both
// `0.7` and `0.70196` composite to the same (173,173,179) in Chromium, one
// LSB under the natives' (174,174,180); the composited byte is pinned by
// LabelChrome.raster.test.tsx through headless Chrome, and the byte-180
// window [0.704, 0.7078] by runtimes/web tests/renderer/BlockFontLabel.test.ts.
//
// MUTATION RECORD (executed on this tree, source restored byte-exact; the
// fix-pass numbers also stand in tools/titan/results/wave51-A/skeptic-web.md):
//   1. (2026-09-16) `Infinity` passed as frameWidth in CaptureCanvas → the
//      "62 glyphs at 390" pin red ("expected '599' to be '371'" — all 100
//      glyphs kept); LabelChrome.width250.test.tsx red on the same run.
//   2. (fix lane, 2026-09-22) BLOCK_LABEL_FILL re-spelt `0.70196` and then
//      `0.7` → the fill string pin red both times ("expected 'rgba(237, 237,
//      237, 0.70196)' to be 'rgba(237, 237, 237, 0.706)'"); the raster pin
//      red on the byte for the same two mutations.
//   3. (polish pass, 2026-09-22, restored sha256-exact) `0.70196` again
//      after the split → this file's fill pin red, BlockFontLabel.test.ts
//      red on the window's lower edge, raster (8,6) = [173,173,179];
//      `0.7079` → fill pin red, BlockFontLabel.test.ts red on the upper
//      edge, raster (8,6) = [175,175,181]; LabelChrome.tsx origin (24,22)
//      → "to contain 'left:8px'" red, raster (8,6) ground [26,26,46]. Each
//      run: style + raster = 4 failed / 3 passed of 7.
import { describe, it, expect, afterEach } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { CaptureGallery } from '../../src/ui/CaptureGallery';
import type { IRComponent, IRDocument } from '@style-converter/web/core/ir/IRModels';

/** The chrome's marker selector — the ONLY svg the canvas may hold. */
const CHROME = 'svg[data-label-chrome]';

/** Minimal decoded v2 document (flat list; composition via `slot`). */
function doc(components: IRComponent[]): IRDocument {
  return { irVersion: 2, minReaderVersion: 2, components } as IRDocument;
}
/** A v2 component with the sibling suites' default identity. */
function comp(overrides: Partial<IRComponent> = {}): IRComponent {
  return { id: 'test-id', name: 'Test_Comp', properties: [], ...overrides };
}
/** Render the gallery into the JSDOM body and return the first canvas's chrome svg. */
function chromeOf(components: IRComponent[]): Element {
  document.body.innerHTML = renderToStaticMarkup(<CaptureGallery document={doc(components)} />);
  return document.body.querySelector('[data-capture-canvas]')!.querySelector(CHROME)!;
}
/** Count distinct 6-px glyph cells the svg's rects occupy (block-local x). */
function glyphColumns(svg: Element): number {
  const cells = new Set<number>();
  svg.querySelectorAll('rect').forEach((r) => cells.add(Math.floor(Number(r.getAttribute('x')) / 6)));
  return cells.size;
}

// A clean body between tests — the pins count elements.
afterEach(() => { document.body.innerHTML = ''; });

describe('LabelChrome — the svg\'s own attributes at the 390 frame (?mode=capture)', () => {
  it('is pinned at frame (8,6): absolute, z-index set, pointer-inert, crisp', () => {
    const svg = chromeOf([comp()]);
    // React serialises the style object verbatim — assert the tokens.
    const style = svg.getAttribute('style') ?? '';
    expect(style).toContain('position:absolute');
    expect(style).toContain('left:8px');
    expect(style).toContain('top:6px');
    expect(style).toMatch(/z-index:\d+/);
    expect(style).toContain('pointer-events:none');
    expect(style).toContain('display:block');
    // Integer-grid rendering — the byte-parity point (no antialiasing).
    expect(svg.getAttribute('shape-rendering')).toBe('crispEdges');
  });

  it('carries the pinned fill SPELLING: alpha 0.706 = alpha BYTE 180 on web', () => {
    const svg = chromeOf([comp()]);
    // Alpha 0.706 is alpha BYTE 180 (0.706 × 255 = 180.03 under floor and
    // round), the value Chromium's CPU raster needs to composite the
    // natives' (174,174,180) over the ground — 179/255 (0.70196) rendered
    // the SAME (173,173,179) as the old 0.7 (wave-51 web skeptic). This pin
    // guards the spelling only; the byte is LabelChrome.raster.test.tsx's.
    expect(svg.getAttribute('fill')).toBe('rgba(237, 237, 237, 0.706)');
  });

  it('truncates against the 390 frame: a 100-char name keeps 62 glyphs (width 371)', () => {
    const svg = chromeOf([comp({ name: 'A'.repeat(100) })]);
    // 8 + n*6 <= 390 - 8 → n = 62; tight width (n-1)*6 + 5 = 371, one cell tall.
    expect(svg.getAttribute('width')).toBe(String(61 * 6 + 5));
    expect(svg.getAttribute('height')).toBe('7');
    expect(glyphColumns(svg)).toBe(62);
    // The a11y name is the UNtruncated label (tooling identity).
    expect(svg.getAttribute('aria-label')).toBe('A'.repeat(100));
  });
});
