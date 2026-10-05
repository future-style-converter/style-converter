// wave-52 lane L2 (Fix A) — the composed canvas's HORIZONTAL VIEWPORT CROP.
//
// THE DEFECT THIS PINS. The Chromium browser-ref is RENDERED in a 358-px
// viewport and padded in image space (capture-browser-ref.mjs
// REF_RENDER_WIDTH / padPngBuffer), so ink past content x=358 is scrollable
// overflow the PNG never contains — the ref's right edge of ink is image
// x=373 by construction. The web canvas clipped at the 390-px OUTER div and
// left the inner `data-capture-icb` div un-clipped, so a `width: 400px`
// flex row (css-gaps/flex/flex-gap-decorations-040, wave51-fix web P 0.9939)
// painted across the right frame to image x=389: a 16x104 = 1664-px band
// that was the cell's ENTIRE pixelMismatchedCount, identical on all three
// platforms. The wave51-fix frame-ink census counts 208 overrun-right + 84
// overrun-left scored cells with frame ink the ref cannot have
// (tools/titan/results/wave52-plan/frame-ink-census.json).
//
// THE CONTRACT (css-overflow-3 §3.1/§3.2, CSS 2.1 §9.1.1):
//   - `overflow-x: clip` rides the ICB div — the ICB IS the ref's viewport,
//     and `clip` clips at the padding edge (358) without creating a scroll
//     container or a new formatting context;
//   - the vertical axis stays VISIBLE: the ref's second viewport is
//     max(scrollHeight, 568), so the ref never crops the bottom, and a
//     vertical clip would hide abspos content the ref shows. `overflow: clip`
//     (both axes) is therefore WRONG, and so is any `overflow-y` key;
//   - the outer framed canvas keeps its `overflow: hidden` (per-canvas
//     isolation in the gallery crop — unchanged).
//
// EXECUTED MUTATIONS (applied to ComposedCaptureGallery.tsx, this file run
// alone, source restored byte-exact — sha256 checked; record in
// tools/titan/results/wave52-composed-canvas/_note.md):
//   M1 delete `overflowX: 'clip'`          → icb_clipsTheInlineAxis red.
//   M2 `overflow: 'clip'` instead (2 axes) → icb_leavesTheBlockAxisVisible red.
//
// The payload is copied VERBATIM out of the wave51-fix per-test IR —
// tools/titan/runs/wave51-fix/sections/css-gaps/per-test-ir/
// wpt__css-gaps__flex__flex-gap-decorations-040.json (body-root + the 400-px
// container; the nine 70x50 items are elided because the wiring pin reads
// the ICB's inline style, not the items) — so a converter wire change fails
// here before it silently un-clips the page. renderToStaticMarkup is enough:
// the observable is the inline style the canvas emits, exactly like the
// sibling ComposedCanvasRootClip / ComposedCanvasPadding pins.

import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { ComposedCaptureGallery } from '../../src/ui/ComposedCaptureGallery';
import type { IRDocument } from '@style-converter/web/core/ir/IRModels';

/** The verbatim gaps-040 body-root bag: `body { margin: 0 }` as four longhands. */
const BODY_ROOT_PROPERTIES = [
  { type: 'MarginTop', data: { px: 0 } },
  { type: 'MarginRight', data: { px: 0 } },
  { type: 'MarginBottom', data: { px: 0 } },
  { type: 'MarginLeft', data: { px: 0 } },
];

/** The verbatim 400-px bordered container — 42 px wider than the 358 ICB. */
const CONTAINER_PROPERTIES = [
  { type: 'Width', data: { type: 'length', px: 400 } },
  { type: 'BorderTopWidth', data: { px: 2 } },
  { type: 'BorderRightWidth', data: { px: 2 } },
  { type: 'BorderBottomWidth', data: { px: 2 } },
  { type: 'BorderLeftWidth', data: { px: 2 } },
  { type: 'BorderTopStyle', data: 'SOLID' },
  { type: 'BorderRightStyle', data: 'SOLID' },
  { type: 'BorderBottomStyle', data: 'SOLID' },
  { type: 'BorderLeftStyle', data: 'SOLID' },
  { type: 'BackgroundColor', data: { srgb: { r: 1, g: 1, b: 1 }, original: '#fff' } },
];

/** The gaps-040 document, body-root bag parameterised for the padding pin. */
const docWithBody = (bodyProperties: unknown[]): IRDocument =>
  ({
    irVersion: 2,
    minReaderVersion: 2,
    components: [
      {
        id: 'wpt__css-gaps__flex__flex-gap-decorations-040__0-289',
        name: 'wpt__css-gaps__flex__flex-gap-decorations-040__0',
        properties: bodyProperties,
        meta: { role: 'body-root' },
      },
      {
        id: 'wpt__css-gaps__flex__flex-gap-decorations-040__1-290',
        name: 'wpt__css-gaps__flex__flex-gap-decorations-040__1',
        properties: CONTAINER_PROPERTIES,
      },
    ],
  }) as unknown as IRDocument;

/** The inner ICB div's inline style — the ref's render viewport (wave-25). */
const icbStyleOf = (html: string) =>
  /data-capture-icb[^>]*?style="([^"]*)"/.exec(html)?.[1] ?? '';
/** The OUTER canvas div's inline style (the 390x600 framed image). */
const canvasStyleOf = (html: string) =>
  /data-capture-canvas[^>]*?style="([^"]*)"/.exec(html)?.[1] ?? '';

/** Render the gaps-040 shape once; every pin below reads the same markup. */
const render = (bodyProperties: unknown[] = BODY_ROOT_PROPERTIES) =>
  renderToStaticMarkup(<ComposedCaptureGallery document={docWithBody(bodyProperties)} />);

describe('ComposedTestCanvas ICB horizontal clip (wave-52 L2 Fix A)', () => {
  it('icb_clipsTheInlineAxis: the ICB div carries overflow-x:clip', () => {
    // The ICB IS the ref's 358-px viewport; `clip` on the inline axis is the
    // viewport crop the ref applies by construction (css-overflow-3 §3.1).
    // Mutation M1 (key deleted) fails here.
    expect(icbStyleOf(render())).toContain('overflow-x:clip');
  });

  it('icb_leavesTheBlockAxisVisible: no overflow-y and no shorthand overflow key', () => {
    // The ref's second viewport is max(scrollHeight, 568): it never crops the
    // bottom, so the block axis must stay visible. `overflow: clip` would
    // clip both axes (mutation M2 fails here), and any `overflow-y` key would
    // be a second owner of the vertical decision.
    const icb = icbStyleOf(render());
    expect(icb).not.toContain('overflow-y');
    // `overflow:` as a shorthand — the `-x` longhand is the only overflow key.
    expect(icb.replace('overflow-x:clip', '')).not.toMatch(/overflow\s*:/);
  });

  it('canvas_keepsItsOwnIsolationClip: the outer framed div stays overflow:hidden', () => {
    // The gallery's per-canvas crop isolation is unchanged — the ICB clip is
    // an ADDITIONAL, inner clip at the viewport edge, not a move of this one.
    expect(canvasStyleOf(render())).toContain('overflow:hidden');
  });

  it('icb_clipIsTheFrameNotTheAuthorPad: a body padding does not move or remove it', () => {
    // An author `body { padding: 40px }` insets content INSIDE the ICB (its
    // padding rides the ICB div), but the clip edge is the ICB's padding
    // edge — the frame — whatever the author declares. The key is present
    // and unchanged with the pad declared.
    const icb = icbStyleOf(render([
      ...BODY_ROOT_PROPERTIES,
      { type: 'PaddingLeft', data: { px: 40 } },
      { type: 'PaddingRight', data: { px: 40 } },
    ]));
    expect(icb).toContain('overflow-x:clip');
    expect(icb).toContain('padding-left:40px');
  });
});
