package app.parsing

// IRFlattener — the single point where the nested authoring tree becomes
// the flat IR v2 component list (schema/spec/03-children.md).
//
// Input side stays nested FOREVER: CssComponent.children remains a
// name→component map because it is a convenient authoring / WPT-extractor
// shape. cssParsing builds a nested IRComponent tree from it (same code
// path as v1, which keeps the deprecated `--emit-ir v1` output
// byte-identical). This flattener then:
//
//   1. Walks the tree PRE-ORDER (parent before its children, children in
//      source order) — so flat-array order == composition order == the
//      exact order v1's depth-first id counter already produced. The
//      sibling-order rule (spec 03) falls out of this for free.
//   2. Stamps `slot: {parent: <parent id>}` on every non-root component.
//      Roots carry no slot.
//   3. Strips the nested `children` list (v2 wire has no children key).
//   4. Verifies id uniqueness across the whole document — a duplicate id
//      would make slot references ambiguous, so it is a CONVERT-TIME
//      ERROR (design §1.2), never a warning.

import app.irmodels.IRComponent
import app.irmodels.IRDocument
import app.irmodels.IRSlot

object IRFlattener {

    /**
     * Flatten a (possibly) nested IRDocument into the v2 flat form.
     * Idempotent: an already-flat document passes through unchanged
     * (existing slots are preserved; only nested children are rewritten).
     *
     * @throws IllegalArgumentException on duplicate component ids —
     *   ambiguous slot targets are a hard convert error.
     */
    fun flatten(doc: IRDocument): IRDocument {
        // Accumulator for the flat list — pre-order append only.
        val flat = mutableListOf<IRComponent>()
        // Every id seen so far; duplicate detection must span the WHOLE
        // document (a root clashing with a deep grandchild is just as
        // ambiguous as two siblings clashing).
        val seenIds = mutableSetOf<String>()

        // Pre-order walk: emit the node itself (children stripped, slot
        // stamped), then recurse into its children in source order.
        fun walk(component: IRComponent, parentId: String?) {
            // Duplicate id = ambiguous slot target = convert error.
            require(seenIds.add(component.id)) {
                "duplicate component id '${component.id}' — ids must be unique across the flat IR v2 document"
            }
            flat += component.copy(
                // v2 entries never nest — the relationship moves to slot.
                children = null,
                // Roots keep slot == null; children point at their parent.
                // An explicit pre-existing slot (already-flat input) wins
                // over restamping so flatten() stays idempotent.
                slot = when {
                    parentId != null -> IRSlot(parent = parentId)
                    else -> component.slot
                }
            )
            // Children flatten immediately after their parent (pre-order),
            // preserving source order among siblings.
            component.children?.forEach { child -> walk(child, component.id) }
        }

        // Document roots walk in source order; each subtree is contiguous.
        doc.components.forEach { root -> walk(root, null) }
        return IRDocument(flat)
    }
}
