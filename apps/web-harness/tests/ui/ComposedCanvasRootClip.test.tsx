// wave-49 lane A4 — the composed canvas's DOCUMENT-ELEMENT clip channel.
//
// css-masking-1 §5: a non-`none` `clip-path` clips the element AND its
// descendants. On the document element that covers the whole page, the
// background css-backgrounds-3 §2.11.2 propagates to the canvas included —
// the fxtf compositing §rootgroup / §pagebackdrop chain WPT
// `css-masking/clip-path/clip-path-document-element` links, whose
// `meta name=assert` states it outright: "Clip-path on the document element
// applies to the root background."
//
// MEASURED on the wave-48 gate for that test and its `-will-change` twin:
// the Chromium ref is 7500 green px, an "L" whose ink bbox is exactly
// [66,66]-[165,165] in the 390x600 image; web/iOS/Android all painted
// 79.91 % green + 20.09 % red (ssim 0.5367) because the root's clip landed
// on a merged body-root bag that, in the flat IR v2 wire, is a SIBLING of
// the document's top-level boxes and therefore has nothing inside it.
//
// These pins hold the WIRING half of the repair — WHERE the resolved value
// is spent. They are the sibling of ComposedCanvasPadding /
// ComposedCanvasDirection / ComposedCanvasWritingMode: the observable is the
// inline style the canvas emits, so renderToStaticMarkup is enough.
//
//   - the clip rides the ICB div, never the framed outer div: the ICB IS the
//     root element's rendering surface (the ref's render viewport), so its
//     border box is the reference box the clip's lengths are measured from.
//     16 (frame) + 50 (the polygon's first vertex) = 66, the ref's ink x0;
//   - the propagated root background MOVES onto that same ICB box, so it is
//     clipped with everything else (the assert sentence above), and the outer
//     framed div falls back to the page backdrop (the WPT canvas default);
//   - a document whose root declares no clip writes NO new key and keeps its
//     background exactly where wave-25 put it — the byte-compatibility pin
//     that covers 1433 of the corpus's 1435 documents.
//
// The positive payload is copied VERBATIM out of the wave-48 gate's per-test
// IR — tools/titan/runs/wave48-final/sections/css-masking/per-test-ir/
// wpt__css-masking__clip-path__clip-path-document-element.json — so a
// converter wire change fails here before it silently un-clips the page.

import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { ComposedCaptureGallery } from '../../src/ui/ComposedCaptureGallery';
import type { IRDocument } from '@style-converter/web/core/ir/IRModels';

/** The verbatim `html { background: red; clip-path: polygon(…) }` bag. */
const ROOT_CLIP_PROPERTIES = [
  { type: 'BackgroundColor', data: { srgb: { r: 1, g: 0, b: 0 }, original: 'red' } },
  {
    type: 'ClipPath',
    data: {
      type: 'polygon',
      points: [
        { x: { px: 50 }, y: { px: 50 } },
        { x: { px: 100 }, y: { px: 50 } },
        { x: { px: 100 }, y: { px: 100 } },
        { x: { px: 150 }, y: { px: 100 } },
        { x: { px: 150 }, y: { px: 150 } },
        { x: { px: 50 }, y: { px: 150 } },
      ],
    },
  },
];

/** The verbatim `div { width:500px; height:500px; background:green }` sibling. */
const GREEN_DIV_PROPERTIES = [
  { type: 'Width', data: { type: 'length', px: 500 } },
  { type: 'Height', data: { type: 'length', px: 500 } },
  { type: 'BackgroundColor', data: { srgb: { r: 0, g: 0.5019607843137255, b: 0 }, original: 'green' } },
];

/** The document-element test document, body-root bag parameterised. */
const docWithBody = (properties: unknown[]): IRDocument =>
  ({
    irVersion: 2,
    minReaderVersion: 2,
    components: [
      {
        id: 'wpt__css-masking__clip-path__clip-path-document-element__0-047',
        name: 'wpt__css-masking__clip-path__clip-path-document-element__0',
        properties,
        meta: { role: 'body-root' },
      },
      {
        id: 'wpt__css-masking__clip-path__clip-path-document-element__1-048',
        name: 'wpt__css-masking__clip-path__clip-path-document-element__1',
        properties: GREEN_DIV_PROPERTIES,
      },
    ],
  }) as unknown as IRDocument;

/** The inner ICB div's inline style — the ref's render viewport (wave-25). */
const icbStyleOf = (html: string) =>
  /data-capture-icb[^>]*?style="([^"]*)"/.exec(html)?.[1] ?? '';
/** The OUTER canvas div's inline style (the 390x600 framed image). */
const canvasStyleOf = (html: string) =>
  /data-capture-canvas[^>]*?style="([^"]*)"/.exec(html)?.[1] ?? '';

describe('ComposedTestCanvas document-element clip wiring (wave-49 A4)', () => {
  it('puts the root clip on the ICB, never on the framed outer div', () => {
    // The ICB's border box starts at (16,16) in the image, so a clip whose
    // first vertex is 50px lands at 66 — exactly the ref's ink x0/y0. Put on
    // the outer div instead, the same declaration would land at 50.
    const html = renderToStaticMarkup(<ComposedCaptureGallery document={docWithBody(ROOT_CLIP_PROPERTIES)} />);
    expect(icbStyleOf(html)).toContain(
      'clip-path:polygon(50px 50px, 100px 50px, 100px 100px, 150px 100px, 150px 150px, 50px 150px)',
    );
    expect(canvasStyleOf(html)).not.toContain('clip-path');
  });

  it('moves the propagated root background inside that clip', () => {
    // "Clip-path on the document element applies to the root background"
    // (the test's own assert). Left on the outer framed div, the red would
    // keep painting the whole 390x600 image — the 20.09 % red the wave-48
    // gate measured on all three platforms.
    const html = renderToStaticMarkup(<ComposedCaptureGallery document={docWithBody(ROOT_CLIP_PROPERTIES)} />);
    // resolveCanvasBackground turns the wire's sRGB triple into the same
    // string a component would paint; whatever it is, it must be on the ICB…
    const icb = icbStyleOf(html);
    expect(icb).toMatch(/background:(?!transparent)/);
    // …and the frame must fall back to the page backdrop, the WPT canvas
    // default white, so everything outside the clip reads white like the ref.
    expect(canvasStyleOf(html)).toContain('background:#FFFFFF');
  });

  it('writes no clip key and keeps the frame background for a root with no clip', () => {
    // The byte-compatibility pin: 1433 of the corpus's 1435 documents take
    // this branch and must render the exact inline style they did in wave 48.
    const html = renderToStaticMarkup(
      <ComposedCaptureGallery document={docWithBody([ROOT_CLIP_PROPERTIES[0]])} />,
    );
    expect(icbStyleOf(html)).not.toContain('clip-path');
    expect(icbStyleOf(html)).not.toMatch(/background:/);
    // The propagated red stays on the frame, exactly where wave-25 put it.
    expect(canvasStyleOf(html)).not.toContain('background:#FFFFFF');
  });

  it('writes no clip key for the initial `none` keyword', () => {
    // `none` is the initial value (css-masking-1 §5.1); writing it would be a
    // no-op declaration carrying a byte-diff risk for no benefit.
    const html = renderToStaticMarkup(
      <ComposedCaptureGallery document={docWithBody([{ type: 'ClipPath', data: 'none' }])} />,
    );
    expect(icbStyleOf(html)).not.toContain('clip-path');
  });
});
