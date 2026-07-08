package com.styleconverter.test.style.borders.radius

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.test.style.PropertyRegistry
import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Extracts border-radius configurations from IR properties.
 *
 * The IR schema for a single corner comes from the CSS parser at
 * `src/main/kotlin/app/parsing/css/properties/longhands/borders/radius/`,
 * which emits one of:
 *   - `{"px": N}`                               — simple circular radius
 *   - `{"horizontal": IRLength, "vertical": IRLength}` — elliptical pair
 *                                                    (CSS "30px 15px")
 *   - `{"original": {"v": N, "u": "PERCENT"}}`  — percentage radius,
 *                                                  resolved against component
 *                                                  dimensions per CSS spec.
 *
 * Handles both physical (top-left) and logical (start-start) corner names;
 * they fold into the same start/end corner slot since Compose's RTL-aware
 * corner shape handles the physical mirroring at paint time.
 */
object BorderRadiusExtractor {

    init {
        // Register every border-radius IR property so the legacy dispatch
        // knows these are owned by the migrated extractor. Listed in IR
        // camelcase form — matches what the CSS parser emits.
        PropertyRegistry.migrated(
            "BorderTopLeftRadius", "BorderTopRightRadius",
            "BorderBottomRightRadius", "BorderBottomLeftRadius",
            "BorderStartStartRadius", "BorderStartEndRadius",
            "BorderEndEndRadius", "BorderEndStartRadius",
            owner = "borders/radius"
        )
    }

    /**
     * Extract border radius configuration from a list of property pairs.
     *
     * @param properties List of (IR-type, IR-data) pairs.
     * @param componentWidth Used to resolve percentage x-axis radii.
     * @param componentHeight Used to resolve percentage y-axis radii.
     * @return Populated BorderRadiusConfig with (x, y) per corner.
     */
    fun extractRadiusConfig(
        properties: List<Pair<String, JsonElement?>>,
        componentWidth: Dp? = null,
        componentHeight: Dp? = null
    ): BorderRadiusConfig {
        // Each corner is an (x, y) pair; start at zero and overwrite as we
        // encounter matching properties in the IR stream.
        var topStart = 0.dp to 0.dp
        var topEnd = 0.dp to 0.dp
        var bottomEnd = 0.dp to 0.dp
        var bottomStart = 0.dp to 0.dp

        properties.forEach { (type, data) ->
            // Extract x/y pair for this corner; skip if neither axis parses.
            val pair = extractRadiusPair(data, componentWidth, componentHeight)
                ?: return@forEach
            when (type) {
                // Physical and logical names fold into the same start/end
                // slot — the downstream Shape honors layoutDirection.
                "BorderTopLeftRadius", "BorderStartStartRadius" -> topStart = pair
                "BorderTopRightRadius", "BorderStartEndRadius" -> topEnd = pair
                "BorderBottomRightRadius", "BorderEndEndRadius" -> bottomEnd = pair
                "BorderBottomLeftRadius", "BorderEndStartRadius" -> bottomStart = pair
            }
        }

        return BorderRadiusConfig(topStart, topEnd, bottomEnd, bottomStart)
    }

    /**
     * Extract an (x-radius, y-radius) pair for one corner.
     *
     * Handles the three IR shapes the CSS parser produces:
     *   1. `{horizontal, vertical}` — explicit elliptical pair
     *   2. `{px: N}`                 — circular radius (x == y)
     *   3. `{original: {v, u=PERCENT}}` — percentage (resolved below)
     *
     * Returns null only when the data is malformed or an unresolvable
     * percentage (missing both width and height).
     */
    private fun extractRadiusPair(
        data: JsonElement?,
        componentWidth: Dp?,
        componentHeight: Dp?
    ): Pair<Dp, Dp>? {
        if (data == null) return null

        // Case 1: explicit elliptical pair. The CSS "40px 20px" shorthand
        // lands here as {horizontal: {px: 40}, vertical: {px: 20}}.
        if (data is JsonObject) {
            val horiz = data["horizontal"]
            val vert = data["vertical"]
            if (horiz != null && vert != null) {
                // Resolve each axis independently — x may be px while y is %,
                // e.g. "40px 50%" which is valid CSS.
                val x = resolveLengthOrPercent(horiz, componentWidth) ?: return null
                val y = resolveLengthOrPercent(vert, componentHeight) ?: return null
                return x to y
            }
        }

        // Case 2 & 3: single value (circular). Try absolute length first,
        // then fall back to percentage resolution.
        val single = resolveLengthOrPercent(
            data,
            // Per CSS spec, single-value percentages on border-radius resolve
            // against the smaller dimension (makes "50%" on a non-square box
            // produce a pill rather than an oval). We approximate by using
            // min when both dimensions are known.
            when {
                componentWidth != null && componentHeight != null ->
                    minOf(componentWidth, componentHeight)
                componentWidth != null -> componentWidth
                else -> componentHeight
            }
        ) ?: return null
        return single to single
    }

    /**
     * Resolve a single IRLength to Dp, handling both absolute and percentage
     * formats. `reference` is the dimension a percentage resolves against.
     */
    private fun resolveLengthOrPercent(el: JsonElement, reference: Dp?): Dp? {
        // Fast path: straight pixel value (`{"px": N}` or bare number).
        ValueExtractors.extractDp(el)?.let { return it }

        // Percentage path: `{original: {v, u: "PERCENT"}}` per IR contract.
        if (el is JsonObject) {
            val original = el["original"]
            if (original is JsonObject) {
                val v = original["v"]?.jsonPrimitive?.doubleOrNull
                val u = original["v"]?.let { original["u"] }?.jsonPrimitive?.content?.uppercase()
                if (v != null && u == "PERCENT") {
                    // Cannot resolve percentage without a reference dimension —
                    // return null so the caller treats this corner as zero.
                    val ref = reference ?: return null
                    return ref * (v.toFloat() / 100f)
                }
            }
        }
        return null
    }
}
