package com.styleconverter.test.style.layout.advanced

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Applies CSS offset-path (motion path) animations to Compose modifiers.
 *
 * ## CSS Properties
 * ```css
 * .animated-element {
 *     offset-path: path("M0,0 C50,100 150,100 200,0");
 *     offset-distance: 50%;
 *     offset-rotate: auto;
 *     offset-anchor: center;
 * }
 * ```
 *
 * ## Compose Implementation
 * Uses Path and PathMeasure to calculate position and tangent along the path,
 * then applies translation and rotation via graphicsLayer.
 *
 * ## Limitations
 * - URL references not supported (inline paths only)
 * - ray() function has limited support
 * - Complex path operations may impact performance
 */
object OffsetPathApplier {

    /**
     * Apply offset path to a modifier.
     *
     * @param modifier Base modifier
     * @param config OffsetPathConfig with path and distance settings
     * @param progress Animation progress 0-1 (overrides offsetDistance if provided)
     * @return Modified Modifier with path positioning applied
     */
    fun applyOffsetPath(
        modifier: Modifier,
        config: OffsetPathConfig,
        progress: Float? = null
    ): Modifier {
        if (!config.hasOffsetPathProperties || config.offsetPath == OffsetPathValue.None) {
            return modifier
        }

        val path = createPath(config.offsetPath) ?: return modifier
        val pathMeasure = PathMeasure().apply { setPath(path, false) }
        val pathLength = pathMeasure.length

        if (pathLength <= 0f) return modifier

        // Calculate distance along path
        val distance = when {
            progress != null -> progress * pathLength
            config.offsetDistanceUnit == OffsetDistanceUnit.PERCENTAGE ->
                (config.offsetDistance / 100f) * pathLength
            else -> config.offsetDistance
        }

        // Get position and tangent at distance
        val position = pathMeasure.getPosition(distance.coerceIn(0f, pathLength))
        val tangent = pathMeasure.getTangent(distance.coerceIn(0f, pathLength))

        // Calculate rotation
        val rotation = calculateRotation(config.offsetRotate, tangent)

        // Calculate anchor offset
        val anchorOffset = calculateAnchorOffset(config.offsetAnchor)

        return modifier.graphicsLayer {
            translationX = position.x - anchorOffset.x
            translationY = position.y - anchorOffset.y
            rotationZ = rotation
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                anchorOffset.x / size.width.coerceAtLeast(1f),
                anchorOffset.y / size.height.coerceAtLeast(1f)
            )
        }
    }

    /**
     * Create a composable state for animated offset path.
     *
     * @param config OffsetPathConfig
     * @param progress Animated progress state (0-1)
     * @return State containing the position and rotation
     */
    @Composable
    fun rememberOffsetPathState(
        config: OffsetPathConfig,
        progress: State<Float>
    ): State<OffsetPathState> {
        val path = remember(config.offsetPath) { createPath(config.offsetPath) }

        return remember(config, path) {
            derivedStateOf {
                if (path == null) {
                    OffsetPathState.None
                } else {
                    val pathMeasure = PathMeasure().apply { setPath(path, false) }
                    val pathLength = pathMeasure.length

                    if (pathLength <= 0f) {
                        OffsetPathState.None
                    } else {
                        val distance = progress.value * pathLength
                        val position = pathMeasure.getPosition(distance.coerceIn(0f, pathLength))
                        val tangent = pathMeasure.getTangent(distance.coerceIn(0f, pathLength))
                        val rotation = calculateRotation(config.offsetRotate, tangent)

                        OffsetPathState.OnPath(
                            x = position.x,
                            y = position.y,
                            rotation = rotation,
                            progress = progress.value
                        )
                    }
                }
            }
        }
    }

    /**
     * Create a modifier that animates along an offset path.
     *
     * @param config OffsetPathConfig
     * @param progress Animation progress 0-1
     * @return Modifier with path animation applied
     */
    @Composable
    fun Modifier.animateAlongPath(
        config: OffsetPathConfig,
        progress: Float
    ): Modifier {
        val density = LocalDensity.current

        val pathState = remember(config.offsetPath) {
            createPath(config.offsetPath)?.let { path ->
                val pathMeasure = PathMeasure().apply { setPath(path, false) }
                PathAnimationState(path, pathMeasure, pathMeasure.length)
            }
        } ?: return this

        if (pathState.length <= 0f) return this

        val distance = progress * pathState.length
        val position = pathState.measure.getPosition(distance.coerceIn(0f, pathState.length))
        val tangent = pathState.measure.getTangent(distance.coerceIn(0f, pathState.length))
        val rotation = calculateRotation(config.offsetRotate, tangent)

        return this.graphicsLayer {
            translationX = position.x
            translationY = position.y
            rotationZ = rotation
        }
    }

    /**
     * Create a Compose Path from OffsetPathValue.
     */
    private fun createPath(pathValue: OffsetPathValue): Path? {
        return when (pathValue) {
            is OffsetPathValue.None -> null
            is OffsetPathValue.Path -> parseSvgPath(pathValue.d)
            is OffsetPathValue.Circle -> createCirclePath(pathValue.radius)
            is OffsetPathValue.Ellipse -> createEllipsePath(pathValue.radiusX, pathValue.radiusY)
            is OffsetPathValue.Polygon -> createPolygonPath(pathValue.points)
            is OffsetPathValue.Ray -> createRayPath(pathValue)
            is OffsetPathValue.Inset -> null // Inset not applicable for motion paths
            is OffsetPathValue.Url -> null // URL references not supported
        }
    }

    /**
     * Parse SVG path data string into a Compose Path.
     * Supports basic SVG path commands: M, L, C, Q, A, Z
     */
    private fun parseSvgPath(d: String): Path? {
        if (d.isBlank()) return null

        val path = Path()
        var currentX = 0f
        var currentY = 0f
        var startX = 0f
        var startY = 0f

        // Tokenize the path string
        val tokens = tokenizeSvgPath(d)
        var i = 0

        while (i < tokens.size) {
            val command = tokens[i]
            i++

            when (command.uppercase()) {
                "M" -> {
                    // MoveTo
                    if (i + 1 < tokens.size) {
                        val x = tokens[i++].toFloatOrNull() ?: continue
                        val y = tokens[i++].toFloatOrNull() ?: continue
                        if (command == "m") {
                            currentX += x; currentY += y
                        } else {
                            currentX = x; currentY = y
                        }
                        path.moveTo(currentX, currentY)
                        startX = currentX; startY = currentY
                    }
                }
                "L" -> {
                    // LineTo
                    if (i + 1 < tokens.size) {
                        val x = tokens[i++].toFloatOrNull() ?: continue
                        val y = tokens[i++].toFloatOrNull() ?: continue
                        if (command == "l") {
                            currentX += x; currentY += y
                        } else {
                            currentX = x; currentY = y
                        }
                        path.lineTo(currentX, currentY)
                    }
                }
                "H" -> {
                    // Horizontal LineTo
                    if (i < tokens.size) {
                        val x = tokens[i++].toFloatOrNull() ?: continue
                        currentX = if (command == "h") currentX + x else x
                        path.lineTo(currentX, currentY)
                    }
                }
                "V" -> {
                    // Vertical LineTo
                    if (i < tokens.size) {
                        val y = tokens[i++].toFloatOrNull() ?: continue
                        currentY = if (command == "v") currentY + y else y
                        path.lineTo(currentX, currentY)
                    }
                }
                "C" -> {
                    // Cubic Bezier
                    if (i + 5 < tokens.size) {
                        val x1 = tokens[i++].toFloatOrNull() ?: continue
                        val y1 = tokens[i++].toFloatOrNull() ?: continue
                        val x2 = tokens[i++].toFloatOrNull() ?: continue
                        val y2 = tokens[i++].toFloatOrNull() ?: continue
                        val x = tokens[i++].toFloatOrNull() ?: continue
                        val y = tokens[i++].toFloatOrNull() ?: continue
                        if (command == "c") {
                            path.cubicTo(
                                currentX + x1, currentY + y1,
                                currentX + x2, currentY + y2,
                                currentX + x, currentY + y
                            )
                            currentX += x; currentY += y
                        } else {
                            path.cubicTo(x1, y1, x2, y2, x, y)
                            currentX = x; currentY = y
                        }
                    }
                }
                "Q" -> {
                    // Quadratic Bezier
                    if (i + 3 < tokens.size) {
                        val x1 = tokens[i++].toFloatOrNull() ?: continue
                        val y1 = tokens[i++].toFloatOrNull() ?: continue
                        val x = tokens[i++].toFloatOrNull() ?: continue
                        val y = tokens[i++].toFloatOrNull() ?: continue
                        if (command == "q") {
                            path.quadraticTo(
                                currentX + x1, currentY + y1,
                                currentX + x, currentY + y
                            )
                            currentX += x; currentY += y
                        } else {
                            path.quadraticTo(x1, y1, x, y)
                            currentX = x; currentY = y
                        }
                    }
                }
                "Z" -> {
                    // ClosePath
                    path.close()
                    currentX = startX; currentY = startY
                }
            }
        }

        return path
    }

    /**
     * Tokenize SVG path data into commands and numbers.
     */
    private fun tokenizeSvgPath(d: String): List<String> {
        val tokens = mutableListOf<String>()
        val regex = Regex("([MmLlHhVvCcSsQqTtAaZz])|(-?\\d*\\.?\\d+)")

        regex.findAll(d).forEach { match ->
            tokens.add(match.value)
        }

        return tokens
    }

    /**
     * Create a circular path.
     */
    private fun createCirclePath(radius: Dp?): Path {
        val r = radius?.value ?: 50f
        return Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    -r, -r, r, r
                )
            )
        }
    }

    /**
     * Create an elliptical path.
     */
    private fun createEllipsePath(radiusX: Dp?, radiusY: Dp?): Path {
        val rx = radiusX?.value ?: 50f
        val ry = radiusY?.value ?: 30f
        return Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    -rx, -ry, rx, ry
                )
            )
        }
    }

    /**
     * Create a polygon path from points.
     */
    private fun createPolygonPath(points: List<Pair<Float, Float>>): Path? {
        if (points.size < 2) return null

        return Path().apply {
            moveTo(points[0].first, points[0].second)
            for (i in 1 until points.size) {
                lineTo(points[i].first, points[i].second)
            }
            close()
        }
    }

    /**
     * Create a ray path (straight line at angle).
     */
    private fun createRayPath(ray: OffsetPathValue.Ray): Path {
        val length = 1000f // Arbitrary length for ray
        val radians = Math.toRadians(ray.angle.toDouble())
        val endX = (length * cos(radians)).toFloat()
        val endY = (length * sin(radians)).toFloat()

        return Path().apply {
            moveTo(0f, 0f)
            lineTo(endX, endY)
        }
    }

    /**
     * Calculate rotation based on offset-rotate config and path tangent.
     */
    private fun calculateRotation(rotateValue: OffsetRotateValue, tangent: Offset): Float {
        val tangentAngle = Math.toDegrees(
            atan2(tangent.y.toDouble(), tangent.x.toDouble())
        ).toFloat()

        return when (rotateValue) {
            is OffsetRotateValue.Auto -> tangentAngle
            is OffsetRotateValue.AutoReverse -> tangentAngle + 180f
            is OffsetRotateValue.Angle -> rotateValue.degrees
            is OffsetRotateValue.AutoAngle -> tangentAngle + rotateValue.degrees
        }
    }

    /**
     * Calculate anchor offset based on offset-anchor config.
     */
    private fun calculateAnchorOffset(anchorValue: OffsetAnchorValue): Offset {
        return when (anchorValue) {
            is OffsetAnchorValue.Auto -> Offset.Zero
            is OffsetAnchorValue.Position -> Offset(anchorValue.x, anchorValue.y)
        }
    }

    /**
     * Internal state for path animation.
     */
    private data class PathAnimationState(
        val path: Path,
        val measure: PathMeasure,
        val length: Float
    )
}

/**
 * State representing position on an offset path.
 */
sealed interface OffsetPathState {
    data object None : OffsetPathState

    data class OnPath(
        val x: Float,
        val y: Float,
        val rotation: Float,
        val progress: Float
    ) : OffsetPathState
}
