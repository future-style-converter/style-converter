package com.styleconverter.runtime.sizing

// FitContentSqueeze — css-sizing-3 §5.1 fit-content under a SQUEEZING
// container (wave 42, lane W3).
//
// ## The measured defect (frozen wave41-final Android captures)
// A bare `width: fit-content` box renders through Modifier.wrapContentWidth,
// which measures its content with the INCOMING max — so when the container
// offers ZERO width the content is measured at zero and the box paints
// nothing. css-sizing-3 §5.1 says fit-content is
//   max(min-content, min(available, max-content))
// i.e. the box NEVER shrinks below its min-content size; a too-small
// container is overflowed, not honoured. MEASURED on css-values/calc-size/
// calc-size-min-max-sizes-001 and -004: a `width: fit-content; height:
// 100px` green box inside a `width: 0px` outer div, whose content's
// min-content contribution is 100px (a calc-size() child — see
// CalcSizeLayout's contribution overrides). The browser ref paints a
// 100×100 green square; Android painted NO GREEN AT ALL (wptPass=false at
// ssim 0.9542, presence veto — the same veto class as the flex family).
//
// ## Why a wrapper, not a wrapContentWidth replacement
// Replacing the wrapContent lane wholesale would move every bare
// fit-content box in the corpus (148 width occurrences) through new
// measurement code for a two-test defect — the wave-1 "+2px placeholder"
// lesson forbids exactly that. This node is chained INSIDE the existing
// wrapContentWidth and is a PASS-THROUGH whenever the container offers at
// least the content's min-content width (measure with untouched
// constraints, report the child verbatim): the squeeze path only engages in
// the state that today collapses to nothing, so no currently-painted
// geometry can move. It is also gated on wptCaptureMode at the call site
// (SizingApplier) so the dark-stage/327-pair chain carries no extra node.

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.styleconverter.runtime.layout.IntrinsicChannel

internal object FitContentSqueeze {

    /** Log tag — refusals must name this lane. */
    private const val TAG = "FitContentSqueeze"

    /** Refusal wording, per the no-silent-fallthrough rule. */
    private const val REFUSAL =
        "css-sizing-3 §5.1 fit-content min-content floor skipped — this " +
            "box's subtree has no intrinsic channel; the box keeps the " +
            "wrapContent measure (collapsing under a zero-width container, " +
            "the pre-wave-42 state)."

    /**
     * Pure squeeze decision, JVM-pinned (CalcSizeValueTest): does the
     * incoming max squeeze the box below its content minimum? Only then
     * does the wrapper leave the pass-through path.
     */
    fun squeezed(incomingMax: Int, contentMin: Int): Boolean =
        incomingMax != Constraints.Infinity && contentMin > incomingMax

    /**
     * Width-axis guard. Chained INSIDE wrapContentWidth by SizingApplier's
     * bare-fit-content branch (WPT capture only). The HEIGHT axis has no
     * corpus case (no bare fit-content height under a squeezing container
     * anywhere in the wave41-final sections) and deliberately has no twin
     * yet — adding unexercised measurement code is how silent regressions
     * happen; this comment is the non-silent record of that decision.
     */
    fun Modifier.fitContentWidthSqueezeGuard(): Modifier = this.layout { measurable, constraints ->
        // The content's min-content width — the §5.1 floor. Guarded: a
        // refusing subtree (SubcomposeLayout below) logs once and keeps
        // the pre-wave-42 wrapContent measure byte-for-byte.
        val contentMin = IntrinsicChannel.probe(TAG, REFUSAL) {
            measurable.minIntrinsicWidth(
                if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity
            )
        }
        if (contentMin == null || !squeezed(constraints.maxWidth, contentMin)) {
            // PASS-THROUGH: measure with byte-identical constraints and
            // report the child verbatim — the wrapper adds a layout node
            // but not a measurable difference (identity measure policy).
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        } else {
            // SQUEEZE: the used width is the min-content floor (§5.1 with
            // available < min-content ⇒ max() picks min-content). Measure
            // the content TIGHT at the floor so the background (chained
            // inside, StyleApplier step 6) paints the full box; report the
            // incoming-clamped size so the parent's flow geometry cannot
            // grow (ExactWidthOverflow's report philosophy); start-anchor
            // so the ink spills toward inline-END like the browser.
            val placeable = measurable.measure(
                constraints.copy(minWidth = contentMin, maxWidth = contentMin))
            layout(constraints.maxWidth, placeable.height) {
                placeable.placeRelative(0, 0)
            }
        }
    }
}
