// NodeRenderer.test.tsx — the shared renderer core (issue #41), pinning
// the PURE-CSS defaults that distinguish the package from the capture
// harness skin:
//
//   - NO sizing calibration: no fit-content, no 50×30 floors, no
//     max-width cap, no empty grid/flex demotion.
//   - NO placeholder label: an empty component is an empty element.
//   - display:none stays in the DOM (with display:none), never null.
//   - meta.sourceTag is TRUSTED: replaced/interactive elements
//     (img/button/a/input) render natively; only document-breaking tags
//     (script/iframe/…) demote to <div>.
//   - text renders as a bare text node; pseudos render in CSS order.
//   - the calibration hooks (RendererOptions) actually calibrate — the
//     "two skins" contract the harness wrapper builds on.
import { describe, it, expect, vi, afterEach } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { NodeRenderer } from '../../src/renderer/NodeRenderer';
import type { ComposedNode } from '../../src/renderer/Composer';
import type { RendererOptions } from '../../src/renderer/RendererOptions';
import type { IRComponent, IRProperty } from '../../src/core/ir/IRModels';

// Helper: v2 IRComponent without the full scaffolding.
function comp(overrides: Partial<IRComponent> = {}): IRComponent {
  return { id: 'core-id', name: 'Core_Comp', properties: [], ...overrides };
}
// Helper: ComposedNode wrapper (what composeTree produces).
function node(component: IRComponent, children: ComposedNode[] = []): ComposedNode {
  return { component, children };
}
// Helper: typed property envelope + common payload shapes.
function prop(type: string, data: unknown): IRProperty {
  return { type, data };
}
const len = (px: number) => ({ type: 'length', px });
const srgb = (r: number, g: number, b: number) => ({ srgb: { r, g, b } });
// Render shorthand.
const html = (n: ComposedNode, options?: RendererOptions) =>
  renderToStaticMarkup(<NodeRenderer node={n} options={options} />);

afterEach(() => vi.restoreAllMocks());

describe('pure CSS semantics — no capture-calibrated sizing defaults', () => {
  it('an empty component renders an EMPTY element with NO synthetic styles', () => {
    // The strongest pin: exact output. No width:fit-content, no min-*
    // floors, no max-width cap, no placeholder <span>, no label text.
    expect(html(node(comp()))).toBe(
      '<div data-component-id="core-id" data-component-name="Core_Comp" class="sc-core-id"></div>',
    );
  });

  it('emits exactly what the engine produced — nothing more', () => {
    const n = node(comp({ properties: [prop('BackgroundColor', srgb(1, 0, 0))] }));
    const out = html(n);
    // The declared style is present…
    expect(out).toContain('background-color:rgba(255, 0, 0, 1)');
    // …and none of the harness defaults are.
    expect(out).not.toMatch(/fit-content|min-width|min-height|max-width/);
  });

  it('keeps display:grid on a childless container (no block demotion)', () => {
    const out = html(node(comp({ properties: [prop('Display', 'grid')] })));
    expect(out).toContain('display:grid');
  });

  it('keeps a display:none component IN the DOM (hidden, not removed)', () => {
    const out = html(node(comp({ properties: [prop('Display', 'none')] })));
    // Pure CSS: the box is visually suppressed but the node exists.
    expect(out).toContain('data-component-id="core-id"');
    expect(out).toContain('display:none');
  });
});

describe('text and children content', () => {
  it('renders text as a BARE text node (no wrapper span)', () => {
    expect(html(node(comp({ text: 'hello world' })))).toBe(
      '<div data-component-id="core-id" data-component-name="Core_Comp" class="sc-core-id">hello world</div>',
    );
  });

  it('renders mixed content: text before children, children nested in order', () => {
    const out = html(node(
      comp({ id: 'p1', text: 'lead' }),
      [node(comp({ id: 'c1' })), node(comp({ id: 'c2' }))],
    ));
    // Parent text precedes the first child; siblings keep array order.
    expect(out.indexOf('lead')).toBeLessThan(out.indexOf('data-component-id="c1"'));
    expect(out.indexOf('data-component-id="c1"')).toBeLessThan(out.indexOf('data-component-id="c2"'));
  });

  it('renders pseudos in CSS order: marker, before, text, children, after', () => {
    const out = html(node(
      comp({
        id: 'ps1',
        text: 'body',
        pseudos: {
          marker: { id: 'm', properties: [], _text: '•' },
          before: { id: 'b', properties: [], _text: 'B' },
          after: { id: 'a', properties: [], text: 'A' }, // v2 spelling tolerated
        },
      }),
      [node(comp({ id: 'kid' }))],
    ));
    const order = ['data-pseudo="marker"', 'data-pseudo="before"', 'body', 'data-component-id="kid"', 'data-pseudo="after"'];
    // Every landmark present, strictly increasing positions.
    const positions = order.map((s) => out.indexOf(s));
    expect(positions.every((p) => p >= 0)).toBe(true);
    expect([...positions].sort((x, y) => x - y)).toEqual(positions);
  });

  it('variables land as --name inline keys on the defining element', () => {
    const out = html(node(comp({ variables: { '--brand': '#123456' } })));
    expect(out).toContain('--brand:#123456');
  });
});

describe('meta.sourceTag mapping — trusted wire, denylist-only demotion', () => {
  it('renders replaced/interactive elements the harness excludes', () => {
    // <button> hosts children natively.
    expect(html(node(comp({ meta: { sourceTag: 'button' } }), [node(comp({ id: 'in-btn' }))])))
      .toMatch(/^<button\b/);
    // <a> renders as a real anchor (no href on today's wire).
    expect(html(node(comp({ meta: { sourceTag: 'a' }, text: 'link' })))).toMatch(/^<a\b/);
    // <input> is void; wire text becomes its initial value.
    const input = html(node(comp({ meta: { sourceTag: 'input' }, text: 'typed' })));
    expect(input).toMatch(/^<input\b/);
    expect(input).toContain('value="typed"');
  });

  it('renders <img> with alt from text and NO src by default (wave-9 gap)', () => {
    const out = html(node(comp({
      meta: { sourceTag: 'img' }, text: 'a kitten',
      properties: [prop('Width', len(120))],
    })));
    expect(out).toMatch(/^<img\b/);
    expect(out).toContain('alt="a kitten"');
    // The package must NOT invent image bytes — no src until the wire
    // carries one (resolveImageSource is the skin hook for that).
    expect(out).not.toContain('src=');
    // The style pipeline still applies to the replaced element.
    expect(out).toContain('width:120px');
  });

  it('resolveImageSource supplies the src when a skin provides one', () => {
    const out = html(
      node(comp({ meta: { sourceTag: 'img' } })),
      { resolveImageSource: () => 'data:image/svg+xml,x' },
    );
    expect(out).toContain('src="data:image/svg+xml,x"');
  });

  it('warns loudly and drops composed children under a void element', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const out = html(node(comp({ id: 'v1', meta: { sourceTag: 'img' } }), [node(comp({ id: 'stray' }))]));
    expect(out).not.toContain('stray');
    expect(warn).toHaveBeenCalledWith(expect.stringContaining("sourceTag 'img'"));
  });

  it('demotes document-breaking tags to <div> (denylist, not allowlist)', () => {
    for (const bad of ['script', 'style', 'iframe', 'object', 'template', 'body']) {
      expect(html(node(comp({ meta: { sourceTag: bad } })))).toMatch(/^<div\b/);
    }
  });

  it('demotes syntactically-invalid tags and passes custom elements through', () => {
    // Garbage can never reach createElement…
    expect(html(node(comp({ meta: { sourceTag: 'no soup' } })))).toMatch(/^<div\b/);
    // …but hyphenated custom elements are legal wire content.
    expect(html(node(comp({ meta: { sourceTag: 'my-widget' } })))).toMatch(/^<my-widget\b/);
  });

  it('falls back to <div> when meta is absent', () => {
    expect(html(node(comp()))).toMatch(/^<div\b/);
  });
});

describe('calibration hooks — the two-skins contract', () => {
  it('shouldRender=false suppresses the node entirely', () => {
    expect(html(node(comp()), { shouldRender: () => false })).toBe('');
  });

  it('decorateStyles reshapes the inline styles (variables still merge last)', () => {
    const out = html(
      node(comp({ variables: { '--x': '1' } })),
      { decorateStyles: (styles) => ({ width: 'fit-content', ...styles }) },
    );
    expect(out).toContain('width:fit-content');
    expect(out).toContain('--x:1');
  });

  it('renderEmptyContent substitutes childless content (placeholder slot)', () => {
    const out = html(
      node(comp()),
      { renderEmptyContent: ({ component }) => <em>{component.name}</em> },
    );
    expect(out).toContain('<em>Core_Comp</em>');
  });

  it('renderText wraps mixed-content text (harness span shape)', () => {
    const out = html(
      node(comp({ text: 'wrapped' }), [node(comp({ id: 'k' }))]),
      { renderText: (t) => <span>{t}</span> },
    );
    expect(out).toContain('<span>wrapped</span>');
  });

  it('mapTag overrides the element policy at every depth', () => {
    const out = html(
      node(comp({ meta: { sourceTag: 'button' } }), [node(comp({ id: 'k', meta: { sourceTag: 'a' } }))]),
      { mapTag: () => 'div' },
    );
    // Skin demoted both the parent button AND the child anchor.
    expect(out).not.toMatch(/<button|<a\b/);
  });

  it('forceState appends the force class next to the rule class', () => {
    const out = html(node(comp()), { forceState: 'hover' });
    expect(out).toContain('class="sc-core-id force-hover"');
  });
});
