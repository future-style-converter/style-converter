package com.styleconverter.runtime.typography.font

// Wave 34, lane F1 — pin table for the SPAN INSTALLATION half of the
// per-script fallback: which family each script claims, and the WPT gate
// that keeps the whole mechanism out of the product renderer.
//
// The segmentation contract itself is pinned in ScriptRunSegmenterTest;
// these pins are about what the renderer actually hands `Text`.

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.styleconverter.runtime.typography.InterFontFamily
import com.styleconverter.runtime.typography.font.ScriptRunSegmenter.TextScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptFallbackFontsTest {

    // ── The script → family map ─────────────────────────────────────────

    @Test
    fun `each target script maps to its OWN bundled family`() {
        // Five distinct families — a collision would render one script with
        // another's outlines, which no device capture would flag as wrong,
        // only as "still not matching the ref".
        val families = listOf(
            TextScript.ARABIC, TextScript.ARMENIAN, TextScript.BENGALI,
            TextScript.HEBREW, TextScript.KHMER
        ).map { ScriptFallbackFonts.familyFor(it) }
        families.forEach { assertNotNull("every target script has a family", it) }
        assertEquals("no two scripts share a family", 5, families.toSet().size)
    }

    @Test
    fun `DEFAULT claims NO family — a default run keeps the caller's face`() {
        // null, not InterFontFamily. Re-stating Inter on a default run would
        // OVERRIDE a document's own `font-family` declaration — a regression
        // this lane has no business causing.
        assertNull(ScriptFallbackFonts.familyFor(TextScript.DEFAULT))
    }

    @Test
    fun `no fallback family IS the Inter family`() {
        // Guards the same mistake from the other side: the bundled Noto
        // faces must be separate FontFamily instances from the harness's
        // pinned Latin face.
        for (s in listOf(TextScript.ARABIC, TextScript.ARMENIAN, TextScript.BENGALI,
                         TextScript.HEBREW, TextScript.KHMER)) {
            assertTrue("$s must not resolve to Inter",
                ScriptFallbackFonts.familyFor(s) !== (InterFontFamily as FontFamily))
        }
    }

    // ── The measured platform verdict ───────────────────────────────────

    @Test
    fun `substitution is ENABLED on Compose by measurement`() {
        // The lane's central finding, pinned so a later wave has to CHANGE a
        // test to flip it rather than flip it by accident: the Android
        // emulator's own non-Latin faces are NOT the ones the
        // Chromium-on-macOS browser-ref lands on, so the bundled faces move
        // the capture towards the ref. Measured on the private emulator over
        // the 13 non-Latin-ink documents — net +1 test over the 0.95 gate,
        // 10 of 13 improved. The full table is in the constant's own banner;
        // this pin is the tripwire.
        assertTrue(ScriptFallbackFonts.SUBSTITUTION_ENABLED)
    }

    @Test
    fun `the iOS twin's verdict is the opposite, and that is deliberate`() {
        // Documented here because the twins are otherwise byte-parallel and
        // a reader of ONE file would reasonably assume the other agrees.
        // runtimes/swiftui/…/ScriptFallbackFonts.swift sets
        // `substitutionEnabled = false`: the iOS simulator shares CoreText's
        // fallback cascade WITH the Chromium-on-macOS ref, so substituting
        // there replaces a matching face with a non-matching one (measured:
        // net −1 pass, 10 of 13 worse, armenian-006 0.9544 → 0.9171).
        // Nothing to assert cross-language — this pin exists so the asymmetry
        // is stated in both trees.
        assertTrue(ScriptFallbackFonts.SUBSTITUTION_ENABLED)
    }

    // ── The WPT gate ────────────────────────────────────────────────────

    @Test
    fun `disabled is the IDENTITY — the dark-stage 327 cannot move`() {
        // The gate the whole scope claim rests on: outside WPT capture the
        // function returns its ARGUMENT, not a rebuilt copy, so there is no
        // path by which a product/baseline render can differ.
        val base = AnnotatedString("ԺԱ.")
        assertSame(base, ScriptFallbackFonts.applyTo(base, enabled = false))
    }

    @Test
    fun `enabled but Latin-only is ALSO the identity`() {
        // The structural second guarantee: even inside WPT capture, a string
        // with no target-script codepoint takes the exact pre-lane path.
        val base = AnnotatedString("Test passes if the two columns match.")
        assertSame(base, ScriptFallbackFonts.applyTo(base, enabled = true))
    }

    // ── The installed spans ─────────────────────────────────────────────

    @Test
    fun `an Armenian marker gets ONE span over the letters and none over the dot`() {
        // The armenian-007 marker shape. NotoSansArmenian has no U+002E, so
        // the period must stay on the primary face — see the FULL STOP pin
        // in ScriptRunSegmenterTest for the cmap measurement behind it.
        val out = ScriptFallbackFonts.applyTo(AnnotatedString("ԺԱ."), enabled = true)
        assertEquals("only the Armenian run is spanned", 1, out.spanStyles.size)
        assertEquals(0, out.spanStyles[0].start)
        assertEquals(2, out.spanStyles[0].end)
        assertEquals(ScriptFallbackFonts.ArmenianFontFamily, out.spanStyles[0].item.fontFamily)
        // The text itself is untouched — this lane changes the FACE, never
        // the string (a changed string would be a wrong marker, not a font).
        assertEquals("ԺԱ.", out.text)
    }

    @Test
    fun `a span is installed per script for a multi-script string`() {
        val out = ScriptFallbackFonts.applyTo(AnnotatedString("ա٠০"), enabled = true)
        assertEquals(3, out.spanStyles.size)
        assertEquals(ScriptFallbackFonts.ArmenianFontFamily, out.spanStyles[0].item.fontFamily)
        assertEquals(ScriptFallbackFonts.ArabicFontFamily, out.spanStyles[1].item.fontFamily)
        assertEquals(ScriptFallbackFonts.BengaliFontFamily, out.spanStyles[2].item.fontFamily)
    }

    @Test
    fun `the installed span sets ONLY fontFamily — existing spans survive`() {
        // Compose merges overlapping SpanStyles field-wise, so the
        // small-caps size spans (synthesizeSmallCaps) and the word-spacing
        // letter-spacing spans (TextStyleApplier.applyWordSpacingSpans) that
        // may already sit on the input must come through untouched.
        val builder = AnnotatedString.Builder("ԺԱ ԺԲ")
        builder.addStyle(SpanStyle(letterSpacing = 12f.sp), 2, 3) // the space
        val base = builder.toAnnotatedString()
        val out = ScriptFallbackFonts.applyTo(base, enabled = true)
        // 1 pre-existing + 2 Armenian runs (the space splits them).
        assertEquals(3, out.spanStyles.size)
        val kept = out.spanStyles.first { it.item.letterSpacing == 12f.sp }
        assertEquals(2, kept.start)
        assertEquals(3, kept.end)
        assertNull("the pre-existing span must not gain a family", kept.item.fontFamily)
        // …and neither Armenian span carries a letter-spacing of its own.
        out.spanStyles.filter { it.item.fontFamily != null }
            .forEach { assertEquals(SpanStyle(fontFamily = it.item.fontFamily), it.item) }
    }

    @Test
    fun `annotate is the String door onto the same rule`() {
        // The three ::marker call sites hand `Text` a raw String; this is
        // the only difference between them and the placeholder path.
        val out = ScriptFallbackFonts.annotate("১.", enabled = true)
        assertEquals("১.", out.text)
        assertEquals(1, out.spanStyles.size)
        assertEquals(ScriptFallbackFonts.BengaliFontFamily, out.spanStyles[0].item.fontFamily)
        // Latin marker, WPT capture on → no spans at all, so `Text` lays it
        // out exactly as the String overload did.
        assertEquals(0, ScriptFallbackFonts.annotate("1.", enabled = true).spanStyles.size)
    }
}
