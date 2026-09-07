package com.styleconverter.runtime.effects.clip

import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.borders.radius.BorderRadiusExtractor
import com.styleconverter.runtime.borders.sides.BorderSideConfig
import com.styleconverter.runtime.borders.sides.BorderSideExtractor
import com.styleconverter.runtime.layout.position.PositionApplier
import com.styleconverter.runtime.layout.position.PositionExtractor
import com.styleconverter.runtime.spacing.PaddingExtractor
import com.styleconverter.runtime.spacing.SpacingContext
import com.styleconverter.runtime.spacing.SpacingExtractor
import com.styleconverter.runtime.spacing.resolveToDp
import kotlinx.serialization.json.JsonElement

/**
 * Wave 46 (lane Y4) — the css-masking-1 §5.1 half of clip-path
 * extraction: the `<geometry-box>` keyword and the element's own box
 * metrics ([ClipBoxGeometry]) the applier turns into a reference box at
 * draw time. Split from [ClipPathExtractor] (the shape-wire reader) to
 * keep both files near the house size target.
 */
internal object ClipBoxGeometryExtractor {

    /**
     * css-masking-1 §5.1 keyword → [ClipGeometryBox]. The SVG-only boxes
     * take the spec's used value for an element with a CSS layout box
     * (fill-box → content-box, stroke-box / view-box → border-box); an
     * unknown keyword keeps the border-box initial rather than dropping
     * the clip.
     */
    fun parseGeometryBox(keyword: String): ClipGeometryBox = when (keyword.lowercase()) {
        "margin-box" -> ClipGeometryBox.MARGIN_BOX
        "padding-box" -> ClipGeometryBox.PADDING_BOX
        "content-box", "fill-box" -> ClipGeometryBox.CONTENT_BOX
        else -> ClipGeometryBox.BORDER_BOX
    }

    /**
     * The element's own box metrics, read through the SAME extractors the
     * layout steps use (SpacingExtractor / BorderSideExtractor /
     * PaddingExtractor / BorderRadiusExtractor / PositionExtractor +
     * PositionApplier.resolvedOffset), so the reference box the clip
     * derives at draw time is built from the numbers that sized the node.
     * See ClipReferenceBox for the node-vs-border-box geometry and the
     * stated limits (default SpacingContext, auto margins).
     */
    fun extract(properties: List<Pair<String, JsonElement?>>): ClipBoxGeometry {
        val margin = SpacingExtractor.extractMarginConfig(properties).takeIf { it.hasMargin }
        // USED border width: 0 for a side whose style is none/hidden or
        // absent (CSS2 §8.5.3) — BorderSideConfig.hasBorder encodes that.
        val borders = BorderSideExtractor.extractBorderConfig(properties)
        fun used(side: BorderSideConfig) = if (side.hasBorder) side.width ?: 0.dp else 0.dp
        val padding = PaddingExtractor.extract(properties).resolve(isRtl = false)
        val ctx = SpacingContext()
        return ClipBoxGeometry(
            margin = margin,
            borderWidths = ClipBands(used(borders.top), used(borders.end), used(borders.bottom), used(borders.start)),
            paddings = ClipBands(
                resolveToDp(padding.top, ctx), resolveToDp(padding.right, ctx),
                resolveToDp(padding.bottom, ctx), resolveToDp(padding.left, ctx),
            ),
            radius = BorderRadiusExtractor.extractRadiusConfig(properties),
            positionOffset = PositionApplier.resolvedOffset(PositionExtractor.extractPositionConfig(properties)),
        )
    }

}
