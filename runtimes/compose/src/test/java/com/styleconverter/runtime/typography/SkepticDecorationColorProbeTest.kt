package com.styleconverter.runtime.typography

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SKEPTIC PROBE (wave 22, lane DECOR review) — edge cases the lane's own
 * suites do not cover. Every case is a PARITY assertion against the two
 * reference emitters the renderer used before wave 22, on inputs outside
 * the pinned lattice (fractional / zero thickness, fractional and negative
 * baselines, empty interior visual lines).
 */
class SkepticDecorationColorProbeTest {

    private val allFlags = TextStyleApplier.DecorationLineFlags(
        underline = true, overline = true, lineThrough = true
    )

    private fun coloredTops(bands: List<DecorationColorOps.ColoredBand>) =
        bands.map { it.top to it.thickness }

    /** EC1 — FRACTIONAL and ZERO explicit thickness (pins cover 10/20/30 only). */
    @Test
    fun fractionalAndZeroExplicitThicknessMatchExplicitBands() {
        for (t in listOf(0f, 0.4f, 1.5f, 3.7f, 7.5f)) {
            val ref = DecorationOps.explicitBands(
                lineCount = 1, fontSizePx = 16f, thicknessPx = t,
                underline = true, overline = true, lineThrough = true,
                lineBaseline = { 236f }, lineLeft = { 0f }, lineRight = { 74f }
            ).map { it.top to it.thickness }
            val got = coloredTops(
                DecorationColorOps.bands(
                    lineCount = 1, fontSizePx = 16f,
                    autoThicknessPx = 999f, // must be ignored
                    explicitThicknessPx = t,
                    lines = DecorationColorOps.resolve(null, true, true, true),
                    lineBaseline = { 236f }, lineLeft = { 0f }, lineRight = { 74f }
                )
            )
            assertEquals("thickness=$t", ref, got)
        }
    }

    /** EC2 — FRACTIONAL / NEGATIVE baselines (Math.round half-up parity). */
    @Test
    fun fractionalAndNegativeBaselinesMatchDecorationSegments() {
        for (b in listOf(-0.5f, -3.5f, 0.5f, 41.5f, 236.49f, 236.5f, 236.51f)) {
            val ref = TextStyleApplier.decorationSegments(
                lineCount = 1, fontSizePx = 22f, flags = allFlags,
                lineBaseline = { b }, lineLeft = { 0f }, lineRight = { 100f }
            ).map { it.top to it.thickness }
            val got = coloredTops(
                DecorationColorOps.bands(
                    lineCount = 1, fontSizePx = 22f,
                    autoThicknessPx = TextStyleApplier.decorationThicknessPx(22f),
                    explicitThicknessPx = null,
                    lines = DecorationColorOps.resolve(null, true, true, true),
                    lineBaseline = { b }, lineLeft = { 0f }, lineRight = { 100f }
                )
            )
            assertEquals("baseline=$b", ref, got)
        }
    }

    /** EC3 — an EMPTY INTERIOR visual line must be skipped identically. */
    @Test
    fun emptyInteriorVisualLineIsSkippedLikeTheReferenceEmitters() {
        val left = { i: Int -> 0f }
        val right = { i: Int -> if (i == 1) 0f else 74f }   // line 1 empty
        val base = { i: Int -> 236f + i * 20f }
        val ref = TextStyleApplier.decorationSegments(
            lineCount = 3, fontSizePx = 16f, flags = allFlags,
            lineBaseline = base, lineLeft = left, lineRight = right
        ).map { Triple(it.left, it.top, it.width) }
        val got = DecorationColorOps.bands(
            lineCount = 3, fontSizePx = 16f,
            autoThicknessPx = TextStyleApplier.decorationThicknessPx(16f),
            explicitThicknessPx = null,
            lines = DecorationColorOps.resolve(null, true, true, true),
            lineBaseline = base, lineLeft = left, lineRight = right
        ).map { Triple(it.left, it.top, it.width) }
        assertEquals(6, ref.size)   // 2 painted lines x 3 kinds
        assertEquals(ref, got)
    }

    /** EC4 — an EMPTY (non-null) wire is AUTHORITATIVE and paints nothing.
     *
     *  This probe used to document the contract HOLE (the old
     *  "authoritative when present" gate was keyed on NON-EMPTY, so a wire
     *  whose every entry failed to map — all `blink` — silently re-enabled
     *  the legacy flag path and painted lines from the merged flat bag).
     *  Wave-22 lane DECOR closed it: presence alone is authoritative, and
     *  the two painters additionally suppress the platform built-ins on
     *  `wire != null` rather than on "resolved list non-empty" (Compose
     *  `paintedTextStyle`, iOS `overlayOwns`). */
    @Test
    fun emptyNonNullWireIsAuthoritativeAndPaintsNothing() {
        val got = DecorationColorOps.resolve(emptyList(), true, false, false)
        assertEquals(0, got.size)
    }

    /** EC5 — ops() on a ZERO-WIDTH band (reachable only through a direct
     *  call; `bands` filters these) must not emit a negative-extent op. */
    @Test
    fun zeroWidthBandEmitsNoNegativeExtentOps() {
        for (style in DecorationOps.LineStyle.values()) {
            val ops = DecorationColorOps.ops(
                listOf(DecorationColorOps.ColoredBand(10f, 20f, 0f, 2f, null)), style
            )
            ops.forEach { c ->
                when (val o = c.op) {
                    is DecorationOps.Op.Band ->
                        assertTrue("$style band width ${o.width}", o.width >= 0f)
                    is DecorationOps.Op.Dot ->
                        assertTrue("$style dot radius ${o.radius}", o.radius >= 0f)
                }
            }
        }
    }

    /** EC6 — colour tagging survives the DOTTED expander per band: two bands
     *  in different colours must not bleed into one another. */
    @Test
    fun dottedExpansionKeepsEachBandsOwnColour() {
        val blue = DecorationColorOps.Rgba(0f, 0f, 1f)
        val gray = DecorationColorOps.Rgba(0.5f, 0.5f, 0.5f)
        val ops = DecorationColorOps.ops(
            listOf(
                DecorationColorOps.ColoredBand(0f, 0f, 20f, 2f, blue),
                DecorationColorOps.ColoredBand(0f, 10f, 20f, 2f, gray)
            ),
            DecorationOps.LineStyle.DOTTED
        )
        val half = ops.size / 2
        assertTrue(ops.isNotEmpty() && ops.size % 2 == 0)
        assertTrue(ops.take(half).all { it.color == blue })
        assertTrue(ops.drop(half).all { it.color == gray })
    }
}
