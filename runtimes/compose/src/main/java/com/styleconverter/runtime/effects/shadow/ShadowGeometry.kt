package com.styleconverter.runtime.effects.shadow

// Pure geometry only — androidx.compose.ui.geometry is plain Kotlin, so this
// file stays runnable in the JVM unit suite (this repo runs no Robolectric;
// android.graphics is a throwing stub there). The draw-time halves (Paint,
// BlurMaskFilter, Path) live in [OutsetShadowPainter] and [InsetShadowPainter].
import androidx.compose.ui.geometry.Rect
// Compose Color's `copy` is pure Kotlin (packed-ULong arithmetic), so the alpha
// attenuation below is JVM-testable too.
import androidx.compose.ui.graphics.Color

/**
 * Value-level geometry for the box-shadow painters — the JVM-testable half of
 * their draw lambdas, mirroring how the backdrop lane keeps its coordinate
 * math in `BackdropSampleGeometry` instead of inline in the modifier node.
 *
 * ## Why the position offset appears here (the un-offset-slot bug)
 * `StyleApplier` chains the effects step (step 3) OUTSIDE the layout step
 * (step 4), and step 4 ends with `PositionApplier.applyPosition` →
 * `Modifier.absoluteOffset`. A draw node installed at step 3 is therefore
 * OUTER of the offset: its local (0,0) is the element's UN-offset layout
 * slot, while the element's own background/borders (steps 5–6, INNER of the
 * offset) paint at the offset one. css-backgrounds-3 §6.1 attaches the
 * shadow to the BOX — so the shadow perimeter must slide by the same
 * resolved offset the element itself is about to be slid by, or a
 * `position: absolute; top: 125px; left: 75px` element casts its shadow up
 * at the slot it never visually occupied (the measured WPT
 * `backdrop-filter-box-shadow.html` failure: the grey blob painted in the
 * explanatory-text band instead of inside the green box).
 *
 * ## Why the margin bands appear here (the margin-box bug — retro round-2 F1)
 * The same "step 3 is outer of step 4" chain puts the shadow draw node OUTER
 * of MarginApplier's `absolutePadding` too, so its `size` is the element's
 * MARGIN box (CSS2 §8.1 Box dimensions), not the border box that
 * css-backgrounds-3 §6.1.1 clips the shadow against ("it is clipped inside
 * the border-box of the element"). Retro R6's knockout ([borderBoxRect]) and
 * the perimeter ([outsetShadowRect]) both read that `size`, so on a margined
 * element the Difference clip knocked out the whole margin box and the ring
 * sat one margin band too far out — skeptic S3 predicted R6's own fixture
 * fixtures/properties/effects/box-shadow-opacity-knockout.json
 * BSK_Op55_RingDominant (10px box, `margin: 24px`, 20px spread ring) RED on
 * Android: a 98×98 ring with a 24px ground gap around the fill instead of
 * the 50×50 ring hugging it. Both rects now take the four resolved bands
 * (MarginApplier.resolvedInsets — literally the numbers step 4 adds as
 * padding) and subtract them FIRST, exactly as BackdropSampleGeometry.
 * borderBox does for the backdrop lane; zero bands reduce every edge to the
 * pre-fix formula, which keeps the margin-less corpus byte-identical.
 */
object ShadowGeometry {

    /**
     * The outset shadow's perimeter rectangle in the DRAW NODE's local
     * space — i.e. the border box translated by the position offset and the
     * shadow's own offset, then inflated by the spread
     * (css-backgrounds-3 §6.1.1: spread expands the perimeter uniformly on
     * all four sides; the corner-radius growth it also mandates is handled
     * by the caller because radii, not edges, live there).
     *
     * @param widthPx/[heightPx] the draw node's laid-out size in px — the
     *   MARGIN box (see the class KDoc), unchanged by positioning because
     *   `Modifier.absoluteOffset` translates its child without resizing it.
     * @param shadowOffsetXPx/[shadowOffsetYPx] the CSS `box-shadow`
     *   horizontal/vertical offset in px (§6.1 lengths 1–2).
     * @param spreadPx the CSS spread distance in px (§6.1 length 4); may be
     *   negative (perimeter contraction) — callers clamp the CORNER growth
     *   separately, matching browser behavior.
     * @param positionOffsetXPx/[positionOffsetYPx] the element's resolved
     *   position offset in px (`PositionApplier.resolvedOffset` — literally
     *   the value the `absoluteOffset` at step 4 will apply), 0 for a
     *   static box. SIGNED on purpose: a `right`/`bottom` inset resolves
     *   negative and must pull the shadow the same way it pulls the box.
     * @param marginLeftPx…[marginBottomPx] the resolved POSITIVE margin band
     *   on each physical side in px (MarginApplier.resolvedInsets — the very
     *   `absolutePadding` step 4 chains INNER of this node), 0 for a
     *   margin-less box. Subtracted from the node's box before anything else
     *   so the perimeter hugs the BORDER box (§6.1.1). Positive-only on
     *   purpose, like the backdrop lane's: a negative margin rides
     *   `Modifier.offset`, which translates the node without resizing it, so
     *   it has no band to subtract — negative values clamp to 0 ([band]).
     */
    fun outsetShadowRect(
        widthPx: Float,
        heightPx: Float,
        shadowOffsetXPx: Float,
        shadowOffsetYPx: Float,
        spreadPx: Float,
        positionOffsetXPx: Float = 0f,
        positionOffsetYPx: Float = 0f,
        marginLeftPx: Float = 0f,
        marginTopPx: Float = 0f,
        marginRightPx: Float = 0f,
        marginBottomPx: Float = 0f,
    ): Rect = Rect(
        // Margin bands move the near edges IN and the far edges IN; both
        // offsets translate; only the spread inflates. With zero bands and a
        // zero position offset every edge reduces to the pre-fix formula,
        // which is what keeps all static, margin-less captures byte-identical.
        left = band(marginLeftPx) + positionOffsetXPx + shadowOffsetXPx - spreadPx,
        top = band(marginTopPx) + positionOffsetYPx + shadowOffsetYPx - spreadPx,
        right = widthPx - band(marginRightPx) + positionOffsetXPx + shadowOffsetXPx + spreadPx,
        bottom = heightPx - band(marginBottomPx) + positionOffsetYPx + shadowOffsetYPx + spreadPx,
    )

    /**
     * The element's BORDER BOX in the draw node's local space — the outset
     * shadow's KNOCKOUT region (retro R6, audit findings A11#2 / A10#5), the
     * inset painter's clip box, and the reference box the corner-radius
     * percentages resolve against ([cornerRadiusPx]).
     *
     * css-backgrounds-3 §6.1.1 (box-shadow): "The shadow is drawn outside the
     * border edge only: it is clipped inside the border-box of the element."
     * Android painted the whole Gaussian UNDER the box, so a translucent
     * element showed its own shadow through itself (images.combos
     * 003_Images_Decorated: Android fill (150,110,132) = fill@0.55 over an
     * opaque ring vs iOS/web (139,54,54) = fill@0.55 over the canvas).
     *
     * This is [outsetShadowRect] with a ZERO shadow offset and ZERO spread:
     * the node minus its margin bands, translated by the position offset
     * only. The spread inflates the shadow's perimeter (§6.1.1), never the
     * box that occludes it, and the shadow's own offset moves the shadow,
     * never the box — kept as its own function so the knockout can never
     * silently pick up either term. The margin bands are the round-2 F1
     * correction (class KDoc): `borderBoxRect(58, 58, margins 24)` is the
     * (24,24)–(34,34) box of BSK_Op55_RingDominant, not the whole 58×58 node.
     *
     * @param marginLeftPx…[marginBottomPx] as on [outsetShadowRect].
     */
    fun borderBoxRect(
        widthPx: Float,
        heightPx: Float,
        positionOffsetXPx: Float = 0f,
        positionOffsetYPx: Float = 0f,
        marginLeftPx: Float = 0f,
        marginTopPx: Float = 0f,
        marginRightPx: Float = 0f,
        marginBottomPx: Float = 0f,
    ): Rect = Rect(
        // Same translation the element's own background receives at step 6
        // (absoluteOffset at step 4), the margin bands stripped off each
        // side, no inflation of any kind.
        left = band(marginLeftPx) + positionOffsetXPx,
        top = band(marginTopPx) + positionOffsetYPx,
        right = widthPx - band(marginRightPx) + positionOffsetXPx,
        bottom = heightPx - band(marginBottomPx) + positionOffsetYPx,
    )

    /**
     * One corner radius component in px for the shadow perimeter / knockout:
     * css-backgrounds-3 §4.1 resolves a border-radius PERCENTAGE against the
     * "corresponding dimension of the border box" — so the reference length
     * is the BORDER box's width or height ([borderBoxRect]), never the draw
     * node's (the margin box — before round-2 F1 `border-radius: 50%` on a
     * margined element inflated its shadow's corner by the margin). A Dp
     * radius is already resolved and is used as is. [growPx] is §6.1.1's
     * spread growth for a shadow layer (0 for the knockout box); the caller
     * clamps it at 0 because a negative spread contracts the perimeter but
     * never the radii.
     */
    fun cornerRadiusPx(dpPx: Float, fraction: Float?, borderBoxDimPx: Float, growPx: Float): Float =
        (fraction?.times(borderBoxDimPx) ?: dpPx) + growPx

    /**
     * A margin band clamped at zero — the same rule BackdropSampleGeometry.
     * borderBox applies: MarginApplier turns only the POSITIVE part of a
     * margin into `absolutePadding` (a negative margin becomes an `offset`,
     * which does not change the node's size), so a caller that hands us a
     * negative value can never GROW the border box.
     */
    private fun band(px: Float): Float = if (px > 0f) px else 0f

    /**
     * The shadow colour as the element's transparency group composites it
     * (retro R6, audit finding A11#2).
     *
     * css-color-4 §3.3: "The opacity property applies the specified opacity
     * to the element as a whole, including its contents" — the box-shadow is
     * part of the element's paint, so a 0.55-opacity element casts a
     * 0.55-alpha shadow. The shadow paints at StyleApplier step 3, OUTSIDE
     * the group ColorApplier opens at step 6, so it cannot ride the group's
     * alpha; multiplying the paint alpha instead is exact wherever shadow and
     * box do not overlap — and [borderBoxRect]'s knockout guarantees the box
     * region is never under a shadow. Residual (documented, not hidden): two
     * OVERLAPPING shadow layers composite per layer here where the group
     * would flatten them first, so their overlap reads slightly darker than
     * web's — no corpus or fixture carrier declares overlapping shadows on a
     * translucent element.
     *
     * Measured before this fix on images.combos 003_Images_Decorated
     * (`box-shadow: 0 0 0 3px #3498db; opacity: .55`, canvas (26,26,46)):
     * Android ring (52,152,219) = the declared colour at FULL alpha; iOS/web
     * (40,95,141) = (26 + 0.55·26, 26 + 0.55·126, 46 + 0.55·173) — the ring
     * at 0.55 over the canvas. The clamp is css-color-4 §3.3's computed-value
     * clamp of opacity to [0,1].
     */
    fun attenuate(color: Color, elementAlpha: Float): Color =
        color.copy(alpha = color.alpha * elementAlpha.coerceIn(0f, 1f))
}
