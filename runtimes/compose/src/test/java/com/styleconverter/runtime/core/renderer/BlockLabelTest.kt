package com.styleconverter.runtime.core.renderer

import androidx.compose.ui.graphics.toArgb
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pin suite for the block-font placeholder label (BlockLabel.kt +
 * BlockFont.gen.kt — the cross-platform glyph-wall fix).
 *
 * The MANDATORY test here is the atlas checksum pin: it recomputes the
 * sha256 of the CANONICAL serialization from the EMBEDDED Kotlin constants
 * and asserts it equals the embedded CHECKSUM. All three platforms run the
 * same pin against their own embedded copy, so any drift between platform
 * copies (or a hand-edit of a generated file) fails loudly on at least one
 * platform — the atlas can only change via tools/visual/gen-block-font.mjs
 * regenerating all three files together.
 *
 * The remaining tests pin the pure layout math the composable draws with:
 * normalize (uppercase + unknown→'-'), truncation, and the per-bit rect
 * list — the SAME functions the draw phase consumes, so a green suite
 * means the pixels match the shared spec.
 */
class BlockLabelTest {

    /** sha256 → lowercase hex, first 16 chars — the generator's exact
     *  digest recipe (createHash('sha256')…digest('hex').slice(0, 16)). */
    private fun sha256Hex16(s: String): String =
        MessageDigest.getInstance("SHA-256")
            // The generator hashes the UTF-8 bytes of the canonical string.
            .digest(s.toByteArray(Charsets.UTF_8))
            // Two lowercase hex digits per byte, matching node's 'hex'.
            .joinToString("") { "%02x".format(it) }
            // Node slices the first 16 hex chars (8 bytes) as the pin.
            .substring(0, 16)

    // ── The cross-platform drift guard ──────────────────────────────────

    @Test
    fun atlasChecksum_recomputedFromEmbeddedConstants_matchesPin() {
        // Recompute from the EMBEDDED constants (not the JSON atlas — the
        // natives never read repo files) and compare against the embedded
        // pin. Fails if any glyph row, cell metric, advance or line height
        // in BlockFont.gen.kt drifts from what the generator hashed.
        assertEquals(
            BlockFont.CHECKSUM,
            sha256Hex16(BlockLabel.canonicalAtlasSerialization())
        )
    }

    @Test
    fun canonicalSerialization_hasTheGeneratorShape() {
        val canonical = BlockLabel.canonicalAtlasSerialization()
        // First glyph is space (codepoint 32), all-zero rows — guards the
        // codepoint sort + the "charCode:rows" join syntax.
        assertTrue(canonical.startsWith("32:0,0,0,0,0,0,0;"))
        // Trailing geometry block: 5x7 cell, advance 6, line-height 10 —
        // guards the "|WxH|advA|lhL" suffix byte-for-byte.
        assertTrue(canonical.endsWith("|5x7|adv6|lh10"))
    }

    // ── Label transform: uppercase + unknown→'-' ────────────────────────

    @Test
    fun normalize_uppercases_andKeepsAtlasCharacters() {
        // Lowercase letters map to their capital glyphs; digits, space,
        // '%', '.', '-', '_' all exist in the atlas and pass through.
        assertEquals("ABZ09 .%-_", BlockLabel.normalize("abZ09 .%-_"))
    }

    @Test
    fun normalize_mapsUnknownCharactersToHyphen() {
        // '?' has no glyph; 'é' uppercases to 'É' which has none either;
        // '#' has none — all become the shared '-' fallback.
        assertEquals("A---B", BlockLabel.normalize("a?é#b"))
    }

    // ── Truncation math: 8 + n*ADVANCE <= componentWidth - 8 ────────────

    @Test
    fun truncation_wideComponent_keepsWholeLabel() {
        // Budget (100-16)/6 = 14 chars — a 10-char label fits untouched.
        assertEquals(10, BlockLabel.truncatedCount(10, 100))
    }

    @Test
    fun truncation_dropsTrailingCharacters() {
        // Same 14-char budget truncates a 20-char label to 14 (no ellipsis).
        assertEquals(14, BlockLabel.truncatedCount(20, 100))
    }

    @Test
    fun truncation_boundaryIsInclusive() {
        // Width 34 = 8 + 3*6 + 8 exactly → 3 chars fit (<=, not <)…
        assertEquals(3, BlockLabel.truncatedCount(99, 34))
        // …while one pixel less (33) only fits 2 whole advances.
        assertEquals(2, BlockLabel.truncatedCount(99, 33))
    }

    @Test
    fun truncation_tinyComponent_rendersNothing() {
        // 16px is all margin (8+8) — zero budget, zero chars, never negative.
        assertEquals(0, BlockLabel.truncatedCount(5, 16))
        assertEquals(0, BlockLabel.truncatedCount(5, 0))
    }

    @Test
    fun truncation_unboundedWidth_neverTruncates() {
        // The composable passes Int.MAX_VALUE for unbounded constraints —
        // the whole label survives (and the math must not overflow).
        assertEquals(5, BlockLabel.truncatedCount(5, Int.MAX_VALUE))
    }

    // ── Rect list: one 1x1 rect per set bit, cells at (8 + i*6, 6) ──────

    @Test
    fun rects_knownString_hasExactBitCount() {
        // 'H' rows (17,17,17,31,17,17,17) carry 17 set bits and 'I' rows
        // (14,4,4,4,4,4,14) carry 11 → "HI" paints exactly 28 rects.
        assertEquals(28, BlockLabel.rects(BlockLabel.normalize("Hi")).size)
    }

    @Test
    fun rects_firstCellStartsAtSharedOrigin() {
        val rects = BlockLabel.rects("H")
        // 'H' row 0 is 10001: bit 4 (leftmost, col 0) → the very first
        // rect sits at the shared origin (8, 6)…
        assertEquals(8 to 6, rects.first())
        // …and bit 0 (col 4) of the same row lands at x = 8+4 = 12.
        assertTrue(rects.contains(12 to 6))
    }

    @Test
    fun rects_secondCellAdvancesBySix() {
        // 'I' row 0 is 01110 (cols 1..3); as char index 1 its cell origin
        // is x = 8 + 1*6 = 14, so the row-0 rects are x = 15,16,17 at y=6.
        val rects = BlockLabel.rects("HI")
        assertTrue(rects.contains(15 to 6))
        assertTrue(rects.contains(16 to 6))
        assertTrue(rects.contains(17 to 6))
        // Row 0 of 'I' has nothing at the cell's col 0/col 4 (14 and 18).
        assertTrue(!rects.contains(14 to 6))
        assertTrue(!rects.contains(18 to 6))
    }

    @Test
    fun rects_truncatedCount_dropsTrailingCells() {
        // Drawing "HI" clamped to 1 char paints only H's 17 bits — the
        // exact list the drawBehind consumes after width-derived clamping.
        assertEquals(17, BlockLabel.rects("HI", 1).size)
        // A count beyond the string length re-clamps (coerced layout
        // widths from parent min constraints must never overrun).
        assertEquals(28, BlockLabel.rects("HI", 99).size)
    }

    // ── Ink color: rgba(237,237,237,0.7) with the 179/255 alpha pin ────

    @Test
    fun labelColor_isTheSharedArgbLiteral() {
        // 0xB3EDEDED: RGB 237 (0xED) per channel; alpha 0.7×255 = 178.5
        // rounded HALF-UP to 179 (0xB3) — the documented shared rounding.
        assertEquals(0xB3EDEDED.toInt(), BlockLabel.COLOR.toArgb())
        // Pin the effective alpha byte explicitly: 179, never 178.
        assertEquals(179, (BlockLabel.COLOR.toArgb() ushr 24) and 0xFF)
    }
}
