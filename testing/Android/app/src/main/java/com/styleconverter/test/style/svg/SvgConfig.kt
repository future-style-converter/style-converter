package com.styleconverter.test.style.svg

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin

/**
 * Configuration for SVG-specific CSS properties.
 *
 * ## Supported Properties
 * - fill: Fill color or paint server reference
 * - fill-opacity: Fill transparency
 * - fill-rule: How to determine interior of a path
 * - stroke: Stroke color or paint server reference
 * - stroke-width: Width of the stroke
 * - stroke-linecap: Shape at end of open subpaths
 * - stroke-linejoin: Shape at corners of paths
 * - stroke-dasharray: Pattern of dashes and gaps
 * - stroke-dashoffset: Offset for start of dash pattern
 * - stroke-miterlimit: Limit for miter joins
 * - stroke-opacity: Stroke transparency
 * - stop-color: Gradient stop color
 * - stop-opacity: Gradient stop transparency
 * - paint-order: Order of painting operations
 *
 * ## Compose Mapping
 * SVG properties map to Canvas/DrawScope operations in Compose.
 * Use with custom vector drawing logic.
 */
data class SvgConfig(
    /** Fill color or reference */
    val fill: SvgFillValue = SvgFillValue.ColorFill(Color.Black),
    /** Fill opacity (0.0-1.0) */
    val fillOpacity: Float = 1.0f,
    /** Fill rule for determining path interior */
    val fillRule: FillRuleValue = FillRuleValue.NONZERO,
    /** Stroke color or reference */
    val stroke: SvgStrokeValue = SvgStrokeValue.None,
    /** Stroke width in pixels */
    val strokeWidth: Float = 1.0f,
    /** Shape at end of open subpaths */
    val strokeLinecap: StrokeLinecapValue = StrokeLinecapValue.BUTT,
    /** Shape at corners of paths */
    val strokeLinejoin: StrokeLinejoinValue = StrokeLinejoinValue.MITER,
    /** Dash pattern */
    val strokeDasharray: DashArrayValue = DashArrayValue.None,
    /** Offset for start of dash pattern */
    val strokeDashoffset: Float = 0.0f,
    /** Miter limit for miter joins */
    val strokeMiterlimit: Float = 4.0f,
    /** Stroke opacity (0.0-1.0) */
    val strokeOpacity: Float = 1.0f,
    /** Gradient stop color */
    val stopColor: Color? = null,
    /** Gradient stop opacity (0.0-1.0) */
    val stopOpacity: Float = 1.0f,
    /** Order of painting operations */
    val paintOrder: List<PaintOrderElement> = listOf(
        PaintOrderElement.FILL,
        PaintOrderElement.STROKE,
        PaintOrderElement.MARKERS
    ),
    /** Marker configuration */
    val markers: MarkerConfig = MarkerConfig.NONE
) {
    /** Returns true if fill is defined and not None. */
    val hasFill: Boolean
        get() = fill !is SvgFillValue.None

    /** Returns true if stroke is defined and not None. */
    val hasStroke: Boolean
        get() = stroke !is SvgStrokeValue.None

    /** Returns true if any SVG-specific property is explicitly set. */
    val hasSvgProperties: Boolean
        get() = hasFill || hasStroke || stopColor != null

    /** Get the fill color if it's a simple color, null otherwise. */
    fun getFillColor(): Color? = when (fill) {
        is SvgFillValue.ColorFill -> fill.color
        is SvgFillValue.UrlReference -> fill.fallbackColor
        else -> null
    }

    /** Get the stroke color if it's a simple color, null otherwise. */
    fun getStrokeColor(): Color? = when (stroke) {
        is SvgStrokeValue.ColorStroke -> stroke.color
        is SvgStrokeValue.UrlReference -> stroke.fallbackColor
        else -> null
    }

    /** Get the fill color with applied opacity. */
    fun getEffectiveFillColor(): Color? {
        val baseColor = getFillColor() ?: return null
        return baseColor.copy(alpha = baseColor.alpha * fillOpacity)
    }

    /** Get the stroke color with applied opacity. */
    fun getEffectiveStrokeColor(): Color? {
        val baseColor = getStrokeColor() ?: return null
        return baseColor.copy(alpha = baseColor.alpha * strokeOpacity)
    }

    /** Get the gradient stop color with applied opacity. */
    fun getEffectiveStopColor(): Color? = stopColor?.copy(alpha = stopColor.alpha * stopOpacity)

    /** Get dash pattern as a FloatArray for PathEffect.dashPathEffect(). */
    fun getDashPattern(): FloatArray? = when (strokeDasharray) {
        is DashArrayValue.Pattern -> strokeDasharray.values.toFloatArray()
        else -> null
    }

    /** Check if even-odd fill rule should be used. */
    val useEvenOddFillRule: Boolean
        get() = fillRule == FillRuleValue.EVENODD

    /** Convert stroke linecap to Compose StrokeCap. */
    fun toComposeStrokeCap(): StrokeCap = when (strokeLinecap) {
        StrokeLinecapValue.BUTT -> StrokeCap.Butt
        StrokeLinecapValue.ROUND -> StrokeCap.Round
        StrokeLinecapValue.SQUARE -> StrokeCap.Square
    }

    /** Convert stroke linejoin to Compose StrokeJoin. */
    fun toComposeStrokeJoin(): StrokeJoin = when (strokeLinejoin) {
        StrokeLinejoinValue.MITER, StrokeLinejoinValue.MITER_CLIP -> StrokeJoin.Miter
        StrokeLinejoinValue.ROUND -> StrokeJoin.Round
        StrokeLinejoinValue.BEVEL -> StrokeJoin.Bevel
        StrokeLinejoinValue.ARCS -> StrokeJoin.Round // Fallback
    }

    /** Convert fill rule to Compose PathFillType. */
    fun toComposePathFillType(): PathFillType = when (fillRule) {
        FillRuleValue.NONZERO -> PathFillType.NonZero
        FillRuleValue.EVENODD -> PathFillType.EvenOdd
    }

    companion object {
        val DEFAULT = SvgConfig()
        val STROKE_ONLY = SvgConfig(
            fill = SvgFillValue.None,
            stroke = SvgStrokeValue.ColorStroke(Color.Black)
        )
        val FILL_ONLY = SvgConfig(
            fill = SvgFillValue.ColorFill(Color.Black),
            stroke = SvgStrokeValue.None
        )
    }
}

/**
 * SVG fill values.
 */
sealed interface SvgFillValue {
    /** No fill */
    data object None : SvgFillValue
    /** Solid color fill */
    data class ColorFill(val color: Color) : SvgFillValue
    /** Reference to a paint server (gradient, pattern) */
    data class UrlReference(val url: String, val fallbackColor: Color? = null) : SvgFillValue
    /** Context fill (inherited) */
    data object ContextFill : SvgFillValue
}

/**
 * SVG stroke values.
 */
sealed interface SvgStrokeValue {
    /** No stroke */
    data object None : SvgStrokeValue
    /** Solid color stroke */
    data class ColorStroke(val color: Color) : SvgStrokeValue
    /** Reference to a paint server */
    data class UrlReference(val url: String, val fallbackColor: Color? = null) : SvgStrokeValue
    /** Context stroke (inherited) */
    data object ContextStroke : SvgStrokeValue
}

/**
 * Fill rule values.
 */
enum class FillRuleValue {
    /** Non-zero winding rule */
    NONZERO,
    /** Even-odd rule */
    EVENODD
}

/**
 * Stroke linecap values.
 */
enum class StrokeLinecapValue {
    /** Flat end at the endpoint */
    BUTT,
    /** Rounded end */
    ROUND,
    /** Square end that extends past the endpoint */
    SQUARE
}

/**
 * Stroke linejoin values.
 */
enum class StrokeLinejoinValue {
    /** Sharp corner */
    MITER,
    /** Miter with clipping */
    MITER_CLIP,
    /** Rounded corner */
    ROUND,
    /** Beveled corner */
    BEVEL,
    /** Smooth arc (CSS4) */
    ARCS
}

/**
 * Dash array values.
 */
sealed interface DashArrayValue {
    /** No dashing (solid line) */
    data object None : DashArrayValue
    /** Custom dash pattern */
    data class Pattern(val values: List<Float>) : DashArrayValue
}

/**
 * Paint order elements.
 */
enum class PaintOrderElement {
    FILL, STROKE, MARKERS
}

/**
 * SVG marker configuration.
 *
 * Markers are symbols placed at vertices of paths.
 * CSS properties: marker-start, marker-mid, marker-end
 */
data class MarkerConfig(
    /** Marker at the start of the path */
    val markerStart: MarkerValue = MarkerValue.None,
    /** Marker at middle vertices of the path */
    val markerMid: MarkerValue = MarkerValue.None,
    /** Marker at the end of the path */
    val markerEnd: MarkerValue = MarkerValue.None
) {
    /** True if any marker is defined */
    val hasMarkers: Boolean
        get() = markerStart !is MarkerValue.None ||
                markerMid !is MarkerValue.None ||
                markerEnd !is MarkerValue.None

    companion object {
        val NONE = MarkerConfig()

        /** Create arrow markers (commonly used for flowcharts) */
        fun arrows() = MarkerConfig(
            markerEnd = MarkerValue.Predefined(MarkerShape.ARROW)
        )

        /** Create circle markers (commonly used for data points) */
        fun circles() = MarkerConfig(
            markerStart = MarkerValue.Predefined(MarkerShape.CIRCLE),
            markerMid = MarkerValue.Predefined(MarkerShape.CIRCLE),
            markerEnd = MarkerValue.Predefined(MarkerShape.CIRCLE)
        )
    }
}

/**
 * Marker value types.
 */
sealed interface MarkerValue {
    /** No marker */
    data object None : MarkerValue

    /** Reference to a marker definition by URL */
    data class UrlReference(val url: String) : MarkerValue

    /** Predefined marker shape */
    data class Predefined(
        val shape: MarkerShape,
        val size: Float = 6f,
        val color: androidx.compose.ui.graphics.Color? = null
    ) : MarkerValue
}

/**
 * Predefined marker shapes.
 */
enum class MarkerShape {
    /** Triangle arrow pointing in path direction */
    ARROW,
    /** Filled circle */
    CIRCLE,
    /** Filled square */
    SQUARE,
    /** Diamond shape */
    DIAMOND,
    /** Empty circle (ring) */
    CIRCLE_OPEN,
    /** Empty square */
    SQUARE_OPEN
}
