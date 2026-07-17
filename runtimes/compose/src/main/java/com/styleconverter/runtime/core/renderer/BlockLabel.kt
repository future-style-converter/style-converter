package com.styleconverter.runtime.core.renderer

// Compose pieces the label composable needs: it is a childless Layout node
// that reports the shared line-box size and paints 1x1-px rects behind it.
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout

/**
 * Block-font placeholder label — the Compose side of the cross-platform
 * glyph-wall fix (see BlockFont.gen.kt): the harness's SYNTHESIZED
 * component-name label no longer renders through a font stack at all.
 * Instead every platform rasterizes the same 5x7 bit atlas as integer-
 * coordinate 1x1-px filled rects, so the three captures of a placeholder
 * label are byte-identical (fonts were the residual noise capping ~50
 * text-bearing fixtures at SSIM 0.90-0.949).
 *
 * All geometry decisions live here as PURE functions (JVM-unit-testable
 * without Robolectric), mirroring the WptCaptureMode.kt pattern; the
 * `@Composable` below only wires them into measure + draw.
 */
internal object BlockLabel {

    /** Cell-grid x origin: the first glyph cell starts 8px right of the
     *  label's top-left — the shared cross-platform constant (all three
     *  runtimes draw their first column at x=8 in the label's space). */
    const val ORIGIN_X = 8

    /** Cell-grid y origin: glyph row 0 sits 6px below the label's
     *  top-left — shared constant, same rationale as [ORIGIN_X]. */
    const val ORIGIN_Y = 6

    /** Right-edge margin the truncation rule reserves: characters are
     *  dropped until `ORIGIN_X + n*ADVANCE <= componentWidth - EDGE_MARGIN`
     *  (no ellipsis — the shared spec truncates silently). */
    const val EDGE_MARGIN = 8

    /**
     * The label ink: rgba(237, 237, 237, 0.7) — the placeholder label color
     * the web reference paints. 0xED == 237 for each RGB channel. Alpha
     * ROUNDING CHOICE (shared across platforms): 0.7 × 255 = 178.5, which
     * we round HALF-UP to 179 == 0xB3, so the effective alpha every
     * platform must rasterize with is exactly 179/255 (~0.70196) — a
     * platform that truncated to 178 instead would diverge by one blend
     * step on every lit pixel.
     */
    val COLOR = Color(0xB3EDEDED)

    /**
     * Shared label transform: UPPERCASE the input, and map any character
     * the atlas has no glyph for to '-' (the atlas only carries space, %,
     * -, ., 0-9, A-Z, _ — see BlockFont.GLYPHS). Uppercasing FIRST means
     * 'a'..'z' hit their capital glyphs instead of degrading to '-'.
     * The INPUT is exactly the string PlaceholderContent used to hand its
     * Text composable (what is labeled does not change — only how it is
     * rasterized).
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
     * with `ORIGIN_X + n*ADVANCE <= componentWidthPx - EDGE_MARGIN`, i.e.
     * n = floor((componentWidth - 16) / 6), clamped to the label length.
     * Integer division IS the floor here because the budget is coerced
     * non-negative first (Kotlin `/` truncates toward zero).
     */
    fun truncatedCount(labelLength: Int, componentWidthPx: Int): Int {
        // Horizontal pixel budget left for glyph cells once both the 8px
        // origin inset and the 8px right margin are reserved.
        val budget = componentWidthPx - ORIGIN_X - EDGE_MARGIN
        // No budget → no characters (never negative counts).
        if (budget <= 0) return 0
        // Whole advances that fit, never more than the label provides.
        return (budget / BlockFont.ADVANCE).coerceAtMost(labelLength)
    }

    /**
     * The full rect list for the first [count] characters of an already-
     * [normalize]d label: one (x, y) pair per SET bit, in the label's own
     * coordinate space (so the first possible rect is at (8, 6)). Cell
     * origin for char i is x = ORIGIN_X + i*ADVANCE, y = ORIGIN_Y; within
     * a cell, bit 4 of the row int is the LEFTMOST column (col 0) and row
     * 0 is the top — the exact convention BlockFont.gen.kt documents.
     * This single pure function is BOTH what the draw phase paints and
     * what the unit suite pins, so tests and pixels cannot diverge.
     */
    fun rects(normalized: String, count: Int = normalized.length): List<Pair<Int, Int>> {
        // Pre-size roughly (≤ 35 bits/cell) — micro-optimization only.
        val out = ArrayList<Pair<Int, Int>>(count * 16)
        // Guard: callers may pass a count from a coerced layout width, so
        // never read past the string (see BlockLabelPlaceholder's draw).
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

/**
 * The composable that replaces the synthesized-name placeholder Text: a
 * childless Layout node occupying the SAME layout slot the Text did (so
 * surrounding layout — the wrap-content component box, the 50x30 floor —
 * still sizes off a real measured child), drawing the block glyphs itself.
 *
 * DENSITY: measure + draw are in RAW PIXELS (Layout's `layout()` takes px
 * and DrawScope draws in px) — deliberately NOT dp-converted. The visual
 * harness runs the emulator at 160dpi (density 1.0) where px == dp, so
 * the reported (n*ADVANCE) x LINE_HEIGHT footprint is the same number of
 * dp the shared spec states; on any other density the label would still
 * rasterize on the integer pixel grid (the whole point — no fractional
 * scaling, no antialiasing), matching web/iOS px-space output.
 *
 * NO ANTIALIASING: every rect has integer topLeft and integer 1x1 size,
 * so each covers exactly one full pixel — Skia's AA produces full
 * coverage on pixel-aligned edges, i.e. no blended edge pixels.
 */
@Composable
internal fun BlockLabelPlaceholder(label: String) {
    // Normalize once per label string (uppercase + unknown→'-'), cached
    // across recompositions — the label never changes for a component.
    val normalized = remember(label) { BlockLabel.normalize(label) }
    // Measure→draw plumbing for the truncated char count: the label node
    // reports ZERO size (see below), so the draw pass can no longer derive
    // the count from its own measured width. The measure pass writes the
    // count here; measure always precedes draw in a frame.
    val truncated = remember(label) { mutableIntStateOf(Int.MAX_VALUE) }
    Layout(
        // No children: this node IS the label; all ink comes from draw.
        content = {},
        modifier = Modifier.drawBehind {
            // rects() re-clamps to the string length, so a stale/MAX count
            // can never overrun the string.
            for ((x, y) in BlockLabel.rects(normalized, truncated.intValue)) {
                drawRect(
                    // Shared label ink — see BlockLabel.COLOR for the
                    // 179/255 alpha rounding contract.
                    color = BlockLabel.COLOR,
                    // Integer offsets cast to float stay exact (px grid).
                    topLeft = Offset(x.toFloat(), y.toFloat()),
                    // Exactly one device pixel per bit.
                    size = Size(1f, 1f)
                )
            }
        }
    ) { _, constraints ->
        // componentWidth for the truncation rule: the incoming max-width
        // constraint is the width of the box the label lives in (the
        // component's content box — the nearest measurable stand-in for
        // the spec's componentWidth on Compose). Unbounded constraints
        // (scrollables / intrinsic passes) mean "no truncation".
        val componentWidthPx =
            if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        // Shared truncation math (drop trailing chars, no ellipsis).
        truncated.intValue = BlockLabel.truncatedCount(normalized.length, componentWidthPx)
        // ZERO LAYOUT FOOTPRINT: the label is dev chrome, not content — it
        // must not contribute to the component's auto-height. The first
        // block-font cut reported (n*ADVANCE) x LINE_HEIGHT here while the
        // three platforms' OLD text line boxes all differed, so auto-height
        // components rendered different canvas heights per platform and
        // every pixel below the label shifted (X-web pairs cratered while
        // iOS-Android agreed). All three platforms now give the label zero
        // flow size and draw the ink as an overlay at the shared (8,6)
        // origin — rects() already carries that origin, and Compose never
        // clips drawBehind without an explicit clip modifier.
        layout(0, 0) {
            // Childless — nothing to place.
        }
    }
}
