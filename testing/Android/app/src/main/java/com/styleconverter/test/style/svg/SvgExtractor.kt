package com.styleconverter.test.style.svg

import androidx.compose.ui.graphics.Color
import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts SVG configuration from IR properties.
 */
object SvgExtractor {

    /**
     * Extract complete SVG configuration from property pairs.
     */
    fun extractSvgConfig(properties: List<Pair<String, JsonElement?>>): SvgConfig {
        var fill: SvgFillValue = SvgFillValue.ColorFill(Color.Black)
        var fillOpacity = 1.0f
        var fillRule = FillRuleValue.NONZERO
        var stroke: SvgStrokeValue = SvgStrokeValue.None
        var strokeWidth = 1.0f
        var strokeLinecap = StrokeLinecapValue.BUTT
        var strokeLinejoin = StrokeLinejoinValue.MITER
        var strokeDasharray: DashArrayValue = DashArrayValue.None
        var strokeDashoffset = 0.0f
        var strokeMiterlimit = 4.0f
        var strokeOpacity = 1.0f
        var stopColor: Color? = null
        var stopOpacity = 1.0f
        var paintOrder = listOf(PaintOrderElement.FILL, PaintOrderElement.STROKE, PaintOrderElement.MARKERS)
        var markerStart: MarkerValue = MarkerValue.None
        var markerMid: MarkerValue = MarkerValue.None
        var markerEnd: MarkerValue = MarkerValue.None

        for ((type, data) in properties) {
            when (type) {
                "Fill" -> fill = extractSvgFill(data)
                "FillOpacity" -> fillOpacity = extractSvgOpacity(data)
                "FillRule" -> fillRule = extractFillRule(data)
                "Stroke" -> stroke = extractSvgStroke(data)
                "StrokeWidth" -> strokeWidth = extractStrokeWidth(data)
                "StrokeLinecap" -> strokeLinecap = extractStrokeLinecap(data)
                "StrokeLinejoin" -> strokeLinejoin = extractStrokeLinejoin(data)
                "StrokeDasharray" -> strokeDasharray = extractStrokeDasharray(data)
                "StrokeDashoffset" -> strokeDashoffset = extractStrokeDashoffset(data)
                "StrokeMiterlimit" -> strokeMiterlimit = extractStrokeMiterlimit(data)
                "StrokeOpacity" -> strokeOpacity = extractSvgOpacity(data)
                "StopColor" -> stopColor = extractStopColor(data)
                "StopOpacity" -> stopOpacity = extractSvgOpacity(data)
                "PaintOrder" -> paintOrder = extractPaintOrder(data)
                "Marker" -> {
                    val marker = extractMarkerValue(data)
                    markerStart = marker
                    markerMid = marker
                    markerEnd = marker
                }
                "MarkerStart" -> markerStart = extractMarkerValue(data)
                "MarkerMid" -> markerMid = extractMarkerValue(data)
                "MarkerEnd" -> markerEnd = extractMarkerValue(data)
            }
        }

        return SvgConfig(
            fill = fill,
            fillOpacity = fillOpacity,
            fillRule = fillRule,
            stroke = stroke,
            strokeWidth = strokeWidth,
            strokeLinecap = strokeLinecap,
            strokeLinejoin = strokeLinejoin,
            strokeDasharray = strokeDasharray,
            strokeDashoffset = strokeDashoffset,
            strokeMiterlimit = strokeMiterlimit,
            strokeOpacity = strokeOpacity,
            stopColor = stopColor,
            stopOpacity = stopOpacity,
            paintOrder = paintOrder,
            markers = MarkerConfig(markerStart, markerMid, markerEnd)
        )
    }

    private fun extractSvgFill(data: JsonElement?): SvgFillValue {
        if (data == null) return SvgFillValue.ColorFill(Color.Black)

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull?.lowercase()
                return when (content) {
                    "none" -> SvgFillValue.None
                    "context-fill" -> SvgFillValue.ContextFill
                    else -> {
                        val color = ValueExtractors.extractColor(data)
                        if (color != null) SvgFillValue.ColorFill(color)
                        else SvgFillValue.ColorFill(Color.Black)
                    }
                }
            }
            is JsonObject -> {
                val type = data["type"]?.jsonPrimitive?.contentOrNull
                return when (type?.lowercase()) {
                    "none" -> SvgFillValue.None
                    "url" -> {
                        val url = data["url"]?.jsonPrimitive?.contentOrNull ?: ""
                        val fallback = data["fallback"]?.let { ValueExtractors.extractColor(it) }
                        SvgFillValue.UrlReference(url, fallback)
                    }
                    "context-fill" -> SvgFillValue.ContextFill
                    else -> {
                        val color = ValueExtractors.extractColor(data)
                        if (color != null) SvgFillValue.ColorFill(color)
                        else SvgFillValue.ColorFill(Color.Black)
                    }
                }
            }
            else -> return SvgFillValue.ColorFill(Color.Black)
        }
    }

    private fun extractSvgStroke(data: JsonElement?): SvgStrokeValue {
        if (data == null) return SvgStrokeValue.None

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull?.lowercase()
                return when (content) {
                    "none" -> SvgStrokeValue.None
                    "context-stroke" -> SvgStrokeValue.ContextStroke
                    else -> {
                        val color = ValueExtractors.extractColor(data)
                        if (color != null) SvgStrokeValue.ColorStroke(color)
                        else SvgStrokeValue.None
                    }
                }
            }
            is JsonObject -> {
                val type = data["type"]?.jsonPrimitive?.contentOrNull
                return when (type?.lowercase()) {
                    "none" -> SvgStrokeValue.None
                    "url" -> {
                        val url = data["url"]?.jsonPrimitive?.contentOrNull ?: ""
                        val fallback = data["fallback"]?.let { ValueExtractors.extractColor(it) }
                        SvgStrokeValue.UrlReference(url, fallback)
                    }
                    "context-stroke" -> SvgStrokeValue.ContextStroke
                    else -> {
                        val color = ValueExtractors.extractColor(data)
                        if (color != null) SvgStrokeValue.ColorStroke(color)
                        else SvgStrokeValue.None
                    }
                }
            }
            else -> return SvgStrokeValue.None
        }
    }

    private fun extractStrokeWidth(data: JsonElement?): Float {
        if (data == null) return 1.0f
        val dp = ValueExtractors.extractDp(data)
        return dp?.value ?: 1.0f
    }

    private fun extractStrokeLinecap(data: JsonElement?): StrokeLinecapValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()
            ?: return StrokeLinecapValue.BUTT

        return when (keyword) {
            "BUTT" -> StrokeLinecapValue.BUTT
            "ROUND" -> StrokeLinecapValue.ROUND
            "SQUARE" -> StrokeLinecapValue.SQUARE
            else -> StrokeLinecapValue.BUTT
        }
    }

    private fun extractStrokeLinejoin(data: JsonElement?): StrokeLinejoinValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return StrokeLinejoinValue.MITER

        return when (keyword) {
            "MITER" -> StrokeLinejoinValue.MITER
            "MITER_CLIP" -> StrokeLinejoinValue.MITER_CLIP
            "ROUND" -> StrokeLinejoinValue.ROUND
            "BEVEL" -> StrokeLinejoinValue.BEVEL
            "ARCS" -> StrokeLinejoinValue.ARCS
            else -> StrokeLinejoinValue.MITER
        }
    }

    private fun extractStrokeDasharray(data: JsonElement?): DashArrayValue {
        if (data == null) return DashArrayValue.None

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull?.lowercase()
                if (content == "none") return DashArrayValue.None
            }
            is JsonArray -> {
                val values = data.mapNotNull { it.jsonPrimitive.floatOrNull }
                if (values.isNotEmpty()) {
                    return DashArrayValue.Pattern(values)
                }
            }
            is JsonObject -> {
                val type = data["type"]?.jsonPrimitive?.contentOrNull
                if (type?.lowercase() == "none") return DashArrayValue.None

                val valuesArray = data["values"] as? JsonArray
                if (valuesArray != null) {
                    val values = valuesArray.mapNotNull { it.jsonPrimitive.floatOrNull }
                    if (values.isNotEmpty()) {
                        return DashArrayValue.Pattern(values)
                    }
                }
            }
        }

        return DashArrayValue.None
    }

    private fun extractStrokeDashoffset(data: JsonElement?): Float {
        if (data == null) return 0.0f
        val dp = ValueExtractors.extractDp(data)
        return dp?.value ?: 0.0f
    }

    private fun extractStrokeMiterlimit(data: JsonElement?): Float {
        if (data == null) return 4.0f
        return when (data) {
            is JsonPrimitive -> data.floatOrNull ?: 4.0f
            is JsonObject -> data["value"]?.jsonPrimitive?.floatOrNull ?: 4.0f
            else -> 4.0f
        }
    }

    private fun extractSvgOpacity(data: JsonElement?): Float {
        if (data == null) return 1.0f
        return when (data) {
            is JsonPrimitive -> data.floatOrNull ?: 1.0f
            is JsonObject -> {
                data["alpha"]?.jsonPrimitive?.floatOrNull
                    ?: data["value"]?.jsonPrimitive?.floatOrNull
                    ?: 1.0f
            }
            else -> 1.0f
        }
    }

    private fun extractFillRule(data: JsonElement?): FillRuleValue {
        val keyword = ValueExtractors.extractKeyword(data)?.lowercase()
            ?: return FillRuleValue.NONZERO

        return when (keyword) {
            "evenodd" -> FillRuleValue.EVENODD
            else -> FillRuleValue.NONZERO
        }
    }

    private fun extractStopColor(data: JsonElement?): Color? {
        return ValueExtractors.extractColor(data)
    }

    private fun extractPaintOrder(data: JsonElement?): List<PaintOrderElement> {
        if (data == null) return listOf(PaintOrderElement.FILL, PaintOrderElement.STROKE, PaintOrderElement.MARKERS)

        val result = mutableListOf<PaintOrderElement>()

        when (data) {
            is JsonArray -> {
                data.forEach { element ->
                    val keyword = element.jsonPrimitive.contentOrNull?.uppercase()
                    when (keyword) {
                        "FILL" -> result.add(PaintOrderElement.FILL)
                        "STROKE" -> result.add(PaintOrderElement.STROKE)
                        "MARKERS" -> result.add(PaintOrderElement.MARKERS)
                    }
                }
            }
            is JsonPrimitive -> {
                val content = data.contentOrNull?.uppercase()?.split(" ") ?: emptyList()
                content.forEach { keyword ->
                    when (keyword.trim()) {
                        "FILL" -> result.add(PaintOrderElement.FILL)
                        "STROKE" -> result.add(PaintOrderElement.STROKE)
                        "MARKERS" -> result.add(PaintOrderElement.MARKERS)
                    }
                }
            }
            else -> { /* JsonObject or other - use default */ }
        }

        return result.ifEmpty {
            listOf(PaintOrderElement.FILL, PaintOrderElement.STROKE, PaintOrderElement.MARKERS)
        }
    }

    /**
     * Extract marker value from JSON data.
     */
    private fun extractMarkerValue(data: JsonElement?): MarkerValue {
        if (data == null) return MarkerValue.None

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull?.lowercase()
                return when (content) {
                    "none" -> MarkerValue.None
                    else -> {
                        // Check for URL reference like url(#arrowhead)
                        if (content?.startsWith("url(") == true) {
                            val url = content.removePrefix("url(").removeSuffix(")")
                            MarkerValue.UrlReference(url)
                        } else {
                            MarkerValue.None
                        }
                    }
                }
            }
            is JsonObject -> {
                val type = data["type"]?.jsonPrimitive?.contentOrNull
                return when (type?.lowercase()) {
                    "none" -> MarkerValue.None
                    "url" -> {
                        val url = data["url"]?.jsonPrimitive?.contentOrNull ?: ""
                        MarkerValue.UrlReference(url)
                    }
                    "predefined" -> {
                        val shapeStr = data["shape"]?.jsonPrimitive?.contentOrNull?.uppercase()
                        val size = data["size"]?.jsonPrimitive?.floatOrNull ?: 6f
                        val color = data["color"]?.let { ValueExtractors.extractColor(it) }
                        val shape = when (shapeStr) {
                            "ARROW" -> MarkerShape.ARROW
                            "CIRCLE" -> MarkerShape.CIRCLE
                            "SQUARE" -> MarkerShape.SQUARE
                            "DIAMOND" -> MarkerShape.DIAMOND
                            "CIRCLE_OPEN" -> MarkerShape.CIRCLE_OPEN
                            "SQUARE_OPEN" -> MarkerShape.SQUARE_OPEN
                            else -> MarkerShape.CIRCLE
                        }
                        MarkerValue.Predefined(shape, size, color)
                    }
                    else -> MarkerValue.None
                }
            }
            else -> return MarkerValue.None
        }
    }

    /**
     * Check if a property type is SVG-related.
     */
    fun isSvgProperty(type: String): Boolean {
        return type in SVG_PROPERTIES
    }

    private val SVG_PROPERTIES = setOf(
        "Fill", "FillOpacity", "FillRule",
        "Stroke", "StrokeWidth", "StrokeLinecap", "StrokeLinejoin",
        "StrokeDasharray", "StrokeDashoffset", "StrokeMiterlimit", "StrokeOpacity",
        "StopColor", "StopOpacity", "PaintOrder",
        "Marker", "MarkerStart", "MarkerMid", "MarkerEnd"
    )
}
