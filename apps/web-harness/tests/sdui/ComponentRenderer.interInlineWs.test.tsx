// Wave-26 lane WWS — DOM pins for the inter-INLINE whitespace separator in
// apps/web-harness/src/sdui/ComponentRenderer.tsx (HARNESS_OPTIONS
// .renderChildSeparator) + the NodeRenderer core's separator slot.
//
// THE CONTRACT:
//   Wave-20 already re-inserted a real ' ' text node between adjacent
//   WIDGET-tag siblings, unconditionally, under `?wpt=1`. Wave 26 extends
//   the same DOM-honest mirror to ANY adjacent INLINE-LEVEL pair — but
//   only in COMPOSED capture (`?wptComposed=1`), and only when the
//   extractor's `ws-after` marker (IR `meta.role`, stamped on the EARLIER
//   sibling when SOURCE whitespace separated the two) says the source
//   really had whitespace there.
//
//   MEASURED motivation: filter-effects/backdrop-filter-clip-rect-2 writes
//   three `display:inline-block` boxes one per source line; the ref
//   collapses each newline+indent to one 4.5px space advance (x = 0,
//   104.5, 209) while the flush composed canvas put them at 0, 100, 200.
//   Measured in puppeteer against the ref page under the real
//   capture-browser-ref.mjs injection (Inter @16px, line-height 1.25);
//   with the separator the composed boxes land on 0 / 104.5 / 209 exactly.
//
//   The three ways this must NOT fire are the point of the lane: no marker
//   (flush source), non-inline neighbour, and the legacy 327-pair flow —
//   whose DOM has to stay byte-identical.
//
// WPT_MODE / WPT_COMPOSED_MODE are read-once module constants, so each
// describe stubs `window.location.search` and re-imports the module — the
// pattern the wptInk / floatRuns / widgets suites established.

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import type { ComposedNode } from '../../src/sdui/Composer';
import type { IRComponent, IRProperty } from '@style-converter/web/core/ir/IRModels';

// v2 IRComponent without the scaffolding (same helper as the sibling suites).
function makeComp(overrides: Partial<IRComponent> = {}): IRComponent {
  return { id: 'test-id', name: 'Test_Comp', properties: [], ...overrides };
}

// Wrap a component into the ComposedNode shape the renderer eats.
function makeNode(component: IRComponent, children: ComposedNode[] = []): ComposedNode {
  return { component, children };
}

// Typed-IR property (the live wire shape: {"type":"Display","data":"..."}).
function prop(type: string, data: unknown): IRProperty {
  return { type, data };
}

// One `.box` from backdrop-filter-clip-rect-2. `marked` = the extractor
// found source whitespace after this box (`_role: 'ws-after'` → meta.role).
function box(id: string, marked: boolean): ComposedNode {
  return makeNode(makeComp({
    id,
    properties: [prop('Display', 'INLINE_BLOCK'), prop('Width', { type: 'length', px: 100 })],
    ...(marked ? { meta: { role: 'ws-after' } } : {}),
  }));
}

// A block-level sibling — never an inline atom, whatever the marker says.
function blockChild(id: string, marked: boolean): ComposedNode {
  return makeNode(makeComp({
    id,
    properties: [prop('Display', 'BLOCK')],
    ...(marked ? { meta: { role: 'ws-after' } } : {}),
  }));
}

describe('wave-26 WWS — composed WPT mode spaces marked inline siblings', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  beforeEach(async () => {
    // Composed capture is `?wpt=1&wptComposed=1` (what run-titan.sh sets
    // via WPT_MODE=1 WPT_COMPOSED=1); re-import so both read-once module
    // constants re-evaluate.
    vi.stubGlobal('window', { location: { search: '?wpt=1&wptComposed=1' } });
    vi.resetModules();
    const mod = await import('../../src/sdui/ComponentRenderer');
    ComponentRenderer = mod.ComponentRenderer;
  }, 30000); // Generous timeout: cold vite import can hit ~15s.

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('re-inserts one space between marked inline-block siblings', () => {
    // The backdrop-filter-clip-rect-2 row: boxes 0/1 carry the marker, box
    // 2 is last and carries none — so exactly TWO gaps get a space.
    const row = makeNode(makeComp({ id: 'row' }), [
      box('b0', true), box('b1', true), box('b2', false),
    ]);
    const html = renderToStaticMarkup(<ComponentRenderer node={row} />);
    // A real collapsed-whitespace text node between each marked pair.
    expect(html.match(/<\/div> <div/g)?.length).toBe(2);
  });

  it('keeps flush siblings flush when the source had no whitespace', () => {
    // The anti-invention pin. Same inline-block boxes, zero markers →
    // zero separators, because the source packed them together.
    const row = makeNode(makeComp({ id: 'row2' }), [
      box('c0', false), box('c1', false),
    ]);
    const html = renderToStaticMarkup(<ComponentRenderer node={row} />);
    expect(html).not.toMatch(/<\/div> <div/);
  });

  it('never spaces a block-level neighbour', () => {
    // Whitespace between block boxes paints nothing (CSS 2.1 §9.2.2.1);
    // emitting a node there would be dead DOM bytes, so the predicate
    // refuses on EITHER side being block-level.
    const mixed = makeNode(makeComp({ id: 'row3' }), [
      box('d0', true), blockChild('d1', true), box('d2', false),
    ]);
    const html = renderToStaticMarkup(<ComponentRenderer node={mixed} />);
    expect(html).not.toMatch(/<\/div> <div/);
  });

  it('falls back to the UA default display via meta.sourceTag', () => {
    // No declared display on the wire → the tag decides. <span> is
    // inline by UA default, so the marked gap is a real space advance.
    const row = makeNode(makeComp({ id: 'row4' }), [
      makeNode(makeComp({ id: 's0', meta: { sourceTag: 'span', role: 'ws-after' } })),
      makeNode(makeComp({ id: 's1', meta: { sourceTag: 'span' } })),
    ]);
    const html = renderToStaticMarkup(<ComponentRenderer node={row} />);
    expect(html).toMatch(/<\/span> <span/);
  });

  it('skips flex containers — whitespace-only children are dropped there', () => {
    // css-flexbox-1 §4: a whitespace-only text child of a flex container
    // generates no box at all, so the separator would be pure noise.
    const flexRow = makeNode(makeComp({
      id: 'row5',
      properties: [prop('Display', 'FLEX')],
    }), [box('e0', true), box('e1', false)]);
    const html = renderToStaticMarkup(<ComponentRenderer node={flexRow} />);
    expect(html).not.toMatch(/<\/div> <div/);
  });

  it('skips every whitespace-preserving container — the run is not collapsed', () => {
    // CSS Text §4.1.1: under pre / pre-wrap / break-spaces the ref shows
    // the source's own newline + indent, and under pre-line it shows a
    // segment break. One space would be a different wrong answer in all
    // four, so the hook declines rather than guess. IR keywords arrive
    // SHOUTY_SNAKE from the converter — the same normalisation the display
    // predicate applies, exercised here on the white-space axis.
    for (const kw of ['PRE', 'PRE_WRAP', 'PRE_LINE', 'BREAK_SPACES']) {
      const preRow = makeNode(makeComp({
        id: `row6-${kw}`,
        properties: [prop('WhiteSpace', kw)],
      }), [box('f0', true), box('f1', false)]);
      const html = renderToStaticMarkup(<ComponentRenderer node={preRow} />);
      expect(html, `white-space:${kw} must not gain a separator`).not.toMatch(/<\/div> <div/);
    }
  });

  it('leaves the wave-20 widget rule unconditional (no marker needed)', () => {
    // The widget gap predates the marker and its css-ui captures are
    // baselined against it — re-gating it would silently move that section.
    const row = makeNode(makeComp({ id: 'row7' }), [
      makeNode(makeComp({ id: 'w0', meta: { sourceTag: 'input', attrs: { type: 'button' } } })),
      makeNode(makeComp({ id: 'w1', meta: { sourceTag: 'input', attrs: { type: 'submit' } } })),
    ]);
    const html = renderToStaticMarkup(<ComponentRenderer node={row} />);
    expect(html).toMatch(/\/> <input/);
  });
});

describe('wave-26 WWS — the extension is composed-capture only', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  beforeEach(async () => {
    // `?wpt=1` WITHOUT `?wptComposed=1`: the per-component capture crops
    // each component on its own canvas, with no ref-page line boxes to
    // match, so the inline extension must not fire there.
    vi.stubGlobal('window', { location: { search: '?wpt=1' } });
    vi.resetModules();
    const mod = await import('../../src/sdui/ComponentRenderer');
    ComponentRenderer = mod.ComponentRenderer;
  }, 30000);

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('does not space marked inline siblings in per-component capture', () => {
    const row = makeNode(makeComp({ id: 'row8' }), [box('g0', true), box('g1', false)]);
    const html = renderToStaticMarkup(<ComponentRenderer node={row} />);
    expect(html).not.toMatch(/<\/div> <div/);
  });
});

describe('wave-26 WWS — the legacy 327-pair flow is byte-identical', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  beforeEach(async () => {
    // No `?wpt=1` at all — the dark-stage 327 baseline flow.
    vi.stubGlobal('window', { location: { search: '' } });
    vi.resetModules();
    const mod = await import('../../src/sdui/ComponentRenderer');
    ComponentRenderer = mod.ComponentRenderer;
  }, 30000);

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('emits no separator even for marked inline siblings', () => {
    // The marker can appear on any wire; the legacy flow must ignore it
    // completely so the 327 committed baselines cannot move.
    const row = makeNode(makeComp({ id: 'row9' }), [box('h0', true), box('h1', false)]);
    const html = renderToStaticMarkup(<ComponentRenderer node={row} />);
    expect(html).not.toMatch(/<\/div> <div/);
  });
});
