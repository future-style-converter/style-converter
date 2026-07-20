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

import com.styleconverter.runtime.borders.sides.BorderSideConfig
import com.styleconverter.runtime.borders.sides.BorderSideExtractor
import com.styleconverter.runtime.core.types.LengthValue
import com.styleconverter.runtime.core.types.extractLength
import com.styleconverter.runtime.spacing.SpacingContext
import com.styleconverter.runtime.spacing.SpacingExtractor
import com.styleconverter.runtime.spacing.resolveToDp
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
     * Lane BX — `box-sizing` is CONSUMED here but deliberately NOT added to
     * [PROPERTIES]: that set feeds LayoutFacade.isLayoutProperty routing and
     * the registry claim already held by performance/ (BoxModelConfig keeps
     * extracting it as a no-op for wire-compat). Sizing reads the SAME IR
     * entry independently — extraction in this runtime is scan-everything,
     * not exclusive-dispatch, so double-reading is safe and keeps both the
     * registry ownership and coverage audit untouched.
     */
    private const val BOX_SIZING_TYPE = "BoxSizing"

    /**
     * Fold (type,data) pairs into a SizingConfig. Unknown sizing shapes are
     * dropped (treated as "not specified") so callers don't need to distinguish
     * "IR said Unknown" from "property absent".
     */
    fun extractSizingConfig(
        properties: List<Pair<String, JsonElement?>>,
        // TITAN WPT lane — true ONLY on the LocalWptCaptureMode capture
        // path (threaded from ComponentRenderer through StyleApplier /
        // LayoutFacade, the same plumbing pattern as collapsedMargin).
        // Default false keeps every existing call site — and therefore the
        // whole dark-stage/327-pair baseline corpus — byte-identical.
        wptCaptureMode: Boolean = false,
    ): SizingConfig {
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
            if (type !in PROPERTIES && type != BOX_SIZING_TYPE) continue
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
                // Lane BX — box-sizing tri-state (unset ≠ content-box).
                BOX_SIZING_TYPE -> cfg.copy(boxSizing = asBoxSizing(data))
                else -> cfg
            }
        }
        // TITAN WPT lane — resolve the tri-state BEFORE the band gate below
        // so a WPT-defaulted CONTENT_BOX also gets its inflation bands
        // (frame = content + padding + border needs the resolved px bands;
        // computing them here keeps SizingApplier a pure config→Modifier
        // function). effectiveBoxSizing is identity when wptCaptureMode is
        // false, so the tri-state pin (`absent stays null`) still holds on
        // every non-WPT path.
        cfg = cfg.copy(boxSizing = SizingApplier.effectiveBoxSizing(cfg.boxSizing, wptCaptureMode))
        // Lane BX — only an EXPLICIT content-box needs the padding+border
        // inflation bands; unset/border-box configs keep 0f so the Applier
        // is a guaranteed no-change on the whole existing fixture corpus.
        if (cfg.boxSizing == BoxSizingKeyword.CONTENT_BOX) {
            val (x, y) = contentBoxInflation(properties)
            cfg = cfg.copy(contentBoxInflateX = x, contentBoxInflateY = y)
        }
        return cfg
    }

    /**
     * Lane BX — decode the `box-sizing` wire value. Shape (see
     * BoxSizingPropertyParser.kt → single-field data class, flattened, and
     * the web decoder runtimes/web/src/engine/sizing/BoxSizingExtractor.ts):
     * bare SHOUTY enum string "CONTENT_BOX" | "BORDER_BOX". Anything else is
     * wire drift → null (slot stays UNSET; never guess content-box because
     * that would inflate every frame against the border-box baselines).
     */
    private fun asBoxSizing(data: JsonElement?): BoxSizingKeyword? =
        when ((data as? JsonPrimitive)?.contentOrNull) {
            "CONTENT_BOX" -> BoxSizingKeyword.CONTENT_BOX
            "BORDER_BOX" -> BoxSizingKeyword.BORDER_BOX
            else -> null
        }

    /**
     * Lane BX — resolved (x, y) frame inflation for content-box: the CSS
     * padding band plus the USED border widths per axis, in px (css-sizing-3
     * §3: frame = content + padding + border). Mirrors the resolution lanes
     * the real modifiers use so inflation and inset never disagree:
     *   * padding — SpacingExtractor + resolveToDp with the default
     *     SpacingContext, exactly like StyleApplier.placeholderFloorMinSize;
     *   * border — BorderSideExtractor with the [BorderSideConfig.hasBorder]
     *     gate, so a side with `border-style: none` has used width 0
     *     (CSS 2.1 §8.5.3) and does not inflate, matching web layout.
     */
    private fun contentBoxInflation(
        properties: List<Pair<String, JsonElement?>>
    ): Pair<Float, Float> {
        // Padding, resolved exactly like PaddingApplier will resolve it —
        // EXCEPT the percent basis (wave-18 lane 2, pin P13): the frame
        // inflation is an INTRINSIC-sizing computation and this extractor
        // runs statically, where no containing-block width is ever known
        // (parentWidthPx stays null). css-sizing-3 §5.2.1 resolves cyclic
        // percentages against ZERO in intrinsic sizing, so an indefinite
        // basis contributes 0 to the frame instead of the legacy viewport
        // fallback — which inflated `width:100px; padding-left:50%` under
        // the WPT content-box default to 100 + 195 = 295px while the
        // browser ref paints 100px (css-sizing abspos-auto-sizing-fit-
        // content-percentage-003/004).
        val pad = SpacingExtractor.extractPaddingConfig(properties).resolve(isRtl = false)
        val ctx = SpacingContext(percentIndefiniteAsZero = true)
        fun side(v: LengthValue?): Float = resolveToDp(v, ctx).value.coerceAtLeast(0f)
        // Border band — only sides that actually paint consume space.
        val borders = BorderSideExtractor.extractBorderConfig(properties)
        fun band(s: BorderSideConfig): Float = if (s.hasBorder) s.width?.value ?: 0f else 0f
        return Pair(
            side(pad.left) + side(pad.right) + band(borders.start) + band(borders.end),
            side(pad.top) + side(pad.bottom) + band(borders.top) + band(borders.bottom),
        )
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
