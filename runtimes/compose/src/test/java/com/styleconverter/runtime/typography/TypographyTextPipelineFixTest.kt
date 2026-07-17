package com.styleconverter.runtime.typography

// COMPOSE-TEXT lane pins — diagnosed real bugs in the Compose text
// pipeline, each test pinned to the wire shapes the converter actually
// emits. LIVE wire fact (verified by running the converter on a probe
// fixture): relative lengths DEEP-FLATTEN — `font-size: 1.5em` emits
// {"original":{"type":"length","original":{"v":1.5,"u":"EM"}}} with the
// {v,u} DIRECTLY under the outer "original" (no "value" key, no px);
// percentages emit {"original":{"type":"percentage","value":120}}. The
// "value"-nested envelope is the LEGACY shape older pinned wires used.
//
//   1. word-spacing must NOT ride TextStyle.letterSpacing (the old
//      fallback spread 12px between EVERY glyph and clipped '4567' off
//      the box) — it renders via space-only AnnotatedString spans.
//   2. word-spacing em/rem originals must survive the wire's bogus px:0
//      (same escape hatch letter-spacing already had).
//   3. letter-spacing em must resolve against the ELEMENT font-size via
//      Compose's native em TextUnit, not a hardcoded ×16.
//   4. font-size smaller/larger must step ±1 (×1.2 / ÷1.2, CSS 2.1
//      §15.7) from the INHERITED base instead of silently reusing the
//      16sp medium default — and em / % / rem must resolve too (em+%
//      against the inherited base, rem against the 16px root).
//   5. overline needs a draw-pass gate + line geometry (Compose's
//      TextDecoration has no overline flag).
//   6. text-transform capitalize titlecases the first LETTER of each
//      word, punctuation-leading words included (WPT capitalize-031).

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypographyTextPipelineFixTest {

    private fun j(s: String): JsonElement = Json.parseToJsonElement(s)
    private fun prop(type: String, json: String) = IRProperty(type, j(json))

    // ---- word-spacing: no more letter-spacing masquerade ----------------

    @Test
    fun `word-spacing no longer leaks into TextStyle letterSpacing`() {
        // The old fallback set letterSpacing=12sp — 12px between every
        // GLYPH, not every word ('0123' spread and clipped off its box).
        val style = TextStyleApplier.extractTextStyle(
            listOf(prop("WordSpacing", """{"px":12.0,"original":{"type":"length","value":{"px":12.0}}}"""))
        )
        assertEquals(TextUnit.Unspecified, style.letterSpacing)
    }

    // ---- word-spacing value extraction (px / em / rem / normal) ---------

    @Test
    fun `word-spacing px resolves directly`() {
        val sp = TextStyleApplier.extractWordSpacingSp(
            listOf(prop("WordSpacing", """{"px":12.0,"original":{"type":"length","value":{"px":12.0}}}""")),
            fontSizeSp = 16f
        )
        assertEquals(12f, sp!!, 0f)
    }

    @Test
    fun `word-spacing negative px passes through`() {
        // css-text-3 §5.1 allows negatives; spans contract advance the
        // same way they expand it.
        val sp = TextStyleApplier.extractWordSpacingSp(
            listOf(prop("WordSpacing", """{"px":-4.0}""")),
            fontSizeSp = 16f
        )
        assertEquals(-4f, sp!!, 0f)
    }

    @Test
    fun `word-spacing normal keyword is absent, not zero`() {
        // `normal` is the initial value — callers must skip the span pass
        // entirely (null), keeping the frozen baseline byte-identical.
        val sp = TextStyleApplier.extractWordSpacingSp(
            listOf(prop("WordSpacing", """{"px":0.0,"original":"normal"}""")),
            fontSizeSp = 16f
        )
        assertNull(sp)
    }

    @Test
    fun `word-spacing true zero stays zero`() {
        val sp = TextStyleApplier.extractWordSpacingSp(
            listOf(prop("WordSpacing", """{"px":0.0}""")),
            fontSizeSp = 16f
        )
        assertEquals(0f, sp!!, 0f)
    }

    @Test
    fun `word-spacing em resolves against the element font-size (live direct nesting)`() {
        // Wire defect: relative units ship px:0.0 — the {v,u} original
        // must win. 0.5em × 22px element = 11px. LIVE wire (verified
        // against the running converter): the IRLength {v,u} sits
        // DIRECTLY under the outer "original" — deep-flattened, no
        // "value" key.
        val sp = TextStyleApplier.extractWordSpacingSp(
            listOf(prop("WordSpacing", """{"px":0.0,"original":{"type":"length","original":{"v":0.5,"u":"EM"}}}""")),
            fontSizeSp = 22f
        )
        assertEquals(11f, sp!!, 1e-4f)
    }

    @Test
    fun `word-spacing em resolves on the legacy value nesting too`() {
        // Older pinned wires nested IRLength under a "value" key before
        // the deep-flatten — the escape hatch keeps accepting them.
        val sp = TextStyleApplier.extractWordSpacingSp(
            listOf(prop("WordSpacing", """{"px":0.0,"original":{"type":"length","value":{"original":{"v":0.5,"u":"EM"}}}}""")),
            fontSizeSp = 22f
        )
        assertEquals(11f, sp!!, 1e-4f)
    }

    @Test
    fun `word-spacing rem resolves against the 16px root regardless of element size`() {
        // Live direct nesting (see above); rem's base is the root size.
        val sp = TextStyleApplier.extractWordSpacingSp(
            listOf(prop("WordSpacing", """{"px":0.0,"original":{"type":"length","original":{"v":0.5,"u":"REM"}}}""")),
            fontSizeSp = 22f
        )
        assertEquals(8f, sp!!, 1e-4f)
    }

    @Test
    fun `word-spacing absent property yields null`() {
        assertNull(TextStyleApplier.extractWordSpacingSp(emptyList(), fontSizeSp = 16f))
    }

    // ---- word-spacing span resolution (sum with base letter-spacing) ----

    @Test
    fun `span spacing with unspecified base is the word-spacing alone`() {
        assertEquals(
            12f,
            TextStyleApplier.resolveWordSpacingSpanSp(12f, TextUnit.Unspecified, 16f),
            0f
        )
    }

    @Test
    fun `span spacing sums an sp letter-spacing base`() {
        // css-text-3 §5.1: BOTH trackings apply at a separator, and a
        // SpanStyle letterSpacing REPLACES the base on the spanned char —
        // so the span must carry the sum.
        assertEquals(
            14f,
            TextStyleApplier.resolveWordSpacingSpanSp(12f, 2f.sp, 16f),
            1e-4f
        )
    }

    @Test
    fun `span spacing resolves an em letter-spacing base against the font-size`() {
        // 0.1em base @ 20px element = 2px → 12 + 2.
        assertEquals(
            14f,
            TextStyleApplier.resolveWordSpacingSpanSp(12f, 0.1f.em, 20f),
            1e-4f
        )
    }

    // ---- word-spacing span placement ------------------------------------

    @Test
    fun `spans land on every space and only on spaces`() {
        val out = TextStyleApplier.applyWordSpacingSpans(AnnotatedString("ab cd ef"), 12f)
        // Text content untouched.
        assertEquals("ab cd ef", out.text)
        // Exactly the two separators at indices 2 and 5 carry a span.
        assertEquals(2, out.spanStyles.size)
        assertEquals(2, out.spanStyles[0].start)
        assertEquals(3, out.spanStyles[0].end)
        assertEquals(5, out.spanStyles[1].start)
        assertEquals(6, out.spanStyles[1].end)
        // Each span carries the resolved tracking.
        assertEquals(12f.sp, out.spanStyles[0].item.letterSpacing)
        assertEquals(12f.sp, out.spanStyles[1].item.letterSpacing)
    }

    @Test
    fun `no-break space is a word separator too`() {
        val out = TextStyleApplier.applyWordSpacingSpans(AnnotatedString("a\u00A0b"), 6f)
        assertEquals(1, out.spanStyles.size)
        assertEquals(1, out.spanStyles[0].start)
        assertEquals(2, out.spanStyles[0].end)
    }

    @Test
    fun `separator-free text gains no spans`() {
        val out = TextStyleApplier.applyWordSpacingSpans(AnnotatedString("abcdef"), 12f)
        assertTrue(out.spanStyles.isEmpty())
    }

    @Test
    fun `existing spans survive the word-spacing pass`() {
        // The small-caps synthesis path feeds an already-spanned string —
        // the builder copy must preserve those runs.
        val smallCaps = com.styleconverter.runtime.core.renderer.ComponentRenderer
            .synthesizeSmallCaps("ab cd", 16f)
        val before = smallCaps.spanStyles.size
        val out = TextStyleApplier.applyWordSpacingSpans(smallCaps, 12f)
        // One extra span (the single space at index 2) on top of the
        // pre-existing small-caps runs.
        assertEquals(before + 1, out.spanStyles.size)
        assertEquals("AB CD", out.text) // small-caps upper-cased content intact
    }

    // ---- letter-spacing em: native em TextUnit, not ×16 -----------------

    @Test
    fun `letter-spacing em resolves via the native em TextUnit (live direct nesting)`() {
        // 0.1em on a 22px element must yield 2.2px — the old ×16 hardcode
        // produced 1.6px. The em TextUnit defers resolution to the style's
        // own fontSize, which is exactly the css-values-4 §5.1.1 base.
        // LIVE wire (verified against the running converter):
        // `letter-spacing: 0.1em` emits {"px":0.0,"original":{"type":
        // "length","original":{"v":0.1,"u":"EM"}}} — {v,u} DIRECTLY under
        // the outer "original", no "value" key.
        val style = TextStyleApplier.extractTextStyle(
            listOf(prop("LetterSpacing", """{"px":0.0,"original":{"type":"length","original":{"v":0.1,"u":"EM"}}}"""))
        )
        assertTrue(style.letterSpacing.isEm)
        assertEquals(0.1f, style.letterSpacing.value, 1e-6f)
    }

    @Test
    fun `letter-spacing em resolves on the legacy value nesting too`() {
        // Older pinned wires nested IRLength under "value" — the escape
        // hatch keeps accepting that legacy envelope alongside the live
        // deep-flattened one.
        val style = TextStyleApplier.extractTextStyle(
            listOf(prop("LetterSpacing", """{"px":0.0,"original":{"type":"length","value":{"original":{"v":0.1,"u":"EM"}}}}"""))
        )
        assertTrue(style.letterSpacing.isEm)
        assertEquals(0.1f, style.letterSpacing.value, 1e-6f)
    }

    @Test
    fun `letter-spacing rem keeps the 16px root constant`() {
        // rem's base is the root font-size, pinned at the browser-default
        // 16px by the harness — a true constant, unlike em. Live direct
        // nesting.
        val style = TextStyleApplier.extractTextStyle(
            listOf(prop("LetterSpacing", """{"px":0.0,"original":{"type":"length","original":{"v":0.25,"u":"REM"}}}"""))
        )
        assertEquals(4f.sp, style.letterSpacing)
    }

    // ---- font-size smaller / larger -------------------------------------

    @Test
    fun `font-size larger steps up by the CSS ladder factor`() {
        // CSS 2.1 §15.7: one step up from the inherited 16px base at the
        // recommended 1.2 scaling factor → 19.2px. Previously the shape
        // fell through to null and reused the 16sp medium rendering.
        val style = TextStyleApplier.extractTextStyle(
            listOf(prop("FontSize", """{"original":{"type":"relative","keyword":"larger"}}"""))
        )
        assertEquals(16f * 1.2f, style.fontSize.value, 1e-4f)
    }

    @Test
    fun `font-size smaller steps down by the CSS ladder factor`() {
        val style = TextStyleApplier.extractTextStyle(
            listOf(prop("FontSize", """{"original":{"type":"relative","keyword":"smaller"}}"""))
        )
        assertEquals(16f / 1.2f, style.fontSize.value, 1e-4f)
    }

    @Test
    fun `font-size larger steps up from a threaded inherited base`() {
        // When the caller threads the real inherited size (the render
        // sites read it off the inheritance channel), smaller/larger step
        // from IT — matching iOS's inherited-size resolution — not from
        // the 16 constant. 20px inherited → larger = 24px.
        val style = TextStyleApplier.extractTextStyle(
            listOf(prop("FontSize", """{"original":{"type":"relative","keyword":"larger"}}""")),
            inheritedFontSizeSp = 20f
        )
        assertEquals(20f * 1.2f, style.fontSize.value, 1e-4f)
    }

    // ---- font-size relative lengths (em / rem) and percentage -----------

    @Test
    fun `font-size em resolves against the default inherited base (live wire)`() {
        // LIVE wire, verified against the running converter:
        // `font-size: 1.5em` deep-flattens to {"original":{"type":
        // "length","original":{"v":1.5,"u":"EM"}}} — NO "value" key, NO
        // resolved px. css-values-4 §5.1.1: font-size's own em resolves
        // against the INHERITED size (16px browser default here) → 24.
        // Previously this shape fell through to the 16sp default while
        // iOS/web resolved it.
        val style = TextStyleApplier.extractTextStyle(
            listOf(prop("FontSize", """{"original":{"type":"length","original":{"v":1.5,"u":"EM"}}}"""))
        )
        assertEquals(24f, style.fontSize.value, 1e-4f)
    }

    @Test
    fun `font-size em resolves against a threaded inherited base`() {
        // 1.5em × the parent's 20px = 30px.
        val style = TextStyleApplier.extractTextStyle(
            listOf(prop("FontSize", """{"original":{"type":"length","original":{"v":1.5,"u":"EM"}}}""")),
            inheritedFontSizeSp = 20f
        )
        assertEquals(30f, style.fontSize.value, 1e-4f)
    }

    @Test
    fun `font-size rem resolves against the 16px root even with an inherited base`() {
        // rem's base is the ROOT font-size (harness-pinned browser
        // default 16px — fixtures never style the root), NOT the parent:
        // 1.5rem stays 24px under a 20px inherited size.
        val style = TextStyleApplier.extractTextStyle(
            listOf(prop("FontSize", """{"original":{"type":"length","original":{"v":1.5,"u":"REM"}}}""")),
            inheritedFontSizeSp = 20f
        )
        assertEquals(24f, style.fontSize.value, 1e-4f)
    }

    @Test
    fun `font-size percentage resolves against the inherited base (live wire)`() {
        // LIVE wire: `font-size: 120%` emits {"original":{"type":
        // "percentage","value":120.0}} (no px). css-fonts-4 §2.4: % of
        // the inherited size — 120% × 16 default = 19.2. Previously fell
        // through to the 16sp default.
        val style = TextStyleApplier.extractTextStyle(
            listOf(prop("FontSize", """{"original":{"type":"percentage","value":120.0}}"""))
        )
        assertEquals(19.2f, style.fontSize.value, 1e-4f)
    }

    @Test
    fun `font-size percentage resolves against a threaded inherited base`() {
        // 120% × the parent's 20px = 24px.
        val style = TextStyleApplier.extractTextStyle(
            listOf(prop("FontSize", """{"original":{"type":"percentage","value":120.0}}""")),
            inheritedFontSizeSp = 20f
        )
        assertEquals(24f, style.fontSize.value, 1e-4f)
    }

    @Test
    fun `font-size em drives the unitless line-height base too`() {
        // preResolvedFontSp must see the SAME relative resolution:
        // `line-height: 2` on a 1.5em (=24px) element is 48px, not 32.
        val style = TextStyleApplier.extractTextStyle(
            listOf(
                prop("LineHeight", """{"multiplier":2.0}"""),
                prop("FontSize", """{"original":{"type":"length","original":{"v":1.5,"u":"EM"}}}""")
            )
        )
        assertEquals(48f, style.lineHeight.value, 1e-4f)
    }

    @Test
    fun `font-size absolute keyword resolves under the live wire tag`() {
        // The wire tag is "absolute" (FontSizeProperty serializer); the old
        // check matched only the stale "absoluteKeyword" name and survived
        // on the resolved px alone. Pin the keyword path itself (no px).
        val style = TextStyleApplier.extractTextStyle(
            listOf(prop("FontSize", """{"original":{"type":"absolute","keyword":"x-large"}}"""))
        )
        assertEquals(24f, style.fontSize.value, 0f)
    }

    // ---- text-transform capitalize: first LETTER, not first char --------

    @Test
    fun `capitalize titlecases the first letter after leading punctuation`() {
        // css-text-3 §2.1 / WPT capitalize-031: Chromium titlecases the
        // first LETTER of the word even behind leading punctuation —
        // "(this)" renders "(This)". The old replaceFirstChar touched
        // word[0] only, so "(hello" stayed lowercase on Android while web
        // capitalized it.
        assertEquals(
            "(Hello) \"World\"",
            TextStyleApplier.applyTextTransform(
                "(hello) \"world\"",
                TextStyleApplier.TextTransformMode.CAPITALIZE
            )
        )
    }

    @Test
    fun `capitalize still titlecases plain words`() {
        // Regression guard: the boundary fix must not disturb the common
        // letter-first path.
        assertEquals(
            "Hello World",
            TextStyleApplier.applyTextTransform(
                "hello world",
                TextStyleApplier.TextTransformMode.CAPITALIZE
            )
        )
    }

    @Test
    fun `capitalize leaves letterless words untouched`() {
        // Pure punctuation / numeric words have no letter to titlecase —
        // they pass through unchanged (explicitly, matching the browser).
        assertEquals(
            "123 --- Ok",
            TextStyleApplier.applyTextTransform(
                "123 --- ok",
                TextStyleApplier.TextTransformMode.CAPITALIZE
            )
        )
    }

    // ---- text-transform full-width is a DOCUMENTED no-op ----------------

    @Test
    fun `text-transform full-width maps to NONE`() {
        // Chromium — the visual reference — leaves the Latin corpus
        // unchanged for full-width; substituting U+FFxx forms would
        // diverge from the reference captures.
        // Wire form: the bare enum name the converter emits.
        val mode = TextStyleApplier.extractTextTransform(
            listOf(prop("TextTransform", "\"FULL_WIDTH\""))
        )
        assertEquals(TextStyleApplier.TextTransformMode.NONE, mode)
    }

    // ---- overline gate ---------------------------------------------------

    @Test
    fun `overline detected in the decoration array`() {
        assertTrue(TextStyleApplier.extractHasOverline(
            listOf(prop("TextDecorationLine", """["OVERLINE"]"""))
        ))
    }

    @Test
    fun `overline detected alongside underline`() {
        assertTrue(TextStyleApplier.extractHasOverline(
            listOf(prop("TextDecorationLine", """["UNDERLINE","OVERLINE"]"""))
        ))
    }

    @Test
    fun `overline detected as a bare keyword`() {
        assertTrue(TextStyleApplier.extractHasOverline(
            listOf(prop("TextDecorationLine", "\"OVERLINE\""))
        ))
    }

    @Test
    fun `underline alone does not trigger the overline pass`() {
        assertFalse(TextStyleApplier.extractHasOverline(
            listOf(prop("TextDecorationLine", """["UNDERLINE"]"""))
        ))
    }

    @Test
    fun `absent decoration does not trigger the overline pass`() {
        assertFalse(TextStyleApplier.extractHasOverline(emptyList()))
    }

    // ---- owned decoration-line flags -------------------------------------

    @Test
    fun `all three keywords flag their lines from one array wire`() {
        // The Triple fixture's wire: every css-text-decor-3 §2.1 line at once.
        val flags = TextStyleApplier.extractDecorationLineFlags(
            listOf(prop("TextDecorationLine", """["UNDERLINE","OVERLINE","LINE_THROUGH"]"""))
        )
        assertTrue(flags.underline)
        assertTrue(flags.overline)
        assertTrue(flags.lineThrough)
        assertTrue(flags.any)
    }

    @Test
    fun `bare keyword wire flags a single line`() {
        // Legacy primitive wire shape ("LINE_THROUGH", underscores intact).
        val flags = TextStyleApplier.extractDecorationLineFlags(
            listOf(prop("TextDecorationLine", "\"LINE_THROUGH\""))
        )
        assertFalse(flags.underline)
        assertFalse(flags.overline)
        assertTrue(flags.lineThrough)
    }

    @Test
    fun `none and blink flag nothing`() {
        // `none` is the initial value; `blink` has no visual effect in any
        // modern browser (css-text-decor-3 §2.1) — neither owns a pass.
        assertFalse(TextStyleApplier.extractDecorationLineFlags(
            listOf(prop("TextDecorationLine", "\"NONE\""))
        ).any)
        assertFalse(TextStyleApplier.extractDecorationLineFlags(
            listOf(prop("TextDecorationLine", """["BLINK"]"""))
        ).any)
        // Absent property → initial `none`.
        assertFalse(TextStyleApplier.extractDecorationLineFlags(emptyList()).any)
    }

    // ---- owned decoration geometry: the measured 22px Chromium oracle ----
    //
    // Empirical anchor (wave-5 gate captures, 22px Inter, alphabetic
    // baselines at rows 41 and 67, text span x=20..232): Chromium paints
    // pixel-snapped 2px rects at EXACTLY
    //   overline [18,20) / strike [33,35) / underline [43,45) (line 1)
    //   overline [44,46) / strike [59,61)                     (line 2)
    // These tests pin the decorationSegments math to those rows.

    /** The measured line-1 oracle inputs: baseline row 41, ink x 20..232. */
    private fun oracleSegments(flags: TextStyleApplier.DecorationLineFlags) =
        TextStyleApplier.decorationSegments(
            lineCount = 1,
            fontSizePx = 22f,
            flags = flags,
            lineBaseline = { 41f },
            lineLeft = { 20f },
            lineRight = { 232f }
        )

    @Test
    fun `underline at 22px lands on the measured rows 43-44`() {
        val seg = oracleSegments(
            TextStyleApplier.DecorationLineFlags(underline = true, overline = false, lineThrough = false)
        ).single()
        // Measured: 2px gap below the baseline (rows 41-42 clean), ink
        // rows 43-44 → rect [43, 45), thickness 2.
        assertEquals(43f, seg.top, 0f)
        assertEquals(2f, seg.thickness, 0f)
        // Spanning the line's inked extent, x 20..232.
        assertEquals(20f, seg.left, 0f)
        assertEquals(212f, seg.width, 0f)
    }

    @Test
    fun `overline at 22px lands on the measured rows 18-19`() {
        val seg = oracleSegments(
            TextStyleApplier.DecorationLineFlags(underline = false, overline = true, lineThrough = false)
        ).single()
        // Measured: bottom edge flush with baseline − rounded ascent
        // (41 − 21 = 20, the line box top) → rect [18, 20).
        assertEquals(18f, seg.top, 0f)
        assertEquals(2f, seg.thickness, 0f)
    }

    @Test
    fun `line-through at 22px lands on the measured rows 33-34`() {
        val seg = oracleSegments(
            TextStyleApplier.DecorationLineFlags(underline = false, overline = false, lineThrough = true)
        ).single()
        // Measured: strike centered at baseline − 671/2048 em (41 − 7.21),
        // rect straddles it and snaps → [33, 35).
        assertEquals(33f, seg.top, 0f)
        assertEquals(2f, seg.thickness, 0f)
    }

    @Test
    fun `second visual line reproduces the measured line-2 rows`() {
        // The capture's second wrapped line: baseline row 67, ink x 20..68.
        val flags = TextStyleApplier.DecorationLineFlags(
            underline = true, overline = true, lineThrough = true
        )
        val segments = TextStyleApplier.decorationSegments(
            lineCount = 2,
            fontSizePx = 22f,
            flags = flags,
            lineBaseline = { if (it == 0) 41f else 67f },
            lineLeft = { 20f },
            lineRight = { if (it == 0) 232f else 68f }
        )
        // 3 kinds × 2 lines.
        assertEquals(6, segments.size)
        val line2 = segments.drop(3)
        // Order is under → over → through (the emit order in decorationSegments).
        // Underline: 67 + 2 = 69 (clipped by the 50px fixture box in the
        // capture — consistent, the box ends at row 66).
        assertEquals(69f, line2[0].top, 0f)
        // Overline: 67 − 21 − 2 = 44 → measured rows 44-45. ✓
        assertEquals(44f, line2[1].top, 0f)
        // Strike: round(67 − 7.208 − 1) = 59 → measured rows 59-60. ✓
        assertEquals(59f, line2[2].top, 0f)
        // Line 2's shorter inked extent (the "0123" fragment).
        assertEquals(48f, line2[0].width, 0f)
    }

    @Test
    fun `fractional Android baselines snap to the integral web rows`() {
        // Android reports float baselines; web rows are integral. A
        // baseline of 40.6 must land on the same rows as 41.0.
        val seg = TextStyleApplier.decorationSegments(
            lineCount = 1,
            fontSizePx = 22f,
            flags = TextStyleApplier.DecorationLineFlags(underline = true, overline = false, lineThrough = false),
            lineBaseline = { 40.6f },
            lineLeft = { 20f },
            lineRight = { 232f }
        ).single()
        assertEquals(43f, seg.top, 0f)
    }

    @Test
    fun `thickness follows Inter underlineThickness em with a 1px floor`() {
        // Inter post.underlineThickness = 140/2048 em, rounded to device
        // rows: 16px → 1 (Chrome at body size), 22px → 2 (the measured
        // oracle), 32px → 2, 8px → floor at 1 so the line never vanishes.
        assertEquals(1f, TextStyleApplier.decorationThicknessPx(16f), 0f)
        assertEquals(2f, TextStyleApplier.decorationThicknessPx(22f), 0f)
        assertEquals(2f, TextStyleApplier.decorationThicknessPx(32f), 0f)
        assertEquals(1f, TextStyleApplier.decorationThicknessPx(8f), 0f)
    }

    @Test
    fun `empty visual lines paint no decoration`() {
        // A trailing blank line reports left==right — the browser draws
        // nothing over zero inked extent.
        val segments = TextStyleApplier.decorationSegments(
            lineCount = 2,
            fontSizePx = 16f,
            flags = TextStyleApplier.DecorationLineFlags(underline = true, overline = true, lineThrough = true),
            lineBaseline = { if (it == 0) 14f else 34f },
            lineLeft = { 4f },
            lineRight = { if (it == 0) 100f else 4f }
        )
        // Only line 0 paints (its 3 kinds); line 1 contributes nothing.
        assertEquals(3, segments.size)
    }

    @Test
    fun `no flags produce no segments even with lines`() {
        // Total-function guarantee: callers gate on flags.any, but the
        // math never invents rects for an unflagged pass.
        val segments = TextStyleApplier.decorationSegments(
            lineCount = 1,
            fontSizePx = 22f,
            flags = TextStyleApplier.DecorationLineFlags(underline = false, overline = false, lineThrough = false),
            lineBaseline = { 41f },
            lineLeft = { 20f },
            lineRight = { 232f }
        )
        assertTrue(segments.isEmpty())
    }
}
