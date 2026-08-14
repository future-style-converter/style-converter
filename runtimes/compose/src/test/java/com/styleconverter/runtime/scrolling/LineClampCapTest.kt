package com.styleconverter.runtime.scrolling

// Wave 41 (lane T6) — pins for the block-level line-clamp height cap.
//
// The cap turns `line-clamp: <n>` (css-overflow-4 §5: max-lines +
// continue:discard) into an N-line-box height budget on the clamp
// CONTAINER, so child components' line boxes are discarded too — the
// wave-40 defect family (block-ellipsis-004/005/006/012/015/027/031/032,
// line-clamp-005/006/007 on both natives) where every child rendered
// unclamped below the ref's closed box. Wire shapes below are verbatim
// from runs/wave40-final/sections/css-overflow/per-test-ir/*.json, and
// the expected numbers are the MEASURED ref geometry of those tests'
// frozen Chromium captures (see each test's comment).

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LineClampCapTest {

    private fun parse(s: String): JsonElement = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    // ==================== linesCount: the wire gate =======================

    @Test
    fun `lines variant yields its count`() {
        // The converter's LineClampPropertyParser emits IRNumber counts as
        // floats — {"type":"lines","count":2.0} for `line-clamp: 2`.
        assertEquals(
            2,
            LineClampCap.linesCount(listOf(pair("LineClamp", """{"type":"lines","count":2.0}""")))
        )
    }

    @Test
    fun `none and auto variants do not cap`() {
        // `none` = clamp off; `auto` = clamp at the box's own block-size
        // constraint — neither maps to a fixed N-line height budget.
        assertNull(LineClampCap.linesCount(listOf(pair("LineClamp", """{"type":"none"}"""))))
        assertNull(LineClampCap.linesCount(listOf(pair("LineClamp", """{"type":"auto"}"""))))
    }

    @Test
    fun `sub-1 counts are outside the grammar and do not cap`() {
        // css-overflow-4 §5.1: <integer [1,∞]>. The converter accepts
        // `line-clamp: 0` and emits count 0.0 — same guard as the leaf
        // Text(maxLines) path (which crashes Compose below 1).
        assertNull(LineClampCap.linesCount(listOf(pair("LineClamp", """{"type":"lines","count":0.0}"""))))
    }

    @Test
    fun `absent property does not cap`() {
        // The common path: no LineClamp on the wire → no cap at all.
        assertNull(LineClampCap.linesCount(listOf(pair("Width", """{"type":"length","px":100.0}"""))))
    }

    @Test
    fun `last declaration wins like the cascade`() {
        // Mirrors the typography extractor's fold: later declarations of
        // the same property override earlier ones.
        assertEquals(
            3,
            LineClampCap.linesCount(
                listOf(
                    pair("LineClamp", """{"type":"lines","count":1.0}"""),
                    pair("LineClamp", """{"type":"lines","count":3.0}""")
                )
            )
        )
    }

    // ==================== capPx: the geometry contract ====================

    @Test
    fun `monospace clamp with border matches the ref leaf calibration`() {
        // block-ellipsis-012's clamp root (verbatim IR): line-clamp 1,
        // font-family monospace (no font-size → the UA fixed default,
        // 13px), 1px solid borders. Ref geometry: 1 line × (13 × 1.25 =
        // 16.25) + 2×1px band = 18.25 — the frozen Chromium box is rows
        // 16..33 (17-18px tall) in the wave-40 ref PNG.
        val cap = LineClampCap.capPx(
            listOf(
                pair("LineClamp", """{"type":"lines","count":1.0}"""),
                pair("FontFamily", """["monospace"]"""),
                pair("BorderTopWidth", """{"px":1.0}"""),
                pair("BorderBottomWidth", """{"px":1.0}"""),
                pair("BorderTopStyle", "\"SOLID\""),
                pair("BorderBottomStyle", "\"SOLID\"")
            )
        )
        assertNotNull(cap)
        assertEquals(18.25f, cap!!, 0.01f)
    }

    @Test
    fun `declared line-height wins verbatim`() {
        // line-clamp-005's clamp root (verbatim IR): `font: 16px/32px` →
        // FontSize 16px + LineHeight 32px, clamp 3, zero vertical padding.
        // Ref: the yellow box closes after line 3 — the cap must be 3×32,
        // NOT 3 × (16 × 1.25).
        val cap = LineClampCap.capPx(
            listOf(
                pair("LineClamp", """{"type":"lines","count":3.0}"""),
                pair("FontSize", """{"px":16.0,"original":{"type":"length","px":16.0}}"""),
                pair("LineHeight", """{"original":{"type":"length","px":32.0}}"""),
                pair("PaddingTop", """{"px":0.0}"""),
                pair("PaddingBottom", """{"px":0.0}""")
            )
        )
        assertNotNull(cap)
        assertEquals(96f, cap!!, 0.01f)
    }

    @Test
    fun `default font clamp uses the composed 1_25 ref pin`() {
        // block-ellipsis-004's clamp root: line-clamp 3, no font-size, no
        // border → 3 × (16 × 1.25 = 20) = 60. The 1.25 ratio is the
        // corpus-v4.1 REF_LINE_HEIGHT pin shared by all four surfaces.
        val cap = LineClampCap.capPx(
            listOf(pair("LineClamp", """{"type":"lines","count":3.0}"""))
        )
        assertNotNull(cap)
        assertEquals(60f, cap!!, 0.01f)
    }

    @Test
    fun `vertical padding rides inside the cap`() {
        // Padding is chained INSIDE the overflow node (StyleApplier step 8
        // is inner of step 7), so the node the cap measures includes it:
        // 2 lines × 20 + 4 + 6 = 50.
        val cap = LineClampCap.capPx(
            listOf(
                pair("LineClamp", """{"type":"lines","count":2.0}"""),
                pair("PaddingTop", """{"px":4.0}"""),
                pair("PaddingBottom", """{"px":6.0}""")
            )
        )
        assertNotNull(cap)
        assertEquals(50f, cap!!, 0.01f)
    }

    @Test
    fun `borders without a visible style reserve no band`() {
        // borderBandInsets only reserves width for a VISIBLE border —
        // width with style:none contributes nothing (mirrors web layout).
        val cap = LineClampCap.capPx(
            listOf(
                pair("LineClamp", """{"type":"lines","count":1.0}"""),
                pair("BorderTopWidth", """{"px":5.0}"""),
                pair("BorderBottomWidth", """{"px":5.0}""")
            )
        )
        assertNotNull(cap)
        // No BorderStyle on the wire → no visible border → cap is the
        // bare line box (16 × 1.25 = 20).
        assertEquals(20f, cap!!, 0.01f)
    }

    @Test
    fun `no clamp yields no cap`() {
        // The hot path for every clamp-less component in the corpus.
        assertNull(LineClampCap.capPx(listOf(pair("Color", """{"srgb":{"r":0,"g":0,"b":0}}"""))))
    }

    // ==================== cappedHeight: the pure decision =================

    @Test
    fun `measurement within slack is kept byte-identically`() {
        // The leaf calibration: block-ellipsis-013's clamped leaf measures
        // 35px against a 34.5 cap (2×16.25 + 2px band). ceil(34.5)=35,
        // 35 ≤ 35+2 → the leaf keeps its measured height untouched.
        assertEquals(35, LineClampCap.cappedHeight(35, 34.5f))
    }

    @Test
    fun `overflowing measurement is capped at ceil of the cap`() {
        // block-ellipsis-012 today: the unclamped children measure ~50px
        // against the 18.25 cap → clamp to ceil(18.25) = 19 (the ref box
        // is 17-18px; ceil never truncates a fractional line's last row).
        assertEquals(19, LineClampCap.cappedHeight(50, 18.25f))
    }

    @Test
    fun `boundary just past slack trips the cap`() {
        // cap 20 → ceil 20, slack 2: 22 is kept (could be rounding), 23
        // is a genuine extra-content overflow and is discarded.
        assertEquals(22, LineClampCap.cappedHeight(22, 20f))
        assertEquals(20, LineClampCap.cappedHeight(23, 20f))
    }

    // ==================== extractor + applier wiring ======================

    @Test
    fun `extractor carries the cap on the config`() {
        // OverflowExtractor folds the cap into OverflowConfig so the
        // StyleApplier chain (which only hands the applier this config)
        // can reach it.
        val cfg = OverflowExtractor.extractOverflowConfig(
            listOf(pair("LineClamp", """{"type":"lines","count":2.0}"""))
        )
        assertNotNull(cfg.lineClampCapPx)
        assertEquals(40f, cfg.lineClampCapPx!!, 0.01f)
    }

    @Test
    fun `clamp-less config is byte-identical to the pre-wave-41 default`() {
        // No LineClamp → the extractor must not touch the config: same
        // default instance shape, null cap.
        val cfg = OverflowExtractor.extractOverflowConfig(
            listOf(pair("Overflow", "\"HIDDEN\""))
        )
        assertNull(cfg.lineClampCapPx)
    }

    @Test
    fun `applyOverflow identity is preserved without overflow or clamp`() {
        // The pre-wave-41 fast path: neither declared overflow nor a clamp
        // → the applier returns the incoming modifier INSTANCE unchanged.
        val m = androidx.compose.ui.Modifier
        assertSame(m, OverflowApplier.applyOverflow(m, OverflowConfig()))
    }

    @Test
    fun `applyOverflow chains the cap when a clamp is present`() {
        // With a cap the applier must return a DIFFERENT modifier (Y-clip
        // + layout cap chained) — the clamp genuinely changes rendering.
        val m = androidx.compose.ui.Modifier
        val out = OverflowApplier.applyOverflow(m, OverflowConfig(lineClampCapPx = 40f))
        assertTrue(out !== m)
    }
}
