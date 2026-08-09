// wave-38 lane N6 — the composed canvas's PRINCIPAL DIRECTION resolver, and
// the body `text-align` pin that rides with it.
//
// THE MECHANISM. css-writing-modes-4 §8 propagates TWO properties from the root
// element (in HTML, from the first `<body>` child when there is one) to the
// initial containing block: "the used values of writing-mode, direction and
// text-orientation on the root element are propagated to the viewport". Wave-37
// lane W6 wired the BLOCK axis (resolveCanvasWritingMode); this lane wires the
// INLINE one.
//
// WHY THE BAKE IS NOT ENOUGH. extract-fixture's ROOT_INHERITED_TRIGGER_PROPS
// already copies the body-root's `direction` onto every top-level body child,
// so the TEXT inside each root already ran RTL. What a value copy cannot
// deliver is the decision only the containing block makes: where a root-forest
// box NARROWER than the ICB is placed (CSS 2.1 §10.3.3 reads the containing
// block's direction for an over-constrained block; css-flexbox/css-align read
// it for `justify-content` and the `start`/`end` keywords).
//
// MEASURED — the 42-test corpus-wide root-scope-`direction` slice
// (_diag38/N6/rootdir.tests: every bucket-A test whose html/body/:root rule or
// html/body `dir=` attribute declares one), web-only at the wave-38 head, base
// → this change. 13 rows up, 0 down, 29 byte-identical, passes 22 → 26:
//   css-flexbox/gap-005-rtl                       0.8317 → 1.0000  (pass)
//   css-flexbox/gap-006-rtl                       0.8417 → 1.0000  (pass)
//   css-flexbox/gap-007-rtl                       0.8372 → 0.9862  (pass)
//   css-break/table-collapsed-borders-…-vlr-rtl   0.7141 → 0.9838  (pass)
//   css-break/table-grid-paint-vlr-rtl            0.7987 → 0.9015
//   css-break/table-section-paint-vrl-rtl         0.7947 → 0.8570
//   css-anchor-position/anchor-center-safe-rtl    0.8830 → 0.9032
//   CSS2/text/bidi-span-002                       1.0000 → 1.0000  (held at 1.0
//     ONLY by the text-align pin below — the direction alone moved its ink to
//     the rtl `start` edge for 0.9945)
//
// These pins hold the decision table and the ICB wiring. Both resolvers must
// stay SILENT (undefined ⇒ no key at all) for every LTR document, because that
// silence is what keeps the other ~10,600 corpus fixtures byte-identical.

import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import {
  ComposedCaptureGallery,
  resolveCanvasDirection,
  resolveCanvasTextAlign,
} from '../../src/ui/ComposedCaptureGallery';
import type { IRDocument } from '@style-converter/web/core/ir/IRModels';

/** Minimal per-test document with one body-root carrying `props`. */
const docWithBody = (props: Array<{ type: string; data: unknown }>): IRDocument => ({
  irVersion: 2,
  minReaderVersion: 2,
  components: [
    {
      id: 'b',
      name: 'wpt__CSS2__text__bidi-span-002__body',
      properties: props as never,
      meta: { role: 'body-root' },
    },
    { id: 'r', name: 'wpt__CSS2__text__bidi-span-002__0', properties: [] },
  ],
});

/** A document with no body-root at all — the corpus majority. */
const bareDoc: IRDocument = {
  irVersion: 2,
  minReaderVersion: 2,
  components: [{ id: 'r', name: 'wpt__s__t__0', properties: [] }],
};

describe('resolveCanvasDirection (wave-38 N6)', () => {
  it('is silent when the document has no body-root', () => {
    expect(resolveCanvasDirection(bareDoc)).toBeUndefined();
  });

  it('is silent for a body-root that declares no direction', () => {
    // The a98rgb-003 shape: a body-root exists (it declares a background) but
    // says nothing about the inline axis — that capture must not move.
    const doc = docWithBody([
      { type: 'BackgroundColor', data: { srgb: { r: 0.5, g: 0.5, b: 0.5 } } },
    ]);
    expect(resolveCanvasDirection(doc)).toBeUndefined();
  });

  it('is silent for an explicit ltr (the canvas already does that)', () => {
    // css-text/text-align-match-parent-root-ltr's shape: the document states
    // the initial value. Writing the key would be a no-op with a byte-diff
    // risk, so we refuse it — that test is one of the 29 rows measured
    // byte-identical across the A/B.
    expect(resolveCanvasDirection(docWithBody([{ type: 'Direction', data: 'LTR' }]))).toBeUndefined();
  });

  it('propagates rtl', () => {
    expect(resolveCanvasDirection(docWithBody([{ type: 'Direction', data: 'RTL' }]))).toBe('rtl');
  });

  it('refuses a value that is not a css-writing-modes keyword', () => {
    // No-silent-fallthrough: an unrecognised IR keyword must never be written
    // verbatim into a live style object.
    expect(resolveCanvasDirection(docWithBody([{ type: 'Direction', data: 'AUTO' }]))).toBeUndefined();
  });

  it('is blocked by containment on the body-root', () => {
    // css-contain-1 §3.1 names `direction` in the SAME clause as writing-mode:
    // for a contained body the used values are not propagated to the viewport.
    // WPT states it in the titles — css-contain/contain-body-dir-001..004 and
    // contain-html-dir-001..004 read "…containment on body|html prevents
    // direction propagation", and all eight match one reference whose orange
    // square sits upper-LEFT. All eight are measured at SSIM 1.0000 on both
    // sides of the A/B, which is exactly this gate holding.
    const doc = docWithBody([
      { type: 'Direction', data: 'RTL' },
      { type: 'Contain', data: ['LAYOUT'] },
    ]);
    expect(resolveCanvasDirection(doc)).toBeUndefined();
  });

  it('is NOT blocked by `contain: none`', () => {
    // `["NONE"]` is not containment — the same reading bodyRootHasContainment
    // pins for the background and writing-mode propagations.
    const doc = docWithBody([
      { type: 'Direction', data: 'RTL' },
      { type: 'Contain', data: ['NONE'] },
    ]);
    expect(resolveCanvasDirection(doc)).toBe('rtl');
  });
});

describe('resolveCanvasTextAlign (wave-38 N6)', () => {
  it('is silent unless the direction propagation fired', () => {
    // The gate that bounds the blast radius: an LTR document with a body
    // `text-align` keeps the ICB it had in wave 37, measured or not.
    const ltr = docWithBody([
      { type: 'TextAlign', data: 'CENTER' },
      { type: 'Direction', data: 'LTR' },
    ]);
    expect(resolveCanvasTextAlign(ltr)).toBeUndefined();
    // …and so does a document that declares an alignment and no direction.
    expect(resolveCanvasTextAlign(docWithBody([{ type: 'TextAlign', data: 'CENTER' }])))
      .toBeUndefined();
    // …and one whose direction propagation is blocked by containment.
    const contained = docWithBody([
      { type: 'TextAlign', data: 'CENTER' },
      { type: 'Direction', data: 'RTL' },
      { type: 'Contain', data: ['PAINT'] },
    ]);
    expect(resolveCanvasTextAlign(contained)).toBeUndefined();
  });

  it('reads the body alignment once the inline axis is reversed', () => {
    // CSS2/text/bidi-span-002 exactly: `body { text-align:left; direction:rtl }`
    // with a single INLINE `<span>` root. `text-align` on the span itself does
    // nothing (it aligns a BLOCK container's line boxes), so only the ICB can
    // answer — and without this the ink moved to the rtl start edge.
    const doc = docWithBody([
      { type: 'TextAlign', data: 'LEFT' },
      { type: 'Direction', data: 'RTL' },
    ]);
    expect(resolveCanvasTextAlign(doc)).toBe('left');
  });

  it('is silent for `start` (the ICB initial) and for unknown keywords', () => {
    const start = docWithBody([
      { type: 'TextAlign', data: 'START' },
      { type: 'Direction', data: 'RTL' },
    ]);
    expect(resolveCanvasTextAlign(start)).toBeUndefined();
    // `match-parent` resolves against a parent the ICB does not have.
    const matchParent = docWithBody([
      { type: 'TextAlign', data: 'MATCH_PARENT' },
      { type: 'Direction', data: 'RTL' },
    ]);
    expect(resolveCanvasTextAlign(matchParent)).toBeUndefined();
  });
});

/** The inner ICB div's inline style — the ref's render viewport (wave-25). */
const icbStyleOf = (html: string) =>
  /data-capture-icb[^>]*?style="([^"]*)"/.exec(html)?.[1] ?? '';
/** The OUTER canvas div's inline style (the 390x600 framed image). */
const canvasStyleOf = (html: string) =>
  /data-capture-canvas[^>]*?style="([^"]*)"/.exec(html)?.[1] ?? '';

describe('ComposedTestCanvas direction wiring (wave-38 N6)', () => {
  it('puts the principal direction on the ICB, never on the frame', () => {
    // The ICB IS the ref's render viewport, so it is the containing block whose
    // direction CSS 2.1 §10.3.3 reads when placing a root-forest box — and
    // `direction` is inherited, so one declaration there also reaches every
    // descendant's bidi resolution. The outer div carries only the image frame.
    const doc = docWithBody([{ type: 'Direction', data: 'RTL' }]);
    const html = renderToStaticMarkup(<ComposedCaptureGallery document={doc} />);
    expect(icbStyleOf(html)).toMatch(/direction:rtl/);
    expect(canvasStyleOf(html)).not.toMatch(/direction/);
  });

  it('emits NO direction key at all for an LTR document', () => {
    // The byte-compatibility pin: the ~10,600 fixtures with no rtl root must
    // render the exact same inline style they did in wave 37.
    const html = renderToStaticMarkup(<ComposedCaptureGallery document={bareDoc} />);
    expect(icbStyleOf(html)).not.toMatch(/direction/);
    expect(icbStyleOf(html)).not.toMatch(/text-align/);
  });

  it('emits the text-align pin alongside the reversed direction', () => {
    const doc = docWithBody([
      { type: 'TextAlign', data: 'LEFT' },
      { type: 'Direction', data: 'RTL' },
    ]);
    const icb = icbStyleOf(renderToStaticMarkup(<ComposedCaptureGallery document={doc} />));
    expect(icb).toMatch(/direction:rtl/);
    expect(icb).toMatch(/text-align:left/);
  });

  it('keeps the ICB geometry and containing block intact when reversed', () => {
    // The reversal must not disturb the wave-25 round-3 calibration: the ICB is
    // still 358 x 568 minimum and still the containing block for out-of-flow
    // descendants.
    const doc = docWithBody([{ type: 'Direction', data: 'RTL' }]);
    const icb = icbStyleOf(renderToStaticMarkup(<ComposedCaptureGallery document={doc} />));
    expect(icb).toMatch(/position:relative/);
    expect(icb).toMatch(/transform:translateZ\(0\)/);
    expect(icb).toMatch(/min-height:568px/);
  });

  it('composes with the W6 block axis — both keys ride the same ICB', () => {
    // css-writing-modes/dynamic-offset-vrl-rtl-001/002 are the corpus's own
    // both-axes documents (`html { writing-mode: vertical-rl; direction: rtl }`)
    // and both are measured byte-identical across the A/B at 1.0000 / 0.9955.
    const doc = docWithBody([
      { type: 'WritingMode', data: 'VERTICAL_RL' },
      { type: 'Direction', data: 'RTL' },
    ]);
    const icb = icbStyleOf(renderToStaticMarkup(<ComposedCaptureGallery document={doc} />));
    expect(icb).toMatch(/writing-mode:vertical-rl/);
    expect(icb).toMatch(/direction:rtl/);
  });
});
