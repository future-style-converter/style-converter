package app.parsing.css.properties.longhands.sizing

// Wave 42 (lane W3) — css-values-5 §10.1 `calc-size()` typed parsing.
//
// Every rawValue below is VERBATIM from the wave41-final css-values per-test
// IRs (tools/titan/runs/wave41-final/sections/css-values/per-test-ir), where
// they all still rode the Generic degradation envelope: web replayed them
// into Chromium and passed, Android dropped them (calc-size-flex-001..006
// painted NO green), iOS fell back to the intrinsic size. The pinned
// invariants:
//   1. affine `size` expressions over a keyword basis type as CalcSize with
//      the exact (basis, factor, offsetPx) triple and the verbatim original;
//   2. basis-independent calls (pure-length basis / size-free expr) resolve
//      to a plain px length AT PARSE TIME — never a CalcSize;
//   3. unreducible calls (nested min(), percent basis) return null so the
//      caller keeps the Generic envelope — web's verbatim replay must not
//      regress;
//   4. the wire shape is one flat object, identical across the hand-written
//      WidthValue serializer and the kotlinx-polymorphic Min/Max twins.

import app.irmodels.IRProperty
import app.irmodels.IRPropertySerializer
import app.irmodels.properties.spacing.MaxWidthProperty
import app.irmodels.properties.spacing.MinWidthProperty
import app.irmodels.properties.spacing.WidthProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CalcSizeParsingTest {

    // ── invariant 1: the corpus affine family types as CalcSize ──────────

    @Test
    fun `min-width calc-size auto size plus 20px types the affine triple`() {
        // calc-size-flex-001's exact declaration.
        val p = assertIs<MinWidthProperty>(
            MinWidthPropertyParser.parse("calc-size(auto, size + 20px)"))
        val v = assertIs<MinWidthProperty.MinMaxValue.CalcSize>(p.minWidth)
        assertEquals("auto", v.basis)
        assertEquals(1.0, v.factor)
        assertEquals(20.0, v.offsetPx)
        assertEquals("calc-size(auto, size + 20px)", v.original)
    }

    @Test
    fun `min-width calc-size auto size times 2 types factor 2`() {
        // calc-size-flex-009's declaration — multiplication, no offset.
        val p = assertIs<MinWidthProperty>(
            MinWidthPropertyParser.parse("calc-size(auto, size * 2)"))
        val v = assertIs<MinWidthProperty.MinMaxValue.CalcSize>(p.minWidth)
        assertEquals("auto", v.basis)
        assertEquals(2.0, v.factor)
        assertEquals(0.0, v.offsetPx)
    }

    @Test
    fun `min-height calc-size auto size minus 50px carries a negative offset`() {
        // calc-size-aspect-ratio-003's declaration — subtraction.
        val p = assertIs<app.irmodels.properties.spacing.MinHeightProperty>(
            MinHeightPropertyParser.parse("calc-size(auto, size - 50px)"))
        val v = assertIs<MinWidthProperty.MinMaxValue.CalcSize>(p.minHeight)
        assertEquals(1.0, v.factor)
        assertEquals(-50.0, v.offsetPx)
    }

    @Test
    fun `height calc-size auto size div 2 types factor one-half`() {
        // calc-size-flex-007's declaration — division by a number.
        val p = assertIs<app.irmodels.properties.spacing.HeightProperty>(
            HeightPropertyParser.parse("calc-size(auto, size / 2)"))
        val v = assertIs<WidthProperty.WidthValue.CalcSize>(p.height)
        assertEquals(0.5, v.factor)
        assertEquals(0.0, v.offsetPx)
    }

    @Test
    fun `width calc-size fit-content basis survives with its keyword`() {
        // calc-size-min-max-sizes-004..006's declaration family.
        val p = assertIs<WidthProperty>(
            WidthPropertyParser.parse("calc-size(fit-content, size + 80px)"))
        val v = assertIs<WidthProperty.WidthValue.CalcSize>(p.width)
        assertEquals("fit-content", v.basis)
        assertEquals(1.0, v.factor)
        assertEquals(80.0, v.offsetPx)
    }

    @Test
    fun `max-width calc-size min-content size times 1000 types on the max longhand`() {
        // calc-size-grid-repeat's declaration.
        val p = assertIs<MaxWidthProperty>(
            MaxWidthPropertyParser.parse("calc-size(min-content, size * 1000)"))
        val v = assertIs<MaxWidthProperty.MaxValue.CalcSize>(p.maxWidth)
        assertEquals("min-content", v.basis)
        assertEquals(1000.0, v.factor)
    }

    @Test
    fun `height calc-size auto size identity types factor 1 offset 0`() {
        // calc-size-no-body-height-quirk-001's declaration.
        val p = assertIs<app.irmodels.properties.spacing.HeightProperty>(
            HeightPropertyParser.parse("calc-size(auto, size)"))
        val v = assertIs<WidthProperty.WidthValue.CalcSize>(p.height)
        assertEquals(1.0, v.factor)
        assertEquals(0.0, v.offsetPx)
    }

    // ── invariant 2: basis-independent calls resolve at parse time ───────

    @Test
    fun `pure-length basis evaluates to plain px at parse time`() {
        // css-values-5: `calc-size(50px, size * 2)` is fully determined —
        // no runtime basis needed, so the wire carries an ordinary length.
        val p = assertIs<WidthProperty>(
            WidthPropertyParser.parse("calc-size(50px, size * 2)"))
        val v = assertIs<WidthProperty.WidthValue.LengthValue>(p.width)
        assertEquals(100.0, v.length.pixels)
    }

    @Test
    fun `size-free expression over a keyword basis evaluates to its offset`() {
        // The expr never reads `size`, so the basis is irrelevant.
        val p = assertIs<WidthProperty>(
            WidthPropertyParser.parse("calc-size(auto, 30px)"))
        val v = assertIs<WidthProperty.WidthValue.LengthValue>(p.width)
        assertEquals(30.0, v.length.pixels)
    }

    @Test
    fun `any basis admits only size-free expressions`() {
        // `any` + size-free expr → parse-time px.
        val ok = assertIs<WidthProperty>(
            WidthPropertyParser.parse("calc-size(any, 25px)"))
        assertEquals(25.0,
            assertIs<WidthProperty.WidthValue.LengthValue>(ok.width).length.pixels)
        // `any` + `size` is invalid css-values-5 — refuse (null → Generic).
        assertNull(WidthPropertyParser.parse("calc-size(any, size + 25px)"))
    }

    // ── invariant 3: unreducible calls refuse (Generic keeps them) ───────

    @Test
    fun `nested min() refuses so the Generic envelope keeps web replay`() {
        // calc-size-flex-008's declaration — min() is not affine in `size`.
        assertNull(HeightPropertyParser.parse("calc-size(auto, min(size, 100px))"))
    }

    @Test
    fun `percent basis refuses — runtime-dependent either way`() {
        assertNull(WidthPropertyParser.parse("calc-size(50%, size + 10px)"))
    }

    @Test
    fun `relative units in the expression refuse`() {
        // em cannot pre-resolve to px in the converter (null-px contract).
        assertNull(WidthPropertyParser.parse("calc-size(auto, size + 2em)"))
    }

    // ── invariant 4: one flat wire shape on every longhand family ────────

    // The DEFAULT Json instance — the same configuration the production wire
    // uses (IRWireV2's shared `Json`), so the flattening the test observes is
    // exactly what tmpOutput.json carries.
    private val json = Json

    private fun wire(p: IRProperty) =
        json.encodeToJsonElement(IRPropertySerializer, p).jsonObject

    @Test
    fun `min-width CalcSize serialises to the flat calc-size wire object`() {
        val p = MinWidthPropertyParser.parse("calc-size(auto, size + 20px)")!!
        val data = wire(p)["data"]!!.jsonObject
        // Polymorphic discriminator + the four payload keys, nothing nested.
        assertEquals("calc-size", data["type"]!!.jsonPrimitive.content)
        assertEquals("auto", data["basis"]!!.jsonPrimitive.content)
        assertEquals("1.0", data["factor"]!!.jsonPrimitive.content)
        assertEquals("20.0", data["offsetPx"]!!.jsonPrimitive.content)
        assertEquals("calc-size(auto, size + 20px)",
            data["original"]!!.jsonPrimitive.content)
    }

    @Test
    fun `width CalcSize serialises byte-compatible with the min-width twin`() {
        val p = WidthPropertyParser.parse("calc-size(auto, size + 60px)")!!
        val data = wire(p)["data"]!!.jsonObject
        // The hand-written WidthValueSerializer must emit the SAME keys the
        // kotlinx-polymorphic MinMaxValue encoding produces — one decode
        // path for all six physical sizing longhands on every runtime.
        assertEquals(
            setOf("type", "basis", "factor", "offsetPx", "original"),
            data.keys)
        assertEquals("calc-size", data["type"]!!.jsonPrimitive.content)
    }
}
