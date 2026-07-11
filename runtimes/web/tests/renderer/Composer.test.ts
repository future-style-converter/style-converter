// Composer.test.ts — the package composition layer
// (src/renderer/Composer.ts, promoted from the harness in issue #41),
// pinning the spec 03 contract:
//
//   - Mode A: slot refs compose the render tree; sibling order = flat
//     array order (never re-sorted by the composer).
//   - Mode B: zero-slot documents compose to a flat root list.
//   - Dangling slot.parent → root + warning; never a crash or a drop.
//   - Malformed cycles are broken by promotion (defensive only).
//   - findNode: depth-first document-order lookup by name or id.
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { composeTree, findNode } from '../../src/renderer/Composer';
import type { IRComponent, IRDocument } from '../../src/core/ir/IRModels';

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
});

describe('composeTree — Mode B (zero-slot documents)', () => {
  it('renders every entry as a root, in flat-array order', () => {
    const roots = composeTree(doc([comp('r1'), comp('r2'), comp('r3')]));
    expect(roots.map((n) => n.component.id)).toEqual(['r1', 'r2', 'r3']);
    // No children anywhere — composition never invented structure.
    expect(roots.every((n) => n.children.length === 0)).toBe(true);
  });
});

describe('composeTree — dangling refs and cycles (spec 03 §2)', () => {
  it('promotes a dangling slot.parent to root with a warning', () => {
    const roots = composeTree(doc([comp('ok'), comp('lost', 'no-such-id')]));
    // Never a drop: both components render, the dangling one as a root.
    expect(roots.map((n) => n.component.id)).toEqual(['ok', 'lost']);
    expect(console.warn).toHaveBeenCalledWith(expect.stringContaining('dangling slot.parent'));
  });

  it('promotes a degenerate self-reference to root', () => {
    const roots = composeTree(doc([comp('selfie', 'selfie')]));
    expect(roots.map((n) => n.component.id)).toEqual(['selfie']);
    expect(console.warn).toHaveBeenCalledWith(expect.stringContaining('dangling slot.parent'));
  });

  it('breaks an a→b→a slot cycle by promotion, keeping every node exactly once', () => {
    const roots = composeTree(doc([comp('a', 'b'), comp('b', 'a')]));
    // The first unreachable node (flat order: a) is promoted; b stays its child.
    expect(roots).toHaveLength(1);
    expect(roots[0].component.id).toBe('a');
    expect(roots[0].children.map((n) => n.component.id)).toEqual(['b']);
    // b must NOT still claim a as a child (a was detached when promoted).
    expect(roots[0].children[0].children).toHaveLength(0);
    expect(console.warn).toHaveBeenCalledWith(expect.stringContaining('slot cycle'));
  });
});

describe('findNode — name/id lookup over a composed forest', () => {
  it('finds nodes by id and by name, depth-first document order', () => {
    const roots = composeTree(doc([comp('root'), comp('kid', 'root')]));
    expect(findNode(roots, 'kid')?.component.id).toBe('kid');
    // Name lookup (helper uppercases names).
    expect(findNode(roots, 'KID')?.component.id).toBe('kid');
    // Miss → null, never a throw.
    expect(findNode(roots, 'nope')).toBeNull();
  });
});
