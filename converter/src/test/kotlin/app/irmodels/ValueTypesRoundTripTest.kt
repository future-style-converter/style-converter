package app.irmodels

// Round-trip tests for the seven composite value serializers whose
// deserializers used to branch on containsKey("u") — a key the
// IRLengthSerializer NEVER emits at the top level ("u" only exists inside
// the nested "original" object). Those phantom branches meant every
// Length value round-tripped into the Percentage branch and crashed (or
// silently mis-typed). Fixed at the v2 freeze: readers now dispatch on
// what the serializers ACTUALLY emit. NOTE the wire bytes for these
// values did NOT change — only the broken readers did — so these tests
// assert serialize→deserialize equality, not new shapes.

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValueTypesRoundTripTest {

    private val json = Json

    // Generic helper: encode with the value's own serializer, decode back,
    // and compare structural equality.
    private inline fun <reified T> roundTrip(value: T): T =
        json.decodeFromString<T>(json.encodeToString(kotlinx.serialization.serializer<T>(), value))

    // ---- PaddingValue ----

    @Test
    fun `PaddingValue Length round-trips through the px object form`() {
        // 16px emits {"px":16.0} — the old reader sent this to the
        // Percentage branch (crash on jsonPrimitive of an object).
        val v = PaddingValue.Length(IRLength.fromPx(16.0))
        assertEquals(v, roundTrip<PaddingValue>(v))
    }

    @Test
    fun `PaddingValue relative Length round-trips through the original object form`() {
        // 2em emits {"original":{"v":2.0,"u":"EM"}} (no px key at all).
        val v = PaddingValue.Length(IRLength.fromRelative(2.0, IRLength.LengthUnit.EM))
        assertEquals(v, roundTrip<PaddingValue>(v))
    }

    @Test
    fun `PaddingValue Percentage round-trips through the bare number form`() {
        val v = PaddingValue.Percentage(IRPercentage(10.0))
        assertEquals(v, roundTrip<PaddingValue>(v))
    }

    @Test
    fun `PaddingValue Expression and Keyword round-trip`() {
        val e = PaddingValue.Expression("calc(1px + 2%)")
        assertEquals(e, roundTrip<PaddingValue>(e))
        val k = PaddingValue.Keyword("inherit")
        assertEquals(k, roundTrip<PaddingValue>(k))
    }

    // ---- MarginValue ----

    @Test
    fun `MarginValue all variants round-trip`() {
        val l = MarginValue.Length(IRLength.fromPt(12.0)) // dual storage: px + original
        assertEquals(l, roundTrip<MarginValue>(l))
        val p = MarginValue.Percentage(IRPercentage(25.0))
        assertEquals(p, roundTrip<MarginValue>(p))
        val a = MarginValue.Auto()
        assertEquals(a, roundTrip<MarginValue>(a))
        val e = MarginValue.Expression("var(--m)")
        assertEquals(e, roundTrip<MarginValue>(e))
    }

    // ---- ScrollPaddingValue ----

    @Test
    fun `ScrollPaddingValue all variants round-trip`() {
        val l = ScrollPaddingValue.Length(IRLength.fromPx(4.0))
        assertEquals(l, roundTrip<ScrollPaddingValue>(l))
        val p = ScrollPaddingValue.Percentage(IRPercentage(50.0))
        assertEquals(p, roundTrip<ScrollPaddingValue>(p))
        val a = ScrollPaddingValue.Auto()
        assertEquals(a, roundTrip<ScrollPaddingValue>(a))
        val k = ScrollPaddingValue.Keyword("inherit") // {"kw": ...} wrapper
        assertEquals(k, roundTrip<ScrollPaddingValue>(k))
        val r = ScrollPaddingValue.Raw("clamp(1px, 2%, 3px)") // {"raw": ...} wrapper
        assertEquals(r, roundTrip<ScrollPaddingValue>(r))
    }

    // ---- BorderRadiusValue ----

    @Test
    fun `BorderRadiusValue all variants round-trip`() {
        val l = BorderRadiusValue.Length(IRLength.fromPx(8.0))
        assertEquals(l, roundTrip<BorderRadiusValue>(l))
        val p = BorderRadiusValue.Percentage(IRPercentage(50.0))
        assertEquals(p, roundTrip<BorderRadiusValue>(p))
        val k = BorderRadiusValue.Keyword("initial")
        assertEquals(k, roundTrip<BorderRadiusValue>(k))
        val r = BorderRadiusValue.Raw("calc(1em + 2px)")
        assertEquals(r, roundTrip<BorderRadiusValue>(r))
    }

    // ---- AnimationRangeValue ----

    @Test
    fun `AnimationRangeValue Length no longer collapses into Percentage`() {
        // The old reader's `is JsonObject -> Percentage` branch was exactly
        // backwards: a Percentage is a bare number and a Length is the
        // ONLY plain-object form.
        val l = AnimationRangeValue.Length(IRLength.fromPx(100.0))
        assertEquals(l, roundTrip<AnimationRangeValue>(l))
        val p = AnimationRangeValue.Percentage(IRPercentage(25.0))
        assertEquals(p, roundTrip<AnimationRangeValue>(p))
    }

    @Test
    fun `AnimationRangeValue NamedRange and Keyword round-trip`() {
        val n = AnimationRangeValue.NamedRange(TimelineRangeName.ENTRY_CROSSING, IRPercentage(30.0))
        assertEquals(n, roundTrip<AnimationRangeValue>(n))
        val k = AnimationRangeValue.Keyword("normal")
        assertEquals(k, roundTrip<AnimationRangeValue>(k))
        // Documented asymmetry: Raw shares the string wire form with
        // Keyword and reads back as Keyword — both carry verbatim text.
        val raw = AnimationRangeValue.Raw("entry 10% exit 90%")
        val back = roundTrip<AnimationRangeValue>(raw)
        assertTrue(back is AnimationRangeValue.Keyword && back.value == raw.value)
    }

    // ---- PositionValue ----

    @Test
    fun `PositionValue all variants round-trip`() {
        val l = PositionValue.Length(IRLength.fromPx(10.0))
        assertEquals(l, roundTrip<PositionValue>(l))
        // Percentage: bare number wire form (the old reader could never
        // produce this variant — objects went to Percentage, numbers to
        // Keyword, both wrong).
        val p = PositionValue.Percentage(IRPercentage(50.0))
        assertEquals(p, roundTrip<PositionValue>(p))
        val k = PositionValue.Keyword("center")
        assertEquals(k, roundTrip<PositionValue>(k))
    }

    // ---- SizeValue ----

    @Test
    fun `SizeValue Length and Percentage round-trip honestly`() {
        val l = SizeValue.LengthValue(IRLength.fromPx(120.0))
        assertEquals(l, roundTrip<SizeValue>(l))
        // Previously a bare-number percentage fell into the Keyword branch
        // ("50.0" as keyword text) — now it decodes as PercentageValue.
        val p = SizeValue.PercentageValue(IRPercentage(50.0))
        assertEquals(p, roundTrip<SizeValue>(p))
    }

    @Test
    fun `SizeValue keyword family and structured variants round-trip`() {
        listOf(SizeValue.Auto, SizeValue.None, SizeValue.MaxContent, SizeValue.MinContent).forEach { v ->
            assertEquals(v, roundTrip<SizeValue>(v))
        }
        val fitNull = SizeValue.FitContent(null)
        assertEquals(fitNull, roundTrip<SizeValue>(fitNull))
        val fitLen = SizeValue.FitContent(IRLength.fromPx(200.0))
        assertEquals(fitLen, roundTrip<SizeValue>(fitLen))
        val e = SizeValue.Expression("min(100%, 300px)")
        assertEquals(e, roundTrip<SizeValue>(e))
        val k = SizeValue.Keyword("inherit")
        assertEquals(k, roundTrip<SizeValue>(k))
    }
}
