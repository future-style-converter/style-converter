// typography/inline — wave 50 (lane B9): the document-order walk that turns
// a line-clamp container's in-flow content into [LineBoxRun]s, plus the
// [LineBoxRun]s. Twin of iOS's wave-46 `LineClampCensusRuns.swift`; the
// per-CHILD wire readers it leans on live in LineBoxChildMetrics.kt (the
// twin of `LineClampChildMetrics.swift`), split out under the ≤200-line
// file rule.
//
// A clamp root's children are rendered LATER, recursively, each with its
// inherited properties merged in at its own render — so at the root's
// render, where the cap must be decided, a child's USED font-size and
// line-height are not on any style yet. This file resolves the two numbers
// off the child's RAW wire (the same `ValueExtractors.extractDp` reads the
// child's own chain will make), inheriting from the root where the child
// declares nothing: font-size, line-height and white-space all inherit
// (CSS 2.1 §6.2). Anything this reader cannot resolve reads null, which the
// census turns into [LineBoxCensus.Verdict.Unprovable] — and the cap
// resolver then keeps the wave-41 uniform cap. Never a guessed height.
package com.styleconverter.runtime.typography.inline

// The typed IR nodes the walk classifies (pure data, no Compose).
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
// The SAME run resolver the renderer paints with — reusing it is what keeps
// census order and paint order from ever disagreeing (dangling keys,
// duplicate references and rule-4 leftovers are all decided there once).
import com.styleconverter.runtime.core.renderer.ComponentRenderer
import com.styleconverter.runtime.core.renderer.InlineRunPlan

/**
 * The clamp ROOT's resolved text metrics the census inherits from — built
 * by the cap resolver off the same property list the root's own label lays
 * out with, so cap metrics and leaf metrics can never disagree.
 */
data class LineBoxRootMetrics(
    /** The root's used font size in px (monospace-UA 13px already folded). */
    val fontSizePx: Float,
    /** The root's one line box in px (declared line-height, else the pin). */
    val lineBoxPx: Float,
    /** The root's `white-space` keyword as it rides the wire, or null. */
    val whiteSpace: String?,
    /** The root's DECLARED px line-height, when it declared a length. */
    val declaredLineHeightPx: Float?,
    /**
     * The root's DECLARED unitless line-height multiplier, when that is how
     * it declared one: a number inherits as the NUMBER and recomputes
     * against each child's own font size (CSS 2.1 §10.8).
     */
    val declaredMultiplier: Float?,
)

object LineBoxCensusRuns {

    /**
     * The container's in-flow content as census runs, in paint order.
     *
     * `meta.runs` is authoritative when present (schema/spec/03-children.md
     * §4.1) and [InlineRunPlan.resolve] is the ONE reader of it; its
     * rule-4 leftovers (children the runs never named) paint after the
     * ordered entries, so they are appended in sibling order. Without runs
     * the renderer paints the leading text and then the children — the
     * pre-wave-32 path this mirrors exactly.
     *
     * Out-of-flow children (`position: absolute` / `fixed`) generate no
     * line box in this container (css-position-3 §2.1) and are skipped.
     */
    fun runs(component: IRComponent, root: LineBoxRootMetrics): List<LineBoxRun> {
        // Every child that participates in this container's flow, in
        // sibling order — the index space InlineRunPlan resolves against is
        // `component.children`, so filter AFTER resolution, not before.
        val children = component.children.orEmpty()
        // A text run of the container's own anonymous content: it lays out
        // on the root's line box, under the root's white-space.
        val rootRun = { text: String ->
            LineBoxRun(
                lineBoxPx = root.lineBoxPx,
                exactLines = LineBoxCensus.exactLineCount(text, root.whiteSpace),
            )
        }
        val plan = InlineRunPlan.resolve(component.runs, children)
        // No usable runs: the default leading-text-then-children order.
        if (plan == null) {
            val out = mutableListOf<LineBoxRun>()
            component._text?.takeIf { it.isNotEmpty() }?.let { out += rootRun(it) }
            for (child in children) if (!ComponentRenderer.isOutOfFlowChild(child.properties)) {
                out += childRun(child, root)
            }
            return out
        }
        val out = mutableListOf<LineBoxRun>()
        for (entry in plan.entries) when (entry) {
            is InlineRunPlan.Entry.Text -> out += rootRun(entry.text)
            is InlineRunPlan.Entry.Child -> {
                val child = children.getOrNull(entry.index) ?: continue
                if (!ComponentRenderer.isOutOfFlowChild(child.properties)) out += childRun(child, root)
            }
        }
        // Rule-4 leftovers paint after the ordered entries.
        for (index in plan.unreferenced) {
            val child = children.getOrNull(index) ?: continue
            if (!ComponentRenderer.isOutOfFlowChild(child.properties)) out += childRun(child, root)
        }
        return out
    }

    /**
     * True when the container's content is NOT uniform on the root's line
     * box — some run lays out on a box of its own, is monolithic, or opens
     * a vertical band (a resolvable non-zero one, or one this reader could
     * not resolve at all).
     *
     * This is exactly the condition under which the wave-41 uniform cap
     * (N × the root's line box) can be WRONG. The converse is an identity,
     * not a heuristic: while every run shares the root's box, adds no band
     * and is not monolithic, the census's own arithmetic reduces to
     * N × that box whatever the per-run counts are — pinned by
     * LineBoxCensusTest's "uniform unbanded runs" case. So the gate is
     * behaviour-NEUTRAL and exists to keep the walk off the clamp roots it
     * could only ever re-derive — 35 of the 38 fixed-count roots in the 34
     * wave49-final per-test IR documents that carry one — leaving the
     * measured movers, line-clamp-005 / -006 / -007, as the only roots the
     * census decides.
     */
    fun hasNonUniformContent(runs: List<LineBoxRun>, root: LineBoxRootMetrics): Boolean =
        runs.any {
            it.monolithicPx != null || it.lineBoxPx != root.lineBoxPx || it.leadingBandPx != 0f
        }

    /**
     * One in-flow child as a census run.
     *
     *  • `display: none` generates no box at all (css-display-3 §2.5) — the
     *    renderer emits nothing for it, so it costs no height here;
     *  • a `<br>` is a forced line break (HTML §4.5.27): it generates no box
     *    and no line box of its own — it ENDS one, and the text runs on
     *    either side are already counted. The converter stamps a measured
     *    `height` on it (0 or 20px in the corpus), which must NOT be
     *    budgeted as a box;
     *  • a box with a definite px `height`, or a child whose used overflow
     *    is not `visible` (an independent formatting context, css-overflow-3
     *    §3), is MONOLITHIC: its full height, no line boxes of ours;
     *  • a leaf child with text yields its own line boxes plus its top band;
     *  • a child that nests its own children or runs would need its
     *    subtree's census — unprovable here, and stated as such.
     */
    fun childRun(child: IRComponent, root: LineBoxRootMetrics): LineBoxRun {
        val properties = child.properties
        // `display: none` — no box, no height, no line box.
        if (LineBoxChildMetrics.keyword(properties, "Display") == "none") {
            return LineBoxRun(root.lineBoxPx, exactLines = 0, leadingBandPx = 0f)
        }
        // A forced break contributes the break, not a box (see the doc).
        // TWIN DIVERGENCE (b) — LineBoxCensus.kt's header names it: Swift's
        // LineClampCensusRuns.childRun has NO br arm, so the converter's
        // stamped height falls through to its explicit-height branch there
        // and a <br> is budgeted as a monolithic box on iOS. Corpus impact
        // unmeasured (no device gate this wave); the S3 mutation log records
        // that deleting this arm moves zero wave49-final caps.
        val tag = child._tag?.lowercase()
        if (tag == "br" || child.role == "line-break") {
            return LineBoxRun(root.lineBoxPx, exactLines = 0, leadingBandPx = 0f)
        }
        // The child's own typography, inherited from the root where absent.
        val fontPx = LineBoxChildMetrics.fontSizePx(properties, root)
        val lineBox = LineBoxChildMetrics.lineBoxPx(properties, fontPx, root)
        val bands = LineBoxChildMetrics.verticalBands(properties)
        val box = lineBox ?: root.lineBoxPx
        // `white-space` inherits (CSS 2.1 §6.2) — the child's own wins.
        val ws = LineBoxChildMetrics.keyword(properties, "WhiteSpace") ?: root.whiteSpace
        // A child with its own subtree needs that subtree's census: the
        // count is not provable from this level (stated, never guessed).
        val hasNested = !child.children.isNullOrEmpty() || !child.runs.isNullOrEmpty()
        val lines = if (!hasNested && lineBox != null) {
            LineBoxCensus.exactLineCount(child._text, ws)
        } else {
            null
        }
        // A definite `height` makes the box exactly that tall, whatever its
        // text does (css-sizing-3 §5.1 — the used height wins).
        val explicitHeight = LineBoxChildMetrics.explicitHeightPx(properties)
        if (explicitHeight != null) {
            return LineBoxRun(
                lineBoxPx = box, exactLines = 0, leadingBandPx = bands?.first,
                monolithicPx = bands?.let { explicitHeight + it.first + it.second },
            )
        }
        // A scroll container's lines are not this container's: keep the box
        // whole when its own lines and bands are provable, else report an
        // UNKNOWN run whose band is also null — so the census can never
        // mistake it for a plain run and cut inside it.
        if (LineBoxChildMetrics.isScrollContainer(properties)) {
            val n = lines
            val b = bands
            if (n == null || b == null) return LineBoxRun(box, exactLines = null, leadingBandPx = null)
            return LineBoxRun(
                lineBoxPx = box, exactLines = 0, leadingBandPx = b.first,
                monolithicPx = n * box + b.first + b.second,
            )
        }
        // A plain block / inline child: its line boxes under its top band.
        return LineBoxRun(lineBoxPx = box, exactLines = lines, leadingBandPx = bands?.first)
    }
}
