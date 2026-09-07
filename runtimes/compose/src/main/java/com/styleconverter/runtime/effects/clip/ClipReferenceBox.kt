package com.styleconverter.runtime.effects.clip

// Wave 46 (lane Y4) — css-masking-1 §5.1 REFERENCE BOX resolution for
// `clip-path` on Android.
//
// The spec resolves every `<basic-shape>` (and the bare `<geometry-box>`
// form) against a reference box chosen by the `<geometry-box>` keyword:
// margin-box / border-box (the default) / padding-box / content-box. The
// runtime's clip node (StyleApplier step 3, EffectsFacade.apply) is chained
// OUTSIDE the margin step (step 4, MarginApplier → `absolutePadding`) and
// OUTSIDE the position step (PositionApplier → `absoluteOffset`), so the
// `size` a Shape receives in createOutline is the MARGIN-band-inflated node
// and the painted border box sits inside it at (appliedLeft + offsetX,
// appliedTop + offsetY). Measured on wave45-final: WPT clip-path-ellipse-006
// (`margin: 50px; clip-path: ellipse()`) drew rx against a 250px-wide node
// instead of the 150px border box, and every `{geometry-box, shape}` wire
// rendered unclipped. This file turns the node rect into the four CSS boxes
// (plus their corner curves) so the shape factories in ClipPathApplier can
// resolve against the right one — and against the node rect unchanged
// (byte-identical arithmetic) when the element has no margin / offset.

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.borders.radius.BorderRadiusConfig
import com.styleconverter.runtime.spacing.CollapsedMargin
import com.styleconverter.runtime.spacing.MarginApplier
import com.styleconverter.runtime.spacing.MarginConfig
import com.styleconverter.runtime.spacing.MarginValue
import com.styleconverter.runtime.spacing.SpacingContext
import com.styleconverter.runtime.spacing.resolveToDp
import kotlin.math.max
import kotlin.math.min

/** Four physical Dp bands — one value per side, top/right/bottom/left. */
data class ClipBands(val top: Dp, val right: Dp, val bottom: Dp, val left: Dp) {
    companion object {
        /** All-zero bands — no border / no padding / no margin. */
        val ZERO = ClipBands(0.dp, 0.dp, 0.dp, 0.dp)
    }
}

/**
 * The element's own box metrics, read from the same IR the layout steps
 * consume (ClipPathExtractor.extractBoxGeometry), so the reference box is
 * derived from the very numbers that sized the node.
 *
 * @param margin the element's margin config, null when it declared none.
 *   Kept as a CONFIG (not resolved bands) because the APPLIED block-axis
 *   bands depend on the parent's CSS2 §8.3.1 collapse override, which only
 *   the composition knows (BlockMarginCollapse.LocalCollapsedMargin — the
 *   harness strips a root's vertical margins through that same channel);
 *   [nodeInsets] resolves it with the override the margin step itself used.
 * @param borderWidths USED border widths (0 for a side whose style is
 *   none/hidden — CSS2 §8.5.3), the padding-box inset from the border box.
 * @param paddings resolved padding bands, the content-box inset from the
 *   padding box.
 * @param radius the element's border-radius config — the border box's
 *   corner curves, from which the other boxes' curves derive.
 * @param positionOffset the resolved position inset (PositionApplier
 *   .resolvedOffset) — the paint slides by it INSIDE the clip node, so the
 *   reference box slides with it.
 */
data class ClipBoxGeometry(
    val margin: MarginConfig? = null,
    val borderWidths: ClipBands = ClipBands.ZERO,
    val paddings: ClipBands = ClipBands.ZERO,
    val radius: BorderRadiusConfig = BorderRadiusConfig(),
    val positionOffset: DpOffset = DpOffset.Zero,
) {
    /** True when the border box can differ from the node rect at all. */
    val displacesNode: Boolean
        get() = margin != null || positionOffset != DpOffset.Zero

    companion object {
        /** No metrics: every reference box coincides with the node rect. */
        val NONE = ClipBoxGeometry()
    }
}

/**
 * Where the BORDER BOX sits inside the clip node: the applied positive
 * margin bands (the node's own `absolutePadding`), plus the translation the
 * paint undergoes inside the node — the position offset and the negative-
 * margin remainder MarginApplier chains as `Modifier.offset`.
 */
data class ClipNodeInsets(val bands: ClipBands, val shiftX: Dp, val shiftY: Dp) {
    companion object {
        /** Border box == node rect. */
        val NONE = ClipNodeInsets(ClipBands.ZERO, 0.dp, 0.dp)
    }
}

/** One resolved reference box: its rect (node space, px) + corner curves. */
data class ClipReferenceFrame(
    val rect: Rect,
    val topLeft: CornerRadius,
    val topRight: CornerRadius,
    val bottomRight: CornerRadius,
    val bottomLeft: CornerRadius,
)

object ClipReferenceBox {

    /**
     * Resolve [ClipNodeInsets] for a box, given the CSS2 §8.3.1 collapse
     * override the margin step received (null outside a collapsing flow).
     * Shares MarginApplier.resolvedInsets for the positive bands — literally
     * the numbers the margin step padded the node with — and repeats its
     * two-line negative-remainder arithmetic for the `Modifier.offset` half.
     * KNOWN LIMITS (stated, not hidden): the default SpacingContext (a
     * preserved em/ch/lh margin could resolve on a different basis than the
     * layout step's threaded context — no corpus clip-path carries one), and
     * `margin: auto` centering (wrapContentWidth moves the box inside a
     * wider node; auto reads as 0 here, so the clip stays node-anchored).
     */
    fun nodeInsets(geometry: ClipBoxGeometry, collapsed: CollapsedMargin?): ClipNodeInsets {
        val offset = geometry.positionOffset
        val margin = geometry.margin
            ?: return ClipNodeInsets(ClipBands.ZERO, offset.x, offset.y)
        val applied = MarginApplier.resolvedInsets(margin, collapsed = collapsed)
        // Negative remainder — MarginApplier.applyResolved: the positive part
        // of each side is padding, the rest translates the node's content.
        val r = margin.resolve(isRtl = false)
        val ctx = SpacingContext()
        fun len(v: MarginValue?): Dp = (v as? MarginValue.Length)?.let { resolveToDp(it.value, ctx) } ?: 0.dp
        val negX = (len(r.left) - applied.left) - (len(r.right) - applied.right)
        val negY = (len(r.top) - applied.top) - (len(r.bottom) - applied.bottom)
        return ClipNodeInsets(
            ClipBands(applied.top, applied.right, applied.bottom, applied.left),
            shiftX = offset.x + negX, shiftY = offset.y + negY,
        )
    }

    /**
     * The reference box [box] of an element whose clip node measures [size].
     * Border box = node minus the applied bands, shifted; the other three
     * derive from it per css-masking-1 §5.1 / css-shapes-1 §4 (margin box
     * outset by the DECLARED margins — collapsing moves the node, not the
     * element's own margin box; padding/content boxes inset by the used
     * border widths and paddings).
     */
    fun resolve(
        geometry: ClipBoxGeometry, box: ClipGeometryBox, insets: ClipNodeInsets,
        size: Size, density: Density,
    ): ClipReferenceFrame = with(density) {
        val sx = insets.shiftX.toPx(); val sy = insets.shiftY.toPx()
        val border = Rect(
            left = insets.bands.left.toPx() + sx, top = insets.bands.top.toPx() + sy,
            right = size.width - insets.bands.right.toPx() + sx,
            bottom = size.height - insets.bands.bottom.toPx() + sy,
        )
        // Corner curves of the border box: css-backgrounds-3 §4.1 percent
        // axes against the border box, then the §5.1 overlap scaling.
        val radii = borderRadii(geometry.radius, border, density)
        val bw = geometry.borderWidths; val pd = geometry.paddings
        when (box) {
            ClipGeometryBox.BORDER_BOX -> frame(border, radii)
            ClipGeometryBox.PADDING_BOX ->
                insetFrame(border, radii, bw.top.toPx(), bw.right.toPx(), bw.bottom.toPx(), bw.left.toPx())
            ClipGeometryBox.CONTENT_BOX ->
                insetFrame(border, radii,
                    bw.top.toPx() + pd.top.toPx(), bw.right.toPx() + pd.right.toPx(),
                    bw.bottom.toPx() + pd.bottom.toPx(), bw.left.toPx() + pd.left.toPx())
            ClipGeometryBox.MARGIN_BOX -> {
                val r = geometry.margin?.resolve(isRtl = false)
                val ctx = SpacingContext()
                fun len(v: MarginValue?): Float =
                    (v as? MarginValue.Length)?.let { resolveToDp(it.value, ctx).toPx() } ?: 0f
                val mt = len(r?.top); val mr = len(r?.right); val mb = len(r?.bottom); val ml = len(r?.left)
                val rect = Rect(border.left - ml, border.top - mt, border.right + mr, border.bottom + mb)
                ClipReferenceFrame(
                    rect,
                    topLeft = CornerRadius(marginOutsetRadius(radii[0].x, ml), marginOutsetRadius(radii[0].y, mt)),
                    topRight = CornerRadius(marginOutsetRadius(radii[1].x, mr), marginOutsetRadius(radii[1].y, mt)),
                    bottomRight = CornerRadius(marginOutsetRadius(radii[2].x, mr), marginOutsetRadius(radii[2].y, mb)),
                    bottomLeft = CornerRadius(marginOutsetRadius(radii[3].x, ml), marginOutsetRadius(radii[3].y, mb)),
                )
            }
        }
    }

    /**
     * css-shapes-1 §4 margin-box corner rule: a corner radius r outset by a
     * margin m is `r + m` when m ≤ 0 or r/m ≥ 1, else `r + m·(1 + (r/m − 1)³)`
     * — so a square corner (r = 0) STAYS square (0 + m·(1 − 1) = 0) while a
     * large radius grows by the full margin (WPT marginBox-1c: 50 + 25 = 75
     * → a full circle; marginBox-1d: 10 + 50·(1 + (0.2 − 1)³) = 34.4).
     */
    fun marginOutsetRadius(r: Float, m: Float): Float {
        if (m <= 0f || r >= m) return max(0f, r + m)
        val ratio = r / m
        val d = ratio - 1f
        return max(0f, r + m * (1f + d * d * d))
    }

    /** Inner-box curves: css-backgrounds-3 §4.1 "inner radius = outer − width", floored at 0. */
    private fun insetFrame(outer: Rect, radii: Array<CornerRadius>, t: Float, r: Float, b: Float, l: Float) =
        ClipReferenceFrame(
            Rect(outer.left + l, outer.top + t, max(outer.left + l, outer.right - r), max(outer.top + t, outer.bottom - b)),
            topLeft = CornerRadius(max(0f, radii[0].x - l), max(0f, radii[0].y - t)),
            topRight = CornerRadius(max(0f, radii[1].x - r), max(0f, radii[1].y - t)),
            bottomRight = CornerRadius(max(0f, radii[2].x - r), max(0f, radii[2].y - b)),
            bottomLeft = CornerRadius(max(0f, radii[3].x - l), max(0f, radii[3].y - b)),
        )

    private fun frame(rect: Rect, radii: Array<CornerRadius>) =
        ClipReferenceFrame(rect, radii[0], radii[1], radii[2], radii[3])

    /**
     * Border-box corner curves in px, order TL / TR / BR / BL. A percent
     * axis (fraction wins over the Dp placeholder, as BorderRadiusConfig
     * documents) resolves against the border box's width (x) / height (y);
     * then css-backgrounds-3 §4.5's overlap rule scales ALL radii by the
     * smallest `side / (sum of the two adjacent radii)` ratio below 1.
     */
    private fun borderRadii(cfg: BorderRadiusConfig, box: Rect, density: Density): Array<CornerRadius> {
        fun axis(dp: Dp, fraction: Float?, extent: Float): Float =
            fraction?.let { extent * it } ?: with(density) { dp.toPx() }
        fun corner(pair: Pair<Dp, Dp>, fr: Pair<Float?, Float?>) =
            CornerRadius(axis(pair.first, fr.first, box.width), axis(pair.second, fr.second, box.height))
        val tl = corner(cfg.topStart, cfg.topStartFraction)
        val tr = corner(cfg.topEnd, cfg.topEndFraction)
        val br = corner(cfg.bottomEnd, cfg.bottomEndFraction)
        val bl = corner(cfg.bottomStart, cfg.bottomStartFraction)
        fun ratio(extent: Float, sum: Float) = if (sum > extent && sum > 0f) extent / sum else 1f
        val f = min(
            min(ratio(box.width, tl.x + tr.x), ratio(box.width, bl.x + br.x)),
            min(ratio(box.height, tl.y + bl.y), ratio(box.height, tr.y + br.y)),
        )
        fun scaled(c: CornerRadius) = if (f < 1f) CornerRadius(c.x * f, c.y * f) else c
        return arrayOf(scaled(tl), scaled(tr), scaled(br), scaled(bl))
    }
}
