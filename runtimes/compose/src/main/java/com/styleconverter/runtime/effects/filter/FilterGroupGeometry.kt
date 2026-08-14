package com.styleconverter.runtime.effects.filter

// Pure geometry only — androidx.compose.ui.geometry is plain Kotlin, so this
// file stays runnable in the JVM unit suite (this repo runs no Robolectric;
// android.graphics is a throwing stub there). The draw-time half (saveLayer,
// Paint, ColorFilter) stays in [FilterApplier.applyGroupColorFilters].
import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.min

/**
 * Value-level geometry for the element-filter GROUP layer — the JVM-testable
 * half of [FilterApplier.applyGroupColorFilters], mirroring how the shadow
 * lane keeps its coordinate math in `ShadowGeometry` and the backdrop lane in
 * `BackdropSampleGeometry` instead of inline in the modifier node.
 *
 * ## Why a group layer needs its own bounds (the un-offset-slot crop bug)
 * `StyleApplier` chains the effects step (step 3) OUTSIDE the layout step
 * (step 4), and step 4 ends with `PositionApplier.applyPosition` →
 * `Modifier.absoluteOffset`. A layer installed at step 3 is therefore OUTER
 * of the offset: its local (0,0) is the element's UN-offset layout slot,
 * while the element's paint (background at step 6, borders at step 5, its
 * children) lands at the OFFSET slot inside it.
 *
 * The previous colour-matrix path was `graphicsLayer { renderEffect = … }`.
 * HWUI renders a RenderNode that carries a RenderEffect into an offscreen
 * buffer SIZED TO THE NODE'S OWN BOUNDS, so everything the element painted
 * outside its un-offset slot was silently discarded. Measured on WPT
 * `css/filter-effects/backdrop-filter-plus-filter.html` (wave40-final): the
 * `.bluebox` (`filter: invert(1)`, `position: absolute; left: 60px;
 * top: 110px`) slides its whole paint 60/110px outside the layer's slot —
 * the offset box and the slot do not intersect — and the Android capture
 * carries ZERO non-white pixels for it (Android SSIM 0.9168 vs web 1.0000),
 * while the sibling `.orangebox` (no filter → no effect layer) renders at
 * its offset exactly. The same buffer-crop physics was measured on
 * `backdrop-filter-isolation-isolate.html`, where `CompositingStrategy
 * .Offscreen` (`performance/IsolationApplier`) clips the subtree to the
 * un-offset 100×100 slot to the pixel (out of this lane's tree; reported).
 *
 * The fix: apply the matrix with `canvas.saveLayer(bounds, paint)` instead —
 * Skia's own group-with-colour-filter mechanism, available on every API
 * level — and hand it bounds that COVER the offset paint, computed here.
 */
object FilterGroupGeometry {

    /**
     * The group layer's bounds in the DRAW NODE's local space: the union of
     * the un-offset node box `(0,0,w,h)` and the same box translated by the
     * element's resolved position offset (`PositionApplier.resolvedOffset`,
     * the value form of the very `absoluteOffset` step 4 chains).
     *
     * Union rather than the offset box alone: a zero offset must degenerate
     * to EXACTLY the pre-fix `(0, 0, w, h)` rect (the byte-identity guarantee
     * for every static element, i.e. all committed baseline captures), and a
     * union keeps that identity by construction while still covering the
     * paint of an offset box in every direction — `right`/`bottom` insets
     * resolve to NEGATIVE offsets (PositionConfig.offsetX), so both signs
     * must extend the bounds.
     *
     * Deliberately NOT covering: children that overflow the element's own
     * box. Filter Effects 1 §5 applies `filter` to the whole subtree
     * including overflow, but the discarded RenderEffect path cropped those
     * exactly the same way, so this keeps parity with every committed
     * capture rather than silently changing a second behaviour in one fix.
     *
     * @param widthPx/[heightPx] the draw node's laid-out size in px —
     *   unchanged by positioning, because `Modifier.absoluteOffset`
     *   translates its content without resizing the node.
     * @param positionOffsetXPx/[positionOffsetYPx] the resolved position
     *   offset in px; 0 for a static box. SIGNED — see above.
     * @return the saveLayer bounds. Never empty: the union always contains
     *   the node's own `(0,0,w,h)`.
     */
    fun groupLayerBounds(
        widthPx: Float,
        heightPx: Float,
        positionOffsetXPx: Float,
        positionOffsetYPx: Float,
    ): Rect = Rect(
        // A negative offset slides paint left/up past the slot origin, so the
        // union's near edges are the smaller of 0 and the offset.
        left = min(0f, positionOffsetXPx),
        top = min(0f, positionOffsetYPx),
        // A positive offset slides paint right/down past the slot's far
        // edges, so the far edges are the larger of the un-offset box's and
        // the offset box's.
        right = max(widthPx, positionOffsetXPx + widthPx),
        bottom = max(heightPx, positionOffsetYPx + heightPx),
    )
}
