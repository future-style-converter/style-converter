// InlineRuns.test.tsx — wave-32 lane R: the wire `meta.runs` → the ordered
// inline content walk (InlineRuns.ts) and its application inside the
// renderer core (NodeRenderer).
//
// WHY THIS EXISTS. `text` is ONE string and the flat list has ONE sibling
// order, so the wire could say `run + children` or `children + run` but
// never `run / child / run`. The moment a kept child sits BETWEEN two text
// nodes the reading order the browser paints is not the order we paint —
// through wave-31 the extractor said so out loud (`inline-run-reordered`,
// 1,203 components / 472 fixtures) and shipped the concatenation.
//
// The canonical victim is CSS2/abspos/static-inside-inline-001:
//   <span id=inline><div id=abspos></div> X </span>
// The div is styled, so it survives as a child; 'X' landed in the span's
// `_text` and painted FIRST. The test asserts the abspos' STATIC POSITION
// (§10.6.4): with the div first the preceding inline fragment is empty →
// zero-height line box (§9.4.2) → top = 0; with 'X' first the fragment
// carries text → 100px line → top = 100. Our order flipped the very
// quantity under test.
//
// Pins:
//   - the DOM SNAPSHOT: text and child boxes interleave in wire order.
//   - a `{text}` entry is a BARE TEXT NODE — no wrapper element (a block
//     box inside an inline box splits it, CSS 2.1 §9.2.1.1: the wave-31
//     "span lesson", the reason the bounded extractor fix was rejected).
//   - a referenced child renders ONCE (rule 2), an unreferenced one still
//     renders, after the runs (rule 4).
//   - a dangling key is a warn-and-skip, never a throw (rule 5).
//   - components with no `meta.runs` are BYTE-IDENTICAL.
import { describe, it, expect, vi, afterEach } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { NodeRenderer } from '../../src/renderer/NodeRenderer';
import { resolveRuns } from '../../src/renderer/InlineRuns';
import type { ComposedNode } from '../../src/renderer/Composer';
import type { IRComponent, IRRun } from '../../src/core/ir/IRModels';

function comp(overrides: Partial<IRComponent> = {}): IRComponent {
  return { id: 'r-id', name: 'Runs_Comp', properties: [], ...overrides };
}
function node(component: IRComponent, children: ComposedNode[] = []): ComposedNode {
  return { component, children };
}
const html = (n: ComposedNode) => renderToStaticMarkup(<NodeRenderer node={n} />);

/** `the quick <u>brown</u> fox` — the glue case, as the extractor emits it. */
function gluedTree(): ComposedNode {
  const u = comp({ id: 'u-1', name: 'glue__0__0', text: 'brown', meta: { sourceTag: 'u' } });
  const p = comp({
    id: 'p-1',
    name: 'glue__0',
    text: 'the quick fox',
    meta: {
      sourceTag: 'p',
      runs: [{ text: 'the quick ' }, { child: 'glue__0__0' }, { text: ' fox' }],
    },
  });
  return node(p, [node(u)]);
}

afterEach(() => vi.restoreAllMocks());

describe('resolveRuns — the render plan', () => {
  const kids = [
    node(comp({ id: 'a-id', name: 'a-name' })),
    node(comp({ id: 'b-id', name: 'b-name' })),
  ];

  it('returns null for every absent / empty / malformed list', () => {
    // Null means "take the default path", which is what keeps every
    // pre-wave-32 document's DOM byte-identical.
    expect(resolveRuns(undefined, kids, 'x')).toBeNull();
    expect(resolveRuns(null, kids, 'x')).toBeNull();
    expect(resolveRuns([], kids, 'x')).toBeNull();
    expect(resolveRuns('nope' as unknown as IRRun[], kids, 'x')).toBeNull();
  });

  it('resolves a child by its AUTHORING KEY (name), and falls back to id', () => {
    // The reference is the child's name on the converter-emitted wire —
    // the converter mints ids at the flatten boundary, so a producer-
    // written id would name nothing after the hop (spec 03 §4.1).
    const byName = resolveRuns([{ child: 'b-name' }], kids, 'x');
    expect(byName?.entries).toEqual([{ kind: 'child', index: 1 }]);
    // …and in the extractor-direct pipeline key === name === id, so the
    // id spelling must resolve identically rather than dangle.
    const byId = resolveRuns([{ child: 'a-id' }], kids, 'x');
    expect(byId?.entries).toEqual([{ kind: 'child', index: 0 }]);
  });

  it('keeps document order and reports the unreferenced leftovers', () => {
    const plan = resolveRuns([{ text: 'lead' }, { child: 'a-name' }], kids, 'x');
    expect(plan?.entries).toEqual([
      { kind: 'text', text: 'lead' },
      { kind: 'child', index: 0 },
    ]);
    // Rule 4: a child the list did not name still renders, after the runs.
    expect(plan?.unreferenced).toEqual([1]);
  });

  it('keeps a whitespace-only run but drops an empty one', () => {
    // Rule 6: a single space between two children IS the inter-run word
    // space and carries real advance width; a zero-length run carries
    // nothing and would only add an empty DOM text node.
    const plan = resolveRuns(
      [{ child: 'a-name' }, { text: ' ' }, { text: '' }, { child: 'b-name' }],
      kids,
      'x',
    );
    expect(plan?.entries).toEqual([
      { kind: 'child', index: 0 },
      { kind: 'text', text: ' ' },
      { kind: 'child', index: 1 },
    ]);
  });

  it('warns and skips a dangling key, keeping every other position', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const plan = resolveRuns(
      [{ text: 'a' }, { child: 'nobody' }, { text: 'b' }],
      kids,
      'owner-1',
    );
    // Rule 5 — warn-and-skip, mirroring the dangling-slot.parent rule.
    expect(plan?.entries).toEqual([
      { kind: 'text', text: 'a' },
      { kind: 'text', text: 'b' },
    ]);
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('nobody'));
    // Both children were left unclaimed, so both still render.
    expect(plan?.unreferenced).toEqual([0, 1]);
  });

  it('warns and skips a duplicate reference rather than painting twice', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const plan = resolveRuns([{ child: 'a-name' }, { child: 'a-name' }], kids, 'owner-1');
    expect(plan?.entries).toEqual([{ kind: 'child', index: 0 }]);
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('more than once'));
  });

  it('warns and skips an entry carrying both keys or neither', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const both = { text: 'x', child: 'a-name' } as unknown as IRRun;
    expect(resolveRuns([both, { text: 'ok' }], kids, 'owner-1')?.entries)
      .toEqual([{ kind: 'text', text: 'ok' }]);
    expect(resolveRuns([{} as unknown as IRRun], kids, 'owner-1')).toBeNull();
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('exactly one'));
  });

  it('falls back to the default path when nothing survives validation', () => {
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    // The honest failure mode: the wire asked for an order we could not
    // reconstruct, so paint the pre-wave-32 approximation, not nothing.
    expect(resolveRuns([{ child: 'nobody' }], kids, 'x')).toBeNull();
  });
});

describe('NodeRenderer — the inline anonymous-run box', () => {
  it('interleaves text and child boxes in wire order', () => {
    const out = html(gluedTree());
    // The whole point: 'the quick ' … <u>brown</u> … ' fox', in that order.
    expect(out.indexOf('the quick ')).toBeLessThan(out.indexOf('brown'));
    expect(out.indexOf('brown')).toBeLessThan(out.indexOf(' fox'));
  });

  it('emits a text run as a BARE text node — no wrapper element', () => {
    const out = html(gluedTree());
    // The wave-31 span lesson: a block box inside an inline box splits it
    // (CSS 2.1 §9.2.1.1), which would re-break the very line box the
    // interleave exists to preserve. Exactly two elements: <p> and <u>.
    expect(out.match(/<[a-z]/g)?.length).toBe(2);
    expect(out).toContain('<p ');
    expect(out).toContain('<u ');
  });

  it('does not also paint `text` — the concatenation runs were split from', () => {
    const out = html(gluedTree());
    // `text` is 'the quick fox' (no space around the child). If the leading
    // text slot still fired, that string would appear as well and the word
    // 'quick' would occur twice.
    expect(out.match(/quick/g)?.length).toBe(1);
    expect(out).not.toContain('the quick fox');
  });

  it('paints a referenced child once and an unreferenced one after the runs', () => {
    const a = comp({ id: 'a-1', name: 'kid-a', text: 'AAA' });
    const b = comp({ id: 'b-1', name: 'kid-b', text: 'BBB' });
    const host = comp({
      id: 'h-1',
      name: 'host',
      text: 'lead',
      meta: { runs: [{ text: 'lead' }, { child: 'kid-b' }] },
    });
    const out = html(node(host, [node(a), node(b)]));
    // Rule 2 — the referenced child appears exactly once…
    expect(out.match(/BBB/g)?.length).toBe(1);
    // …rule 4 — and the unreferenced one still renders, after the runs.
    expect(out).toContain('AAA');
    expect(out.indexOf('BBB')).toBeLessThan(out.indexOf('AAA'));
  });

  it('places a child BEFORE the text (the CSS2 static-inside-inline shape)', () => {
    // <span id=inline><div id=abspos></div> X </span> — the order the
    // static-position assertion is actually about.
    const box = comp({ id: 'abs-1', name: 'inline__0__0', text: '' });
    const span = comp({
      id: 'span-1',
      name: 'inline__0',
      text: 'X',
      meta: { sourceTag: 'span', runs: [{ child: 'inline__0__0' }, { text: ' X' }] },
    });
    const out = html(node(span, [node(box)]));
    expect(out.indexOf('data-component-id="abs-1"')).toBeLessThan(out.indexOf(' X'));
  });

  it('flows a text-then-child list on ONE line (the wave-34 widening)', () => {
    // wave-34 lane R widened the producer's emission: a component whose own
    // text simply PRECEDES a kept child now ships a list too, because the
    // §4.1 trim on `text` deletes the boundary space there
    // (`<p>the quick <u>brown</u></p>` renders welded without it).
    // Renderer-side that is the SAME contract as the glue case — but the
    // pin that matters is the one the flat path could not give: the run and
    // the child are siblings in ONE inline flow, with no element boundary
    // between the text and the box, so the line does not break between them.
    const u = comp({ id: 'u-2', name: 'w__0__0', text: 'brown', meta: { sourceTag: 'u' } });
    const p = comp({
      id: 'p-2',
      name: 'w__0',
      // `text` still carries the TRIMMED concatenation — the additive rule.
      text: 'the quick',
      meta: { sourceTag: 'p', runs: [{ text: 'the quick ' }, { child: 'w__0__0' }] },
    });
    const out = html(node(p, [node(u)]));
    // The boundary space survives, and it is a bare text node adjoining the
    // child's box — no wrapper element between them.
    expect(out).toContain('the quick <');
    expect(out).not.toContain('the quick<');
    // The trimmed `text` is NOT painted a second time (rule 2).
    expect(out.match(/the quick/g)?.length).toBe(1);
    // Document order: run first, then the box.
    expect(out.indexOf('the quick')).toBeLessThan(out.indexOf('data-component-id="u-2"'));
  });

  it('leaves a component without meta.runs byte-identical', () => {
    const kid = comp({ id: 'k-1', name: 'kid', text: 'KID' });
    const host = comp({ id: 'h-1', name: 'host', text: 'LEAD' });
    // The pre-wave-32 default: leading text, then children.
    const out = html(node(host, [node(kid)]));
    expect(out.indexOf('LEAD')).toBeLessThan(out.indexOf('KID'));
  });
});
