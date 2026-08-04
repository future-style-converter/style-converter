package com.styleconverter.runtime.effects.shadow

// Pure geometry only — androidx.compose.ui.geometry is plain Kotlin, so this
// file stays runnable in the JVM unit suite (this repo runs no Robolectric;
// android.graphics is a throwing stub there). The draw-time half (Paint,
// BlurMaskFilter, Path) stays in [ShadowApplier].
import androidx.compose.ui.geometry.Rect

/**
 * Value-level geometry for the box-shadow painter — the JVM-testable half of
 * [ShadowApplier]'s draw lambdas, mirroring how the backdrop lane keeps its
 * coordinate math in `BackdropSampleGeometry` instead of inline in the
 * modifier node.
 *
 * ## Why the position offset appears here (the un-offset-slot bug)
 * `StyleApplier` chains the effects step (step 3) OUTSIDE the layout step
 * (step 4), and step 4 ends with `PositionApplier.applyPosition` →
 * `Modifier.absoluteOffset`. A draw node installed at step 3 is therefore
 * OUTER of the offset: its local (0,0) is the element's UN-offset layout
 * slot, while the element's own background/borders (steps 5–6, INNER of the
 * offset) paint at the offset one. css-backgrounds-3 §7.1 attaches the
 * shadow to the BOX — so the shadow perimeter must slide by the same
 * resolved offset the element itself is about to be slid by, or a
 * `position: absolute; top: 125px; left: 75px` element casts its shadow up
 * at the slot it never visually occupied (the measured WPT
 * `backdrop-filter-box-shadow.html` failure: the grey blob painted in the
 * explanatory-text band instead of inside the green box).
 */
object ShadowGeometry {

    /**
     * The outset shadow's perimeter rectangle in the DRAW NODE's local
     * space — i.e. the border box translated by the position offset and the
     * shadow's own offset, then inflated by the spread
     * (css-backgrounds-3 §7.1.1: spread expands the perimeter uniformly on
     * all four sides; the corner-radius growth it also mandates is handled
     * by the caller because radii, not edges, live there).
     *
     * @param widthPx/[heightPx] the draw node's laid-out size in px —
     *   unchanged by positioning, because `Modifier.absoluteOffset`
     *   translates its child without resizing it.
     * @param shadowOffsetXPx/[shadowOffsetYPx] the CSS `box-shadow`
     *   horizontal/vertical offset in px (§7.1 lengths 1–2).
     * @param spreadPx the CSS spread distance in px (§7.1 length 4); may be
     *   negative (perimeter contraction) — callers clamp the CORNER growth
     *   separately, matching browser behavior.
     * @param positionOffsetXPx/[positionOffsetYPx] the element's resolved
     *   position offset in px (`PositionApplier.resolvedOffset` — literally
     *   the value the `absoluteOffset` at step 4 will apply), 0 for a
     *   static box. SIGNED on purpose: a `right`/`bottom` inset resolves
     *   negative and must pull the shadow the same way it pulls the box.
     */
    fun outsetShadowRect(
        widthPx: Float,
        heightPx: Float,
        shadowOffsetXPx: Float,
        shadowOffsetYPx: Float,
        spreadPx: Float,
        positionOffsetXPx: Float = 0f,
        positionOffsetYPx: Float = 0f,
    ): Rect = Rect(
        // Both offsets translate; only the spread inflates. With a zero
        // position offset every edge reduces to the pre-fix formula, which
        // is what keeps all static-element captures byte-identical.
        left = positionOffsetXPx + shadowOffsetXPx - spreadPx,
        top = positionOffsetYPx + shadowOffsetYPx - spreadPx,
        right = widthPx + positionOffsetXPx + shadowOffsetXPx + spreadPx,
        bottom = heightPx + positionOffsetYPx + shadowOffsetYPx + spreadPx,
    )
}
