package com.styleconverter.runtime.transforms

// Pure over TransformConfig (Dp is read via `.value`, never converted here),
// so the routing gate and the full 4x4 product are JVM-pinnable
// (TransformMatrixComposerTest).
import kotlin.math.max

/**
 * Builds the css-transforms-2 §6 current transformation matrix for one
 * element and decides which configs must take it (retro R1).
 *
 * ## Routing contract
 * The legacy routes stay byte-identical for everything they already render
 * exactly: pure-2D lists (wave-48 TransformListComposer), single-axis
 * rotateX/rotateY with no perspective (wave-49 OrthographicFlatten) and
 * `translateZ` under a perspective (graphicsLayer's uniform depth scale is
 * the exact P/(P − z)). [takesMatrixPath] claims ONLY the shapes those
 * routes get wrong, each named in the finding it closes:
 *  - a `perspective()` FUNCTION together with a 3D rotation (A11#6: the
 *    graphicsLayer camera rendered `perspective(500px) rotateY(30deg)`
 *    as a flat rectangle; web/iOS draw the trapezoid);
 *  - `matrix3d()` (A11#6: silently dropped by the matrix route);
 *  - a diagonal-axis `rotate3d()` / `rotate: x y z <angle>` (A11#6/#11:
 *    the largest-axis heuristic);
 *  - an X rotation meeting a Y rotation (the sin·sin shear
 *    OrthographicFlatten could only report, never draw);
 *  - skew or scaleZ combined with a 3D rotation (the abs(cos)·scaleZ
 *    hacks in the legacy paths).
 *
 * ## The own `perspective` PROPERTY is the CHILDREN's — never this element's
 * css-transforms-2 §4.1.1 names the two ways a perspective reaches a
 * 3d-transformed element: first, a `perspective()` function in the
 * element's own transform function list, which "computes into the
 * element's current transformation matrix"; second, the `perspective` and
 * `perspective-origin` PROPERTIES, which "influence the rendering of its
 * 3d-transformed children". §8 (The perspective Property) defines the
 * property purely as the input of that children-side perspective matrix;
 * nothing in §6's own-matrix product reads it. So an element carrying
 * `perspective: 1000px; transform: rotateY(45deg)` draws ITSELF
 * orthographically (§4.1 flattens by dropping the z row and column) and
 * only its children see the 1000px camera.
 *
 * Retro R1 folded the property into the element's OWN list whenever a
 * depth-bearing function was present (its `ownPerspectiveFolds` rule),
 * copying web (runtimes/web/src/engine/transforms/_dispatch.ts prefixes
 * `perspective(<len>)` onto the element's own `transform` string) and iOS
 * (TransformsApplier.swift seeds `pendingPerspective` from the property).
 * Retro F3 (skeptic S2, should-fix) REMOVED that fold on Compose, on
 * measured evidence: css-transforms/backface-visibility-hidden-001's
 * frozen ref draws the green child as 70 columns × exactly 100 rows —
 * orthographic — while the folded product keystones it to 103.7 rows on
 * the left edge and 96.6 on the right, a shape the ref does not have (the
 * spec-orthographic product gives 100 rows in every column; pinned in
 * TransformMatrixComposerTest). R1's "the corpus cost is nil / the only
 * carriers are rotateY(180deg) lists" was false: the wave49-final census
 * through this very gate finds THREE property+depth carriers —
 * backface-visibility-hidden-001 __1 (P = 1000, rotateY(45deg), PASS 0.998,
 * which the fold moved AWAY from the ref) and composited-under-rotateY-
 * 180deg-perspective __0/__1 (P = 10, rotateY(180deg), PASS 1.0, where the
 * z = 0 plane stays at z = 0, so the fold was the identity homography and
 * scaleX(−1) either way). No other wave49-final component pairs the
 * property with a depth-bearing own list (css-view-transitions/
 * 3d-transform-{incoming,outgoing} __0 pair it with a 2D matrix(), which
 * never folded).
 *
 * What the reversal costs: web and iOS still fold, so the property-form
 * fixtures (fixtures/properties/transforms/perspective.json
 * Perspective_500px/1000px/Zero, perspective-origin.json PerspOrigin_*,
 * visual-test.json Perspective_Rotate) keystone there and stay flat here.
 * That flat box IS the committed wave-49 Android baseline (tools/visual/
 * baseline/Android__046_Perspective_Rotate.png: 40 rows in every column,
 * against iOS/web's 38 on the far edge), so nothing committed moves; the
 * twins are the ones off the spec — reported as a cross-engine seam, not
 * folded around here. The children channel itself — projecting a CHILD
 * through its parent's `perspective` — is still unimplemented: a Modifier
 * cannot see its ancestor (OrthographicFlatten names the renderer seam and
 * the carriers), and [TransformConfig.perspective] stays extracted for it.
 */
object TransformMatrixComposer {

    /** css-transforms-2 §8 / §12.2: "values less than 1px must be treated as 1px". */
    const val MIN_PERSPECTIVE_PX = 1f

    /** Clamp a perspective distance per §8/§12.2 — a 0 stays projective, never `none`. */
    fun clampPerspectivePx(px: Float): Float = max(px, MIN_PERSPECTIVE_PX)

    /**
     * Uniform depth scale of a flat element under perspective P at depth z:
     * §16's perspective matrix gives w = 1 − z/P, i.e. P/(P − z). This is
     * the single owner for every legacy route (A10#1: the skew route still
     * carried the first-order Taylor form 1 + z/P in its no-`perspective()`
     * branch and the INVERTED P/(P + z) in its `perspective()` branch, so
     * `perspective(500px) translateZ(250px) skewX(20deg)` drew 1.5x /
     * 0.667x where 2.0x is right). No perspective in effect → 1 (§4.1:
     * without a perspective matrix the z row and column are dropped, so a
     * translateZ is invisible — A11#6's pairs-04 040, `translate: 10px
     * 20px 30px` with `perspective: none`, was drawn 184x93 instead of
     * 180x92 by a 1000px default that has no basis in the spec). z ≥ P puts
     * the element at or behind the camera; CSS stops painting it and there
     * is no finite scale, so the legacy clamp caps it rather than dividing
     * by zero or flipping sign.
     */
    fun depthScale(perspectivePx: Float, zPx: Float): Float {
        if (perspectivePx <= 0f || zPx == 0f) return 1f  // no perspective, or no depth: identity
        val denom = perspectivePx - zPx                    // the w = 1 − z/P denominator, times P
        val factor = if (denom > 0f) perspectivePx / denom else Float.MAX_VALUE
        return factor.coerceIn(0.1f, 10f)                  // legacy cap, kept for parity with the graphicsLayer route
    }

    /**
     * Is a perspective in effect on the element's OWN matrix? Only a
     * `perspective()` FUNCTION in its own list is (§4.1.1's first way; the
     * function itself is §12.2). The `perspective` PROPERTY is §4.1.1's
     * second way and projects the CHILDREN (§8), so — since retro F3 — it is
     * deliberately consulted by no own-transform route: not here, not in
     * [ctm], not in TransformListComposer.isOrthographicRotationOnly, not in
     * TransformApplier's scalar routes (the class doc has the measurement
     * that retired R1's fold).
     */
    fun perspectiveInEffect(config: TransformConfig): Boolean =
        config.functions.any { it is TransformFunction.Perspective }

    /** Summed X and Y rotation angles, list members and longhands alike (the shear predicate's inputs). */
    fun rotationSums(config: TransformConfig): Pair<Float, Float> {
        var rx = config.rotateX ?: 0f                       // `rotate: x <angle>` longhand
        var ry = config.rotateY ?: 0f                       // `rotate: y <angle>` longhand
        for (fn in config.functions) {
            if (fn is TransformFunction.RotateX) rx += fn.degrees
            if (fn is TransformFunction.RotateY) ry += fn.degrees
        }
        return rx to ry
    }

    /** True when the config must be drawn through the exact 4x4 canvas route — see the class doc's list. */
    fun takesMatrixPath(config: TransformConfig): Boolean {
        if (!config.hasTransform) return false             // nothing to draw
        val fns = config.functions
        val diagonal = config.rotate3d != null || fns.any { it is TransformFunction.Rotate3d }
        val matrix3d = fns.any { it is TransformFunction.Matrix3d }
        val (rx, ry) = rotationSums(config)
        val twoAxis = OrthographicFlatten.shearResidual(rx, ry) != 0f   // X meets Y → shear no scale pair carries
        val anyRotation3D = rx != 0f || ry != 0f || diagonal
        val scaleZ = (config.scaleZ ?: 1f) != 1f ||
            fns.any { (it is TransformFunction.ScaleZ && it.z != 1f) || (it is TransformFunction.Scale && it.z != 1f) }
        // The keystone graphicsLayer never drew — a perspective() FUNCTION
        // meeting a 3D rotation. The own `perspective` PROPERTY is not a
        // factor (retro F3: it is the children's, class doc), so
        // `perspective: 1000px; transform: rotateY(45deg)` keeps the
        // orthographic wave-49 route.
        val projective = perspectiveInEffect(config) && anyRotation3D
        val skew3D = config.hasSkew && (anyRotation3D || matrix3d)     // the skew route's cos·scaleZ approximations
        return matrix3d || diagonal || twoAxis || projective || skew3D || (scaleZ && anyRotation3D)
    }

    /**
     * One function's 4x4 in DEVICE px. Lengths in the IR are CSS px read as
     * dp, so translations scale by [density]; a matrix3d's perspective row
     * scales by 1/density (S·M·S⁻¹ with S = diag(ρ,ρ,ρ,1)); linear parts are
     * unit-free. [w]/[h] are reserved for own-size percentages, which the
     * function form does not carry yet (extractTranslateFunction resolves
     * only lengths) — kept in the signature so the seam is ready.
     */
    fun functionMatrix(fn: TransformFunction, density: Float, @Suppress("UNUSED_PARAMETER") w: Float, @Suppress("UNUSED_PARAMETER") h: Float): Mat4 = when (fn) {
        is TransformFunction.Translate -> Mat4.translate(fn.x.value * density, fn.y.value * density, fn.z.value * density)
        is TransformFunction.TranslateX -> Mat4.translate(fn.x.value * density, 0f, 0f)
        is TransformFunction.TranslateY -> Mat4.translate(0f, fn.y.value * density, 0f)
        is TransformFunction.TranslateZ -> Mat4.translate(0f, 0f, fn.z.value * density)
        is TransformFunction.Rotate -> Mat4.rotate3d(0f, 0f, 1f, fn.degrees)      // §16: rotate(α) = rotate3d(0,0,1,α)
        is TransformFunction.RotateZ -> Mat4.rotate3d(0f, 0f, 1f, fn.degrees)
        is TransformFunction.RotateX -> Mat4.rotate3d(1f, 0f, 0f, fn.degrees)
        is TransformFunction.RotateY -> Mat4.rotate3d(0f, 1f, 0f, fn.degrees)
        is TransformFunction.Rotate3d -> Mat4.rotate3d(fn.x, fn.y, fn.z, fn.degrees)
        is TransformFunction.Scale -> Mat4.scale(fn.x, fn.y, fn.z)
        is TransformFunction.ScaleX -> Mat4.scale(fn.x, 1f, 1f)
        is TransformFunction.ScaleY -> Mat4.scale(1f, fn.y, 1f)
        is TransformFunction.ScaleZ -> Mat4.scale(1f, 1f, fn.z)
        is TransformFunction.Skew -> Mat4.skew(fn.xDegrees, fn.yDegrees)
        is TransformFunction.SkewX -> Mat4.skew(fn.degrees, 0f)
        is TransformFunction.SkewY -> Mat4.skew(0f, fn.degrees)
        is TransformFunction.Perspective -> Mat4.perspective(clampPerspectivePx(fn.distance.value * density))
        is TransformFunction.Matrix -> fn.values.let { m ->
            // css-transforms-1 §7.1: e/f are lengths → device px.
            Mat4.cssMatrix(m[0], m[1], m[2], m[3], m[4] * density, m[5] * density)
        }
        is TransformFunction.Matrix3d -> fn.values.toMutableList().let { m ->
            // Column-major: the translation column is entries 12..14, the
            // perspective coefficients sit at rows 3 of columns 0..2 (entries 3, 7, 11).
            m[12] *= density; m[13] *= density; m[14] *= density
            m[3] /= density; m[7] /= density; m[11] /= density
            Mat4.cssMatrix3d(m)
        }
        TransformFunction.None -> Mat4.IDENTITY           // `transform: none` — nothing else can share the list
    }

    /**
     * The full css-transforms-2 §6 current transformation matrix in device
     * px about a pivot already conjugated by the abspos shift
     * (TransformPivot): identity → translate by the origin (step 2) →
     * `translate` (3) → `rotate` (4) → `scale` (5) → [offset is chained by
     * OffsetPathApplier, not here (6)] → each function left to right (7) →
     * translate by the negated origin (8). The own `perspective` PROPERTY
     * is NOT a factor of this product (§8: it is the children's; retro F3
     * removed the R1 fold that used to lead step 7 — class doc). Percentage
     * `translate` longhand components resolve against the element's own
     * box (css-transforms-2 §5 defers to <length-percentage> against the
     * reference box).
     */
    fun ctm(config: TransformConfig, density: Float, w: Float, h: Float, pivotXPx: Float, pivotYPx: Float): Mat4 {
        var m = Mat4.translate(pivotXPx, pivotYPx, 0f)     // §6 step 2 (+ retro-R1 abspos shift, already in the pivot)
        // Step 3 — `translate` longhand: dp·density plus own-size fractions.
        val tx = (config.translateX?.value ?: 0f) * density + (config.translateXFraction ?: 0f) * w
        val ty = (config.translateY?.value ?: 0f) * density + (config.translateYFraction ?: 0f) * h
        val tz = (config.translateZ?.value ?: 0f) * density
        if (tx != 0f || ty != 0f || tz != 0f) m *= Mat4.translate(tx, ty, tz)
        // Step 4 — `rotate` longhand in whichever form the extractor kept.
        config.rotate?.let { m *= Mat4.rotate3d(0f, 0f, 1f, it) }
        config.rotateX?.let { m *= Mat4.rotate3d(1f, 0f, 0f, it) }
        config.rotateY?.let { m *= Mat4.rotate3d(0f, 1f, 0f, it) }
        config.rotate3d?.let { m *= Mat4.rotate3d(it.x, it.y, it.z, it.degrees) }
        // Step 5 — `scale` longhand (uniform × axis, the legacy accumulation contract).
        val sx = (config.scale ?: 1f) * (config.scaleX ?: 1f)
        val sy = (config.scale ?: 1f) * (config.scaleY ?: 1f)
        val sz = config.scaleZ ?: 1f
        if (sx != 1f || sy != 1f || sz != 1f) m *= Mat4.scale(sx, sy, sz)
        // Legacy pseudo-longhands SkewX/SkewY (not CSS properties; the skew
        // route accumulated them after scale) — same slot here.
        if ((config.skewX ?: 0f) != 0f || (config.skewY ?: 0f) != 0f) m *= Mat4.skew(config.skewX ?: 0f, config.skewY ?: 0f)
        // Step 7 — the function list, left to right. A perspective row enters
        // ONLY through a perspective() FUNCTION here (§4.1.1 first way);
        // `config.perspective` is the children's and is not multiplied in.
        for (fn in config.functions) m *= functionMatrix(fn, density, w, h)
        // Step 8 — back from the origin.
        return m * Mat4.translate(-pivotXPx, -pivotYPx, 0f)
    }
}
