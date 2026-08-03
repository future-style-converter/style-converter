// wave-27 A-RC1 — the composed canvas's CONTAINMENT GATE on body→canvas
// background propagation.
//
// THE BUG: `resolveCanvasBackground` propagated a body-root's
// `BackgroundColor` to the composed canvas UNCONDITIONALLY. Propagation is
// not unconditional: css-backgrounds-3 §2.11.2 propagates the root/body
// background to the canvas only while that element is ON the propagation
// path, and css-contain-2 §3.5 takes it off that path as soon as it has ANY
// containment — a contained body paints its background on its OWN box and
// the canvas keeps the UA default.
//
// MEASURED (wave-27 gate, tools/titan/runs/wave27-gate/sections/css-contain):
// contain-body-bg-001..004 declare `body { background: red; contain:
// layout|paint|size|style }` over a white 300×200 `<p>`, and their shared
// reference is pure white — "Test passes if there is no red". All four
// scored 0.6274 (web) / 0.6268 (iOS) / 0.6204 (Android) against that ref
// with the whole capture flooded red. Gating restores the ref's reading: the
// body's own 300×200 red box, fully covered by the test's white `<p>`.
//
// These pins hold the rule the three composed canvases share — web here,
// Compose `containmentBlocksCanvasPropagation` (WptCanvasBackgroundTest),
// SwiftUI `WPTCanvas.containmentBlocksPropagation` (WPTCaptureModeTests):
//   - ANY containment keyword blocks propagation, regardless of kind
//     (layout / paint / size / style / strict / content each do it alone —
//     the four WPT variants all match ONE all-white reference);
//   - `contain: none` (`["NONE"]`) and an absent/empty leaf are NOT
//     containment, so every non-contained document is byte-identical to
//     wave 26;
//   - the gate fires BEFORE the color is read, so a contained body's
//     background never reaches the canvas whatever its value.
//
// The PADDING resolver is deliberately NOT gated: css-contain-2 §3.5 is
// about the BACKGROUND propagation path only, and the canvas pad models the
// ref pipeline's image-space frame plus the author body inset — neither of
// which containment touches. The last case below pins that split.

import { describe, it, expect } from 'vitest';
import {
  resolveCanvasBackground,
  resolveCanvasPadding,
  bodyRootHasContainment,
} from '../../src/ui/ComposedCaptureGallery';
import type { IRComponent, IRDocument } from '@style-converter/web/core/ir/IRModels';

/** The pipeline default the gate falls back to — the corpus-v4 white canvas. */
const WHITE = '#FFFFFF';

// The two leaves below are the VERBATIM shapes the converter emits for
// contain-body-bg-001 — copied from a live `--to ir` run of that test's
// extracted fixture, so these pins exercise the real wire, not a guess at it.

/** `body { background: red }` as the converter emits it (sRGB 0..1 floats). */
const RED = { type: 'BackgroundColor', data: { srgb: { r: 1, g: 0, b: 0 }, original: 'red' } };

/** A `Contain` leaf: the converter's `ContainProperty.values` keyword list. */
const contain = (...values: string[]) => ({ type: 'Contain', data: values });

/** Minimal per-test document whose body-root carries `props`. */
const docWithBody = (props: Array<{ type: string; data: unknown }>): IRDocument => ({
  irVersion: 2,
  minReaderVersion: 2,
  components: [
    {
      id: 'b',
      name: 'wpt__css-contain__contain-body-bg-001__body',
      properties: props as never,
      meta: { role: 'body-root' },
    },
    { id: 'r', name: 'wpt__css-contain__contain-body-bg-001__0', properties: [] },
  ],
});

/** A bare body-root component for the predicate-level cases. */
const bodyWith = (props: Array<{ type: string; data: unknown }>): IRComponent =>
  ({ id: 'b', name: 'b', properties: props as never, meta: { role: 'body-root' } }) as IRComponent;

describe('bodyRootHasContainment (wave-27 A-RC1)', () => {
  it('is false when the body-root declares no contain at all', () => {
    // The overwhelmingly common case — every pre-wave-27 fixture.
    expect(bodyRootHasContainment(bodyWith([RED]))).toBe(false);
  });

  it('is false for `contain: none`', () => {
    // css-contain-2 §2: `none` is the initial value and applies NO
    // containment, so the body stays on the propagation path.
    expect(bodyRootHasContainment(bodyWith([RED, contain('NONE')]))).toBe(false);
  });

  it('is false for an empty keyword list', () => {
    // Defensive: a malformed leaf is not evidence of containment.
    expect(bodyRootHasContainment(bodyWith([contain()]))).toBe(false);
  });

  it('is true for each of the four WPT variants and for the composites', () => {
    // contain-body-bg-001..004 in order, then the two shorthand keywords.
    for (const kw of ['LAYOUT', 'PAINT', 'SIZE', 'STYLE', 'STRICT', 'CONTENT']) {
      expect(bodyRootHasContainment(bodyWith([RED, contain(kw)]))).toBe(true);
    }
  });

  it('is true when a multi-keyword list carries any non-none token', () => {
    // `contain: size style` → ["SIZE","STYLE"]; one real keyword suffices.
    expect(bodyRootHasContainment(bodyWith([contain('SIZE', 'STYLE')]))).toBe(true);
  });

  it('accepts the defensive bare-string leaf shape', () => {
    // Same tolerance Compose's Contain reader already has for this leaf.
    expect(bodyRootHasContainment(bodyWith([{ type: 'Contain', data: 'layout' }]))).toBe(true);
    expect(bodyRootHasContainment(bodyWith([{ type: 'Contain', data: 'none' }]))).toBe(false);
  });
});

describe('resolveCanvasBackground (wave-27 A-RC1 gate)', () => {
  it('still propagates an author body background when nothing is contained', () => {
    // The wave-14 behaviour (css-color/a98rgb-003's full-page grey) must not
    // move: an uncontained body still paints the canvas.
    expect(resolveCanvasBackground(docWithBody([RED]))).toBe('rgba(255, 0, 0, 1)');
  });

  it('keeps the white canvas when the body-root is contained', () => {
    // contain-body-bg-001: `body { background: red; contain: layout }`, ref
    // is pure white. The red must stay on the body's own box.
    expect(resolveCanvasBackground(docWithBody([RED, contain('LAYOUT')]))).toBe(WHITE);
  });

  it('blocks propagation for paint / size / style too', () => {
    // contain-body-bg-002/003/004 — all three match the SAME white ref, so
    // the gate cannot grade by containment kind.
    for (const kw of ['PAINT', 'SIZE', 'STYLE']) {
      expect(resolveCanvasBackground(docWithBody([RED, contain(kw)]))).toBe(WHITE);
    }
  });

  it('still propagates under `contain: none`', () => {
    // The initial value must behave exactly like an absent declaration.
    expect(resolveCanvasBackground(docWithBody([RED, contain('NONE')]))).toBe('rgba(255, 0, 0, 1)');
  });

  it('leaves the canvas PADDING ungated', () => {
    // css-contain-2 §3.5 is about the background propagation path only. A
    // contained body that also declares `padding: 40px` must still get
    // frame + author inset on every side — the pad resolver never consults
    // the containment gate.
    const doc = docWithBody([
      RED,
      contain('LAYOUT'),
      { type: 'PaddingTop', data: { px: 40 } },
      { type: 'PaddingRight', data: { px: 40 } },
      { type: 'PaddingBottom', data: { px: 40 } },
      { type: 'PaddingLeft', data: { px: 40 } },
    ]);
    expect(resolveCanvasBackground(doc)).toBe(WHITE);
    expect(resolveCanvasPadding(doc)).toEqual({ top: 56, right: 56, bottom: 56, left: 56 });
  });
});
