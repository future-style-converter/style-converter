package com.styleconverter.runtime.columns

// css-gap-decorations-1 — SHARED GEOMETRY PRIMITIVES for the flex
// gap-decoration painter (wave 24, lane GAPS-A).
//
// This file holds only pure, allocation-cheap value types and interval
// algebra. It has NO Compose dependency so the whole segment model stays
// unit-testable on the JVM without a device (the campaign forbids device
// runs this wave). GapDecorationSegments.kt builds on it; the painter and
// the draw hook sit above both.
//
// Axis convention throughout the family is PHYSICAL, never flex-logical:
//   * a "column rule" is a bar standing in a HORIZONTAL gap (the gap that
//     `column-gap` opens), so it runs vertically;
//   * a "row rule" is a bar lying in a VERTICAL gap (`row-gap`), so it
//     runs horizontally.
// That matches css-gap-decorations-1 §2, where the rule names follow the
// gap property they decorate, not the flex main/cross axis. The mapping
// from flex lines to those two families lives in GapDecorationSegments.

/**
 * Axis-aligned rectangle in the flex container's CONTENT-BOX coordinate
 * space, in real pixels (the space the draw hook hands us: item bounds
 * translated by the container's own content-box origin).
 */
data class GapRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    /** Width; never negative for a placed box, but clamped defensively. */
    val width: Float get() = (right - left).coerceAtLeast(0f)

    /** Height; see [width]. */
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}

/**
 * Which rule family painted a segment. Drives BOTH the stroke spec used
 * (column-rule-* vs row-rule-*) and the `rule-overlap` paint order.
 */
enum class GapAxis { COLUMN, ROW }

/** One painted rule piece: a solid rectangle plus the family it came from. */
data class GapSegment(val rect: GapRect, val axis: GapAxis)

/**
 * A half-open 1-D interval `[start, end)` used for every gap / band /
 * cut computation. Kept as a plain data class (not kotlin.ranges) so the
 * emptiness rule is explicit: a zero-or-negative extent paints nothing.
 */
data class GapInterval(val start: Float, val end: Float) {
    /** True when the interval has no positive extent → contributes no paint. */
    val isEmpty: Boolean get() = end <= start

    /** Positive extent, 0 for an empty interval. */
    val size: Float get() = (end - start).coerceAtLeast(0f)
}

/**
 * Interval algebra shared by the column- and row-rule builders.
 *
 * Every helper here is total (no exceptions, no nulls-on-success) so the
 * segment builders never need a defensive branch — a degenerate input
 * simply yields an empty result list, which paints nothing. That is the
 * "no silent fallthrough" contract for geometry: nothing is dropped
 * quietly, an empty list IS the honest answer for a zero-size gap.
 */
object GapIntervals {

    /**
     * Merge overlapping/touching intervals into a minimal sorted cover.
     *
     * Needed because a row rule under `row-rule-break: intersection` is
     * cut by the column gaps of BOTH adjacent flex lines, and those two
     * sets routinely overlap when the lines have different item widths.
     * Pinned by WPT flex-gap-decorations-009: line-1 gaps {[52,62],
     * [112,122]} unioned with line-2's {[102,112]} must collapse to
     * {[52,62], [102,122]}, otherwise the middle segment is mis-cut.
     */
    fun union(intervals: List<GapInterval>): List<GapInterval> {
        // Drop degenerate members first: a zero-width gap cuts nothing.
        val sorted = intervals.filterNot { it.isEmpty }.sortedBy { it.start }
        val merged = mutableListOf<GapInterval>()
        for (iv in sorted) {
            val last = merged.lastOrNull()
            // Touching counts as overlapping ([102,112] + [112,122] is one
            // 20px cut in 009) — hence `>=`, not `>`.
            if (last != null && iv.start <= last.end) {
                merged[merged.size - 1] = GapInterval(last.start, maxOf(last.end, iv.end))
            } else {
                merged.add(iv)
            }
        }
        return merged
    }

    /**
     * Subtract a set of cut intervals from [base], returning the surviving
     * pieces in order.
     *
     * This is the `*-rule-break: intersection` operator of
     * css-gap-decorations-1 §4.2: the rule does not enter a crossing gap.
     * The cut extent is the crossing GAP, not the crossing rule's declared
     * width — pinned by WPT flex-gap-decorations-034, where a 5px row rule
     * in a 90px gap is cut by 90px-wide column gaps (ref segments
     * [0,60] [150,230] [330,390] [480,600] on a 600px content box).
     */
    fun subtract(base: GapInterval, cuts: List<GapInterval>): List<GapInterval> {
        if (base.isEmpty) return emptyList()
        val out = mutableListOf<GapInterval>()
        var cursor = base.start
        for (cut in union(cuts)) {
            // Cuts entirely before/after the base contribute nothing.
            if (cut.end <= cursor) continue
            if (cut.start >= base.end) break
            // Emit the piece that survives ahead of this cut, if any.
            if (cut.start > cursor) out.add(GapInterval(cursor, minOf(cut.start, base.end)))
            cursor = maxOf(cursor, cut.end)
            if (cursor >= base.end) break
        }
        // Tail piece after the last cut.
        if (cursor < base.end) out.add(GapInterval(cursor, base.end))
        return out.filterNot { it.isEmpty }
    }

    /**
     * Shorten an interval by [insetPx] at BOTH ends; a negative inset
     * lengthens it.
     *
     * css-gap-decorations-1 §4.3 (`*-rule-inset`). Pinned twice against
     * fresh WPT refs on the shared 003-family layout (170px content box,
     * lines at cross [2,52] [62,112] [122,172]):
     *   * 011, `column-rule-inset: -2px` → [0,54] [60,114] [120,174];
     *   * 013, `column-rule-inset:  2px` → [4,50] [64,110] [124,170].
     * Note 011 proves a negative inset is NOT clipped to the content box
     * (the top segment starts at 0, two px above the content edge).
     *
     * @return the adjusted interval, or null when a positive inset ate it
     *         entirely (a rule shorter than 2×inset paints nothing).
     */
    fun inset(iv: GapInterval, insetPx: Float): GapInterval? {
        val adjusted = GapInterval(iv.start + insetPx, iv.end - insetPx)
        return if (adjusted.isEmpty) null else adjusted
    }

    /**
     * The band a rule of [widthPx] occupies inside [gap]: centered, with
     * the declared width, NOT clamped to the gap.
     *
     * Centering is pinned by WPT flex-gap-decorations-011: a 2px column
     * rule in the 10px gap [52,62] paints at [56,58], and its 2px row
     * rule in the 10px gap [52,62] paints at [56,58] too. Rules wider
     * than their gap deliberately overflow (css-gap-decorations-1 §3
     * gives the gap no clamping role) — WPT 003's 10px rule in a 10px
     * gap is the boundary case and lands exactly on the gap.
     */
    fun band(gap: GapInterval, widthPx: Float): GapInterval {
        val center = (gap.start + gap.end) / 2f
        return GapInterval(center - widthPx / 2f, center + widthPx / 2f)
    }

    /**
     * Assemble a rectangle from a horizontal and a vertical interval.
     * Split out so the segment builders never hand-write x/y ordering.
     */
    fun rect(horizontal: GapInterval, vertical: GapInterval): GapRect =
        GapRect(horizontal.start, vertical.start, horizontal.end, vertical.end)
}
