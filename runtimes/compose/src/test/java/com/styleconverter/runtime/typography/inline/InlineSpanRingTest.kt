package com.styleconverter.runtime.typography.inline

// Wave 47 (lane Z6) — the STYLED-SPAN RING's pure pins: admission (which
// member styling may ride the fold, which refuses with the property
// named) and the range alignment through the leaf pipeline's string
// surgery (soft-hyphen strip + the greedy pre-break's newline/space/
// hyphen ops). The wire shapes below are the exact payloads
// wave46-final's victims carry (block-ellipsis-004's span, inset-014's
// u) plus one synthetic per admission rule.

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineSpanRingTest {

    /** Shorthand: one wire property from raw JSON. */
    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    /** Admit with an empty host list (host ink unknown). */
    private fun admit(tag: String = "span", vararg props: IRProperty) =
        InlineSpanRing.admit(tag, props.toList(), emptyList())

    // ── admission: the styled attributes ─────────────────────────────────

    @Test
    fun `color, weight, style and em size ride as one typed attribution`() {
        // block-ellipsis-004's span payload, verbatim shapes.
        val admitted = admit(
            "span",
            prop("Color", """{"srgb":{"r":0.5019607843137255,"g":0,"b":0.5019607843137255},"original":"purple"}"""),
            prop("FontWeight", """{"weight":700,"original":"bold"}"""),
            prop("FontStyle", "\"italic\""),
            prop("FontSize", """{"original":{"type":"length","original":{"v":1.5,"u":"EM"}}}"""),
        ) as InlineSpanRing.Admission.Admitted
        assertEquals(700, admitted.style.fontWeight)
        assertTrue(admitted.style.italic)
        assertEquals(1.5f, admitted.style.fontSizeEm!!, 1e-6f)
        assertNull(admitted.style.fontSizePx)
        assertEquals(0.5019608f, admitted.style.ink!!.r, 1e-4f)
        // No border longhands → nothing lost, nothing to log.
        assertTrue(admitted.statedLossTypes.isEmpty())
    }

    @Test
    fun `font-size shapes - resolved px, percentage factor and the keyword ladder`() {
        // A DynamicValueResolver-resolved px wire.
        val px = admit("span", prop("FontSize", """{"px":24,"original":{"type":"length","px":24}}"""))
            as InlineSpanRing.Admission.Admitted
        assertEquals(24f, px.style.fontSizePx!!, 1e-6f)
        // css-fonts-4 §2.4 percentage — a parent-relative factor.
        val pct = admit("span", prop("FontSize", """{"original":{"type":"percentage","value":120}}"""))
            as InlineSpanRing.Admission.Admitted
        assertEquals(1.2f, pct.style.fontSizeEm!!, 1e-6f)
        // The absolute keyword ladder (x-large = 24px, the §2.4 table
        // TextStyleApplier pins — the two reads must agree to the pixel).
        val kw = admit("span", prop("FontSize", """{"original":{"type":"absolute","keyword":"x-large"}}"""))
            as InlineSpanRing.Admission.Admitted
        assertEquals(24f, kw.style.fontSizePx!!, 1e-6f)
        // An unresolvable unit (vw has no static base here) refuses.
        val vw = admit("span", prop("FontSize", """{"original":{"type":"length","original":{"v":5,"u":"VW"}}}"""))
        assertEquals(
            "member-prop:FontSize-unresolved",
            (vw as InlineSpanRing.Admission.Refused).reason
        )
    }

    @Test
    fun `u tag seeds the UA underline, decoration-line adds line-through, overline refuses`() {
        // HTML rendering §15.3.3: `u { text-decoration: underline }`.
        val u = admit("u") as InlineSpanRing.Admission.Admitted
        assertTrue(u.style.underline)
        // A wire list adds line-through (css-text-decor-3 §2.1 ||).
        val strike = admit("span", prop("TextDecorationLine", """["LINE_THROUGH"]"""))
            as InlineSpanRing.Admission.Admitted
        assertTrue(strike.style.lineThrough)
        // Per-range overline exists on neither platform — refuse, named.
        val over = admit("span", prop("TextDecorationLine", """["overline"]"""))
        assertEquals(
            "member-prop:TextDecorationLine:overline",
            (over as InlineSpanRing.Admission.Refused).reason
        )
    }

    @Test
    fun `decoration color must equal the effective ink (span decorations paint in text color)`() {
        // inset-014's shape: decoration black over host-inherited black —
        // equal, so the span-colored underline is exact.
        val equal = InlineSpanRing.admit(
            "u",
            listOf(prop("TextDecorationColor", """{"srgb":{"r":0,"g":0,"b":0},"original":"black"}""")),
            listOf(prop("Color", """{"srgb":{"r":0,"g":0,"b":0},"original":"black"}""")),
        )
        assertTrue(equal is InlineSpanRing.Admission.Admitted)
        // inset-011's shape: blue decoration over black text — a span
        // underline would paint the wrong color, so the ring refuses.
        val diverging = InlineSpanRing.admit(
            "u",
            listOf(prop("TextDecorationColor", """{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}""")),
            listOf(prop("Color", """{"srgb":{"r":0,"g":0,"b":0},"original":"black"}""")),
        )
        assertEquals(
            "member-prop:TextDecorationColor-divergence",
            (diverging as InlineSpanRing.Admission.Refused).reason
        )
        // Unknown effective ink (no member Color, no host Color) cannot
        // prove equality — refuse rather than guess.
        val unknown = admit("u", prop("TextDecorationColor", """{"srgb":{"r":0,"g":0,"b":0}}"""))
        assertEquals(
            "member-prop:TextDecorationColor-divergence",
            (unknown as InlineSpanRing.Admission.Refused).reason
        )
    }

    @Test
    fun `borders are a stated loss, styleless border colors stay inert, unknown props refuse`() {
        // 004's 2px solid blue box: admitted, but REPORTED as a loss so
        // the seam can log the dropped ink (repo no-silent-fallthrough).
        val bordered = admit(
            "span",
            prop("BorderTopStyle", "\"SOLID\""),
            prop("BorderTopWidth", """{"px":2}"""),
            prop("BorderTopColor", """{"srgb":{"r":0,"g":0,"b":1}}"""),
        ) as InlineSpanRing.Admission.Admitted
        assertEquals(1, bordered.statedLossTypes.size)
        assertTrue(bordered.statedLossTypes[0].startsWith("border-box-ink("))
        // Width without a style paints nothing (css-backgrounds-3 §3.2 —
        // computed width of a styleless side is zero): no loss to report.
        val styleless = admit(
            "span",
            prop("BorderTopWidth", """{"px":2}"""),
            prop("BorderTopColor", """{"srgb":{"r":0,"g":0,"b":1}}"""),
        ) as InlineSpanRing.Admission.Admitted
        assertTrue(styleless.statedLossTypes.isEmpty())
        // Anything outside the ring refuses with the type named — the
        // wave-44 `member-prop:` contract (032's pre-wrap hangs span).
        val ws = admit("span", prop("WhiteSpace", "\"PRE_WRAP\""))
        assertEquals("member-prop:WhiteSpace", (ws as InlineSpanRing.Admission.Refused).reason)
    }

    // ── wave 48 (lane W4): the UA-styled tag rings ───────────────────────

    @Test
    fun `sup and sub seed the UA smaller factor and their vertical shifts`() {
        // HTML rendering §15.3.4: `sub, sup { font-size: smaller }` plus
        // the super/sub vertical-align keywords (VerticalShift's banner
        // derives Blink's px rule; parent = the paragraph for a direct
        // member, factor 1).
        val sup = admit("sup") as InlineSpanRing.Admission.Admitted
        assertEquals(1f / 1.2f, sup.style.fontSizeEm!!, 1e-6f)
        assertEquals(InlineSpanRing.VerticalShift(up = true, parentPx = null, parentEm = 1f), sup.style.shift)
        val sub = admit("sub") as InlineSpanRing.Admission.Admitted
        assertEquals(InlineSpanRing.VerticalShift(up = false, parentPx = null, parentEm = 1f), sub.style.shift)
        // A shifted member is never plain — the span must be emitted.
        assertTrue(!sup.style.isPlain && !sub.style.isPlain)
    }

    @Test
    fun `a declared font-size beats the sup UA smaller seed (author over UA)`() {
        // css-cascade-4 §6.1 origin order: the author's 24px wins over
        // the UA `smaller`; the shift itself still rides.
        val sized = admit("sup", prop("FontSize", """{"px":24,"original":{"type":"length","px":24}}"""))
            as InlineSpanRing.Admission.Admitted
        assertEquals(24f, sized.style.fontSizePx!!, 1e-6f)
        assertNull(sized.style.fontSizeEm)
        assertTrue(sized.style.shift!!.up)
    }

    @Test
    fun `the italic family seeds the UA slant and a declared normal resets it`() {
        // §15.3.4: `cite, dfn, em, i, var { font-style: italic }`.
        for (tag in listOf("i", "em", "cite", "var", "dfn")) {
            val ua = admit(tag) as InlineSpanRing.Admission.Admitted
            assertTrue("$tag must seed italic", ua.style.italic)
        }
        // An author `font-style: normal` beats the UA rule (§6.1) — the
        // member then folds as PLAIN text, exactly like a bare span.
        val reset = admit("i", prop("FontStyle", "\"normal\""))
            as InlineSpanRing.Admission.Admitted
        assertTrue(reset.style.isPlain)
    }

    @Test
    fun `composeNested - a sup inside an i inherits the slant and rebases its shift parent`() {
        val outer = (admit("i") as InlineSpanRing.Admission.Admitted).style
        val nested = (admit("sup") as InlineSpanRing.Admission.Admitted).style
        // Size-less outer: the sup keeps paragraph-relative smaller and
        // its shift parent stays the paragraph (factor 1).
        val composed = InlineSpanRing.composeNested(outer, nested)!!
        assertTrue(composed.italic)
        assertEquals(1f / 1.2f, composed.fontSizeEm!!, 1e-6f)
        assertEquals(InlineSpanRing.VerticalShift(up = true, parentPx = null, parentEm = 1f), composed.shift)
        // A 1.5em outer: factors multiply (css-values-4 §5.1.1 — the
        // sup's parent is the i) and the shift parent rebases to 1.5.
        val bigOuter = outer.copy(fontSizeEm = 1.5f)
        val rebased = InlineSpanRing.composeNested(bigOuter, nested)!!
        assertEquals(1.5f / 1.2f, rebased.fontSizeEm!!, 1e-5f)
        assertEquals(InlineSpanRing.VerticalShift(up = true, parentPx = null, parentEm = 1.5f), rebased.shift)
        // A px outer: the nested factor resolves absolute.
        val pxOuter = outer.copy(fontSizePx = 30f)
        val absolute = InlineSpanRing.composeNested(pxOuter, nested)!!
        assertEquals(25f, absolute.fontSizePx!!, 1e-4f)
        assertEquals(InlineSpanRing.VerticalShift(up = true, parentPx = 30f, parentEm = 1f), absolute.shift)
    }

    @Test
    fun `composeNested - decorations propagate down and a compound shift refuses`() {
        // css-text-decor-3 §2.1: an outer underline paints over the
        // nested descendant — the composed piece keeps it.
        val outerU = (admit("u") as InlineSpanRing.Admission.Admitted).style
        val nestedSup = (admit("sup") as InlineSpanRing.Admission.Admitted).style
        assertTrue(InlineSpanRing.composeNested(outerU, nestedSup)!!.underline)
        // sup-in-sub (offsets ADD box-by-box) has no flat expression —
        // the named wall answers null and the fold bails.
        val outerSub = (admit("sub") as InlineSpanRing.Admission.Admitted).style
        assertNull(InlineSpanRing.composeNested(outerSub, nestedSup))
    }

    // ── wave 48 (fix lane F5): the sup/sub PIXEL rule, pinned ────────────
    // S5 must-fix 4: the parent/3+1 / −(parent/5+1) resolution had ZERO
    // coverage (mutating /3+1 → /3 passed all 4644 native tests). These
    // rows pin InlineSpanRing.shiftPx — the ONE Compose resolution site —
    // and the SAME rows are pinned on the iOS twin (InlineSpanRingTests),
    // so the two seams cannot drift apart silently again.

    @Test
    fun `shiftPx - the super rows pin Blink's parent-over-3-plus-1 at every probe size`() {
        // Direct sup members (parent = the paragraph, factor 1) — the
        // S5 probe's exact paragraph sizes and expected px
        // (16/3+1, 32/3+1, 48/3+1, 96/3+1).
        val sup = (admit("sup") as InlineSpanRing.Admission.Admitted).style.shift!!
        assertEquals(6.3333f, InlineSpanRing.shiftPx(sup, 16f), 1e-3f)
        assertEquals(11.6667f, InlineSpanRing.shiftPx(sup, 32f), 1e-3f)
        assertEquals(17.0f, InlineSpanRing.shiftPx(sup, 48f), 1e-3f)
        assertEquals(33.0f, InlineSpanRing.shiftPx(sup, 96f), 1e-3f)
    }

    @Test
    fun `shiftPx - the sub rows pin minus parent-over-5-plus-1 at every probe size`() {
        // Direct sub members — lowering is NEGATIVE px
        // (−(16/5+1), −(32/5+1), −(96/5+1)).
        val sub = (admit("sub") as InlineSpanRing.Admission.Admitted).style.shift!!
        assertEquals(-4.2f, InlineSpanRing.shiftPx(sub, 16f), 1e-3f)
        assertEquals(-7.4f, InlineSpanRing.shiftPx(sub, 32f), 1e-3f)
        assertEquals(-20.2f, InlineSpanRing.shiftPx(sub, 96f), 1e-3f)
    }

    @Test
    fun `shiftPx - a sup nested under a 30px outer resolves off the OUTER size`() {
        // composeNested rewrites the shift parent to the outer's px
        // encoding (subelements-002's `<i>…<sup>…` shape with a sized
        // outer): parent 30 → 30/3+1 = 11, regardless of the paragraph.
        val outer = (admit("i") as InlineSpanRing.Admission.Admitted).style.copy(fontSizePx = 30f)
        val nested = (admit("sup") as InlineSpanRing.Admission.Admitted).style
        val shift = InlineSpanRing.composeNested(outer, nested)!!.shift!!
        assertEquals(11.0f, InlineSpanRing.shiftPx(shift, 16f), 1e-3f)
        // The paragraph size is irrelevant once the parent is absolute.
        assertEquals(11.0f, InlineSpanRing.shiftPx(shift, 96f), 1e-3f)
    }

    // ── the range alignment through the pipeline's string surgery ────────

    @Test
    fun `alignment - identity when no surgery ran`() {
        val map = InlineSpanRing.alignment("Line 1\nLine 2", "Line 1\nLine 2")!!
        assertEquals(0, map[0])
        assertEquals(7, map[7])
        assertEquals(13, map[13])
    }

    @Test
    fun `alignment - the pre-break's space-to-newline replacement keeps ranges exact`() {
        // The greedy pre-break rewrites the break space as '\n'.
        val map = InlineSpanRing.alignment("aaa bbb", "aaa\nbbb")!!
        // "bbb" starts at 4 in both coordinate spaces.
        assertEquals(4, map[4])
        assertEquals(7, map[7])
    }

    @Test
    fun `alignment - soft-hyphen deletion shifts downstream ranges left`() {
        // SoftHyphenPolicy (`hyphens: none`) deletes the U+00AD.
        val map = InlineSpanRing.alignment("ab­cd", "abcd")!!
        // 'c' sat at original index 3, lands at transformed index 2.
        assertEquals(2, map[3])
        assertEquals(4, map[5])
    }

    @Test
    fun `alignment - materialized hyphen insertion and space collapse both align`() {
        // A taken soft-hyphen break: shy deleted, U+2010 + '\n' inserted.
        val hyphen = InlineSpanRing.alignment("high­way", "high‐\nway")!!
        // "way" sat at original 5, lands after the inserted "‐\n" at 6.
        assertEquals(6, hyphen[5])
        // The breaker's collapse-split drops a doubled space.
        val collapse = InlineSpanRing.alignment("a  b", "a b")!!
        assertEquals(2, collapse[3])
    }

    @Test
    fun `alignment - surgery outside the op set answers null (caller falls back, logged)`() {
        // A case rewrite (small caps) is NOT in the modeled op set — the
        // caller renders un-spanned rather than mis-ranged.
        assertNull(InlineSpanRing.alignment("abc", "ABC"))
    }
}
