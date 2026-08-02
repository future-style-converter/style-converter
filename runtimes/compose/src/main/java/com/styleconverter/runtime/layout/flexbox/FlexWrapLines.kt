package com.styleconverter.runtime.layout.flexbox

// Wave 25 lane CFLEX — CAL-RC5, the PURE half: css-flexbox-1 §9.3
// "Collect flex items into flex lines" + §9.4 step 8 (align-content
// stretch). Pure Int arithmetic, no Compose types, so the JVM suite pins
// the line geometry directly instead of through a composition.
//
// Compose's FlowRow owns neither rule in the shape CSS needs: it breaks
// lines (§9.3) but sizes each line to its tallest item and then leaves any
// leftover cross space to the container's arrangement, so a wrapping flex
// container with a DEFINITE cross size never grows its lines — which is
// what made css-gaps flex-gap-decorations-001/002 render empty on Android
// (four width-only children, auto height → 0-height lines → nothing to
// paint, while the browser stretched each line to half the 100px box).
object FlexWrapLines {

    /** One flex line as an INCLUSIVE index range into the item list. */
    data class Line(val first: Int, val last: Int) {
        /** Item count — used for the gap arithmetic on both axes. */
        val count: Int get() = last - first + 1
    }

    /**
     * §9.3 — greedy line collection along the main axis.
     *
     * @param mainSizes  each item's hypothetical main size, in px.
     * @param containerMain the line's main-axis budget. Pass
     *        [Int.MAX_VALUE] for an unbounded container: every item then
     *        lands on one line, which is what an unconstrained flex
     *        container does (nothing can overflow, so nothing wraps).
     * @param gap main-axis gap (`column-gap` for a row container) — it
     *        counts against the budget exactly like item size does
     *        (css-align-3 §8.1: gaps participate in line breaking).
     * @return lines in document order; never empty unless [mainSizes] is.
     *
     * §9.3 requires at least ONE item per line even when that item alone
     * overflows, so the "does it fit" test is skipped for a line's first
     * item — otherwise a single oversized child would loop forever.
     */
    fun breakLines(mainSizes: IntArray, containerMain: Int, gap: Int): List<Line> {
        if (mainSizes.isEmpty()) return emptyList()
        val lines = mutableListOf<Line>()
        var first = 0
        // Running main-axis extent of the line under construction.
        var used = 0L
        for (i in mainSizes.indices) {
            // Cost of appending item i: its size, plus a gap when it is not
            // the line's first item.
            val add = mainSizes[i].toLong() + if (i == first) 0L else gap.toLong()
            if (i > first && used + add > containerMain.toLong()) {
                // Doesn't fit → close the current line and start a new one
                // whose first item is unconditionally accepted.
                lines += Line(first, i - 1)
                first = i
                used = mainSizes[i].toLong()
            } else {
                used += add
            }
        }
        lines += Line(first, mainSizes.lastIndex)
        return lines
    }

    /**
     * The §9.4-step-8 PRECONDITION, factored out so the JVM suite can pin
     * it (the Layout body that consumes it cannot be unit-tested here).
     *
     * Step 8 grows the lines only when BOTH hold: the container's cross
     * size is definite ([hasFixedCross] — Compose's fixed block band), and
     * `align-content` is `normal`/`stretch` ([alignContentStretches]).
     * css-align-3 §5.3: every other keyword leaves the leftover as free
     * space and merely POSITIONS the line block.
     *
     * @return the definite cross size to distribute into, or null for the
     *         "lines hug their content" case [stretchLines] returns intact.
     */
    fun definiteCrossOrNull(
        alignContentStretches: Boolean,
        hasFixedCross: Boolean,
        maxCross: Int
    ): Int? = if (alignContentStretches && hasFixedCross) maxCross else null

    /**
     * §9.4 step 8 — `align-content: stretch` (the CSS `normal` default for
     * a flex container): when the container's cross size is DEFINITE and
     * the lines leave free space, every line grows by an equal share.
     *
     * @param base per-line cross size, each the max hypothetical cross size
     *        of its items (§9.4 step 7).
     * @param containerCross the container's definite cross CONTENT size, or
     *        null when the container hugs its lines (auto cross size) — the
     *        spec's "align-content has no effect" case, returned unchanged.
     * @param gap cross-axis gap (`row-gap` for a row container).
     * @return per-line cross size after distribution. Never shrinks a line:
     *         a negative leftover means the lines overflow, which CSS
     *         permits (the container clips or spills, it does not compress).
     *
     * The share is integer-split with the remainder handed to the leading
     * lines, so the lines always sum EXACTLY to the container's cross size
     * — a fractional split would leave a 1px seam that the gap-decoration
     * painter would then draw in the wrong place.
     */
    fun stretchLines(base: IntArray, containerCross: Int?, gap: Int): IntArray {
        if (containerCross == null || base.isEmpty()) return base
        val gaps = gap.toLong() * (base.size - 1)
        val leftover = containerCross.toLong() - base.sumOf { it.toLong() } - gaps
        if (leftover <= 0L) return base
        val share = (leftover / base.size).toInt()
        val remainder = (leftover % base.size).toInt()
        return IntArray(base.size) { base[it] + share + if (it < remainder) 1 else 0 }
    }
}
