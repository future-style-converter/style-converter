package com.styleconverter.test.style.borders.sides

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.styleconverter.test.style.core.types.ValueExtractors.LineStyle

/**
 * Configuration for a single border side.
 */
data class BorderSideConfig(
    val width: Dp? = null,
    val color: Color? = null,
    val style: LineStyle? = null
) {
    val hasBorder: Boolean get() = width != null && width.value > 0 && style != LineStyle.NONE
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
