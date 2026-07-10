package com.styleconverter.runtime.borders.radius

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.PropertyRegistry
import com.styleconverter.runtime.core.types.ValueExtractors
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
 *                                                  carried as a FRACTION and
 *                                                  resolved by the applier's
 *                                                  Shape at paint time.
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
     * One axis of one corner: either an absolute length resolved by the
     * parser, or a percentage carried as a 0..1 fraction of the border
     * box's corresponding dimension (css-backgrounds-3 §4.4 — horizontal
     * axis → box width, vertical axis → box height) resolved at paint time.
     */
    private sealed interface Axis {
        data class Fixed(val dp: Dp) : Axis
        data class Fraction(val f: Float) : Axis
    }

    /**
     * Extract border radius configuration from a list of property pairs.
     *
     * @param properties List of (IR-type, IR-data) pairs.
     * @param componentWidth Unused for resolution (percentages now resolve
     *        at paint time); kept for call-site compatibility.
     * @param componentHeight Unused — see above.
     * @return Populated BorderRadiusConfig with (x, y) per corner.
     */
    fun extractRadiusConfig(
        properties: List<Pair<String, JsonElement?>>,
        componentWidth: Dp? = null,
        componentHeight: Dp? = null
    ): BorderRadiusConfig {
        // Each corner is an (x, y) Axis pair; start at zero and overwrite as
        // we encounter matching properties in the IR stream.
        val zero: Pair<Axis, Axis> = Axis.Fixed(0.dp) to Axis.Fixed(0.dp)
        var topStart = zero
        var topEnd = zero
        var bottomEnd = zero
        var bottomStart = zero

        properties.forEach { (type, data) ->
            // Extract x/y pair for this corner; skip if neither axis parses.
            val pair = extractRadiusPair(data) ?: return@forEach
            when (type) {
                // Physical and logical names fold into the same start/end
                // slot — the downstream Shape honors layoutDirection.
                "BorderTopLeftRadius", "BorderStartStartRadius" -> topStart = pair
                "BorderTopRightRadius", "BorderStartEndRadius" -> topEnd = pair
                "BorderBottomRightRadius", "BorderEndEndRadius" -> bottomEnd = pair
                "BorderBottomLeftRadius", "BorderEndStartRadius" -> bottomStart = pair
            }
        }

        // Split each Axis pair into the config's fixed-Dp channel and the
        // nullable fraction channel (fraction wins in the applier).
        fun dpOf(a: Axis): Dp = (a as? Axis.Fixed)?.dp ?: 0.dp
        fun frOf(a: Axis): Float? = (a as? Axis.Fraction)?.f
        return BorderRadiusConfig(
            topStart = dpOf(topStart.first) to dpOf(topStart.second),
            topEnd = dpOf(topEnd.first) to dpOf(topEnd.second),
            bottomEnd = dpOf(bottomEnd.first) to dpOf(bottomEnd.second),
            bottomStart = dpOf(bottomStart.first) to dpOf(bottomStart.second),
            topStartFraction = frOf(topStart.first) to frOf(topStart.second),
            topEndFraction = frOf(topEnd.first) to frOf(topEnd.second),
            bottomEndFraction = frOf(bottomEnd.first) to frOf(bottomEnd.second),
            bottomStartFraction = frOf(bottomStart.first) to frOf(bottomStart.second)
        )
    }

    /**
     * Extract an (x-radius, y-radius) Axis pair for one corner.
     *
     * Handles the three IR shapes the CSS parser produces:
     *   1. `{horizontal, vertical}` — explicit elliptical pair
     *   2. `{px: N}`                 — circular radius (x == y)
     *   3. `{original: {v, u=PERCENT}}` — percentage (kept as fraction)
     *
     * For the single-value forms, a percentage becomes the SAME fraction on
     * both axes — which per §4.4 still yields different pixel radii when the
     * box isn't square (x resolves against width, y against height): CSS
     * `border-radius: 50%` is a full ellipse, not a pill. The previous
     * implementation resolved single percentages against min(width, height)
     * at extract time, which was both spec-wrong and silently dropped the
     * radius whenever the IR carried no explicit Width/Height.
     *
     * Returns null only when the data is malformed.
     */
    private fun extractRadiusPair(data: JsonElement?): Pair<Axis, Axis>? {
        if (data == null) return null

        // Case 1: explicit elliptical pair. The CSS "40px 20px" shorthand
        // lands here as {horizontal: {px: 40}, vertical: {px: 20}}. Each
        // axis may independently be a length or a percentage ("40px 50%").
        if (data is JsonObject) {
            val horiz = data["horizontal"]
            val vert = data["vertical"]
            if (horiz != null && vert != null) {
                val x = readAxis(horiz) ?: return null
                val y = readAxis(vert) ?: return null
                return x to y
            }
        }

        // Case 2 & 3: single value (circular / same-fraction both axes).
        val single = readAxis(data) ?: return null
        return single to single
    }

    /**
     * Read a single IRLength into an [Axis]: `{"px": N}` (or bare number)
     * becomes Fixed; `{original: {v, u: "PERCENT"}}` becomes Fraction(v/100).
     * Other relative units (em/vw/…) are unresolvable here and return null
     * so the caller treats the corner as square — same policy as before.
     */
    private fun readAxis(el: JsonElement): Axis? {
        // Fast path: straight pixel value (`{"px": N}` or bare number).
        ValueExtractors.extractDp(el)?.let { return Axis.Fixed(it) }

        // Percentage path: `{original: {v, u: "PERCENT"}}` per IR contract.
        if (el is JsonObject) {
            val original = el["original"]
            if (original is JsonObject) {
                val v = original["v"]?.jsonPrimitive?.doubleOrNull
                val u = original["u"]?.jsonPrimitive?.content?.uppercase()
                if (v != null && u == "PERCENT") {
                    return Axis.Fraction((v / 100.0).toFloat())
                }
            }
        }
        return null
    }
}
