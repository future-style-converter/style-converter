package com.styleconverter.test.style.sizing

// Phase 3 SizingExtractor — reads the 13 sizing IR property types into a
// SizingConfig. All length-shaped IR goes through the Phase 1 extractLength()
// primitive, which already handles the following shapes that show up here:
//   {"type":"length","px":N}             — Width/Height absolute
//   {"type":"length","original":{v,u}}   — Width/Height relative (em/vw/…)
//   {"type":"percentage","value":N}      — Width/Height percent
//   {"type":"none"}                      — MinWidth/MaxWidth `none`
//   {"fit-content":<inner>}              — bounded fit-content
//   {"px":N} / bare-number / "auto"…     — SizeValue shape for logical sizes
//
// AspectRatio has its own wire shape, so we dispatch to extractAspectRatio().

import com.styleconverter.test.style.core.types.LengthValue
import com.styleconverter.test.style.core.types.extractLength
import kotlinx.serialization.json.JsonElement

object SizingExtractor {

    /**
     * Property-type strings owned by this extractor. Used by LayoutFacade
     * (isLayoutProperty) to route IR entries to us.
     */
    val PROPERTIES: Set<String> = setOf(
        "Width", "Height",
        "MinWidth", "MaxWidth", "MinHeight", "MaxHeight",
        "BlockSize", "InlineSize",
        "MinBlockSize", "MaxBlockSize", "MinInlineSize", "MaxInlineSize",
        "AspectRatio",
    )

    /**
     * Fold (type,data) pairs into a SizingConfig. Unknown sizing shapes are
     * dropped (treated as "not specified") so callers don't need to distinguish
     * "IR said Unknown" from "property absent".
     */
    fun extractSizingConfig(properties: List<Pair<String, JsonElement?>>): SizingConfig {
        var cfg = SizingConfig()
        // Walk once, dispatch per property. Last occurrence wins (matches CSS
        // cascade for same-specificity rules).
        for ((type, data) in properties) {
            if (type !in PROPERTIES) continue
            cfg = when (type) {
                // Physical sizing. extractLength handles WidthValue shape +
                // the new None/fit-content extensions for min/max.
                "Width" -> cfg.copy(width = asSize(data))
                "Height" -> cfg.copy(height = asSize(data))
                "MinWidth" -> cfg.copy(minWidth = asSize(data))
                "MaxWidth" -> cfg.copy(maxWidth = asSize(data))
                "MinHeight" -> cfg.copy(minHeight = asSize(data))
                "MaxHeight" -> cfg.copy(maxHeight = asSize(data))
                // Logical sizing. Same extractLength path: SizeValue shape is
                // either a raw IRLength ({"px":N}), a bare number (percent),
                // or an intrinsic keyword — all already handled by Phase 2.
                "BlockSize" -> cfg.copy(blockSize = asSize(data))
                "InlineSize" -> cfg.copy(inlineSize = asSize(data))
                "MinBlockSize" -> cfg.copy(minBlockSize = asSize(data))
                "MaxBlockSize" -> cfg.copy(maxBlockSize = asSize(data))
                "MinInlineSize" -> cfg.copy(minInlineSize = asSize(data))
                "MaxInlineSize" -> cfg.copy(maxInlineSize = asSize(data))
                // AspectRatio uses its own wire shape.
                "AspectRatio" -> cfg.copy(aspectRatio = extractAspectRatio(data))
                else -> cfg
            }
        }
        return cfg
    }

    /**
     * Run extractLength and swallow Unknown — we want `null` (slot unset) when
     * the IR shape is not one we recognise, so the Applier can distinguish
     * "property absent" from "explicit none/auto".
     */
    private fun asSize(data: JsonElement?): LengthValue? {
        val v = extractLength(data)
        return if (v is LengthValue.Unknown) null else v
    }

    /** Predicate used by LayoutFacade dispatch. */
    fun isSizingProperty(type: String): Boolean = type in PROPERTIES
}
