// Wave 21 (lane TEXTDECOR, B-RC7) — pins for the text span's
// `word-break: break-word` HARNESS default in
// apps/web-harness/src/sdui/ComponentRenderer.tsx (PlaceholderContent).
//
// THE CONTRACT: break-word is a harness nicety (keeps long placeholder
// labels inside their 327-pair cards), NOT a CSS initial value. Real CSS
// lays a run with NO soft-wrap opportunity (UAX #14 — no spaces, no
// break punctuation, no ideographs) out on ONE overflowing line. The
// Chromium browser-refs for text-decoration-dotted-001/002
// (tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/
// white-black-ink-font-lh/css-text-decor/) keep 'fooשלוםbaz' /
// 'foobarbaz' @92px Arial on a single clipped line inside the 390px
// canvas, while the hardcoded break-word wrapped the run mid-glyph on
// our side and dominated both tests' losses (per-test IR:
// tools/titan/runs/wave21-gate/sections/css-text-decor/per-test-ir/).
//
// FIX SHAPE: composed WPT capture (?wptComposed=1) drops the harness
// default and inherits the spec initial; every other path keeps it
// byte-for-byte (the 327-pair baseline + per-component `?wpt=1` inbox
// never set the flag). Native twins gate the same predicate:
// Compose softWrap (ComponentRenderer.kt effectiveSoftWrap), iOS
// fixedSize(horizontal:) (PlaceholderLabel) — both via
// DecorationOps.hasSoftWrapOpportunity.
//
// WPT_COMPOSED_MODE is a read-once module constant keyed on
// `?wptComposed=1`, so each describe block stubs window.location.search
// and re-imports the module — the swarm003 / wptInk test pattern.

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import type { ComposedNode } from '../../src/sdui/Composer';
import type { IRComponent } from '@style-converter/web/core/ir/IRModels';

// Helper: minimal v2 IRComponent (same shape as the wptInk suite's).
function makeComp(overrides: Partial<IRComponent> = {}): IRComponent {
  return {
    id: 'wrap-id',
    name: 'Wrap_Probe',
    properties: [],
    ...overrides,
  };
}

// Helper: wrap a component into the ComposedNode shape the renderer eats.
function makeNode(component: IRComponent, children: ComposedNode[] = []): ComposedNode {
  return { component, children };
}

describe('wave 21 — composed WPT capture drops the harness break-word', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  beforeEach(async () => {
    // (a) window with the composed-capture params, (b) re-import so the
    // module-level WPT_COMPOSED_MODE const re-evaluates.
    vi.stubGlobal('window', {
      location: { search: '?wpt=1&wptComposed=1' },
    });
    vi.resetModules();
    const mod = await import('../../src/sdui/ComponentRenderer');
    ComponentRenderer = mod.ComponentRenderer;
  }, 30000); // Cold vite import can hit ~15s.

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('an unbreakable run renders with NO word-break declaration', () => {
    // The dotted-001 live-IR text: no soft-wrap opportunity anywhere —
    // the ref keeps it on one overflowing line, so the span must not
    // carry the harness break-word (which would emergency-wrap it).
    const comp = makeComp({ id: 'dotted-1', text: 'fooשלוםbaz' });
    const html = renderToStaticMarkup(<ComponentRenderer node={makeNode(comp)} />);
    expect(html).not.toMatch(/word-break/);
  });
});

describe('wave 21 — every non-composed path keeps break-word byte-identical', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  beforeEach(async () => {
    // Per-component WPT inbox (`?wpt=1` WITHOUT wptComposed): the 327
    // baseline and the inbox never set the composed flag, so the span
    // must keep the harness default exactly as before.
    vi.stubGlobal('window', {
      location: { search: '?wpt=1' },
    });
    vi.resetModules();
    const mod = await import('../../src/sdui/ComponentRenderer');
    ComponentRenderer = mod.ComponentRenderer;
  }, 30000);

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('the per-component WPT path still declares break-word', () => {
    // Same unbreakable text — the gate is the MODE, not the string:
    // only composed capture compares against the browser-ref canvas.
    const comp = makeComp({ id: 'dotted-1', text: 'fooשלוםbaz' });
    const html = renderToStaticMarkup(<ComponentRenderer node={makeNode(comp)} />);
    expect(html).toMatch(/word-break:\s*break-word/);
  });
});
