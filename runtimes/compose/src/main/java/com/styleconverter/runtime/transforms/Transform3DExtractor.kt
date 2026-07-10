package com.styleconverter.runtime.transforms

import com.styleconverter.runtime.PropertyRegistry
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts 3D transform configuration from IR properties.
 */
object Transform3DExtractor {

    init {
        // Phase 8 registration. The four 3D-context properties (perspective,
        // perspective-origin, transform-style, backface-visibility) live here
        // because Compose has no true 3D scene graph — all four are best-effort
        // and grouped under the transforms/ owner so the Phase 8 coverage
        // matrix lines up with the IR folder structure.
        PropertyRegistry.migrated(
            "Perspective",
            "PerspectiveOrigin",
            "TransformStyle",
            "BackfaceVisibility",
            owner = "transforms"
        )
    }

    fun extractTransform3DConfig(properties: List<Pair<String, JsonElement?>>): Transform3DConfig {
        var perspective: androidx.compose.ui.unit.Dp? = null
        var perspectiveOriginX = 50f
        var perspectiveOriginY = 50f
        var transformStyle = TransformStyleValue.FLAT
        var backfaceVisibility = BackfaceVisibilityValue.VISIBLE

        for ((type, data) in properties) {
            when (type) {
                "Perspective" -> perspective = ValueExtractors.extractDp(data)
                "PerspectiveOrigin" -> {
                    val origin = extractPerspectiveOrigin(data)
                    perspectiveOriginX = origin.first
                    perspectiveOriginY = origin.second
                }
                "TransformStyle" -> transformStyle = extractTransformStyle(data)
                "BackfaceVisibility" -> backfaceVisibility = extractBackfaceVisibility(data)
            }
        }

        return Transform3DConfig(
            perspective = perspective,
            perspectiveOriginX = perspectiveOriginX,
            perspectiveOriginY = perspectiveOriginY,
            transformStyle = transformStyle,
            backfaceVisibility = backfaceVisibility
        )
    }

    private fun extractPerspectiveOrigin(data: JsonElement?): Pair<Float, Float> {
        if (data == null) return 50f to 50f

        val obj = data as? JsonObject ?: return 50f to 50f
        // Each axis arrives from PerspectiveOriginPropertyParser.kt as ONE of:
        //   {"type":"left"|"center"|…}   keyword (serialName-as-type)
        //   {"type":"percentage","value":25.0}
        //   {"type":"length","px":10.0}
        //   bare number                  legacy percent shape
        // The old `.jsonPrimitive.floatOrNull` threw IllegalArgumentException
        // on every OBJECT axis, aborting StyleApplier.extractConfig for the
        // whole element (Transforms_C01: width/height/bg all collapsed to
        // the bare placeholder). Parse defensively and never throw.
        val rawX = axisPercent(obj["x"])
        val rawY = axisPercent(obj["y"])

        // css-values-4 <position>: keywords bind to their OWN axis, not the
        // token order — the converter stores tokens positionally, so
        // "bottom right" arrives as x=bottom / y=right and must be swapped
        // (browsers resolve it to x=right, y=bottom).
        val xIsVertical = rawX?.second == Axis.VERTICAL
        val yIsHorizontal = rawY?.second == Axis.HORIZONTAL
        val (px, py) = if (xIsVertical || yIsHorizontal) {
            (rawY?.first ?: 50f) to (rawX?.first ?: 50f)
        } else {
            (rawX?.first ?: 50f) to (rawY?.first ?: 50f)
        }
        return px to py
    }

    /** Which axis a <position> keyword is pinned to (css-values-4 §2.4). */
    private enum class Axis { HORIZONTAL, VERTICAL, EITHER }

    /**
     * One <position> axis → (percent, axis-affinity). Percent is the CSS
     * keyword mapping (left/top→0, center→50, right/bottom→100); lengths
     * can't resolve to a fraction without the box size, so they fall back
     * to the 50% initial value rather than crashing the extraction chain.
     */
    private fun axisPercent(el: JsonElement?): Pair<Float, Axis>? {
        if (el == null) return null
        // Legacy bare-number percent (pre-sealed-interface IR shape).
        (el as? JsonPrimitive)?.floatOrNull?.let {
            return it to Axis.EITHER
        }
        val o = el as? JsonObject ?: return null
        return when ((o["type"] as? JsonPrimitive)?.contentOrNull) {
            "left" -> 0f to Axis.HORIZONTAL
            "right" -> 100f to Axis.HORIZONTAL
            "top" -> 0f to Axis.VERTICAL
            "bottom" -> 100f to Axis.VERTICAL
            "center" -> 50f to Axis.EITHER
            "percentage" -> ((o["value"] as? JsonPrimitive)?.floatOrNull ?: 50f) to Axis.EITHER
            // Lengths need the element box to become a fraction; keep the
            // 50% initial value (better than aborting the whole element).
            "length" -> 50f to Axis.EITHER
            else -> null
        }
    }

    private fun extractTransformStyle(data: JsonElement?): TransformStyleValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return TransformStyleValue.FLAT

        return when (keyword) {
            "FLAT" -> TransformStyleValue.FLAT
            "PRESERVE_3D" -> TransformStyleValue.PRESERVE_3D
            else -> TransformStyleValue.FLAT
        }
    }

    private fun extractBackfaceVisibility(data: JsonElement?): BackfaceVisibilityValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()
            ?: return BackfaceVisibilityValue.VISIBLE

        return when (keyword) {
            "VISIBLE" -> BackfaceVisibilityValue.VISIBLE
            "HIDDEN" -> BackfaceVisibilityValue.HIDDEN
            else -> BackfaceVisibilityValue.VISIBLE
        }
    }

    fun isTransform3DProperty(type: String): Boolean {
        return type in setOf(
            "Perspective", "PerspectiveOrigin", "TransformStyle", "BackfaceVisibility"
        )
    }
}
