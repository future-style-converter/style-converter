// DecorationSpans.test.tsx — wave-22 lane DECOR: the wire
// `meta.decorations` → nested decorating-box spans (DecorationSpans.ts)
// and its application inside the renderer core (NodeRenderer).
//
// WHY THIS EXISTS. The WPT extractor collapses a chain of decoration-only
// inline wrappers into ONE text component and hoists the per-box lines
// onto `meta.decorations`; the flat `text-decoration*` longhands left on
// the component are a root-wins merged SUBSET (one line, one colour).
// Before this lane the web runtime had no reader for the list, so the
// live oracle — `text-decoration-color__7`, span[underline blue] >
// span[overline gray] > span[line-through green] — rendered ONE blue
// underline where the browser-ref paints three rows in three colours
// (measured rows 219 gray / 230 green / 237 blue, see the natives'
// DecorationColorOps pin tables).
//
// Pins:
//   - the DOM SNAPSHOT: outermost-first nesting, one span per entry,
//     each with its own line + colour.
//   - the host element stops painting its own (merged) line.
//   - the run's shared style/thickness ride onto every wrapper.
//   - present-but-EMPTY is authoritative: nothing painted.
//   - non-decorated components are BYTE-IDENTICAL.
import { describe, it, expect, vi, afterEach } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { NodeRenderer } from '../../src/renderer/NodeRenderer';
import { normalizeLine, withDecorationSpans } from '../../src/renderer/DecorationSpans';
import type { ComposedNode } from '../../src/renderer/Composer';
import type { IRComponent } from '../../src/core/ir/IRModels';

// Helpers mirror NodeRenderer.test.tsx (v2 component + composed wrapper).
function comp(overrides: Partial<IRComponent> = {}): IRComponent {
  return { id: 'd-id', name: 'Decor_Comp', properties: [], ...overrides };
}
function node(component: IRComponent, children: ComposedNode[] = []): ComposedNode {
  return { component, children };
}
const html = (n: ComposedNode) => renderToStaticMarkup(<NodeRenderer node={n} />);

/** The live oracle wire (converter output for text-decoration-color__7). */
const ORACLE = [
  { line: 'underline', color: 'blue' },
  { line: 'overline', color: 'gray' },
  { line: 'line-through', color: 'green' },
];

afterEach(() => vi.restoreAllMocks());

describe('normalizeLine — the paintable keyword table', () => {
  it('accepts the CSS spelling and the IR screaming spelling', () => {
    expect(normalizeLine('underline')).toBe('underline');
    expect(normalizeLine('overline')).toBe('overline');
    expect(normalizeLine('line-through')).toBe('line-through');
    // The IR enum spelling the natives' lineKindFrom also tolerates.
    expect(normalizeLine('LINE_THROUGH')).toBe('line-through');
    expect(normalizeLine('UNDERLINE')).toBe('underline');
  });

  it('rejects the non-painting and unknown keywords', () => {
    // css-text-decor-3 §2.1: `none` paints nothing, `blink` has no visual
    // effect in any modern UA — neither may become a wrapper.
    expect(normalizeLine('none')).toBeNull();
    expect(normalizeLine('blink')).toBeNull();
    expect(normalizeLine('grand-underline')).toBeNull();
    expect(normalizeLine(undefined)).toBeNull();
  });
});

describe('withDecorationSpans — absence vs emptiness', () => {
  it('returns the content untouched with no wire and with an empty wire', () => {
    // No wire → not a collapsed run. Empty wire → authoritative "no
    // lines", and the host's text-decoration-line:none is the whole
    // effect (nothing to wrap in).
    expect(withDecorationSpans('x', undefined)).toBe('x');
    expect(withDecorationSpans('x', null)).toBe('x');
    expect(withDecorationSpans('x', [])).toBe('x');
  });
});

describe('NodeRenderer — the decorating-box stack', () => {
  it('renders the oracle chain as nested spans, outermost-first', () => {
    const out = html(node(comp({
      text: 'Black text with blue underline, gray overline and green line-through',
      meta: { decorations: ORACLE },
    })));
    // The DOM SNAPSHOT: entry 0 outermost, innermost holds the text —
    // the exact shape of the source markup the extractor collapsed.
    expect(out).toContain(
      '<span data-decoration="underline" style="text-decoration-line:underline;text-decoration-color:blue">'
      + '<span data-decoration="overline" style="text-decoration-line:overline;text-decoration-color:gray">'
      + '<span data-decoration="line-through" style="text-decoration-line:line-through;text-decoration-color:green">'
      + 'Black text with blue underline, gray overline and green line-through'
      + '</span></span></span>',
    );
  });

  it('turns the host element line OFF so the merged bag cannot double-paint', () => {
    // The component keeps the root-wins merged longhands (underline blue
    // here). Left alone it would paint the outermost entry a second time,
    // and in the run's own style — a solid wrapper over a dotted element.
    const out = html(node(comp({
      properties: [
        { type: 'TextDecorationLine', data: { underline: true } },
      ],
      text: 'run',
      meta: { decorations: [{ line: 'underline', color: 'blue' }] },
    })));
    // The host declares none…
    expect(out).toMatch(/<div [^>]*style="[^"]*text-decoration-line:none/);
    // …and the single wrapper owns the line.
    expect(out).toContain('data-decoration="underline"');
  });

  it('carries the run-level style and thickness onto every wrapper', () => {
    // style/thickness are per-RUN (the extractor folds them root-wins into
    // the merged bag), not per-entry — so each wrapper must restate them
    // or it would draw the CSS initial solid/auto.
    const out = html(node(comp({
      properties: [
        { type: 'TextDecorationStyle', data: { keyword: 'dotted' } },
        { type: 'TextDecorationThickness', data: { type: 'length', px: 10 } },
      ],
      text: 'run',
      meta: { decorations: [{ line: 'underline', color: 'blue' }, { line: 'overline' }] },
    })));
    // THREE occurrences, not two: the host keeps the engine's own
    // style/thickness declarations (inert there — its line is `none`, so
    // there is nothing for them to style) plus one per wrapper.
    expect(out.match(/text-decoration-style:dotted/g)?.length).toBe(3);
    expect(out.match(/text-decoration-thickness:10px/g)?.length).toBe(3);
    // The uncoloured entry omits the colour declaration entirely so the
    // browser applies the §2.2 initial `currentColor` itself.
    expect(out).toContain('<span data-decoration="overline" style="text-decoration-line:overline;'
      + 'text-decoration-style:dotted;text-decoration-thickness:10px">');
  });

  it('paints NOTHING for a present-but-empty wire (authoritative)', () => {
    // The natives' resolve() contract, mirrored: presence alone is
    // authoritative, so an empty list retires the merged flat bag's lines
    // instead of falling back to them.
    const out = html(node(comp({
      properties: [{ type: 'TextDecorationLine', data: { underline: true } }],
      text: 'run',
      meta: { decorations: [] },
    })));
    expect(out).toMatch(/style="[^"]*text-decoration-line:none/);
    expect(out).not.toContain('data-decoration');
  });

  it('drops an unpaintable keyword and records it, keeping the rest nested', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const out = html(node(comp({
      text: 'run',
      meta: {
        decorations: [
          { line: 'underline', color: 'blue' },
          { line: 'blink', color: 'gray' },
          { line: 'line-through' },
        ],
      },
    })));
    // Two wrappers, still outermost-first; no wrapper for `blink`.
    expect(out).toContain('<span data-decoration="underline" '
      + 'style="text-decoration-line:underline;text-decoration-color:blue">'
      + '<span data-decoration="line-through" style="text-decoration-line:line-through">run</span></span>');
    expect(out).not.toContain('blink');
    // No silent fallthrough: the drop reached the tracker's warn channel.
    expect(warn).toHaveBeenCalled();
  });

  it('leaves a component without decorations byte-identical', () => {
    // The regression guard for every one of the 327 baseline components.
    const plain = comp({ text: 'plain', meta: { sourceTag: 'p' } });
    const before = renderToStaticMarkup(<NodeRenderer node={node(plain)} />);
    expect(before).not.toContain('data-decoration');
    expect(before).not.toContain('text-decoration-line:none');
    // …and an explicitly-null wire is the same "no wire" state.
    expect(renderToStaticMarkup(
      <NodeRenderer node={node(comp({ text: 'plain', meta: { sourceTag: 'p', decorations: null } }))} />,
    )).toBe(before);
  });
});
