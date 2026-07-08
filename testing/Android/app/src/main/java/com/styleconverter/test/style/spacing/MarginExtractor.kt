package com.styleconverter.test.style.spacing

// Fold an IR property list into a MarginConfig. The tricky part here (vs
// PaddingExtractor) is the `auto` keyword: it reaches us as a bare JSON
// string "auto", NOT wrapped in a length shape. We detect that before
// deferring to extractLength().
//
// Fixture coverage: examples/properties/spacing/margin-basic.json and
// margin-units.json. margin-basic.json's `Margin_Auto_Left_Right` component
// exercises the mixed case (two sides "auto", two sides {px:0}).

import com.styleconverter.test.style.core.types.LengthValue
import com.styleconverter.test.style.core.types.extractLength
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

object MarginExtractor {

    /** Property-type strings owned by this extractor. */
    val PROPERTIES: Set<String> = setOf(
        "MarginTop", "MarginRight", "MarginBottom", "MarginLeft",
        "MarginBlockStart", "MarginBlockEnd",
        "MarginInlineStart", "MarginInlineEnd",
    )

    fun extract(properties: List<Pair<String, JsonElement?>>): MarginConfig {
        var top: MarginValue? = null
        var right: MarginValue? = null
        var bottom: MarginValue? = null
        var left: MarginValue? = null
        var blockStart: MarginValue? = null
        var blockEnd: MarginValue? = null
        var inlineStart: MarginValue? = null
        var inlineEnd: MarginValue? = null

        for ((type, data) in properties) {
            if (type !in PROPERTIES) continue
            val mv = toMarginValue(data) ?: continue
            when (type) {
                "MarginTop" -> top = mv
                "MarginRight" -> right = mv
                "MarginBottom" -> bottom = mv
                "MarginLeft" -> left = mv
                "MarginBlockStart" -> blockStart = mv
                "MarginBlockEnd" -> blockEnd = mv
                "MarginInlineStart" -> inlineStart = mv
                "MarginInlineEnd" -> inlineEnd = mv
            }
        }

        return MarginConfig(
            top = top, right = right, bottom = bottom, left = left,
            blockStart = blockStart, blockEnd = blockEnd,
            inlineStart = inlineStart, inlineEnd = inlineEnd,
        )
    }

    /**
     * Convert a single margin longhand's data into a MarginValue, or null
     * when the shape is unrecognised (then the Config slot stays unset).
     */
    private fun toMarginValue(data: JsonElement?): MarginValue? {
        if (data == null) return null
        // Fast-path: the IR encodes `margin: auto` as a bare JSON string.
        if (data is JsonPrimitive && data.isString && data.content == "auto") {
            return MarginValue.Auto
        }
        // Everything else is a length in one of the shapes extractLength handles.
        val len = extractLength(data)
        // Unknown → don't store anything; preserves null-means-unset invariant.
        if (len is LengthValue.Unknown) return null
        // `auto` could also arrive via extractLength (as LengthValue.Auto) if
        // we ever receive it wrapped oddly — map that back to MarginValue.Auto.
        if (len is LengthValue.Auto) return MarginValue.Auto
        return MarginValue.Length(len)
    }

    fun isMarginProperty(type: String): Boolean = type in PROPERTIES
}
