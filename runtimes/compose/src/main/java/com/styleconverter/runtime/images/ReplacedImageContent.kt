package com.styleconverter.runtime.images

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.core.ir.IRComponent

/**
 * ReplacedImageContent — the Compose paint half of wave-39 lane A2: a
 * component that IS a replaced element paints its delivered raster instead of
 * the text/placeholder content every other leaf paints.
 *
 * Kept out of ComponentRenderer.kt (already 6 000+ lines) and beside the
 * registry that feeds it, so the whole channel — deliver, decode, size, paint —
 * reads as one folder. The renderer's only involvement is a four-line mount
 * hook next to the UA-widget one, which is the same shape for the same reason:
 * a replaced element's content REPLACES the normal content path.
 */
object ReplacedImageContent {

    /** The `meta.sourceTag` values that carry replaced content, mirroring the
     *  producer's table (tools/titan/extract-fixture.mjs REPLACED_SRC_TAGS)
     *  and the feeder's corroborating copy in feed-lib.mjs.
     *
     *  `<input type=image>` is deliberately absent: `input` is a WIDGET tag
     *  owned by the UA-widget lane, whose mount hook runs first and returns —
     *  so admitting it here could only ever create an ordering dependency
     *  between two hooks that today have none. */
    private val REPLACED_TAGS = setOf("img", "embed", "object", "video")

    /**
     * Does this component paint replaced image content? True only when the
     * wire carries BOTH halves of the identity — a replaced `meta.sourceTag`
     * and a non-blank `meta.attrs.src`.
     *
     * A tag with no src is NOT a candidate: an `<img>` with no source renders
     * as its alt text in a browser, which is the existing text/placeholder
     * path, so claiming it here would paint nothing where something was
     * painted before.
     */
    fun isCandidate(component: IRComponent): Boolean {
        val tag = component._tag?.lowercase() ?: return false
        if (tag !in REPLACED_TAGS) return false
        return !component.attrs?.src.isNullOrBlank()
    }

    /**
     * Paint one replaced element's content inside the box the style chain has
     * already sized, or return false when nothing could be delivered.
     *
     * Returning FALSE rather than painting a placeholder is the contract: the
     * caller then runs its normal content path, which is byte-for-byte the
     * behaviour every capture had before this channel existed. An undelivered
     * asset must look like an undelivered asset, not like a renderer bug.
     *
     * @param widthDefinite  the style chain gave the box a used inline size
     * @param heightDefinite the style chain gave the box a used block size
     */
    @Composable
    fun Paint(
        component: IRComponent,
        widthDefinite: Boolean,
        heightDefinite: Boolean,
    ): Boolean {
        val decoded = DocumentImageRegistry.resolve(component.attrs?.src) ?: return false
        // object-fit / object-position, read from the SAME per-property
        // extractor the images/ triplet already ships (wave-8). This is the
        // wiring its own applier header called for: "When a content-image
        // channel lands, route its raster through … with the objectFit keyword
        // mapped". `contentScale` only has an effect once the content box and
        // the raster disagree, i.e. in the FILL_BOTH / ratio modes below.
        val fit = ObjectFitExtractor.extractObjectFitConfig(
            component.properties.map { it.type to it.data }
        )
        // §10.3.2 used-content-size decision (pure, twinned on iOS).
        val mode = ReplacedBoxSizing.mode(widthDefinite, heightDefinite, decoded.aspectRatio)
        val sizing = when (mode) {
            // Fill the box the chain produced; object-fit maps the raster in.
            ReplacedBoxSizing.Mode.FILL_BOTH -> Modifier.fillMaxSize()
            // One axis declared, the other derived from the intrinsic ratio.
            // `aspectRatio` is Compose's own §10.6.2: it resolves the free
            // axis from the constrained one. matchHeightConstraintsFirst
            // selects WHICH axis is treated as constrained, so the two rows
            // differ only in that flag.
            ReplacedBoxSizing.Mode.WIDTH_FILLS_RATIO_HEIGHT ->
                Modifier.fillMaxWidth().aspectRatio(decoded.aspectRatio!!, false)
            ReplacedBoxSizing.Mode.HEIGHT_FILLS_RATIO_WIDTH ->
                Modifier.fillMaxHeight().aspectRatio(decoded.aspectRatio!!, true)
            // Neither axis declared: the content is its intrinsic size and the
            // wrapping Box hugs it. CSS px == dp at the 160 dpi capture
            // density, the identity every geometry constant here assumes.
            ReplacedBoxSizing.Mode.INTRINSIC ->
                Modifier.size(decoded.intrinsicWidthPx.dp, decoded.intrinsicHeightPx.dp)
        }
        Image(
            bitmap = decoded.bitmap,
            // Null, not the alt text: this is a screenshot harness and the
            // a11y tree is not captured, so a description would be an
            // unverifiable claim. The alt text still reaches the wire as the
            // component's own text for the no-source path.
            contentDescription = null,
            contentScale = fit.contentScale,
            alignment = fit.alignment,
            modifier = sizing,
        )
        return true
    }
}
