package com.styleconverter.test.style.performance

// Fixture shape confirmed from /tmp/c4-isolation/tmpOutput.json:
//   Isolation -> "AUTO"
//   Isolation -> "ISOLATE"
// i.e. a bare JsonPrimitive string. We tolerate object-wrapped shapes too
// (type field) in case an upstream producer ever normalizes it.

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts CSS `isolation` configuration from IR property pairs.
 */
object IsolationExtractor {

    /**
     * Walk property pairs and produce a config. Default is [IsolationConfig.Value.AUTO].
     */
    fun extractIsolationConfig(properties: List<Pair<String, JsonElement?>>): IsolationConfig {
        // Default keyword when nothing matches — AUTO is a no-op.
        var value = IsolationConfig.Value.AUTO
        for ((type, data) in properties) {
            if (type != "Isolation" || data == null) continue
            // Support both primitive-string and object-with-type shapes. The
            // fixtures only show the primitive form today but we defend against
            // drift — parsing is cheap.
            val raw = when (data) {
                is JsonPrimitive -> data.contentOrNull
                is JsonObject -> data["type"]?.jsonPrimitive?.contentOrNull
                    ?: data["value"]?.jsonPrimitive?.contentOrNull
                else -> null
            } ?: continue
            // Case-insensitive compare — IR uses uppercase but CSS source is lowercase.
            value = when (raw.uppercase()) {
                "ISOLATE" -> IsolationConfig.Value.ISOLATE
                else -> IsolationConfig.Value.AUTO
            }
        }
        return IsolationConfig(value)
    }

    /** True when this property type belongs to this extractor. */
    fun isIsolationProperty(type: String): Boolean = type == "Isolation"
}
