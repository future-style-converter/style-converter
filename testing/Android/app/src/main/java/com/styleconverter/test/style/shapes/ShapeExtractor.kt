package com.styleconverter.test.style.shapes

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts shape configuration from IR properties.
 */
object ShapeExtractor {

    /**
     * Extract shape configuration from property pairs.
     */
    fun extractShapeConfig(properties: List<Pair<String, JsonElement?>>): ShapeConfig {
        var shapeOutside: ShapeOutsideValue = ShapeOutsideValue.None
        var shapeMargin: Dp = 0.dp
        var shapeImageThreshold = 0f

        for ((type, data) in properties) {
            when (type) {
                "ShapeOutside" -> shapeOutside = extractShapeOutside(data)
                "ShapeMargin" -> shapeMargin = extractShapeMargin(data)
                "ShapeImageThreshold" -> shapeImageThreshold = extractShapeImageThreshold(data)
            }
        }

        return ShapeConfig(
            shapeOutside = shapeOutside,
            shapeMargin = shapeMargin,
            shapeImageThreshold = shapeImageThreshold
        )
    }

    private fun extractShapeOutside(data: JsonElement?): ShapeOutsideValue {
        if (data == null) return ShapeOutsideValue.None

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull?.lowercase()?.replace("-", "_")
                return when (content) {
                    "none" -> ShapeOutsideValue.None
                    "margin_box" -> ShapeOutsideValue.MarginBox
                    "content_box" -> ShapeOutsideValue.ContentBox
                    "padding_box" -> ShapeOutsideValue.PaddingBox
                    "border_box" -> ShapeOutsideValue.BorderBox
                    else -> ShapeOutsideValue.None
                }
            }
            is JsonObject -> {
                val type = data["type"]?.jsonPrimitive?.contentOrNull?.lowercase()
                return when (type) {
                    "none" -> ShapeOutsideValue.None
                    "margin-box", "marginbox" -> ShapeOutsideValue.MarginBox
                    "content-box", "contentbox" -> ShapeOutsideValue.ContentBox
                    "padding-box", "paddingbox" -> ShapeOutsideValue.PaddingBox
                    "border-box", "borderbox" -> ShapeOutsideValue.BorderBox
                    "inset" -> extractInset(data)
                    "circle" -> extractCircle(data)
                    "ellipse" -> extractEllipse(data)
                    "polygon" -> extractPolygon(data)
                    "path" -> extractPath(data)
                    "url" -> ShapeOutsideValue.Url(data["url"]?.jsonPrimitive?.contentOrNull ?: "")
                    else -> ShapeOutsideValue.None
                }
            }
            else -> return ShapeOutsideValue.None
        }
    }

    private fun extractInset(data: JsonObject): ShapeOutsideValue.Inset {
        val top = data["top"]?.let { ValueExtractors.extractDp(it) } ?: 0.dp
        val right = data["right"]?.let { ValueExtractors.extractDp(it) } ?: 0.dp
        val bottom = data["bottom"]?.let { ValueExtractors.extractDp(it) } ?: 0.dp
        val left = data["left"]?.let { ValueExtractors.extractDp(it) } ?: 0.dp
        val borderRadius = data["borderRadius"]?.let { ValueExtractors.extractDp(it) }

        return ShapeOutsideValue.Inset(
            top = top,
            right = right,
            bottom = bottom,
            left = left,
            borderRadius = borderRadius
        )
    }

    private fun extractCircle(data: JsonObject): ShapeOutsideValue.Circle {
        val radius = data["radius"]?.let { ValueExtractors.extractDp(it) }
        val centerX = data["centerX"]?.jsonPrimitive?.floatOrNull ?: 50f
        val centerY = data["centerY"]?.jsonPrimitive?.floatOrNull ?: 50f

        return ShapeOutsideValue.Circle(
            radius = radius,
            centerX = centerX,
            centerY = centerY
        )
    }

    private fun extractEllipse(data: JsonObject): ShapeOutsideValue.Ellipse {
        val radiusX = data["radiusX"]?.let { ValueExtractors.extractDp(it) }
        val radiusY = data["radiusY"]?.let { ValueExtractors.extractDp(it) }
        val centerX = data["centerX"]?.jsonPrimitive?.floatOrNull ?: 50f
        val centerY = data["centerY"]?.jsonPrimitive?.floatOrNull ?: 50f

        return ShapeOutsideValue.Ellipse(
            radiusX = radiusX,
            radiusY = radiusY,
            centerX = centerX,
            centerY = centerY
        )
    }

    private fun extractPolygon(data: JsonObject): ShapeOutsideValue.Polygon {
        val pointsArray = data["points"] as? JsonArray ?: return ShapeOutsideValue.Polygon(emptyList())

        val points = pointsArray.mapNotNull { point ->
            when (point) {
                is JsonArray -> {
                    if (point.size >= 2) {
                        val x = point[0].jsonPrimitive.floatOrNull ?: return@mapNotNull null
                        val y = point[1].jsonPrimitive.floatOrNull ?: return@mapNotNull null
                        x to y
                    } else null
                }
                is JsonObject -> {
                    val x = point["x"]?.jsonPrimitive?.floatOrNull ?: return@mapNotNull null
                    val y = point["y"]?.jsonPrimitive?.floatOrNull ?: return@mapNotNull null
                    x to y
                }
                else -> null
            }
        }

        return ShapeOutsideValue.Polygon(points)
    }

    private fun extractPath(data: JsonObject): ShapeOutsideValue.Path {
        val d = data["d"]?.jsonPrimitive?.contentOrNull
            ?: data["path"]?.jsonPrimitive?.contentOrNull
            ?: ""
        return ShapeOutsideValue.Path(d)
    }

    private fun extractShapeMargin(data: JsonElement?): Dp {
        return ValueExtractors.extractDp(data) ?: 0.dp
    }

    private fun extractShapeImageThreshold(data: JsonElement?): Float {
        if (data == null) return 0f
        return when (data) {
            is JsonPrimitive -> data.floatOrNull?.coerceIn(0f, 1f) ?: 0f
            is JsonObject -> {
                data["value"]?.jsonPrimitive?.floatOrNull?.coerceIn(0f, 1f) ?: 0f
            }
            else -> 0f
        }
    }

    /**
     * Check if a property type is shape-related.
     */
    fun isShapeProperty(type: String): Boolean {
        return type in SHAPE_PROPERTIES
    }

    private val SHAPE_PROPERTIES = setOf(
        "ShapeOutside", "ShapeMargin", "ShapeImageThreshold"
    )
}
