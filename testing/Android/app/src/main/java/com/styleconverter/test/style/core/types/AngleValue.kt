package com.styleconverter.test.style.core.types

// Phase 1 primitive: IR angles always expose a normalized `deg` field, with
// the original unit preserved under `original: { v, u }` only when the source
// wasn't already degrees. See examples/primitives/angles.json.

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Normalized angle in degrees (what every renderer wants). */
data class AngleValue(val degrees: Double)

/**
 * Extract an angle from its IR shape `{ deg: Double, original?: … }`.
 * Returns null on parse failure (angles appear inside larger structures where
 * a nullable return is more ergonomic than an Unknown variant).
 */
fun extractAngle(json: JsonElement?): AngleValue? {
    if (json == null || json !is JsonObject) return null
    // The "deg" key is the canonical normalized representation; we never
    // need to re-derive it from `original.v, original.u` because the codegen
    // already did that conversion for us (see IRAngle serializer upstream).
    val deg = json["deg"]?.jsonPrimitive?.doubleOrNull ?: return null
    return AngleValue(deg)
}
