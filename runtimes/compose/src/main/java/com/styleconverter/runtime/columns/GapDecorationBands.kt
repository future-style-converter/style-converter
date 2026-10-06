package com.styleconverter.runtime.columns

// css-flexbox-1 §9.4 step 8 + §9.6 — the LINE BAND a gap decoration is
// scoped to, as opposed to the union of the items standing on that line
// (wave 50, lane B10; queue item 7(a) in docs/BACKLOG.md).
//
// THE DEFECT THIS REPAIRS, measured against the frozen Chromium refs.
// GapDecorationLines.toLines builds each line's cross extent as the UNION
// of its item rectangles. That is the line box only while the items fill
// their line. Under `align-content: stretch` (which is what the flex
// default `normal` computes to, css-align-3 §5.1) a DEFINITE container
// cross size grows every line past its items, and the union then
// under-reports the line box at BOTH ends:
//
//   EVERY PIXEL RANGE BELOW IS AN INCLUSIVE RUN — first..last painted
//   column/row of that colour, as `tools/titan/results/wave50-S6/measure.py`
//   reports it. (Wave-50 skeptic S6-16: this banner used to write them with
//   an EXCLUSIVE upper bound while the surrounding prose read inclusive, and
//   said "both natives" where the two natives differ.) The CSS extents in the
//   next sentence are content-box start/end pairs, not pixel runs.
//
//   WPT css-gaps flex-gap-decorations-045 (column flex, width 120 content,
//   column-gap 5, two lines of 50px-wide items). Chromium's line boxes span
//   content-x 0→58 and 63→120; the item unions span 0→50 and 63→113, so the
//   inter-line gap is 58→63. Red rule (255,0,0), inclusive image columns,
//   2000 px in every one of the four images: ref x76-80 and web x76-80
//   — tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/
//   white-black-ink-font-lh-imgpad-htmlpins/css-gaps/
//   flex__flex-gap-decorations-045.png and wave49-final screenshots/ — against
//   BOTH natives at x72-76 (wave49-final android-screenshots/ +
//   ios-screenshots/ for that test). FOUR PIXELS LEFT, and the two natives
//   agree with each other here.
//
//   WPT flex-gap-decorations-046 (row flex, 180px content height, row-gap
//   5, three lines of 50px-tall items) is the same story on the other axis,
//   and it is where the natives STOP agreeing. Gold (255,215,0), inclusive
//   image rows: ref and web paint y73-77 and y134-138 (1400 px); iOS paints
//   y70-73 and y131-135 (1260 px); ANDROID paints y70-73 and y132-135
//   (1120 px). Both natives start the first band 3 rows high, but their
//   SECOND bands differ by one row — Android's starts at 132, iOS's at 131.
//
//   THAT ONE-ROW SPLIT IS ARITHMETIC, NOT A DIFFERENT MODEL (wave-50 skeptic
//   S6, third finding; the wave-50 state — superseded by the WAVE 52 note
//   below). The two runtimes run the same §9.4 step-8
//   distribution in different NUMERIC DOMAINS. Compose measures in whole
//   pixels, so this file rounded the per-line base cross sizes, the gap and
//   the container cross size to `Int` before calling
//   [FlexWrapLines.stretchLines], which splits the leftover as an INTEGER
//   share and hands the remainder to the LEADING lines — the lines then sum
//   EXACTLY to the container cross size and any odd pixel lands early. The
//   SwiftUI twin lays out in points and keeps `CGFloat` all the way through
//   `FlexWrapPlan.stretchLines`, giving every line an EQUAL FRACTIONAL share.
//
//   Derivation on 046's own numbers, the same one the Swift header carries
//   (wave-50 lane F5): base [50,50,50], gap 5, container cross 180 →
//   leftover 20. Here the lines come out 57/57/56 (share 6, remainder 2 to
//   the two leading lines); there they come out 56.67 each. So the second
//   inter-line band starts at 119 on Compose against 118.33 on SwiftUI —
//   two-thirds of a point, one row once rasterised, which is exactly the
//   y132 vs y131 split MEASURED in the frozen captures above. The first band
//   differs by only a third of a point and does not cross a pixel boundary,
//   which is why both natives agree on it.
//
//   The split itself is measured; the attribution to the two `stretchLines`
//   domains is derived from the two implementations, not from a device A/B —
//   no gate ran this wave. Bounded at ≤1 px of band edge, and it can never
//   change the band COUNT.
//
// WHY THE ARITHMETIC IS RE-RUN HERE AND NOT THREADED FROM THE LAYOUT.
// The renderer's wrapping flex path already computes these bands
// (FlexWrapLayout/FlexWrapColumn call FlexWrapLines.stretchLines), but the
// painter is a draw modifier on the CONTAINER and Compose exposes no
// post-placement channel from a child Layout to it — the item rectangles
// only reach the painter because every child reports them through
// GapDecorationHook's sink. Rather than add a second reporting channel
// through ComponentRenderer, this object re-derived the bands from the
// same inputs the layout sees.
//
// WAVE 52 (lane L10, queue 7(a′)) — NO LONGER THE LAYOUT'S INT SPLIT. It
// used to call FlexWrapLines.stretchLines itself so the rule sat between
// the integer-placed items; that reproduced the layout's +1 row instead
// of the ref (046's second band one row low) and kept the natives apart.
// The share is now the SwiftUI twin's fractional `leftover / n` and the
// painter snaps the rule rect (GapDecorationPainter.snapped) — the
// Chromium order: fractional layout, one pixel snap at paint. The
// agreement gate below still checks the placed items against the bands
// (an Int-placed item sits ≤ 1 px inside a fractional band).
//
// NO SILENT FALLTHROUGH. The reconstruction is admissible only when the
// observed geometry CONFIRMS it (every line's item union must fall inside
// the band the reconstruction claims for it). When it does not — a
// `wrap-reverse` container on the legacy FlowRow path, an overflowing
// line, a positioning `align-content` keyword — the union model is kept
// unchanged and a PropertyTracker breadcrumb is left, so the fallback is
// visible in the coverage report instead of silently wrong.

import com.styleconverter.runtime.PropertyTracker

/**
 * Promotes item-union line extents to real §9.4-step-8 line boxes.
 *
 * Pure and Compose-free, like the rest of the segment model, so the JVM
 * suite pins the numbers directly.
 */
object GapDecorationBands {

    /**
     * Slack allowed when testing that an item union sits inside its
     * reconstructed band. The layout works in whole pixels and the
     * reported rectangles come back through `positionInWindow()` as
     * Floats, so an exact containment test would reject on rounding dust.
     * Half a pixel is smaller than any rule this family can paint (the
     * thinnest CSS `<line-width>` keyword is `thin` == 1px).
     */
    private const val CONTAINMENT_EPS = 0.5f

    /**
     * Replace each line's cross extent with its §9.4-step-8 line box.
     *
     * @param lines            lines in CROSS order, as GapDecorationLines
     *                         .toLines returns them (its trailing sort is
     *                         what makes "index order == cross order" —
     *                         the reconstruction below packs lines from
     *                         the cross-start edge and relies on it).
     * @param contentBox       the container's content box, origin-relative.
     *                         Supplies the DEFINITE cross size step 8
     *                         distributes into; the painter is attached at
     *                         the end of the modifier chain, so this is the
     *                         CSS content box, which is the box
     *                         css-flexbox-1 §9.4 measures.
     * @param mainHorizontal   true for a row-direction container — decides
     *                         which physical axis is the cross axis.
     * @param crossGapPx       the used cross-axis gap in the same real
     *                         pixels as [lines] (`row-gap` for a row
     *                         container, `column-gap` for a column one),
     *                         or null when the wire left it unresolved
     *                         (`calc()`, a percentage, `var()`), in which
     *                         case no reconstruction is attempted.
     * @param alignContentStretches whether `align-content` DISTRIBUTES the
     *                         leftover cross space to the lines rather than
     *                         merely positioning the line block
     *                         (css-align-3 §5.1). Every other keyword
     *                         leaves the lines content-sized, which is
     *                         exactly what the union model already reports.
     * @return the lines with corrected cross extents, or [lines] unchanged
     *         when the reconstruction is inadmissible.
     */
    fun resolve(
        lines: List<GapFlexLine>,
        contentBox: GapRect,
        mainHorizontal: Boolean,
        crossGapPx: Float?,
        alignContentStretches: Boolean
    ): List<GapFlexLine> {
        // A single line has no inter-line gap and fills whatever the
        // container gives it: its band never changes a painted rule's
        // position, only its length, and the length it would gain is the
        // container's full cross size — which is what a §9.4 stretch
        // ACTUALLY does, but with one line the item union is also the only
        // evidence we have, so leave it alone rather than guess.
        if (lines.size < 2) return lines
        // Step 8 only fires for `normal`/`stretch` (css-align-3 §5.1).
        if (!alignContentStretches) return lines
        // An unresolved gap means the wire could not pre-compute it; the
        // band arithmetic is meaningless without it.
        val gap = crossGapPx ?: run {
            PropertyTracker.markUnhandled("GapDecorationBands:unresolved-cross-gap")
            return lines
        }
        // Cross extent of the container's content box.
        val cross = GapDecorationLines.crossOf(contentBox, mainHorizontal)
        if (cross.isEmpty) return lines

        // Wave 52 (lane L10, queue 7(a′)) — the share is FRACTIONAL now,
        // the SwiftUI twin's `leftover / n` (FlexWrapPlan.stretchLines),
        // not the Int split FlexWrapLines.stretchLines hands the layout.
        // Chromium splits the leftover in LayoutUnits and snaps only at
        // paint, so 046's bands are [0,56.67] [61.67,118.33] [123.33,180]
        // in the ref; the Int split (57/57/56) put the second rule one
        // row low (image y 135–139 against the ref's 134–138) and made
        // the natives disagree. GapDecorationPainter snaps the RULE rect
        // to whole pixels afterwards — exactly once, at paint, as Blink
        // does — so 045's 7.5-px share still lands on [58,63).
        val base = FloatArray(lines.size) { lines[it].cross.size }
        // §9.4 step 8 — the free cross space after the lines and gaps.
        val leftover = cross.size - base.sum() - gap * (lines.size - 1)
        // No leftover (within rounding dust) ⇒ the lines already hug their
        // items ⇒ the union IS the band. Returning the input keeps every
        // already-correct fixture byte-identical.
        if (leftover < CONTAINMENT_EPS) return lines
        // Equal share per line (css-flexbox-1 §9.4 step 8 "distribute
        // equally"), no remainder bookkeeping in a fractional domain.
        val share = leftover / lines.size

        // §9.6 with no distribution keyword: the lines pack from the
        // cross-start edge, separated by the used cross gap.
        val bands = ArrayList<GapInterval>(lines.size)
        var cursor = cross.start
        for (size in base) {
            bands.add(GapInterval(cursor, cursor + size + share))
            cursor += size + share + gap
        }

        // AGREEMENT GATE. The reconstruction is a claim about where the
        // renderer put the lines; the item rectangles are the evidence. If
        // any line's items fall outside the band claimed for it, the
        // container did not lay out the way step 8 describes (wrap-reverse
        // on the legacy FlowRow path, an overflowing line, a container
        // whose cross size was not definite after all) and the union model
        // — which is derived from the same evidence — is the honest answer.
        for (i in lines.indices) {
            val union = lines[i].cross
            val band = bands[i]
            if (union.start < band.start - CONTAINMENT_EPS ||
                union.end > band.end + CONTAINMENT_EPS
            ) {
                PropertyTracker.markUnhandled("GapDecorationBands:band-disagrees-with-items")
                return lines
            }
        }
        // Accepted: paint against the line boxes, not the item unions.
        return lines.mapIndexed { i, line -> line.copy(cross = bands[i]) }
    }
}
