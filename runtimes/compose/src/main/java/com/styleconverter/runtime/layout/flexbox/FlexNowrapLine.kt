package com.styleconverter.runtime.layout.flexbox

// Wave 52 (lane L10, brief runtime-bugs-and-gaps §3 M2/M3) — a GATED
// single-line flex layout for the two shapes Compose's `Row` cannot draw.
// M3: Row measures child i with `maxWidth = remaining`, so six 50-px
// `flex-shrink: 0` items in a 200-px nowrap row (css-gaps 008/027) squeeze to
// 183/193/193; §9.7 with every shrink 0 keeps the hypothetical sizes and
// §9.5 packs them at start — the line OVERFLOWS (css-overflow-3 §2), x 0…300.
// M2: a negative main-axis margin is a paint offset only (MarginApplier.kt
// `Modifier.offset`), so Row advances 027's "Two" to 60 where §9.2 step 3 /
// §9.5 put it at −90 (a −150 margin-left item's OUTER size is −100).
// THE GATE (`engages`) is deliberately narrow: every other nowrap row or
// column (every RTL one included) stays on Row/Column byte-identically; the
// census (tools/titan/results/wave52-flex-nowrap-gaps/) admits 008 and 027 only.

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull

/** Gate, outer-size facts and §9.5 placement for one nowrap flex line. */
object FlexNowrapLine {

    /** Concrete px of the LAST declaration of [type], or null (absent / not a px leaf). */
    internal fun px(props: List<IRProperty>, type: String): Float? {
        // Last declaration wins — the cascade order the extractor emitted.
        val data = props.lastOrNull { it.type == type }?.data ?: return null
        // `{"px": n}` / `{"type":"length","px": n}` are the concrete shapes.
        return ((data as? JsonObject)?.get("px") as? JsonPrimitive)?.floatOrNull
    }

    /** Upper-cased keyword of the last [type] declaration, or null. */
    private fun keyword(props: List<IRProperty>, type: String): String? =
        // Plain-string wire (`"NOWRAP"`, `"ROW"`, `"ABSOLUTE"`).
        (props.lastOrNull { it.type == type }?.data as? JsonPrimitive)?.contentOrNull?.uppercase()

    /** A flex factor's normalized number (`{"normalizedValue": 0}`), or null when absent. */
    private fun factor(props: List<IRProperty>, type: String): Float? =
        // FlexGrow / FlexShrink both carry the resolved number here.
        ((props.lastOrNull { it.type == type }?.data as? JsonObject)
            ?.get("normalizedValue") as? JsonPrimitive)?.floatOrNull

    /** True when [type] is absent or a concrete px leaf (never auto / % / em). */
    private fun pxOrAbsent(props: List<IRProperty>, type: String): Boolean =
        props.none { it.type == type } || px(props, type) != null

    /**
     * css-flexbox-1 §9.2 step 3 — the NEGATIVE part of the two main-axis
     * margins, in IR px (== dp): the extent the outer size loses. The
     * positive part is already inside the measured box (absolutePadding).
     */
    fun outerDeltaPx(props: List<IRProperty>, rowAxis: Boolean): Float {
        // Physical start/end sides of the main axis (no reverse — gated).
        val (s, e) = if (rowAxis) "MarginLeft" to "MarginRight" else "MarginTop" to "MarginBottom"
        return minOf(0f, px(props, s) ?: 0f) + minOf(0f, px(props, e) ?: 0f)
    }

    /**
     * MarginApplier's paint shift (`negX = min(0,L) − min(0,R)`, same for y), IR px —
     * GapDecorationHook moves the item's slot by it to find the painted box. RTL (the
     * container's [inheritedRtl] or the item's own `direction`) mirrors x: Dp `Modifier.offset` is RTL-aware.
     */
    fun paintShiftPx(props: List<IRProperty>, inheritedRtl: Boolean): Pair<Float, Float> {
        // Negative part of one side, 0 for absent / non-px.
        fun neg(t: String) = minOf(0f, px(props, t) ?: 0f)
        val x = neg("MarginLeft") - neg("MarginRight")
        // The item's own RTL provider (ComponentRenderer) mirrors its offset too.
        val rtl = inheritedRtl || keyword(props, "Direction") == "RTL"
        // `0f - x`, not `-x`: never a −0f that Pair.equals tells from 0f.
        return (if (rtl) 0f - x else x) to (neg("MarginTop") - neg("MarginBottom"))
    }

    /**
     * THE GATE. True only for a non-reversed nowrap container with a px
     * main size, start packing and start/stretch cross alignment, whose
     * every child is an in-flow fixed-size box (px width AND height, no
     * grow, no basis, no align-self, px-or-absent margins), AND which
     * either carries a negative main-axis margin or overflows with every
     * shrink factor 0 — and never a line §9.7 would still have to shrink.
     * [rtl] (LocalLayoutDirection is Rtl at the container: own or inherited) refuses.
     */
    fun engages(container: List<IRProperty>, children: List<List<IRProperty>>,
                rowAxis: Boolean, hasText: Boolean, rtl: Boolean): Boolean {
        // An anonymous text item would be an extra, unsized flex item.
        if (hasText || children.isEmpty()) return false
        // RTL refuses: main/cross-start is the RIGHT edge (css-flexbox-1 §5.1, css-writing-modes-4
        // §2.1), Row mirrors there and MarginApplier's pull ignores CSS RTL margins — Row keeps it.
        if (rtl) return false
        // nowrap only (absent = initial nowrap); wrap stays on the wrap layouts.
        if (keyword(container, "FlexWrap").let { it != null && it != "NOWRAP" }) return false
        // No reverse direction (§5.1 would mirror the placement).
        val dir = keyword(container, "FlexDirection")
        if (dir != null && dir != if (rowAxis) "ROW" else "COLUMN") return false
        // Start packing only: §8.2 distribution is Row's job.
        if (keyword(container, "JustifyContent").let { it != null && it !in START }) return false
        // Cross alignment that puts a fixed-size item at cross-start.
        if (keyword(container, "AlignItems").let { it != null && it !in CROSS_START }) return false
        // The definite main size the line is compared against.
        val main = px(container, if (rowAxis) "Width" else "Height") ?: return false
        // The main-axis gap (absent = 0; a non-px gap refuses).
        val gapType = if (rowAxis) "ColumnGap" else "RowGap"
        if (!pxOrAbsent(container, gapType)) return false
        val gap = px(container, gapType) ?: 0f
        var outerSum = 0f
        var anyNegative = false
        var allShrinkZero = true
        for (c in children) {
            // Out-of-flow and floated children are not flex items (§4.1).
            if (keyword(c, "Position") in OUT_OF_FLOW || c.any { it.type == "Float" }) return false
            // Fixed-size boxes only: px on both axes.
            val size = px(c, if (rowAxis) "Width" else "Height") ?: return false
            if (px(c, if (rowAxis) "Height" else "Width") == null) return false
            // Nothing the §9.7 loop or §8.3 could change.
            if ((factor(c, "FlexGrow") ?: 0f) != 0f) return false
            if (c.any { it.type == "FlexBasis" || it.type == "AlignSelf" || it.type == "Order" }) return false
            // Margins must be concrete (auto would absorb free space, §8.1).
            if (MARGINS.any { !pxOrAbsent(c, it) }) return false
            // Outer size = size + positive margins + negative margins.
            val (s, e) = if (rowAxis) "MarginLeft" to "MarginRight" else "MarginTop" to "MarginBottom"
            val delta = outerDeltaPx(c, rowAxis)
            outerSum += size + maxOf(0f, px(c, s) ?: 0f) + maxOf(0f, px(c, e) ?: 0f) + delta
            anyNegative = anyNegative || delta < 0f
            allShrinkZero = allShrinkZero && factor(c, "FlexShrink") == 0f
        }
        val line = outerSum + gap * (children.size - 1)
        // Overflow with shrinking allowed is §9.7's job — not this layout's.
        if (line > main && !allShrinkZero) return false
        // M2 (negative margin) or M3 (overflow with every shrink factor 0).
        return anyNegative || line > main
    }

    /**
     * css-flexbox-1 §9.5 (start-packed, no free-space distribution): the
     * main-axis position of each item's slot, advancing by the OUTER size
     * (`sizes[i] + deltas[i]`) plus the gap — never clamped, so an
     * overflowing line runs past the container end (css-overflow-3 §2).
     */
    fun mainOffsets(sizes: IntArray, deltas: IntArray, gap: Int): IntArray {
        var cursor = 0
        return IntArray(sizes.size) { i ->
            // This slot starts at the cursor…
            val at = cursor
            // …and the next one after this item's OUTER size and the gap.
            cursor += sizes[i] + deltas.getOrElse(i) { 0 } + gap
            at
        }
    }

    /**
     * The layout. Children are measured with the MAIN axis unbounded (each
     * fixed-size item answers its own size — no `remaining` squeeze) and
     * placed at [mainOffsets]; the container keeps its own constraint size
     * and lets the line overflow, as Row does for a fitting line.
     */
    @Composable
    fun Line(modifier: Modifier, rowAxis: Boolean, gap: Dp, outerDeltas: List<Dp>,
             content: @Composable () -> Unit) {
        Layout(content = content, modifier = modifier) { measurables, constraints ->
            // Unbounded main axis, the container's own bound on the cross axis.
            val child = if (rowAxis) Constraints(maxHeight = constraints.maxHeight)
                else Constraints(maxWidth = constraints.maxWidth)
            val placeables = measurables.map { it.measure(child) }
            // Main sizes and outer deltas in whole pixels (Compose layout units).
            val sizes = IntArray(placeables.size) { if (rowAxis) placeables[it].width else placeables[it].height }
            val deltas = IntArray(placeables.size) { outerDeltas.getOrNull(it)?.roundToPx() ?: 0 }
            val at = mainOffsets(sizes, deltas, gap.roundToPx())
            // Content extents: the outer line on main, the tallest item on cross.
            val lineMain = (sizes.sum() + deltas.sum() + gap.roundToPx() * (sizes.size - 1)).coerceAtLeast(0)
            val cross = placeables.maxOfOrNull { if (rowAxis) it.height else it.width } ?: 0
            val w = constraints.constrainWidth(if (rowAxis) lineMain else cross)
            val h = constraints.constrainHeight(if (rowAxis) cross else lineMain)
            layout(w, h) {
                // Cross-start placement (the only alignment admitted). placeRelative mirrors x under
                // LayoutDirection.Rtl; the gate refuses RTL, so it equals `place` on every admitted line.
                placeables.forEachIndexed { i, p ->
                    if (rowAxis) p.placeRelative(at[i], 0) else p.placeRelative(0, at[i])
                }
            }
        }
    }

    /** justify-content keywords that pack at the main-start edge. */
    private val START = setOf("FLEX_START", "START", "NORMAL", "LEFT")
    /** align-items keywords that leave a fixed-cross-size item at cross-start. */
    private val CROSS_START = setOf("NORMAL", "STRETCH", "FLEX_START", "START", "SELF_START")
    /** Position keywords that take a child out of flow (css-position-3 §2). */
    private val OUT_OF_FLOW = setOf("ABSOLUTE", "FIXED")
    /** The four physical margin longhands. */
    private val MARGINS = listOf("MarginTop", "MarginRight", "MarginBottom", "MarginLeft")
}
