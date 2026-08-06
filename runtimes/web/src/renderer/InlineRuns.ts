/**
 * InlineRuns — the wave-32 `meta.runs` resolver (the inline anonymous-run
 * box), split out of NodeRenderer.ts to keep that file under the size rule.
 *
 * WHAT THE WIRE SAYS (schema/spec/03-children.md §4.1): `meta.runs` is the
 * component's inline content in DOCUMENT order — a list whose entries are
 * each exactly one of `{text}` (an anonymous inline run) or `{child}` (the
 * position of one child box). It exists because `text` is ONE string and
 * the flat list has ONE sibling order, so the wire could previously say
 * `run + children` or `children + run` but never `run / child / run` —
 * exactly the case `the quick <u>brown</u> fox` needs when the `<u>`
 * survives as a child. Through wave-31 the extractor marked those
 * components `inline-run-reordered` and shipped the concatenation.
 *
 * WHAT THIS MODULE DOES: turn the wire list into a render plan over the
 * ALREADY-COMPOSED children (indices into `node.children`), applying the
 * three rules a renderer owns —
 *   rule 2  a referenced child renders at its run slot and NOT again in
 *           the sibling walk (hence `unreferenced`, not "all children");
 *   rule 4  a child the list does not name still renders, after the runs,
 *           in sibling order — a partial list can never lose a box;
 *   rule 5  a dangling `child` key is a warn-and-skip, mirroring the
 *           dangling-`slot.parent` rule, never a throw.
 *
 * WHAT IT DELIBERATELY DOES NOT DO: build DOM. The caller emits a `{text}`
 * entry as a BARE TEXT NODE. Wrapping it in an element is the shape
 * wave-31 measured and rejected: a block box inside an inline box splits
 * it (CSS 2.1 §9.2.1.1), re-breaking the very line box the interleave
 * exists to preserve.
 */

import type { IRRun } from '../core/ir/IRModels';
import type { ComposedNode } from './Composer';

/** One resolved entry — a text run, or an index into `node.children`. */
export type ResolvedRun =
  | { kind: 'text'; text: string }
  | { kind: 'child'; index: number };

/** The render plan: ordered content, plus the children it left over. */
export interface RunsPlacement {
  /** Interleaved content, in document order. Never empty. */
  entries: ResolvedRun[];
  /** Indices of children no entry named, in sibling order. */
  unreferenced: number[];
}

/**
 * Resolve `meta.runs` against a node's composed children.
 *
 * @param runs     the wire list (any shape — this function validates it).
 * @param children the composed children, in flat-array sibling order.
 * @param ownerId  the owning component's id, for warning provenance only.
 * @returns the plan, or `null` to mean "no usable runs — take the default
 *          leading-text-then-children path". Returning null rather than an
 *          empty plan is what keeps every pre-wave-32 document's DOM
 *          byte-identical: absent, malformed, and fully-dangling lists all
 *          land back on the exact code path they used before.
 */
export function resolveRuns(
  runs: IRRun[] | null | undefined,
  children: ComposedNode[],
  ownerId: string,
): RunsPlacement | null {
  // Omit-when-absent is the common case by an enormous margin — bail
  // before allocating anything.
  if (!Array.isArray(runs) || runs.length === 0) return null;

  // Two lookup keys, because the reference is the child's AUTHORING KEY
  // (spec 03 §4.1): on the converter-emitted wire that key is the child's
  // `name`; in the extractor-direct pipeline (no converter hop) it is also
  // its `id`. `name` is consulted first so a document that happens to
  // reuse a name as some other component's id cannot mis-resolve.
  const byName = new Map<string, number>();
  const byId = new Map<string, number>();
  children.forEach((child, index) => {
    const { name, id } = child.component;
    // First occurrence wins on a duplicate — the composer is lenient the
    // same way (duplicate ids are a convert-time error upstream).
    if (typeof name === 'string' && name.length > 0 && !byName.has(name)) byName.set(name, index);
    if (typeof id === 'string' && id.length > 0 && !byId.has(id)) byId.set(id, index);
  });

  const entries: ResolvedRun[] = [];
  // Which child indices a run entry claimed — drives rules 2 and 4.
  const claimed = new Set<number>();

  for (const run of runs) {
    // Defensive: the wire is validated by the schema in CI, but a runtime
    // must never throw on a bad hint (it is a DROPPABLE meta key).
    if (run === null || typeof run !== 'object') {
      console.warn(`[InlineRuns] component "${ownerId}": meta.runs entry is not an object — skipped`);
      continue;
    }
    const hasText = typeof run.text === 'string';
    const hasChild = typeof run.child === 'string' && run.child.length > 0;
    if (hasText === hasChild) {
      // Both or neither: the schema says exactly one, so this is a writer
      // bug. Skipping (loudly) keeps the rest of the order intact.
      console.warn(
        `[InlineRuns] component "${ownerId}": meta.runs entry must carry exactly one of ` +
          `{text, child} — skipped`,
      );
      continue;
    }
    if (hasText) {
      // An EMPTY run contributes no glyphs and no box; dropping it keeps
      // the DOM free of zero-length text nodes. Whitespace-only runs are
      // NOT empty — a single space between two children is the inter-run
      // word space (spec 03 §4.1 rule 6) and must survive.
      if ((run.text as string).length > 0) entries.push({ kind: 'text', text: run.text as string });
      continue;
    }
    const key = run.child as string;
    const index = byName.get(key) ?? byId.get(key);
    if (index === undefined) {
      // Rule 5 — dangling key: warn and skip. The named box does not exist
      // in this composition, so there is nothing to place; every other
      // entry keeps its position.
      console.warn(
        `[InlineRuns] component "${ownerId}": meta.runs names child "${key}", ` +
          'which is not among its composed children — entry skipped',
      );
      continue;
    }
    if (claimed.has(index)) {
      // A child named twice would render twice. The wire has no meaning
      // for that, so honour the FIRST position and say so.
      console.warn(
        `[InlineRuns] component "${ownerId}": meta.runs names child "${key}" more than once — ` +
          'later reference skipped',
      );
      continue;
    }
    claimed.add(index);
    entries.push({ kind: 'child', index });
  }

  // Nothing survived validation: fall back to the default path rather than
  // render an empty element. This is the honest failure mode — the wire
  // asked for an order we could not reconstruct, so we paint the
  // pre-wave-32 approximation instead of painting nothing.
  if (entries.length === 0) return null;

  // Rule 4 — leftovers, in sibling order, after the runs.
  const unreferenced: number[] = [];
  children.forEach((_, index) => { if (!claimed.has(index)) unreferenced.push(index); });

  return { entries, unreferenced };
}
