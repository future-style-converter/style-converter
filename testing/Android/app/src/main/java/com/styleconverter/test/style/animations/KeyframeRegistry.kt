package com.styleconverter.test.style.animations

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Registry for CSS @keyframes definitions.
 *
 * ## Usage
 * ```kotlin
 * // Register keyframes from IR document
 * KeyframeRegistry.registerFromIR(document.keyframes)
 *
 * // Look up keyframes by name
 * val fadeIn = KeyframeRegistry.get("fadeIn")
 *
 * // In composable
 * val progress by infiniteTransition.animateFloat(...)
 * val interpolated = fadeIn?.interpolate(progress)
 * ```
 *
 * ## Built-in Animations
 * Common CSS animations are pre-registered:
 * - fadeIn, fadeOut
 * - slideInLeft, slideInRight, slideInUp, slideInDown
 * - bounce, shake, pulse
 * - spin, rotate
 * - zoomIn, zoomOut
 */
object KeyframeRegistry {

    private val keyframes = mutableMapOf<String, CSSKeyframes>()

    init {
        // Register built-in animations
        registerBuiltIns()
    }

    /**
     * Get a keyframe definition by name.
     */
    fun get(name: String): CSSKeyframes? = keyframes[name.lowercase()]

    /**
     * Register a keyframe definition.
     */
    fun register(definition: CSSKeyframes) {
        keyframes[definition.name.lowercase()] = definition
    }

    /**
     * Register multiple keyframe definitions.
     */
    fun registerAll(definitions: List<CSSKeyframes>) {
        definitions.forEach { register(it) }
    }

    /**
     * Register keyframes from IR JSON format.
     */
    fun registerFromIR(keyframesData: JsonElement?) {
        if (keyframesData == null) return

        when (keyframesData) {
            is JsonArray -> {
                keyframesData.forEach { item ->
                    parseCSSKeyframes(item)?.let { register(it) }
                }
            }
            is JsonObject -> {
                // Object with keyframe names as keys
                keyframesData.forEach { (name, data) ->
                    parseCSSKeyframes(data, name)?.let { register(it) }
                }
            }
            else -> {}
        }
    }

    /**
     * Parse a single @keyframes definition from IR.
     */
    private fun parseCSSKeyframes(data: JsonElement, name: String? = null): CSSKeyframes? {
        when (data) {
            is JsonObject -> {
                val keyframeName = name ?: data["name"]?.jsonPrimitive?.contentOrNull ?: return null
                val frames = data["keyframes"]?.jsonArray ?: data["frames"]?.jsonArray

                if (frames == null) {
                    // Try parsing as direct percentage-keyed object
                    val parsedFrames = mutableListOf<Keyframe>()
                    data.forEach { (key, value) ->
                        if (key != "name") {
                            val percentage = parsePercentage(key)
                            if (percentage != null && value is JsonObject) {
                                parseKeyframe(value, percentage)?.let { parsedFrames.add(it) }
                            }
                        }
                    }
                    if (parsedFrames.isNotEmpty()) {
                        return CSSKeyframes(keyframeName, parsedFrames)
                    }
                    return null
                }

                val keyframeList = frames.mapNotNull { frame ->
                    if (frame is JsonObject) {
                        val percentage = frame["percentage"]?.jsonPrimitive?.floatOrNull
                            ?: frame["offset"]?.jsonPrimitive?.floatOrNull?.times(100)
                            ?: parsePercentage(frame["at"]?.jsonPrimitive?.contentOrNull)
                            ?: 0f
                        parseKeyframe(frame, percentage)
                    } else null
                }

                return CSSKeyframes(keyframeName, keyframeList)
            }
            else -> return null
        }
    }

    /**
     * Parse percentage from string (e.g., "50%", "from", "to").
     */
    private fun parsePercentage(value: String?): Float? {
        return when (value?.lowercase()?.trim()) {
            "from" -> 0f
            "to" -> 100f
            else -> value?.replace("%", "")?.toFloatOrNull()
        }
    }

    /**
     * Parse a single keyframe from IR JSON.
     */
    private fun parseKeyframe(data: JsonObject, percentage: Float): Keyframe? {
        return Keyframe(
            percentage = percentage,
            opacity = data["opacity"]?.jsonPrimitive?.floatOrNull
                ?: extractOpacityFromProperties(data),
            translateX = extractTranslateX(data),
            translateY = extractTranslateY(data),
            translateZ = extractTranslateZ(data),
            scaleX = extractScaleX(data),
            scaleY = extractScaleY(data),
            scaleZ = extractScaleZ(data),
            rotateX = extractRotateX(data),
            rotateY = extractRotateY(data),
            rotateZ = extractRotateZ(data) ?: data["rotate"]?.jsonPrimitive?.floatOrNull,
            skewX = extractSkewX(data),
            skewY = extractSkewY(data),
            backgroundColor = extractColor(data["backgroundColor"] ?: data["background-color"]),
            color = extractColor(data["color"]),
            borderColor = extractColor(data["borderColor"] ?: data["border-color"]),
            width = extractLength(data["width"]),
            height = extractLength(data["height"]),
            top = extractLength(data["top"]),
            left = extractLength(data["left"]),
            right = extractLength(data["right"]),
            bottom = extractLength(data["bottom"]),
            paddingTop = extractLength(data["paddingTop"] ?: data["padding-top"]),
            paddingRight = extractLength(data["paddingRight"] ?: data["padding-right"]),
            paddingBottom = extractLength(data["paddingBottom"] ?: data["padding-bottom"]),
            paddingLeft = extractLength(data["paddingLeft"] ?: data["padding-left"]),
            marginTop = extractLength(data["marginTop"] ?: data["margin-top"]),
            marginRight = extractLength(data["marginRight"] ?: data["margin-right"]),
            marginBottom = extractLength(data["marginBottom"] ?: data["margin-bottom"]),
            marginLeft = extractLength(data["marginLeft"] ?: data["margin-left"]),
            borderRadius = extractLength(data["borderRadius"] ?: data["border-radius"]),
            borderWidth = extractLength(data["borderWidth"] ?: data["border-width"]),
            fontSize = extractLength(data["fontSize"] ?: data["font-size"]),
            letterSpacing = extractLength(data["letterSpacing"] ?: data["letter-spacing"]),
            lineHeight = extractLength(data["lineHeight"] ?: data["line-height"]),
            blur = extractFilterValue(data, "blur"),
            brightness = extractFilterValue(data, "brightness"),
            contrast = extractFilterValue(data, "contrast"),
            grayscale = extractFilterValue(data, "grayscale"),
            hueRotate = extractFilterValue(data, "hue-rotate") ?: extractFilterValue(data, "hueRotate"),
            saturate = extractFilterValue(data, "saturate"),
            sepia = extractFilterValue(data, "sepia")
        )
    }

    // Transform extraction helpers
    private fun extractTranslateX(data: JsonObject): Float? {
        val transform = data["transform"]
        if (transform is JsonObject) {
            return transform["translateX"]?.jsonPrimitive?.floatOrNull
                ?: transform["x"]?.jsonPrimitive?.floatOrNull
        }
        return data["translateX"]?.jsonPrimitive?.floatOrNull
    }

    private fun extractTranslateY(data: JsonObject): Float? {
        val transform = data["transform"]
        if (transform is JsonObject) {
            return transform["translateY"]?.jsonPrimitive?.floatOrNull
                ?: transform["y"]?.jsonPrimitive?.floatOrNull
        }
        return data["translateY"]?.jsonPrimitive?.floatOrNull
    }

    private fun extractTranslateZ(data: JsonObject): Float? {
        val transform = data["transform"]
        if (transform is JsonObject) {
            return transform["translateZ"]?.jsonPrimitive?.floatOrNull
                ?: transform["z"]?.jsonPrimitive?.floatOrNull
        }
        return data["translateZ"]?.jsonPrimitive?.floatOrNull
    }

    private fun extractScaleX(data: JsonObject): Float? {
        val transform = data["transform"]
        if (transform is JsonObject) {
            return transform["scaleX"]?.jsonPrimitive?.floatOrNull
                ?: transform["scale"]?.jsonPrimitive?.floatOrNull
        }
        return data["scaleX"]?.jsonPrimitive?.floatOrNull
            ?: data["scale"]?.jsonPrimitive?.floatOrNull
    }

    private fun extractScaleY(data: JsonObject): Float? {
        val transform = data["transform"]
        if (transform is JsonObject) {
            return transform["scaleY"]?.jsonPrimitive?.floatOrNull
                ?: transform["scale"]?.jsonPrimitive?.floatOrNull
        }
        return data["scaleY"]?.jsonPrimitive?.floatOrNull
            ?: data["scale"]?.jsonPrimitive?.floatOrNull
    }

    private fun extractScaleZ(data: JsonObject): Float? {
        val transform = data["transform"]
        if (transform is JsonObject) {
            return transform["scaleZ"]?.jsonPrimitive?.floatOrNull
        }
        return data["scaleZ"]?.jsonPrimitive?.floatOrNull
    }

    private fun extractRotateX(data: JsonObject): Float? {
        val transform = data["transform"]
        if (transform is JsonObject) {
            return transform["rotateX"]?.jsonPrimitive?.floatOrNull
        }
        return data["rotateX"]?.jsonPrimitive?.floatOrNull
    }

    private fun extractRotateY(data: JsonObject): Float? {
        val transform = data["transform"]
        if (transform is JsonObject) {
            return transform["rotateY"]?.jsonPrimitive?.floatOrNull
        }
        return data["rotateY"]?.jsonPrimitive?.floatOrNull
    }

    private fun extractRotateZ(data: JsonObject): Float? {
        val transform = data["transform"]
        if (transform is JsonObject) {
            return transform["rotateZ"]?.jsonPrimitive?.floatOrNull
                ?: transform["rotate"]?.jsonPrimitive?.floatOrNull
        }
        return data["rotateZ"]?.jsonPrimitive?.floatOrNull
    }

    private fun extractSkewX(data: JsonObject): Float? {
        val transform = data["transform"]
        if (transform is JsonObject) {
            return transform["skewX"]?.jsonPrimitive?.floatOrNull
        }
        return data["skewX"]?.jsonPrimitive?.floatOrNull
    }

    private fun extractSkewY(data: JsonObject): Float? {
        val transform = data["transform"]
        if (transform is JsonObject) {
            return transform["skewY"]?.jsonPrimitive?.floatOrNull
        }
        return data["skewY"]?.jsonPrimitive?.floatOrNull
    }

    private fun extractOpacityFromProperties(data: JsonObject): Float? {
        val properties = data["properties"]
        if (properties is JsonArray) {
            for (prop in properties) {
                if (prop is JsonObject && prop["type"]?.jsonPrimitive?.contentOrNull == "Opacity") {
                    return prop["data"]?.jsonPrimitive?.floatOrNull
                }
            }
        }
        return null
    }

    private fun extractLength(element: JsonElement?): Float? {
        if (element == null) return null
        return when (element) {
            is JsonPrimitive -> element.floatOrNull
            is JsonObject -> element["px"]?.jsonPrimitive?.floatOrNull
                ?: element["value"]?.jsonPrimitive?.floatOrNull
            else -> null
        }
    }

    private fun extractColor(element: JsonElement?): Color? {
        if (element == null) return null
        return try {
            ValueExtractors.extractColor(element)
        } catch (e: Exception) {
            null
        }
    }

    private fun extractFilterValue(data: JsonObject, filterName: String): Float? {
        val filter = data["filter"]
        if (filter is JsonArray) {
            for (item in filter) {
                if (item is JsonObject) {
                    if (item["fn"]?.jsonPrimitive?.contentOrNull == filterName ||
                        item["function"]?.jsonPrimitive?.contentOrNull == filterName) {
                        return item["value"]?.jsonPrimitive?.floatOrNull
                            ?: item["v"]?.jsonPrimitive?.floatOrNull
                    }
                }
            }
        }
        return data[filterName]?.jsonPrimitive?.floatOrNull
    }

    /**
     * Register built-in common animations.
     */
    private fun registerBuiltIns() {
        // fadeIn
        register(CSSKeyframes("fadeIn", listOf(
            Keyframe(0f, opacity = 0f),
            Keyframe(100f, opacity = 1f)
        )))

        // fadeOut
        register(CSSKeyframes("fadeOut", listOf(
            Keyframe(0f, opacity = 1f),
            Keyframe(100f, opacity = 0f)
        )))

        // fadeInUp
        register(CSSKeyframes("fadeInUp", listOf(
            Keyframe(0f, opacity = 0f, translateY = 20f),
            Keyframe(100f, opacity = 1f, translateY = 0f)
        )))

        // fadeInDown
        register(CSSKeyframes("fadeInDown", listOf(
            Keyframe(0f, opacity = 0f, translateY = -20f),
            Keyframe(100f, opacity = 1f, translateY = 0f)
        )))

        // slideInLeft
        register(CSSKeyframes("slideInLeft", listOf(
            Keyframe(0f, translateX = -100f, opacity = 0f),
            Keyframe(100f, translateX = 0f, opacity = 1f)
        )))

        // slideInRight
        register(CSSKeyframes("slideInRight", listOf(
            Keyframe(0f, translateX = 100f, opacity = 0f),
            Keyframe(100f, translateX = 0f, opacity = 1f)
        )))

        // slideInUp
        register(CSSKeyframes("slideInUp", listOf(
            Keyframe(0f, translateY = 100f, opacity = 0f),
            Keyframe(100f, translateY = 0f, opacity = 1f)
        )))

        // slideInDown
        register(CSSKeyframes("slideInDown", listOf(
            Keyframe(0f, translateY = -100f, opacity = 0f),
            Keyframe(100f, translateY = 0f, opacity = 1f)
        )))

        // bounce
        register(CSSKeyframes("bounce", listOf(
            Keyframe(0f, translateY = 0f),
            Keyframe(20f, translateY = 0f),
            Keyframe(40f, translateY = -30f),
            Keyframe(50f, translateY = 0f),
            Keyframe(60f, translateY = -15f),
            Keyframe(80f, translateY = 0f),
            Keyframe(100f, translateY = 0f)
        )))

        // shake
        register(CSSKeyframes("shake", listOf(
            Keyframe(0f, translateX = 0f),
            Keyframe(10f, translateX = -10f),
            Keyframe(20f, translateX = 10f),
            Keyframe(30f, translateX = -10f),
            Keyframe(40f, translateX = 10f),
            Keyframe(50f, translateX = -10f),
            Keyframe(60f, translateX = 10f),
            Keyframe(70f, translateX = -10f),
            Keyframe(80f, translateX = 10f),
            Keyframe(90f, translateX = -10f),
            Keyframe(100f, translateX = 0f)
        )))

        // pulse
        register(CSSKeyframes("pulse", listOf(
            Keyframe(0f, scaleX = 1f, scaleY = 1f),
            Keyframe(50f, scaleX = 1.05f, scaleY = 1.05f),
            Keyframe(100f, scaleX = 1f, scaleY = 1f)
        )))

        // spin / rotate
        register(CSSKeyframes("spin", listOf(
            Keyframe(0f, rotateZ = 0f),
            Keyframe(100f, rotateZ = 360f)
        )))
        register(CSSKeyframes("rotate", listOf(
            Keyframe(0f, rotateZ = 0f),
            Keyframe(100f, rotateZ = 360f)
        )))

        // zoomIn
        register(CSSKeyframes("zoomIn", listOf(
            Keyframe(0f, scaleX = 0f, scaleY = 0f, opacity = 0f),
            Keyframe(100f, scaleX = 1f, scaleY = 1f, opacity = 1f)
        )))

        // zoomOut
        register(CSSKeyframes("zoomOut", listOf(
            Keyframe(0f, scaleX = 1f, scaleY = 1f, opacity = 1f),
            Keyframe(100f, scaleX = 0f, scaleY = 0f, opacity = 0f)
        )))

        // heartbeat
        register(CSSKeyframes("heartbeat", listOf(
            Keyframe(0f, scaleX = 1f, scaleY = 1f),
            Keyframe(14f, scaleX = 1.3f, scaleY = 1.3f),
            Keyframe(28f, scaleX = 1f, scaleY = 1f),
            Keyframe(42f, scaleX = 1.3f, scaleY = 1.3f),
            Keyframe(70f, scaleX = 1f, scaleY = 1f),
            Keyframe(100f, scaleX = 1f, scaleY = 1f)
        )))

        // wobble
        register(CSSKeyframes("wobble", listOf(
            Keyframe(0f, translateX = 0f, rotateZ = 0f),
            Keyframe(15f, translateX = -25f, rotateZ = -5f),
            Keyframe(30f, translateX = 20f, rotateZ = 3f),
            Keyframe(45f, translateX = -15f, rotateZ = -3f),
            Keyframe(60f, translateX = 10f, rotateZ = 2f),
            Keyframe(75f, translateX = -5f, rotateZ = -1f),
            Keyframe(100f, translateX = 0f, rotateZ = 0f)
        )))

        // flash
        register(CSSKeyframes("flash", listOf(
            Keyframe(0f, opacity = 1f),
            Keyframe(25f, opacity = 0f),
            Keyframe(50f, opacity = 1f),
            Keyframe(75f, opacity = 0f),
            Keyframe(100f, opacity = 1f)
        )))

        // rubberBand
        register(CSSKeyframes("rubberBand", listOf(
            Keyframe(0f, scaleX = 1f, scaleY = 1f),
            Keyframe(30f, scaleX = 1.25f, scaleY = 0.75f),
            Keyframe(40f, scaleX = 0.75f, scaleY = 1.25f),
            Keyframe(50f, scaleX = 1.15f, scaleY = 0.85f),
            Keyframe(65f, scaleX = 0.95f, scaleY = 1.05f),
            Keyframe(75f, scaleX = 1.05f, scaleY = 0.95f),
            Keyframe(100f, scaleX = 1f, scaleY = 1f)
        )))
    }

    /**
     * Clear all registered keyframes (including built-ins).
     */
    fun clear() {
        keyframes.clear()
    }

    /**
     * Reset to built-in animations only.
     */
    fun reset() {
        clear()
        registerBuiltIns()
    }

    /**
     * Get all registered keyframe names.
     */
    fun getAllNames(): Set<String> = keyframes.keys.toSet()
}

/**
 * CompositionLocal for keyframe registry.
 * Allows overriding keyframes in specific composition subtrees.
 */
val LocalKeyframeRegistry = compositionLocalOf { KeyframeRegistry }
