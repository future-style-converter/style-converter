// wave-53 lane L3 (item B) — a `display: table` BODY lays its children out as
// a table on the composed web canvas (CSS 2.1 §17.2.1 rule 2, §8.3).
//
// THE DEFECT THIS PINS. The body's children are SIBLING roots, so the body's
// `display: table` reached only an empty box, and the children stacked in the
// canvas's block `flow-root` wrapper. MEASURED on wave52-ship
// CSS2/css21-errata/s-11-1-1b-006 web P 0.9941 with the WRONG picture: the
// caption div's 10-px margin put the td's square at image rows 66-85 (the
// reference, Chrome on a real table body, has 56-75).
//
// THE CONTRACT: the `data-capture-flow` wrapper — the box standing in for the
// body — becomes `display: table` with the body's border-spacing, even at zero
// margin; Chrome (the reference engine) then does the anonymous-table fixup.
// Every non-table body keeps the wave-52 wrapper exactly (the M1 pins in
// ComposedCanvasMargin.test.tsx stay green).
//
// Component lists VERBATIM from tools/titan/runs/wave52-ship/sections/<section>/per-test-ir/.
//
// EXECUTED MUTATION (ComposedCaptureGallery.tsx, this file run alone, source
// restored byte-exact — sha256 in tools/titan/results/wave53-canvas-root/_note.md):
//   BW1 the table branch removed (`canvasTableBody` forced null) → s006_* red.

import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { ComposedCaptureGallery } from '../../src/ui/ComposedCaptureGallery';
import { resolveCanvasTableBody } from '../../src/ui/CanvasTableBody';
import type { IRDocument } from '@style-converter/web/core/ir/IRModels';

/** CSS2/css21-errata/s-11-1-1b-006 — all four roots, verbatim (`body { display: table }`). */
const S006 = [{"id":"wpt__css2__css21-errata__s-11-1-1b-006__0-141","name":"wpt__CSS2__css21-errata__s-11-1-1b-006__0","properties":[{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"Display","data":"TABLE"},{"type":"BorderSpacing","data":{"type":"single","px":0}},{"type":"MarginTop","data":{"px":40}},{"type":"MarginRight","data":{"px":8}},{"type":"MarginBottom","data":{"px":8}},{"type":"MarginLeft","data":{"px":8}}],"meta":{"role":"body-root"}},{"id":"wpt__css2__css21-errata__s-11-1-1b-006__1-142","name":"wpt__CSS2__css21-errata__s-11-1-1b-006__1","properties":[{"type":"Generic","data":{"propertyName":"display","rawValue":"caption","_unmapped":true}},{"type":"MarginBottom","data":{"px":10}}],"meta":{"role":"ws-after"}},{"id":"wpt__css2__css21-errata__s-11-1-1b-006__2-143","name":"wpt__CSS2__css21-errata__s-11-1-1b-006__2","properties":[{"type":"Display","data":"TABLE_CELL"},{"type":"Width","data":{"type":"length","px":20}},{"type":"Height","data":{"type":"length","px":20}},{"type":"MarginTop","data":{"px":-15}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"meta":{"role":"ws-after"}},{"id":"wpt__css2__css21-errata__s-11-1-1b-006__3-144","name":"wpt__CSS2__css21-errata__s-11-1-1b-006__3","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Top","data":{"px":0}},{"type":"Left","data":{"px":8}}],"text":"Test passes if there is a black square below.","meta":{"sourceTag":"p"}}];
/** CSS2/css21-errata/s-11-1-1b-005 — verbatim (`display: table-cell` body). */
const S005 = [{"id":"wpt__css2__css21-errata__s-11-1-1b-005__0-139","name":"wpt__CSS2__css21-errata__s-11-1-1b-005__0","properties":[{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"Display","data":"TABLE_CELL"},{"type":"BorderSpacing","data":{"type":"single","px":0}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}},{"type":"MarginTop","data":{"px":-15}},{"type":"MarginRight","data":{"px":8}},{"type":"MarginBottom","data":{"px":8}},{"type":"MarginLeft","data":{"px":8}},{"type":"Width","data":{"type":"length","px":20}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"role":"body-root"}},{"id":"css21-errata__s-11-1-1b-005__0-140","name":"css21-errata__s-11-1-1b-005__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Top","data":{"px":0}}],"slot":{"parent":"wpt__css2__css21-errata__s-11-1-1b-005__0-139"},"text":"Test passes if there is a black square below.","meta":{"sourceTag":"p"}}];
/** CSS2/css21-errata/s-11-1-1b-007 — verbatim (a neighbour with no table body). */
const S007 = [{"id":"wpt__css2__css21-errata__s-11-1-1b-007__0-145","name":"wpt__CSS2__css21-errata__s-11-1-1b-007__0","properties":[],"text":"Test passes if there is a black square below.","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css2__css21-errata__s-11-1-1b-007__1-146","name":"wpt__CSS2__css21-errata__s-11-1-1b-007__1","properties":[{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"BorderSpacing","data":{"type":"single","px":10}}],"meta":{"sourceTag":"table"}},{"id":"css21-errata__s-11-1-1b-007__1__0-147","name":"css21-errata__s-11-1-1b-007__1__0","properties":[],"slot":{"parent":"wpt__css2__css21-errata__s-11-1-1b-007__1-146"},"meta":{"sourceTag":"tr"}},{"id":"css21-errata__s-11-1-1b-007__1__0__0-148","name":"css21-errata__s-11-1-1b-007__1__0__0","properties":[{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}}],"slot":{"parent":"css21-errata__s-11-1-1b-007__1__0-147"},"meta":{"sourceTag":"td"}},{"id":"css21-errata__s-11-1-1b-007__1__0__0__0-149","name":"css21-errata__s-11-1-1b-007__1__0__0__0","properties":[{"type":"Width","data":{"type":"length","px":20}},{"type":"Height","data":{"type":"length","px":25}},{"type":"BorderTopWidth","data":{"px":10}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"MarginTop","data":{"px":-25}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"slot":{"parent":"css21-errata__s-11-1-1b-007__1__0__0-148"}}];
/** css-gaps/flex/flex-gap-decorations-027 — verbatim (a margin body, the M1 wrapper). */
const GAPS027 = [{"id":"wpt__css-gaps__flex__flex-gap-decorations-027__0-193","name":"wpt__css-gaps__flex__flex-gap-decorations-027__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":200}}],"meta":{"role":"body-root"}},{"id":"wpt__css-gaps__flex__flex-gap-decorations-027__1-194","name":"wpt__css-gaps__flex__flex-gap-decorations-027__1","properties":[{"type":"BorderTopWidth","data":{"px":2}},{"type":"BorderRightWidth","data":{"px":2}},{"type":"BorderBottomWidth","data":{"px":2}},{"type":"BorderLeftWidth","data":{"px":2}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647},"original":{"r":96,"g":139,"b":168}}},{"type":"BorderRightColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647},"original":{"r":96,"g":139,"b":168}}},{"type":"BorderBottomColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647},"original":{"r":96,"g":139,"b":168}}},{"type":"BorderLeftColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647},"original":{"r":96,"g":139,"b":168}}},{"type":"Display","data":"FLEX"},{"type":"ColumnGap","data":{"type":"length","px":10}},{"type":"ColumnRuleStyle","data":"SOLID"},{"type":"ColumnRuleWidth","data":{"type":"length","px":10}},{"type":"ColumnRuleColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Width","data":{"type":"length","px":200}},{"type":"FlexWrap","data":"NOWRAP"}]},{"id":"flex__flex-gap-decorations-027__0__0-195","name":"flex__flex-gap-decorations-027__0__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647,"a":0.2},"original":{"r":96,"g":139,"b":168,"a":0.2}}},{"type":"Width","data":{"type":"length","px":50}},{"type":"Height","data":{"type":"length","px":50}},{"type":"FlexShrink","data":{"value":{"type":"app.irmodels.properties.layout.flexbox.FlexShrinkProperty.FlexShrinkValue.Number","value":0},"normalizedValue":0}},{"type":"MarginLeft","data":{"px":-150}}],"slot":{"parent":"wpt__css-gaps__flex__flex-gap-decorations-027__1-194"},"text":"One","meta":{"role":"ws-after"}},{"id":"flex__flex-gap-decorations-027__0__1-196","name":"flex__flex-gap-decorations-027__0__1","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647,"a":0.2},"original":{"r":96,"g":139,"b":168,"a":0.2}}},{"type":"Width","data":{"type":"length","px":50}},{"type":"Height","data":{"type":"length","px":50}},{"type":"FlexShrink","data":{"value":{"type":"app.irmodels.properties.layout.flexbox.FlexShrinkProperty.FlexShrinkValue.Number","value":0},"normalizedValue":0}}],"slot":{"parent":"wpt__css-gaps__flex__flex-gap-decorations-027__1-194"},"text":"Two","meta":{"role":"ws-after"}},{"id":"flex__flex-gap-decorations-027__0__2-197","name":"flex__flex-gap-decorations-027__0__2","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647,"a":0.2},"original":{"r":96,"g":139,"b":168,"a":0.2}}},{"type":"Width","data":{"type":"length","px":50}},{"type":"Height","data":{"type":"length","px":50}},{"type":"FlexShrink","data":{"value":{"type":"app.irmodels.properties.layout.flexbox.FlexShrinkProperty.FlexShrinkValue.Number","value":0},"normalizedValue":0}}],"slot":{"parent":"wpt__css-gaps__flex__flex-gap-decorations-027__1-194"},"text":"Three","meta":{"role":"ws-after"}},{"id":"flex__flex-gap-decorations-027__0__3-198","name":"flex__flex-gap-decorations-027__0__3","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647,"a":0.2},"original":{"r":96,"g":139,"b":168,"a":0.2}}},{"type":"Width","data":{"type":"length","px":50}},{"type":"Height","data":{"type":"length","px":50}},{"type":"FlexShrink","data":{"value":{"type":"app.irmodels.properties.layout.flexbox.FlexShrinkProperty.FlexShrinkValue.Number","value":0},"normalizedValue":0}}],"slot":{"parent":"wpt__css-gaps__flex__flex-gap-decorations-027__1-194"},"text":"Four","meta":{"role":"ws-after"}},{"id":"flex__flex-gap-decorations-027__0__4-199","name":"flex__flex-gap-decorations-027__0__4","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647,"a":0.2},"original":{"r":96,"g":139,"b":168,"a":0.2}}},{"type":"Width","data":{"type":"length","px":50}},{"type":"Height","data":{"type":"length","px":50}},{"type":"FlexShrink","data":{"value":{"type":"app.irmodels.properties.layout.flexbox.FlexShrinkProperty.FlexShrinkValue.Number","value":0},"normalizedValue":0}}],"slot":{"parent":"wpt__css-gaps__flex__flex-gap-decorations-027__1-194"},"text":"Five","meta":{"role":"ws-after"}},{"id":"flex__flex-gap-decorations-027__0__5-200","name":"flex__flex-gap-decorations-027__0__5","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.3764705882352941,"g":0.5450980392156862,"b":0.6588235294117647,"a":0.2},"original":{"r":96,"g":139,"b":168,"a":0.2}}},{"type":"Width","data":{"type":"length","px":50}},{"type":"Height","data":{"type":"length","px":50}},{"type":"FlexShrink","data":{"value":{"type":"app.irmodels.properties.layout.flexbox.FlexShrinkProperty.FlexShrinkValue.Number","value":0},"normalizedValue":0}}],"slot":{"parent":"wpt__css-gaps__flex__flex-gap-decorations-027__1-194"},"text":"Six"}];

/** A per-test document over a verbatim component list. */
const doc = (components: unknown[]): IRDocument =>
  ({ irVersion: 2, minReaderVersion: 2, components }) as unknown as IRDocument;
/** Render one document through the real gallery (static markup). */
const render = (d: IRDocument) => renderToStaticMarkup(<ComposedCaptureGallery document={d} />);
/** The flow wrapper's inline style, or null when no wrapper was emitted. */
const flowStyleOf = (html: string) => /data-capture-flow[^>]*?style="([^"]*)"/.exec(html)?.[1] ?? null;

describe('ComposedTestCanvas — table body (wave-53 L3 item B)', () => {
  it('s006_wrapperIsATableBox_withTheBodysBorderSpacing', () => {
    expect(resolveCanvasTableBody(doc(S006))).toEqual({ display: 'table', borderSpacing: '0px' });
    const flow = flowStyleOf(render(doc(S006)));
    expect(flow).toContain('display:table;');
    expect(flow).toContain('border-spacing:0px');
    // The body's own margin still rides the wrapper (it is the body's box).
    expect(flow).toContain('margin-top:40px');
    expect(flow).toContain('margin-left:8px');
  });

  it('s006_zeroMarginTableBody_stillGetsTheWrapper', () => {
    // The wrapper is emitted for a table body even when the canvas owns no margin.
    const noMargin = S006.map((c, i) => i === 0
      ? { ...c, properties: c.properties.filter((p: { type: string }) => !p.type.startsWith('Margin')) } : c);
    expect(flowStyleOf(render(doc(noMargin)))).toContain('display:table;');
  });

  it('nonTableBodies_keepTheWave52Wrapper', () => {
    // table-cell body: no used margin, no wrapper (s-11-1-1b-005, M1 pin).
    expect(resolveCanvasTableBody(doc(S005))).toBeNull();
    expect(flowStyleOf(render(doc(S005)))).toBeNull();
    // A body-less neighbour: no wrapper at all.
    expect(resolveCanvasTableBody(doc(S007))).toBeNull();
    expect(flowStyleOf(render(doc(S007)))).toBeNull();
    // A margin body: the flow-root wrapper, no border-spacing key.
    const flow = flowStyleOf(render(doc(GAPS027))) ?? '';
    expect(flow).toContain('display:flow-root');
    expect(flow).not.toContain('border-spacing');
  });
});
