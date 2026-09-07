package com.styleconverter.runtime.transforms

// retro R1 (finding A11#1) — every shape of the `translate` longhand's wire.
// All eight payloads are VERBATIM `:converter:run --to ir` output of
// fixtures/properties/transforms/translate-longhand.json (retro/A11/runs/
// properties__transforms__translate-longhand/ir.json); the pairs-04 payload
// is from fidelity/pairwise/pairs-04. The one-axis shapes used to decode as
// identity (Translate_OneAxis_Px: Android x0 = 16 vs iOS/web 36).

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TranslateLonghandWireTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun config(vararg props: Pair<String, String>): TransformConfig =
        TransformExtractor.extractTransformConfig(props.map { (t, j) -> t to parse(j) })

    @Test
    fun `none is the identity`() {
        // Translate_None — css-transforms-2 §5 initial value.
        val c = config("Translate" to """{"type":"none"}""")
        assertNull(c.translateX); assertNull(c.translateY); assertNull(c.translateZ)
        assertNull(c.translateXFraction); assertNull(c.translateYFraction)
        assertFalse(c.hasTransform)
    }

    @Test
    fun `one-axis length nests the px under length`() {
        // Translate_OneAxis_Px — `translate: 20px`. The dropped shape.
        val c = config("Translate" to """{"type":"length","length":{"px":20}}""")
        assertEquals(20f, c.translateX!!.value, 0f)
        assertNull(c.translateY)
        assertNull(c.translateXFraction)
    }

    @Test
    fun `one-axis percentage rides as an own-width fraction`() {
        // Translate_OneAxis_Pct — `translate: 25%` resolves against the
        // element's own reference box (§5), so it is 0.25 of width at draw time.
        val c = config("Translate" to """{"type":"percentage","percentage":25}""")
        assertEquals(0.25f, c.translateXFraction!!, 1e-6f)
        assertNull(c.translateX)
        assertNull(c.translateYFraction)
    }

    @Test
    fun `two-axis shapes carry flat px or percentage per axis`() {
        // Translate_TwoAxis_Px — `translate: 20px 10px`.
        val px = config("Translate" to """{"type":"2d","x":{"type":"length","px":20},"y":{"type":"length","px":10}}""")
        assertEquals(20f, px.translateX!!.value, 0f)
        assertEquals(10f, px.translateY!!.value, 0f)
        // Translate_TwoAxis_Pct — `translate: 10% 5%`.
        val pct = config("Translate" to """{"type":"2d","x":{"type":"percentage","percentage":10},"y":{"type":"percentage","percentage":5}}""")
        assertEquals(0.10f, pct.translateXFraction!!, 1e-6f)
        assertEquals(0.05f, pct.translateYFraction!!, 1e-6f)
        // Translate_TwoAxis_Mixed — `translate: 20px 50%`.
        val mixed = config("Translate" to """{"type":"2d","x":{"type":"length","px":20},"y":{"type":"percentage","percentage":50}}""")
        assertEquals(20f, mixed.translateX!!.value, 0f)
        assertNull(mixed.translateY)
        assertEquals(0.5f, mixed.translateYFraction!!, 1e-6f)
        // Translate_Negative — `translate: -15px -5px`.
        val neg = config("Translate" to """{"type":"2d","x":{"type":"length","px":-15},"y":{"type":"length","px":-5}}""")
        assertEquals(-15f, neg.translateX!!.value, 0f)
        assertEquals(-5f, neg.translateY!!.value, 0f)
    }

    @Test
    fun `three-axis shape carries z as a bare length`() {
        // Translate_ThreeAxis — `translate: 10px 20px 30px`; z has no
        // percentage form in the grammar, so it is a bare IRLength.
        val c = config("Translate" to """{"type":"3d","x":{"type":"length","px":10},"y":{"type":"length","px":20},"z":{"px":30}}""")
        assertEquals(10f, c.translateX!!.value, 0f)
        assertEquals(20f, c.translateY!!.value, 0f)
        assertEquals(30f, c.translateZ!!.value, 0f)
    }

    @Test
    fun `pairs-04 043 keeps its translate next to a reflecting scale`() {
        // VERBATIM: PW_Images_Transforms_04 — `perspective: none; scale: -1 1;
        // translate: 20px`. Android drew x0 = 16 (translate dropped) vs 36.
        val c = config(
            "Perspective" to """{"type":"none"}""",
            "Scale" to """{"type":"2d","x":-1,"y":1}""",
            "Translate" to """{"type":"length","length":{"px":20}}""",
        )
        assertEquals(20f, c.translateX!!.value, 0f)
        assertEquals(-1f, c.scaleX!!, 0f)
        assertEquals(1f, c.scaleY!!, 0f)
        // Longhand-only: stays on the graphicsLayer route (T·R·S is §6 order).
        assertFalse(TransformMatrixComposer.takesMatrixPath(c))
    }

    @Test
    fun `the pre-union untyped shape still decodes`() {
        // The shape TransformExtractorTest has always used ({x:{px},y:{px}});
        // kept so an older IR stream is not silently zeroed.
        val c = config("Translate" to """{"x":{"px":50.0},"y":{"px":100.0}}""")
        assertEquals(50f, c.translateX!!.value, 0f)
        assertEquals(100f, c.translateY!!.value, 0f)
    }
}
