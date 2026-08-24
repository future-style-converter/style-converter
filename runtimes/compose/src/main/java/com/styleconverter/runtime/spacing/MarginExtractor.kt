package com.styleconverter.runtime.spacing

// Fold an IR property list into a MarginConfig. The tricky part here (vs
// PaddingExtractor) is the `auto` keyword: it reaches us as a bare JSON
// string "auto", NOT wrapped in a length shape. We detect that before
// deferring to extractLength().
//
// Fixture coverage: examples/properties/spacing/margin-basic.json and
// margin-units.json. margin-basic.json's `Margin_Auto_Left_Right` component
// exercises the mixed case (two sides "auto", two sides {px:0}).

import com.styleconverter.runtime.core.types.LengthUnit
import com.styleconverter.runtime.core.types.LengthValue
import com.styleconverter.runtime.core.types.extractLength
import com.styleconverter.runtime.core.types.parseLengthUnit
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

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
            // Wave-47 lane Z2 — the §6.4 mapping for the component's used
            // writing mode, read off the SAME (merged, inheritance-resolved)
            // list so an ancestor's `writing-mode: vertical-rl` reaches a
            // margin-only child (css-break background-image-001's .mc rows).
            // Null for every horizontal mode → resolve() is byte-identical
            // for all pre-existing content (LogicalSides contract).
            logicalSides = LogicalSides.verticalOrNull(properties),
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
        // wave-45 lane X4 — keep an `em` margin RESOLVABLE AT APPLY TIME even
        // when a px was prebaked beside it. The shape `{"px":N,"original":
        // {"v":V,"u":"EM"}}` is produced ONLY by DynamicValueResolver's
        // pre-pass (the converter never pre-resolves em — LengthParser emits
        // px=null for every font-relative unit), and that pre-pass resolves
        // em against ITS OWN font-size oracle, which does NOT model the CSS
        // Fonts 4 §3.5 fixed-default (`monospace` ⇒ 13px) quirk the
        // typography extractor folds into SpacingContext.fontSizePx (wave
        // 36, MonospaceUAFontSize). css-values-4 §5.1.1 makes the element's
        // OWN computed font-size the em base for a margin, so the applier's
        // context is the truthful basis. Measured defect (wave-44
        // css-overflow discard-multicol-001): `margin: 1em` on a
        // `font-family: monospace` div prebaked 16px against the ref's 13px
        // — border left edge 32 vs 29. Preserving Relative(EM) with the
        // prebaked px as fallback lets SpacingResolve multiply against the
        // quirk-corrected fontSizePx; every consumer whose basis AGREES with
        // the pre-pass (declared/inherited FontSize on the merged list — the
        // two oracles then read the same value) resolves byte-identically,
        // because v × fontSizePx equals the prebake by construction.
        emWithPrebakedPx(data)?.let { return MarginValue.Length(it) }
        // Everything else is a length in one of the shapes extractLength handles.
        val len = extractLength(data)
        // Unknown → don't store anything; preserves null-means-unset invariant.
        if (len is LengthValue.Unknown) return null
        // `auto` could also arrive via extractLength (as LengthValue.Auto) if
        // we ever receive it wrapped oddly — map that back to MarginValue.Auto.
        if (len is LengthValue.Auto) return MarginValue.Auto
        return MarginValue.Length(len)
    }

    /**
     * The DynamicValueResolver-prebaked em shape → a Relative(EM) carrying
     * the prebake as [LengthValue.Relative.pxFallback], or null for every
     * other shape (the caller then falls through to extractLength, which
     * treats a top-level px as canonical — unchanged for absolute units,
     * raw relative units, calc, keywords, and malformed data).
     *
     * Strictly narrower than extractLength's px-canonical rule on purpose:
     * ONLY the EM unit re-resolves at apply time (css-values-4 §5.1.1 own-
     * font-size basis, where the applier context is better informed than
     * the pre-pass — see toMarginValue). Other prebaked units (%/vw/rem…)
     * resolve against bases the pre-pass owns exclusively (containing
     * block, composed viewport, root size) and MUST keep their prebake.
     */
    private fun emWithPrebakedPx(data: JsonElement): LengthValue.Relative? {
        // Both the raw and the {"type":"length"} wrapper shapes qualify —
        // the pre-pass preserves whichever envelope the wire carried.
        val obj = data as? JsonObject ?: return null
        // No prebaked px → extractLength already yields Relative(EM, null);
        // nothing to preserve here (identity with the pre-wave-45 path).
        val px = obj["px"]?.jsonPrimitive?.doubleOrNull ?: return null
        // The preserved author value: {"v":V,"u":"EM"} under `original`.
        val original = obj["original"] as? JsonObject ?: return null
        val v = original["v"]?.jsonPrimitive?.doubleOrNull ?: return null
        // Unit gate — EM only (see kdoc). parseLengthUnit uppercases, so
        // hand-written fixtures with "em" match the wire's "EM" too.
        if (parseLengthUnit((original["u"] as? JsonPrimitive)?.contentOrNull) != LengthUnit.EM) {
            return null
        }
        return LengthValue.Relative(v, LengthUnit.EM, pxFallback = px)
    }

    fun isMarginProperty(type: String): Boolean = type in PROPERTIES
}
