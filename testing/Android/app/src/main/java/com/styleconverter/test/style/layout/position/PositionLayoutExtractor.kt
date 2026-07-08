package com.styleconverter.test.style.layout.position

// Phase 7b position style-engine extractor.
//
// Produces the position-related subset of the aggregate
// [com.styleconverter.test.style.layout.LayoutConfig]:
//   - position keyword -> PositionKind enum
//   - logical (inset-block-*, inset-inline-*) + physical (top/right/bottom/
//     left) sides reconciled under the current LayoutDirection into a single
//     [InsetRect] with physical-only fields
//   - z-index integer (null == CSS "auto")
//
// The legacy [PositionExtractor] remains untouched (still feeds PositionApplier
// which services the existing ComponentRenderer path). This one services the
// new LayoutConfig surface. The split mirrors the grid split in
// GridLayoutExtractor — see that file for the rationale.

import androidx.compose.ui.unit.LayoutDirection
import com.styleconverter.test.style.core.types.ValueExtractors
import com.styleconverter.test.style.layout.InsetRect
import com.styleconverter.test.style.layout.PositionKind
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Result bundle for [PositionLayoutExtractor.extract]. Mirrors the grid
 * extract shape: a small POJO the aggregate builder copies onto the real
 * LayoutConfig fields in one pass.
 */
data class PositionLayoutExtract(
    val position: PositionKind? = null,
    val inset: InsetRect? = null,
    val zIndex: Int? = null,
)

object PositionLayoutExtractor {

    /**
     * Extract the position-family fields.
     *
     * [layoutDirection] drives logical→physical inset resolution. We default
     * to LTR because that's the Compose-default and matches every existing
     * fixture; RTL support is unit-tested by the logical-inset test.
     */
    fun extract(
        properties: List<Pair<String, JsonElement?>>,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ): PositionLayoutExtract {
        // Two passes conceptually: (1) collect raw physical + logical sides
        // + z-index + position keyword; (2) reconcile logicals to physicals.
        // A single pass with nullable intermediates is equivalent and shorter.
        var position: PositionKind? = null
        var top: Float? = null
        var right: Float? = null
        var bottom: Float? = null
        var left: Float? = null
        var insetBlockStart: Float? = null
        var insetBlockEnd: Float? = null
        var insetInlineStart: Float? = null
        var insetInlineEnd: Float? = null
        var zIndex: Int? = null
        // Track whether the user supplied any position-family property at
        // all — distinguishes "nothing specified" (return null fields) from
        // "specified `static` with no offsets" (return PositionKind.Static).
        var sawAnything = false

        for ((type, data) in properties) {
            when (type) {
                "Position" -> {
                    sawAnything = true
                    position = parsePositionKind(data)
                }
                "Top" -> { sawAnything = true; top = dpFloat(data) }
                "Right" -> { sawAnything = true; right = dpFloat(data) }
                "Bottom" -> { sawAnything = true; bottom = dpFloat(data) }
                "Left" -> { sawAnything = true; left = dpFloat(data) }
                "InsetBlockStart" -> { sawAnything = true; insetBlockStart = dpFloat(data) }
                "InsetBlockEnd" -> { sawAnything = true; insetBlockEnd = dpFloat(data) }
                "InsetInlineStart" -> { sawAnything = true; insetInlineStart = dpFloat(data) }
                "InsetInlineEnd" -> { sawAnything = true; insetInlineEnd = dpFloat(data) }
                "ZIndex" -> {
                    sawAnything = true
                    zIndex = parseZIndex(data)
                }
            }
        }

        if (!sawAnything) return PositionLayoutExtract()

        // Reconcile logical -> physical. Horizontal writing mode is the only
        // one Compose/Android actually supports, so block = vertical axis,
        // inline = horizontal axis. RTL flips inline-start ↔ inline-end.
        //
        // Precedence per CSS Logical Properties Level 1: when both a
        // physical and a logical property target the same side, the LATER
        // declaration wins in source order. We're past that point here —
        // the caller's property list is already in declaration order, and
        // the per-side variables above overwrite each other accordingly.
        // What's left: if the physical side is null but the logical isn't,
        // fall through to the logical value.
        val physicalTop = top ?: insetBlockStart
        val physicalBottom = bottom ?: insetBlockEnd
        val (physicalLeft, physicalRight) = when (layoutDirection) {
            LayoutDirection.Ltr -> Pair(left ?: insetInlineStart, right ?: insetInlineEnd)
            LayoutDirection.Rtl -> Pair(left ?: insetInlineEnd, right ?: insetInlineStart)
        }

        val anyInset = listOf(physicalTop, physicalBottom, physicalLeft, physicalRight).any { it != null }
        val inset = if (anyInset) InsetRect(
            top = physicalTop,
            right = physicalRight,
            bottom = physicalBottom,
            left = physicalLeft,
        ) else null

        return PositionLayoutExtract(
            position = position,
            inset = inset,
            zIndex = zIndex,
        )
    }

    /** Position keyword → enum. Unknown/absent → null (engine stays out). */
    private fun parsePositionKind(json: JsonElement?): PositionKind? {
        val kw = ValueExtractors.extractKeyword(json)?.lowercase() ?: return null
        return when (kw) {
            "static" -> PositionKind.Static
            "relative" -> PositionKind.Relative
            "absolute" -> PositionKind.Absolute
            "fixed" -> PositionKind.Fixed
            "sticky" -> PositionKind.Sticky
            else -> null
        }
    }

    /**
     * Convert a length-shaped JSON value to a Float in dp-units. The
     * aggregate InsetRect holds Float (not Dp) to keep the root LayoutConfig
     * free of Compose imports (see LayoutConfig.kt header comment).
     * Returns null for "auto" / missing. The legacy ValueExtractors.extractDp
     * returns null for auto — we piggyback on that to get identical behavior.
     */
    private fun dpFloat(json: JsonElement?): Float? {
        val dp = ValueExtractors.extractDp(json) ?: return null
        return dp.value
    }

    /**
     * z-index parser. CSS allows integers (including negative) or the
     * keyword "auto". We return null for auto / missing; Int otherwise.
     */
    private fun parseZIndex(json: JsonElement?): Int? {
        // Integer form first — the most common IR shape.
        ValueExtractors.extractInt(json)?.let { return it }
        // Fallback: accept "auto" keyword; accept float-as-int.
        val primitive = json as? JsonPrimitive ?: return null
        val s = primitive.contentOrNull?.trim()?.lowercase() ?: return null
        if (s == "auto") return null
        return s.toIntOrNull() ?: s.toFloatOrNull()?.toInt()
    }
}
