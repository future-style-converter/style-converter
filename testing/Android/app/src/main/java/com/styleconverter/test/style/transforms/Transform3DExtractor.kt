package com.styleconverter.test.style.transforms

import com.styleconverter.test.style.PropertyRegistry
import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
        val x = obj["x"]?.jsonPrimitive?.floatOrNull ?: 50f
        val y = obj["y"]?.jsonPrimitive?.floatOrNull ?: 50f

        return x to y
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
