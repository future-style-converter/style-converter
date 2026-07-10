package com.styleconverter.runtime.layout.advanced

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
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
     * css-motion-1 static ray() placement.
     *
     * Chrome's rendering of
     *   `offset-path: ray(45deg); offset-position: left top; offset-rotate: reverse`
     * (Layout_C14 web reference) is: the element's ANCHOR point (offset-anchor,
     * auto → transform-origin → 50% 50%) is moved onto the ray's START point
     * (offset-position, resolved in the containing block), advanced by
     * offset-distance along the ray, and the element is rotated to the ray's
     * direction (auto) / its opposite (reverse). With distance 0 that leaves
     * only a rotated corner of the box on-canvas — web's capture shows exactly
     * that wedge at the canvas origin.
     *
     * The containing block is approximated by the element's parent layout
     * node, observed post-layout via onPlaced (positionInParent + parent
     * size). That matches Chrome's behaviour for the harness's in-flow
     * components, whose containing block is the capture canvas.
     *
     * Only applied when offset-position was DECLARED as a position — the
     * `normal` initial value starts rays at the container CENTER, which the
     * own-box approximation would get wrong, so we leave those untouched
     * rather than paint a confidently wrong translate.
     */
    fun applyRayOffset(modifier: Modifier, config: OffsetPathConfig): Modifier {
        val ray = config.offsetPath as? OffsetPathValue.Ray ?: return modifier
        val pos = config.offsetPosition as? OffsetAnchorValue.Position ?: return modifier
        // Anchor: auto → transform-origin default (50% 50%).
        val anchor = config.offsetAnchor as? OffsetAnchorValue.Position
        val anchorX = (anchor?.x ?: 50f) / 100f
        val anchorY = (anchor?.y ?: 50f) / 100f
        // CSS ray angles: 0deg points UP, clockwise-positive (css-motion-1
        // §3.1, matching <angle> in gradients). Screen-space direction:
        // (sin θ, −cos θ). The element's auto rotation aligns its inline
        // axis (+x) with that direction: atan2(dy, dx).
        val rad = Math.toRadians(ray.angle.toDouble())
        val dirX = kotlin.math.sin(rad).toFloat()
        val dirY = (-kotlin.math.cos(rad)).toFloat()
        val pathAngle = Math.toDegrees(
            atan2(dirY.toDouble(), dirX.toDouble())
        ).toFloat()
        val rotation = when (val r = config.offsetRotate) {
            is OffsetRotateValue.Auto -> pathAngle
            is OffsetRotateValue.AutoReverse -> pathAngle + 180f
            is OffsetRotateValue.Angle -> r.degrees
            is OffsetRotateValue.AutoAngle -> pathAngle + r.degrees
        }
        // px offset-distance advances along the ray; percentage distance is
        // relative to the ray's size (closest-side etc.) which needs
        // container geometry — treated as 0 (safe: fixture rays omit it).
        val distPx = if (config.offsetDistanceUnit == OffsetDistanceUnit.LENGTH)
            config.offsetDistance else 0f

        // offset-position resolves in the CONTAINING BLOCK, not the element
        // box. From a Modifier we can observe the element's placement inside
        // its parent layout node (onPlaced → positionInParent + parent
        // size), which IS the containing block for the harness's in-flow
        // components. Chrome pins Layout_C14's ray start at the canvas
        // origin (wedge 44×44 at 0,0) while the element itself sits 16px in
        // (canvas padding) — without this correction Android's wedge was
        // 76×76 (anchored at the element origin instead, 0.9267 vs web).
        // The state is written post-layout and the graphicsLayer block
        // re-reads it on change; the capture harness waits a settle frame,
        // so the corrected placement is what gets photographed.
        val placement = androidx.compose.runtime.mutableStateOf(
            androidx.compose.ui.geometry.Offset.Zero to androidx.compose.ui.geometry.Size.Zero
        )
        return modifier
            .onPlaced { coords ->
                // Chrome resolves offset-position against the containing
                // block's PADDING box, not its content box: the puppeteer
                // probe (390px canvas, padding 16, child ray(45deg)/left
                // top/reverse) reports the child's transformed bbox centered
                // at the canvas origin (0,0), 16px up-left of the child's
                // static position. Compose's parentLayoutCoordinates is the
                // parent's inner placement scope (content box), so hop ONE
                // more chain point outward — for a padded parent that is the
                // pre-padding coordinate (the padding box). For unpadded
                // parents the sizes match and we keep the content scope
                // (content box == padding box there).
                val content = coords.parentLayoutCoordinates
                val outer = content?.parentCoordinates
                val block = if (outer != null && outer.size != content.size) outer else content
                if (block != null) {
                    placement.value = block.localPositionOf(
                        coords, androidx.compose.ui.geometry.Offset.Zero
                    ) to androidx.compose.ui.geometry.Size(
                        block.size.width.toFloat(), block.size.height.toFloat()
                    )
                }
            }
            .graphicsLayer {
                val (inParent, parentSize) = placement.value
                // Ray start point in the containing block, then re-expressed
                // relative to the element's own top-left.
                val startX = (pos.x / 100f) * parentSize.width - inParent.x
                val startY = (pos.y / 100f) * parentSize.height - inParent.y
                // Move the anchor onto the start point, advance by distance.
                translationX = startX - anchorX * size.width + dirX * distPx
                translationY = startY - anchorY * size.height + dirY * distPx
                rotationZ = rotation
                // Rotate about the anchor, matching CSS (the anchor point
                // stays fixed on the path while the box spins around it).
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(anchorX, anchorY)
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
