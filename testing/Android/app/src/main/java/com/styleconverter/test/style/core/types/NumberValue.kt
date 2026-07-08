package com.styleconverter.test.style.core.types

// Phase 1 primitive: CSS numbers ride in property-specific envelopes. There
// is no single IRNumber shape, so we ship a tiny adapter per property. All
// shapes below come from examples/primitives/numbers.json.

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Normalized number value. Keep flexible (Double) to cover int + float uses. */
data class NumberValue(val value: Double)

/** Opacity: { alpha: 0.5, original: { type: "number", value: 0.5 } }. */
fun extractOpacity(data: JsonElement?): NumberValue? {
    if (data !is JsonObject) return null
    val v = data["alpha"]?.jsonPrimitive?.doubleOrNull ?: return null
    return NumberValue(v)
}

/** Unitless line-height: { multiplier: 1.5, original: … }. */
fun extractLineHeightMultiplier(data: JsonElement?): NumberValue? {
    if (data !is JsonObject) return null
    val v = data["multiplier"]?.jsonPrimitive?.doubleOrNull ?: return null
    return NumberValue(v)
}

/** FlexGrow: { value: { type: …Number, value: 1.0 }, normalizedValue: 1.0 }. */
fun extractFlexGrow(data: JsonElement?): NumberValue? {
    if (data !is JsonObject) return null
    // "normalizedValue" is the IR's pre-resolved number we can trust blindly.
    val v = data["normalizedValue"]?.jsonPrimitive?.doubleOrNull ?: return null
    return NumberValue(v)
}

/** ZIndex: { value: 10, original: { type: "integer", value: 10 } }. */
fun extractZIndex(data: JsonElement?): NumberValue? {
    if (data !is JsonObject) return null
    val v = data["value"]?.jsonPrimitive?.doubleOrNull ?: return null
    return NumberValue(v)
}

/**
 * FontWeight: the IR emits a bare integer (e.g. `700`). Keyword inputs
 * ("normal", "bold") are already translated to numbers upstream.
 */
fun extractFontWeight(data: JsonElement?): NumberValue? {
    val prim = data as? JsonPrimitive ?: return null
    val v = prim.doubleOrNull ?: return null
    return NumberValue(v)
}

/**
 * FontSize: { px: 16, original: { type: "length", px: 16 } }. We expose it
 * as a NumberValue of pixels for callers that only want the scalar; full
 * LengthValue decoding is done by extractLength for the normal case.
 */
fun extractFontSizePx(data: JsonElement?): NumberValue? {
    if (data !is JsonObject) return null
    val v = data["px"]?.jsonPrimitive?.doubleOrNull ?: return null
    return NumberValue(v)
}
