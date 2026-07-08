package com.styleconverter.test.style.sizing

// Phase 3 primitive: normalize every aspect-ratio IR shape into a single value
// type. Unlike Width/Height the aspect-ratio property has its own wire shape
// (not an IRLength) so it gets its own extractor rather than reusing the
// LengthValue pipeline.
//
// Reference fixtures (examples/properties/sizing/aspect-ratio.json):
//   {"ratio":{"w":16.0,"h":9.0},"normalizedRatio":1.777…}  — two-number form
//   {"ratio":{"value":1.5},"normalizedRatio":1.5}          — single-number form
//   "auto"                                                  — bare string
//   {"ratio":{"auto":true,"w":16.0,"h":9.0}, …}             — auto with fallback

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * CSS aspect-ratio value. `ratio` is width/height (>0) when an explicit ratio
 * is present; when only `auto` is specified we use 0.0 as a sentinel and set
 * [isAuto]. The `auto <ratio>` form sets both [isAuto] and a positive [ratio]
 * — the platform may choose to honour either.
 */
data class AspectRatioValue(
    // Numeric width/height ratio. 0.0 when author wrote bare `auto` only.
    val ratio: Double,
    // True when the CSS `auto` keyword was present. Bare `auto` → ratio=0.0.
    val isAuto: Boolean = false,
)

/**
 * Normalize an IR aspect-ratio data element. Never throws. Returns null when
 * the shape is entirely unrecognised; that lets callers treat it the same as
 * "property not specified" rather than wire a distinct Unknown sentinel in.
 */
fun extractAspectRatio(data: JsonElement?): AspectRatioValue? {
    if (data == null) return null
    // Shape 3: bare string "auto" collapses the whole property.
    if (data is JsonPrimitive && data.isString && data.content == "auto") {
        // ratio=0.0 is our sentinel for "auto-only" — the Applier skips
        // aspectRatio() and leaves Compose to auto-size.
        return AspectRatioValue(ratio = 0.0, isAuto = true)
    }
    if (data !is JsonObject) return null
    // normalizedRatio is pre-computed by the parser for both the two-number
    // and single-number shapes. Prefer it when available so we're byte-for-byte
    // consistent with the generator's notion of the ratio.
    val normalized = data["normalizedRatio"]?.jsonPrimitive?.doubleOrNull
    // ratio sub-object carries the raw components. We read the `auto` flag
    // from here for the "auto <ratio>" variant (shape 4).
    val ratioObj = data["ratio"] as? JsonObject
    // IR emits `auto:true` as a real JSON boolean — accept booleans first,
    // then fall through to stringy "true"/numeric 1.0 for hand-written JSON.
    val autoFlag = ratioObj?.get("auto")?.jsonPrimitive?.let { prim ->
        prim.booleanOrNull
            ?: (if (prim.isString) prim.content == "true" else prim.doubleOrNull == 1.0)
    } ?: false
    if (normalized != null) {
        return AspectRatioValue(ratio = normalized, isAuto = autoFlag)
    }
    // Fallback path: compute ratio from w/h when normalizedRatio is missing.
    // Defensive — current IR always emits normalizedRatio, but hand-written
    // fixtures might not.
    if (ratioObj != null) {
        val w = ratioObj["w"]?.jsonPrimitive?.doubleOrNull
        val h = ratioObj["h"]?.jsonPrimitive?.doubleOrNull
        val single = ratioObj["value"]?.jsonPrimitive?.doubleOrNull
        if (single != null && single > 0.0) {
            return AspectRatioValue(ratio = single, isAuto = autoFlag)
        }
        if (w != null && h != null && h > 0.0) {
            return AspectRatioValue(ratio = w / h, isAuto = autoFlag)
        }
    }
    return null
}
