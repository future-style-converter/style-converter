package com.styleconverter.runtime.core.renderer

// The only Compose type the pure geometry below exposes: the shared ink
// colour the harness chrome paints its 1x1-px rects with.
import androidx.compose.ui.graphics.Color

/**
 * Block-font label GEOMETRY — the Compose copy of the cross-platform 5x7
 * glyph atlas math (BlockFont.gen.kt) that every platform's capture harness
 * rasterizes the component-name debug label with.
 *
 * WHAT THIS IS NOT (wave 51, PR A — the label-chrome move): the runtime no
 * longer draws the label. It used to be a composable placeholder INSIDE
 * the styled component, so the ink rode through the element's own paint
 * chain — offset by margin / relative insets / translate, rotated and
 * zoomed with it, clipped by filter layers, truncated at the CONTENT width
 * while web/iOS truncated at the border-box width — and the three captures
 * of one fixture disagreed on where (and whether) the label landed. The
 * label is now HARNESS CHROME: the capture app draws it as a SIBLING layer
 * over the whole capture root, after the component's paint, from the PLAIN
 * component name (`_` → space), at a fixed origin in the CAPTURE FRAME.
 * The normative rule lives in docs/DYNAMIC_CAPTURE.md, section "Harness
 * label chrome"; the Android drawer is `CaptureCanvas` in
 * apps/android-harness/…/screenshot/ScreenshotCaptureScreen.kt.
 *
 * Everything here is a PURE function (JVM-unit-testable without
 * Robolectric, the WptCaptureMode.kt pattern), and it is PUBLIC because the
 * harness is a different Gradle module (`app/build.gradle.kts`
 * `implementation(project(":runtime"))`) — a Kotlin `internal` object would
 * be invisible to it. BlockFont.gen.kt itself stays `internal`: nothing in
 * this object's signatures leaks it (`rects()` returns `List<Pair<Int,Int>>`).
 */
object BlockLabel {

    /** Cell-grid x origin IN THE CAPTURE FRAME: the first glyph cell starts
     *  8px right of the PNG's left edge — the shared cross-platform constant
     *  (web `left: 8px`, iOS `.offset(x: 8)`, Compose `drawRect` at x=8 on
     *  the capture root). Not relative to any component box any more — see
     *  docs/DYNAMIC_CAPTURE.md "Harness label chrome". */
    const val ORIGIN_X = 8

    /** Cell-grid y origin IN THE CAPTURE FRAME: glyph row 0 sits 6px below
     *  the PNG's top edge, so the 7 glyph rows (6..12) lie inside the 16px
     *  top padding band over the canvas ground and never overlap the border
     *  box of an in-flow root with non-negative margin-top / relative top
     *  (that box starts at y=16 on all three platforms). */
    const val ORIGIN_Y = 6

    /** Right-edge margin the truncation rule reserves against the FRAME
     *  width: characters are dropped until
     *  `ORIGIN_X + n*ADVANCE <= frameWidth - EDGE_MARGIN` (no ellipsis — the
     *  shared spec truncates silently). At the default 390px frame that is
     *  62 glyphs; at `CAPTURE_WIDTH=250` it is 39; below 22px it is 0. */
    const val EDGE_MARGIN = 8

    /**
     * The label ink: rgba(237, 237, 237, 179/255) — the debug-label colour
     * all three harnesses composite over the #1A1A2E ground. 0xED == 237 for
     * each RGB channel. Alpha ROUNDING CONTRACT (shared across platforms):
     * the historical 0.7 × 255 = 178.5 is rounded HALF-UP to 179 == 0xB3, so
     * the effective alpha every platform must rasterize with is exactly
     * 179/255 (~0.70196) → (174,174,180) over the ground; a platform that
     * used 178, or CSS `0.7` (which Chromium quantises one LSB darker to
     * (173,173,179)), diverges by one blend step on every lit pixel — which
     * is why the web harness moved to 179/255 in the same PR.
     */
    val COLOR = Color(0xB3EDEDED)

    /**
     * Shared label transform: UPPERCASE the input, and map any character
     * the atlas has no glyph for to '-' (the atlas only carries space, %,
     * -, ., 0-9, A-Z, _ — see BlockFont.GLYPHS). Uppercasing FIRST means
     * 'a'..'z' hit their capital glyphs instead of degrading to '-'.
     * The INPUT is the PLAIN component name with `_` replaced by a space —
     * the same string on web, iOS and Android (no text-transform, no
     * tab-size: those are the COMPONENT's styles, and the chrome is not
     * part of the component). Because this uppercases everything, a name
     * differs from its `text-transform: uppercase`d form by zero rects.
     */
    fun normalize(label: String): String = buildString(label.length) {
        // Per character: try the uppercased form against the atlas; every
        // miss (punctuation, accents, tabs, …) becomes the shared '-'.
        for (c in label) {
            val upper = c.uppercaseChar()
            append(if (BlockFont.GLYPHS.containsKey(upper)) upper else '-')
        }
    }

    /**
     * Truncation math (shared spec): keep the longest prefix of n chars
     * with `ORIGIN_X + n*ADVANCE <= frameWidthPx - EDGE_MARGIN`, i.e.
     * n = floor((frameWidth - 16) / 6), clamped to the label length.
     * [frameWidthPx] is the CAPTURE FRAME width — the number of pixels the
     * PNG is cut to (390 by default, `CAPTURE_WIDTH` otherwise) — never a
     * component or content-box width. Integer division IS the floor here
     * because the budget is coerced non-negative first (Kotlin `/`
     * truncates toward zero); widths 17..21 reach 0 through that division.
     */
    fun truncatedCount(labelLength: Int, frameWidthPx: Int): Int {
        // Horizontal pixel budget left for glyph cells once both the 8px
        // origin inset and the 8px right margin are reserved.
        val budget = frameWidthPx - ORIGIN_X - EDGE_MARGIN
        // No budget → no characters (never negative counts).
        if (budget <= 0) return 0
        // Whole advances that fit, never more than the label provides.
        return (budget / BlockFont.ADVANCE).coerceAtMost(labelLength)
    }

    /**
     * The full rect list for the first [count] characters of an already-
     * [normalize]d label: one (x, y) pair per SET bit, in CAPTURE-FRAME
     * pixels (so the first possible rect is at (8, 6) — the PNG's (8, 6)).
     * Cell origin for char i is x = ORIGIN_X + i*ADVANCE, y = ORIGIN_Y;
     * within a cell, bit 4 of the row int is the LEFTMOST column (col 0)
     * and row 0 is the top — the exact convention BlockFont.gen.kt
     * documents. This single pure function is BOTH what the harness paints
     * (one 1x1 `drawRect` per pair, integer offsets, no AA) and what the
     * unit suites pin, so tests and pixels cannot diverge.
     */
    fun rects(normalized: String, count: Int = normalized.length): List<Pair<Int, Int>> {
        // Pre-size roughly (≤ 35 bits/cell) — micro-optimization only.
        val out = ArrayList<Pair<Int, Int>>(count * 16)
        // Guard: callers pass a count from truncatedCount(), which is already
        // clamped, but never read past the string regardless (a stale or
        // over-large count must degrade to "whole label", not crash).
        for (i in 0 until count.coerceAtMost(normalized.length)) {
            // normalize() guarantees every char has a glyph → getValue is
            // total here (a miss would be a programming error, fail loud).
            val rows = BlockFont.GLYPHS.getValue(normalized[i])
            // Row 0 = top of the 7-row cell.
            for (row in 0 until BlockFont.CELL_H) {
                // The 5-bit row pattern for this scanline.
                val bitsRow = rows[row]
                // Col 0 = leftmost = bit (CELL_W - 1) = bit 4.
                for (col in 0 until BlockFont.CELL_W) {
                    // Test the bit for this column (shift the leftmost
                    // column down to bit 0).
                    if ((bitsRow shr (BlockFont.CELL_W - 1 - col)) and 1 == 1) {
                        // Emit the 1x1 rect's integer top-left.
                        out.add((ORIGIN_X + i * BlockFont.ADVANCE + col) to (ORIGIN_Y + row))
                    }
                }
            }
        }
        return out
    }

    /**
     * The CANONICAL atlas serialization — byte-identical to what
     * tools/visual/gen-block-font.mjs hashed to produce BlockFont.CHECKSUM:
     * glyphs sorted by codepoint as `<charCode>:<row,row,…>` joined with
     * ';', then `|<W>x<H>|adv<A>|lh<L>`. The pin test sha256s this string
     * (hex, first 16 chars) and compares against the embedded CHECKSUM —
     * the cross-platform drift guard that catches any hand-edit of the
     * generated constants or divergence between platform copies.
     */
    fun canonicalAtlasSerialization(): String {
        // Sort by codepoint — the generator's deterministic glyph order.
        val glyphPart = BlockFont.GLYPHS.entries
            .sortedBy { it.key.code }
            // `<charCode>:<rows joined with ','>` per glyph, ';'-joined.
            .joinToString(";") { (ch, rows) -> "${ch.code}:${rows.joinToString(",")}" }
        // Trailing geometry block: cell size, advance, line height.
        return glyphPart +
            "|${BlockFont.CELL_W}x${BlockFont.CELL_H}" +
            "|adv${BlockFont.ADVANCE}" +
            "|lh${BlockFont.LINE_HEIGHT}"
    }
}
