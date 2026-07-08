package com.styleconverter.test.style.shapes

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Applies CSS clip-path and shape properties using Compose Shape.
 *
 * ## CSS Properties
 * ```css
 * .clipped-circle {
 *     clip-path: circle(50%);
 * }
 *
 * .clipped-polygon {
 *     clip-path: polygon(50% 0%, 100% 100%, 0% 100%);
 * }
 *
 * .clipped-inset {
 *     clip-path: inset(10px 20px round 8px);
 * }
 *
 * .clipped-ellipse {
 *     clip-path: ellipse(50% 30% at center);
 * }
 *
 * .clipped-path {
 *     clip-path: path('M 0,0 L 100,0 L 50,100 Z');
 * }
 * ```
 *
 * ## Compose Mapping
 *
 * | CSS Shape | Compose Shape |
 * |-----------|---------------|
 * | circle() | CircleShape or GenericShape |
 * | ellipse() | GenericShape |
 * | inset() | RoundedCornerShape or GenericShape |
 * | polygon() | GenericShape |
 * | path() | GenericShape with PathParser |
 *
 * ## Usage
 * ```kotlin
 * Box(
 *     modifier = ShapeApplier.applyClipPath(
 *         modifier = Modifier.size(200.dp),
 *         shape = ShapeOutsideValue.Circle(radius = 100.dp)
 *     )
 * )
 * ```
 */
object ShapeApplier {

    /**
     * Apply clip-path to a modifier.
     *
     * @param modifier Base modifier
     * @param shape Shape configuration
     * @return Modifier with clip applied
     */
    fun applyClipPath(
        modifier: Modifier,
        shape: ShapeOutsideValue
    ): Modifier {
        val composeShape = toComposeShape(shape) ?: return modifier
        return modifier.clip(composeShape)
    }

    /**
     * Convert ShapeOutsideValue to a Compose Shape.
     *
     * @param shape Shape configuration
     * @return Compose Shape or null if not applicable
     */
    fun toComposeShape(shape: ShapeOutsideValue): Shape? {
        return when (shape) {
            ShapeOutsideValue.None -> null
            ShapeOutsideValue.MarginBox -> null // Box shapes handled by layout
            ShapeOutsideValue.ContentBox -> null
            ShapeOutsideValue.PaddingBox -> null
            ShapeOutsideValue.BorderBox -> null
            is ShapeOutsideValue.Inset -> createInsetShape(shape)
            is ShapeOutsideValue.Circle -> createCircleShape(shape)
            is ShapeOutsideValue.Ellipse -> createEllipseShape(shape)
            is ShapeOutsideValue.Polygon -> createPolygonShape(shape)
            is ShapeOutsideValue.Path -> createPathShape(shape)
            is ShapeOutsideValue.Url -> null // Image-based shapes not supported
        }
    }

    /**
     * Create an inset shape.
     *
     * CSS: inset(top right bottom left round border-radius)
     */
    private fun createInsetShape(inset: ShapeOutsideValue.Inset): Shape {
        return object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                val top = with(density) { inset.top.toPx() }
                val right = with(density) { inset.right.toPx() }
                val bottom = with(density) { inset.bottom.toPx() }
                val left = with(density) { inset.left.toPx() }
                val radius = inset.borderRadius?.let { with(density) { it.toPx() } } ?: 0f

                val rect = Rect(
                    left = left,
                    top = top,
                    right = size.width - right,
                    bottom = size.height - bottom
                )

                return if (radius > 0f) {
                    Outline.Rounded(
                        RoundRect(rect, CornerRadius(radius, radius))
                    )
                } else {
                    Outline.Rectangle(rect)
                }
            }
        }
    }

    /**
     * Create a circle shape.
     *
     * CSS: circle(radius at centerX centerY)
     * - radius: Length or percentage (50% = half of smallest dimension)
     * - centerX/centerY: percentages (50% = center)
     */
    private fun createCircleShape(circle: ShapeOutsideValue.Circle): Shape {
        return object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                val centerX = size.width * (circle.centerX / 100f)
                val centerY = size.height * (circle.centerY / 100f)

                val radius = if (circle.radius != null) {
                    with(density) { circle.radius.toPx() }
                } else {
                    // Default: closest-side (distance to nearest edge from center)
                    minOf(
                        centerX,
                        size.width - centerX,
                        centerY,
                        size.height - centerY
                    )
                }

                val path = Path().apply {
                    addOval(
                        Rect(
                            left = centerX - radius,
                            top = centerY - radius,
                            right = centerX + radius,
                            bottom = centerY + radius
                        )
                    )
                }

                return Outline.Generic(path)
            }
        }
    }

    /**
     * Create an ellipse shape.
     *
     * CSS: ellipse(radiusX radiusY at centerX centerY)
     */
    private fun createEllipseShape(ellipse: ShapeOutsideValue.Ellipse): Shape {
        return object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                val centerX = size.width * (ellipse.centerX / 100f)
                val centerY = size.height * (ellipse.centerY / 100f)

                val radiusX = ellipse.radiusX?.let { with(density) { it.toPx() } }
                    ?: minOf(centerX, size.width - centerX)
                val radiusY = ellipse.radiusY?.let { with(density) { it.toPx() } }
                    ?: minOf(centerY, size.height - centerY)

                val path = Path().apply {
                    addOval(
                        Rect(
                            left = centerX - radiusX,
                            top = centerY - radiusY,
                            right = centerX + radiusX,
                            bottom = centerY + radiusY
                        )
                    )
                }

                return Outline.Generic(path)
            }
        }
    }

    /**
     * Create a polygon shape.
     *
     * CSS: polygon(x1 y1, x2 y2, x3 y3, ...)
     * Points are percentages (0-100).
     */
    private fun createPolygonShape(polygon: ShapeOutsideValue.Polygon): Shape {
        return object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                if (polygon.points.isEmpty()) {
                    return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
                }

                val path = Path().apply {
                    polygon.points.forEachIndexed { index, (x, y) ->
                        val px = size.width * (x / 100f)
                        val py = size.height * (y / 100f)

                        if (index == 0) {
                            moveTo(px, py)
                        } else {
                            lineTo(px, py)
                        }
                    }
                    close()
                }

                return Outline.Generic(path)
            }
        }
    }

    /**
     * Create a path shape from SVG path data.
     *
     * CSS: path('M 0,0 L 100,0 L 50,100 Z')
     */
    private fun createPathShape(pathData: ShapeOutsideValue.Path): Shape {
        return object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                if (pathData.d.isEmpty()) {
                    return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
                }

                return try {
                    val parser = PathParser()
                    val pathNodes = parser.parsePathString(pathData.d)
                    val path = pathNodes.toPath()

                    // Scale path to fit the size
                    // Note: This is a simple approach; CSS paths may need viewBox handling
                    Outline.Generic(path)
                } catch (e: Exception) {
                    // Fallback to rectangle if path parsing fails
                    Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
                }
            }
        }
    }

    /**
     * Pre-built common shapes for convenience.
     */
    object Shapes {

        /** Triangle pointing up */
        val TriangleUp = createPolygonShape(
            ShapeOutsideValue.Polygon(listOf(50f to 0f, 100f to 100f, 0f to 100f))
        )

        /** Triangle pointing down */
        val TriangleDown = createPolygonShape(
            ShapeOutsideValue.Polygon(listOf(0f to 0f, 100f to 0f, 50f to 100f))
        )

        /** Triangle pointing right */
        val TriangleRight = createPolygonShape(
            ShapeOutsideValue.Polygon(listOf(0f to 0f, 100f to 50f, 0f to 100f))
        )

        /** Triangle pointing left */
        val TriangleLeft = createPolygonShape(
            ShapeOutsideValue.Polygon(listOf(100f to 0f, 0f to 50f, 100f to 100f))
        )

        /** Pentagon */
        val Pentagon: Shape = createRegularPolygon(5)

        /** Hexagon */
        val Hexagon: Shape = createRegularPolygon(6)

        /** Octagon */
        val Octagon: Shape = createRegularPolygon(8)

        /** Star (5-pointed) */
        val Star: Shape = createStar(5, 0.5f)

        /** Diamond/Rhombus */
        val Diamond = createPolygonShape(
            ShapeOutsideValue.Polygon(listOf(50f to 0f, 100f to 50f, 50f to 100f, 0f to 50f))
        )

        /** Parallelogram leaning right */
        val Parallelogram = createPolygonShape(
            ShapeOutsideValue.Polygon(listOf(20f to 0f, 100f to 0f, 80f to 100f, 0f to 100f))
        )

        /** Trapezoid */
        val Trapezoid = createPolygonShape(
            ShapeOutsideValue.Polygon(listOf(20f to 0f, 80f to 0f, 100f to 100f, 0f to 100f))
        )

        /** Arrow pointing right */
        val ArrowRight = createPolygonShape(
            ShapeOutsideValue.Polygon(listOf(
                0f to 25f, 60f to 25f, 60f to 0f,
                100f to 50f, 60f to 100f, 60f to 75f, 0f to 75f
            ))
        )

        /** Plus/Cross shape */
        val Plus = createPolygonShape(
            ShapeOutsideValue.Polygon(listOf(
                35f to 0f, 65f to 0f, 65f to 35f,
                100f to 35f, 100f to 65f, 65f to 65f,
                65f to 100f, 35f to 100f, 35f to 65f,
                0f to 65f, 0f to 35f, 35f to 35f
            ))
        )
    }

    /**
     * Create a regular polygon with the specified number of sides.
     */
    private fun createRegularPolygon(sides: Int): Shape {
        val points = (0 until sides).map { i ->
            val angle = (2 * Math.PI * i / sides) - (Math.PI / 2)
            val x = 50f + 50f * cos(angle).toFloat()
            val y = 50f + 50f * sin(angle).toFloat()
            x to y
        }
        return createPolygonShape(ShapeOutsideValue.Polygon(points))
    }

    /**
     * Create a star shape with the specified number of points.
     *
     * @param points Number of points on the star
     * @param innerRatio Ratio of inner radius to outer radius (0-1)
     */
    private fun createStar(points: Int, innerRatio: Float): Shape {
        val totalPoints = points * 2
        val starPoints = (0 until totalPoints).map { i ->
            val angle = (2 * Math.PI * i / totalPoints) - (Math.PI / 2)
            val radius = if (i % 2 == 0) 50f else 50f * innerRatio
            val x = 50f + radius * cos(angle).toFloat()
            val y = 50f + radius * sin(angle).toFloat()
            x to y
        }
        return createPolygonShape(ShapeOutsideValue.Polygon(starPoints))
    }

    /**
     * Create a custom star with specified parameters.
     *
     * @param points Number of points on the star
     * @param outerRadius Outer radius as percentage (0-50)
     * @param innerRadius Inner radius as percentage (0-50)
     */
    fun createCustomStar(
        points: Int,
        outerRadius: Float = 50f,
        innerRadius: Float = 25f
    ): Shape {
        val totalPoints = points * 2
        val starPoints = (0 until totalPoints).map { i ->
            val angle = (2 * Math.PI * i / totalPoints) - (Math.PI / 2)
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            val x = 50f + radius * cos(angle).toFloat()
            val y = 50f + radius * sin(angle).toFloat()
            x to y
        }
        return createPolygonShape(ShapeOutsideValue.Polygon(starPoints))
    }

    /**
     * Create a heart shape.
     */
    fun createHeartShape(): Shape {
        return object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                val path = Path().apply {
                    val width = size.width
                    val height = size.height

                    // Heart shape using bezier curves
                    moveTo(width / 2, height * 0.25f)

                    // Left curve
                    cubicTo(
                        width * 0.1f, height * 0.1f,
                        0f, height * 0.5f,
                        width / 2, height
                    )

                    // Right curve
                    moveTo(width / 2, height * 0.25f)
                    cubicTo(
                        width * 0.9f, height * 0.1f,
                        width, height * 0.5f,
                        width / 2, height
                    )

                    close()
                }

                return Outline.Generic(path)
            }
        }
    }

    /**
     * CSS shape-outside notes.
     */
    object Notes {
        const val SHAPE_OUTSIDE = """
            CSS shape-outside is used for text wrapping around floated elements.
            Compose doesn't have equivalent text wrapping around shapes.
            This applier focuses on clip-path which uses the same shape syntax.
        """

        const val PATH_SUPPORT = """
            SVG path data is supported through PathParser.
            Complex paths may need viewBox scaling to render correctly.
            Path coordinates should be in the range matching the element size.
        """

        const val PERCENTAGE_COORDS = """
            Polygon and shape coordinates use percentages (0-100).
            0,0 is top-left, 100,100 is bottom-right.
            This matches CSS percentage-based coordinates.
        """
    }
}
