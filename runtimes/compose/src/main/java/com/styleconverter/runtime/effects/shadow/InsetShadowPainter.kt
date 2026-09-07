package com.styleconverter.runtime.effects.shadow

// Draw-time half of the INSET box-shadow painter — carved out of ShadowApplier
// in retro round 2 (lane F1) under the file-size rule, the way retro R6 carved
// out OutsetShadowPainter: ShadowApplier had crept back over the ~300-line
// split threshold (324) before the margin-box threading added its lines. The
// body is the private `applyInsetShadows` that lived there, with ONE geometry
// change — the border box now comes from ShadowGeometry.borderBoxRect with the
// margin bands subtracted (see paint). Deliberately NOT named `*Applier`:
// coverage-audit.mjs / doc-staleness-check count dedicated property appliers
// by that suffix, and this is a helper of the BoxShadow applier.
import androidx.compose.ui.Modifier
// Content first, then the shadow ON TOP of it (an inset shadow is inside the
// box, css-backgrounds-3 §6.1.1: "drawn inside the padding edge only").
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
// ClipOp.Intersect — nothing of the inset shadow may leave the border box.
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
// One canvas transform shifts the whole clip+hole block — see paint.
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
// The resolved margin bands ride in as a value (retro round-2 F1) — see paint.
import com.styleconverter.runtime.spacing.MarginInsets

/**
 * Paints every INSET `box-shadow` layer of one element, over its content and
 * clipped to its border box (css-backgrounds-3 §6.1.1). Entered only from
 * [ShadowApplier.applyFullShadow]. The chain shape is unchanged by the
 * carve-out — one `drawWithContent` node, which ShadowPositionOffsetTest /
 * ShadowMarginBoxTest pin.
 */
internal object InsetShadowPainter {

    /**
     * Apply inset shadows - drawn inside the element boundary.
     *
     * Inset shadows are implemented by:
     * 1. Clipping to the element boundary
     * 2. Drawing the shadow from outside inward (inverted)
     *
     * @param positionOffset the element's resolved position offset — see
     *   ShadowApplier.applyFullShadow's KDoc.
     * @param marginInsets the element's resolved POSITIVE margin bands
     *   (retro round-2 F1, skeptic S3 must-fix — "the inset painter shares
     *   the defect"). This node is OUTER of step 4's margin `absolutePadding`
     *   so `size` is the MARGIN box (CSS2 §8.1); before F1 the clip box and
     *   the hole were the whole node, so a margined element's inset shadow
     *   hugged its margin edge instead of its border edge. The bands are
     *   folded into the ONE translate below together with the position
     *   offset (ShadowGeometry.borderBoxRect gives both the origin and the
     *   size), so clip, outer rect and hole can never shear apart. Default
     *   NONE keeps every margin-less caller byte-identical. Zero corpus /
     *   dark-stage carriers today (no inset shadow on a margined element).
     */
    fun paint(
        modifier: Modifier,
        shadows: List<ShadowData>,
        cornerRadius: Dp,
        positionOffset: DpOffset = DpOffset.Zero,
        marginInsets: MarginInsets = MarginInsets.NONE,
    ): Modifier {
        return modifier.drawWithContent {
            // Draw the content first. NOT translated: the content chain
            // already contains step 4's absoluteOffset and absolutePadding,
            // so it lands in the border box on its own — only OUR shadow
            // geometry below is authored in this node's un-offset,
            // margin-box local space.
            drawContent()

            // The BORDER box in this node's space — its origin is what the
            // block below translates to, its size is what it is authored
            // against (Dp→px is only legal inside draw, so the bands convert
            // here, as in OutsetShadowPainter and the backdrop node).
            val box = ShadowGeometry.borderBoxRect(
                widthPx = size.width,
                heightPx = size.height,
                positionOffsetXPx = positionOffset.x.toPx(),
                positionOffsetYPx = positionOffset.y.toPx(),
                marginLeftPx = marginInsets.left.toPx(),
                marginTopPx = marginInsets.top.toPx(),
                marginRightPx = marginInsets.right.toPx(),
                marginBottomPx = marginInsets.bottom.toPx(),
            )

            // One canvas transform shifts the whole inset block — bounds
            // clip, outer rect, and inner hole together — so the clip can
            // never shear away from the hole. Unlike the outset path this
            // can't route each coordinate through the ShadowGeometry rect
            // seam: the hole is an android.graphics.Path (a throwing stub in
            // the JVM unit suite), so the translate keeps all three pieces on
            // one transform instead of hand-offsetting each coordinate.
            // Everything inside is authored in BORDER-box space: (0,0) is the
            // border box's top-left, box.width × box.height its size.
            translate(left = box.left, top = box.top) {

                // Create clip path for the element bounds
                val clipPath = Path().apply {
                    if (cornerRadius > 0.dp) {
                        addRoundRect(
                            RoundRect(
                                left = 0f,
                                top = 0f,
                                right = box.width,
                                bottom = box.height,
                                cornerRadius = CornerRadius(cornerRadius.toPx())
                            )
                        )
                    } else {
                        addRect(Rect(0f, 0f, box.width, box.height))
                    }
                }

                // Draw inset shadows clipped to bounds
                clipPath(clipPath, ClipOp.Intersect) {
                    for (shadowData in shadows) {
                        drawIntoCanvas { canvas ->
                            // For inset shadows, we draw a large rect with a hole
                            // and apply blur to create the inner shadow effect
                            val offsetX = shadowData.offsetX.toPx()
                            val offsetY = shadowData.offsetY.toPx()
                            val spread = shadowData.spreadRadius.toPx()
                            val blur = shadowData.blurRadius.toPx()
                            val radius = cornerRadius.toPx()

                            val nativePaint = android.graphics.Paint().apply {
                                isAntiAlias = true
                                color = shadowData.color.toArgb()

                                // Same CSS→Skia radius conversion as the outset
                                // path (derivation in blurMaskRadius) — the raw
                                // CSS radius over-blurred inset shadows by the
                                // same ~2.3× factor. Guard: BlurMaskFilter
                                // throws on radius ≤ 0.
                                val maskRadius = ShadowApplier.blurMaskRadius(blur)
                                if (maskRadius > 0f) {
                                    maskFilter = android.graphics.BlurMaskFilter(
                                        maskRadius,
                                        android.graphics.BlurMaskFilter.Blur.NORMAL
                                    )
                                }
                            }

                            // Create the inset shadow by drawing the negative space
                            // The shadow is drawn at the edges by using a path with a hole
                            val outerPadding = blur + spread.coerceAtLeast(0f) + 50f
                            val path = android.graphics.Path().apply {
                                // Outer rect (large, outside visible area)
                                addRect(
                                    -outerPadding,
                                    -outerPadding,
                                    box.width + outerPadding,
                                    box.height + outerPadding,
                                    android.graphics.Path.Direction.CW
                                )

                                // Inner rect (hole where no shadow appears)
                                // Offset and contracted by spread
                                val innerLeft = offsetX + spread
                                val innerTop = offsetY + spread
                                val innerRight = box.width + offsetX - spread
                                val innerBottom = box.height + offsetY - spread

                                if (radius > 0f) {
                                    addRoundRect(
                                        innerLeft, innerTop, innerRight, innerBottom,
                                        (radius - spread).coerceAtLeast(0f),
                                        (radius - spread).coerceAtLeast(0f),
                                        android.graphics.Path.Direction.CCW
                                    )
                                } else {
                                    addRect(
                                        innerLeft, innerTop, innerRight, innerBottom,
                                        android.graphics.Path.Direction.CCW
                                    )
                                }
                            }

                            canvas.nativeCanvas.drawPath(path, nativePaint)
                        }
                    }
                }
            }
        }
    }
}
