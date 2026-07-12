// @vitest-environment jsdom
//
// CrashContainment.test.tsx — pins for the three defenses added after the
// 2026-07-12 WPT smoke zero-out: a single component carrying the
// extractor's RAW-MAP `pseudos` payload crashed buildStyles ("properties
// is not iterable"), React unmounted the whole capture page, the
// data-capture-ready sentinel never rendered, and 100 tests reported
// no-data. Three layers, each pinned here:
//
//   1. buildStyles: non-array payload → loud skip, never a throw.
//   2. renderPseudoNode: the raw declarations map (spec 01 —
//      extractor-owned, forwarded verbatim) BRIDGES to inline styles.
//   3. RootErrorBoundary: a component that still manages to throw fails
//      alone as a visible data-render-error box; siblings keep rendering.
//      (Boundaries only run in CLIENT rendering — SSR rethrows by design,
//      hence the jsdom + createRoot pattern from DocumentRenderer.test.tsx.)
import { describe, it, expect, vi, afterEach } from 'vitest';
import { createElement } from 'react';
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { renderToStaticMarkup } from 'react-dom/server';
import { NodeRenderer, styleFromRawDeclarations } from '../../src/renderer/NodeRenderer';
import { RootErrorBoundary } from '../../src/renderer/RootErrorBoundary';
import { buildStyles } from '../../src/core/renderer/StyleBuilder';
import type { ComposedNode } from '../../src/renderer/Composer';
import type { IRComponent, IRProperty } from '../../src/core/ir/IRModels';

// React 19 requires the act-environment opt-in for client-side act().
(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

// Helper: minimal v2 component / composed node (NodeRenderer.test.tsx pattern).
function comp(overrides: Partial<IRComponent> = {}): IRComponent {
  return { id: 'crash-id', name: 'Crash_Comp', properties: [], ...overrides };
}
function node(component: IRComponent, children: ComposedNode[] = []): ComposedNode {
  return { component, children };
}

// Client-mount plumbing (DocumentRenderer.test.tsx pattern).
let root: Root | null = null;
let container: HTMLElement | null = null;
function mount(el: React.ReactElement): HTMLElement {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
  act(() => root!.render(el));
  return container;
}

afterEach(() => {
  vi.restoreAllMocks();
  if (root) act(() => root!.unmount());
  container?.remove();
  root = null;
  container = null;
});

describe('buildStyles malformed-payload guard', () => {
  it('returns {} (with a warning) for a raw object instead of throwing', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    // The exact shape the WPT extractor puts in pseudos.<slot>.properties.
    const raw = { display: 'block', background: 'green' } as unknown as IRProperty[];
    expect(buildStyles(raw)).toEqual({});
    expect(warn).toHaveBeenCalledOnce();
  });

  it('returns {} for null/undefined/string payloads', () => {
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    for (const bad of [null, undefined, 'display:block', 42]) {
      expect(buildStyles(bad as unknown as IRProperty[])).toEqual({});
    }
  });
});

describe('styleFromRawDeclarations', () => {
  it('camelises kebab-case keys and keeps values verbatim', () => {
    expect(styleFromRawDeclarations({ 'font-weight': 'bold', width: '100px' }))
      .toEqual({ fontWeight: 'bold', width: '100px' });
  });

  it('passes custom properties through untouched and capitalises vendor prefixes', () => {
    expect(styleFromRawDeclarations({ '--brand': 'red', '-webkit-mask': 'none' }))
      .toEqual({ '--brand': 'red', WebkitMask: 'none' });
  });

  it('drops non-primitive values loudly', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    expect(styleFromRawDeclarations({ nested: { not: 'a declaration' } as unknown as string }))
      .toEqual({});
    expect(warn).toHaveBeenCalledOnce();
  });
});

describe('pseudo raw-map bridge (the css-scoping-shadow-host shape)', () => {
  it('renders raw-map ::before/::after styles inline instead of crashing', () => {
    // Verbatim shape from fixtures/wpt/css-shadow/
    // css-scoping-shadow-host-with-before-after.json after conversion.
    const c = comp({
      pseudos: {
        before: { properties: { display: 'block', width: '100px', height: '25px', background: 'green' } },
        after: { properties: { display: 'block', background: 'green' } },
      },
    });
    const html = renderToStaticMarkup(createElement(NodeRenderer, { node: node(c) }));
    // Both pseudo spans render, styles bridged to inline CSS.
    expect(html).toContain('data-pseudo="before"');
    expect(html).toContain('data-pseudo="after"');
    expect(html).toContain('display:block');
    expect(html).toContain('background:green');
  });

  it('still routes typed IRProperty lists through the engine', () => {
    // Real converter payload shape for Width (spec 02 length):
    // {"type":"length","px":50}.
    const c = comp({
      pseudos: {
        before: {
          properties: [{ type: 'Width', data: { type: 'length', px: 50 } }] as IRProperty[],
          _text: 'x',
        },
      },
    });
    const html = renderToStaticMarkup(createElement(NodeRenderer, { node: node(c) }));
    expect(html).toContain('data-pseudo="before"');
    expect(html).toContain('width:50px');
  });
});

describe('RootErrorBoundary containment (client render — boundaries are inert in SSR)', () => {
  // A child that always throws in render — stands in for any future
  // malformed-wire crash the data guards don't anticipate.
  function Thrower(): never {
    throw new Error('boom: synthetic render crash');
  }

  it('renders the visible data-render-error fallback instead of propagating', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    const el = mount(
      createElement(RootErrorBoundary, { componentId: 'bad-root' },
        createElement(Thrower)),
    );
    const fallback = el.querySelector('[data-render-error]');
    expect(fallback).not.toBeNull();
    expect(fallback!.getAttribute('data-component-id')).toBe('bad-root');
  });

  it('adds zero DOM of its own when the subtree renders cleanly (parity-safe)', () => {
    const inner = createElement(NodeRenderer, { node: node(comp()) });
    const bare = renderToStaticMarkup(inner);
    const wrapped = renderToStaticMarkup(
      createElement(RootErrorBoundary, { componentId: 'ok-root' }, inner),
    );
    expect(wrapped).toBe(bare);
  });

  it('a crashing sibling does not take down the healthy one — the capture-ready analogue', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    const el = mount(createElement('div', null,
      createElement(RootErrorBoundary, { key: 'a', componentId: 'bad' }, createElement(Thrower)),
      createElement(RootErrorBoundary, { key: 'b', componentId: 'good' },
        createElement(NodeRenderer, { node: node(comp({ id: 'good', name: 'Good' })) })),
      // The sentinel the whole defense exists to protect.
      createElement('div', { 'data-capture-ready': 2 }),
    ));
    expect(el.querySelector('[data-render-error]')).not.toBeNull();
    expect(el.querySelector('[data-component-id="good"]')).not.toBeNull();
    expect(el.querySelector('[data-capture-ready]')).not.toBeNull();
  });
});
