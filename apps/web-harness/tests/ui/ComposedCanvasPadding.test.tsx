// wave-24 B-RC5 — the composed canvas's PAD resolver.
//
// capture-browser-ref.mjs frames every reference page with a
// ZERO-specificity `:where(body) { padding: 16px }`. Per CSS Selectors L4
// §17 `:where()` contributes no specificity, so a ref declaring its OWN
// `body { padding: 0 }` (0,0,1) WINS and renders with no body pad. The
// composed canvas hardcoded 16px, so every such test's whole render sat
// (+16,+16) off its ref — MEASURED as the ENTIRE divergence of
// css-masking/clip-path-circle-007, whose test AND ref both open with
// `body, div { padding: 0; margin: 0 }`.
//
// These pins hold the contract shared by all three composed canvases (web
// here, Compose resolveComposedCanvasPadding, SwiftUI
// ComposedCaptureCanvas.resolvedPadding):
//   - no body-root, or one with no padding ⇒ 16px on all four sides, i.e.
//     every pre-wave-24 capture is byte-identical;
//   - a declared side overrides that side ONLY (the cascade is
//     per-longhand — a lone `padding-left: 0` keeps 16 elsewhere);
//   - runtime-dependent lengths (the converter's unresolved em/%/calc)
//     keep the default rather than inventing a number;
//   - negatives clamp to 0 (CSS 2.1 §8.4 forbids negative padding).
//
// renderToStaticMarkup is enough for the wiring pin: the observable is the
// inline style the canvas div emits, exactly like the sibling gallery test.

import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import {
  ComposedCaptureGallery,
  resolveCanvasPadding,
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

  it('honors a zeroed body pad — the clip-path-circle-007 case', () => {
    // `body, div { padding: 0 }` expands to the four longhands at px 0.
    const doc = docWithBody([
      { type: 'PaddingTop', data: px(0) },
      { type: 'PaddingRight', data: px(0) },
      { type: 'PaddingBottom', data: px(0) },
      { type: 'PaddingLeft', data: px(0) },
    ]);
    expect(resolveCanvasPadding(doc)).toEqual({ top: 0, right: 0, bottom: 0, left: 0 });
  });

  it('resolves PER SIDE — an undeclared side keeps the 16px default', () => {
    // The cascade is per-longhand: `body { padding-left: 40px }` leaves the
    // injected `:where(body)` 16px standing on the other three sides.
    const doc = docWithBody([{ type: 'PaddingLeft', data: px(40) }]);
    expect(resolveCanvasPadding(doc)).toEqual({ top: 16, right: 16, bottom: 16, left: 40 });
  });

  it('keeps the default for a runtime-dependent length (em/%/calc)', () => {
    // The converter emits `padding: 1em` with no absolute px — this canvas
    // has no font-size context to resolve it, so it must NOT guess.
    const doc = docWithBody([
      { type: 'PaddingTop', data: { original: { v: 1, u: 'EM' } } },
      { type: 'PaddingLeft', data: { keyword: 'auto' } },
    ]);
    expect(resolveCanvasPadding(doc)).toEqual({ top: 16, right: 16, bottom: 16, left: 16 });
  });

  it('clamps a negative pad to 0 (CSS 2.1 §8.4 forbids negative padding)', () => {
    const doc = docWithBody([{ type: 'PaddingTop', data: px(-8) }]);
    expect(resolveCanvasPadding(doc).top).toBe(0);
  });
});

describe('ComposedTestCanvas pad wiring', () => {
  it('emits the resolved pad as the four longhands on the canvas div', () => {
    const doc = docWithBody([
      { type: 'PaddingTop', data: px(0) },
      { type: 'PaddingRight', data: px(0) },
      { type: 'PaddingBottom', data: px(0) },
      { type: 'PaddingLeft', data: px(0) },
    ]);
    const html = renderToStaticMarkup(<ComposedCaptureGallery document={doc} />);
    // Isolate the CANVAS div's own style attribute — the gallery container
    // and the rendered components carry paddings of their own.
    const canvasStyle = /data-capture-canvas[^>]*?style="([^"]*)"/.exec(html)?.[1] ?? '';
    // The whole point: a zeroed body pad must reach the DOM, or the render
    // stays 16px off its ref.
    expect(canvasStyle).toMatch(/padding-top:0px/);
    expect(canvasStyle).toMatch(/padding-left:0px/);
    // …and the shorthand must NOT also be emitted on the canvas (React warns
    // about a shorthand/longhand mix, and the winner would depend on key
    // order) — composedCanvasStyle deliberately carries no `padding` key.
    expect(canvasStyle).not.toMatch(/(^|;)padding:/);
  });

  it('emits 16px per side for a padding-free document (byte-compat)', () => {
    const doc: IRDocument = {
      irVersion: 2, minReaderVersion: 2,
      components: [{ id: 'r', name: 'wpt__s__t__0', properties: [] }],
    };
    const html = renderToStaticMarkup(<ComposedCaptureGallery document={doc} />);
    expect(html).toMatch(/padding-top:16px/);
    expect(html).toMatch(/padding-right:16px/);
    expect(html).toMatch(/padding-bottom:16px/);
    expect(html).toMatch(/padding-left:16px/);
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
  expect(pad.left).toBe(24);
  expect(pad.top).toBe(16); // undeclared side keeps the default
});
});
