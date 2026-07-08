package com.styleconverter.test.style.effects.mask

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.styleconverter.test.style.PropertyRegistry
import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts mask configuration from IR properties.
 *
 * ## Gradient Mask Extraction
 * Parses gradient definitions from mask-image and converts to MaskGradientConfig.
 * Supports linear-gradient, radial-gradient, and conic-gradient.
 *
 * ## URL Mask Extraction
 * Extracts URL from mask-image: url(...) for external image loading.
 */
object MaskExtractor {

    init {
        // Phase 8 registration. Mask longhands + the MaskBorder* family.
        // MaskPositionX/Y and MaskType are claimed even though the Compose
        // applier currently falls back on a composed BlendMode.DstIn path and
        // does not split position by axis; MaskBorder* is all-TODO (analogous
        // to border-image, tracked in Phase 5 follow-ups). Claiming them in
        // the registry stops the legacy dispatch from trying to re-render
        // them via the old sdui path and keeps the coverage matrix honest.
        PropertyRegistry.migrated(
            "MaskImage",
            "MaskMode",
            "MaskRepeat",
            "MaskPosition",
            "MaskPositionX",
            "MaskPositionY",
            "MaskSize",
            "MaskOrigin",
            "MaskClip",
            "MaskComposite",
            "MaskType",
            "MaskBorderSource",
            "MaskBorderSlice",
            "MaskBorderWidth",
            "MaskBorderOutset",
            "MaskBorderRepeat",
            "MaskBorderMode",
            owner = "effects/mask"
        )
    }

    /**
     * Extract complete mask configuration from property pairs.
     */
    fun extractMaskConfig(properties: List<Pair<String, JsonElement?>>): MaskConfig {
        var hasImage = false
        var imageUrl: String? = null
        var isGradient = false
        var gradient: MaskGradientConfig? = null
        var mode = MaskModeValue.MATCH_SOURCE
        var size: MaskSizeValue = MaskSizeValue.Auto
        var repeat = MaskRepeatValue.REPEAT
        var position = MaskPositionValue()
        var composite = MaskCompositeValue.ADD
        var clip = MaskBoxValue.BORDER_BOX
        var origin = MaskBoxValue.BORDER_BOX

        for ((type, data) in properties) {
            when (type) {
                "MaskImage" -> {
                    val result = extractMaskImage(data)
                    hasImage = result.hasImage
                    imageUrl = result.url
                    isGradient = result.isGradient
                    gradient = result.gradient
                }
                "MaskMode" -> mode = extractMaskMode(data)
                "MaskSize" -> size = extractMaskSize(data)
                "MaskRepeat" -> repeat = extractMaskRepeat(data)
                "MaskPosition" -> position = extractMaskPosition(data)
                "MaskComposite" -> composite = extractMaskComposite(data)
                "MaskClip" -> clip = extractMaskBox(data)
                "MaskOrigin" -> origin = extractMaskBox(data)
            }
        }

        return MaskConfig(
            hasImage = hasImage,
            imageUrl = imageUrl,
            isGradient = isGradient,
            gradient = gradient,
            mode = mode,
            size = size,
            repeat = repeat,
            position = position,
            composite = composite,
            clip = clip,
            origin = origin
        )
    }

    /**
     * Result of extracting mask image info.
     */
    private data class MaskImageResult(
        val hasImage: Boolean,
        val url: String?,
        val isGradient: Boolean,
        val gradient: MaskGradientConfig?
    )

    /**
     * Extract mask image info from MaskImage property data.
     */
    private fun extractMaskImage(data: JsonElement?): MaskImageResult {
        if (data == null) return MaskImageResult(false, null, false, null)

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull?.lowercase()
                return if (content == "none") {
                    MaskImageResult(false, null, false, null)
                } else {
                    MaskImageResult(true, null, false, null)
                }
            }
            is JsonObject -> {
                val type = data["type"]?.jsonPrimitive?.contentOrNull?.lowercase()
                return when {
                    type == "none" -> MaskImageResult(false, null, false, null)
                    type == "url" || data.containsKey("url") -> {
                        val url = data["url"]?.jsonPrimitive?.contentOrNull
                        MaskImageResult(true, url, false, null)
                    }
                    type == "linear-gradient" || type == "repeating-linear-gradient" -> {
                        val gradient = extractLinearGradient(data)
                        MaskImageResult(true, null, true, gradient)
                    }
                    type == "radial-gradient" || type == "repeating-radial-gradient" -> {
                        val gradient = extractRadialGradient(data)
                        MaskImageResult(true, null, true, gradient)
                    }
                    type == "conic-gradient" || type == "repeating-conic-gradient" -> {
                        val gradient = extractConicGradient(data)
                        MaskImageResult(true, null, true, gradient)
                    }
                    type?.contains("gradient") == true -> {
                        // Generic gradient fallback
                        MaskImageResult(true, null, true, null)
                    }
                    else -> {
                        // Check for values array
                        val values = data["values"] as? JsonArray
                        if (values != null && values.isNotEmpty()) {
                            extractMaskImage(values[0])
                        } else {
                            MaskImageResult(true, null, false, null)
                        }
                    }
                }
            }
            is JsonArray -> {
                if (data.isEmpty()) return MaskImageResult(false, null, false, null)
                return extractMaskImage(data[0])
            }
            else -> return MaskImageResult(false, null, false, null)
        }
    }

    /**
     * Extract linear gradient configuration.
     */
    private fun extractLinearGradient(data: JsonObject): MaskGradientConfig.Linear? {
        val type = data["type"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: ""
        val repeating = type.startsWith("repeating")

        // Extract angle
        val angleData = data["angle"]
        val angle = when (angleData) {
            is JsonPrimitive -> angleData.floatOrNull ?: 180f
            is JsonObject -> {
                angleData["deg"]?.jsonPrimitive?.floatOrNull
                    ?: angleData["degrees"]?.jsonPrimitive?.floatOrNull
                    ?: 180f
            }
            else -> 180f
        }

        // Extract color stops
        val stopsData = data["stops"] ?: data["colorStops"]
        val colorStops = extractColorStops(stopsData)

        return if (colorStops.isNotEmpty()) {
            MaskGradientConfig.Linear(angle, colorStops, repeating)
        } else {
            // Default fade gradient for mask
            MaskGradientConfig.Linear(
                angle = angle,
                colorStops = listOf(
                    MaskColorStop(Color.White, 0f),
                    MaskColorStop(Color.Transparent, 1f)
                ),
                repeating = repeating
            )
        }
    }

    /**
     * Extract radial gradient configuration.
     */
    private fun extractRadialGradient(data: JsonObject): MaskGradientConfig.Radial? {
        val type = data["type"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: ""
        val repeating = type.startsWith("repeating")

        // Extract center position
        val centerX = data["centerX"]?.jsonPrimitive?.floatOrNull
            ?: data["x"]?.jsonPrimitive?.floatOrNull
            ?: 0.5f
        val centerY = data["centerY"]?.jsonPrimitive?.floatOrNull
            ?: data["y"]?.jsonPrimitive?.floatOrNull
            ?: 0.5f

        // Extract color stops
        val stopsData = data["stops"] ?: data["colorStops"]
        val colorStops = extractColorStops(stopsData)

        return if (colorStops.isNotEmpty()) {
            MaskGradientConfig.Radial(centerX, centerY, colorStops, repeating)
        } else {
            // Default radial fade for mask
            MaskGradientConfig.Radial(
                centerX = centerX,
                centerY = centerY,
                colorStops = listOf(
                    MaskColorStop(Color.White, 0f),
                    MaskColorStop(Color.Transparent, 1f)
                ),
                repeating = repeating
            )
        }
    }

    /**
     * Extract conic gradient configuration.
     */
    private fun extractConicGradient(data: JsonObject): MaskGradientConfig.Conic? {
        val type = data["type"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: ""
        val repeating = type.startsWith("repeating")

        // Extract center position
        val centerX = data["centerX"]?.jsonPrimitive?.floatOrNull
            ?: data["x"]?.jsonPrimitive?.floatOrNull
            ?: 0.5f
        val centerY = data["centerY"]?.jsonPrimitive?.floatOrNull
            ?: data["y"]?.jsonPrimitive?.floatOrNull
            ?: 0.5f

        // Extract starting angle
        val angleData = data["angle"] ?: data["fromAngle"]
        val angle = when (angleData) {
            is JsonPrimitive -> angleData.floatOrNull ?: 0f
            is JsonObject -> {
                angleData["deg"]?.jsonPrimitive?.floatOrNull
                    ?: angleData["degrees"]?.jsonPrimitive?.floatOrNull
                    ?: 0f
            }
            else -> 0f
        }

        // Extract color stops
        val stopsData = data["stops"] ?: data["colorStops"]
        val colorStops = extractColorStops(stopsData)

        return if (colorStops.isNotEmpty()) {
            MaskGradientConfig.Conic(centerX, centerY, angle, colorStops, repeating)
        } else {
            // Default conic gradient for mask
            MaskGradientConfig.Conic(
                centerX = centerX,
                centerY = centerY,
                angle = angle,
                colorStops = listOf(
                    MaskColorStop(Color.White, 0f),
                    MaskColorStop(Color.Transparent, 1f)
                ),
                repeating = repeating
            )
        }
    }

    /**
     * Extract color stops from gradient data.
     */
    private fun extractColorStops(data: JsonElement?): List<MaskColorStop> {
        if (data == null) return emptyList()

        val stops = mutableListOf<MaskColorStop>()

        when (data) {
            is JsonArray -> {
                data.forEachIndexed { index, stopElement ->
                    val stop = extractSingleColorStop(stopElement, index, data.size)
                    if (stop != null) stops.add(stop)
                }
            }
            is JsonObject -> {
                val stop = extractSingleColorStop(data, 0, 1)
                if (stop != null) stops.add(stop)
            }
            else -> { /* ignore */ }
        }

        return stops
    }

    /**
     * Extract a single color stop.
     */
    private fun extractSingleColorStop(element: JsonElement, index: Int, total: Int): MaskColorStop? {
        when (element) {
            is JsonObject -> {
                // Get color
                val colorData = element["color"] ?: element["c"]
                val color = extractMaskColor(colorData) ?: return null

                // Get position
                val position = element["position"]?.jsonPrimitive?.floatOrNull
                    ?: element["pos"]?.jsonPrimitive?.floatOrNull
                    ?: (if (total > 1) index.toFloat() / (total - 1) else 0f)

                return MaskColorStop(color, position.coerceIn(0f, 1f))
            }
            is JsonArray -> {
                // [position, color] or [color, position] format
                if (element.size >= 2) {
                    val first = element[0]
                    val second = element[1]

                    // Try to determine which is color and which is position
                    val position = first.jsonPrimitive.floatOrNull
                        ?: second.jsonPrimitive.floatOrNull
                        ?: (if (total > 1) index.toFloat() / (total - 1) else 0f)

                    val colorElement = if (first.jsonPrimitive.floatOrNull != null) second else first
                    val color = extractMaskColor(colorElement) ?: return null

                    return MaskColorStop(color, position.coerceIn(0f, 1f))
                }
            }
            else -> { /* ignore */ }
        }
        return null
    }

    /**
     * Extract color for mask (typically black/white for alpha channel masking).
     */
    private fun extractMaskColor(data: JsonElement?): Color? {
        if (data == null) return null

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull?.lowercase() ?: return null
                return when (content) {
                    "white", "#fff", "#ffffff" -> Color.White
                    "black", "#000", "#000000" -> Color.Black
                    "transparent" -> Color.Transparent
                    else -> parseHexColor(content)
                }
            }
            is JsonObject -> {
                // Check for sRGB
                val srgb = data["srgb"]?.jsonObject
                if (srgb != null) {
                    val r = srgb["r"]?.jsonPrimitive?.floatOrNull ?: 0f
                    val g = srgb["g"]?.jsonPrimitive?.floatOrNull ?: 0f
                    val b = srgb["b"]?.jsonPrimitive?.floatOrNull ?: 0f
                    val a = srgb["a"]?.jsonPrimitive?.floatOrNull ?: 1f
                    return Color(r, g, b, a)
                }

                // Check for direct r,g,b,a values
                val r = data["r"]?.jsonPrimitive?.floatOrNull
                val g = data["g"]?.jsonPrimitive?.floatOrNull
                val b = data["b"]?.jsonPrimitive?.floatOrNull
                if (r != null && g != null && b != null) {
                    val a = data["a"]?.jsonPrimitive?.floatOrNull ?: 1f
                    // Handle 0-255 range
                    return if (r > 1f || g > 1f || b > 1f) {
                        Color(r.toInt(), g.toInt(), b.toInt(), (a * 255).toInt())
                    } else {
                        Color(r, g, b, a)
                    }
                }

                // Check for "original" string
                val original = data["original"]?.jsonPrimitive?.contentOrNull
                if (original != null) {
                    return parseHexColor(original.lowercase())
                }
            }
            else -> { /* ignore */ }
        }

        return null
    }

    /**
     * Parse hex color string.
     */
    private fun parseHexColor(hex: String): Color? {
        val cleanHex = hex.removePrefix("#")

        return try {
            when (cleanHex.length) {
                3 -> {
                    val r = cleanHex[0].digitToInt(16) * 17
                    val g = cleanHex[1].digitToInt(16) * 17
                    val b = cleanHex[2].digitToInt(16) * 17
                    Color(r, g, b)
                }
                4 -> {
                    val r = cleanHex[0].digitToInt(16) * 17
                    val g = cleanHex[1].digitToInt(16) * 17
                    val b = cleanHex[2].digitToInt(16) * 17
                    val a = cleanHex[3].digitToInt(16) * 17
                    Color(r, g, b, a)
                }
                6 -> {
                    val r = cleanHex.substring(0, 2).toInt(16)
                    val g = cleanHex.substring(2, 4).toInt(16)
                    val b = cleanHex.substring(4, 6).toInt(16)
                    Color(r, g, b)
                }
                8 -> {
                    val r = cleanHex.substring(0, 2).toInt(16)
                    val g = cleanHex.substring(2, 4).toInt(16)
                    val b = cleanHex.substring(4, 6).toInt(16)
                    val a = cleanHex.substring(6, 8).toInt(16)
                    Color(r, g, b, a)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractMaskMode(data: JsonElement?): MaskModeValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return MaskModeValue.MATCH_SOURCE

        return when (keyword) {
            "ALPHA" -> MaskModeValue.ALPHA
            "LUMINANCE" -> MaskModeValue.LUMINANCE
            "MATCH_SOURCE" -> MaskModeValue.MATCH_SOURCE
            else -> MaskModeValue.MATCH_SOURCE
        }
    }

    private fun extractMaskSize(data: JsonElement?): MaskSizeValue {
        if (data == null) return MaskSizeValue.Auto

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull?.uppercase()?.replace("-", "_")
                return when (content) {
                    "CONTAIN" -> MaskSizeValue.Contain
                    "COVER" -> MaskSizeValue.Cover
                    "AUTO" -> MaskSizeValue.Auto
                    else -> MaskSizeValue.Auto
                }
            }
            is JsonObject -> {
                val type = data["type"]?.jsonPrimitive?.contentOrNull?.uppercase()
                when (type) {
                    "CONTAIN" -> return MaskSizeValue.Contain
                    "COVER" -> return MaskSizeValue.Cover
                    "AUTO" -> return MaskSizeValue.Auto
                }

                val width = data["width"]?.let { ValueExtractors.extractDp(it) }
                val height = data["height"]?.let { ValueExtractors.extractDp(it) }

                return if (width != null || height != null) {
                    MaskSizeValue.Dimensions(width, height)
                } else {
                    MaskSizeValue.Auto
                }
            }
            else -> return MaskSizeValue.Auto
        }
    }

    private fun extractMaskRepeat(data: JsonElement?): MaskRepeatValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return MaskRepeatValue.REPEAT

        return when (keyword) {
            "REPEAT" -> MaskRepeatValue.REPEAT
            "NO_REPEAT" -> MaskRepeatValue.NO_REPEAT
            "REPEAT_X" -> MaskRepeatValue.REPEAT_X
            "REPEAT_Y" -> MaskRepeatValue.REPEAT_Y
            "SPACE" -> MaskRepeatValue.SPACE
            "ROUND" -> MaskRepeatValue.ROUND
            else -> MaskRepeatValue.REPEAT
        }
    }

    private fun extractMaskPosition(data: JsonElement?): MaskPositionValue {
        if (data == null) return MaskPositionValue()

        val obj = data as? JsonObject ?: return MaskPositionValue()

        val xElement = obj["x"]
        val yElement = obj["y"]

        val x = extractPositionComponent(xElement, isHorizontal = true)
        val y = extractPositionComponent(yElement, isHorizontal = false)

        return MaskPositionValue(x = x, y = y)
    }

    private fun extractPositionComponent(data: JsonElement?, isHorizontal: Boolean): PositionComponent {
        if (data == null) {
            return if (isHorizontal) {
                PositionComponent.Keyword(HorizontalPosition.LEFT)
            } else {
                PositionComponent.Keyword(VerticalPosition.TOP)
            }
        }

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull?.uppercase()
                when (content) {
                    "LEFT" -> return PositionComponent.Keyword(HorizontalPosition.LEFT)
                    "CENTER" -> return PositionComponent.Keyword(
                        if (isHorizontal) HorizontalPosition.CENTER else VerticalPosition.CENTER
                    )
                    "RIGHT" -> return PositionComponent.Keyword(HorizontalPosition.RIGHT)
                    "TOP" -> return PositionComponent.Keyword(VerticalPosition.TOP)
                    "BOTTOM" -> return PositionComponent.Keyword(VerticalPosition.BOTTOM)
                }

                // Try percentage
                data.floatOrNull?.let {
                    return PositionComponent.Percentage(it)
                }
            }
            is JsonObject -> {
                val type = data["type"]?.jsonPrimitive?.contentOrNull
                when (type) {
                    "percentage" -> {
                        val value = data["percentage"]?.jsonPrimitive?.floatOrNull
                            ?: data["value"]?.jsonPrimitive?.floatOrNull
                            ?: 0f
                        return PositionComponent.Percentage(value)
                    }
                    "length" -> {
                        val dp = ValueExtractors.extractDp(data)
                        return PositionComponent.Length(dp ?: 0.dp)
                    }
                }
            }
            else -> { /* JsonArray or other - use default */ }
        }

        return if (isHorizontal) {
            PositionComponent.Keyword(HorizontalPosition.LEFT)
        } else {
            PositionComponent.Keyword(VerticalPosition.TOP)
        }
    }

    private fun extractMaskComposite(data: JsonElement?): MaskCompositeValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()
            ?: return MaskCompositeValue.ADD

        return when (keyword) {
            "ADD" -> MaskCompositeValue.ADD
            "SUBTRACT" -> MaskCompositeValue.SUBTRACT
            "INTERSECT" -> MaskCompositeValue.INTERSECT
            "EXCLUDE" -> MaskCompositeValue.EXCLUDE
            else -> MaskCompositeValue.ADD
        }
    }

    private fun extractMaskBox(data: JsonElement?): MaskBoxValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return MaskBoxValue.BORDER_BOX

        return when (keyword) {
            "CONTENT_BOX" -> MaskBoxValue.CONTENT_BOX
            "PADDING_BOX" -> MaskBoxValue.PADDING_BOX
            "BORDER_BOX" -> MaskBoxValue.BORDER_BOX
            "FILL_BOX" -> MaskBoxValue.FILL_BOX
            "STROKE_BOX" -> MaskBoxValue.STROKE_BOX
            "VIEW_BOX" -> MaskBoxValue.VIEW_BOX
            "NO_CLIP" -> MaskBoxValue.NO_CLIP
            else -> MaskBoxValue.BORDER_BOX
        }
    }

    /**
     * Check if a property type is mask-related.
     */
    fun isMaskProperty(type: String): Boolean {
        return type in MASK_PROPERTIES
    }

    private val MASK_PROPERTIES = setOf(
        "MaskImage", "MaskMode", "MaskSize", "MaskRepeat",
        "MaskPosition", "MaskPositionX", "MaskPositionY",
        "MaskComposite", "MaskClip", "MaskOrigin"
    )
}
