package com.styleconverter.runtime.layout.grid

// Wave 19 (lane GRID-DISTRIBUTION): content-distribution of the grid TRACK
// GROUP inside the grid container's content box (css-align-3 §5.3
// justify-content on grid tracks) + direction:rtl column mirroring
// (css-grid-1 §7.1 / css-writing-modes: in RTL the inline START edge is the
// RIGHT content edge, so column 1 sits rightmost and the whole group packs
// toward the right). Before this wave both natives pinned the track group at
// the LEFT content edge unconditionally, so justify-content:end/center and
// direction:rtl grids (wpt css-grid descendant-static-position-002/003/004)
// never shifted — in-flow items AND the abspos static-position rects that
// anchor inside them stayed at the content-box start.
//
// SHARED-SEMANTICS CONTRACT: this object is the byte-parallel twin of iOS
// GridContentDistribution.swift — same function name, same argument list,
// same math, and the SAME pin table (GridContentDistributionTest.kt ↔
// GridContentDistributionTests.swift) so skeptic probes can diff them.
object GridContentDistribution {

    /**
     * The container justify-content domain the distribution math consumes.
     * Deliberately its OWN enum (not ComponentRenderer.JustifyContent, not
     * the iOS AlignmentKeyword) so the twin function bodies stay literally
     * identical across platforms; each platform adapts its container keyword
     * domain via [justifyOf] below / `justify(of:)` on iOS.
     */
    enum class Justify { START, END, CENTER, SPACE_BETWEEN, SPACE_AROUND, SPACE_EVENLY }

    /**
     * Physical origin (LEFT edge, px) of every column track, in LOGICAL
     * track order (index 0 = grid column 1), measured from the content-box
     * LEFT edge.
     *
     *  - [trackWidths] resolved track widths in logical order (the output of
     *    computeTrackWidths / GridTrackMath.columnWidths — sizing runs FIRST,
     *    distribution only moves the already-sized group, css-align-3 §5.3).
     *  - [gap]           the used column-gap between adjacent tracks.
     *  - [contentExtent] the grid content-box inline size the group aligns
     *    within. For an indefinite (fit-content) grid the caller passes the
     *    track footprint itself, making leftover 0 → distribution a no-op
     *    and RTL a pure order mirror inside the hugged box.
     *  - [justify]       the container's justify-content (adapted keyword).
     *  - [rtl]           direction:rtl on the container — flips which
     *    physical edge is the inline start AND reverses the physical column
     *    order (logical positions are computed start-relative, then mirrored).
     *
     * Overflow (leftover < 0) follows css-align-3 §5.3 default (unsafe)
     * semantics: end/center simply overflow the start/both side(s), and the
     * <content-distribution> fallbacks apply — space-between falls back to
     * start, space-around/space-evenly fall back to center.
     */
    fun trackOrigins(
        trackWidths: List<Double>,
        gap: Double,
        contentExtent: Double,
        justify: Justify,
        rtl: Boolean
    ): List<Double> {
        // Track count; an empty template distributes nothing.
        val n = trackWidths.size
        if (n == 0) return emptyList()
        // The group's footprint: track sum + the n-1 inner gaps (css-align-3
        // §3: gaps are part of the content to be distributed around).
        val total = trackWidths.sum() + gap * (n - 1)
        // Free space in the alignment container (may be negative = overflow).
        val leftover = contentExtent - total
        // §5.3 keyword table → (leading offset from the inline-start edge,
        // spacing BETWEEN adjacent tracks). Distribution keywords widen the
        // between-track spacing on top of the used gap.
        val lead: Double
        val between: Double
        when (justify) {
            // start (and the normal/stretch fallback the adapters fold here):
            // group flush at the inline-start edge, plain gaps.
            Justify.START -> { lead = 0.0; between = gap }
            // end: group flush at the inline-end edge — all leftover leads.
            Justify.END -> { lead = leftover; between = gap }
            // center: leftover split evenly on both sides (overflows both
            // sides equally when negative — §5.3 unsafe default).
            Justify.CENTER -> { lead = leftover / 2.0; between = gap }
            // space-between: first/last flush, leftover split into the n-1
            // inner gaps; <2 tracks or overflow → the §5.3 start fallback.
            Justify.SPACE_BETWEEN ->
                if (leftover > 0.0 && n > 1) { lead = 0.0; between = gap + leftover / (n - 1) }
                else { lead = 0.0; between = gap }
            // space-around: each track gets equal space around it — half a
            // share outside the edges, full shares between; overflow → the
            // §5.3 center fallback.
            Justify.SPACE_AROUND ->
                if (leftover > 0.0) { lead = leftover / (2.0 * n); between = gap + leftover / n }
                else { lead = leftover / 2.0; between = gap }
            // space-evenly: n+1 equal shares (edges + between); overflow →
            // the §5.3 center fallback.
            Justify.SPACE_EVENLY ->
                if (leftover > 0.0) { lead = leftover / (n + 1); between = gap + leftover / (n + 1) }
                else { lead = leftover / 2.0; between = gap }
        }
        // Logical positions: cumulative offsets from the INLINE-START edge
        // (left in LTR, right in RTL), lead first, then width+between steps.
        var p = lead
        val logical = trackWidths.map { w ->
            // Capture this track's start-relative offset, advance the cursor.
            val cur = p; p += w + between; cur
        }
        // LTR: inline start IS the left edge — logical offsets are physical.
        return if (!rtl) logical
        // RTL: mirror each track about the content box — a track whose
        // start-relative offset is p with width w has its LEFT edge at
        // extent − p − w (this reverses physical column order AND lets an
        // overflowing group spill past the LEFT edge, exactly like Chrome
        // renders descendant-static-position-002's 40px track in a 20px box).
        else logical.mapIndexed { i, pos -> contentExtent - pos - trackWidths[i] }
    }

    /**
     * Adapter: the renderer's container-level JustifyContent domain (the
     * DisplayConfig keyword FlexRow/FlexColumn already consume) → the twin
     * math's [Justify]. FLEX_START also covers the parse-level fold of
     * start/left/normal (extractDisplayConfig's default arm), matching the
     * css-align-3 §5.3 rule that `normal` behaves as `stretch`, which for a
     * template of non-auto tracks distributes nothing = start.
     */
    fun justifyOf(
        j: com.styleconverter.runtime.core.renderer.ComponentRenderer.JustifyContent
    ): Justify = when (j) {
        // flex-start / start / left / normal → group at inline start.
        com.styleconverter.runtime.core.renderer.ComponentRenderer.JustifyContent.FLEX_START -> Justify.START
        // flex-end / end / right → group at inline end.
        com.styleconverter.runtime.core.renderer.ComponentRenderer.JustifyContent.FLEX_END -> Justify.END
        // center → group centered.
        com.styleconverter.runtime.core.renderer.ComponentRenderer.JustifyContent.CENTER -> Justify.CENTER
        // The three distribution keywords map 1:1.
        com.styleconverter.runtime.core.renderer.ComponentRenderer.JustifyContent.SPACE_BETWEEN -> Justify.SPACE_BETWEEN
        com.styleconverter.runtime.core.renderer.ComponentRenderer.JustifyContent.SPACE_AROUND -> Justify.SPACE_AROUND
        com.styleconverter.runtime.core.renderer.ComponentRenderer.JustifyContent.SPACE_EVENLY -> Justify.SPACE_EVENLY
    }
}
