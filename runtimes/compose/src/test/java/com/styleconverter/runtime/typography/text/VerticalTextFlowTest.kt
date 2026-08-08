package com.styleconverter.runtime.typography.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 35 lane B5 — the vertical-flow decision module.
 *
 * Every case here has a Swift twin of the same name in
 * `runtimes/swiftui/Tests/StyleConverterRuntimeTests/VerticalTextFlowTests.swift`;
 * the two files are the contract that keeps the natives from disagreeing
 * about which runs are upright or where a vertical line breaks.
 */
class VerticalTextFlowTest {

    // ── §5.1 Vertical_Orientation classification ──────────────────────────

    @Test
    fun `fullwidth latin is upright`() {
        // The available-size-011 run: Ｐ Ａ Ｓ (U+FF30 / U+FF21 / U+FF33).
        assertTrue(VerticalTextFlow.isUprightOrientation(0xFF30))
        assertTrue(VerticalTextFlow.isUprightOrientation(0xFF21))
        assertTrue(VerticalTextFlow.isUprightOrientation(0xFF33))
    }

    @Test
    fun `cjk and kana are upright`() {
        assertTrue(VerticalTextFlow.isUprightOrientation(0x4E00))   // 一
        assertTrue(VerticalTextFlow.isUprightOrientation(0x3042))   // あ
        assertTrue(VerticalTextFlow.isUprightOrientation(0xAC00))   // 가
        assertTrue(VerticalTextFlow.isUprightOrientation(0x20000))  // CJK Ext B
    }

    @Test
    fun `ascii and halfwidth kana rotate`() {
        assertEquals(false, VerticalTextFlow.isUprightOrientation('A'.code))
        assertEquals(false, VerticalTextFlow.isUprightOrientation('0'.code))
        // U+FF71 HALFWIDTH KATAKANA A — Vertical_Orientation R.
        assertEquals(false, VerticalTextFlow.isUprightOrientation(0xFF71))
    }

    // ── §5.1 run orientation ──────────────────────────────────────────────

    @Test
    fun `horizontal mode has no opinion`() {
        assertNull(
            VerticalTextFlow.runOrientation(
                WritingModeValue.HORIZONTAL_TB, TextOrientationValue.MIXED, "ＰＡＳＳ"
            )
        )
    }

    @Test
    fun `mixed classifies the fullwidth run upright, spaces ignored`() {
        assertEquals(
            GlyphOrientation.UPRIGHT,
            VerticalTextFlow.runOrientation(
                WritingModeValue.VERTICAL_RL, TextOrientationValue.MIXED, "Ｓ Ｓ Ａ Ｐ"
            )
        )
    }

    @Test
    fun `mixed classifies ascii runs rotated`() {
        // Every other vertical run in the frozen corpus: the available-size
        // asides ("0"), their interleaved digit strings, and
        // css-text-decor/line-through-vertical ("ABC ABC").
        for (run in listOf("0", "0 0 0 0 0 0 0", "ABC ABC")) {
            assertEquals(
                GlyphOrientation.ROTATED,
                VerticalTextFlow.runOrientation(
                    WritingModeValue.VERTICAL_RL, TextOrientationValue.MIXED, run
                )
            )
        }
    }

    @Test
    fun `a run with both classes is mixed and declines`() {
        assertEquals(
            GlyphOrientation.MIXED,
            VerticalTextFlow.runOrientation(
                WritingModeValue.VERTICAL_RL, TextOrientationValue.MIXED, "A漢"
            )
        )
    }

    @Test
    fun `whitespace-only run answers rotated, not upright`() {
        assertEquals(
            GlyphOrientation.ROTATED,
            VerticalTextFlow.runOrientation(
                WritingModeValue.VERTICAL_RL, TextOrientationValue.MIXED, "   "
            )
        )
    }

    @Test
    fun `sideways modes ignore text-orientation`() {
        // §3: sideways-rl / sideways-lr ARE "text-orientation: sideways", so
        // even an all-CJK run is typeset rotated there.
        assertEquals(
            GlyphOrientation.ROTATED,
            VerticalTextFlow.runOrientation(
                WritingModeValue.SIDEWAYS_RL, TextOrientationValue.UPRIGHT, "漢字"
            )
        )
        assertEquals(
            GlyphOrientation.ROTATED,
            VerticalTextFlow.runOrientation(
                WritingModeValue.SIDEWAYS_LR, TextOrientationValue.MIXED, "漢字"
            )
        )
    }

    @Test
    fun `explicit text-orientation keywords are unconditional`() {
        assertEquals(
            GlyphOrientation.UPRIGHT,
            VerticalTextFlow.runOrientation(
                WritingModeValue.VERTICAL_RL, TextOrientationValue.UPRIGHT, "ABC"
            )
        )
        assertEquals(
            GlyphOrientation.ROTATED,
            VerticalTextFlow.runOrientation(
                WritingModeValue.VERTICAL_LR, TextOrientationValue.SIDEWAYS, "漢字"
            )
        )
    }

    // ── §3 line stacking ──────────────────────────────────────────────────

    @Test
    fun `line stacking side follows the mode, not direction`() {
        assertEquals(LineStack.RIGHT_TO_LEFT, VerticalTextFlow.lineStack(WritingModeValue.VERTICAL_RL))
        assertEquals(LineStack.RIGHT_TO_LEFT, VerticalTextFlow.lineStack(WritingModeValue.SIDEWAYS_RL))
        assertEquals(LineStack.LEFT_TO_RIGHT, VerticalTextFlow.lineStack(WritingModeValue.VERTICAL_LR))
        assertEquals(LineStack.LEFT_TO_RIGHT, VerticalTextFlow.lineStack(WritingModeValue.SIDEWAYS_LR))
        assertNull(VerticalTextFlow.lineStack(WritingModeValue.HORIZONTAL_TB))
    }

    // ── the column plan ───────────────────────────────────────────────────

    @Test
    fun `the available-size-011 run breaks one glyph per line`() {
        // main { max-height: 1em; line-height: 1em } at the 16px body default
        // ⇒ a 16px budget against a 16px upright advance ⇒ capacity 1, and
        // the three separating spaces collapse at the breaks.
        assertEquals(
            listOf("Ｓ", "Ｓ", "Ａ", "Ｐ"),
            VerticalTextFlow.uprightColumns("Ｓ Ｓ Ａ Ｐ", charAdvancePx = 16.0, budgetPx = 16.0)
        )
    }

    @Test
    fun `a taller budget packs more glyphs per line`() {
        assertEquals(
            listOf("あいう", "えお"),
            VerticalTextFlow.uprightColumns("あいうえお", charAdvancePx = 20.0, budgetPx = 60.0)
        )
    }

    @Test
    fun `sub-pixel advance still fits one glyph in its own budget`() {
        // The measured advance carries float noise; a 16.0000001px glyph in a
        // 16px budget must not round to capacity 0.
        assertEquals(
            listOf("あ", "い"),
            VerticalTextFlow.uprightColumns("あい", charAdvancePx = 16.0000001, budgetPx = 16.0)
        )
    }

    @Test
    fun `an unbounded budget declines`() {
        assertNull(VerticalTextFlow.uprightColumns("あい", charAdvancePx = 16.0, budgetPx = null))
    }

    @Test
    fun `degenerate inputs decline`() {
        assertNull(VerticalTextFlow.uprightColumns("あい", charAdvancePx = 0.0, budgetPx = 16.0))
        assertNull(VerticalTextFlow.uprightColumns("あい", charAdvancePx = 16.0, budgetPx = 0.0))
        assertNull(VerticalTextFlow.uprightColumns("あい", charAdvancePx = 16.0, budgetPx = -4.0))
        assertNull(VerticalTextFlow.uprightColumns("   ", charAdvancePx = 16.0, budgetPx = 16.0))
    }

    @Test
    fun `a plan wider than the ceiling declines`() {
        val long = "あ".repeat(VerticalTextFlow.MAX_COLUMNS + 2)
        assertNull(VerticalTextFlow.uprightColumns(long, charAdvancePx = 16.0, budgetPx = 16.0))
    }

    @Test
    fun `column indices address the code-point slots the renderer composed`() {
        val glyphs = VerticalTextFlow.codePointsOf("Ｓ Ｓ Ａ Ｐ")
        assertEquals(7, glyphs.size)  // 4 letters + 3 spaces
        val plan = VerticalTextFlow.uprightColumnIndices(glyphs, charAdvancePx = 16.0, budgetPx = 16.0)
        // The dropped separators leave GAPS in the index sequence — which is
        // exactly why the renderer needs indices and not substrings.
        assertEquals(listOf(listOf(0), listOf(2), listOf(4), listOf(6)), plan)
    }

    @Test
    fun `surrogate pairs stay whole`() {
        // U+20000 is a surrogate pair in UTF-16; one code point = one glyph.
        val run = String(Character.toChars(0x20000)) + String(Character.toChars(0x20001))
        assertEquals(2, VerticalTextFlow.codePointsOf(run).size)
        assertEquals(
            2,
            VerticalTextFlow.uprightColumns(run, charAdvancePx = 16.0, budgetPx = 16.0)!!.size
        )
    }
}
