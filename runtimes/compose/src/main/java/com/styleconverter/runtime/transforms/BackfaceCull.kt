package com.styleconverter.runtime.transforms

// Wave 52 (lane L4, native-near-misses T5) — the `backface-visibility: hidden`
// CULLING DECISION, split by `transform-style`.
//
// css-transforms-2 §10 (`backface-visibility`) hides the ELEMENT when its
// plane faces away from the viewer. What "the element" paints depends on
// §4.1.2 (`transform-style`):
//   * FLAT (the initial value): the element's subtree is flattened INTO the
//     element's plane, so when that plane is back-facing the whole flattened
//     picture — own box decoration AND every descendant — is the back face
//     and disappears together. StyleApplier's `alpha(0f)` on the element
//     chain is exactly that.
//   * PRESERVE_3D: the descendants are NOT flattened; each positioned child
//     is its own plane in the 3D rendering context the element extends, and
//     each keeps its OWN `backface-visibility` (initial `visible`). Only the
//     element's own box — its background and borders — is the back face.
//
// MEASURED (wave51-fix, css-transforms/composited-under-rotateY-180deg-
// preserve-3d, ios f 0.9565 · android f 0.9565): parent `rotateY(180deg);
// backface-visibility: hidden; transform-style: preserve-3d; width: 100`
// holds a 100×100 GREEN child. Chromium's frozen ref paints the green square
// at (16,16); both natives alpha'd the subtree and painted a BLANK canvas.
// The child never asked to be culled — the parent's flat-mode rule reached
// it through the flattening it was not in.
//
// The DEGREE rule (which rotations count as back-facing) is carried over
// VERBATIM from StyleApplier's banner and deliberately stays the element-
// local sum of X/Y rotation degrees: the matrix-aware own-m22 test was tried
// and REJECTED on css-transforms/backface-visibility-hidden-animated-001/002
// (`matrix3d(−1,0,0,0, 0,1,0,0, 0,0,−1,0, 0,0,0,1)` on both `#flip` and
// `#back` → accumulated identity → Chromium PAINTS; the corpus has no other
// carrier where the two rules disagree). A `matrix3d` therefore never flips
// here — same answer as before this file existed.
//
// What this file does NOT do (named, not silent): a culled preserve-3d
// element's own TEXT and its box-shadow still paint — the placeholder text is
// a child composable the modifier chain cannot separate from the layout
// node's children, and the shadow rides the effects step. A kept box-shadow
// is breadcrumbed ([SHADOW_KEPT_BREADCRUMB]); own text is not visible from
// StyleConfig, so it is named here only. The wave51-fix corpus has exactly
// ONE preserve-3d culled carrier (the test above) and it has neither
// (census: tools/titan/results/wave52-small-fixes/census.json,
// `t5.ownDecorationOnCulledPreserve3d = 0`).

// The extracted per-element config the decision and the strip read.
import com.styleconverter.runtime.StyleApplier.StyleConfig
// Unhandled-variant log: the kept box-shadow is recorded, not silent.
import com.styleconverter.runtime.PropertyTracker
// Empty outline — the stripped face's outline (css-ui-4 §3: none).
import com.styleconverter.runtime.borders.outline.OutlineConfig
// Empty border sides — the stripped face's four bands.
import com.styleconverter.runtime.borders.sides.AllBordersConfig
// The `backface-visibility` value (css-transforms-2 §10).
import com.styleconverter.runtime.interactions.BackfaceVisibilityMode
// Where the extractor files `backface-visibility`.
import com.styleconverter.runtime.interactions.InteractionConfig

object BackfaceCull {

    /** What StyleApplier's backface step must do for one element. */
    enum class Decision {
        /** `visible`, or `hidden` on a front-facing plane — paint normally. */
        NONE,
        /** `hidden` + back-facing + FLAT: the flattened subtree IS the back face → `alpha(0f)`. */
        HIDE_SUBTREE,
        /** `hidden` + back-facing + PRESERVE_3D: only the element's own box is the back face. */
        CULL_OWN_FACE,
    }

    /**
     * The element-local degree rule: total X or Y rotation in (90°, 270°)
     * mod 360 ⇔ cos θ < 0 ⇔ the plane's normal points away from the viewer.
     * Sums the `rotate` longhand axes with every `rotateX`/`rotateY` in the
     * `transform` function list; other functions (translate, scale, skew,
     * matrix*, rotateZ) never turn a plane's normal in this rule.
     */
    fun isBackFacing(transforms: TransformConfig): Boolean {
        // Y-axis total: the longhand (rotateY) plus every list RotateY.
        val ry = (transforms.rotateY ?: 0f) +
            transforms.functions.sumOf {
                when (it) {
                    is TransformFunction.RotateY -> it.degrees.toDouble()
                    else -> 0.0
                }
            }.toFloat()
        // X-axis total, the same way.
        val rx = (transforms.rotateX ?: 0f) +
            transforms.functions.sumOf {
                when (it) {
                    is TransformFunction.RotateX -> it.degrees.toDouble()
                    else -> 0.0
                }
            }.toFloat()
        // Normalise to [-180, 180] so 270° reads as −90° (edge-on, visible).
        fun normalised(deg: Float): Float {
            var d = deg % 360f
            if (d > 180f) d -= 360f
            if (d < -180f) d += 360f
            return d
        }
        // Strictly greater: an exactly edge-on 90° plane stays visible (§10
        // hides on a NEGATIVE z, and cos 90° is zero).
        return kotlin.math.abs(normalised(ry)) > 90f ||
            kotlin.math.abs(normalised(rx)) > 90f
    }

    /**
     * The decision, from the three lanes it depends on: the visibility flag
     * lives in InteractionConfig, the rotation in TransformConfig, and the
     * flattening mode in Transform3DConfig — which is why StyleApplier
     * resolves it cross-applier instead of any one of them.
     */
    fun decide(
        interactions: InteractionConfig,
        transforms: TransformConfig,
        transform3D: Transform3DConfig,
    ): Decision {
        // `backface-visibility: visible` (the initial value) never culls.
        if (interactions.backfaceVisibility != BackfaceVisibilityMode.HIDDEN) return Decision.NONE
        // A front-facing plane paints its front face — nothing to hide.
        if (!isBackFacing(transforms)) return Decision.NONE
        // §4.1.2: preserve-3d keeps the descendants as their own planes.
        return if (transform3D.transformStyle == TransformStyleValue.PRESERVE_3D)
            Decision.CULL_OWN_FACE
        else Decision.HIDE_SUBTREE
    }

    /** [decide] over one extracted [StyleConfig] — the applier's call shape. */
    fun decide(config: StyleConfig): Decision =
        decide(config.interactions, config.transforms, config.transform3D)

    /**
     * The config StyleApplier's DECORATION steps (borders, then colors) paint
     * for a [Decision.CULL_OWN_FACE] element: the element's own back face —
     * background colour, background image layers, border sides and outline
     * (css-transforms-2 §10 hides the element's own box) — is removed, and
     * EVERYTHING ELSE is kept byte-identical so layout, opacity (which wraps
     * the still-visible children — css-color-4 §3.3 group opacity), overflow,
     * padding and the transform itself are untouched. The border radius is
     * kept too: with no sides and no fill it paints nothing, and dropping it
     * would change which of StyleApplier's two radius routes runs.
     */
    fun stripOwnFace(config: StyleConfig): StyleConfig {
        // The box-shadow is part of the culled face too, but it rides the
        // effects step (shared with filters/clip/mask), so it is KEPT —
        // recorded, never silent (0 corpus carriers; see the header).
        if (config.effects.shadows.hasShadow) PropertyTracker.markUnhandled(SHADOW_KEPT_BREADCRUMB)
        // Background colour + image layers, border sides and outline go.
        return config.copy(
            colors = config.colors.copy(
                backgroundColor = null,
                backgroundImages = emptyList(),
            ),
            borders = config.borders.copy(
                sides = AllBordersConfig(),
                outline = OutlineConfig(),
            ),
        )
    }

    /** Breadcrumb for a culled preserve-3d face whose box-shadow still paints. */
    const val SHADOW_KEPT_BREADCRUMB = "BackfaceVisibility[preserve-3d-own-box-shadow-kept]"
}
