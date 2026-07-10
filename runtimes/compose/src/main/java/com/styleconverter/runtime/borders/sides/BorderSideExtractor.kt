package com.styleconverter.runtime.borders.sides

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.PropertyRegistry
import com.styleconverter.runtime.core.types.ValueExtractors
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

        // Resolve CSS `currentColor` for border colors. Per css-backgrounds-3
        // §4.1 the INITIAL value of border-*-color is `currentcolor`, i.e. the
        // element's computed `color`. A side declared with style+width but no
        // color must therefore paint with the text color — NOT black. Our
        // renderer has no full cascade, so the fallback chain is:
        //   1. the element's own `Color` property, if declared;
        //   2. #eee — the capture harness body color that `color` inherits
        //      from on web (apps/web-harness/index.html `body { color:#eee }`),
        //      which is what the browser resolves currentcolor to for these
        //      fixtures (Borders_C04: web paints a light dotted border,
        //      Android painted black → 0.8665).
        // extractColor returns null for the `currentcolor` keyword itself, so
        // explicit `border-color: currentColor` declarations land here too.
        val currentColor = properties.firstOrNull { it.first == "Color" }
            ?.second?.let { ValueExtractors.extractColor(it) }
            ?: Color(0xFFEEEEEE)

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

        // Fill any still-missing side color with the resolved currentColor
        // (AFTER the shorthand merge so an explicit `border-color` shorthand
        // keeps precedence over the currentcolor fallback).
        if (top.color == null) top = top.copy(color = currentColor)
        if (end.color == null) end = end.copy(color = currentColor)
        if (bottom.color == null) bottom = bottom.copy(color = currentColor)
        if (start.color == null) start = start.copy(color = currentColor)

        // css-backgrounds-3 §4.3: border-width's initial value is `medium`
        // (3px in every browser UA sheet). A side declared with a visible
        // style but NO width must therefore paint 3px — not vanish.
        // Borders_Decorated (`border-block-end-style: dotted`, no width)
        // renders a 3px dotted bottom edge on web; Android dropped it
        // because hasBorder required an explicit width.
        fun applyMediumDefault(side: BorderSideConfig): BorderSideConfig =
            if (side.width == null && side.style != null &&
                side.style != ValueExtractors.LineStyle.NONE &&
                side.style != ValueExtractors.LineStyle.HIDDEN
            ) side.copy(width = 3.dp) else side
        top = applyMediumDefault(top)
        end = applyMediumDefault(end)
        bottom = applyMediumDefault(bottom)
        start = applyMediumDefault(start)

        return AllBordersConfig(top, end, bottom, start)
    }
}
