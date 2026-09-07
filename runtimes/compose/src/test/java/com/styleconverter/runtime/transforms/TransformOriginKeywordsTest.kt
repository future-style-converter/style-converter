package com.styleconverter.runtime.transforms

// retro R1 (finding A11#14, Compose half) — transform-origin keyword pairs
// bind to their own axis (css-transforms-1 §4 <position> grammar), whatever
// order the converter stored them in. Wires are VERBATIM from
// fixtures/properties/transforms/transform-origin.json converted with
// `:converter:run --to ir` (retro/A11/runs/properties__transforms__transform-
// origin/ir.json) and from fidelity/pairwise/pairs-06.

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransformOriginKeywordsTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun config(vararg props: Pair<String, String>): TransformConfig =
        TransformExtractor.extractTransformConfig(props.map { (t, j) -> t to parse(j) })

    private val rotate15 = """{"type":"functions","list":[{"fn":"rotate","a":{"deg":15}}]}"""

    @Test
    fun `top right binds RIGHT to x and TOP to y`() {
        // VERBATIM: Origin_TopRight — stored positionally as x=TOP, y=RIGHT.
        // Old reading: x = TOP → 0, y = RIGHT → 1 = the bottom-LEFT corner.
        val c = config(
            "TransformOrigin" to """{"x":{"type":"keyword","value":"TOP"},"y":{"type":"keyword","value":"RIGHT"}}""",
            "Transform" to rotate15,
        )
        assertEquals(1f, c.originX, 0f)
        assertEquals(0f, c.originY, 0f)
    }

    @Test
    fun `bottom center binds BOTTOM to y and CENTER to x`() {
        // VERBATIM: Origin_BottomCenter (and pairs-06 PW_Transforms_Typography_03).
        val c = config(
            "TransformOrigin" to """{"x":{"type":"keyword","value":"BOTTOM"},"y":{"type":"keyword","value":"CENTER"}}""",
            "Transform" to rotate15,
        )
        assertEquals(0.5f, c.originX, 0f)
        assertEquals(1f, c.originY, 0f)
    }

    @Test
    fun `pairs in the natural order and single keywords are unchanged`() {
        // VERBATIM: Origin_TopLeft — x=TOP, y=LEFT is also a swapped store,
        // but both corners are 0 so the old and new readings coincide.
        val tl = config("TransformOrigin" to """{"x":{"type":"keyword","value":"TOP"},"y":{"type":"keyword","value":"LEFT"}}""")
        assertEquals(0f, tl.originX, 0f)
        assertEquals(0f, tl.originY, 0f)
        // VERBATIM: Origin_KeywordSingle — `left` fills y with the 50% default.
        val left = config("TransformOrigin" to """{"x":{"type":"keyword","value":"LEFT"},"y":{"type":"keyword","value":"CENTER"}}""")
        assertEquals(0f, left.originX, 0f)
        assertEquals(0.5f, left.originY, 0f)
        // VERBATIM: Origin_Center — identical on all three platforms in the audit.
        val centre = config("TransformOrigin" to """{"x":{"type":"keyword","value":"CENTER"},"y":{"type":"keyword","value":"CENTER"}}""")
        assertEquals(0.5f, centre.originX, 0f)
        assertEquals(0.5f, centre.originY, 0f)
    }

    @Test
    fun `percentages and lengths keep their axis-neutral readings`() {
        // VERBATIM: Origin_Pct_Pair, Origin_Lengths, Origin_Mixed_LenPct.
        val pct = config("TransformOrigin" to """{"x":{"type":"percentage","percentage":50},"y":{"type":"percentage","percentage":50}}""")
        assertEquals(0.5f, pct.originX, 0f)
        assertEquals(0.5f, pct.originY, 0f)
        val len = config("TransformOrigin" to """{"x":{"type":"length","px":20},"y":{"type":"length","px":40}}""")
        assertEquals(20f, len.originXDp!!.value, 0f)
        assertEquals(40f, len.originYDp!!.value, 0f)
        val mixed = config("TransformOrigin" to """{"x":{"type":"length","px":30},"y":{"type":"percentage","percentage":25}}""")
        assertEquals(30f, mixed.originXDp!!.value, 0f)
        assertNull(mixed.originYDp)
        assertEquals(0.25f, mixed.originY, 0f)
    }

    @Test
    fun `top right rotates the fixture box to Chromium's centroid, not the natives' old one`() {
        // 120x80 box, rotate(15deg) about the top-RIGHT corner (120, 0). The
        // box centre (60,40), rel (−60, 40), rotates 15° cw to
        // (−60·cos15 − 40·sin15, −60·sin15 + 40·cos15) = (−68.31, 23.11) →
        // (51.69, 23.11); + the 16px capture pad = (67.7, 39.1) — Chromium
        // measured (68.4, 41.2). The un-swapped origin (0, 80) gives rel
        // (60, −40) → (68.31, −23.11) → (68.31, 56.89) — both natives
        // measured (82.3, 71.8) = (68.3, 56.9) + 16 − wrong corner.
        val c = config(
            "TransformOrigin" to """{"x":{"type":"keyword","value":"TOP"},"y":{"type":"keyword","value":"RIGHT"}}""",
            "Transform" to rotate15,
        )
        val pivX = TransformPivot.axisPx(null, 1f, c.originX, 120f, 0f)
        val pivY = TransformPivot.axisPx(null, 1f, c.originY, 80f, 0f)
        assertEquals(120f, pivX, 0f)
        assertEquals(0f, pivY, 0f)
        val (x, y) = TransformMatrixComposer.ctm(c, 1f, 120f, 80f, pivX, pivY).project(60f, 40f)
        assertEquals(51.69f, x, 0.01f)
        assertEquals(23.11f, y, 0.01f)
        // The old corner, for the record — the natives' shared centroid.
        val (ox, oy) = TransformMatrixComposer.ctm(c, 1f, 120f, 80f, 0f, 80f).project(60f, 40f)
        assertEquals(68.31f, ox, 0.01f)
        assertEquals(56.89f, oy, 0.01f)
    }
}
