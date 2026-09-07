package com.styleconverter.runtime.layout.position

// Wave 22 (B-RC3) — END-EDGE anchoring for out-of-flow boxes whose only
// declared inset on an axis is `right` / `bottom` (css-position-3 §4.3).
//
// ## The measured defect
// PositionConfig.offsetX/offsetY treat `right`/`bottom` as NEGATED offsets
// from the TOP-LEFT anchor, so `bottom: 0; right: 0` resolved to
// absoluteOffset(0, 0) and painted at the top-left corner. On
// css-multicol/abspos-containing-block-outside-spanner the two root-level
// `position:absolute` 100×100 red divs — one `top:0; left:0`, one
// `bottom:0; right:0` (live IR at tools/titan/runs/wave21-final/sections/
// css-multicol/per-test-ir/wpt__css-multicol__abspos-containing-block-
// outside-spanner.json) — therefore stacked on top of each other: the
// wave21-final Android capture contains EXACTLY ONE 100×100 red region at
// x 0..99 / y 0..99 for two red divs. The web capture (the pixel oracle)
// paints them at (0,0)-(99,99) and (290,500)-(389,599) on the 390×600
// canvas; the iOS capture already matches it (both reds present, the second
// at x 290..389 / y 500..599), because iOS's PositionApplier.
// anchoredInsetOffset picks a `.trailing`/`.bottom` frame ALIGNMENT before
// applying the same negated offset. This file is that iOS rule table,
// mirrored for Compose.
//
// ## The composition (why the negated offset stays)
// Anchoring places the box's END edge flush with the containing block's end
// edge (x = cbWidth − boxWidth); PositionConfig.offsetX then contributes
// `−right`, so the painted position is cbWidth − boxWidth − right. For the
// multicol root: 390 − 100 − 0 = 290 (and 600 − 100 − 0 = 500), exactly the
// web oracle's coordinates. Keeping the negated offset where it is means
// the start-anchored path (`left`/`top`) and every css-position pin stay
// byte-identical — this file only chooses the ANCHOR.
//
// Pure decisions + arithmetic (Dp is a JVM value class, no Android runtime)
// so the whole rule table is pinnable without Robolectric — the standing
// constraint of this suite (CanvasRootHoistTest / AbsposOverflowMeasureTest).

// Dp is the currency of the position lane's insets (PositionConfig); the
// px conversion happens at the one Density-bearing call site (the anchor).
import androidx.compose.ui.unit.Dp

object EndInsetAnchor {

    /**
     * The containing block's END edge on one axis, in the anchor slot's own
     * coordinate space — or null when this axis must keep the START anchor.
     *
     * The pin table (A1–A4, shared with the iOS oracle
     * PositionApplier.anchoredInsetOffset):
     *  - A1 end-only inset + known containing-block extent → that extent
     *    (the box's end edge is then placed flush with it).
     *  - A2 BOTH insets declared → null: over-constrained, and in an LTR
     *    containing block `left`/`top` wins (css-position-3 §4.3), which
     *    the existing start anchor + positive offset already render.
     *  - A3 start-only or no inset → null: the start anchor is correct
     *    (an all-auto-inset box is at its static position — §3.1).
     *  - A4 end-only inset but UNKNOWN containing-block extent → null:
     *    there is nothing honest to measure from, so the box keeps the
     *    pre-wave-22 start-anchored placement rather than guessing. This is
     *    a documented degradation, not a silent fallthrough — every caller
     *    that can supply an extent does.
     */
    fun endEdge(anchorsFromEnd: Boolean, containingBlockExtent: Dp?): Dp? =
        // A2/A3 fold into the predicate (PositionConfig.anchorsFromEndX/Y);
        // A4 folds into the extent's nullability.
        if (anchorsFromEnd) containingBlockExtent else null

    /**
     * Where an anchor slot must PLACE a measured box on one axis, in whole
     * device px (Compose places on integer px).
     *
     * A5: with an end edge, the box's own end edge sits flush with it —
     * `endEdgePx − boxPx`. The caller's PositionApplier offset (`−right`)
     * then pulls the box inward by the declared inset, producing the CSS
     * used position `cb − box − right` (css-position-3 §4.3).
     *
     * A6: with no end edge (null) the slot origin is the anchor — 0 — which
     * is the byte-identical pre-wave-22 behavior for every start-anchored,
     * static-position and unknown-extent box.
     *
     * No floor at 0: a box WIDER than its containing block anchors at a
     * negative coordinate and overflows toward the start edge, which is
     * what the browser paints (an out-of-flow box may overflow its
     * containing block — css-position-3 §2.1) and what Compose's
     * unclipped `place()` renders.
     */
    fun placePx(endEdgePx: Float?, boxPx: Int): Int =
        // A6 — start anchor.
        if (endEdgePx == null) 0
        // A5 — end anchor; rounded like every other placement in the lane
        // (ComponentRenderer.absposOverflowMeasure's roundToInt).
        else kotlin.math.round(endEdgePx - boxPx).toInt()
}
