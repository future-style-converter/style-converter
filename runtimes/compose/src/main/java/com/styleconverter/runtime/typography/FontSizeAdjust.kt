package com.styleconverter.runtime.typography

// Retrospective lane R3 (finding A7#4) — `font-size-adjust` on Compose.
//
// css-fonts-4 §2.6 ("Relative sizing: the font-size-adjust property"):
//   font-size-adjust: none | <number [0,∞]>
//                   | [ ex-height | cap-height | ch-width | ic-width | ic-height ] <number [0,∞]>?
// The property scales the USED font size so that the chosen metric of the
// first available font hits the requested ratio of the font size:
//   used = computed × number / metricRatio(first available font)
// where metricRatio is that metric expressed as a fraction of the em (the
// bare <number> form is the ex-height ratio — the spec's "aspect value").
//
// Why a Compose file exists: the Android registry claimed FontSizeAdjust
// under "no Compose analogue — parse-only" (TypographyExtractor's migrated
// list) while the iOS twin (TypographyExtractor.swift fontSizeAdjustFactor,
// fidelity wave 2) has applied it since wave 2 and the web reference applies
// it natively — the audit measured the claim as false and the twin as
// asymmetric. The math is platform-free (a pure multiply on the resolved sp),
// so it is ported here byte-parallel to the Swift twin, INCLUDING its metric
// table: Inter 4.0's OS/2 sCapHeight 1490 / sxHeight 1118 over unitsPerEm
// 2048 — verified against the bundled face itself
// (runtimes/compose/src/main/res/font/inter_regular.ttf, fontTools OS/2:
// 1490 / 1118 / 2048) which is also apps/web-harness's Inter-Regular.ttf,
// i.e. the exact tables Chromium consults for the reference render. iOS
// reads UIFont("Inter") for the same two numbers at runtime and falls back
// to these constants in its test bundle; Compose has no JVM-side font-table
// reader, so the constants ARE the source here.
//
// Honest scope (documented, tracker-visible, never a silent fallthrough):
//   • ch-width / ic-width / ic-height need advance metrics this runtime does
//     not read → no adjustment + PropertyTracker breadcrumb (iOS: same skip).
//   • `from-font` means "the number is the first available font's OWN
//     ratio" — for the face actually rendering, that adjustment is exactly
//     ×1, so it is honestly a no-op here (iOS: same, via its `value` guard).
//   • The ratio table is Inter's. Every corpus text run resolves to the
//     bundled Inter (CssFontFamilyResolver / the renderer's default), so the
//     table is the honest one; a generic `monospace` run would need its own
//     — flagged by the caller-side breadcrumb in TextStyleApplier when the
//     resolved family is not Inter.
//   • Applied to the GLYPH size only (TextStyleApplier.extractTextStyle).
//     The em basis for sizing units (StyleApplier.buildSpacingContext via
//     TypographyExtractor) stays the COMPUTED size on purpose: css-values-4
//     §6.1.1 em tracks the computed font-size, not the adjusted used size —
//     the same order the Swift twin keeps (em resolved before the scale).
//
// Corpus exposure at wave49-final: zero FontSizeAdjust carriers in any
// per-test-ir, so no gate cell moves; the fixture corpus carries the
// property in fixtures/properties/typography/font-size-adjust.json and
// fixtures/fidelity Typography_C04 (28px + `cap-height 0.7` → 26.94px, the
// number the Swift pin asserts), neither of which has a committed PNG under
// tools/visual/baseline.

import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull

object FontSizeAdjust {

    /** The IR type the converter emits for `font-size-adjust`. */
    const val TYPE: String = "FontSizeAdjust"

    /** Inter 4.0 OS/2 sCapHeight / unitsPerEm = 1490 / 2048 (0.7275…). */
    const val INTER_CAP_HEIGHT_EM: Float = 1490f / 2048f

    /** Inter 4.0 OS/2 sxHeight / unitsPerEm = 1118 / 2048 (0.5459…). */
    const val INTER_X_HEIGHT_EM: Float = 1118f / 2048f

    /**
     * The scale factor the element's `font-size-adjust` applies to its
     * computed font size, or null when nothing is to be adjusted (absent,
     * `none`, `from-font`, an unsupported metric, a malformed payload).
     * The LAST declaration wins — the same cascade fold every reader in
     * this runtime applies to its own property.
     */
    fun factor(properties: List<IRProperty>): Float? =
        factorOf(properties.lastOrNull { it.type == TYPE }?.data)

    /**
     * Decode ONE wire value into a factor. Shapes, verbatim from the running
     * converter on fixtures/properties/typography/font-size-adjust.json
     * (FontSizeAdjustProperty's sealed serializer):
     *   {"type":"none"}                                        → null
     *   {"type":"from-font"}                                   → null (×1, see header)
     *   {"type":"number","value":0.5}                          → 0.5 / xHeight
     *   {"type":"metric-value","metric":"cap-height","value":0.7} → 0.7 / capHeight
     *   {"type":"metric-value","metric":"ex-height","value":0.5}  → 0.5 / xHeight
     *   {"type":"metric-value","metric":"ch-width"|"ic-*",…}   → null + breadcrumb
     *   {"type":"keyword","keyword":"inherit"}                 → null + breadcrumb
     * plus the bare-primitive legacy shapes the Swift pin feeds ("NONE",
     * a bare number) so the two twins accept identical payloads.
     */
    fun factorOf(data: JsonElement?): Float? {
        when (data) {
            // No declaration → the initial value `none` → no adjustment.
            null -> return null
            is JsonPrimitive -> {
                // A bare number is the one-value grammar: ex-height ratio.
                data.floatOrNull?.let { return ratio("ex-height", it) }
                // Keyword primitives (legacy wire / the Swift pin's "NONE").
                return when (val kw = data.contentOrNull?.lowercase()) {
                    // `none`: initial value. `from-font`: ×1 for the face
                    // that renders (its own metric IS the number).
                    null, "none", "from-font" -> null
                    // Any other keyword (inherit/initial/unset/garbage) has
                    // no inherited-adjust channel here — recorded, not eaten.
                    else -> unhandled("keyword:$kw")
                }
            }
            is JsonObject -> {
                // The sealed discriminator the converter serializes.
                val type = (data["type"] as? JsonPrimitive)?.contentOrNull
                // IRNumber serializes as a plain JSON number under "value".
                val value = (data["value"] as? JsonPrimitive)?.floatOrNull
                return when (type) {
                    // Initial value / first-available-font identity — see header.
                    "none", "from-font" -> null
                    // One-value form: `<number>` alone means ex-height (§2.6).
                    "number" -> ratio("ex-height", value)
                    // Two-value form: the named metric's ratio.
                    "metric-value" -> ratio(
                        (data["metric"] as? JsonPrimitive)?.contentOrNull?.lowercase(), value
                    )
                    // CSS-wide keywords the parser passes through verbatim.
                    "keyword" -> unhandled(
                        "keyword:${(data["keyword"] as? JsonPrimitive)?.contentOrNull}"
                    )
                    // An unknown discriminator is wire drift — surface it.
                    else -> unhandled("shape:$type")
                }
            }
            // Arrays/other JSON kinds never carry this property.
            else -> return unhandled("shape:${data::class.simpleName}")
        }
    }

    /**
     * number / metricRatio for the two metrics this runtime can answer.
     * Non-positive numbers are refused (a 0 would size the glyphs to
     * nothing; the Swift twin guards `v > 0` identically) — refused LOUDLY.
     */
    private fun ratio(metric: String?, value: Float?): Float? {
        // Grammar requires the number; a missing one is a broken wire.
        if (value == null) return unhandled("missing-value")
        // <number [0,∞]> admits 0 but a 0 used size is not renderable.
        if (value <= 0f) return unhandled("non-positive:$value")
        // The metric-to-em ratio of the reference face (Inter, see header).
        val fontRatio = when (metric) {
            "ex-height" -> INTER_X_HEIGHT_EM
            "cap-height" -> INTER_CAP_HEIGHT_EM
            // ch-width / ic-width / ic-height: advance metrics we do not read.
            else -> return unhandled("metric:$metric")
        }
        // css-fonts-4 §2.6: used = computed × (number / fontRatio).
        return value / fontRatio
    }

    /** The used size for a computed size and an optional factor (§2.6). */
    fun usedSizePx(computedPx: Float, factor: Float?): Float =
        if (factor == null) computedPx else computedPx * factor

    /** Breadcrumb + null: the runtime's no-silent-fallthrough contract. */
    private fun unhandled(reason: String): Float? {
        PropertyTracker.markUnhandled("$TYPE[$reason]")
        return null
    }
}
