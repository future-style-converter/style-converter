package com.styleconverter.test.style.scrolling

import androidx.compose.ui.unit.dp
import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts overflow-related configuration from IR properties.
 *
 * ## Supported Properties
 * - Overflow: Both X and Y overflow
 * - OverflowX, OverflowY: Individual axis overflow
 * - OverflowBlock, OverflowInline: Logical axis overflow
 * - OverflowAnchor: Scroll anchoring
 * - OverflowClipMargin: Clip margin value
 */
object OverflowExtractor {

    /**
     * Extract a complete OverflowConfig from a list of property type/data pairs.
     */
    fun extractOverflowConfig(properties: List<Pair<String, JsonElement?>>): OverflowConfig {
        var config = OverflowConfig()

        for ((type, data) in properties) {
            config = when (type) {
                "Overflow" -> {
                    val behavior = extractOverflowBehavior(data)
                    config.copy(overflowX = behavior, overflowY = behavior)
                }
                "OverflowX" -> config.copy(overflowX = extractOverflowBehavior(data))
                "OverflowY" -> config.copy(overflowY = extractOverflowBehavior(data))
                "OverflowBlock" -> config.copy(overflowY = extractOverflowBehavior(data))
                "OverflowInline" -> config.copy(overflowX = extractOverflowBehavior(data))
                "OverflowAnchor" -> config.copy(anchor = extractOverflowAnchor(data))
                "OverflowClipMargin" -> {
                    val (margin, box) = extractClipMargin(data)
                    config.copy(
                        clipMargin = margin?.dp,
                        clipMarginBox = box
                    )
                }
                else -> config
            }
        }

        return config
    }

    /**
     * Extract overflow behavior from IR data.
     */
    private fun extractOverflowBehavior(json: JsonElement?): OverflowBehavior {
        val keyword = ValueExtractors.extractKeyword(json)?.lowercase() ?: return OverflowBehavior.VISIBLE
        return when (keyword) {
            "visible" -> OverflowBehavior.VISIBLE
            "hidden" -> OverflowBehavior.HIDDEN
            "scroll" -> OverflowBehavior.SCROLL
            "auto" -> OverflowBehavior.AUTO
            "clip" -> OverflowBehavior.CLIP
            else -> OverflowBehavior.VISIBLE
        }
    }

    /**
     * Extract overflow anchor mode from IR data.
     */
    private fun extractOverflowAnchor(json: JsonElement?): OverflowAnchorMode {
        val keyword = ValueExtractors.extractKeyword(json)?.lowercase() ?: return OverflowAnchorMode.AUTO
        return when (keyword) {
            "auto" -> OverflowAnchorMode.AUTO
            "none" -> OverflowAnchorMode.NONE
            else -> OverflowAnchorMode.AUTO
        }
    }

    /**
     * Extract overflow clip margin from IR data.
     *
     * IR format can be:
     * - { "margin": { "px": 10 } }
     * - { "margin": { "px": 10 }, "box": "content-box" }
     * - Just a length value
     */
    private fun extractClipMargin(json: JsonElement?): Pair<Float?, OverflowClipMarginBox> {
        if (json == null) return Pair(null, OverflowClipMarginBox.PADDING_BOX)

        return when (json) {
            is JsonObject -> {
                val margin = json["margin"]?.let { ValueExtractors.extractDp(it)?.value }
                    ?: ValueExtractors.extractDp(json)?.value

                val boxKeyword = json["box"]?.jsonPrimitive?.contentOrNull?.lowercase()
                    ?: json["referenceBox"]?.jsonPrimitive?.contentOrNull?.lowercase()
                val box = when (boxKeyword) {
                    "content-box" -> OverflowClipMarginBox.CONTENT_BOX
                    "padding-box" -> OverflowClipMarginBox.PADDING_BOX
                    "border-box" -> OverflowClipMarginBox.BORDER_BOX
                    else -> OverflowClipMarginBox.PADDING_BOX
                }

                Pair(margin, box)
            }
            else -> {
                val margin = ValueExtractors.extractDp(json)?.value
                Pair(margin, OverflowClipMarginBox.PADDING_BOX)
            }
        }
    }

    /**
     * Check if a property type is an overflow-related property.
     */
    fun isOverflowProperty(type: String): Boolean {
        return type in OVERFLOW_PROPERTIES
    }

    private val OVERFLOW_PROPERTIES = setOf(
        "Overflow",
        "OverflowX",
        "OverflowY",
        "OverflowBlock",
        "OverflowInline",
        "OverflowAnchor",
        "OverflowClipMargin"
    )
}
