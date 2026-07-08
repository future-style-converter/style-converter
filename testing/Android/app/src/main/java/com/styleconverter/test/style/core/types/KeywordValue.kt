package com.styleconverter.test.style.core.types

// Phase 1 primitive: CSS keywords. The IR has two conventions that predate
// this refactor — some properties emit "flex-start", others emit "FLEX_START".
// We canonicalize to CSS spelling (lowercase + hyphen) and provide a
// `.matches()` helper that tolerates the legacy uppercase/underscore form so
// existing Applier code can migrate gradually.

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * A keyword in its canonical CSS form: lowercase, hyphen-separated, trimmed.
 * Use [matches] to compare against a candidate that may still be in the
 * legacy uppercase/underscore form.
 */
@JvmInline
value class KeywordValue(val normalized: String) {
    /**
     * True when [candidate] denotes the same keyword as this value in either
     * CSS (`flex-start`) or legacy (`FLEX_START`) spelling.
     */
    fun matches(candidate: String): Boolean =
        normalized == normalize(candidate)

    companion object {
        /** Canonicalize: lowercase, swap `_` → `-`, trim whitespace. */
        internal fun normalize(raw: String): String =
            raw.trim().lowercase().replace('_', '-')
    }
}

/**
 * Extract a keyword from any of the IR's keyword shapes.
 *
 *   "flex-start"                     (bare string)         → normalized
 *   "FLEX_START"                     (legacy enum name)    → normalized
 *   { type: "flex-start" }           (sealed-interface tag)
 *   { keyword: "flex-start" }
 *   { value: "flex-start" }
 *
 * Returns null when the input has no recognizable keyword. Callers
 * typically then fall back to another extractor or a default value.
 */
fun extractKeyword(json: JsonElement?): KeywordValue? {
    val raw = readKeywordString(json) ?: return null
    return KeywordValue(KeywordValue.normalize(raw))
}

/** Pull the raw keyword string out of whichever wrapper the IR used. */
private fun readKeywordString(json: JsonElement?): String? = when (json) {
    null -> null
    is JsonPrimitive -> if (json.isString) json.content else null
    is JsonObject -> {
        // Prefer "type" (our sealed-interface tag), then the explicit
        // "keyword" and "value" fields. Order matters: some payloads carry a
        // "value" that is a nested number, in which case type/keyword win.
        (json["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: (json["keyword"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: (json["value"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }
    else -> null
}
