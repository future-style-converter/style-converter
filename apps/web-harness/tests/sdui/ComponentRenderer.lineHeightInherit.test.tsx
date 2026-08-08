// Wave 36 (lane M4) — pins for the composed-mode line-height default in
// apps/web-harness/src/sdui/ComponentRenderer.tsx (PlaceholderContent).
//
// THE CONTRACT: `line-height` is an INHERITED property, and the browser-ref
// pins the harness default at ZERO specificity on `:where(html)`
// (tools/titan/capture-browser-ref.mjs REF_LINE_HEIGHT) precisely so an
// author declaration one level up wins for every descendant. The v4.1 cut
// re-stated the same literal ('1.25') on the innermost placeholder span, and
// an inline re-statement can never lose — so every ancestor-declared
// line-height in the corpus was silently clobbered.
//
// MEASURED (wave-35 full-corpus web map,
// tools/titan/runs/wave35-webmap/sections/css-overflow):
// css/css-overflow/line-clamp/line-clamp-009 puts `font: 16px/32px serif` on
// the `.clamp` component and its text in a CHILD component with no
// declaration of its own. The clamp COUNT (4) and the ellipsis were already
// right; the yellow box measured 80px (4 x 20) against the ref's 128px
// (4 x 32). 53 of the section's 57 failing line-clamp cells declare a
// line-height, 28 of them with no `line-clamp: auto` involvement at all.
//
// FIX SHAPE: the span still declares line-height, but as `inherit` — the
// ancestor component's inline value when there is one, otherwise
// index.html's `body.wpt-composed-mode #root { line-height: 1.25 }` stage
// rule, i.e. the same unitless ref number. Bare text is unchanged.
//
// WPT_COMPOSED_MODE is a read-once module constant keyed on
// `?wptComposed=1`, so each describe block stubs window.location.search and
// re-imports the module — the swarm003 / wptInk / textdecorWrap pattern.

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import type { ComposedNode } from '../../src/sdui/Composer';
import type { IRComponent, IRProperty } from '@style-converter/web/core/ir/IRModels';

function makeComp(overrides: Partial<IRComponent> = {}): IRComponent {
  return { id: 'lh-id', name: 'Lh_Probe', properties: [], ...overrides };
}
function makeNode(component: IRComponent, children: ComposedNode[] = []): ComposedNode {
  return { component, children };
}
function prop(type: string, data: unknown): IRProperty {
  return { type, data } as IRProperty;
}

// The exact `LineHeight` leaf the Kotlin reader emits for `font: 16px/32px`
// (transcribed from the wave-35 css-overflow IR).
const LH_32PX = prop('LineHeight', { original: { type: 'length', px: 32 } });

describe('wave 36 — composed placeholder inherits an ancestor line-height', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  beforeEach(async () => {
    vi.stubGlobal('window', { location: { search: '?wpt=1&wptComposed=1' } });
    vi.resetModules();
    const mod = await import('../../src/sdui/ComponentRenderer');
    ComponentRenderer = mod.ComponentRenderer;
  }, 30000);

  afterEach(() => { vi.unstubAllGlobals(); });

  it('a text component with NO line-height declares inherit, not a literal', () => {
    // This is the line-clamp-009 child: WhiteSpace only, text, no LineHeight.
    const child = makeComp({ id: 'lh-child', text: 'Line 1' });
    const html = renderToStaticMarkup(<ComponentRenderer node={makeNode(child)} />);
    expect(html).toMatch(/line-height:\s*inherit/);
    // The clobbering literal must be gone from the span.
    expect(html).not.toMatch(/line-height:\s*1\.25/);
  });

  it('the ancestor keeps its own declared line-height inline', () => {
    // The .clamp component itself: its declaration must still reach the DOM,
    // because `inherit` on the descendant span is only worth anything if the
    // ancestor actually carries the value.
    const parent = makeComp({ id: 'lh-parent', properties: [LH_32PX] });
    const child = makeComp({ id: 'lh-child', text: 'Line 1' });
    const html = renderToStaticMarkup(
      <ComponentRenderer node={makeNode(parent, [makeNode(child)])} />,
    );
    expect(html).toMatch(/line-height:\s*32px/);
    expect(html).toMatch(/line-height:\s*inherit/);
  });

  it('a component that declares its own line-height still wins over inherit', () => {
    const own = makeComp({ id: 'lh-own', text: 'Line 1', properties: [LH_32PX] });
    const html = renderToStaticMarkup(<ComponentRenderer node={makeNode(own)} />);
    expect(html).toMatch(/line-height:\s*32px/);
  });
});

describe('wave 36 — non-composed paths are untouched', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  beforeEach(async () => {
    // Per-component `?wpt=1` inbox — WPT_COMPOSED_MODE is false here, so the
    // span must declare NO line-height at all (exactly as before wave-36).
    vi.stubGlobal('window', { location: { search: '?wpt=1' } });
    vi.resetModules();
    const mod = await import('../../src/sdui/ComponentRenderer');
    ComponentRenderer = mod.ComponentRenderer;
  }, 30000);

  afterEach(() => { vi.unstubAllGlobals(); });

  it('the per-component wpt path emits no placeholder line-height', () => {
    const child = makeComp({ id: 'lh-child', text: 'Line 1' });
    const html = renderToStaticMarkup(<ComponentRenderer node={makeNode(child)} />);
    expect(html).not.toMatch(/line-height:\s*inherit/);
    expect(html).not.toMatch(/line-height:\s*1\.25/);
  });
});
