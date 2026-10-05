// wave-52 lane L2 (M1) — the composed canvas's BODY MARGIN resolver and its
// single-owner wrapper.
//
// THE DEFECT THIS PINS. The extractor emits html+body as ONE synthetic
// `body-root` whose element children are SIBLINGS, and the canvas read that
// root for background and PADDING only — so a DECLARED body margin never
// moved the forest, while the ref keeps it (its `:where(html, body)
// { margin: 0 }` injection has specificity 0 and loses to the author's
// `body {}` rule). MEASURED: css-gaps/flex/flex-gap-decorations-027's whole
// web page sat exactly 200 px left of the ref (wave51-fix web f 0.9012).
//
// THE CONTRACT (shared with Compose resolveComposedCanvasMargin and SwiftUI
// ComposedCaptureCanvas.resolvedMargin):
//   - concrete px sides only, per side, negatives kept; `auto` / `em` → 0;
//   - CSS 2.1 §8.3: a table-internal body (`display: table-cell`,
//     CSS2/css21-errata/s-11-1-1b-005) has no used margin → all 0, no wrapper;
//   - the margin rides a `display: flow-root` wrapper around the forest, and
//     the body-root NODE renders WITHOUT the sides the wrapper owns — one
//     owner, never applied twice (collapsed-border-*-rtl-overflow's 60 px);
//   - no concrete margin ⇒ no wrapper ⇒ the exact wave-51 forest.
//
// Every body-root bag is VERBATIM from tools/titan/runs/wave51-fix/sections/
// <section>/per-test-ir/ (the collapsed-border table subtree is elided: the
// pins read the wrapper and the body-root node, not the table).
//
// EXECUTED MUTATIONS (applied to ComposedCaptureGallery.tsx, this file run
// alone, source restored byte-exact — sha256 checked; recorded in
// tools/titan/results/wave52-composed-canvas/_note.md):
//   MW1 CANVAS_MARGIN_SIDES 'MarginLeft' → 'MarginInlineStart' → gaps027_* red.
//   MW2 the §8.3 table-internal guard deleted                  → s005_* red.
//   MW3 the wrapper maps `roots` instead of `flowRoots`         → oneOwner_* red.

import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import {
  ComposedCaptureGallery,
  resolveCanvasMargin,
  withCanvasOwnedBodyMargin,
} from '../../src/ui/ComposedCaptureGallery';
import type { IRDocument } from '@style-converter/web/core/ir/IRModels';

/** css-gaps/flex/flex-gap-decorations-027 root 0, verbatim. */
const BODY_027 = {"id":"wpt__css-gaps__flex__flex-gap-decorations-027__0-193","name":"wpt__css-gaps__flex__flex-gap-decorations-027__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":200}}],"meta":{"role":"body-root"}};
/** CSS2/css21-errata/s-11-1-1b-006 root 0 (`display: table`), verbatim. */
const BODY_006 = {"id":"wpt__css2__css21-errata__s-11-1-1b-006__0-141","name":"wpt__CSS2__css21-errata__s-11-1-1b-006__0","properties":[{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"Display","data":"TABLE"},{"type":"BorderSpacing","data":{"type":"single","px":0}},{"type":"MarginTop","data":{"px":40}},{"type":"MarginRight","data":{"px":8}},{"type":"MarginBottom","data":{"px":8}},{"type":"MarginLeft","data":{"px":8}}],"meta":{"role":"body-root"}};
/** CSS2/css21-errata/s-11-1-1b-005 root 0 (`display: table-cell`), verbatim. */
const BODY_005 = {"id":"wpt__css2__css21-errata__s-11-1-1b-005__0-139","name":"wpt__CSS2__css21-errata__s-11-1-1b-005__0","properties":[{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"Display","data":"TABLE_CELL"},{"type":"BorderSpacing","data":{"type":"single","px":0}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}},{"type":"MarginTop","data":{"px":-15}},{"type":"MarginRight","data":{"px":8}},{"type":"MarginBottom","data":{"px":8}},{"type":"MarginLeft","data":{"px":8}},{"type":"Width","data":{"type":"length","px":20}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"role":"body-root"}};
/** css-tables/collapsed-border-vertical-rtl-overflow root 0, verbatim. */
const BODY_COLLAPSED = {"id":"wpt__css-tables__collapsed-border-vertical-rtl-overflow__0-207","name":"wpt__css-tables__collapsed-border-vertical-rtl-overflow__0","properties":[{"type":"MarginTop","data":{"px":60}},{"type":"MarginRight","data":{"px":60}},{"type":"MarginBottom","data":{"px":60}},{"type":"MarginLeft","data":{"px":60}}],"meta":{"role":"body-root"}};
/** css-contain/contain-body-dir-001 root 0 (`margin-right: auto`), properties verbatim (pseudo elided). */
const BODY_AUTO = {"id":"wpt__css-contain__contain-body-dir-001__0-011","name":"wpt__css-contain__contain-body-dir-001__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":"auto"},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},{"type":"Width","data":{"type":"length","px":200}},{"type":"Height","data":{"type":"length","px":200}},{"type":"Direction","data":"RTL"},{"type":"Contain","data":["LAYOUT"]}],"meta":{"role":"body-root","lang":"en"}};

/** A one-test document: the body-root bag plus one plain in-flow root. */
const doc = (body: unknown): IRDocument =>
  ({ irVersion: 2, minReaderVersion: 2,
     components: [body, { id: 'r-1', name: 'wpt__t__1', properties: [] }] }) as unknown as IRDocument;

/** The flow wrapper's inline style, or null when no wrapper was emitted. */
const flowStyleOf = (html: string) => /data-capture-flow[^>]*?style="([^"]*)"/.exec(html)?.[1] ?? null;
/** One component node's inline style, by its data-component-id. */
const nodeStyleOf = (html: string, id: string) =>
  new RegExp(`data-component-id="${id}"[^>]*?style="([^"]*)"`).exec(html)?.[1] ?? '';
/** Render one document through the real gallery (static markup). */
const render = (d: IRDocument) => renderToStaticMarkup(<ComposedCaptureGallery document={d} />);

describe('resolveCanvasMargin (wave-52 L2 M1)', () => {
  it('gaps027_bodyMarginLeft200_isTheFlowInset', () => {
    // `body { margin: 0 0 0 200px }` → the ref's container border at 216.
    expect(resolveCanvasMargin(doc(BODY_027))).toEqual({ top: 0, right: 0, bottom: 0, left: 200 });
    // Wired: the wrapper carries it, as a flow-root BFC (the ref body's twin).
    const flow = flowStyleOf(render(doc(BODY_027)));
    expect(flow).toContain('display:flow-root');
    expect(flow).toContain('margin-left:200px');
  });

  it('s006_tableBody_keepsAllFourSides', () => {
    // A `display: table` body DOES take margins (§8.3 names table).
    expect(resolveCanvasMargin(doc(BODY_006))).toEqual({ top: 40, right: 8, bottom: 8, left: 8 });
  });

  it('s005_tableCellBody_hasNoUsedMargin_noWrapper', () => {
    // §8.3: margins do not apply to table-internal boxes — nothing to move,
    // no wrapper, the wave-51 markup (web P 0.9947 stays byte-identical).
    expect(resolveCanvasMargin(doc(BODY_005))).toEqual({ top: 0, right: 0, bottom: 0, left: 0 });
    expect(flowStyleOf(render(doc(BODY_005)))).toBeNull();
  });

  it('autoAndEmLeaves_keepZero_noWrapper', () => {
    // `margin-right: auto` has no absolute answer here; the zero sides are 0.
    expect(resolveCanvasMargin(doc(BODY_AUTO))).toEqual({ top: 0, right: 0, bottom: 0, left: 0 });
    expect(flowStyleOf(render(doc(BODY_AUTO)))).toBeNull();
    // An em leaf likewise (first-letter-exclude-* shape).
    const em = { ...BODY_027, properties: [{ type: 'MarginLeft', data: { original: { v: 1, u: 'EM' } } }] };
    expect(resolveCanvasMargin(doc(em)).left).toBe(0);
  });
});

describe('withCanvasOwnedBodyMargin (wave-52 L2 M1 — one owner)', () => {
  it('oneOwner_collapsedBorder_wrapperHas60_bodyNodeHasNone', () => {
    const html = render(doc(BODY_COLLAPSED));
    // The wrapper owns the 60 on every side …
    const flow = flowStyleOf(html) ?? '';
    expect(flow).toContain('margin-top:60px');
    expect(flow).toContain('margin-left:60px');
    // … and the body-root node renders without it (else 120 px, MW3 red).
    expect(nodeStyleOf(html, BODY_COLLAPSED.id)).not.toMatch(/margin-(top|left|right|bottom):60px/);
  });

  it('identity_whenTheCanvasOwnsNothing', () => {
    // No margin ⇒ the SAME array object (React keeps every node identical).
    const roots = [{ component: BODY_AUTO as never, children: [] }];
    expect(withCanvasOwnedBodyMargin(roots, { top: 0, right: 0, bottom: 0, left: 0 })).toBe(roots);
  });

  it('stripsOnlyTheConcreteSides_andOnlyTheBodyRoot', () => {
    // A body with px left + auto right: only MarginLeft leaves the node.
    const body = { ...BODY_027, properties: [
      { type: 'MarginLeft', data: { px: 100 } }, { type: 'MarginRight', data: 'auto' }] };
    const plain = { id: 'p', name: 'p', properties: [{ type: 'MarginLeft', data: { px: 100 } }] };
    const out = withCanvasOwnedBodyMargin(
      [{ component: body as never, children: [] }, { component: plain as never, children: [] }],
      { top: 0, right: 0, bottom: 0, left: 100 });
    expect(out[0].component.properties.map((p) => p.type)).toEqual(['MarginRight']);
    // A non-body root keeps its own margin untouched.
    expect(out[1].component.properties.map((p) => p.type)).toEqual(['MarginLeft']);
  });
});
