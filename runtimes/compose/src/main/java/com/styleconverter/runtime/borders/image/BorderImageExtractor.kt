package com.styleconverter.runtime.borders.image

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.PropertyRegistry
import com.styleconverter.runtime.core.types.LengthValue
import com.styleconverter.runtime.core.types.ValueExtractors
import com.styleconverter.runtime.spacing.PaddingExtractor
import com.styleconverter.runtime.spacing.SpacingContext
import com.styleconverter.runtime.spacing.resolveToDp
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
// The single-keyword `border-image-repeat` wire is a BARE STRING primitive
// (see extractBorderImageRepeat) — retro R6, A6#0.
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts border-image configuration from IR properties.
 */
object BorderImageExtractor {

    init {
        // The 5 CSS border-image longhands (source / slice / width / outset /
        // repeat). The applier is a Composable (BorderImageBox) rather than
        // a Modifier, so the legacy dispatch still has to route to it — we
        // register them here only so the coverage report knows they're owned.
        PropertyRegistry.migrated(
            "BorderImageSource", "BorderImageSlice", "BorderImageWidth",
            "BorderImageOutset", "BorderImageRepeat",
            owner = "borders/image"
        )
    }

    /**
     * Extract complete border-image configuration from property pairs.
     */
    fun extractBorderImageConfig(properties: List<Pair<String, JsonElement?>>): BorderImageConfig {
        var source: BorderImageSourceValue = BorderImageSourceValue.None
        var sliceTop: BorderImageSliceEdge? = null
        var sliceRight: BorderImageSliceEdge? = null
        var sliceBottom: BorderImageSliceEdge? = null
        var sliceLeft: BorderImageSliceEdge? = null
        var sliceFill = false
        var widthTop: BorderImageDimension? = null
        var widthRight: BorderImageDimension? = null
        var widthBottom: BorderImageDimension? = null
        var widthLeft: BorderImageDimension? = null
        var outsetTop: BorderImageDimension? = null
        var outsetRight: BorderImageDimension? = null
        var outsetBottom: BorderImageDimension? = null
        var outsetLeft: BorderImageDimension? = null
        var repeatHorizontal = BorderImageRepeatValue.STRETCH
        var repeatVertical = BorderImageRepeatValue.STRETCH

        for ((type, data) in properties) {
            when (type) {
                "BorderImageSource" -> source = extractBorderImageSource(data)
                "BorderImageSlice" -> {
                    val result = extractBorderImageSlice(data)
                    sliceTop = result.top
                    sliceRight = result.right
                    sliceBottom = result.bottom
                    sliceLeft = result.left
                    sliceFill = result.fill
                }
                "BorderImageWidth" -> {
                    val result = extractBorderImageFourValues(data)
                    widthTop = result.top
                    widthRight = result.right
                    widthBottom = result.bottom
                    widthLeft = result.left
                }
                "BorderImageOutset" -> {
                    val result = extractBorderImageFourValues(data)
                    outsetTop = result.top
                    outsetRight = result.right
                    outsetBottom = result.bottom
                    outsetLeft = result.left
                }
                "BorderImageRepeat" -> {
                    val result = extractBorderImageRepeat(data)
                    repeatHorizontal = result.first
                    repeatVertical = result.second
                }
            }
        }

        // Resolve the element's COMPUTED border widths — the §5.3 basis for
        // `<number>` border-image-width values (and for the initial value 1
        // when no border-image-width is declared). css-backgrounds-3 §4.3:
        // a side whose border-style is none/hidden/absent computes to width
        // 0, which BorderSideConfig.hasBorder already encodes (width>0 AND
        // style != NONE). Reuses BorderSideExtractor so both consumers stay
        // in lockstep on the width/style gating rules.
        val sides = com.styleconverter.runtime.borders.sides.BorderSideExtractor
            .extractBorderConfig(properties)
        fun computed(side: com.styleconverter.runtime.borders.sides.BorderSideConfig): androidx.compose.ui.unit.Dp =
            if (side.hasBorder) side.width ?: androidx.compose.ui.unit.Dp(0f)
            else androidx.compose.ui.unit.Dp(0f)

        // Resolve the element's CSS padding — the second dest-compensation
        // basis alongside the computed border widths. ComponentRenderer
        // hands BorderImageBox the component's FULL modifier chain, whose
        // LayoutFacade padding (PaddingApplier.apply) shrinks the DrawScope
        // box just like borderContentInset does, so the drawBehind must
        // expand the destination back out by exactly what the chain inset.
        // Mirror PaddingApplier byte-for-byte: logical→physical collapse
        // with the same LTR default (the renderer doesn't plumb
        // LayoutDirection into either consumer yet — Phase 3 note in
        // PaddingApplier), reusing PaddingExtractor so both consumers stay
        // in lockstep on the 8-longhand precedence rules.
        val paddingSides = PaddingExtractor.extract(properties).resolve(isRtl = false)
        // Resolution mirrors PaddingApplier.apply: the DEFAULT
        // SpacingContext (font 16px / viewport 390×844 — the exact context
        // SpacingApplier.applyPadding used on the layout side), clamped at
        // 0 because CSS padding is never negative (spec clamp, and the
        // layout chain never applied a negative inset to undo).
        fun resolvedPad(value: LengthValue?): Dp =
            resolveToDp(value, SpacingContext()).let { if (it.value < 0f) 0.dp else it }

        return BorderImageConfig(
            source = source,
            sliceTop = sliceTop,
            sliceRight = sliceRight,
            sliceBottom = sliceBottom,
            sliceLeft = sliceLeft,
            sliceFill = sliceFill,
            widthTop = widthTop,
            widthRight = widthRight,
            widthBottom = widthBottom,
            widthLeft = widthLeft,
            outsetTop = outsetTop,
            outsetRight = outsetRight,
            outsetBottom = outsetBottom,
            outsetLeft = outsetLeft,
            repeatHorizontal = repeatHorizontal,
            repeatVertical = repeatVertical,
            computedBorderTop = computed(sides.top),
            computedBorderRight = computed(sides.end),
            computedBorderBottom = computed(sides.bottom),
            computedBorderLeft = computed(sides.start),
            // Physical padding sides, resolved above with the same context
            // the layout chain used — feeds the drawBehind dest expansion.
            resolvedPaddingTop = resolvedPad(paddingSides.top),
            resolvedPaddingRight = resolvedPad(paddingSides.right),
            resolvedPaddingBottom = resolvedPad(paddingSides.bottom),
            resolvedPaddingLeft = resolvedPad(paddingSides.left)
        )
    }

    /**
     * Extract border-image-source from IR data.
     */
    fun extractBorderImageSource(data: JsonElement?): BorderImageSourceValue {
        if (data == null) return BorderImageSourceValue.None
        if (data !is JsonObject) return BorderImageSourceValue.None

        val sourceData = (data["source"] as? JsonObject) ?: data
        val type = sourceData["type"]?.jsonPrimitive?.contentOrNull

        return when (type?.lowercase()) {
            "url" -> {
                val url = sourceData["url"]?.jsonPrimitive?.contentOrNull ?: ""
                BorderImageSourceValue.Url(url)
            }
            "gradient" -> {
                val gradient = sourceData["gradient"]?.jsonPrimitive?.contentOrNull ?: ""
                BorderImageSourceValue.Gradient(gradient)
            }
            "none" -> BorderImageSourceValue.None
            else -> BorderImageSourceValue.None
        }
    }

    /**
     * Extract border-image-slice values.
     */
    private fun extractBorderImageSlice(data: JsonElement?): SliceResult {
        if (data == null) return SliceResult()
        if (data !is JsonObject) return SliceResult()

        val top = extractSliceEdge(data["top"])
        val right = extractSliceEdge(data["right"])
        val bottom = extractSliceEdge(data["bottom"])
        val left = extractSliceEdge(data["left"])
        val fill = data["fill"]?.jsonPrimitive?.contentOrNull?.lowercase() == "true" ||
                   data["fill"]?.jsonPrimitive?.contentOrNull?.lowercase() == "fill"

        return SliceResult(top, right, bottom, left, fill)
    }

    private fun extractSliceEdge(data: JsonElement?): BorderImageSliceEdge? {
        if (data == null) return null
        if (data !is JsonObject) return null

        val type = data["type"]?.jsonPrimitive?.contentOrNull
        return when (type?.lowercase()) {
            "number" -> {
                val value = data["value"]?.jsonPrimitive?.floatOrNull
                    ?: (data["value"] as? JsonObject)?.get("value")?.jsonPrimitive?.floatOrNull
                    ?: return null
                BorderImageSliceEdge(value, isPercentage = false)
            }
            "percentage" -> {
                val value = data["percentage"]?.jsonPrimitive?.floatOrNull
                    ?: data["value"]?.jsonPrimitive?.floatOrNull
                    ?: return null
                BorderImageSliceEdge(value, isPercentage = true)
            }
            else -> null
        }
    }

    /**
     * Extract four-value properties (width, outset).
     */
    private fun extractBorderImageFourValues(data: JsonElement?): FourValuesResult {
        if (data == null) return FourValuesResult()
        if (data !is JsonObject) return FourValuesResult()

        val top = extractDimension(data["top"])
        val right = extractDimension(data["right"])
        val bottom = extractDimension(data["bottom"])
        val left = extractDimension(data["left"])

        return FourValuesResult(top, right, bottom, left)
    }

    private fun extractDimension(data: JsonElement?): BorderImageDimension? {
        if (data == null) return null
        if (data !is JsonObject) return null

        val type = data["type"]?.jsonPrimitive?.contentOrNull
        return when (type?.lowercase()) {
            "auto" -> BorderImageDimension.Auto
            "length" -> {
                val dp = ValueExtractors.extractDp(data)
                if (dp != null) BorderImageDimension.Length(dp) else null
            }
            "percentage" -> {
                val value = data["percentage"]?.jsonPrimitive?.floatOrNull
                    ?: data["value"]?.jsonPrimitive?.floatOrNull
                    ?: return null
                BorderImageDimension.Percentage(value)
            }
            "number" -> {
                val value = data["value"]?.jsonPrimitive?.floatOrNull ?: return null
                BorderImageDimension.Number(value)
            }
            else -> null
        }
    }

    /**
     * Extract border-image-repeat values — (horizontal, vertical).
     *
     * TWO wire shapes, both live (retro R6, A6#0 — pinned against
     * `./gradlew :converter:run … -i fixtures/properties/borders/
     * border-image-repeat.json`):
     *   "ROUND"                                       — the single-keyword form
     *                                                   is a BARE STRING primitive
     *   {"horizontal": "REPEAT", "vertical": "STRETCH"} — the two-keyword form
     * The old reader accepted only the object and threw the primitive away
     * (`if (data !is JsonObject) return stretch/stretch`), so `border-image-
     * repeat: round | repeat | space` all rendered stretch/stretch on Android
     * while iOS's BorderImageExtractor.swift handled both shapes. Gate-
     * invisible (no corpus carrier), fixture-visible (BIR_Round / BIR_Space).
     *
     * css-backgrounds-3 §5.5 (border-image-repeat): the first keyword is the
     * horizontal (top/bottom edge) behaviour, the second the vertical; "If
     * the second keyword is absent, it is assumed to be the same as the
     * first." — the elvis below is that rule, live now that
     * [extractRepeatValue] returns null for a missing/unknown keyword.
     */
    private fun extractBorderImageRepeat(data: JsonElement?): Pair<BorderImageRepeatValue, BorderImageRepeatValue> {
        // Initial value `stretch` (§5.5) for an absent property.
        val stretch = Pair(BorderImageRepeatValue.STRETCH, BorderImageRepeatValue.STRETCH)
        if (data == null) return stretch
        // Single-keyword form: one keyword sets BOTH axes (§5.5).
        if (data is JsonPrimitive) {
            val both = extractRepeatValue(data.contentOrNull) ?: run {
                // Unknown keyword on the wire — say so, then fall back to the
                // initial value (no silent fallthrough).
                com.styleconverter.runtime.core.ir.IRLog.warn(
                    "BorderImageExtractor",
                    "BorderImageRepeat keyword not understood: $data — using stretch",
                )
                return stretch
            }
            return Pair(both, both)
        }
        if (data !is JsonObject) return stretch

        // Two-keyword form: horizontal first; a missing horizontal keyword
        // is the initial `stretch`.
        val horizontal = extractRepeatValue(data["horizontal"]?.jsonPrimitive?.contentOrNull)
            ?: BorderImageRepeatValue.STRETCH
        // §5.5 absent-second-keyword rule: vertical mirrors horizontal.
        val vertical = extractRepeatValue(data["vertical"]?.jsonPrimitive?.contentOrNull)
            ?: horizontal

        return Pair(horizontal, vertical)
    }

    /**
     * One `<repeat-style>` keyword → enum, or NULL for absent/unknown so
     * callers can apply their own default (the §5.5 mirror rule above needs
     * to tell "absent" from "stretch" — the old non-null return made the
     * `?: horizontal` fallback unreachable; kotlinc flagged it: "Elvis
     * operator (?:) always returns the left operand").
     */
    private fun extractRepeatValue(keyword: String?): BorderImageRepeatValue? {
        return when (keyword?.uppercase()) {
            "STRETCH" -> BorderImageRepeatValue.STRETCH
            "REPEAT" -> BorderImageRepeatValue.REPEAT
            "ROUND" -> BorderImageRepeatValue.ROUND
            "SPACE" -> BorderImageRepeatValue.SPACE
            else -> null
        }
    }

    /**
     * Check if a property type is border-image related.
     */
    fun isBorderImageProperty(type: String): Boolean {
        return type in BORDER_IMAGE_PROPERTIES
    }

    private val BORDER_IMAGE_PROPERTIES = setOf(
        "BorderImageSource", "BorderImageSlice", "BorderImageWidth",
        "BorderImageOutset", "BorderImageRepeat"
    )

    // Helper data classes
    private data class SliceResult(
        val top: BorderImageSliceEdge? = null,
        val right: BorderImageSliceEdge? = null,
        val bottom: BorderImageSliceEdge? = null,
        val left: BorderImageSliceEdge? = null,
        val fill: Boolean = false
    )

    private data class FourValuesResult(
        val top: BorderImageDimension? = null,
        val right: BorderImageDimension? = null,
        val bottom: BorderImageDimension? = null,
        val left: BorderImageDimension? = null
    )
}
