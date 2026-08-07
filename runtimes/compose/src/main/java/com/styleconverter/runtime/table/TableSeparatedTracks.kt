package com.styleconverter.runtime.table

// Wave 34 (lane T, T1) — the SEPARATED-borders track model, CSS 2.1
// §17.6.1, as a pure decision table. Twin: `runtimes/swiftui/Sources/
// StyleConverterRuntime/StyleEngine/table/TableSeparatedTracks.swift`.
//
// ## The measured defect (frozen wave33-final pixel evidence)
// css-tables/abspos-container-change-dynamic-001 renders a two-cell table
// whose second `<td>` is turned `position: relative` and given a 100×100
// lime abspos child. The reference paints that lime box at (33,18)–
// (132,117): the padding-box corner of the SECOND cell, which sits one
// border-spacing right of the first cell's 13.05px border box, which
// itself sits one border-spacing in from the table's own edge
// (16 + 2 + 13.0469 + 2 = 33.047, and 16 + 2 = 18).
//
// iOS painted it at (16,38)–(115,137) — SSIM 0.9489, the LAST failing
// css-tables cell — because it has no table display type at all and the
// row's two cells stacked VERTICALLY in its block VStack. Android, which
// does synthesize rows (TableApplier.TableRow), painted it at (30,37):
// the x is one horizontal spacing short (16 + 13.047 = 29.05 instead of
// 16 + 2 + 13.047 + 2 = 33.05) because `TableConfig.effectiveSpacing*`
// resolves an undeclared `border-spacing` to 0 rather than to the HTML
// UA sheet's 2px. Both misses are the same two §17.6.1 facts:
//
//  1. **Cells of a row are laid out in the INLINE direction**, separated
//     by the horizontal border-spacing. (`Arrangement.INLINE_ROW`.)
//  2. **The distance between the border of the table box and the borders
//     of the cells on the edge of the table is the border-spacing** — an
//     outer band on the table box, not just gaps between cells.
//     (`outerBandApplies`.)
//
// ## Why `border-spacing` is not on the wire
// The test declares none: `border-spacing: 2px` comes from the HTML
// Standard's rendering UA stylesheet (§15.3.3 Tables — `table { border-
// spacing: 2px; border-collapse: separate; }`), and this converter never
// serializes UA defaults. `meta.sourceTag` (`IRComponent._tag`) is the
// only sighting of a bare `<table>` element, exactly as
// `AbsposCbUsedHeight`'s H3 lane and `AbsposInsetStretch.isTableBox`
// already use it. The CSS-authored case (`display: table` on a `<div>`)
// gets the CSS initial value 0 instead — the UA sheet targets the
// ELEMENT, not the display type.
//
// ## Wiring status on this platform (honest scope)
// The decision table is live on iOS (ComponentRenderer's table region
// reads it). On Compose the consumer is `TableExtractor.extractTableConfig`,
// whose `sourceTag` parameter defaults to null so the existing
// `ComponentRenderer` call site — which lane T does not own — stays
// byte-identical. Threading `component._tag` at that one call site is
// what turns the UA lane on for Android; it is recorded as a deferred
// item rather than reached across an ownership boundary.
//
// The arithmetic here is pure — no Compose types — so the whole table is
// pinnable on the JVM without Robolectric, the standing constraint of
// this suite.

import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

object TableSeparatedTracks {

    /**
     * The used `border-spacing` of one table box, in px.
     *
     * @property horizontalPx §17.6.1 horizontal spacing — between adjacent
     *   cells in a row, and (with [outerBandApplies]) inside the table's
     *   left/right edges.
     * @property verticalPx §17.6.1 vertical spacing — between adjacent
     *   rows, and inside the table's top/bottom edges.
     */
    data class Spacing(val horizontalPx: Double, val verticalPx: Double) {
        companion object {
            /** The CSS initial value of `border-spacing` (§17.6.1: `0`). */
            val ZERO = Spacing(0.0, 0.0)
        }
    }

    /**
     * How a box of a given role arranges its in-flow children under the
     * separated model.
     */
    enum class Arrangement {
        /** Not a track-forming box — the caller keeps its normal path. */
        NONE,

        /**
         * Rows stack in the BLOCK direction, separated by the vertical
         * border-spacing (§17.6.1). Used by the table box and by every row
         * group, which is transparent for row ordering (§2.1).
         */
        BLOCK_STACK,

        /**
         * Cells lay out in the INLINE direction, separated by the
         * horizontal border-spacing (§17.6.1). This is the fix.
         */
        INLINE_ROW
    }

    /**
     * The HTML Standard rendering UA stylesheet's `border-spacing` for a
     * `<table>` ELEMENT (HTML §15.3.3 Tables). NOT a CSS initial value —
     * `border-spacing`'s initial value is 0 (CSS 2.1 §17.6.1).
     */
    const val HTML_UA_BORDER_SPACING_PX: Double = 2.0

    /**
     * The element names the UA sheet's `table` rule matches. `<table>`
     * only: the HTML UA rule is written against that element, and
     * `border-spacing` inherits, so no other tag needs to claim it.
     */
    private val UA_TABLE_TAGS = setOf("table")

    /**
     * The used `border-spacing` for a TABLE box, or null when the
     * separated model does not apply to it.
     *
     * null when `border-collapse: collapse` — §17.6.2's collapsing model
     * has no border-spacing at all ("the `border-spacing` property is
     * ignored"), so a null answer tells the caller to leave the box on its
     * existing path rather than to insert zero-width tracks.
     *
     * @param properties the table box's own resolved declarations.
     * @param sourceTag its `meta.sourceTag`, for the HTML UA default lane.
     */
    fun usedSpacing(
        properties: List<Pair<String, JsonElement?>>,
        sourceTag: String?
    ): Spacing? {
        // §17.6.2 — the collapsing model ignores border-spacing entirely.
        // Read the raw keyword through the shared decoder; anything other
        // than `collapse` (including an absent declaration) is `separate`,
        // which is both the CSS initial value and the HTML UA value.
        val collapse = properties.firstOrNull { it.first == "BorderCollapse" }?.second
        if (collapse != null &&
            ValueExtractors.extractKeyword(collapse)?.uppercase()?.replace('-', '_') == "COLLAPSE"
        ) {
            return null
        }
        // An AUTHOR-declared border-spacing always wins over the UA sheet
        // (CSS 2.1 §6.4.1 cascade order: author beats user agent).
        val declared = properties.firstOrNull { it.first == "BorderSpacing" }?.second
        if (declared != null) {
            declaredSpacing(declared)?.let { return it }
        }
        // No declaration: the HTML UA sheet's 2px for a real `<table>`
        // element, the CSS initial 0 for everything else (a `display:
        // table` div is NOT matched by the UA rule).
        if (sourceTag?.lowercase() in UA_TABLE_TAGS) {
            return Spacing(HTML_UA_BORDER_SPACING_PX, HTML_UA_BORDER_SPACING_PX)
        }
        return Spacing.ZERO
    }

    /**
     * Decode the wire's `BorderSpacing` payload.
     *
     * The converter emits exactly two shapes (verified against
     * `converter/…/irmodels/properties/table/BorderSpacingProperty.kt`):
     *   • `{"type":"single","px":4}`
     *   • `{"type":"two-values","horizontal":{"px":3},"vertical":{"px":5}}`
     * Anything else (a `calc()`/`em` that stayed unresolved, i.e. the
     * wire's `null`-means-runtime-dependent contract) answers null, and the
     * caller falls through to the UA/initial lane rather than inventing a
     * number.
     */
    private fun declaredSpacing(data: JsonElement): Spacing? {
        val obj = data as? JsonObject ?: return null
        // Two-values form: independent horizontal / vertical lengths.
        val h = ValueExtractors.extractDp(obj["horizontal"])
        val v = ValueExtractors.extractDp(obj["vertical"])
        if (h != null && v != null) {
            return Spacing(h.value.toDouble(), v.value.toDouble())
        }
        // Single form: one length used on both axes (§17.6.1).
        val single = ValueExtractors.extractDp(data) ?: return null
        return Spacing(single.value.toDouble(), single.value.toDouble())
    }

    /** How a box of this role arranges its in-flow children (§17.6.1). */
    fun arrangement(role: TableBoxTree.Role): Arrangement = when (role) {
        // The table box stacks its rows / row groups in the block
        // direction; a row group is transparent for row ordering (§2.1)
        // and therefore stacks identically.
        TableBoxTree.Role.TABLE, TableBoxTree.Role.ROW_GROUP -> Arrangement.BLOCK_STACK
        // The one behavioural change this module exists for.
        TableBoxTree.Role.ROW -> Arrangement.INLINE_ROW
        // A cell / caption establishes a BLOCK CONTAINER for its contents
        // (§2.1) — it does not form tracks of its own.
        TableBoxTree.Role.CELL, TableBoxTree.Role.CAPTION, TableBoxTree.Role.NONE ->
            Arrangement.NONE
    }

    /**
     * Does this role carry the OUTER spacing band — §17.6.1's "distance
     * between the border of the table box and the borders of the cells on
     * the edge of the table"?
     *
     * The table box only. A row group sits INSIDE that band, so adding it
     * there again would double-count the edge spacing.
     */
    fun outerBandApplies(role: TableBoxTree.Role): Boolean =
        role == TableBoxTree.Role.TABLE

    // ── Track arithmetic ───────────────────────────────────────────────

    /**
     * The along-axis origins of consecutive tracks, relative to the
     * container's own content origin.
     *
     * One band in from the edge, then each track's extent plus one
     * spacing before the next — CSS 2.1 §17.6.1's two distances in one
     * scan. This is the arithmetic that turns
     * abspos-container-change-dynamic-001's two cells (13.0469 and
     * 12.4688 border-box widths, 2px spacing, 2px band on the table one
     * level up) into the reference's cell-2 x of 33.047.
     *
     * Shared by both natives so a row's cell origins cannot drift between
     * them; the platform adapters only translate the result into
     * `Placeable.place()` / SwiftUI `place(at:)`.
     */
    fun trackOrigins(extents: List<Double>, spacing: Double, band: Double): List<Double> {
        val out = ArrayList<Double>(extents.size)
        // The first track starts one band in from the content edge.
        var cursor = band
        for (e in extents) {
            out.add(cursor)
            // Advance past this track and the inter-track spacing.
            cursor += e + spacing
        }
        return out
    }

    /**
     * The container's own extent along the track axis: every track, the
     * n−1 gaps between them, and the band on BOTH edges.
     */
    fun trackExtent(extents: List<Double>, spacing: Double, band: Double): Double {
        val gaps = spacing * maxOf(0, extents.size - 1)
        return extents.sum() + gaps + band * 2
    }
}
