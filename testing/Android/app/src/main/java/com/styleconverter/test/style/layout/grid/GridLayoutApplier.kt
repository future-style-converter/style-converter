package com.styleconverter.test.style.layout.grid

// Phase 7b grid style-engine applier.
//
// Consumes the grid-related fields of the aggregate LayoutConfig and
// produces the Compose cells description + child placement info the
// container renderer needs.
//
// This is intentionally lightweight — the existing [GridRenderer] already
// knows how to render from the legacy GridConfig + GridItemConfig shapes.
// The applier here produces a [GridCellsPlan] summary that a future
// ComponentRenderer rewrite (step 6) can consume without re-parsing. The
// legacy render path stays authoritative until that step.

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.test.style.layout.GridLine
import com.styleconverter.test.style.layout.GridLinePair
import com.styleconverter.test.style.layout.GridPlacement
import com.styleconverter.test.style.layout.GridTrackList
import com.styleconverter.test.style.layout.LayoutConfig
import com.styleconverter.test.style.layout.Track

/**
 * Flat, Compose-friendly description of a grid container's column axis.
 * The applier picks the right [GridCells] subclass based on the track-list
 * shape. Rows use the same enum; LazyVerticalGrid only needs the column
 * side for its [GridCells] argument, but callers handle rows similarly.
 */
sealed class GridCellsPlan {
    /** LazyVerticalGrid's Fixed(N) — N equal-sized columns. */
    data class FixedCount(val count: Int) : GridCellsPlan()
    /** LazyVerticalGrid's FixedSize(dp) — track width fixed in px. */
    data class FixedSize(val size: Dp) : GridCellsPlan()
    /** LazyVerticalGrid's Adaptive(minSize) for repeat(auto-fill/auto-fit). */
    data class Adaptive(val minSize: Dp) : GridCellsPlan()
    /** No translation possible — caller falls back to legacy renderer. */
    data object Unsupported : GridCellsPlan()
}

/**
 * Child-side grid placement summary. Row and column are 0-based [IntRange]
 * for easy consumption by custom Layout; null means "auto — let the grid
 * engine place it via auto-flow."
 */
data class GridItemPlacement(
    val column: IntRange? = null,
    val row: IntRange? = null,
) {
    companion object { val Auto = GridItemPlacement() }
}

object GridLayoutApplier {

    /**
     * Translate the container-side track list into a [GridCellsPlan].
     * Returns [GridCellsPlan.Unsupported] for non-uniform track lists
     * (e.g. "80px 1fr 20%") — LazyVerticalGrid can't express those so
     * the renderer must take the custom-layout legacy path.
     */
    fun cellsPlan(trackList: GridTrackList?): GridCellsPlan {
        if (trackList == null) return GridCellsPlan.Unsupported
        return when (trackList) {
            GridTrackList.None -> GridCellsPlan.Unsupported
            is GridTrackList.Adaptive -> GridCellsPlan.Adaptive(trackList.minSize.dp)
            is GridTrackList.Explicit -> {
                val tracks = trackList.tracks
                if (tracks.isEmpty()) return GridCellsPlan.Unsupported
                // Uniform N × fr → Fixed(N). All fr values must be equal —
                // Fixed(N) doesn't support weighting, so unequal weights
                // can't be expressed here (fall through to Unsupported).
                val frs = tracks.mapNotNull { (it as? Track.Flexible)?.fr }
                if (frs.size == tracks.size && frs.all { it == frs[0] }) {
                    return GridCellsPlan.FixedCount(tracks.size)
                }
                // Uniform N × Fixed(px) with all equal sizes → FixedSize(px).
                val pxs = tracks.mapNotNull { (it as? Track.Fixed)?.px }
                if (pxs.size == tracks.size && pxs.all { it == pxs[0] }) {
                    return GridCellsPlan.FixedSize(pxs[0].dp)
                }
                // Mixed or non-uniform — legacy renderer handles.
                GridCellsPlan.Unsupported
            }
        }
    }

    /**
     * Resolve the child's [GridPlacement] into concrete 0-based IntRanges
     * given the container's [LayoutConfig] (for template-areas lookup) and
     * the resolved columnCount. Span counts convert relative to the start
     * line using CSS's "span starts at start, ends at start + count" rule.
     */
    fun placement(
        itemConfig: LayoutConfig,
        container: LayoutConfig,
    ): GridItemPlacement {
        // Named area takes precedence (CSS grid-area named form).
        (itemConfig.gridArea as? GridPlacement.Named)?.let { named ->
            val areas = container.gridTemplateAreas ?: return GridItemPlacement.Auto
            return lookupNamedArea(named.name, areas)
        }
        // Explicit 4-line shorthand on gridArea.
        (itemConfig.gridArea as? GridPlacement.Lines)?.let { lines ->
            return GridItemPlacement(
                column = linesToRange(lines.columnStart, lines.columnEnd),
                row = linesToRange(lines.rowStart, lines.rowEnd),
            )
        }
        // Otherwise assemble from grid-column / grid-row pairs.
        val colRange = itemConfig.gridColumn?.let { linesToRange(it.start, it.end) }
        val rowRange = itemConfig.gridRow?.let { linesToRange(it.start, it.end) }
        return GridItemPlacement(column = colRange, row = rowRange)
    }

    /**
     * Convert (start, end) grid lines into a 0-based [IntRange] (inclusive
     * start, inclusive end — renderer typically uses +1 for columnEnd when
     * passing to LazyGrid spans). Returns null when we can't resolve
     * (e.g. both sides Auto, or Span without a known start).
     */
    private fun linesToRange(start: GridLine, end: GridLine): IntRange? {
        val startIdx = (start as? GridLine.Line)?.index?.let { it - 1 } // CSS 1-based → 0-based
        return when {
            start is GridLine.Auto && end is GridLine.Auto -> null
            startIdx != null && end is GridLine.Line -> startIdx until (end.index - 1).coerceAtLeast(startIdx + 1)
            startIdx != null && end is GridLine.Span -> startIdx until (startIdx + end.count)
            startIdx != null && end is GridLine.Auto -> startIdx..startIdx
            // Span-only / end-only placement — can't resolve without auto-flow.
            // Returning null lets the legacy renderer take over.
            else -> null
        }
    }

    /**
     * Look up a named area in a 2-D template-areas grid, returning the
     * bounding box (min..max row, min..max col) as 0-based ranges.
     * Mirrors the existing [GridTemplateAreas.parse] behaviour.
     */
    private fun lookupNamedArea(
        name: String,
        areas: List<List<String>>,
    ): GridItemPlacement {
        var minRow = Int.MAX_VALUE; var maxRow = Int.MIN_VALUE
        var minCol = Int.MAX_VALUE; var maxCol = Int.MIN_VALUE
        for ((r, row) in areas.withIndex()) for ((c, cell) in row.withIndex()) if (cell == name) {
            if (r < minRow) minRow = r; if (r > maxRow) maxRow = r
            if (c < minCol) minCol = c; if (c > maxCol) maxCol = c
        }
        // No match → auto placement, same as a typo'd area name in CSS.
        if (minRow == Int.MAX_VALUE) return GridItemPlacement.Auto
        return GridItemPlacement(column = minCol..maxCol, row = minRow..maxRow)
    }

    /** Does this LayoutConfig describe a grid container? */
    fun isGridContainer(config: LayoutConfig): Boolean =
        config.display == com.styleconverter.test.style.layout.DisplayKind.Grid ||
            config.display == com.styleconverter.test.style.layout.DisplayKind.InlineGrid ||
            config.gridTemplateColumns != null ||
            config.gridTemplateRows != null ||
            config.gridTemplateAreas != null
}
