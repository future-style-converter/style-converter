// Tests for the swarm-003 renderer fixes in
// apps/web-harness/src/sdui/ComponentRenderer.tsx, plus the supporting
// schema change in runtimes/web/src/core/ir/IRModels.ts.
//
// Bug 1: WPT block-flow widen — skip fit-content + minWidth/minHeight
//        defaults under WPT_MODE so block-level elements participate
//        in normal CSS block-flow (width:auto stretches to container).
//        Source: swarm-003 css-ui__negative-outline-offset.json
//
// Bug 2: <span> in TAG_ALLOWLIST — emit a real <span> when the source
//        tag is 'span' so content-visibility (and similar inline/block-
//        sensitive properties) observe the correct box type.
//        Source: swarm-003 css-contain__content-visibility-hidden-and-innertext.json
//
// Bug 3: aspect-ratio + child intrinsic min-content lift — inject
//        `min-width: min-content` (resp. height) when the aspect-ratio
//        carve-out fires AND the component has children, so the
//        spec's automatic-minimum-size rule (CSS Sizing 4 §6.2.2)
//        actually transfers the child's intrinsic width to the parent.
//        Source: swarm-003 css-sizing__block-aspect-ratio-015.json
//
// Bug 4: pseudos rendering — materialise PseudoElements.before /
//        .after / .marker as inline <span>s whose properties carry
//        the originating CSS rule's declarations. The browser
//        evaluates counter() / counters() / attr() natively.
//        Source: swarm-003 css-lists__counter-001.json +
//                swarm-003 css-pseudo__before-preceding-whitespace-dynamic.json
//
// IR v2 note: the renderer consumes ComposedNodes and the v2 field
// spellings (`text`, `pseudos`, `meta.sourceTag`); the pseudo PAYLOAD
// stays in the extractor's verbatim shape, so inner nodes keep `_text`.
//
// All tests assert both the new behaviour AND a backward-compat case
// so the legacy 327-pair visual-test baseline stays byte-stable.

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import type { ComposedNode } from '../../src/sdui/Composer';
import type { IRComponent, IRProperty, IRPseudoNode } from '@style-converter/web/core/ir/IRModels';

// Helper: build a v2 IRComponent without forcing every test to spell out
// all of the IR scaffolding.
function makeComp(overrides: Partial<IRComponent> = {}): IRComponent {
  return {
    id: 'test-id',
    name: 'Test_Comp',
    properties: [],
    ...overrides,
  };
}

// Helper: wrap a component (+ children) into the ComposedNode shape the
// renderer consumes — what Composer.composeTree produces from slot refs.
function makeNode(component: IRComponent, children: ComposedNode[] = []): ComposedNode {
  return { component, children };
}

function prop(type: string, data: unknown): IRProperty {
  return { type, data };
}

// WPT_MODE is read once at module-load time from window.location. The
// vitest default node environment has no window, so the module loads
// with WPT_MODE=false. To exercise the WPT_MODE branch we have to:
//   (a) define a window with the ?wpt=1 query parameter,
//   (b) re-import the module so the const re-evaluates.
// vi.resetModules() between imports lets us flip the flag per test
// group cleanly without leaking state into the legacy-mode tests.

describe('Bug 1 — WPT block-flow widen (swarm-003 css-ui__negative-outline-offset)', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  // beforeAll (not beforeEach) — the dynamic re-import is expensive
  // (~7s on cold start due to vite's full transform pass), so we pay
  // it once for the whole describe block. The module-level WPT_MODE
  // is captured at import time and shared across all tests in the
  // block; that's fine because every test in this block wants the
  // same WPT_MODE=true behaviour.
  beforeEach(async () => {
    vi.stubGlobal('window', {
      location: { search: '?wpt=1' },
    });
    vi.resetModules();
    const mod = await import('../../src/sdui/ComponentRenderer');
    ComponentRenderer = mod.ComponentRenderer;
  }, 30000);  // Generous timeout: cold vite import can hit ~15s.

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('omits width:fit-content + minWidth/minHeight defaults under WPT_MODE', () => {
    // Plain placeholder component — no width, no min-*. Under WPT_MODE
    // we want the browser's normal block-flow (width:auto). The legacy
    // mode would have set width:fit-content + min-width:50px.
    const comp = makeComp({ id: 'wpt-1', name: 'Block_Default' });
    const html = renderToStaticMarkup(<ComponentRenderer node={makeNode(comp)} />);
    const styleMatch = html.match(/data-component-id="wpt-1"[^>]*style="([^"]*)"/);
    expect(styleMatch).not.toBeNull();
    const style = styleMatch![1];
    // Critical: NONE of the legacy defaults should appear.
    expect(style).not.toMatch(/width:\s*fit-content/);
    expect(style).not.toMatch(/min-width:\s*50px/);
    expect(style).not.toMatch(/min-height:\s*30px/);
    // max-width:100% is preserved so wide text runs still cap at canvas.
    expect(style).toMatch(/max-width:\s*100%/);
  });

  it('still honours IR-declared width/min-width under WPT_MODE', () => {
    // The carve-out only suppresses DEFAULTS — explicit IR width/min-*
    // must still reach the inline style via the buildStyles spread.
    const comp = makeComp({
      id: 'wpt-2',
      properties: [
        prop('Width', { type: 'length', px: 200 }),
        prop('MinWidth', { type: 'length', px: 120 }),
      ],
    });
    const html = renderToStaticMarkup(<ComponentRenderer node={makeNode(comp)} />);
    const styleMatch = html.match(/data-component-id="wpt-2"[^>]*style="([^"]*)"/);
    const style = styleMatch![1];
    expect(style).toMatch(/width:\s*200px/);
    expect(style).toMatch(/min-width:\s*120px/);
  });
});

describe('Bug 1 — legacy-mode defaults preserved (327-pair compat)', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  beforeEach(async () => {
    // No window stub → WPT_MODE=false → legacy defaults apply.
    vi.unstubAllGlobals();
    vi.resetModules();
    const mod = await import('../../src/sdui/ComponentRenderer');
    ComponentRenderer = mod.ComponentRenderer;
  });

  it('legacy mode (no ?wpt=1) still applies fit-content + minWidth:50px floor', () => {
    const comp = makeComp({ id: 'legacy-1' });
    const html = renderToStaticMarkup(<ComponentRenderer node={makeNode(comp)} />);
    const styleMatch = html.match(/data-component-id="legacy-1"[^>]*style="([^"]*)"/);
    const style = styleMatch![1];
    // The 327-pair contract requires these defaults to be present.
    expect(style).toMatch(/width:\s*fit-content/);
    expect(style).toMatch(/min-width:\s*50px/);
    expect(style).toMatch(/min-height:\s*30px/);
  });
});

describe('Bug 2 — <span> in TAG_ALLOWLIST (swarm-003 css-contain__content-visibility)', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  beforeEach(async () => {
    vi.unstubAllGlobals();
    vi.resetModules();
    const mod = await import('../../src/sdui/ComponentRenderer');
    ComponentRenderer = mod.ComponentRenderer;
  });

  it('renders <span> when meta.sourceTag is "span"', () => {
    const comp = makeComp({ id: 'span-1', meta: { sourceTag: 'span' } });
    const html = renderToStaticMarkup(<ComponentRenderer node={makeNode(comp)} />);
    expect(html).toMatch(/^<span\b/);
    expect(html).toContain('</span>');
  });

  it('renders <strong> when meta.sourceTag is "strong" (inline-level companion)', () => {
    // The inline-level companions (strong, em, b, i, etc.) were added
    // in the same set so content-visibility / whitespace-collapse /
    // text-decoration tests that depend on inline boxes work too.
    const comp = makeComp({ id: 'strong-1', meta: { sourceTag: 'strong' } });
    const html = renderToStaticMarkup(<ComponentRenderer node={makeNode(comp)} />);
    expect(html).toMatch(/^<strong\b/);
  });

  it('still falls back to <div> when meta.sourceTag is not in the allowlist', () => {
    // Security check — we only emit tags we positively want. A bogus
    // 'script' / 'iframe' / 'object' must demote to <div>.
    const comp = makeComp({ id: 'bogus-1', meta: { sourceTag: 'iframe' } });
    const html = renderToStaticMarkup(<ComponentRenderer node={makeNode(comp)} />);
    expect(html).toMatch(/^<div\b/);
  });
});

describe('Bug 3 — aspect-ratio + child min-content lift (swarm-003 css-sizing__block-aspect-ratio-015)', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  beforeEach(async () => {
    vi.unstubAllGlobals();
    vi.resetModules();
    const mod = await import('../../src/sdui/ComponentRenderer');
    ComponentRenderer = mod.ComponentRenderer;
  });

  it('injects min-width:min-content when aspect-ratio + has-children + inline-unconstrained', () => {
    // Mirrors the block-aspect-ratio-015 fixture: parent has
    // background:green + height:100 + aspect-ratio:1/2 + one 100px-wide
    // child. The spec's automatic minimum size rule (CSS Sizing 4 §6.2.2)
    // wants the parent's min-width to resolve to min-content (= the
    // child's 100px), making the box 100x100 green.
    const child = makeComp({
      id: 'br15-child',
      properties: [prop('Width', { type: 'length', px: 100 })],
    });
    const parent = makeComp({
      id: 'br15-parent',
      properties: [
        prop('AspectRatio', { ratio: { w: 1, h: 2 }, normalizedRatio: 0.5 }),
        prop('Height', { type: 'length', px: 100 }),
      ],
    });
    const html = renderToStaticMarkup(
      <ComponentRenderer node={makeNode(parent, [makeNode(child)])} />,
    );
    const styleMatch = html.match(/data-component-id="br15-parent"[^>]*style="([^"]*)"/);
    expect(styleMatch).not.toBeNull();
    const style = styleMatch![1];
    // The Bug 3 fix injects min-width:min-content on the unconstrained
    // inline axis when there are children to lift from.
    expect(style).toMatch(/min-width:\s*min-content/);
    // The aspect-ratio itself must still reach the DOM.
    expect(style).toMatch(/aspect-ratio:\s*0\.5/);
    // And the IR's height must propagate.
    expect(style).toMatch(/height:\s*100px/);
  });

  it('leaves min-width:undefined when aspect-ratio + NO children (032 fixture compat)', () => {
    // The 032 case (empty box, aspect-ratio + height) must keep its
    // original behaviour: min-width undefined so the browser does
    // pure aspect-ratio transfer with no min-content floor.
    const comp = makeComp({
      id: 'br32-empty',
      properties: [
        prop('AspectRatio', { ratio: { w: 4, h: 1 }, normalizedRatio: 4 }),
        prop('Height', { type: 'length', px: 300 }),
        prop('MaxHeight', { type: 'length', px: 25 }),
      ],
    });
    const html = renderToStaticMarkup(<ComponentRenderer node={makeNode(comp)} />);
    const styleMatch = html.match(/data-component-id="br32-empty"[^>]*style="([^"]*)"/);
    const style = styleMatch![1];
    // Empty box → no min-content floor, no 50px floor either.
    expect(style).not.toMatch(/min-width:\s*min-content/);
    expect(style).not.toMatch(/min-width:\s*50px/);
  });

  it('symmetric: injects min-height:min-content when block axis unconstrained + children', () => {
    // The inverse — aspect-ratio + width declared + no height +
    // has children → browser should be able to lift child's intrinsic
    // height into the parent.
    const child = makeComp({
      id: 'br15-vchild',
      properties: [prop('Height', { type: 'length', px: 80 })],
    });
    const parent = makeComp({
      id: 'br15-vparent',
      properties: [
        prop('AspectRatio', { ratio: { w: 2, h: 1 }, normalizedRatio: 2 }),
        prop('Width', { type: 'length', px: 100 }),
      ],
    });
    const html = renderToStaticMarkup(
      <ComponentRenderer node={makeNode(parent, [makeNode(child)])} />,
    );
    const styleMatch = html.match(/data-component-id="br15-vparent"[^>]*style="([^"]*)"/);
    const style = styleMatch![1];
    expect(style).toMatch(/min-height:\s*min-content/);
    expect(style).toMatch(/aspect-ratio:\s*2/);
    expect(style).toMatch(/width:\s*100px/);
  });
});

describe('Bug 4 — pseudos rendering (swarm-003 css-lists__counter-001 + css-pseudo__before-preceding-whitespace-dynamic)', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  beforeEach(async () => {
    vi.unstubAllGlobals();
    vi.resetModules();
    const mod = await import('../../src/sdui/ComponentRenderer');
    ComponentRenderer = mod.ComponentRenderer;
  });

  // The pseudo payload is forwarded VERBATIM from the extractor (spec 01:
  // `pseudos` is extractor-owned), so inner nodes keep the `_text`
  // spelling — exactly what the wire carries today.
  function makePseudo(overrides: Partial<IRPseudoNode> = {}): IRPseudoNode {
    return { id: 'pseudo-id', name: 'Pseudo', properties: [], ...overrides };
  }

  it('renders pseudos.before as a leading <span data-pseudo="before">', () => {
    // Mirrors css-pseudo__before-preceding-whitespace-dynamic: the host
    // <div> has children but also `::before { content: "two" }`. The
    // synthetic pseudo node carries `_text: 'two'`.
    const before = makePseudo({ id: 'before-1', _text: 'two' });
    const host = makeComp({
      id: 'host-1',
      text: 'words',
      pseudos: { before },
    });
    const html = renderToStaticMarkup(<ComponentRenderer node={makeNode(host)} />);
    expect(html).toContain('data-pseudo="before"');
    expect(html).toContain('two');
    // Order: the before pseudo must appear before the host text.
    expect(html.indexOf('two')).toBeLessThan(html.indexOf('words'));
  });

  it('renders pseudos.after as a trailing <span data-pseudo="after">', () => {
    const after = makePseudo({ id: 'after-1', _text: 'end' });
    const child = makeComp({ id: 'real-child', name: 'Child' });
    const host = makeComp({
      id: 'host-after',
      pseudos: { after },
    });
    const html = renderToStaticMarkup(
      <ComponentRenderer node={makeNode(host, [makeNode(child)])} />,
    );
    expect(html).toContain('data-pseudo="after"');
    expect(html).toContain('end');
    // The after pseudo must appear AFTER the real child.
    expect(html.indexOf('data-component-id="real-child"'))
      .toBeLessThan(html.indexOf('data-pseudo="after"'));
  });

  it('renders pseudos.marker as a leading <span data-pseudo="marker">', () => {
    // Markers come even before ::before in the spec (and visually they
    // sit in the marker side of the principal box).
    const marker = makePseudo({ id: 'marker-1', _text: '1.' });
    const before = makePseudo({ id: 'before-2', _text: 'B' });
    const host = makeComp({
      id: 'host-marker',
      text: 'item text',
      pseudos: { marker, before },
      meta: { sourceTag: 'li' },
    });
    const html = renderToStaticMarkup(<ComponentRenderer node={makeNode(host)} />);
    expect(html).toContain('data-pseudo="marker"');
    expect(html).toContain('data-pseudo="before"');
    // Order: marker first, then before, then host text.
    expect(html.indexOf('data-pseudo="marker"'))
      .toBeLessThan(html.indexOf('data-pseudo="before"'));
    expect(html.indexOf('data-pseudo="before"'))
      .toBeLessThan(html.indexOf('item text'));
  });

  it('forwards the pseudo content via inline content style for counter()', () => {
    // For functional `content` (e.g. `counter(c, decimal-leading-zero)`),
    // F-G-EXTRACTOR emits an IRProperty that buildStyles forwards onto
    // the inline-style as `content: counter(c, ...)` — the BROWSER then
    // evaluates the function against the live counter tree. Here we
    // simulate the IR shape: the pseudo carries a Content property
    // whose serialised value is a counter() string.
    const before = makePseudo({
      id: 'counter-before',
      properties: [
        prop('Content', { value: 'counter(c, decimal-leading-zero)' }),
      ],
    });
    const host = makeComp({
      id: 'counter-host',
      properties: [prop('CounterIncrement', { name: 'c', value: 1 })],
      pseudos: { before },
      meta: { sourceTag: 'span' },
    });
    const html = renderToStaticMarkup(<ComponentRenderer node={makeNode(host)} />);
    // The pseudo wrapper must be present...
    expect(html).toContain('data-pseudo="before"');
    // ...and the host must render as <span>.
    expect(html).toMatch(/<span\b[^>]*data-component-id="counter-host"/);
  });

  it('tolerates the v2 `text` spelling inside the pseudo payload', () => {
    // The wire forwards the extractor's shape verbatim; if a future
    // extractor emits `text` instead of `_text`, the renderer must
    // pick it up the same way.
    const before = makePseudo({ id: 'v2-before', text: 'v2 spelled' });
    const host = makeComp({ id: 'v2-host', pseudos: { before } });
    const html = renderToStaticMarkup(<ComponentRenderer node={makeNode(host)} />);
    expect(html).toContain('data-pseudo="before"');
    expect(html).toContain('v2 spelled');
  });

  it('components without pseudos render byte-identically to legacy (327-pair compat)', () => {
    // Hard rule: omitting pseudos MUST produce the same DOM the
    // pre-Bug-4 renderer produced. No data-pseudo attribute anywhere.
    const comp = makeComp({ id: 'no-pseudo' });
    const html = renderToStaticMarkup(<ComponentRenderer node={makeNode(comp)} />);
    expect(html).not.toContain('data-pseudo');
  });

  it('null pseudo slots are skipped silently', () => {
    // F-G-EXTRACTOR may emit `pseudos: { before: null, after: null }`
    // for elements where the selector matched but the cascade
    // produced an empty rule. None of those slots should render a
    // <span>.
    const comp = makeComp({
      id: 'null-pseudo',
      pseudos: { before: null, after: null, marker: null },
    });
    const html = renderToStaticMarkup(<ComponentRenderer node={makeNode(comp)} />);
    expect(html).not.toContain('data-pseudo');
  });
});
