package com.styleconverter.test.style.borders.image

import com.styleconverter.test.style.PropertyRegistry
import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts border-image configuration from IR properties.
 */
object BorderImageExtractor {

    init {
        // The 5 CSS border-image longhands (source / slice / width / outset /
        // repeat). The applier is a Composable (BorderImageBox) rather than
        // a Modifier, so the legacy dispatch still has to route to it — we
        // register them here only so the coverage report knows they're owned.
        PropertyRegistry.migrated(
            "BorderImageSource", "BorderImageSlice", "BorderImageWidth",
            "BorderImageOutset", "BorderImageRepeat",
            owner = "borders/image"
        )
    }

    /**
     * Extract complete border-image configuration from property pairs.
     */
    fun extractBorderImageConfig(properties: List<Pair<String, JsonElement?>>): BorderImageConfig {
        var source: BorderImageSourceValue = BorderImageSourceValue.None
        var sliceTop: BorderImageSliceEdge? = null
        var sliceRight: BorderImageSliceEdge? = null
        var sliceBottom: BorderImageSliceEdge? = null
        var sliceLeft: BorderImageSliceEdge? = null
        var sliceFill = false
        var widthTop: BorderImageDimension? = null
        var widthRight: BorderImageDimension? = null
        var widthBottom: BorderImageDimension? = null
        var widthLeft: BorderImageDimension? = null
        var outsetTop: BorderImageDimension? = null
        var outsetRight: BorderImageDimension? = null
        var outsetBottom: BorderImageDimension? = null
        var outsetLeft: BorderImageDimension? = null
        var repeatHorizontal = BorderImageRepeatValue.STRETCH
        var repeatVertical = BorderImageRepeatValue.STRETCH

        for ((type, data) in properties) {
            when (type) {
                "BorderImageSource" -> source = extractBorderImageSource(data)
                "BorderImageSlice" -> {
                    val result = extractBorderImageSlice(data)
                    sliceTop = result.top
                    sliceRight = result.right
                    sliceBottom = result.bottom
                    sliceLeft = result.left
                    sliceFill = result.fill
                }
                "BorderImageWidth" -> {
                    val result = extractBorderImageFourValues(data)
                    widthTop = result.top
                    widthRight = result.right
                    widthBottom = result.bottom
                    widthLeft = result.left
                }
                "BorderImageOutset" -> {
                    val result = extractBorderImageFourValues(data)
                    outsetTop = result.top
                    outsetRight = result.right
                    outsetBottom = result.bottom
                    outsetLeft = result.left
                }
                "BorderImageRepeat" -> {
                    val result = extractBorderImageRepeat(data)
                    repeatHorizontal = result.first
                    repeatVertical = result.second
                }
            }
        }

        return BorderImageConfig(
            source = source,
            sliceTop = sliceTop,
            sliceRight = sliceRight,
            sliceBottom = sliceBottom,
            sliceLeft = sliceLeft,
            sliceFill = sliceFill,
            widthTop = widthTop,
            widthRight = widthRight,
            widthBottom = widthBottom,
            widthLeft = widthLeft,
            outsetTop = outsetTop,
            outsetRight = outsetRight,
            outsetBottom = outsetBottom,
            outsetLeft = outsetLeft,
            repeatHorizontal = repeatHorizontal,
            repeatVertical = repeatVertical
        )
    }

    /**
     * Extract border-image-source from IR data.
     */
    fun extractBorderImageSource(data: JsonElement?): BorderImageSourceValue {
        if (data == null) return BorderImageSourceValue.None
        if (data !is JsonObject) return BorderImageSourceValue.None

        val sourceData = (data["source"] as? JsonObject) ?: data
        val type = sourceData["type"]?.jsonPrimitive?.contentOrNull

        return when (type?.lowercase()) {
            "url" -> {
                val url = sourceData["url"]?.jsonPrimitive?.contentOrNull ?: ""
                BorderImageSourceValue.Url(url)
            }
            "gradient" -> {
                val gradient = sourceData["gradient"]?.jsonPrimitive?.contentOrNull ?: ""
                BorderImageSourceValue.Gradient(gradient)
            }
            "none" -> BorderImageSourceValue.None
            else -> BorderImageSourceValue.None
        }
    }

    /**
     * Extract border-image-slice values.
     */
    private fun extractBorderImageSlice(data: JsonElement?): SliceResult {
        if (data == null) return SliceResult()
        if (data !is JsonObject) return SliceResult()

        val top = extractSliceEdge(data["top"])
        val right = extractSliceEdge(data["right"])
        val bottom = extractSliceEdge(data["bottom"])
        val left = extractSliceEdge(data["left"])
        val fill = data["fill"]?.jsonPrimitive?.contentOrNull?.lowercase() == "true" ||
                   data["fill"]?.jsonPrimitive?.contentOrNull?.lowercase() == "fill"

        return SliceResult(top, right, bottom, left, fill)
    }

    private fun extractSliceEdge(data: JsonElement?): BorderImageSliceEdge? {
        if (data == null) return null
        if (data !is JsonObject) return null

        val type = data["type"]?.jsonPrimitive?.contentOrNull
        return when (type?.lowercase()) {
            "number" -> {
                val value = data["value"]?.jsonPrimitive?.floatOrNull
                    ?: (data["value"] as? JsonObject)?.get("value")?.jsonPrimitive?.floatOrNull
                    ?: return null
                BorderImageSliceEdge(value, isPercentage = false)
            }
            "percentage" -> {
                val value = data["percentage"]?.jsonPrimitive?.floatOrNull
                    ?: data["value"]?.jsonPrimitive?.floatOrNull
                    ?: return null
                BorderImageSliceEdge(value, isPercentage = true)
            }
            else -> null
        }
    }

    /**
     * Extract four-value properties (width, outset).
     */
    private fun extractBorderImageFourValues(data: JsonElement?): FourValuesResult {
        if (data == null) return FourValuesResult()
        if (data !is JsonObject) return FourValuesResult()

        val top = extractDimension(data["top"])
        val right = extractDimension(data["right"])
        val bottom = extractDimension(data["bottom"])
        val left = extractDimension(data["left"])

        return FourValuesResult(top, right, bottom, left)
    }

    private fun extractDimension(data: JsonElement?): BorderImageDimension? {
        if (data == null) return null
        if (data !is JsonObject) return null

        val type = data["type"]?.jsonPrimitive?.contentOrNull
        return when (type?.lowercase()) {
            "auto" -> BorderImageDimension.Auto
            "length" -> {
                val dp = ValueExtractors.extractDp(data)
                if (dp != null) BorderImageDimension.Length(dp) else null
            }
            "percentage" -> {
                val value = data["percentage"]?.jsonPrimitive?.floatOrNull
                    ?: data["value"]?.jsonPrimitive?.floatOrNull
                    ?: return null
                BorderImageDimension.Percentage(value)
            }
            "number" -> {
                val value = data["value"]?.jsonPrimitive?.floatOrNull ?: return null
                BorderImageDimension.Number(value)
            }
            else -> null
        }
    }

    /**
     * Extract border-image-repeat values.
     */
    private fun extractBorderImageRepeat(data: JsonElement?): Pair<BorderImageRepeatValue, BorderImageRepeatValue> {
        if (data == null) return Pair(BorderImageRepeatValue.STRETCH, BorderImageRepeatValue.STRETCH)
        if (data !is JsonObject) return Pair(BorderImageRepeatValue.STRETCH, BorderImageRepeatValue.STRETCH)

        val horizontal = extractRepeatValue(data["horizontal"]?.jsonPrimitive?.contentOrNull)
        val vertical = extractRepeatValue(data["vertical"]?.jsonPrimitive?.contentOrNull)
            ?: horizontal

        return Pair(horizontal, vertical)
    }

    private fun extractRepeatValue(keyword: String?): BorderImageRepeatValue {
        return when (keyword?.uppercase()) {
            "STRETCH" -> BorderImageRepeatValue.STRETCH
            "REPEAT" -> BorderImageRepeatValue.REPEAT
            "ROUND" -> BorderImageRepeatValue.ROUND
            "SPACE" -> BorderImageRepeatValue.SPACE
            else -> BorderImageRepeatValue.STRETCH
        }
    }

    /**
     * Check if a property type is border-image related.
     */
    fun isBorderImageProperty(type: String): Boolean {
        return type in BORDER_IMAGE_PROPERTIES
    }

    private val BORDER_IMAGE_PROPERTIES = setOf(
        "BorderImageSource", "BorderImageSlice", "BorderImageWidth",
        "BorderImageOutset", "BorderImageRepeat"
    )

    // Helper data classes
    private data class SliceResult(
        val top: BorderImageSliceEdge? = null,
        val right: BorderImageSliceEdge? = null,
        val bottom: BorderImageSliceEdge? = null,
        val left: BorderImageSliceEdge? = null,
        val fill: Boolean = false
    )

    private data class FourValuesResult(
        val top: BorderImageDimension? = null,
        val right: BorderImageDimension? = null,
        val bottom: BorderImageDimension? = null,
        val left: BorderImageDimension? = null
    )
}
