package com.styleconverter.runtime.core.types

// Phase 1 primitive: IR times expose `ms: Double` and optionally `original`.
// Several properties (TransitionDuration, AnimationDelay, …) wrap times in
// an array — callers can iterate and call extractTime on each element.
// See examples/primitives/times.json.
//
// Retro P2e (dangling-pointer sweep): every `examples/primitives/*.json`
// path above is GONE — renamed to `fixtures/primitives/` by restructure
// 02e4c457, then deleted by the 2026-07-08 hard prune 1e0234f6 (#8), with
// nothing to replace it. The shapes enumerated here (and the pins over
// them) are now the only record of that wire contract: read the names as
// history, not as a path to open.

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
