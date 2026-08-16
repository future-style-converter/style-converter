package com.styleconverter.runtime.core.renderer

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRRun

/**
 * InlineRunPlan — the wave-32 `meta.runs` resolver (lane R), the Compose
 * twin of web's `renderer/InlineRuns.ts` and iOS's `InlineRunPlan.swift`.
 *
 * WHAT THE WIRE SAYS (schema/spec/03-children.md §4.1): `meta.runs` is the
 * component's inline content in DOCUMENT order — entries that are each
 * exactly one of `{text}` (an anonymous inline run) or `{child}` (the
 * position of one child box). It exists because `_text` is ONE string and
 * the child list has ONE order, so the wire could previously say
 * `run + children` or `children + run` but never `run / child / run` —
 * exactly what `the quick <u>brown</u> fox` needs when the `<u>` survives.
 *
 * WHAT THIS FILE DOES: turn the wire list into indices into the ALREADY
 * COMPOSED `component.children`, applying the three rules a renderer owns:
 *   rule 2  a referenced child renders at its run slot and NOT again in the
 *           sibling walk (hence [unreferenced], not "all children");
 *   rule 4  a child the list does not name still renders, after the runs,
 *           in sibling order — a partial list can never lose a box;
 *   rule 5  a dangling `child` key is a warn-and-skip, never a throw.
 *
 * HONEST SCOPE ON THIS PLATFORM. Compose's block container is a `Column`:
 * it has no inline line box, so an anonymous text run is a STACKED text
 * box, not a fragment sharing a line with the child beside it. `meta.runs`
 * therefore buys Compose the correct ORDER, not correct inline layout —
 * the same approximation the leading-`_text` box always was, now at the
 * right position instead of always first. That is a real fidelity gain
 * (the CSS2 static-position family is decided purely by order) and it is
 * NOT the full inline-formatting-context model.
 *
 * Wave 44 (lane U1) AMENDS that scope for ONE member family: when every
 * resolved member is a plain text run or a policy-only/empty inline leaf,
 * `typography/inline/InlineRunFold` collapses the plan into a single
 * paragraph rendered through the ordinary one-Text pipeline — real
 * inline flow (shared line boxes, one greedy break pass, one baseline)
 * for exactly the shapes where one paragraph is provably equivalent to
 * the inline formatting context. Everything else (atoms, floats, abspos,
 * styled/nested inline boxes) still takes the stacked fallback above,
 * with the refusal logged at the renderer seam.
 */
object InlineRunPlan {

    /** One resolved entry: a text run, or an index into `component.children`. */
    sealed interface Entry {
        data class Text(val text: String) : Entry
        data class Child(val index: Int) : Entry
    }

    /** The render plan: ordered content plus the children it left over. */
    data class Plan(val entries: List<Entry>, val unreferenced: List<Int>)

    /**
     * Resolve [runs] against [children].
     *
     * @return the plan, or null meaning "no usable runs — take the default
     *   leading-text-then-children path". Returning null rather than an
     *   empty plan is what keeps every pre-wave-32 document's composition
     *   byte-identical: absent, malformed and fully-dangling lists all land
     *   back on the exact code path they used before.
     */
    fun resolve(runs: List<IRRun>?, children: List<IRComponent>?): Plan? {
        // Omit-when-absent is the overwhelming common case — bail before
        // allocating anything.
        if (runs.isNullOrEmpty() || children.isNullOrEmpty()) return null

        // Two lookup keys, because the reference is the child's AUTHORING
        // KEY (spec 03 §4.1): on the converter-emitted wire that key is the
        // child's `name`; in the extractor-direct pipeline it is also its
        // `id`. `name` is consulted first so a document that happens to
        // reuse a name as some other component's id cannot mis-resolve.
        val byName = HashMap<String, Int>()
        val byId = HashMap<String, Int>()
        children.forEachIndexed { index, child ->
            // First occurrence wins on a duplicate — the composer is lenient
            // the same way (duplicate ids are a convert-time error upstream).
            if (child.name.isNotEmpty()) byName.putIfAbsent(child.name, index)
            if (child.id.isNotEmpty()) byId.putIfAbsent(child.id, index)
        }

        val entries = ArrayList<Entry>(runs.size)
        val claimed = HashSet<Int>()
        for (run in runs) {
            val text = run.text
            val key = run.child
            if (text != null) {
                // An EMPTY run contributes no glyphs and no box; dropping it
                // keeps the composition free of zero-content Text nodes. A
                // WHITESPACE-ONLY run is not empty — it is the inter-run word
                // space (rule 6) and must survive.
                if (text.isNotEmpty()) entries.add(Entry.Text(text))
                continue
            }
            if (key == null) continue // decoder already rejected this shape
            val index = byName[key] ?: byId[key]
            if (index == null) {
                // Rule 5 — dangling key: skip loudly. The named box is not in
                // this composition, so there is nothing to place; every other
                // entry keeps its position.
                // runCatching guards android.util.Log for JVM callers (the
                // unit suite exercises this pure resolver directly) — the
                // same guard logCollapseFallbackOnce uses.
                runCatching {
                    android.util.Log.w(
                        "InlineRunPlan",
                        "meta.runs names child '$key', which is not among the composed children — entry skipped"
                    )
                }
                continue
            }
            if (!claimed.add(index)) {
                // A child named twice would render twice; the wire has no
                // meaning for that, so honour the FIRST position and say so.
                runCatching {
                    android.util.Log.w(
                        "InlineRunPlan",
                        "meta.runs names child '$key' more than once — later reference skipped"
                    )
                }
                continue
            }
            entries.add(Entry.Child(index))
        }

        // Nothing survived: fall back to the default path rather than render
        // an empty container. The honest failure mode — the wire asked for an
        // order we could not reconstruct, so paint the pre-wave-32
        // approximation instead of painting nothing.
        if (entries.isEmpty()) return null

        // Rule 4 — leftovers, in sibling order, after the runs.
        val unreferenced = children.indices.filter { it !in claimed }
        return Plan(entries, unreferenced)
    }
}
