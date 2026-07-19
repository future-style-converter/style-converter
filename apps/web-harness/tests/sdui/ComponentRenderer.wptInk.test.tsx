// Tests for the corpus-v4.1 BLACK default-ink sub-boundary in
// apps/web-harness/src/sdui/ComponentRenderer.tsx (PlaceholderContent).
//
// THE CONTRACT: real WPT pages paint default prose in the UA
// `color: CanvasText` BLACK; through corpus-v4.0 the placeholder span's
// colorless bottom-out was the near-white rgba(237,237,237,0.7), which
// vanished into the v4 white canvas exactly like the ref injection's old
// `color:#fff` — so default-ink text tests matched VACUOUSLY (neither
// side of the diff showed the text). From v4.1 the WPT_MODE bottom-out is
// opaque #000000, in lock-step with the ref injection
// (tools/titan/capture-browser-ref.mjs `:where(body) { color:#000 }`) and
// the native WPT-mode bottom-outs (Compose WPT_DEFAULT_TEXT_INK, SwiftUI
// WPTCanvas.textInk). The dark-stage luminance contrast pick must stay
// byte-identical for the 327-pair baseline path.
//
// WPT_MODE is a read-once module constant keyed on `?wpt=1`, so each
// describe block stubs `window.location.search` and re-imports the module
// — the exact pattern ComponentRenderer.swarm003.test.tsx established.

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import type { ComposedNode } from '../../src/sdui/Composer';
import type { IRComponent, IRProperty } from '@style-converter/web/core/ir/IRModels';

// Helper: build a v2 IRComponent without spelling out all the scaffolding
// (same shape as the swarm003 suite's helper).
function makeComp(overrides: Partial<IRComponent> = {}): IRComponent {
  return {
    id: 'test-id',
    name: 'Test_Comp',
    properties: [],
    ...overrides,
  };
}

// Helper: wrap a component into the ComposedNode shape the renderer eats.
function makeNode(component: IRComponent, children: ComposedNode[] = []): ComposedNode {
  return { component, children };
}

function prop(type: string, data: unknown): IRProperty {
  return { type, data };
}

describe('corpus-v4.1 — WPT_MODE default prose ink is spec black', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  beforeEach(async () => {
    // (a) window with ?wpt=1, (b) re-import so the module-level WPT_MODE
    // const re-evaluates — see the swarm003 suite for the rationale.
    vi.stubGlobal('window', {
      location: { search: '?wpt=1' },
    });
    vi.resetModules();
    const mod = await import('../../src/sdui/ComponentRenderer');
    ComponentRenderer = mod.ComponentRenderer;
  }, 30000); // Generous timeout: cold vite import can hit ~15s.

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('colorless real text renders in opaque #000000 (not the near-white pick)', () => {
    // A text-bearing component with NO Color property and NO background —
    // the exact default-ink prose shape every WPT text test exercises. The
    // v4.0 bottom-out (no bg → light branch rgba(237,237,237,0.7)) was
    // invisible on the white canvas; v4.1 must paint the UA black.
    const comp = makeComp({ id: 'ink-1', text: 'Filler text of the page.' });
    const html = renderToStaticMarkup(<ComponentRenderer node={makeNode(comp)} />);
    // The placeholder span carries the WPT black ink…
    expect(html).toMatch(/color:\s*#000000/);
    // …and the near-white fallback is nowhere in the render.
    expect(html).not.toMatch(/rgba\(237,\s*237,\s*237/);
  });

  it('an explicit IR color still wins over the WPT default ink', () => {
    // Author color beats the UA default — the span inherits the
    // container's inline color instead of restating the black bottom-out
    // (mirrors the ref, where any author rule beats the zero-specificity
    // `:where(body)` injection).
    const comp = makeComp({
      id: 'ink-2',
      text: 'Colored text',
      properties: [prop('Color', { srgb: { r: 0.906, g: 0.298, b: 0.235 }, original: '#e74c3c' })],
    });
    const html = renderToStaticMarkup(<ComponentRenderer node={makeNode(comp)} />);
    // The span defers to the container's cascade (explicitColor → inherit)…
    expect(html).toMatch(/color:\s*inherit/);
    // …and never pins the black bottom-out over the author color.
    expect(html).not.toMatch(/color:\s*#000000/);
  });
});

describe('corpus-v4.1 — legacy (327-pair) contrast pick preserved', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  beforeEach(async () => {
    // No window stub → WPT_MODE=false → the dark-stage contract applies.
    vi.unstubAllGlobals();
    vi.resetModules();
    const mod = await import('../../src/sdui/ComponentRenderer');
    ComponentRenderer = mod.ComponentRenderer;
  });

  it('colorless text keeps the near-white no-bg fallback (baseline contract)', () => {
    // The committed 327-pair baselines were captured with the luminance
    // pick — the ink flip is WPT-mode-only, so this side must not move.
    const comp = makeComp({ id: 'legacy-ink-1', text: 'Filler text of the page.' });
    const html = renderToStaticMarkup(<ComponentRenderer node={makeNode(comp)} />);
    // No-bg fallback = the light branch, exactly as before v4.1…
    expect(html).toMatch(/rgba\(237,\s*237,\s*237/);
    // …and never the WPT black.
    expect(html).not.toMatch(/color:\s*#000000/);
  });
});
