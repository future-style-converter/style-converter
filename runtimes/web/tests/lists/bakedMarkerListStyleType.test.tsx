// bakedMarkerListStyleType.test.tsx — wave 52, lane L6 (T3): the web
// runtime consumes `meta.markerText` for EXACTLY two shapes (range-limited
// additive styles; author `@counter-style` names), in the form the replay
// measured (inside + childless → inline box; else the `<string>`).
// Payloads are VERBATIM wave51-fix per-test IR components (css-counter-
// styles/per-test-ir/ …armenian__css3-counter-styles-008.json,
// …cssom__cssom-pad-setter-invalid.json + the "001." the T7 bake stamps,
// …counter-suffix.json).
//
// MUTATION PROOF (EXECUTED 2026-09-25, RE-EXECUTED 2026-10-05 on this
// 13-test file by tools/titan/results/wave52-counters-and-lists/mutate.py,
// mutations.log): `web-widen` (the string form for EVERY keyword) 3 red —
// the predefined-keyword pin, the plan's decline pin, the decimal renderer
// control; `web-narrow` (shape 1 only) 4 red — the author-name cases;
// `web-inline` (inside + childless branch off) 3 red — the plan pin and
// both NodeRenderer inline-form cases. Each restored byte-exact.
import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { NodeRenderer } from '../../src/renderer/NodeRenderer';
import type { ComposedNode } from '../../src/renderer/Composer';
import type { IRComponent } from '../../src/core/ir/IRModels';
import {
  bakedMarkerListStyleType, bakedMarkerPlan, RANGE_LIMITED_ADDITIVE_STYLES, PREDEFINED_COUNTER_STYLE_KEYWORDS,
} from '../../src/engine/lists/ListStyleTypeApplier';

// ── the pure rule ───────────────────────────────────────────────────────────

describe('bakedMarkerListStyleType — the two shapes', () => {
  it('shape 1: a range-limited additive style takes the baked string + one space', () => {
    // armenian-008: the bake resolved 10000 through §6.2 `fallback: decimal`.
    expect(bakedMarkerListStyleType('armenian', '10000.')).toBe('"10000. "');
    // In-range values take it too — the rule is per KEYWORD, not per value,
    // so a document's rows cannot mix Chrome's glyphs with the bake's.
    expect(bakedMarkerListStyleType('armenian', 'ՔՋՂԹ.')).toBe('"ՔՋՂԹ. "');
    for (const kw of RANGE_LIMITED_ADDITIVE_STYLES) {
      expect(bakedMarkerListStyleType(kw, 'x.')).toBe('"x. "');
    }
    // Case-insensitive keyword, like every CSS keyword.
    expect(bakedMarkerListStyleType('Armenian', '10000.')).toBe('"10000. "');
  });

  it('shape 2: an author @counter-style name the wire could not carry', () => {
    // cssom-pad-setter-invalid: `list-style-type: foo`, bake stamps "001.".
    expect(bakedMarkerListStyleType('foo', '001.')).toBe('"001. "');
    expect(bakedMarkerListStyleType('my-style', '(A)')).toBe('"(A) "');
  });

  it('a predefined keyword keeps Chrome as the oracle (the wave-27 rule)', () => {
    // Every §6 / §7 keyword outside shape 1 declines, markerText or not.
    for (const kw of PREDEFINED_COUNTER_STYLE_KEYWORDS) {
      if (RANGE_LIMITED_ADDITIVE_STYLES.has(kw)) continue;
      expect(bakedMarkerListStyleType(kw, '1.'), kw).toBeUndefined();
    }
    // The four cells the T1 twin + T5 hang target on the natives stay
    // Chrome-driven on web: counter-suffix's decimal / hebrew / cjk-decimal
    // / korean-hangul-formal rows all pass on web today (P 0.9818).
    expect(bakedMarkerListStyleType('korean-hangul-formal', '일,')).toBeUndefined();
    expect(bakedMarkerListStyleType('cjk-decimal', '一、')).toBeUndefined();
  });

  it('declines without a baked marker, without a keyword, and on <string> / symbols()', () => {
    for (const none of [undefined, null, '']) expect(bakedMarkerListStyleType('armenian', none)).toBeUndefined();
    // Nothing declared anywhere = UA default: `start` forwarding covers it.
    expect(bakedMarkerListStyleType(undefined, '1.')).toBeUndefined();
    // The author already spelled the marker text in CSS.
    expect(bakedMarkerListStyleType('"# "', '1.')).toBeUndefined();
    expect(bakedMarkerListStyleType("symbols(cyclic '*')", '1.')).toBeUndefined();
  });

  it('escapes the CSS <string> delimiters (css-syntax-3 §4.3.5)', () => {
    expect(bakedMarkerListStyleType('foo', 'say "hi"')).toBe('"say \\"hi\\" "');
    expect(bakedMarkerListStyleType('foo', 'a\\b')).toBe('"a\\\\b "');
  });
});
// ── the FORM (replay/replay.json: ::marker's TABULAR digits wrap 008's row 3)
describe('bakedMarkerPlan — which form the baked marker takes', () => {
  it('inside + childless: list-style-type none and the inline text (marker + one space)', () => {
    expect(bakedMarkerPlan('armenian', 'inside', '10000.', false)).toEqual({ listStyleType: 'none', inlineMarkerText: '10000. ' });
    // The engine emits the position lowercase; the wire spells it INSIDE.
    expect(bakedMarkerPlan('foo', 'INSIDE', '001.', false)).toEqual({ listStyleType: 'none', inlineMarkerText: '001. ' });
  });

  it('outside, the initial value, or an item with children: the <string> form', () => {
    // counter-suffix's hebrew rows declare no position — initial `outside`.
    expect(bakedMarkerPlan('hebrew', undefined, 'א.', false)).toEqual({ listStyleType: '"א. "' });
    expect(bakedMarkerPlan('armenian', 'outside', 'Ա.', false)).toEqual({ listStyleType: '"Ա. "' });
    // marker-text-matches-armenian's item composes an inline-block child.
    expect(bakedMarkerPlan('armenian', 'inside', 'Ա.', true)).toEqual({ listStyleType: '"Ա. "' });
  });

  it('declines exactly where the shape gate declines', () => {
    expect(bakedMarkerPlan('decimal', 'inside', '1.', false)).toBeUndefined();
    expect(bakedMarkerPlan('armenian', 'inside', undefined, false)).toBeUndefined();
  });
});

// ── the renderer seam: own keyword, inherited keyword, and the control ─────

function node(component: IRComponent, children: ComposedNode[] = []): ComposedNode {
  return { component, children };
}
const html = (n: ComposedNode) => renderToStaticMarkup(<NodeRenderer node={n} />);
/** The `style` attribute of the element carrying `data-component-id="<id>"`. */
function styleOf(markup: string, id: string): string {
  const m = markup.match(new RegExp(`data-component-id="${id}"[^>]*style="([^"]*)"`));
  return m ? m[1] : '';
}

// VERBATIM: armenian-008's `<ol start=10000>` and its `<li>`.
const armenianOl: IRComponent = {
  id: 'armenian__css3-counter-styles-008__1__1-164', name: 'armenian__css3-counter-styles-008__1__1',
  properties: [
    { type: 'MarginTop', data: { px: 0 } }, { type: 'MarginRight', data: { px: 0 } },
    { type: 'MarginBottom', data: { px: 0 } }, { type: 'MarginLeft', data: { px: 0 } },
    { type: 'PaddingLeft', data: { original: { v: 8, u: 'EM' } } },
    { type: 'ListStylePosition', data: 'INSIDE' },
  ],
  meta: { sourceTag: 'ol', role: 'ws-after', attrs: { start: '10000' }, lang: 'en' },
};
const armenianLi: IRComponent = {
  id: 'armenian__css3-counter-styles-008__1__1__0-165', name: 'armenian__css3-counter-styles-008__1__1__0',
  properties: [{ type: 'ListStyleType', data: 'armenian' }],
  text: '10000',
  meta: { sourceTag: 'li', markerText: '10000.', lang: 'en' },
};

// VERBATIM: cssom-pad-setter-invalid's `<ol>` (declares the author name)
// and its first `<li>`, which declares NOTHING list-related — the keyword
// reaches it by inheritance. `markerText` is what the T7 bake stamps.
const cssomOl: IRComponent = {
  id: 'wpt__css-counter-styles__cssom__cssom-pad-setter-invalid__0-673',
  name: 'wpt__css-counter-styles__cssom__cssom-pad-setter-invalid__0',
  properties: [{ type: 'ListStyleType', data: 'foo' }, { type: 'ListStylePosition', data: 'INSIDE' }],
  meta: { sourceTag: 'ol' },
};
const cssomLi = (markerText?: string): IRComponent => ({
  id: 'cssom__cssom-pad-setter-invalid__0__0-674', name: 'cssom__cssom-pad-setter-invalid__0__0',
  properties: [
    { type: 'Width', data: { type: 'length', px: 100 } }, { type: 'Height', data: { type: 'length', px: 100 } },
  ],
  meta: markerText === undefined ? { sourceTag: 'li', role: 'ws-after' }
    : { sourceTag: 'li', role: 'ws-after', markerText },
});

describe('NodeRenderer emits the baked marker for the two shapes only', () => {
  it('armenian-008: the inside <li> paints "10000. " as a leading inline box', () => {
    const out = html(node(armenianOl, [node(armenianLi)]));
    // The INHERITED inside position + a childless item → the inline form:
    // ::marker suppressed, the ref's own `<bdi>10000. </bdi>10000` shape.
    expect(styleOf(out, armenianLi.id)).toContain('list-style-type:none');
    expect(styleOf(out, armenianLi.id)).not.toContain('list-style-type:armenian');
    expect(out).toContain('<span data-baked-marker="" style="unicode-bidi:isolate">10000. </span>10000</li>');
    // The container keeps every style it had — nothing leaks upward.
    expect(styleOf(out, armenianOl.id)).toContain('list-style-position:inside');
    expect(styleOf(out, armenianOl.id)).not.toContain('list-style-type');
  });

  it('counter-suffix: an OUTSIDE hebrew <li> takes the <string> form, no extra box', () => {
    // VERBATIM: counter-suffix's `<ol class="heb">` (no position — initial
    // `outside`) and its first `<li>` "foo", baked "א.".
    const ol: IRComponent = {
      id: 'counter-suffix__0__1-614', name: 'counter-suffix__0__1',
      properties: [{ type: 'ListStyleType', data: 'hebrew' }], meta: { sourceTag: 'ol', role: 'ws-after' },
    };
    const li: IRComponent = {
      id: 'counter-suffix__0__1__0-615', name: 'counter-suffix__0__1__0', properties: [],
      text: 'foo', meta: { sourceTag: 'li', markerText: 'א.' },
    };
    const out = html(node(ol, [node(li)]));
    // React serialises the double quotes of the CSS string as &quot;.
    expect(styleOf(out, li.id)).toBe('list-style-type:&quot;א. &quot;');
    expect(out).not.toContain('data-baked-marker');
  });

  it('cssom: the <li> takes the INHERITED author keyword and paints "001. "', () => {
    const out = html(node(cssomOl, [node(cssomLi('001.'))]));
    // Inherited `inside`, childless → the inline form.
    expect(styleOf(out, cssomLi().id)).toContain('list-style-type:none');
    expect(out).toContain('<span data-baked-marker="" style="unicode-bidi:isolate">001. </span>');
    // The <ol> still emits the author keyword itself (Chrome inherits it
    // natively; the override is per item, where the marker lives).
    expect(styleOf(out, cssomOl.id)).toContain('list-style-type:foo');
  });

  it('cssom without a baked marker is byte-identical to before', () => {
    const out = html(node(cssomOl, [node(cssomLi())]));
    expect(styleOf(out, cssomLi().id)).toBe('width:100px;height:100px');
  });

  it('control: a decimal <li> with a baked "1." keeps the keyword (Chrome is the oracle)', () => {
    const li: IRComponent = {
      id: 'dec', name: 'dec', properties: [{ type: 'ListStyleType', data: 'decimal' }],
      text: 'foo', meta: { sourceTag: 'li', markerText: '1.' },
    };
    const out = html(node({ id: 'ol', name: 'ol', properties: [], meta: { sourceTag: 'ol' } }, [node(li)]));
    expect(styleOf(out, 'dec')).toBe('list-style-type:decimal');
  });
});
