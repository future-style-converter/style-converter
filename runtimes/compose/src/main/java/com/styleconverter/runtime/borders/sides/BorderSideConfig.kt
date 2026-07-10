package com.styleconverter.runtime.borders.sides

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.styleconverter.runtime.core.types.ValueExtractors.LineStyle

/**
 * Configuration for a single border side.
 */
data class BorderSideConfig(
    val width: Dp? = null,
    val color: Color? = null,
    val style: LineStyle? = null
) {
    // CSS 2.1 §8.5.3 / css-backgrounds-3 §3.2: border-style's initial value
    // is `none`, and a side whose style is none/hidden/ABSENT has a USED
    // border-width of 0 — it neither paints nor consumes layout space.
    // The old predicate treated a null (absent) style as bordered, so
    // `border-inline-end-width: 6px` alone painted a 6px solid band and
    // inset the content while web painted nothing (PW_Borders_Layout_03's
    // green box lost its right 6px, A-w 0.9438 after the float fix).
    val hasBorder: Boolean get() = width != null && width.value > 0 &&
        style != null && style != LineStyle.NONE && style != LineStyle.HIDDEN
}

/**
 * Configuration for all four border sides.
 */
data class AllBordersConfig(
    val top: BorderSideConfig = BorderSideConfig(),
    val end: BorderSideConfig = BorderSideConfig(),
    val bottom: BorderSideConfig = BorderSideConfig(),
    val start: BorderSideConfig = BorderSideConfig()
) {
    val hasBorders: Boolean get() = top.hasBorder || end.hasBorder || bottom.hasBorder || start.hasBorder

    val isUniform: Boolean get() = top.width == end.width && end.width == bottom.width && bottom.width == start.width &&
        top.color == end.color && end.color == bottom.color && bottom.color == start.color &&
        top.style == end.style && end.style == bottom.style && bottom.style == start.style
}
