package com.styleconverter.test.style.effects.shadow

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Configuration for CSS box-shadow properties.
 *
 * Box shadows can have multiple layers, each with different offsets,
 * blur, spread, and color values.
 *
 * ## Example
 * ```kotlin
 * val config = ShadowConfig(
 *     shadows = listOf(
 *         ShadowData(
 *             offsetX = 5.dp,
 *             offsetY = 5.dp,
 *             blurRadius = 10.dp,
 *             color = Color.Black.copy(alpha = 0.5f)
 *         )
 *     )
 * )
 * ```
 *
 * ## Compose Limitations
 * - Compose's built-in `shadow()` only supports elevation (no offset/spread/color control)
 * - Spread radius is not supported natively
 * - Inset shadows have limited support
 * - Custom implementation uses `drawBehind` for full CSS shadow compatibility
 */
data class ShadowConfig(
    val shadows: List<ShadowData> = emptyList()
) {
    /** True if there are any shadows to apply. */
    val hasShadow: Boolean get() = shadows.isNotEmpty()

    /** True if any shadow is an inset shadow. */
    val hasInsetShadow: Boolean get() = shadows.any { it.inset }

    /** True if any shadow uses spread radius. */
    val hasSpread: Boolean get() = shadows.any { it.spreadRadius != 0.dp }
}

/**
 * Data class representing a single CSS box-shadow value.
 *
 * ## CSS Syntax
 * ```css
 * box-shadow: offset-x offset-y blur-radius spread-radius color inset;
 * ```
 *
 * @property offsetX Horizontal offset of the shadow. Positive values push the shadow right.
 * @property offsetY Vertical offset of the shadow. Positive values push the shadow down.
 * @property blurRadius The blur radius. Higher values create softer shadows.
 * @property spreadRadius The spread radius. Positive values expand, negative contract.
 * @property color The shadow color including alpha.
 * @property inset If true, the shadow is drawn inside the element.
 */
data class ShadowData(
    val offsetX: Dp = 0.dp,
    val offsetY: Dp = 0.dp,
    val blurRadius: Dp = 0.dp,
    val spreadRadius: Dp = 0.dp,
    val color: Color = Color.Black,
    val inset: Boolean = false
)
