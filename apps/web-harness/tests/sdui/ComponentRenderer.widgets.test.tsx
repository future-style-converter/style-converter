// ComponentRenderer.widgets.test.tsx — wave-20 lane W1 (RC2): the WPT-mode
// widget passthrough in the harness skin, pinned against the LIVE wire.
//
// THE CONTRACT UNDER TEST:
//   - WPT mode (`?wpt=1`): form/widget tags (WidgetAttrs.WIDGET_TAGS) pass
//     through as REAL elements, `meta.attrs` applies (checked/type/value/
//     multiple/…), every widget carries `inert` + `tabindex="-1"` (no
//     focus rings on a capture), widget text renders BARE (no placeholder
//     span), and `appearance` CSS still reaches the element so
//     `appearance:none` suppresses native chrome exactly like a real
//     Chromium input.
//   - Legacy flow (no `?wpt=1`): the demotion divergence is untouched —
//     widgets render as <div> with the placeholder, zero attrs, zero
//     inert. The 327-pair baseline DOM must stay byte-identical.
//
// WPT_MODE is a read-once module constant, so each describe stubs
// `window.location.search` and re-imports the module — the exact pattern
// ComponentRenderer.wptInk.test.tsx established.
//
// LIVE-WIRE PINS: the select/option and checked-checkbox shapes below are
// read from the real wave19-final per-test IR
// (tools/titan/runs/wave19-final/sections/css-ui/per-test-ir/) — skip-
// guarded so hermetic checkouts without the run directory stay green. The
// `meta.attrs` payloads are byte-copies of the live converter run on the
// re-extracted appearance-checkbox-001 fixture (verified against
// schema/ir-v2.schema.json in this lane's verification pass).

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { existsSync, readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { composeTree } from '../../src/sdui/Composer';
import type { ComposedNode } from '../../src/sdui/Composer';
import type { IRComponent, IRDocument, IRProperty } from '@style-converter/web/core/ir/IRModels';

// Repo-root anchored path to the real wave19 per-test IR (the live wire).
const REPO_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..', '..', '..');
const LIVE_IR = join(
  REPO_ROOT, 'tools', 'titan', 'runs', 'wave19-final',
  'sections', 'css-ui', 'per-test-ir', 'wpt__css-ui__appearance-checkbox-001.json',
);
const liveIrReady = existsSync(LIVE_IR);

// Helpers (same shapes as the sibling suites).
function comp(overrides: Partial<IRComponent> = {}): IRComponent {
  return { id: 'wg-id', name: 'Widget_Comp', properties: [], ...overrides };
}
function node(component: IRComponent, children: ComposedNode[] = []): ComposedNode {
  return { component, children };
}
function prop(type: string, data: unknown): IRProperty {
  return { type, data };
}

describe('wave-20 W1 — WPT mode widget passthrough', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  beforeEach(async () => {
    // Stub `?wpt=1` and re-import so the read-once WPT_MODE re-evaluates.
    vi.stubGlobal('window', { location: { search: '?wpt=1' } });
    vi.resetModules();
    const mod = await import('../../src/sdui/ComponentRenderer');
    ComponentRenderer = mod.ComponentRenderer;
  }, 30000); // cold vite import can be slow — same margin as wptInk

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  const render = (n: ComposedNode) =>
    renderToStaticMarkup(<ComponentRenderer node={n} />);

  it('a checked checkbox renders as a REAL inert input (accent-color family shape)', () => {
    // accent-color-checkbox-checked-001: `<input type=checkbox checked
    // style="accent-color: red">` — attrs byte-copied from the live
    // converter run (meta.attrs rides the wire verbatim).
    const out = render(node(comp({
      properties: [prop('AccentColor', { srgb: { r: 1, g: 0, b: 0 }, original: 'red' })],
      meta: { sourceTag: 'input', attrs: { type: 'checkbox', checked: true } },
    })));
    // Real element, real chrome selector, real state…
    expect(out).toContain('<input');
    expect(out).toContain('type="checkbox"');
    expect(out).toContain('checked=""');
    // …the styled accent reaches the element…
    expect(out).toContain('accent-color');
    // …and the capture can never focus it (inert + tabindex).
    expect(out).toContain('inert=""');
    expect(out).toContain('tabindex="-1"');
  });

  it('widget text renders BARE — no placeholder span perturbing native layout', () => {
    // `<button>button</button>`: the ref centers the inline text run; a
    // display:block placeholder span would change that geometry.
    const out = render(node(comp({ text: 'button', meta: { sourceTag: 'button' } })));
    expect(out).toContain('<button');
    // The text is a direct child text node (no <span> wrapper in between).
    expect(out).toMatch(/<button[^>]*>button<\/button>/);
  });

  it('appearance:none still suppresses chrome — the CSS lands on the real input', () => {
    // css-ui-4 §7: a real Chromium input with `appearance:none` paints no
    // native chrome. Our job is only to DELIVER the declaration onto the
    // real element; the browser does the suppression natively.
    const out = render(node(comp({
      meta: { sourceTag: 'input', attrs: { type: 'checkbox' } },
      properties: [prop('Appearance', { type: 'none' })],
    })));
    expect(out).toContain('<input');
    expect(out).toMatch(/style="[^"]*appearance:none/);
  });

  it('interleaves a real space text node between consecutive inline widget siblings', () => {
    // FIX-2 (wave-20 W2 follow-up): the source markup separates every
    // control by collapsed whitespace; the flat wire dropped it, so the
    // composed widgets packed flush (zero text nodes) while the ref
    // spaced them by one ~4.16px space advance (the natives'
    // UAWidgetIntrinsics.atomGapPx pin). The renderer re-inserts ' '
    // between widget-tag neighbours — DOM-honest, browser-measured.
    const out = render(node(
      comp({ id: 'row', name: 'Row' }),
      [
        node(comp({ id: 'ta', name: 'TA', text: 'textarea', meta: { sourceTag: 'textarea' } })),
        node(comp({ id: 'ib', name: 'IB', meta: { sourceTag: 'input', attrs: { type: 'button', value: 'input-button' } } })),
        node(comp({ id: 'is', name: 'IS', meta: { sourceTag: 'input', attrs: { type: 'submit', value: 'input-submit' } } })),
      ],
    ));
    // A single collapsed-whitespace text node between each widget pair.
    expect(out).toMatch(/<\/textarea> <input/);
    expect(out).toMatch(/value="input-button"[^>]*\/> <input/);
    // FIX-1 rides the same wire: the submit label is the SOURCE value.
    expect(out).toContain('value="input-submit"');
  });

  it('no separator between a widget and a non-widget sibling', () => {
    // The gap contract is widget↔widget only — a plain block sibling
    // keeps the flush DOM (its own margins/flow own the spacing).
    const out = render(node(
      comp({ id: 'row2', name: 'Row2' }),
      [
        node(comp({ id: 'd1', name: 'D1' })),
        node(comp({ id: 'i1', name: 'I1', meta: { sourceTag: 'input', attrs: { type: 'checkbox' } } })),
      ],
    ));
    expect(out).not.toMatch(/<\/div> </);
  });

  it('LIVE WIRE: appearance-checkbox-001 composes select>option with real widget tags', () => {
    if (!liveIrReady) return; // run-dir absent on hermetic checkouts — skip
    // The REAL wave19 per-test IR (flat v2 wire) → composed forest.
    const doc = JSON.parse(readFileSync(LIVE_IR, 'utf8')) as IRDocument;
    const roots = composeTree(doc);
    const out = renderToStaticMarkup(
      <>{roots.map((r) => <ComponentRenderer key={r.component.id} node={r} />)}</>,
    );
    // The wire's widget tags all pass through as real elements.
    for (const tag of ['<a', '<button', '<input', '<textarea', '<select', '<option', '<meter', '<progress']) {
      expect(out).toContain(tag);
    }
    // The option's text content renders inside the select natively.
    expect(out).toMatch(/<option[^>]*>select<\/option>/);
    // Every widget is inert (the capture-focus suppression contract).
    const inertCount = (out.match(/inert=""/g) ?? []).length;
    expect(inertCount).toBeGreaterThanOrEqual(8);
  });
});

describe('wave-20 W1 — legacy (327-pair) flow byte-stability', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  beforeEach(async () => {
    // No window stub → WPT_MODE=false → the demotion divergence applies.
    vi.unstubAllGlobals();
    vi.resetModules();
    const mod = await import('../../src/sdui/ComponentRenderer');
    ComponentRenderer = mod.ComponentRenderer;
  }, 30000);

  afterEach(() => vi.restoreAllMocks());

  it('widgets stay demoted to <div>: no attrs, no inert, placeholder kept', () => {
    const out = renderToStaticMarkup(
      <ComponentRenderer node={node(comp({
        meta: { sourceTag: 'input', attrs: { type: 'checkbox', checked: true } },
      }))} />,
    );
    // The demotion: a <div>, never an <input>.
    expect(out).toContain('<div');
    expect(out).not.toContain('<input');
    // Zero widget attrs leak onto the div (byte-stable legacy DOM)…
    expect(out).not.toContain('type="checkbox"');
    expect(out).not.toContain('checked');
    expect(out).not.toContain('inert');
    // …and the placeholder label machinery still renders (the name label
    // block-font svg — `role="img"` with the underscore-stripped name;
    // uppercasing happens only inside the block-glyph layout, not here).
    expect(out).toContain('aria-label="Widget Comp"');
  });
});
