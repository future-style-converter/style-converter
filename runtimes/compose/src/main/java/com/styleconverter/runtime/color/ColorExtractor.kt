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
                // css-color-4 §6.4: `background-color: currentcolor` resolves
                // to THIS element's computed `color`. The static decoder
                // returns null for the keyword (it has no element context), so
                // route through CurrentColorBackground, which delegates every
                // statically-decodable payload straight back to
                // ValueExtractors.extractColor — only the previously-null
                // keyword case changes. `properties` is the MERGED list
                // (own declarations over the inherited channel), which is what
                // "the same element's computed color" means after the cascade.
                "BackgroundColor" -> backgroundColor =
                    CurrentColorBackground.resolve(data, properties)
                "Opacity" -> opacity = extractOpacity(data)
                // Font context threads through for lh/em gradient centers
                // (`at 1lh 50px`) — font metrics live HERE, on the
                // component's own FontSize/LineHeight properties.
                "BackgroundImage" -> backgroundImages = extractBackgroundImages(data, fontContextOf(properties))
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
        // insets from the border-box (css-backgrounds-3 §2.7):
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
     * Font metrics needed to resolve lh/em/rem gradient centers at
     * extract time (the applier never sees runtime-dependent units).
     * Byte-parallel with the SwiftUI extractor's FontContext.
     *
     * @property fontSizePx Element font-size in px (CSS initial 16px).
     * @property lineHeightPx Used line-height in px: an explicit
     *   LineHeight multiplier × fontSize, an explicit px value, or the
     *   `normal` ≈ 1.2 × fontSize ratio — the SAME pin the spacing
     *   resolver uses for `lh` (SpacingResolve pin P5).
     */
    data class FontContext(val fontSizePx: Float, val lineHeightPx: Float) {
        companion object {
            /** CSS initials: font-size 16px, line-height normal ≈ 1.2em. */
            val DEFAULT = FontContext(16f, 19.2f)
        }
    }

    /**
     * Build the FontContext from the component's own property list.
     * FontSize wire: {"px": N, ...}; LineHeight wire: {"multiplier": M}
     * (unitless) or {"px": N} (length) — pinned against the live
     * wave21-gate conic-gradient-line-height-relative-units artifacts
     * (FontSize {"px":50}, LineHeight {"multiplier":2} → lh = 100px).
     */
    internal fun fontContextOf(properties: List<Pair<String, JsonElement?>>): FontContext {
        // Element font-size in px; absent → CSS initial 16px.
        val fontPx = properties.firstOrNull { it.first == "FontSize" }?.second
            ?.let { (it as? JsonObject)?.get("px")?.jsonPrimitive?.floatOrNull }
            ?: 16f
        val lh = properties.firstOrNull { it.first == "LineHeight" }?.second as? JsonObject
        val lineHeightPx = lh?.get("multiplier")?.jsonPrimitive?.floatOrNull?.times(fontPx)
            ?: lh?.get("px")?.jsonPrimitive?.floatOrNull
            // `normal` computes to ≈1.2 × font-size — SpacingResolve pin P5.
            ?: (1.2f * fontPx)
        return FontContext(fontPx, lineHeightPx)
    }

    /**
     * Extract background images (gradients, URLs) from IR data.
     *
     * @param data JSON array of background image objects
     * @param fontCtx Font metrics for lh/em center resolution — callers
     *   without a property list keep the CSS-initial default.
     * @return List of BackgroundImageConfig objects
     */
    fun extractBackgroundImages(
        data: JsonElement?,
        fontCtx: FontContext = FontContext.DEFAULT
    ): List<BackgroundImageConfig> {
        val array = (data as? JsonArray) ?: return emptyList()

        return array.mapNotNull { element -> extractImageEntry(element, fontCtx) }
    }

    // One IR layer entry → config. Split out of extractBackgroundImages so
    // the cross-fade branch can recurse per argument image.
    private fun extractImageEntry(element: JsonElement, fontCtx: FontContext): BackgroundImageConfig? {
        return run {
            // The converter's BackgroundImageProperty serializes url layers
            // in TWO untagged shapes (pinned against live output, wave 9):
            //   "x.png"                          — plain-url layer, a BARE
            //                                      string primitive
            //   {"url": "data:...", "data": true} — data-URI layer, object
            //                                      WITHOUT a "type" tag
            // Both fell through the object-with-"type" parse below and were
            // silently dropped, so NO url() background ever reached the
            // applier. Handle the bare-string shape first.
            if (element is JsonPrimitive) {
                val s = element.contentOrNull ?: return@run null
                // `none` as a bare keyword = an image layer that draws
                // nothing (css-backgrounds-3 §2.3).
                return@run if (s.equals("none", ignoreCase = true)) {
                    BackgroundImageConfig.None
                } else {
                    BackgroundImageConfig.Url(s)
                }
            }
            val obj = (element as? JsonObject) ?: return@run null
            val type = obj["type"]?.jsonPrimitive?.contentOrNull

            when (type) {
                "linear-gradient" -> extractLinearGradient(obj, repeating = false)
                "repeating-linear-gradient" -> extractLinearGradient(obj, repeating = true)
                "radial-gradient" -> extractRadialGradient(obj, repeating = false, fontCtx)
                "repeating-radial-gradient" -> extractRadialGradient(obj, repeating = true, fontCtx)
                "conic-gradient" -> extractConicGradient(obj, repeating = false, fontCtx)
                "repeating-conic-gradient" -> extractConicGradient(obj, repeating = true, fontCtx)
                "url" -> BackgroundImageConfig.Url(obj["url"]?.jsonPrimitive?.contentOrNull ?: "")
                "none" -> BackgroundImageConfig.None
                // cross-fade() (css-images-4 §2.6.2, A-RC2) — weighted
                // composite of sub-images; recursion handles the args.
                "cross-fade" -> extractCrossFade(obj, fontCtx)
                // A bare <color> used as an image (cross-fade argument).
                // TWO live wire shapes: the nested {"type":"color",
                // "color":{srgb,…}} the serializer builds, AND the
                // flattened {"type":"color","srgb":…,"original":…} that
                // IRPropertySerializer.deepFlatten emits on the real wire
                // (it inlines any type+single-object-field pattern —
                // pinned by the converter run on the premultiplied-alpha
                // fixture). extractColor reads `srgb` either way.
                "color" -> ValueExtractors.extractColor(obj["color"] ?: obj)
                    ?.let { BackgroundImageConfig.SolidColor(it) }
                // image() notation (wave-48 lane W5, css-images-4 §2.5;
                // candidate walk wave-49 lane A3).
                // Wire (BackgroundImageSerializer.kt): {"type":"image",
                // "srcs":[<IRUrl>…], "color":{…IRColor…}?} — candidate
                // sources in author try-order plus an optional fallback
                // colour. §2.1: the FIRST source that can be displayed
                // paints; only if none can does the colour paint.
                //
                // Both halves of that rule now survive to the paint path.
                // This arm used to COLLAPSE the value here — colour if
                // present, else Url(srcs[0]) — which threw candidates 2..n
                // away and made WPT css-image-fallbacks-and-annotations
                // 003/004 unwinnable (their first candidate `1x1-green.svg`
                // does not exist beside the test, so the only paintable
                // sources were the ones being discarded). The ordering
                // decision belongs to ColorApplier, which is the only place
                // that can attempt a decode; see ImageCandidateChain for why
                // that is a MOVE of the wave-48 precedence note rather than
                // a reversal of it.
                //
                // An UNPARSEABLE colour (extractColor → null) yields a null
                // fallbackColor and leaves the sources intact — the same
                // "garbage colour must not eat the srcs" rule wave-48 F3
                // aligned the twins on, now expressed as an absent fallback.
                "image" -> {
                    // IRUrl wire per candidate: bare string, or {url,
                    // data:true} for data URIs. A candidate in neither shape
                    // is a malformed wire entry — dropped from the list, not
                    // silently promoted to a paintable src.
                    val srcs = (obj["srcs"] as? JsonArray).orEmpty().mapNotNull { entry ->
                        (entry as? JsonPrimitive)?.contentOrNull
                            ?: (entry as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull
                    }
                    val fallback = obj["color"]?.let { ValueExtractors.extractColor(it) }
                    // Nothing paintable at all (`image()` with neither srcs
                    // nor a usable colour) → null, so the caller's mapNotNull
                    // drops the layer instead of emitting a phantom clear one.
                    if (srcs.isEmpty() && fallback == null) null
                    else BackgroundImageConfig.ImageNotation(srcs, fallback)
                }
                // Untagged object carrying a "url" key — the data-URI layer
                // shape above (the "data": true flag just records that the
                // converter recognised the scheme; the url string is
                // authoritative either way).
                null -> obj["url"]?.jsonPrimitive?.contentOrNull
                    ?.let { BackgroundImageConfig.Url(it) }
                else -> null
            }
        }
    }

    /**
     * Extract a cross-fade() layer. Wire (BackgroundImageSerializer.kt):
     * {"type":"cross-fade","args":[{"weight":10,"image":<layer>},…]}
     * (absent "weight" = author omitted the percentage). Weights normalize
     * through the shared CrossFadeMath twin — see that file for the
     * §2.6.2 rules and the cross-platform pin table.
     */
    private fun extractCrossFade(obj: JsonObject, fontCtx: FontContext): BackgroundImageConfig? {
        val args = (obj["args"] as? JsonArray) ?: return null
        if (args.isEmpty()) return null
        // Authored weights: null = omitted (key absent on the wire).
        val weights = args.map { arg ->
            (arg as? JsonObject)?.get("weight")?.jsonPrimitive?.floatOrNull?.toDouble()
        }
        // Sub-images recurse through the same per-entry extractor.
        val images = args.map { arg ->
            (arg as? JsonObject)?.get("image")?.let { extractImageEntry(it, fontCtx) }
        }
        // ANY unparseable arg drops the WHOLE function — partially kept
        // args would silently re-weight the rest (no-silent-fallthrough).
        if (images.any { it == null }) return null
        val fractions = com.styleconverter.runtime.background.CrossFadeMath.normalizeWeights(weights)
        return BackgroundImageConfig.CrossFade(
            images.mapIndexed { i, img ->
                BackgroundImageConfig.CrossFadeEntry(fractions[i].toFloat(), img!!)
            }
        )
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
        // Wave 47: the authored <color-interpolation-method> rides the
        // layer as the optional `interp` key (BackgroundImageProperty.kt,
        // pinned by the wave46-final increasing-hue-hsl IR: "in hsl
        // increasing hue"). Absent → LEGACY (the historical sRGB ramp).
        return BackgroundImageConfig.LinearGradient(angle, stops, repeating, extractInterp(obj))
    }

    /** Parse the layer's optional `interp` wire key (see LinearGradient). */
    private fun extractInterp(obj: JsonObject): GradientInterpolation =
        GradientInterpolation.parse(obj["interp"]?.jsonPrimitive?.contentOrNull)

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
    private fun extractRadialGradient(
        obj: JsonObject, repeating: Boolean,
        fontCtx: FontContext = FontContext.DEFAULT
    ): BackgroundImageConfig.RadialGradient {
        // The IR serializer emits the position under the key "pos" (see
        // BackgroundImageProperty.kt); the legacy "position" key is also
        // tolerated so older snapshots still extract.
        val position = (obj["pos"] ?: obj["position"])?.jsonObject
        val centerX = extractGradientCoord(position?.get("x"), fontCtx)
        val centerY = extractGradientCoord(position?.get("y"), fontCtx)
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
        // The `interp` clause is grammatical on every gradient flavour
        // (css-images-4 §3.1) — read it here too, same wire key.
        return BackgroundImageConfig.RadialGradient(
            centerX, centerY, stops, repeating, shape, size, extractInterp(obj))
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
    private fun extractConicGradient(
        obj: JsonObject, repeating: Boolean,
        fontCtx: FontContext = FontContext.DEFAULT
    ): BackgroundImageConfig.ConicGradient {
        // Same key drift as radial: serializer writes "pos" (BackgroundImageProperty.kt).
        // Legacy "position" key tolerated for older snapshots.
        val position = (obj["pos"] ?: obj["position"])?.jsonObject
        val centerX = extractGradientCoord(position?.get("x"), fontCtx)
        val centerY = extractGradientCoord(position?.get("y"), fontCtx)
        // Serializer writes "angle" for conic; "fromAngle" was legacy.
        val angle = (obj["angle"] ?: obj["fromAngle"])?.jsonObject?.get("deg")?.jsonPrimitive?.floatOrNull ?: 0f
        val stops = extractColorStops(obj["stops"] as? JsonArray)
        // Conic stops are <angle-percentage>s — positionLength never
        // applies — but the interp clause does (css-images-4 §3.1).
        return BackgroundImageConfig.ConicGradient(
            centerX, centerY, angle, stops, repeating, extractInterp(obj))
    }

    /**
     * One gradient-center axis from the IRLengthPercentage wire
     * (ValueTypes.kt §IRLengthPercentageSerializer):
     *   raw number           → percentage → FRACTION(n / 100)
     *   {"px": N}            → absolute length → PX(N)
     *   {"original":{v,u}}   → runtime-dependent unit, resolved HERE
     *     against the component's own font metrics (lh/rlh/em/rem —
     *     the same ratios SpacingResolve pins: lh = used line-height,
     *     `normal` ≈ 1.2 × font-size). Unsupported units (vw/ch/…) fall
     *     back to CENTER with the fallthrough documented here — they
     *     cannot be resolved without a viewport, and the CSS default
     *     center is the least-wrong visible answer.
     * Absent axis → CENTER (the CSS `at` default, css-images-3 §3.2).
     */
    internal fun extractGradientCoord(el: JsonElement?, fontCtx: FontContext): GradientCoord {
        if (el == null) return GradientCoord.CENTER
        // Raw number = percent (legacy wire; also the keyword mappings).
        (el as? JsonPrimitive)?.floatOrNull?.let { return GradientCoord.fraction(it / 100f) }
        val obj = el as? JsonObject ?: return GradientCoord.CENTER
        // Runtime-dependent unit first — a typed original with pixels
        // ABSENT is the "null means runtime-dependent" IR convention.
        val orig = obj["original"] as? JsonObject
        val hasPx = obj["px"]?.jsonPrimitive?.floatOrNull
        if (hasPx != null) return GradientCoord.px(hasPx)
        if (orig != null) {
            val v = orig["v"]?.jsonPrimitive?.floatOrNull ?: return GradientCoord.CENTER
            return when (orig["u"]?.jsonPrimitive?.contentOrNull) {
                // lh = used line-height (multiplier × font-size when the
                // component declares one; `normal` ≈ 1.2em otherwise).
                "LH" -> GradientCoord.px(v * fontCtx.lineHeightPx)
                // rlh anchors to the ROOT line-height; without a root
                // context here we use the CSS-initial 16px × 1.2 — the
                // same lockstep ratio SpacingResolve applies.
                "RLH" -> GradientCoord.px(v * 19.2f)
                // em/rem — font-relative (css-values-4 §6.1.1).
                "EM" -> GradientCoord.px(v * fontCtx.fontSizePx)
                "REM" -> GradientCoord.px(v * 16f)
                // Viewport/other units need context this engine doesn't
                // have — documented fallback to the CSS default center.
                else -> GradientCoord.CENTER
            }
        }
        return GradientCoord.CENTER
    }

    /**
     * Extract color stops from a JSON array.
     *
     * IR format (live-pinned against the wave46-final gradient-border-box
     * per-test IR):
     * ```json
     * [
     *   { "color": { "srgb": { "r": 1, "g": 0, "b": 0 } }, "position": 0.0 },
     *   { "color": { "srgb": …  }, "position": null },
     *   { "color": { "srgb": …  }, "position": null, "positionLength": { "px": 30 } }
     * ]
     * ```
     *
     * `position` is a percentage (0-100) → [ColorStop.declaredPosition]
     * fraction; `positionLength` (the wave-40 <length> arm) → [ColorStop
     * .positionPx] — before wave 47 that arm was DROPPED here, so the
     * 30px repeat period of gradient-border-box never reached the brush
     * (android-ref 0.646, one ramp instead of stripes). The legacy
     * [ColorStop.position] keeps the declared-or-even-spread value for
     * the unowned RepeatingGradientHelper consumer; the render pipeline
     * spreads unpositioned stops per §3.4.3 in GradientStopResolver
     * instead (between positioned NEIGHBOURS, not over the whole count).
     */
    private fun extractColorStops(array: JsonArray?): List<ColorStop> {
        if (array == null) return emptyList()

        return array.mapIndexedNotNull { index, element ->
            val obj = (element as? JsonObject) ?: return@mapIndexedNotNull null
            val color = obj["color"]?.let { ValueExtractors.extractColor(it) } ?: return@mapIndexedNotNull null

            // Authored percent (0-100 → 0..1 fraction); JsonNull (the
            // serializer emits `"position": null` for unpositioned
            // stops) reads as floatOrNull == null, i.e. undeclared.
            val declared = obj["position"]?.jsonPrimitive?.floatOrNull?.let { it / 100f }

            // Wave 47: the <length> arm — `positionLength: {px: N}`
            // (absolute, reader-normalised). Runtime-dependent units
            // ({original:{v,u}} with no px) stay unpositioned here: the
            // gradient line has no font context of its own, and a
            // breadcrumb beats a guessed period (iOS twin rule).
            val lenObj = obj["positionLength"] as? JsonObject
            val positionPx = lenObj?.get("px")?.jsonPrimitive?.floatOrNull
            if (lenObj != null && positionPx == null) {
                GradientLog.once("gradient-stop-length-unit",
                    "gradient stop <length> in a runtime-dependent unit — treated as unpositioned on Android")
            }

            ColorStop(
                color = color,
                // Legacy resolution (declared, else the pre-wave even
                // spread) — kept ONLY for the unowned helper consumer.
                position = declared ?: (index.toFloat() / (array.size - 1).coerceAtLeast(1)),
                declaredPosition = declared,
                positionPx = positionPx
            )
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
     *
     * The `background` SHORTHAND emits a different wire entirely (wave 9,
     * pinned against live converter output of `background: red url(...)
     * right bottom`): BackgroundPosition carries a tagged PositionValue
     * LIST — one entry per comma layer — e.g.
     *   [{"type":"two-value","x":{"type":"right"},"y":{"type":"bottom"}}]
     *   [{"type":"center"}]
     * (see converter irmodels/properties/background/
     * BackgroundPositionProperty.kt). No runtime consumed this shape —
     * it silently fell into the `else -> current` arm, so every shorthand
     * position rendered top-left. Entry 0 parses here (per-layer position
     * lists are a later step, matching the single-position ColorConfig).
     */
    private fun extractBackgroundPosition(
        data: JsonElement?,
        current: BackgroundPositionConfig,
        type: String
    ): BackgroundPositionConfig {
        if (data == null) return current

        when (data) {
            // The shorthand's PositionValue list — first layer wins until
            // ColorConfig gains per-layer position support (same first-
            // entry rule the size/repeat extractors started with).
            is JsonArray -> return data.firstOrNull()
                ?.let { extractShorthandPositionEntry(it, current) } ?: current
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
                // Absolute px position (css-backgrounds-3 §2.6: a <length>
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
            // Array/primitive/object cover every JsonElement subtype — the
            // `when` is exhaustive, so no else arm (compiler-verified).
        }
    }

    /**
     * Parse ONE tagged PositionValue entry from the `background`
     * shorthand's list wire (BackgroundPositionProperty.PositionValue —
     * kotlinx sealed-class serialization tags each variant via "type"):
     *   {"type":"center"}                     → 50% 50% (§3.6 one-value)
     *   {"type":"two-value","x":E,"y":E}      → per-axis EdgeValues
     *   {"type":"keyword","keyword":"top"}    → one-keyword form (other
     *                                           axis centers, §3.6)
     *   {"type":"raw",…}                      → unparseable in the
     *                                           converter; logged, kept
     * Internal so the JVM suite pins it against the live wire strings.
     */
    internal fun extractShorthandPositionEntry(
        entry: JsonElement,
        current: BackgroundPositionConfig
    ): BackgroundPositionConfig {
        // Every PositionValue variant serializes as a tagged object.
        val obj = entry as? JsonObject ?: return current
        return when (obj["type"]?.jsonPrimitive?.contentOrNull) {
            // `center` alone means center on BOTH axes (§3.6).
            "center" -> BackgroundPositionConfig(0.5f, 0.5f)
            "two-value" -> {
                // Each axis is an EdgeValue: keyword-as-type, length, or
                // percentage. Fold the two axes into one config.
                val xa = shorthandEdgeAxis(obj["x"], horizontal = true)
                val ya = shorthandEdgeAxis(obj["y"], horizontal = false)
                BackgroundPositionConfig(
                    x = xa.first, y = ya.first,
                    xOffset = xa.second, yOffset = ya.second
                )
            }
            "keyword" -> {
                // One-keyword form: the named axis takes the keyword, the
                // OTHER axis defaults to center (css-backgrounds-3 §2.6).
                when (obj["keyword"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
                    "top" -> BackgroundPositionConfig(0.5f, 0f)
                    "bottom" -> BackgroundPositionConfig(0.5f, 1f)
                    "left" -> BackgroundPositionConfig(0f, 0.5f)
                    "right" -> BackgroundPositionConfig(1f, 0.5f)
                    "center" -> BackgroundPositionConfig(0.5f, 0.5f)
                    else -> current // unknown keyword — keep prior state
                }
            }
            else -> {
                // "raw" (converter could not parse the CSS) or a future
                // variant: keep the current position but say so — no
                // silent fallthrough (IRLog prints on JVM too).
                com.styleconverter.runtime.core.ir.IRLog.warn(
                    "ColorExtractor",
                    "BackgroundPosition shorthand entry not understood: $obj — keeping default position"
                )
                current
            }
        }
    }

    /**
     * One EdgeValue axis of the shorthand's two-value form → (fraction,
     * px offset). EdgeValue serializes keyword-as-type ("left"/"right"/
     * "top"/"bottom"/"center" ARE the tag), lengths as {"type":"length",
     * "px":N} (IRLength flattens to a px field — live-pinned), and
     * percentages as {"type":"percentage","percentage":N}. Start-edge
     * keywords and lengths anchor at fraction 0 with the px carried as an
     * offset — the same (fraction, offset) split the longhand length
     * branch uses, consumed by the applier as free×fraction + offset.
     */
    private fun shorthandEdgeAxis(el: JsonElement?, horizontal: Boolean): Pair<Float, androidx.compose.ui.unit.Dp> {
        // Missing axis (defensive — the converter always emits both in
        // two-value form): start edge, no offset.
        val obj = el as? JsonObject
            ?: return 0f to androidx.compose.ui.unit.Dp(0f)
        return when (obj["type"]?.jsonPrimitive?.contentOrNull) {
            // Start edges = fraction 0 (left on x, top on y — §3.6).
            "left", "top" -> 0f to androidx.compose.ui.unit.Dp(0f)
            // End edges = fraction 1: `right`/`bottom` align the tile's
            // far edge with the box's far edge via free-space × 1.
            "right", "bottom" -> 1f to androidx.compose.ui.unit.Dp(0f)
            // Either-axis center.
            "center" -> 0.5f to androidx.compose.ui.unit.Dp(0f)
            // Absolute px: raw edge offset from the start edge (§3.6) —
            // fraction 0 + Dp offset, matching the longhand length branch.
            "length" -> 0f to androidx.compose.ui.unit.Dp(
                obj["px"]?.jsonPrimitive?.floatOrNull ?: 0f
            )
            // Percentage: free-space fraction (0..100 → 0..1).
            "percentage" -> ((obj["percentage"]?.jsonPrimitive?.floatOrNull ?: 0f) / 100f) to
                androidx.compose.ui.unit.Dp(0f)
            // Unknown EdgeValue variant: start edge + one loud log.
            else -> {
                com.styleconverter.runtime.core.ir.IRLog.warn(
                    "ColorExtractor",
                    "BackgroundPosition EdgeValue not understood (${if (horizontal) "x" else "y"} axis): $obj"
                )
                0f to androidx.compose.ui.unit.Dp(0f)
            }
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
