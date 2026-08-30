package com.styleconverter.runtime.transforms

// Pure math — deliberately NO Compose UI / android imports, so the whole
// file is exercisable from the plain-JVM unit suite (same discipline as
// TransformListComposer.kt, which states the rule for this folder).
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Flattens the 3D rotation part of a CSS transform onto the 2D plane the
 * way a browser does when **no perspective is in effect**.
 *
 * ## The spec rule
 *
 * css-transforms-2 §4.1 ("3D Transform Rendering") composes an element's
 * transform into a 4x4 matrix and then flattens it into the parent's 2D
 * plane. The projection is PROJECTIVE only when a perspective matrix was
 * introduced — by a `perspective()` function in the same transform list,
 * or by the `perspective` property (§8). With neither, the m34 row is
 * zero, the flattening is a plain "drop the z row and column", and the
 * result is an ORTHOGRAPHIC projection: a bare `rotateY(θ)` on a flat
 * (z = 0) box is EXACTLY `scaleX(cos θ)`, and `rotateX(θ)` is exactly
 * `scaleY(cos θ)`. Derivation, for the y-axis case:
 *
 *     R_y(θ)·(x, y, 0) = (x·cosθ, y, −x·sinθ)   →  drop z  →  (x·cosθ, y)
 *
 * The iOS twin already renders this correctly and is the measured control
 * for it: `Rotate3DEffect` (StyleEngine/transforms/TransformsEffects.swift)
 * sets `m34` only when a perspective distance is present — the §4.1.1
 * ("Perspective") matrix, which is the identity when no distance is
 * given. (TransformsEffects.swift and TransformsApplier.swift still cite
 * §13.1 for that matrix on their own pre-existing lines; §13.1 is
 * "Interpolation of 3D matrices" and is the wrong anchor. Left alone
 * because those lines are outside this wave's diff — reported, not fixed.)
 *
 * ## Why Compose needs an explicit flattener
 *
 * `Modifier.graphicsLayer`'s `rotationX` / `rotationY` ALWAYS project
 * through a camera sitting `cameraDistance` away — there is no
 * "orthographic" setting. Whatever camera distance is chosen, the box is
 * rendered as a keystoned trapezoid that CSS never draws. The wave-48
 * Android captures show exactly that: on
 * `css-transforms/css-transform-3d-rotateY-positive` a 240x120 box under
 * `rotateY(60deg)` must project to the ref's exact 120x120 SQUARE (iOS
 * renders 120x120, n = 14400 px, pixel-identical to the frozen ref),
 * while Android rendered a 139x149 trapezoid — because
 * `TransformApplier.applyTransformFunctions` set
 * `cameraDistance = 8f * density`, which Compose then multiplies by
 * density again, i.e. a camera 8·density² px away (~72 px on a 3x
 * device) staring at a 240 px box.
 *
 * Folding cos into `scaleX`/`scaleY` and leaving `rotationX`/`rotationY`
 * at zero removes the camera from the equation entirely and reproduces
 * the spec matrix exactly.
 *
 * ## THIS IS AN APPROXIMATION, NOT AN IMPLEMENTATION OF THE RENDERING MODEL
 *
 * This object does NOT read `transform-style` (§7) and knows nothing about
 * 3D rendering contexts (§4.1.2) or transformed element hierarchies
 * (§4.1.3). It flattens EVERY element on its own, then the composition
 * multiplies the flattened 2D results down the tree. That is the correct
 * answer only for the `transform-style: flat` default. Twenty wave-48
 * corpus tests declare `preserve-3d` (grep PRESERVE_3D under
 * `tools/titan/runs/wave48-final/sections/`, per-test-ir); exactly one of
 * them has been measured against this route, and it diverges.
 *
 * The measurement (skeptic S5, wave 49, driving this object directly in
 * the JVM) is `css-transforms/css-transform-3d-transform-style`, whose
 * per-test IR was read to confirm the shape: the parent declares
 * `transform-style: preserve-3d` and `rotateY(-60deg)`, and the nested
 * green child (400 px wide, same `slot.parent` chain) declares
 * `rotateY(120deg)`. Under §4.1.3 a
 * preserve-3d parent does not flatten its children, so the two rotations
 * compose in 3D and project ONCE — |cos(−60° + 120°)|·400 = 200 px of
 * green. This object instead flattens each element and the composition
 * multiplies the results — |cos(−60°)·cos(120°)|·400 = 100 px. Half the
 * correct width.
 *
 * So for that test the value here is wrong, and it is wrong for a
 * structural reason this lane did not fix: no `transform-style` channel
 * reaches a Modifier. Naming that beats a comment that reads as if §4.1
 * were implemented.
 */
object OrthographicFlatten {

    /**
     * The flattened contribution of the accumulated 3D rotations.
     *
     * @property scaleX multiplier for the X axis — cos of the Y rotation.
     * @property scaleY multiplier for the Y axis — cos of the X rotation.
     * @property shearDropped true when BOTH axes rotate, in which case the
     *   true flattened matrix also carries an off-diagonal shear term that
     *   a pure scale pair cannot express (see [shearResidual]).
     */
    data class Flattened(
        val scaleX: Float,
        val scaleY: Float,
        val shearDropped: Boolean,
    )

    /**
     * Is the orthographic rule the correct one for this accumulation?
     *
     * Two guards, both about staying inside what is provably exact:
     *
     * - `perspectivePx > 0` means a `perspective()` function or the
     *   `perspective` property put a real m34 in the matrix (§8/§4.1.1),
     *   so the projection IS projective and the existing camera path is
     *   the right owner. NOTE the honest limit: this only sees the
     *   element's OWN perspective. A perspective inherited from an
     *   ancestor's `perspective` property is not visible to a Modifier
     *   (it would need a CompositionLocal threaded from the renderer —
     *   a seam this lane does not own). Corpus carriers of that shape:
     *   `filter-effects/backdrop-filter-3d-transform-perspective` and
     *   `…-nested-3d-transform-perspective` (parent `perspective: 600px`,
     *   child `rotateY(45deg)`). For those the orthographic answer is
     *   still far closer to the browser than the previous 8·density² px
     *   camera was — the residual is named, not hidden.
     * - `translateZPx != 0` keeps the depth-scale path (which models
     *   translateZ under [TransformApplier.DEFAULT_PERSPECTIVE]) intact.
     *   Under a strict reading of §4.1 a translateZ with no perspective is
     *   invisible too, but its corpus carriers
     *   (`css-transforms/3d-rendering-context-*`) sit inside preserve-3d
     *   rendering contexts whose semantics are a separate mechanism; they
     *   currently score 0.984–0.992 on Android and are deliberately left
     *   untouched by this change.
     */
    fun appliesTo(perspectivePx: Float, translateZPx: Float): Boolean =
        perspectivePx <= 0f && translateZPx == 0f

    /**
     * Flatten the accumulated X/Y rotations to a scale pair.
     *
     * Degrees in, multipliers out. A negative cos (|θ| > 90°) is KEPT
     * rather than absolute-valued: CSS shows the mirrored back face of a
     * box rotated past 90°, and a negative `scaleX` in graphicsLayer is
     * exactly that mirror. (Hiding it is `backface-visibility`'s job, a
     * separate property — see the residual note in TransformApplier.)
     */
    fun of(rotateXDeg: Float, rotateYDeg: Float): Flattened {
        // Radians once per axis; cos is the whole projection.
        val ax = Math.toRadians(rotateXDeg.toDouble())
        val ay = Math.toRadians(rotateYDeg.toDouble())
        return Flattened(
            // rotateY foreshortens the X axis, rotateX the Y axis.
            scaleX = cos(ay).toFloat(),
            scaleY = cos(ax).toFloat(),
            // Only a two-axis rotation loses information here.
            shearDropped = shearResidual(rotateXDeg, rotateYDeg) != 0f,
        )
    }

    /**
     * The off-diagonal term a scale pair cannot carry.
     *
     * Flattening distributes over a product EXCEPT when an X rotation
     * meets a Y rotation. Composing R_y(β)·R_x(α) and dropping z gives
     *
     *     [ cosβ   sinα·sinβ ]
     *     [   0      cosα    ]
     *
     * whose diagonal is exactly [of]'s scale pair, but whose top-right
     * entry — `sinα·sinβ` — is a horizontal shear no `scaleX`/`scaleY`
     * pair reproduces. Every other pairing IS exact: a Z rotation
     * commutes through the flattening (R_z(γ)·R_x(α) flattens to
     * R_z(γ)·diag(1, cosα), i.e. the 2D rotation times the scale pair),
     * which is why `rotationZ` may stay a real graphicsLayer rotation.
     *
     * Returned (rather than asserted) so the applier can log the one
     * lossy shape instead of silently swallowing it. No test in the
     * wave-48 corpus carries a two-axis rotation — the census over all
     * 30 sections' per-test IR found single-axis rotateX / rotateY /
     * rotate3d only.
     */
    fun shearResidual(rotateXDeg: Float, rotateYDeg: Float): Float {
        val sx = sin(Math.toRadians(rotateXDeg.toDouble())).toFloat()
        val sy = sin(Math.toRadians(rotateYDeg.toDouble())).toFloat()
        val term = sx * sy
        // Snap float dust (sin(180°) is 1.2e-16, not 0) so a single-axis
        // rotation never reports a shear it does not have.
        return if (abs(term) < 1e-6f) 0f else term
    }
}
