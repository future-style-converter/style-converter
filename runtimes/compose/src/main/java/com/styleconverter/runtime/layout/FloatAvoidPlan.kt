package com.styleconverter.runtime.layout

// FloatAvoidPlan — wave-53 lane L4. CSS 2.1 §9.5: a normal-flow box that
// establishes a BFC "must not overlap the margin box of any floats in the same
// block formatting context". Pure half (JVM-pinned); Compose adapter =
// FloatAvoidLayout.kt; iOS twin = StyleEngine/layout/FloatAvoidPlan.swift.
// Why: neither native had float avoidance — FloatRowPacking bails on R,L,R and
// FloatClearance needs a Clear — so contain-inline-size-bfc-floats-001/-002 and
// display-flow-root-002 stacked every float as an in-flow block and started the
// BFC below them all (tools/titan/results/wave53-plan/layout-degenerates.md
// §4.1; web renders the same IR right). Honesty contract (FloatClearance's):
// shape() is null — the frozen path — unless every input is proven px wire.
// Clauses G2–G8 follow PLAN.md §2 L4; G1 (capture mode) is a CompositionLocal,
// checked at the ComponentRenderer call site.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.types.ValueExtractors
import com.styleconverter.runtime.performance.PerformanceExtractor
import com.styleconverter.runtime.scrolling.OverflowBehavior
import com.styleconverter.runtime.scrolling.OverflowExtractor
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** A proven sibling list: children[0 until k] float, children[k] is the BFC root. */
data class FloatAvoidShape(
    val sides: List<FloatValue>,          // physical side per float (LEFT/RIGHT; G3 pins LTR)
    val floatWidthsPx: List<Double>,      // G6 px margin boxes off the wire (the pins' inputs)
    val floatHeightsPx: List<Double>,
    val bfcInlineSizePx: Double,          // G7 used inline size, CSS px
    val containerWidthPx: Double?,        // P7: the container's own px Width (null = auto)
    val containerIsBfc: Boolean,          // §10.6.7: a BFC root's auto height encloses floats
)

/** Solved geometry, index-aligned with the children (floats, then the BFC). */
data class FloatAvoidPlacement(val x: List<Double>, val y: List<Double>, val width: Double, val height: Double)

object FloatAvoidPlan {

    /** The gated shape for [container], or null (identity — the frozen rendering). */
    fun shape(container: IRComponent): FloatAvoidShape? {
        val cp = pairs(container)                                    // (type, data): the extractors' input
        val containerBfc = establishesBfc(cp)                        // read once: G2 and §10.6.7
        // G2 — FloatClearance's attach rule: a composed root or a BFC root, so no outside float intrudes.
        if (container.slot?.parent != null && !containerBfc) return null
        // G3 — plain LTR horizontal-tb block container, no own text/runs/pseudos/buckets.
        if (!plainBox(container) || cp.any { it.first in CONTAINER_BAILS }) return null
        if (keyword(cp, "Display") !in BLOCK_DISPLAYS || keyword(cp, "Position") !in STATIC) return null
        if (hasWire(container, "Clear")) return null                 // G5 — any Clear below is W5's
        val kids = container.children.orEmpty()                      // G4 — floats…, then ONE BFC root, last
        if (kids.size < 2) return null
        val floats = kids.dropLast(1); val bfc = kids.last()          // the k floats; the BFC candidate
        val sides = floats.map { sideOf(it) ?: return null }         // every leading child floats
        if (sides.zipWithNext().any { (a, b) -> a == b }) return null // G5 — same-side runs: FloatRowPacking
        // G6 — floats: plain childless px boxes, no box-model bands, block display.
        if (floats.any { !plainBox(it) || !it.children.isNullOrEmpty() || boxBails(it) }) return null
        if (floats.any { keyword(pairs(it), "Display") !in setOf(null, "BLOCK") }) return null
        // CSS 2.1 §9.7: an absolute/fixed box's `float` computes to none (out of flow) — no float to avoid.
        if (floats.any { keyword(pairs(it), "Position") !in FLOAT_POSITIONS }) return null
        val widths = floats.map { lengthPx(it, "Width") ?: return null }
        val heights = floats.map { lengthPx(it, "Height") ?: return null }
        val bp = pairs(bfc)                                          // G4 — the in-flow BFC root itself
        if (sideOf(bfc) != null || !plainBox(bfc) || boxBails(bfc) || !establishesBfc(bp)) return null
        if (keyword(bp, "Display") !in BLOCK_DISPLAYS || keyword(bp, "Position") !in STATIC) return null
        val pxWidth = lengthPx(bfc, "Width")                         // G7 — provable used inline size
        val used = pxWidth ?: if (containedIntrinsic(bfc, bp)) 0.0 else return null
        // G8 — a contain-sized BFC paints nothing of its own: natively it is measured at content width,
        // and in wire order its paint would sit ABOVE the floats (Appendix E: step 4, below them).
        if (pxWidth == null && bp.any { paints(it.first) }) return null
        if (!paintedDescendantsInline(bfc)) return null              // G8 for descendants (step 7 only)
        return FloatAvoidShape(sides, widths, heights, used, lengthPx(container, "Width"), containerBfc)
    }

    /**
     * §9.5.1 float placement, then §9.5 BFC avoidance, in one unit: the
     * adapter passes device px with [scale] = density, pins pass CSS px.
     * [incomingWidth] is used only when the container has no px Width (P7).
     */
    fun place(
        shape: FloatAvoidShape, floatWidths: List<Double>, floatHeights: List<Double>,
        incomingWidth: Double, bfcHeight: Double, scale: Double = 1.0,
    ): FloatAvoidPlacement {
        val w = shape.containerWidthPx?.let { it * scale } ?: incomingWidth // P7: the root's 400, not 358
        val rects = ArrayList<DoubleArray>()                        // placed floats: l, t, r, b, isLeft
        var floor = 0.0                                              // §9.5.1 rule 5: not above earlier tops
        for (i in shape.sides.indices) {
            val fw = floatWidths[i]; val fh = floatHeights[i]; val isLeft = shape.sides[i] == FloatValue.LEFT
            var y = floor                                            // rule 8: as high as possible
            while (true) {
                val (l, r) = gap(rects, y, fh, w)                    // the band's float-free span
                val next = rects.map { it[3] }.filter { it > y }.minOrNull()
                if (r - l >= fw || next == null) {                   // fits, or no float left to pass
                    val x = if (isLeft) l else r - fw                // rules 1-3: hug the band edge
                    rects.add(doubleArrayOf(x, y, x + fw, y + fh, if (isLeft) 1.0 else 0.0))
                    break
                }
                y = next                                             // too narrow: drop to the next bottom
            }
            floor = y
        }
        val used = shape.bfcInlineSizePx * scale                     // G7's size in the input unit
        // Candidate tops: the static position 0, then every float bottom, ascending.
        val candidates = (listOf(0.0) + rects.map { it[3] }).distinct().sorted()
        var bx = 0.0; var by = candidates.last()                     // default: below every float
        for (cy in candidates) {
            val (l, r) = gap(rects, cy, bfcHeight, w)
            if (r - l >= used) { bx = l; by = cy; break }            // first band whose gap holds it
        }
        val bfcBottom = by + bfcHeight; val floatBottom = rects.maxOfOrNull { it[3] } ?: 0.0
        // §10.6.3: floats do not grow a non-BFC container's auto height; §10.6.7: a BFC root's they do.
        val height = if (shape.containerIsBfc) maxOf(bfcBottom, floatBottom) else bfcBottom
        return FloatAvoidPlacement(rects.map { it[0] } + bx, rects.map { it[1] } + by, w, height)
    }

    /**
     * The float-free span over the HALF-OPEN band [y, y+h): a float ending at
     * y does not touch a band starting at y; a zero-height band is the point y.
     */
    private fun gap(rects: List<DoubleArray>, y: Double, h: Double, w: Double): Pair<Double, Double> {
        val hit = rects.filter { it[3] > y && (it[1] < y + h || it[1] <= y) } // intersecting floats
        val l = hit.filter { it[4] == 1.0 }.maxOfOrNull { it[2] } ?: 0.0     // left floats push right
        val r = hit.filter { it[4] == 0.0 }.minOfOrNull { it[0] } ?: w       // right floats pull left
        return maxOf(0.0, l) to minOf(w, r)                                  // clamp to the content box
    }
    /** G7 contained arm: an intrinsic Width keyword under inline-size containment ALONE. */
    private fun containedIntrinsic(bfc: IRComponent, bp: List<Pair<String, JsonElement?>>): Boolean {
        if (keyword(bp, "Width") !in INTRINSIC_WIDTHS) return false  // fit-/min-/max-content only
        val c = PerformanceExtractor.extractPerformanceConfig(bp).contain // single owner of Contain tokens
        // css-contain-3 §4.2 sizes the box as if empty: fit-content = 0. Size/block-size containment
        // would zero the height and paint containment would clip — unmodeled natively, so bail.
        if (!c.inlineSize || c.size || c.blockSize || c.paint || clips(bp)) return false
        return !hasText(bfc)                                         // text wraps at 0 in CSS, not natively
    }
    /** BFC roots (CSS 2.1 §9.4.1, css-display-3): flow-root, clipping overflow, layout/paint containment. */
    private fun establishesBfc(p: List<Pair<String, JsonElement?>>): Boolean {
        val c = PerformanceExtractor.extractPerformanceConfig(p).contain // strict/content expand to both
        return keyword(p, "Display") == "FLOW_ROOT" || clips(p) || c.layout || c.paint
    }
    /** Any non-visible overflow axis (OverflowExtractor owns the keyword + logical-alias fold). */
    private fun clips(p: List<Pair<String, JsonElement?>>): Boolean =
        OverflowExtractor.extractOverflowConfig(p).let { it.overflowX != OverflowBehavior.VISIBLE || it.overflowY != OverflowBehavior.VISIBLE }
    /** Physical float side via FloatExtractor (single owner), logical sides mapped for LTR. */
    private fun sideOf(c: IRComponent): FloatValue? = when (FloatExtractor.extractFloatConfig(pairs(c)).float) {
        FloatValue.LEFT, FloatValue.INLINE_START -> FloatValue.LEFT
        FloatValue.RIGHT, FloatValue.INLINE_END -> FloatValue.RIGHT
        else -> null
    }
    /** No own text/runs/pseudos/live buckets, and a UA-margin-free tag (div or none). */
    private fun plainBox(c: IRComponent): Boolean =
        c._text.isNullOrEmpty() && c.runs.isNullOrEmpty() && c.pseudos == null &&
            c.selectors.isEmpty() && c.media.isEmpty() && (c._tag == null || c._tag.lowercase() == "div")
    /** Box-model bands / axis remaps this lane's px arithmetic does not model. */
    private fun boxBails(c: IRComponent): Boolean = c.properties.any { p ->
        p.type.startsWith("Margin") || p.type.startsWith("Padding") || p.type.startsWith("Border") || p.type in BOX_BAILS
    }
    /** Every painted descendant is an inline-level px-wide atom (Appendix E step 7), recursively. */
    private fun paintedDescendantsInline(c: IRComponent): Boolean = c.children.orEmpty().all { d ->
        val dp = pairs(d)
        (dp.none { paints(it.first) } || (keyword(dp, "Display") in INLINE_LEVEL && lengthPx(d, "Width") != null)) &&
            paintedDescendantsInline(d)
    }
    /** Wires that paint the box itself (outline excluded: Chromium paints it on top too). */
    private fun paints(type: String): Boolean = type.startsWith("Background") || type.startsWith("Border") || type == "BoxShadow"
    /** Own text anywhere in the subtree (leaf `_text` or `meta.runs`). */
    private fun hasText(c: IRComponent): Boolean = !c._text.isNullOrEmpty() || !c.runs.isNullOrEmpty() || c.children.orEmpty().any(::hasText)
    /** Any box in the subtree declaring [type]. */
    private fun hasWire(c: IRComponent, type: String): Boolean = c.properties.any { it.type == type } || c.children.orEmpty().any { hasWire(it, type) }
    /** Plain-px length wire (`{"type":"length","px":N}` or `{"px":N}`); % and keywords → null. */
    private fun lengthPx(c: IRComponent, type: String): Double? {
        val obj = c.properties.firstOrNull { it.type == type }?.data as? JsonObject ?: return null
        if ((obj["type"] as? JsonPrimitive)?.content == "percentage") return null // needs live context
        return (obj["px"] as? JsonPrimitive)?.doubleOrNull
    }
    /** SHOUTY-normalized keyword of the first [type] wire (null when absent or not a keyword). */
    private fun keyword(p: List<Pair<String, JsonElement?>>, type: String): String? =
        p.firstOrNull { it.first == type }?.second?.let { ValueExtractors.extractKeyword(it)?.uppercase()?.replace('-', '_') }
    /** The extractors' (type, data) input shape. */
    private fun pairs(c: IRComponent): List<Pair<String, JsonElement?>> = c.properties.map { it.type to it.data }

    // G3 container refusals: block-axis remaps, gaps the VStack/Column would insert, multicol, sizing folds.
    private val CONTAINER_BAILS = setOf(
        "WritingMode", "Direction", "Gap", "RowGap", "ColumnGap", "ColumnCount", "ColumnWidth",
        "BoxSizing", "Float", "MinWidth", "MaxWidth", "InlineSize", "MinInlineSize", "MaxInlineSize",
    )
    // G6/G7 float + BFC refusals (Margin*/Padding*/Border* prefixes are checked in boxBails).
    private val BOX_BAILS = setOf(
        "BoxSizing", "MinWidth", "MaxWidth", "MinHeight", "MaxHeight", "InlineSize", "BlockSize",
        "MinInlineSize", "MaxInlineSize", "MinBlockSize", "MaxBlockSize", "AspectRatio",
        "WritingMode", "Direction", "Order", "Transform", "Translate", "Scale", "Rotate",
    )
    private val BLOCK_DISPLAYS = setOf(null, "BLOCK", "FLOW_ROOT")   // absent = block (div / anonymous)
    private val STATIC = setOf(null, "STATIC")                       // absent = the initial `static`
    private val FLOAT_POSITIONS = setOf(null, "STATIC", "RELATIVE")  // §9.7: the positions a float keeps
    private val INTRINSIC_WIDTHS = setOf("FIT_CONTENT", "MIN_CONTENT", "MAX_CONTENT") // css-sizing-3 §3.2
    private val INLINE_LEVEL = setOf("INLINE", "INLINE_BLOCK", "INLINE_FLEX", "INLINE_GRID", "INLINE_TABLE")
}
