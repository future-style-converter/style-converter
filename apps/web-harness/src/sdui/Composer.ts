/**
 * Composer — the harness-side composition layer for the flat IR v2 wire
 * (schema/spec/03-children.md).
 *
 * The @style-converter/web ENGINE is composition-agnostic by contract:
 * StyleBuilder only ever sees ONE component's properties, and nothing in
 * the engine reads `slot`. THIS file is the only place the preview
 * harness turns child-side `slot` refs back into a render tree:
 *
 *   - Mode A (slots): entries with `slot.parent` compose under that
 *     parent; relative flat-array order of siblings IS the DOM order
 *     (spec 03 sibling-order rule — the composer never re-sorts; CSS
 *     `order` re-sorts visually via the browser, exactly CSS semantics).
 *   - Mode B (zero-slot documents): every entry is a root; the harness
 *     renders them as a flat root list. Zero engine involvement.
 *   - Dangling `slot.parent` (id not in the document): treat as root and
 *     warn — never a crash, never a dropped component (spec 03 §2).
 */

import type { IRComponent, IRDocument } from '@style-converter/web/core/ir/IRModels';

/**
 * One node of the composed preview tree. Deliberately a wrapper AROUND
 * IRComponent rather than a field ON it: the v2 model has no `children`,
 * and keeping composition out of the engine's type is what enforces the
 * engine/composer split at compile time.
 */
export interface ComposedNode {
  /** The flat-wire component this node renders. */
  component: IRComponent;
  /** Composed children, in flat-array sibling order. Empty for leaves. */
  children: ComposedNode[];
}

/**
 * Build the composed root forest from a decoded v2 document.
 *
 * Two-pass so document order never matters for correctness (Mode B docs
 * or externally-authored compositions may list children before parents):
 * pass 1 creates one node per entry; pass 2 attaches by slot ref. Array
 * order is preserved at every level because both passes iterate the flat
 * list in order — the spec's sibling-order rule falls out for free.
 */
export function composeTree(doc: IRDocument): ComposedNode[] {
  // id → node lookup for slot resolution. First occurrence wins on a
  // duplicate id (a convert-time error upstream; the composer is lenient).
  const byId = new Map<string, ComposedNode>();
  // Pass 1: wrap every component; remember flat order for pass 2/3.
  const order: ComposedNode[] = doc.components.map((component) => {
    const node: ComposedNode = { component, children: [] };
    if (!byId.has(component.id)) byId.set(component.id, node);
    return node;
  });
  const roots: ComposedNode[] = [];
  // Pass 2: attach children to parents (or promote to root).
  for (const node of order) {
    const parentId = node.component.slot?.parent;
    // No slot at all → root (covers Mode B documents wholesale).
    if (!parentId) {
      roots.push(node);
      continue;
    }
    const parent = byId.get(parentId);
    // Dangling ref or degenerate self-ref → root + composer warning
    // (spec 03 §2: never a crash, never a dropped component).
    if (!parent || parent === node) {
      console.warn(
        `[Composer] dangling slot.parent "${parentId}" on component ` +
          `"${node.component.id}" — treating as root`,
      );
      roots.push(node);
      continue;
    }
    parent.children.push(node);
  }
  // Pass 3: cycle safety. A malformed doc where slots form a cycle
  // (a→b→a) leaves those nodes unreachable from any root. Promote the
  // first unreachable node (flat order) to root — breaking the cycle —
  // and repeat until everything is reachable. Converter output is a tree
  // by construction, so this loop is purely defensive and normally free.
  let reachable = collectReachable(roots);
  while (reachable.size < order.length) {
    const orphan = order.find((n) => !reachable.has(n))!;
    console.warn(
      `[Composer] slot cycle detected at component "${orphan.component.id}" ` +
        '— promoting to root',
    );
    // Detach from its (in-cycle) parent so it isn't rendered twice.
    const parent = byId.get(orphan.component.slot?.parent ?? '');
    if (parent) parent.children = parent.children.filter((c) => c !== orphan);
    roots.push(orphan);
    reachable = collectReachable(roots);
  }
  return roots;
}

/** Depth-first reachability sweep used by the cycle-safety pass. */
function collectReachable(roots: ComposedNode[]): Set<ComposedNode> {
  const seen = new Set<ComposedNode>();
  const stack = [...roots];
  // Iterative DFS — no recursion so pathological depths can't overflow.
  while (stack.length > 0) {
    const node = stack.pop()!;
    if (seen.has(node)) continue;
    seen.add(node);
    stack.push(...node.children);
  }
  return seen;
}

/**
 * Find a node anywhere in a composed forest by component name or id.
 * Used by FixtureCanvas (Tier 5) to mount a single component's subtree.
 */
export function findNode(roots: ComposedNode[], nameOrId: string): ComposedNode | null {
  // Depth-first, document order — mirrors the pre-composer findByName.
  const stack = [...roots].reverse();
  while (stack.length > 0) {
    const node = stack.pop()!;
    if (node.component.name === nameOrId || node.component.id === nameOrId) return node;
    // Reverse-push so children are visited in sibling order.
    for (let i = node.children.length - 1; i >= 0; i--) stack.push(node.children[i]);
  }
  return null;
}
