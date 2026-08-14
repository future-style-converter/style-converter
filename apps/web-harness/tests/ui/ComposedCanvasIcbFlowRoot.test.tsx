// wave-40 lane T1 — the composed ICB's BFC (`display: flow-root`).
//
// THE DEFECT THIS PINS. The composed capture's inner `data-capture-icb` div is
// the web twin of the box the browser-ref is laid out in, and
// tools/titan/capture-browser-ref.mjs's `canvasFrameCss()` pins
// `:where(body) { display: flow-root; box-sizing: border-box; min-height: 100vh }`
// on that box — flow-root explicitly so "a first child's block margin cannot
// escape through the body edge (it would shift the whole page relative to a
// harness canvas that clips it)". The harness twin carried box-sizing and the
// min-height but NOT flow-root, so a first in-flow root with a UA block margin
// (every WPT test opening `<p>Test passes if …</p>`) collapsed its 1em margin
// THROUGH the ICB and pushed the ICB's border box down by that margin.
//
// In-flow content did not move (the child sits at the displaced div's top edge
// either way), which is why this survived 15 waves — but the ICB is ALSO the
// containing block for every abspos/fixed descendant, and the ref's is the
// viewport, which never moves. MEASURED (puppeteer, this exact markup, the
// css-transform-3d-rotateX-positive body — _diag40/T1/probe5.mjs):
//
//                       canvas h   ICB y   <p> y   abspos box
//   without flow-root      616       32      32      (76, 92)
//   with    flow-root      600       16      32      (76, 76)
//   browser-ref            600       16      32      (76, 76)
//
// On the wave39-final css-transforms head that 16px displacement is the whole
// of the eight-strong `css-transform-3d-rotate{3d-,}{X,Y}-*` cluster (web
// 0.9293 against iOS 0.9988).
//
// The observable is the ICB div's inline style, same as the sibling
// ComposedCanvasPadding pin — renderToStaticMarkup is enough for a wiring pin.

import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { ComposedCaptureGallery } from '../../src/ui/ComposedCaptureGallery';
import type { IRDocument } from '@style-converter/web/core/ir/IRModels';

/** The inner ICB div's inline style — the ref's render viewport. */
const icbStyleOf = (html: string) =>
  /data-capture-icb[^>]*?style="([^"]*)"/.exec(html)?.[1] ?? '';

/** A one-root document: the prose intro every WPT reftest opens with. */
const doc: IRDocument = {
  irVersion: 2,
  minReaderVersion: 2,
  components: [
    {
      id: 'p0',
      name: 'wpt__css-transforms__css-transform-3d-rotateX-positive__0',
      properties: [] as never,
      text: 'Test passes if there is a green square with black border around.',
      meta: { sourceTag: 'p' },
    },
  ],
};

describe('composed ICB is a block formatting context (wave-40 T1)', () => {
  it('declares display:flow-root — the ref canvasFrameCss body twin', () => {
    const style = icbStyleOf(renderToStaticMarkup(<ComposedCaptureGallery document={doc} />));
    // React serialises the shorthand verbatim; accept either spacing so the
    // pin survives a formatter change but never a REMOVAL of the declaration.
    expect(style.replace(/\s+/g, '')).toContain('display:flow-root');
  });

  it('keeps the positioning half of the containing-block contract', () => {
    // flow-root is ADDITIVE: the ICB must still be the containing block for
    // abspos (position:relative) and for fixed descendants (transform), which
    // is the wave-25 round-3 guarantee this change must not disturb.
    const style = icbStyleOf(renderToStaticMarkup(<ComposedCaptureGallery document={doc} />)).replace(/\s+/g, '');
    expect(style).toContain('position:relative');
    expect(style).toContain('transform:translateZ(0');
  });
});
