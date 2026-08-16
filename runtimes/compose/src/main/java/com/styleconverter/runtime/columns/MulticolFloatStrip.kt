// Pure IR-side classification for the wave-44 lane-U8 FLOAT STRIP: multicol
// containers whose flow children carry leading out-of-flow floats (the WPT
// CSS2/floats-clear-multicol family — floats-clear-multicol-000..003 and
// -balancing-000..003). No Compose imports on purpose: the JUnit suite pins
// this classification on the plain JVM and the IDENTICAL FS-table is pinned
// by the iOS twin (runtimes/swiftui .../columns/MulticolFloatStrip.swift).
//
// The model (CSS 2.1 §9.5 + §9.5.2 inside css-multicol-1 column boxes):
//  • a float takes NO block-axis space in its parent (out of flow) and
//    paints at its float position — anchored at its parent's content top
//    for the leading-float shapes this lane proves;
//  • a following sibling with `clear` gets CLEARANCE: its top border edge
//    lands at the relevant floats' bottom outer edge (§9.5.2);
//  • the container's whole flow is ONE continuous strip that fragments
//    into column boxes (css-break-3 §4), floats included — Chromium's ref
//    slices the 250px floats across three 100px columns.
// The strict-bail contract mirrors FloatClearanceModel (layout/): any
// wire flavor outside the proven scope makes factsFor return null, and a
// single null child fact disables the whole container's strip (engages).
package com.styleconverter.runtime.columns

// The IR box tree this classification walks (read-only).
import com.styleconverter.runtime.core.ir.IRComponent
// The shared keyword decoder — the same reader MulticolSpannerFlow's role
// classification rides, so Float/Clear keyword reads can never drift.
import com.styleconverter.runtime.core.types.ValueExtractors
// The §9.5.2 adjustment types the zero-flow synthesis emits (read-only
// use of layout/ — the block child loop already consumes these id-keyed,
// so no renderer or clearance-file edit is needed).
import com.styleconverter.runtime.layout.ClearanceAdjustment
import com.styleconverter.runtime.layout.FloatClearancePlan
// Wire-value JSON shapes for the plain-px reads (same idiom as
// FloatClearanceModel.heightPx).
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

object MulticolFloatStrip {

    /** One proven leading float: id (zero-flow key) + side + px extent. */
    data class FloatFact(
        // The float component's document-unique id — keys the synthetic
        // zero-flow plan the block child loop consumes (ClearanceZeroFlow).
        val componentId: String,
        // Physical side under the engine's LTR normalization (true=right).
        val rightSide: Boolean,
        // Declared px height — §9.5.2 clears past top + height (margins/
        // borders/padding bail below, so the outer edge IS the height).
        val heightPx: Double
    )

    /** One multicol child's strip-relevant facts (null = out of scope). */
    data class ChildFacts(
        // The child's own id — must sit in the zero-flow plan's scopeIds so
        // the child's block loop adopts the inherited plan (renderer's
        // `scopeIds.contains(component.id)` gate).
        val componentId: String,
        // Leading floats anchored at this child's content top (≤1 per side).
        val floats: List<FloatFact>,
        // §9.5.2 clearance sides this child declares (`clear` keyword).
        val clearsLeft: Boolean,
        val clearsRight: Boolean,
        // IR-derived paint extent floor in px: borders + content the child
        // paints even when its MEASURED height clamps shorter (the
        // height:0 box whose childless child carries the orange
        // border-bottom in floats-clear-multicol-003 — Chromium's balanced
        // column height includes that 5px band, ref rows 171-175).
        val trailingInkPx: Double
    )

    /**
     * Project one multicol child into [ChildFacts], or null when any wire
     * flavor is outside the proven scope. Strictness mirrors
     * FloatClearanceModel: the strip only fires when every simulated
     * position is exact, never as a guess.
     */
    fun factsFor(child: IRComponent): ChildFacts? {
        // Interleaved inline runs re-order painting (wave 32) — bail.
        if (!child.runs.isNullOrEmpty()) return null
        // Live selector/media buckets can re-style any read below after
        // the static facts baked — bail (mirrors collapse bail B6).
        if (child.selectors.isNotEmpty() || child.media.isNotEmpty()) return null
        // Generated content paints boxes this projection cannot see.
        if (child.pseudos != null) return null
        // Per-wire bails on the child itself.
        for (p in child.properties) {
            when {
                // A floated DIRECT child of the multicol is different
                // geometry (it floats within a column box, not within a
                // sibling) — out of this lane's scope.
                p.type == "Float" && keyword(p.data) in FLOAT_SIDES -> return null
                // Any margin breaks the flush cursor stacking the strip
                // assumes (measured heights exclude margins).
                p.type.startsWith("Margin") -> return null
                // Positioned children paint away from their flow slot
                // (CSS 2.1 §9.4.3); abspos/fixed are STATIC roles upstream,
                // relative/sticky would slip through as FLOW — bail here.
                p.type == "Position" && keyword(p.data) != "STATIC" -> return null
                // Vertical writing modes / rtl remap the axes this lane's
                // y-math and LTR side normalization assume.
                p.type == "WritingMode" || p.type == "Direction" -> return null
                // Transforms move ink off the strip (css-transforms-1 §3).
                p.type in TRANSFORMS -> return null
                // A nested multicol leaves the plain block strip entirely.
                p.type == "ColumnCount" || p.type == "ColumnWidth" -> return null
            }
        }
        // Split the child's children: the LEADING run of floated boxes
        // anchors at the child's content top (CSS 2.1 §9.5 — nothing
        // precedes them in flow, so their top = the parent's content top).
        val kids = child.children.orEmpty()
        val floats = mutableListOf<FloatFact>()
        var i = 0
        while (i < kids.size) {
            // Side from the Float wire (LTR normalization: inline-start →
            // left, inline-end → right — css-logical-1 §2.1, same fold as
            // FloatClearanceModel; the Direction bail above pins LTR).
            val side = kids[i].properties.firstOrNull { it.type == "Float" }
                ?.let { keyword(it.data) } ?: break
            if (side !in FLOAT_SIDES) break
            // The float itself must be fully provable: childless + textless
            // (content could overflow the declared extent), explicit px
            // height (the §9.5.2 ledger's whole outer size), and none of
            // the wires that would grow its outer edge past that height —
            // margins/padding/borders/box-sizing (the FloatClearance
            // padded-float bail, same reasoning) — nor its own clear
            // (float+clear combines §9.5.1+§9.5.2 — out of scope).
            val f = kids[i]
            if (!f.children.isNullOrEmpty() || !f._text.isNullOrEmpty()) return null
            val h = f.properties.firstOrNull { it.type == "Height" }
                ?.let { px(it.data) } ?: return null
            if (f.properties.any {
                    it.type.startsWith("Margin") || it.type.startsWith("Padding") ||
                        it.type.startsWith("Border") || it.type == "BoxSizing" ||
                        it.type == "Clear" || it.type == "Transform"
                }
            ) return null
            floats.add(
                FloatFact(f.id, rightSide = side == "RIGHT" || side == "INLINE_END", heightPx = h)
            )
            i++
        }
        // Two same-side floats would stack per §9.5.1 rules 2/3 (beside or
        // below) — geometry this ledger does not model (FloatClearance's
        // exact one-per-side rule).
        if (floats.count { it.rightSide } > 1 || floats.count { !it.rightSide } > 1) return null
        // No float may hide anywhere PAST the leading run: its anchor
        // would not be the content top this model pins.
        for (j in i until kids.size) if (hasFloatedDescendant(kids[j])) return null
        // A float-bearing child must carry no text of its own: line boxes
        // beside floats shorten (CSS 2.1 §9.5) — not modeled.
        if (floats.isNotEmpty() && !child._text.isNullOrEmpty()) return null
        // §9.5.2 clear sides from the child's own Clear wire (LTR fold).
        val clear = child.properties.firstOrNull { it.type == "Clear" }?.let { keyword(it.data) }
        return ChildFacts(
            componentId = child.id,
            floats = floats,
            clearsLeft = clear == "LEFT" || clear == "INLINE_START" || clear == "BOTH",
            clearsRight = clear == "RIGHT" || clear == "INLINE_END" || clear == "BOTH",
            trailingInkPx = trailingInk(child)
        )
    }

    /**
     * IR-derived paint-extent floor for one child: its block borders plus
     * the taller of its declared height and its direct childless
     * children's own extents. Covers the height:0 cleared box whose inner
     * `.bar` paints a border-bottom BELOW the measured 0 (css-overflow-3
     * §2: block overflow paints) — Chromium's balanced column height
     * includes that band, so the strip's C must too.
     */
    private fun trailingInk(child: IRComponent): Double {
        // Content floor: declared height, or the stacked extents of direct
        // childless+textless non-float children (block flow from content
        // top — CSS 2.1 §9.4.1; deeper nesting stays unmodeled = 0).
        val declared = child.properties.firstOrNull { it.type == "Height" }?.let { px(it.data) }
        val childrenExtent = child.children.orEmpty()
            .filter { it.children.isNullOrEmpty() && it._text.isNullOrEmpty() }
            .filter { c -> c.properties.none { it.type == "Float" && keyword(it.data) in FLOAT_SIDES } }
            .sumOf { c ->
                // Each stacked child contributes height + its own block
                // borders (border-box extent, css-box-4 §2).
                (c.properties.firstOrNull { it.type == "Height" }?.let { px(it.data) } ?: 0.0) +
                    borderPx(c, "Top") + borderPx(c, "Bottom")
            }
        // The child's own block borders wrap the content band.
        return borderPx(child, "Top") + maxOf(declared ?: 0.0, childrenExtent) +
            borderPx(child, "Bottom")
    }

    /**
     * Used block border width of one side in px: the declared px width, or
     * the `medium` default 3px when only a visible style is declared
     * (css-backgrounds-3 §4.3 — the exact 3.dp default
     * BorderSideExtractor applies, so the ink floor matches the paint).
     */
    private fun borderPx(c: IRComponent, side: String): Double {
        // Style first: none/hidden (or absent) ⇒ used width 0 (§4.3).
        val style = c.properties.firstOrNull { it.type == "Border${side}Style" }
            ?.let { keyword(it.data) }
        if (style == null || style == "NONE" || style == "HIDDEN") return 0.0
        // Declared width, else the UA `medium` default.
        return c.properties.firstOrNull { it.type == "Border${side}Width" }
            ?.let { px(it.data) } ?: 3.0
    }

    /** Any box in [c]'s subtree declaring an actually-floated Float wire. */
    private fun hasFloatedDescendant(c: IRComponent): Boolean =
        c.properties.any { it.type == "Float" && keyword(it.data) in FLOAT_SIDES } ||
            c.children.orEmpty().any { hasFloatedDescendant(it) }

    /** IR keyword, SHOUTY-normalized like every keyword read in this engine. */
    private fun keyword(data: JsonElement?): String? =
        ValueExtractors.extractKeyword(data)?.uppercase()?.replace('-', '_')

    /** Plain-px wire read: `{"type":"length","px":N}` or bare `{"px":N}`. */
    private fun px(data: JsonElement): Double? {
        // Percentage / keyword flavors need live context a pure pass lacks.
        val obj = data as? JsonObject ?: return null
        if ((obj["type"] as? JsonPrimitive)?.content == "percentage") return null
        return (obj["px"] as? JsonPrimitive)?.doubleOrNull
    }

    /** The two floated `float` keywords, logical members folded LTR. */
    private val FLOAT_SIDES = setOf("LEFT", "RIGHT", "INLINE_START", "INLINE_END")

    /** Transform-family wires (containing-block makers + ink movers). */
    private val TRANSFORMS = setOf("Transform", "Translate", "Scale", "Rotate")

    /**
     * Whether the float strip owns this container's layout: every child a
     * plain FLOW box with proven facts (a single unprovable child disables
     * the whole strip — one wrong slice would replicate into every
     * column), no forced breaks (the chunk model owns those), and at
     * least one actual float (else the run/greedy paths already render
     * the container correctly).
     */
    fun engages(specs: List<MulticolSpannerFlow.ChildSpec>?): Boolean {
        // No specs = dark stage / text-only container — never engage.
        if (specs.isNullOrEmpty()) return false
        // Spanners/statics/forced-break roles have owning models; the
        // anonymous leading-text spec carries no facts and bails too.
        if (specs.any { it.role != MulticolSpannerFlow.Role.FLOW }) return false
        if (specs.any { it.forcedBreakContent }) return false
        // Every child must be proven; any null fact disables the strip.
        if (specs.any { it.floatStrip == null }) return false
        // At least one real float — otherwise this model adds nothing.
        return specs.any { it.floatStrip!!.floats.isNotEmpty() }
    }

    /**
     * The synthetic §9.5.2 zero-flow plan for an engaged container: every
     * proven float reports ZERO block-axis size at its flow slot (the
     * ClearanceZeroFlow contract — floats are out of flow, CSS 2.1 §9.5),
     * which is what turns the stacked-float container into the 0-tall box
     * whose floats paint side-by-side at its top. Id-keyed and scoped, so
     * the plan is inert outside the multicol subtree by construction.
     *
     * @param columnFillAuto the container's §7.1/§7.2 fill mode — it rides
     *   in because the shared [MulticolFloatStripPlan.engagesPreMeasure]
     *   predicate needs it (see there for why the balance branch has a
     *   degenerate case at all, and why the decision must be made BEFORE
     *   anything is measured).
     */
    fun zeroFlowPlan(
        specs: List<MulticolSpannerFlow.ChildSpec>?,
        columnFillAuto: Boolean
    ): FloatClearancePlan? {
        // Same gate as the measure pass — stage-1 (composition) and
        // stage-2 (measure) must decide together or floats would zero-flow
        // under a layout that still stacks them. The ONE decision the
        // measure half still makes alone is the specs/measurables count
        // alignment (a hoisted child that composed nothing): composition
        // cannot see measurables at all, so that residual asymmetry is
        // documented rather than pretended away.
        if (!MulticolFloatStripPlan.engagesPreMeasure(specs, columnFillAuto)) return null
        // Every proven float id zero-flows.
        val floatIds = specs!!.flatMap { s -> s.floatStrip!!.floats.map { it.componentId } }
        // Scope: the multicol children (so their block loops ADOPT the
        // inherited plan — the renderer's scopeIds.contains(id) gate) plus
        // the floats themselves.
        val scope = specs.map { it.floatStrip!!.componentId }.toSet() + floatIds
        return FloatClearancePlan(
            scopeIds = scope,
            adjustments = floatIds.associateWith { ClearanceAdjustment(zeroFlowHeight = true) }
        )
    }
}
