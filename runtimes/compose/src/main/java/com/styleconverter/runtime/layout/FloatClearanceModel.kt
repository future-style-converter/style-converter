package com.styleconverter.runtime.layout

// FloatClearanceModel — the IR→box projection half of the wave-42 lane-W5
// CSS 2.2 §9.5.2 clearance emulation (the pure math lives in
// FloatClearance.kt; the Compose adapters in ClearanceZeroFlow.kt).
// Byte-parallel twin of iOS StyleEngine/layout/FloatClearance.swift's
// projection half — same bail set, same field names, so the two natives'
// pin tables can be diffed line-for-line.
//
// The projection is deliberately STRICT: any subtree box whose geometry
// this lane cannot prove from plain-px wire values makes the WHOLE scope
// resolve to null (identity — the frozen pre-wave-42 rendering). That is
// the honesty contract: the §9.5.2 plan only fires when every simulated
// position is exact, never as a guess.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.types.LengthValue
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * One projected box of a clearance scope: the §9.5.2-relevant facts of an
 * IRComponent, with every length already reduced to plain CSS px.
 * Built ONLY by [FloatClearanceModel.project]; a failed projection of ANY
 * subtree box aborts the whole scope (see file banner).
 */
internal class ClearanceNode(
    // The projected component — id keys the plan, children drive the walk.
    val comp: IRComponent,
    // Tree link for the §8.3.1 ascent (null only for the scope root).
    val parent: ClearanceNode?,
    // Physical float side per the LTR-normalized engine (FloatExtractor is
    // the single owner of float/clear keyword parsing on this platform).
    val floatSide: FloatValue,
    // Clear keyword (NONE for non-clearing boxes) — same single owner.
    val clear: ClearValue,
    // Declared block-axis margins in px (0 when undeclared — the CSS
    // initial value). Negative values are LEGAL here (unlike the §8.3.1
    // collapse plan) because §9.5.2's absorbed-chain math needs them
    // (clear-on-parent-with-margins' -1000px child).
    val marginTopPx: Double,
    val marginBottomPx: Double,
    // Block-start/-end padding in px (0 when undeclared). paddingTop gates
    // both the §8.3.1 descent (adjoining needs "no top padding") and the
    // ascent crossing; paddingBottom only feeds the zero-outer predicate.
    val paddingTopPx: Double,
    val paddingBottomPx: Double,
    // Explicit used height in px, or null for height:auto.
    val heightPx: Double?,
    // Establishes a block formatting context (overflow non-visible /
    // display:flow-root): §8.3.1 "margins of elements that establish new
    // block formatting contexts do not collapse with their in-flow
    // children" — blocks both the descent and the ascent at this box.
    val bfcRoot: Boolean,
) {
    // Children in wire order (the order both natives render block children).
    val children = mutableListOf<ClearanceNode>()
}

internal object FloatClearanceModel {

    /**
     * Project [comp]'s subtree into [ClearanceNode]s, or null when any box
     * carries a value flavor outside this lane's proven scope (the strict
     * bail contract in the file banner). Pure — JVM-pinnable without
     * Compose.
     */
    fun project(comp: IRComponent, parent: ClearanceNode?): ClearanceNode? {
        // Text content makes flow heights unknowable to a pure pass (glyph
        // metrics are a device concern) — bail the whole scope.
        if (!comp._text.isNullOrEmpty()) return null
        // Interleaved inline runs re-order painting (wave 32) — same bail.
        if (!comp.runs.isNullOrEmpty()) return null
        // Live selector/media buckets can re-style any of the reads below
        // after the static plan baked — bail (mirrors collapse bail B6).
        if (comp.selectors.isNotEmpty() || comp.media.isNotEmpty()) return null
        // Generated content paints boxes the projection cannot see.
        if (comp.pseudos != null) return null
        // UA default margins are tag-keyed (wave-25 lane UAM): any tag
        // outside the margin-free set would need the UA fold modeled here
        // too — only bare/div boxes are in scope (all six lane fixtures).
        val tag = comp._tag?.lowercase()
        if (tag != null && tag != "div") return null
        // Per-property bails + reads, one pass over the wire list.
        val pairs = comp.properties.map { it.type to it.data }
        for ((type, data) in pairs) {
            when {
                // Any border could add block-start bands the position math
                // would have to model — none of the in-scope fixtures
                // carry borders, so presence bails (conservative).
                type.startsWith("Border") -> return null
                // Unmodeled block-size pins / ratios (only Height is read).
                type in UNMODELED_SIZE_TYPES -> return null
                // Vertical writing modes / rtl direction re-map the block
                // axis this lane's y-math assumes (css-writing-modes-4 §2).
                type == "WritingMode" || type == "Direction" -> return null
                // `order` re-sorts iOS's ForEach (FlexboxApplier.sorted) —
                // wire order would no longer be render order.
                type == "Order" -> return null
                // Transforms establish containing blocks + move ink.
                type in TRANSFORM_TYPES -> return null
                // Row gaps insert stack spacing the simulation omits.
                type == "Gap" || type == "RowGap" || type == "ColumnGap" -> return null
                // Multicol containers leave the plain block stack entirely.
                type == "ColumnCount" || type == "ColumnWidth" -> return null
                // Any positioning scheme but static: positioned containers
                // take the overlay branch, and even `relative` children can
                // paint away from their flow slot (CSS 2.1 §9.4.3).
                type == "Position" ->
                    if (keywordOf(data) != "STATIC") return null
                // Display: only plain block flow (and flow-root, which is
                // block flow + BFC, read again below) is modeled.
                type == "Display" ->
                    if (keywordOf(data) !in ALLOWED_DISPLAY) return null
            }
        }
        // Block-axis margins via the production margin reader (single
        // owner), then reduced to plain px — auto/relative/calc bail.
        val margins = com.styleconverter.runtime.spacing.SpacingExtractor
            .extractMarginConfig(pairs).resolve(isRtl = false)
        val mt = marginPx(margins.top) ?: return null
        val mb = marginPx(margins.bottom) ?: return null
        // Block-axis paddings, same single-owner discipline (non-negative
        // per css-box-4 §4.2 — negative padding is invalid CSS).
        val paddings = com.styleconverter.runtime.spacing.PaddingExtractor
            .extract(pairs).resolve(isRtl = false)
        val pt = paddingPx(paddings.top) ?: return null
        val pb = paddingPx(paddings.bottom) ?: return null
        // Explicit height: a plain-px length or absent; % / keywords bail.
        val height = when (val h = pairs.firstOrNull { it.first == "Height" }?.second) {
            null -> null
            else -> heightPx(h) ?: return null
        }
        // Float/clear keywords through the platform's single owner.
        val fc = FloatExtractor.extractFloatConfig(pairs)
        // A box that both floats and clears (floats-bfc-003's stacked
        // cleared floats) needs §9.5.1+§9.5.2 combined — out of scope.
        if (fc.float != FloatValue.NONE && fc.clear != ClearValue.NONE) return null
        // Logical float/clear members need the container's direction; the
        // Direction bail above pins the scope LTR, so INLINE_START→LEFT,
        // INLINE_END→RIGHT (css-logical-1 §2.1) via the shared facts table.
        val side = when (fc.float) {
            FloatValue.LEFT, FloatValue.INLINE_START -> FloatValue.LEFT
            FloatValue.RIGHT, FloatValue.INLINE_END -> FloatValue.RIGHT
            else -> FloatValue.NONE
        }
        // BFC roots: non-visible overflow on either axis (css-overflow-3
        // §2.1 — one non-visible axis already implies a scroll container)
        // or an explicit display:flow-root (css-display-3 §2.5).
        val overflow = com.styleconverter.runtime.scrolling.OverflowExtractor
            .extractOverflowConfig(pairs)
        val bfc = overflow.overflowX != com.styleconverter.runtime.scrolling.OverflowBehavior.VISIBLE ||
            overflow.overflowY != com.styleconverter.runtime.scrolling.OverflowBehavior.VISIBLE ||
            pairs.any { it.first == "Display" && keywordOf(it.second) == "FLOW_ROOT" }
        // Assemble this node, then project children depth-first; ANY child
        // bail aborts the whole scope (strict contract).
        val node = ClearanceNode(comp, parent, side, fc.clear, mt, mb, pt, pb, height, bfc)
        for (child in comp.children.orEmpty()) {
            node.children.add(project(child, node) ?: return null)
        }
        return node
    }

    /** IR keyword, SHOUTY-normalized like every keyword read in this engine. */
    private fun keywordOf(data: JsonElement?): String? =
        ValueExtractors.extractKeyword(data)?.uppercase()?.replace('-', '_')

    /** Margin side → px: unset = 0 (CSS initial), exact px passes SIGNED. */
    private fun marginPx(v: com.styleconverter.runtime.spacing.MarginValue?): Double? = when (v) {
        null -> 0.0
        is com.styleconverter.runtime.spacing.MarginValue.Length ->
            (v.value as? LengthValue.Exact)?.px
        else -> null
    }

    /** Padding side → px: unset = 0, exact non-negative px only. */
    private fun paddingPx(v: LengthValue?): Double? = when (v) {
        null -> 0.0
        is LengthValue.Exact -> v.px.takeIf { it >= 0.0 }
        else -> null
    }

    /** Height wire value → px: `{"type":"length","px":N}` (or bare {"px":N}). */
    private fun heightPx(data: JsonElement): Double? {
        // Percentage / keyword flavors need live context a pure pass lacks.
        val obj = data as? JsonObject ?: return null
        if ((obj["type"] as? JsonPrimitive)?.content == "percentage") return null
        return (obj["px"] as? JsonPrimitive)?.doubleOrNull
    }

    // Size-pinning wire types this lane does not model (Height is read).
    private val UNMODELED_SIZE_TYPES = setOf(
        "MinHeight", "MaxHeight", "MinBlockSize", "MaxBlockSize",
        "BlockSize", "AspectRatio",
    )

    // Transform family (css-transforms-1 §3 — containing-block makers).
    private val TRANSFORM_TYPES = setOf("Transform", "Translate", "Scale", "Rotate")

    // Display keywords whose boxes stay in the modeled plain block flow.
    private val ALLOWED_DISPLAY = setOf("BLOCK", "FLOW_ROOT")
}
