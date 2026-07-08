package com.styleconverter.test.style.spacing

// Reads all 8 padding longhands out of an IR property list and builds a
// PaddingConfig. Uses the Phase 1 extractLength() primitive — every IR shape
// quirk (bare-number %, calc expr, negative px) is handled there, not here.
//
// Recognised IR keys (see IRDocument on the codegen side):
//   PaddingTop/Right/Bottom/Left
//   PaddingBlockStart/BlockEnd/InlineStart/InlineEnd

import com.styleconverter.test.style.core.types.LengthValue
import com.styleconverter.test.style.core.types.extractLength
import kotlinx.serialization.json.JsonElement

object PaddingExtractor {

    /** Property-type strings owned by this extractor. */
    val PROPERTIES: Set<String> = setOf(
        "PaddingTop", "PaddingRight", "PaddingBottom", "PaddingLeft",
        "PaddingBlockStart", "PaddingBlockEnd",
        "PaddingInlineStart", "PaddingInlineEnd",
    )

    /**
     * Fold the (type, data) list into a PaddingConfig. Unknown padding
     * longhands (none, we cover all 8) and non-padding entries are skipped.
     */
    fun extract(properties: List<Pair<String, JsonElement?>>): PaddingConfig {
        // Walk the property list once; for each padding longhand we see, drop
        // its LengthValue into the matching slot. The last occurrence wins
        // (matches CSS cascade for same-specificity rules).
        var top: LengthValue? = null
        var right: LengthValue? = null
        var bottom: LengthValue? = null
        var left: LengthValue? = null
        var blockStart: LengthValue? = null
        var blockEnd: LengthValue? = null
        var inlineStart: LengthValue? = null
        var inlineEnd: LengthValue? = null

        for ((type, data) in properties) {
            // Skip fast when the entry is not ours — keeps this O(n).
            if (type !in PROPERTIES) continue
            val len = extractLength(data)
            // Don't store Unknown values — that preserves the "null = not
            // specified" invariant the Applier relies on.
            if (len is LengthValue.Unknown) continue
            when (type) {
                "PaddingTop" -> top = len
                "PaddingRight" -> right = len
                "PaddingBottom" -> bottom = len
                "PaddingLeft" -> left = len
                "PaddingBlockStart" -> blockStart = len
                "PaddingBlockEnd" -> blockEnd = len
                "PaddingInlineStart" -> inlineStart = len
                "PaddingInlineEnd" -> inlineEnd = len
            }
        }

        return PaddingConfig(
            top = top,
            right = right,
            bottom = bottom,
            left = left,
            blockStart = blockStart,
            blockEnd = blockEnd,
            inlineStart = inlineStart,
            inlineEnd = inlineEnd,
        )
    }

    /** Predicate used by LayoutFacade / StyleApplier dispatch. */
    fun isPaddingProperty(type: String): Boolean = type in PROPERTIES
}
