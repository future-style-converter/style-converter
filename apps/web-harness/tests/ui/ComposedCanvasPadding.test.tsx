// wave-24 B-RC5 / wave-25 round 3 — the composed canvas's PAD resolver.
//
// WAVE 24: capture-browser-ref.mjs used to frame every reference page with a
// ZERO-specificity `:where(body) { padding: 16px }`. Per CSS Selectors L4
// §17 `:where()` contributes no specificity, so a ref declaring its OWN
// `body { padding: 0 }` (0,0,1) WON and rendered with no body pad. The
// composed canvas hardcoded 16px, so every such test's whole render sat
// (+16,+16) off its ref — MEASURED as the ENTIRE divergence of
// css-masking/clip-path-circle-007, whose test AND ref both open with
// `body, div { padding: 0; margin: 0 }`.
//
// WAVE 25 ROUND 3: at CAL-RC1 the ref stopped injecting a body padding at
// all — it renders at 358 wide with `padding: 0` and the 16px frame is
// memcpy'd around the finished PNG. An image-space translation has no
// cascade, so the FRAME is unconditional and the author's body padding is an
// ADDITIONAL inset inside it. Each side resolves to `frame + declared`.
//
// These pins hold the contract shared by all three composed canvases (web
// here, Compose resolveComposedCanvasPadding, SwiftUI
// ComposedCaptureCanvas.resolvedPadding):
//   - no body-root, or one with no padding ⇒ 16px on all four sides, i.e.
//     every such capture is byte-identical to wave 24;
//   - a declared side stacks on the frame for that side ONLY (the cascade is
//     per-longhand — a lone `padding-left: 40px` gives left 56, others 16);
//   - runtime-dependent lengths (the converter's unresolved em/%/calc)
//     keep the bare frame rather than inventing a number;
//   - a negative author term clamps to 0, leaving the bare frame (CSS 2.1
//     §8.4 forbids negative padding).
//
// renderToStaticMarkup is enough for the wiring pin: the observable is the
// inline style the canvas div emits, exactly like the sibling gallery test.

import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import {
  ComposedCaptureGallery,
  resolveCanvasPadding,
  CANVAS_FRAME_PX,
  ICB_WIDTH_PX,
  ICB_MIN_HEIGHT_PX,
} from '../../src/ui/ComposedCaptureGallery';
import type { IRDocument } from '@style-converter/web/core/ir/IRModels';

/** Minimal per-test document with one body-root carrying `props`. */
const docWithBody = (props: Array<{ type: string; data: unknown }>): IRDocument => ({
  irVersion: 2,
  minReaderVersion: 2,
  components: [
    {
      id: 'b',
      name: 'wpt__css-masking__clip-path-circle-007__body',
      properties: props as never,
      meta: { role: 'body-root' },
    },
    { id: 'r', name: 'wpt__css-masking__clip-path-circle-007__0', properties: [] },
  ],
});

/** An absolute-px IRLength leaf, the shape the converter emits for `0`. */
const px = (n: number) => ({ px: n });

describe('resolveCanvasPadding (wave-24 B-RC5)', () => {
  it('defaults to the ref pad (16 per side) when there is no body-root', () => {
    const doc: IRDocument = {
      irVersion: 2, minReaderVersion: 2,
      components: [{ id: 'r', name: 'wpt__s__t__0', properties: [] }],
    };
    expect(resolveCanvasPadding(doc)).toEqual({ top: 16, right: 16, bottom: 16, left: 16 });
  });

  it('keeps 16 per side for a body-root that declares no padding', () => {
    // The a98rgb-003 shape: a body-root exists (it declares a background)
    // but says nothing about padding — that capture must not move.
    const doc = docWithBody([{ type: 'BackgroundColor', data: { srgb: { r: 0.5, g: 0.5, b: 0.5, a: 1 } } }]);
    expect(resolveCanvasPadding(doc)).toEqual({ top: 16, right: 16, bottom: 16, left: 16 });
  });

  it('keeps the image frame for a zeroed body pad — clip-path-circle-007', () => {
    // `body, div { padding: 0 }` expands to the four longhands at px 0.
    // Wave 24 let that zero the canvas inset entirely, because the inset WAS
    // the (author-beatable) injected body padding. Wave 25 round 3: the frame
    // is applied to the ref PNG in image space, which no author rule can
    // cancel — this ref's content sits at (+16,+16) in the ref image like
    // every other ref's, so the canvas keeps the frame and adds the zero.
    const doc = docWithBody([
      { type: 'PaddingTop', data: px(0) },
      { type: 'PaddingRight', data: px(0) },
      { type: 'PaddingBottom', data: px(0) },
      { type: 'PaddingLeft', data: px(0) },
    ]);
    expect(resolveCanvasPadding(doc)).toEqual({ top: 16, right: 16, bottom: 16, left: 16 });
  });

  it('resolves PER SIDE — an undeclared side keeps the bare frame', () => {
    // The cascade is per-longhand: `body { padding-left: 40px }` leaves the
    // other three sides at the bare frame and stacks 40 INSIDE the frame on
    // the left (16 + 40 = 56) — the ref renders that 40px pad in its
    // 358-wide viewport and the image frame adds 16 around it.
    const doc = docWithBody([{ type: 'PaddingLeft', data: px(40) }]);
    expect(resolveCanvasPadding(doc)).toEqual({ top: 16, right: 16, bottom: 16, left: 56 });
  });

  it('keeps the bare frame for a runtime-dependent length (em/%/calc)', () => {
    // The converter emits `padding: 1em` with no absolute px — this canvas
    // has no font-size context to resolve it, so it must NOT guess.
    const doc = docWithBody([
      { type: 'PaddingTop', data: { original: { v: 1, u: 'EM' } } },
      { type: 'PaddingLeft', data: { keyword: 'auto' } },
    ]);
    expect(resolveCanvasPadding(doc)).toEqual({ top: 16, right: 16, bottom: 16, left: 16 });
  });

  it('clamps a negative author pad to 0, leaving the bare frame', () => {
    // CSS 2.1 §8.4 forbids negative padding; the AUTHOR term clamps at 0, so
    // the side resolves to the frame — never inside it (which would pull
    // content outside the canvas and thus outside the crop).
    const doc = docWithBody([{ type: 'PaddingTop', data: px(-8) }]);
    expect(resolveCanvasPadding(doc).top).toBe(16);
  });
});

/** The CANVAS div's own inline style (the gallery container and the rendered
 *  components carry paddings of their own, so the scan must be scoped). */
const canvasStyleOf = (html: string) =>
  /data-capture-canvas[^>]*?style="([^"]*)"/.exec(html)?.[1] ?? '';
/** The inner ICB div's inline style — the ref's render viewport (wave-25). */
const icbStyleOf = (html: string) =>
  /data-capture-icb[^>]*?style="([^"]*)"/.exec(html)?.[1] ?? '';

describe('ComposedTestCanvas pad wiring', () => {
  it('spends the frame on the canvas and the author pad on the ICB', () => {
    // A body-root declaring `padding: 0` (clip-path-circle-007). Under the
    // image-space ref frame the canvas keeps its 16px frame and the ICB adds
    // nothing — the ref's own content sits at (+16,+16) in the ref PNG.
    const doc = docWithBody([
      { type: 'PaddingTop', data: px(0) },
      { type: 'PaddingRight', data: px(0) },
      { type: 'PaddingBottom', data: px(0) },
      { type: 'PaddingLeft', data: px(0) },
    ]);
    const html = renderToStaticMarkup(<ComposedCaptureGallery document={doc} />);
    expect(canvasStyleOf(html)).toMatch(/padding-top:16px/);
    expect(canvasStyleOf(html)).toMatch(/padding-left:16px/);
    expect(icbStyleOf(html)).toMatch(/padding-top:0px/);
    expect(icbStyleOf(html)).toMatch(/padding-left:0px/);
    // The shorthand must NOT also be emitted (React warns about a
    // shorthand/longhand mix, and the winner would depend on key order) —
    // composedCanvasStyle/composedIcbStyle deliberately carry no `padding`.
    expect(canvasStyleOf(html)).not.toMatch(/(^|;)padding:/);
    expect(icbStyleOf(html)).not.toMatch(/(^|;)padding:/);
  });

  it('puts an AUTHOR body pad inside the frame, not instead of it', () => {
    // `body { padding-left: 40px }` → resolver 56, spent as frame 16 on the
    // canvas + 40 on the ICB. That is exactly the ref's box tree: 40px of
    // body padding inside a 358-wide viewport, framed by 16px of image pad.
    const doc = docWithBody([{ type: 'PaddingLeft', data: px(40) }]);
    const html = renderToStaticMarkup(<ComposedCaptureGallery document={doc} />);
    expect(canvasStyleOf(html)).toMatch(/padding-left:16px/);
    expect(icbStyleOf(html)).toMatch(/padding-left:40px/);
    expect(icbStyleOf(html)).toMatch(/padding-right:0px/);
  });

  it('emits 16px per side for a padding-free document (byte-compat)', () => {
    const doc: IRDocument = {
      irVersion: 2, minReaderVersion: 2,
      components: [{ id: 'r', name: 'wpt__s__t__0', properties: [] }],
    };
    const html = renderToStaticMarkup(<ComposedCaptureGallery document={doc} />);
    expect(canvasStyleOf(html)).toMatch(/padding-top:16px/);
    expect(canvasStyleOf(html)).toMatch(/padding-right:16px/);
    expect(canvasStyleOf(html)).toMatch(/padding-bottom:16px/);
    expect(canvasStyleOf(html)).toMatch(/padding-left:16px/);
  });
});

// wave-25 round 3 — the ICB div IS the ref's render viewport, and it is the
// ONE box that may establish a containing block for out-of-flow descendants.
//
// MEASURED (probe of the live canvas CSS, Chromium): with
// `position:relative` + `transform` on the OUTER canvas div, an abspos
// `left:100px` child landed at canvas x=100 and a `right:0;bottom:0` child at
// (380,590) — flush with the IMAGE edge — while in-flow content sat at
// (16,16). Moving those two declarations to the inner div moved them to
// (116,16) and (364,574): flush with the CONTENT edge, frame intact, which is
// what the image-space-framed ref raster contains.
describe('ComposedTestCanvas ICB geometry (wave-25 round 3)', () => {
  const html = () => renderToStaticMarkup(
    <ComposedCaptureGallery document={{
      irVersion: 2, minReaderVersion: 2,
      components: [{ id: 'r', name: 'wpt__s__t__0', properties: [] }],
    } as IRDocument} />);

  it('exports the ref render viewport as the ICB extents', () => {
    // capture-browser-ref.mjs REF_RENDER_WIDTH / REF_RENDER_MIN_HEIGHT.
    expect(CANVAS_FRAME_PX).toBe(16);
    expect(ICB_WIDTH_PX).toBe(358);
    expect(ICB_MIN_HEIGHT_PX).toBe(568);
  });

  it('makes the ICB div the containing block, at the ICB min-height', () => {
    const icb = icbStyleOf(html());
    // position + transform = containing block for abspos AND fixed
    // descendants (CSS Transforms §3) — the whole point of the inner div.
    expect(icb).toMatch(/position:relative/);
    expect(icb).toMatch(/transform:translateZ\(0\)/);
    // 568 floor, so `bottom:0` lands 16px above the image edge like the ref.
    expect(icb).toMatch(new RegExp(`min-height:${ICB_MIN_HEIGHT_PX}px`));
  });

  it('leaves NO containing block on the outer canvas div', () => {
    // If either declaration stayed here, abspos children would anchor at the
    // canvas corner again — the exact (-16,-16) the ref no longer has.
    const canvas = canvasStyleOf(html());
    expect(canvas).not.toMatch(/position:relative/);
    expect(canvas).not.toMatch(/transform:/);
    // The outer box still owns the crop isolation and the 390x600 frame.
    expect(canvas).toMatch(/overflow:hidden/);
    expect(canvas).toMatch(/width:390px/);
    expect(canvas).toMatch(/min-height:600px/);
  });
});

// Wave 24 (skeptic B1) — the wrapped-px leaf shape {original:{px:N}} must
// resolve identically on all three composed canvases; the natives read the
// pxFallback, so the web resolver must too (the engine string re-emits the
// author's unit, which the canvas frame cannot use).
describe('wave-24 skeptic B1 — cross-canvas leaf-shape parity', () => {
it('reads the wrapped original.px leaf like the natives', () => {
  const doc = {
    components: [{
      id: 'x__body', meta: { role: 'body-root' },
      properties: [{ type: 'PaddingLeft', data: { original: { px: 24 } } }],
    }],
  } as never;
  const pad = resolveCanvasPadding(doc);
  expect(pad.left).toBe(40);   // frame 16 + author 24 (wave-25 round 3)
  expect(pad.top).toBe(16);    // undeclared side keeps the bare frame
});
});
