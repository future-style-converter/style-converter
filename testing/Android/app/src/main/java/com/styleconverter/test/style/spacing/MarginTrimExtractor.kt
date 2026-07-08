package com.styleconverter.test.style.spacing

// Fold the IR `MarginTrim` property into a MarginTrimConfig. The IR emits a
// bare SCREAMING_SNAKE_CASE string (see fixture spec) like "NONE", "BLOCK",
// "INLINE_START", etc. We accept JsonArray too to stay robust against a
// future fixture that uses ["BLOCK", "INLINE"] — we pick the first keyword
// since Config only carries one.

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object MarginTrimExtractor {

    /** The single property-type string this extractor owns. */
    val PROPERTIES: Set<String> = setOf("MarginTrim")

    /** Build the config from an IR property list. */
    fun extract(properties: List<Pair<String, JsonElement?>>): MarginTrimConfig {
        // Scan for the MarginTrim entry; last one wins if duplicated.
        var keyword: MarginTrimKeyword = MarginTrimKeyword.NONE
        for ((type, data) in properties) {
            if (type != "MarginTrim") continue
            keyword = parse(data) ?: keyword
        }
        return MarginTrimConfig(value = keyword)
    }

    // Kept as the old method name so older callers inside the repo keep
    // compiling. Delegates to the new extract() so behaviour is consistent.
    fun extractMarginTrimConfig(properties: List<Pair<String, JsonElement?>>): MarginTrimConfig =
        extract(properties)

    fun isMarginTrimProperty(type: String): Boolean = type == "MarginTrim"

    /** Parse a JSON element into one of the seven supported keywords. */
    private fun parse(data: JsonElement?): MarginTrimKeyword? {
        if (data == null) return null
        val raw: String? = when (data) {
            // Bare "NONE"/"BLOCK"/... string — the common case.
            is JsonPrimitive -> data.contentOrNull
            // Defensive: accept a 1-element array by taking its first entry.
            is JsonArray -> data.firstOrNull()?.jsonPrimitive?.contentOrNull
            else -> null
        }
        if (raw == null) return null
        // Normalise hyphen to underscore to tolerate "block-start".
        val normalized = raw.uppercase().replace("-", "_")
        return when (normalized) {
            "NONE" -> MarginTrimKeyword.NONE
            "BLOCK" -> MarginTrimKeyword.BLOCK
            "INLINE" -> MarginTrimKeyword.INLINE
            "BLOCK_START" -> MarginTrimKeyword.BLOCK_START
            "BLOCK_END" -> MarginTrimKeyword.BLOCK_END
            "INLINE_START" -> MarginTrimKeyword.INLINE_START
            "INLINE_END" -> MarginTrimKeyword.INLINE_END
            else -> null
        }
    }
}
