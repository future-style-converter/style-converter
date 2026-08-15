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
import com.styleconverter.runtime.borders.sides.BorderSideConfig
import com.styleconverter.runtime.borders.sides.BorderSideExtractor
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.renderer.LocalWptCaptureMode
import com.styleconverter.runtime.core.types.LengthValue
import com.styleconverter.runtime.sizing.BoxSizingKeyword
import com.styleconverter.runtime.sizing.SizingExtractor
import com.styleconverter.runtime.spacing.SpacingContext
import com.styleconverter.runtime.spacing.SpacingExtractor
import com.styleconverter.runtime.spacing.resolveToDp
import kotlinx.serialization.json.JsonElement

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
        // The (type, data) pair list every extractor below consumes — built
        // once so object-fit and the §10.4 bounds read the SAME properties.
        val props = component.properties.map { it.type to it.data }
        // object-fit / object-position, read from the SAME per-property
        // extractor the images/ triplet already ships (wave-8). This is the
        // wiring its own applier header called for: "When a content-image
        // channel lands, route its raster through … with the objectFit keyword
        // mapped". `contentScale` only has an effect once the content box and
        // the raster disagree, i.e. in the FILL_BOTH / ratio modes below.
        val fit = ObjectFitExtractor.extractObjectFitConfig(props)
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
            // Neither axis declared: the content is its intrinsic size run
            // through §10.4's min/max constraint resolution (wave-42 W6 —
            // identity when no bounds are declared, so every pre-wave-42
            // capture is byte-identical). The wrapping Box hugs the result.
            // CSS px == dp at the 160 dpi capture density, the identity every
            // geometry constant here assumes.
            ReplacedBoxSizing.Mode.INTRINSIC -> {
                // §10.4 bounds live on the SAME wire properties the sizing
                // chain clamps the box with, so box and content stay agreed.
                val used = constrainedAutoContentSize(
                    properties = props,
                    // The chain's own box-sizing tri-state resolution needs
                    // the WPT flag (unset defaults to content-box ONLY there
                    // — SizingApplier.effectiveBoxSizing's pinned decision).
                    wptCaptureMode = LocalWptCaptureMode.current,
                    intrinsicWidthPx = decoded.intrinsicWidthPx.toFloat(),
                    intrinsicHeightPx = decoded.intrinsicHeightPx.toFloat(),
                    aspectRatio = decoded.aspectRatio,
                )
                Modifier.size(used.widthPx.dp, used.heightPx.dp)
            }
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

    /**
     * Wave-42 W6 — the wire half of §10.4: read the min/max bounds off the
     * component's properties, convert each to CONTENT-BOX px, and hand them to
     * [ReplacedBoxSizing.constrainAutoSize]. Pure (no composition) so the JVM
     * suite pins the border-box band arithmetic without a device.
     *
     * Bound conversion (css-sizing-3 §3, mirroring the sizing chain's own
     * semantics so the box and its content can never disagree):
     *   * effective `content-box` — the declared bound IS the content bound;
     *   * everything else (explicit `border-box`, or unset = the chain's
     *     border-box status quo) — content bound = declared − (padding +
     *     used border) band on that axis, floored at 0 (a bound smaller than
     *     its own bands leaves a zero content box, css-ui-3 §5's floor).
     * Only [LengthValue.Exact] bounds participate: `none` means unbounded and
     * a %/em bound has no resolvable px here (documented narrowing — the
     * chain still clamps the BOX by it at layout time).
     */
    internal fun constrainedAutoContentSize(
        properties: List<Pair<String, JsonElement?>>,
        wptCaptureMode: Boolean,
        intrinsicWidthPx: Float,
        intrinsicHeightPx: Float,
        aspectRatio: Float?,
    ): ReplacedBoxSizing.UsedSize {
        // The SAME extractor the sizing chain runs, WPT tri-state included
        // (SizingExtractor resolves effectiveBoxSizing internally), so the
        // keyword this reads is the keyword the chain clamped with.
        val sizing = SizingExtractor.extractSizingConfig(properties, wptCaptureMode)
        // Effective content-box needs no band; border-box (explicit or the
        // unset status quo) subtracts the padding+border band per axis.
        val (bandX, bandY) =
            if (sizing.boxSizing == BoxSizingKeyword.CONTENT_BOX) 0f to 0f
            else paddingAndBorderBands(properties)
        // One declared bound → content-box px, or null when unresolvable.
        fun bound(v: LengthValue?, band: Float): Float? =
            (v as? LengthValue.Exact)?.px?.toFloat()?.minus(band)?.coerceAtLeast(0f)
        // The pure §10.4 table, in content-box px throughout.
        return ReplacedBoxSizing.constrainAutoSize(
            intrinsicWidthPx = intrinsicWidthPx,
            intrinsicHeightPx = intrinsicHeightPx,
            aspectRatio = aspectRatio,
            minWidthPx = bound(sizing.minWidth, bandX),
            maxWidthPx = bound(sizing.maxWidth, bandX),
            minHeightPx = bound(sizing.minHeight, bandY),
            maxHeightPx = bound(sizing.maxHeight, bandY),
        )
    }

    /**
     * Per-axis padding + USED border band, px — the amount a border-box bound
     * exceeds its content bound. Mirrors SizingExtractor.contentBoxInflation
     * line for line (that helper is private and gated on content-box, where
     * this caller needs the band for the border-box direction): padding rides
     * SpacingExtractor with the §5.2.1 indefinite-percent-as-zero context,
     * borders ride the [BorderSideConfig.hasBorder] gate so `border-style:
     * none` contributes 0 (CSS 2.1 §8.5.3) — one definition of "band" keeps
     * inflation and deflation from ever drifting.
     */
    private fun paddingAndBorderBands(
        properties: List<Pair<String, JsonElement?>>
    ): Pair<Float, Float> {
        // Padding resolved exactly as PaddingApplier will inset it; the
        // indefinite percent basis contributes zero (css-sizing-3 §5.2.1),
        // matching the sizing chain's static resolution.
        val pad = SpacingExtractor.extractPaddingConfig(properties).resolve(isRtl = false)
        val ctx = SpacingContext(percentIndefiniteAsZero = true)
        fun side(v: LengthValue?): Float = resolveToDp(v, ctx).value.coerceAtLeast(0f)
        // Border band — only sides that actually paint consume space.
        val borders = BorderSideExtractor.extractBorderConfig(properties)
        fun band(s: BorderSideConfig): Float = if (s.hasBorder) s.width?.value ?: 0f else 0f
        return Pair(
            // Inline axis: left + right padding plus start + end borders.
            side(pad.left) + side(pad.right) + band(borders.start) + band(borders.end),
            // Block axis: top + bottom padding plus top + bottom borders.
            side(pad.top) + side(pad.bottom) + band(borders.top) + band(borders.bottom),
        )
    }
}
