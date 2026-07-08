package com.styleconverter.test.style.borders.outline

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Configuration for CSS outline properties.
 *
 * Outline is similar to border but:
 * - Drawn OUTSIDE the element's border box
 * - Does not affect layout (no space taken)
 * - Can have an offset (outline-offset)
 * - Cannot have different values per side
 *
 * ## CSS Properties Mapped
 * - `outline-width` -> [width]
 * - `outline-style` -> [style]
 * - `outline-color` -> [color]
 * - `outline-offset` -> [offset]
 *
 * ## Example
 * ```kotlin
 * val config = OutlineConfig(
 *     width = 2.dp,
 *     style = OutlineStyle.SOLID,
 *     color = Color.Blue,
 *     offset = 4.dp
 * )
 * ```
 */
data class OutlineConfig(
    val width: Dp = 0.dp,
    val style: OutlineStyle = OutlineStyle.NONE,
    val color: Color = Color.Black,
    val offset: Dp = 0.dp
) {
    /**
     * True if there is an outline to render.
     * An outline is visible when it has width > 0 and a visible style.
     */
    val hasOutline: Boolean
        get() = width > 0.dp && style != OutlineStyle.NONE
}

/**
 * CSS outline-style values.
 *
 * Note: Compose has limited support for non-solid line styles.
 * DASHED and DOTTED are implemented using DashPathEffect.
 * DOUBLE is implemented by drawing two lines.
 * GROOVE, RIDGE, INSET, OUTSET fall back to SOLID.
 */
enum class OutlineStyle {
    NONE,
    SOLID,
    DASHED,
    DOTTED,
    DOUBLE,
    GROOVE,
    RIDGE,
    INSET,
    OUTSET
}
