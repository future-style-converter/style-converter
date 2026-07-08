package com.styleconverter.test.style.svg

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.atan2

/**
 * Applies SVG styling properties to Compose drawing operations.
 *
 * ## CSS Property Mapping
 * - fill → DrawScope color parameter with Fill style
 * - fill-opacity → Applied to fill color alpha
 * - fill-rule → PathFillType on Path objects
 * - stroke → DrawScope color parameter with Stroke style
 * - stroke-width → Stroke.width
 * - stroke-linecap → Stroke.cap (StrokeCap)
 * - stroke-linejoin → Stroke.join (StrokeJoin)
 * - stroke-dasharray → Stroke.pathEffect with DashPathEffect
 * - stroke-miterlimit → Stroke.miter
 * - paint-order → Order of fill/stroke drawing
 *
 * ## Usage
 * ```kotlin
 * val svgConfig = SvgExtractor.extractSvgConfig(properties)
 *
 * // Option 1: Use helper to draw a rectangle
 * Canvas(modifier) {
 *     SvgApplier.drawRect(this, svgConfig, Offset.Zero, size)
 * }
 *
 * // Option 2: Use stroke/fill directly
 * Canvas(modifier) {
 *     val stroke = SvgApplier.createStroke(svgConfig)
 *     drawPath(path, color, style = stroke)
 * }
 * ```
 */
object SvgApplier {

    /**
     * Create a Stroke style from SVG configuration.
     *
     * @param config The SVG configuration
     * @return Stroke object configured with width, cap, join, miter, and dash pattern
     */
    fun createStroke(config: SvgConfig): Stroke {
        val dashEffect = config.getDashPattern()?.let { pattern ->
            if (pattern.isNotEmpty()) {
                PathEffect.dashPathEffect(pattern, config.strokeDashoffset)
            } else null
        }

        return Stroke(
            width = config.strokeWidth,
            cap = config.toComposeStrokeCap(),
            join = config.toComposeStrokeJoin(),
            miter = config.strokeMiterlimit,
            pathEffect = dashEffect
        )
    }

    /**
     * Draw a rectangle with SVG styling.
     *
     * @param drawScope The DrawScope to draw in
     * @param config SVG configuration
     * @param topLeft Top-left corner position
     * @param size Size of the rectangle
     */
    fun drawRect(
        drawScope: DrawScope,
        config: SvgConfig,
        topLeft: Offset = Offset.Zero,
        size: Size = drawScope.size
    ) {
        drawInPaintOrder(drawScope, config) { operation ->
            when (operation) {
                PaintOrderElement.FILL -> {
                    config.getEffectiveFillColor()?.let { fillColor ->
                        drawScope.drawRect(
                            color = fillColor,
                            topLeft = topLeft,
                            size = size,
                            style = Fill
                        )
                    }
                }
                PaintOrderElement.STROKE -> {
                    if (config.hasStroke) {
                        config.getEffectiveStrokeColor()?.let { strokeColor ->
                            drawScope.drawRect(
                                color = strokeColor,
                                topLeft = topLeft,
                                size = size,
                                style = createStroke(config)
                            )
                        }
                    }
                }
                PaintOrderElement.MARKERS -> {
                    // Markers not applicable to rect
                }
            }
        }
    }

    /**
     * Draw a circle with SVG styling.
     *
     * @param drawScope The DrawScope to draw in
     * @param config SVG configuration
     * @param center Center of the circle
     * @param radius Radius of the circle
     */
    fun drawCircle(
        drawScope: DrawScope,
        config: SvgConfig,
        center: Offset = Offset(drawScope.size.width / 2, drawScope.size.height / 2),
        radius: Float = minOf(drawScope.size.width, drawScope.size.height) / 2
    ) {
        drawInPaintOrder(drawScope, config) { operation ->
            when (operation) {
                PaintOrderElement.FILL -> {
                    config.getEffectiveFillColor()?.let { fillColor ->
                        drawScope.drawCircle(
                            color = fillColor,
                            center = center,
                            radius = radius,
                            style = Fill
                        )
                    }
                }
                PaintOrderElement.STROKE -> {
                    if (config.hasStroke) {
                        config.getEffectiveStrokeColor()?.let { strokeColor ->
                            drawScope.drawCircle(
                                color = strokeColor,
                                center = center,
                                radius = radius,
                                style = createStroke(config)
                            )
                        }
                    }
                }
                PaintOrderElement.MARKERS -> {
                    // Markers not applicable to circle
                }
            }
        }
    }

    /**
     * Draw an oval/ellipse with SVG styling.
     *
     * @param drawScope The DrawScope to draw in
     * @param config SVG configuration
     * @param topLeft Top-left corner of bounding box
     * @param size Size of the bounding box
     */
    fun drawOval(
        drawScope: DrawScope,
        config: SvgConfig,
        topLeft: Offset = Offset.Zero,
        size: Size = drawScope.size
    ) {
        drawInPaintOrder(drawScope, config) { operation ->
            when (operation) {
                PaintOrderElement.FILL -> {
                    config.getEffectiveFillColor()?.let { fillColor ->
                        drawScope.drawOval(
                            color = fillColor,
                            topLeft = topLeft,
                            size = size,
                            style = Fill
                        )
                    }
                }
                PaintOrderElement.STROKE -> {
                    if (config.hasStroke) {
                        config.getEffectiveStrokeColor()?.let { strokeColor ->
                            drawScope.drawOval(
                                color = strokeColor,
                                topLeft = topLeft,
                                size = size,
                                style = createStroke(config)
                            )
                        }
                    }
                }
                PaintOrderElement.MARKERS -> {
                    // Markers not applicable to oval
                }
            }
        }
    }

    /**
     * Draw a path with SVG styling.
     *
     * @param drawScope The DrawScope to draw in
     * @param config SVG configuration
     * @param path The Path to draw
     * @param pathPoints Optional list of path vertices for marker placement
     */
    fun drawPath(
        drawScope: DrawScope,
        config: SvgConfig,
        path: Path,
        pathPoints: List<PathPoint>? = null
    ) {
        // Apply fill rule to path
        path.fillType = config.toComposePathFillType()

        drawInPaintOrder(drawScope, config) { operation ->
            when (operation) {
                PaintOrderElement.FILL -> {
                    config.getEffectiveFillColor()?.let { fillColor ->
                        drawScope.drawPath(
                            path = path,
                            color = fillColor,
                            style = Fill
                        )
                    }
                }
                PaintOrderElement.STROKE -> {
                    if (config.hasStroke) {
                        config.getEffectiveStrokeColor()?.let { strokeColor ->
                            drawScope.drawPath(
                                path = path,
                                color = strokeColor,
                                style = createStroke(config)
                            )
                        }
                    }
                }
                PaintOrderElement.MARKERS -> {
                    if (config.markers.hasMarkers && pathPoints != null) {
                        drawMarkers(drawScope, config, pathPoints)
                    }
                }
            }
        }
    }

    /**
     * Draw markers at path vertices.
     *
     * @param drawScope The DrawScope to draw in
     * @param config SVG configuration with marker settings
     * @param points List of path points with position and direction info
     */
    private fun drawMarkers(
        drawScope: DrawScope,
        config: SvgConfig,
        points: List<PathPoint>
    ) {
        if (points.isEmpty()) return

        val markerColor = config.getEffectiveStrokeColor()
            ?: config.getEffectiveFillColor()
            ?: Color.Black

        // Draw start marker
        if (points.isNotEmpty()) {
            drawMarker(drawScope, config.markers.markerStart, points.first(), markerColor)
        }

        // Draw mid markers
        if (points.size > 2) {
            for (i in 1 until points.size - 1) {
                drawMarker(drawScope, config.markers.markerMid, points[i], markerColor)
            }
        }

        // Draw end marker
        if (points.size > 1) {
            drawMarker(drawScope, config.markers.markerEnd, points.last(), markerColor)
        }
    }

    /**
     * Draw a single marker at a point.
     */
    private fun drawMarker(
        drawScope: DrawScope,
        marker: MarkerValue,
        point: PathPoint,
        defaultColor: Color
    ) {
        when (marker) {
            is MarkerValue.None -> { /* No marker */ }
            is MarkerValue.UrlReference -> {
                // URL references to external marker definitions not supported
                // Would require loading external SVG marker elements
            }
            is MarkerValue.Predefined -> {
                val color = marker.color ?: defaultColor
                val size = marker.size

                drawScope.translate(point.x, point.y) {
                    rotate(point.angle) {
                        when (marker.shape) {
                            MarkerShape.ARROW -> drawArrowMarker(this, size, color)
                            MarkerShape.CIRCLE -> drawCircleMarker(this, size, color, filled = true)
                            MarkerShape.CIRCLE_OPEN -> drawCircleMarker(this, size, color, filled = false)
                            MarkerShape.SQUARE -> drawSquareMarker(this, size, color, filled = true)
                            MarkerShape.SQUARE_OPEN -> drawSquareMarker(this, size, color, filled = false)
                            MarkerShape.DIAMOND -> drawDiamondMarker(this, size, color)
                        }
                    }
                }
            }
        }
    }

    /**
     * Draw an arrow marker pointing right (will be rotated by path direction).
     */
    private fun drawArrowMarker(drawScope: DrawScope, size: Float, color: Color) {
        val path = Path().apply {
            moveTo(-size, -size / 2)
            lineTo(0f, 0f)
            lineTo(-size, size / 2)
            close()
        }
        drawScope.drawPath(path, color, style = Fill)
    }

    /**
     * Draw a circle marker.
     */
    private fun drawCircleMarker(drawScope: DrawScope, size: Float, color: Color, filled: Boolean) {
        val radius = size / 2
        if (filled) {
            drawScope.drawCircle(color, radius, Offset.Zero, style = Fill)
        } else {
            drawScope.drawCircle(color, radius, Offset.Zero, style = Stroke(width = 1f))
        }
    }

    /**
     * Draw a square marker.
     */
    private fun drawSquareMarker(drawScope: DrawScope, size: Float, color: Color, filled: Boolean) {
        val halfSize = size / 2
        val rect = Rect(-halfSize, -halfSize, halfSize, halfSize)
        if (filled) {
            drawScope.drawRect(color, topLeft = Offset(-halfSize, -halfSize), size = Size(size, size), style = Fill)
        } else {
            drawScope.drawRect(color, topLeft = Offset(-halfSize, -halfSize), size = Size(size, size), style = Stroke(width = 1f))
        }
    }

    /**
     * Draw a diamond marker.
     */
    private fun drawDiamondMarker(drawScope: DrawScope, size: Float, color: Color) {
        val halfSize = size / 2
        val path = Path().apply {
            moveTo(0f, -halfSize)
            lineTo(halfSize, 0f)
            lineTo(0f, halfSize)
            lineTo(-halfSize, 0f)
            close()
        }
        drawScope.drawPath(path, color, style = Fill)
    }

    /**
     * Draw a line with SVG styling.
     *
     * @param drawScope The DrawScope to draw in
     * @param config SVG configuration
     * @param start Start point
     * @param end End point
     */
    fun drawLine(
        drawScope: DrawScope,
        config: SvgConfig,
        start: Offset,
        end: Offset
    ) {
        // Lines only have stroke, no fill
        if (config.hasStroke) {
            config.getEffectiveStrokeColor()?.let { strokeColor ->
                drawScope.drawLine(
                    color = strokeColor,
                    start = start,
                    end = end,
                    strokeWidth = config.strokeWidth,
                    cap = config.toComposeStrokeCap(),
                    pathEffect = config.getDashPattern()?.let { pattern ->
                        PathEffect.dashPathEffect(pattern, config.strokeDashoffset)
                    }
                )
            }
        }
    }

    /**
     * Draw a rounded rectangle with SVG styling.
     *
     * @param drawScope The DrawScope to draw in
     * @param config SVG configuration
     * @param topLeft Top-left corner
     * @param size Size of the rectangle
     * @param cornerRadius Corner radius
     */
    fun drawRoundRect(
        drawScope: DrawScope,
        config: SvgConfig,
        topLeft: Offset = Offset.Zero,
        size: Size = drawScope.size,
        cornerRadius: Float = 0f
    ) {
        val cornerRadiusObj = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)

        drawInPaintOrder(drawScope, config) { operation ->
            when (operation) {
                PaintOrderElement.FILL -> {
                    config.getEffectiveFillColor()?.let { fillColor ->
                        drawScope.drawRoundRect(
                            color = fillColor,
                            topLeft = topLeft,
                            size = size,
                            cornerRadius = cornerRadiusObj,
                            style = Fill
                        )
                    }
                }
                PaintOrderElement.STROKE -> {
                    if (config.hasStroke) {
                        config.getEffectiveStrokeColor()?.let { strokeColor ->
                            drawScope.drawRoundRect(
                                color = strokeColor,
                                topLeft = topLeft,
                                size = size,
                                cornerRadius = cornerRadiusObj,
                                style = createStroke(config)
                            )
                        }
                    }
                }
                PaintOrderElement.MARKERS -> {
                    // Markers not applicable to round rect
                }
            }
        }
    }

    /**
     * Execute drawing operations in paint order.
     */
    private inline fun drawInPaintOrder(
        drawScope: DrawScope,
        config: SvgConfig,
        draw: (PaintOrderElement) -> Unit
    ) {
        config.paintOrder.forEach { element ->
            draw(element)
        }
    }

    /**
     * Create a modifier that draws SVG-styled shapes behind content.
     *
     * @param config SVG configuration
     * @param drawOperation The drawing operation to perform
     * @return Modifier with the drawing applied
     */
    fun Modifier.drawSvgBehind(
        config: SvgConfig,
        drawOperation: DrawScope.(SvgConfig) -> Unit
    ): Modifier {
        return this.drawBehind {
            drawOperation(config)
        }
    }

    /**
     * Create a modifier that draws SVG-styled shapes with content.
     *
     * @param config SVG configuration
     * @param drawOperation The drawing operation to perform (called after content)
     * @return Modifier with the drawing applied
     */
    fun Modifier.drawSvgWithContent(
        config: SvgConfig,
        drawOperation: DrawScope.(SvgConfig) -> Unit
    ): Modifier {
        return this.drawWithContent {
            drawContent()
            drawOperation(config)
        }
    }

    /**
     * Create a modifier that applies SVG fill as background.
     *
     * This is a convenience method for simple solid color backgrounds
     * that also applies opacity.
     *
     * @param config SVG configuration
     * @return Modifier with background color applied, or unchanged if no fill
     */
    fun applySvgFillAsBackground(modifier: Modifier, config: SvgConfig): Modifier {
        val fillColor = config.getEffectiveFillColor() ?: return modifier
        return modifier.background(fillColor)
    }

    /**
     * Apply SVG stroke as a border-like effect.
     *
     * This draws a stroke around the component bounds.
     *
     * @param modifier Base modifier
     * @param config SVG configuration
     * @return Modifier with stroke border applied
     */
    fun applySvgStrokeAsBorder(modifier: Modifier, config: SvgConfig): Modifier {
        if (!config.hasStroke) return modifier

        return modifier.drawWithContent {
            drawContent()
            config.getEffectiveStrokeColor()?.let { strokeColor ->
                drawRect(
                    color = strokeColor,
                    size = size,
                    style = createStroke(config)
                )
            }
        }
    }

    /**
     * Check if SVG properties are primarily fill-related.
     */
    fun isFillOnly(config: SvgConfig): Boolean {
        return config.hasFill && !config.hasStroke
    }

    /**
     * Check if SVG properties are primarily stroke-related.
     */
    fun isStrokeOnly(config: SvgConfig): Boolean {
        return config.hasStroke && !config.hasFill
    }

    /**
     * Check if a property type is SVG-related.
     */
    fun isSvgProperty(type: String): Boolean {
        return SvgExtractor.isSvgProperty(type)
    }
}

/**
 * Represents a point on a path with position and direction.
 *
 * Used for marker placement at path vertices.
 *
 * @property x X coordinate of the point
 * @property y Y coordinate of the point
 * @property angle Direction angle in degrees (0 = right, 90 = down)
 */
data class PathPoint(
    val x: Float,
    val y: Float,
    val angle: Float = 0f
) {
    companion object {
        /**
         * Create a PathPoint with angle calculated from direction vector.
         */
        fun withDirection(x: Float, y: Float, dx: Float, dy: Float): PathPoint {
            val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            return PathPoint(x, y, angle)
        }

        /**
         * Create path points from a list of coordinates.
         *
         * @param points List of x,y coordinate pairs
         * @return List of PathPoints with angles calculated from segment directions
         */
        fun fromCoordinates(points: List<Pair<Float, Float>>): List<PathPoint> {
            if (points.isEmpty()) return emptyList()
            if (points.size == 1) return listOf(PathPoint(points[0].first, points[0].second))

            return points.mapIndexed { index, (x, y) ->
                val angle = when (index) {
                    0 -> {
                        // First point: angle from first to second point
                        val (nextX, nextY) = points[1]
                        Math.toDegrees(atan2((nextY - y).toDouble(), (nextX - x).toDouble())).toFloat()
                    }
                    points.lastIndex -> {
                        // Last point: angle from second-to-last to last point
                        val (prevX, prevY) = points[index - 1]
                        Math.toDegrees(atan2((y - prevY).toDouble(), (x - prevX).toDouble())).toFloat()
                    }
                    else -> {
                        // Middle points: average angle of incoming and outgoing segments
                        val (prevX, prevY) = points[index - 1]
                        val (nextX, nextY) = points[index + 1]
                        val inAngle = atan2((y - prevY).toDouble(), (x - prevX).toDouble())
                        val outAngle = atan2((nextY - y).toDouble(), (nextX - x).toDouble())
                        Math.toDegrees((inAngle + outAngle) / 2).toFloat()
                    }
                }
                PathPoint(x, y, angle)
            }
        }
    }
}
