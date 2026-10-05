package com.styleconverter.runtime.lists

// Wave 52, lane L6 (T1) — the `korean-hangul-formal` counter style on
// Compose. TWIN of runtimes/swiftui/Tests/StyleConverterRuntimeTests/
// KoreanHangulFormalTests.swift: the expansion table below is transcribed
// from it byte-for-byte, so the two natives cannot drift on this keyword.
//
// Three layers are pinned so a regression cannot hide in a seam:
//   1. the additive expansion itself (css-counter-styles-3 §7.1);
//   2. the keyword table (ListStyleExtractor.typeFromKeyword) — the exact
//      place the gap was: an unmodelled keyword answers null and the
//      resolver silently keeps the `<ol>` UA `decimal`;
//   3. the end-to-end marker string, driven by the VERBATIM per-test IR of
//      the one corpus carrier (tools/titan/runs/wave51-fix/sections/
//      css-counter-styles/per-test-ir/wpt__css-counter-styles__
//      counter-suffix.json, components `counter-suffix__0__3` and its two
//      `<li>` children `counter-suffix__0__3__0` / `__1`, copied below).
//
// MUTATION PROOF (EXECUTED 2026-09-25 at authoring, RE-EXECUTED 2026-10-05
// by tools/titan/results/wave52-counters-and-lists/mutate.py, entry
// `korean-arm` in mutations.log): making the `"korean_hangul_formal" ->`
// arm of typeFromKeyword unreachable turned tests 2 and 3 red
// (`expected:<KOREAN_HANGUL_FORMAL> but was:<null>` and, in the
// end-to-end pin, `… but was:<DECIMAL>` — the `<ol>` UA default kept)
// while test 1 and every other lists.* test stayed green; the file was
// restored byte-exact (sha256 equal before/after). Deleting the
// `ListStyleType.KOREAN_HANGUL_FORMAL ->` arm of getMarker does not
// compile — the `when` is exhaustive — which is the strongest form of
// "cannot regress silently" available for a Kotlin enum switch.

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KoreanHangulFormalTest {

    private fun json(s: String): JsonElement = Json.parseToJsonElement(s)
    private fun pair(type: String, wire: String): Pair<String, JsonElement?> = type to json(wire)

    // ── 1. The additive expansion (css-counter-styles-3 §7.1) ───────────

    @Test
    fun `expand covers the additive table`() {
        // Units — the two the corpus carrier actually asks for.
        assertEquals("일", KoreanHangulFormal.expand(1))
        assertEquals("이", KoreanHangulFormal.expand(2))
        assertEquals("구", KoreanHangulFormal.expand(9))
        // The FORMAL rule: the digit is written before the place marker,
        // so 10 is 일십 (`10 일십` in the table), never a bare 십.
        assertEquals("일십", KoreanHangulFormal.expand(10))
        assertEquals("일십일", KoreanHangulFormal.expand(11))
        assertEquals("이십", KoreanHangulFormal.expand(20))
        assertEquals("일백", KoreanHangulFormal.expand(100))
        assertEquals("일천", KoreanHangulFormal.expand(1000))
        // A ZERO digit contributes nothing — 1001 is 일천일, not
        // 일천영백영십일. This is the assertion that fails if the
        // `digit == 0 → continue` skip is ever removed.
        assertEquals("일천일", KoreanHangulFormal.expand(1001))
        // 2026 = 2·1000 + 0·100 + 2·10 + 6 — the hundreds digit is skipped
        // and the tens digit keeps its formal leading symbol.
        assertEquals("이천이십육", KoreanHangulFormal.expand(2026))
        // Top of the declared `range: -9999 9999`.
        assertEquals("구천구백구십구", KoreanHangulFormal.expand(9999))
        // The `0 영` row is in the table even though the marker path
        // (1-based) never asks for it.
        assertEquals("영", KoreanHangulFormal.expand(0))
        // ABOVE the declared range → the decimal spelling (§3.2 `range`),
        // the same bottom-out armenian/georgian/hebrew take here.
        assertEquals("10000", KoreanHangulFormal.expand(10_000))
        // -1 is NOT out of range (§7.1 declares `range: -9999 9999`); the
        // decimal spelling is this runtime's deliberate UNMODELLED-NEGATIVE
        // fallback, pinned so the choice cannot change silently — the
        // Swift twin pins the identical answer.
        assertEquals("-1", KoreanHangulFormal.expand(-1))
    }

    // ── 2. The keyword table — where the gap actually was ───────────────

    @Test
    fun `keyword table claims korean-hangul-formal`() {
        // The live wire spelling (the converter lowercases and hyphenates);
        // the extractor's normaliser turns the hyphens into underscores.
        assertEquals(ListStyleType.KOREAN_HANGUL_FORMAL,
            ListStyleExtractor.typeFromKeyword("korean-hangul-formal"))
        // The table normalises case and underscores for every keyword; the
        // new row must not be the one exception.
        assertEquals(ListStyleType.KOREAN_HANGUL_FORMAL,
            ListStyleExtractor.typeFromKeyword("KOREAN_HANGUL_FORMAL"))
        // NEGATIVE CONTROL — the documented scope boundary. The two hanja
        // siblings have zero corpus carriers and stay unmodelled, so they
        // keep answering null (read as "keep the running value"). If a
        // later wave models them, THIS assertion is the one to update.
        assertNull(ListStyleExtractor.typeFromKeyword("korean-hanja-formal"))
        assertNull(ListStyleExtractor.typeFromKeyword("korean-hanja-informal"))
    }

    // ── 3. End to end on the VERBATIM corpus payload ────────────────────

    @Test
    fun `counter-suffix korean list resolves hangul markers`() {
        // `counter-suffix__0__3`'s property list, byte-shapes copied from
        // the wave51-fix per-test IR. The `<ol>` carries the type; neither
        // `<li>` carries a baked `meta.markerText` (the counter bake
        // declined this style), which is exactly why the runtime table has
        // to answer — the natives cannot lean on the bake here.
        val ol = listOf(
            pair("MarginTop", """{"px":0}"""), pair("MarginRight", """{"px":0}"""),
            pair("MarginBottom", """{"px":0}"""), pair("MarginLeft", """{"px":0}"""),
            pair("PaddingTop", """{"px":0}"""),
            pair("PaddingRight", """{"original":{"v":3,"u":"EM"}}"""),
            pair("PaddingBottom", """{"px":0}"""),
            pair("PaddingLeft", """{"original":{"v":3,"u":"EM"}}"""),
            pair("LineHeight", """{"multiplier":1.5,"original":{"type":"percentage","value":150}}"""),
            pair("ListStyleType", "\"korean-hangul-formal\"")
        )
        // `counter-suffix__0__3__0` — declares nothing list-related of its
        // own, so the container's type reaches it through the cascade
        // (css-lists-3 §3.1, "Inherited: yes"), slot 2 of the resolver.
        val li = listOf(
            pair("MarginTop", """{"px":0}"""), pair("MarginRight", """{"px":0}"""),
            pair("MarginBottom", """{"px":0}"""), pair("MarginLeft", """{"px":0}"""),
            pair("PaddingTop", """{"px":0}"""), pair("PaddingRight", """{"px":0}"""),
            pair("PaddingBottom", """{"px":0}"""), pair("PaddingLeft", """{"px":0}"""),
            pair("LineHeight", """{"multiplier":1.5,"original":{"type":"percentage","value":150}}""")
        )
        val cfg = ListStyleExtractor.resolveMarkerConfig("ol", ol, li)!!
        assertEquals(ListStyleType.KOREAN_HANGUL_FORMAL, cfg.listStyleType)
        // The two marker strings the frozen browser reference paints on
        // rows 7 and 8 of this document ("일, foo" / "이, bar" — ink bands
        // 165–180 and 189–204 of tools/wpt/refs/9b5435e55e0b54a6cd09c1c563
        // 861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins/
        // css-counter-styles/counter-suffix.png).
        assertEquals("일,", ListStyleApplier.getMarker(0, cfg))
        assertEquals("이,", ListStyleApplier.getMarker(1, cfg))
        // The formal tens: index 9 is the 10th item (일십), index 10 the
        // 11th (일십일) — NOT the bare 십 / 십일 an informal table would give.
        assertEquals("일십,", ListStyleApplier.getMarker(9, cfg))
        assertEquals("일십일,", ListStyleApplier.getMarker(10, cfg))
        // REGRESSION GUARD: before this lane both of these read "1." / "2."
        // because the unmodelled keyword left the `<ol>` UA default in
        // place. Stating the old answer makes the pin's purpose explicit.
        assertNotEquals("1.", ListStyleApplier.getMarker(0, cfg))
    }

    @Test
    fun `a negative ordinal falls back to decimal instead of throwing`() {
        // The wave-43 S4 crash class: ListOrdinal can hand getMarker a
        // negative index straight off the wire (`<ol start="-3">`). The
        // korean arm must take the same guarded bottom-out as `cyclic`,
        // spelled with the style's own suffix.
        val cfg = ListStyleConfig(listStyleType = ListStyleType.KOREAN_HANGUL_FORMAL)
        assertEquals("-3,", ListStyleApplier.getMarker(-4, cfg))
    }

    @Test
    fun `korean marker keeps its glyphs and takes no painted symbol`() {
        // The painted-symbol table must NOT claim a korean marker: disc /
        // circle / square are the only shapes ListMarkerSymbol draws, and
        // a text counter style has to keep its glyphs (css-lists-3 §3.5).
        assertNull(ListMarkerSymbol.shapeFor(ListStyleType.KOREAN_HANGUL_FORMAL))
        assertNull(ListMarkerSymbol.shapeFor(ListStyleType.KOREAN_HANGUL_FORMAL, "일,"))
    }
}
