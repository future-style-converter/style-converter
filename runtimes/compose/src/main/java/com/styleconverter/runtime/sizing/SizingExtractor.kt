package com.styleconverter.runtime.sizing

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

import com.styleconverter.runtime.core.types.LengthValue
import com.styleconverter.runtime.core.types.extractLength
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
        //
        // Logical properties FOLD INTO the physical slots: css-logical-1 §1.1
        // cascades `inline-size` together with `width` (in the harness's
        // horizontal-tb LTR writing mode they are the same computed
        // property), so whichever is declared LAST must win. The previous
        // per-slot storage plus the applier's `width ?: inlineSize` made
        // physical ALWAYS win — Sizing_BoxModel's later `inline-size: 250px`
        // lost to the earlier `width: 240px`, and `block-size: auto` never
        // overrode `height: 48px` (Android box 240×48 vs web's 250×60).
        // The dedicated logical slots stay null; they remain on the config
        // only as wire-compat for external readers.
        for ((type, data) in properties) {
            if (type !in PROPERTIES) continue
            cfg = when (type) {
                // Inline axis (width in horizontal-tb).
                "Width", "InlineSize" -> cfg.copy(width = asSize(data))
                "MinWidth", "MinInlineSize" -> cfg.copy(minWidth = asSize(data))
                "MaxWidth", "MaxInlineSize" -> cfg.copy(maxWidth = asSize(data))
                // Block axis (height in horizontal-tb).
                "Height", "BlockSize" -> cfg.copy(height = asSize(data))
                "MinHeight", "MinBlockSize" -> cfg.copy(minHeight = asSize(data))
                "MaxHeight", "MaxBlockSize" -> cfg.copy(maxHeight = asSize(data))
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
