package com.styleconverter.runtime.lists

// Wave 43 (lane G2, fix for skeptic S4 defect D1) — the NEGATIVE-ORDINAL
// crash class in ListStyleApplier.getMarker.
//
// ## The defect
// Every cyclic / fixed-symbol counter style indexed its symbol run with a
// bare `symbols[index]` guarded only on the UPPER bound
// (`if (index < run.length)`). Before wave 43 that was safe by accident:
// the marker index was the raw children-loop position, so it could never
// go below 0. Wave 43 (lane V4) rerouted it through ListOrdinal — the
// HTML ordinal plan — and `<ol start="-3">` / `<li value="-3">` both parse
// their sign (ListOrdinal.parseHtmlInteger), with markerIndex subtracting
// one more. S4 measured ordinals -3…-1 arriving from the live corpus
// (wpt/css-lists cssom-negative-setter) and showed `greekLower[-4]` throws
// StringIndexOutOfBoundsException. That throw happens inside a
// @Composable: the harness app dies and the ENTIRE run reports TIMEOUT,
// not one wrong marker — which is why this is pinned rather than left to
// the visual gate to notice.
//
// ## The contract pinned here
// css-counter-styles-3 §2: a predefined style whose symbol run does not
// cover the value falls back to `decimal` (the counter-representation
// algorithm exits to the fallback style when the value is out of range). The guard applies that at BOTH
// ends, exactly like the iOS twin StyleEngine/lists/ListMarkerText.cyclic
// (`index >= 0 && index < symbols.count ? … : "\(index + 1)"`), which has
// carried the lower bound since wave 24.

import org.junit.Assert.assertEquals
import org.junit.Test

class ListStyleApplierTest {

    /** Marker for [index] under [type], the rest of the config default. */
    private fun marker(index: Int, type: ListStyleType): String =
        ListStyleApplier.getMarker(index, ListStyleConfig(listStyleType = type))

    // ── D1: negative ordinals fall back to decimal, never throw ────────

    @Test
    fun `negative index with LOWER_GREEK yields the decimal fallback`() {
        // THE pin S4 asked for. index -4 is what `<li value="-3">` becomes
        // after ListOrdinal.markerIndex (ordinal -3, minus one). Before the
        // guard this line threw StringIndexOutOfBoundsException; now §6.2's
        // decimal fallback prints the ordinal itself (-4 + 1 = -3).
        assertEquals("-3.", marker(-4, ListStyleType.LOWER_GREEK))
    }

    @Test
    fun `every cyclic run survives the whole measured negative range`() {
        // S4's measured corpus window is ordinals -3…-1 ⇒ marker indices
        // -4…-2. Sweep every style that indexes a fixed symbol run, so a
        // future style added to the family cannot regress one branch only.
        val cyclicTypes = listOf(
            ListStyleType.LOWER_GREEK, ListStyleType.UPPER_GREEK,
            ListStyleType.HIRAGANA, ListStyleType.KATAKANA,
            ListStyleType.HIRAGANA_IROHA, ListStyleType.KATAKANA_IROHA
        )
        for (type in cyclicTypes) {
            for (index in -4..-2) {
                // Decimal fallback = the ordinal (index + 1) plus the
                // predefined styles' "." suffix. Any throw fails the test
                // by escaping; any silent "" would fail this equality.
                assertEquals(
                    "$type at $index must fall back to decimal",
                    "${index + 1}.", marker(index, type)
                )
            }
        }
    }

    @Test
    fun `negative index with CJK_DECIMAL keeps the sign instead of throwing`() {
        // The same crash class one step removed: CJK_DECIMAL maps digits
        // through `cjkDigits[c - '0']`, and '-' - '0' == -3 would index the
        // map at -3. Guarded to the plain decimal string — css-counter-
        // styles-3 §6.2 gives cjk-decimal no `negative` override, so the
        // default "-" prefix is what a UA would paint. (Documented twin
        // divergence: iOS drops the sign entirely — see ListStyleApplier.)
        assertEquals("-3.", marker(-4, ListStyleType.CJK_DECIMAL))
    }

    // ── the guard must not disturb the in-range table ──────────────────

    @Test
    fun `in-range cyclic markers are unchanged by the guard`() {
        // Regression fence: the lower-bound guard is additive only. First
        // and last glyph of each run, the two positions a bad refactor of
        // `cyclic` would break first.
        assertEquals("α.", marker(0, ListStyleType.LOWER_GREEK))
        assertEquals("ω.", marker(23, ListStyleType.LOWER_GREEK))
        assertEquals("Α.", marker(0, ListStyleType.UPPER_GREEK))
        assertEquals("あ.", marker(0, ListStyleType.HIRAGANA))
        assertEquals("ア.", marker(0, ListStyleType.KATAKANA))
        assertEquals("い.", marker(0, ListStyleType.HIRAGANA_IROHA))
        assertEquals("イ.", marker(0, ListStyleType.KATAKANA_IROHA))
        assertEquals("三.", marker(2, ListStyleType.CJK_DECIMAL))
    }

    @Test
    fun `past the end of a run still falls back to decimal`() {
        // The UPPER bound the pre-wave-43 code already had — pinned so the
        // rewrite into the shared `cyclic` helper provably kept it. The
        // greek run is 24 glyphs, so index 24 is the first overflow.
        assertEquals("25.", marker(24, ListStyleType.LOWER_GREEK))
    }

    // ── the ListOrdinal seam: end to end, the way the renderer calls ───

    @Test
    fun `a negative li value renders through the real ordinal plan`() {
        // Not a synthetic index: build the plan the way ComponentRenderer
        // does, from a wire `<li value="-3">`, and bridge it exactly as
        // RenderListItemMarker's call site does. This is the whole failing
        // path S4 traced, asserted end to end.
        val children = listOf(
            com.styleconverter.runtime.core.ir.IRComponent(
                id = "li", name = "li", _tag = "li",
                attrs = com.styleconverter.runtime.core.ir.IRAttrs(value = "-3")
            )
        )
        val plan = ListOrdinal.ordinals(startAttr = null, reversed = false, children = children)
        // The ordinal itself is the wire's -3 (HTML §4.4.8 counter-set).
        assertEquals(-3, plan[0])
        // …and the marker prints the decimal fallback rather than dying.
        assertEquals(
            "-3.",
            marker(ListOrdinal.markerIndex(plan, 0), ListStyleType.LOWER_GREEK)
        )
    }
}
