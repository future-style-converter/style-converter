package com.styleconverter.runtime.color

// css-backgrounds-3 §2.4 background-repeat tile placement — PURE math,
// no Compose types, so the space/round rules are pinned by plain JVM tests
// (BackgroundTileMathTest). ColorApplier feeds one axisPlan per axis and
// draws the cartesian product of the two per-axis [start, end) segment
// lists (pixel-snapped for the abutting modes — see AxisPlan).

import kotlin.math.floor
import kotlin.math.roundToInt

object BackgroundTileMath {

    /**
     * The resolved tiling of ONE axis: the (possibly `round`-rescaled) tile
     * size plus every tile's DRAWN segment — tile i spans
     * [origins[i], ends[i]). Empty [origins] = nothing to draw (degenerate
     * tile/area).
     *
     * [tileSize] is the SHADER pitch (fractional for `round`, e.g. 200/7):
     * gradient geometry must resolve against it (css-images-4 §3.1 sizes
     * the gradient box, not the rasterized rect). [ends] is the DRAW edge:
     * for the abutting modes (REPEAT/ROUND) the edges are pixel-snapped so
     * adjacent rects share INTEGER boundaries — two independently
     * antialiased rects meeting on a fractional edge never sum to full
     * coverage, so every interior boundary leaked a light background seam
     * (the Repeat_Round 0.946 SSIM straggler vs Chromium's seamless
     * pattern rasterization). Non-abutting modes (NO_REPEAT/SPACE) keep
     * exact ends — their tiles never share an edge, so there is no seam to
     * close and no reason to perturb the spec'd geometry; the default
     * derives end = start + tileSize for them.
     */
    data class AxisPlan(
        val tileSize: Float,
        val origins: List<Float>,
        // Default: exact (unsnapped) ends — overridden by REPEAT/ROUND.
        val ends: List<Float> = origins.map { it + tileSize }
    )

    /**
     * Pixel-snap one lattice edge. floor(x + 0.5) is exactly Kotlin's
     * roundToInt contract (ties toward +∞) spelled out so the Swift port
     * (BackgroundTileMath.swift) can pin the IDENTICAL rule — Swift's
     * default `.rounded()` breaks ties away from zero, which diverges on
     * negative REPEAT overhang edges like −20.5.
     */
    private fun snap(x: Float): Float = floor(x + 0.5f)

    /**
     * Place tiles of [tile] px along an axis of [area] px.
     *
     * @param anchor the background-position-resolved offset of the FIRST
     *        tile (free-space × fraction + any px offset). Only NO_REPEAT
     *        honours it exactly; REPEAT phase-shifts the grid through it
     *        (css-backgrounds-3 §2.6: position anchors the tiling pattern),
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
                // Collect the FRACTIONAL grid edges (start + k·tile) — one
                // past the last tile so every tile has a closing edge.
                val edges = mutableListOf<Float>()
                var x = start
                while (x < area) {
                    edges.add(x)
                    x += tile
                }
                edges.add(x) // closing edge of the final (clipped) tile
                // REPEAT is an abutting lattice: snap every edge so adjacent
                // rects meet on integer pixels (identity for integer pitch +
                // anchor — see AxisPlan doc for the seam rationale).
                val snapped = edges.map { snap(it) }
                // tileSize stays the fractional pitch for the shader; the
                // drawn segments are consecutive snapped edge pairs.
                AxisPlan(tile, origins = snapped.dropLast(1), ends = snapped.drop(1))
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
                // Fractional pitch (e.g. 200/7 ≈ 28.571) at snapped shared
                // edges: edge i = snap(i·X'), so tile i draws
                // [snap(i·X'), snap((i+1)·X')) — per-tile width varies ±1px
                // but adjacent rects abut on integer pixels (no AA seam).
                // The SHADER keeps the fractional `rounded` pitch untouched.
                val edges = List(n + 1) { i -> snap(i * rounded) }
                AxisPlan(rounded, origins = edges.dropLast(1), ends = edges.drop(1))
            }
        }
    }
}
