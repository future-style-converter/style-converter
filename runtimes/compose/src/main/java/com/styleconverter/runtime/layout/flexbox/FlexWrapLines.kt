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

    /**
     * Wave 47 (lane Z7) — the §9.6 POSITIONING keywords stretchLines'
     * gate deliberately excludes: css-align-3 §5.3 says every keyword
     * except `normal`/`stretch` leaves the leftover cross space FREE and
     * places the line block inside it. The twin of the iOS runtime's
     * CSSFlexMath.mainOffsets cross-axis call (FlowLayout.placeSubviews).
     */
    enum class CrossDistribution { START, END, CENTER, SPACE_BETWEEN, SPACE_AROUND, SPACE_EVENLY }

    /**
     * Wave 48 (lane W7) — the ROUTING gate for "line geometry needs the
     * real wrap layout even though no ITEM stretches": css-flexbox-1
     * §9.4 step 8 grows the LINES whenever `align-content` stretches and
     * the container's cross size is definite, which moves every later
     * line's cross position (and the gap-decoration bands between them)
     * regardless of item alignment. Compose's FlowRow/FlowColumn never
     * run step 8, so a container matching this predicate must route
     * through FlexWrapRow/FlexWrapColumn (measured: WPT css-gaps
     * flex-gap-decorations-045 packed its second column 8px left of
     * Chromium's, -046 its second and third rows 6.7/13.3px above).
     *
     * `plainWrap` keeps `wrap-reverse` on the frozen Flow* paths — the
     * wrap layouts do not implement reverse line ordering (their named
     * TODO), and both wave48-cal wrap-reverse column tests pass on the
     * legacy path today.
     *
     * One shared, JVM-pinned predicate so the renderer's row and column
     * branches can never answer the question differently (each feeds its
     * own axis: the row branch its definite HEIGHT, the column branch
     * its definite WIDTH).
     */
    fun routesForLineStretch(
        plainWrap: Boolean,
        alignContentStretches: Boolean,
        hasDefiniteCross: Boolean
    ): Boolean = plainWrap && alignContentStretches && hasDefiniteCross

    /**
     * §9.6 — each line's cross-axis START offset, in px.
     *
     * @param lineCross per-line cross sizes (post-stretch, though under a
     *        positioning keyword stretch never fired).
     * @param containerCross the definite cross size to distribute inside,
     *        or null when the container hugs (packed offsets — the
     *        pre-wave-47 accumulation, bit for bit).
     * @param gap `row-gap` between lines — always preserved; the
     *        distributed extra ADDS to it (css-align-3 §8.3).
     * @param distribution the keyword, or null for packed (normal/
     *        stretch — their leftover is zero after stretchLines anyway).
     *
     * WPT flex-gap-decorations-047…049 are the pins: 3×40px lines in a
     * 200px box under space-between/around/evenly put the Chromium row
     * rules centred in the DISTRIBUTED gaps (frozen refs: rule bands at
     * y 74-78/154-158, 80-84/147-151, 85-89/145-149 respectively).
     * Fractions accumulate in Float and round per line so the offsets
     * match the browser's subpixel layout to ≤0.5px.
     */
    fun lineCrossOffsets(
        lineCross: IntArray,
        containerCross: Int?,
        gap: Int,
        distribution: CrossDistribution?
    ): IntArray {
        if (lineCross.isEmpty()) return IntArray(0)
        // Content extent: lines plus their gaps.
        val content = lineCross.sumOf { it.toLong() } + gap.toLong() * (lineCross.size - 1)
        // Free space only exists inside a definite container; overflow
        // (negative free) packs, like the browser.
        val free = ((containerCross?.toLong() ?: content) - content).coerceAtLeast(0L).toFloat()
        val n = lineCross.size
        // Leading inset and extra between-line spacing per keyword —
        // the css-align-3 <content-distribution> table, same arms as the
        // iOS twin's mainOffsets.
        var lead = 0f
        var between = 0f
        when (distribution) {
            CrossDistribution.END -> lead = free
            CrossDistribution.CENTER -> lead = free / 2f
            CrossDistribution.SPACE_BETWEEN -> if (n > 1) between = free / (n - 1)
            CrossDistribution.SPACE_AROUND -> { between = free / n; lead = free / (2f * n) }
            CrossDistribution.SPACE_EVENLY -> { between = free / (n + 1); lead = between }
            // START and null both pack at the cross start.
            CrossDistribution.START, null -> {}
        }
        // Accumulate fractionally, round per line (browser subpixel).
        val out = IntArray(n)
        var cursor = lead
        for (i in 0 until n) {
            out[i] = Math.round(cursor)
            cursor += lineCross[i] + gap + between
        }
        return out
    }
}
