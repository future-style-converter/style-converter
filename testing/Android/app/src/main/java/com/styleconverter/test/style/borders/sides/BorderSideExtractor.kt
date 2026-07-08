package com.styleconverter.test.style.borders.sides

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.styleconverter.test.style.PropertyRegistry
import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement

/**
 * Extracts border side configurations from IR properties.
 * Handles both shorthand (border-width) and individual side properties.
 */
object BorderSideExtractor {

    init {
        // Claim every physical + logical border-side property so the
        // legacy dispatch knows we own them. Categories:
        //   * shorthand (BorderWidth/Color/Style)
        //   * physical sides (Top/Right/Bottom/Left × Width/Color/Style)
        //   * logical sides (BlockStart/BlockEnd/InlineStart/InlineEnd × …)
        // CSS spec: https://drafts.csswg.org/css-backgrounds-3/#borders
        //           https://drafts.csswg.org/css-logical/#border-properties
        PropertyRegistry.migrated(
            "BorderWidth", "BorderColor", "BorderStyle",
            "BorderTopWidth", "BorderRightWidth", "BorderBottomWidth", "BorderLeftWidth",
            "BorderTopColor", "BorderRightColor", "BorderBottomColor", "BorderLeftColor",
            "BorderTopStyle", "BorderRightStyle", "BorderBottomStyle", "BorderLeftStyle",
            "BorderBlockStartWidth", "BorderBlockEndWidth",
            "BorderInlineStartWidth", "BorderInlineEndWidth",
            "BorderBlockStartColor", "BorderBlockEndColor",
            "BorderInlineStartColor", "BorderInlineEndColor",
            "BorderBlockStartStyle", "BorderBlockEndStyle",
            "BorderInlineStartStyle", "BorderInlineEndStyle",
            owner = "borders/sides"
        )
    }

    /**
     * Extract border configuration from a list of property type/data pairs.
     *
     * @param properties List of pairs where first is the property type (e.g., "BorderTopWidth")
     *                   and second is the JSON data for that property.
     * @return AllBordersConfig with extracted values for all sides.
     */
    fun extractBorderConfig(properties: List<Pair<String, JsonElement?>>): AllBordersConfig {
        var top = BorderSideConfig()
        var end = BorderSideConfig()
        var bottom = BorderSideConfig()
        var start = BorderSideConfig()

        // Track shorthand values to apply to sides without specific values
        var sharedWidth: Dp? = null
        var sharedColor: Color? = null
        var sharedStyle: ValueExtractors.LineStyle? = null

        properties.forEach { (type, data) ->
            when (type) {
                // Shorthand (all sides)
                "BorderWidth" -> sharedWidth = ValueExtractors.extractBorderWidth(data)
                "BorderColor" -> sharedColor = ValueExtractors.extractColor(data)
                "BorderStyle" -> sharedStyle = ValueExtractors.extractLineStyle(data)

                // Top side (physical and logical)
                "BorderTopWidth", "BorderBlockStartWidth" -> top = top.copy(width = ValueExtractors.extractBorderWidth(data))
                "BorderTopColor", "BorderBlockStartColor" -> top = top.copy(color = ValueExtractors.extractColor(data))
                "BorderTopStyle", "BorderBlockStartStyle" -> top = top.copy(style = ValueExtractors.extractLineStyle(data))

                // Right/End side (physical and logical)
                "BorderRightWidth", "BorderInlineEndWidth" -> end = end.copy(width = ValueExtractors.extractBorderWidth(data))
                "BorderRightColor", "BorderInlineEndColor" -> end = end.copy(color = ValueExtractors.extractColor(data))
                "BorderRightStyle", "BorderInlineEndStyle" -> end = end.copy(style = ValueExtractors.extractLineStyle(data))

                // Bottom side (physical and logical)
                "BorderBottomWidth", "BorderBlockEndWidth" -> bottom = bottom.copy(width = ValueExtractors.extractBorderWidth(data))
                "BorderBottomColor", "BorderBlockEndColor" -> bottom = bottom.copy(color = ValueExtractors.extractColor(data))
                "BorderBottomStyle", "BorderBlockEndStyle" -> bottom = bottom.copy(style = ValueExtractors.extractLineStyle(data))

                // Left/Start side (physical and logical)
                "BorderLeftWidth", "BorderInlineStartWidth" -> start = start.copy(width = ValueExtractors.extractBorderWidth(data))
                "BorderLeftColor", "BorderInlineStartColor" -> start = start.copy(color = ValueExtractors.extractColor(data))
                "BorderLeftStyle", "BorderInlineStartStyle" -> start = start.copy(style = ValueExtractors.extractLineStyle(data))
            }
        }

        // Apply shorthand values to sides that don't have specific values
        if (sharedWidth != null || sharedColor != null || sharedStyle != null) {
            top = top.copy(
                width = top.width ?: sharedWidth,
                color = top.color ?: sharedColor,
                style = top.style ?: sharedStyle
            )
            end = end.copy(
                width = end.width ?: sharedWidth,
                color = end.color ?: sharedColor,
                style = end.style ?: sharedStyle
            )
            bottom = bottom.copy(
                width = bottom.width ?: sharedWidth,
                color = bottom.color ?: sharedColor,
                style = bottom.style ?: sharedStyle
            )
            start = start.copy(
                width = start.width ?: sharedWidth,
                color = start.color ?: sharedColor,
                style = start.style ?: sharedStyle
            )
        }

        return AllBordersConfig(top, end, bottom, start)
    }
}
