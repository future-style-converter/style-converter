package com.styleconverter.runtime.transforms

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Decodes the `translate` longhand's wire (retro R1, finding A11#1).
 *
 * The converter's TranslateProperty (converter/src/main/kotlin/app/irmodels/
 * properties/transforms/TranslateProperty.kt) is a discriminated union, and
 * only the `2d` shape carries top-level x/y keys — which was ALL the old
 * extractor read, so a one-axis `translate: 20px` decoded to an empty
 * config and Android drew no shift at all (Translate_OneAxis_Px x0 = 16 vs
 * iOS/web 36; pairs-04 043 `scale: -1 1` + `translate: 20px` likewise).
 * Verbatim wires, from fixtures/properties/transforms/translate-longhand.json
 * converted with `:converter:run --to ir`:
 *
 *     none        {"type":"none"}
 *     1d length   {"type":"length","length":{"px":20.0}}
 *     1d percent  {"type":"percentage","percentage":25.0}
 *     2d          {"type":"2d","x":{"type":"length","px":20.0},"y":{"type":"percentage","percentage":50.0}}
 *     3d          {"type":"3d","x":{"type":"length","px":10.0},"y":{"type":"length","px":20.0},"z":{"px":30.0}}
 *
 * Note the 1d length nests the px under "length" while the 2d/3d axes
 * carry it flat — both are real serializer output (LengthPercentage.
 * LengthValue wraps an IRLength; the 2d/3d axes are serialised by
 * IRLength's own serializer). css-transforms-2 §5: percentages resolve
 * against the element's own reference box, so they ride as 0..1 fractions
 * the applier multiplies by `size` at draw time (the existing
 * translateXFraction/translateYFraction contract).
 */
object TranslateLonghandWire {

    /** The decoded longhand: absolute dp per axis plus own-size fractions. */
    data class Decoded(
        val x: Dp? = null,
        val y: Dp? = null,
        val z: Dp? = null,
        val xFraction: Float? = null,
        val yFraction: Float? = null,
    )

    /** One <length-percentage> axis → (dp, fraction); either side may be null. */
    private fun axis(el: JsonElement?): Pair<Dp?, Float?> {
        val o = el as? JsonObject ?: return null to null
        return when (o["type"]?.jsonPrimitive?.contentOrNull) {
            // Percentage → fraction of the element's own size (§5).
            "percentage" -> null to o["percentage"]?.jsonPrimitive?.floatOrNull?.div(100f)
            // Length: nested {"length":{"px"}} (1d) or flat {"px"} (2d/3d) —
            // extractDp reads the flat form; the nested one is unwrapped.
            "length" -> ((o["length"] as? JsonObject)?.let { ValueExtractors.extractDp(it) }
                ?: ValueExtractors.extractDp(o)) to null
            // Legacy untyped {"px": N} axis (pre-union wire, kept for old streams).
            else -> ValueExtractors.extractDp(o) to null
        }
    }

    /** Decode any shape the serializer emits; unknown/expression shapes → empty (no shift, no throw). */
    fun decode(data: JsonElement?): Decoded {
        val obj = data as? JsonObject ?: return Decoded()
        return when (obj["type"]?.jsonPrimitive?.contentOrNull) {
            "none" -> Decoded()                            // css-transforms-2 §5 initial value: identity
            // One axis: the whole object IS the <length-percentage>.
            "length", "percentage" -> axis(obj).let { (dp, frac) -> Decoded(x = dp, xFraction = frac) }
            "2d", "3d" -> {
                val (x, xf) = axis(obj["x"])
                val (y, yf) = axis(obj["y"])
                // z is a bare IRLength (no percentage allowed by the grammar).
                val z = obj["z"]?.let { ValueExtractors.extractDp(it) }
                Decoded(x = x, y = y, z = z, xFraction = xf, yFraction = yf)
            }
            // Untyped legacy object {x:{px},y:{px},z:{px}} — the shape the
            // old extractor (and TransformExtractorTest) used.
            else -> {
                val (x, xf) = axis(obj["x"])
                val (y, yf) = axis(obj["y"])
                Decoded(x = x, y = y, z = obj["z"]?.let { ValueExtractors.extractDp(it) }, xFraction = xf, yFraction = yf)
            }
        }
    }

    /** Zero dp — exposed for callers that need a typed default. */
    val ZERO: Dp = 0.dp
}
