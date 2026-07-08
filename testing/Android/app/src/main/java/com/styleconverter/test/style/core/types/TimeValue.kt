package com.styleconverter.test.style.core.types

// Phase 1 primitive: IR times expose `ms: Double` and optionally `original`.
// Several properties (TransitionDuration, AnimationDelay, …) wrap times in
// an array — callers can iterate and call extractTime on each element.
// See examples/primitives/times.json.

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Normalized time in milliseconds (platform-agnostic). */
data class TimeValue(val milliseconds: Double)

/**
 * Extract a time value from its IR shape `{ ms: Double, original?: … }`.
 * Returns null on parse failure.
 */
fun extractTime(json: JsonElement?): TimeValue? {
    if (json == null || json !is JsonObject) return null
    // Like angles, the codegen has already normalized s → ms, so we only
    // read the canonical "ms" field.
    val ms = json["ms"]?.jsonPrimitive?.doubleOrNull ?: return null
    return TimeValue(ms)
}

/**
 * Convenience: pull a flat list of [TimeValue] from the common
 * `data: [ {ms:…}, {ms:…} ]` shape. Malformed elements are dropped, never
 * rethrown — callers inspect emptiness to decide what to do.
 */
fun extractTimeList(json: JsonElement?): List<TimeValue> {
    if (json !is JsonArray) return emptyList()
    return json.mapNotNull(::extractTime)
}
