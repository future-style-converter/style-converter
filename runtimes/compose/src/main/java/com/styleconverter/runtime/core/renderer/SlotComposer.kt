package com.styleconverter.runtime.core.renderer

// SlotComposer — the Android COMPOSER of the IR v2 Slot & Placement
// contract (schema/spec/03-children.md). It rebuilds the in-memory render
// tree from the flat component list's `slot` refs so the harness screens
// (ComponentListScreen / ScreenshotCaptureScreen) hand the renderer one
// composed root at a time.
//
// Contract boundaries (design §1.2):
//   - ONLY composers read `slot` — the style engine (ComponentRenderer +
//     appliers) never consults it; engines stay composition-agnostic.
//   - Sibling order rule: relative order of entries sharing a slot.parent
//     in the flat array IS the composition order. The converter's
//     IRFlattener produces the flat array by pre-order walk, so
//     compose() here is its exact inverse — flatten(compose(v2 doc))
//     reproduces the v1 nested tree byte-for-byte (pinned by the
//     conformance suite against the children-nesting golden pair).
//   - Mode B: a v2 document with zero slot fields is valid (composition
//     supplied externally); compose() returns the list untouched, every
//     entry a root. v1 nested documents also pass through untouched —
//     their children arrays are already the tree.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRDocument
import com.styleconverter.runtime.core.ir.IRLog

object SlotComposer {

    private const val TAG = "SlotComposer"

    /**
     * Build the render tree(s) from a decoded IR document.
     *
     * @return the root components, with `children` populated from slot
     *   refs (v2) or passed through untouched (v1 nested / Mode B flat).
     */
    fun compose(document: IRDocument): List<IRComponent> {
        val components = document.components
        // Fast path: no slot refs anywhere → nothing to compose. Covers
        // BOTH v1 nested documents (children already in place) and v2
        // Mode B documents (composition is external; all entries root).
        if (components.none { it.slot != null }) return components

        // Duplicate ids make slot.parent ambiguous — the converter
        // already hard-errors (IRFlattener), so a duplicate here means a
        // hand-edited or corrupted document. Loud error, not a guess.
        val seen = HashSet<String>(components.size)
        for (c in components) {
            require(seen.add(c.id)) {
                "duplicate component id '${c.id}' — slot.parent references are ambiguous (spec 03 duplicate-id rule)"
            }
        }

        // Bucket children under their parent id, PRESERVING flat-array
        // order within each bucket (the sibling-order rule). slot.name is
        // decoded and retained on each child; with only the default
        // "content" slot defined today, all names funnel into the single
        // children list (multi-slot containers are a v2 reservation).
        val childrenOf = LinkedHashMap<String, MutableList<IRComponent>>()
        val roots = mutableListOf<IRComponent>()
        val ids = components.mapTo(HashSet()) { it.id }
        for (c in components) {
            val parentId = c.slot?.parent
            when {
                // No slot → root (design §1.2: roots omit slot).
                parentId == null -> roots.add(c)
                // Dangling slot.parent is NOT a decode error — the design
                // assigns it to the composer: treat as root + warning.
                parentId !in ids -> {
                    IRLog.warn(TAG, "dangling slot.parent '$parentId' on '${c.id}' — treating as root (spec 03)")
                    roots.add(c)
                }
                else -> childrenOf.getOrPut(parentId) { mutableListOf() }.add(c)
            }
        }

        // Cycle guard: components whose parent chain never reaches a root
        // (a→b→a) would silently vanish from the output. The converter
        // cannot emit cycles (it flattens real trees), so this is a
        // corrupted-document path: break each cycle at its first member
        // in flat-array order, promoting it to a root with a warning.
        val reachable = HashSet<String>()
        fun markReachable(c: IRComponent) {
            if (!reachable.add(c.id)) return
            childrenOf[c.id]?.forEach(::markReachable)
        }
        roots.forEach(::markReachable)
        if (reachable.size < components.size) {
            for (c in components) {
                if (c.id in reachable) continue
                IRLog.warn(TAG, "slot cycle detected at '${c.id}' — breaking cycle, treating as root")
                // Detach from its parent bucket and promote to root, then
                // everything below it becomes reachable again.
                childrenOf[c.slot!!.parent]?.remove(c)
                roots.add(c)
                markReachable(c)
            }
        }

        // Recursive assembly. Depth is bounded by the (acyclic, verified
        // above) parent chains; fixtures are shallow (3-5 levels).
        fun build(c: IRComponent): IRComponent {
            val kids = childrenOf[c.id] ?: return c
            return c.copy(children = kids.map(::build))
        }
        return roots.map(::build)
    }
}
