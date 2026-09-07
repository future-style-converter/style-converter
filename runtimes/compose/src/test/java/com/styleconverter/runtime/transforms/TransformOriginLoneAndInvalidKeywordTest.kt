package com.styleconverter.runtime.transforms

// retro R5 (finding A11#14 — natives twin agreement) — the two cases lane R1's
// TransformOriginKeywords left to positional luck, pinned to the answers the
// iOS resolver gives (TransformOriginKeywordTests.swift), so both natives agree:
//   * a LONE vertical keyword: the converter (TransformOriginPropertyParser,
//     `isVerticalKeyword(parts[0]) -> x`) encodes `transform-origin: top` as
//     x:TOP, y:TOP. css-transforms-1 §5: "If only one value is specified, the
//     second value is assumed to be center" — so that is `center top` (0.5, 0),
//     not the top-LEFT corner the plain swap produced.
//   * a <length-percentage> beside a REORDERED keyword (`top 10px`, `30% right`):
//     outside the §5 grammar — only the keyword pair may reorder — so Chromium
//     drops the declaration, the origin is the initial 50% 50%, and the dropped
//     <length> must not ride as a dp anchor either.
// Wires are synthetic: wave49-final carries 0 keyword TransformOrigin tests and
// no fixture declares a lone vertical keyword; the TOP,TOP shape is the
// converter's verbatim emission for `top`.

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransformOriginLoneAndInvalidKeywordTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun config(vararg props: Pair<String, String>): TransformConfig =
        TransformExtractor.extractTransformConfig(props.map { (t, j) -> t to parse(j) })

    @Test
    fun `a lone vertical keyword is center-top, not the corner`() {
        // The converter's verbatim wire for `transform-origin: top`.
        val top = config("TransformOrigin" to """{"x":{"type":"keyword","value":"TOP"},"y":{"type":"keyword","value":"TOP"}}""")
        assertEquals(0.5f, top.originX, 0f)
        assertEquals(0f, top.originY, 0f)
        // …and for `bottom`.
        val bottom = config("TransformOrigin" to """{"x":{"type":"keyword","value":"BOTTOM"},"y":{"type":"keyword","value":"BOTTOM"}}""")
        assertEquals(0.5f, bottom.originX, 0f)
        assertEquals(1f, bottom.originY, 0f)
    }

    @Test
    fun `a length or percentage beside a reordered keyword is an ignored declaration`() {
        // `top 10px`: fractions at the initial value, no dp anchor on either axis.
        val c = config("TransformOrigin" to """{"x":{"type":"keyword","value":"TOP"},"y":{"type":"length","px":10}}""")
        assertEquals(0.5f, c.originX, 0f)
        assertEquals(0.5f, c.originY, 0f)
        assertNull(c.originXDp)
        assertNull(c.originYDp)
        // `30% right`: the percentage does not survive on either axis.
        val p = config("TransformOrigin" to """{"x":{"type":"percentage","percentage":30},"y":{"type":"keyword","value":"RIGHT"}}""")
        assertEquals(0.5f, p.originX, 0f)
        assertEquals(0.5f, p.originY, 0f)
        assertTrue(TransformOriginKeywords.dropsDeclaration(
            parse("""{"type":"keyword","value":"TOP"}"""), parse("""{"type":"length","px":10}""")))
    }

    @Test
    fun `a 50 percent beside a reordered keyword is dropped, as on iOS`() {
        // Retro round-2 F2 (skeptic S4 defect 0) — the twin-probe table. `50%` is a
        // <percentage>, not the `center` keyword: the converter emits
        // {type:percentage,percentage:50} for it and {type:keyword,value:CENTER} for
        // `center`, so the wire distinguishes them and css-transforms-1 §4's (ED
        // numbering) keyword-only `&&` production applies — Chromium drops `top 50%`
        // and `50% right` to the initial 50% 50%. This side already behaved so
        // (Component.keyword = false → dropsDeclaration); the iOS resolver read the
        // 50% as the implicit centre and gave (0.5, 0) / (1, 0.5) — TransformOrigin-
        // Resolver.swift now carries the same keyword flag (Axis.isKeyword) and
        // TransformOriginKeywordTests.testAFiftyPercentBesideASwappedKeywordIsDropped-
        // LikeCompose pins these identical wires, so the natives cannot drift apart
        // without a test failing on each side. Synthetic wires: no corpus or fixture
        // carrier exists, so no cell moves.
        val top50 = config("TransformOrigin" to """{"x":{"type":"keyword","value":"TOP"},"y":{"type":"percentage","percentage":50}}""")
        assertEquals(0.5f, top50.originX, 0f)
        assertEquals(0.5f, top50.originY, 0f)
        val right50 = config("TransformOrigin" to """{"x":{"type":"percentage","percentage":50},"y":{"type":"keyword","value":"RIGHT"}}""")
        assertEquals(0.5f, right50.originX, 0f)
        assertEquals(0.5f, right50.originY, 0f)
        assertTrue(TransformOriginKeywords.dropsDeclaration(
            parse("""{"type":"keyword","value":"TOP"}"""), parse("""{"type":"percentage","percentage":50}""")))
        // Control — the KEYWORD centre beside the same reordered keywords is the valid
        // `&&` pair and keeps its axes: `top center` = (0.5, 0), `center right` = (1, 0.5).
        val topCenter = config("TransformOrigin" to """{"x":{"type":"keyword","value":"TOP"},"y":{"type":"keyword","value":"CENTER"}}""")
        assertEquals(0.5f, topCenter.originX, 0f)
        assertEquals(0f, topCenter.originY, 0f)
        val centerRight = config("TransformOrigin" to """{"x":{"type":"keyword","value":"CENTER"},"y":{"type":"keyword","value":"RIGHT"}}""")
        assertEquals(1f, centerRight.originX, 0f)
        assertEquals(0.5f, centerRight.originY, 0f)
        assertFalse(TransformOriginKeywords.dropsDeclaration(
            parse("""{"type":"keyword","value":"TOP"}"""), parse("""{"type":"keyword","value":"CENTER"}""")))
    }

    @Test
    fun `the valid positional orders keep their length`() {
        // `10px top` and `right 10px` fit `[<lp>|h-kw] [<lp>|v-kw]` as written.
        val a = config("TransformOrigin" to """{"x":{"type":"length","px":10},"y":{"type":"keyword","value":"TOP"}}""")
        assertEquals(10f, a.originXDp!!.value, 0f)
        assertEquals(0f, a.originY, 0f)
        val b = config("TransformOrigin" to """{"x":{"type":"keyword","value":"RIGHT"},"y":{"type":"length","px":10}}""")
        assertEquals(1f, b.originX, 0f)
        assertEquals(10f, b.originYDp!!.value, 0f)
        assertFalse(TransformOriginKeywords.dropsDeclaration(
            parse("""{"type":"keyword","value":"RIGHT"}"""), parse("""{"type":"length","px":10}""")))
    }
}
