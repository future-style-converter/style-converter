package com.styleconverter.test.style.table

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
    val emptyCells: EmptyCells = EmptyCells.SHOW
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
            borderSpacingHorizontal ?: 0.dp
        }

    val effectiveSpacingVertical: Dp
        get() = if (borderCollapse == BorderCollapse.COLLAPSE) {
            0.dp
        } else {
            borderSpacingVertical ?: 0.dp
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
