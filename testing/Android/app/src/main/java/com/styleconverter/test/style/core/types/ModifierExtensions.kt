package com.styleconverter.test.style.core.types

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Common modifier extensions for property application.
 *
 * These extensions provide null-safe and optimized ways to apply
 * common modifier operations, avoiding unnecessary modifier chains
 * when values are zero or null.
 */

/**
 * Apply padding only if at least one value is non-null and non-zero.
 *
 * This avoids creating unnecessary padding modifiers when all values
 * are zero, which can improve performance in deeply nested layouts.
 *
 * @param top Top padding, or null to use 0.
 * @param end End padding (right in LTR), or null to use 0.
 * @param bottom Bottom padding, or null to use 0.
 * @param start Start padding (left in LTR), or null to use 0.
 * @return The modifier with padding applied, or unchanged if all values are zero/null.
 */
fun Modifier.paddingIfNotNull(
    top: Dp? = null,
    end: Dp? = null,
    bottom: Dp? = null,
    start: Dp? = null
): Modifier {
    val t = top ?: 0.dp
    val e = end ?: 0.dp
    val b = bottom ?: 0.dp
    val s = start ?: 0.dp
    return if (t == 0.dp && e == 0.dp && b == 0.dp && s == 0.dp) {
        this
    } else {
        this.padding(start = s, top = t, end = e, bottom = b)
    }
}

/**
 * Apply offset only if at least one value is non-null and non-zero.
 *
 * @param x Horizontal offset, or null to use 0.
 * @param y Vertical offset, or null to use 0.
 * @return The modifier with offset applied, or unchanged if both values are zero/null.
 */
fun Modifier.offsetIfNotNull(
    x: Dp? = null,
    y: Dp? = null
): Modifier {
    val xVal = x ?: 0.dp
    val yVal = y ?: 0.dp
    return if (xVal == 0.dp && yVal == 0.dp) {
        this
    } else {
        this.offset(x = xVal, y = yVal)
    }
}

/**
 * Conditionally apply a modifier transformation.
 *
 * @param condition Whether to apply the transformation.
 * @param block The transformation to apply if condition is true.
 * @return The modified or original modifier based on condition.
 */
inline fun Modifier.applyIf(
    condition: Boolean,
    block: Modifier.() -> Modifier
): Modifier = if (condition) block() else this

/**
 * Apply a modifier transformation if the value is non-null.
 *
 * @param value The nullable value to check.
 * @param block The transformation to apply if value is non-null.
 * @return The modified or original modifier based on value presence.
 */
inline fun <T> Modifier.applyIfNotNull(
    value: T?,
    block: Modifier.(T) -> Modifier
): Modifier = if (value != null) block(value) else this

/**
 * Apply padding with the same value on all sides, only if non-null and non-zero.
 *
 * @param all The padding to apply to all sides.
 * @return The modifier with padding applied, or unchanged if value is zero/null.
 */
fun Modifier.paddingAllIfNotNull(all: Dp?): Modifier {
    return if (all != null && all != 0.dp) {
        this.padding(all)
    } else {
        this
    }
}

/**
 * Apply horizontal padding only if non-null and non-zero.
 *
 * @param horizontal The horizontal padding to apply.
 * @return The modifier with padding applied, or unchanged if value is zero/null.
 */
fun Modifier.paddingHorizontalIfNotNull(horizontal: Dp?): Modifier {
    return if (horizontal != null && horizontal != 0.dp) {
        this.padding(horizontal = horizontal)
    } else {
        this
    }
}

/**
 * Apply vertical padding only if non-null and non-zero.
 *
 * @param vertical The vertical padding to apply.
 * @return The modifier with padding applied, or unchanged if value is zero/null.
 */
fun Modifier.paddingVerticalIfNotNull(vertical: Dp?): Modifier {
    return if (vertical != null && vertical != 0.dp) {
        this.padding(vertical = vertical)
    } else {
        this
    }
}
