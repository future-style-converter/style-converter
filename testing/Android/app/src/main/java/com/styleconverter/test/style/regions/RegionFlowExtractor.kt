package com.styleconverter.test.style.regions

import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts region flow configuration from IR properties.
 */
object RegionFlowExtractor {

    fun extractRegionFlowConfig(properties: List<Pair<String, JsonElement?>>): RegionFlowConfig {
        var flowFrom: String? = null
        var flowInto: String? = null
        var regionFragment = RegionFragmentValue.AUTO

        for ((type, data) in properties) {
            when (type) {
                "FlowFrom" -> flowFrom = extractFlowName(data)
                "FlowInto" -> flowInto = extractFlowName(data)
                "RegionFragment" -> regionFragment = extractRegionFragment(data)
            }
        }

        return RegionFlowConfig(
            flowFrom = flowFrom,
            flowInto = flowInto,
            regionFragment = regionFragment
        )
    }

    private fun extractFlowName(data: JsonElement?): String? {
        if (data == null) return null

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull
                return if (content?.lowercase() == "none") null else content
            }
            is JsonObject -> {
                val type = data["type"]?.jsonPrimitive?.contentOrNull
                if (type?.lowercase() == "none") return null
                return data["name"]?.jsonPrimitive?.contentOrNull
                    ?: data["value"]?.jsonPrimitive?.contentOrNull
            }
            else -> return null
        }
    }

    private fun extractRegionFragment(data: JsonElement?): RegionFragmentValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()
            ?: return RegionFragmentValue.AUTO

        return when (keyword) {
            "AUTO" -> RegionFragmentValue.AUTO
            "BREAK" -> RegionFragmentValue.BREAK
            else -> RegionFragmentValue.AUTO
        }
    }

    fun isRegionFlowProperty(type: String): Boolean {
        return type in setOf("FlowFrom", "FlowInto", "RegionFragment")
    }
}
