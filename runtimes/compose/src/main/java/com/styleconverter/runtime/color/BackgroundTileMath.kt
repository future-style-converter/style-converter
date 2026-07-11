package com.styleconverter.runtime.color

// css-backgrounds-3 §3.7 background-repeat tile placement — PURE math,
// no Compose types, so the space/round rules are pinned by plain JVM tests
// (BackgroundTileMathTest). ColorApplier feeds one axisPlan per axis and
// draws the cartesian product of the two origin lists.

import kotlin.math.floor
import kotlin.math.roundToInt

object BackgroundTileMath {

    /**
     * The resolved tiling of ONE axis: the (possibly `round`-rescaled) tile
     * size plus every tile's start offset inside the positioning area.
     * Empty [origins] = nothing to draw (degenerate tile/area).
     */
    data class AxisPlan(val tileSize: Float, val origins: List<Float>)

    /**
     * Place tiles of [tile] px along an axis of [area] px.
     *
     * @param anchor the background-position-resolved offset of the FIRST
     *        tile (free-space × fraction + any px offset). Only NO_REPEAT
     *        honours it exactly; REPEAT phase-shifts the grid through it
     *        (css-backgrounds-3 §3.6: position anchors the tiling pattern),
     *        SPACE/ROUND pin the pattern to the area edges per §3.7.
     */
    fun axisPlan(area: Float, tile: Float, anchor: Float, mode: AxisRepeat): AxisPlan {
        // Degenerate inputs: nothing sensible to draw. CSS treats zero-size
        // images as invisible rather than dividing by zero.
        if (area <= 0f || tile <= 0f) return AxisPlan(tile, emptyList())
        return when (mode) {
            // Single tile at the position anchor.
            AxisRepeat.NO_REPEAT -> AxisPlan(tile, listOf(anchor))
            // Edge-to-edge tiling phase-shifted so one tile edge lands on
            // the anchor: start at anchor − k·tile for the smallest k that
            // puts the first tile at or left of 0, then step until the area
            // is covered (last tile may clip past the right edge — spec'd).
            AxisRepeat.REPEAT -> {
                // Normalize the anchor into (−tile, 0] so the walk begins
                // just left of the area and every tile is grid-aligned.
                var start = anchor % tile
                if (start > 0f) start -= tile
                val origins = mutableListOf<Float>()
                var x = start
                while (x < area) {
                    origins.add(x)
                    x += tile
                }
                AxisPlan(tile, origins)
            }
            // §3.7 space: as many WHOLE tiles as fit, first and last flush
            // with the area edges, leftover split into equal gaps BETWEEN
            // tiles. Fewer than two fitting → the single tile falls back to
            // the position anchor (spec: "background-position is ignored
            // unless only one image can be placed").
            AxisRepeat.SPACE -> {
                val n = floor(area / tile).toInt()
                if (n >= 2) {
                    val gap = (area - n * tile) / (n - 1)
                    AxisPlan(tile, List(n) { i -> i * (tile + gap) })
                } else {
                    AxisPlan(tile, listOf(anchor))
                }
            }
            // §3.7 round: rescale the tile so a WHOLE number fills the area
            // exactly — X' = area / round(area / tile), never fewer than one
            // tile. Rounding half-up matches the spec's `round()`.
            AxisRepeat.ROUND -> {
                val n = (area / tile).roundToInt().coerceAtLeast(1)
                val rounded = area / n
                AxisPlan(rounded, List(n) { i -> i * rounded })
            }
        }
    }
}
