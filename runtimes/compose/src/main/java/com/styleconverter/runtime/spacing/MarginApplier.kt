package com.styleconverter.runtime.spacing

// MarginApplier — converts a MarginConfig into Modifier ops.
//
// Compose has no native margin modifier; the historical approach (used by the
// old SpacingApplier.applyMargin) was `Modifier.offset(x = left-right, y =
// top-bottom)`. That works for positive/negative absolute margins but CANNOT
// express horizontal centering (margin: 0 auto).
//
// Phase 2 upgrade: we replicate the old offset semantics for explicit length
// sides, and add wrapContentWidth(CenterHorizontally) / wrapContentHeight
// (CenterVertically) when `auto` appears on both opposite sides of an axis.
// This matches the most common CSS centering idiom and, importantly, keeps
// byte-identical rendering for px-only inputs (no auto → same code path as
// before).

import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object MarginApplier {

    fun apply(
        modifier: Modifier,
        config: MarginConfig,
        ctx: SpacingContext = SpacingContext(),
        isRtl: Boolean = false,
    ): Modifier {
        if (!config.hasMargin) return modifier
        val r = config.resolve(isRtl = isRtl)

        // Resolve each side to either a Dp (for lengths) or Auto. We inspect
        // the Auto pairs BEFORE computing offsets so auto sides contribute 0
        // to the offset math and let the centering modifier do the work.
        val topDp = lengthOrZero(r.top, ctx)
        val rightDp = lengthOrZero(r.right, ctx)
        val bottomDp = lengthOrZero(r.bottom, ctx)
        val leftDp = lengthOrZero(r.left, ctx)

        var result = modifier
        // Positive margins use Modifier.absolutePadding which inflates
        // the LAYOUT box (outer = inner + padding). This is the only way
        // sibling children in a Row/Column see the spacing — Compose's
        // Modifier.offset only translates the painted position and does
        // NOT push siblings, so a flex column with `margin-block-end: 8`
        // on child A used to render with A and B touching even though
        // visually shifted. Negative margins keep the offset path because
        // padding can't go negative in Compose.
        val padTop    = topDp.coerceAtLeast(0.dp)
        val padRight  = rightDp.coerceAtLeast(0.dp)
        val padBottom = bottomDp.coerceAtLeast(0.dp)
        val padLeft   = leftDp.coerceAtLeast(0.dp)
        if (padTop.value != 0f || padRight.value != 0f ||
            padBottom.value != 0f || padLeft.value != 0f) {
            // absolutePadding (vs padding) takes physical sides regardless
            // of layout direction — matches CSS top/right/bottom/left.
            result = result.absolutePadding(
                left = padLeft, top = padTop,
                right = padRight, bottom = padBottom
            )
        }
        // Negative-margin remainder: the ABS(positive) component is now
        // covered by padding above; subtract the negative components via
        // offset. (Most fixtures don't mix negative + positive — fall
        // through cleanly when all margins are >= 0.)
        val negX = (leftDp - padLeft) - (rightDp - padRight)
        val negY = (topDp - padTop) - (bottomDp - padBottom)
        if (negX.value != 0f || negY.value != 0f) {
            result = result.offset(x = negX, y = negY)
        }

        // Centering: in CSS, `margin-left: auto; margin-right: auto` centers
        // the element horizontally within its parent. In Compose we emulate
        // that with wrapContentWidth(CenterHorizontally) on a
        // fillMaxWidth'd parent. We keep this optional (only when both
        // inline sides are Auto) to avoid hijacking layout for asymmetric
        // auto cases (single-sided auto is CSS left-out-of-scope here).
        val leftAuto = r.left == MarginValue.Auto
        val rightAuto = r.right == MarginValue.Auto
        val topAuto = r.top == MarginValue.Auto
        val bottomAuto = r.bottom == MarginValue.Auto

        if (leftAuto && rightAuto) {
            // wrapContentWidth(unbounded=true) so child's intrinsic width is
            // preserved; Alignment centers it inside the incoming constraints.
            result = result.wrapContentWidth(Alignment.CenterHorizontally)
        } else if (leftAuto && !rightAuto) {
            // Single left auto pushes the element to the right edge.
            result = result.wrapContentWidth(Alignment.End)
        } else if (rightAuto && !leftAuto) {
            // Single right auto pushes the element to the left edge.
            result = result.wrapContentWidth(Alignment.Start)
        }
        if (topAuto && bottomAuto) {
            result = result.wrapContentHeight(Alignment.CenterVertically)
        }

        return result
    }

    /** Return the Dp for a MarginValue.Length, 0.dp for Auto / null. */
    private fun lengthOrZero(v: MarginValue?, ctx: SpacingContext): Dp = when (v) {
        is MarginValue.Length -> resolveToDp(v.value, ctx)
        MarginValue.Auto, null -> 0.dp
    }
}
