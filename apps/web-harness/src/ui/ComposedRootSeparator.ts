// ComposedRootSeparator.ts — wave-54 lane L6 (unit RS): the collapsed space
// between two inline-level ROOTS on the composed web canvas.
//
// WHY: the extractor stamps `meta.role: 'ws-after'` on the earlier of two
// siblings that SOURCE whitespace separated (extract-fixture.mjs
// WS_AFTER_ROLE), and the harness replays it as a real ' ' text node — but
// only BETWEEN CHILDREN of one component (ComponentRenderer.tsx
// renderChildSeparator, wave-26 WWS). The body's element children are SIBLING
// ROOTS of the flat v2 forest, and ComposedCaptureGallery rendered them flush,
// so two root `<img>`s abutted. MEASURED on wave53-final css-ui/box-sizing-007
// web f 0.9036: the second atom column starts at x146 where the reference (and
// both natives, which pack root atoms with their 4.5-px atomGapPx) has x151 —
// one Inter 16-px space advance (web-root-separator.md §2).
//
// SPEC: CSS 2.1 §16.6.1 and css-text-3 §4.1.1 — a collapsible white-space run
// between two inline-level boxes renders as ONE space; CSS 2.1 §9.2.2.1 —
// none between block boxes (the predicate's inline-level gate).
//
// RULE 2b (wave-54 PLAN §0): the predicate is a PARAMETER. It is the WWS branch
// lifted out of renderChildSeparator as the exported `wsAfterSeparator` by
// seam-1.patch, and only the gallery's call sites (also inside that patch)
// import it — so this file compiles on a tree without the seam.

import type { ReactNode } from 'react';
// The runtime entry point the gallery already uses for the body-root (GAP 2):
// the body's declared `white-space` is read in the same CSS spelling that
// renderChildSeparator reads from `ctx.styles`.
import { buildStyles } from '@style-converter/web/core/renderer/StyleBuilder';
import type { IRDocument } from '@style-converter/web/core/ir/IRModels';
import type { ComposedNode } from '../sdui/Composer';
// wave-53 L3 (item B): the table-body plan whose wrapper box declines below.
import type { CanvasTableBody } from './CanvasTableBody';

/** The box the root forest lays out in, as the separator predicate reads it. */
export interface RootSeparatorContainer {
  /** CSS `display` of the canvas box holding the roots (the ICB / flow wrapper). */
  display?: unknown;
  /** The body-root's DECLARED `white-space` — what the ref page's body children inherit. */
  whiteSpace?: unknown;
}

/** The (prev, next, container) predicate: `' '` to separate, null for flush. */
export type RootSeparatorPredicate = (
  prev: ComposedNode,
  next: ComposedNode,
  container: RootSeparatorContainer,
) => string | null;

/**
 * Table boxes the canvas wrapper becomes for a `display: table` body
 * (CanvasTableBody.ts). DECLINED — conservatively. CSS 2.1 §17.2.1 rule 1
 * drops a white-space-only run only between table-internal boxes (or a
 * caption), and the inline-level gate already keeps those out of every
 * pair; between two inline-level NON-table children rule 2 would wrap both
 * (and the space) into one anonymous cell, where the space survives. So
 * this decline can only ever withhold a space in a shape the corpus does
 * not carry (census: 0 table-body documents with an inline-level ws-after
 * root pair) — chosen because the table-body canvas is a one-document plan
 * whose fixup has never been measured with an interleaved text node, and
 * PLAN §2 L6 pre-registers it. TODO(wave-55+): measure, then drop.
 */
const TABLE_BOX_DISPLAYS: ReadonlySet<string> = new Set(['table', 'inline-table']);

/**
 * The container the root forest sits in, for one per-test document.
 * `display`: the table-body wrapper's table box when the plan exists, else
 * `flow-root` (both the ICB div and the margin wrapper are flow-roots —
 * ComposedCaptureGallery composedIcbStyle / data-capture-flow).
 * `whiteSpace`: the body-root's own declared value (undefined when there is
 * no body-root or it declares none — 27 of 27 census documents).
 */
export function rootSeparatorContainer(
  doc: IRDocument,
  tableBody: CanvasTableBody | null,
): RootSeparatorContainer {
  // Same lookup rule as every canvas resolver — a document has one body.
  const bodyRoot = doc.components.find((c) => c.meta?.role === 'body-root');
  // The body's declared white-space in CSS spelling (renderChildSeparator's read).
  const whiteSpace = bodyRoot ? buildStyles(bodyRoot.properties).whiteSpace : undefined;
  return { display: tableBody ? tableBody.display : 'flow-root', whiteSpace };
}

/**
 * The rendered roots, in forest order, with the predicate's separator
 * between each adjacent pair it accepts — the root-level twin of
 * NodeRenderer's child walk (`node.children.flatMap`, :366-376): the first
 * root has no separator slot before it, and a null answer keeps the pair
 * flush. A document with no accepted pair returns exactly the rendered
 * roots, so its DOM is byte-identical to the pre-wave-54 `.map`.
 */
export function interleaveRootSeparators(
  roots: ComposedNode[],
  render: (root: ComposedNode, index: number) => ReactNode,
  container: RootSeparatorContainer,
  separator: RootSeparatorPredicate,
): ReactNode[] {
  // The table-body decline (banner above): the whole forest stays flush.
  const declined = typeof container.display === 'string' && TABLE_BOX_DISPLAYS.has(container.display);
  return roots.flatMap((root, index) => {
    // Every root renders exactly as the bare `.map` rendered it.
    const el = render(root, index);
    // First root, or a declined container: no separator slot.
    if (index === 0 || declined) return [el];
    // Ask the predicate about (previous root, this root) in that container.
    const sep = separator(roots[index - 1], root, container);
    // A bare string needs no React key (NodeRenderer's child walk relies on the same).
    return sep !== null && sep !== undefined ? [sep, el] : [el];
  });
}
