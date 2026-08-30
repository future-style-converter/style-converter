package com.styleconverter.runtime.transforms

// Pure math — no Compose UI imports, so the whole file is unit-testable on
// the plain JVM without Robolectric. The applier converts Dp/size at the
// call site; everything here works in raw floats.
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Composes a CSS transform list into ONE affine matrix in declared order.
 *
 * Why this exists (wave 48, lane W6): css-transforms-1 §11 defines
 * `transform: A B` as the matrix product A·B — B maps the point FIRST.
 * The legacy Compose path (TransformApplier.applyTransformFunctions)
 * instead accumulates each function KIND into a separate scalar
 * (translateX += …, rotation += …, scaleX *= …) and hands the totals to
 * graphicsLayer, whose fixed field order is translate·rotate·scale. The
 * list order is therefore discarded: `scale(2) translate(30px)` rendered
 * identically to `translate(30px) scale(2)` (ledgered as the
 * Transform_Combined iOS-Android divergence; measured web (156,96) vs
 * Android (126,96) on a 40x40 probe). iOS was fixed 2026-08-28 by
 * emitting SwiftUI modifiers in reverse list order; this composer is the
 * Compose twin — same spec section, same declared-order semantics.
 */
object TransformListComposer {

    /**
     * 2D affine in the CSS matrix(a,b,c,d,tx,ty) convention
     * (css-transforms-1 §9.1): columns (a,b) and (c,d) are the linear
     * part, (tx,ty) the translation. Maps p -> (a·x + c·y + tx,
     * b·x + d·y + ty).
     */
    data class Affine2D(
        val a: Float, val b: Float,   // first column  — image of (1,0)
        val c: Float, val d: Float,   // second column — image of (0,1)
        val tx: Float, val ty: Float, // translation   — image of (0,0)
    ) {
        companion object {
            /** Identity — the empty transform list (css-transforms-1 §11). */
            val IDENTITY = Affine2D(1f, 0f, 0f, 1f, 0f, 0f)
        }
    }

    /**
     * Matrix product m·n — n maps the point first, matching the reading
     * order of a CSS transform list (§11: left-to-right lists multiply
     * left-to-right, so the RIGHTMOST function is innermost).
     */
    fun multiply(m: Affine2D, n: Affine2D): Affine2D = Affine2D(
        // Linear block: standard 2x2 product of column-convention matrices.
        a = m.a * n.a + m.c * n.b,
        b = m.b * n.a + m.d * n.b,
        c = m.a * n.c + m.c * n.d,
        d = m.b * n.c + m.d * n.d,
        // Translation: m applied to n's translation, plus m's own.
        tx = m.a * n.tx + m.c * n.ty + m.tx,
        ty = m.b * n.tx + m.d * n.ty + m.ty,
    )

    /** translate(x, y) — css-transforms-1 §13.1. */
    fun translation(x: Float, y: Float): Affine2D = Affine2D(1f, 0f, 0f, 1f, x, y)

    /**
     * rotate(deg) — css-transforms-1 §13.1. Positive angles are clockwise
     * in CSS's y-down coordinate system, which is also the convention of
     * Compose's graphicsLayer.rotationZ and Canvas.rotate, so degrees pass
     * through with no sign flip (the legacy path relied on the same
     * agreement for single rotations, verified against web).
     */
    fun rotation(deg: Float): Affine2D {
        val r = Math.toRadians(deg.toDouble())            // trig wants radians
        val cs = cos(r).toFloat()                          // cos once, reused
        val sn = sin(r).toFloat()                          // sin once, reused
        return Affine2D(cs, sn, -sn, cs, 0f, 0f)           // [[c,-s],[s,c]]
    }

    /** scale(sx, sy) — css-transforms-1 §13.1. */
    fun scale(sx: Float, sy: Float): Affine2D = Affine2D(sx, 0f, 0f, sy, 0f, 0f)

    /**
     * Conjugate by transform-origin — css-transforms-1 §8: the full
     * transform is translate(origin) · M · translate(-origin). The linear
     * part is untouched; only the translation moves, which is why the
     * graphicsLayer route can decompose the linear part BEFORE the origin
     * (and the element size it depends on) is known.
     */
    fun conjugateByOrigin(m: Affine2D, originXPx: Float, originYPx: Float): Affine2D =
        multiply(translation(originXPx, originYPx), multiply(m, translation(-originXPx, -originYPx)))

    /**
     * One member of the ordered composition. Steps carry raw dp/fraction
     * values because px need density and the element's own size (CSS
     * percentage translates resolve against the border box, css-transforms-1
     * §6) — both only known inside the draw/layer lambda.
     */
    sealed interface Step {
        /** translate: dp offsets plus own-size fractions (either may be zero). */
        data class Translate(val xDp: Float, val yDp: Float, val xFrac: Float, val yFrac: Float) : Step
        /** rotate about Z, degrees. */
        data class Rotate(val deg: Float) : Step
        /** axis scale (uniform scale arrives with sx == sy). */
        data class Scale(val sx: Float, val sy: Float) : Step
    }

    /**
     * Builds the ordered step list for a config, or null when the config
     * is NOT eligible for the ordered path and must stay on the legacy
     * routes. Null — not a silent fallthrough: every branch below names
     * why the legacy path is the correct owner.
     */
    fun stepsFor(config: TransformConfig): List<Step>? {
        // No function list — the standalone-property-only path
        // (applyWithGraphicsLayer / applySimpleTransforms) already applies
        // css-transforms-2 §5's translate·rotate·scale in graphicsLayer's
        // native T·R·S order, which is exactly the spec order. Correct as-is.
        if (config.functions.isEmpty()) return null
        // Skew members (function or standalone) are owned by the canvas
        // skew path — graphicsLayer cannot express shear. Ordering THAT
        // path is a named deferred item, not silently absorbed here.
        if (config.hasSkew) return null
        // Any 3D ingredient (rotateX/Y, translateZ, scaleZ, perspective,
        // z-bearing translate/scale functions) keeps the legacy
        // approximations — this composer targets the 2D plane. HONESTY
        // (S3/S6, fix lane F5): "strictly 2D" is only as strict as the
        // EXTRACTOR's classification, which this gate inherits —
        // TransformExtractor.extractRotate3dFunction maps rotate3d by a
        // largest-axis heuristic, so e.g. rotate3d(1,1,1,θ) arrives here
        // as a plain RotateZ(θ) and rides the ordered path as a Z
        // rotation (an approximation of the true diagonal-axis rotation,
        // inherited — not introduced — by this composer).
        if (config.has3DTransform) return null

        val steps = mutableListOf<Step>()

        // css-transforms-2 §5: the individual properties compose as
        // translate, then rotate, then scale, and the `transform` list
        // comes AFTER all three (i.e. the list maps the point first).
        val hasTranslateProp = config.translateX != null || config.translateY != null ||
            config.translateXFraction != null || config.translateYFraction != null
        if (hasTranslateProp) {
            // Dp `.value` is kept raw; affineOf multiplies by density later.
            steps += Step.Translate(
                xDp = config.translateX?.value ?: 0f,
                yDp = config.translateY?.value ?: 0f,
                xFrac = config.translateXFraction ?: 0f,
                yFrac = config.translateYFraction ?: 0f,
            )
        }
        // `rotate:` longhand — plain Z rotation only (axis-angle X/Y forms
        // land in rotateX/rotateY and were rejected by the 3D gate above).
        config.rotate?.let { steps += Step.Rotate(it) }
        // `scale:` longhand — mirror the legacy accumulation contract:
        // the extractor emits EITHER uniform `scale` OR axis scaleX/scaleY,
        // and the legacy path multiplies whichever are present.
        val sx = (config.scale ?: 1f) * (config.scaleX ?: 1f)
        val sy = (config.scale ?: 1f) * (config.scaleY ?: 1f)
        if (sx != 1f || sy != 1f) steps += Step.Scale(sx, sy)
        val standaloneCount = steps.size                   // for the churn guard below

        // The `transform` function list, in declared order (§11).
        for (fn in config.functions) {
            when (fn) {
                // z components are guaranteed 0/1 here by the 3D gate.
                is TransformFunction.Translate -> steps += Step.Translate(fn.x.value, fn.y.value, 0f, 0f)
                is TransformFunction.TranslateX -> steps += Step.Translate(fn.x.value, 0f, 0f, 0f)
                is TransformFunction.TranslateY -> steps += Step.Translate(0f, fn.y.value, 0f, 0f)
                is TransformFunction.Rotate -> steps += Step.Rotate(fn.degrees)
                is TransformFunction.RotateZ -> steps += Step.Rotate(fn.degrees)
                is TransformFunction.Scale -> steps += Step.Scale(fn.x, fn.y)
                is TransformFunction.ScaleX -> steps += Step.Scale(fn.x, 1f)
                is TransformFunction.ScaleY -> steps += Step.Scale(1f, fn.y)
                // matrix()/matrix3d() stay on the legacy decomposition path
                // (applyTransformsWithMatrix); `none` carries reset
                // semantics the accumulator implements. Anything else 2D
                // was excluded by hasSkew/has3DTransform above — refusing
                // here keeps this whitelist honest instead of guessing.
                else -> return null
            }
        }

        // Churn guard: a SINGLE function with no standalone properties is
        // order-trivial — the legacy graphicsLayer path already renders it
        // exactly (pivot + one field). Keeping it there leaves every
        // single-function baseline byte-stable instead of re-rendering the
        // same matrix through a different parameterisation.
        if (steps.size - standaloneCount < 2 && standaloneCount == 0) return null

        return steps
    }

    /** Linear part only — density-independent (translations drop out), so
     *  the graphicsLayer-vs-canvas route can be decided at modifier
     *  construction time, before size/density exist. */
    fun linearOf(steps: List<Step>): Affine2D {
        var m = Affine2D.IDENTITY                          // running product, left-to-right
        for (s in steps) {
            m = when (s) {
                is Step.Translate -> m                     // translation has identity linear part
                is Step.Rotate -> multiply(m, rotation(s.deg))
                is Step.Scale -> multiply(m, scale(s.sx, s.sy))
            }
        }
        return m
    }

    /** Full affine in px: dp·density plus own-size fractions (§6 —
     *  percentages resolve against the element's border box). */
    fun affineOf(steps: List<Step>, density: Float, widthPx: Float, heightPx: Float): Affine2D {
        var m = Affine2D.IDENTITY                          // running product, left-to-right
        for (s in steps) {
            m = when (s) {
                is Step.Translate -> multiply(
                    m,
                    translation(s.xDp * density + s.xFrac * widthPx, s.yDp * density + s.yFrac * heightPx),
                )
                is Step.Rotate -> multiply(m, rotation(s.deg))
                is Step.Scale -> multiply(m, scale(s.sx, s.sy))
            }
        }
        return m
    }

    /** graphicsLayer field values for a linear part that IS expressible
     *  as rotate·scale. rotationDeg feeds rotationZ; scales feed
     *  scaleX/scaleY; the caller supplies translation separately. */
    data class LayerFields(val rotationDeg: Float, val scaleX: Float, val scaleY: Float)

    /**
     * Exact-or-refuse decomposition of a linear part into R(θ)·S(sx,sy) —
     * the ONLY shape Android's render pipeline can express through
     * graphicsLayer fields (RenderNode composes translate · rotate ·
     * scale, scale innermost; corroborated by the TLO probe where both
     * orderings of scaleX(2)/rotate(45deg) rendered R·S's 85x85 box).
     *
     * Returns null when the matrix carries shear (e.g. scaleX(2)
     * rotate(45deg) = S·R, whose columns are not orthogonal) or is
     * degenerate — the caller then draws the exact matrix on the canvas
     * instead. Never approximates: a recomposition check guards against
     * feeding graphicsLayer anything but the true matrix.
     *
     * NOTE deliberately NOT reusing TransformApplier.decomposeMatrix2D:
     * that helper negates scaleX on a negative determinant WITHOUT
     * flipping the rotation basis, so a pure reflection matrix(-1,0,0,1,
     * 0,0) round-trips to diag(1,-1) — the wrong axis. Here sy carries
     * the sign (R(θ)·diag(sx>0, sy signed) spans the same set including
     * reflections) and the verify step refuses anything inexact.
     */
    fun decomposeRotateScale(m: Affine2D): LayerFields? {
        // First column = sx · (cosθ, sinθ) with sx > 0 by construction.
        val sx = hypot(m.a, m.b)
        // Degenerate X axis (scale ~0) — atan2 undefined; refuse and let
        // the canvas route draw the collapsed matrix exactly.
        if (sx < 1e-6f) return null
        // Rotation angle from the first column's direction.
        val theta = atan2(m.b, m.a)
        val cs = cos(theta)                                // basis vector r1 = (cs, sn)
        val sn = sin(theta)
        // Signed second scale: project the second column onto r1's
        // perpendicular (-sn, cs). A y-reflection lands here as sy < 0.
        val sy = -m.c * sn + m.d * cs
        // Verify by recomposition — R(θ)·S must reproduce m EXACTLY (to
        // float tolerance). Shear residue (non-orthogonal columns) fails
        // this and returns null rather than silently mis-rendering.
        val eps = 1e-4f * maxOf(1f, abs(sx), abs(sy))
        if (abs(sx * cs - m.a) > eps || abs(sx * sn - m.b) > eps ||
            abs(-sy * sn - m.c) > eps || abs(sy * cs - m.d) > eps
        ) return null
        // Radians back to degrees for graphicsLayer.rotationZ.
        return LayerFields(Math.toDegrees(theta.toDouble()).toFloat(), sx, sy)
    }
}
