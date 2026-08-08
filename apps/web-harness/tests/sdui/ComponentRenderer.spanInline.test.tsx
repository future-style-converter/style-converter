// wave-31 lane S — DOM pins for `<span>` as a REAL inline box in
// apps/web-harness/src/sdui/ComponentRenderer.tsx.
//
// wave-35 lane B4 EXTENDED these pins from `<span>` alone to the whole
// inline family the harness allowlist maps to a real inline element
// (INLINE_ALLOWLISTED_TAGS): q · strong · em · b · i · u · s · mark ·
// small · sub · sup · code · kbd · samp · var · cite · dfn · abbr · time.
// Lane S's CONTENT argument was never span-specific — only its WIRE half
// was — so the deferred-follow-up pin at the bottom of the first describe
// is now inverted, and the scope guard it carried is restated for the tags
// that really do stay on the placeholder path (div-demoted inline tags and
// block-level allowlisted tags).
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

  // ── wave-35 lane B4 — the rest of the inline family ────────────────────
  //
  // Lane S deferred <em>/<strong>/<b>/<i>/<code>… as "a SEPARATE measurable
  // change"; B4 takes it. The three pins below replace lane S's
  // deferred-follow-up pin (which asserted the OPPOSITE) and re-state its
  // scope guard for the tags that genuinely stay on the placeholder path.
  //
  // The set is INLINE_ALLOWLISTED_TAGS in ComponentRenderer.tsx — the
  // members of TAG_ALLOWLIST whose UA default display is inline. Measured
  // population across the archived bucket-A run IRs (childless components,
  // deduped by section+component, span excluded): q 123 · em 4 · code 3 ·
  // sub 1 · sup 1 · b 1 = 133 leaves.
  const INLINE_FAMILY = [
    'span', 'q',
    'strong', 'em', 'b', 'i', 'u', 's', 'mark', 'small', 'sub', 'sup',
    'code', 'kbd', 'samp', 'var', 'cite', 'dfn', 'abbr', 'time',
  ];

  it('emits bare text for every inline-family tag — no display:block wrapper', () => {
    // Same contract as <span>, one level out: an inline element's content
    // must stay inline or the line box the ref measures never forms.
    for (const tag of INLINE_FAMILY) {
      const html = renderToStaticMarkup(
        <ComponentRenderer node={makeNode(makeComp({ id: `t-${tag}`, meta: { sourceTag: tag }, text: 'X' }))} />,
      );
      expect(html, tag).not.toMatch(/display:\s*block/);
      expect(html, tag).toMatch(new RegExp(`>X</${tag}>$`));
    }
  });

  it('renders every inline-family tag as its own element — the subset pin', () => {
    // INLINE_ALLOWLISTED_TAGS must stay a SUBSET of TAG_ALLOWLIST: a tag
    // outside the allowlist maps to <div> (a BLOCK container) where the
    // placeholder wrapper is correct and load-bearing, so bare-texting it
    // would be a silent regression. Asserting the emitted element name is
    // the end-to-end version of that subset check — the two literals in
    // ComponentRenderer.tsx cannot drift apart without failing here.
    for (const tag of INLINE_FAMILY) {
      const html = renderToStaticMarkup(
        <ComponentRenderer node={makeNode(makeComp({ id: `t-${tag}`, meta: { sourceTag: tag }, text: 'X' }))} />,
      );
      expect(html, tag).toMatch(new RegExp(`^<${tag}\\b`));
    }
  });

  it('a textless inline-family element is an EMPTY inline box, not a 0-content block', () => {
    // The `hasText ? text : null` half, generalised off <span>.
    for (const tag of INLINE_FAMILY) {
      const html = renderToStaticMarkup(
        <ComponentRenderer node={makeNode(makeComp({ id: `e-${tag}`, meta: { sourceTag: tag } }))} />,
      );
      expect(html, tag).toMatch(new RegExp(`^<${tag}\\b`));
      expect(html, tag).not.toMatch(/display:\s*block/);
    }
  });

  it('leaves inline-level tags OUTSIDE the allowlist on the placeholder path', () => {
    // Scope guard, restated for the tags B4 did NOT take. `label` / `bdo` /
    // `bdi` / `rt` are inline-level on the wire but absent from
    // TAG_ALLOWLIST, so mapTag demotes them to <div> — a block container,
    // where the placeholder wrapper is the right DOM. Promoting them is a
    // tag-MAPPING change, a different measurable. (`a` is NOT in this list:
    // it is a WIDGET_TAG, so wave-20 W1 already passes it through with bare
    // text — the pin below records that, so a future WIDGET_TAGS edit that
    // dropped `a` would surface here rather than silently in a capture.)
    for (const tag of ['label', 'bdo', 'bdi', 'rt']) {
      const html = renderToStaticMarkup(
        <ComponentRenderer node={makeNode(makeComp({ id: `x-${tag}`, meta: { sourceTag: tag }, text: 'X' }))} />,
      );
      expect(html, tag).toMatch(/^<div\b/);
      expect(html, tag).toMatch(/display:\s*block/);
    }
  });

  it('<a> stays on the wave-20 WIDGET_TAGS passthrough, not the B4 set', () => {
    // Pin the boundary the comment above describes: `a` is inline-level but
    // reaches bare text through WIDGET_TAGS, which B4 left untouched.
    const html = renderToStaticMarkup(
      <ComponentRenderer node={makeNode(makeComp({ id: 'anchor', meta: { sourceTag: 'a' }, text: 'X' }))} />,
    );
    expect(html).toMatch(/^<a\b/);
    expect(html).not.toMatch(/display:\s*block/);
  });

  it('leaves BLOCK-level allowlisted tags on the placeholder path', () => {
    // The other half of the scope guard: <p>/<h1>/<li*>/<td> are
    // allowlisted but block-level, and their composed scores are frozen in
    // runs/wave34-final. `li` has its own wave-27 early return; everything
    // else here keeps the placeholder byte-for-byte.
    for (const tag of ['p', 'h1', 'td', 'blockquote', 'figcaption']) {
      const html = renderToStaticMarkup(
        <ComponentRenderer node={makeNode(makeComp({ id: `bl-${tag}`, meta: { sourceTag: tag }, text: 'X' }))} />,
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

  it('the wave-35 inline family is behind the same WPT_MODE gate', () => {
    // B4 widened the early return's TAG SET, not its mode gate. Every newly
    // covered tag must still hit the placeholder on the legacy path, or the
    // 327 committed baselines would need re-capturing.
    for (const tag of ['q', 'em', 'strong', 'b', 'i', 'code', 'sub', 'sup', 'u', 's']) {
      const html = renderToStaticMarkup(
        <ComponentRenderer node={makeNode(makeComp({ id: `l-${tag}`, meta: { sourceTag: tag }, text: 'X' }))} />,
      );
      expect(html, tag).toMatch(/display:\s*block/);
    }
  });
});
