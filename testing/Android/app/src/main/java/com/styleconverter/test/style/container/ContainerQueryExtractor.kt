package com.styleconverter.test.style.container

import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts container query configuration from IR properties.
 */
object ContainerQueryExtractor {

    /**
     * Extract complete container query configuration from property pairs.
     */
    fun extractContainerQueryConfig(properties: List<Pair<String, JsonElement?>>): ContainerQueryConfig {
        var containerType = ContainerQueryType.NORMAL
        var containerName: String? = null

        for ((type, data) in properties) {
            when (type) {
                "ContainerType" -> containerType = extractContainerType(data)
                "ContainerName" -> containerName = extractContainerName(data)
            }
        }

        return ContainerQueryConfig(
            containerType = containerType,
            containerName = containerName
        )
    }

    private fun extractContainerType(data: JsonElement?): ContainerQueryType {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return ContainerQueryType.NORMAL

        return when (keyword) {
            "NORMAL" -> ContainerQueryType.NORMAL
            "INLINE_SIZE" -> ContainerQueryType.INLINE_SIZE
            "BLOCK_SIZE" -> ContainerQueryType.BLOCK_SIZE
            "SIZE" -> ContainerQueryType.SIZE
            else -> ContainerQueryType.NORMAL
        }
    }

    private fun extractContainerName(data: JsonElement?): String? {
        if (data == null) return null

        return when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull
                if (content?.lowercase() == "none") null else content
            }
            is JsonObject -> {
                val type = data["type"]?.jsonPrimitive?.contentOrNull
                if (type?.lowercase() == "none") {
                    null
                } else {
                    data["name"]?.jsonPrimitive?.contentOrNull
                        ?: data["value"]?.jsonPrimitive?.contentOrNull
                }
            }
            else -> null
        }
    }

    /**
     * Check if a property type is container query-related.
     */
    fun isContainerQueryProperty(type: String): Boolean {
        return type in CONTAINER_QUERY_PROPERTIES
    }

    private val CONTAINER_QUERY_PROPERTIES = setOf(
        "ContainerType", "ContainerName"
    )
}
