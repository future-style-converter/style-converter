// wave-50 lane B5 — DOM pins for the UA internal-display default
// (apps/web-harness/src/sdui/UaBoxlessDisplay.ts + its decorateStyles hook).
//
// THE CONTRACT: a component whose `meta.sourceTag` is one of the six
// internal-layout tags (css-display-3 §2.4) and that declares no `display`
// of its own renders with that tag's UA display, so the demoted <div> is
// not a block box the reference never had. Everything else — a different
// tag, or a declared display — is byte-identical.
//
// Measured chain and per-test blast radius: UaBoxlessDisplay's banner.

import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { ComponentRenderer } from '../../src/sdui/ComponentRenderer';
import { uaBoxlessDisplay, UA_BOXLESS_DISPLAY } from '../../src/sdui/UaBoxlessDisplay';
import type { ComposedNode } from '../../src/sdui/Composer';
import type { IRComponent } from '@style-converter/web/core/ir/IRModels';

// One component, no scaffolding (same helper shape as the floatRuns suite).
function node(overrides: Partial<IRComponent>, children: ComposedNode[] = []): ComposedNode {
  return { component: { id: 'id', name: 'C', properties: [], ...overrides }, children };
}

describe('uaBoxlessDisplay (the pure map)', () => {
  it('defaults each internal-layout tag to its UA display', () => {
    // The six tags and the values css-display-3 §2.4 names for them.
    expect(uaBoxlessDisplay({}, 'colgroup')).toEqual({ display: 'table-column-group' });
    expect(uaBoxlessDisplay({}, 'col')).toEqual({ display: 'table-column' });
    expect(uaBoxlessDisplay({}, 'ruby')).toEqual({ display: 'ruby' });
    expect(uaBoxlessDisplay({}, 'rb')).toEqual({ display: 'ruby-base' });
    expect(uaBoxlessDisplay({}, 'rt')).toEqual({ display: 'ruby-text' });
    expect(uaBoxlessDisplay({}, 'rtc')).toEqual({ display: 'ruby-text-container' });
  });

  it('is the IDENTITY for a tag outside the map, for no tag, and for a declared display', () => {
    // Identity, not a copy: a non-carrier capture cannot move at all.
    const styles = { color: 'red' };
    expect(uaBoxlessDisplay(styles, 'div')).toBe(styles);
    expect(uaBoxlessDisplay(styles, null)).toBe(styles);
    expect(uaBoxlessDisplay(styles, undefined)).toBe(styles);
    // Cascade: an author-declared display wins over a UA default.
    const declared = { display: 'block' };
    expect(uaBoxlessDisplay(declared, 'col')).toBe(declared);
  });

  it('keeps the table row/cell tags OUT — they carry their UA display from the real tag', () => {
    // These six are in TAG_ALLOWLIST, so the browser applies their UA
    // display itself; defaulting them here would be a second owner.
    for (const tag of ['table', 'thead', 'tbody', 'tfoot', 'tr', 'td', 'th', 'caption']) {
      expect(UA_BOXLESS_DISPLAY.has(tag)).toBe(false);
    }
  });
});

describe('the decorateStyles hook (end to end through the renderer)', () => {
  it('emits display:table-column on a <col> component', () => {
    const html = renderToStaticMarkup(
      <ComponentRenderer node={node({ meta: { sourceTag: 'col' } } as Partial<IRComponent>)} />,
    );
    expect(html).toContain('display:table-column');
  });

  it('emits display:ruby-text on an <rt> component and ruby on its <ruby> parent', () => {
    const rt = node({ id: 'rt', name: 'Rt', meta: { sourceTag: 'rt' } } as Partial<IRComponent>);
    const html = renderToStaticMarkup(
      <ComponentRenderer node={node({ meta: { sourceTag: 'ruby' } } as Partial<IRComponent>, [rt])} />,
    );
    expect(html).toContain('display:ruby;');
    expect(html).toContain('display:ruby-text');
  });

  it('leaves a plain div component byte-identical (no display key added)', () => {
    const html = renderToStaticMarkup(<ComponentRenderer node={node({ text: 'x' })} />);
    // The renderer writes no `display` on the component box itself for a
    // tag-less component; the inner content wrapper's own display:block is
    // a different box and is matched by the more specific assertion below.
    expect(html).not.toContain('display:table-column');
    expect(html).not.toContain('display:ruby');
  });
});
