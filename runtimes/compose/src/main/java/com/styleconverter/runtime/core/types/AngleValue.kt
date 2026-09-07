package com.styleconverter.runtime.core.types

// Phase 1 primitive: IR angles always expose a normalized `deg` field, with
// the original unit preserved under `original: { v, u }` only when the source
// wasn't already degrees. See examples/primitives/angles.json.
//
// Retro P2e (dangling-pointer sweep): every `examples/primitives/*.json`
// path above is GONE — renamed to `fixtures/primitives/` by restructure
// 02e4c457, then deleted by the 2026-07-08 hard prune 1e0234f6 (#8), with
// nothing to replace it. The shapes enumerated here (and the pins over
// them) are now the only record of that wire contract: read the names as
// history, not as a path to open.

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
