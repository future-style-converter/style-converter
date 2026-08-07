package com.styleconverter.runtime.table

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Configuration for CSS table-related properties.
 *
 * ## Supported Properties
 * - table-layout: auto, fixed
 * - border-collapse: separate, collapse
 * - border-spacing: horizontal and vertical
 * - caption-side: top, bottom
 * - empty-cells: show, hide
 *
 * ## Compose Mapping
 * Compose doesn't have native table support. This config is used for
 * custom table implementations using Column/Row/LazyColumn with dividers.
 */
data class TableConfig(
    /** Table layout algorithm (auto or fixed) */
    val layout: TableLayout = TableLayout.AUTO,
    /** Border collapse mode */
    val borderCollapse: BorderCollapse = BorderCollapse.SEPARATE,
    /** Horizontal border spacing */
    val borderSpacingHorizontal: Dp? = null,
    /** Vertical border spacing */
    val borderSpacingVertical: Dp? = null,
    /** Caption position */
    val captionSide: CaptionSide = CaptionSide.TOP,
    /** Empty cells visibility */
    val emptyCells: EmptyCells = EmptyCells.SHOW,
    /**
     * Wave 34 (lane T) — the USED `border-spacing` (CSS 2.1 §17.6.1),
     * resolved by [TableSeparatedTracks.usedSpacing]: the declared value
     * when there is one, the HTML UA sheet's 2px for a bare `<table>`
     * ELEMENT otherwise, and null under `border-collapse: collapse`
     * (§17.6.2 ignores border-spacing entirely).
     *
     * Distinct from [borderSpacingHorizontal]/[borderSpacingVertical],
     * which stay exactly what the WIRE declared — [hasTableConfig] reads
     * those, and folding a UA default into them would flip that predicate
     * for every table in the corpus.
     *
     * Null default keeps every pre-wave-34 construction byte-identical.
     */
    val usedSpacing: TableSeparatedTracks.Spacing? = null
) {
    val hasTableConfig: Boolean
        get() = layout != TableLayout.AUTO ||
                borderCollapse != BorderCollapse.SEPARATE ||
                borderSpacingHorizontal != null ||
                borderSpacingVertical != null ||
                captionSide != CaptionSide.TOP ||
                emptyCells != EmptyCells.SHOW

    val effectiveSpacingHorizontal: Dp
        get() = if (borderCollapse == BorderCollapse.COLLAPSE) {
            0.dp
        } else {
            // Wave 34 (lane T): the §17.6.1 used value when the extractor
            // resolved one — that is the lane carrying the HTML UA 2px for
            // a bare `<table>`. It can only DIFFER from the line below when
            // a source tag was supplied, so every existing call site keeps
            // its exact number.
            usedSpacing?.horizontalPx?.dp ?: borderSpacingHorizontal ?: 0.dp
        }

    val effectiveSpacingVertical: Dp
        get() = if (borderCollapse == BorderCollapse.COLLAPSE) {
            0.dp
        } else {
            usedSpacing?.verticalPx?.dp ?: borderSpacingVertical ?: 0.dp
        }
}

/**
 * Table layout algorithm.
 */
enum class TableLayout {
    /** Column widths based on content */
    AUTO,
    /** Fixed column widths based on first row */
    FIXED
}

/**
 * Border collapse mode.
 */
enum class BorderCollapse {
    /** Borders are separated */
    SEPARATE,
    /** Adjacent borders are merged */
    COLLAPSE
}

/**
 * Caption position.
 */
enum class CaptionSide {
    TOP, BOTTOM
}

/**
 * Empty cells visibility.
 */
enum class EmptyCells {
    SHOW, HIDE
}
