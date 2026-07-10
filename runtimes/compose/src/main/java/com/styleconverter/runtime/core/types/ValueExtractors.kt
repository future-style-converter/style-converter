package com.styleconverter.runtime.core.types

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*

/**
 * Utility functions to extract typed values from IR JSON data.
 *
 * These extractors handle the normalized IR format where values have
 * both normalized (px, srgb) and original representations.
 */
object ValueExtractors {

    /**
     * Extract a Dp value from an IRLength JSON object.
     *
     * IRLength format: { "px": 16.0 } or { "value": 1.5, "unit": "em" }
     * Returns the pixel value as Dp, or null if not available.
     */
    fun extractDp(json: JsonElement?): Dp? {
        if (json == null) return null
        return when (json) {
            is JsonObject -> {
                json["px"]?.jsonPrimitive?.doubleOrNull?.dp
                    ?: run {
                        // Fall through to relative units when no `px` key:
                        // the IR carries `{ original: { v, u } }` for
                        // em/rem/ex/etc that the converter couldn't pre-
                        // resolve. We resolve against a 16-px font-size
                        // default — matches CSS body inherited default
                        // and Compose's default text size. Only em/rem
                        // are meaningful for gap/padding/margin in the
                        // current fixture set; other relative units
                        // (vw, ch, etc.) fall through to null.
                        val original = json["original"] as? JsonObject ?: return@run null
                        val v = original["v"]?.jsonPrimitive?.doubleOrNull ?: return@run null
                        when (original["u"]?.jsonPrimitive?.contentOrNull?.uppercase()) {
                            "EM", "REM" -> (v * 16).dp
                            else -> null
                        }
                    }
            }
            is JsonPrimitive -> {
                json.doubleOrNull?.dp
            }
            else -> null
        }
    }

    /**
     * Result type for length extraction that can be either Dp or percentage.
     */
    sealed interface LengthOrPercentage {
        data class Length(val dp: Dp) : LengthOrPercentage
        data class Percentage(val fraction: Float) : LengthOrPercentage  // 0.0-1.0
        data object Auto : LengthOrPercentage
    }

    /**
     * Extract a length value that may be either Dp or percentage.
     *
     * IRLength format:
     * - Absolute: { "px": 16.0 }
     * - Percentage: { "original": { "v": 50.0, "u": "PERCENT" } }
     * - Auto: { "keyword": "auto" } or just "auto"
     *
     * Returns LengthOrPercentage to let callers handle both cases appropriately.
     */
    fun extractLengthOrPercentage(json: JsonElement?): LengthOrPercentage? {
        if (json == null) return null
        return when (json) {
            is JsonObject -> {
                // Check for pixel value first (normalized)
                json["px"]?.jsonPrimitive?.doubleOrNull?.let {
                    return LengthOrPercentage.Length(it.dp)
                }

                // Check for auto keyword
                val keyword = json["keyword"]?.jsonPrimitive?.contentOrNull
                if (keyword?.equals("auto", ignoreCase = true) == true) {
                    return LengthOrPercentage.Auto
                }

                // Check for percentage in original format
                val original = json["original"] as? JsonObject
                if (original != null) {
                    val unit = original["u"]?.jsonPrimitive?.contentOrNull
                    val value = original["v"]?.jsonPrimitive?.floatOrNull
                    if (unit?.equals("PERCENT", ignoreCase = true) == true && value != null) {
                        return LengthOrPercentage.Percentage(value / 100f)  // Convert to 0-1 range
                    }
                }

                // Check for direct percentage notation
                val pct = json["percentage"]?.jsonPrimitive?.floatOrNull
                    ?: json["pct"]?.jsonPrimitive?.floatOrNull
                if (pct != null) {
                    return LengthOrPercentage.Percentage(pct / 100f)
                }

                null
            }
            is JsonPrimitive -> {
                // Could be a direct number (treat as px) or keyword
                json.doubleOrNull?.let { return LengthOrPercentage.Length(it.dp) }
                if (json.contentOrNull?.equals("auto", ignoreCase = true) == true) {
                    return LengthOrPercentage.Auto
                }
                null
            }
            else -> null
        }
    }

    /**
     * Extract a Color from an IRColor JSON object.
     *
     * IRColor format: { "srgb": { "r": 1.0, "g": 0.0, "b": 0.0, "a": 1.0 }, "original": "red" }
     * Returns the sRGB color, or null if not available (e.g., for var() or currentColor).
     */
    fun extractColor(json: JsonElement?): Color? {
        if (json == null) return null
        return when (json) {
            is JsonObject -> {
                val srgb = json["srgb"]?.jsonObject
                if (srgb != null) {
                    val r = srgb["r"]?.jsonPrimitive?.doubleOrNull ?: return null
                    val g = srgb["g"]?.jsonPrimitive?.doubleOrNull ?: return null
                    val b = srgb["b"]?.jsonPrimitive?.doubleOrNull ?: return null
                    val a = srgb["a"]?.jsonPrimitive?.doubleOrNull ?: 1.0
                    return Color(r.toFloat(), g.toFloat(), b.toFloat(), a.toFloat())
                }
                // No pre-resolved srgb: the parser leaves `color-mix()` as
                // original-only metadata ({"original": {"type":"color-mix",
                // "colorSpace":"srgb", "color1":"red", "percent1":50.0,
                // "color2":"blue"}}). The browser resolves this natively, so
                // web painted the mixed purple while Android fell back to
                // the grey placeholder (Borders_C09's 1px inset border:
                // web rgb(43,0,43) vs Android rgb(119,119,119)). Resolve
                // the simple srgb case here: css-color-5 §3 interpolates
                // the gamma-encoded sRGB components linearly (Chrome shows
                // color-mix(in srgb, red 50%, blue) = rgb(128,0,128)).
                val original = json["original"] as? JsonObject ?: return null
                if (original["type"]?.jsonPrimitive?.contentOrNull != "color-mix") return null
                // Only srgb interpolation is a plain component lerp; other
                // spaces (oklch, hsl…) need real color-space math — bail to
                // the caller's fallback rather than mixing in the wrong space.
                if (original["colorSpace"]?.jsonPrimitive?.contentOrNull != "srgb") return null
                val c1 = original["color1"]?.jsonPrimitive?.contentOrNull?.let(::parseCssColorLiteral) ?: return null
                val c2 = original["color2"]?.jsonPrimitive?.contentOrNull?.let(::parseCssColorLiteral) ?: return null
                // css-color-5 §3.1 percentage normalization: a missing
                // percent defaults to (100 − the other); both missing = 50/50.
                val p1raw = original["percent1"]?.jsonPrimitive?.doubleOrNull
                val p2raw = original["percent2"]?.jsonPrimitive?.doubleOrNull
                val p1 = (p1raw ?: p2raw?.let { 100.0 - it } ?: 50.0).toFloat() / 100f
                val p2 = (p2raw ?: (100.0 - (p1raw ?: 50.0))).toFloat() / 100f
                val total = (p1 + p2).takeIf { it > 0f } ?: return null
                val w1 = p1 / total
                val w2 = p2 / total
                Color(
                    red = c1.red * w1 + c2.red * w2,
                    green = c1.green * w1 + c2.green * w2,
                    blue = c1.blue * w1 + c2.blue * w2,
                    alpha = c1.alpha * w1 + c2.alpha * w2
                )
            }
            else -> null
        }
    }

    /**
     * Minimal CSS color literal parser for `color-mix()` endpoints, which
     * the IR carries as raw CSS strings. Handles #hex (3/4/6/8 digit) and
     * the CSS named colors that appear in fixtures. Anything else → null
     * (the caller treats the whole mix as unresolvable, same as var()).
     */
    internal fun parseCssColorLiteral(s: String): Color? {
        val t = s.trim().lowercase()
        if (t.startsWith("#")) {
            val h = t.drop(1)
            fun hx(c: Char) = Character.digit(c, 16).takeIf { it >= 0 }
            return when (h.length) {
                3, 4 -> {
                    val ch = h.map { hx(it) ?: return null }
                    Color(
                        red = ch[0] * 17 / 255f, green = ch[1] * 17 / 255f, blue = ch[2] * 17 / 255f,
                        alpha = if (h.length == 4) ch[3] * 17 / 255f else 1f
                    )
                }
                6, 8 -> {
                    val v = h.toLongOrNull(16) ?: return null
                    if (h.length == 6) Color(
                        ((v shr 16) and 0xFF) / 255f, ((v shr 8) and 0xFF) / 255f, (v and 0xFF) / 255f
                    ) else Color(
                        ((v shr 24) and 0xFF) / 255f, ((v shr 16) and 0xFF) / 255f,
                        ((v shr 8) and 0xFF) / 255f, (v and 0xFF) / 255f
                    )
                }
                else -> null
            }
        }
        // CSS Level 1 named colors + the extended names our fixtures use.
        return when (t) {
            "black" -> Color(0f, 0f, 0f)
            "white" -> Color(1f, 1f, 1f)
            "red" -> Color(1f, 0f, 0f)
            "green" -> Color(0f, 128 / 255f, 0f)
            "blue" -> Color(0f, 0f, 1f)
            "yellow" -> Color(1f, 1f, 0f)
            "cyan", "aqua" -> Color(0f, 1f, 1f)
            "magenta", "fuchsia" -> Color(1f, 0f, 1f)
            "gray", "grey" -> Color(128 / 255f, 128 / 255f, 128 / 255f)
            "silver" -> Color(192 / 255f, 192 / 255f, 192 / 255f)
            "maroon" -> Color(128 / 255f, 0f, 0f)
            "olive" -> Color(128 / 255f, 128 / 255f, 0f)
            "lime" -> Color(0f, 1f, 0f)
            "teal" -> Color(0f, 128 / 255f, 128 / 255f)
            "navy" -> Color(0f, 0f, 128 / 255f)
            "purple" -> Color(128 / 255f, 0f, 128 / 255f)
            "orange" -> Color(1f, 165 / 255f, 0f)
            "crimson" -> Color(220 / 255f, 20 / 255f, 60 / 255f)
            "transparent" -> Color(0f, 0f, 0f, 0f)
            else -> null
        }
    }

    /**
     * Extract a Float value (for opacity, flex-grow, scale, etc.).
     *
     * Safety contract: NEVER throw on schema mismatch. The previous version
     * used `.jsonPrimitive` — a throwing cast — on `json["value"]`, which
     * crashed the whole app when the IR wrapped its value in a nested object
     * (e.g. `FlexGrowProperty` serializes to
     * `{"value":{"type":"Number","value":2.0}, "normalizedValue":2.0}`:
     * `json["value"]` is itself a JsonObject, not a primitive). We now use
     * safe casts + a depth-1 recursion for wrapped objects, and also honour
     * the `normalizedValue` field that several IR properties expose as the
     * pre-computed cross-platform numeric. See yesterday's crash in
     * ComponentRenderer.extractFlexGrow (ComponentRenderer.kt:708).
     */
    fun extractFloat(json: JsonElement?): Float? {
        if (json == null) return null
        return when (json) {
            is JsonPrimitive -> json.floatOrNull
            is JsonObject -> {
                // Prefer `normalizedValue` — IR's canonical flat Double.
                // Then the common wrapper keys. Each uses a safe cast so
                // a nested-object miss falls through instead of throwing.
                (json["normalizedValue"] as? JsonPrimitive)?.floatOrNull
                    ?: (json["alpha"] as? JsonPrimitive)?.floatOrNull
                    ?: (json["value"] as? JsonPrimitive)?.floatOrNull
                    ?: (json["numeric"] as? JsonPrimitive)?.floatOrNull
                    ?: (json["number"] as? JsonPrimitive)?.floatOrNull
                    // Last resort: if `value` is itself an object (e.g.
                    // FlexGrow's `{value: {type: "Number", value: 2.0}}`),
                    // recurse once so the inner primitive is reachable.
                    ?: extractFloat(json["value"])
            }
            else -> null
        }
    }

    /**
     * Extract an Int value (for z-index, column-count, etc.).
     *
     * Same safety contract as `extractFloat` — safe casts only, with a
     * single depth recursion for wrapped values.
     */
    fun extractInt(json: JsonElement?): Int? {
        if (json == null) return null
        return when (json) {
            is JsonPrimitive -> json.intOrNull
            is JsonObject -> {
                (json["normalizedValue"] as? JsonPrimitive)?.intOrNull
                    ?: (json["value"] as? JsonPrimitive)?.intOrNull
                    ?: (json["numeric"] as? JsonPrimitive)?.intOrNull
                    ?: (json["number"] as? JsonPrimitive)?.intOrNull
                    ?: extractInt(json["value"])
            }
            else -> null
        }
    }

    /**
     * Extract a String keyword value.
     */
    fun extractKeyword(json: JsonElement?): String? {
        if (json == null) return null
        return when (json) {
            is JsonPrimitive -> json.contentOrNull
            is JsonObject -> {
                // Use safe casts: `json["value"]` is sometimes itself a
                // JsonObject (e.g. ImageOrientation's
                // {value:{deg:180}}). Bare `.jsonPrimitive` would throw
                // and the StyleApplier-wrapped catch would silently drop
                // every other property on the element.
                (json["keyword"] as? JsonPrimitive)?.contentOrNull
                    ?: (json["value"] as? JsonPrimitive)?.contentOrNull
            }
            else -> null
        }
    }

    /**
     * Extract a keyword from a sealed interface object with type/value structure.
     *
     * Handles IR formats like:
     * - { "type": "start" } (serialName as type)
     * - { "keyword": "START" } (keyword field)
     * - { "value": "START" } (value field)
     */
    fun extractKeywordFromObject(json: JsonElement?): String? {
        if (json == null) return null
        return when (json) {
            is JsonPrimitive -> json.contentOrNull
            is JsonObject -> {
                // Handle sealed interface serialization with type field
                json["type"]?.jsonPrimitive?.contentOrNull
                    ?: json["keyword"]?.jsonPrimitive?.contentOrNull
                    ?: json["value"]?.jsonPrimitive?.contentOrNull
            }
            else -> null
        }
    }

    /**
     * Extract degrees from an IRAngle JSON object.
     *
     * IRAngleSerializer (src/main/kotlin/app/irmodels/ValueTypes.kt
     * line 949+) emits the normalised degrees under the key "deg" — see
     * the serializer's `put("deg", value.degrees)`. So the canonical IR
     * shape coming from the converter is `{"deg": 45.0, ...}`. This
     * extractor previously read only `json["degrees"]`, which never
     * matches IR-as-emitted: every rotate(45deg) silently extracted as
     * null → applier defaulted to 0f → boxes rendered un-rotated. The
     * real-world cost was severe — Transform_Rotate / Transform_Origin /
     * Transform_Combined all rendered axis-aligned on Android while iOS
     * and web rotated correctly (SSIM 0.75 / 0.75 / 0.72 respectively).
     *
     * We try `deg` FIRST (canonical IR), fall back to `degrees` (legacy
     * shape some unit tests still emit), and finally accept a bare
     * JsonPrimitive number for shorthand degree values.
     */
    fun extractDegrees(json: JsonElement?): Float? {
        if (json == null) return null
        return when (json) {
            is JsonObject -> {
                json["deg"]?.jsonPrimitive?.floatOrNull
                    ?: json["degrees"]?.jsonPrimitive?.floatOrNull
            }
            is JsonPrimitive -> json.floatOrNull
            else -> null
        }
    }

    /**
     * Extract milliseconds from an IRTime JSON object.
     */
    fun extractMillis(json: JsonElement?): Int? {
        if (json == null) return null
        return when (json) {
            is JsonObject -> {
                json["ms"]?.jsonPrimitive?.intOrNull
                    ?: json["milliseconds"]?.jsonPrimitive?.intOrNull
            }
            is JsonPrimitive -> json.intOrNull
            else -> null
        }
    }

    /**
     * Extract a percentage value (0-100).
     * Handles both raw numbers and percentage objects.
     */
    fun extractPercentage(json: JsonElement?): Float? {
        if (json == null) return null
        return when (json) {
            is JsonPrimitive -> json.floatOrNull
            is JsonObject -> {
                json["percentage"]?.jsonPrimitive?.floatOrNull
                    ?: json["value"]?.jsonPrimitive?.floatOrNull
                    ?: json["v"]?.jsonPrimitive?.floatOrNull
            }
            else -> null
        }
    }

    /**
     * Extract timing function as cubic bezier points [x1, y1, x2, y2].
     * Handles keywords (ease, linear, etc.) and cubic-bezier() functions.
     */
    fun extractTimingFunction(json: JsonElement?): FloatArray? {
        if (json == null) return null
        return when (json) {
            is JsonObject -> {
                // Check for cubic bezier points in "cb" array
                val cb = json["cb"] as? JsonArray
                if (cb != null && cb.size >= 4) {
                    return floatArrayOf(
                        cb[0].jsonPrimitive.floatOrNull ?: 0f,
                        cb[1].jsonPrimitive.floatOrNull ?: 0f,
                        cb[2].jsonPrimitive.floatOrNull ?: 0f,
                        cb[3].jsonPrimitive.floatOrNull ?: 0f
                    )
                }
                // Check for original keyword
                val original = json["original"]?.jsonPrimitive?.contentOrNull
                keywordToTimingFunction(original)
            }
            is JsonPrimitive -> keywordToTimingFunction(json.contentOrNull)
            else -> null
        }
    }

    /**
     * Convert timing function keyword to cubic bezier points.
     */
    private fun keywordToTimingFunction(keyword: String?): FloatArray? {
        return when (keyword?.lowercase()) {
            "linear" -> floatArrayOf(0f, 0f, 1f, 1f)
            "ease" -> floatArrayOf(0.25f, 0.1f, 0.25f, 1f)
            "ease-in" -> floatArrayOf(0.42f, 0f, 1f, 1f)
            "ease-out" -> floatArrayOf(0f, 0f, 0.58f, 1f)
            "ease-in-out" -> floatArrayOf(0.42f, 0f, 0.58f, 1f)
            else -> null
        }
    }

    /**
     * Extract font weight as numeric value (100-900).
     * Handles numeric values and keywords (normal, bold, lighter, bolder).
     */
    fun extractFontWeight(json: JsonElement?): Int? {
        if (json == null) return null
        return when (json) {
            is JsonPrimitive -> {
                json.intOrNull ?: when (json.contentOrNull?.lowercase()) {
                    "normal" -> 400
                    "bold" -> 700
                    "lighter", "bolder" -> null // Context-dependent
                    else -> null
                }
            }
            is JsonObject -> {
                json["weight"]?.jsonPrimitive?.intOrNull
                    ?: json["numericValue"]?.jsonPrimitive?.intOrNull
                    ?: json["value"]?.jsonPrimitive?.intOrNull
            }
            else -> null
        }
    }

    /**
     * Extract border width with keyword support (thin, medium, thick).
     * Returns Dp value.
     */
    fun extractBorderWidth(json: JsonElement?): Dp? {
        if (json == null) return null
        return when (json) {
            is JsonPrimitive -> {
                json.floatOrNull?.dp ?: when (json.contentOrNull?.lowercase()) {
                    "thin" -> 1.dp
                    "medium" -> 3.dp
                    "thick" -> 5.dp
                    else -> null
                }
            }
            is JsonObject -> {
                // Check for normalized pixels
                json["px"]?.jsonPrimitive?.floatOrNull?.dp
                    ?: json["pixels"]?.jsonPrimitive?.floatOrNull?.dp
                    // Typed keyword envelope — the parser emits width
                    // keywords as {"type":"keyword","value":"THICK"}
                    // (`outline-width: thick` in Borders_C14). This shape
                    // was previously unhandled, so the width fell through
                    // to null → 0 → hasOutline false → the crimson ridge
                    // outline never painted at all (Android-web 0.8025).
                    // css-backgrounds-3 §4.3: thin=1px, medium=3px, thick=5px.
                    ?: (json.takeIf { it["type"]?.jsonPrimitive?.contentOrNull == "keyword" })
                        ?.let { kw ->
                            when (kw["value"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
                                "thin" -> 1.dp
                                "medium" -> 3.dp
                                "thick" -> 5.dp
                                else -> null
                            }
                        }
                    // Check for original with keyword
                    ?: (json["original"] as? JsonObject)?.let { original ->
                        when (original["keyword"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
                            "thin" -> 1.dp
                            "medium" -> 3.dp
                            "thick" -> 5.dp
                            else -> null
                        }
                    }
            }
            else -> null
        }
    }

    /**
     * Line styles for borders and outlines.
     */
    enum class LineStyle {
        SOLID, DASHED, DOTTED, DOUBLE, GROOVE, RIDGE, INSET, OUTSET, NONE, HIDDEN
    }

    /**
     * Extract line style (solid, dashed, dotted, etc.).
     */
    fun extractLineStyle(json: JsonElement?): LineStyle? {
        if (json == null) return null
        val keyword = extractKeyword(json)?.uppercase() ?: return null
        return try {
            LineStyle.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Extract aspect ratio as a float (width / height).
     * Handles "auto", "1/1", "16/9" formats.
     */
    fun extractAspectRatio(json: JsonElement?): Float? {
        if (json == null) return null
        return when (json) {
            is JsonPrimitive -> {
                json.floatOrNull ?: json.contentOrNull?.let { parseAspectRatioString(it) }
            }
            is JsonObject -> {
                val width = json["width"]?.jsonPrimitive?.floatOrNull
                val height = json["height"]?.jsonPrimitive?.floatOrNull
                if (width != null && height != null && height != 0f) {
                    width / height
                } else {
                    json["ratio"]?.jsonPrimitive?.floatOrNull
                }
            }
            else -> null
        }
    }

    /**
     * Parse aspect ratio string like "16/9" or "1/1".
     */
    private fun parseAspectRatioString(str: String): Float? {
        if (str.equals("auto", ignoreCase = true)) return null
        val parts = str.split("/")
        if (parts.size == 2) {
            val width = parts[0].trim().toFloatOrNull()
            val height = parts[1].trim().toFloatOrNull()
            if (width != null && height != null && height != 0f) {
                return width / height
            }
        }
        return str.toFloatOrNull()
    }

    /**
     * Extract box shadow data including offset, blur, spread, and color.
     */
    data class ShadowData(
        val offsetX: Dp,
        val offsetY: Dp,
        val blurRadius: Dp,
        val spreadRadius: Dp,
        val color: Color,
        val inset: Boolean
    )

    /**
     * Extract shadow from JSON array (box-shadow can have multiple shadows).
     */
    fun extractShadows(json: JsonElement?): List<ShadowData> {
        if (json !is JsonArray) return emptyList()

        return json.mapNotNull { shadowElement ->
            val shadow = (shadowElement as? JsonObject) ?: return@mapNotNull null

            val offsetX = shadow["x"]?.let { extractDp(it) } ?: 0.dp
            val offsetY = shadow["y"]?.let { extractDp(it) } ?: 0.dp
            val blurRadius = shadow["blur"]?.let { extractDp(it) } ?: 0.dp
            val spreadRadius = shadow["spread"]?.let { extractDp(it) } ?: 0.dp
            val color = shadow["c"]?.let { extractColor(it) } ?: Color.Black
            val inset = shadow["inset"]?.jsonPrimitive?.booleanOrNull ?: false

            ShadowData(
                offsetX = offsetX,
                offsetY = offsetY,
                blurRadius = blurRadius,
                spreadRadius = spreadRadius,
                color = color,
                inset = inset
            )
        }
    }

    /**
     * Extract transform origin as a pair of percentages (0-100).
     * Returns Pair(x%, y%).
     */
    fun extractTransformOrigin(json: JsonElement?): Pair<Float, Float>? {
        if (json == null) return null
        return when (json) {
            is JsonObject -> {
                val x = json["x"]?.jsonPrimitive?.floatOrNull ?: 50f
                val y = json["y"]?.jsonPrimitive?.floatOrNull ?: 50f
                Pair(x, y)
            }
            is JsonPrimitive -> {
                when (json.contentOrNull?.lowercase()) {
                    "center" -> Pair(50f, 50f)
                    "top" -> Pair(50f, 0f)
                    "bottom" -> Pair(50f, 100f)
                    "left" -> Pair(0f, 50f)
                    "right" -> Pair(100f, 50f)
                    "top left" -> Pair(0f, 0f)
                    "top right" -> Pair(100f, 0f)
                    "bottom left" -> Pair(0f, 100f)
                    "bottom right" -> Pair(100f, 100f)
                    else -> null
                }
            }
            else -> null
        }
    }
}
