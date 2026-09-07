package com.styleconverter.runtime.spacing

// wave-46 lane Y8 — the B1-B8 / F pins for UaBlockMarginFontBasis, the
// font basis of the UA default block margins.
//
// Provenance: CSS2/cascade/inherit-computed-001 on wave45-final. The
// composed root `<p>` declares `font-size: larger` (live wire copied
// verbatim below); the Chromium browser-ref puts its border-top at row 35
// = 16px image pad + 19.2px (1em of the p's OWN computed 19.2px), both
// natives at row 32 = 16 + the fixed 16px-root table value. Shifting the
// Android capture down 3px re-scores 0.8959 → 0.9984 through the scorer's
// own diffWebVsRef — the basis IS the residual. These pins hold that
// number and, above all, the IDENTITY contract: every element without its
// own font signal must resolve to null so the table is kept verbatim.

import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.typography.TextStyleApplier
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UaBlockMarginFontBasisTest {

    /** One IR property from a JSON literal (the frozen wire byte shape). */
    private fun prop(type: String, json: String) = IRProperty(type, Json.parseToJsonElement(json))

    /** The inherit-computed-001 wire, verbatim from the wave45-final per-test IR. */
    private val larger = prop("FontSize", """{"original":{"type":"relative","keyword":"larger"}}""")

    /** Floating tolerance for the ×1.2 products. */
    private val eps = 1e-4f

    // ── B1/B2: the identity contract ────────────────────────────────────

    /** B2 — NO FontSize and no monospace family ⇒ null: the caller keeps
     *  the 16px-root table, which is what protects every corpus root and
     *  child without a font-size declaration (the simulated blast radius
     *  of the root path is exactly ONE test). */
    @Test
    fun `B2 - no own font signal resolves to null`() {
        assertNull(UaBlockMarginFontBasis.ownFontSizePx(emptyList()))
        assertNull(UaBlockMarginFontBasis.ownFontSizePx(listOf(prop("MarginTop", """{"px":20}"""))))
        // A non-monospace family is not a font-SIZE signal either.
        assertNull(UaBlockMarginFontBasis.ownFontSizePx(listOf(prop("FontFamily", """["Inter"]"""))))
    }

    /** B1 — no FontSize but a first-family `monospace` ⇒ Chromium's 13px
     *  defaultFixedFontSize via MonospaceUAFontSize (consulted, not
     *  re-derived): a `<p style="font-family: monospace">` has a 13px UA
     *  margin in the browser. */
    @Test
    fun `B1 - monospace quirk is the basis when nothing is declared`() {
        assertEquals(13f, UaBlockMarginFontBasis.ownFontSizePx(listOf(prop("FontFamily", """["monospace"]"""))))
    }

    /** B1-gate — the fixed default is a KEYWORD-size rule. Blink resolves
     *  `medium` against the fixed table only when the computed size CAME
     *  from a keyword; an element whose UA size is an em (html.css h1 2em,
     *  h2 1.5em, h3 1.17em, h5 .83em, h6 .67em — h4 declares none) takes
     *  CheckForGenericFamilyChange's 13/16 scaling instead, so a monospace
     *  `<h1>` computes 26px and its UA margin is .67 × 26 ≈ 17.4px. An
     *  unguarded B1 gave .67 × 13 = 8.71px — a 12px error. This module does
     *  not model the scaling: the em-sized headings resolve to null and keep
     *  the ref-calibrated table (h1 → 21px), which is the pre-wave-46 render
     *  and the right answer for any non-monospace heading. */
    @Test
    fun `B1 gate - an em-sized heading never takes the monospace fixed default`() {
        val mono = listOf(prop("FontFamily", """["monospace"]"""))
        for (t in listOf("h1", "h2", "h3", "h5", "h6", "H1")) {
            assertNull("tag=$t", UaBlockMarginFontBasis.ownFontSizePx(mono, sourceTag = t))
        }
        // The keyword-sized tags keep the quirk — as does a tag-less caller
        // (identity for every pre-wave-46 call site) and any tag outside the
        // UA margin table, whose basis cannot move a pixel anyway.
        for (t in listOf("p", "ul", "ol", "blockquote", "figure", "pre", "h4", "div", null)) {
            assertEquals("tag=$t", 13f, UaBlockMarginFontBasis.ownFontSizePx(mono, sourceTag = t))
        }
        // The margin the gate protects, end to end: the table's 21px, NOT
        // the .67 × 13 = 8.71px an unguarded B1 produced (and not the
        // browser's ≈17.4 either — the conservative fallback, stated).
        val h1 = uaVerticalBlockMargins("h1", UaBlockMarginFontBasis.ownFontSizePx(mono, sourceTag = "h1"))
        assertEquals(21f to 21f, h1)
        assertNotEquals(0.67 * 13.0, h1.first.toDouble(), 0.01)
        // A DECLARED size is tag-independent: nothing is left for the UA
        // sheet to scale, so an `<h1>` with font-size: 40px still resolves 40.
        val declared = listOf(prop("FontSize", """{"px":40,"original":{"type":"length","px":40}}"""))
        assertEquals(40f, UaBlockMarginFontBasis.ownFontSizePx(declared, sourceTag = "h1"))
    }

    // ── B3-B7: the resolution ladder ────────────────────────────────────

    /** B3 — a resolved top-level px wins outright (absolute lengths and
     *  DynamicValueResolver prebakes both ride this shape). */
    @Test
    fun `B3 - top-level px is the basis`() {
        assertEquals(92f, UaBlockMarginFontBasis.ownFontSizePx(listOf(
            prop("FontSize", """{"px":92,"original":{"type":"length","px":92}}"""))))
        // Zero / negative cannot scale a margin ⇒ null (table kept).
        assertNull(UaBlockMarginFontBasis.ownFontSizePx(listOf(prop("FontSize", """{"px":0}"""))))
    }

    /** B4 — absolute keywords resolve through the css-fonts-4 §2.5.1 ladder. */
    @Test
    fun `B4 - absolute keyword ladder`() {
        fun kw(k: String) = listOf(prop("FontSize", """{"original":{"type":"absolute","keyword":"$k"}}"""))
        assertEquals(9f, UaBlockMarginFontBasis.ownFontSizePx(kw("xx-small")))
        assertEquals(16f, UaBlockMarginFontBasis.ownFontSizePx(kw("medium")))
        assertEquals(48f, UaBlockMarginFontBasis.ownFontSizePx(kw("xxx-large")))
        assertNull(UaBlockMarginFontBasis.ownFontSizePx(kw("huge")))
    }

    /** B5 — THE measured case: `larger` of the inherited 16px = 19.2px
     *  (CSS 2.1 §15.7 one ladder step, Blink's ×1.2), `smaller` = 13.33. */
    @Test
    fun `B5 - larger and smaller step from the inherited size`() {
        assertEquals(19.2f, UaBlockMarginFontBasis.ownFontSizePx(listOf(larger))!!, eps)
        assertEquals(16f / 1.2f, UaBlockMarginFontBasis.ownFontSizePx(listOf(
            prop("FontSize", """{"original":{"type":"relative","keyword":"smaller"}}""")))!!, eps)
        // The inherited base threads through: larger of a 20px parent = 24.
        assertEquals(24f, UaBlockMarginFontBasis.ownFontSizePx(listOf(larger), inheritedPx = 20f)!!, eps)
    }

    /** B6/B7 — em and % on font-size resolve against the INHERITED size
     *  (css-values-4 §6.1.1 / css-fonts-4 §2.5); rem against the 16px root. */
    @Test
    fun `B6 B7 - em rem and percentage`() {
        assertEquals(32f, UaBlockMarginFontBasis.ownFontSizePx(listOf(
            prop("FontSize", """{"original":{"type":"length","original":{"v":2,"u":"EM"}}}""")))!!, eps)
        assertEquals(24f, UaBlockMarginFontBasis.ownFontSizePx(listOf(
            prop("FontSize", """{"original":{"type":"length","original":{"v":1.5,"u":"REM"}}}""")), inheritedPx = 30f)!!, eps)
        assertEquals(19.2f, UaBlockMarginFontBasis.ownFontSizePx(listOf(
            prop("FontSize", """{"original":{"type":"percentage","value":120}}""")))!!, eps)
    }

    /** B8 — declared but not statically resolvable (var()/calc()/vw) ⇒
     *  null, the documented honest fallback to the table. */
    @Test
    fun `B8 - unresolvable declared sizes fall back to null`() {
        assertNull(UaBlockMarginFontBasis.ownFontSizePx(listOf(
            prop("FontSize", """{"original":{"type":"expression","expr":"calc(1em + 2px)"}}"""))))
        assertNull(UaBlockMarginFontBasis.ownFontSizePx(listOf(
            prop("FontSize", """{"original":{"type":"length","original":{"v":5,"u":"VW"}}}"""))))
        assertNull(UaBlockMarginFontBasis.ownFontSizePx(listOf(prop("FontSize", """"var(--x)""""))))
    }

    /** Last declaration wins, mirroring the extractors' fold. */
    @Test
    fun `last FontSize declaration wins`() {
        assertEquals(20f, UaBlockMarginFontBasis.ownFontSizePx(listOf(
            prop("FontSize", """{"px":10}"""), prop("FontSize", """{"px":20}"""))))
    }

    // ── F: parity with the painted size ─────────────────────────────────

    /** F — the basis the UA margin resolves against must be the size the
     *  glyphs actually paint at (TextStyleApplier's ladder), or the margin
     *  and the text would disagree about the em. Pinned for the relative
     *  keyword, the absolute keyword and the em shape. */
    @Test
    fun `F - basis equals the painted font size`() {
        for (props in listOf(
            listOf(larger),
            listOf(prop("FontSize", """{"original":{"type":"absolute","keyword":"x-large"}}""")),
            listOf(prop("FontSize", """{"original":{"type":"length","original":{"v":1.5,"u":"EM"}}}""")),
            listOf(prop("FontSize", """{"original":{"type":"percentage","value":75}}""")),
        )) {
            val painted = TextStyleApplier.extractTextStyle(props).fontSize.value
            assertEquals(props.toString(), painted, UaBlockMarginFontBasis.ownFontSizePx(props)!!, eps)
        }
    }

    // ── The em factor table ─────────────────────────────────────────────

    /** The UA sheet's block-margin em per tag (Chromium html.css). */
    @Test
    fun `ua block margin em factors`() {
        for (t in listOf("p", "ul", "ol", "blockquote", "pre", "figure", "h3", "P")) {
            assertEquals("tag=$t", 1f, UaBlockMarginFontBasis.uaBlockMarginEm(t))
        }
        assertEquals(0.67f, UaBlockMarginFontBasis.uaBlockMarginEm("h1"))
        assertEquals(2.33f, UaBlockMarginFontBasis.uaBlockMarginEm("h6"))
        for (t in listOf("div", "li", "span", "section", null)) {
            assertNull("tag=$t", UaBlockMarginFontBasis.uaBlockMarginEm(t))
        }
    }
}
