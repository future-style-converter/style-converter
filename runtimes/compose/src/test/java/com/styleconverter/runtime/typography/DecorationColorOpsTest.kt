package com.styleconverter.runtime.typography

// DecorationColorOps pin table — applier campaign wave 22, lane DECOR
// (B-RC4b, per-line decoration colors).
//
// TWIN of the iOS suite DecorationColorOpsTests.swift — SAME cases, SAME
// expected numbers (repo twin rule: identical pin tables). Change one,
// change both. The only case that is NOT twinned is the absolute-row
// geometry block: Compose anchors bands on TextLayoutResult BASELINES
// while iOS anchors on LINE-BOX TOPS, so the iOS twin pins the SAME web
// rows through DecorationMetrics.top as line-box-relative offsets.
//
// Every number below is pinned against LIVE wave-21 artifacts:
//   • WEB capture (the pixel oracle for this section — web is the
//     platform that matches the browser ref):
//     tools/titan/runs/wave21-final/sections/css-text-decor/report/images/
//     web/wpt__css-text-decor__text-decoration-color.png, 390x600.
//     Component `text-decoration-color__7` = span[underline blue] >
//     span[overline gray] > span[line-through green], text on the
//     innermost. Visual line 0 paints THREE 1px rows in THREE colors:
//       row 219 (128,128,128) gray  → overline
//       row 230 (0,128,0)     green → line-through
//       row 237 (0,0,255)     blue  → underline
//     (line 1 repeats at 239/250/257 — advance 20). The iOS capture of
//     the same component painted two rows, the Android capture ONE
//     (green, the innermost box only): the loss this lane closes.
//   • per-test IR: tools/titan/runs/wave21-final/sections/css-text-decor/
//     per-test-ir/wpt__css-text-decor__text-decoration-color.json — the
//     exact normalized sRGB leaves used below (gray 0.5019607843137255,
//     green g=0.5019607843137255, blue b=1).

import com.styleconverter.runtime.typography.DecorationColorOps.DecorationLine
import com.styleconverter.runtime.typography.DecorationColorOps.LineKind
import com.styleconverter.runtime.typography.DecorationColorOps.Rgba
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecorationColorOpsTest {

    /** The live IR's `gray` leaf (128/255 in the capture's row 219). */
    private val gray = Rgba(0.5019607843137255f, 0.5019607843137255f, 0.5019607843137255f, 1f)

    /** The live IR's `green` leaf (0,128,0 — capture row 230). */
    private val green = Rgba(0f, 0.5019607843137255f, 0f, 1f)

    /** The live IR's `blue` leaf (0,0,255 — capture row 237). */
    private val blue = Rgba(0f, 0f, 1f, 1f)

    /**
     * The merged wire for the oracle component, ANCESTOR-FIRST exactly as
     * the IR nests it: outer span underlines blue, its child overlines
     * gray, the innermost (text-bearing) line-throughs green.
     */
    private val chain = listOf(
        DecorationLine(LineKind.UNDERLINE, blue),
        DecorationLine(LineKind.OVERLINE, gray),
        DecorationLine(LineKind.LINE_THROUGH, green)
    )

    // ---- wire keyword table ----

    @Test
    fun `both wire spellings of every line keyword map to a kind`() {
        // The IR ships the screaming spelling (TextDecorationLine data is
        // ["UNDERLINE"] / ["LINE_THROUGH"] in the live per-test IR)…
        assertEquals(LineKind.UNDERLINE, DecorationColorOps.lineKindFrom("UNDERLINE"))
        assertEquals(LineKind.OVERLINE, DecorationColorOps.lineKindFrom("OVERLINE"))
        assertEquals(LineKind.LINE_THROUGH, DecorationColorOps.lineKindFrom("LINE_THROUGH"))
        // …the meta.decorations contract spells them the CSS way.
        assertEquals(LineKind.UNDERLINE, DecorationColorOps.lineKindFrom("underline"))
        assertEquals(LineKind.OVERLINE, DecorationColorOps.lineKindFrom("overline"))
        assertEquals(LineKind.LINE_THROUGH, DecorationColorOps.lineKindFrom("line-through"))
    }

    @Test
    fun `non-painting and unknown keywords yield no line`() {
        // css-text-decor-3 §2.1: `none` paints nothing and `blink` has no
        // visual effect in any modern UA — neither may become a band.
        assertNull(DecorationColorOps.lineKindFrom("none"))
        assertNull(DecorationColorOps.lineKindFrom("blink"))
        // Garbage / absent → dropped, never guessed (no silent fallthrough).
        assertNull(DecorationColorOps.lineKindFrom("grand-underline"))
        assertNull(DecorationColorOps.lineKindFrom(null))
        // decorationLine propagates the drop rather than inventing a kind.
        assertNull(DecorationColorOps.decorationLine("blink", blue))
        assertEquals(
            DecorationLine(LineKind.OVERLINE, gray),
            DecorationColorOps.decorationLine("OVERLINE", gray)
        )
    }

    // ---- resolve: the ordered request list ----

    @Test
    fun `no wire synthesizes the legacy flag order with currentColor`() {
        // The pre-wave-22 emit order (decorationSegments' buildList) and a
        // null color, so the painter substitutes exactly what it did
        // before — the dark-stage 327 byte-identity guarantee.
        assertEquals(
            listOf(
                DecorationLine(LineKind.UNDERLINE, null),
                DecorationLine(LineKind.OVERLINE, null),
                DecorationLine(LineKind.LINE_THROUGH, null)
            ),
            DecorationColorOps.resolve(null, underline = true, overline = true, lineThrough = true)
        )
        // Only the flagged kinds appear, in the same order.
        assertEquals(
            listOf(DecorationLine(LineKind.LINE_THROUGH, null)),
            DecorationColorOps.resolve(null, underline = false, overline = false, lineThrough = true)
        )
        // Nothing flagged → nothing painted (the modifier's whole gate).
        assertTrue(
            DecorationColorOps.resolve(null, underline = false, overline = false, lineThrough = false)
                .isEmpty()
        )
    }

    @Test
    fun `a present but EMPTY wire is authoritative and paints nothing`() {
        // The contract hole this closes: DecorationWire's known-keyword
        // filter can empty a PRESENT list (every entry an unrecognised
        // §2.1 keyword). Treating that as "absent" would re-enable the
        // legacy flag path and paint lines from the merged flat bag that
        // the authoritative list just said were unpaintable.
        assertTrue(
            "an empty-but-present wire must win over the component's own flags",
            DecorationColorOps.resolve(
                emptyList(), underline = true, overline = true, lineThrough = true
            ).isEmpty()
        )
        // …and with no bands requested, the geometry pass owns nothing.
        assertTrue(
            DecorationColorOps.bands(
                lineCount = 1, fontSizePx = 16f, autoThicknessPx = 1f, explicitThicknessPx = null,
                lines = DecorationColorOps.resolve(emptyList(), true, true, true),
                lineBaseline = { 236f }, lineLeft = { 0f }, lineRight = { 100f }
            ).isEmpty()
        )
    }

    @Test
    fun `a merged wire wins over the flags and keeps ancestor-first order`() {
        // The collapsed run's own TextDecorationLine says only
        // `line-through` (the innermost box), but the merged list carries
        // all three ancestors — the wire is authoritative and its ORDER is
        // the §5.1 per-kind paint order + the extractor's outermost-first
        // emission order (see DecorationColorOps' banner).
        assertEquals(
            chain,
            DecorationColorOps.resolve(chain, underline = false, overline = false, lineThrough = true)
        )
    }

    @Test
    fun `the same kind may appear twice with different colors`() {
        // Two nested boxes both underlining is legal CSS; keying the list
        // by kind would silently drop one. Order = ancestor first, so the
        // DESCENDANT's band is emitted last and paints on top.
        val doubled = listOf(
            DecorationLine(LineKind.UNDERLINE, blue),
            DecorationLine(LineKind.UNDERLINE, green)
        )
        assertEquals(doubled, DecorationColorOps.resolve(doubled, true, false, false))
        // Both survive into bands at the SAME row, in the same order.
        val bands = DecorationColorOps.bands(
            lineCount = 1, fontSizePx = 16f, autoThicknessPx = 1f, explicitThicknessPx = null,
            lines = doubled, lineBaseline = { 236f }, lineLeft = { 0f }, lineRight = { 100f }
        )
        assertEquals(2, bands.size)
        assertEquals(bands[0].top, bands[1].top, 0f)
        assertEquals(blue, bands[0].color)
        assertEquals(green, bands[1].color)
    }

    // ---- geometry: the measured web rows, per kind ----

    @Test
    fun `the oracle chain lands on the measured web rows in its own colors`() {
        // fontSize 16 (the capture's default face) → auto thickness
        // TextStyleApplier.decorationThicknessPx(16) = max(1, round(16 ×
        // 140/2048)) = 1 (the capture's 1px rows). Baseline 236 is the row
        // the three measured rows solve to (underline = baseline + T).
        val bands = DecorationColorOps.bands(
            lineCount = 1, fontSizePx = 16f, autoThicknessPx = 1f, explicitThicknessPx = null,
            lines = chain, lineBaseline = { 236f }, lineLeft = { 0f }, lineRight = { 74f }
        )
        // One band per request, request order preserved (underline first).
        assertEquals(3, bands.size)
        // Underline: 236 + 1 = 237 → the measured BLUE row.
        assertEquals(237f, bands[0].top, 0f)
        assertEquals(blue, bands[0].color)
        // Overline: 236 − round(16 × 1984/2048) − 1 = 236 − 16 − 1 = 219
        // → the measured GRAY row.
        assertEquals(219f, bands[1].top, 0f)
        assertEquals(gray, bands[1].color)
        // Line-through: round(236 − 16 × 671/2048 − 0.5) = round(230.258)
        // = 230 → the measured GREEN row.
        assertEquals(230f, bands[2].top, 0f)
        assertEquals(green, bands[2].color)
        // The row DELTAS the capture shows: gray→green 11, gray→blue 18.
        assertEquals(11f, bands[2].top - bands[1].top, 0f)
        assertEquals(18f, bands[0].top - bands[1].top, 0f)
    }

    @Test
    fun `bands skip empty visual lines and repeat per line`() {
        // Two lines, the second with zero inked extent → only line 0's
        // three bands (the browser paints nothing over nothing).
        val bands = DecorationColorOps.bands(
            lineCount = 2, fontSizePx = 16f, autoThicknessPx = 1f, explicitThicknessPx = null,
            lines = chain,
            lineBaseline = { if (it == 0) 236f else 256f },
            lineLeft = { 0f }, lineRight = { if (it == 0) 74f else 0f }
        )
        assertEquals(3, bands.size)
        // Empty request list → total no-op even with lines to draw on.
        assertTrue(
            DecorationColorOps.bands(
                1, 16f, 1f, null, emptyList(), { 236f }, { 0f }, { 74f }
            ).isEmpty()
        )
    }

    @Test
    fun `an explicit thickness swaps in Blink's underline gap only`() {
        // Wave-21's ref-pinned rule (dotted-001 band tops 207/363/519):
        // gap = max(1, ceil(T/2)) instead of the auto gap == T.
        val bands = DecorationColorOps.bands(
            lineCount = 1, fontSizePx = 92f, autoThicknessPx = 6f, explicitThicknessPx = 10f,
            lines = listOf(
                DecorationLine(LineKind.UNDERLINE, null),
                DecorationLine(LineKind.OVERLINE, null),
                DecorationLine(LineKind.LINE_THROUGH, null)
            ),
            lineBaseline = { 202f }, lineLeft = { 62f }, lineRight = { 522f }
        )
        // Underline 202 + max(1, ceil(10/2)) = 207 — the measured ref row.
        assertEquals(207f, bands[0].top, 0f)
        // Overline 202 − round(92 × 1984/2048) − 10 = 202 − 89 − 10 = 103.
        assertEquals(103f, bands[1].top, 0f)
        // Strike: round(202 − 92 × 671/2048 − 10/2)
        //       = round(202 − 30.14 − 5) = round(166.86) = 167
        // (the center is thickness-independent; the band straddles ±T/2).
        assertEquals(167f, bands[2].top, 0f)
        // Every band carries the EXPLICIT thickness, not the auto one.
        bands.forEach { assertEquals(10f, it.thickness, 0f) }
    }

    @Test
    fun `a fat explicit thickness makes overline and line-through OVERLAP`() {
        // Z-ORDER CORRECTION PIN. The wave-22 iOS banner justified its
        // ZStack ordering by claiming the three rows are "disjoint by
        // construction … so no pixel can differ". That justification is
        // FALSE, and this case is the counter-example: at the default face
        // the rows sit 11 and 18px apart, but `text-decoration-thickness`
        // is authorable at any width (the css-text-decor dotted tests
        // already declare 10/20/30px), and at t=30 the LINE-THROUGH band
        // grows up into the OVERLINE band. Paint order therefore decides
        // the visible colour: request order is load-bearing, not cosmetic.
        val t = 30f
        val bands = DecorationColorOps.bands(
            lineCount = 1, fontSizePx = 16f, autoThicknessPx = 1f, explicitThicknessPx = t,
            lines = chain, lineBaseline = { 236f }, lineLeft = { 0f }, lineRight = { 74f }
        )
        // Underline 236 + max(1, ceil(30/2)) = 251; overline
        // 236 − 16 − 30 = 190; strike round(236 − 5.242 − 15) = 216.
        assertEquals(251f, bands[0].top, 0f)
        assertEquals(190f, bands[1].top, 0f)
        assertEquals(216f, bands[2].top, 0f)
        fun overlaps(a: DecorationColorOps.ColoredBand, b: DecorationColorOps.ColoredBand) =
            a.top < b.top + b.thickness && b.top < a.top + a.thickness
        // THE counter-example: strike [216,246) ∩ overline [190,220) ≠ ∅,
        // and the strike is emitted LAST, so those 4 rows paint GREEN.
        assertTrue("overline and line-through overlap at t=30", overlaps(bands[1], bands[2]))
        assertEquals(green, bands[2].color)
        // The other two pairs stay disjoint at ANY thickness, and that is a
        // property of the anchors rather than luck: the overline's BOTTOM
        // edge is pinned to the line-box top (baseline − ascent) while the
        // underline's TOP edge is pinned below the baseline, so growing t
        // moves them apart, not together. The strike is the only band whose
        // centre is fixed, so it is the only one that can grow into either.
        assertTrue("underline never meets the overline", !overlaps(bands[0], bands[1]))
        assertTrue("underline never meets the strike", !overlaps(bands[0], bands[2]))
        // The REAL rule the painter relies on: emission order == paint
        // order, so the LAST band wins the shared pixels. For a merged wire
        // that is the innermost decorating box (ancestor-first list); for
        // the legacy synthesis it is line-through, which agrees with
        // css-text-decor-3 §5.1's per-kind order (bottom-first: shadows,
        // underlines, overlines, TEXT, emphasis marks, line-through).
        assertEquals(listOf(blue, gray, green), bands.map { it.color })
    }

    // ---- ops: the per-line color reaches every paint op ----

    @Test
    fun `solid ops carry their own line's color`() {
        val ops = DecorationColorOps.ops(
            DecorationColorOps.bands(
                lineCount = 1, fontSizePx = 16f, autoThicknessPx = 1f, explicitThicknessPx = null,
                lines = chain, lineBaseline = { 236f }, lineLeft = { 0f }, lineRight = { 74f }
            ),
            DecorationOps.LineStyle.SOLID
        )
        // Solid = one band op per line → three ops, three colors.
        assertEquals(3, ops.size)
        assertEquals(listOf(blue, gray, green), ops.map { it.color })
        // The op geometry is untouched by the coloring (the wave-21
        // byte-for-byte solid identity).
        assertEquals(DecorationOps.Op.Band(0f, 237f, 74f, 1f), ops[0].op)
    }

    @Test
    fun `a dotted chain keeps every dot of a line in that line's color`() {
        // Style expansion composes with per-line color: the dotted
        // emitter's dash runs (t=2 ≤ 3 → square dashes at 4px pitch) all
        // inherit their band's color, and the second line's ops carry the
        // OTHER color. This is the "dotted underline in A + solid overline
        // in B" composition case, one style at a time (the style is a
        // per-element property, so both lines share it).
        val ops = DecorationColorOps.ops(
            DecorationColorOps.bands(
                lineCount = 1, fontSizePx = 16f, autoThicknessPx = 2f, explicitThicknessPx = null,
                lines = listOf(
                    DecorationLine(LineKind.UNDERLINE, blue),
                    DecorationLine(LineKind.OVERLINE, gray)
                ),
                lineBaseline = { 236f }, lineLeft = { 0f }, lineRight = { 20f }
            ),
            DecorationOps.LineStyle.DOTTED
        )
        // 5 dashes per band (x = 0,4,8,12,16 over a 20px run), two bands.
        assertEquals(10, ops.size)
        // First band = the underline → all blue; second = overline → gray.
        assertTrue(ops.take(5).all { it.color == blue })
        assertTrue(ops.drop(5).all { it.color == gray })
    }

    @Test
    fun `the legacy path leaves every op color null for the painter`() {
        // No wire → null colors all the way to the op, so the painter
        // substitutes `text-decoration-color ?: text color` exactly as it
        // did before wave 22. This is the byte-identity pin.
        val ops = DecorationColorOps.ops(
            DecorationColorOps.bands(
                lineCount = 1, fontSizePx = 16f, autoThicknessPx = 1f, explicitThicknessPx = null,
                lines = DecorationColorOps.resolve(null, true, true, true),
                lineBaseline = { 236f }, lineLeft = { 0f }, lineRight = { 74f }
            ),
            DecorationOps.LineStyle.SOLID
        )
        assertEquals(3, ops.size)
        assertTrue(ops.all { it.color == null })
    }
}
