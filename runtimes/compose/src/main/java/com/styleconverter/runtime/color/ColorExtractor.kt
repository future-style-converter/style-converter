package com.styleconverter.runtime.color

import androidx.compose.ui.graphics.Color
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts color-related configuration from IR properties.
 *
 * ## Supported Properties
 * - BackgroundColor: Solid background color (IRColor format)
 * - Opacity: Alpha transparency (number or object with alpha field)
 * - BackgroundImage: Gradients and image URLs (array of gradient objects)
 *
 * ## IR Formats
 *
 * ### BackgroundColor
 * ```json
 * { "srgb": { "r": 1.0, "g": 0.0, "b": 0.0, "a": 1.0 }, "original": "red" }
 * ```
 *
 * ### Opacity
 * ```json
 * { "alpha": 0.5, "original": { "type": "number", "value": 0.5 } }
 * // or just: 0.5
 * ```
 *
 * ### BackgroundImage (Linear Gradient)
 * ```json
 * [{
 *   "type": "linear-gradient",
 *   "angle": { "deg": 90.0 },
 *   "stops": [
 *     { "color": { "srgb": { "r": 1, "g": 0, "b": 0 } }, "position": 0.0 },
 *     { "color": { "srgb": { "r": 0, "g": 0, "b": 1 } }, "position": 100.0 }
 *   ]
 * }]
 * ```
 */
object ColorExtractor {

    /**
     * Extract a complete ColorConfig from a list of property type/data pairs.
     *
     * @param properties List of (propertyType, data) pairs from IR
     * @return ColorConfig with all extracted values
     */
    fun extractColorConfig(properties: List<Pair<String, JsonElement?>>): ColorConfig {
        var backgroundColor: Color? = null
        var opacity: Float? = null
        var backgroundImages: List<BackgroundImageConfig> = emptyList()
        var backgroundPosition = BackgroundPositionConfig()
        var backgroundSize: BackgroundSizeConfig = BackgroundSizeConfig.Auto
        var backgroundRepeat = BackgroundRepeatConfig.REPEAT
        // Per-layer comma lists (css-backgrounds-3 §2.3): source-order
        // entries pairing with backgroundImages by index. The single fields
        // above keep carrying the FIRST entry for legacy readers.
        var backgroundSizes: List<BackgroundSizeConfig> = emptyList()
        var backgroundRepeats: List<BackgroundRepeatAxes> = emptyList()
        var backgroundAttachment = BackgroundAttachment.SCROLL
        // CSS `background-clip: text` masks the bg-image to glyph
        // shapes — Compose paints the gradient via TextStyle.brush in
        // the placeholder text path, so the rectangular paint here must
        // be suppressed to avoid double-rendering.
        var suppressBackgroundImage = false
        var backgroundBlendModes: List<androidx.compose.ui.graphics.BlendMode> = emptyList()
        // padding-box / content-box painting-area clip (css-backgrounds-3
        // §3.11). Resolved into edge insets AFTER the property loop because
        // the inset amounts come from OTHER properties (border widths and
        // padding) that may appear later in the IR stream.
        var clipKeyword: String? = null

        properties.forEach { (type, data) ->
            when (type) {
                "BackgroundColor" -> backgroundColor = ValueExtractors.extractColor(data)
                "Opacity" -> opacity = extractOpacity(data)
                "BackgroundImage" -> backgroundImages = extractBackgroundImages(data)
                "BackgroundClip" -> {
                    // IR shape: array of keyword strings (one per layer),
                    // e.g. ["TEXT"] or ["BORDER_BOX"]. extractKeyword
                    // doesn't unwrap arrays — read the first entry
                    // directly. CSS treats `text` as the painting clip
                    // for every layer in our simplified single-layer
                    // model; suppress the rectangular bg paint when
                    // any layer requests it.
                    val keywords = (data as? JsonArray)?.mapNotNull {
                        (it as? JsonPrimitive)?.contentOrNull?.lowercase()
                    } ?: emptyList()
                    if (keywords.any { it == "text" }) suppressBackgroundImage = true
                    // First layer decides the box clip in our single-layer
                    // model (padding_box / content_box shrink the paint area).
                    clipKeyword = keywords.firstOrNull()
                }
                "BackgroundBlendMode" -> {
                    // IR shape: array of keyword strings — one per
                    // background-image layer. Pull through so the
                    // applier can saveLayer + paint.blendMode per
                    // layer in source order. Unknown / NORMAL tokens
                    // map to SrcOver = no-op blend.
                    backgroundBlendModes = (data as? JsonArray)?.mapNotNull { el ->
                        (el as? JsonPrimitive)?.contentOrNull?.let {
                            com.styleconverter.runtime.effects.blend.BlendModeMapping.fromCssValue(it)
                                ?: androidx.compose.ui.graphics.BlendMode.SrcOver
                        }
                    } ?: emptyList()
                }
                "BackgroundPosition", "BackgroundPositionX", "BackgroundPositionY",
                // Logical-axis variants. In LTR horizontal writing-mode the
                // CSS `block` axis is Y and `inline` axis is X; we don't
                // currently read writing-mode here so we fold them onto the
                // physical axes directly. RTL / vertical writing modes will
                // need an adjustment that swaps the mapping.
                "BackgroundPositionBlock", "BackgroundPositionInline" -> {
                    val mappedType = when (type) {
                        "BackgroundPositionBlock" -> "BackgroundPositionY"
                        "BackgroundPositionInline" -> "BackgroundPositionX"
                        else -> type
                    }
                    backgroundPosition = extractBackgroundPosition(data, backgroundPosition, mappedType)
                }
                "BackgroundSize" -> {
                    // Full per-layer list first; the single legacy field
                    // stays the first layer so older readers keep working.
                    backgroundSizes = extractBackgroundSizeLayers(data)
                    backgroundSize = backgroundSizes.firstOrNull() ?: BackgroundSizeConfig.Auto
                }
                "BackgroundRepeat" -> {
                    backgroundRepeats = extractBackgroundRepeatLayers(data)
                    backgroundRepeat = extractBackgroundRepeat(data)
                }
                "BackgroundAttachment" -> backgroundAttachment = extractBackgroundAttachment(data)
            }
        }

        // Resolve `background-clip: padding-box | content-box` into edge
        // insets from the border-box (css-backgrounds-3 §3.11):
        //   padding-box → inset by the computed border widths;
        //   content-box → border widths + padding.
        // The web reference paints exactly this: Background_BoxModel's
        // double-border gap shows the page through (padding-box), and
        // Background_Decorated paints red only behind the content
        // (content-box) — Android previously flooded the whole border box
        // (0.8852 / 0.6529). Border widths gate on hasBorder so a style-less
        // side contributes 0, mirroring the computed-value rules.
        val backgroundClipInsets: BackgroundClipInsets? = when (clipKeyword) {
            "padding_box", "padding-box", "content_box", "content-box" -> {
                val sides = com.styleconverter.runtime.borders.sides.BorderSideExtractor
                    .extractBorderConfig(properties)
                fun borderOf(side: com.styleconverter.runtime.borders.sides.BorderSideConfig) =
                    if (side.hasBorder) side.width ?: androidx.compose.ui.unit.Dp(0f)
                    else androidx.compose.ui.unit.Dp(0f)
                val contentBox = clipKeyword.startsWith("content")
                fun paddingOf(type: String) = if (!contentBox) androidx.compose.ui.unit.Dp(0f) else
                    properties.firstOrNull { it.first == type }?.second
                        ?.let { ValueExtractors.extractDp(it) } ?: androidx.compose.ui.unit.Dp(0f)
                BackgroundClipInsets(
                    top = borderOf(sides.top) + paddingOf("PaddingTop"),
                    right = borderOf(sides.end) + paddingOf("PaddingRight"),
                    bottom = borderOf(sides.bottom) + paddingOf("PaddingBottom"),
                    left = borderOf(sides.start) + paddingOf("PaddingLeft")
                ).takeIf { it.hasInsets }
            }
            else -> null
        }

        return ColorConfig(
            backgroundColor = backgroundColor,
            opacity = opacity,
            backgroundImages = backgroundImages,
            backgroundPosition = backgroundPosition,
            backgroundSize = backgroundSize,
            backgroundRepeat = backgroundRepeat,
            backgroundSizes = backgroundSizes,
            backgroundRepeats = backgroundRepeats,
            backgroundAttachment = backgroundAttachment,
            suppressBackgroundImage = suppressBackgroundImage,
            backgroundBlendModes = backgroundBlendModes,
            backgroundClipInsets = backgroundClipInsets,
        )
    }

    /**
     * Extract opacity value from IR data.
     *
     * Handles formats:
     * - Direct number: `0.5`
     * - Object with alpha: `{ "alpha": 0.5 }`
     * - Object with value: `{ "value": 0.5 }`
     *
     * @param data JSON element containing opacity data
     * @return Float opacity value (0.0-1.0), or null if not extractable
     */
    fun extractOpacity(data: JsonElement?): Float? {
        if (data == null) return null
        return when (data) {
            is JsonPrimitive -> data.floatOrNull
            is JsonObject -> {
                data["alpha"]?.jsonPrimitive?.floatOrNull
                    ?: data["value"]?.jsonPrimitive?.floatOrNull
            }
            else -> null
        }
    }

    /**
     * Extract background images (gradients, URLs) from IR data.
     *
     * @param data JSON array of background image objects
     * @return List of BackgroundImageConfig objects
     */
    fun extractBackgroundImages(data: JsonElement?): List<BackgroundImageConfig> {
        val array = (data as? JsonArray) ?: return emptyList()

        return array.mapNotNull { element ->
            val obj = (element as? JsonObject) ?: return@mapNotNull null
            val type = obj["type"]?.jsonPrimitive?.contentOrNull

            when (type) {
                "linear-gradient" -> extractLinearGradient(obj, repeating = false)
                "repeating-linear-gradient" -> extractLinearGradient(obj, repeating = true)
                "radial-gradient" -> extractRadialGradient(obj, repeating = false)
                "repeating-radial-gradient" -> extractRadialGradient(obj, repeating = true)
                "conic-gradient" -> extractConicGradient(obj, repeating = false)
                "repeating-conic-gradient" -> extractConicGradient(obj, repeating = true)
                "url" -> BackgroundImageConfig.Url(obj["url"]?.jsonPrimitive?.contentOrNull ?: "")
                "none" -> BackgroundImageConfig.None
                else -> null
            }
        }
    }

    /**
     * Extract linear gradient configuration.
     *
     * IR format:
     * ```json
     * {
     *   "type": "linear-gradient",
     *   "angle": { "deg": 180.0 },
     *   "stops": [...]
     * }
     * ```
     */
    private fun extractLinearGradient(obj: JsonObject, repeating: Boolean): BackgroundImageConfig.LinearGradient {
        val angle = obj["angle"]?.jsonObject?.get("deg")?.jsonPrimitive?.floatOrNull ?: 180f
        val stops = extractColorStops(obj["stops"] as? JsonArray)
        return BackgroundImageConfig.LinearGradient(angle, stops, repeating)
    }

    /**
     * Extract radial gradient configuration.
     *
     * IR format:
     * ```json
     * {
     *   "type": "radial-gradient",
     *   "position": { "x": 50.0, "y": 50.0 },
     *   "stops": [...]
     * }
     * ```
     */
    private fun extractRadialGradient(obj: JsonObject, repeating: Boolean): BackgroundImageConfig.RadialGradient {
        // The IR serializer emits the position under the key "pos" (see
        // BackgroundImageProperty.kt); the legacy "position" key is also
        // tolerated so older snapshots still extract.
        val position = (obj["pos"] ?: obj["position"])?.jsonObject
        val centerX = position?.get("x")?.jsonPrimitive?.floatOrNull?.div(100f) ?: 0.5f
        val centerY = position?.get("y")?.jsonPrimitive?.floatOrNull?.div(100f) ?: 0.5f
        val stops = extractColorStops(obj["stops"] as? JsonArray)
        // shape/size keywords arrive as plain lowercase strings.
        val shape = obj["shape"]?.jsonPrimitive?.contentOrNull?.let {
            when (it) {
                "circle" -> BackgroundImageConfig.RadialShape.CIRCLE
                "ellipse" -> BackgroundImageConfig.RadialShape.ELLIPSE
                else -> null
            }
        }
        val size = obj["size"]?.jsonPrimitive?.contentOrNull?.let {
            when (it) {
                "closest-side" -> BackgroundImageConfig.RadialSize.CLOSEST_SIDE
                "closest-corner" -> BackgroundImageConfig.RadialSize.CLOSEST_CORNER
                "farthest-side" -> BackgroundImageConfig.RadialSize.FARTHEST_SIDE
                "farthest-corner" -> BackgroundImageConfig.RadialSize.FARTHEST_CORNER
                else -> null
            }
        }
        return BackgroundImageConfig.RadialGradient(centerX, centerY, stops, repeating, shape, size)
    }

    /**
     * Extract conic gradient configuration.
     *
     * IR format:
     * ```json
     * {
     *   "type": "conic-gradient",
     *   "position": { "x": 50.0, "y": 50.0 },
     *   "fromAngle": { "deg": 0.0 },
     *   "stops": [...]
     * }
     * ```
     */
    private fun extractConicGradient(obj: JsonObject, repeating: Boolean): BackgroundImageConfig.ConicGradient {
        // Same key drift as radial: serializer writes "pos" (BackgroundImageProperty.kt).
        // Legacy "position" key tolerated for older snapshots.
        val position = (obj["pos"] ?: obj["position"])?.jsonObject
        val centerX = position?.get("x")?.jsonPrimitive?.floatOrNull?.div(100f) ?: 0.5f
        val centerY = position?.get("y")?.jsonPrimitive?.floatOrNull?.div(100f) ?: 0.5f
        // Serializer writes "angle" for conic; "fromAngle" was legacy.
        val angle = (obj["angle"] ?: obj["fromAngle"])?.jsonObject?.get("deg")?.jsonPrimitive?.floatOrNull ?: 0f
        val stops = extractColorStops(obj["stops"] as? JsonArray)
        return BackgroundImageConfig.ConicGradient(centerX, centerY, angle, stops, repeating)
    }

    /**
     * Extract color stops from a JSON array.
     *
     * IR format:
     * ```json
     * [
     *   { "color": { "srgb": { "r": 1, "g": 0, "b": 0 } }, "position": 0.0 },
     *   { "color": { "srgb": { "r": 0, "g": 0, "b": 1 } }, "position": 100.0 }
     * ]
     * ```
     *
     * Note: Position is in percentage (0-100) in IR, converted to fraction (0-1) here.
     */
    private fun extractColorStops(array: JsonArray?): List<ColorStop> {
        if (array == null) return emptyList()

        return array.mapIndexedNotNull { index, element ->
            val obj = (element as? JsonObject) ?: return@mapIndexedNotNull null
            val color = obj["color"]?.let { ValueExtractors.extractColor(it) } ?: return@mapIndexedNotNull null

            // Position is in percentage (0-100), convert to fraction (0-1)
            // If no position, distribute evenly
            val position = obj["position"]?.jsonPrimitive?.floatOrNull?.let { it / 100f }
                ?: (index.toFloat() / (array.size - 1).coerceAtLeast(1))

            ColorStop(color, position)
        }
    }

    /**
     * Extract background position from IR data.
     *
     * IR shapes observed in fixtures (see CLAUDE.md Phase 4 notes):
     *   BackgroundPositionX/Y -> { "type": "keyword", "value": "LEFT" }
     *   BackgroundPositionX/Y -> { "type": "percentage", "percentage": 50.0 }
     *   BackgroundPositionX/Y -> { "type": "length", "px": 10.0 }
     * Also tolerates the legacy shapes the previous extractor handled.
     */
    private fun extractBackgroundPosition(
        data: JsonElement?,
        current: BackgroundPositionConfig,
        type: String
    ): BackgroundPositionConfig {
        if (data == null) return current

        when (data) {
            is JsonPrimitive -> {
                // Legacy path — bare string keywords. Still emitted by some
                // older producers, so keep support.
                val keyword = data.contentOrNull?.lowercase()
                return when (keyword) {
                    "center" -> BackgroundPositionConfig.CENTER
                    "top" -> BackgroundPositionConfig.TOP_CENTER
                    "bottom" -> BackgroundPositionConfig.BOTTOM_CENTER
                    "left" -> BackgroundPositionConfig.CENTER_LEFT
                    "right" -> BackgroundPositionConfig.CENTER_RIGHT
                    else -> {
                        val percent = keyword?.replace("%", "")?.toFloatOrNull()?.div(100f)
                        if (percent != null) {
                            when (type) {
                                "BackgroundPositionX" -> current.copy(x = percent)
                                "BackgroundPositionY" -> current.copy(y = percent)
                                else -> BackgroundPositionConfig(percent, percent)
                            }
                        } else current
                    }
                }
            }
            is JsonObject -> {
                // New canonical shape: tagged by "type" with a sibling payload.
                val tag = data["type"]?.jsonPrimitive?.contentOrNull?.lowercase()
                // Resolve a 0..1 fraction from whichever payload fits.
                val fraction: Float? = when (tag) {
                    "keyword" -> when (data["value"]?.jsonPrimitive?.contentOrNull?.uppercase()) {
                        "LEFT", "TOP" -> 0f
                        "CENTER" -> 0.5f
                        "RIGHT", "BOTTOM" -> 1f
                        else -> null
                    }
                    "percentage" -> data["percentage"]?.jsonPrimitive?.floatOrNull?.div(100f)
                        ?: data["value"]?.jsonPrimitive?.floatOrNull?.div(100f)
                    else -> null
                }
                if (fraction != null) {
                    return when (type) {
                        "BackgroundPositionX" -> current.copy(x = fraction)
                        "BackgroundPositionY" -> current.copy(y = fraction)
                        else -> BackgroundPositionConfig(fraction, fraction)
                    }
                }
                // Absolute px position (css-backgrounds-3 §3.6: a <length>
                // offsets the tile edge from the box edge, independent of
                // the box size). Carried as a Dp offset with fraction 0 —
                // ColorApplier adds `xOffset/yOffset` to the free-space ×
                // fraction anchor, so `background-position-x: 20px` lands
                // the tile 20px from the left. Previously length values
                // were silently dropped here.
                if (tag == "length") {
                    val px = ValueExtractors.extractDp(data)
                    if (px != null) {
                        return when (type) {
                            "BackgroundPositionX" -> current.copy(x = 0f, xOffset = px)
                            "BackgroundPositionY" -> current.copy(y = 0f, yOffset = px)
                            else -> current.copy(x = 0f, y = 0f, xOffset = px, yOffset = px)
                        }
                    }
                }
                // Legacy object with {x, y} as percentages — retained for safety.
                val x = data["x"]?.jsonPrimitive?.floatOrNull?.div(100f) ?: current.x
                val y = data["y"]?.jsonPrimitive?.floatOrNull?.div(100f) ?: current.y
                return BackgroundPositionConfig(x, y)
            }
            else -> return current
        }
    }

    /**
     * Extract background size from IR data.
     *
     * IR shape (per CLAUDE.md Phase 4):
     *   BackgroundSize -> ["auto"] | ["cover"] | ["contain"]
     *   BackgroundSize -> [{ "w": {"px": N} }]              // width-only, h = auto
     *   BackgroundSize -> [{ "w": {"px": N}, "h": {"px": N} }]
     *   BackgroundSize -> [{ "w": 50.0, "h": 100.0 }]        // bare numbers = percentages
     * Multiple layers come through as a multi-element array; we use the first
     * because the ColorConfig carries a single BackgroundSizeConfig for now.
     */
    private fun extractBackgroundSize(data: JsonElement?): BackgroundSizeConfig {
        if (data == null) return BackgroundSizeConfig.Auto
        // Unwrap the array envelope — modern IR always wraps in [] for
        // per-layer support. Also accept a bare value for legacy inputs.
        val entry: JsonElement = when (data) {
            is JsonArray -> data.firstOrNull() ?: return BackgroundSizeConfig.Auto
            else -> data
        }
        return parseBackgroundSizeEntry(entry)
    }

    /**
     * Extract the FULL per-layer background-size list. Each array element
     * is one comma-separated CSS entry in source order (css-backgrounds-3
     * §2.3 pairs entry i with background-image layer i; the applier cycles
     * a shorter list). Bare non-array data = a one-entry list.
     */
    fun extractBackgroundSizeLayers(data: JsonElement?): List<BackgroundSizeConfig> {
        if (data == null) return emptyList()
        return when (data) {
            is JsonArray -> data.map { parseBackgroundSizeEntry(it) }
            else -> listOf(parseBackgroundSizeEntry(data))
        }
    }

    /** Parse ONE background-size layer entry (keyword or w/h object). */
    private fun parseBackgroundSizeEntry(entry: JsonElement): BackgroundSizeConfig {
        when (entry) {
            is JsonPrimitive -> {
                return when (entry.contentOrNull?.lowercase()) {
                    "cover" -> BackgroundSizeConfig.Cover
                    "contain" -> BackgroundSizeConfig.Contain
                    "auto" -> BackgroundSizeConfig.Auto
                    else -> BackgroundSizeConfig.Auto
                }
            }
            is JsonObject -> {
                // Keyword envelope — older format some producers still emit.
                val typeKey = entry["type"]?.jsonPrimitive?.contentOrNull?.lowercase()
                when (typeKey) {
                    "cover" -> return BackgroundSizeConfig.Cover
                    "contain" -> return BackgroundSizeConfig.Contain
                    "auto" -> return BackgroundSizeConfig.Auto
                }

                // New canonical w/h shape. Each may be either:
                //   { "px": N }       absolute length
                //   bare number N     percentage (0..100)
                //   absent            treat as auto
                val wEl = entry["w"]
                val hEl = entry["h"]
                val (width, widthPct) = parseSizeAxis(wEl)
                val (height, heightPct) = parseSizeAxis(hEl)

                // Legacy object with explicit pct fields — retained for safety.
                val legacyWPct = entry["widthPercent"]?.jsonPrimitive?.floatOrNull?.div(100f)
                val legacyHPct = entry["heightPercent"]?.jsonPrimitive?.floatOrNull?.div(100f)

                return if (width != null || height != null
                    || widthPct != null || heightPct != null
                    || legacyWPct != null || legacyHPct != null) {
                    BackgroundSizeConfig.Dimensions(
                        width = width,
                        height = height,
                        widthPercent = widthPct ?: legacyWPct,
                        heightPercent = heightPct ?: legacyHPct
                    )
                } else {
                    BackgroundSizeConfig.Auto
                }
            }
            else -> return BackgroundSizeConfig.Auto
        }
    }

    /**
     * Parse one axis (`w` or `h`) of BackgroundSize into (Dp, percent-fraction).
     * Exactly one side of the Pair will be non-null for any valid input.
     */
    private fun parseSizeAxis(el: JsonElement?): Pair<androidx.compose.ui.unit.Dp?, Float?> {
        if (el == null) return null to null
        return when (el) {
            // Bare number = percentage (0..100). Divide by 100 to match the
            // Dimensions.widthPercent contract (0..1 fraction).
            is JsonPrimitive -> el.floatOrNull?.let { null to (it / 100f) } ?: (null to null)
            // Object with px field = absolute length.
            is JsonObject -> ValueExtractors.extractDp(el) to null
            else -> null to null
        }
    }

    /**
     * Extract background repeat from IR data.
     *
     * IR shape (per fixtures):
     *   BackgroundRepeat -> ["repeat"] | ["no-repeat"] | ["space"] | ["round"]
     *   BackgroundRepeat -> [{"x": "repeat", "y": "no-repeat"}]  // repeat-x
     *   BackgroundRepeat -> [{"x": "no-repeat", "y": "repeat"}]  // repeat-y
     */
    private fun extractBackgroundRepeat(data: JsonElement?): BackgroundRepeatConfig {
        if (data == null) return BackgroundRepeatConfig.REPEAT
        // Unwrap array envelope — first layer wins until ColorConfig gains
        // per-layer repeat support.
        val entry: JsonElement = when (data) {
            is JsonArray -> data.firstOrNull() ?: return BackgroundRepeatConfig.REPEAT
            else -> data
        }
        // For two-axis objects, detect repeat-x / repeat-y directly.
        if (entry is JsonObject) {
            val x = entry["x"]?.jsonPrimitive?.contentOrNull?.lowercase()
            val y = entry["y"]?.jsonPrimitive?.contentOrNull?.lowercase()
            if (x != null && y != null) {
                return when {
                    x == "repeat" && y == "no-repeat" -> BackgroundRepeatConfig.REPEAT_X
                    x == "no-repeat" && y == "repeat" -> BackgroundRepeatConfig.REPEAT_Y
                    x == "no-repeat" && y == "no-repeat" -> BackgroundRepeatConfig.NO_REPEAT
                    else -> BackgroundRepeatConfig.REPEAT
                }
            }
        }
        val keyword = when (entry) {
            is JsonPrimitive -> entry.contentOrNull?.lowercase()?.replace("-", "_")
            is JsonObject -> entry["type"]?.jsonPrimitive?.contentOrNull?.lowercase()?.replace("-", "_")
            else -> null
        } ?: return BackgroundRepeatConfig.REPEAT

        return when (keyword) {
            "repeat" -> BackgroundRepeatConfig.REPEAT
            "repeat_x" -> BackgroundRepeatConfig.REPEAT_X
            "repeat_y" -> BackgroundRepeatConfig.REPEAT_Y
            "no_repeat" -> BackgroundRepeatConfig.NO_REPEAT
            "space" -> BackgroundRepeatConfig.SPACE
            "round" -> BackgroundRepeatConfig.ROUND
            else -> BackgroundRepeatConfig.REPEAT
        }
    }

    /**
     * Extract the FULL per-layer background-repeat list as two-axis values.
     * Each array element is one comma-separated CSS entry:
     *   "space"                       → space space (single-keyword expand)
     *   {"x": "space", "y": "round"}  → the §3.7 two-value syntax verbatim
     * This is the shape that finally represents `background-repeat:
     * space round` (Background_C03) — the flat enum can't.
     */
    fun extractBackgroundRepeatLayers(data: JsonElement?): List<BackgroundRepeatAxes> {
        if (data == null) return emptyList()
        val entries: List<JsonElement> = when (data) {
            is JsonArray -> data.toList()
            else -> listOf(data)
        }
        return entries.map { entry ->
            when (entry) {
                // Two-axis object → parse each axis keyword directly.
                is JsonObject -> {
                    val x = entry["x"]?.jsonPrimitive?.contentOrNull
                    val y = entry["y"]?.jsonPrimitive?.contentOrNull
                    if (x != null || y != null) {
                        BackgroundRepeatAxes(
                            x = BackgroundRepeatAxes.axisOf(x),
                            y = BackgroundRepeatAxes.axisOf(y)
                        )
                    } else {
                        // Keyword-envelope object ({"type": "..."}) — expand
                        // the single keyword like the primitive branch.
                        BackgroundRepeatAxes.from(extractBackgroundRepeat(entry))
                    }
                }
                // Single keyword → §3.7 expansion (repeat-x ≡ repeat no-repeat …).
                else -> BackgroundRepeatAxes.from(extractBackgroundRepeat(entry))
            }
        }
    }

    /**
     * Extract background attachment from IR data.
     *
     * IR shape: [{"type": "scroll"}] | [{"type": "fixed"}] | [{"type": "local"}]
     */
    private fun extractBackgroundAttachment(data: JsonElement?): BackgroundAttachment {
        if (data == null) return BackgroundAttachment.SCROLL
        val entry: JsonElement = when (data) {
            is JsonArray -> data.firstOrNull() ?: return BackgroundAttachment.SCROLL
            else -> data
        }
        val keyword = when (entry) {
            is JsonPrimitive -> entry.contentOrNull?.lowercase()
            is JsonObject -> entry["type"]?.jsonPrimitive?.contentOrNull?.lowercase()
            else -> null
        } ?: return BackgroundAttachment.SCROLL

        return when (keyword) {
            "fixed" -> BackgroundAttachment.FIXED
            "local" -> BackgroundAttachment.LOCAL
            "scroll" -> BackgroundAttachment.SCROLL
            else -> BackgroundAttachment.SCROLL
        }
    }
}
