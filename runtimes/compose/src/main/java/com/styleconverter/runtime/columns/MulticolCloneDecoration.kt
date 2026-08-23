// The IR-side half of css-break-3 §5.2 `box-decoration-break: clone` for
// multicol fragmentation (wave-46 lane Y3): reads a multicol child's
// declared decoration bands off its IR property list — CONSUMING the
// borders / radius / padding trees' extractors read-only, never
// re-parsing their wire shapes — into the pure MulticolCloneGeometry.Bands
// the measure pass needs. Lives in columns/ because the only consumer is
// the column fragmenter; the decoration trees stay untouched.
package com.styleconverter.runtime.columns

// IR types: the child component and its (type, data) property rows.
import com.styleconverter.runtime.core.ir.IRComponent
// Length shapes for the padding bands — only Exact px resolves statically.
import com.styleconverter.runtime.core.types.LengthValue
// Keyword reader shared with every other IR keyword gate in this tree.
import com.styleconverter.runtime.core.types.ValueExtractors
// The three decoration extractors consumed read-only (repo triplet
// contract: columns/ reads their configs, never their wire shapes).
import com.styleconverter.runtime.borders.radius.BorderRadiusExtractor
import com.styleconverter.runtime.borders.sides.BorderSideExtractor
import com.styleconverter.runtime.spacing.PaddingExtractor
import kotlin.math.roundToInt

/**
 * Resolves the clone-fragment bands of ONE multicol child from its IR.
 *
 * `box-decoration-break` (css-break-3 §5.2) is carried on the wire as the
 * IR `BoxDecorationBreak` keyword (`SLICE` | `CLONE`, see the converter's
 * BoxDecorationBreakPropertyParser). Only `CLONE` produces facts; the
 * default `slice` is the existing S-table path and returns null here so
 * every slice container stays byte-identical.
 *
 * The bands are the child's USED border widths (a side with
 * `border-style: none/hidden` has used width 0 — CSS 2.1 §8.5.3, the same
 * `hasBorder` gate BorderSideConfig applies when painting) plus its
 * padding. Horizontal-tb only (the fragmenter's contract): block-start =
 * top, block-end = bottom, with the logical padding longhands folded onto
 * their physical sides (css-logical-1 §4.1) when the physical ones are
 * absent.
 *
 * STATIC-RESOLUTION CONTRACT — returns null (caller logs + keeps slice)
 * whenever a band is not a plain px value: em/%/calc padding needs the
 * child's font or containing block, and a percentage corner radius
 * resolves against the fragment box at paint time. Both are honest bails,
 * not silent fallthroughs; the corpus' clone family is px throughout
 * (WPT css-break background-image-004/007, borders-008).
 */
object MulticolCloneDecoration {

    /** The IR keyword that selects the clone model (css-break-3 §5.2). */
    private const val CLONE_KEYWORD = "CLONE"

    /**
     * True iff [component] declares `box-decoration-break: clone` — the
     * cheap pre-check the spec builder uses before resolving any band.
     */
    fun declaresClone(component: IRComponent): Boolean =
        component.properties.firstOrNull { it.type == "BoxDecorationBreak" }
            ?.let { ValueExtractors.extractKeyword(it.data)?.uppercase() } == CLONE_KEYWORD

    /**
     * The clone bands for [component], or null when it is a slice child
     * OR a band is not statically resolvable (see the class doc).
     */
    fun bandsFor(component: IRComponent): MulticolCloneGeometry.Bands? {
        // Slice (the default) never builds bands — the S-table owns it.
        if (!declaresClone(component)) return null
        // The (type, data) rows every decoration extractor consumes.
        val pairs = component.properties.map { it.type to it.data }
        // USED border widths: the `hasBorder` gate zeroes a side whose
        // style is none/hidden (CSS 2.1 §8.5.3), exactly as the painter
        // does — so the bands agree with the rendered box by construction.
        val borders = BorderSideExtractor.extractBorderConfig(pairs)
        val borderTop = if (borders.top.hasBorder) borders.top.width!!.value else 0f
        val borderBottom = if (borders.bottom.hasBorder) borders.bottom.width!!.value else 0f
        // Padding: physical longhand first, logical fallback (horizontal-tb
        // maps block-start → top, block-end → bottom). Only Exact px is
        // statically known; anything else bails the clone model.
        val padding = PaddingExtractor.extract(pairs)
        val paddingTop = staticPx(padding.top ?: padding.blockStart) ?: return null
        val paddingBottom = staticPx(padding.bottom ?: padding.blockEnd) ?: return null
        // Bottom corner radii: the block-end band must cover the taller
        // bottom arc so a short last fragment's corners rejoin (see
        // MulticolCloneGeometry.Bands.blockEndBandPx). A percentage corner
        // only resolves against the fragment box at paint time → bail.
        val radius = BorderRadiusExtractor.extractRadiusConfig(pairs)
        if (radius.hasFraction) return null
        // The VERTICAL (second) axis of each bottom corner is the arc's
        // block-axis extent — the rows the band must contain.
        val bottomArc = maxOf(radius.bottomStart.second.value, radius.bottomEnd.second.value)
        // Bands in whole px (the fragmenter works in integer px like the
        // S-table); the end band is widened to the arc when it is taller.
        val endPx = (borderBottom + paddingBottom).roundToInt()
        return MulticolCloneGeometry.Bands(
            blockStartPx = (borderTop + paddingTop).roundToInt(),
            blockEndPx = endPx,
            blockEndBandPx = maxOf(endPx, bottomArc.roundToInt())
        )
    }

    /**
     * A padding band as static px: absent padding is 0 (CSS initial
     * value), an Exact length is its px, everything else (em/rem/%/calc,
     * auto-like keywords) is not knowable here → null.
     */
    private fun staticPx(v: LengthValue?): Float? = when (v) {
        null -> 0f
        is LengthValue.Exact -> v.px.toFloat()
        else -> null
    }
}
