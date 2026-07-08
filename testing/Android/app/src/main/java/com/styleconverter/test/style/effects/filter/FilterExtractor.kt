package com.styleconverter.test.style.effects.filter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.styleconverter.test.style.PropertyRegistry
import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.*

/**
 * Extracts filter configuration from IR property JSON data.
 *
 * Handles the following IR property types:
 * - `Filter` - CSS `filter` property
 * - `BackdropFilter` - CSS `backdrop-filter` property
 *
 * ## IR Format
 * Filters are represented as arrays of filter function objects:
 * ```json
 * {
 *   "type": "Filter",
 *   "data": [
 *     { "fn": "blur", "r": { "px": 5.0 } },
 *     { "fn": "brightness", "v": 120.0 }
 *   ]
 * }
 * ```
 */
object FilterExtractor {

    init {
        // Phase 8 registration. `filter` and `backdrop-filter` both consume the
        // same function-list IR shape so they share one extractor. `url(#id)`
        // references are parsed but no-op on Compose (see FilterApplier TODO).
        PropertyRegistry.migrated(
            "Filter",
            "BackdropFilter",
            owner = "effects/filter"
        )
    }

    /**
     * Extract filter configuration from a list of property type/data pairs.
     *
     * @param properties List of pairs where first is the property type
     *                   and second is the JSON data for that property.
     * @return FilterConfig with extracted filters and backdrop filters.
     */
    fun extractFilterConfig(properties: List<Pair<String, JsonElement?>>): FilterConfig {
        var filters: List<FilterFunction> = emptyList()
        var backdropFilters: List<FilterFunction> = emptyList()

        properties.forEach { (type, data) ->
            when (type) {
                "Filter" -> filters = extractFilters(data)
                "BackdropFilter" -> backdropFilters = extractFilters(data)
            }
        }

        return FilterConfig(filters, backdropFilters)
    }

    /**
     * Extract a list of filter functions from a JSON array.
     *
     * @param data The JSON data (should be a JsonArray of filter objects)
     * @return List of parsed FilterFunction values
     */
    private fun extractFilters(data: JsonElement?): List<FilterFunction> {
        val array = (data as? JsonArray) ?: return emptyList()

        return array.mapNotNull { element ->
            val obj = (element as? JsonObject) ?: return@mapNotNull null
            extractFilterFunction(obj)
        }
    }

    /**
     * Extract a single filter function from a JSON object.
     *
     * @param obj The filter function JSON object
     * @return Parsed FilterFunction or null if unrecognized/invalid
     */
    private fun extractFilterFunction(obj: JsonObject): FilterFunction? {
        val fn = obj["fn"]?.jsonPrimitive?.contentOrNull ?: return null

        return when (fn) {
            "blur" -> {
                val radius = obj["r"]?.let { ValueExtractors.extractDp(it) } ?: 0.dp
                FilterFunction.Blur(radius)
            }

            "brightness" -> {
                // IR stores as percentage (100 = normal), convert to multiplier (1.0 = normal)
                val value = extractPercentageValue(obj) ?: 100f
                FilterFunction.Brightness(value / 100f)
            }

            "contrast" -> {
                val value = extractPercentageValue(obj) ?: 100f
                FilterFunction.Contrast(value / 100f)
            }

            "grayscale" -> {
                // 0-100%, convert to 0-1
                val value = extractPercentageValue(obj) ?: 0f
                FilterFunction.Grayscale((value / 100f).coerceIn(0f, 1f))
            }

            "hue-rotate" -> {
                val angle = obj["angle"]?.let { ValueExtractors.extractDegrees(it) } ?: 0f
                FilterFunction.HueRotate(angle)
            }

            "invert" -> {
                val value = extractPercentageValue(obj) ?: 0f
                FilterFunction.Invert((value / 100f).coerceIn(0f, 1f))
            }

            "opacity" -> {
                val value = extractPercentageValue(obj) ?: 100f
                FilterFunction.Opacity((value / 100f).coerceIn(0f, 1f))
            }

            "saturate" -> {
                val value = extractPercentageValue(obj) ?: 100f
                FilterFunction.Saturate(value / 100f)
            }

            "sepia" -> {
                val value = extractPercentageValue(obj) ?: 0f
                FilterFunction.Sepia((value / 100f).coerceIn(0f, 1f))
            }

            "drop-shadow" -> {
                val x = obj["x"]?.let { ValueExtractors.extractDp(it) } ?: 0.dp
                val y = obj["y"]?.let { ValueExtractors.extractDp(it) } ?: 0.dp
                val blur = obj["blur"]?.let { ValueExtractors.extractDp(it) }
                    ?: obj["r"]?.let { ValueExtractors.extractDp(it) }
                    ?: 0.dp
                val color = obj["c"]?.let { ValueExtractors.extractColor(it) }
                    ?: obj["color"]?.let { ValueExtractors.extractColor(it) }
                    ?: Color.Black
                FilterFunction.DropShadow(x, y, blur, color)
            }

            "none" -> FilterFunction.None

            else -> null
        }
    }

    /**
     * Extract a percentage value from various possible field names.
     * Checks "v", "amount", and "a" fields.
     */
    private fun extractPercentageValue(obj: JsonObject): Float? {
        return obj["v"]?.jsonPrimitive?.floatOrNull
            ?: obj["amount"]?.jsonPrimitive?.floatOrNull
            ?: obj["a"]?.jsonPrimitive?.floatOrNull
    }

    /**
     * Check if a property type is a filter-related property.
     */
    fun isFilterProperty(type: String): Boolean {
        return type == "Filter" || type == "BackdropFilter"
    }
}
