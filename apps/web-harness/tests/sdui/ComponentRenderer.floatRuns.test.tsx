// Wave-19 lane FLOAT — DOM pins for the planChildRuns skin hook in
// apps/web-harness/src/sdui/ComponentRenderer.tsx (HARNESS_OPTIONS) +
// the NodeRenderer core's run-wrapping branch.
//
// THE CONTRACT (lane pin table P4/P6/P8): under `?wpt=1` a block parent
// whose children contain ≥2 consecutive left-floating siblings renders
// each run inside a `display:flow-root; width:max-content` wrapper
// (<div data-float-run>), with the br line-box strut (min-height:20px)
// only on `<br clear>`-terminated runs — so float rows break ONLY at
// the clear markers, matching the browser-ref's body-wide containing
// block instead of the captured IR's synthetic 100px root frames.
// WITHOUT `?wpt=1` (the legacy 327-pair flow) the DOM must stay
// byte-identical: direct siblings, no wrapper anywhere.
//
// WPT_MODE is a read-once module constant keyed on `?wpt=1`, so each
// describe block stubs `window.location.search` and re-imports the
// module — the exact pattern the wptInk/swarm003 suites established.

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import type { ComposedNode } from '../../src/sdui/Composer';
import type { IRComponent, IRProperty } from '@style-converter/web/core/ir/IRModels';

// Helper: build a v2 IRComponent without the scaffolding (same shape as
// the wptInk suite's helper).
function makeComp(overrides: Partial<IRComponent> = {}): IRComponent {
  return { id: 'test-id', name: 'Test_Comp', properties: [], ...overrides };
}

// Helper: wrap a component into the ComposedNode shape the renderer eats.
function makeNode(component: IRComponent, children: ComposedNode[] = []): ComposedNode {
  return { component, children };
}

// Typed-IR property (the live wire shape: {"type":"Float","data":"LEFT"}).
function prop(type: string, data: unknown): IRProperty {
  return { type, data };
}

// A left-floating child mirroring the justify-self-001 yellow boxes.
function floatChild(id: string): ComposedNode {
  return makeNode(makeComp({
    id,
    properties: [prop('Float', 'LEFT'), prop('Width', { type: 'length', px: 16 })],
  }));
}

// The `<br clear:both>` marker — childless, Clear only (pin P2).
function brChild(id: string): ComposedNode {
  return makeNode(makeComp({ id, properties: [prop('Clear', 'BOTH')] }));
}

describe('wave-19 float runs — WPT mode wraps clear-delimited runs', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  beforeEach(async () => {
    // (a) window with ?wpt=1, (b) re-import so the module-level WPT_MODE
    // const re-evaluates (read-once pattern — see the wptInk suite).
    vi.stubGlobal('window', { location: { search: '?wpt=1' } });
    vi.resetModules();
    const mod = await import('../../src/sdui/ComponentRenderer');
    ComponentRenderer = mod.ComponentRenderer;
  }, 30000); // Generous timeout: cold vite import can hit ~15s.

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('wraps a br-terminated run in a strutted flow-root max-content div', () => {
    // Two floats + the br marker — the smallest strutted run (pin P6).
    const parent = makeNode(makeComp({ id: 'root' }), [
      floatChild('f1'), floatChild('f2'), brChild('br1'),
    ]);
    const html = renderToStaticMarkup(<ComponentRenderer node={parent} />);
    // One run wrapper appears…
    expect(html).toContain('data-float-run');
    // …carrying the BFC + max-content containing block (pin P4)…
    expect(html).toMatch(/display:\s*flow-root/);
    expect(html).toMatch(/width:\s*max-content/);
    // …with the 20px br line-box strut (pin P6).
    expect(html).toMatch(/min-height:\s*20px/);
    // Both floats render INSIDE the wrapper, the br after it.
    expect(html.indexOf('data-float-run')).toBeLessThan(html.indexOf('"f1"'));
  });

  it('leaves an un-terminated run without the strut (grid abspos shape)', () => {
    // The descendant-static-position-001 green+grey pair: no trailing br
    // → the wrapper exists but reports its bare float height (pin P6).
    const parent = makeNode(makeComp({ id: 'root' }), [
      floatChild('green'), floatChild('grey'),
    ]);
    const html = renderToStaticMarkup(<ComponentRenderer node={parent} />);
    expect(html).toContain('data-float-run');
    expect(html).not.toMatch(/min-height:\s*20px/);
  });

  it('never wraps a lone float (F4 conservatism)', () => {
    // One float + one plain sibling — today's block path, no wrapper.
    const parent = makeNode(makeComp({ id: 'root' }), [
      floatChild('lone'), makeNode(makeComp({ id: 'plain' })),
    ]);
    const html = renderToStaticMarkup(<ComponentRenderer node={parent} />);
    expect(html).not.toContain('data-float-run');
  });

  it('never wraps flex container children (floats have no effect on items)', () => {
    // css-flexbox-1 §3: float does not apply to flex items — the block-
    // flow-only gate must keep flex parents untouched.
    const parent = makeNode(
      makeComp({ id: 'root', properties: [prop('Display', 'FLEX')] }),
      [floatChild('f1'), floatChild('f2')],
    );
    const html = renderToStaticMarkup(<ComponentRenderer node={parent} />);
    expect(html).not.toContain('data-float-run');
  });
});

describe('wave-19 float runs — legacy flow (no ?wpt=1) is byte-identical', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  beforeEach(async () => {
    // No `?wpt=1` — the 327-pair baseline path (pin P8).
    vi.stubGlobal('window', { location: { search: '' } });
    vi.resetModules();
    const mod = await import('../../src/sdui/ComponentRenderer');
    ComponentRenderer = mod.ComponentRenderer;
  }, 30000);

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders float children as direct siblings with no wrapper', () => {
    // The exact shape that wraps under WPT mode must NOT wrap here.
    const parent = makeNode(makeComp({ id: 'root' }), [
      floatChild('f1'), floatChild('f2'), brChild('br1'),
    ]);
    const html = renderToStaticMarkup(<ComponentRenderer node={parent} />);
    // No synthetic box of any kind — the dark-stage DOM is frozen.
    expect(html).not.toContain('data-float-run');
    expect(html).not.toMatch(/flow-root/);
  });
});
