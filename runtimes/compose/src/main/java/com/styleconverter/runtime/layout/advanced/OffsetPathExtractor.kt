package com.styleconverter.runtime.layout.advanced

import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts offset path configuration from IR properties.
 */
object OffsetPathExtractor {

    /**
     * Extract offset path configuration from property pairs.
     */
    fun extractOffsetPathConfig(properties: List<Pair<String, JsonElement?>>): OffsetPathConfig {
        var offsetPath: OffsetPathValue = OffsetPathValue.None
        var offsetDistance = 0f
        var offsetDistanceUnit = OffsetDistanceUnit.PERCENTAGE
        var offsetRotate: OffsetRotateValue = OffsetRotateValue.Auto
        var offsetAnchor: OffsetAnchorValue = OffsetAnchorValue.Auto
        var offsetPosition: OffsetAnchorValue = OffsetAnchorValue.Auto

        for ((type, data) in properties) {
            when (type) {
                "OffsetPath" -> offsetPath = extractOffsetPath(data)
                "OffsetDistance" -> {
                    val result = extractOffsetDistance(data)
                    offsetDistance = result.first
                    offsetDistanceUnit = result.second
                }
                "OffsetRotate" -> offsetRotate = extractOffsetRotate(data)
                "OffsetAnchor" -> offsetAnchor = extractOffsetAnchor(data)
                "OffsetPosition" -> offsetPosition = extractOffsetAnchor(data)
            }
        }

        return OffsetPathConfig(
            offsetPath = offsetPath,
            offsetDistance = offsetDistance,
            offsetDistanceUnit = offsetDistanceUnit,
            offsetRotate = offsetRotate,
            offsetAnchor = offsetAnchor,
            offsetPosition = offsetPosition
        )
    }

    private fun extractOffsetPath(data: JsonElement?): OffsetPathValue {
        if (data == null) return OffsetPathValue.None

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull?.lowercase()
                if (content == "none") return OffsetPathValue.None
            }
            is JsonObject -> {
                val type = data["type"]?.jsonPrimitive?.contentOrNull?.lowercase()
                return when (type) {
                    "none" -> OffsetPathValue.None
                    "path" -> {
                        val d = data["d"]?.jsonPrimitive?.contentOrNull
                            ?: data["path"]?.jsonPrimitive?.contentOrNull
                            ?: ""
                        OffsetPathValue.Path(d)
                    }
                    "url" -> {
                        val url = data["url"]?.jsonPrimitive?.contentOrNull ?: ""
                        OffsetPathValue.Url(url)
                    }
                    "ray" -> {
                        // IRAngleSerializer emits the normalized angle under
                        // "deg" (ValueTypes.kt: put("deg", value.degrees));
                        // reading only "angle" left every ray() at 0deg —
                        // Layout_C14's ray(45deg) never rotated on Android.
                        val angle = (data["deg"] as? JsonPrimitive)?.floatOrNull
                            ?: (data["angle"] as? JsonPrimitive)?.floatOrNull ?: 0f
                        val size = data["size"]?.jsonPrimitive?.contentOrNull?.uppercase()?.replace("-", "_")
                        val sizeValue = when (size) {
                            "CLOSEST_SIDE" -> RaySizeValue.CLOSEST_SIDE
                            "CLOSEST_CORNER" -> RaySizeValue.CLOSEST_CORNER
                            "FARTHEST_SIDE" -> RaySizeValue.FARTHEST_SIDE
                            "FARTHEST_CORNER" -> RaySizeValue.FARTHEST_CORNER
                            "SIDES" -> RaySizeValue.SIDES
                            else -> RaySizeValue.CLOSEST_SIDE
                        }
                        val contain = data["contain"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                        OffsetPathValue.Ray(angle, sizeValue, contain)
                    }
                    "circle" -> {
                        val radius = data["radius"]?.let { ValueExtractors.extractDp(it) }
                        OffsetPathValue.Circle(radius)
                    }
                    "ellipse" -> {
                        val radiusX = data["radiusX"]?.let { ValueExtractors.extractDp(it) }
                        val radiusY = data["radiusY"]?.let { ValueExtractors.extractDp(it) }
                        OffsetPathValue.Ellipse(radiusX, radiusY)
                    }
                    "polygon" -> {
                        val pointsArray = data["points"] as? JsonArray ?: return OffsetPathValue.None
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
                        OffsetPathValue.Polygon(points)
                    }
                    "inset" -> {
                        val values = data["values"] as? JsonArray
                        val insets = values?.mapNotNull { ValueExtractors.extractDp(it) } ?: listOf(0.dp)
                        OffsetPathValue.Inset(insets)
                    }
                    else -> OffsetPathValue.None
                }
            }
            else -> return OffsetPathValue.None
        }

        return OffsetPathValue.None
    }

    private fun extractOffsetDistance(data: JsonElement?): Pair<Float, OffsetDistanceUnit> {
        if (data == null) return 0f to OffsetDistanceUnit.PERCENTAGE

        when (data) {
            is JsonPrimitive -> {
                val value = data.floatOrNull ?: 0f
                return value to OffsetDistanceUnit.PERCENTAGE
            }
            is JsonObject -> {
                val type = data["type"]?.jsonPrimitive?.contentOrNull?.lowercase()
                val value = data["value"]?.jsonPrimitive?.floatOrNull
                    ?: data["percentage"]?.jsonPrimitive?.floatOrNull
                    ?: 0f

                return when (type) {
                    "percentage" -> value to OffsetDistanceUnit.PERCENTAGE
                    "length" -> value to OffsetDistanceUnit.LENGTH
                    else -> value to OffsetDistanceUnit.PERCENTAGE
                }
            }
            else -> return 0f to OffsetDistanceUnit.PERCENTAGE
        }
    }

    private fun extractOffsetRotate(data: JsonElement?): OffsetRotateValue {
        if (data == null) return OffsetRotateValue.Auto

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull?.lowercase()
                return when (content) {
                    "auto" -> OffsetRotateValue.Auto
                    "reverse" -> OffsetRotateValue.AutoReverse
                    else -> {
                        data.floatOrNull?.let { OffsetRotateValue.Angle(it) } ?: OffsetRotateValue.Auto
                    }
                }
            }
            is JsonObject -> {
                val type = data["type"]?.jsonPrimitive?.contentOrNull?.lowercase()
                return when (type) {
                    "auto" -> OffsetRotateValue.Auto
                    "reverse", "auto-reverse" -> OffsetRotateValue.AutoReverse
                    "angle" -> {
                        val degrees = data["degrees"]?.jsonPrimitive?.floatOrNull
                            ?: data["value"]?.jsonPrimitive?.floatOrNull
                            ?: 0f
                        OffsetRotateValue.Angle(degrees)
                    }
                    "auto-angle" -> {
                        val degrees = data["degrees"]?.jsonPrimitive?.floatOrNull
                            ?: data["value"]?.jsonPrimitive?.floatOrNull
                            ?: 0f
                        OffsetRotateValue.AutoAngle(degrees)
                    }
                    else -> OffsetRotateValue.Auto
                }
            }
            else -> return OffsetRotateValue.Auto
        }
    }

    private fun extractOffsetAnchor(data: JsonElement?): OffsetAnchorValue {
        if (data == null) return OffsetAnchorValue.Auto

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull?.lowercase()
                if (content == "auto") return OffsetAnchorValue.Auto
            }
            is JsonObject -> {
                val type = (data["type"] as? JsonPrimitive)?.contentOrNull?.lowercase()
                if (type == "auto") return OffsetAnchorValue.Auto

                // The IR position shape is {"type":"position","x":<comp>,
                // "y":<comp>} where each component is EITHER a bare number
                // (percent) OR an object — a positional keyword
                // ({"type":"center"|"left"|…}) or a length/percentage
                // ({"px":N} / {"type":"percentage","value":N}). The old
                // bare `.jsonPrimitive` call THREW on the object shape,
                // and because StyleApplier.extractConfig runs every
                // extractor, the throw nuked the WHOLE style chain: any
                // component carrying offset-anchor/-position lost its
                // background + size (Layout_C13 0.717, Layout_C14 —
                // box vanished while web/iOS rendered it).
                val x = positionComponent(data["x"], horizontal = true) ?: 50f
                val y = positionComponent(data["y"], horizontal = false) ?: 50f
                return OffsetAnchorValue.Position(x, y)
            }
            else -> return OffsetAnchorValue.Auto
        }

        return OffsetAnchorValue.Auto
    }

    /**
     * Resolve one <position> component to a percentage (0..100).
     * css-values-4 §6.1 keyword mapping: left/top → 0%, center → 50%,
     * right/bottom → 100%. Explicit percentages pass through; px lengths
     * are not expressible as a containing-block percentage here → null
     * (caller falls back to 50%, the CSS initial anchor).
     */
    private fun positionComponent(el: JsonElement?, horizontal: Boolean): Float? {
        return when (el) {
            null -> null
            is JsonPrimitive -> el.floatOrNull
            is JsonObject -> {
                val kw = (el["type"] as? JsonPrimitive)?.contentOrNull?.lowercase()
                when (kw) {
                    "left", "top" -> 0f
                    "center" -> 50f
                    "right", "bottom" -> 100f
                    "percentage" -> (el["value"] as? JsonPrimitive)?.floatOrNull
                    else -> (el["value"] as? JsonPrimitive)?.floatOrNull
                }
            }
            else -> null
        }
    }

    /**
     * Check if a property type is offset path-related.
     */
    fun isOffsetPathProperty(type: String): Boolean {
        return type in OFFSET_PATH_PROPERTIES
    }

    private val OFFSET_PATH_PROPERTIES = setOf(
        "OffsetPath", "OffsetDistance", "OffsetRotate", "OffsetAnchor", "OffsetPosition"
    )
}
