// wave-37 lane W6 — the composed canvas's PRINCIPAL WRITING MODE resolver.
//
// THE MECHANISM. css-writing-modes-4 §8 ("The Principal Writing Mode") makes
// the writing mode used for the initial containing block — and therefore the
// block-progression axis of the WHOLE document — a property of the ROOT
// element, or, in HTML, of the first `<body>` child when there is one. A page
// that opens `html { writing-mode: vertical-rl }` lays its top-level blocks
// out as a RIGHT-TO-LEFT row of vertical columns; our composed canvas laid
// every such page out top-to-bottom.
//
// WHY IT WAS LOST. The extractor keeps the declaration — it lands on the
// synthetic `meta.role: 'body-root'` component, the same bag the background
// and padding propagations read. But that component is a SIBLING of the
// document's top-level blocks in the flat root forest (spec 03 placement),
// never their parent, so its `writing-mode` styled an empty zero-size box and
// inherited to nobody.
//
// MEASURED (wave35-webmap web map, re-measured at the wave-37 head):
//   css-logical/logical-values-float-clear-2   0.2254 → 0.9976  (pass)
//   css-logical/logical-values-float-clear-4   0.5995 → 0.9938  (pass)
//   css-writing-modes/mongolian-span-001       0.9355 → 1.0000  (pass)
//   css-writing-modes/vrl-…-alongside-vrl-floats 0.6841 → 1.0000 (pass)
//   css-writing-modes/wm-propagation-body-032  0.8080 → 0.8967  (blue square
//     moves from the upper-LEFT to the upper-RIGHT corner the ref shows)
//
// These pins hold the resolver's decision table and the ICB wiring. The
// resolver must stay SILENT (undefined ⇒ no `writing-mode` key at all) for
// every horizontal document, because that silence is what keeps the other
// ~10,600 corpus fixtures byte-identical to wave 36.

import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import {
  ComposedCaptureGallery,
  resolveCanvasWritingMode,
} from '../../src/ui/ComposedCaptureGallery';
import type { IRDocument } from '@style-converter/web/core/ir/IRModels';

/** Minimal per-test document with one body-root carrying `props`. */
const docWithBody = (props: Array<{ type: string; data: unknown }>): IRDocument => ({
  irVersion: 2,
  minReaderVersion: 2,
  components: [
    {
      id: 'b',
      name: 'wpt__css-writing-modes__wm-propagation-body-032__body',
      properties: props as never,
      meta: { role: 'body-root' },
    },
    { id: 'r', name: 'wpt__css-writing-modes__wm-propagation-body-032__0', properties: [] },
  ],
});

/** A document with no body-root at all — the corpus majority. */
const bareDoc: IRDocument = {
  irVersion: 2,
  minReaderVersion: 2,
  components: [{ id: 'r', name: 'wpt__s__t__0', properties: [] }],
};

describe('resolveCanvasWritingMode (wave-37 W6)', () => {
  it('is silent when the document has no body-root', () => {
    expect(resolveCanvasWritingMode(bareDoc)).toBeUndefined();
  });

  it('is silent for a body-root that declares no writing-mode', () => {
    // The a98rgb-003 shape: a body-root exists (it declares a background) but
    // says nothing about the writing mode — that capture must not move.
    const doc = docWithBody([
      { type: 'BackgroundColor', data: { srgb: { r: 0.5, g: 0.5, b: 0.5 } } },
    ]);
    expect(resolveCanvasWritingMode(doc)).toBeUndefined();
  });

  it('is silent for an explicit horizontal-tb (the canvas already does that)', () => {
    // css-writing-modes/wm-propagation-body-040's shape: `html` declares a
    // vertical mode and `body` overrides it back to horizontal, and per §8 the
    // BODY wins. Writing the key would be a no-op with a byte-diff risk.
    const doc = docWithBody([{ type: 'WritingMode', data: 'HORIZONTAL_TB' }]);
    expect(resolveCanvasWritingMode(doc)).toBeUndefined();
  });

  it('propagates each of the four vertical keywords', () => {
    // css-writing-modes-4 §3.1's non-horizontal set. The wm-propagation-body
    // family walks all four: 032 vertical-rl, 033 vertical-lr, 034
    // sideways-rl, 035 sideways-lr, each against the same upper-right ref.
    for (const [ir, css] of [
      ['VERTICAL_RL', 'vertical-rl'],
      ['VERTICAL_LR', 'vertical-lr'],
      ['SIDEWAYS_RL', 'sideways-rl'],
      ['SIDEWAYS_LR', 'sideways-lr'],
    ] as const) {
      expect(resolveCanvasWritingMode(docWithBody([{ type: 'WritingMode', data: ir }]))).toBe(css);
    }
  });

  it('refuses a value that is not a css-writing-modes keyword', () => {
    // No-silent-fallthrough: an unrecognised IR keyword must never be written
    // verbatim into a live style object.
    const doc = docWithBody([{ type: 'WritingMode', data: 'LR_TB' }]);
    expect(resolveCanvasWritingMode(doc)).toBeUndefined();
  });

  it('is blocked by containment on the body-root', () => {
    // css-contain/contain-body-w-m-001..004 and contain-html-w-m-001..004 are
    // literally titled "layout containment on body/html prevents writing-mode
    // propagation", and all eight match a reference whose orange square sits
    // in the upper-LEFT — i.e. the viewport stays horizontal-tb. Same gate the
    // background propagation uses (bodyRootHasContainment).
    const doc = docWithBody([
      { type: 'WritingMode', data: 'VERTICAL_RL' },
      { type: 'Contain', data: ['LAYOUT'] },
    ]);
    expect(resolveCanvasWritingMode(doc)).toBeUndefined();
  });

  it('is NOT blocked by `contain: none`', () => {
    // `["NONE"]` is not containment — the same reading bodyRootHasContainment
    // pins for the background propagation.
    const doc = docWithBody([
      { type: 'WritingMode', data: 'VERTICAL_RL' },
      { type: 'Contain', data: ['NONE'] },
    ]);
    expect(resolveCanvasWritingMode(doc)).toBe('vertical-rl');
  });
});

/** The inner ICB div's inline style — the ref's render viewport (wave-25). */
const icbStyleOf = (html: string) =>
  /data-capture-icb[^>]*?style="([^"]*)"/.exec(html)?.[1] ?? '';
/** The OUTER canvas div's inline style (the 390x600 framed image). */
const canvasStyleOf = (html: string) =>
  /data-capture-canvas[^>]*?style="([^"]*)"/.exec(html)?.[1] ?? '';

describe('ComposedTestCanvas writing-mode wiring (wave-37 W6)', () => {
  it('puts the principal writing mode on the ICB, never on the frame', () => {
    // The ICB IS the ref's render viewport, and `writing-mode` is inherited —
    // so one declaration there rotates the block axis for the whole root
    // forest AND every descendant's text runs, all laid out by Blink's own
    // orthogonal-flow pass. The outer div carries only the image frame; a
    // writing mode there would rotate the 16px frame's own box model.
    const doc = docWithBody([{ type: 'WritingMode', data: 'VERTICAL_RL' }]);
    const html = renderToStaticMarkup(<ComposedCaptureGallery document={doc} />);
    expect(icbStyleOf(html)).toMatch(/writing-mode:vertical-rl/);
    expect(canvasStyleOf(html)).not.toMatch(/writing-mode/);
  });

  it('emits NO writing-mode key at all for a horizontal document', () => {
    // The byte-compatibility pin: the ~10,600 fixtures with no vertical root
    // must render the exact same inline style they did in wave 36.
    const html = renderToStaticMarkup(<ComposedCaptureGallery document={bareDoc} />);
    expect(icbStyleOf(html)).not.toMatch(/writing-mode/);
  });

  it('keeps the ICB geometry and containing block intact when rotated', () => {
    // The rotation must not disturb the wave-25 round-3 calibration: the ICB
    // is still 358 x 568 minimum and still the containing block for
    // out-of-flow descendants.
    const doc = docWithBody([{ type: 'WritingMode', data: 'VERTICAL_LR' }]);
    const icb = icbStyleOf(renderToStaticMarkup(<ComposedCaptureGallery document={doc} />));
    expect(icb).toMatch(/position:relative/);
    expect(icb).toMatch(/transform:translateZ\(0\)/);
    expect(icb).toMatch(/min-height:568px/);
    expect(icb).toMatch(/writing-mode:vertical-lr/);
  });
});
