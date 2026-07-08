package com.styleconverter.test.style.core.types

// Phase 1 primitive: every IRColor shape from the fixtures reduces to one
// of these variants. Extractors never throw or return null.
//
// Reference fixtures:
//   colors-legacy.json  — rgb/rgba, hsl/hsla, hex3/4/6/8
//   colors-modern.json  — hwb, lab, lch, oklab, oklch, color(), color-mix, light-dark, relative
//   colors-named.json   — named colors, transparent, currentColor

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Every color-ish IR shape normalizes to one of these variants. */
sealed interface ColorValue {
    /**
     * Fully-resolved sRGB color in 0..1 floats. This is what Compose consumes
     * directly. All static CSS colors resolve to this; any input that has an
     * "srgb" key in IR lands here.
     */
    data class Srgb(
        val r: Double,
        val g: Double,
        val b: Double,
        val a: Double = 1.0,
    ) : ColorValue {
        /** Convert to a Compose Color (clamping happens inside Color()). */
        fun toComposeColor(): Color =
            Color(r.toFloat(), g.toFloat(), b.toFloat(), a.toFloat())
    }

    /**
     * A color that cannot be resolved statically — `currentColor`, CSS
     * variables, `color-mix`, `light-dark`, relative-color syntax. The raw
     * JSON is preserved so callers with extra context can still render it.
     */
    data class Dynamic(val kind: DynamicKind, val raw: JsonElement) : ColorValue

    /** Sentinel for parse failure. */
    data object Unknown : ColorValue

    /** Distinguishes the several flavors of dynamic color the IR carries. */
    enum class DynamicKind { CURRENT_COLOR, COLOR_MIX, LIGHT_DARK, RELATIVE, VAR }
}

/**
 * Normalize any IR color JSON shape into a [ColorValue]. Never throws.
 *
 * Shape cheat-sheet (see fixture notes):
 *   { srgb: {r,g,b[,a]}, original: <any> }       → Srgb (static colors)
 *   { original: "currentColor" }                  → Dynamic(CURRENT_COLOR)
 *   { original: { type: "color-mix", … } }        → Dynamic(COLOR_MIX)
 *   { original: { type: "light-dark", … } }       → Dynamic(LIGHT_DARK)
 *   { original: { type: "relative", … } }         → Dynamic(RELATIVE)
 *   { original: { type: "var", … } }              → Dynamic(VAR)
 *   anything else                                 → Unknown
 */
fun extractColor(json: JsonElement?): ColorValue {
    if (json == null || json !is JsonObject) return ColorValue.Unknown

    // Static path: the IR attaches a resolved sRGB payload for every static
    // color. The "a" key is omitted when alpha == 1.0 (see fixture notes).
    (json["srgb"] as? JsonObject)?.let { srgb ->
        val r = srgb["r"]?.jsonPrimitive?.doubleOrNull ?: return ColorValue.Unknown
        val g = srgb["g"]?.jsonPrimitive?.doubleOrNull ?: return ColorValue.Unknown
        val b = srgb["b"]?.jsonPrimitive?.doubleOrNull ?: return ColorValue.Unknown
        val a = srgb["a"]?.jsonPrimitive?.doubleOrNull ?: 1.0
        return ColorValue.Srgb(r, g, b, a)
    }

    // Dynamic path: no srgb. Look inside `original` to pick the kind.
    val original = json["original"] ?: return ColorValue.Unknown
    return classifyDynamic(original)
}

/** Determine which DynamicKind an `original` payload represents. */
private fun classifyDynamic(original: JsonElement): ColorValue {
    // String-typed originals: only `currentColor` is a dynamic marker we care
    // about. A bare hex/named string without srgb shouldn't happen in IR, but
    // if it does we fall through to Unknown rather than guess.
    if (original is JsonPrimitive && original.isString) {
        return when (original.content) {
            "currentColor" -> ColorValue.Dynamic(ColorValue.DynamicKind.CURRENT_COLOR, original)
            else -> ColorValue.Unknown
        }
    }
    val obj = original as? JsonObject ?: return ColorValue.Unknown
    val type = (obj["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    return when (type) {
        "color-mix" -> ColorValue.Dynamic(ColorValue.DynamicKind.COLOR_MIX, obj)
        "light-dark" -> ColorValue.Dynamic(ColorValue.DynamicKind.LIGHT_DARK, obj)
        "relative" -> ColorValue.Dynamic(ColorValue.DynamicKind.RELATIVE, obj)
        "var" -> ColorValue.Dynamic(ColorValue.DynamicKind.VAR, obj)
        // Any other typed-original (hwb, lab, lch, oklab, oklch, color()) is
        // a static-with-fallback shape; the srgb branch above should already
        // have matched. If we got here, the IR is malformed.
        else -> ColorValue.Unknown
    }
}

