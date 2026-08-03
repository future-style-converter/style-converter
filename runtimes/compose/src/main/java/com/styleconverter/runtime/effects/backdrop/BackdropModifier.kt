package com.styleconverter.runtime.effects.backdrop

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.LayoutDirection
import com.styleconverter.runtime.borders.radius.BorderRadiusConfig
// The resolved margin bands this node has to strip back off — see the
// marginInsets parameter and BackdropSampleGeometry.borderBox.
import com.styleconverter.runtime.spacing.MarginInsets

/**
 * Per-element scratch state for the two-pass render: where this element's
 * border box sits, in WINDOW pixels, as of the last layout.
 *
 * Deliberately a plain (non-snapshot) holder. It is written from layout and
 * read from draw within the same frame, and — critically — it must SURVIVE the
 * SAMPLE→COMPOSITE flip. That flip only invalidates draw, so the modifier
 * chain instance (and therefore this holder) is the same object in both
 * passes, still carrying the position layout measured during pass A.
 */
internal class BackdropSlot {
    /** Border-box origin in window space; Unspecified until layout runs. */
    var originInWindow: Offset = Offset.Unspecified
}

/**
 * Register this element with the two-pass backdrop coordinator and render its
 * `backdrop-filter` chain.
 *
 * The chain sits at StyleApplier step 3 (effects), which is OUTER of borders
 * (step 5) and background (step 6). Draw modifiers paint outer-first, so
 * "patch, then `drawContent()`" puts the filtered backdrop UNDER everything
 * the element paints — exactly the paint order filter-effects-2 §2 specifies.
 *
 * Both passes are driven from one `drawWithContent`:
 *  - [BackdropPass.SAMPLE]: paint nothing at all. `drawContent()` is not
 *    called, so this element's box, background, borders AND children are all
 *    absent from the recorded canvas — the backdrop root image is what is left.
 *    Children are suppressed because a backdrop is what is painted BELOW the
 *    element, and its descendants paint above it.
 *  - [BackdropPass.COMPOSITE] (and DISABLED): draw the filtered patch if a
 *    backdrop image exists, then paint normally. With no image — idle
 *    coordinator, or a pass-A failure — this collapses to plain
 *    `drawContent()`, i.e. the historical no-op this lane replaced.
 *
 * ## Declared boundaries (lane scope)
 *  - ONE backdrop root: the composed canvas. Elements that establish their own
 *    backdrop root (ancestor filter/opacity/mask/clip-path — the
 *    `backdrop-filter-backdrop-root-*.html` family) are not modelled.
 *  - The sample is axis-aligned in canvas space, so an element under a
 *    rotation/scale would sample the wrong rectangle; the 3D-transform tests
 *    are out of scope for the same reason.
 */
internal fun Modifier.backdropFilterTwoPass(
    chain: BackdropChain,
    radiusConfig: BorderRadiusConfig,
    coordinator: BackdropPassCoordinator,
    // wave-26 skeptic fix — the element's resolved MARGIN bands. This node is
    // installed at StyleApplier step 3, OUTER of the margin step 4, so both
    // `size` (draw) and `positionInWindow()` (layout) describe the MARGIN box.
    // filter-effects-2 §2 samples and clips the BORDER box, so the bands are
    // subtracted here (BackdropSampleGeometry.borderBox). Threaded like
    // elementAlpha rather than re-derived: MarginApplier.resolvedInsets is the
    // very function the layout step's absolutePadding comes from, so the two
    // can never disagree. Default NONE keeps a margin-less element unchanged.
    marginInsets: MarginInsets = MarginInsets.NONE,
    // wave-26 skeptic fix — the element's own `opacity`. filter-effects-2 §2
    // composites the FILTERED BACKDROP into the element's group, so the
    // element's opacity attenuates it: `backdrop-filter-basic-opacity.html`
    // sets `opacity: 0` on the inverting box and its ref is the UNFILTERED
    // green box. ColorApplier's `Modifier.alpha` lives at StyleApplier step 6,
    // INSIDE this draw node, so it can never reach the patch — the value has
    // to be threaded here or the patch paints at full strength and turns a
    // test that passed under the old no-op into a failure.
    elementAlpha: Float = 1f,
): Modifier {
    // opacity: 0 → the element contributes nothing at all; installing the
    // draw node would only cost a pass-A suppression with no pass-B paint.
    if (elementAlpha <= 0f) return this
    // One holder per modifier-chain instance, captured by both lambdas below.
    val slot = BackdropSlot()
    return this
        // Layout publishes the MARGIN box's origin (this node is outer of the
        // margin step — see marginInsets); the border box's origin is that
        // plus the top/left bands, added at draw time where Dp→px is legal.
        // positionInWindow() (not boundsInWindow()) because the latter CLIPS
        // to the window, and the composed canvas is routinely taller than the
        // 844px window — a clipped origin would mis-place every patch below
        // the fold.
        .onGloballyPositioned { coords -> slot.originInWindow = coords.positionInWindow() }
        .drawWithContent {
            // Snapshot reads. Both happen inside draw, so a coordinator flip
            // repaints this node without recomposing or re-laying it out.
            val pass = coordinator.pass
            val backdrop = coordinator.backdrop
            if (pass == BackdropPass.SAMPLE) {
                // Pass A: contribute nothing to the backdrop root image.
                return@drawWithContent
            }
            val origin = slot.originInWindow
            // Margin box → border box. Null means the bands ate the whole node
            // (see borderBox): nothing honest to sample, so no patch — the
            // element still paints itself via drawContent() below.
            val box = BackdropSampleGeometry.borderBox(
                nodeWidth = size.width,
                nodeHeight = size.height,
                marginLeftPx = marginInsets.left.toPx(),
                marginTopPx = marginInsets.top.toPx(),
                marginRightPx = marginInsets.right.toPx(),
                marginBottomPx = marginInsets.bottom.toPx(),
            )
            if (backdrop != null && box != null && origin != Offset.Unspecified) {
                // Window space → backdrop-image space: the pass-A bitmap's
                // origin is the canvas's own top-left. The border box's own
                // origin is the node's plus the left/top margin bands.
                val canvasOrigin = coordinator.canvasOriginInWindow
                BackdropPainter.paint(
                    scope = this,
                    backdrop = backdrop,
                    elemLeftPx = Math.round(origin.x - canvasOrigin.x + box.localLeft),
                    elemTopPx = Math.round(origin.y - canvasOrigin.y + box.localTop),
                    box = box,
                    chain = chain,
                    radii = BackdropClipGeometry.resolve(
                        config = radiusConfig,
                        // Percentage radii resolve against the BORDER box
                        // (css-backgrounds-3 §4.4) — using the node's own
                        // size here would size a `border-radius: 50%` clip off
                        // the margin box and leave the corners showing.
                        widthPx = box.width,
                        heightPx = box.height,
                        dpToPx = { dp -> dp.toPx() },
                        // wave-26 skeptic fix: the LIVE layout direction, not
                        // the LTR default. BorderRadiusApplier resolves the
                        // element's OWN clip through a Shape that receives
                        // layoutDirection, so an RTL document with asymmetric
                        // logical radii (border-start-start-radius etc.) had
                        // the box clipped one way and the backdrop patch the
                        // other — the patch's corners would show past the box.
                        isLtr = layoutDirection == LayoutDirection.Ltr,
                    ),
                    // Element opacity attenuates the filtered backdrop (see
                    // the elementAlpha param). Per-layer rather than true
                    // group alpha — exact at 0 and 1, an approximation in
                    // between where the element's own paint overlaps the
                    // patch, which is strictly closer than ignoring it.
                    alpha = elementAlpha.coerceIn(0f, 1f),
                )
            }
            // The element paints normally, on top of whatever patch was drawn.
            drawContent()
        }
}
