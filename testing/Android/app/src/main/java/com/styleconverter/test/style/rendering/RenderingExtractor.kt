package com.styleconverter.test.style.rendering

import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement

/**
 * Extracts rendering configuration from IR properties.
 */
object RenderingExtractor {

    /**
     * Extract rendering configuration from property pairs.
     */
    fun extractRenderingConfig(properties: List<Pair<String, JsonElement?>>): RenderingConfig {
        var colorRendering = ColorRenderingValue.AUTO
        var imageRendering = ImageRenderingValue.AUTO
        var shapeRendering = ShapeRenderingValue.AUTO
        var textRendering = TextRenderingValue.AUTO

        for ((type, data) in properties) {
            when (type) {
                "ColorRendering" -> colorRendering = extractColorRendering(data)
                "ImageRendering" -> imageRendering = extractImageRendering(data)
                "ShapeRendering" -> shapeRendering = extractShapeRendering(data)
                "TextRendering" -> textRendering = extractTextRendering(data)
            }
        }

        return RenderingConfig(
            colorRendering = colorRendering,
            imageRendering = imageRendering,
            shapeRendering = shapeRendering,
            textRendering = textRendering
        )
    }

    private fun extractColorRendering(data: JsonElement?): ColorRenderingValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "")
            ?: return ColorRenderingValue.AUTO

        return when (keyword) {
            "AUTO" -> ColorRenderingValue.AUTO
            "OPTIMIZESPEED" -> ColorRenderingValue.OPTIMIZESPEED
            "OPTIMIZEQUALITY" -> ColorRenderingValue.OPTIMIZEQUALITY
            else -> ColorRenderingValue.AUTO
        }
    }

    private fun extractImageRendering(data: JsonElement?): ImageRenderingValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return ImageRenderingValue.AUTO

        return when (keyword) {
            "AUTO" -> ImageRenderingValue.AUTO
            "SMOOTH" -> ImageRenderingValue.SMOOTH
            "HIGH_QUALITY" -> ImageRenderingValue.HIGH_QUALITY
            "CRISP_EDGES" -> ImageRenderingValue.CRISP_EDGES
            "PIXELATED" -> ImageRenderingValue.PIXELATED
            else -> ImageRenderingValue.AUTO
        }
    }

    private fun extractShapeRendering(data: JsonElement?): ShapeRenderingValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "")
            ?: return ShapeRenderingValue.AUTO

        return when (keyword) {
            "AUTO" -> ShapeRenderingValue.AUTO
            "OPTIMIZESPEED" -> ShapeRenderingValue.OPTIMIZESPEED
            "CRISPEDGES" -> ShapeRenderingValue.CRISPEDGES
            "GEOMETRICPRECISION" -> ShapeRenderingValue.GEOMETRICPRECISION
            else -> ShapeRenderingValue.AUTO
        }
    }

    private fun extractTextRendering(data: JsonElement?): TextRenderingValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "")
            ?: return TextRenderingValue.AUTO

        return when (keyword) {
            "AUTO" -> TextRenderingValue.AUTO
            "OPTIMIZESPEED" -> TextRenderingValue.OPTIMIZESPEED
            "OPTIMIZELEGIBILITY" -> TextRenderingValue.OPTIMIZELEGIBILITY
            "GEOMETRICPRECISION" -> TextRenderingValue.GEOMETRICPRECISION
            else -> TextRenderingValue.AUTO
        }
    }

    /**
     * Check if a property type is rendering-related.
     */
    fun isRenderingProperty(type: String): Boolean {
        return type in RENDERING_PROPERTIES
    }

    private val RENDERING_PROPERTIES = setOf(
        "ColorRendering", "ImageRendering", "ShapeRendering", "TextRendering"
    )
}
