package com.styleconverter.runtime.effects.shadow

// Draw-time half of the OUTSET box-shadow painter — carved out of
// ShadowApplier in retro R6 (file-size rule: ShadowApplier had grown to 491
// lines once the §6.1.1 knockout + css-color-4 §3.3 group alpha landed). The
// value-level geometry it draws lives in ShadowGeometry (JVM-pinned); the
// inset painter is InsetShadowPainter and the blur-radius conversion stays
// in ShadowApplier. Deliberately NOT named `*Applier`: coverage-audit.mjs
// counts every `<Name>Applier.kt` as a dedicated property applier and
// doc-staleness-check pins that count — this is a helper of the BoxShadow
// applier, not a property.
import androidx.compose.ui.Modifier
// The shadow paints BEHIND the element's own content (css-backgrounds-3 §6.1).
import androidx.compose.ui.draw.drawBehind
// Per-corner (elliptical) radii for the shadow perimeter and the knockout box.
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
// ClipOp.Difference — the §6.1.1 knockout of the border box.
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
// The perimeter path is handed to the framework canvas (BlurMaskFilter lives
// on android.graphics.Paint, which Compose's Paint does not expose).
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
// The resolved position offset rides in as a value — see the positionOffset
// param KDoc on ShadowApplier.applyFullShadow.
import androidx.compose.ui.unit.DpOffset
// The resolved margin bands ride in as a value too (retro round-2 F1) — see
// the marginInsets param KDoc on paint and ShadowGeometry's class KDoc.
import com.styleconverter.runtime.spacing.MarginInsets

/**
 * Paints every OUTSET `box-shadow` layer of one element, behind its content
 * (css-backgrounds-3 §6.1 / §6.1.1 / §6.1.3). Entered only from
 * [ShadowApplier.applyFullShadow]; every parameter is documented there and on
 * [paint]. The chain shape is unchanged by the carve-out — one `drawBehind`
 * node, which ShadowApplierTest / ShadowOpacityKnockoutTest /
 * ShadowMarginBoxTest pin.
 */
internal object OutsetShadowPainter {

    /**
     * Apply outset (normal) shadows - drawn behind the content.
     *
     * Two css-backgrounds-3 §6.1 rules landed here in retro R6 (audit
     * findings A11#2 / A10#5), both measured on images.combos
     * 003_Images_Decorated (`border-radius: 50%; box-shadow: 0 0 0 3px
     * #3498db; opacity: .55` on #e74c3c over the (26,26,46) canvas):
     *  - KNOCKOUT — "The shadow is drawn outside the border edge only: it is
     *    clipped inside the border-box of the element." Every layer is
     *    painted under a `ClipOp.Difference` clip of the border box (radius-
     *    aware, at the position-offset slot), so nothing is ever painted
     *    UNDER the box. Android's fill read (150,110,132) = fill@0.55 over an
     *    opaque ring instead of (139,54,54) = fill@0.55 over the canvas.
     *  - GROUP ALPHA — css-color-4 §3.3 applies `opacity` to the element as a
     *    whole, shadow included; the paint alpha is multiplied by the
     *    element's opacity (ShadowGeometry.attenuate). Android's ring read
     *    (52,152,219) at full alpha instead of (40,95,141).
     * PR #95 recorded the knockout as a follow-up for WPT
     * `backdrop-filter-box-shadow` (Android ~0.03 below iOS: the grey
     * Gaussian painting under the pink box) and it never reached BACKLOG.
     *
     * @param marginInsets the element's resolved POSITIVE margin bands
     *   (retro round-2 F1, skeptic S3 must-fix). This draw node is installed
     *   at StyleApplier step 3, OUTER of the step-4 margin `absolutePadding`,
     *   so `size` here is the MARGIN box (CSS2 §8.1) — R6's knockout and the
     *   perimeter read it as if it were the border box, which knocked out
     *   the whole margin box and pushed the ring one band outward on every
     *   margined element (BSK_Op55_RingDominant: 98×98 ring + 24px ground
     *   gap instead of a 50×50 ring hugging the box). Threaded like the
     *   backdrop lane's — from MarginApplier.resolvedInsets, the very
     *   function step 4's padding comes from, so the two cannot disagree.
     *   Default NONE keeps every margin-less caller byte-identical.
     */
    fun paint(
        modifier: Modifier,
        shadows: List<ShadowData>,
        cornerRadius: Dp,
        radiusConfig: com.styleconverter.runtime.borders.radius.BorderRadiusConfig =
            com.styleconverter.runtime.borders.radius.BorderRadiusConfig.NONE,
        // The element's resolved position offset — see ShadowApplier.applyFullShadow KDoc.
        positionOffset: DpOffset = DpOffset.Zero,
        // The element's `opacity` — see ShadowGeometry.attenuate.
        elementAlpha: Float = 1f,
        // The element's resolved margin bands — see the KDoc above.
        marginInsets: MarginInsets = MarginInsets.NONE,
    ): Modifier {
        // NOTE: no elevation fast path. The old code routed a single
        // 0-offset/0-spread shadow through Modifier.shadow(elevation) —
        // but elevation is Android's physically-modeled z-shadow with
        // FIXED ambient/spot alphas and light-source-dependent geometry,
        // not the colored Gaussian css-backgrounds-3 §6.1 asks for, so
        // `box-shadow: 0 0 40px <color>` lost both its color intensity
        // and its 40px reach. Every shadow now takes the BlurMaskFilter
        // path below, which honors color/blur/shape exactly.
        return modifier.drawBehind {
            // Resolve the position offset once for all layers: this draw
            // node sits OUTER of step 4's absoluteOffset, so its local
            // origin is the UN-offset slot and every perimeter below must
            // slide by the same amount the element itself is about to
            // (composed via ShadowGeometry.outsetShadowRect, the pure-math
            // seam the JVM unit suite pins — the zero case degenerates to
            // the pre-fix formula, keeping static captures byte-identical).
            val posXPx = positionOffset.x.toPx()
            val posYPx = positionOffset.y.toPx()
            // Resolve the margin bands once too. Dp→px is only legal inside
            // draw (Density is the scope) — the same reason the backdrop node
            // converts its bands here and not at the call site.
            val mlPx = marginInsets.left.toPx()
            val mtPx = marginInsets.top.toPx()
            val mrPx = marginInsets.right.toPx()
            val mbPx = marginInsets.bottom.toPx()
            // The element's BORDER box in this node's space: the node minus
            // its margin bands, at the offset slot (ShadowGeometry.
            // borderBoxRect). It is the §6.1.1 knockout region AND the
            // reference box every corner-radius percentage resolves against
            // (§4.1 "corresponding dimension of the border box").
            val box = ShadowGeometry.borderBoxRect(
                size.width, size.height, posXPx, posYPx, mlPx, mtPx, mrPx, mbPx,
            )

            // css-backgrounds-3 §6.1.1: a shadow perimeter takes the border
            // box's corner shape (per-corner radii, percentages resolved
            // against the BORDER box's size — ShadowGeometry.cornerRadiusPx)
            // grown by [grow] — the spread for a shadow layer (each corner
            // radius grows by the spread too, which is what turns
            // `border-radius: 50%` + a spread ring into a concentric ELLIPSE
            // ring instead of a rectangle — Borders_Decorated web/iOS vs old
            // Android), ZERO for the knockout box.
            fun corner(
                dp: Pair<Dp, Dp>,
                frac: Pair<Float?, Float?>,
                grow: Float,
            ): CornerRadius {
                val rx = ShadowGeometry.cornerRadiusPx(dp.first.toPx(), frac.first, box.width, grow)
                val ry = ShadowGeometry.cornerRadiusPx(dp.second.toPx(), frac.second, box.height, grow)
                return CornerRadius(rx, ry)
            }
            // Legacy single-Dp radius keeps working when no per-corner
            // config was supplied.
            val useConfig = radiusConfig.hasRadius
            fun perimeter(rect: Rect, grow: Float): RoundRect {
                val legacy = CornerRadius(cornerRadius.toPx() + grow, cornerRadius.toPx() + grow)
                return RoundRect(
                    rect = rect,
                    topLeft = if (useConfig) corner(radiusConfig.topStart, radiusConfig.topStartFraction, grow) else legacy,
                    topRight = if (useConfig) corner(radiusConfig.topEnd, radiusConfig.topEndFraction, grow) else legacy,
                    bottomRight = if (useConfig) corner(radiusConfig.bottomEnd, radiusConfig.bottomEndFraction, grow) else legacy,
                    bottomLeft = if (useConfig) corner(radiusConfig.bottomStart, radiusConfig.bottomStartFraction, grow) else legacy,
                )
            }

            // The §6.1.1 knockout: the border box itself (`box` above —
            // margin bands stripped, translated by the position offset, NO
            // spread, NO shadow offset), with the element's own corner radii.
            // A Difference clip on it excludes the box from every layer's
            // paint below; the clip is anti-aliased like the box's own edge
            // (Canvas.clipOutPath on the HWUI Skia pipeline), which is also
            // how Blink paints the rounded hole.
            val borderBox = Path().apply { addRoundRect(perimeter(box, grow = 0f)) }
            clipPath(borderBox, ClipOp.Difference) {
                // CSS spec: "Shadows are rendered in back-to-front order: the
                // FIRST shadow in the list is on top of the stack." We iterate
                // the list in REVERSE so the last-listed shadow is drawn first
                // (bottom of the stack) and the first-listed shadow lands on
                // top — matching how Chrome/Firefox/WebKit composite. The
                // previous forward iteration inverted the order, which also
                // explains the Phase 12 finding that "only the last 2-3 layers
                // are visible" for many-layer shadows: the small tight shadow
                // listed first was the one the author wanted visible, but
                // under forward iteration it got painted over by the larger
                // later-listed layers.
                for (shadowData in shadows.asReversed()) {
                    drawIntoCanvas { canvas ->
                        val nativePaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            // css-color-4 §3.3 group alpha — the declared
                            // colour × the element's opacity (see
                            // ShadowGeometry.attenuate for the arithmetic
                            // and the measured ring values).
                            color = ShadowGeometry.attenuate(shadowData.color, elementAlpha).toArgb()

                            // Convert the CSS blur radius to Skia's mask-filter
                            // radius (see ShadowApplier.blurMaskRadius for the derivation) —
                            // passing the raw CSS value made Android's blur
                            // ~2.3× wider/softer than web's for the same
                            // declaration. Skip the filter entirely when the
                            // converted radius rounds to 0: BlurMaskFilter
                            // rejects non-positive radii, and a sub-pixel σ is
                            // visually indistinguishable from a crisp edge.
                            val maskRadius = ShadowApplier.blurMaskRadius(shadowData.blurRadius.toPx())
                            if (maskRadius > 0f) {
                                maskFilter = android.graphics.BlurMaskFilter(
                                    maskRadius,
                                    android.graphics.BlurMaskFilter.Blur.NORMAL
                                )
                            }
                        }

                        val offsetX = shadowData.offsetX.toPx()
                        val offsetY = shadowData.offsetY.toPx()
                        val spread = shadowData.spreadRadius.toPx()
                        // Corner growth clamps at 0 (a negative spread
                        // contracts the perimeter but never the radii).
                        val spreadGrow = spread.coerceAtLeast(0f)

                        val shadowShape = perimeter(
                            // Perimeter = border box (node minus margin bands)
                            // translated by (position offset + shadow offset),
                            // inflated by spread — the pure-math seam
                            // ShadowGeometry owns (and the unit suite pins),
                            // so the draw code can't drift from the tested
                            // formula.
                            ShadowGeometry.outsetShadowRect(
                                widthPx = size.width,
                                heightPx = size.height,
                                shadowOffsetXPx = offsetX,
                                shadowOffsetYPx = offsetY,
                                spreadPx = spread,
                                positionOffsetXPx = posXPx,
                                positionOffsetYPx = posYPx,
                                marginLeftPx = mlPx,
                                marginTopPx = mtPx,
                                marginRightPx = mrPx,
                                marginBottomPx = mbPx,
                            ),
                            grow = spreadGrow,
                        )
                        // Path-based draw so per-corner (and elliptical) radii
                        // render exactly; Skia clamps overlapping radii for us.
                        val path = Path().apply { addRoundRect(shadowShape) }
                        canvas.nativeCanvas.drawPath(path.asAndroidPath(), nativePaint)
                    }
                }
            }
        }
    }
}
