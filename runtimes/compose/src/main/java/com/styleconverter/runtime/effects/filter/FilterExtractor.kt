package com.styleconverter.runtime.effects.filter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.PropertyRegistry
import com.styleconverter.runtime.core.types.ValueExtractors
// filter-effects-1 §6.1 drop-shadow(): "the missing used color is taken from
// the color property" — resolved by the shared effects ink helper (retro R6).
import com.styleconverter.runtime.effects.EffectsCurrentColorInk
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
        // same function-list IR shape so they share one extractor.
        //
        // `url(#id)` (filter-effects-1 §5 "Graphic filters: the filter
        // property": a <filter-value-list> may name an SVG filter element
        // instead of one of the §6.1 filter functions) is DROPPED HERE,
        // not in the applier — retro P2e, finding A6#15: this line said "see
        // FilterApplier TODO" and FilterApplier.kt has neither a TODO nor any
        // url handling, so the pointer led nowhere. The converter emits the
        // reference as an OBJECT (`{"url":"#id"}` — FilterPropertyParser →
        // FilterProperty.FilterValue.UrlReference, verified by converting
        // `filter: url(#svgblur)`), while every function list is an ARRAY, so
        // `extractFilters`'s `(data as? JsonArray) ?: return emptyList()`
        // discards it before `extractFilterFunction` is ever reached. Compose
        // has no SVG filter graph to point at, so dropping is the only honest
        // answer; what is missing is the breadcrumb, since an SVG filter
        // reference and `filter: none` currently render identically with no
        // PropertyTracker note. MEASURED cost today: zero — all 73 Filter /
        // BackdropFilter payloads across the 1435 wave49-final per-test IR
        // documents are function-list arrays, none is a url reference.
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
     * @param wptCaptureMode the StyleApplier-threaded WPT flag for the
     *   drop-shadow `currentcolor` bottom-out (EffectsCurrentColorInk);
     *   default false keeps every dark-stage caller on the historical #eee.
     * @return FilterConfig with extracted filters and backdrop filters.
     */
    fun extractFilterConfig(
        properties: List<Pair<String, JsonElement?>>,
        wptCaptureMode: Boolean = false,
    ): FilterConfig {
        var filters: List<FilterFunction> = emptyList()
        var backdropFilters: List<FilterFunction> = emptyList()
        // The element's resolved `color` — drop-shadow()'s default colour
        // (retro R6, A7#1). Resolved once per element, lazily on first use.
        val currentColorInk by lazy { EffectsCurrentColorInk.resolve(properties, wptCaptureMode) }

        properties.forEach { (type, data) ->
            when (type) {
                "Filter" -> filters = extractFilters(data) { currentColorInk }
                "BackdropFilter" -> backdropFilters = extractFilters(data) { currentColorInk }
            }
        }

        return FilterConfig(filters, backdropFilters)
    }

    /**
     * Extract a list of filter functions from a JSON array.
     *
     * @param data The JSON data (should be a JsonArray of filter objects)
     * @param currentColorInk supplier of the element's resolved `color`, the
     *   drop-shadow() default (only evaluated when a drop-shadow needs it)
     * @return List of parsed FilterFunction values
     */
    private fun extractFilters(data: JsonElement?, currentColorInk: () -> Color): List<FilterFunction> {
        val array = (data as? JsonArray) ?: return emptyList()

        return array.mapNotNull { element ->
            val obj = (element as? JsonObject) ?: return@mapNotNull null
            extractFilterFunction(obj, currentColorInk)
        }
    }

    /**
     * Extract a single filter function from a JSON object.
     *
     * @param obj The filter function JSON object
     * @param currentColorInk supplier of the element's resolved `color`
     * @return Parsed FilterFunction or null if unrecognized/invalid
     */
    private fun extractFilterFunction(obj: JsonObject, currentColorInk: () -> Color): FilterFunction? {
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
                // Key drift fix: the IR serializer writes the angle under
                // "a" ({"fn":"hue-rotate","a":{"deg":90}} — see
                // FilterPropertyParser/serializer), but this reader only
                // looked for a legacy "angle" key, so EVERY hue-rotate
                // silently extracted as 0deg and the filter was an
                // identity on Android (filter-functions 013/014: web
                // rotated #e67e22 → green, Android stayed orange; 28-29%
                // mismatched pixels, Lab ΔE p95 ≈ 52). "angle" is kept as
                // a fallback for older snapshots.
                val angle = (obj["a"] ?: obj["angle"])
                    ?.let { ValueExtractors.extractDegrees(it) } ?: 0f
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
                // filter-effects-1 §6.1 drop-shadow(): "the missing used
                // color is taken from the color property", and an explicit
                // `currentColor` resolves to the same (css-color-4 §6.4).
                // extractColor returns null for BOTH (no srgb block on the
                // wire — the css-color/currentcolor-003 carrier is
                // `c: {"original": "currentColor"}`), so the fallback is the
                // element's resolved `color` — the SAME ink rule the Swift
                // twin adopts (retro R4/R6, A7#1), replacing the opaque
                // black that painted a solid red-on-red block there while
                // iOS painted 30 %-black copies and the ref paints copies in
                // the text colour.
                val color = obj["c"]?.let { ValueExtractors.extractColor(it) }
                    ?: obj["color"]?.let { ValueExtractors.extractColor(it) }
                    ?: currentColorInk()
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
