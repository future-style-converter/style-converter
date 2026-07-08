package com.styleconverter.test.style.typography.advanced

import androidx.compose.ui.unit.dp
import com.styleconverter.test.style.PropertyRegistry
import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts baseline configuration from IR properties.
 */
object BaselineExtractor {

    init {
        // Claim the SVG/CSS-Inline-3 baseline family. These values drive
        // BaselineConfig which is surfaced on TextStyle.baselineShift and
        // SVG text placement. DominantBaselineAdjust is an experimental spec
        // alias — we register it so the legacy dispatcher skips it even
        // though we don't yet have a Compose rendering path for it.
        // CSS spec: https://drafts.csswg.org/css-inline-3/#baseline-props
        PropertyRegistry.migrated(
            "AlignmentBaseline",
            "BaselineShift",
            "BaselineSource",
            "DominantBaseline",
            "DominantBaselineAdjust",
            owner = "typography/advanced"
        )
    }

    /**
     * Extract baseline configuration from property pairs.
     */
    fun extractBaselineConfig(properties: List<Pair<String, JsonElement?>>): BaselineConfig {
        var alignmentBaseline = AlignmentBaselineValue.AUTO
        var dominantBaseline = DominantBaselineValue.AUTO
        var baselineShift: BaselineShiftValue = BaselineShiftValue.Baseline
        var baselineSource = BaselineSourceValue.AUTO

        for ((type, data) in properties) {
            when (type) {
                "AlignmentBaseline" -> alignmentBaseline = extractAlignmentBaseline(data)
                "DominantBaseline" -> dominantBaseline = extractDominantBaseline(data)
                "BaselineShift" -> baselineShift = extractBaselineShift(data)
                "BaselineSource" -> baselineSource = extractBaselineSource(data)
            }
        }

        return BaselineConfig(
            alignmentBaseline = alignmentBaseline,
            dominantBaseline = dominantBaseline,
            baselineShift = baselineShift,
            baselineSource = baselineSource
        )
    }

    private fun extractAlignmentBaseline(data: JsonElement?): AlignmentBaselineValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return AlignmentBaselineValue.AUTO

        return when (keyword) {
            "AUTO" -> AlignmentBaselineValue.AUTO
            "BASELINE" -> AlignmentBaselineValue.BASELINE
            "BEFORE_EDGE" -> AlignmentBaselineValue.BEFORE_EDGE
            "TEXT_BEFORE_EDGE" -> AlignmentBaselineValue.TEXT_BEFORE_EDGE
            "MIDDLE" -> AlignmentBaselineValue.MIDDLE
            "CENTRAL" -> AlignmentBaselineValue.CENTRAL
            "AFTER_EDGE" -> AlignmentBaselineValue.AFTER_EDGE
            "TEXT_AFTER_EDGE" -> AlignmentBaselineValue.TEXT_AFTER_EDGE
            "IDEOGRAPHIC" -> AlignmentBaselineValue.IDEOGRAPHIC
            "ALPHABETIC" -> AlignmentBaselineValue.ALPHABETIC
            "HANGING" -> AlignmentBaselineValue.HANGING
            "MATHEMATICAL" -> AlignmentBaselineValue.MATHEMATICAL
            else -> AlignmentBaselineValue.AUTO
        }
    }

    private fun extractDominantBaseline(data: JsonElement?): DominantBaselineValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return DominantBaselineValue.AUTO

        return when (keyword) {
            "AUTO" -> DominantBaselineValue.AUTO
            "TEXT_BOTTOM" -> DominantBaselineValue.TEXT_BOTTOM
            "ALPHABETIC" -> DominantBaselineValue.ALPHABETIC
            "IDEOGRAPHIC" -> DominantBaselineValue.IDEOGRAPHIC
            "MIDDLE" -> DominantBaselineValue.MIDDLE
            "CENTRAL" -> DominantBaselineValue.CENTRAL
            "MATHEMATICAL" -> DominantBaselineValue.MATHEMATICAL
            "HANGING" -> DominantBaselineValue.HANGING
            "TEXT_TOP" -> DominantBaselineValue.TEXT_TOP
            else -> DominantBaselineValue.AUTO
        }
    }

    private fun extractBaselineShift(data: JsonElement?): BaselineShiftValue {
        if (data == null) return BaselineShiftValue.Baseline

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull?.lowercase()
                return when (content) {
                    "baseline" -> BaselineShiftValue.Baseline
                    "sub" -> BaselineShiftValue.Sub
                    "super" -> BaselineShiftValue.Super
                    else -> {
                        data.floatOrNull?.let {
                            BaselineShiftValue.Length(it.dp)
                        } ?: BaselineShiftValue.Baseline
                    }
                }
            }
            is JsonObject -> {
                val type = data["type"]?.jsonPrimitive?.contentOrNull?.lowercase()
                return when (type) {
                    "sub" -> BaselineShiftValue.Sub
                    "super" -> BaselineShiftValue.Super
                    "percentage" -> {
                        val value = data["value"]?.jsonPrimitive?.floatOrNull
                            ?: data["percentage"]?.jsonPrimitive?.floatOrNull
                            ?: 0f
                        BaselineShiftValue.Percentage(value)
                    }
                    "length" -> {
                        val dp = ValueExtractors.extractDp(data)
                        BaselineShiftValue.Length(dp ?: 0.dp)
                    }
                    else -> {
                        val dp = ValueExtractors.extractDp(data)
                        if (dp != null) {
                            BaselineShiftValue.Length(dp)
                        } else {
                            BaselineShiftValue.Baseline
                        }
                    }
                }
            }
            else -> return BaselineShiftValue.Baseline
        }
    }

    private fun extractBaselineSource(data: JsonElement?): BaselineSourceValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()
            ?: return BaselineSourceValue.AUTO

        return when (keyword) {
            "AUTO" -> BaselineSourceValue.AUTO
            "FIRST" -> BaselineSourceValue.FIRST
            "LAST" -> BaselineSourceValue.LAST
            else -> BaselineSourceValue.AUTO
        }
    }

    /**
     * Check if a property type is baseline-related.
     */
    fun isBaselineProperty(type: String): Boolean {
        return type in BASELINE_PROPERTIES
    }

    private val BASELINE_PROPERTIES = setOf(
        "AlignmentBaseline", "DominantBaseline", "BaselineShift", "BaselineSource"
    )
}
