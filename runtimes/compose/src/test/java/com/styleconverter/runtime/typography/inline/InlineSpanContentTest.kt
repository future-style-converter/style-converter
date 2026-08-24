package com.styleconverter.runtime.typography.inline

// Wave 47 (lane Z6) — the Compose styled-span MOUNT's pins: the overlay
// maps fold ranges through the string surgery, keeps every base span
// intact, and resolves the member em factor against the paragraph size.
// Plain object construction (AnnotatedString is a value type), no
// composition needed — the same JVM-only approach InlineAtomContentTest
// takes for the atom mount.

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineSpanContentTest {

    /** block-ellipsis-004's span attribution, typed (the ring's output). */
    private val purpleBoldItalic15em = InlineSpanRing.Style(
        ink = InlineSpanRing.Ink(0.5019608f, 0f, 0.5019608f, 1f),
        fontSizeEm = 1.5f,
        fontWeight = 700,
        italic = true,
    )

    @Test
    fun `overlay - the member range styles the final string with em resolved against the paragraph`() {
        // The fold's merged string and its (surgery-free) final twin.
        val original = "Line 1\nLine 2\nLine 3\nLine 4"
        val out = InlineSpanContent.overlay(
            base = AnnotatedString(original),
            original = original,
            spans = listOf(InlineRunFold.Span(5, 14, 27, purpleBoldItalic15em)),
            paragraphFontSizePx = 16f,
        )!!
        // Glyphs untouched; exactly one added span over "Line 3\nLine 4".
        assertEquals(original, out.text)
        val range = out.spanStyles.single()
        assertEquals(14, range.start)
        assertEquals(27, range.end)
        // 1.5em × the paragraph's resolved 16px = 24 (px == sp harness).
        assertEquals(24.sp, range.item.fontSize)
        assertEquals(FontWeight(700), range.item.fontWeight)
        assertEquals(FontStyle.Italic, range.item.fontStyle)
        assertEquals(Color(0.5019608f, 0f, 0.5019608f, 1f), range.item.color)
    }

    @Test
    fun `overlay - ranges remap through pre-break surgery and base spans survive`() {
        // The pre-break rewrote the member-boundary space as '\n' — the
        // member range (over "bbb", original 4..7) must land at 4..7 in
        // the transformed string too, ON TOP of an existing base span
        // (word-spacing kern, script fallback…), which stays untouched.
        val base = buildAnnotatedString {
            append("aaa\nbbb")
            addStyle(SpanStyle(fontWeight = FontWeight(300)), 0, 7)
        }
        val out = InlineSpanContent.overlay(
            base = base,
            original = "aaa bbb",
            spans = listOf(
                InlineRunFold.Span(1, 4, 7, InlineSpanRing.Style(underline = true))
            ),
            paragraphFontSizePx = 16f,
        )!!
        assertEquals(2, out.spanStyles.size)
        // The base paragraph span survives verbatim…
        assertEquals(FontWeight(300), out.spanStyles[0].item.fontWeight)
        // …and the member underline lands after it (added LAST = wins on
        // overlap, the CSS inner-box order), at the remapped range.
        assertEquals(TextDecoration.Underline, out.spanStyles[1].item.textDecoration)
        assertEquals(4, out.spanStyles[1].start)
        assertEquals(7, out.spanStyles[1].end)
    }

    @Test
    fun `overlay - unalignable surgery answers null so the caller renders un-spanned`() {
        // A case rewrite is outside the modeled op set (the fold's
        // font-variant host gate makes this unreachable in production —
        // this pins the defensive fallback, not a live path).
        assertNull(
            InlineSpanContent.overlay(
                base = AnnotatedString("ABC"),
                original = "abc",
                spans = listOf(InlineRunFold.Span(0, 0, 3, InlineSpanRing.Style(underline = true))),
                paragraphFontSizePx = 16f,
            )
        )
    }

    @Test
    fun `overlay - a range fully consumed by surgery styles nothing`() {
        // The member was a lone collapsible space the breaker dropped —
        // an empty overlay range would be invalid; it is skipped instead.
        val out = InlineSpanContent.overlay(
            base = AnnotatedString("ab"),
            original = "a b",
            spans = listOf(InlineRunFold.Span(0, 1, 2, InlineSpanRing.Style(underline = true))),
            paragraphFontSizePx = 16f,
        )!!
        assertTrue(out.spanStyles.isEmpty())
    }
}
