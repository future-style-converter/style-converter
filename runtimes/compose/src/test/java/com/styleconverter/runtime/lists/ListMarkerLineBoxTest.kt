package com.styleconverter.runtime.lists

// Wave 29, lane MP — pin table for ListMarkerLineBox, the line box a
// synthesized `::marker` box occupies.
//
// ## The defect these pin against (measured on-device, emulator-5558)
// Composed WPT capture of wpt/css-counter-styles/armenian/
// css3-counter-styles-007 — 24 single-`<li>` `<ol>`s at `font-size: 25px`:
// Android's ink-band pitch was 34px/row against web's 31–32 and iOS's 31,
// accumulating into a 920px capture against web's 885. Re-feeding the same
// document with every `meta.markerText` stripped gave 31 — so the whole
// +2.75px/row was the marker box. Instrumenting the row's two children
// showed the marker measuring h=34 baseline=27 against the item's h=31
// baseline=26 while BOTH carried the same 31.25px CSS line box: Compose
// floors a single line at the resolved face's natural glyph box (these
// markers fall back to a system face with a ~1.36em line), and the
// baseline-aligned row's height is max(baseline) + max(height − baseline)
// = 34. The item's text runs have always been repaired by
// ComponentRenderer's `composedLineBoxSnap`; the marker `Text` was the one
// run in the runtime that never went through it.
//
// The @Composable/measure pathway needs androidTest, so — the same split
// ListMarkerRowTest and ListMarkerTextStyleTest document — these cover the
// pure halves: which box, and what height the snap claims for it.

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ListMarkerLineBoxTest {

    // ── resolve(): WHICH box ─────────────────────────────────────────────

    @Test
    fun `an undeclared line-height takes the composed ref box`() {
        // THE lane's case: nothing in the counter-styles corpus declares a
        // line-height, so the marker must take the same calibration the
        // item takes — 1.25 × font-size, the browser-ref's pinned box
        // (WptCaptureMode.REF_DEFAULT_FONT_LINE_HEIGHT_RATIO). At the 25px
        // these items inherit that is the 31.25px the item measures.
        val box = ListMarkerLineBox.resolve(
            declaredLineHeight = TextUnit.Unspecified,
            fontSize = 25.sp,
            declaredNormal = false,
            wptCapture = true,
            composedWpt = true
        )
        assertEquals(31.25f, box.value, 0.001f)
    }

    @Test
    fun `outside composed capture the undeclared box is the native ratio`() {
        // The 327-pair dark stage and the per-component inbox path both
        // run with composedWpt=false and must keep the historical 1.2×
        // box — the same split every other consumer of
        // composedDefaultLineHeightPx makes.
        val box = ListMarkerLineBox.resolve(
            declaredLineHeight = TextUnit.Unspecified,
            fontSize = 25.sp,
            declaredNormal = false,
            wptCapture = false,
            composedWpt = false
        )
        assertEquals(30.0f, box.value, 0.001f)
    }

    @Test
    fun `a declared line-height wins verbatim`() {
        // Author > us: an IR-declared value is used as-is, exactly as the
        // item's `effectiveLineHeight` uses it. `composedWpt` must not be
        // able to override it.
        val box = ListMarkerLineBox.resolve(
            declaredLineHeight = 40.sp,
            fontSize = 25.sp,
            declaredNormal = false,
            wptCapture = true,
            composedWpt = true
        )
        assertEquals(40.sp, box)
    }

    @Test
    fun `a declared normal keyword stays unpinned under WPT capture`() {
        // css-fonts-4 §4.3: the `font` shorthand RESETS line-height to
        // `normal`, and a directly-matching declaration beats the ref
        // injection's inherited rule — so the FACE's own metrics are the
        // right answer and no calibration may be imposed. Unspecified is
        // how Compose spells "use the face". Same row of the table
        // LineHeightNormal.lineBoxSource gives the item.
        val box = ListMarkerLineBox.resolve(
            // `normal` survives extraction as the legacy 1.2 multiplier,
            // so the DECLARED value is present and the keyword flag is
            // what must beat it — pinned here in the order that matters.
            declaredLineHeight = 30.sp,
            fontSize = 25.sp,
            declaredNormal = true,
            wptCapture = true,
            composedWpt = true
        )
        assertEquals(TextUnit.Unspecified, box)
    }

    @Test
    fun `declared normal outside WPT capture keeps the numeric stand-in`() {
        // Outside WPT capture the natives have always shipped the numeric
        // approximation for `normal`, and the committed baselines depend
        // on that side never moving (LineHeightNormal's stated risk).
        val box = ListMarkerLineBox.resolve(
            declaredLineHeight = 30.sp,
            fontSize = 25.sp,
            declaredNormal = true,
            wptCapture = false,
            composedWpt = false
        )
        assertEquals(30.sp, box)
    }

    @Test
    fun `an unresolved font-size falls back to the browser default`() {
        // The calibration is a MULTIPLE of the font size, so an
        // Unspecified size must not propagate as a missing number. 16sp is
        // the browser's inherited body default and the identical bottom-out
        // PlaceholderContent applies before multiplying.
        val box = ListMarkerLineBox.resolve(
            declaredLineHeight = TextUnit.Unspecified,
            fontSize = TextUnit.Unspecified,
            declaredNormal = false,
            wptCapture = true,
            composedWpt = true
        )
        assertEquals(ListMarkerLineBox.DEFAULT_FONT_SIZE_SP * 1.25f, box.value, 0.001f)
    }

    // ── snappedHeightPx(): MAKING Compose report it ──────────────────────

    @Test
    fun `one line snaps to exactly one rounded line box`() {
        // The measured case: a 31.25px box, whatever the face's natural
        // glyph box was (34 here). Rounded, not truncated — the item's
        // snap rounds too, and a shared rounding rule is what puts the two
        // boxes on the same integer pixel.
        assertEquals(31, ListMarkerLineBox.snappedHeightPx(1, 31.25f))
    }

    @Test
    fun `multiple lines snap to N boxes`() {
        // Why the line count is READ from the layout result rather than
        // assumed to be 1: a marker that ever wrapped would otherwise be
        // snapped to a single box and clipped.
        assertEquals(63, ListMarkerLineBox.snappedHeightPx(2, 31.25f))
    }

    @Test
    fun `a pre-settle line count passes the natural height through`() {
        // The count arrives from the PREVIOUS layout pass, so the first
        // frame has none. Null = "report what you measured" — the same
        // settle behaviour the item's snap has; the capture waits for
        // layout to settle before shooting.
        assertNull(ListMarkerLineBox.snappedHeightPx(0, 31.25f))
        assertNull(ListMarkerLineBox.snappedHeightPx(-1, 31.25f))
    }

    @Test
    fun `no resolved box means no modifier at all`() {
        // The declared-`normal` state resolves to Unspecified, which the
        // caller converts to 0f. There is no CSS box to snap into then —
        // the natural box IS the answer — so the chain must be untouched,
        // not merely a no-op layout pass.
        assertSame(Modifier, ListMarkerLineBox.snap(0f) { 1 })
        assertSame(Modifier, ListMarkerLineBox.snap(-1f) { 1 })
    }

    @Test
    fun `a resolved box does install a modifier`() {
        // The other side of the same gate, so a future edit cannot make
        // the snap silently inert for every marker.
        assertNotSame(Modifier, ListMarkerLineBox.snap(31.25f) { 1 })
    }
}
