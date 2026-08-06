// wave-31 lane S — DOM pins for `<span>` as a REAL inline box in
// apps/web-harness/src/sdui/ComponentRenderer.tsx.
//
// THE CONTRACT (two halves, both required — either alone is a no-op):
//
//   1. WIRE — tools/titan/extract-fixture.mjs stopped treating `span` as a
//      generic wrapper this wave, so a span that SURVIVES the inline merge
//      now carries `_tag: 'span'` → `meta.sourceTag`. TAG_ALLOWLIST has
//      listed 'span' since wave-26 (the CSS-Containment-2 "Bug 2" banner),
//      but the entry was dead code: the wire never carried one.
//
//   2. CONTENT — mapping the element to <span> is not enough. The
//      placeholder path wraps a childless component's text in a
//      `display: block` span, and a block box inside an inline box splits
//      that inline into anonymous block boxes (CSS 2.1 §9.2.1.1) — the
//      element was inline, its content was not, and the line box the test
//      measures never formed. Under composed WPT capture a span's text is
//      therefore emitted as a BARE TEXT NODE, the source markup shape.
//
// Measured population (wave-31 lane S corpus probe): 1488 components across
// 377 fixture files gained `_tag: 'span'`; on CSS2 bucket-A it is 382
// components over 90 of 287 tests.
//
// WPT_MODE is a read-once module constant keyed on `?wpt=1`, so each
// describe stubs `window.location.search` and re-imports the module — the
// pattern the wptInk / swarm003 suites established.

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import type { ComposedNode } from '../../src/sdui/Composer';
import type { IRComponent } from '@style-converter/web/core/ir/IRModels';

// v2 IRComponent without the scaffolding (same helper as the sibling suites).
function makeComp(overrides: Partial<IRComponent> = {}): IRComponent {
  return { id: 'test-id', name: 'Test_Comp', properties: [], ...overrides };
}

// Wrap a component into the ComposedNode shape the renderer eats.
function makeNode(component: IRComponent, children: ComposedNode[] = []): ComposedNode {
  return { component, children };
}

describe('lane S — composed WPT capture renders a span as an inline box', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  beforeEach(async () => {
    vi.stubGlobal('window', { location: { search: '?wpt=1&wptComposed=1' } });
    vi.resetModules();
    const mod = await import('../../src/sdui/ComponentRenderer');
    ComponentRenderer = mod.ComponentRenderer;
  }, 30000); // Generous timeout: cold vite import can hit ~15s.

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('maps meta.sourceTag "span" to a <span> element', () => {
    // Half 1: the allowlist entry finally receives a wire value.
    const html = renderToStaticMarkup(
      <ComponentRenderer node={makeNode(makeComp({ id: 'sp', meta: { sourceTag: 'span' }, text: 'X' }))} />,
    );
    expect(html).toMatch(/^<span\b/);
  });

  it('emits a span\'s text as a bare text node — no display:block wrapper', () => {
    // Half 2: the content must stay inline or half 1 buys nothing. The
    // CSS2/abspos/static-inside-inline-001 `<span id=inline>` shape.
    const html = renderToStaticMarkup(
      <ComponentRenderer node={makeNode(makeComp({ id: 'sp', meta: { sourceTag: 'span' }, text: 'X' }))} />,
    );
    expect(html).not.toMatch(/display:\s*block/);
    expect(html).toMatch(/>X<\/span>$/);
  });

  it('a textless span renders as an EMPTY inline box, not a 0-content block', () => {
    // `<span></span>` in the source is an empty inline box; the placeholder
    // emitted a display:block span there, which broke the line just as hard
    // as a text one. Mirrors the wave-27 `li` rule (`hasText ? text : null`).
    const html = renderToStaticMarkup(
      <ComponentRenderer node={makeNode(makeComp({ id: 'sp', meta: { sourceTag: 'span' } }))} />,
    );
    expect(html).toMatch(/^<span\b/);
    expect(html).not.toMatch(/display:\s*block/);
  });

  it('leaves untagged (div) components on the placeholder path', () => {
    // Scope pin: only a span-tagged component changed. A component with no
    // sourceTag is the IR's default block container and keeps the exact
    // placeholder DOM every other composed test was captured against.
    const html = renderToStaticMarkup(
      <ComponentRenderer node={makeNode(makeComp({ id: 'dv', text: 'X' }))} />,
    );
    expect(html).toMatch(/^<div\b/);
    expect(html).toMatch(/display:\s*block/);
  });

  it('leaves the other inline tags on the placeholder path (deferred follow-up)', () => {
    // <em>/<strong>/<b>/<i>/<code> have carried their tags since wave-1 and
    // their composed scores are frozen in runs/wave30-final. Giving them the
    // same bare-text treatment is the right follow-up but a SEPARATE
    // measurable change — this pin states that lane S did not take it.
    for (const tag of ['em', 'strong', 'b', 'i', 'code']) {
      const html = renderToStaticMarkup(
        <ComponentRenderer node={makeNode(makeComp({ id: `t-${tag}`, meta: { sourceTag: tag }, text: 'X' }))} />,
      );
      expect(html, tag).toMatch(new RegExp(`^<${tag}\\b`));
      expect(html, tag).toMatch(/display:\s*block/);
    }
  });
});

describe('lane S — the legacy 327-pair path is untouched', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  beforeEach(async () => {
    // No window stub → WPT_MODE=false → the committed-baseline contract.
    vi.unstubAllGlobals();
    vi.resetModules();
    const mod = await import('../../src/sdui/ComponentRenderer');
    ComponentRenderer = mod.ComponentRenderer;
  });

  it('a span-tagged component keeps the placeholder span off the WPT path', () => {
    // The 327 committed captures never set `?wpt=1`; the span early return
    // is WPT_MODE-gated, so their DOM stays byte-identical.
    const html = renderToStaticMarkup(
      <ComponentRenderer node={makeNode(makeComp({ id: 'sp', meta: { sourceTag: 'span' }, text: 'X' }))} />,
    );
    expect(html).toMatch(/display:\s*block/);
  });
});
