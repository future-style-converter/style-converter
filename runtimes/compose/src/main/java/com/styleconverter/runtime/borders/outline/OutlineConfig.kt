package com.styleconverter.runtime.borders.outline

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
    // css-ui-4 §4.2: the initial value of outline-width is `medium` = 3px,
    // NOT 0 — `outline-style: solid` alone must paint a 3px ring like the
    // browser does. An explicit `outline-width: 0` still extracts to 0.dp
    // and disables the ring via [hasOutline].
    val width: Dp = 3.dp,
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
