package com.styleconverter.runtime.transforms

import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset      // retro R1: the abspos pivot shift rides in as a DpOffset (PositionApplier.resolvedOffset)
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.tan

/**
 * Applies transform configuration to Compose Modifiers.
 *
 * ## Simple vs Complex Transforms
 *
 * Simple transforms (translate, rotate, scale without custom origin or skew)
 * can use basic Modifiers:
 * - Modifier.offset() for translate
 * - Modifier.rotate() for rotation
 * - Modifier.scale() for scaling
 *
 * Complex transforms (skew, custom origin, 3D, combined) use graphicsLayer:
 * - graphicsLayer { translationX/Y, rotationZ, scaleX/Y, transformOrigin }
 *
 * ## Skew Transform Implementation
 * Skew is implemented using drawWithContent with Canvas.skew() since
 * graphicsLayer doesn't support skew directly. The skew values are converted
 * from degrees to radians for the Canvas API.
 *
 * ## Matrix Transform Implementation
 * CSS matrix(a, b, c, d, tx, ty) is decomposed into scale, rotation, skew,
 * and translation components and applied separately.
 *
 * ## Order of Operations
 * CSS transforms are applied in order (left to right in the transform string).
 * Compose graphicsLayer applies in order: scale -> rotate -> translate.
 * For exact CSS matching, complex transforms use custom Canvas rendering.
 *
 * ## Limitations
 * - 3D transforms (rotateX/Y, translateZ) have limited Compose support
 * - A `perspective()` FUNCTION meeting a 3D rotation takes the exact 4x4
 *   canvas route (TransformMatrixPathApplier); graphicsLayer's cameraDistance
 *   is only ever set on the depth-without-rotation shape, where its uniform
 *   scale is the exact P/(P − z).
 * - The own `perspective` PROPERTY is the CHILDREN's (css-transforms-2 §8;
 *   §4.1.1 second way) and — since retro F3 — is applied by NO route here;
 *   the children channel is unimplemented (OrthographicFlatten names it).
 */
object TransformApplier {

    /**
     * Apply transform configuration to a Modifier.
     *
     * Chooses between simple modifiers and graphicsLayer based on
     * the complexity of the transform.
     *
     * @param modifier The base modifier to extend
     * @param config The transform configuration to apply
     * @param pivotShift retro R1 (A11#0): the position offset the layout step
     *   applies INSIDE this node (PositionApplier.resolvedOffset — the value
     *   form of the `absoluteOffset` StyleApplier chains at step 4, inner of
     *   this step-2 node). Every route below conjugates its pivot by it, so a
     *   positioned element rotates/scales about ITS OWN centre instead of its
     *   local centre expressed in the parent frame (TransformPivot has the
     *   measurements). Zero — the default, and every unpositioned element —
     *   leaves every route byte-identical to wave 49.
     * @return Modified Modifier with transforms applied
     */
    fun applyTransforms(modifier: Modifier, config: TransformConfig, pivotShift: DpOffset = DpOffset.Zero): Modifier {
        if (!config.hasTransform) return modifier

        // retro R1 (A11#6 / A10#1 / A11#11): the exact 4x4 canvas route claims
        // the shapes every scalar route below approximates — a perspective in
        // effect with a 3D rotation, matrix3d, a diagonal rotate3d axis, an X
        // rotation meeting a Y rotation, skew or scaleZ under a 3D rotation.
        // TransformMatrixComposer.takesMatrixPath is the single gate (and its
        // class doc the routing contract); everything it refuses keeps its
        // wave-48/49 route so the corpus baselines stay byte-stable.
        if (TransformMatrixComposer.takesMatrixPath(config)) {
            return TransformMatrixPathApplier.apply(modifier, config, pivotShift)
        }

        // Check if we have skew transforms - these need special handling
        if (config.hasSkew) {
            return applyTransformsWithSkew(modifier, config, pivotShift)
        }

        // Check if we have matrix transforms - these need decomposition.
        // (Matrix3d never reaches here any more — takesMatrixPath claims it;
        // kept in the predicate so a future gate change cannot silently send
        // a 4x4 literal through the 2D decomposition that used to drop it.)
        val hasMatrix = config.functions.any { it is TransformFunction.Matrix || it is TransformFunction.Matrix3d }
        if (hasMatrix) {
            return applyTransformsWithMatrix(modifier, config, pivotShift)
        }

        // If we have transform functions, use graphicsLayer for combined transforms
        return if (config.functions.isNotEmpty()) {
            // Wave 48 (lane W6) — compose the function list in CSS order.
            // css-transforms-1 §8: `transform: A B` is the matrix product
            // A·B, so B maps the point FIRST. The legacy path below
            // (applyTransformFunctions) accumulates each function KIND into
            // a separate scalar and feeds graphicsLayer's fixed
            // translate·rotate·scale order, which discards the list order —
            // `scale(2) translate(30px)` rendered identically to
            // `translate(30px) scale(2)` (the ledgered Transform_Combined
            // iOS-Android divergence; web measures centroid +60 vs
            // Android's +30 on the TLO probe). stepsFor returns non-null
            // only for pure-2D multi-member lists it can compose exactly;
            // everything else (3D, skew, matrix, single-function) keeps its
            // existing route, so the blast radius is exactly the
            // order-sensitive 2D lists. iOS's TransformsApplier composes in
            // declared order since 2026-08-28 — this is the Compose twin.
            val steps = TransformListComposer.stepsFor(config)
            if (steps != null) {
                // The linear part (rotations/scales only) is density-free,
                // so the graphicsLayer-vs-canvas decision happens here at
                // construction time, before size/density exist.
                val fields = TransformListComposer.decomposeRotateScale(
                    TransformListComposer.linearOf(steps),
                )
                if (fields != null) {
                    // Expressible as rotate·scale — keep the graphicsLayer
                    // pipeline (same rasterisation path as the legacy route,
                    // so commuting lists keep their exact pixel texture).
                    applyOrderedViaGraphicsLayer(modifier, config, steps, fields, pivotShift)
                } else {
                    // Shear residue (e.g. scaleX(2) rotate(45deg) = S·R,
                    // not expressible as R·S) — draw the exact matrix via
                    // ordered canvas ops, the same mechanism the skew path
                    // already uses for shear.
                    applyOrderedViaCanvas(modifier, config, steps, pivotShift)
                }
            } else {
                applyTransformFunctions(modifier, config, pivotShift)
            }
        } else if (!config.isSimpleTransform || config.hasCustomOrigin ||
            // retro R1 (A11#0): Modifier.rotate/scale pivot at the node's own
            // centre and cannot be told about the abspos shift, so a
            // positioned element whose longhands rotate or scale must take
            // the graphicsLayer route, where the pivot is conjugated. A
            // translate-only longhand is pivot-independent and keeps
            // Modifier.offset (the standalone 005_box in the finding was
            // scaleX(2) at left 60: x 100–180 drawn, 40–119 correct).
            (pivotShift != DpOffset.Zero && (config.rotate != null || config.hasScale))
        ) {
            // Use graphicsLayer for complex standalone transforms
            applyWithGraphicsLayer(modifier, config, pivotShift)
        } else {
            // Use simple modifiers for basic transforms
            applySimpleTransforms(modifier, config)
        }
    }

    // retro R1 (A11#6): the 1000px DEFAULT_PERSPECTIVE that every route fell
    // back to is GONE. css-transforms-2 §4.1: with no perspective matrix in
    // the accumulation the z row and column are dropped, so translateZ is
    // invisible — a default camera had no basis in the spec and drew
    // pairs-04 `translate: 10px 20px 30px` + `perspective: none` at 184x93
    // for the correct 180x92. TransformMatrixComposer.depthScale returns 1
    // whenever no perspective is in effect.

    /**
     * Apply transforms that include skew using Canvas transformations.
     * This provides true skew support that graphicsLayer cannot offer.
     *
     * retro R1: [pivotShift] conjugates the pivot (A11#0); the depth scale
     * comes from the shared P/(P − z) owner (A10#1 — this route carried the
     * Taylor form `1 + z/P` in its no-`perspective()` branch and the INVERTED
     * `P/(P + z)` in its `perspective()` branch, so `perspective(500px)
     * translateZ(250px) skewX(20deg)` drew 1.5x / 0.667x for the correct 2.0x).
     * A skew meeting a 3D rotation or matrix3d never arrives here any more
     * (takesMatrixPath's `skew3D`), so the cos·scaleZ blocks below are
     * unreachable-by-construction and left as they were.
     */
    private fun applyTransformsWithSkew(modifier: Modifier, config: TransformConfig, pivotShift: DpOffset): Modifier {
        // Collect all transform values
        var translateX = 0f
        var translateY = 0f
        var translateZ = 0f
        var rotation = 0f
        var scaleX = 1f
        var scaleY = 1f
        var scaleZ = 1f
        var skewX = 0f
        var skewY = 0f
        var rotationX = 0f
        var rotationY = 0f
        var cameraDistance = 0f

        // Process transform functions
        config.functions.forEach { fn ->
            when (fn) {
                is TransformFunction.Translate -> {
                    // Dp will be converted in drawWithContent
                    translateX += fn.x.value
                    translateY += fn.y.value
                    translateZ += fn.z.value
                }
                is TransformFunction.TranslateX -> translateX += fn.x.value
                is TransformFunction.TranslateY -> translateY += fn.y.value
                is TransformFunction.TranslateZ -> translateZ += fn.z.value
                is TransformFunction.Rotate -> rotation += fn.degrees
                is TransformFunction.RotateX -> rotationX += fn.degrees
                is TransformFunction.RotateY -> rotationY += fn.degrees
                is TransformFunction.RotateZ -> rotation += fn.degrees
                is TransformFunction.Scale -> {
                    scaleX *= fn.x
                    scaleY *= fn.y
                    scaleZ *= fn.z
                }
                is TransformFunction.ScaleX -> scaleX *= fn.x
                is TransformFunction.ScaleY -> scaleY *= fn.y
                is TransformFunction.ScaleZ -> scaleZ *= fn.z
                is TransformFunction.Skew -> {
                    skewX += fn.xDegrees
                    skewY += fn.yDegrees
                }
                is TransformFunction.SkewX -> skewX += fn.degrees
                is TransformFunction.SkewY -> skewY += fn.degrees
                is TransformFunction.Perspective -> {
                    cameraDistance = fn.distance.value
                }
                is TransformFunction.Matrix -> {
                    // Decompose matrix and add to transforms
                    val decomposed = decomposeMatrix2D(fn.values)
                    translateX += decomposed.translateX
                    translateY += decomposed.translateY
                    rotation += decomposed.rotation
                    scaleX *= decomposed.scaleX
                    scaleY *= decomposed.scaleY
                    skewX += decomposed.skewX
                }
                is TransformFunction.Matrix3d -> { /* Complex - skip for now */ }
                // retro R1: a diagonal-axis rotate3d is claimed by the 4x4 route
                // before this function is reached (takesMatrixPath). If a gate
                // change ever lets one through, say so rather than drop it —
                // the scalar accumulator has no exact representation for it.
                is TransformFunction.Rotate3d -> com.styleconverter.runtime.PropertyTracker.markUnhandled("Transform")
                is TransformFunction.None -> {
                    translateX = 0f
                    translateY = 0f
                    translateZ = 0f
                    rotation = 0f
                    scaleX = 1f
                    scaleY = 1f
                    scaleZ = 1f
                    skewX = 0f
                    skewY = 0f
                }
            }
        }

        // Add standalone properties
        config.translateX?.let { translateX += it.value }
        config.translateY?.let { translateY += it.value }
        config.translateZ?.let { translateZ += it.value }
        config.rotate?.let { rotation += it }
        config.rotateX?.let { rotationX += it }
        config.rotateY?.let { rotationY += it }
        config.scaleX?.let { scaleX *= it }
        config.scaleY?.let { scaleY *= it }
        config.scaleZ?.let { scaleZ *= it }
        config.scale?.let { scale ->
            scaleX *= scale
            scaleY *= scale
        }
        config.skewX?.let { skewX += it }
        config.skewY?.let { skewY += it }
        // retro F3: the own `perspective` PROPERTY is NOT read on this route
        // (or any other). It is css-transforms-2 §4.1.1's second way — the
        // CHILDREN's perspective (§8) — while only a perspective() FUNCTION
        // in this element's own list (§12.2, the branch above) puts a camera
        // in front of ITS content. The wave-1 code overrode the function's
        // distance with the property unconditionally; R1 narrowed that to a
        // depth-bearing-list fold (`ownPerspectiveFolds`); both are gone —
        // TransformMatrixComposer's class doc has the measured reason.

        // Convert skew degrees to radians for tan()
        val skewXRad = Math.toRadians(skewX.toDouble()).toFloat()
        val skewYRad = Math.toRadians(skewY.toDouble()).toFloat()

        val originX = config.originX
        val originY = config.originY

        // Apply transforms using drawWithContent for skew support
        return modifier.drawWithContent {
            val density = this.density
            // Transform-origin pivot: LENGTH origins (transform-origin: 30px
            // 25%) ride in originXDp/originYDp and must resolve to absolute
            // px, exactly like the graphicsLayer paths already do (see
            // applyTransformFunctions). This canvas path previously used
            // ONLY the fractional origin, so a `skewY(20deg)` with
            // `transform-origin: 30px 25%` anchored at the box CENTER
            // (0.5 default) instead of x=30px — PW_Color_Transforms_01 sat
            // at A-w 0.771 with iOS agreeing on the same wrong pivot.
            // Per-axis fallback matches css-transforms-1 §4 (each axis
            // resolves independently: px x-axis + % y-axis is legal).
            // retro R1 (A11#0): conjugated by the abspos shift — TransformPivot.
            val pivotX = TransformPivot.axisPx(config.originXDp?.value, density, originX, size.width, pivotShift.x.toPx())
            val pivotY = TransformPivot.axisPx(config.originYDp?.value, density, originY, size.height, pivotShift.y.toPx())

            // Convert Dp values to pixels
            val txPx = translateX * density
            val tyPx = translateY * density
            val tzPx = translateZ * density

            // Perspective in effect, device px — 0 when none (retro R1: no
            // 1000px default; §4.1 drops the z column without a perspective).
            val perspectivePx = if (cameraDistance > 0) cameraDistance * density else 0f

            // retro R1 (A10#1): the ONE depth-scale owner — css-transforms-2
            // §16's perspective matrix gives w = 1 − z/P, i.e. a uniform
            // P/(P − z) (2.0 at z = P/2), replacing the Taylor `1 + z/P`
            // (1.5) and the inverted `P/(P + z)` (0.667) this route carried.
            val depthScale = TransformMatrixComposer.depthScale(perspectivePx, tzPx)

            drawContext.canvas.let { canvas ->
                canvas.save()

                // Move to transform origin
                canvas.translate(pivotX, pivotY)

                // Depth response of a flat element under the perspective in
                // effect — identity (skipped) when there is none.
                if (depthScale != 1f) {
                    canvas.scale(depthScale, depthScale)
                }

                // Apply 3D rotations if present (approximate with scale)
                // scaleZ affects how the element stretches during 3D rotation
                if (rotationX != 0f) {
                    val rx = Math.toRadians(rotationX.toDouble())
                    val cosRx = cos(rx).toFloat()
                    // scaleZ affects the Y-axis during X rotation
                    val effectiveScaleY = cosRx * scaleZ
                    canvas.scale(1f, abs(effectiveScaleY).coerceAtLeast(0.01f))
                }
                if (rotationY != 0f) {
                    val ry = Math.toRadians(rotationY.toDouble())
                    val cosRy = cos(ry).toFloat()
                    // scaleZ affects the X-axis during Y rotation
                    val effectiveScaleX = cosRy * scaleZ
                    canvas.scale(abs(effectiveScaleX).coerceAtLeast(0.01f), 1f)
                }

                // Apply scale (2D)
                canvas.scale(scaleX, scaleY)

                // Apply rotation
                canvas.rotate(rotation)

                // Apply skew
                if (skewX != 0f || skewY != 0f) {
                    canvas.skew(tan(skewXRad), tan(skewYRad))
                }

                // Move back from transform origin
                canvas.translate(-pivotX, -pivotY)

                // Apply translation
                canvas.translate(txPx, tyPx)

                // Draw content
                this@drawWithContent.drawContent()

                canvas.restore()
            }
        }
    }

    /**
     * Apply transforms that include matrix functions.
     */
    private fun applyTransformsWithMatrix(modifier: Modifier, config: TransformConfig, pivotShift: DpOffset): Modifier {
        // For matrix transforms, we decompose and apply as regular transforms
        // This uses the skew path since decomposed matrices often include skew
        // (retro R1: the pivot shift rides through unchanged — A11#0).
        return applyTransformsWithSkew(modifier, config, pivotShift)
    }

    /**
     * Decompose a 2D CSS matrix into component transforms.
     *
     * CSS matrix(a, b, c, d, tx, ty) where:
     * | a  c  tx |
     * | b  d  ty |
     * | 0  0  1  |
     */
    private fun decomposeMatrix2D(values: List<Float>): DecomposedMatrix {
        if (values.size < 6) {
            return DecomposedMatrix()
        }

        val a = values[0]
        val b = values[1]
        val c = values[2]
        val d = values[3]
        val tx = values[4]
        val ty = values[5]

        // Calculate scale
        var scaleX = kotlin.math.sqrt(a * a + b * b)
        var scaleY = kotlin.math.sqrt(c * c + d * d)

        // Check for negative scale (reflection)
        val det = a * d - b * c
        if (det < 0) {
            scaleX = -scaleX
        }

        // Calculate rotation
        val rotation = Math.toDegrees(kotlin.math.atan2(b.toDouble(), a.toDouble())).toFloat()

        // Calculate skew
        val skewX = Math.toDegrees(kotlin.math.atan2((a * c + b * d).toDouble(), (scaleX * scaleY).toDouble())).toFloat()

        return DecomposedMatrix(
            translateX = tx,
            translateY = ty,
            rotation = rotation,
            scaleX = scaleX,
            scaleY = scaleY,
            skewX = skewX
        )
    }

    /**
     * Decomposed matrix components.
     */
    private data class DecomposedMatrix(
        val translateX: Float = 0f,
        val translateY: Float = 0f,
        val rotation: Float = 0f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val skewX: Float = 0f
    )

    /**
     * Ordered-list route, graphicsLayer flavor (wave 48, lane W6).
     *
     * The list composed in CSS order has linear part R(θ)·S(sx,sy) — the
     * exact shape RenderNode expresses (it builds translate · pivot-rotate ·
     * pivot-scale; with the pivot parked at (0,0) that is T·R·S). The
     * transform-origin conjugation (css-transforms-1 §4) and every
     * translation are folded into the composed matrix's translation
     * component, so the layer's own pivot must NOT conjugate again —
     * transformOrigin is pinned to the top-left corner.
     */
    private fun applyOrderedViaGraphicsLayer(
        modifier: Modifier,
        config: TransformConfig,
        steps: List<TransformListComposer.Step>,
        fields: TransformListComposer.LayerFields,
        pivotShift: DpOffset,
    ): Modifier {
        return modifier.graphicsLayer {
            // Per-axis origin resolution, identical to the legacy paths:
            // a length origin (originXDp) resolves to absolute px, a
            // keyword/percentage origin to a fraction of the element's own
            // size (css-transforms-1 §4 — lengths reference the border box).
            // retro R1 (A11#0): conjugated by the abspos shift (TransformPivot).
            val pivX = TransformPivot.axisPx(config.originXDp?.value, density, config.originX, size.width, pivotShift.x.toPx())
            val pivY = TransformPivot.axisPx(config.originYDp?.value, density, config.originY, size.height, pivotShift.y.toPx())
            // Full ordered product in px (density and own-size fractions
            // resolve here, where GraphicsLayerScope provides both), then
            // the css-transforms-1 §4 origin conjugation.
            val f = TransformListComposer.conjugateByOrigin(
                TransformListComposer.affineOf(steps, density, size.width, size.height),
                pivX, pivY,
            )
            // Pivot at the top-left corner: rotation/scale then compose
            // about (0,0) and the conjugation above supplies the rest.
            transformOrigin = TransformOrigin(0f, 0f)
            // Translation component of the composed matrix (includes the
            // origin conjugation and every ordered translate).
            translationX = f.tx
            translationY = f.ty
            // Linear part, decomposed at construction time — exact by the
            // recomposition check in decomposeRotateScale.
            rotationZ = fields.rotationDeg
            scaleX = fields.scaleX
            scaleY = fields.scaleY
        }
    }

    /**
     * Ordered-list route, canvas flavor (wave 48, lane W6) — for composed
     * matrices graphicsLayer CANNOT express, i.e. a linear part with shear
     * residue such as `scaleX(2) rotate(45deg)` (= S·R; its columns are
     * not orthogonal, so no R·S decomposition exists — the TLO_ScaleXRotate
     * red case). Canvas ops concatenate exactly like the css-transforms-1 §8 matrix
     * product (each op maps the geometry drawn AFTER it, so applying steps
     * in declared order makes the last-listed function innermost), so
     * replaying the steps between the origin conjugation reproduces the
     * CSS matrix with no decomposition at all. Same drawWithContent
     * mechanism the skew path has always used for shear, so painted
     * overflow behaves identically.
     */
    private fun applyOrderedViaCanvas(
        modifier: Modifier,
        config: TransformConfig,
        steps: List<TransformListComposer.Step>,
        pivotShift: DpOffset,
    ): Modifier {
        return modifier.drawWithContent {
            // Same per-axis origin resolution as the graphicsLayer flavor,
            // abspos shift included (retro R1, A11#0 — TransformPivot).
            val pivX = TransformPivot.axisPx(config.originXDp?.value, density, config.originX, size.width, pivotShift.x.toPx())
            val pivY = TransformPivot.axisPx(config.originYDp?.value, density, config.originY, size.height, pivotShift.y.toPx())
            val canvas = drawContext.canvas
            canvas.save()
            // css-transforms-1 §4: translate to the origin…
            canvas.translate(pivX, pivY)
            // …apply the list in declared order (later ops are inner —
            // exactly css-transforms-1 §8's product where the rightmost function maps the
            // point first)…
            for (step in steps) {
                when (step) {
                    is TransformListComposer.Step.Translate -> canvas.translate(
                        // dp·density plus own-size percentage (§6: percentages
                        // resolve against the element's border box).
                        step.xDp * density + step.xFrac * size.width,
                        step.yDp * density + step.yFrac * size.height,
                    )
                    // Canvas.rotate is clockwise-positive like CSS (y-down).
                    is TransformListComposer.Step.Rotate -> canvas.rotate(step.deg)
                    is TransformListComposer.Step.Scale -> canvas.scale(step.sx, step.sy)
                }
            }
            // …and translate back (css-transforms-1 §4's closing -origin translation).
            canvas.translate(-pivX, -pivY)
            // Draw the node's actual content under the accumulated CTM.
            this@drawWithContent.drawContent()
            canvas.restore()
        }
    }

    /**
     * Apply transform functions using graphicsLayer.
     *
     * This method processes all transform functions from the CSS transform property
     * and combines them into a single graphicsLayer call.
     *
     * retro R1: the wave-49 `reportDroppedShear` guard is gone — an X
     * rotation meeting a Y rotation is now DRAWN exactly by the 4x4 canvas
     * route (TransformMatrixComposer.takesMatrixPath's `twoAxis`), so there
     * is no dropped shear left to report. What reaches this route is either
     * planar or single-axis orthographic, or a translateZ/scaleZ depth under
     * a perspective with no rotation (graphicsLayer's uniform depth scale is
     * the exact P/(P − z) there).
     */
    private fun applyTransformFunctions(modifier: Modifier, config: TransformConfig, pivotShift: DpOffset): Modifier {
        return modifier.graphicsLayer {
            // Set transform origin. Dp overrides resolve against `size`
            // here because CSS lengths on transform-origin reference the
            // element's own dimensions (css-transforms-1 §4). retro R1
            // (A11#0): the pivot is conjugated by the abspos shift first
            // (TransformPivot), then expressed as the fraction of the node's
            // own size graphicsLayer wants — a fraction outside 0..1 is legal
            // (RenderNode's pivot is a plain px pair, fraction × size).
            val pivX = TransformPivot.axisPx(config.originXDp?.value, density, config.originX, size.width, pivotShift.x.toPx())
            val pivY = TransformPivot.axisPx(config.originYDp?.value, density, config.originY, size.height, pivotShift.y.toPx())
            transformOrigin = TransformOrigin(pivX / size.width.coerceAtLeast(1f), pivY / size.height.coerceAtLeast(1f))

            // Accumulated values (transforms are cumulative in CSS)
            var totalTranslationX = 0f
            var totalTranslationY = 0f
            var totalTranslationZ = 0f
            var totalRotation = 0f
            var totalRotationX = 0f
            var totalRotationY = 0f
            var totalScaleX = 1f
            var totalScaleY = 1f
            var totalScaleZ = 1f
            var perspectiveDistance = 0f

            // Process each transform function in order
            config.functions.forEach { fn ->
                when (fn) {
                    is TransformFunction.Translate -> {
                        totalTranslationX += fn.x.toPx()
                        totalTranslationY += fn.y.toPx()
                        totalTranslationZ += fn.z.toPx()
                    }
                    is TransformFunction.TranslateX -> {
                        totalTranslationX += fn.x.toPx()
                    }
                    is TransformFunction.TranslateY -> {
                        totalTranslationY += fn.y.toPx()
                    }
                    is TransformFunction.TranslateZ -> {
                        totalTranslationZ += fn.z.toPx()
                    }
                    is TransformFunction.Rotate -> {
                        totalRotation += fn.degrees
                    }
                    is TransformFunction.RotateX -> {
                        totalRotationX += fn.degrees
                    }
                    is TransformFunction.RotateY -> {
                        totalRotationY += fn.degrees
                    }
                    is TransformFunction.RotateZ -> {
                        totalRotation += fn.degrees
                    }
                    is TransformFunction.Scale -> {
                        totalScaleX *= fn.x
                        totalScaleY *= fn.y
                        totalScaleZ *= fn.z
                    }
                    is TransformFunction.ScaleX -> {
                        totalScaleX *= fn.x
                    }
                    is TransformFunction.ScaleY -> {
                        totalScaleY *= fn.y
                    }
                    is TransformFunction.ScaleZ -> {
                        totalScaleZ *= fn.z
                    }
                    is TransformFunction.Perspective -> {
                        perspectiveDistance = fn.distance.toPx()
                    }
                    is TransformFunction.None -> {
                        // No transform - reset to defaults
                        totalTranslationX = 0f
                        totalTranslationY = 0f
                        totalTranslationZ = 0f
                        totalRotation = 0f
                        totalRotationX = 0f
                        totalRotationY = 0f
                        totalScaleX = 1f
                        totalScaleY = 1f
                        totalScaleZ = 1f
                    }
                    else -> { /* Skew/Matrix handled by applyTransformsWithSkew */ }
                }
            }

            // Add standalone properties. css-transforms-2 §5: the individual
            // `translate` / `rotate` / `scale` properties multiply into the
            // final transform BEFORE the `transform` list (translate, then
            // rotate, then scale, then transform). graphicsLayer composes a
            // single translate·rotate·scale-about-pivot matrix, so we fold
            // them into the same accumulators the function list uses — exact
            // for the common uniform-scale/rotate combinations, approximate
            // only when a non-uniform scale meets a rotation. Previously only
            // translateZ/scaleZ were folded, so `scale: 150%` alongside
            // `transform: rotate(15deg)` rendered the rotation UNSCALED
            // (Transforms_C02: 180x48 box vs web/iOS 270x72).
            config.translateX?.let { totalTranslationX += it.toPx() }
            config.translateY?.let { totalTranslationY += it.toPx() }
            config.translateXFraction?.let { totalTranslationX += size.width * it }
            config.translateYFraction?.let { totalTranslationY += size.height * it }
            config.translateZ?.let { totalTranslationZ += it.toPx() }
            config.rotate?.let { totalRotation += it }
            config.rotateX?.let { totalRotationX += it }
            config.rotateY?.let { totalRotationY += it }
            // `scale: <uniform>` and axis-split scale/scaleX/scaleY all
            // multiply per css-transforms-2 §5's matrix accumulation.
            config.scale?.let { totalScaleX *= it; totalScaleY *= it }
            config.scaleX?.let { totalScaleX *= it }
            config.scaleY?.let { totalScaleY *= it }
            config.scaleZ?.let { totalScaleZ *= it }

            // Standalone `perspective:` PROPERTY (css-transforms-2 §8) —
            // deliberately NOT folded into `perspectiveDistance`. It is
            // §4.1.1's second way, the perspective the element's CHILDREN
            // are projected through, and this element's own list acquires a
            // perspective row only through a perspective() FUNCTION (§4.1.1
            // first way; the loop above). The wave-1 code folded the
            // property unconditionally; retro R1 narrowed the fold to
            // depth-bearing lists (`ownPerspectiveFolds`), copying the web
            // `_dispatch.ts` prefix hack and the iOS `pendingPerspective`
            // seed; retro F3 removed it on measured evidence — the frozen
            // ref of css-transforms/backface-visibility-hidden-001
            // (`perspective: 1000px; transform: rotateY(45deg)`) is
            // orthographic, 100 rows in every column, and the fold keystoned
            // it (TransformMatrixComposer's class doc). So the property
            // carriers stay on this route's orthographic branch, exactly the
            // committed wave-49 Android render.

            // Depth-based scale from translateZ under a perspective —
            // css-transforms-2 §16's perspective matrix: a point at depth z
            // under P is scaled by P/(P − z), and with NO perspective in
            // effect §4.1 drops the z column, so translateZ is invisible.
            // retro R1 (A11#6 / A10#1): the shared owner replaces both the
            // in-place `p − z` arithmetic and the 1000px default it fell
            // back to (that default drew Transform_TranslateZ / Translate3d /
            // pairs-04 040 at a depth scale CSS never applies: 184x93 for a
            // 180x92 box). The −500px row of the wave-1 measurement table
            // (0.500 correct) still holds — see depthScale's doc.
            val depthScaleFactor = TransformMatrixComposer.depthScale(perspectiveDistance, totalTranslationZ)

            // ORTHOGRAPHIC FLATTENING (wave 49) — css-transforms-2 §4.1.
            // With no perspective matrix in the accumulation the 4x4 is
            // flattened by dropping the z row/column, so rotateY(θ) is
            // EXACTLY scaleX(cos θ) and rotateX(θ) is scaleY(cos θ). See
            // OrthographicFlatten for the derivation and the measured
            // Android-vs-ref evidence. retro R1: the guard is the perspective
            // alone (a translateZ no longer keeps a camera alive), and a
            // perspective WITH a 3D rotation never reaches this route
            // (takesMatrixPath's `projective` claims it), so `flat == null`
            // here means "depth under a perspective, no rotation" — the one
            // shape graphicsLayer's uniform scale renders exactly.
            val flat = if (OrthographicFlatten.appliesTo(perspectiveDistance)) {
                OrthographicFlatten.of(totalRotationX, totalRotationY)
            } else {
                null
            }

            // Apply accumulated transforms. rotationZ is a genuine 2D
            // rotation in every case: a Z rotation commutes through the
            // flattening (css-transforms-2 §4.1 — R_z·R_x flattens to R_z·diag(1, cosα)),
            // so only the X/Y fields are zeroed on the orthographic route.
            translationX = totalTranslationX
            translationY = totalTranslationY
            rotationZ = totalRotation
            rotationX = if (flat != null) 0f else totalRotationX
            rotationY = if (flat != null) 0f else totalRotationY

            // Camera distance, projective route only: graphicsLayer has no
            // "no camera" setting, so on the orthographic route the field is
            // never touched. Compose's cameraDistance is in dp (it multiplies
            // by density inside the layer), hence the divide. With no 3D
            // rotation reaching here the camera has no visible effect; it is
            // set for parity with the wave-49 render of the depth carriers.
            if (flat == null) {
                cameraDistance = perspectiveDistance / density
            }

            // Apply 2D scale with depth adjustment
            // scaleZ affects the perceived depth during 3D rotations
            val zAdjustedScaleX = totalScaleX * depthScaleFactor
            val zAdjustedScaleY = totalScaleY * depthScaleFactor

            // When there's 3D rotation, scaleZ affects how much the element
            // appears to stretch along the axis perpendicular to the rotation
            scaleX = if (flat != null) {
                // Orthographic: cos of the Y rotation, once. totalScaleZ is
                // deliberately NOT folded in — scaling z and then dropping
                // the z column is the identity on a flat (z = 0) box, so
                // under css-transforms-2 §4.1 scaleZ has no visible effect without a
                // perspective. The legacy branch below multiplied by
                // abs(cos·scaleZ), i.e. it counted cos a second time.
                zAdjustedScaleX * flat.scaleX
            } else if (totalRotationY != 0f && totalScaleZ != 1f) {
                // Y rotation affects perceived X width
                val rotRad = Math.toRadians(totalRotationY.toDouble())
                val cosRot = cos(rotRad).toFloat()
                zAdjustedScaleX * abs(cosRot * totalScaleZ).coerceAtLeast(0.01f)
            } else {
                zAdjustedScaleX
            }

            scaleY = if (flat != null) {
                zAdjustedScaleY * flat.scaleY
            } else if (totalRotationX != 0f && totalScaleZ != 1f) {
                // X rotation affects perceived Y height
                val rotRad = Math.toRadians(totalRotationX.toDouble())
                val cosRot = cos(rotRad).toFloat()
                zAdjustedScaleY * abs(cosRot * totalScaleZ).coerceAtLeast(0.01f)
            } else {
                zAdjustedScaleY
            }
        }
    }

    /**
     * Apply transforms using simple Modifiers (offset, rotate, scale).
     *
     * This is more efficient but doesn't support:
     * - Custom transform origin
     * - Skew
     * - 3D transforms
     */
    private fun applySimpleTransforms(modifier: Modifier, config: TransformConfig): Modifier {
        var result = modifier

        // Apply translate
        if (config.hasTranslate) {
            val x = config.translateX ?: 0.dp
            val y = config.translateY ?: 0.dp
            result = result.offset(x = x, y = y)
        }

        // Apply rotation
        config.rotate?.let { degrees ->
            result = result.rotate(degrees)
        }

        // Apply scale
        if (config.hasScale) {
            val uniformScale = config.scale ?: 1f
            val scaleX = config.scaleX ?: uniformScale
            val scaleY = config.scaleY ?: uniformScale
            result = result.scale(scaleX = scaleX, scaleY = scaleY)
        }

        return result
    }

    /**
     * Apply transforms using graphicsLayer.
     *
     * Required for:
     * - Custom transform origin
     * - 3D transforms
     * - Combined transforms with specific ordering
     */
    private fun applyWithGraphicsLayer(modifier: Modifier, config: TransformConfig, pivotShift: DpOffset): Modifier {
        return modifier.graphicsLayer {
            // Set transform origin. CSS lengths on transform-origin are
            // resolved against the element's own size (css-transforms-1 §4);
            // `size` is in scope here so a Dp override becomes a fraction.
            // retro R1 (A11#0): conjugated by the abspos shift first
            // (TransformPivot) — this is the route the finding's standalone
            // 005_box (`scaleX(2)` at left 60) took, drawing x 100–180 for
            // the correct 40–119.
            val pivX = TransformPivot.axisPx(config.originXDp?.value, density, config.originX, size.width, pivotShift.x.toPx())
            val pivY = TransformPivot.axisPx(config.originYDp?.value, density, config.originY, size.height, pivotShift.y.toPx())
            transformOrigin = TransformOrigin(pivX / size.width.coerceAtLeast(1f), pivY / size.height.coerceAtLeast(1f))

            // Apply translations. CSS percentage translates resolve against
            // the element's own size — `size` is available inside the
            // graphicsLayer block, so we add the fractional contribution to
            // the absolute Dp contribution instead of folding it into a
            // single value at extract time.
            translationX = (config.translateX?.toPx() ?: 0f) +
                    (config.translateXFraction?.let { size.width * it } ?: 0f)
            translationY = (config.translateY?.toPx() ?: 0f) +
                    (config.translateYFraction?.let { size.height * it } ?: 0f)

            // retro R1 (A11#6) / F3: this is the LONGHAND-ONLY route, and NO
            // perspective can ever be in effect here — a perspective()
            // FUNCTION lives only in the `transform` list, and the own
            // `perspective` PROPERTY is the CHILDREN's (css-transforms-2 §8;
            // F3 removed the fold from every route, so the "never reaches
            // the longhands" half of R1's convention is now simply the spec
            // — web/iOS agree on this half: _dispatch.ts prefixes only the
            // `transform` string; TransformsApplier.swift "NO perspective for
            // the longhand `rotate`"). So css-transforms-2 §4.1 flattens orthographically, a
            // `translate: … <z>` longhand is invisible (the 1000px default
            // that scaled it is gone — pairs-04 040 drew 184x93 for 180x92),
            // and scaleZ contributes nothing (scaling z then dropping the z
            // column is the identity on a flat box). The camera is never set.
            // The iOS twin is the measured control for the rotation half —
            // `css-transforms/animation/rotate-animation-with-will-change-
            // transform-001` (`rotate: 0 1 0 44deg`, transform-origin
            // 100px 0) renders 72x26 px of ink on iOS, pixel-identical to
            // the frozen ref; cos(44°)·100 = 71.9. A diagonal-axis `rotate:`
            // longhand never arrives here (takesMatrixPath claims rotate3d).
            val flat = OrthographicFlatten.of(config.rotateX ?: 0f, config.rotateY ?: 0f)

            // Z stays a real rotation (it commutes through the flattening);
            // X/Y are the cos scales below, never graphicsLayer rotations.
            config.rotate?.let { rotationZ = it }

            // css-transforms-2 §5: `scale` is uniform OR per-axis.
            val uniformScale = config.scale ?: 1f
            val baseScaleX = config.scaleX ?: uniformScale
            val baseScaleY = config.scaleY ?: uniformScale

            // Orthographic: one cos per axis — rotateY foreshortens X,
            // rotateX foreshortens Y (OrthographicFlatten).
            scaleX = baseScaleX * flat.scaleX
            scaleY = baseScaleY * flat.scaleY
        }
    }

    /**
     * Apply only translation to a Modifier.
     *
     * @param modifier The base modifier
     * @param x Horizontal offset
     * @param y Vertical offset
     * @return Modified Modifier with offset applied
     */
    fun applyTranslate(modifier: Modifier, x: Dp?, y: Dp?): Modifier {
        val dx = x ?: 0.dp
        val dy = y ?: 0.dp
        return if (dx.value != 0f || dy.value != 0f) {
            modifier.offset(x = dx, y = dy)
        } else {
            modifier
        }
    }

    /**
     * Apply only rotation to a Modifier.
     *
     * @param modifier The base modifier
     * @param degrees Rotation angle in degrees
     * @return Modified Modifier with rotation applied
     */
    fun applyRotation(modifier: Modifier, degrees: Float?): Modifier {
        return degrees?.let { modifier.rotate(it) } ?: modifier
    }

    /**
     * Apply rotation with custom origin.
     *
     * @param modifier The base modifier
     * @param degrees Rotation angle in degrees
     * @param originX Transform origin X (0-1)
     * @param originY Transform origin Y (0-1)
     * @return Modified Modifier with rotation applied
     */
    fun applyRotation(
        modifier: Modifier,
        degrees: Float,
        originX: Float,
        originY: Float
    ): Modifier {
        return modifier.graphicsLayer {
            transformOrigin = TransformOrigin(originX, originY)
            rotationZ = degrees
        }
    }

    /**
     * Apply only scale to a Modifier.
     *
     * @param modifier The base modifier
     * @param scaleX Horizontal scale factor
     * @param scaleY Vertical scale factor
     * @return Modified Modifier with scale applied
     */
    fun applyScale(modifier: Modifier, scaleX: Float?, scaleY: Float?): Modifier {
        val sx = scaleX ?: 1f
        val sy = scaleY ?: sx
        return if (sx != 1f || sy != 1f) {
            modifier.scale(scaleX = sx, scaleY = sy)
        } else {
            modifier
        }
    }

    /**
     * Apply scale with custom origin.
     *
     * @param modifier The base modifier
     * @param scaleX Horizontal scale factor
     * @param scaleY Vertical scale factor
     * @param originX Transform origin X (0-1)
     * @param originY Transform origin Y (0-1)
     * @return Modified Modifier with scale applied
     */
    fun applyScale(
        modifier: Modifier,
        scaleX: Float,
        scaleY: Float,
        originX: Float,
        originY: Float
    ): Modifier {
        return modifier.graphicsLayer {
            transformOrigin = TransformOrigin(originX, originY)
            this.scaleX = scaleX
            this.scaleY = scaleY
        }
    }

    /**
     * Apply skew transform.
     *
     * @param modifier The base modifier
     * @param skewXDegrees Skew angle on X axis in degrees
     * @param skewYDegrees Skew angle on Y axis in degrees
     * @param originX Transform origin X (0-1)
     * @param originY Transform origin Y (0-1)
     * @return Modified Modifier with skew applied
     */
    fun applySkew(
        modifier: Modifier,
        skewXDegrees: Float,
        skewYDegrees: Float = 0f,
        originX: Float = 0.5f,
        originY: Float = 0.5f
    ): Modifier {
        val skewXRad = Math.toRadians(skewXDegrees.toDouble()).toFloat()
        val skewYRad = Math.toRadians(skewYDegrees.toDouble()).toFloat()

        return modifier.drawWithContent {
            val pivotX = size.width * originX
            val pivotY = size.height * originY

            drawContext.canvas.let { canvas ->
                canvas.save()
                canvas.translate(pivotX, pivotY)
                canvas.skew(tan(skewXRad), tan(skewYRad))
                canvas.translate(-pivotX, -pivotY)
                this@drawWithContent.drawContent()
                canvas.restore()
            }
        }
    }

    // retro R1 (A10#1): the public helpers applyTranslateZ / applyScaleZ /
    // apply3DTransform and the `Notes` object used to live here. All four
    // carried the first-order Taylor depth scale `1 + z/P` (Notes even
    // documented it as "the CSS formula") and the abs(cos)·scaleZ guess, had
    // no caller anywhere in the runtime, the harness or the tests, and were
    // the last copies of the expansion STATUS.md said was replaced. Deleted
    // rather than fixed: the single depth-scale owner is
    // TransformMatrixComposer.depthScale and the exact 3D route is
    // TransformMatrixPathApplier.
}
