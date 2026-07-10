// Composer.test.ts — the harness-side slot→tree composition layer
// (apps/web-harness/src/sdui/Composer.ts), pinning the spec 03 contract:
//
//   - Mode A: slot refs compose the preview tree; sibling order = flat
//     array order (never re-sorted by the composer).
//   - Mode B: zero-slot documents compose to a flat root list.
//   - Dangling slot.parent → root + warning; never a crash or a drop.
//   - Malformed cycles are broken by promotion (defensive only).
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { composeTree, findNode } from '../../src/sdui/Composer';
import type { IRComponent, IRDocument } from '@style-converter/web/core/ir/IRModels';

// Minimal v2 component helper — flat shape, optional slot ref.
function comp(id: string, parent?: string): IRComponent {
  const c: IRComponent = { id, name: id.toUpperCase(), properties: [] };
  if (parent) c.slot = { parent, name: 'content' };
  return c;
}

// Minimal decoded v2 document wrapper.
function doc(components: IRComponent[]): IRDocument {
  return { irVersion: 2, minReaderVersion: 2, components };
}

// The dangling/cycle paths warn by design; capture instead of printing.
beforeEach(() => vi.spyOn(console, 'warn').mockImplementation(() => {}));
afterEach(() => vi.restoreAllMocks());

describe('composeTree — Mode A (slot refs)', () => {
  it('builds a 3-level tree from slot refs, preserving sibling order', () => {
    // root → (a, b); a → (x, y) — children listed in flat-array order.
    const roots = composeTree(doc([
      comp('root'),
      comp('a', 'root'),
      comp('x', 'a'),
      comp('y', 'a'),
      comp('b', 'root'),
    ]));
    expect(roots).toHaveLength(1);
    expect(roots[0].component.id).toBe('root');
    // Sibling order rule: flat-array order IS composition order.
    expect(roots[0].children.map((n) => n.component.id)).toEqual(['a', 'b']);
    expect(roots[0].children[0].children.map((n) => n.component.id)).toEqual(['x', 'y']);
  });

  it('composes correctly even when a child precedes its parent in the array', () => {
    // Externally-authored Mode A docs aren't guaranteed pre-order; the
    // two-pass composer must not care.
    const roots = composeTree(doc([comp('child', 'parent'), comp('parent')]));
    expect(roots).toHaveLength(1);
    expect(roots[0].component.id).toBe('parent');
    expect(roots[0].children[0].component.id).toBe('child');
  });

  it('multiple roots keep their flat-array order', () => {
    const roots = composeTree(doc([comp('r1'), comp('r2'), comp('kid', 'r2')]));
    expect(roots.map((n) => n.component.id)).toEqual(['r1', 'r2']);
    expect(roots[1].children[0].component.id).toBe('kid');
  });
});

describe('composeTree — Mode B (zero-slot documents)', () => {
  it('renders every entry as a root, in order, with no children', () => {
    // The SDUI production mode: composition supplied externally, none in
    // the doc. Everything is a root; the harness shows a flat root list.
    const roots = composeTree(doc([comp('a'), comp('b'), comp('c')]));
    expect(roots.map((n) => n.component.id)).toEqual(['a', 'b', 'c']);
    expect(roots.every((n) => n.children.length === 0)).toBe(true);
  });

  it('empty document composes to an empty forest', () => {
    expect(composeTree(doc([]))).toEqual([]);
  });
});

describe('composeTree — dangling and malformed refs (spec 03 §2)', () => {
  it('treats a dangling slot.parent as a root and warns — never drops', () => {
    const roots = composeTree(doc([comp('a'), comp('lost', 'no-such-id')]));
    // Both render: the dangling entry is promoted, not dropped.
    expect(roots.map((n) => n.component.id)).toEqual(['a', 'lost']);
    expect(vi.mocked(console.warn)).toHaveBeenCalledWith(
      expect.stringContaining('dangling slot.parent'),
    );
  });

  it('treats a self-referencing slot as a root and warns', () => {
    const roots = composeTree(doc([comp('selfie', 'selfie')]));
    expect(roots.map((n) => n.component.id)).toEqual(['selfie']);
    expect(vi.mocked(console.warn)).toHaveBeenCalled();
  });

  it('breaks a two-node slot cycle by promotion instead of losing the subtree', () => {
    // a→b→a is malformed (the flattener can't produce it) but the
    // composer must stay total: promote to break the cycle, warn, render.
    const roots = composeTree(doc([comp('a', 'b'), comp('b', 'a')]));
    // One of the two becomes a root; the other stays its child — both render.
    expect(roots).toHaveLength(1);
    const all = [roots[0].component.id, ...roots[0].children.map((n) => n.component.id)];
    expect(all.sort()).toEqual(['a', 'b']);
    expect(vi.mocked(console.warn)).toHaveBeenCalledWith(
      expect.stringContaining('cycle'),
    );
  });
});

describe('findNode — Tier 5 fixture lookup', () => {
  it('finds a node anywhere in the forest by name or id', () => {
    const roots = composeTree(doc([comp('root'), comp('deep', 'root')]));
    // By id at depth…
    expect(findNode(roots, 'deep')?.component.id).toBe('deep');
    // …and by (uppercased helper) name at the root.
    expect(findNode(roots, 'ROOT')?.component.id).toBe('root');
  });

  it('returns null for an unknown name', () => {
    expect(findNode(composeTree(doc([comp('a')])), 'missing')).toBeNull();
  });
});
