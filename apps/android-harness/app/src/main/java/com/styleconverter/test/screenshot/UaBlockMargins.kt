package com.styleconverter.test.screenshot

import com.styleconverter.runtime.core.ir.IRComponent

/**
 * Pure user-agent default block-margin model for the COMPOSED WPT capture
 * (TITAN Round 4b, FIX 1). No Compose/Android runtime — fully JVM-testable
 * (see UaBlockMarginsTest).
 *
 * ## Why this exists
 * The composed canvas (ScreenshotCaptureScreen.ComposedCaptureCanvas) stacks
 * every WPT test's roots in a Column. The Chromium browser-ref
 * (tools/titan/capture-browser-ref.mjs) renders the reference page with its FULL
 * UA stylesheet intact, so a `<p>` bar keeps its 1em (≈16px @16px root) block
 * margins and adjacent bars COLLAPSE to a single ~16px gap. Compose has no UA
 * stylesheet and no margin collapsing, so the composed bars render FLUSH and a
 * 10-bar test (background-color-hsl-001 …) scores far below the ref.
 *
 * The web fix reverts the composed elements' margins to the UA origin
 * (`margin: revert`) and lets block-flow collapsing happen natively. Natives
 * have no UA sheet to revert to, so we EMULATE the same values + collapsing here
 * and inject them as vertical spacing between the stacked roots.
 *
 * Two CSS rules this model reproduces:
 *  - An IR-declared margin WINS over the UA default (UA is the lowest-priority
 *    origin) — [effectiveUaMargins] zeroes any side the IR declares, because the
 *    runtime's margin applier already renders that side.
 *  - Adjacent vertical margins COLLAPSE to their max (CSS 2.1 §8.3.1) —
 *    [collapsedVerticalGaps]. Two 16px-margin bars → a single 16px gap.
 */

/** UA default block margins in px (at a 16px root font). */
data class UaMargins(val top: Int, val bottom: Int, val left: Int, val right: Int) {
    companion object { val ZERO = UaMargins(0, 0, 0, 0) }
}

/**
 * The UA stylesheet's default block margins for a source tag, at a 16px root
 * font — the exact per-tag values the browser-ref's `<p>`/`<h*>`/… render with
 * (CSS 2.1 §D.2 default sheet, calibrated to the browser-ref this round diffs
 * against). Tags with no block margin in the UA sheet (div/section/article/
 * header/footer/main/nav/aside) and unknown/null tags return [UaMargins.ZERO].
 */
fun uaBlockMargins(sourceTag: String?): UaMargins = when (sourceTag?.lowercase()) {
    // <p>: 1em top+bottom.
    "p" -> UaMargins(16, 16, 0, 0)
    // Headings: margin scales with the heading's own (larger/smaller) font.
    "h1" -> UaMargins(21, 21, 0, 0) // 0.67em of 2em    ≈ 21px
    "h2" -> UaMargins(19, 19, 0, 0) // 0.83em of 1.5em  ≈ 19px
    "h3" -> UaMargins(16, 16, 0, 0) // 1em of 1.17em    ≈ 16px (ref-calibrated)
    "h4" -> UaMargins(21, 21, 0, 0) // 1.33em of 1em    ≈ 21px
    "h5" -> UaMargins(27, 27, 0, 0) // 1.67em of 0.83em ≈ 27px
    "h6" -> UaMargins(37, 37, 0, 0) // 2.33em of 0.67em ≈ 37px
    // Lists: 1em top+bottom (left padding is list-marker inset, not margin).
    "ul", "ol" -> UaMargins(16, 16, 0, 0)
    // blockquote / figure: 1em block margins + 40px left/right insets.
    "blockquote" -> UaMargins(16, 16, 40, 40)
    "figure" -> UaMargins(16, 16, 40, 40)
    // <pre>: 1em top+bottom.
    "pre" -> UaMargins(16, 16, 0, 0)
    else -> UaMargins.ZERO
}

/**
 * Map from a margin IR-property type name to the physical side(s) it defines.
 * Logical properties collapse to physical sides in the LTR-normalized engine
 * (block = top/bottom, inline = left/right); the shorthands expand to their set.
 */
private val MARGIN_SIDE_TYPES: Map<String, Set<String>> = mapOf(
    "MarginTop" to setOf("top"),
    "MarginBottom" to setOf("bottom"),
    "MarginLeft" to setOf("left"),
    "MarginRight" to setOf("right"),
    "MarginBlockStart" to setOf("top"),
    "MarginBlockEnd" to setOf("bottom"),
    "MarginInlineStart" to setOf("left"),
    "MarginInlineEnd" to setOf("right"),
    "MarginBlock" to setOf("top", "bottom"),
    "MarginInline" to setOf("left", "right"),
    "Margin" to setOf("top", "bottom", "left", "right"),
)

/** Which physical sides the given IR property types declare a margin for. */
fun declaredMarginSides(propertyTypes: Collection<String>): Set<String> {
    val out = mutableSetOf<String>()
    for (t in propertyTypes) MARGIN_SIDE_TYPES[t]?.let { out.addAll(it) }
    return out
}

/**
 * The UA default margins with any IR-declared side zeroed out — because an
 * IR-declared margin (higher-priority origin) already renders through the
 * runtime's margin applier and MUST win over the UA default. Pure/testable.
 */
fun effectiveUaMargins(sourceTag: String?, declaredSides: Set<String>): UaMargins {
    val ua = uaBlockMargins(sourceTag)
    return UaMargins(
        top = if ("top" in declaredSides) 0 else ua.top,
        bottom = if ("bottom" in declaredSides) 0 else ua.bottom,
        left = if ("left" in declaredSides) 0 else ua.left,
        right = if ("right" in declaredSides) 0 else ua.right,
    )
}

/** [effectiveUaMargins] for a decoded component (reads `_tag` + property types). */
fun effectiveUaMargins(component: IRComponent): UaMargins =
    effectiveUaMargins(component._tag, declaredMarginSides(component.properties.map { it.type }))

/**
 * Collapsed vertical GAPS to insert around a vertical stack of blocks whose
 * outermost edges sit against a PADDED container (the composed canvas has 16dp
 * padding, and padding blocks a box's margin from collapsing with its parent —
 * so the first block's top margin and the last block's bottom margin are kept
 * in full, NOT collapsed away).
 *
 * @param margins ordered `(topMargin, bottomMargin)` per block, top-to-bottom.
 * @return `n + 1` gaps: `[beforeFirst, between0-1, between1-2, …, afterLast]`.
 *   Between two blocks the gap is the MAX of the lower block's bottom margin and
 *   the upper block's top margin (positive-margin collapsing, CSS 2.1 §8.3.1).
 *   Negative margins are out of scope (UA defaults are all positive) and are
 *   floored at 0.
 */
fun collapsedVerticalGaps(margins: List<Pair<Int, Int>>): List<Int> {
    if (margins.isEmpty()) return listOf(0)
    val gaps = IntArray(margins.size + 1)
    // First block's top margin — preserved (padding blocks collapse to parent).
    gaps[0] = margins.first().first.coerceAtLeast(0)
    // Between adjacent blocks: collapse to the max of the abutting margins.
    for (i in 1 until margins.size) {
        gaps[i] = maxOf(margins[i - 1].second, margins[i].first).coerceAtLeast(0)
    }
    // Last block's bottom margin — preserved (padding blocks collapse to parent).
    gaps[margins.size] = margins.last().second.coerceAtLeast(0)
    return gaps.toList()
}
