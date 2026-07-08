package com.styleconverter.test.style.performance

import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts performance-related configuration from IR properties.
 */
object PerformanceExtractor {

    fun extractPerformanceConfig(properties: List<Pair<String, JsonElement?>>): PerformanceConfig {
        var contain = ContainConfig()
        var willChange = WillChangeConfig.Auto
        var zoom = ZoomConfig.Default

        for ((type, data) in properties) {
            when (type) {
                "Contain" -> contain = extractContainConfig(data)
                "WillChange" -> willChange = extractWillChangeConfig(data)
                "Zoom" -> zoom = extractZoomConfig(data)
            }
        }

        return PerformanceConfig(
            contain = contain,
            willChange = willChange,
            zoom = zoom
        )
    }

    fun extractBoxModelConfig(properties: List<Pair<String, JsonElement?>>): BoxModelConfig {
        var boxSizing = BoxSizingValue.CONTENT_BOX
        var boxDecorationBreak = BoxDecorationBreakValue.SLICE
        var imageRendering = ImageRenderingValue.AUTO

        for ((type, data) in properties) {
            when (type) {
                "BoxSizing" -> boxSizing = extractBoxSizing(data)
                "BoxDecorationBreak" -> boxDecorationBreak = extractBoxDecorationBreak(data)
                "ImageRendering" -> imageRendering = extractImageRendering(data)
            }
        }

        return BoxModelConfig(
            boxSizing = boxSizing,
            boxDecorationBreak = boxDecorationBreak,
            imageRendering = imageRendering
        )
    }

    private fun extractContainConfig(data: JsonElement?): ContainConfig {
        if (data == null) return ContainConfig()

        val values = extractContainValues(data)
        if (values.isEmpty()) return ContainConfig()

        return ContainConfig(
            layout = "LAYOUT" in values,
            paint = "PAINT" in values,
            size = "SIZE" in values,
            style = "STYLE" in values,
            inlineSize = "INLINE_SIZE" in values || "INLINE-SIZE" in values,
            blockSize = "BLOCK_SIZE" in values || "BLOCK-SIZE" in values
        )
    }

    private fun extractContainValues(data: JsonElement): Set<String> {
        return when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull?.uppercase() ?: return emptySet()
                when (content) {
                    "NONE" -> emptySet()
                    "STRICT" -> setOf("LAYOUT", "PAINT", "SIZE", "STYLE")
                    "CONTENT" -> setOf("LAYOUT", "PAINT", "STYLE")
                    else -> content.split(" ").map { it.trim().uppercase() }.toSet()
                }
            }
            is JsonArray -> data.mapNotNull { it.jsonPrimitive.contentOrNull?.uppercase() }.toSet()
            is JsonObject -> {
                val result = mutableSetOf<String>()
                data["layout"]?.jsonPrimitive?.contentOrNull?.let { if (it == "true") result.add("LAYOUT") }
                data["paint"]?.jsonPrimitive?.contentOrNull?.let { if (it == "true") result.add("PAINT") }
                data["size"]?.jsonPrimitive?.contentOrNull?.let { if (it == "true") result.add("SIZE") }
                data["style"]?.jsonPrimitive?.contentOrNull?.let { if (it == "true") result.add("STYLE") }
                result
            }
            else -> emptySet()
        }
    }

    private fun extractWillChangeConfig(data: JsonElement?): WillChangeConfig {
        if (data == null) return WillChangeConfig.Auto

        val values = mutableListOf<WillChangeValue>()

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull?.uppercase() ?: return WillChangeConfig.Auto
                if (content == "AUTO") return WillChangeConfig.Auto
                values.addAll(parseWillChangeValues(content))
            }
            is JsonArray -> {
                // The parser emits each will-change token as either a bare
                // string ("transform") or a tagged object like
                // {"type":"auto"} / {"type":"custom","value":"foo"}. The
                // legacy code unconditionally called `.jsonPrimitive` on
                // every element, which threw on the object form and tore
                // down the whole StyleApplier chain — collapsing the box
                // height to defaultMinSize for every WillChange_* fixture.
                data.forEach { element ->
                    when (element) {
                        is JsonPrimitive -> element.contentOrNull?.let {
                            values.addAll(parseWillChangeValues(it))
                        }
                        is JsonObject -> {
                            // Tagged form: prefer the explicit `type` token; if
                            // it's a generic wrapper like {"type":"custom","value":"foo"}
                            // fall through to `value` so the custom name still
                            // round-trips.
                            val type = element["type"]?.jsonPrimitive?.contentOrNull
                            val value = element["value"]?.jsonPrimitive?.contentOrNull
                            val token = when {
                                type != null && type != "custom" -> type
                                value != null -> value
                                else -> type
                            }
                            token?.let { values.addAll(parseWillChangeValues(it)) }
                        }
                        else -> Unit  // arrays / nulls are not valid here
                    }
                }
            }
            is JsonObject -> {
                data["properties"]?.jsonArray?.forEach { element ->
                    (element as? JsonPrimitive)?.contentOrNull?.let {
                        values.addAll(parseWillChangeValues(it))
                    }
                }
            }
        }

        return WillChangeConfig(
            properties = values,
            isAuto = values.isEmpty()
        )
    }

    private fun parseWillChangeValues(value: String): List<WillChangeValue> {
        return value.split(",", " ").mapNotNull { v ->
            when (v.trim().uppercase().replace("-", "_")) {
                "AUTO" -> WillChangeValue.AUTO
                "SCROLL_POSITION" -> WillChangeValue.SCROLL_POSITION
                "CONTENTS" -> WillChangeValue.CONTENTS
                "TRANSFORM" -> WillChangeValue.TRANSFORM
                "OPACITY" -> WillChangeValue.OPACITY
                "TOP" -> WillChangeValue.TOP
                "LEFT" -> WillChangeValue.LEFT
                "BOTTOM" -> WillChangeValue.BOTTOM
                "RIGHT" -> WillChangeValue.RIGHT
                "WIDTH" -> WillChangeValue.WIDTH
                "HEIGHT" -> WillChangeValue.HEIGHT
                "BACKGROUND" -> WillChangeValue.BACKGROUND
                "FILTER" -> WillChangeValue.FILTER
                else -> if (v.isNotBlank()) WillChangeValue.CUSTOM else null
            }
        }
    }

    private fun extractZoomConfig(data: JsonElement?): ZoomConfig {
        if (data == null) return ZoomConfig.Default

        return when (data) {
            is JsonPrimitive -> {
                data.floatOrNull?.let { ZoomConfig.fromFactor(it) }
                    ?: when (data.contentOrNull?.lowercase()) {
                        "normal" -> ZoomConfig.Default
                        else -> ZoomConfig.Default
                    }
            }
            is JsonObject -> {
                data["percentage"]?.jsonPrimitive?.floatOrNull?.let {
                    ZoomConfig.fromPercentage(it)
                } ?: data["value"]?.jsonPrimitive?.floatOrNull?.let {
                    ZoomConfig.fromFactor(it)
                } ?: data["factor"]?.jsonPrimitive?.floatOrNull?.let {
                    ZoomConfig.fromFactor(it)
                } ?: ZoomConfig.Default
            }
            else -> ZoomConfig.Default
        }
    }

    private fun extractBoxSizing(data: JsonElement?): BoxSizingValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return BoxSizingValue.CONTENT_BOX
        return when (keyword) {
            "BORDER_BOX" -> BoxSizingValue.BORDER_BOX
            else -> BoxSizingValue.CONTENT_BOX
        }
    }

    private fun extractBoxDecorationBreak(data: JsonElement?): BoxDecorationBreakValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()
            ?: return BoxDecorationBreakValue.SLICE
        return when (keyword) {
            "CLONE" -> BoxDecorationBreakValue.CLONE
            else -> BoxDecorationBreakValue.SLICE
        }
    }

    private fun extractImageRendering(data: JsonElement?): ImageRenderingValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return ImageRenderingValue.AUTO
        return when (keyword) {
            "SMOOTH" -> ImageRenderingValue.SMOOTH
            "HIGH_QUALITY" -> ImageRenderingValue.HIGH_QUALITY
            "CRISP_EDGES" -> ImageRenderingValue.CRISP_EDGES
            "PIXELATED" -> ImageRenderingValue.PIXELATED
            else -> ImageRenderingValue.AUTO
        }
    }

    fun isPerformanceProperty(type: String): Boolean {
        return type in PERFORMANCE_PROPERTIES
    }

    private val PERFORMANCE_PROPERTIES = setOf(
        "Contain", "WillChange", "Zoom",
        "BoxSizing", "BoxDecorationBreak", "ImageRendering",
        "ContainIntrinsicSize", "ContainIntrinsicWidth", "ContainIntrinsicHeight"
    )
}
