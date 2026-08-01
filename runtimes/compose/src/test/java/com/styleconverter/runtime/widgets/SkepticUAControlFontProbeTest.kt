package com.styleconverter.runtime.widgets

// Wave-22 SKEPTIC probe for lane INK (A-RC2). The lane's headline claim
// is that "every widget box size is identical on Android and iOS by
// construction" because both natives size labels from the SAME pure
// UAControlFontMetrics table. That holds for the ASCII corpus, but the
// claim is about ALL wire labels — UAWidgetsResolve reads `label` from
// `component._text` / the `value` / `alt` attrs, i.e. arbitrary Unicode
// off the IR. These probes pin the string-iteration contract that makes
// the claim true beyond ASCII. The Swift twin
// (SkepticUAControlFontProbeTests.swift) asserts the IDENTICAL numbers.
//
// The pinned unit is the UTF-16 CODE UNIT: Kotlin's `for (ch in text)`
// walks UTF-16 units, so the Swift twin must walk `text.utf16` too.
// Walking Swift Characters (grapheme clusters) instead makes a
// non-BMP or combining label measure narrower on iOS than on Android.

import org.junit.Assert.assertEquals
import org.junit.Test

class SkepticUAControlFontProbeTest {

    private val px = UAControlFontMetrics.CONTROL_FONT_PX

    /** One fallback advance ('n'-width, 1139/2048 em) at the control size. */
    private val fb = 1139f * px / 2048f

    @Test
    fun `empty label measures zero and still yields the chrome-only button box`() {
        // No glyphs → no advance. Guards against a fallback that fires
        // on the empty string and silently widens every unlabelled box.
        assertEquals(0f, UAControlFontMetrics.advance("", px), 1e-6f)
        // A label-driven box with no label is pure chrome: 2 × 8.
        val (w, h) = UAWidgetsGeometry.intrinsicSize(
            UAWidgetsGeometry.Spec(UAWidgetsGeometry.Kind.BUTTON, label = ""),
        ) { s -> UAControlFontMetrics.advance(s, px) }
        assertEquals(2f * UAWidgetsGeometry.BUTTON_PAD_X, w, 1e-6f)
        assertEquals(UAWidgetsGeometry.CONTROL_H, h, 1e-6f)
    }

    @Test
    fun `control characters below the ASCII band take the documented fallback`() {
        // '\n' (0x0A) and '\t' (0x09) index NEGATIVE into the table.
        // The lookup must clamp to the fallback, never read out of
        // bounds and never return zero width.
        assertEquals(fb, UAControlFontMetrics.charAdvance('\n', px), 1e-6f)
        assertEquals(fb, UAControlFontMetrics.charAdvance('\t', px), 1e-6f)
        // DEL (0x7F) is one past the pinned band's last entry '~'.
        assertEquals(fb, UAControlFontMetrics.charAdvance('', px), 1e-6f)
        // '~' (0x7E) IS the last pinned entry — 1196 units, not the
        // fallback. This is the boundary the clamp must not swallow.
        assertEquals(1196f * px / 2048f, UAControlFontMetrics.charAdvance('~', px), 1e-6f)
    }

    @Test
    fun `a non-ASCII BMP label falls back once per code unit`() {
        // "café" — 'é' is U+00E9, one UTF-16 unit, outside the band.
        assertEquals(
            UAControlFontMetrics.advance("caf", px) + fb,
            UAControlFontMetrics.advance("café", px),
            1e-6f,
        )
    }

    @Test
    fun `a non-BMP label measures per UTF-16 unit — the cross-native pin`() {
        // U+1F600 GRINNING FACE is a surrogate PAIR: two UTF-16 units,
        // so Kotlin's loop takes the fallback TWICE. The Swift twin must
        // agree — if it walks Characters it takes the fallback once and
        // the same wire label produces a box ~7.4px narrower on iOS.
        assertEquals(2f * fb, UAControlFontMetrics.advance("😀", px), 1e-6f)
        // A combining sequence is likewise TWO units ("e" + U+0301).
        assertEquals(
            1139f * px / 2048f + fb,
            UAControlFontMetrics.advance("é", px),
            1e-6f,
        )
        // CRLF is two units (Swift folds it into ONE Character).
        assertEquals(2f * fb, UAControlFontMetrics.advance("\r\n", px), 1e-6f)
        // The monospace twin counts the same unit.
        assertEquals(
            2f * UAControlFontMetrics.MONO_ADVANCE_EM * px,
            UAControlFontMetrics.monoAdvance("😀", px),
            1e-6f,
        )
    }

    @Test
    fun `hidden inputs occupy no box at all`() {
        // display:none per the UA sheet — the mount hook must get 0×0 so
        // the atom contributes nothing to the inline cursor.
        val (w, h) = UAWidgetsGeometry.intrinsicSize(
            UAWidgetsGeometry.Spec(UAWidgetsGeometry.Kind.HIDDEN),
        ) { s -> UAControlFontMetrics.advance(s, px) }
        assertEquals(0f, w, 1e-6f)
        assertEquals(0f, h, 1e-6f)
        // …and paints nothing.
        assertEquals(
            0,
            UAWidgetsGeometry.plan(UAWidgetsGeometry.Spec(UAWidgetsGeometry.Kind.HIDDEN)) { 0f }.size,
        )
    }
}
