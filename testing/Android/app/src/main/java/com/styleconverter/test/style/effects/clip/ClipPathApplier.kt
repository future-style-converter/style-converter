package com.styleconverter.test.style.effects.clip

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

/**
 * Applies CSS clip-path styling to Compose modifiers.
 *
 * Converts [ClipPathConfig] shapes into Compose [Shape] implementations
 * and applies them using [Modifier.clip].
 *
 * ## Supported Shapes
 * - Circle: Full support with radius keywords
 * - Ellipse: Full support with radius keywords
 * - Inset: Full support including border-radius
 * - Polygon: Full support for arbitrary polygons
 * - Path: Limited support (fallback to rectangle)
 *
 * ## Compose Implementation
 * Compose's clip modifier uses [Shape] to define clipping regions.
 * Custom shapes are created by implementing [Shape.createOutline].
 */
object ClipPathApplier {

    /**
     * Apply clip-path to modifier.
     *
     * @param modifier The modifier to apply clipping to.
     * @param config The clip-path configuration.
     * @return Modified modifier with clipping applied.
     */
    fun applyClipPath(modifier: Modifier, config: ClipPathConfig): Modifier {
        val shape = config.shape ?: return modifier

        val composeShape = when (shape) {
            is ClipShape.Circle -> createCircleShape(shape)
            is ClipShape.Ellipse -> createEllipseShape(shape)
            is ClipShape.Inset -> createInsetShape(shape)
            is ClipShape.Polygon -> createPolygonShape(shape)
            is ClipShape.Path -> createPathShape(shape)
            is ClipShape.LegacyRect -> createLegacyRectShape(shape)
        }

        return modifier.clip(composeShape)
    }

    /**
     * Create a Compose Shape for circle clip-path.
     */
    private fun createCircleShape(circle: ClipShape.Circle): Shape {
        return object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                val centerX = size.width * (circle.centerX / 100f)
                val centerY = size.height * (circle.centerY / 100f)

                val radius = resolveCircleRadius(
                    circle.radius,
                    size,
                    centerX,
                    centerY,
                    density
                )

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
     * Resolve circle radius based on radius type and element size.
     */
    private fun resolveCircleRadius(
        radius: ClipRadius,
        size: Size,
        centerX: Float,
        centerY: Float,
        density: Density
    ): Float {
        return when (radius) {
            is ClipRadius.Fixed -> with(density) { radius.dp.toPx() }
            is ClipRadius.Percentage -> min(size.width, size.height) * (radius.percent / 100f)
            ClipRadius.ClosestSide -> min(
                min(centerX, size.width - centerX),
                min(centerY, size.height - centerY)
            )
            ClipRadius.FarthestSide -> maxOf(
                maxOf(centerX, size.width - centerX),
                maxOf(centerY, size.height - centerY)
            )
        }
    }

    /**
     * Create a Compose Shape for ellipse clip-path.
     */
    private fun createEllipseShape(ellipse: ClipShape.Ellipse): Shape {
        return object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                val centerX = size.width * (ellipse.centerX / 100f)
                val centerY = size.height * (ellipse.centerY / 100f)

                val radiusX = resolveEllipseRadius(
                    ellipse.radiusX,
                    size,
                    centerX,
                    centerY,
                    density,
                    isHorizontal = true
                )
                val radiusY = resolveEllipseRadius(
                    ellipse.radiusY,
                    size,
                    centerX,
                    centerY,
                    density,
                    isHorizontal = false
                )

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
     * Resolve ellipse radius based on radius type, axis, and element size.
     */
    private fun resolveEllipseRadius(
        radius: ClipRadius,
        size: Size,
        centerX: Float,
        centerY: Float,
        density: Density,
        isHorizontal: Boolean
    ): Float {
        return when (radius) {
            is ClipRadius.Fixed -> with(density) { radius.dp.toPx() }
            is ClipRadius.Percentage -> {
                if (isHorizontal) size.width * (radius.percent / 100f)
                else size.height * (radius.percent / 100f)
            }
            ClipRadius.ClosestSide -> {
                if (isHorizontal) min(centerX, size.width - centerX)
                else min(centerY, size.height - centerY)
            }
            ClipRadius.FarthestSide -> {
                if (isHorizontal) maxOf(centerX, size.width - centerX)
                else maxOf(centerY, size.height - centerY)
            }
        }
    }

    /**
     * Create a Compose Shape for inset clip-path.
     */
    private fun createInsetShape(inset: ClipShape.Inset): Shape {
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
                val absRadius = with(density) { inset.borderRadius.toPx() }
                // CSS percentage radius resolves to width × fraction on
                // the X axis and height × fraction on the Y axis (per
                // CSS Backgrounds 3 §5.2). For non-square boxes that
                // produces an ellipse on each corner, which is what
                // `clip-path: inset(0 round 50%)` looks like in browsers.
                val rx = absRadius + (inset.borderRadiusFraction?.let { size.width * it } ?: 0f)
                val ry = absRadius + (inset.borderRadiusFraction?.let { size.height * it } ?: 0f)

                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(
                                left = left,
                                top = top,
                                right = size.width - right,
                                bottom = size.height - bottom
                            ),
                            radiusX = rx,
                            radiusY = ry
                        )
                    )
                }

                return Outline.Generic(path)
            }
        }
    }

    /**
     * Legacy CSS 2.1 `clip: rect(t,r,b,l)` — values are absolute
     * coordinates from the box top-left, NOT inset distances. `null`
     * on any axis means CSS `auto`: 0 for top/left, full extent for
     * bottom/right (CSS 2.1 §11.1.2).
     */
    private fun createLegacyRectShape(rect: ClipShape.LegacyRect): Shape {
        return object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                val t = rect.top?.let { with(density) { it.toPx() } } ?: 0f
                val l = rect.left?.let { with(density) { it.toPx() } } ?: 0f
                val r = rect.right?.let { with(density) { it.toPx() } } ?: size.width
                val b = rect.bottom?.let { with(density) { it.toPx() } } ?: size.height
                return Outline.Rectangle(Rect(left = l, top = t,
                                              right = r.coerceAtLeast(l),
                                              bottom = b.coerceAtLeast(t)))
            }
        }
    }

    /**
     * Create a Compose Shape for polygon clip-path.
     *
     * Per axis the vertex value is either a [ClipShape.PolygonAxis.Percent]
     * (legacy form, % of box dimension) or a [ClipShape.PolygonAxis.Length]
     * (CSS Shapes 2 `<length-percentage>` length form, absolute Dp).
     * `resolveAxis` picks the right path for each axis independently — a
     * vertex like `(100px, 50%)` is fully supported.
     */
    private fun createPolygonShape(polygon: ClipShape.Polygon): Shape {
        return object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                // Resolve a PolygonAxis to a concrete pixel coordinate
                // within the rendered box. `extent` is the relevant
                // dimension (width for x, height for y).
                fun resolveAxis(axis: ClipShape.PolygonAxis, extent: Float): Float =
                    when (axis) {
                        is ClipShape.PolygonAxis.Percent -> extent * (axis.value / 100f)
                        is ClipShape.PolygonAxis.Length -> with(density) { axis.dp.toPx() }
                    }

                val path = Path().apply {
                    if (polygon.points.isNotEmpty()) {
                        val first = polygon.points.first()
                        moveTo(
                            resolveAxis(first.x, size.width),
                            resolveAxis(first.y, size.height)
                        )
                        polygon.points.drop(1).forEach { pt ->
                            lineTo(
                                resolveAxis(pt.x, size.width),
                                resolveAxis(pt.y, size.height)
                            )
                        }
                        close()
                    }
                }

                return Outline.Generic(path)
            }
        }
    }

    /**
     * Create a Compose Shape for SVG path clip-path.
     *
     * Parses SVG path data string (d attribute) into a Compose Path.
     * Supports all standard SVG path commands: M, L, H, V, C, S, Q, T, A, Z.
     */
    private fun createPathShape(pathShape: ClipShape.Path): Shape {
        return object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                // Parse the SVG path data
                val parsedPath = SvgPathParser.parse(pathShape.svgPath)

                if (parsedPath != null) {
                    // Scale the path to fit the element size if needed
                    // SVG paths are often defined in a viewBox coordinate system
                    return Outline.Generic(parsedPath)
                }

                // Fallback: return full rectangle (no clipping)
                val fallbackPath = Path().apply {
                    addRect(Rect(0f, 0f, size.width, size.height))
                }
                return Outline.Generic(fallbackPath)
            }
        }
    }

    /**
     * Create a clip shape directly from configuration.
     *
     * Useful when you need the Shape without applying it to a modifier.
     *
     * @param config The clip-path configuration.
     * @return The Compose Shape, or null if no clip path is configured.
     */
    fun createShape(config: ClipPathConfig): Shape? {
        val shape = config.shape ?: return null

        return when (shape) {
            is ClipShape.Circle -> createCircleShape(shape)
            is ClipShape.Ellipse -> createEllipseShape(shape)
            is ClipShape.Inset -> createInsetShape(shape)
            is ClipShape.Polygon -> createPolygonShape(shape)
            is ClipShape.Path -> createPathShape(shape)
            is ClipShape.LegacyRect -> createLegacyRectShape(shape)
        }
    }
}
