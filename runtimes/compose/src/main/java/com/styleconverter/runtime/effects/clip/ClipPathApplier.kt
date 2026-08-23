package com.styleconverter.runtime.effects.clip

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import com.styleconverter.runtime.spacing.CollapsedMargin

/**
 * Applies CSS clip-path styling to Compose modifiers.
 *
 * Converts [ClipPathConfig] shapes into Compose [Shape] implementations
 * and applies them using [Modifier.clip].
 *
 * ## Supported Shapes
 * - Circle: Full support with radius keywords
 * - Ellipse: Full support with radius keywords
 * - Inset: Full support including border-radius
 * - Polygon: Full support for arbitrary polygons
 * - Path: full SVG path data via [SvgPathParser]
 * - Geometry box: the bare `<geometry-box>` keyword (css-masking-1 §7.1)
 *
 * ## Compose Implementation
 * Compose's clip modifier uses [Shape] to define clipping regions.
 * Custom shapes are created by implementing [Shape.createOutline] — the
 * factories live in [ClipRadialShapes] / [ClipRectShapes]; this file owns
 * the modifier, the reference-box plumbing and the dispatch.
 */
object ClipPathApplier {

    /**
     * Apply clip-path to modifier.
     *
     * @param modifier The modifier to apply clipping to.
     * @param config The clip-path configuration.
     * @param collapsed the CSS2 §8.3.1 collapse override the element's
     *   MARGIN step received, threaded down as a PARAMETER from
     *   StyleApplier.applyConfig (via EffectsFacade.apply). Null outside a
     *   collapsing block flow — then the declared margins are the applied
     *   ones, which is what [ClipReferenceBox.nodeInsets] assumes.
     *
     *   Wave 46 (lane Y4 skeptic S2): this MUST be a parameter, not a
     *   CompositionLocal read. The first cut of the reference box put a
     *   margin-carrying element on a `Modifier.composed` lane that read
     *   `BlockMarginCollapse.LocalCollapsedMargin.current` — but a composed
     *   modifier materialises at the LAYOUT NODE's composition site, which
     *   in ComponentRenderer sits INSIDE the CompositionLocalProvider that
     *   re-provides `LocalCollapsedMargin` as null for descendants
     *   (ComponentRenderer.kt, "§8.3.1 collapse channels are strictly one
     *   level"). So the clip always read null while the margin step used
     *   the real override — the exact reason the margin step takes the
     *   override as a parameter too. Measured consequence: WPT
     *   clip-path-ellipse-006 (`margin:50px` on a root whose block bands
     *   the harness strips via CollapsedMargin(0,0)) resolved its border
     *   box to height 100 − 50 − 50 = 0 and rendered EMPTY.
     * @return Modified modifier with clipping applied.
     */
    fun applyClipPath(
        modifier: Modifier,
        config: ClipPathConfig,
        collapsed: CollapsedMargin? = null,
    ): Modifier {
        config.shape ?: return modifier
        // One static `Modifier.clip` chain for every element, margin or not:
        // the reference box is now a pure function of the config + the
        // threaded override, so nothing here needs a composition.
        return modifier.clip(buildShape(config, ClipReferenceBox.nodeInsets(config.box, collapsed)))
    }

    /**
     * The draw-time reference box for one clip: captures the element's box
     * metrics, the chosen `<geometry-box>` and where the border box sits in
     * the node, and resolves them against the node's measured size inside
     * createOutline. With [ClipBoxGeometry.NONE] + border-box + no insets
     * the rect is exactly `Rect(0, 0, size.width, size.height)`, so every
     * pre-existing shape computes the same floats it did before.
     */
    internal class ClipRefBox(
        private val geometry: ClipBoxGeometry,
        private val box: ClipGeometryBox,
        private val insets: ClipNodeInsets,
    ) {
        fun frame(size: Size, density: Density): ClipReferenceFrame =
            ClipReferenceBox.resolve(geometry, box, insets, size, density)
    }

    /** Build the Compose [Shape] for [config] with the border box at [insets]. */
    private fun buildShape(config: ClipPathConfig, insets: ClipNodeInsets): Shape {
        val ref = ClipRefBox(config.box, config.geometryBox, insets)
        return when (val shape = config.shape!!) {
            is ClipShape.Circle -> ClipRadialShapes.createCircleShape(shape, ref)
            is ClipShape.Ellipse -> ClipRadialShapes.createEllipseShape(shape, ref)
            is ClipShape.Inset -> ClipRectShapes.createInsetShape(shape, ref)
            is ClipShape.Polygon -> ClipRectShapes.createPolygonShape(shape, ref)
            is ClipShape.Path -> ClipRectShapes.createPathShape(shape, ref)
            is ClipShape.LegacyRect -> ClipRectShapes.createLegacyRectShape(shape, ref)
            is ClipShape.Xywh -> ClipRectShapes.createXywhShape(shape, ref)
            ClipShape.ReferenceBox -> ClipRectShapes.createReferenceBoxShape(ref)
        }
    }
    /**
     * Create a clip shape directly from configuration.
     *
     * Useful when you need the Shape without applying it to a modifier.
     *
     * @param config The clip-path configuration.
     * @return The Compose Shape, or null if no clip path is configured.
     */
    fun createShape(config: ClipPathConfig): Shape? {
        config.shape ?: return null
        // No collapse override on this entry point: the border box is
        // assumed at the declared (uncollapsed) margin bands. Callers inside
        // a collapsing block flow go through [applyClipPath], which takes
        // the override its element's margin step received.
        return buildShape(config, ClipReferenceBox.nodeInsets(config.box, null))
    }
}