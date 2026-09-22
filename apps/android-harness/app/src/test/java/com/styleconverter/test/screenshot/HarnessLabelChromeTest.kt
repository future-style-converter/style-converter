package com.styleconverter.test.screenshot

// Wave 51, PR A — the harness debug-label CHROME on Android, part 1 of 2:
// the GEOMETRY the CaptureCanvas draw node paints. Part 2 — the source-scan
// wiring pins — is HarnessLabelChromeSourceTest.kt beside this file; the two
// were one 195-line class until the polish lane made every line carry its
// why and the ≤200-line rule for new files forced the split.
//
// THE CONTRACT PINNED (docs/DYNAMIC_CAPTURE.md "Harness label chrome"): the
// component-name debug label is drawn by the CAPTURE HARNESS as a sibling
// layer over the finished capture root — never by the runtime, never inside
// the component's paint chain — at CAPTURE-FRAME (8,6), from the PLAIN name
// (`_` → space), truncated against the FRAME width, gated by the existing
// WPT/inbox flag, exactly one per capture iff the composed root has no
// children and no `_text`. Byte-identical rects on web / iOS / Android.
//
// WHY A JVM GEOMETRY PIN: this module's unit tests are plain junit:4.13.2
// (no Robolectric / Compose UI test / emulator), so the composable cannot
// run here. Every test below runs the SAME pure function the draw node
// consumes (`harnessLabelRects`, ScreenshotCaptureScreen.kt), so a green
// suite and the painted rect list cannot disagree.
//
// NEGATIVE CONTROLS, EXECUTED — each mutation applied to the working tree,
// this class run alone, the source restored byte-exact (sha256 compared)
// afterwards. Durable record: tools/titan/results/wave51-A/skeptic-android.md
// (the skeptic's re-execution of the lane's M3 and its own S6); the polish
// lane re-ran both against THIS file on 2026-09-22 with the same outcome:
//   M3 BlockLabel.ORIGIN_X/ORIGIN_Y = 24/22 (the old in-component footprint)
//      → geometry_firstRectIsTheCaptureFrameOrigin FAILS, and so do the three
//        other absolute-x pins (62-glyph, 39-glyph, 22px) — 4 of 6 red.
//   S6 `name.replace('_', ' ')` dropped from harnessLabelRects
//      → geometry_underscoreBecomesSpaceBeforeNormalize FAILS — 1 of 6 red.
// All 6 green on the shipped tree.

// The runtime's pure geometry object (public across Gradle modules): its
// `rects` / `normalize` are the oracle each pin compares the harness against.
import com.styleconverter.runtime.core.renderer.BlockLabel
// Exact-value pins (rect lists, counts, the (8,6) origin pair).
import org.junit.Assert.assertEquals
// Negative pins: a rect that must NOT be present past the truncation edge.
import org.junit.Assert.assertFalse
// The `_` → space pin needs "differs from the raw-underscore rect list".
import org.junit.Assert.assertNotEquals
// Presence pins: a specific cell's first rect must be in the list.
import org.junit.Assert.assertTrue
// JUnit 4's test marker — the only test API this module has.
import org.junit.Test

// One class per pin family, so a filtered `--tests` run is one gradle line.
class HarnessLabelChromeTest {

    // ── P1: geometry, through the exact function the draw node paints ──────

    // The origin pin: the mutation M3 (24/22, the old in-component
    // footprint) turns exactly this red first.
    @Test
    fun geometry_firstRectIsTheCaptureFrameOrigin() {
        // 'L' row 0 is 10000 (BlockFont.gen.kt) → the first set bit of
        // "LAYOUT C01 ALIGNCONTENT" is cell 0, col 0, row 0 → PNG (8, 6).
        assertEquals(8 to 6, harnessLabelRects("Layout_C01_AlignContent", 390).first())
    }

    // The default-frame truncation pin: 62 glyphs at 390px, no more.
    @Test
    fun geometry_defaultFrameKeepsSixtyTwoGlyphs() {
        // (390 - 16) / 6 = 62 whole advances. A 62-char name is drawn whole…
        val name62 = "H".repeat(62)
        // …through the harness function under test…
        val rects = harnessLabelRects(name62, 390)
        // …and equals the runtime oracle's untruncated rect list.
        assertEquals(BlockLabel.rects(BlockLabel.normalize(name62)), rects)
        // 'H' carries 17 set bits per cell, so 62 × 17 rects in total.
        assertEquals(62 * 17, rects.size)
        // The LAST cell starts at x = 8 + 61·6 = 374 ('H' row 0 is 10001:
        // col 0 lit), which is the absolute-x half of the M3 control.
        assertTrue(rects.contains(374 to 6))
        // A 63rd glyph would start at 380 > 390 - 8 - 6 → truncated, silently.
        val rects63 = harnessLabelRects("H".repeat(63), 390)
        // Same count as the 62-char name: the extra glyph contributed nothing.
        assertEquals(62 * 17, rects63.size)
        // And its would-be first rect is absent, not merely re-counted.
        assertFalse(rects63.contains(380 to 6))
    }

    // The narrow-frame truncation pin: 39 glyphs at CAPTURE_WIDTH=250.
    @Test
    fun geometry_captureWidth250KeepsThirtyNineGlyphs() {
        // CAPTURE_WIDTH=250 (docs/DYNAMIC_CAPTURE.md §2): (250 - 16) / 6 = 39.
        val rects = harnessLabelRects("H".repeat(62), 250)
        // 39 cells × 17 lit bits per 'H'.
        assertEquals(39 * 17, rects.size)
        // Cell 38 starts at x = 8 + 38·6 = 236 — present…
        assertTrue(rects.contains(236 to 6))
        // …and a 40th cell (x = 242) is dropped.
        assertFalse(rects.contains(242 to 6))
    }

    // The zero-budget pin: no glyph fits below 22px, exactly one at 22px.
    @Test
    fun geometry_frameBelowTwentyTwoDrawsNothing_andTwentyTwoDrawsOneGlyph() {
        // 21px: budget 5 < one 6px advance → zero glyphs (CaptureCanvas logs
        // this once; the math stays pure and returns an empty list).
        assertTrue(harnessLabelRects("H", 21).isEmpty())
        // 22px: budget 6 → exactly one glyph, i.e. the oracle's one-'H' list.
        assertEquals(BlockLabel.rects("H"), harnessLabelRects("HH", 22))
        // Which is 17 rects — the one cell's set bits, nothing from cell 1.
        assertEquals(17, harnessLabelRects("HH", 22).size)
    }

    // The text-parity pin: the plain name and its uppercased form are equal.
    @Test
    fun geometry_isCaseInsensitive_throughNormalize() {
        // The C51 text-parity argument: normalize uppercases everything, so a
        // `text-transform: uppercase` root (065_Typography_Uppercase) yields
        // the same rects from its plain name as from its transformed form.
        assertEquals(
            harnessLabelRects("TYPOGRAPHY_UPPERCASE", 390),
            harnessLabelRects("typography_uppercase", 390)
        )
    }

    // The `_` → space pin: the mutation S6 turns exactly this red.
    @Test
    fun geometry_underscoreBecomesSpaceBeforeNormalize() {
        // The atlas HAS a '_' glyph, so normalize alone would keep it; the
        // chrome contract is the name with `_` → space on every platform.
        assertEquals(BlockLabel.rects("A B"), harnessLabelRects("A_B", 390))
        // And the raw-underscore rendering is a DIFFERENT rect list, so the
        // equality above cannot pass vacuously if '_' ever maps to a space.
        assertNotEquals(BlockLabel.rects("A_B"), harnessLabelRects("A_B", 390))
    }
}
