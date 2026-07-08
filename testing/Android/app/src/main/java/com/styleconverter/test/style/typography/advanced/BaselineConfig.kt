package com.styleconverter.test.style.typography.advanced

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Alignment baseline value options.
 */
enum class AlignmentBaselineValue {
    AUTO,
    BASELINE,
    BEFORE_EDGE,
    TEXT_BEFORE_EDGE,
    MIDDLE,
    CENTRAL,
    AFTER_EDGE,
    TEXT_AFTER_EDGE,
    IDEOGRAPHIC,
    ALPHABETIC,
    HANGING,
    MATHEMATICAL
}

/**
 * Dominant baseline value options.
 */
enum class DominantBaselineValue {
    AUTO,
    TEXT_BOTTOM,
    ALPHABETIC,
    IDEOGRAPHIC,
    MIDDLE,
    CENTRAL,
    MATHEMATICAL,
    HANGING,
    TEXT_TOP
}

/**
 * Baseline shift value.
 */
sealed interface BaselineShiftValue {
    data object Baseline : BaselineShiftValue
    data object Sub : BaselineShiftValue
    data object Super : BaselineShiftValue
    data class Length(val value: Dp) : BaselineShiftValue
    data class Percentage(val value: Float) : BaselineShiftValue
}

/**
 * Configuration for CSS baseline properties.
 * Includes alignment-baseline, dominant-baseline, and baseline-shift.
 */
data class BaselineConfig(
    val alignmentBaseline: AlignmentBaselineValue = AlignmentBaselineValue.AUTO,
    val dominantBaseline: DominantBaselineValue = DominantBaselineValue.AUTO,
    val baselineShift: BaselineShiftValue = BaselineShiftValue.Baseline,
    val baselineSource: BaselineSourceValue = BaselineSourceValue.AUTO
) {
    /**
     * Check if this config has any baseline properties set.
     */
    val hasBaselineProperties: Boolean
        get() = alignmentBaseline != AlignmentBaselineValue.AUTO ||
                dominantBaseline != DominantBaselineValue.AUTO ||
                baselineShift != BaselineShiftValue.Baseline ||
                baselineSource != BaselineSourceValue.AUTO
}

/**
 * Baseline source value options.
 */
enum class BaselineSourceValue {
    AUTO,
    FIRST,
    LAST
}

/**
 * CSS text-combine-upright value.
 * Combines horizontal characters in vertical text.
 */
sealed interface TextCombineUprightValue {
    data object None : TextCombineUprightValue
    data object All : TextCombineUprightValue
    data class Digits(val count: Int = 2) : TextCombineUprightValue
}
