// Wave 52 lane L11 — DOM pins for `harnessDefaultsUnderAll` in
// apps/web-harness/src/sdui/ComponentRenderer.tsx (calibrateStyles).
//
// THE BUG THIS PINS SHUT
//
// The web runtime emits `all` as the FIRST style key (css-cascade-4 §6.4: a
// declaration that FOLLOWS `all` wins over it), and React writes style keys
// in object order. calibrateStyles spread its legacy defaults (`width:
// fit-content`, `maxWidth: 100%`) BEFORE `...styles`, so the author's own
// `width` overwrote the default IN PLACE and kept the default's slot — BEFORE
// `all` — and `all: initial` reset it. Measured on the real harness
// (tools/titan/results/wave52-all-reset-postload-colour/fixture-oracle-probe.mjs):
// fixtures/combinations/all-then-color.json ATC_AllThenProps painted 358 px
// wide instead of 160. The fix drops a default the author re-declares when
// `all` is present; all-free objects keep their historical order.
//
// The IR below is VERBATIM the converter's output for ATC_AllThenProps.
// Executed mutation (recorded in the lane's mutations.log): this file against
// HEAD's calibrateStyles → the first pin fails (`width:160px` precedes `all`).

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import type { ComposedNode } from '../../src/sdui/Composer';
import type { IRComponent, IRProperty } from '@style-converter/web/core/ir/IRModels';

/** ATC_AllThenProps, verbatim from `:converter` (`--to ir`, v2 wire). */
const ALL_THEN_PROPS: IRProperty[] = [
  { type: 'All', data: 'INITIAL' },
  { type: 'Display', data: 'BLOCK' },
  { type: 'Width', data: { type: 'length', px: 160.0 } },
  { type: 'Height', data: { type: 'length', px: 60.0 } },
  { type: 'BackgroundColor', data: { srgb: { r: 0.1803921568627451, g: 0.8, b: 0.44313725490196076 }, original: '#2ecc71' } },
];

describe('harness defaults under an author `all` (legacy capture path)', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  beforeEach(async () => {
    // No window stub → WPT_MODE false → the legacy defaults this file is about;
    // resetModules because WPT_MODE is a module-level constant read at import.
    vi.unstubAllGlobals();
    vi.resetModules();
    ComponentRenderer = (await import('../../src/sdui/ComponentRenderer')).ComponentRenderer;
  }, 60_000); // the cold engine import — same budget as ComponentRenderer.maxSizeFloor.test.tsx

  /** The inline style string React serialises for the component, in key order. */
  function styleOf(id: string, properties: IRProperty[]): string {
    const component: IRComponent = { id, name: 'ATC_AllThenProps', properties };
    const node: ComposedNode = { component, children: [] };
    const html = renderToStaticMarkup(<ComponentRenderer node={node} />);
    const m = html.match(new RegExp(`data-component-id="${id}"[^>]*style="([^"]*)"`));
    if (!m) throw new Error(`component ${id} did not render`);
    return m[1];
  }

  /** Index of a WHOLE declaration (`;`-anchored, so `min-width:160px` never matches `width:160px`); -1 if absent. */
  function at(style: string, decl: string): number {
    if (style.startsWith(decl)) return 0;            // the first declaration has no leading `;`
    const i = style.indexOf(`;${decl}`);             // every later one does
    return i < 0 ? -1 : i + 1;
  }

  it('keeps the author width AFTER `all` (ATC_AllThenProps verbatim)', () => {
    const style = styleOf('atc-1', ALL_THEN_PROPS);
    const all = at(style, 'all:initial');
    // `all` is present and every author declaration that follows it in the IR follows it here.
    expect(all).toBeGreaterThanOrEqual(0);
    expect(at(style, 'width:160px')).toBeGreaterThan(all);
    expect(at(style, 'display:block')).toBeGreaterThan(all);
    // The re-declared default is gone (it would sit before `all` and be reset anyway).
    expect(at(style, 'width:fit-content')).toBe(-1);
  });

  it('leaves an un-redeclared default before `all`, where `all` resets it as before', () => {
    const style = styleOf('atc-2', ALL_THEN_PROPS);
    // `max-width: 100%` is not declared by the author: it keeps its slot ahead of `all`.
    expect(at(style, 'max-width:100%')).toBeGreaterThanOrEqual(0);
    expect(at(style, 'max-width:100%')).toBeLessThan(at(style, 'all:initial'));
  });

  it('keeps the historical key order for an all-free component', () => {
    // Same IR minus `All`: the default's FIRST slot holds the author width, exactly as before wave 52.
    const style = styleOf('atc-3', ALL_THEN_PROPS.slice(1));
    expect(at(style, 'width:160px')).toBe(0);
    expect(at(style, 'max-width:100%')).toBeGreaterThan(0);
    expect(at(style, 'all:initial')).toBe(-1);
  });
});
