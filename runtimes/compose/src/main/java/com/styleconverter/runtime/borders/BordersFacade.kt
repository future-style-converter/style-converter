package com.styleconverter.runtime.borders

import androidx.compose.ui.Modifier
import com.styleconverter.runtime.borders.outline.OutlineApplier
import com.styleconverter.runtime.borders.outline.OutlineConfig
import com.styleconverter.runtime.borders.outline.OutlineExtractor
import com.styleconverter.runtime.borders.radius.BorderRadiusApplier
import com.styleconverter.runtime.borders.radius.BorderRadiusConfig
import com.styleconverter.runtime.borders.radius.BorderRadiusExtractor
import com.styleconverter.runtime.borders.sides.AllBordersConfig
import com.styleconverter.runtime.borders.sides.BorderSideApplier
import com.styleconverter.runtime.borders.sides.BorderSideExtractor
import kotlinx.serialization.json.JsonElement

/**
 * Facade for all border-related property handling.
 *
 * This provides a unified interface for extracting and applying:
 * - Border sides (width, color, style for each side)
 * - Border radius (all four corners)
 * - Outline (outline-width, outline-style, outline-color, outline-offset)
 *
 * ## Usage
 * ```kotlin
 * val properties: List<Pair<String, JsonElement?>> = ...
 * val config = BordersFacade.extractConfig(properties)
 * val modifier = BordersFacade.apply(Modifier, config)
 * ```
 *
 * ## Order of Operations
 * 1. Border radius (clip) is applied first to establish shape
 * 2. Border sides are drawn after to respect the clipped shape
 */
object BordersFacade {

    /**
     * Combined configuration for all border properties.
     */
    data class BordersConfig(
        val sides: AllBordersConfig,
        val radius: BorderRadiusConfig,
        val outline: OutlineConfig = OutlineConfig()
    ) {
        /**
         * Check if there are any border properties to apply.
         */
        val hasBorders: Boolean get() = sides.hasBorders || radius.hasRadius || outline.hasOutline
    }

    /**
     * Extract all border configurations from a list of property type/data pairs.
     *
     * @param properties List of pairs where first is the property type (e.g., "BorderTopWidth")
     *                   and second is the JSON data for that property.
     * @return BordersConfig containing configurations for sides and radius.
     */
    fun extractConfig(
        properties: List<Pair<String, JsonElement?>>,
        // RC-B1 (TITAN WPT lane) — forwarded to the side/outline extractors'
        // currentColor bottom-out (WPT capture → spec black ink, dark stage
        // → the historical #eee). Default false keeps every legacy caller
        // byte-identical; StyleApplier.extractConfig passes the live flag.
        wptCaptureMode: Boolean = false,
    ): BordersConfig {
        // Extract component dimensions for resolving percentage-based border radius
        val componentWidth = properties.find { it.first == "Width" }?.second
            ?.let { com.styleconverter.runtime.core.types.ValueExtractors.extractDp(it) }
        val componentHeight = properties.find { it.first == "Height" }?.second
            ?.let { com.styleconverter.runtime.core.types.ValueExtractors.extractDp(it) }

        return BordersConfig(
            sides = BorderSideExtractor.extractBorderConfig(properties, wptCaptureMode),
            radius = BorderRadiusExtractor.extractRadiusConfig(properties, componentWidth, componentHeight),
            outline = OutlineExtractor.extractOutlineConfig(properties, wptCaptureMode)
        )
    }

    /**
     * Apply all border styling to a modifier.
     *
     * @param modifier The modifier to apply borders to.
     * @param config The combined border configuration.
     * @return Modified modifier with borders and radius applied.
     */
    fun apply(modifier: Modifier, config: BordersConfig): Modifier {
        var result = modifier

        // Apply radius first (clip)
        result = BorderRadiusApplier.applyRadius(result, config.radius)

        // Then apply borders
        result = BorderSideApplier.applyBorders(result, config.sides)

        // Apply outline (drawn outside the element)
        result = OutlineApplier.applyOutline(result, config.outline)

        return result
    }

    /**
     * Check if a property type is a border-related property.
     *
     * @param type The property type string.
     * @return True if this is a border side or radius property.
     */
    fun isBorderProperty(type: String): Boolean {
        // Handle shorthand properties
        if (type == "BorderWidth" || type == "BorderStyle" || type == "BorderColor") return true
        // L4 corner-shape extensions: `BorderBoundary` (CSS Borders 4)
        // selects which painted edge the radius clips to (border vs
        // padding box); `CornerShape` (CSS Borders 4) lets each corner
        // be a notch / scoop / bevel rather than a round. We claim them
        // here so the coverage audit sees them as known on Android even
        // though we currently round them down to the standard rounded
        // border-box (no spec-shape-aware renderer yet).
        if (type == "BorderBoundary" || type == "CornerShape") return true

        // Handle outline properties
        if (OutlineExtractor.isOutlineProperty(type)) return true

        // Handle individual side properties and radius
        return type.startsWith("Border") && (
            type.contains("Width") ||
            type.contains("Color") ||
            type.contains("Style") ||
            type.contains("Radius")
        ) && !type.contains("Image") && !type.contains("Spacing")
    }
}
