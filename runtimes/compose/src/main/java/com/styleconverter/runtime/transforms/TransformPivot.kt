package com.styleconverter.runtime.transforms

// Pure arithmetic — no Compose imports, JVM-pinnable (TransformPivotTest).

/**
 * The transform pivot in the coordinate frame the transform node actually
 * draws in — retro R1, finding A11#0.
 *
 * ## The measured defect
 * StyleApplier.applyConfig chains the transform (step 2) OUTER to the
 * layout step whose last modifier is PositionApplier's `absoluteOffset`
 * (step 4). Compose's offset modifier reports the CHILD's size and places
 * the child at (left, top) inside it, so the transform node's `size` is
 * the child's own box while its content sits (left, top) away from the
 * node origin. Every legacy pivot was `size · originFraction` — the
 * child's local centre expressed in the PARENT frame, missing (left, top).
 * The finding's control (child left 60 / top 20, 40x40, rotate(45deg))
 * therefore pivoted about (20, 20) instead of (80, 40): predicted centre
 * (48.3, 76.6) rel. parent, measured Android bbox (36,65)-(91,111) against
 * iOS/web (69,29)-(122,82). Same mechanism on the WPT captures:
 * css-transforms/css-transform-3d-rotateY-positive (240x120 box, left 20,
 * rotateY(60deg) → scaleX(cos 60°)) sits at x 86..205 on Android against
 * the frozen ref's 96..215 — exactly the predicted left·(1 − cos 60°) =
 * 10 px shift. The wave-1 reading "Android clips the child vertically"
 * was the capture canvas edge catching the mis-pivoted diamond; it was
 * never a clip.
 *
 * ## The fix
 * Conjugate every pivot by the SAME value the offset modifier applies —
 * PositionApplier.resolvedOffset, literally the value form of the step-4
 * modifier (the pattern EffectsFacade's backdrop/shadow/blend consumers
 * already use for the same "step 2/3 is OUTER of step 4" geometry). The
 * two cannot disagree about how far the box moved. Two known limits,
 * named (skeptic S2, retro round 2), neither with a wave49-final carrier:
 *  - PERCENTAGE insets. resolvedOffset returns PositionConfig.offsetX/Y,
 *    which hold the bare percentage NUMBER read as dp, while the layout
 *    step routes such a box through PositionApplier.percentInsetPositioned
 *    (resolved against the containing block in a composed lane). The pivot
 *    is therefore shifted by that bare number in px — WRONG, not "the
 *    legacy pivot" as an earlier draft of this note claimed — the same gap
 *    MarginApplier.resolvedInsets and the effects consumers document.
 *    S2/scan.py finds zero percent-inset + transform components in the
 *    1435 wave49-final docs. The fix is to thread the resolved offset out
 *    of the composed lane (layout/position owns it, not this folder).
 *  - MARGIN bands. LayoutFacade chains margin (absolutePadding, or
 *    Modifier.offset for a negative margin) INSIDE this transform node
 *    too, so the node's `size` is the MARGIN box: an asymmetric margin
 *    moves the border-box centre by ((mR − mL)/2, (mB − mT)/2) that this
 *    pivot does not see, and a negative margin is an offset exactly like
 *    the abspos one (MarginApplier.resolvedInsets is the value form the
 *    effects consumers already thread). Only corpus carrier: backface-
 *    visibility-hidden-005 (margin-top −100, rotateY(180deg) + hidden),
 *    which the degree rule culls, so no gate cell moves. Pre-existing
 *    (wave 49 and earlier); follow-up: conjugate by resolvedInsets and pin
 *    it with an asymmetric-margin row in transform-abspos-pivot.json.
 */
object TransformPivot {

    /**
     * One pivot axis in px inside the transform node's frame.
     *
     * @param originDp   a LENGTH transform-origin component (css-transforms-1
     *                   §5: lengths reference the border box's top-left) in
     *                   dp, or null when the origin is a keyword/percentage.
     * @param density    dp → px factor of the draw/layer scope.
     * @param originFraction the keyword/percentage origin as a fraction of
     *                   the element's own size (css-transforms-1 §4).
     * @param sizePx     the node's own size on this axis, px.
     * @param shiftPx    the position offset the step-4 modifier applies INSIDE
     *                   this node (PositionApplier.resolvedOffset), px.
     */
    fun axisPx(originDp: Float?, density: Float, originFraction: Float, sizePx: Float, shiftPx: Float): Float =
        // Length origin wins over the fraction (the per-axis fallback every
        // legacy path used), then the offset conjugation.
        (originDp?.let { it * density } ?: (sizePx * originFraction)) + shiftPx
}
